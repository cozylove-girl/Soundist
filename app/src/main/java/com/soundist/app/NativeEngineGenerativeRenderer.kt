package com.soundist.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.soundist.core.audio.ExternalPlaybackController
import com.soundist.feature.listening.AmbientSound
import com.soundist.feature.listening.GeneratedAudioRenderer
import com.soundist.feature.listening.GeneratedArrangement
import com.soundist.feature.listening.GeneratedLayer
import com.soundist.feature.listening.GENERATED_TIMBRES
import com.soundist.feature.listening.GeneratorSettings
import com.soundist.feature.listening.PlaybackState
import com.soundist.feature.listening.RadioEngineState
import com.soundist.feature.listening.RadioStation
import com.soundist.feature.listening.normalizeGeneratedTimbre
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 生成电台的 native 引擎后端（阶段 E 落地）：调度 + 混音 + 音色合成都在 native
 * [soundist_generative.c] 完成，Kotlin 侧只负责 AudioTrack 流式写出、生命周期与事件回流。
 *
 * 与 [NativeGeneratedAudioRenderer] 同接口（GeneratedAudioRenderer + ExternalPlaybackController），
 * 由 [MiniaudioFeatureFlags.generativeNativeEnabled] 选择；flag 关闭时始终走 Kotlin 渲染器（可快速回退）。
 *
 * VSCO 音色按当前频道/试听需要加载，避免首次播放等待整套采样库；编排热更新由 native
 * 层按 layer id 做平滑过渡。
 */
class NativeEngineGenerativeRenderer(context: Context) : GeneratedPlaybackController, Closeable {

    private val appContext = context.applicationContext
    private val lock = Any()
    /** Kotlin render thread and UI control calls share one native engine handle. */
    private val nativeLock = Any()
    private val preloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private val renderGeneration = AtomicLong(0L)
    /** Invalidates an in-flight blocking write even if pause is followed by an immediate resume. */
    private val pauseEpoch = AtomicLong(0L)
    private var worker: Thread? = null
    private var track: AudioTrack? = null
    /** 单音色试听使用的静态 AudioTrack；开始新电台/停止时一并释放，避免堆叠音频实例。 */
    @Volatile private var auditionTrack: AudioTrack? = null
    private val auditionGeneration = AtomicLong(0L)
    private var engine: Long = 0L
    private val sampleRegistryLock = Any()
    private val sampleTimbreLocks = ConcurrentHashMap<String, Any>()
    @Volatile private var sampleRegistryInitialized = false
    private val loadedSampleTimbres = ConcurrentHashMap.newKeySet<String>()

    private val _events = MutableStateFlow(RadioEngineState())
    override val radioEvents: Flow<RadioEngineState> = _events.asStateFlow()
    @Volatile private var currentStationId: String? = null
    @Volatile private var currentSeed: String? = null
    @Volatile private var outputVolume = 0.64
    @Volatile private var currentPresetTrim = 1.0
    private val presetTrimById: Map<String, Double> by lazy(::loadPresetTrims)

    /** Move VSCO file IO away from the first play gesture. Native registration remains serialized. */
    fun prewarmDefaultSamples() {
        preloadScope.launch { ensureVscoSamplesLoaded(SAMPLE_TIMBRES.keys) }
    }

    private fun emitState(state: PlaybackState, message: String? = null) {
        currentStationId?.let { _events.value = RadioEngineState(state, it, 0, message) }
    }

    override fun setVolume(value: Float) {
        outputVolume = value.toDouble().coerceIn(0.0, 1.0)
        synchronized(lock) {
            if (engine != 0L) synchronized(nativeLock) {
                val result = NativeAudioCore.setGenerativeOutputVolume(engine, effectiveOutputVolume())
                if (result != 0) emitState(PlaybackState.ERROR, "更新输出音量失败（native code=$result）")
            }
        }
    }

