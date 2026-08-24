package com.soundist.app

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * miniaudio 原生音频核心的 Kotlin 侧入口（阶段 A：安全加固版）。
 *
 * 职责：
 *   - 生命周期状态机：init()/startDevice()/stopDevice()/release() 幂等；release 后禁止
 *     add/remove/set/trigger。
 *   - 单命令生产者：所有原生 JNI 调用都投递到专用 [controlThread]（HandlerThread）串行执行，
 *     保证命令队列（原生 SPSC）严格只有一个生产者；满时返回明确错误，关键 remove/stop 不静默丢。
 *   - native-owned PCM：addTrack/addSample 把解码后的 [FloatArray] 在控制线程统一到设备格式
 *     （[PcmFormat.toDeviceFormat]），再交给原生复制并持有；本对象不持有底层内存引用，
 *     removeTrack/removeSample 后无 use-after-free（原生 retire-ack 负责延迟释放）。
 *   - 错误回流：[lastErrorMessage] 返回最后一次原生错误的可读消息。
 *
 * 实时音频约束（原生回调内强制）：
 *   回调内禁止文件 IO、解码、内存分配/释放、等 mutex、sleep、日志拼接、集合/字符串、JNI。
 *   格式转换（重采样/声道）与 PCM 复制都发生在控制线程，回调只读设备格式交错 float PCM。
 *
 * 说明：
 *   - 当前为单例（object）。若主会话需要多实例，后续可改为 class 携带 handle。
 *   - 阶段 A 只加固核心，不接业务；阶段 B 才会把它接到环境声播放路径（feature flag 后）。
 */
object NativeAudioCore {

    /** 生命周期状态机（与 soundist_audio.c 的 SoundistState 对应）。 */
    enum class State { UNINITIALIZED, INITIALIZED, STARTING, RUNNING, STOPPING, RELEASED, FAILED }

    init {
        System.loadLibrary("soundist_audio")
    }

    // ---- 单生产者控制线程 ----
    private val controlThread = HandlerThread("SoundistNativeAudioControl").apply { start() }
    private val controlLooper: Looper get() = controlThread.looper
    private val controlHandler = Handler(controlThread.looper)
    private val pendingMasterGainBits = AtomicInteger(1f.toRawBits())
    private val appliedMasterGainBits = AtomicInteger(1f.toRawBits())
    private val masterGainDrainScheduled = AtomicBoolean(false)

    /** 原生句柄（SoundistAudioCore* 的整数化），0 = 未分配。仅控制线程读写。 */
    @Volatile
    private var handle: Long = 0L

    /** 镜像状态（仅控制线程写，其它线程 @Volatile 读）。权威状态在原生 atomic。 */
    @Volatile
    private var state = State.UNINITIALIZED

    /** 设备格式（startDevice 后确定，仅控制线程写）。addTrack/addSample 据此重采样。 */
    @Volatile
    private var deviceSampleRate = 0
    @Volatile
    private var deviceChannels = 0

    /** 采样槽占用（仅控制线程读写）：addSample 分配空闲槽，removeSample 归还。 */
    private val sampleSlots = BooleanArray(MAX_SAMPLES)

    // ---------------- JNI（native libsoundist_audio） ----------------

    // 阶段 1
    private external fun nativeCreate(): Long
    private external fun nativeInitialize(handle: Long): Int
    private external fun nativeDestroy(handle: Long): Int
    private external fun nativeVersionString(handle: Long): String
    private external fun nativeLastErrorMessage(handle: Long): String
    private external fun nativePostCommand(handle: Long, type: Int, arg0: Int, arg1: Long): Int
    private external fun nativeCommandCount(handle: Long): Int
    private external fun nativeDrainRetired(handle: Long): Int
    private external fun nativeRetireOverflowCount(handle: Long): Int
    private external fun nativeFailSafe(handle: Long): Int

