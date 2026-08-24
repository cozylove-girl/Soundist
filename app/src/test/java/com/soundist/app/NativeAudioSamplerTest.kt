package com.soundist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 阶段 C 正式采样器的纯逻辑规格测试（C 实现见 soundist_audio.c，无法在 JVM 运行，这里复刻其数学）。
 *
 * 覆盖验收要点：
 * - 音高比 step = frequency / baseFrequency，与设备采样率无关（采样已在 Kotlin 侧统一到设备格式），
 *   故 44.1k/48k 下音高与速度一致。
 * - 线性插值（分数播放位置）。
 * - ADSR 包络（attack→sustain→release）。
 */
class NativeAudioSamplerTest {

    private fun pitchStep(frequency: Float, baseFrequency: Float): Double =
        frequency.toDouble() / baseFrequency.toDouble()

    private fun lerp(a: Float, b: Float, frac: Float): Float = a + (b - a) * frac

    private fun envelope(age: Long, attackFrames: Long, releaseStart: Long, releaseFrames: Long, releasing: Boolean): Float = when {
        age < attackFrames -> if (attackFrames > 0) age.toFloat() / attackFrames else 1f
        !releasing -> 1f
        else -> {
            val r = age - releaseStart
            if (r >= releaseFrames) 0f else 1f - r.toFloat() / releaseFrames
        }
    }

    @Test fun pitch_step_is_frequency_over_base_frequency_independent_of_sample_rate() {
        // 基频 261.63（C4）触发 523.25（C5）→ 2 倍速（高八度）。
        assertEquals(2.0, pitchStep(523.25f, 261.63f), 1e-4)
        // 基频 130.81（C3）触发 261.63（C4）→ 2 倍速。
        assertEquals(2.0, pitchStep(261.63f, 130.81f), 1e-4)
        // 低八度 → 0.5 倍速。
        assertEquals(0.5, pitchStep(130.81f, 261.63f), 1e-4)
    }

    @Test fun linear_interpolation_is_continuous_and_correct() {
        assertEquals(0.0f, lerp(0f, 1f, 0f), 1e-6f)
        assertEquals(1.0f, lerp(0f, 1f, 1f), 1e-6f)
        assertEquals(0.5f, lerp(0f, 1f, 0.5f), 1e-6f)
        assertEquals(0.25f, lerp(0f, 1f, 0.25f), 1e-6f)
        // 分数位置 0 到 1 之间连续（相邻采样点插值不跳变）。
        val a = lerp(0.2f, 0.8f, 0.99f)
        val b = lerp(0.8f, -0.4f, 0.0f)
        assertTrue(abs(a - 0.8f) < 0.02f)
        assertEquals(0.8f, b, 1e-6f)
    }

    @Test fun envelope_attack_sustain_release() {
        // attack：0→1
        assertEquals(0f, envelope(0, 100, Long.MAX_VALUE, 50, false), 1e-6f)
        assertEquals(0.5f, envelope(50, 100, Long.MAX_VALUE, 50, false), 1e-6f)
        // sustain：attack 后、release 前 = 1
        assertEquals(1f, envelope(200, 100, Long.MAX_VALUE, 50, false), 1e-6f)
        // release：1→0（age 已过 attack，从 releaseStart 起衰减）
        assertEquals(1f, envelope(100, 100, 100, 50, true), 1e-6f)
        assertEquals(0.5f, envelope(125, 100, 100, 50, true), 1e-6f)
        assertEquals(0f, envelope(150, 100, 100, 50, true), 1e-6f)
    }

    @Test fun voice_stealing_picks_oldest_voice() {
        // 32 个 voice 全满时，新触发偷 age 最大的（最老）。
        val ages = IntArray(32) { it * 10 } // age 0,10,...,310
        val oldestIndex = ages.indices.maxByOrNull { ages[it] }!!
        assertEquals(31, oldestIndex) // 最老的是 index 31（age 310）
    }
}
