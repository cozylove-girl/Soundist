package com.soundist.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.soundist.core.audio.ExternalPlaybackController
import com.soundist.feature.listening.GeneratedAudioRenderer
import com.soundist.feature.listening.PlaybackState
import com.soundist.feature.listening.RadioEngineState
import com.soundist.feature.listening.RadioStation
import com.soundist.feature.listening.AmbientSound
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.log
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val SAMPLE_RATE = 44_100
private const val BLOCK_FRAMES = 512
private const val WRITE_INTERRUPTED = Int.MIN_VALUE
private const val TAG = "SoundistGeneratedRadio"

/**
 * 真实可听 PCM 峰值门槛（约 -60 dBFS）。任何已创建 Voice 的指数包络底噪约 3~9，低于此值；真声部峰值 100+。
 * 仅用于「已出声（AUDIBLE）」的辅助判定，不再决定 PLAYING（PLAYING = 音频时钟已启动）。
 */
private const val AUDIBLE_FLOOR = 32

/** 采样 PCM 缓存内存上限（约 32 MiB），超出按 LRU 淘汰最旧条目。 */
private const val SAMPLE_CACHE_MAX_BYTES = 32L * 1024 * 1024

/** 音色试听尾部排空超时（毫秒）：写循环已按实时节流，尾部只剩约一个缓冲块，超时即释放，避免永久等待。 */
private const val AUDITION_DRAIN_TIMEOUT_MS = 1_000L

/** 音量 ramp 时间常数（秒）：增益变化在 MasterChain 内以约 30ms 平滑逼近目标，避免 click，也替代旧 AudioTrack.volume 淡入。 */
private const val VOLUME_RAMP_TAU_SECONDS = 0.03

/** 删除/禁用编排层时，其存量声部的可控 release 时长（秒）：在约 150ms 内淡出，而不是等原 duration 自然结束。 */
private const val LAYER_RELEASE_SECONDS = 0.15

/**
 * Offline generated-radio renderer. Faithful port of prototypes/mobile-interactive/src/app/generativeRadio.ts:
 * master Gain → DynamicsCompressor → Limiter chain, sectionState/applyTimelineScene/chordShift,
 * PROFILE_PULSE_PLANS (notes/rhythm/atmosphere/voice/signal), and per-timbre DSP (sonar-ping,
 * electric-piano, glass-bell, harp-pluck, bamboo-flute, warm-strings, vowel-choir, air-pad,
 * deep-drone, muted-pulse, rail-click). Real VSCO 2 CE samples are played for sample timbres;
 * synthetic timbres are generated as local PCM. API 26+.
 */
class NativeGeneratedAudioRenderer(context: Context) : GeneratedPlaybackController, Closeable {
    private val assets = context.applicationContext.assets
    private val lock = Any()
    private val running = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private val renderGeneration = AtomicLong(0L)
    /** Invalidates an in-flight blocking write even if pause is followed by an immediate resume. */
    private val pauseEpoch = AtomicLong(0L)
    private var worker: Thread? = null
    private var track: AudioTrack? = null
    /** 单音色试听（previewTimbre）使用的静态 AudioTrack；开始新电台/停止时必须一并释放，避免堆叠音频实例。 */
    @Volatile private var auditionTrack: AudioTrack? = null

    /** 生成电台真实播放状态回流：PLAYING = 音频时钟已启动；AUDIBLE = 已产生可感知声音（辅助，不决定 PLAYING）。 */
    private val _events = MutableStateFlow(RadioEngineState())
    override val radioEvents: Flow<RadioEngineState> = _events.asStateFlow()
    @Volatile private var currentStationId: String? = null

    private fun emitState(state: PlaybackState, message: String? = null) {
        currentStationId?.let { _events.value = RadioEngineState(state, it, 0, message) }
    }

    /** generativeRadio.ts outputVolume = masterVolume/100*radioVolume/100（App.tsx 3450）。前端默认 0.8×0.8=0.64。 */
    @Volatile
    private var outputVolume = 0.64

    /** 供宿主在运行时更新生成电台音量（master gain = outputVolume * gain / 100，已取消 0.66 统一衰减）。 */
    override fun setVolume(value: Float) {
        outputVolume = clamp(value.toDouble(), 0.0, 1.0)
    }

