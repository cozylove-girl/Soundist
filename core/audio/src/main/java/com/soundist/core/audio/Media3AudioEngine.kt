package com.soundist.core.audio

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.soundist.core.model.MixTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

/** Multi-player engine for true concurrent ambience plus an independent radio stream. */
class Media3AudioEngine(private val context: Context) : AudioEngine {
    // Frontend ambient-track defaults (App.tsx `ambientTrackFromSound`): 3 s fade-in, 4 s fade-out.
    // Slider/master changes need anti-click smoothing, not a perceptible scene fade. Long fades
    // are driven explicitly by fadeTo() (sleep timer / deliberate transitions).
    // Ordinary slider movement needs only a very short anti-click ramp. 12 ms reaches the
    // target in roughly 30-45 ms with the 16 ms envelope tick, so it cannot feel like a fade.
    // Deliberate sleep/transition fades continue to use fadeTo() and are unaffected.
    private val DEFAULT_FADE_IN = 0.016f
    private val DEFAULT_FADE_OUT = 0.016f
    private val attributes = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build()
    private val dataSourceFactory = DefaultDataSource.Factory(context)
    private val ambient = linkedMapOf<String, ExoPlayer>()
    /** soundId → 当前已载入该播放器的规范化 asset URI；未变则不重复 setMediaItem。 */
    private val ambientUris = linkedMapOf<String, String>()
    /** 每个环境声轨播放器独立的声像处理器（声像直接作用于左右声道）。 */
    private val ambientPans = linkedMapOf<String, StereoPanAudioProcessor>()
    private val playerHandler = Handler(Looper.getMainLooper())
    private val automationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var volumeAutomation: Job? = null
    private val interruptions = AudioInterruptionManager(context, ::onAudioFocusChange, ::pauseForNoisy, ::onFocusGranted)
    private var resumeAfterTransientLoss = false
    private val _duckMultiplier = MutableStateFlow(1f)
    private var externalController: ExternalPlaybackController? = null

    /** 环境声后端中断监听器（miniaudio 后端需要；Media3 环境声在引擎内部已处理焦点）。 */
    var ambientInterruptionListener: AmbientInterruptionListener? = null

    /** 焦点压缩系数（可观察 StateFlow，供外部后端如 miniaudio 实时同步「master×duck×muted」音量模型）。 */
    val duckMultiplier: StateFlow<Float> = _duckMultiplier
    private val mutable = MutableStateFlow(AudioState())
    override val state = mutable.asStateFlow()
    @Volatile var ambientGraphController: AmbientGraphController? = null

    // ── 电台播放列表（引擎内部坐标）──────────────────────────────────────────
    private data class RadioEntry(val id: String, val stationIndex: Int, val uri: String)

    @Volatile private var radio: ExoPlayer = player()
    private var radioStandby: ExoPlayer? = null
    private var radioTracks: List<RadioEntry> = emptyList()
    private var radioTransitionMode = "natural"
    private var radioTransitionSeconds = 0.0
    private val radioFailed = mutableSetOf<String>()
    /** 用户是否期望电台出声；跨曲目推进保持，pause/stop 时清 false。 */
    private var radioIntent = false
    /** 过渡（gapless 预载 / crossfade 叠加）进行中。 */
    private var transitionActive = false
    private var transitionJob: Job? = null
    private var standbyPreparedFor: Int? = null