    // 阶段 2：设备 + 环境声轨道（pcm 为 FloatArray，原生复制并持有）
    private external fun nativeStartDevice(handle: Long, sampleRate: Int, channels: Int): Int
    private external fun nativeStopDevice(handle: Long): Int
    private external fun nativeAddTrack(handle: Long, id: Int, pcm: FloatArray, frames: Long, channels: Int): Int
    private external fun nativeRemoveTrack(handle: Long, id: Int): Int
    private external fun nativeSetTrackGain(handle: Long, id: Int, gain: Float): Int
    private external fun nativeSetTrackPan(handle: Long, id: Int, pan: Float): Int
    private external fun nativeSetTrackPlaying(handle: Long, id: Int, playing: Int): Int
    private external fun nativeSetMasterGain(handle: Long, gain: Float): Int
    private external fun nativeAddTrackFromEncoded(handle: Long, id: Int, encoded: ByteArray, channels: Int, sampleRate: Int): Int

    // 阶段 3：采样器
    private external fun nativeAddSample(handle: Long, index: Int, pcm: FloatArray, frames: Long, channels: Int, sampleRate: Int, baseFrequency: Float): Int
    private external fun nativeRemoveSample(handle: Long, index: Int): Int
    private external fun nativeTriggerSample(handle: Long, index: Int, frequency: Float, gain: Float, pan: Float, attackSeconds: Float, releaseSeconds: Float, cutoffHz: Float): Int

    // 阶段 4：生成 voice 骨架
    private external fun nativeAddVoice(handle: Long, frequency: Float, gain: Float, pan: Float, durationFrames: Long): Int
    // 阶段 D：生成电台黄金调度（纯确定性，对照 Kotlin schedulerGolden）
    private external fun nativeSchedulerGolden(presetId: String, ticks: Int): String
    // 阶段 D：渲染一个合成音色声部（对照 Kotlin pcmProbeForTimbre，供设备/集成测试）
    private external fun nativeRenderSynthVoice(timbre: String, frequency: Double, durationSeconds: Double, attackSeconds: Double, releaseSeconds: Double, cutoffHz: Double, room: Double, gain: Double, seed: Int, outputFrames: Int): FloatArray
    // 阶段 D：渲染一个完整预设到交织立体声 float PCM（合成路径，对照 Kotlin renderPresetPcm）
    private external fun nativeRenderPreset(presetId: String, frames: Int, outputVolume: Double, gain: Int): FloatArray
    // 阶段 D：注册/清空 VSCO 采样（对照 SAMPLE_TIMBRES + decodeWav）
    private external fun nativeRegisterSample(timbre: String, baseFrequency: Double, sampleRate: Double, pcm: ShortArray, frames: Int, channels: Int): Int
    private external fun nativeClearSamples()
    // 阶段 D：增量实时渲染引擎（对照 renderLoop，持久化 + 分帧）
    private external fun nativeCreateGenerativeEngine(presetId: String, outputVolume: Double, gain: Int): Long
    private external fun nativeRenderGenerative(handle: Long, frameCount: Int): FloatArray
    private external fun nativeDestroyGenerativeEngine(handle: Long)
    // 阶段 D：引擎 settings / scenes / 层参数热更新（对照 play() 的 generatorSettings + generatorArrangement）
    private external fun nativeSetGenerativeSettings(handle: Long, tempo: Int, density: Int, variation: Int, entryDelay: Double, eventDuration: Double, gain: Int, ambientResponse: Int, adaptive: Int): Int
    private external fun nativeSetGenerativeScenes(handle: Long, ids: Array<String>, durations: IntArray, energy: DoubleArray, density: DoubleArray, brightness: DoubleArray, space: DoubleArray): Int
    private external fun nativeUpdateGenerativeLayer(handle: Long, layerIndex: Int, volume: Int, pan: Double, enabled: Int, solo: Int): Int
    // 阶段 D：整体替换编排（seed/sectionMinutes/arc/layers，全重启语义）
    private external fun nativeSetGenerativeArrangement(handle: Long, seed: String, sectionMinutes: Int, arc: Int, ids: Array<String>, kinds: IntArray, timbres: Array<String>, volumes: IntArray, probabilities: IntArray, densities: IntArray, durations: DoubleArray, attacks: DoubleArray, releases: DoubleArray, pans: DoubleArray, entryDelays: DoubleArray, rhythms: IntArray, relationships: IntArray, targets: Array<String?>, registers: IntArray, scales: IntArray, ambientResponses: IntArray, enabled: IntArray, solo: IntArray, intro: IntArray, reset: Boolean): Int
    private external fun nativeSetGenerativeOutputVolume(handle: Long, outputVolume: Double): Int
    private external fun nativeSetGenerativeAmbientBias(handle: Long, level: Double, water: Double, air: Double, warm: Double, city: Double, noise: Double): Int
    // 阶段 E：单音色试听（采样/合成路径 + 包络 + 主链路，对照 previewTimbre）
    private external fun nativeRenderPreviewVoice(timbre: String, frequency: Double, durationSeconds: Double, attackSeconds: Double, releaseSeconds: Double, pan: Double, cutoffHz: Double, brightness: Double, room: Double, gain: Double, seed: Int, outputFrames: Int): FloatArray