    /**
     * App.tsx previewGeneratedTimbre — 单音色试听（阶段 F：流式输出）。
     * 用 MODE_STREAM 小缓冲、边渲染边写：play() 后立即写首块，点击到出声由 AudioTrack 缓冲延迟
     * （约几十 ms，缓存命中时 <100ms）决定，不再离线生成完整 5 秒 PCM 后才开始播放。
     */
    override suspend fun previewTimbre(timbre: String) {
        val frequency = if (timbre == "vsco-cello") 130.81 else 261.63
        val noteDuration = if (timbre == "vsco-harp") 2.2 else 3.6
        val profile = Profile(261.63, intArrayOf(0), 0.4, 1800, 0.5)
        // 试听也异步预解码该音色的采样（渲染循环内不再解码，只读缓存）。
        withContext(Dispatchers.Default) { preloadSamples(SAMPLE_TIMBRES[timbre].orEmpty().map { it.path }) }
        val samples: (String, Double) -> SampleHit? = { t, f -> sampleFor(t, f) }
        // 试听链需要在手机扬声器上也能可靠辨认；真实钢琴素材本身较安静，音色级 trim
        // 仍由 createVoice 统一处理，这里只把试听基准从正式编排的背景电平提高到可比较电平。
        val voice = createVoice(timbre, frequency, 0.0, noteDuration, 0.10, 0.12, 2.4, 0.0, 1800.0, profile, Mulberry32(0x5eed), samples)
        val totalFrames = (5.0 * SAMPLE_RATE).toInt()
        val master = MasterChain(0.8, 72)
        // 流式 AudioTrack：缓冲取 minBuffer 与一个块的较大者，避免过大缓冲抬高首块出声延迟。
        val audio = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setBufferSizeInBytes(max(AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT), BLOCK_FRAMES * 4))
            .setTransferMode(AudioTrack.MODE_STREAM).build()
        // 新试听拥有唯一播放权。显式停止旧 AudioTrack，而不是只依赖旧协程稍后观察到取消；
        // WRITE_BLOCKING 正在等待时，这也会立即唤醒旧写入并阻止两个音色重叠。
        synchronized(lock) {
            auditionTrack?.let { previous ->
                runCatching { previous.pause() }
                runCatching { previous.flush() }
                runCatching { previous.stop() }
                runCatching { previous.release() }
            }
            auditionTrack = audio
        }
        // 记录实际成功写入的 stereo 帧数（正确处理 write 的负值/零写入/短写），尾部只等这些帧播放完，
        // 不等永远到不了的 totalFrames（写循环可能因错误/短写提前结束）。
        var queuedFrames = 0L
        try {
            withContext(Dispatchers.Default) {
                audio.play()
                val pcm = ShortArray(BLOCK_FRAMES * 2)
                var frame = 0
                // 渲染/写循环内检查协程取消：快速连点新音色时，旧试听在一个缓冲块内停止（不等 5 秒渲染完）。
                while (frame < totalFrames && isActive) {
                    val n = minOf(BLOCK_FRAMES, totalFrames - frame)
                    for (i in 0 until n) {
                        val at = frame + i
                        val v = if (voice.finished(at.toLong())) 0.0 else voice.sample(at.toLong())
                        val out = master.process(v * sqrt(0.5), v * sqrt(0.5))
                        pcm[i * 2] = out.first.toPcm(); pcm[i * 2 + 1] = out.second.toPcm()
                    }
                    // write 返回 short 数（负数=错误码，0=未写入，小于 n*2=短写）。
                    val written = audio.write(pcm, 0, n * 2, AudioTrack.WRITE_BLOCKING)
                    if (written < 0) break
                    queuedFrames += (written / 2).toLong()
                    frame += n
                    if (written < n * 2) break
                }
            }
            // 等尾部排空：只等实际写出的 queuedFrames 播放完，带 playState 检查 + 上限超时，不永久等待。
            // 取消（新点击打断）会抛 CancellationException 进入 finally 释放。
            val deadline = System.currentTimeMillis() + AUDITION_DRAIN_TIMEOUT_MS
            while (coroutineContext.isActive &&
                audio.playState == AudioTrack.PLAYSTATE_PLAYING &&
                audio.playbackHeadPosition < queuedFrames &&
                System.currentTimeMillis() < deadline
            ) {
                delay(20)
            }
        } finally {
            // 协程被取消（宿主销毁/页面退出/新点击打断）时也必须释放 AudioTrack，避免声道泄漏。
            // stopLocked 可能已抢先 stop/release（开始电台时），双保险：release 也容错，避免重复释放崩溃。
            synchronized(lock) {
                if (auditionTrack === audio) auditionTrack = null
            }
            runCatching { audio.stop() }
            runCatching { audio.release() }
        }
    }

    override suspend fun play(station: RadioStation, activeAmbient: List<AmbientSound>) {
        val preset = station.generatorPresetId ?: station.id
        val trimDb = presetTrimDb[preset] ?: 0.0
        // 把 settings（含 tempo）提到编排构建前，使 Arrangement.tempo 反映用户速度设置（此前硬编码 54、速度滑杆无效）。
        val settings = station.generatorSettings?.let { Settings(it.tempo, it.density, it.variation, it.entryDelaySeconds.toDouble(), it.eventDurationSeconds.toDouble(), it.gain, it.ambientResponse, it.adaptive) } ?: Settings()
        val base = ARRANGEMENTS[preset] ?: error("未知持续声场：${station.id}")
        val arrangement = station.generatorArrangement?.let { model ->
            val mappedLayers = model.layers.map { draft ->
                draft.toNative().copy(intro = base.layers.firstOrNull { it.id == draft.id }?.intro == true)
            }
            // DIY 数据模型不持有运行时 intro 标记。保留官方首发层；若用户已删除该层，
            // 选择第一个启用的音乐层承担克制的首次发声，避免试听草稿在数秒入场延迟内完全静默。
            val layersWithIntro = if (mappedLayers.any { it.enabled && it.kind != Kind.AMBIENT && it.intro }) {
                mappedLayers
            } else {
                val firstPlayable = mappedLayers.indexOfFirst { it.enabled && it.kind != Kind.AMBIENT }
                mappedLayers.mapIndexed { index, layer -> if (index == firstPlayable) layer.copy(intro = true) else layer }
            }
            base.copy(
                tempo = settings.tempo,
                seed = model.seed,
                sectionMinutes = model.sectionMinutes,
                arc = runCatching { Arc.valueOf(model.arc.uppercase()) }.getOrDefault(base.arc),
                layers = layersWithIntro,
                scenes = model.scenes.map { Scene(it.id, it.durationMinutes, it.energy.toDouble(), it.density.toDouble(), it.brightness.toDouble(), it.space.toDouble()) },
            )
        } ?: base.copy(tempo = settings.tempo)
        // 首帧加载（阶段 2d）：WAV 采样解码在实时渲染线程外异步预解码（渲染线程只从缓存读）。
        // 先于锁外完成，避免在 synchronized 临界区里挂起。
        val preloadPaths = arrangement.layers.mapNotNull { SAMPLE_TIMBRES[it.timbre] }.flatten().map { it.path }.distinct()
        withContext(Dispatchers.Default) { preloadSamples(preloadPaths) }
        synchronized(lock) {
            val bias = if (settings.adaptive) AmbientBias.from(activeAmbient) else AmbientBias()
            // 空 DIY：没有任何启用且可调度的音乐层（AMBIENT 层从不被调度）→ 不启动 AudioTrack，返回明确错误，
            // 不永久停在 LOADING、不假出声。DIY 删除全部音乐层即真正静音（无隐藏 anchor）。
            if (arrangement.layers.none { it.enabled && it.kind != Kind.AMBIENT }) {
                stopLocked()
                currentStationId = station.id
                emitState(PlaybackState.ERROR, "当前编排没有任何可播放的音乐层，请先添加或启用编排层")
                return
            }
            // 阶段 F「试听当前编排」热更新：同一频道仍在播放且 seed 未变 → 原子替换编排/设置/环境偏置，
            // 不 stopLocked、不重建 AudioTrack/线程、不重新经历入场延迟；新轨道随 attack 淡入、被删轨道随 release 自然淡出。
            if (running.get() && currentStationId == station.id && currentArrangement?.seed == arrangement.seed) {
                currentArrangement = arrangement
                arrangementSettings = settings
                ambientBias = bias
                emitState(PlaybackState.PLAYING)
                return
            }
            stopLocked()
            arrangementSettings = settings
            ambientBias = bias
            currentStationId = station.id
            currentArrangement = arrangement
            running.set(true); paused.set(false)
            val generation = renderGeneration.incrementAndGet()
            val audio = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setBufferSizeInBytes(max(AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT), BLOCK_FRAMES * 8))
                .setTransferMode(AudioTrack.MODE_STREAM).build()
            track = audio
            worker = Thread({ renderLoop(audio, arrangement, trimDb, generation) }, "SoundistGeneratedRadio").also { it.priority = Thread.MAX_PRIORITY; it.start() }
        }
    }

    override suspend fun pause(): Unit = synchronized(lock) {
        pauseExternal()
    }

    override suspend fun stop(): Unit = synchronized(lock) {
        emitState(PlaybackState.IDLE)
        stopLocked()
    }
    override fun close() = synchronized(lock) {
        emitState(PlaybackState.IDLE)
        stopLocked()
    }

    override fun resumeExternal() = synchronized(lock) {
        if (running.get() && paused.get()) {
            track?.play()
            paused.set(false)
            // 焦点恢复续播：必须回流 PLAYING，否则 UI 停留在「已暂停」而音频实际已恢复。
            emitState(PlaybackState.PLAYING)
        }
    }

    override fun pauseExternal() = synchronized(lock) {
        if (running.get()) {
            paused.set(true)
            pauseEpoch.incrementAndGet()
            track?.pause()
            emitState(PlaybackState.PAUSED)
        }
    }

    override fun stopExternal() = synchronized(lock) {
        emitState(PlaybackState.IDLE)
        stopLocked()
    }

    private fun stopLocked() {
        renderGeneration.incrementAndGet()
        running.set(false); paused.set(false); currentArrangement = null
        track?.pause(); track?.flush()
        worker?.interrupt(); worker?.takeUnless { it === Thread.currentThread() }?.join(350)
        worker = null
        track?.release(); track = null
        // 正在进行的单音色试听（静态 AudioTrack）也一并停止，避免与电台双声道同时出声。
        auditionTrack?.let { audition -> runCatching { audition.stop() }; audition.release() }
        auditionTrack = null
    }

    private fun renderLoop(audio: AudioTrack, initialArrangement: Arrangement, trimDb: Double, generation: Long) {
        val random = Mulberry32(fnv1a(initialArrangement.seed))
        val scheduler = SchedulerMemory()
        val voices = ArrayList<Voice>()
        val master = MasterChain(outputVolume, arrangementSettings.gain, trimDb)
        var frame = 0L
        var tick = 0
        // 调度游标（绝对音频时间）：正常情况下等价 tick * sixteenth；tempo 热更新时步长变化，
        // 游标从当前绝对时间继续，天然重新锚定、不产生事件突发或长时间静默。
        var scheduleTime = 0.0
        // 上一块的层音量/声像表：热更新改参数时检测变化，对存量声部做 gain/pan ramp。
        var prevLayerVolumes = emptyMap<String, Int>()
        var prevLayerPans = emptyMap<String, Double>()
        // 上一块「当前生效场景」的明亮度：热更新改明亮度时，只对当前场景的存量声部做滤波 ramp。
        var prevCurrentSceneBrightness = 0.0
        var audibleEmitted = false
        // 单一增益表达点：MasterChain 内的 currentVolume 从 0 平滑 ramp 到目标 outputVolume（约 30ms），
        // 同时覆盖开场淡入与所有增益变化，不再用 AudioTrack.volume（已删除 fadeIn/fadePause）。
        var currentVolume = 0.0
        try {
            if (!isRenderActive(generation)) return
            audio.play()
            // PLAYING = 音频时钟已启动（AudioTrack 开始播放、调度时钟在跑），不再看 PCM 峰值。
            if (isRenderActive(generation)) emitState(PlaybackState.PLAYING)
            while (isRenderActive(generation)) {
                if (paused.get()) { Thread.sleep(20); continue }
                // 阶段 F 热更新：每 tick 读取最新编排（play() 热更新时原子替换 currentArrangement），不重建 AudioTrack/线程。
                val arrangement = currentArrangement ?: initialArrangement
                // 阶段五参数 ramp：层音量/声像在热更新中改变时，对存量声部按比例 ramp（约 30ms），不重启 AudioTrack。
                val currLayerVolumes = arrangement.layers.associate { it.id to it.volume }
                val currLayerPans = arrangement.layers.associate { it.id to it.pan }
                for ((layerId, newVolume) in currLayerVolumes) {
                    val oldVolume = prevLayerVolumes[layerId] ?: continue
                    if (oldVolume != newVolume) {
                        // 用声部自己的「创建时音量」作基准算绝对目标，避免全局首次音量基准对热更新后新建声部失真。
                        voices.forEach { if (it.layerId == layerId && it.baseLayerVolume > 0) it.setGainScale(newVolume.toDouble() / it.baseLayerVolume, frame) }
                    }
                }
                for ((layerId, newPan) in currLayerPans) {
                    val oldPan = prevLayerPans[layerId] ?: continue
                    if (oldPan != newPan) {
                        voices.forEach { if (it.layerId == layerId) it.setPanTarget(newPan, frame) }
                    }
                }
                // 场景明亮度（滤波）变化 → 只对「当前生效场景」的存量声部 ramp cutoff（与 applyTimelineScene 同源），
                // 不再遍历所有场景、不按多个场景比例连续累积；新 ramp 从调用时当前插值开始（见 setCutoffTarget）。
                val currBrightness = currentSceneBrightness(arrangement, frame.toDouble() / SAMPLE_RATE)
                if (prevCurrentSceneBrightness > 0.0 && currBrightness > 0.0 && prevCurrentSceneBrightness != currBrightness) {
                    val ratio = currBrightness / prevCurrentSceneBrightness
                    voices.forEach { it.setCutoffTarget(it.cutoff * ratio, frame) }
                }
                prevCurrentSceneBrightness = currBrightness
                prevLayerVolumes = currLayerVolumes
                prevLayerPans = currLayerPans
                // 20-100ms click-free 音量 ramp：每个块向目标 outputVolume 逼近一个时间常数（约 30ms）。
                currentVolume += (outputVolume - currentVolume) * (1.0 - exp(-(BLOCK_FRAMES.toDouble() / SAMPLE_RATE) / VOLUME_RAMP_TAU_SECONDS))
                master.setVolume(currentVolume, arrangementSettings.gain)
                val seconds = frame.toDouble() / SAMPLE_RATE
                val sixteenth = 60.0 / arrangement.tempo / 4.0
                while (scheduleTime <= seconds + BLOCK_FRAMES.toDouble() / SAMPLE_RATE) {
                    val at = scheduleTime
                    val state = sectionState(arrangement, at)
                    val bias = if (arrangementSettings.adaptive) ambientBias else AmbientBias()
                    val playable = if (arrangement.layers.any { it.solo }) arrangement.layers.filter { it.solo } else arrangement.layers
                    playable.forEach { layer ->
                        val timbre = layer.timbre
                        if (shouldScheduleAtTick(layer, timbre, tick, arrangement.plan)) {
                            voices += scheduleLayer(layer, timbre, tick, at, state, bias, random, arrangementSettings, scheduler, arrangement.profile, ::sampleFor)
                        }
                    }
                    tick++
                    scheduleTime += sixteenth
                }
                // 阶段五热更新：删除/禁用层的存量声部进入可控 release（其 layerId 不在当前启用音乐层集合里），
                // 而不是继续按其原 duration 播完。
                val activeLayerIds = arrangement.layers.filter { it.enabled && it.kind != Kind.AMBIENT }.map { it.id }.toSet()
                val pcm = ShortArray(BLOCK_FRAMES * 2)
                repeat(BLOCK_FRAMES) { i ->
                    var left = 0.0; var right = 0.0
                    val atFrame = frame + i
                    val iterator = voices.iterator()
                    while (iterator.hasNext()) {
                        val voice = iterator.next()
                        if (voice.layerId.isNotEmpty() && voice.layerId !in activeLayerIds) voice.beginRelease(atFrame)
                        if (voice.finished(atFrame)) iterator.remove()
                        else {
                            val value = voice.sample(atFrame)
                            val pan = voice.currentPan(atFrame)
                            left += value * sqrt((1.0 - pan) * .5)
                            right += value * sqrt((1.0 + pan) * .5)
                        }
                    }
                    val processed = master.process(left, right)
                    pcm[i * 2] = processed.first.toPcm(); pcm[i * 2 + 1] = processed.second.toPcm()
                }
                // ③ AudioTrack.write() 返回值必须检查：负值错误码或短写 → 真实 ERROR，不得继续乐观 PLAYING。
                val written = writeFully(audio, pcm, generation)
                if (written == WRITE_INTERRUPTED) continue
                if (written < 0) {
                    if (isRenderActive(generation)) { running.set(false); emitState(PlaybackState.ERROR, "AudioTrack 写入失败（code=$written）") }
                    break
                }
                // AUDIBLE（已出声）是辅助状态：检测到真实可听 PCM（峰值 ≥ AUDIBLE_FLOOR）才发一次。
                // 它不决定 PLAYING（PLAYING 已按音频时钟启动发出）；锚点层保证首段 ~100-300ms 即可听到。
                if (!audibleEmitted) {
                    var blockPeak = 0.0
                    for (sample in pcm) { val a = abs(sample.toInt()); if (a > blockPeak) blockPeak = a.toDouble() }
                    if (blockPeak >= AUDIBLE_FLOOR) { audibleEmitted = true; emitState(PlaybackState.AUDIBLE) }
                }
                frame += BLOCK_FRAMES
            }
        } catch (_: InterruptedException) { } catch (error: Throwable) {
            // AudioTrack 写失败/采样异常：真实错误回流到 ViewModel，UI 不得继续显示「正在播放」。
            if (isRenderActive(generation)) emitState(PlaybackState.ERROR, error.message ?: "生成电台渲染失败")
        } finally { runCatching { audio.stop() } }
    }

    /** Blocking 写入仍可能正数短写；补齐本块，避免长播时把正常背压误报为失败。 */
    private fun writeFully(audio: AudioTrack, pcm: ShortArray, generation: Long): Int {
        var offset = 0
        var emptyWrites = 0
        while (offset < pcm.size && isRenderActive(generation)) {
            if (paused.get()) return WRITE_INTERRUPTED
            val epochBeforeWrite = pauseEpoch.get()
            val written = audio.write(pcm, offset, pcm.size - offset, AudioTrack.WRITE_BLOCKING)
            if (pauseEpoch.get() != epochBeforeWrite) return WRITE_INTERRUPTED
            when {
                written > 0 -> { offset += written; emptyWrites = 0 }
                written == 0 && ++emptyWrites <= 8 -> Thread.yield()
                written == 0 -> return AudioTrack.ERROR_INVALID_OPERATION
                else -> return if (paused.get()) WRITE_INTERRUPTED else if (isRenderActive(generation)) written else offset
            }
        }
        return if (paused.get()) WRITE_INTERRUPTED else if (offset == pcm.size || !isRenderActive(generation)) offset else AudioTrack.ERROR_INVALID_OPERATION
    }

    private fun isRenderActive(generation: Long): Boolean = running.get() && renderGeneration.get() == generation

    @Volatile private var arrangementSettings = Settings()
    @Volatile private var ambientBias = AmbientBias()
    /** 当前正在播放的编排（阶段 F「试听当前编排」热更新）：renderLoop 每 tick 读取，play() 热更新时原子替换。 */
    @Volatile private var currentArrangement: Arrangement? = null

    /**
     * 频道级响度校准 trim（单位 dB，正数提升、负数压低）——从版本化 manifest 读取（任务七）：
     * `assets/loudness_manifest.json` 由离线测量工具（libebur128/FFmpeg，当前为自写近似）生成，
     * 运行时只读取审核后的 trim，不实时执行昂贵测量。manifest 缺失/损坏时中性为 0。
     */
    private val presetTrimDb: Map<String, Double> by lazy {
        runCatching {
            val text = assets.open("loudness_manifest.json").bufferedReader().use { it.readText() }
            val presets = org.json.JSONObject(text).getJSONObject("presets")
            presets.keys().asSequence().associateWith { id ->
                presets.getJSONObject(id).optDouble("trimDb", 0.0)
            }
        }.getOrElse { error ->
            // 解析失败不是静默降级：明确告警，避免 trimDb 悄悄全部失效为 0 而不自知。
            Log.e(TAG, "响度 manifest 解析失败，所有 trimDb 失效为 0：${error.message}", error)
            emptyMap()
        }
    }

    /** 已解码采样缓存条目：Ready=解码成功；Failed=解码失败（原因），不存 null（ConcurrentHashMap 禁止 null）。 */
    private sealed interface SampleCacheEntry {
        data class Ready(val sample: PcmSample) : SampleCacheEntry
        data class Failed(val reason: String) : SampleCacheEntry
    }

    /**
     * 采样缓存：value 是「单飞」的 CompletableFuture —— 同一路径只创建并执行一个解码任务（computeIfAbsent
     * 保证映射函数只对创建者执行一次），其它调用复用其结果。渲染线程用 getNow 无锁读、绝不解码/不等待锁。
     */
    private val sampleCache = ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<SampleCacheEntry>>()

    /** 淘汰队列（插入序 FIFO，最旧在前——诚实命名：这不是访问序 LRU）。只由预解码线程写，渲染线程不碰。 */
    private val sampleFifo = java.util.ArrayDeque<String>()
    /** 已缓存解码 PCM 总字节数（估算，仅用于内存上限淘汰）。 */
    private val sampleCacheBytes = java.util.concurrent.atomic.AtomicLong(0L)
    /** 当前播放编排正在使用的采样路径（pin 住，淘汰时跳过，避免正在用却被淘汰导致重解码）。 */
    private val pinnedPaths = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val sampleEvictLock = Any()

    /** 资源版本失效：清空缓存与淘汰队列。进程内热升级资源时调用；进程重启本身即清空内存缓存，不永久污染。 */
    private fun clearSampleCache() {
        synchronized(sampleEvictLock) {
            sampleCache.clear()
            sampleFifo.clear()
            sampleCacheBytes.set(0L)
            pinnedPaths.clear()
        }
    }

    /**
     * 解码单个采样并缓存（非实时线程调用：play()/previewTimbre 预解码）。失败不静默降级：
     * 用 Log.e 明确输出「原音色 / 失败资源路径 / 错误原因 / 实际替代音色」，且失败只记录一次。
     */
    private fun decodeSample(path: String, timbre: String): PcmSample? {
        sampleCache[path]?.getNow(null)?.let { existing ->
            return (existing as? SampleCacheEntry.Ready)?.sample
        }
        val future = sampleCache.computeIfAbsent(path) {
            java.util.concurrent.CompletableFuture.supplyAsync<SampleCacheEntry>({
                try {
                    val sample = decodeWav(assets.open(path).readBytes())
                    recordSample(path, sample)
                    SampleCacheEntry.Ready(sample)
                } catch (error: Throwable) {
                    SampleCacheEntry.Failed(error.message ?: error.javaClass.simpleName)
                }
            })
        }
        val entry = runCatching { future.get() }.getOrNull() ?: return null
        if (entry is SampleCacheEntry.Failed) {
            val fallback = SAMPLE_FALLBACKS[timbre] ?: timbre
            Log.e(TAG, "WAV 采样解码失败：原音色=$timbre 失败资源=$path 错误原因=${entry.reason} 实际替代音色=$fallback")
        }
        return (entry as? SampleCacheEntry.Ready)?.sample
    }

    /** 记 FIFO + 字节（只由单飞解码任务调用一次，不会重复累计同一路径）。 */
    private fun recordSample(path: String, sample: PcmSample) {
        val bytes = sample.frames.size.toLong() * 2L
        synchronized(sampleEvictLock) {
            sampleFifo.addLast(path)
            sampleCacheBytes.addAndGet(bytes)
            while (sampleCacheBytes.get() > SAMPLE_CACHE_MAX_BYTES) {
                val victim = evictCandidate() ?: break
                val removed = sampleCache.remove(victim)
                if (removed != null && removed.getNow(null) is SampleCacheEntry.Ready) {
                    sampleCacheBytes.addAndGet(-((removed.getNow(null) as SampleCacheEntry.Ready).sample.frames.size.toLong() * 2L))
                }
            }
        }
    }

    /** 从 FIFO 队首找第一个非 pin 的淘汰候选；pin 住的跳过并放回队尾（等价保留）。 */
    private fun evictCandidate(): String? {
        repeat(sampleFifo.size) {
            val head = sampleFifo.removeFirst()
            if (head !in pinnedPaths) return head
            sampleFifo.addLast(head)
        }
        return null
    }

    /** 预解码一批采样路径（渲染线程启动前调用）；已缓存或已失败的路径跳过，并 pin 住当前编排用到的采样。 */
    private fun preloadSamples(paths: Collection<String>) {
        val distinct = paths.distinct()
        pinnedPaths.addAll(distinct)
        distinct.forEach { path ->
            if (sampleCache[path]?.getNow(null) != null) return@forEach
            val timbre = SAMPLE_TIMBRES.entries.firstOrNull { (_, refs) -> refs.any { it.path == path } }?.key ?: "?"
            decodeSample(path, timbre)
        }
    }

    /**
     * 采样命中（渲染线程专用）：只从已解码缓存无锁读（getNow 非阻塞），绝不在实时渲染线程里解码、等待锁或更新淘汰队列。
     * 缓存未命中（预解码未覆盖或仍在解码中）→ 明确告警并退回合成替代。
     */
    private fun sampleFor(timbre: String, frequency: Double): SampleHit? {
        val candidates = SAMPLE_TIMBRES[timbre] ?: return null
        val selected = candidates.minBy { abs(12 * log(frequency / it.frequency, 2.0)) }
        val entry = sampleCache[selected.path]?.getNow(null)
        val sample = (entry as? SampleCacheEntry.Ready)?.sample
        if (sample == null && entry == null) {
            val fallback = SAMPLE_FALLBACKS[timbre] ?: timbre
            Log.w(TAG, "渲染线程采样未预解码：原音色=$timbre 资源=${selected.path} 实际替代音色=$fallback（预解码应在 play() 前完成）")
        }
        return sample?.let { SampleHit(it, selected.frequency) }
    }

    internal fun schedulerSnapshot(stationId: String, ticks: Int): List<String> {
        return schedulerGolden(stationId, ticks)
    }

    companion object {
        /** generativeRadio.ts PROFILES。 */
        private val PROFILES = mapOf(
            "deep-sea" to Profile(55.0, intArrayOf(0, 7, 12, 17, 19), .18, 620, .8),
            "rain-piano" to Profile(110.0, intArrayOf(0, 3, 7, 10, 14), .48, 1750, .56),
            "morning-mist" to Profile(98.0, intArrayOf(0, 4, 7, 11, 14), .62, 2200, .48),
            "endless-focus" to Profile(65.41, intArrayOf(0, 5, 7, 12), .34, 1180, .32),
            "night-train" to Profile(73.42, intArrayOf(0, 3, 7, 10), .28, 920, .42, Noise(.72, 1460.0, 560.0, 4.2)),
            "warm-study" to Profile(82.41, intArrayOf(0, 4, 7, 9, 12), .46, 1580, .38),
            "moon-tide" to Profile(49.0, intArrayOf(0, 5, 7, 12, 17), .14, 480, .9),
            "signal-garden" to Profile(130.81, intArrayOf(0, 2, 7, 9, 14, 16), .74, 2600, .62),
            "bamboo-strings" to Profile(73.42, intArrayOf(0, 2, 5, 7, 9, 12), .5, 1850, .58),
            "custom-lab" to Profile(65.41, intArrayOf(0, 2, 4, 7, 9, 12, 14), .44, 1500, .48),
        )

        /** generativeRadio.ts PROFILE_PULSE_PLANS。 */
        private val PLANS = mapOf(
            "deep-sea" to Plan(64, intArrayOf(18, 50), intArrayOf(0, 32), intArrayOf(0, 32), intArrayOf(24), intArrayOf(20, 52)),
            "rain-piano" to Plan(32, intArrayOf(0, 7, 13, 22, 29), intArrayOf(0, 8, 16, 24), intArrayOf(0, 16), intArrayOf(12), intArrayOf(11, 27)),
            "morning-mist" to Plan(48, intArrayOf(0, 9, 17, 30, 41), intArrayOf(0, 12, 24, 36), intArrayOf(0, 24), intArrayOf(16, 40), intArrayOf(14, 38)),
            "endless-focus" to Plan(16, intArrayOf(0, 8), intArrayOf(0, 4, 8, 12), intArrayOf(0), intArrayOf(8), intArrayOf(6, 14)),
            "night-train" to Plan(16, intArrayOf(6, 14), intArrayOf(0, 2, 4, 6, 8, 10, 12, 14), intArrayOf(0), intArrayOf(8), intArrayOf(6, 14)),
            "warm-study" to Plan(32, intArrayOf(0, 8, 18, 26), intArrayOf(0, 4, 8, 12, 16, 20, 24, 28), intArrayOf(0, 16), intArrayOf(12, 28), intArrayOf(10, 24)),
            "moon-tide" to Plan(64, intArrayOf(14, 46), intArrayOf(0, 32), intArrayOf(0, 32), intArrayOf(24, 56), intArrayOf(16, 48)),
            "signal-garden" to Plan(24, intArrayOf(0, 3, 10, 15, 21), intArrayOf(0, 6, 12, 18), intArrayOf(0, 12), intArrayOf(8, 20), intArrayOf(3, 10, 15, 21)),
            "bamboo-strings" to Plan(48, intArrayOf(0, 11, 25, 37, 44), intArrayOf(0, 12, 24, 36), intArrayOf(0, 24), intArrayOf(18, 42), intArrayOf(13, 35)),
            "custom-lab" to Plan(32, intArrayOf(0, 6, 12, 19, 27), intArrayOf(0, 4, 8, 12, 16, 20, 24, 28), intArrayOf(0, 16), intArrayOf(8, 24), intArrayOf(10, 26)),
        )

        private val ARRANGEMENTS = mapOf(
            "deep-sea" to arrangement("deep-sea", 8, Arc.BREATHING, deepSeaLayers()),
            "rain-piano" to arrangement("rain-piano", 5, Arc.BREATHING, rainPianoLayers()),
            "morning-mist" to arrangement("morning-mist", 5, Arc.BREATHING, morningMistLayers()),
            "endless-focus" to arrangement("endless-focus", 5, Arc.STEADY, endlessFocusLayers()),
            "night-train" to arrangement("night-train", 4, Arc.JOURNEY, nightTrainLayers()),
            "warm-study" to arrangement("warm-study", 5, Arc.STEADY, warmStudyLayers()),
            "moon-tide" to arrangement("moon-tide", 8, Arc.BREATHING, moonTideLayers()),
            "signal-garden" to arrangement("signal-garden", 5, Arc.JOURNEY, signalGardenLayers()),
            "bamboo-strings" to arrangement("bamboo-strings", 5, Arc.BREATHING, bambooLayers()),
            "custom-lab" to arrangement("custom-lab", 5, Arc.BREATHING, customLabLayers()),
        )

        private fun arrangement(preset: String, minutes: Int, arc: Arc, layers: List<Layer>): Arrangement {
            val profiled = PROFILES.getValue(preset)
            return Arrangement("$preset-soundist-02", 54, minutes, arc, profiled, PLANS.getValue(preset), layers)
        }

        internal fun supportedPresetIds(): Set<String> = ARRANGEMENTS.keys

        internal fun schedulerGolden(stationId: String, ticks: Int): List<String> {
            val a = ARRANGEMENTS.getValue(stationId)
            val r = Mulberry32(fnv1a(a.seed))
            val m = SchedulerMemory()
            val out = ArrayList<String>()
            repeat(ticks) { tick ->
                val at = tick * 60.0 / a.tempo / 4
                val s = sectionState(a, at)
                val playable = if (a.layers.any { it.solo }) a.layers.filter { it.solo } else a.layers
                playable.forEach {
                    val timbre = it.timbre
                    if (shouldScheduleAtTick(it, timbre, tick, a.plan)) {
                        if (gatePasses(it, timbre, at, s, AmbientBias(), r, Settings(), m)) out += "$tick:${it.id}"
                    }
                }
            }
            return out
        }

        /** scheduleLayer 的概率/冷却闸门（不含音高/声像随机消费，仅用于 golden 校验）。与 scheduleLayer 同源 eventChance。 */
        private fun gatePasses(
            layer: Layer, timbre: String, at: Double, state: Section, bias: AmbientBias,
            random: Mulberry32, settings: Settings, memory: SchedulerMemory,
        ): Boolean {
            val lastEvent = memory.last[layer.id] ?: Double.NEGATIVE_INFINITY
            val introFirstFire = layer.intro && lastEvent == Double.NEGATIVE_INFINITY
            if (at < (if (introFirstFire) 0.0 else settings.entryDelay + layer.entryDelay)) return false
            val targetEvent = layer.target?.let { memory.last[it] } ?: memory.lastMusical
            val cooldown = when {
                layer.kind == Kind.ATMOSPHERE -> max(4.0, layer.duration * 0.72)
                layer.relationship == Relationship.AVOID -> 1.8
                else -> 0.3
            }
            if (at - lastEvent < cooldown) return false
            if (layer.relationship == Relationship.AVOID && at - targetEvent < 0.9) return false
            if (layer.relationship == Relationship.ALTERNATE && (!targetEvent.isFinite() || targetEvent <= lastEvent || at - targetEvent > 4.2)) return false
            if (layer.relationship == Relationship.FOLLOW && (!targetEvent.isFinite() || at - targetEvent > 2.6)) return false
            // 与运行时 scheduleLayer 完全一致的纯概率闸门，唯一例外是开场层（intro）首次触发，保证 golden 与调度可对齐。
            val chance = if (introFirstFire) 1.0 else eventChance(layer, timbre, at, state, bias, settings)
            if (random.next() > chance) return false
            memory.last[layer.id] = at
            memory.lastMusical = at
            memory.lastLayerId = layer.id
            return true
        }

        internal fun pcmProbe(): ShortArray {
            val master = MasterChain(0.8, 72)
            return ShortArray(4096) { i ->
                val tone = sin(2 * PI * 220 * i / SAMPLE_RATE) * .18
                master.process(tone, tone).first.toPcm()
            }
        }

        /**
         * 按音色渲染 0.6s 真实声部（强制走合成路径，不依赖 VSCO 采样资产，可离线确定性校验）。
         * 逐帧校验 PCM 有限（无 NaN/∞），返回值即最终 AudioTrack 写入的 ShortArray。
         */
        internal fun pcmProbeForTimbre(timbre: String): ShortArray {
            val profile = Profile(261.63, intArrayOf(0), 0.4, 1800, 0.5)
            val samples: (String, Double) -> SampleHit? = { _, _ -> null }
            val voice = createVoice(timbre, 261.63, 0.0, 2.0, 0.06, 0.12, 2.2, 0.0, 1800.0, profile, Mulberry32(0x5eed), samples)
            val master = MasterChain(0.8, 72)
            val n = (0.6 * SAMPLE_RATE).toInt()
            val out = ShortArray(n)
            for (i in 0 until n) {
                val v = if (voice.finished(i.toLong())) 0.0 else voice.sample(i.toLong())
                val (l, r) = master.process(v * sqrt(0.5), v * sqrt(0.5))
                require(l.isFinite() && r.isFinite()) { "PCM 非有限值（NaN/∞）timbre=$timbre frame=$i" }
                out[i] = l.toPcm()
            }
            return out
        }

        /**
         * 完整编排离线渲染：与 renderLoop 完全一致的调度 + 混音 + MasterChain，但不写 AudioTrack。
         * 默认强制合成路径（samples 恒返回 null），不依赖 VSCO 采样资产，可确定性校验「完整编排是否真实出声」。
         * [sampleFor] 非空时走真实采样路径（任务七离线测量用，供 JVM 工具注入 VSCO 真实采样）。
         * 返回 [frames] 帧交错立体声 PCM（每帧 2 个 short）。
         */
        internal fun renderPresetPcm(preset: String, frames: Int, gain: Int = 72, outputVolume: Double = 0.64, disableLayers: Boolean = false, sampleFor: ((String, Double) -> SampleHit?)? = null): ShortArray {
            val base = ARRANGEMENTS.getValue(preset)
            val arrangement = if (disableLayers) base.copy(layers = base.layers.map { it.copy(enabled = false) }) else base
            val settings = Settings(gain = gain)
            val random = Mulberry32(fnv1a(arrangement.seed))
            val scheduler = SchedulerMemory()
            val voices = ArrayList<Voice>()
            val master = MasterChain(outputVolume, settings.gain)
            val out = ShortArray(frames * 2)
            val samples: (String, Double) -> SampleHit? = sampleFor ?: { _, _ -> null }
            var frame = 0L
            var tick = 0
            val sixteenth = 60.0 / arrangement.tempo / 4.0
            while (frame < frames) {
                master.setVolume(outputVolume, settings.gain)
                val seconds = frame.toDouble() / SAMPLE_RATE
                while (tick * sixteenth <= seconds + BLOCK_FRAMES.toDouble() / SAMPLE_RATE && tick * sixteenth < frames.toDouble() / SAMPLE_RATE) {
                    val at = tick * sixteenth
                    val state = sectionState(arrangement, at)
                    val playable = if (arrangement.layers.any { it.solo }) arrangement.layers.filter { it.solo } else arrangement.layers
                    playable.forEach { layer ->
                        if (shouldScheduleAtTick(layer, layer.timbre, tick, arrangement.plan)) {
                            voices += scheduleLayer(layer, layer.timbre, tick, at, state, AmbientBias(), random, settings, scheduler, arrangement.profile, samples)
                        }
                    }
                    tick++
                }
                val block = minOf(BLOCK_FRAMES.toLong(), frames - frame)
                for (i in 0 until block.toInt()) {
                    val atFrame = frame + i
                    var left = 0.0; var right = 0.0
                    val iterator = voices.iterator()
                    while (iterator.hasNext()) {
                        val voice = iterator.next()
                        if (voice.finished(atFrame)) iterator.remove()
                        else {
                            val value = voice.sample(atFrame)
                            val pan = voice.currentPan(atFrame)
                            left += value * sqrt((1.0 - pan) * .5)
                            right += value * sqrt((1.0 + pan) * .5)
                        }
                    }
                    val processed = master.process(left, right)
                    val idx = (atFrame * 2).toInt()
                    out[idx] = processed.first.toPcm(); out[idx + 1] = processed.second.toPcm()
                }
                frame += block
            }
            return out
        }

        /** 统计 PCM 峰值幅度（0..1，越接近 0 越接近全静音）。 */
        internal fun pcmPeak(pcm: ShortArray): Double {
            var peak = 0.0
            for (v in pcm) {
                val a = kotlin.math.abs(v.toInt())
                if (a > peak) peak = a.toDouble()
            }
            return peak / 32767.0
        }

        /** 离线渲染 [preset] [seconds] 秒（与运行期 renderLoop 一致）并测量 EBU R128 响度（阶段 G 校准依据）。
         *  [sampleFor] 非空时走真实采样路径（任务七离线测量）。 */
        internal fun measurePresetLoudness(preset: String, seconds: Int, sampleFor: ((String, Double) -> SampleHit?)? = null): LoudnessReport =
            measureLoudness(renderPresetPcm(preset, (seconds * SAMPLE_RATE).toInt(), sampleFor = sampleFor))

        /**
         * 「已出声」门槛的纯决策（可单测）。阶段 2b 起 PLAYING 不再由峰值决定（= 音频时钟启动），
         * 该函数只决定辅助状态 AUDIBLE：
         * - 已发过 AUDIBLE → null（保持已出声，不再干预）；
         * - 本块峰值 ≥ AUDIBLE_FLOOR → AUDIBLE（真实可听 PCM）；
         * - 峰值低于门槛 → null。锚点层保证首段 ~100-300ms 即可触发 AUDIBLE。
         */
        internal fun startupAudibleDecision(wroteAudio: Boolean, blockPeak: Double): PlaybackState? = when {
            wroteAudio -> null
            blockPeak >= AUDIBLE_FLOOR -> PlaybackState.AUDIBLE
            else -> null
        }

        /**
         * 诊断（定位「无声」的确切因子）：按生产路径构建编排（scenes 为 0-100 整数 [duration,energy,density,brightness,space]），
         * 运行与 renderLoop 完全一致的调度 + 混音（不写 AudioTrack），输出：
         * 1. 前若干调度 tick 的 sectionState 与每层 chance；
         * 2. 是否创建 Voice；
         * 3. 每 512 帧块峰值与启动可听决策（决定 PLAYING 是否触发）。
         */
        internal fun diagnosticTrace(preset: String, seconds: Double, scenes: List<IntArray> = emptyList()): String {
            val base = ARRANGEMENTS.getValue(preset)
            val arrangement = if (scenes.isEmpty()) base
            else base.copy(scenes = scenes.mapIndexed { i, it -> Scene("s$i", it[0], it[1].toDouble(), it[2].toDouble(), it[3].toDouble(), it[4].toDouble()) })
            val settings = Settings()
            val random = Mulberry32(fnv1a(arrangement.seed))
            val scheduler = SchedulerMemory()
            val voices = ArrayList<Voice>()
            val master = MasterChain(0.64, settings.gain)
            val samples: (String, Double) -> SampleHit? = { _, _ -> null }
            val sb = StringBuilder()
            val frames = (seconds * SAMPLE_RATE).toInt()
            var frame = 0L
            var tick = 0
            var wroteAudio = false
            var created = 0
            var overallPeak = 0
            var schedulePrints = 0
            val sixteenth = 60.0 / arrangement.tempo / 4.0
            while (frame < frames) {
                master.setVolume(0.64, settings.gain)
                val now = frame.toDouble() / SAMPLE_RATE
                while (tick * sixteenth <= now + BLOCK_FRAMES.toDouble() / SAMPLE_RATE && tick * sixteenth < seconds) {
                    val at = tick * sixteenth
                    val state = sectionState(arrangement, at)
                    if (schedulePrints == 0) {
                        sb.append("section@t=${"%.2f".format(at)}s energy=${"%.3f".format(state.energy)} density=${"%.3f".format(state.density)} eventGate=${"%.3f".format(state.eventGate)} notesW=${"%.2f".format(state.notesWeight)} atmW=${"%.2f".format(state.atmosphereWeight)} rhythmW=${"%.2f".format(state.rhythmWeight)} voiceW=${"%.2f".format(state.voiceWeight)}\n")
                    }
                    val playable = if (arrangement.layers.any { it.solo }) arrangement.layers.filter { it.solo } else arrangement.layers
                    playable.forEach { layer ->
                        if (shouldScheduleAtTick(layer, layer.timbre, tick, arrangement.plan)) {
                            val chance = eventChance(layer, layer.timbre, at, state, AmbientBias(), settings)
                            if (schedulePrints < 30) sb.append("t=${"%.2f".format(at)}s tick=$tick layer=${layer.id} chance=${"%.4f".format(chance)}\n")
                            val before = voices.size
                            voices += scheduleLayer(layer, layer.timbre, tick, at, state, AmbientBias(), random, settings, scheduler, arrangement.profile, samples)
                            if (voices.size > before) {
                                created += voices.size - before
                                sb.append("  → CREATED ${voices.size - before} Voice(s) for ${layer.id} @ t=${"%.2f".format(at)}s\n")
                            }
                            schedulePrints++
                        }
                    }
                    tick++
                }
                val block = minOf(BLOCK_FRAMES.toLong(), frames - frame)
                var blockPeak = 0.0
                for (i in 0 until block.toInt()) {
                    val atFrame = frame + i
                    var left = 0.0; var right = 0.0
                    val iterator = voices.iterator()
                    while (iterator.hasNext()) {
                        val voice = iterator.next()
                        if (voice.finished(atFrame)) iterator.remove()
                        else { val value = voice.sample(atFrame); left += value * sqrt((1.0 - voice.pan) * .5); right += value * sqrt((1.0 + voice.pan) * .5) }
                    }
                    val processed = master.process(left, right)
                    val a = abs(processed.first.toPcm().toInt()); val b = abs(processed.second.toPcm().toInt())
                    if (a > blockPeak) blockPeak = a.toDouble()
                    if (b > blockPeak) blockPeak = b.toDouble()
                    if (a > overallPeak) overallPeak = a
                    if (b > overallPeak) overallPeak = b
                }
                val decision = startupAudibleDecision(wroteAudio, blockPeak)
                if (decision == PlaybackState.AUDIBLE) wroteAudio = true
                sb.append("  block ${"%.2f".format(now)}s peak=${blockPeak.toInt()} decision=$decision activeVoices=${voices.size}\n")
                frame += block
            }
            sb.append("SUMMARY created=$created overallPeak=$overallPeak wroteAudio=$wroteAudio\n")
            return sb.toString()
        }
    }
}

