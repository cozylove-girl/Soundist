package com.soundist.app

import android.util.Log
import com.soundist.core.audio.Media3AudioEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 环境声后端失败类别（结构化错误，不依赖单一 sticky String）。 */
enum class AmbientFailureKind { INIT, START, DECODE, COMMAND, RETIRE_OVERFLOW }

/** 环境声后端结构化错误。 */
data class AmbientBackendError(val kind: AmbientFailureKind, val message: String, val nativeCode: Int = 0)

/** 音量模型输入（master×ambient×duck×muted），供 controller 的 state+duck 流驱动后端。 */
data class AmbientVolumeInput(val master: Float, val ambient: Float, val duck: Float, val muted: Boolean)

/**
 * 环境声混音后端抽象（阶段 B）。
 *
 * 两个实现：[Media3AmbientMixer]（默认，复用已验证的 Media3 环境声路径，行为逐字不变）与
 * [MiniaudioAmbientMixer]（miniaudio 原生混音，feature flag 后）。宿主通过
 * [MiniaudioFeatureFlags] 选择，flag 关闭时始终走 Media3（可快速回退，不产生双引擎同时播放）。
 *
 * 音量模型：每个实现负责「master × duck × muted」的最终增益（per-track 音量在 [reconcile] 给定）。
 * Media3 实现把 duck 交给引擎内部焦点管理；miniaudio 实现用 [applyVolume] 的 duck 参数显式算 master 增益。
 * fade 语义：用户音量与普通启停只做约 10–50ms 防爆音平滑；睡眠等明确的长时渐弱由
 * Media3AudioEngine.fadeTo() 单独负责，不能在逐轨包络里重复叠加。
 *
 * 生命周期：reconcile/play/pause/stop/release 必须由实现串行化（miniaudio 用同一把 Mutex），
 * stop/release 幂等，且不得在 reconcile 的 delay/解码过程中并发修改内部状态。
 */
interface AmbientMixer {
    /** 环境声是否正在出声。 */
    val playing: Boolean

    /** 最近一次失败的结构化错误；null = 无错误。宿主据此自动回退或上报。 */
    val lastError: AmbientBackendError? get() = null

    /** 同步环境声轨集合（soundId → per-track volume 0..1）：新增/更新/移除。 */
    suspend fun reconcile(
        tracks: Map<String, Float>,
        transitionMillis: Long = 48L,
        resolveUri: suspend (String) -> String?,
    )

    /** 同步逐轨声像；未提供的轨道回到中间。 */
    suspend fun setPans(pans: Map<String, Float>)

    /** 应用全局音量：master（全局）× ambient（环境）× duck（焦点压缩）× muted。 */
    fun applyVolume(master: Float, ambient: Float, duck: Float, muted: Boolean)

    /** 播放（fadeSeconds 淡入）。 */
    suspend fun play(fadeSeconds: Float)

    /** 暂停（fadeSeconds 淡出）。 */
    suspend fun pause(fadeSeconds: Float)

    /** 停止并清空所有轨道与设备（幂等）。 */
    suspend fun stop()

    suspend fun release()
}

/**
 * 环境声后端 feature flag。默认取 BuildConfig（Release 恒 false = Media3 安全回退）。
 * Debug 可在启动前把 [ambientEnabled] 置 true 手动启用 miniaudio；在一次播放会话内固定，
 * 禁止播放途中无序切换后端（MainActivity 只在 composition 初始化时读取一次）。
 */
object MiniaudioFeatureFlags {
    @Volatile var ambientEnabled: Boolean = BuildConfig.USE_MINIAUDIO_AMBIENT
    /** 生成电台 native 引擎后端（对照 USE_NATIVE_GENERATIVE）。默认开启；出问题翻 false 回退 Kotlin 渲染器。 */
    @Volatile var generativeNativeEnabled: Boolean = BuildConfig.USE_NATIVE_GENERATIVE
}

/** 环境声 master 增益模型（纯函数，可单测）：master × ambient × duck × muted（muted→0），clamp [0,1]。
 *  与原生 MasterChain 的 SET_MASTER_GAIN 目标一致。 */