    // ---------------- 控制线程调度 ----------------

    /** 在控制线程串行执行 [task] 并同步返回结果。已在控制线程则直接执行，避免自死锁。 */
    private fun <T> onControl(task: () -> T): T {
        if (Looper.myLooper() == controlLooper) return task()
        val future = FutureTask(task)
        controlHandler.post(future)
        return future.get()
    }

    // ---------------- 生命周期 ----------------

    /** 分配原生上下文并初始化 ma_context。幂等；失败返回 false，可用 [lastErrorMessage] 查询原因。 */
    fun init(): Boolean = onControl {
        if (state == State.INITIALIZED || state == State.STARTING || state == State.RUNNING) return@onControl true
        if (handle == 0L) {
            handle = nativeCreate()
            if (handle == 0L) { state = State.FAILED; return@onControl false }
        }
        val rc = nativeInitialize(handle)
        if (rc >= 0) {
            state = State.INITIALIZED
            true
        } else {
            state = State.FAILED
            false
        }
    }

    /** 释放原生上下文（含设备与全部 PCM）。幂等；之后本对象不可再用（需重新 init）。 */
    fun release() = onControl {
        val h = handle
        if (h != 0L) {
            nativeDestroy(h)
            handle = 0L
            sampleSlots.fill(false)
            deviceSampleRate = 0
            deviceChannels = 0
        }
        state = State.RELEASED
        Unit
    }

    val isInitialized: Boolean get() = state == State.INITIALIZED || state == State.RUNNING
    val isRunning: Boolean get() = state == State.RUNNING
    val currentState: State get() = state

    // ---------------- 可验证最小功能 / 诊断 ----------------

    /** init 后返回 miniaudio 版本字符串（如 "0.11.22"）。未 init 返回空串。 */
    fun version(): String = onControl {
        val h = handle
        if (h == 0L) "" else nativeVersionString(h)
    }

    /** 最后一次原生错误的可读消息；"OK" 表示无错误。未 init 返回提示。 */
    fun lastErrorMessage(): String = onControl {
        val h = handle
        if (h == 0L) "NativeAudioCore is not initialized" else nativeLastErrorMessage(h)
    }

    /** 当前排队的命令数（诊断用）。 */
    val pendingCommandCount: Int
        get() = onControl {
            val h = handle
            if (h == 0L) 0 else nativeCommandCount(h)
        }

    /** 退休队列溢出诊断计数（静态约束下恒为 0；非零 = 内存安全已被破坏，见 [checkHealth]）。 */
    val retireOverflowCount: Int
        get() = onControl {
            val h = handle
            if (h == 0L) 0 else nativeRetireOverflowCount(h)
        }