    override suspend fun play(station: RadioStation, activeAmbient: List<AmbientSound>) {
        val preset = station.generatorPresetId ?: station.id
        val settings = station.generatorSettings
        val arrangement = station.generatorArrangement?.withIntroLayer()
        val seed = arrangement?.seed ?: preset
        val requiredSamples = arrangement?.layers.orEmpty()
            .filter { it.enabled }
            .map { normalizeGeneratedTimbre(it.timbre, it.type) }
            .filter(SAMPLE_TIMBRES::containsKey)
            .toSet()
        // native 采样表是进程级资源。只有确实缺少当前编排需要的音色时才停止旧引擎并注册，
        // 避免边渲染边改采样表，也避免首次播放读取完整 VSCO 库。
        if (requiredSamples.any { !isSampleTimbreLoaded(it) }) {
            synchronized(lock) { if (running.get()) stopLocked() }
            withContext(Dispatchers.IO) { ensureVscoSamplesLoaded(requiredSamples) }
        }
        synchronized(lock) {
            currentPresetTrim = presetTrimById[preset] ?: 1.0
            // 热更新（试听当前编排）：同频道、seed 未变、仍在播 → 只替换层/scenes/settings/bias，不重建引擎/线程。
            if (running.get() && currentStationId == station.id && currentSeed == seed && engine != 0L) {
                // 独奏是一次明确试听命令。重置调度记忆，确保目标层立刻触发 intro，
                // 而不是等待原频道下一次概率事件；普通编辑仍保持无缝热更新。
                val auditioningLayer = arrangement?.layers?.any { it.enabled && it.solo } == true
                try {
                    synchronized(nativeLock) {
                        checkNative("更新生成音量", NativeAudioCore.setGenerativeOutputVolume(engine, effectiveOutputVolume()))
                        applyArrangement(engine, settings, arrangement, activeAmbient, reset = auditioningLayer)
                    }
                    if (paused.get()) {
                        track?.play()
                        paused.set(false)
                    }
                    emitState(PlaybackState.PLAYING)
                    return
                } catch (error: Throwable) {
                    stopLocked()
                    emitState(PlaybackState.ERROR, error.message ?: "编排热更新失败")
                    throw error
                }
            }
            stopLocked()
            currentStationId = station.id
            currentSeed = seed
            val e = NativeAudioCore.createGenerativeEngine(preset, effectiveOutputVolume(), settings?.gain ?: 72)
            if (e == 0L) { emitState(PlaybackState.ERROR, "native 引擎创建失败：未知预设 $preset"); return }
            engine = e
            try {
                synchronized(nativeLock) { applyArrangement(e, settings, arrangement, activeAmbient, reset = true) }
            } catch (error: Throwable) {
                synchronized(nativeLock) { NativeAudioCore.destroyGenerativeEngine(e) }
                engine = 0L
                emitState(PlaybackState.ERROR, error.message ?: "生成编排配置失败")
                throw error
            }
            val audio = try {
                AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                    .setBufferSizeInBytes(max(AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT), BLOCK_FRAMES * 8))
                    .setTransferMode(AudioTrack.MODE_STREAM).build()
            } catch (error: Throwable) {
                synchronized(nativeLock) { NativeAudioCore.destroyGenerativeEngine(e) }
                engine = 0L
                emitState(PlaybackState.ERROR, "音频输出创建失败：${error.message.orEmpty()}")
                throw error
            }
            if (audio.state != AudioTrack.STATE_INITIALIZED) {
                audio.release()
                synchronized(nativeLock) { NativeAudioCore.destroyGenerativeEngine(e) }
                engine = 0L
                val error = IllegalStateException("音频输出未能初始化")
                emitState(PlaybackState.ERROR, error.message)
                throw error
            }
            track = audio
            running.set(true); paused.set(false)
            val generation = renderGeneration.incrementAndGet()
            worker = Thread({ renderLoop(audio, e, generation) }, "SoundistNativeGenerativeRadio").also { it.priority = Thread.MAX_PRIORITY; it.start() }
        }
    }

    /** 把 settings/环境偏置/自定义编排/scenes 应用到引擎。reset=true 为全重启，false 为热更新（保留调度/声部）。 */
    private fun applyArrangement(e: Long, settings: GeneratorSettings?, arrangement: GeneratedArrangement?, activeAmbient: List<AmbientSound>, reset: Boolean) {
        checkNative("应用生成设置", NativeAudioCore.setGenerativeSettings(
            e,
            settings?.tempo ?: 54, settings?.density ?: 42, settings?.variation ?: 48,
            (settings?.entryDelaySeconds ?: 1.4f).toDouble(), (settings?.eventDurationSeconds ?: 3.2f).toDouble(),
            settings?.gain ?: 72, settings?.ambientResponse ?: 70, settings?.adaptive ?: true,
        ))
        val bias = if (settings?.adaptive != false) ambientBias(activeAmbient) else DoubleArray(6)
        checkNative("应用环境响应", NativeAudioCore.setGenerativeAmbientBias(e, bias[0], bias[1], bias[2], bias[3], bias[4], bias[5]))
        if (arrangement != null) {
            checkNative("应用生成编排", NativeAudioCore.setGenerativeArrangement(
                e, arrangement.seed, arrangement.sectionMinutes, arcOf(arrangement.arc),
                arrangement.layers.map { it.toNativeLayer() }, reset,
            ))
            checkNative("应用场景时间线", NativeAudioCore.setGenerativeScenes(
                e,
                arrangement.scenes.map { it.id }.toTypedArray(),
                arrangement.scenes.map { it.durationMinutes }.toIntArray(),
                arrangement.scenes.map { it.energy.toDouble() }.toDoubleArray(),
                arrangement.scenes.map { it.density.toDouble() }.toDoubleArray(),
                arrangement.scenes.map { it.brightness.toDouble() }.toDoubleArray(),
                arrangement.scenes.map { it.space.toDouble() }.toDoubleArray(),
            ))
        }
    }

    private fun checkNative(operation: String, result: Int) {
        check(result == 0) { "${operation}失败（native code=$result）" }
    }

    override suspend fun pause(): Unit = synchronized(lock) { pauseExternal() }
    override suspend fun stop(): Unit = synchronized(lock) { emitState(PlaybackState.IDLE); stopLocked() }
    override fun close() {
        synchronized(lock) { emitState(PlaybackState.IDLE); stopLocked() }
        preloadScope.cancel()
    }
    override fun resumeExternal() = synchronized(lock) {
        if (running.get() && paused.get()) { track?.play(); paused.set(false); emitState(PlaybackState.PLAYING) }
    }
    override fun pauseExternal() = synchronized(lock) {
        if (running.get()) { paused.set(true); pauseEpoch.incrementAndGet(); track?.pause(); emitState(PlaybackState.PAUSED) }
    }
    override fun stopExternal() = synchronized(lock) { emitState(PlaybackState.IDLE); stopLocked() }

    private fun renderLoop(audio: AudioTrack, e: Long, generation: Long) {
        try {
            if (!isRenderActive(generation)) return
            audio.play()
            if (isRenderActive(generation)) emitState(PlaybackState.PLAYING)
            val pcm = ShortArray(BLOCK_FRAMES * 2)
            var audible = false
            var silentFrames = 0L
            while (isRenderActive(generation)) {
                if (paused.get()) { Thread.sleep(20); continue }
                val block = synchronized(nativeLock) {
                    if (engine != e || !isRenderActive(generation)) FloatArray(0)
                    else NativeAudioCore.renderGenerative(e, BLOCK_FRAMES)
                }
                if (block.size != BLOCK_FRAMES * 2) {
                    throw IllegalStateException("native 生成器返回了不完整的音频块（${block.size}）")
                }
                for (i in 0 until BLOCK_FRAMES) {
                    pcm[i * 2] = (block[i * 2].coerceIn(-1f, 1f) * 32767).toInt().toShort()
                    pcm[i * 2 + 1] = (block[i * 2 + 1].coerceIn(-1f, 1f) * 32767).toInt().toShort()
                }
                val written = writeFully(audio, pcm, generation)
                if (written == WRITE_INTERRUPTED) continue
                if (written < 0) {
                    if (isRenderActive(generation)) { running.set(false); emitState(PlaybackState.ERROR, "AudioTrack 写入失败（code=$written）") }
                    break
                }
                if (!audible) {
                    var peak = 0
                    for (s in pcm) { val a = if (s < 0) -s.toInt() else s.toInt(); if (a > peak) peak = a }
                    if (peak >= AUDIBLE_FLOOR) {
                        audible = true
                        emitState(PlaybackState.AUDIBLE)
                    } else if (outputVolume > 0.01) {
                        silentFrames += BLOCK_FRAMES
                        if (silentFrames >= SILENCE_TIMEOUT_FRAMES) {
                            throw IllegalStateException("持续声场未生成可听输出，请重试或检查当前编排")
                        }
                    }
                }
            }
        } catch (_: InterruptedException) {
        } catch (error: Throwable) {
            if (isRenderActive(generation)) emitState(PlaybackState.ERROR, error.message ?: "native 生成电台渲染失败")
        } finally { runCatching { audio.stop() } }
    }

    /** AudioTrack 即使在 blocking 模式也允许返回正数短写；循环补齐，只有负错误码才失败。 */
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

    override suspend fun previewTimbre(timbre: String) = withContext(Dispatchers.Default) {
        val generation = auditionGeneration.incrementAndGet()
        synchronized(lock) {
            auditionTrack?.let { old -> runCatching { old.pause() }; runCatching { old.flush() }; runCatching { old.stop() }; runCatching { old.release() } }
            auditionTrack = null
        }
        val frequency = if (timbre == "vsco-cello") 130.81 else 261.63
        val noteDuration = if (timbre == "vsco-harp") 2.2 else 3.6
        // 单音色试听不属于任何编排层。已登记音色必须原样试听；只有旧版自由文本才需要
        // 借 notes 层规则归一化，否则钢琴等真实采样会因伪造的层类型退化成 air-pad。
        val normalizedTimbre = if (GENERATED_TIMBRES.any { it.id == timbre }) {
            timbre
        } else {
            normalizeGeneratedTimbre(timbre, "notes")
        }
        if (normalizedTimbre in SAMPLE_TIMBRES && !isSampleTimbreLoaded(normalizedTimbre)) {
            synchronized(lock) { if (running.get()) stopLocked() }
            ensureVscoSamplesLoaded(setOf(normalizedTimbre))
        }
        val totalFrames = (5.0 * SAMPLE_RATE).toInt()
        val mono = synchronized(nativeLock) {
            NativeAudioCore.renderPreviewVoice(normalizedTimbre, frequency, noteDuration, 0.18, 2.4, 0.0, 1800.0, 0.4, 0.5, 0.12, 0x5eed, totalFrames)
        }
        if (mono.size < totalFrames) error("音色试听渲染数据不完整")
        val peak = mono.maxOfOrNull { abs(it) } ?: 0f
        if (peak < 0.0005f) error("该音色未能生成可听声音")
        // 只校准试听链，不改变正式编排的混音平衡。真实钢琴采样通常比合成音色低很多，
        // 统一到克制但清楚的试听峰值，且限制最大补偿以免噪声被过度放大。
        val auditionScale = (0.30f / peak).coerceIn(0.75f, 4.0f)
        if (generation != auditionGeneration.get()) return@withContext
        val audio = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setBufferSizeInBytes(max(AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT), BLOCK_FRAMES * 4))
            .setTransferMode(AudioTrack.MODE_STREAM).build()
        synchronized(lock) {
            if (generation != auditionGeneration.get()) {
                runCatching { audio.release() }
                return@withContext
            }
            auditionTrack = audio
        }
        try {
            audio.play()
            val pcm = ShortArray(BLOCK_FRAMES * 2)
            var frame = 0
            while (frame < totalFrames) {
                if (generation != auditionGeneration.get()) break
                val n = minOf(BLOCK_FRAMES, totalFrames - frame)
                for (i in 0 until n) {
                    val v = mono[frame + i] * auditionScale
                    pcm[i * 2] = (v.coerceIn(-1f, 1f) * 32767).toInt().toShort()
                    pcm[i * 2 + 1] = pcm[i * 2]
                }
                val written = audio.write(pcm, 0, n * 2, AudioTrack.WRITE_BLOCKING)
                if (written < 0 || written < n * 2) break
                frame += n
            }
        } finally {
            synchronized(lock) { if (auditionTrack === audio) auditionTrack = null }
            runCatching { audio.stop() }
            runCatching { audio.release() }
        }
    }

    private fun stopLocked() {
        auditionGeneration.incrementAndGet()
        renderGeneration.incrementAndGet()
        running.set(false); paused.set(false)
        track?.pause(); track?.flush()
        worker?.interrupt(); worker?.takeUnless { it === Thread.currentThread() }?.join(350)
        worker = null
        track?.release(); track = null
        auditionTrack?.let { runCatching { it.stop() }; runCatching { it.release() } }
        auditionTrack = null
        if (engine != 0L) {
            synchronized(nativeLock) { NativeAudioCore.destroyGenerativeEngine(engine) }
            engine = 0L
        }
    }

    private fun isSampleTimbreLoaded(timbre: String): Boolean = timbre in loadedSampleTimbres

    private fun ensureVscoSamplesLoaded(required: Set<String>) {
        if (required.isEmpty() || required.all(::isSampleTimbreLoaded)) return
        synchronized(sampleRegistryLock) {
            if (!sampleRegistryInitialized) {
                synchronized(nativeLock) { NativeAudioCore.clearSamples() }
                sampleRegistryInitialized = true
            }
        }
        // A foreground play request must not wait for an unrelated full-pack preload.
        // Registration remains serialized per timbre and all JNI access remains under nativeLock.
        for (timbre in required) {
            val timbreLock = sampleTimbreLocks.getOrPut(timbre) { Any() }
            synchronized(timbreLock) {
                if (timbre !in loadedSampleTimbres) {
                    val refs = SAMPLE_TIMBRES[timbre]
                    var registered = false
                    for (ref in refs.orEmpty()) {
                        val result = runCatching {
                            val bytes = appContext.assets.open(ref.path).use { it.readBytes() }
                            synchronized(nativeLock) { NativeAudioCore.registerWavSample(timbre, ref.frequency, bytes) }
                        }
                        if (result.getOrDefault(-1) >= 0) registered = true
                    }
                    if (registered) loadedSampleTimbres += timbre
                }
            }
        }
    }

    private fun loadPresetTrims(): Map<String, Double> = runCatching {
        val root = appContext.assets.open("loudness_manifest.json").bufferedReader().use { JSONObject(it.readText()) }
        val presets = root.getJSONObject("presets")
        buildMap {
            presets.keys().forEach { id ->
                val trimDb = presets.getJSONObject(id).optDouble("trimDb", 0.0).coerceIn(-24.0, 24.0)
                put(id, 10.0.pow(trimDb / 20.0))
            }
        }
    }.getOrDefault(emptyMap())

    /** Semantic volume remains 0..1; reviewed loudness trim is a separate gain stage. */
    private fun effectiveOutputVolume(): Double =
        (outputVolume * currentPresetTrim).coerceIn(0.0, MAX_CALIBRATED_OUTPUT)

    /**
     * 编排数据刻意不暴露运行时 intro 开关。官方预设在 C 内已有首发层；DIY 替换层时，
     * 为第一个启用的音乐层补一个内部标记，保证试听草稿立即给出一次克制的声音确认。
     */
    private fun GeneratedArrangement.withIntroLayer(): GeneratedArrangement {
        val hasSolo = layers.any { it.enabled && it.solo && it.type !in setOf("ambient", "modulation") }
        val first = layers.indexOfFirst {
            it.enabled && it.type !in setOf("ambient", "modulation") && (!hasSolo || it.solo)
        }
        if (first < 0) return this
        return copy(layers = layers.mapIndexed { index, layer ->
            if (index == first) layer.copy(entryDelaySeconds = INTRO_MARKER_SECONDS) else layer
        })
    }

    /** 对照 NativeGeneratedAudioRenderer 的 AmbientBias.from：level + 水/风/暖/城/噪 六个分量。 */
    private fun ambientBias(items: List<AmbientSound>): DoubleArray {
        fun strength(pattern: Regex): Double {
            val total = items.filter { it.active && pattern.containsMatchIn("${it.name} ${it.category}") }
                .sumOf { (it.volume * it.volume).toDouble() }
            return sqrt(total).coerceAtMost(1.0)
        }
        val level = sqrt(items.filter { it.active }.sumOf { (it.volume * it.volume).toDouble() }).coerceAtMost(1.0)
        return doubleArrayOf(
            level,
            strength(Regex("雨|水|海|浪|溪|河|瀑|潮|rain|water|ocean")),
            strength(Regex("风|森林|树|鸟|丛林|wind|forest|bird")),
            strength(Regex("火|咖啡|壁炉|餐厅|键盘|纸|fire|cafe")),
            strength(Regex("车|列车|交通|城市|街|地铁|机场|train|city|traffic")),
            strength(Regex("噪|白噪|粉噪|棕噪|noise")),
        )
    }

    /** 对照 NativeGeneratedAudioRenderer 的 GeneratedLayer.toNative()：字段映射到 native 的 int 枚举序号。 */
    private fun GeneratedLayer.toNativeLayer() = NativeAudioCore.NativeGenerativeLayer(
        id = id,
        kind = when (type) { "rhythm" -> 1; "atmosphere" -> 2; "voice" -> 3; "ambient", "modulation" -> 4; else -> 0 },
        timbre = normalizeGeneratedTimbre(timbre, type),
        volume = volume, probability = probability, density = density,
        duration = durationSeconds.toDouble(), attack = fadeInSeconds.toDouble(), release = fadeOutSeconds.toDouble(),
        pan = pan / 100.0, entryDelay = entryDelaySeconds.toDouble(),
        rhythm = when (rhythm) { "whole" -> 16; "half" -> 8; "quarter" -> 4; "eighth" -> 2; else -> 1 },
        relationship = when (relationship) { "follow" -> 1; "avoid" -> 2; "alternate" -> 3; else -> 0 },
        target = relationshipTargetId,
        register = when (register) { "low" -> 0; "high" -> 2; "wide" -> 3; else -> 1 },
        scale = when (scale) { "major" -> 1; "minor" -> 2; "dorian" -> 3; else -> 0 },
        ambientResponse = ambientResponse, enabled = enabled, solo = solo,
        intro = entryDelaySeconds == INTRO_MARKER_SECONDS,
    )

    private fun arcOf(arc: String): Int = when (arc.uppercase()) { "STEADY" -> 1; "JOURNEY" -> 2; else -> 0 }

    companion object {
        const val SAMPLE_RATE = 44_100
        const val BLOCK_FRAMES = 512
        const val AUDIBLE_FLOOR = 32
        const val WRITE_INTERRUPTED = Int.MIN_VALUE
        // Every arrangement receives an explicit intro event. If that event is still inaudible
        // after 1.5 s, fail fast so the failover renderer can recover instead of leaving a
        // misleading "playing" state for six seconds.
        const val SILENCE_TIMEOUT_FRAMES = SAMPLE_RATE * 3L / 2L
        const val INTRO_MARKER_SECONDS = -0.001f
        const val MAX_CALIBRATED_OUTPUT = 16.0
    }
}