private enum class Kind { NOTES, RHYTHM, ATMOSPHERE, VOICE, AMBIENT }
private enum class Relationship { INDEPENDENT, FOLLOW, AVOID, ALTERNATE }
private enum class Arc { BREATHING, STEADY, JOURNEY }
private enum class Register { LOW, MIDDLE, HIGH, WIDE }
private enum class Scale { PENTATONIC, MAJOR, MINOR, DORIAN }
private data class Settings(
    val tempo: Int = 54, val density: Int = 42, val variation: Int = 48, val entryDelay: Double = 1.4,
    val eventDuration: Double = 3.2, val gain: Int = 72, val ambientResponse: Int = 70, val adaptive: Boolean = true,
)
private data class Noise(val color: Double, val center: Double, val spread: Double, val resonance: Double)
private data class Profile(val root: Double, val intervals: IntArray, val brightness: Double, val cutoff: Int = 620, val room: Double = .8, val noise: Noise? = null)
private data class Plan(val cycle: Int, val notes: IntArray, val rhythm: IntArray, val atmosphere: IntArray, val voice: IntArray, val signal: IntArray)
private data class Arrangement(
    val seed: String, val tempo: Int, val sectionMinutes: Int, val arc: Arc, val profile: Profile,
    val plan: Plan, val layers: List<Layer>, val scenes: List<Scene> = emptyList(),
)
private data class Layer(
    val id: String, val kind: Kind, val timbre: String, val volume: Int, val probability: Int, val density: Int,
    val duration: Double, val attack: Double, val release: Double, val pan: Double = 0.0,
    val entryDelay: Double = if (kind == Kind.ATMOSPHERE) 0.0 else 2.0, val rhythm: Int = 1,
    val relationship: Relationship = Relationship.INDEPENDENT, val target: String? = null,
    val register: Register = if (kind == Kind.ATMOSPHERE) Register.LOW else Register.MIDDLE,
    val scale: Scale = Scale.PENTATONIC, val ambientResponse: Int = 64,
    val enabled: Boolean = true, val solo: Boolean = false,
    /** 频道开场层：首个可调度 tick 即保证触发（绕过 entryDelay 与概率闸门），用频道自己的音色做即时反馈。 */
    val intro: Boolean = false,
)
private data class Section(
    val energy: Double, val density: Double, val registerShift: Int, val cutoff: Double, val chordShift: Int,
    val durationScale: Double, val panSpread: Double, val eventGate: Double, val notesWeight: Double,
    val rhythmWeight: Double, val atmosphereWeight: Double, val voiceWeight: Double, val noiseWeight: Double,
)
private data class Scene(val id: String, val durationMinutes: Int, val energy: Double, val density: Double, val brightness: Double, val space: Double)
private data class AmbientBias(
    val level: Double = 0.0,
    val water: Double = 0.0,
    val air: Double = 0.0,
    val warm: Double = 0.0,
    val city: Double = 0.0,
    val noise: Double = 0.0,
) {
    companion object {
        /** generativeRadio.ts ambientBias (240–259)。 */
        fun from(items: List<AmbientSound>): AmbientBias {
            fun strength(pattern: Regex): Double {
                val total = items.filter { it.active && pattern.containsMatchIn("${it.name} ${it.category}") }
                    .sumOf { (it.volume * it.volume).toDouble() }
                return sqrt(total).coerceAtMost(1.0)
            }
            val level = sqrt(items.filter { it.active }.sumOf { (it.volume * it.volume).toDouble() }).coerceAtMost(1.0)
            return AmbientBias(
                level = level,
                water = strength(Regex("雨|水|海|浪|溪|河|瀑|潮|rain|water|ocean")),
                air = strength(Regex("风|森林|树|鸟|丛林|wind|forest|bird")),
                warm = strength(Regex("火|咖啡|壁炉|餐厅|键盘|纸|fire|cafe")),
                city = strength(Regex("车|列车|交通|城市|街|地铁|机场|train|city|traffic")),
                noise = strength(Regex("噪|白噪|粉噪|棕噪|noise")),
            )
        }
    }
}
internal data class SampleRef(val path: String, val frequency: Double)
internal data class PcmSample(val frames: ShortArray, val channels: Int, val sampleRate: Int)
internal data class SampleHit(val sample: PcmSample, val baseFrequency: Double)