    /**
     * 健康检查（应在关键批次后或周期性调用）。退休队列溢出计数非零意味着某个 native-owned
     * 指针已不可恢复地泄漏（该指针已从槽/shadow/队列移除，无法再找到），内存安全假设被破坏，
     * 此时进入统一 fail-safe：在控制线程停止设备、释放全部 PCM、置 FAILED 终止态，此后所有
     * add/remove/set/trigger 返回明确错误。返回 false 表示核心已不可用，调用方应停止播放并上报。
     */
    fun checkHealth(): Boolean = onControl {
        if (handle == 0L || state == State.FAILED) return@onControl false
        if (nativeRetireOverflowCount(handle) > 0) {
            failSafeLocked()
            return@onControl false
        }
        true
    }

    /** 统一 fail-safe（控制线程内）：停设备 + 释放 PCM + 置 FAILED。幂等；stopDevice/release 仍可安全调用。 */
    fun failSafe(): Unit = onControl {
        if (state == State.FAILED || state == State.RELEASED) return@onControl
        failSafeLocked()
    }

    private fun failSafeLocked() {
        if (state == State.FAILED || state == State.RELEASED) return
        state = State.FAILED
        val h = handle
        if (h != 0L) nativeFailSafe(h) /* 原生：置 FAILED + ma_device_stop + free_all_pcm（控制线程，非回调） */
        sampleSlots.fill(false)
        deviceSampleRate = 0
        deviceChannels = 0
    }

    // ---------------- 阶段 2：设备 + 环境声轨道 ----------------

    /**
     * 打开/启动 playback 设备（float32，period 512 帧）。幂等。
     * @return 0 成功；<0 失败（-2 未 init/状态非法；其余为 miniaudio ma_result 负错误码）。
     */
    fun startDevice(sampleRate: Int, channels: Int): Int = onControl {
        if (state == State.RUNNING) return@onControl 0
        if (state != State.INITIALIZED) return@onControl -2
        if (handle == 0L) return@onControl -2
        val rc = nativeStartDevice(handle, sampleRate, channels)
        if (rc == 0) {
            state = State.RUNNING
            deviceSampleRate = sampleRate
            deviceChannels = channels
            // 新设备的原生 master 初值不能沿用上一次设备的 Kotlin 镜像。
            appliedMasterGainBits.set(Float.NaN.toRawBits())
            scheduleMasterGainDrain()
        } else {
            state = State.FAILED
        }
        rc
    }

    /** 停止并销毁设备（可再次 startDevice）。幂等；会释放全部已注册 PCM。
     *  FAILED/RELEASED 态下仍可安全调用，但不会把终止态「复活」为 INITIALIZED。 */
    fun stopDevice() = onControl {
        val h = handle
        if (h != 0L) {
            nativeStopDevice(h)
            if (state != State.FAILED && state != State.RELEASED) {
                state = State.INITIALIZED
            }
            sampleSlots.fill(false)
            deviceSampleRate = 0
            deviceChannels = 0
        }
        Unit
    }

    /**
     * 注册环境声循环轨道。pcm 为解码后的交错 float PCM（源格式），本对象在控制线程把它
     * 统一到设备格式（重采样 + 声道）后交给原生复制持有；此后调用方可安全丢弃 pcm。
     * @param id 业务轨道 id（0..63）；重复 add 同一 id 为替换（旧 PCM 由 retire-ack 安全释放）。
     * @return 0 成功；-1 命令队列满（关键命令，可重试）；-2 未 init/未运行；-4 参数非法；-5 OOM。
     */
    fun addTrack(id: Int, pcm: FloatArray, sourceSampleRate: Int, sourceChannels: Int): Int = onControl {
        if (handle == 0L || state != State.RUNNING) return@onControl -2
        if (id < 0 || id >= MAX_TRACKS || pcm.isEmpty() || sourceSampleRate <= 0 || sourceChannels !in 1..2) return@onControl -4
        val device = PcmFormat.toDeviceFormat(pcm, sourceChannels, sourceSampleRate, deviceChannels, deviceSampleRate)
        nativeAddTrack(handle, id, device, device.size.toLong() / deviceChannels, deviceChannels)
    }

    /** 移除轨道。旧 PCM 由 retire-ack 在回调确认后释放，无 use-after-free。 */
    fun removeTrack(id: Int): Int = onControl {
        if (handle == 0L) return@onControl -2
        if (id < 0 || id >= MAX_TRACKS) return@onControl -4
        nativeRemoveTrack(handle, id)
    }