fun ambientMasterGain(master: Float, ambient: Float, duck: Float, muted: Boolean): Float =
    if (muted) 0f else (master * ambient * duck).coerceIn(0f, 1f)

/** 原生 MasterChain 的一阶指数 ramp 系数（纯函数，可单测，对应 soundist_ramp_coeff）。
 *  在 blockSeconds 内以时间常数 tauSeconds 逼近目标，与 block 尺寸/sampleRate 无关。 */
fun rampCoefficient(blockSeconds: Float, tauSeconds: Float): Float =
    if (tauSeconds <= 0f) 1f else (1f - kotlin.math.exp((-blockSeconds / tauSeconds).toDouble())).toFloat()

/* -------------------------------------------------------------------------------------- */

/**
 * 默认实现：把 [AmbientMixer] 语义回落到 Media3AudioEngine 环境声 API。
 * 普通播放/暂停只经过引擎的短防爆音包络；睡眠定时仍走独立长渐弱。
 */
class Media3AmbientMixer(private val engine: Media3AudioEngine) : AmbientMixer {
    override val playing: Boolean get() = engine.state.value.ambientPlaying

    override suspend fun reconcile(
        tracks: Map<String, Float>,
        transitionMillis: Long,
        resolveUri: suspend (String) -> String?,
    ) {
        val current = engine.state.value.tracks.map { it.soundId }.toSet()
        val removed = current - tracks.keys
        // Start additions/updates and fade removals during the same window so a
        // channel/personal switch is a true crossfade rather than a silent gap.
        for ((soundId, volume) in tracks) {
            val uri = resolveUri(soundId) ?: continue
            engine.setTrack(soundId, uri, volume)
        }
        removed.forEach { engine.setVolume(it, 0f) }
        if (removed.isNotEmpty()) delay(transitionMillis.coerceIn(40L, 80L))
        removed.forEach { engine.removeTrack(it) }
    }

    override suspend fun setPans(pans: Map<String, Float>) {
        withContext(Dispatchers.Main.immediate) {
            engine.setAmbientPans(pans.mapValues { (_, value) -> value.coerceIn(-100f, 100f) })
        }
    }

    override fun applyVolume(master: Float, ambient: Float, duck: Float, muted: Boolean) {
        // Media3 引擎内部 applyVolumes() 已按 master×ambient×duck×muted 自行驱动 ExoPlayer 音量，
        // 后端无需再推。这里为 no-op（controller 的 state+duck 流对 Media3 冗余但无害）。
    }

    override suspend fun play(fadeSeconds: Float) {
        withContext(Dispatchers.Main.immediate) {
            engine.cancelVolumeAutomation()
            engine.playAmbient()
        }
    }

    override suspend fun pause(fadeSeconds: Float) {
        withContext(Dispatchers.Main.immediate) {
            engine.cancelVolumeAutomation()
            engine.pauseAmbient()
        }
    }

    override suspend fun stop() = withContext(Dispatchers.Main.immediate) { engine.pauseAmbient() }
    override suspend fun release() = Unit /* 引擎由 Media3AudioRuntime 统一持有，不在此释放 */
}

/* -------------------------------------------------------------------------------------- */

/**
 * miniaudio 原生环境声混音（阶段 B）。
 *
 * - [reconcile] 在 IO 线程加载资产字节 → 经 [NativeAudioCore.addTrackFromEncoded] 让原生
 *   ma_decoder 解码到设备格式并注册为循环轨（native-owned + retire-ack）。
 * - 音量模型：master 增益 = master × duck × muted，经 [NativeAudioCore.setMasterGain] 写入
 *   MasterChain（原生一阶 ramp）；per-track 增益 = track volume。
 * - 暂停/删除/替换用原生 ~30ms 时间常数 ramp（满足 30–80ms fade），删除前先把轨道增益
 *   ramp 到 0 再移除，避免爆音。
 * - 内存：单轨解码帧数上限在 C 侧（约 2 分钟），总编码字节数在 [totalEncodedBytes] 封顶；
 *   已加载且 URI 未变的轨道复用（去重），删除/替换经 retire-ack 释放。
 */