/** 音色家族类型（阶段 2e 明确类型）：采样乐器 / 合成乐器 / 人声纹理 / 环境源（+ 节奏 / 信号纹理）。 */
private enum class TimbreKind { SAMPLED_INSTRUMENT, SYNTH_INSTRUMENT, VOCAL_TEXTURE, AMBIENT_SOURCE, RHYTHM_TEXTURE, SIGNAL_TEXTURE }

/**
 * 每个音色的完整元数据定义（阶段 2e）。UI 只展示真实存在的音色名称；试听链与正式编排链共用同一份
 * [TIMBRE_DEFINITIONS]，gainTrimDb 为音色级响度校准（原始素材响度差），禁止对单个音色硬加全局倍数。
 */
private data class TimbreDefinition(
    val id: String,
    val displayName: String,
    val kind: TimbreKind,
    val sampleMap: List<SampleRef>,
    val root: Double,
    val rangeSemitones: IntRange,
    val gainTrimDb: Double,
    val velocityCurve: Double,
    val attackSeconds: Double,
    val releaseSeconds: Double,
    val filterRange: Pair<Double, Double>,
    val loopPoints: IntRange?,
    val source: String,
    val license: String,
)
private data class SchedulerMemory(val last: MutableMap<String, Double> = HashMap(), var lastMusical: Double = Double.NEGATIVE_INFINITY, var lastLayerId: String? = null)

/** generativeRadio.ts SCALE_INTERVALS。 */
private val SCALES = mapOf(
    Scale.PENTATONIC to intArrayOf(0, 2, 4, 7, 9, 12),
    Scale.MAJOR to intArrayOf(0, 2, 4, 5, 7, 9, 11, 12),
    Scale.MINOR to intArrayOf(0, 2, 3, 5, 7, 8, 10, 12),
    Scale.DORIAN to intArrayOf(0, 2, 3, 5, 7, 9, 10, 12),
)

/** 采样音色映射：timbre → 最接近的已采样键（渲染线程只读缓存，不在此解码）。 */
internal val SAMPLE_TIMBRES = mapOf(
    "vsco-upright-piano" to listOf(SampleRef("instruments/vsco/piano-C3.wav", 130.81), SampleRef("instruments/vsco/piano-C4.wav", 261.63), SampleRef("instruments/vsco/piano-C5.wav", 523.25)),
    "vsco-cello" to listOf(SampleRef("instruments/vsco/cello-D2.wav", 73.42), SampleRef("instruments/vsco/cello-C3.wav", 130.81), SampleRef("instruments/vsco/cello-B3.wav", 246.94)),
    "vsco-harp" to listOf(SampleRef("instruments/vsco/harp-C3.wav", 130.81), SampleRef("instruments/vsco/harp-D4.wav", 293.66), SampleRef("instruments/vsco/harp-C5.wav", 523.25)),
    "vsco-flute" to listOf(SampleRef("instruments/vsco/flute-C3.wav", 130.81), SampleRef("instruments/vsco/flute-C4.wav", 261.63), SampleRef("instruments/vsco/flute-C5.wav", 523.25)),
    "vsco-violin" to listOf(SampleRef("instruments/vsco/violin-A3.wav", 220.0), SampleRef("instruments/vsco/violin-C4.wav", 261.63), SampleRef("instruments/vsco/violin-B4.wav", 493.88)),
)

/**
 * 全部真实可播音色的元数据（阶段 2e）。
 * gainTrimDb 依据素材原始响度校准（立式钢琴采样峰值约 -30dBFS，比弦乐低 10–20dB，已知问题）：
 * 通过音色级校准补齐差距，而不是给立式钢琴硬加一个全局倍数。
 * 试听链（previewTimbre）与正式编排链（renderLoop）共用同一份定义（createVoice 读取 gainTrimDb）。
 */
private val TIMBRE_DEFINITIONS: Map<String, TimbreDefinition> = mapOf(
    "vsco-upright-piano" to TimbreDefinition("vsco-upright-piano", "立式钢琴", TimbreKind.SAMPLED_INSTRUMENT, SAMPLE_TIMBRES.getValue("vsco-upright-piano"), 261.63, -36..24, +9.0, 1.0, 0.01, 3.0, 320.0 to 6000.0, null, "VSCO 2 CE", "CC0"),
    "vsco-harp" to TimbreDefinition("vsco-harp", "竖琴", TimbreKind.SAMPLED_INSTRUMENT, SAMPLE_TIMBRES.getValue("vsco-harp"), 293.66, -24..24, 0.0, 1.0, 0.003, 1.8, 400.0 to 8000.0, null, "VSCO 2 CE", "CC0"),
    "vsco-flute" to TimbreDefinition("vsco-flute", "长笛", TimbreKind.SAMPLED_INSTRUMENT, SAMPLE_TIMBRES.getValue("vsco-flute"), 261.63, -12..24, 0.0, 1.0, 0.25, 1.2, 500.0 to 5000.0, null, "VSCO 2 CE", "CC0"),
    "vsco-violin" to TimbreDefinition("vsco-violin", "小提琴组", TimbreKind.SAMPLED_INSTRUMENT, SAMPLE_TIMBRES.getValue("vsco-violin"), 261.63, -12..12, 0.0, 1.0, 0.3, 2.5, 400.0 to 5500.0, null, "VSCO 2 CE", "CC0"),
    "vsco-cello" to TimbreDefinition("vsco-cello", "大提琴组", TimbreKind.SAMPLED_INSTRUMENT, SAMPLE_TIMBRES.getValue("vsco-cello"), 130.81, -12..12, +2.0, 1.0, 0.3, 3.0, 300.0 to 4200.0, null, "VSCO 2 CE", "CC0"),
    "soft-piano" to TimbreDefinition("soft-piano", "合成柔键", TimbreKind.SYNTH_INSTRUMENT, emptyList(), 261.63, -24..24, 0.0, 1.0, 0.018, 1.6, 320.0 to 6000.0, null, "程序合成", "Built-in"),
    "electric-piano" to TimbreDefinition("electric-piano", "FM 电键", TimbreKind.SYNTH_INSTRUMENT, emptyList(), 261.63, -24..24, 0.0, 1.0, 0.025, 2.1, 400.0 to 6200.0, null, "程序合成", "Built-in"),
    "harp-pluck" to TimbreDefinition("harp-pluck", "合成拨弦", TimbreKind.SYNTH_INSTRUMENT, emptyList(), 293.66, -24..24, 0.0, 1.0, 0.003, 1.8, 500.0 to 6200.0, null, "程序合成", "Built-in"),
    "bamboo-flute" to TimbreDefinition("bamboo-flute", "气息长音", TimbreKind.SYNTH_INSTRUMENT, emptyList(), 261.63, -12..24, 0.0, 1.0, 0.16, 1.2, 500.0 to 7200.0, null, "程序合成", "Built-in"),
    "warm-strings" to TimbreDefinition("warm-strings", "暖弦铺底", TimbreKind.SYNTH_INSTRUMENT, emptyList(), 196.0, -24..12, 0.0, 1.0, 0.85, 3.4, 350.0 to 5200.0, null, "程序合成", "Built-in"),
    "air-pad" to TimbreDefinition("air-pad", "空气 Pad", TimbreKind.AMBIENT_SOURCE, emptyList(), 130.81, -36..12, 0.0, 1.0, 2.0, 4.8, 320.0 to 3800.0, null, "程序合成", "Built-in"),
    "deep-drone" to TimbreDefinition("deep-drone", "深海 Drone", TimbreKind.AMBIENT_SOURCE, emptyList(), 55.0, -36..12, 0.0, 1.0, 2.8, 5.5, 220.0 to 1600.0, null, "程序合成", "Built-in"),
    "vowel-choir" to TimbreDefinition("vowel-choir", "元音共鸣", TimbreKind.VOCAL_TEXTURE, emptyList(), 196.0, -24..12, 0.0, 1.0, 1.35, 4.0, 350.0 to 4200.0, null, "程序合成", "Built-in"),
    "muted-pulse" to TimbreDefinition("muted-pulse", "柔性脉冲", TimbreKind.RHYTHM_TEXTURE, emptyList(), 82.41, -24..12, 0.0, 1.0, 0.004, 0.22, 180.0 to 3200.0, null, "程序合成", "Built-in"),
    "rail-click" to TimbreDefinition("rail-click", "轨道轻击", TimbreKind.RHYTHM_TEXTURE, emptyList(), 261.63, -24..24, 0.0, 1.0, 0.002, 0.05, 720.0 to 5400.0, null, "程序合成", "Built-in"),
    "sonar-ping" to TimbreDefinition("sonar-ping", "声呐短波", TimbreKind.SIGNAL_TEXTURE, emptyList(), 261.63, -12..24, 0.0, 1.0, 0.006, 1.8, 420.0 to 5400.0, null, "程序合成", "Built-in"),
    "glass-bell" to TimbreDefinition("glass-bell", "玻璃钟音", TimbreKind.SIGNAL_TEXTURE, emptyList(), 523.25, -12..24, 0.0, 1.0, 0.004, 3.2, 400.0 to 7200.0, null, "程序合成", "Built-in"),
)