    /** 设置轨道目标增益（回调内一阶 ramp 平滑）。 */
    fun setTrackGain(id: Int, gain: Float): Int = onControl {
        if (handle == 0L) return@onControl -2
        nativeSetTrackGain(handle, id, gain)
    }

    /** 设置轨道声像 -1..1（equal-power balance）。 */
    fun setTrackPan(id: Int, pan: Float): Int = onControl {
        if (handle == 0L) return@onControl -2
        nativeSetTrackPan(handle, id, pan)
    }

    /** 播放/暂停轨道（target 值，回调内生效）。 */
    fun setTrackPlaying(id: Int, playing: Boolean): Int = onControl {
        if (handle == 0L) return@onControl -2
        nativeSetTrackPlaying(handle, id, if (playing) 1 else 0)
    }

    /**
     * 设置 MasterChain 目标 master 增益（= masterVolume × ambientVolume × duckMultiplier，静音/duck 时为 0）。
     * 回调内一阶 ramp 平滑到目标，无爆音。
     */
    fun setMasterGain(gain: Float): Int {
        if (handle == 0L || state != State.RUNNING) return -2
        pendingMasterGainBits.set(gain.coerceIn(0f, 1f).toRawBits())
        scheduleMasterGainDrain()
        return 0
    }

    /**
     * 音量滑杆只保留最后一个目标值，不让每个触摸采样都以 FutureTask 同步阻塞调用线程。
     * 真正 JNI 写入仍只发生在 controlThread，保持原生 SPSC 的单生产者约束。
     */
    private fun scheduleMasterGainDrain() {
        if (!masterGainDrainScheduled.compareAndSet(false, true)) return
        controlHandler.post {
            var retry = false
            try {
                while (handle != 0L && state == State.RUNNING) {
                    val targetBits = pendingMasterGainBits.get()
                    if (targetBits == appliedMasterGainBits.get()) break
                    val rc = nativeSetMasterGain(handle, Float.fromBits(targetBits))
                    if (rc != 0) {
                        retry = true
                        break
                    }
                    appliedMasterGainBits.set(targetBits)
                }
            } finally {
                masterGainDrainScheduled.set(false)
                if (handle != 0L && state == State.RUNNING && pendingMasterGainBits.get() != appliedMasterGainBits.get()) {
                    if (retry) controlHandler.postDelayed({ scheduleMasterGainDrain() }, 4L)
                    else scheduleMasterGainDrain()
                }
            }
        }
    }

    /**
     * 从编码音频（MP3/WAV/FLAC，miniaudio 内建解码）解码到设备格式并注册为循环环境声轨。
     * 解码在控制线程完成，原生持有解码后的 PCM（retire-ack 释放）。
     * @return 0 成功；-2 未 init/未运行；-3 解码失败；-4 参数非法；-5 OOM。
     */
    fun addTrackFromEncoded(id: Int, encoded: ByteArray): Int = onControl {
        if (handle == 0L || state != State.RUNNING) return@onControl -2
        if (id < 0 || id >= MAX_TRACKS || encoded.isEmpty()) return@onControl -4
        nativeAddTrackFromEncoded(handle, id, encoded, deviceChannels, deviceSampleRate)
    }

    // ---------------- 阶段 3：采样器 ----------------

