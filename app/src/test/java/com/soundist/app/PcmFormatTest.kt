package com.soundist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 阶段 A 格式转换（[PcmFormat]）确定性测试：44.1↔48kHz 重采样、mono↔stereo 声道转换。
 *
 * 覆盖任务要求「44.1/48kHz、mono/stereo 转换」。这些是纯 JVM 函数，可直接验证；
 * 实际原生回调读到的就是这里产出的设备格式交错 PCM。
 */
class PcmFormatTest {

    // ---- 重采样 ----

    @Test fun resample_44k_to_48k_preserves_frame_count_ratio() {
        // 44100 Hz，1 秒 stereo 源 → 48000 Hz 应得 48000 帧。
        val src = FloatArray(44100 * 2)
        val out = PcmFormat.resampleLinear(src, 2, 44100, 48000)
        assertEquals(48000 * 2, out.size)
    }

    @Test fun resample_48k_to_44k_preserves_frame_count_ratio() {
        val src = FloatArray(48000 * 2)
        val out = PcmFormat.resampleLinear(src, 2, 48000, 44100)
        assertEquals(44100 * 2, out.size)
    }

    @Test fun resample_same_rate_is_identity_copy() {
        val src = FloatArray(64) { it.toFloat() }
        val out = PcmFormat.resampleLinear(src, 2, 44100, 44100)
        assertEquals(src.toList(), out.toList())
        // 不共享底层数组：改 out 不影响 src。
        out[0] = -999f
        assertEquals(0f, src[0])
    }

    @Test fun resample_preserves_dc_level() {
        // DC 0.5 的 mono 信号重采样后每点仍 ≈0.5（线性插值对常数信号无损）。
        val src = FloatArray(1000) { 0.5f }
        val out = PcmFormat.resampleLinear(src, 1, 44100, 48000)
        out.forEach { assertTrue("DC 应保持 0.5，实际 $it", abs(it - 0.5f) < 1e-6f) }
    }

    @Test fun resample_output_is_finite() {
        val src = FloatArray(44100 * 2) { kotlin.math.sin(it * 0.01).toFloat() }
        val out = PcmFormat.resampleLinear(src, 2, 44100, 48000)
        out.forEach { assertTrue("输出必须有限", it.isFinite()) }
    }

    // ---- 声道转换 ----

    @Test fun mono_to_stereo_duplicates_channel() {
        val src = FloatArray(4) { (it + 1).toFloat() } // [1,2,3,4] 帧
        val out = PcmFormat.convertChannels(src, 1, 2)
        assertEquals(8, out.size)
        for (f in 0 until 4) {
            assertEquals(src[f], out[f * 2])
            assertEquals(src[f], out[f * 2 + 1])
        }
    }

    @Test fun stereo_to_mono_averages_channels() {
        // 帧 [L,R] = [1,3],[2,4],[5,7],[6,8] → mono [2,3,6,7]
        val src = floatArrayOf(1f, 3f, 2f, 4f, 5f, 7f, 6f, 8f)
        val out = PcmFormat.convertChannels(src, 2, 1)
        assertEquals(4, out.size)
        assertEquals(2f, out[0])
        assertEquals(3f, out[1])
        assertEquals(6f, out[2])
        assertEquals(7f, out[3])
    }

    @Test fun channel_convert_same_channels_is_copy() {
        val src = FloatArray(8) { it.toFloat() }
        val out = PcmFormat.convertChannels(src, 2, 2)
        assertEquals(src.toList(), out.toList())
        out[0] = -1f
        assertEquals(0f, src[0])
    }

    // ---- 组合 ----

    @Test fun to_device_format_mono_44k_to_stereo_48k() {
        val src = FloatArray(44100) { 0.25f } // 1 秒 mono 44.1k DC
        val out = PcmFormat.toDeviceFormat(src, 1, 44100, 2, 48000)
        assertEquals(48000 * 2, out.size)
        // mono→stereo 后 L==R 且 DC 保持。
        for (f in 0 until 48000) {
            assertEquals(out[f * 2], out[f * 2 + 1], 1e-6f)
            assertTrue(abs(out[f * 2] - 0.25f) < 1e-6f)
        }
    }

    @Test fun to_device_format_stereo_48k_to_mono_44k() {
        // 1 秒 stereo 48k，L 恒 0.5、R 恒 0.1 → mono (0.5+0.1)/2 = 0.3。
        val src = FloatArray(48000 * 2)
        for (f in 0 until 48000) { src[f * 2] = 0.5f; src[f * 2 + 1] = 0.1f }
        val out = PcmFormat.toDeviceFormat(src, 2, 48000, 1, 44100)
        assertEquals(44100, out.size)
        out.forEach { assertTrue("mono 应为 0.3，实际 $it", abs(it - 0.3f) < 1e-5f) }
    }
}