/** generativeRadio.ts SAMPLE_FALLBACKS（采样缺失时的合成替代）。 */
private val SAMPLE_FALLBACKS = mapOf(
    "vsco-upright-piano" to "soft-piano",
    "vsco-harp" to "harp-pluck",
    "vsco-flute" to "bamboo-flute",
    "vsco-violin" to "warm-strings",
    "vsco-cello" to "warm-strings",
)

// ── generativeRadio.ts 常量与工具 ──

private fun clamp(value: Double, min: Double, max: Double): Double = Math.max(min, Math.min(max, value))
private fun clamp(value: Int, min: Int, max: Int): Int = Math.max(min, Math.min(max, value))
private fun semitone(base: Double, interval: Double) = base * 2.0.pow(interval / 12.0)
private fun cents(c: Double) = 2.0.pow(c / 1200.0)

private fun layerWeight(state: Section, kind: Kind): Double = when (kind) {
    Kind.NOTES -> state.notesWeight
    Kind.RHYTHM -> state.rhythmWeight
    Kind.ATMOSPHERE -> state.atmosphereWeight
    Kind.VOICE -> state.voiceWeight
    else -> 0.0
}

private fun isSignalTimbre(timbre: String) = timbre == "sonar-ping" || timbre == "glass-bell"

/** generativeRadio.ts shouldScheduleAtTick (982–992)。 */
private fun shouldScheduleAtTick(layer: Layer, timbre: String, tick: Int, plan: Plan): Boolean {
    val phase = tick % plan.cycle
    if (layer.rhythm > 1 && tick % layer.rhythm != 0) return false
    return when (layer.kind) {
        Kind.RHYTHM -> phase in plan.rhythm
        Kind.ATMOSPHERE -> phase in plan.atmosphere
        Kind.VOICE -> phase in plan.voice
        else -> if (isSignalTimbre(timbre)) phase in plan.signal else phase in plan.notes
    }
}

/** 当前生效场景的明亮度（0-100 原始值）。与 [applyTimelineScene] 的「按时长选当前场景」同源，供热更新只对当前场景生效。 */
private fun currentSceneBrightness(a: Arrangement, elapsed: Double): Double {
    val scenes = a.scenes.filter { it.durationMinutes > 0 }
    if (scenes.isEmpty()) return 0.0
    val totalSeconds = scenes.sumOf { it.durationMinutes * 60.0 }.coerceAtLeast(1.0)
    var cursor = elapsed % totalSeconds
    val scene = scenes.firstOrNull {
        val duration = it.durationMinutes * 60.0
        if (cursor < duration) true else { cursor -= duration; false }
    } ?: scenes.last()
    return scene.brightness.toDouble()
}

/** generativeRadio.ts applyTimelineScene (840–864)。scene 各参数为 0-100（play() 从 GeneratedScene 整数直接转换，不再 /100）。 */
private fun applyTimelineScene(state: Section, a: Arrangement, elapsed: Double): Section {
    val scenes = a.scenes.filter { it.durationMinutes > 0 }
    if (scenes.isEmpty()) return state
    val totalSeconds = scenes.sumOf { it.durationMinutes * 60.0 }.coerceAtLeast(1.0)
    var cursor = elapsed % totalSeconds
    val scene = scenes.firstOrNull {
        val duration = it.durationMinutes * 60.0
        if (cursor < duration) true else { cursor -= duration; false }
    } ?: scenes.last()
    // scene 值为 0-100（GeneratedScene 整数；play() 不再 /100），与 generativeRadio.ts `value / 50` 逐字对齐。
    val energy = clamp(scene.energy / 50.0, 0.2, 1.8)
    val density = clamp(scene.density / 50.0, 0.2, 1.8)
    val brightness = clamp(scene.brightness / 50.0, 0.35, 1.65)
    val space = clamp(scene.space / 50.0, 0.35, 1.65)
    return Section(
        energy = clamp(state.energy * energy, 0.08, 1.4),
        density = clamp(state.density * density, 0.08, 1.6),
        registerShift = state.registerShift,
        cutoff = clamp(state.cutoff * brightness, 0.45, 1.5),
        chordShift = state.chordShift,
        durationScale = clamp(state.durationScale * (0.72 + space * 0.28), 0.45, 1.8),
        panSpread = clamp(state.panSpread * space, 0.25, 1.5),
        eventGate = clamp(state.eventGate * density, 0.08, 1.35),
        notesWeight = state.notesWeight,
        rhythmWeight = state.rhythmWeight,
        atmosphereWeight = state.atmosphereWeight,
        voiceWeight = state.voiceWeight,
        noiseWeight = state.noiseWeight,
    )
}

/** generativeRadio.ts sectionState (866–970)。 */
private fun sectionState(a: Arrangement, seconds: Double): Section {
    val elapsed = max(0.0, seconds)
    val period = max(90.0, a.sectionMinutes * 60.0)
    val phase = (elapsed % period) / period
    val sectionIndex = (elapsed / period).toInt()
    val base: Section = when (a.arc) {
        Arc.STEADY -> {
            val phrasePhase = (elapsed % 32) / 32
            val phraseRest = if (phrasePhase > 0.92) 0.68 else 1.0
            Section(
                energy = 0.66 + sin(elapsed / 31) * 0.024, density = 0.9, registerShift = 0,
                cutoff = 0.95, chordShift = if (sectionIndex % 4 == 3) 2 else 0, durationScale = 1.0,
                panSpread = 0.72, eventGate = phraseRest, notesWeight = 1.0, rhythmWeight = 0.92,
                atmosphereWeight = 0.96, voiceWeight = 0.72, noiseWeight = 0.64,
            )
        }
        Arc.BREATHING -> {
            val breathPeriod = clamp(period / 8.0, 22.0, 42.0)
            val breath = (cos(elapsed / breathPeriod * PI * 2 + PI) + 1) / 2
            val shaped = breath * breath * (3 - 2 * breath)
            Section(
                energy = 0.4 + shaped * 0.42,
                density = 0.5 + shaped * 0.58,
                registerShift = if (shaped > 0.78) 2 else if (shaped < 0.16) -2 else 0,
                cutoff = 0.8 + shaped * 0.34,
                chordShift = if (sectionIndex % 2 == 0) 0 else 5,
                durationScale = 1.22 - shaped * 0.2,
                panSpread = 0.72 + shaped * 0.38,
                eventGate = if (shaped < 0.1) 0.16 else 0.58 + shaped * 0.5,
                notesWeight = 0.34 + shaped * 0.94,
                rhythmWeight = 0.2 + shaped * 0.68,
                atmosphereWeight = 1.12 - shaped * 0.14,
                voiceWeight = 0.58 + (1 - shaped) * 0.34,
                noiseWeight = 0.35 + shaped * 0.28,
            )
        }
        else -> when {
            phase < 0.2 -> Section(0.42 + phase * 0.72, 0.5 + phase * 0.9, -5, 0.76, 0, 1.24, 0.62, 0.72, 0.44, 0.18, 1.24, 0.36, 0.28)
            phase < 0.5 -> Section(0.58 + (phase - 0.2) * 0.64, 0.74 + (phase - 0.2) * 0.72, 0, 1.02, 5, 1.04, 0.9, 0.94, 1.0, 0.58, 0.96, 0.68, 0.62)
            phase < 0.76 -> Section(0.82, 1.06, 5, 1.2, 7, 0.9, 1.16, 1.08, 1.08, 1.16, 0.78, 0.88, 0.9)
            else -> Section(0.74 - (phase - 0.76) * 1.08, 0.78 - (phase - 0.76) * 0.72, -2, 0.86, 3, 1.28, 0.68, 0.64, 0.52, 0.14, 1.16, 0.56, 0.24)
        }
    }
    return applyTimelineScene(base, a, elapsed)
}

/** generativeRadio.ts synthEnvelope (580–599)。返回 (attack, duration, release)。 */
private fun synthEnvelope(timbre: String, attack: Double, duration: Double, release: Double): Triple<Double, Double, Double> = when (timbre) {
    "rail-click" -> Triple(0.002, min(duration, 0.09), 0.05)
    "muted-pulse" -> Triple(0.004, min(duration, 0.3), 0.22)
    "soft-piano" -> Triple(min(attack, 0.018), min(duration, 2.8), max(release, 1.6))
    "electric-piano" -> Triple(min(attack, 0.025), min(duration, 3.4), max(release, 2.1))
    "harp-pluck" -> Triple(0.003, min(duration, 1.5), max(0.8, min(release, 1.8)))
    "glass-bell" -> Triple(0.004, min(duration, 2.8), max(release, 3.2))
    "sonar-ping" -> Triple(0.006, min(duration, 1.8), max(release, 1.8))
    "bamboo-flute" -> Triple(max(0.16, min(attack, 0.65)), max(duration, 2.2), max(release, 1.2))
    "warm-strings" -> Triple(max(0.85, attack), max(duration, 5.5), max(release, 3.4))
    "vowel-choir" -> Triple(max(1.35, attack), max(duration, 6.0), max(release, 4.0))
    "air-pad" -> Triple(max(2.0, attack), max(duration, 7.0), max(release, 4.8))
    "deep-drone" -> Triple(max(2.8, attack), max(duration, 10.0), max(release, 5.5))
    else -> Triple(attack, duration, release)
}

/** generativeRadio.ts scheduleLayer 的概率闸门 (1028–1031)：纯 chance 计算，无概率地板、无首次强制。 */
private fun eventChance(
    layer: Layer, timbre: String, at: Double, state: Section, bias: AmbientBias,
    settings: Settings,
): Double {
    val response = if (settings.adaptive) layer.ambientResponse / 100.0 * settings.ambientResponse / 100.0 else 0.0
    val timbreBias = when (timbre) {
        "bamboo-flute", "vsco-flute", "vsco-harp" -> bias.air * .34
        "rail-click", "muted-pulse" -> bias.city * .42
        "sonar-ping", "deep-drone" -> bias.water * .38
        "warm-strings", "electric-piano", "vsco-cello", "vsco-violin", "vsco-upright-piano" -> bias.warm * .22
        else -> 0.0
    }
    val masking = bias.noise * response * .36
    val density = clamp(layer.density * settings.density / 42.0 * state.density * (1 - masking), 4.0, 100.0) / 100.0
    return clamp(layer.probability * density * state.energy * state.eventGate * layerWeight(state, layer.kind) * (1 + timbreBias * response), 1.0, 96.0) / 100.0
}

/** generativeRadio.ts scheduleLayer (994–1087)。消耗与前端一致的随机数序列，返回需要渲染的声部。 */
private fun scheduleLayer(
    layer: Layer, timbre: String, tick: Int, at: Double, state: Section, bias: AmbientBias,
    random: Mulberry32, settings: Settings, memory: SchedulerMemory, profile: Profile,
    samples: (String, Double) -> SampleHit?,
): List<Voice> {
    if (!layer.enabled || layer.kind == Kind.AMBIENT) return emptyList()
    val response = if (settings.adaptive) layer.ambientResponse / 100.0 * settings.ambientResponse / 100.0 else 0.0
    val ambientPresence = bias.level * response
    val lastEvent = memory.last[layer.id] ?: Double.NEGATIVE_INFINITY
    // 开场层（intro）：首个可调度 tick 保证触发一次，用频道自己的音色做即时反馈（绕过 entryDelay 与概率闸门），
    // 之后回到正常概率调度。这是频道配方里显式声明的 intro/bed，不是全局隐藏 drone。
    val introFirstFire = layer.intro && lastEvent == Double.NEGATIVE_INFINITY
    if (at < (if (introFirstFire) 0.0 else settings.entryDelay + layer.entryDelay)) return emptyList()
    val targetEvent = layer.target?.let { memory.last[it] } ?: memory.lastMusical
    val cooldown = when {
        layer.kind == Kind.ATMOSPHERE -> max(4.0, layer.duration * 0.72)
        layer.relationship == Relationship.AVOID -> 1.8
        else -> 0.3
    }
    if (at - lastEvent < cooldown) return emptyList()
    if (layer.relationship == Relationship.AVOID && at - targetEvent < 0.9) return emptyList()
    if (layer.relationship == Relationship.ALTERNATE && (!targetEvent.isFinite() || targetEvent <= lastEvent || at - targetEvent > 4.2)) return emptyList()
    if (layer.relationship == Relationship.FOLLOW && (!targetEvent.isFinite() || at - targetEvent > 2.6)) return emptyList()

    // 与 Web 一致的纯概率闸门：chance = probability×density×energy×eventGate×layerWeight。
    // 无 25% 概率地板、无首次强制触发（neverFired），唯一例外是开场层（intro）的首次触发。
    val chance = if (introFirstFire) 1.0 else eventChance(layer, timbre, at, state, bias, settings)
    if (random.next() > chance) return emptyList()

    val register = when (layer.register) {
        Register.LOW -> -12
        Register.HIGH -> 12
        Register.WIDE -> if (random.next() > 0.5) 12 else 0
        else -> 0
    }
    val scale = SCALES.getValue(layer.scale)
    val variationCount = max(2, ceil(scale.size * clamp(settings.variation, 0, 100) / 100.0).toInt()).coerceAtMost(scale.size)
    val available = scale.take(variationCount)
    val randomInterval = available[(random.next() * available.size).toInt()]
    val motifPosition = tick / if (layer.kind == Kind.RHYTHM) 4 else 8
    val motifTarget = profile.intervals[motifPosition % profile.intervals.size]
    val motif = available.minBy { abs(it - motifTarget) }
    val phrase = if (layer.kind != Kind.RHYTHM && random.next() < 0.7) motif else randomInterval
    val interval = phrase + register + state.registerShift + state.chordShift
    val start = at + random.next() * 0.08
    val baseDuration = layer.duration
    // 全局「事件时长」设置：相对默认 3.2s 归一化后直接缩放每个事件的实际时长（不再是被忽略的摆设）。
    val eventScale = (settings.eventDuration / 3.2).coerceIn(0.25, 3.0)
    val evolvedDuration = baseDuration * eventScale * state.durationScale * (0.84 + random.next() * 0.28) * (1 + bias.water * response * 0.28)
    val duration = when {
        layer.kind == Kind.RHYTHM -> clamp(evolvedDuration, 0.12, 0.9)
        layer.kind == Kind.ATMOSPHERE -> max(6.0, evolvedDuration)
        layer.kind == Kind.VOICE -> max(5.0, evolvedDuration)
        isSignalTimbre(timbre) -> clamp(evolvedDuration, 0.4, 3.2)
        else -> max(0.24, evolvedDuration)
    }
    val textureWeight = if (timbre == "rail-click") state.noiseWeight * (0.72 + bias.city * response * 0.4) else 1.0
    // 开场层（intro）首次触发用克制的可感知电平（约 -40dBFS，与旧 anchor 同量级），用频道自己音色做即时反馈，
    // 不把全音量 bed 瞬间铺满（否则稀疏/助眠频道响度被抬得比普通频道还高）。
    val peak = if (introFirstFire) 0.018
    else (0.025 + profile.brightness * 0.026) * layer.volume / 52.0 * (0.72 + state.energy * 0.44) *
        (1 + bias.warm * response * 0.12) * (1 - ambientPresence * 0.12) * textureWeight
    val panDrift = (bias.air * 0.3 + bias.water * 0.08) * response
    val pan = clamp(layer.pan / 100.0 + (random.next() - 0.5) * (0.12 + panDrift) * state.panSpread, -1.0, 1.0)
    val cutoff = profile.cutoff * state.cutoff * (1 + profile.brightness * 0.6 + bias.air * response * 0.4 - bias.noise * response * 0.16)
    val frequency = semitone(profile.root, when {
        layer.kind == Kind.RHYTHM -> (interval - 24).toDouble()
        layer.kind == Kind.VOICE -> (interval - 12).toDouble()
        else -> interval.toDouble()
    })
    val chordIntervals = when (layer.kind) {
        Kind.ATMOSPHERE -> intArrayOf(0, 7)
        Kind.VOICE -> intArrayOf(0, 7, 12)
        else -> intArrayOf(0)
    }
    val chordPeak = max(0.001, peak / sqrt(chordIntervals.size.toDouble()) * (if (chordIntervals.size > 1) 0.78 else 1.0))
    // intro 是一次性的“频道已开始”听觉确认，不应继承氛围层 5–7 秒的慢起音；
    // 后续事件仍完整使用层自身 attack，因此不会改变频道长期呼吸与助眠质感。
    val attack = if (introFirstFire) min(0.14, max(0.025, layer.attack)) else max(0.025, layer.attack)
    val release = max(0.08, layer.release)
    val result = ArrayList<Voice>(chordIntervals.size)
    chordIntervals.forEachIndexed { index, chordInterval ->
        val voicePan = clamp(pan + (index - (chordIntervals.size - 1) / 2.0) * 0.12, -1.0, 1.0)
        result += createVoice(timbre, semitone(frequency, chordInterval.toDouble()), start + index * 0.018, duration, chordPeak, attack, release, voicePan, cutoff, profile, random, samples, layerId = layer.id, voiceGroup = tick, baseLayerVolume = layer.volume)
    }
    memory.last[layer.id] = at
    memory.lastMusical = at
    memory.lastLayerId = layer.id
    return result
}