    /** 电台真实状态回流：全部状态来自 ExoPlayer 播放器事件，绝不在点击时乐观置位 PLAYING。 */
    private val radioListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) = syncRadioFromPlayer(playbackState)
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = syncRadioFromPlayer(radio.playbackState)
        // 只有播放器真实开始输出音频（player.isPlaying == true）才进入 PLAYING。
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (radioTracks.isEmpty()) return
            if (isPlaying) publishRadio(RadioPlaybackState.PLAYING)
            else syncRadioFromPlayer(radio.playbackState)
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (radioTracks.isEmpty()) return
            val index = radioTracks.indexOfFirst { it.id == mediaItem?.mediaId }
            if (index >= 0) updateRadioIndex(index)
        }
        override fun onPlayerError(error: PlaybackException) = handleRadioError(error)
    }

    /** 每个环境声轨播放器的失败监听：音频文件解码失败 → ambientErrors 回流（不是 UI 假播放）。 */
    private fun ambientListener(soundId: String) = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) = handleAmbientError(soundId, error)
    }

    /** 记录环境声轨失败（soundId → 可理解错误信息）。不终止整个环境声会话：其它声音继续。 */
    private fun handleAmbientError(soundId: String, error: PlaybackException) {
        android.util.Log.e("SoundistAmbient", "ambient error soundId=$soundId errorCode=${error.errorCode} msg=${error.message}", error)
        if (mutable.value.ambientErrors.containsKey(soundId)) return
        mutable.value = mutable.value.copy(ambientErrors = mutable.value.ambientErrors + (soundId to ambientErrorMessage(error)))
    }

    private fun ambientErrorMessage(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "无法找到该声音文件"
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_INIT_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED -> "无法解码该声音文件"
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_NOT_SUPPORTED -> "该声音格式不受支持"
        else -> "无法播放该声音"
    }

    // Per-track anti-click envelope. Deliberate long fades use fadeTo(); applying another
    // 3–4 second envelope here made every slider movement visibly and audibly lag behind.
    private val perTrackTarget = mutableMapOf<String, Float>()
    private val perTrackCurrent = mutableMapOf<String, Float>()
    private val trackPan = mutableMapOf<String, Float>()
    private val envelopeJob: Job = automationScope.launch {
        while (true) {
            delay(16)
            stepTrackEnvelope()
            maybeStartRadioTransition()
        }
    }

    /** Media3 players in this engine are created on the main looper and must stay there. */
    private fun dispatchToPlayerThread(action: () -> Unit): Boolean {
        if (Looper.myLooper() == playerHandler.looper) return false
        playerHandler.post(action)
        return true
    }

    private fun player(panProcessor: StereoPanAudioProcessor? = null) = ExoPlayer.Builder(context)
        .setRenderersFactory(if (panProcessor != null) PannableRenderersFactory(context, panProcessor) else DefaultRenderersFactory(context))
        .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)).build().apply {
            // One engine owns focus for the whole ambient+radio graph; individual players must not compete.
            setAudioAttributes(attributes, false)
        }

    override suspend fun setTrack(soundId: String, assetUri: String, volume: Float) = withContext(Dispatchers.Main.immediate) {
        val normalized = volume.coerceIn(0f, 1f)
        val item = mutable.value.tracks.firstOrNull { it.soundId == soundId }
        if (normalized <= 0f) {
            // Frontend skips tracks whose volume is <= 0 (App.tsx: `if (!track.active || track.volume <= 0) return`):
            // they are not part of the audio graph. Keep the track in state (volume 0) so the UI
            // still lists it, but do not create a player; any existing player is faded out and
            // released by the envelope below.
            mutable.value = mutable.value.copy(
                tracks = mutable.value.tracks.filterNot { it.soundId == soundId } + (item ?: MixTrack(soundId, 0f)).copy(volume = 0f, enabled = true),
            )
            perTrackTarget[soundId] = 0f
            return@withContext
        }
        val fresh = ambient[soundId] == null
        val soundPlayer = ambient.getOrPut(soundId) {
            // `volume` is getter-only on Player, so use the explicit setter.
            // 每个环境声轨播放器独立挂 Player.Listener：音频文件解码/载入失败经 ambientErrors 回流 UI，
            // 声音不能继续显示「播放中」（电台路径已有独立回流，见 radioListener）。
            player(panProcessor = StereoPanAudioProcessor().also { ambientPans[soundId] = it }).apply {
                setRepeatMode(Player.REPEAT_MODE_ONE)
                setVolume(0f)
                addListener(ambientListener(soundId))
            }
        }
        // 创建播放器时应用此前记录的声像（setTrackPan 先于 setTrack 到达的情况）。
        ambientPans[soundId]?.setPan((trackPan[soundId] ?: 0f) / 100f)
        if (fresh) perTrackCurrent[soundId] = 0f
        val normalizedUri = normalizeAssetUri(assetUri)
        // 同一 soundId 的 asset URI 未变则复用已载入的媒体项，不重复 setMediaItem。
        if (ambientUris[soundId] != normalizedUri) {
            soundPlayer.setMediaItem(MediaItem.fromUri(normalizedUri))
            soundPlayer.prepare()
            ambientUris[soundId] = normalizedUri
        }
        val next = (item ?: MixTrack(soundId, volume)).copy(volume = normalized, enabled = true)
        mutable.value = mutable.value.copy(tracks = mutable.value.tracks.filterNot { it.soundId == soundId } + next)
        applyVolumes()
        if (mutable.value.ambientPlaying && mutable.value.masterPlaying) soundPlayer.play()
    }

    /** Restores the composition without autoplay; a cold start never begins audio without a user gesture. */
    suspend fun restoreComposition(tracks: List<MixTrack>, masterVolume: Float) = withContext(Dispatchers.Main.immediate) {
        setMasterVolume(masterVolume)
        tracks.filter { it.enabled }.forEach { track ->
            val sound = com.soundist.core.model.SoundCatalog.sounds.firstOrNull { it.id == track.soundId } ?: return@forEach
            setTrack(track.soundId, sound.assetUri, track.volume)
        }
        pauseAmbient()
    }

    override suspend fun removeTrack(soundId: String) = withContext(Dispatchers.Main.immediate) {
        ambient.remove(soundId)?.release()
        ambientUris.remove(soundId)
        ambientPans.remove(soundId)
        perTrackCurrent.remove(soundId)
        perTrackTarget.remove(soundId)
        trackPan.remove(soundId)
        if (mutable.value.ambientErrors.containsKey(soundId)) {
            mutable.value = mutable.value.copy(ambientErrors = mutable.value.ambientErrors - soundId)
        }
        mutable.value = mutable.value.copy(tracks = mutable.value.tracks.filterNot { it.soundId == soundId })
    }

    override suspend fun setVolume(soundId: String, volume: Float) = withContext(Dispatchers.Main.immediate) {
        mutable.value = mutable.value.copy(tracks = mutable.value.tracks.map { if (it.soundId == soundId) it.copy(volume = volume.coerceIn(0f, 1f)) else it })
        applyVolumes()
    }

    // ── 电台：播放列表 + 真实状态 ─────────────────────────────────────────────

    /**
     * 以播放列表方式播放电台。自动连播、失败跳曲与 transition 是播放器内部逻辑。
     *
     * @param tracks 仅包含可播放源（不可播放的曲目由宿主剔除，并记录对应错误）。
     * @param startIndex 频道坐标（station.tracks / station.localAudio）下的起始曲目。
     * @param transitionMode natural / gapless / crossfade（App.tsx radioCatalog channel.transition.mode）。
     * @param transitionSeconds 过渡时长（秒）。
     * @param autoplay 是否直接开始播放（false 时仅准备，由 playRadio() 接管，供淡入使用）。
     */
    suspend fun playRadioPlaylist(
        tracks: List<RadioTrackSource>,
        startIndex: Int,
        transitionMode: String,
        transitionSeconds: Double,
        autoplay: Boolean = false,
    ) = withContext(Dispatchers.Main.immediate) {
        require(tracks.isNotEmpty()) { "电台没有可播放的音源" }
        cancelRadioTransition()
        radio.removeListener(radioListener)
        radio.stop()
        radioStandby?.stop()
        radioTracks = tracks.map { RadioEntry(it.id, it.stationIndex, it.uri) }
        radioFailed.clear()
        radioTransitionMode = transitionMode.ifBlank { "natural" }
        radioTransitionSeconds = transitionSeconds.coerceAtLeast(0.0)
        radioIntent = autoplay
        radio.addListener(radioListener)
        radio.setRepeatMode(if (radioTracks.size <= 1) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF)
        // 优先播放请求的曲目本身；不可播放（未安装/已被剔除/已失败）时从其后的第一首可播曲目开始
        // （前端 handleRadioDeckFailure → nextPlayableRadioIndex 语义）。
        val start = radioTracks.indexOfFirst { it.stationIndex == startIndex }.takeIf { it >= 0 }
        val first = when {
            start != null && radioTracks[start].id !in radioFailed -> start
            start != null -> firstPlayableIndex(start)
            else -> radioTracks.indices.firstOrNull { radioTracks[it].stationIndex > startIndex && radioTracks[it].id !in radioFailed }
                ?: radioTracks.indices.firstOrNull { radioTracks[it].id !in radioFailed }
        }
        if (first == null) {
            stopRadioWithError("该频道的音源暂时都无法载入。请稍后重试，或选择本地音频与生成电台", RadioErrorType.PACK_NOT_INSTALLED)
            return@withContext
        }
        val entry = radioTracks[first]
        if (entry.uri.startsWith("file://")) {
            val path = entry.uri.removePrefix("file://")
            if (!java.io.File(path).exists()) {
                radioFailed += entry.id
                val next = firstPlayableIndex(first)
                if (next == null) {
                    stopRadioWithError(radioErrorMessage(RadioErrorType.FILE_MISSING, entry), RadioErrorType.FILE_MISSING)
                    return@withContext
                }
                startRadioTrack(next)
                publishRadioError(radioErrorMessage(RadioErrorType.FILE_MISSING, entry, transient = true), RadioErrorType.FILE_MISSING)
                return@withContext
            }
        }
        startRadioTrack(first)
    }

    /** 播放器内部使用的旧接口（单曲）。 */
    override suspend fun playRadio(uri: String) = withContext(Dispatchers.Main.immediate) {
        playRadioPlaylist(listOf(RadioTrackSource(uri, 0, uri)), 0, "natural", 0.0, autoplay = false)
    }

    override fun playRadio() {
        if (dispatchToPlayerThread { playRadio() }) return
        if (mutable.value.radioUri == null && radioTracks.isEmpty()) { mutable.value = mutable.value.copy(radioPlaying = false, error = "No radio station selected"); return }
        if (!interruptions.acquire()) {
            // 焦点被系统拒绝是终态错误：回流 ERROR，UI 不得显示「正在播放」。
            stopRadioWithError("音频焦点被系统拒绝，无法播放", RadioErrorType.AUDIO_FOCUS_DENIED)
            return
        }
        radioIntent = true
        radio.play()
        // 是否真实播放由 onIsPlayingChanged 事件回流（play() 后不假设成功）。
        mutable.value = mutable.value.copy(masterPlaying = true, error = null)
        // 状态就位后再应用音量：acquire() 内的焦点恢复可能早于 masterPlaying 置位，
        // 这里用最终状态重新计算，避免恢复播放时音量仍带旧 duck 系数/旧的暂停目标。
        applyVolumes()
        interruptions.registerNoisyReceiver()
    }

    override fun pauseRadio() {
        if (dispatchToPlayerThread { pauseRadio() }) return
        radioIntent = false
        cancelRadioTransition()
        radio.pause()
        // 暂停是确定性命令：radio.pause() 同步生效，立即发布 PAUSED 给 UI；播放器事件随后幂等确认。
        publishRadio(RadioPlaybackState.PAUSED)
    }

    /** Notification-level radio control: includes the external generated renderer. */
    fun playRadioGraph() {
        if (dispatchToPlayerThread { playRadioGraph() }) return
        if (mutable.value.externalSelected) {
            if (!interruptions.acquire()) return
            externalController?.resumeExternal()
            applyVolumes()
            interruptions.registerNoisyReceiver()
        } else playRadio()
    }

    fun pauseRadioGraph() {
        if (dispatchToPlayerThread { pauseRadioGraph() }) return
        if (mutable.value.externalSelected) pauseExternalPlayback() else pauseRadio()
    }

    fun playAmbientGraph() {
        if (dispatchToPlayerThread { playAmbientGraph() }) return
        ambientGraphController?.resumeAmbientGraph() ?: playAmbient()
    }

    fun pauseAmbientGraph() {
        if (dispatchToPlayerThread { pauseAmbientGraph() }) return
        ambientGraphController?.pauseAmbientGraph() ?: pauseAmbient()
    }

    fun publishAmbientGraphPlaying(playing: Boolean) {
        if (dispatchToPlayerThread { publishAmbientGraphPlaying(playing) }) return
        mutable.value = mutable.value.copy(
            ambientPlaying = playing,
            masterPlaying = playing || mutable.value.radioPlaying || mutable.value.externalPlaying,
        )
    }

    fun updatePlaybackLabels(ambient: String? = null, radio: String? = null) {
        if (dispatchToPlayerThread { updatePlaybackLabels(ambient, radio) }) return
        mutable.value = mutable.value.copy(
            ambientLabel = ambient ?: mutable.value.ambientLabel,
            radioLabel = radio ?: mutable.value.radioLabel,
        )
    }

    /** 停止电台并清空会话回到 IDLE（区别于暂停：不保留可恢复的播放位置/队列）。 */
    override fun stopRadio() {
        if (dispatchToPlayerThread { stopRadio() }) return
        radioIntent = false
        cancelRadioTransition()
        stopRadioInternal()
    }

    /** 电台状态全部来源于播放器事件；LOADING 仅在开始一首新曲目到 prepare 完成之间使用。 */
    private fun syncRadioFromPlayer(playbackState: Int) {
        if (radioTracks.isEmpty()) return
        if (mutable.value.radioPlayback == RadioPlaybackState.ERROR) return
        when (playbackState) {
            Player.STATE_IDLE -> Unit
            Player.STATE_BUFFERING -> publishRadio(if (radioIntent && radio.playWhenReady) RadioPlaybackState.BUFFERING else RadioPlaybackState.PAUSED)
            // 单纯 STATE_READY 不能算播放中：只有播放器真实开始输出音频（isPlaying）才算 PLAYING。
            Player.STATE_READY -> publishRadio(if (radioIntent && radio.isPlaying) RadioPlaybackState.PLAYING else RadioPlaybackState.PAUSED)
            Player.STATE_ENDED -> handleRadioEnded()
        }
    }

    private fun publishRadio(state: RadioPlaybackState) {
        val playing = state == RadioPlaybackState.PLAYING || state == RadioPlaybackState.BUFFERING || state == RadioPlaybackState.LOADING
        mutable.value = mutable.value.copy(
            radioPlayback = state,
            radioPlaying = playing,
            masterPlaying = playing || mutable.value.ambientPlaying || mutable.value.externalPlaying,
            radioError = if (state == RadioPlaybackState.ERROR) mutable.value.radioError else null,
            radioErrorType = if (state == RadioPlaybackState.ERROR) mutable.value.radioErrorType else null,
        )
    }

    private fun publishRadioError(message: String, type: RadioErrorType) {
        mutable.value = mutable.value.copy(radioError = message, radioErrorType = type)
    }

    private fun updateRadioIndex(index: Int) {
        val entry = radioTracks.getOrNull(index) ?: return
        mutable.value = mutable.value.copy(
            radioTrackIndex = index,
            radioStationIndex = entry.stationIndex,
            radioTrackCount = radioTracks.size,
            radioItemId = entry.id,
            radioUri = entry.uri,
        )
    }

    private fun currentRadioIndex(): Int {
        val mediaId = radio.currentMediaItem?.mediaId
        val byId = radioTracks.indexOfFirst { it.id == mediaId }
        if (byId >= 0) return byId
        return radio.currentMediaItemIndex.coerceIn(0, radioTracks.lastIndex)
    }

    /** 前端 nextPlayableRadioIndex：环绕查找，跳过失败曲目；曲目不足 2 首时返回 null。 */
    private fun firstPlayableIndex(fromIndex: Int): Int? {
        val size = radioTracks.size
        if (size < 2) return if (size == 1 && radioTracks[0].id !in radioFailed) 0 else null
        for (offset in 1..size) {
            val index = (fromIndex + offset) % size
            if (radioTracks[index].id !in radioFailed) return index
        }
        return null
    }

    private fun startRadioTrack(index: Int) {
        val entry = radioTracks.getOrNull(index) ?: return
        cancelRadioTransition()
        radio.setMediaItem(MediaItem.Builder().setMediaId(entry.id).setUri(entry.uri).build())
        radio.prepare()
        if (radioIntent) radio.play()
        updateRadioIndex(index)
        publishRadio(RadioPlaybackState.LOADING)
    }

    /** 曲目播完：官方频道自动连播下一首；全部失败则停止并给出明确错误。 */
    private fun handleRadioEnded() {
        if (radioTracks.isEmpty()) return
        if (transitionActive) return // crossfade 进行中，由过渡收尾并接管
        // gapless：若备用播放器已预载下一首可播曲目，直接无缝切换。
        if (radioTransitionMode == "gapless") {
            val next = firstPlayableIndex(currentRadioIndex())
            if (next != null && standbyPreparedFor == next) {
                promoteStandby(next)
                return
            }
        }
        val next = firstPlayableIndex(currentRadioIndex())
        if (next == null) {
            // 单曲频道自然播完：前端 requestRadioPlayback(false) 语义——干净停止，不报错误；
            // 多曲目全部失败：停止并显示明确错误。
            if (radioTracks.size <= 1) stopRadioInternal()
            else stopRadioWithError(radioAllFailedMessage(), RadioErrorType.UNKNOWN)
            return
        }
        startRadioTrack(next)
    }

    /** 播放失败：标记失败 → 自动跳过到下一首可播曲目；全部失败 → 停止并显示明确错误。 */
    private fun handleRadioError(error: PlaybackException) {
        if (radioTracks.isEmpty()) return
        val index = currentRadioIndex()
        val entry = radioTracks.getOrNull(index)
        val type = classifyRadioError(error, entry?.uri)
        entry?.let { radioFailed += it.id }
        cancelRadioTransition()
        val next = firstPlayableIndex(index)
        if (next != null) {
            startRadioTrack(next)
            publishRadioError(radioErrorMessage(type, entry, transient = true), type)
        } else {
            stopRadioWithError(radioAllFailedMessage(type), type)
        }
    }

    private fun radioAllFailedMessage(type: RadioErrorType = RadioErrorType.UNKNOWN): String =
        when (type) {
            RadioErrorType.FILE_MISSING, RadioErrorType.PERMISSION_REVOKED -> "该频道的本地音频都已缺失或不可访问，已停止播放。请重新选择本地音频后重试"
            RadioErrorType.PACK_NOT_INSTALLED -> "该频道所属音频包尚未安装，已停止播放"
            else -> "该频道的音源暂时都无法载入，已停止播放。请稍后重试，或选择本地音频与生成电台"
        }

    private fun radioErrorMessage(type: RadioErrorType, entry: RadioEntry?, transient: Boolean = false): String {
        val title = entry?.id ?: "当前曲目"
        val base = when (type) {
            RadioErrorType.FILE_MISSING -> "「$title」的本地文件已缺失或不可访问"
            RadioErrorType.PACK_NOT_INSTALLED -> "「$title」所属音频包尚未安装"
            RadioErrorType.CHECKSUM_MISMATCH -> "「$title」的本地文件校验失败"
            RadioErrorType.UNSUPPORTED_FORMAT -> "「$title」的音频格式不受支持"
            RadioErrorType.DECODER_ERROR -> "「$title」解码失败"
            RadioErrorType.PERMISSION_REVOKED -> "「$title」的访问权限已失效，请重新选择文件"
            RadioErrorType.AUDIO_FOCUS_DENIED -> "音频焦点被系统拒绝"
            RadioErrorType.UNKNOWN -> "「$title」播放失败"
        }
        return if (transient) "$base，正在衔接下一首" else base
    }

    private fun classifyRadioError(error: PlaybackException, uri: String?): RadioErrorType = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> RadioErrorType.FILE_MISSING
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> RadioErrorType.PERMISSION_REVOKED
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
        PlaybackException.ERROR_CODE_NOT_SUPPORTED -> RadioErrorType.UNSUPPORTED_FORMAT
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_INIT_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED -> RadioErrorType.DECODER_ERROR
        else -> when {
            uri != null && (uri.startsWith("content://") || uri.startsWith("file://")) -> RadioErrorType.FILE_MISSING
            else -> RadioErrorType.UNKNOWN
        }
    }

    private fun stopRadioWithError(message: String, type: RadioErrorType) {
        radioIntent = false
        cancelRadioTransition()
        radio.stop()
        radioTracks = emptyList()
        radioFailed.clear()
        mutable.value = mutable.value.copy(
            radioPlayback = RadioPlaybackState.ERROR,
            radioPlaying = false,
            radioError = message,
            radioErrorType = type,
            masterPlaying = mutable.value.ambientPlaying || mutable.value.externalPlaying,
        )
    }

    private fun stopRadioInternal() {
        radioIntent = false
        cancelRadioTransition()
        radio.stop()
        radioStandby?.stop()
        radioTracks = emptyList()
        radioFailed.clear()
        mutable.value = mutable.value.copy(
            radioPlayback = RadioPlaybackState.IDLE,
            radioPlaying = false,
            radioUri = null,
            radioTrackIndex = 0,
            radioStationIndex = 0,
            radioTrackCount = 0,
            radioItemId = null,
            radioError = null,
            radioErrorType = null,
        )
    }

    // ── 电台 transition：natural / gapless / crossfade ───────────────────────

    /** 在 45ms 循环中检查是否需要预载（gapless）或开始叠加淡入淡出（crossfade）。 */
    private fun maybeStartRadioTransition() {
        if (radioTracks.isEmpty() || !radioIntent || transitionActive || transitionJob != null) return
        val mode = radioTransitionMode
        if (mode != "gapless" && mode != "crossfade") return
        if (radio.playbackState != Player.STATE_READY || radioTracks.size < 2) return
        val duration = radio.duration
        if (duration <= 0L || duration == C.TIME_UNSET) return
        val remaining = duration - radio.currentPosition
        val windowMs = when (mode) {
            "crossfade" -> (radioTransitionSeconds * 1000.0).coerceAtLeast(350.0)
            else -> 1_500.0 // gapless：提前预载，播完立即无缝切换
        }
        if (remaining <= 0L || remaining > windowMs) return
        if (mode == "gapless" && standbyPreparedFor != null) return
        val next = firstPlayableIndex(currentRadioIndex()) ?: return
        if (mode == "crossfade") startCrossfade(next) else preloadStandby(next)
    }

    /** gapless：把下一首预载到备用播放器；播完（STATE_ENDED）时零延迟切换。 */
    private fun preloadStandby(next: Int) {
        transitionJob = automationScope.launch {
            val standby = radioStandby ?: player().also { radioStandby = it }
            standby.stop()
            standby.setMediaItem(radioMediaItem(radioTracks[next]))
            standby.prepare()
            val deadline = android.os.SystemClock.elapsedRealtime() + 5_000L
            while (standby.playbackState != Player.STATE_READY && android.os.SystemClock.elapsedRealtime() < deadline) {
                if (standby.playerError != null) {
                    radioFailed += radioTracks[next].id
                    standbyPreparedFor = null
                    standby.stop()
                    transitionJob = null
                    return@launch
                }
                delay(20)
            }
            if (standby.playbackState == Player.STATE_READY) standbyPreparedFor = next
            else { standbyPreparedFor = null; standby.stop() }
            transitionJob = null
        }
    }

    /** crossfade：与前端 startCrossfadeRadioTransition 一致的等功率 cos/sin 叠加。 */
    private fun startCrossfade(next: Int) {
        val standby = radioStandby ?: player().also { radioStandby = it }
        standby.stop()
        standby.setMediaItem(radioMediaItem(radioTracks[next]))
        standby.prepare()
        transitionActive = true
        transitionJob = automationScope.launch {
            val deadline = android.os.SystemClock.elapsedRealtime() + 5_000L
            while (standby.playbackState != Player.STATE_READY && android.os.SystemClock.elapsedRealtime() < deadline) {
                if (standby.playerError != null) {
                    // 备用曲目预载失败：跳过它，当前曲目继续播完（前端「将在本曲结束后跳过」）。
                    radioFailed += radioTracks[next].id
                    transitionActive = false
                    transitionJob = null
                    return@launch
                }
                delay(20)
            }
            if (standby.playbackState != Player.STATE_READY) { transitionActive = false; transitionJob = null; return@launch }
            val configuredMs = (radioTransitionSeconds * 1000.0).coerceAtLeast(350.0)
            val availableMs = (radio.duration - radio.currentPosition).coerceAtLeast(350L)
            val durationMs = minOf(configuredMs, availableMs.toDouble()).coerceAtLeast(350.0)
            val startedAt = android.os.SystemClock.elapsedRealtime()
            standby.seekToDefaultPosition()
            standby.volume = 0f
            standby.play()
            while (transitionActive) {
                val progress = ((android.os.SystemClock.elapsedRealtime() - startedAt) / durationMs).coerceIn(0.0, 1.0)
                val target = radioOutputVolume()
                radio.volume = (target * cos(progress * Math.PI / 2)).toFloat().coerceIn(0f, 1f)
                standby.volume = (target * sin(progress * Math.PI / 2)).toFloat().coerceIn(0f, 1f)
                if (progress >= 1.0) break
                delay(30)
            }
            if (transitionActive) promoteStandby(next, alreadyPlaying = true) else { standby.pause(); standby.stop() }
            transitionJob = null
        }
    }

    private fun radioMediaItem(entry: RadioEntry) = MediaItem.Builder().setMediaId(entry.id).setUri(entry.uri).build()

    private fun radioOutputVolume(): Float =
        if (mutable.value.muted) 0f else (mutable.value.radioVolume * mutable.value.masterVolume * _duckMultiplier.value).coerceIn(0f, 1f)

    /**
     * 交接：备用播放器成为主播放器，旧播放器回收为备用。
     * @param alreadyPlaying crossfade 交接时备用已在播放（叠加中），不能从头重播。
     */
    private fun promoteStandby(next: Int, alreadyPlaying: Boolean = false) {
        val standby = radioStandby ?: run { startRadioTrack(next); return }
        if (standbyPreparedFor != next) { startRadioTrack(next); return }
        val outgoing = radio
        transitionActive = false
        transitionJob?.cancel(); transitionJob = null
        standbyPreparedFor = null
        outgoing.removeListener(radioListener)
        standby.addListener(radioListener)
        if (!alreadyPlaying) standby.seekToDefaultPosition()
        standby.volume = radioOutputVolume()
        standby.play()
        radio = standby
        radioStandby = outgoing
        applyVolumes()
        updateRadioIndex(next)
        publishRadio(if (radioIntent) RadioPlaybackState.PLAYING else RadioPlaybackState.PAUSED)
    }

    /** 中止预载/交叉淡化（暂停、切曲、停止、失败时）。 */
    private fun cancelRadioTransition() {
        transitionActive = false
        transitionJob?.cancel(); transitionJob = null
        standbyPreparedFor = null
        radioStandby?.let { standby ->
            standby.pause()
            standby.stop()
        }
        applyVolumes()
    }

    override fun setMasterVolume(volume: Float) {
        if (dispatchToPlayerThread { setMasterVolume(volume) }) return
        mutable.value = mutable.value.copy(masterVolume = volume.coerceIn(0f, 1f))
        applyVolumes()
    }

    override fun setAmbientVolume(volume: Float) {
        if (dispatchToPlayerThread { setAmbientVolume(volume) }) return
        mutable.value = mutable.value.copy(ambientVolume = volume.coerceIn(0f, 1f))
        applyVolumes()
    }

    override fun setRadioVolume(volume: Float) {
        if (dispatchToPlayerThread { setRadioVolume(volume) }) return
        mutable.value = mutable.value.copy(radioVolume = volume.coerceIn(0f, 1f))
        applyVolumes()
    }

    override fun playAmbient() {
        if (dispatchToPlayerThread { playAmbient() }) return
        if (ambient.isEmpty()) { mutable.value = mutable.value.copy(ambientPlaying = false, error = "No ambient tracks selected"); return }
        if (!interruptions.acquire()) { mutable.value = mutable.value.copy(error = "Audio focus was denied"); return }
        mutable.value = mutable.value.copy(ambientPlaying = true, masterPlaying = true)
        ambient.values.forEach { it.play() }
        // 状态就位后重算每轨目标音量（含淡入起点与 duck 恢复系数）。
        applyVolumes()
        interruptions.registerNoisyReceiver()
    }

    override fun pauseAmbient() {
        if (dispatchToPlayerThread { pauseAmbient() }) return
        ambient.values.forEach { it.pause() }
        mutable.value = mutable.value.copy(ambientPlaying = false)
    }

    /** 供外部环境声后端（miniaudio）获取本会话共享的 AudioFocus + noisy receiver。返回是否取得焦点。 */
    fun acquireSessionFocus(): Boolean {
        val granted = interruptions.acquire()
        if (granted) interruptions.registerNoisyReceiver()
        return granted
    }

    /** 供外部环境声后端（miniaudio）释放共享焦点与 noisy receiver。 */
    fun releaseSessionFocus() {
        interruptions.unregisterNoisyReceiver()
        interruptions.abandon()
    }

    /** Registers an already prepared offline renderer and makes it part of audio focus/media keys. */
    suspend fun beginExternalPlayback(controller: ExternalPlaybackController): Boolean = withContext(Dispatchers.Main.immediate) {
        if (!interruptions.acquire()) {
            mutable.value = mutable.value.copy(error = "Audio focus was denied")
            return@withContext false
        }
        externalController?.takeUnless { it === controller }?.stopExternal()
        externalController = controller
        mutable.value = mutable.value.copy(externalSelected = true, externalPlaying = false, masterPlaying = mutable.value.ambientPlaying || mutable.value.radioPlaying, error = null)
        // 状态就位后重算音量（外部播放开始时同步恢复环境/电台音量系数）。
        applyVolumes()
        interruptions.registerNoisyReceiver()
        true
    }

    fun pauseExternalPlayback() {
        if (dispatchToPlayerThread { pauseExternalPlayback() }) return
        externalController?.pauseExternal()
        mutable.value = mutable.value.copy(externalPlaying = false, masterPlaying = mutable.value.ambientPlaying || mutable.value.radioPlaying)
        if (!mutable.value.masterPlaying) {
            interruptions.unregisterNoisyReceiver()
            interruptions.abandon()
        }
    }

    fun stopExternalPlayback() {
        if (dispatchToPlayerThread { stopExternalPlayback() }) return
        externalController?.stopExternal()
        externalController = null
        mutable.value = mutable.value.copy(externalSelected = false, externalPlaying = false, masterPlaying = mutable.value.ambientPlaying || mutable.value.radioPlaying)
        if (!mutable.value.masterPlaying) {
            interruptions.unregisterNoisyReceiver()
            interruptions.abandon()
        }
    }

    /** The external renderer owns the AudioTrack clock; mirror only states it has actually emitted. */
    fun publishExternalPlaybackState(selected: Boolean, playing: Boolean, error: String? = null) {
        if (dispatchToPlayerThread { publishExternalPlaybackState(selected, playing, error) }) return
        val current = mutable.value
        mutable.value = current.copy(
            externalSelected = selected,
            externalPlaying = playing,
            masterPlaying = current.ambientPlaying || current.radioPlaying || playing,
            error = error,
        )
    }

    override fun play() {
        if (dispatchToPlayerThread { play() }) return
        if (!hasPlayableSelection()) { mutable.value = mutable.value.copy(masterPlaying = false, error = "Nothing is ready to play"); return }
        if (!interruptions.acquire()) { mutable.value = mutable.value.copy(error = "Audio focus was denied"); return }
        val resumeAmbient = hasAmbientSelection()
        mutable.value = mutable.value.copy(masterPlaying = true, ambientPlaying = resumeAmbient)
        if (resumeAmbient) {
            ambientGraphController?.resumeAmbientGraph() ?: ambient.values.forEach { it.play() }
        }
        if (radioTracks.isNotEmpty() || mutable.value.radioUri != null) { radioIntent = true; radio.play() }
        if (mutable.value.externalSelected) {
            externalController?.resumeExternal()
        }
        // 状态就位后重算音量（恢复播放时离开 duck 状态、环境声目标恢复为最终音量）。
        applyVolumes()
        interruptions.registerNoisyReceiver()
    }
    override fun pause() {
        if (dispatchToPlayerThread { pause() }) return
        ambientGraphController?.pauseAmbientGraph() ?: ambient.values.forEach { it.pause() }
        radioIntent = false
        radio.pause()
        externalController?.pauseExternal()
        // A master pause is an audible-state transition, not merely a gain change. Publish it
        // immediately so notification/headset actions update Compose controls and visuals while
        // asynchronous native graph shutdown completes.
        mutable.value = mutable.value.copy(masterPlaying = false, ambientPlaying = false, externalPlaying = false)
        interruptions.unregisterNoisyReceiver(); interruptions.abandon()
    }
    override fun stop() {
        if (dispatchToPlayerThread { stop() }) return
        ambient.values.forEach { it.stop() }
        stopRadioInternal()
        externalController?.stopExternal(); externalController = null
        mutable.value = mutable.value.copy(masterPlaying = false, ambientPlaying = false, externalSelected = false, externalPlaying = false)
        interruptions.unregisterNoisyReceiver(); interruptions.abandon()
    }
    override fun setMuted(muted: Boolean) {
        if (dispatchToPlayerThread { setMuted(muted) }) return
        mutable.value = mutable.value.copy(muted = muted)
        applyVolumes()
    }

    /**
     * Performs an idempotent, monotonic fade from the current volume. Repeated calls from the
     * sleep reconciler replace the previous automation instead of stacking multiple jobs.
     */
    fun fadeToZero(target: FadeTarget, durationMillis: Long) {
        fadeTo(target, 0f, durationMillis)
    }

    fun fadeTo(target: FadeTarget, destination: Float, durationMillis: Long) {
        if (dispatchToPlayerThread { fadeTo(target, destination, durationMillis) }) return
        volumeAutomation?.cancel()
        val duration = durationMillis.coerceAtLeast(0L)
        val end = destination.coerceIn(0f, 1f)
        val start = when (target) {
            FadeTarget.ALL -> mutable.value.masterVolume
            FadeTarget.AMBIENT -> mutable.value.ambientVolume
            FadeTarget.RADIO -> mutable.value.radioVolume
        }
        if (duration == 0L || start == end) {
            setTargetVolume(target, end)
            return
        }
        volumeAutomation = automationScope.launch {
            val startedAt = android.os.SystemClock.elapsedRealtime()
            val frameMillis = 50L
            while (true) {
                val elapsed = android.os.SystemClock.elapsedRealtime() - startedAt
                val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
                // Equal-power-style curve avoids an audible cliff near the end.
                val curved = if (end < start) {
                    val remaining = 1f - progress
                    1f - remaining * remaining
                } else progress * progress
                setTargetVolume(target, start + (end - start) * curved)
                if (progress >= 1f) break
                delay(minOf(frameMillis, (duration - elapsed).coerceAtLeast(1L)))
            }
        }
    }

    fun cancelVolumeAutomation() {
        if (dispatchToPlayerThread { cancelVolumeAutomation() }) return
        volumeAutomation?.cancel()
        volumeAutomation = null
    }

    private fun setTargetVolume(target: FadeTarget, value: Float) = when (target) {
        FadeTarget.ALL -> setMasterVolume(value)
        FadeTarget.AMBIENT -> setAmbientVolume(value)
        FadeTarget.RADIO -> setRadioVolume(value)
    }

    private fun applyVolumes() {
        if (dispatchToPlayerThread { applyVolumes() }) return
        val state = mutable.value
        // When the ambient graph is not actually sounding, every track's target is 0
        // (frontend: `entry.target = playing ? master*env*track : 0`), which the envelope
        // uses to fade tracks down on pause and up again on resume.
        val sounding = state.ambientPlaying && state.masterPlaying
        ambient.forEach { (id, _) ->
            val track = state.tracks.firstOrNull { it.soundId == id }
            val target = if (!sounding || state.muted || track?.muted == true || track?.enabled == false) 0f
                else (track?.volume ?: 0f) * state.ambientVolume * state.masterVolume * _duckMultiplier.value
            perTrackTarget[id] = target.coerceIn(0f, 1f)
        }
        radio.volume = if (state.muted) 0f else state.radioVolume * state.masterVolume * _duckMultiplier.value
        // 生成电台（外部渲染器）与 Media3 电台走同一条主音量通路：睡眠渐弱把 masterVolume 淡到 0 时，
        // 外部渲染器同步淡出；静音/duck 时也保持一致，避免「渐弱时生成电台仍满音量」。
        externalController?.setVolume(if (state.muted) 0f else state.radioVolume * state.masterVolume * _duckMultiplier.value)
    }

    /**
     * Per-track anti-click envelope. Every 16 ms the player volume converges toward its
     * target with `smoothing = 1 - exp(-0.016 / fadeSeconds)`.
     * A track whose own volume is 0 is faded out and then removed from the graph entirely.
     */
    private fun stepTrackEnvelope() {
        if (dispatchToPlayerThread { stepTrackEnvelope() }) return
        val state = mutable.value
        val toRelease = mutableListOf<String>()
        ambient.forEach { (id, player) ->
            val track = state.tracks.firstOrNull { it.soundId == id }
            val target = perTrackTarget[id] ?: 0f
            val current = perTrackCurrent[id] ?: 0f
            val fadeSeconds = (if (target >= current) DEFAULT_FADE_IN else DEFAULT_FADE_OUT).coerceAtLeast(0.006f)
            val smoothing = 1f - kotlin.math.exp(-0.016f / fadeSeconds)
            val next = if (kotlin.math.abs(target - current) < 0.002f) target else current + (target - current) * smoothing
            perTrackCurrent[id] = next
            player.volume = next.coerceIn(0f, 1f)
            if (target <= 0f && next < 0.003f && (track?.volume ?: 0f) <= 0f) toRelease += id
        }
        toRelease.forEach { releaseAmbientPlayer(it) }
    }

    private fun releaseAmbientPlayer(id: String) {
        ambient.remove(id)?.release()
        ambientUris.remove(id)
        ambientPans.remove(id)
        perTrackCurrent.remove(id)
        perTrackTarget.remove(id)
        if (mutable.value.ambientErrors.containsKey(id)) {
            mutable.value = mutable.value.copy(ambientErrors = mutable.value.ambientErrors - id)
        }
    }

    /**
     * 环境声轨声像（-100..100，对应前端 ambientAudio.ts 每轨 StereoPannerNode）。
     * 通过每轨播放器音频输出链中的 StereoPanAudioProcessor 真实作用于左右声道：
     * 已有播放器的轨道立即生效，尚未创建的轨道在创建时应用。
     */
    fun setTrackPan(soundId: String, pan: Float) {
        if (dispatchToPlayerThread { setTrackPan(soundId, pan) }) return
        val clamped = pan.coerceIn(-100f, 100f)
        trackPan[soundId] = clamped
        ambientPans[soundId]?.setPan(clamped / 100f)
    }

    /**
     * 批量应用环境声轨声像（生成电台编排 arrangement.ambientTracks 的 pan）。
     * 不在映射中的已记录声像重置为居中——切到非生成频道时用空映射即全部复位。
     */
    fun setAmbientPans(pans: Map<String, Float>) {
        if (dispatchToPlayerThread { setAmbientPans(pans) }) return
        val ids = trackPan.keys + pans.keys
        ids.forEach { id ->
            val clamped = (pans[id] ?: 0f).coerceIn(-100f, 100f)
            trackPan[id] = clamped
            ambientPans[id]?.setPan(clamped / 100f)
        }
    }

    private fun normalizeAssetUri(uri: String) = when {
        uri.startsWith("asset:///") -> "asset:///" + uri.removePrefix("asset:///")
        uri.startsWith("file:///android_asset/") -> "asset:///" + uri.removePrefix("file:///android_asset/")
        else -> uri
    }

    fun release() {
        if (dispatchToPlayerThread { release() }) return
        envelopeJob.cancel()
        cancelVolumeAutomation()
        interruptions.release()
        ambient.values.forEach { it.release() }
        ambient.clear(); ambientUris.clear(); ambientPans.clear()
        perTrackTarget.clear(); perTrackCurrent.clear(); trackPan.clear()
        cancelRadioTransition()
        radio.removeListener(radioListener)
        radio.release()
        radioStandby?.release()
        radioStandby = null
    }
    /** Returns an actually selected player; ambient-only sessions never advertise silent radio playback. */
    fun primaryPlayer(): Player = if (mutable.value.radioPlaying && mutable.value.radioUri != null) radio else ambient.values.firstOrNull() ?: radio
    private fun hasAmbientSelection(): Boolean =
        ambient.isNotEmpty() ||
            mutable.value.tracks.any { it.enabled } ||
            mutable.value.ambientLabel.isNotBlank() && mutable.value.ambientLabel != "未选择环境声"

    fun hasPlayableSelection(): Boolean =
        hasAmbientSelection() ||
            radioTracks.isNotEmpty() ||
            mutable.value.radioUri != null ||
            mutable.value.externalSelected && externalController != null
    internal fun handleInterruption(change: Int) = onAudioFocusChange(change)
    internal fun handleBecomingNoisy() = pauseForNoisy()

    private fun pauseForNoisy() {
        resumeAfterTransientLoss = false
        pause()
        ambientInterruptionListener?.onBecomingNoisy()
    }
    private fun onAudioFocusChange(change: Int) = when (change) {
        AudioManager.AUDIOFOCUS_LOSS -> { resumeAfterTransientLoss = false; pause(); ambientInterruptionListener?.onFocusLoss() }
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { resumeAfterTransientLoss = mutable.value.masterPlaying; ambient.values.forEach { it.pause() }; radio.pause(); externalController?.pauseExternal(); mutable.value = mutable.value.copy(masterPlaying = false, ambientPlaying = false, externalPlaying = false); ambientInterruptionListener?.onFocusLossTransient() }
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> { _duckMultiplier.value = duckMultiplierFor(change, _duckMultiplier.value); applyVolumes() }
        AudioManager.AUDIOFOCUS_GAIN -> { _duckMultiplier.value = duckMultiplierFor(change, _duckMultiplier.value); applyVolumes(); if (resumeAfterTransientLoss) { resumeAfterTransientLoss = false; play() }; ambientInterruptionListener?.onFocusGain(); Unit }
        else -> Unit
    }

    /**
     * 重新取得/放弃音频焦点后离开 duck 状态：恢复音量系数。
     * 部分设备（尤其语音助手/通知打断后恢复、暂停后重试播放）在焦点重新授予时不派发
     * AUDIOFOCUS_GAIN，导致 duckMultiplier 卡在 0.2、整体音量持续偏小，直到进程重启才复位。
     * 因此成功取得焦点（acquire）与放弃焦点（abandon）都强制回到系数 1。
     */
    private fun onFocusGranted() {
        _duckMultiplier.value = duckMultiplierFor(AudioManager.AUDIOFOCUS_GAIN, _duckMultiplier.value)
        applyVolumes()
    }
}

