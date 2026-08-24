package com.soundist.core.audio

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * B3：音量卡小（duck 后不恢复）回归测试。
 * 引擎在 duck 时把音量系数压到 0.2，收到 AUDIOFOCUS_GAIN 或重新取得/放弃焦点时恢复到 1。
 * 这些常量是编译期内联的 static final int，可在 JVM 单元测试中直接引用。
 */
class AudioFocusDuckTest {

    @Test
    fun `duck lowers the volume coefficient to 0_2`() {
        assertEquals(0.2f, duckMultiplierFor(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK, 1f), 0.0001f)
    }

    @Test
    fun `gain restores the coefficient back to 1`() {
        // 核心回归：duck 后 gain 恢复，音量系数回到 1（音量不再卡小）。
        assertEquals(1f, duckMultiplierFor(AudioManager.AUDIOFOCUS_GAIN, 0.2f), 0.0001f)
    }

    @Test
    fun `unrelated focus changes keep the current coefficient`() {
        // 普通打断（LOSS / LOSS_TRANSIENT）不改变压缩系数，只有显式 GAIN 才恢复。
        assertEquals(0.2f, duckMultiplierFor(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, 0.2f), 0.0001f)
        assertEquals(1f, duckMultiplierFor(AudioManager.AUDIOFOCUS_LOSS, 1f), 0.0001f)
        assertEquals(0.2f, duckMultiplierFor(0, 0.2f), 0.0001f)
    }

    @Test
    fun `repeated gain stays at one and repeated duck stays compressed`() {
        assertEquals(1f, duckMultiplierFor(AudioManager.AUDIOFOCUS_GAIN, 1f), 0.0001f)
        assertEquals(0.2f, duckMultiplierFor(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK, 0.2f), 0.0001f)
    }
}