class MiniaudioAmbientMixer(
    private val assetLoader: suspend (String) -> ByteArray?,
) : AmbientMixer {
    /** 已加载编码音频总字节数上限（约 48MB），控制总 decoded PCM 内存。 */
    private val MAX_TOTAL_ENCODED_BYTES = 48L * 1024 * 1024

    private val mutex = Mutex()
    private val trackIds = HashMap<String, Int>()          // soundId → 原生轨道 id
    private val trackUris = HashMap<String, String>()      // soundId → 已加载 asset URI（去重）
    private val trackEncodedSizes = HashMap<String, Int>() // soundId → 当前轨道编码字节数
    private val freeIds = ArrayDeque<Int>((0 until NativeAudioCore.MAX_TRACKS).toList())
    @Volatile private var master = 0.8f
    @Volatile private var ambient = 1f
    @Volatile private var duck = 1f
    @Volatile private var muted = false
    @Volatile private var started = false
    @Volatile private var isPlaying = false
    @Volatile private var failed = false
    @Volatile private var totalEncodedBytes = 0L

    override val playing: Boolean get() = isPlaying

    @Volatile override var lastError: AmbientBackendError? = null
        private set

    private fun fail(kind: AmbientFailureKind, message: String, code: Int = 0) {
        if (failed) return /* 只记录第一个错误，避免被后续级联错误覆盖 */
        failed = true
        lastError = AmbientBackendError(kind, message, code)
        Log.e("SoundistMiniaudio", message)
    }

    /** 检查一个关键 native 命令返回值；非 0 记录命令错误并返回 false。 */
    private fun commandOk(rc: Int, op: String): Boolean {
        if (rc == 0) return true
        fail(AmbientFailureKind.COMMAND, "native $op 返回 $rc：${NativeAudioCore.lastErrorMessage()}", rc)
        return false
    }

    private fun ensureStarted(): Boolean {
        if (started) return true
        if (failed) return false
        if (!NativeAudioCore.init()) {
            fail(AmbientFailureKind.INIT, "miniaudio 初始化失败：${NativeAudioCore.lastErrorMessage()}")
            return false
        }
        if (NativeAudioCore.startDevice(48_000, 2) != 0) {
            fail(AmbientFailureKind.START, "miniaudio 设备启动失败：${NativeAudioCore.lastErrorMessage()}")
            return false
        }
        started = true
        return true
    }

    private fun applyMasterGainLocked() {
        commandOk(NativeAudioCore.setMasterGain(ambientMasterGain(master, ambient, duck, muted)), "setMasterGain")
    }

    override suspend fun reconcile(
        tracks: Map<String, Float>,
        transitionMillis: Long,
        resolveUri: suspend (String) -> String?,
    ) = mutex.withLock {
        if (!ensureStarted()) return@withLock
        // 1. 移除不再需要的轨道（约 40–80ms 交叉淡化后再移除，避免爆音）。
        val removed = trackIds.keys - tracks.keys
        if (removed.isNotEmpty()) {
            removed.forEach { soundId -> trackIds[soundId]?.let { commandOk(NativeAudioCore.setTrackGain(it, 0f), "setTrackGain") } }
            delay(transitionMillis.coerceIn(40L, 80L))
            removed.forEach { soundId ->
                trackIds.remove(soundId)?.let { id ->
                    commandOk(NativeAudioCore.removeTrack(id), "removeTrack")
                    freeIds.addLast(id)
                }
                trackUris.remove(soundId)
                totalEncodedBytes = (totalEncodedBytes - (trackEncodedSizes.remove(soundId) ?: 0)).coerceAtLeast(0L)
            }
        }
        // 2. 新增/更新轨道（已加载且 URI 未变的复用，不重复解码）。
        for ((soundId, volume) in tracks) {
            val vol = volume.coerceIn(0f, 1f)
            val existing = trackIds[soundId]
            if (existing != null) {
                commandOk(NativeAudioCore.setTrackGain(existing, vol), "setTrackGain")
                continue
            }
            val uri = resolveUri(soundId)
            if (uri == null) {
                fail(AmbientFailureKind.DECODE, "环境声「$soundId」没有可用资源 URI")
                continue
            }
            if (trackUris[soundId] == uri) continue /* URI 未变但轨道丢失（不应发生），跳过避免重复解码 */
            val bytes = withContext(Dispatchers.IO) { assetLoader(uri) }
            if (bytes == null) {
                fail(AmbientFailureKind.DECODE, "环境声「$soundId」资源读取失败")
                continue
            }
            if (totalEncodedBytes + bytes.size > MAX_TOTAL_ENCODED_BYTES) {
                fail(AmbientFailureKind.DECODE, "环境声「$soundId」超出总解码内存上限（${bytes.size}B）")
                return@withLock
            }
            val id = freeIds.removeFirstOrNull()
            if (id == null) { fail(AmbientFailureKind.DECODE, "环境声轨数超上限 ${NativeAudioCore.MAX_TRACKS}"); return@withLock }
            val rc = NativeAudioCore.addTrackFromEncoded(id, bytes)
            if (rc != 0) {
                freeIds.addLast(id)
                fail(AmbientFailureKind.DECODE, "环境声「$soundId」解码失败：${NativeAudioCore.lastErrorMessage()}", rc)
                continue
            }
            commandOk(NativeAudioCore.setTrackGain(id, vol), "setTrackGain")
            commandOk(NativeAudioCore.setTrackPlaying(id, isPlaying), "setTrackPlaying")
            trackIds[soundId] = id
            trackUris[soundId] = uri
            trackEncodedSizes[soundId] = bytes.size
            totalEncodedBytes += bytes.size
        }
    }

    override suspend fun setPans(pans: Map<String, Float>) = mutex.withLock {
        trackIds.forEach { (soundId, id) ->
            commandOk(NativeAudioCore.setTrackPan(id, (pans[soundId] ?: 0f).coerceIn(-100f, 100f) / 100f), "setTrackPan")
        }
    }

    override fun applyVolume(master: Float, ambient: Float, duck: Float, muted: Boolean) {
        this.master = master.coerceIn(0f, 1f)
        this.ambient = ambient.coerceIn(0f, 1f)
        this.duck = duck.coerceIn(0f, 1f)
        this.muted = muted
        if (!failed) {
            commandOk(NativeAudioCore.setMasterGain(ambientMasterGain(this.master, this.ambient, this.duck, this.muted)), "setMasterGain")
        }
    }

    override suspend fun play(fadeSeconds: Float) = mutex.withLock {
        if (!ensureStarted()) return@withLock
        isPlaying = true
        trackIds.values.forEach { commandOk(NativeAudioCore.setTrackPlaying(it, true), "setTrackPlaying") }
        applyMasterGainLocked() /* 原生 MasterChain 从当前值 ramp 到目标（~30ms），无爆音 */
    }

    override suspend fun pause(fadeSeconds: Float) = mutex.withLock {
        if (!isPlaying) return@withLock
        isPlaying = false
        NativeAudioCore.setMasterGain(0f) /* 先淡出（~30ms），再停轨道，避免 click */
        delay(80)
        trackIds.values.forEach { commandOk(NativeAudioCore.setTrackPlaying(it, false), "setTrackPlaying") }
    }

    override suspend fun stop() = mutex.withLock {
        isPlaying = false
        trackIds.values.forEach { NativeAudioCore.removeTrack(it) } /* 清理，忽略返回码（幂等） */
        trackIds.clear()
        trackUris.clear()
        trackEncodedSizes.clear()
        freeIds.clear()
        (0 until NativeAudioCore.MAX_TRACKS).forEach { freeIds.addLast(it) }
        totalEncodedBytes = 0L
        if (started) {
            NativeAudioCore.stopDevice()
            started = false
        }
    }

    override suspend fun release() = stop()
}

/** 从 asset URI（asset:///...）读取字节。 */
suspend fun loadAssetBytes(assets: android.content.res.AssetManager, uri: String): ByteArray? =
    withContext(Dispatchers.IO) {
        val path = uri.removePrefix("asset:///").removePrefix("asset://")
        runCatching { assets.open(path).use { it.readBytes() } }.getOrNull()
    }