/** 焦点变化 → 音量压缩系数。duck 压缩到 0.2，恢复（GAIN）回到 1，其它变化保持现值。 */
internal fun duckMultiplierFor(change: Int, current: Float): Float = when (change) {
    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> 0.2f
    AudioManager.AUDIOFOCUS_GAIN -> 1f
    else -> current
}

enum class FadeTarget { ALL, AMBIENT, RADIO }

/** One playback graph per app process so the Activity and MediaSessionService never create competing players. */
object Media3AudioRuntime {
    @Volatile private var instance: Media3AudioEngine? = null
    fun get(context: Context): Media3AudioEngine = instance ?: synchronized(this) {
        instance ?: Media3AudioEngine(context.applicationContext).also { instance = it }
    }
}

private class AudioInterruptionManager(
    context: Context,
    private val focusChanged: (Int) -> Unit,
    private val becomingNoisy: () -> Unit,
    private val focusGranted: () -> Unit,
) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val callbackHandler = Handler(Looper.getMainLooper())
    private val listener = AudioManager.OnAudioFocusChangeListener(focusChanged)
    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setAcceptsDelayedFocusGain(false).setWillPauseWhenDucked(false).setOnAudioFocusChangeListener(listener, callbackHandler).build()
    private var receiverRegistered = false
    private val receiver = object : BroadcastReceiver() { override fun onReceive(context: Context?, intent: Intent?) { if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) becomingNoisy() } }

    private fun dispatchCallback(action: () -> Unit) {
        if (Looper.myLooper() == callbackHandler.looper) action() else callbackHandler.post { action() }
    }

    fun acquire(): Boolean {
        val granted = manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        // 成功取得焦点代表当前不再处于 duck 状态：强制恢复音量系数，防御系统不派发 GAIN 的情况。
        if (granted) dispatchCallback(focusGranted)
        return granted
    }
    fun abandon() {
        // 放弃焦点同样退出 duck 状态，避免下次恢复播放时仍带着 0.2 压缩系数。
        dispatchCallback(focusGranted)
        manager.abandonAudioFocusRequest(request)
    }
    fun registerNoisyReceiver() { if (!receiverRegistered) { app.registerReceiver(receiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), Context.RECEIVER_NOT_EXPORTED); receiverRegistered = true } }
    fun unregisterNoisyReceiver() { if (receiverRegistered) { runCatching { app.unregisterReceiver(receiver) }; receiverRegistered = false } }
    fun release() { unregisterNoisyReceiver(); abandon() }
}