/** 采样/合成声部创建：sample 走 VSCO 采样（velocity + 音量 dB），synth 走逐音色合成。 */
private fun createVoice(
    timbre: String, frequency: Double, startSeconds: Double, duration: Double,
    peak: Double, attackIn: Double, releaseIn: Double, pan: Double, cutoffIn: Double, profile: Profile,
    random: Mulberry32, samples: (String, Double) -> SampleHit?,
    layerId: String = "", voiceGroup: Int = 0, baseLayerVolume: Int = 52,
): Voice {
    val startFrame = (startSeconds * SAMPLE_RATE).toLong()
    // 音色级响度校准（阶段 2e/2g）：gainTrimDb 补齐原始素材响度差（如立式钢琴采样峰值比弦乐低 10–20dB），
    // 试听链与正式编排链共用同一份 TIMBRE_DEFINITIONS。
    val trimDb = TIMBRE_DEFINITIONS[timbre]?.gainTrimDb ?: 0.0
    val trimGain = 10.0.pow(trimDb / 20.0)
    val hit = samples(timbre, frequency)
    if (hit != null) {
        val velocity = clamp(0.48 + peak * 7.0, 0.45, 0.86)
        // generativeRadio.ts 618：sampler.volume = gainToDb(clamp(peak*8.5,0.08,0.78)) → 线性 = clamp(peak*8.5,0.08,0.78)。
        val volume = Math.pow(10.0, 20.0 * log10(clamp(peak * 8.5, 0.08, 0.78)) / 20.0)
        val attack = clamp(attackIn, 0.01, 4.0)
        val release = clamp(releaseIn, 0.08, 8.0)
        return Voice(startFrame, max(0.3, duration), attack, release, velocity * volume * trimGain, pan, frequency, timbre,
            hit.sample, hit.baseFrequency, clamp(cutoffIn, 320.0, 9000.0), profile.room, null, layerId, voiceGroup, baseLayerVolume)
    }
    val synthTimbre = SAMPLE_FALLBACKS[timbre] ?: timbre
    val (shapedAttack, shapedDuration, shapedRelease) = synthEnvelope(synthTimbre, attackIn, duration, releaseIn)
    val safePeak = clamp(peak, 0.001, 0.075)
    val cutoff = clamp(cutoffIn, 220.0, 7200.0)
    val extra = buildExtra(synthTimbre, frequency, duration, cutoffIn, profile, random)
    return Voice(startFrame, shapedDuration, shapedAttack, shapedRelease, safePeak * trimGain, pan, frequency, synthTimbre,
        null, 0.0, cutoff, profile.room, extra, layerId, voiceGroup, baseLayerVolume)
}

/** 每种音色的专用声部状态。 */
private data class TimbreExtra(
    val railNoise: DoubleArray? = null,
    val railBand: Bandpass? = null,
    val harp: HarpString? = null,
    val vibratoFreq: Double = 0.0,
    val breathNoise: DoubleArray? = null,
    val breathBand: Bandpass? = null,
    val choir: ChoirFormants? = null,
)

private fun buildExtra(timbre: String, frequency: Double, duration: Double, cutoffIn: Double, profile: Profile, random: Mulberry32): TimbreExtra = when (timbre) {
    "rail-click" -> {
        val noiseSpec = profile.noise ?: Noise(
            color = clamp(0.28 + (1 - profile.brightness) * 0.38, 0.18, 0.78),
            center = clamp(profile.cutoff * 1.25, 720.0, 2400.0),
            spread = 420.0,
            resonance = 3.2,
        )
        val band = Bandpass(noiseSpec.center + (random.next() - 0.5) * noiseSpec.spread, noiseSpec.resonance)
        TimbreExtra(railNoise = createNoiseBuffer(random, 0.12, noiseSpec.color), railBand = band)
    }
    "harp-pluck" -> TimbreExtra(harp = HarpString(clamp(1.0 / max(70.0, frequency), 0.002, 0.055), clamp(cutoffIn * 1.45, 900.0, 6200.0)))
    "bamboo-flute" -> TimbreExtra(
        vibratoFreq = 4.3 + random.next(),
        breathNoise = createNoiseBuffer(random, 0.16, 0.82),
        breathBand = Bandpass(clamp(frequency * 5.0, 900.0, 4200.0), 1.4),
    )
    "vowel-choir" -> TimbreExtra(choir = ChoirFormants())
    else -> TimbreExtra()
}

private fun createNoiseBuffer(random: Mulberry32, seconds: Double, color: Double): DoubleArray {
    val n = max(1, (seconds * SAMPLE_RATE).toInt())
    val data = DoubleArray(n)
    val smoothing = clamp(color, 0.0, 0.94)
    var previous = 0.0
    for (i in 0 until n) {
        val white = random.next() * 2 - 1
        previous = previous * smoothing + white * (1 - smoothing)
        data[i] = white * (1 - smoothing * 0.72) + previous * smoothing * 1.4
    }
    return data
}

private var burstNoiseCache: DoubleArray? = null
private fun createBurstSample(t: Double, color: Double): Double {
    if (burstNoiseCache == null) {
        val r = Mulberry32(0x9e3779b9.toInt())
        burstNoiseCache = createNoiseBuffer(r, 0.028, color)
    }
    val idx = (t * SAMPLE_RATE).toInt().coerceIn(0, burstNoiseCache!!.lastIndex)
    return burstNoiseCache!![idx]
}

// ── 声部渲染 ──

private class Voice(
    val startFrame: Long,
    val duration: Double,
    val attack: Double,
    val release: Double,
    val gain: Double,
    var pan: Double,
    val frequency: Double,
    val timbre: String,
    val sample: PcmSample?,
    val sampleBase: Double,
    var cutoff: Double,
    val room: Double,
    val extra: TimbreExtra?,
    /** 所属编排层 id（阶段五热更新：删除/禁用层时对对应存量声部做可控 release）。 */
    val layerId: String,
    /** 同一次调度事件的声部组 id（和弦的多个声部共享；阶段五，供未来参数 ramp 按组联动）。 */
    val voiceGroup: Int,
    /** 声部创建时所属层的音量（热更新时按「新音量/基准音量」算绝对目标，不用全局首次音量）。 */
    val baseLayerVolume: Int,
) {
    private val isSample = sample != null
    private val end = startFrame + ((duration + release) * SAMPLE_RATE).toLong()
    private val q = when (timbre) { "muted-pulse" -> 1.2; "vowel-choir" -> 0.8; else -> 0.45 }
    private val lp = BiquadLp(cutoff, q)
    private val echo: Echo? = if (timbre == "sonar-ping") Echo(0.42 + room * 0.18) else null
    /** 层被删除/禁用时由渲染线程置位，此后在 LAYER_RELEASE_SECONDS 内淡出。 */
    private var releaseStart = Long.MAX_VALUE
    private val releaseFrames = (LAYER_RELEASE_SECONDS * SAMPLE_RATE).toLong()
    /** 层音量参数 ramp：gainScale 平滑逼近 gainScaleTarget（约 30ms），用于热更新改音量时不跳变。 */
    private var gainScale = 1.0
    private var gainScaleTarget = 1.0
    private var gainScaleAt = Long.MAX_VALUE
    /** 声像 / 滤波（cutoff）参数 ramp 目标与起点。 */
    private var panTarget = pan
    private var panAt = Long.MAX_VALUE
    private var cutoffTarget = cutoff
    private var cutoffAt = Long.MAX_VALUE

    /** 渲染线程调用：层被删除/禁用 → 存量声部进入可控 release。 */
    fun beginRelease(at: Long) { if (releaseStart > at) releaseStart = at }

    /** 渲染线程调用：层音量改变 → 存量声部 ramp 到新增益。新 ramp 从调用时当前插值开始，不跳变。 */
    fun setGainScale(target: Double, at: Long) {
        gainScale = currentGainScale(at)
        gainScaleTarget = target
        gainScaleAt = at
    }

    /** 渲染线程调用：层声像改变 → 新 ramp 从调用时当前插值开始，不跳变。 */
    fun setPanTarget(target: Double, at: Long) {
        pan = currentPan(at)
        panTarget = target
        panAt = at
    }

    /** 渲染线程调用：层滤波（cutoff）改变 → 新 ramp 从调用时当前插值开始，不跳变。 */
    fun setCutoffTarget(target: Double, at: Long) {
        cutoff = rampedCutoff(at)
        cutoffTarget = target
        cutoffAt = at
    }

    /** 渲染线程用于混音的当前声像（含 ramp）。 */
    fun currentPan(at: Long): Double {
        if (panAt == Long.MAX_VALUE || at <= panAt) return pan
        val ramp = 1.0 - exp(-(at - panAt).toDouble() / SAMPLE_RATE / VOLUME_RAMP_TAU_SECONDS)
        val p = pan + (panTarget - pan) * ramp
        if (ramp > 0.99) { pan = panTarget; panAt = Long.MAX_VALUE }
        return p
    }

    /** 渲染线程用于混音的当前增益比例（含 ramp）。 */
    fun currentGainScale(at: Long): Double {
        if (gainScaleAt == Long.MAX_VALUE || at <= gainScaleAt) return gainScale
        val ramp = 1.0 - exp(-(at - gainScaleAt).toDouble() / SAMPLE_RATE / VOLUME_RAMP_TAU_SECONDS)
        val s = gainScale + (gainScaleTarget - gainScale) * ramp
        if (ramp > 0.99) { gainScale = gainScaleTarget; gainScaleAt = Long.MAX_VALUE }
        return s
    }

    /** 采样/合成声部当前 cutoff（含 ramp），用于低通滤波。 */
    private fun rampedCutoff(at: Long): Double {
        if (cutoffAt == Long.MAX_VALUE || at <= cutoffAt) return cutoff
        val ramp = 1.0 - exp(-(at - cutoffAt).toDouble() / SAMPLE_RATE / VOLUME_RAMP_TAU_SECONDS)
        val c = cutoff + (cutoffTarget - cutoff) * ramp
        if (ramp > 0.99) { cutoff = cutoffTarget; cutoffAt = Long.MAX_VALUE }
        return c
    }

    fun finished(at: Long): Boolean {
        // releaseStart 用 Long.MAX_VALUE 作「未释放」哨兵；加 releaseFrames 会溢出，故先判哨兵。
        if (releaseStart != Long.MAX_VALUE && at >= releaseStart + releaseFrames) return true
        return at >= end
    }

    private fun currentCutoff(t: Double, base: Double): Double = when (timbre) {
        "air-pad" -> base + clamp(base * 0.12, 28.0, 240.0) * sin(2 * PI * (0.06 + room * 0.05) * t)
        "muted-pulse" -> {
            val target = max(180.0, base * 0.36)
            val span = min(0.2, duration)
            if (t >= span) target else base * (target / base).pow(t / span)
        }
        else -> base
    }

    fun sample(at: Long): Double {
        if (at < startFrame) return 0.0
        val t = (at - startFrame).toDouble() / SAMPLE_RATE
        val base = rampedCutoff(at)
        val out = if (isSample) {
            val ratio = frequency / sampleBase * sample!!.sampleRate / SAMPLE_RATE
            val index = (t * SAMPLE_RATE * ratio).toInt()
            val raw = if (index < sample.frames.size / sample.channels) sample.frames[index * sample.channels] / 32768.0 else 0.0
            val env = linearEnv(t)
            // generativeRadio.ts 619：采样声部同样走每事件低通（sampleVoice.filter.frequency clamp(cutoff,320,9000)）；
            // 滤波 ramp 时跟随 base 更新。
            lp.set(base, q)
            lp.process(raw) * env * gain
        } else {
            val raw = synthRaw(timbre, frequency, t, duration, extra)
            if (timbre == "air-pad" || timbre == "muted-pulse") lp.set(currentCutoff(t, base), q)
            val filtered = lp.process(raw)
            val env = expEnv(t)
            var o = filtered * env
            if (timbre == "sonar-ping") o += echo!!.process(filtered)
            o
        }
        // 层音量参数 ramp（热更新）：gainScale 平滑逼近 gainScaleTarget（约 30ms）。
        var result = out
        if (gainScaleAt != Long.MAX_VALUE) {
            val ramp = 1.0 - exp(-(at - gainScaleAt).toDouble() / SAMPLE_RATE / VOLUME_RAMP_TAU_SECONDS)
            val scale = gainScale + (gainScaleTarget - gainScale) * ramp
            result *= scale
            if (ramp > 0.99) { gainScale = gainScaleTarget; gainScaleAt = Long.MAX_VALUE }
        }
        // 层被删除/禁用 → 存量声部在 releaseFrames 内线性淡出（可控 release），而非等原 duration 自然结束。
        if (at >= releaseStart) {
            val fade = (1.0 - (at - releaseStart).toDouble() / releaseFrames).coerceIn(0.0, 1.0)
            result *= fade
        }
        return result
    }

    private fun linearEnv(t: Double): Double = when {
        t >= duration + release -> 0.0
        t < attack -> if (attack > 0) t / attack else 1.0
        t < duration -> 1.0
        else -> ((duration + release - t) / max(0.0001, release)).coerceIn(0.0, 1.0)
    }

    /** generativeRadio.ts envelope (552–560)：指数包络 0.0001 → peak → peak*0.42 → 0.0001。 */
    private fun expEnv(t: Double): Double {
        if (gain <= 0.0) return 0.0
        val sustainAt = max(0.025, attack)
        val releaseAt = max(sustainAt + 0.04, duration)
        return when {
            t <= 0.0 -> 0.0001
            t < sustainAt -> 0.0001 * (gain / 0.0001).pow(t / sustainAt)
            t < releaseAt -> gain * 0.42.pow((t - sustainAt) / max(0.0001, releaseAt - sustainAt))
            t < releaseAt + release -> gain * 0.42 * (0.0001 / (gain * 0.42)).pow((t - releaseAt) / max(0.0001, release))
            else -> 0.0001
        }
    }
}