    /**
     * 注册预解码 one-shot 采样。pcm 为解码后的交错 float PCM（源格式），控制线程统一到设备格式。
     * @param sourceSampleRate 源采样率（供重采样）。
     * @param sourceChannels 源声道数（1/2）。
     * @param baseFrequency 基频（供阶段 C 音高整形）。
     * @return 采样槽下标（>=0）用于 [triggerSample]/[removeSample]；<0 失败（-2 未 init/未运行，
     *   -4 参数非法，-5 OOM，-1 队列满，-6 无空闲槽）。
     */
    fun addSample(pcm: FloatArray, sourceSampleRate: Int, sourceChannels: Int, baseFrequency: Float = 440f): Int = onControl {
        if (handle == 0L || state != State.RUNNING) return@onControl -2
        if (pcm.isEmpty() || sourceSampleRate <= 0 || sourceChannels !in 1..2) return@onControl -4
        val idx = sampleSlots.indexOfFirst { !it }
        if (idx < 0) return@onControl -6
        val device = PcmFormat.toDeviceFormat(pcm, sourceChannels, sourceSampleRate, deviceChannels, deviceSampleRate)
        val rc = nativeAddSample(handle, idx, device, device.size.toLong() / deviceChannels, deviceChannels, deviceSampleRate, baseFrequency)
        if (rc == 0) sampleSlots[idx] = true else sampleSlots[idx] = false
        if (rc == 0) idx else rc
    }

    /** 移除采样槽。活跃 one-shot 实例在回调内一并停用，旧 PCM 由 retire-ack 安全释放。 */
    fun removeSample(index: Int): Int = onControl {
        if (handle == 0L) return@onControl -2
        if (index < 0 || index >= MAX_SAMPLES) return@onControl -4
        val rc = nativeRemoveSample(handle, index)
        if (rc == 0) sampleSlots[index] = false
        rc
    }

    /**
     * 触发采样 one-shot（正式采样器，阶段 C）。frequency 决定音高（frequency/baseFrequency），
     * 原生侧做分数播放位置 + 线性插值 + ADSR 包络 + 低通滤波 + gain/pan + voice stealing。
     * @return 0 成功；-1 队列满；-2 未 init/FAILED；-4 参数非法或资源缺失（不静默变正弦）。
     */
    fun triggerSample(
        index: Int,
        frequency: Float,
        gain: Float = 1f,
        pan: Float = 0f,
        attackSeconds: Float = 0.01f,
        releaseSeconds: Float = 0.05f,
        cutoffHz: Float = 0f,
    ): Int = onControl {
        if (handle == 0L) return@onControl -2
        if (index < 0 || index >= MAX_SAMPLES) return@onControl -4
        nativeTriggerSample(handle, index, frequency, gain, pan, attackSeconds, releaseSeconds, cutoffHz)
    }

    // ---------------- 阶段 4：生成 voice 骨架 ----------------

    /**
     * 投递一个合成 voice（阶段 4 骨架：正弦，固定 gain/pan，播满 [durationFrames] 即停）。
     * 注意：仅骨架——18 音色、调度器、包络、MasterChain 尚未迁移。
     */
    fun addVoice(frequency: Float, gain: Float = 1f, pan: Float = 0f, durationFrames: Long = 44100): Int = onControl {
        if (handle == 0L) return@onControl -2
        nativeAddVoice(handle, frequency, gain, pan, durationFrames)
    }

    // ---------------- 阶段 D：生成电台黄金调度（native 迁移） ----------------

    /**
     * 黄金调度（纯确定性，不需 handle/设备）：返回 presetId 前 ticks 个 tick 的逗号分隔 "tick:layerId"。
     * 供真机/集成测试与 Kotlin 的 NativeGeneratedAudioRenderer.schedulerGolden 逐字符比对。
     */
    fun schedulerGolden(presetId: String, ticks: Int): String = nativeSchedulerGolden(presetId, ticks)

    /** 渲染一个合成音色声部（native 迁移，对照 Kotlin pcmProbeForTimbre；仅设备可验证听感）。 */
    fun renderSynthVoice(timbre: String, frequency: Double, durationSeconds: Double, attackSeconds: Double, releaseSeconds: Double, cutoffHz: Double, room: Double, gain: Double, seed: Int, outputFrames: Int): FloatArray =
        nativeRenderSynthVoice(timbre, frequency, durationSeconds, attackSeconds, releaseSeconds, cutoffHz, room, gain, seed, outputFrames)

    /** 渲染一个完整预设到交织立体声 float PCM（合成路径，对照 Kotlin renderPresetPcm；仅设备可验证）。 */
    fun renderPreset(presetId: String, frames: Int, outputVolume: Double = 0.64, gain: Int = 72): FloatArray =
        nativeRenderPreset(presetId, frames, outputVolume, gain)

