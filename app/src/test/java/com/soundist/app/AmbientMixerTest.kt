package com.soundist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.tanh

/**
 * 阶段 B 纯逻辑确定性测试：环境声音量模型、时间常数 ramp、feature flag 回退，
 * 以及原生 MasterChain（DC 阻断 + tanh soft clip）的可执行规格（C 无法在 JVM 运行，
 * 这里用最小 Kotlin 模型复刻其数学，供审查对照，见 [soundist_audio.c]）。
 */
class AmbientMixerTest {

    // ---- 音量模型 ----

    @Test fun muted_master_gain_is_zero() {
        assertEquals(0f, ambientMasterGain(master = 0.8f, ambient = 1f, duck = 1f, muted = true))
    }

    @Test fun master_gain_is_master_times_ambient_times_duck_clamped() {
        assertEquals(0.8f, ambientMasterGain(0.8f, 1f, 1f, false))
        assertEquals(0.16f, ambientMasterGain(0.8f, 1f, 0.2f, false), 1e-5f) // duck 0.2（焦点压缩）
        assertEquals(0.5f, ambientMasterGain(0.8f, 0.625f, 1f, false), 1e-5f) // ambient 0.625（环境声渐弱）
        assertEquals(1f, ambientMasterGain(1.5f, 1f, 1f, false))     // clamp 到 1
        assertEquals(0f, ambientMasterGain(-0.1f, 1f, 1f, false))    // clamp 到 0
    }

    // ---- 时间常数 ramp ----

    @Test fun ramp_coefficient_depends_on_wall_clock_not_frame_count() {
        // 同为 10.67ms 的块：512 帧@48k 与 1024 帧@96k 系数一致。
        val a = rampCoefficient(512f / 48000f, 0.03f)
        val b = rampCoefficient(1024f / 96000f, 0.03f)
        assertEquals(a, b, 1e-6f)
    }

    @Test fun ramp_converges_to_target_within_tau_wall_clock() {
        val tau = 0.03f
        // 分别用 512 帧块 与 1024 帧块（同 48k），模拟 5×tau = 150ms 后都应逼近目标。
        for (blockFrames in intArrayOf(512, 1024)) {
            val blockSeconds = blockFrames.toFloat() / 48000f
            val coeff = rampCoefficient(blockSeconds, tau)
            var cur = 0f
            val target = 0.8f
            var t = 0f
            while (t < 0.15f) { cur += (target - cur) * coeff; t += blockSeconds }
            assertTrue("${blockFrames} 帧块在 5×tau 后应接近目标，实际 $cur", abs(cur - target) < 0.01f)
        }
    }

    @Test fun ramp_coefficient_is_bounded() {
        assertTrue(rampCoefficient(0.01f, 0.03f) in 0f..1f)
        assertEquals(1f, rampCoefficient(0.01f, 0f)) // tau<=0 → 立即
    }

    // ---- 原生 MasterChain 规格（DC 阻断 + tanh soft clip） ----

    @Test fun dc_blocker_removes_constant_offset() {
        // 对应 soundist_audio.c：y = x - x1 + R*y1，R=0.995。输入恒定 0.5 直流应收敛到 ~0。
        val r = 0.995f
        var x1 = 0f; var y1 = 0f
        var y = 0f
        repeat(48000) { // 1 秒 @48k
            val x = 0.5f
            y = x - x1 + r * y1
            x1 = x; y1 = y
        }
        assertTrue("DC 应被阻断（收敛到 ~0），实际 $y", abs(y) < 0.001f)
    }

    @Test fun soft_clip_is_bounded_and_transparent_near_zero() {
        // tanh soft clip：有界 [-1,1]，小信号近似线性。
        assertTrue(tanh(100.0) <= 1.0)
        assertTrue(tanh(-100.0) >= -1.0)
        assertEquals(0.0, tanh(0.0), 1e-9)
        assertTrue("小信号 tanh ≈ x", abs(tanh(0.1) - 0.1) < 0.01)
    }

    @Test fun dc_cutoff_is_consistent_across_sample_rates_and_preserves_20hz() {
        // 对应 soundist_audio.c：R = exp(-2π·cutoff/sampleRate)，cutoff=2Hz。
        fun dcCoeff(cutoffHz: Float, sampleRate: Float): Float =
            kotlin.math.exp((-2.0 * Math.PI * cutoffHz / sampleRate).toDouble()).toFloat()

        for (sr in intArrayOf(48000, 44100, 96000)) {
            val coeff = dcCoeff(2f, sr.toFloat())
            val impliedCutoff = -sr * kotlin.math.ln(coeff.toDouble()) / (2 * Math.PI)
            assertTrue("$sr 下 cutoff 应≈2Hz，实际 $impliedCutoff", abs(impliedCutoff - 2.0) < 0.01)
        }
        // 2Hz cutoff 的一阶高通对 20Hz 幅度应≈1（旧 0.995≈38Hz 会削弱约 6dB）。
        val coeff = dcCoeff(2f, 48000f)
        val w = 2 * Math.PI * 20.0 / 48000.0
        val mag = kotlin.math.hypot(1.0 - kotlin.math.cos(w), kotlin.math.sin(w)) /
            kotlin.math.hypot(1.0 - coeff * kotlin.math.cos(w), coeff * kotlin.math.sin(w))
        assertTrue("20Hz 幅度应≈1，实际 $mag", mag > 0.99)
    }

    // ---- feature flag 回退 ----

    @Test fun feature_flag_defaults_to_media3() {
        // 默认关 = Media3 路径（已验证）；只有显式置 true 才走 miniaudio。
        assertFalse(MiniaudioFeatureFlags.ambientEnabled)
    }
}