private fun sine(f: Double, t: Double) = sin(2 * PI * f * t)
private fun triangle(f: Double, t: Double) = 2 / PI * asin(sin(2 * PI * f * t))
private fun saw(f: Double, t: Double) = 2 * ((t * f) - floor(0.5 + t * f))

/** generativeRadio.ts scheduleTimbre 的逐音色振荡器合成（进入主低通前的原始混合）。 */
private fun synthRaw(timbre: String, f: Double, t: Double, duration: Double, extra: TimbreExtra?): Double = when (timbre) {
    "soft-piano" -> triangle(f, t) + 0.28 * sine(f * 2.01 * cents(3.0), t)
    "electric-piano" -> {
        val depth = min(42.0, f * 0.12)
        val fm = f * 2.0
        val phaseMod = depth / (2 * PI * fm)
        sin(2 * PI * f * t + phaseMod * (1 - cos(2 * PI * fm * t)))
    }
    "glass-bell" -> sine(f, t) + 0.26 * sine(f * 2.01 * cents(3.0), t) + 0.09 * sine(f * 3.94 * cents(6.0), t)
    "harp-pluck" -> {
        val h = extra?.harp
        if (h == null) 0.0 else h.process(if (t < 0.028) createBurstSample(t, 0.16) else 0.0)
    }
    "bamboo-flute" -> {
        val vf = extra?.vibratoFreq ?: 4.3
        val phaseMod = 1.8 / (2 * PI * vf)
        var v = sin(2 * PI * f * t + phaseMod * (1 - cos(2 * PI * vf * t)))
        val breath = extra?.breathNoise
        if (breath != null && t < 0.16) {
            val idx = (t * SAMPLE_RATE).toInt().coerceIn(0, breath.lastIndex)
            v += extra.breathBand!!.process(breath[idx]) * 0.035
        }
        v
    }
    "warm-strings" -> saw(f * cents(-7.0), t) + saw(f, t) + saw(f * cents(7.0), t) + triangle(f * 1.5 * cents(3.0), t)
    "vowel-choir" -> extra?.choir?.process(f, t) ?: 0.0
    "air-pad" -> sine(f * cents(-5.0), t) + triangle(f * 1.5 * cents(5.0), t) + sine(f * 2.0 * cents(2.0), t)
    "deep-drone" -> sine(f * 0.5 * cents(-4.0), t) + triangle(f * 0.75 * cents(4.0), t)
    "muted-pulse" -> {
        val target = max(34.0, f * 0.32)
        val span = min(0.18, duration)
        val p = if (t >= span) target else f * 0.5 * (target / (f * 0.5)).pow(t / span)
        (if (sin(2 * PI * p * t) >= 0) 1.0 else -1.0) + sine(f * 0.25 * cents(-2.0), t)
    }
    "sonar-ping" -> {
        val glideTo = max(42.0, f * 1.45)
        val span = min(duration, 1.6)
        val g = if (t >= span) glideTo else f * 2.0 * (glideTo / (f * 2.0)).pow(t / span)
        sine(g, t)
    }
    "rail-click" -> {
        val noise = extra?.railNoise
        if (noise == null || t >= 0.12) 0.0 else extra.railBand!!.process(noise[(t * SAMPLE_RATE).toInt().coerceIn(0, noise.lastIndex)])
    }
    else -> sine(f, t)
}

/** vowel-choir 的共振峰带通（formants 620/1180/2450，Q 2.4/3.1/4）。 */
private class ChoirFormants {
    private val bands = arrayOf(Bandpass(620.0, 2.4), Bandpass(1180.0, 3.1), Bandpass(2450.0, 4.0))
    fun process(f: Double, t: Double): Double {
        val f0 = f * cents(-4.0)
        val f1 = f * 1.5 * cents(4.0)
        val f2 = f * 2.0 * cents(8.0)
        return bands[0].process(triangle(f0, t)) + bands[1].process(sine(f1, t)) + bands[2].process(sine(f2, t))
    }
}

// ── 滤波器 / 延迟 / 主链路 ──

/** RBJ 低通（biquad）。系数按块更新：cutoff 变化超过 1Hz 才重算 sin/cos，避免每采样点重算。 */
private class BiquadLp(freq: Double, q: Double) {
    private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0
    private var b0 = 0.0; private var b1 = 0.0; private var b2 = 0.0; private var a1 = 0.0; private var a2 = 0.0
    private var lastFreq = -1.0
    init { set(freq, q) }
    fun set(freq: Double, q: Double) {
        if (abs(freq - lastFreq) < 1.0) return /* cutoff 未明显变化，跳过系数重算 */
        lastFreq = freq
        val w0 = 2 * PI * freq / SAMPLE_RATE
        val cosw = cos(w0); val sinw = sin(w0)
        val alpha = sinw / (2 * max(0.0001, q))
        b0 = (1 - cosw) / 2; b1 = 1 - cosw; b2 = (1 - cosw) / 2
        val a0 = 1 + alpha; val a1n = -2 * cosw; val a2n = 1 - alpha
        b0 /= a0; b1 /= a0; b2 /= a0; a1 = a1n / a0; a2 = a2n / a0
    }
    fun process(x: Double): Double {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x; y2 = y1; y1 = y
        return y
    }
}

/** RBJ 带通（constant 0 dB peak gain）。 */
private class Bandpass(freq: Double, q: Double) {
    private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0
    private var b0 = 0.0; private var b1 = 0.0; private var b2 = 0.0; private var a1 = 0.0; private var a2 = 0.0
    init { set(freq, q) }
    fun set(freq: Double, q: Double) {
        val w0 = 2 * PI * freq / SAMPLE_RATE
        val cosw = cos(w0); val sinw = sin(w0)
        val alpha = sinw / (2 * max(0.0001, q))
        b0 = alpha; b1 = 0.0; b2 = -alpha
        val a0 = 1 + alpha; val a1n = -2 * cosw; val a2n = 1 - alpha
        b0 /= a0; b1 /= a0; b2 /= a0; a1 = a1n / a0; a2 = a2n / a0
    }
    fun process(x: Double): Double {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x; y2 = y1; y1 = y
        return y
    }
}

/** 拨弦反馈延迟（Karplus-强式阻尼，feedback 0.92）。 */
private class HarpString(delaySeconds: Double, dampingCutoff: Double) {
    private val frames = max(2, (delaySeconds * SAMPLE_RATE).toInt())
    private val buffer = DoubleArray(frames)
    private var idx = 0
    private val damp = BiquadLp(dampingCutoff, 0.5)
    fun process(input: Double): Double {
        val read = buffer[idx]
        val damped = damp.process(read)
        buffer[idx] = input + 0.92 * damped
        idx = (idx + 1) % frames
        return damped
    }
}

private class Echo(delaySeconds: Double, private val gain: Double = 0.16) {
    private val frames = max(2, (delaySeconds * SAMPLE_RATE).toInt())
    private val buffer = DoubleArray(frames)
    private var idx = 0
    fun process(x: Double): Double {
        val d = buffer[idx]
        buffer[idx] = x
        idx = (idx + 1) % frames
        return d * gain
    }
}

/**
 * 主链路（阶段 2g）：master gain = outputVolume × gain/100（取消不合理的统一 0.66 衰减）→
 * DynamicsCompressor → Limiter。总线保留混音余量：每声部峰值有界 + 压限器对多声部叠加与
 * 环境声同播留出 headroom，Limiter 阈值 -1dB 把最终 True Peak 限制在约不高于 -1dBTP。
 */
private class MasterChain(outputVolume: Double, gain: Int, trimDb: Double = 0.0) {
    private val trimGain = 10.0.pow(trimDb / 20.0)
    private var vol = outputVolume * gain / 100.0 * trimGain
    private val compressor = Compressor(-22.0, 20.0, 4.0, 0.02, 0.65)
    private val limiter = Compressor(-1.0, 0.0, 20.0, 0.002, 0.12)
    fun setVolume(v: Double, gain: Int) { vol = v * gain / 100.0 * trimGain }
    fun process(l: Double, r: Double): Pair<Double, Double> {
        val (cl, cr) = compressor.process(l * vol, r * vol)
        return limiter.process(cl, cr)
    }
}

/** Web Audio DynamicsCompressor 近似（软膝、attack/release 包络、共享立体声检测）。 */
private class Compressor(thresholdDb: Double, kneeDb: Double, ratio: Double, attackSec: Double, releaseSec: Double) {
    private val thr = thresholdDb
    private val knee = kneeDb
    private val ratio = ratio
    private val aCoeff = if (attackSec > 0) exp(-1.0 / (attackSec * SAMPLE_RATE)) else 0.0
    private val rCoeff = if (releaseSec > 0) exp(-1.0 / (releaseSec * SAMPLE_RATE)) else 0.0
    private var envDb = 0.0
    /** 前端是共享立体声节点：以双声道联合电平检测，同一增益衰减应用到左右。 */
    fun process(l: Double, r: Double): Pair<Double, Double> {
        val absL = abs(l); val absR = abs(r)
        val level = if (absL > absR) absL else absR
        var desired = 0.0
        if (level > 1e-6) {
            val levelDb = 20.0 * log10(level)
            val reduction = when {
                levelDb < thr - knee / 2 -> 0.0
                levelDb < thr + knee / 2 -> {
                    val delta = levelDb - thr + knee / 2
                    delta * delta / (2 * knee)
                }
                else -> levelDb - thr
            }
            desired = -reduction * (ratio - 1) / ratio
        }
        envDb = if (desired > envDb) envDb + aCoeff * (desired - envDb) else envDb + rCoeff * (desired - envDb)
        val g = 10.0.pow(envDb / 20.0)
        return l * g to r * g
    }
}

// ── 随机 / 解码 ──

private class Mulberry32(private var seed: Int) {
    fun next(): Double {
        seed += 0x6d2b79f5
        var v = seed
        v = (v xor (v ushr 15)) * (v or 1)
        v = v xor (v + ((v xor (v ushr 7)) * (v or 61)))
        return ((v xor (v ushr 14)).toLong() and 0xffffffffL) / 4294967296.0
    }
}
private fun fnv1a(s: String): Int {
    var h = 0x811c9dc5.toInt()
    s.forEach { h = h xor it.code; h *= 16777619 }
    return h
}
/**
 * 解码 RIFF WAVE 为 16-bit PCM 采样缓存（渲染器统一按 ShortArray 消费）。
 * 支持：PCM 16-bit / 24-bit / 32-bit（format=1）与 IEEE Float 32-bit（format=3）。
 * 解析失败抛异常（调用方负责日志与降级），绝不静默返回错数据。
 */