    /**
     * 注册一个 VSCO 采样（供 native 采样路径就近选样；渲染前调用）。
     * 契约：`frames` 为「帧数」= pcm.size / channels（pcm 为 16-bit 交错 PCM，与 Kotlin decodeWav 的 frames.size/channels 一致）。
     */
    fun registerSample(timbre: String, baseFrequency: Double, sampleRate: Double, pcm: ShortArray, frames: Int, channels: Int): Int =
        nativeRegisterSample(timbre, baseFrequency, sampleRate, pcm, frames, channels)

    /** 清空采样注册表。 */
    fun clearSamples() = nativeClearSamples()

    /** 创建增量实时渲染引擎（对照 renderLoop），返回句柄（0 = 失败）。 */
    fun createGenerativeEngine(presetId: String, outputVolume: Double = 0.64, gain: Int = 72): Long =
        nativeCreateGenerativeEngine(presetId, outputVolume, gain)

    /** 渲染 frameCount 帧（增量续播），返回交织立体声 float PCM。 */
    fun renderGenerative(handle: Long, frameCount: Int): FloatArray =
        nativeRenderGenerative(handle, frameCount)

    /** 销毁引擎。 */
    fun destroyGenerativeEngine(handle: Long) = nativeDestroyGenerativeEngine(handle)

    /**
     * 原生编排层（与 C GenLayer 一一对应；枚举字段用 native 的 int 序号，见各注释）。
     * 由 GeneratedLayer 映射得到（对照 NativeGeneratedAudioRenderer 的 toNative()）。
     */
    data class NativeGenerativeLayer(
        val id: String,
        val kind: Int,             // 0=notes 1=rhythm 2=atmosphere 3=voice 4=ambient
        val timbre: String,
        val volume: Int,
        val probability: Int,
        val density: Int,
        val duration: Double,
        val attack: Double,
        val release: Double,
        val pan: Double,
        val entryDelay: Double,
        val rhythm: Int,
        val relationship: Int,     // 0=independent 1=follow 2=avoid 3=alternate
        val target: String?,
        val register: Int,         // 0=low 1=middle 2=high 3=wide
        val scale: Int,            // 0=pentatonic 1=major 2=minor 3=dorian
        val ambientResponse: Int,
        val enabled: Boolean,
        val solo: Boolean,
        val intro: Boolean = false,
    )

    /** 更新引擎 settings（tempo/density/…），支持速度滑杆等自定义设置；gain 变化同步 master 音量。 */
    fun setGenerativeSettings(handle: Long, tempo: Int, density: Int, variation: Int, entryDelay: Double, eventDuration: Double, gain: Int, ambientResponse: Int, adaptive: Boolean): Int =
        nativeSetGenerativeSettings(handle, tempo, density, variation, entryDelay, eventDuration, gain, ambientResponse, if (adaptive) 1 else 0)

    /** 覆盖引擎 scenes（ids 元数据 + durations 分钟；energy/density/brightness/space 为 0-100 原始值）；空数组清空场景。 */
    fun setGenerativeScenes(handle: Long, ids: Array<String>, durations: IntArray, energy: DoubleArray, density: DoubleArray, brightness: DoubleArray, space: DoubleArray): Int =
        nativeSetGenerativeScenes(handle, ids, durations, energy, density, brightness, space)

