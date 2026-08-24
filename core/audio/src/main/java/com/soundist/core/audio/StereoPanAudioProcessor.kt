package com.soundist.core.audio

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * 等功率立体声声像处理器（对应前端 ambientAudio.ts 每轨 StereoPannerNode）。
 *
 * pan ∈ [-1, 1]：-1 全左、0 居中、+1 全右，等功率公式
 * （left = sqrt((1-pan)/2)，right = sqrt((1+pan)/2)）。pan=0 时左右增益同为
 * sqrt(0.5)，与轻微偏移连续（没有 ~3dB 跳变），并与前端 StereoPannerNode 的居中行为一致。
 *
 * 仅对双声道 PCM（float 或 16-bit）生效；单声道 / 多声道 / 非 PCM 编码在 [onConfigure]
 * 返回 NOT_SET，[isActive] 为 false，不进入处理管线（直通）。
 *
 * 缓冲安全（root cause：errorCode 1004 / "The source buffer is this buffer"）：
 *  - 空输入（管线 drain 时传入共享的 AudioProcessor.EMPTY_BUFFER）直接返回，绝不产出输出。
 *    否则 replaceOutputBuffer(0) 会返回同一个共享 EMPTY_BUFFER，再执行 put(inputBuffer)
 *    就是 ByteBuffer 自拷贝，抛 IllegalArgumentException: The source buffer is this buffer。
 *  - 输出缓冲与输入缓冲是同一实例时（`===`），绝不执行 outputBuffer.put(inputBuffer)；
 *    pan==0 数据已就位直接返回，pan≠0 先快照到独立临时缓冲，再原位处理写回。
 *  - 输入字节按完整左右声道帧处理（float 8 字节 / 16-bit 4 字节），尾部不足一帧的字节透传不处理。
 *
 * 运行时通过 [setPan] 修改声像无需重建播放器即可立即生效。
 */
class StereoPanAudioProcessor : BaseAudioProcessor() {
    @Volatile
    private var pan = 0f

    /** 最近一次 configure 的输入格式；unsupported 时为 null。[isActive] / [queueInput] 读取它，避免读到未 flush 的旧 inputAudioFormat。 */
    private var configuredFormat: AudioProcessor.AudioFormat? = null

    /** 设置声像，[-1, 1] 范围外自动夹取。 */
    fun setPan(value: Float) { pan = value.coerceIn(-1f, 1f) }

    fun currentPan(): Float = pan

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        configuredFormat = inputAudioFormat
        val supported = inputAudioFormat.channelCount == 2 &&
            (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT || inputAudioFormat.encoding == C.ENCODING_PCM_16BIT)
        // 不支持的格式返回 NOT_SET（默认 DefaultAudioSink 会绕过本处理器），不抛 UnhandledAudioFormatException。
        return if (supported) inputAudioFormat else AudioProcessor.AudioFormat.NOT_SET
    }

    override fun isActive(): Boolean {
        val fmt = configuredFormat ?: return false
        return fmt.channelCount == 2 &&
            (fmt.encoding == C.ENCODING_PCM_FLOAT || fmt.encoding == C.ENCODING_PCM_16BIT)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val inputSize = inputBuffer.remaining()
        // 空输入（EMPTY_BUFFER drain）：不产出任何输出，也不触碰 replaceOutputBuffer——
        // 此时处理器内部 buffer 可能仍是共享 EMPTY_BUFFER，replaceOutputBuffer(0) 返回同一实例，
        // 再做 put(inputBuffer) 会触发 ByteBuffer 自拷贝崩溃。
        if (inputSize == 0) return

        val format = configuredFormat ?: run {
            inputBuffer.position(inputBuffer.limit())
            return
        }
        val isFloat = format.encoding == C.ENCODING_PCM_FLOAT
        val frameBytes = if (isFloat) FLOAT_FRAME_BYTES else SHORT_FRAME_BYTES
        val frameCount = inputSize / frameBytes
        val processedBytes = frameCount * frameBytes
        val leftoverBytes = inputSize - processedBytes

        val currentPan = pan
        val leftGain = sqrt((1.0 - currentPan) * 0.5).toFloat()
        val rightGain = sqrt((1.0 + currentPan) * 0.5).toFloat()

        val outputBuffer = replaceOutputBuffer(inputSize)
        if (outputBuffer === inputBuffer) {
            // 输出缓冲与输入缓冲是同一实例（缓冲复用）：绝不能执行 outputBuffer.put(inputBuffer)。
            if (currentPan == 0f) {
                // 数据已就位（pan==0 无声道间增益差），标记输入已消费直接返回。
                inputBuffer.position(inputBuffer.limit())
                return
            }
            // pan≠0：先快照完整帧到独立临时缓冲，再原位处理写回，避免边读边覆盖同一存储。
            val snapshot = ByteBuffer.allocateDirect(processedBytes).order(ByteOrder.nativeOrder())
            val dup = inputBuffer.duplicate()
            dup.limit(dup.position() + processedBytes)
            snapshot.put(dup)
            snapshot.flip()
            processFrames(snapshot, inputBuffer, frameCount, leftGain, rightGain, isFloat)
            // 尾部不足一帧的字节未被快照覆盖，保持原位；输出与输入同存储，数据已就位。
            inputBuffer.position(0)
            inputBuffer.limit(inputSize)
            return
        }

        // 常规路径：输入输出独立缓冲。
        if (frameCount > 0) {
            processFrames(inputBuffer, outputBuffer, frameCount, leftGain, rightGain, isFloat)
        }
        if (leftoverBytes > 0) {
            // 不足一帧的字节透传不处理（不调整增益）。
            val src = inputBuffer.duplicate()
            src.position(src.position() + processedBytes)
            outputBuffer.position(processedBytes)
            outputBuffer.put(src)
        }
        outputBuffer.position(0)
        outputBuffer.limit(processedBytes + leftoverBytes)
        inputBuffer.position(inputBuffer.limit())
    }

    private fun processFrames(
        input: ByteBuffer,
        output: ByteBuffer,
        frameCount: Int,
        leftGain: Float,
        rightGain: Float,
        isFloat: Boolean,
    ) {
        if (isFloat) {
            val inF = input.asFloatBuffer()
            val outF = output.asFloatBuffer()
            repeat(frameCount) {
                outF.put(inF.get() * leftGain)
                outF.put(inF.get() * rightGain)
            }
        } else {
            val inS = input.asShortBuffer()
            val outS = output.asShortBuffer()
            repeat(frameCount) {
                outS.put((inS.get() * leftGain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
                outS.put((inS.get() * rightGain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            }
        }
    }

    companion object {
        /** 16-bit 双声道一帧字节数（2 × 2）。 */
        private const val SHORT_FRAME_BYTES = 4
        /** float 双声道一帧字节数（2 × 4）。 */
        private const val FLOAT_FRAME_BYTES = 8
    }
}

/**
 * 把 [StereoPanAudioProcessor] 注入每个 ExoPlayer 的音频输出链。
 * 每个环境声轨都有独立播放器，因此各自持有独立的声像处理器实例。
 */
class PannableRenderersFactory(context: Context, private val panProcessor: StereoPanAudioProcessor) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink {
        return DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf(panProcessor))
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()
    }
}