internal fun decodeWav(bytes: ByteArray): PcmSample {
    val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    require(b.int == 0x46464952) { "不是 RIFF 文件" }
    b.int
    require(b.int == 0x45564157) { "不是 WAVE 文件" }
    var format = 0; var channels = 0; var rate = 0; var bits = 0; var data = ByteArray(0)
    while (b.remaining() >= 8) {
        val id = b.int; val len = b.int
        when (id) {
            0x20746d66 -> { // "fmt "
                format = b.short.toInt() and 0xFFFF
                channels = b.short.toInt()
                rate = b.int
                b.position(b.position() + 6)
                bits = b.short.toInt() and 0xFFFF
                b.position(b.position() + len - 16)
            }
            0x61746164 -> { // "data"
                data = ByteArray(len); b.get(data)
            }
            else -> b.position((b.position() + len).coerceAtMost(b.limit()))
        }
    }
    require(channels > 0 && rate > 0 && bits > 0) { "WAV 头缺 channels/rate/bits（channels=$channels rate=$rate bits=$bits）" }
    require(format == 1 || format == 3) { "不支持的 WAV 编码格式 format=$format（仅 PCM=1 / IEEE Float=3）" }
    if (format == 1) require(bits == 16 || bits == 24 || bits == 32) { "不支持的 PCM 位深 bits=$bits（支持 16/24/32）" }
    if (format == 3) require(bits == 32) { "Float WAV 仅支持 32-bit，实际 bits=$bits" }
    val frameBytes = bits / 8
    val sampleCount = data.size / frameBytes
    val shorts = ShortArray(sampleCount)
    val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
    for (i in 0 until sampleCount) {
        val normalized = when (format) {
            3 -> buf.float.toDouble()
            1 -> when (bits) {
                16 -> buf.short.toDouble() / 32768.0
                24 -> {
                    val s0 = buf.get().toInt() and 0xFF
                    val s1 = buf.get().toInt() and 0xFF
                    val s2 = buf.get().toInt()
                    ((s0) or (s1 shl 8) or (s2 shl 16)).toDouble() / 8388608.0
                }
                else -> buf.int.toDouble() / 2147483648.0
            }
            else -> 0.0
        }
        shorts[i] = (normalized.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
    }
    return PcmSample(shorts, channels, rate)
}
private fun Double.toPcm() = (coerceIn(-1.0, 1.0) * 32767).toInt().toShort()

// ── 离线响度测量（阶段 G：EBU R128 频道间响度校准依据） ──

/** 离线响度测量结果。IL=Integrated LUFS；STmax=最大短时(3s)LUFS；LRA=响度范围；TP=True Peak(dBTP)。 */
internal data class LoudnessReport(
    val integratedLufs: Double,
    val shortTermLufsMax: Double,
    val loudnessRangeLu: Double,
    val truePeakDbTp: Double,
    val rmsDb: Double,
    val peakDb: Double,
) {
    override fun toString(): String = java.lang.String.format(
        java.util.Locale.US,
        "IL=%+.2f LUFS  STmax=%+.2f  LRA=%.2f LU  TP=%.2f dBTP  RMS=%.2f dBFS  peak=%.2f dBFS",
        integratedLufs, shortTermLufsMax, loudnessRangeLu, truePeakDbTp, rmsDb, peakDb,
    )
}

/** 通用 RBJ biquad（a0 已归一化到 1）。 */
private class RbjBiquad(
    private val b0: Double, private val b1: Double, private val b2: Double,
    private val a1: Double, private val a2: Double,
) {
    private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0
    fun process(x: Double): Double {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x; y2 = y1; y1 = y
        return y
    }
}

/** ITU-R BS.1770-4 K 加权（高频架 1681.97Hz +4dB → 高通 38.14Hz）。 */
private class KWeighting(sampleRate: Int) {
    private val shelf = highShelf(1681.974450955533, 3.999843853973347, 0.7071752369554196, sampleRate.toDouble())
    private val hp = highPass(38.13547087602444, 0.5003270373238773, sampleRate.toDouble())
    fun process(x: Double): Double = hp.process(shelf.process(x))
}

private fun highShelf(f0: Double, gainDb: Double, q: Double, fs: Double): RbjBiquad {
    val a = 10.0.pow(gainDb / 40.0)
    val w0 = 2.0 * PI * f0 / fs
    val cosw = cos(w0); val alpha = sin(w0) / (2.0 * q); val sqa = sqrt(a)
    var b0 = a * ((a + 1) + (a - 1) * cosw + 2 * sqa * alpha)
    var b1 = -2 * a * ((a - 1) + (a + 1) * cosw)
    var b2 = a * ((a + 1) + (a - 1) * cosw - 2 * sqa * alpha)
    var a0 = (a + 1) - (a - 1) * cosw + 2 * sqa * alpha
    var a1 = 2 * ((a - 1) - (a + 1) * cosw)
    var a2 = (a + 1) - (a - 1) * cosw - 2 * sqa * alpha
    b0 /= a0; b1 /= a0; b2 /= a0; a1 /= a0; a2 /= a0
    return RbjBiquad(b0, b1, b2, a1, a2)
}

private fun highPass(f0: Double, q: Double, fs: Double): RbjBiquad {
    val w0 = 2.0 * PI * f0 / fs
    val cosw = cos(w0); val alpha = sin(w0) / (2.0 * q)
    var b0 = (1 + cosw) / 2; var b1 = -(1 + cosw); var b2 = (1 + cosw) / 2
    var a0 = 1 + alpha; var a1 = -2 * cosw; var a2 = 1 - alpha
    b0 /= a0; b1 /= a0; b2 /= a0; a1 /= a0; a2 /= a0
    return RbjBiquad(b0, b1, b2, a1, a2)
}

private fun db(v: Double): Double = 20.0 * log10(max(1e-12, v))

/** 把均方能量 z 转 LUFS（BS.1770 常数 -0.691）。 */
private fun lufsOf(z: Double): Double = -0.691 + 10.0 * log10(z)

/**
 * EBU R128 响度测量：交错立体声 PCM → 单声道 K 加权 → 400ms 块能量 → 绝对(-70LUFS)/相对(-10LU)门限
 * → Integrated LUFS；3s 块短时响度 → 最大值 + LRA；4x 线性插值过采样 → True Peak；另有 RMS 与峰值。
 */
internal fun measureLoudness(pcm: ShortArray, sampleRate: Int = SAMPLE_RATE): LoudnessReport {
    val frames = pcm.size / 2
    val neg = Double.NEGATIVE_INFINITY
    if (frames == 0) return LoudnessReport(neg, neg, 0.0, neg, neg, neg)
    val k = KWeighting(sampleRate)
    val mono = DoubleArray(frames)
    var rawSumSq = 0.0
    var peak = 0.0
    var tp = 0.0
    for (i in 0 until frames) {
        val l = pcm[i * 2] / 32768.0
        val r = pcm[i * 2 + 1] / 32768.0
        val raw = (l + r) * 0.5
        mono[i] = k.process(raw)
        rawSumSq += raw * raw
        val a = max(abs(l), abs(r)); if (a > peak) peak = a
    }
    // True Peak：4x 线性插值过采样。
    for (i in 0 until frames - 1) {
        val l0 = pcm[i * 2] / 32768.0; val l1 = pcm[(i + 1) * 2] / 32768.0
        val r0 = pcm[i * 2 + 1] / 32768.0; val r1 = pcm[(i + 1) * 2 + 1] / 32768.0
        for (j in 0 until 4) {
            val f = j / 4.0
            val a = max(abs(l0 + (l1 - l0) * f), abs(r0 + (r1 - r0) * f))
            if (a > tp) tp = a
        }
    }
    val rmsDb = db(sqrt(rawSumSq / frames))
    val peakDb = db(peak)
    val tpDb = db(tp)
    // 400ms 块均方能量。
    val block = (0.4 * sampleRate).toInt()
    val z400 = ArrayList<Double>()
    var off = 0
    while (off + block <= frames) {
        var s = 0.0
        for (i in off until off + block) s += mono[i] * mono[i]
        z400 += s / block; off += block
    }
    if (z400.isEmpty()) return LoudnessReport(neg, neg, 0.0, tpDb, rmsDb, peakDb)
    // 绝对门限 -70 LUFS → 相对门限（集成响度 -10 LU）。
    val absGated = z400.filter { lufsOf(it) > -70.0 }
    if (absGated.isEmpty()) return LoudnessReport(neg, neg, 0.0, tpDb, rmsDb, peakDb)
    var integrated = lufsOf(absGated.sum() / absGated.size)
    val relGated = absGated.filter { lufsOf(it) > integrated - 10.0 }
    val finalZ = if (relGated.isEmpty()) absGated else relGated
    integrated = lufsOf(finalZ.sum() / finalZ.size)
    // 3s 短时响度 + LRA（10%~95% 分位差）。
    val block3 = (3.0 * sampleRate).toInt()
    val stLufs = ArrayList<Double>()
    off = 0
    while (off + block3 <= frames) {
        var s = 0.0
        for (i in off until off + block3) s += mono[i] * mono[i]
        stLufs += lufsOf(s / block3); off += block3
    }
    val stMax = stLufs.maxOrNull() ?: neg
    val lra = if (stLufs.size >= 10) {
        val sorted = stLufs.sorted()
        val lo = sorted[(sorted.size * 0.10).toInt().coerceIn(0, sorted.size - 1)]
        val hi = sorted[(sorted.size * 0.95).toInt().coerceIn(0, sorted.size - 1)]
        hi - lo
    } else 0.0
    return LoudnessReport(integrated, stMax, lra, tpDb, rmsDb, peakDb)
}

private fun com.soundist.feature.listening.GeneratedLayer.toNative() = Layer(
    id = id,
    kind = when (type) {
        "rhythm" -> Kind.RHYTHM
        "atmosphere" -> Kind.ATMOSPHERE
        "voice" -> Kind.VOICE
        "ambient", "modulation" -> Kind.AMBIENT
        else -> Kind.NOTES
    },
    timbre = com.soundist.feature.listening.normalizeGeneratedTimbre(timbre, type),
    volume = volume,
    probability = probability,
    density = density,
    duration = durationSeconds.toDouble(),
    attack = fadeInSeconds.toDouble(),
    release = fadeOutSeconds.toDouble(),
    pan = pan / 100.0,
    entryDelay = entryDelaySeconds.toDouble(),
    rhythm = when (rhythm) {
        "whole" -> 16; "half" -> 8; "quarter" -> 4; "eighth" -> 2; else -> 1
    },
    relationship = when (relationship) {
        "follow" -> Relationship.FOLLOW
        "avoid" -> Relationship.AVOID
        "alternate" -> Relationship.ALTERNATE
        else -> Relationship.INDEPENDENT
    },
    target = relationshipTargetId,
    register = when (register) {
        "low" -> Register.LOW; "high" -> Register.HIGH; "wide" -> Register.WIDE; else -> Register.MIDDLE
    },
    scale = when (scale) {
        "major" -> Scale.MAJOR; "minor" -> Scale.MINOR; "dorian" -> Scale.DORIAN; else -> Scale.PENTATONIC
    },
    ambientResponse = ambientResponse,
    enabled = enabled,
    solo = solo,
)

// ── 预设编排层（generativeRadio.ts PRESET_ARRANGEMENTS + makeLayer）──

private fun makeLayer(
    id: String, name: String, kind: Kind, timbre: String, volume: Int? = null, probability: Int? = null,
    density: Int? = null, duration: Double? = null, register: Register? = null, rhythm: Int? = null,
    attack: Double? = null, release: Double? = null, relationship: Relationship = Relationship.INDEPENDENT,
    ambientResponse: Int = 64, solo: Boolean = false, intro: Boolean = false,
) = Layer(
    id = id, kind = kind, timbre = timbre,
    volume = volume ?: if (kind == Kind.ATMOSPHERE) 42 else if (kind == Kind.RHYTHM) 30 else 52,
    probability = probability ?: if (kind == Kind.ATMOSPHERE) 68 else if (kind == Kind.RHYTHM) 56 else 48,
    density = density ?: if (kind == Kind.ATMOSPHERE) 24 else if (kind == Kind.RHYTHM) 54 else 38,
    duration = duration ?: if (kind == Kind.ATMOSPHERE) 12.0 else if (kind == Kind.RHYTHM) 0.8 else 3.4,
    attack = attack ?: if (kind == Kind.ATMOSPHERE) 3.5 else 0.12,
    release = release ?: if (kind == Kind.ATMOSPHERE) 4.5 else 2.2,
    entryDelay = if (kind == Kind.ATMOSPHERE) 0.0 else 2.0,
    rhythm = rhythm ?: if (kind == Kind.RHYTHM) 4 else 1,
    register = register ?: if (kind == Kind.ATMOSPHERE) Register.LOW else Register.MIDDLE,
    relationship = relationship,
    ambientResponse = ambientResponse,
    solo = solo,
    intro = intro,
)

private fun withTarget(layers: List<Layer>): List<Layer> = layers.mapIndexed { index, layer ->
    if (layer.relationship == Relationship.INDEPENDENT) layer
    else layer.copy(target = layers.subList(0, index).reversed().firstOrNull { it.kind != layer.kind }?.id ?: layers[0].id)
}

private fun deepSeaLayers() = withTarget(listOf(
    makeLayer("deep-sea-floor", "深海低音", Kind.ATMOSPHERE, "deep-drone", volume = 54, duration = 18.0, probability = 76, density = 18, register = Register.LOW, attack = 5.0, release = 7.0, intro = true),
    makeLayer("deep-sea-sonar", "远处声呐", Kind.NOTES, "sonar-ping", volume = 38, probability = 28, density = 20, duration = 2.8, register = Register.WIDE, relationship = Relationship.AVOID, ambientResponse = 86),
    makeLayer("deep-sea-bell", "水下泛音", Kind.NOTES, "glass-bell", volume = 30, probability = 34, density = 25, duration = 5.5, register = Register.HIGH, relationship = Relationship.ALTERNATE),
))
private fun rainPianoLayers() = withTarget(listOf(
    makeLayer("rain-piano-notes", "雨窗钢琴", Kind.NOTES, "vsco-upright-piano", volume = 58, probability = 60, density = 42, duration = 4.2, ambientResponse = 92, intro = true),
    makeLayer("rain-piano-strings", "湿润弦光", Kind.ATMOSPHERE, "vsco-cello", volume = 30, probability = 54, density = 16, duration = 14.0, relationship = Relationship.AVOID),
    makeLayer("rain-piano-bell", "窗沿水滴", Kind.NOTES, "glass-bell", volume = 24, probability = 25, density = 22, duration = 3.4, register = Register.HIGH, relationship = Relationship.ALTERNATE),
))
private fun morningMistLayers() = withTarget(listOf(
    makeLayer("mist-flute", "晨雾长笛", Kind.NOTES, "vsco-flute", volume = 42, probability = 38, density = 30, duration = 5.8, register = Register.HIGH, ambientResponse = 90, intro = true),
    makeLayer("mist-harp", "露水竖琴", Kind.NOTES, "vsco-harp", volume = 44, probability = 54, density = 36, duration = 2.4, relationship = Relationship.ALTERNATE),
    makeLayer("mist-air", "薄雾铺底", Kind.ATMOSPHERE, "air-pad", volume = 32, probability = 58, density = 16, duration = 15.0, register = Register.WIDE),
))
private fun endlessFocusLayers() = withTarget(listOf(
    makeLayer("focus-electric", "稳定电钢", Kind.NOTES, "electric-piano", volume = 48, probability = 46, density = 42, duration = 3.2, rhythm = 8, intro = true),
    makeLayer("focus-pulse", "专注脉冲", Kind.RHYTHM, "muted-pulse", volume = 28, probability = 76, density = 62, duration = 0.65, rhythm = 4, relationship = Relationship.FOLLOW),
    makeLayer("focus-strings", "低位大提琴", Kind.ATMOSPHERE, "vsco-cello", volume = 26, probability = 54, density = 14, duration = 16.0, register = Register.LOW),
))
private fun nightTrainLayers() = withTarget(listOf(
    makeLayer("train-drone", "车窗低频", Kind.ATMOSPHERE, "deep-drone", volume = 38, probability = 70, density = 18, duration = 16.0, register = Register.LOW, ambientResponse = 82, intro = true),
    makeLayer("train-rail", "轨道节律", Kind.RHYTHM, "rail-click", volume = 34, probability = 82, density = 68, duration = 0.45, rhythm = 2, ambientResponse = 96),
    makeLayer("train-signal", "远站信号", Kind.NOTES, "sonar-ping", volume = 30, probability = 24, density = 18, duration = 2.4, register = Register.HIGH, relationship = Relationship.AVOID),
))
private fun warmStudyLayers() = withTarget(listOf(
    makeLayer("study-ep", "暖色电钢", Kind.NOTES, "electric-piano", volume = 54, probability = 58, density = 44, duration = 3.6, ambientResponse = 74, intro = true),
    makeLayer("study-harp", "纸页竖琴", Kind.NOTES, "vsco-harp", volume = 30, probability = 34, density = 24, duration = 2.1, relationship = Relationship.ALTERNATE),
    makeLayer("study-pulse", "低速节拍", Kind.RHYTHM, "muted-pulse", volume = 22, probability = 62, density = 50, duration = 0.8, rhythm = 4, relationship = Relationship.FOLLOW),
))
private fun moonTideLayers() = withTarget(listOf(
    makeLayer("moon-drone", "月潮低音", Kind.ATMOSPHERE, "deep-drone", volume = 50, probability = 78, density = 14, duration = 22.0, attack = 7.0, release = 8.0, ambientResponse = 92, intro = true),
    makeLayer("moon-choir", "远景人声", Kind.VOICE, "vowel-choir", volume = 24, probability = 32, density = 15, duration = 10.0, register = Register.WIDE, relationship = Relationship.AVOID),
    makeLayer("moon-bell", "月面泛音", Kind.NOTES, "glass-bell", volume = 24, probability = 22, density = 16, duration = 6.0, register = Register.HIGH, relationship = Relationship.AVOID),
))
private fun signalGardenLayers() = withTarget(listOf(
    makeLayer("signal-bell", "电子花粉", Kind.NOTES, "glass-bell", volume = 46, probability = 62, density = 54, duration = 2.8, register = Register.WIDE, intro = true),
    makeLayer("signal-harp", "枝叶拨弦", Kind.NOTES, "harp-pluck", volume = 32, probability = 48, density = 40, duration = 1.8, relationship = Relationship.ALTERNATE),
    makeLayer("signal-pulse", "细粒脉冲", Kind.RHYTHM, "muted-pulse", volume = 20, probability = 58, density = 48, duration = 0.8, rhythm = 2, relationship = Relationship.FOLLOW),
))
private fun bambooLayers() = withTarget(listOf(
    makeLayer("bamboo-flute", "竹影长笛", Kind.NOTES, "vsco-flute", volume = 50, probability = 44, density = 30, duration = 6.5, register = Register.HIGH, ambientResponse = 92, intro = true),
    makeLayer("bamboo-strings", "低声大提琴", Kind.ATMOSPHERE, "vsco-cello", volume = 34, probability = 62, density = 18, duration = 16.0, register = Register.LOW, relationship = Relationship.AVOID),
    makeLayer("bamboo-harp", "山涧竖琴", Kind.NOTES, "vsco-harp", volume = 28, probability = 32, density = 24, duration = 2.2, relationship = Relationship.ALTERNATE),
))
private fun customLabLayers() = withTarget(listOf(
    makeLayer("lab-piano", "主旋律", Kind.NOTES, "vsco-upright-piano", volume = 52, probability = 52, density = 38, intro = true),
    makeLayer("lab-pad", "氛围铺底", Kind.ATMOSPHERE, "air-pad", volume = 34, probability = 62, density = 18, duration = 14.0),
    makeLayer("lab-pulse", "节拍骨架", Kind.RHYTHM, "muted-pulse", volume = 22, probability = 58, density = 46, duration = 0.8, rhythm = 4),
))