    /**
     * 整体替换引擎编排（对照 play() 里 generatorArrangement 的 seed/sectionMinutes/arc/layers），全重启语义：
     * 重建层 + 重置 RNG/调度/声部/prev 快照。scenes 另用 [setGenerativeScenes] 设置。arc：0=breathing 1=steady 2=journey。
     */
    fun setGenerativeArrangement(handle: Long, seed: String, sectionMinutes: Int, arc: Int, layers: List<NativeGenerativeLayer>, reset: Boolean = true): Int =
        nativeSetGenerativeArrangement(
            handle, seed, sectionMinutes, arc,
            layers.map { it.id }.toTypedArray(),
            layers.map { it.kind }.toIntArray(),
            layers.map { it.timbre }.toTypedArray(),
            layers.map { it.volume }.toIntArray(),
            layers.map { it.probability }.toIntArray(),
            layers.map { it.density }.toIntArray(),
            layers.map { it.duration }.toDoubleArray(),
            layers.map { it.attack }.toDoubleArray(),
            layers.map { it.release }.toDoubleArray(),
            layers.map { it.pan }.toDoubleArray(),
            layers.map { it.entryDelay }.toDoubleArray(),
            layers.map { it.rhythm }.toIntArray(),
            layers.map { it.relationship }.toIntArray(),
            layers.map { it.target }.toTypedArray(),
            layers.map { it.register }.toIntArray(),
            layers.map { it.scale }.toIntArray(),
            layers.map { it.ambientResponse }.toIntArray(),
            layers.map { if (it.enabled) 1 else 0 }.toIntArray(),
            layers.map { if (it.solo) 1 else 0 }.toIntArray(),
            layers.map { if (it.intro) 1 else 0 }.toIntArray(),
            reset,
        )

    /** 更新单个层实时参数（音量/声像/启用/独奏），下一块渲染时对存量声部做 ramp。 */
    fun updateGenerativeLayer(handle: Long, layerIndex: Int, volume: Int, pan: Double, enabled: Boolean, solo: Boolean): Int =
        nativeUpdateGenerativeLayer(handle, layerIndex, volume, pan, if (enabled) 1 else 0, if (solo) 1 else 0)

    /** 更新引擎输出音量（对照 setVolume，0..1），音量变化由渲染循环 30ms ramp 平滑。 */
    fun setGenerativeOutputVolume(handle: Long, outputVolume: Double): Int =
        nativeSetGenerativeOutputVolume(handle, outputVolume)

    /** 更新引擎环境偏置（对照 AmbientBias.from(activeAmbient)），作用于 eventChance/scheduleLayer。 */
    fun setGenerativeAmbientBias(handle: Long, level: Double, water: Double, air: Double, warm: Double, city: Double, noise: Double): Int =
        nativeSetGenerativeAmbientBias(handle, level, water, air, warm, city, noise)

    /** 解码 WAV 并注册为 VSCO 采样（对照 preloadSamples + decodeWav）；baseFrequency 为基频（供就近选样）。 */
    fun registerWavSample(timbre: String, baseFrequency: Double, wavBytes: ByteArray): Int {
        val sample = decodeWav(wavBytes)
        return registerSample(timbre, baseFrequency, sample.sampleRate.toDouble(), sample.frames, sample.frames.size / sample.channels, sample.channels)
    }

    /** 渲染一个音色的试听声部（对照 previewTimbre，采样/合成路径 + 主链路），返回 mono float PCM。 */
    fun renderPreviewVoice(timbre: String, frequency: Double, durationSeconds: Double, attackSeconds: Double, releaseSeconds: Double, pan: Double, cutoffHz: Double, brightness: Double, room: Double, gain: Double, seed: Int, outputFrames: Int): FloatArray =
        nativeRenderPreviewVoice(timbre, frequency, durationSeconds, attackSeconds, releaseSeconds, pan, cutoffHz, brightness, room, gain, seed, outputFrames)

    // ---------------- 常量（与 soundist_audio.c 保持一致） ----------------

    const val MAX_TRACKS = 64
    const val MAX_SAMPLES = 128
    const val CMD_CAPACITY = 256

    /** 命令类型，与 soundist_audio.c 的 SoundistCommandType 保持一致（诊断用）。 */
    object Command {
        const val NONE = 0
        const val SET_GAIN = 1
        const val SET_PAN = 2
        const val SET_PLAYING = 3
        const val ADD_TRACK = 4
        const val REMOVE_TRACK = 5
        const val TRIGGER_SAMPLE = 6
        const val REMOVE_SAMPLE = 7
        const val TRIGGER_VOICE = 8
        const val ADD_SAMPLE = 9
    }
}
