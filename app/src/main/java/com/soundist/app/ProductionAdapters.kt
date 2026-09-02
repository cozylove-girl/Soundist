package com.soundist.app

import android.content.Context
import com.soundist.core.audio.Media3AudioEngine
import com.soundist.core.audio.AmbientInterruptionListener
import com.soundist.core.audio.AmbientGraphController
import com.soundist.core.audio.FadeTarget
import com.soundist.core.audio.PlaybackServiceController
import com.soundist.core.audio.PlaybackServiceStartResult
import com.soundist.core.audio.ExternalPlaybackController
import com.soundist.core.audio.RadioErrorType
import com.soundist.core.audio.RadioPlaybackException
import com.soundist.core.audio.RadioPlaybackState
import com.soundist.core.audio.RadioTrackSource
import com.soundist.core.database.RoomSoundRepository
import com.soundist.core.database.RoomProductivityRepository
import com.soundist.core.database.RoomRecordsRepository
import com.soundist.core.database.RoomOfflineContentRepository
import com.soundist.core.model.PlaybackEvent
import com.soundist.core.model.PlaybackKind
import com.soundist.core.model.FocusTargetKind
import com.soundist.core.model.FocusTimerMode
import com.soundist.core.model.SessionKind
import com.soundist.core.model.SessionStatus as DomainSessionStatus
import com.soundist.feature.productivity.CompletionStatus
import com.soundist.feature.productivity.CountdownEvent
import com.soundist.feature.productivity.FocusPlan
import com.soundist.feature.productivity.FocusRecord
import com.soundist.feature.productivity.FocusReviewNote
import com.soundist.feature.productivity.FocusAudioSnapshot
import com.soundist.feature.productivity.FocusSoundSnapshot
import com.soundist.feature.productivity.FocusSessionController
import com.soundist.feature.productivity.FocusSessionState
import com.soundist.feature.productivity.FocusSceneController
import com.soundist.feature.productivity.FocusPersistenceStore
import com.soundist.feature.productivity.FocusPersistenceEvent
import com.soundist.feature.productivity.FocusPersistenceEventKind
import com.soundist.feature.productivity.HabitCheckStore
import com.soundist.feature.productivity.FocusTarget
import com.soundist.feature.productivity.FocusTransitionAlerts
import com.soundist.feature.productivity.Habit
import com.soundist.feature.productivity.HabitMetric
import com.soundist.feature.productivity.PlanScope
import com.soundist.feature.productivity.Priority
import com.soundist.feature.productivity.ProductivityDependencies
import com.soundist.feature.productivity.ProductivityRepository
import com.soundist.feature.productivity.ProductivityState
import com.soundist.feature.productivity.ReminderOffset
import com.soundist.feature.productivity.ReminderScheduler as FeatureReminderScheduler
import com.soundist.feature.productivity.SessionPhase
import com.soundist.feature.productivity.SessionStatus
import com.soundist.feature.productivity.SleepAudioController
import com.soundist.feature.productivity.SleepEndMode
import com.soundist.feature.productivity.SleepRecord
import com.soundist.feature.productivity.SleepRoutine
import com.soundist.feature.productivity.SleepSession
import com.soundist.feature.productivity.SleepStatus
import com.soundist.feature.productivity.SleepTarget
import com.soundist.feature.productivity.TargetKind
import com.soundist.feature.productivity.TimerMode
import com.soundist.feature.productivity.Todo
import com.soundist.feature.productivity.TodoKind
import com.soundist.feature.listening.AmbientErrorEvent
import com.soundist.feature.listening.AmbientSound
import com.soundist.feature.listening.ListeningAudioController
import com.soundist.feature.listening.ListeningRepository
import com.soundist.feature.listening.PlaybackState
import com.soundist.feature.listening.PlaybackGraphState
import com.soundist.feature.listening.RadioEngineState
import com.soundist.feature.listening.ListeningState
import com.soundist.feature.listening.RadioGroup
import com.soundist.feature.listening.RadioSourceKind as FeatureRadioSourceKind
import com.soundist.feature.listening.RadioStation as FeatureRadioStation
import com.soundist.feature.listening.playableSources
import com.soundist.feature.listening.SoundCatalog as FeatureSoundCatalog
import com.soundist.feature.listening.SoundFilter
import com.soundist.feature.listening.SoundPreset
import com.soundist.feature.listening.GeneratedAudioRenderer
import com.soundist.feature.listening.GeneratedAmbientTrack
import com.soundist.feature.listening.GeneratedArrangement
import com.soundist.feature.listening.GeneratedLayer
import com.soundist.feature.listening.GeneratedScene
import com.soundist.feature.listening.GeneratorSettings
import com.soundist.feature.listening.LocalAudioSelection
import com.soundist.feature.listening.RadioTrack
import com.soundist.feature.listening.TrackLicense
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import java.time.LocalTime
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

class RoomListeningRepository(private val room: RoomSoundRepository, private val offlineContent: RoomOfflineContentRepository) : ListeningRepository {
    override suspend fun load(): ListeningState {
        val storedSounds = room.observeSounds().first().associateBy { it.id }
        val snapshot = room.observePlayback().first()
        val activeTracks = snapshot?.tracks.orEmpty().associateBy { it.soundId }
        val sounds = FeatureSoundCatalog.items.map { item ->
            val stored = storedSounds[item.id]
            val track = activeTracks[item.id]
            item.copy(
                favorite = stored?.isFavorite ?: false,
                active = track?.enabled == true,
                volume = track?.volume ?: stored?.defaultVolume ?: item.volume,
            )
        }
        val presets = room.observePresets().first().map { preset ->
            SoundPreset(preset.id, preset.name, preset.tracks.filter { it.enabled }.associate { it.soundId to it.volume }, preset.builtIn, preset.purposeLabel ?: "自由")
        }
        val stations = room.observeStations().first().map { it.feature() }
        val radioWasPlaying = snapshot?.playing == true && snapshot?.radioId != null
        return ListeningState(
            sounds = sounds,
            presets = mergeBuiltIns(presets),
            stations = mergeStations(stations),
            ambientPlaying = snapshot?.playing == true && activeTracks.isNotEmpty(),
            globalPlaying = snapshot?.playing == true,
            globalVolume = snapshot?.masterVolume ?: .8f,
            environmentVolume = snapshot?.ambientVolume ?: 1f,
            radioVolume = snapshot?.radioVolume ?: .8f,
            selectedStationId = snapshot?.radioId,
            // 前端恢复语义：先恢复所选频道与曲目索引，播放等用户手势（或宿主 autoResume）触发。
            radioTrackIndex = snapshot?.radioTrackIndex ?: 0,
            radioPlayback = if (radioWasPlaying) PlaybackState.PAUSED else PlaybackState.IDLE,
        )
    }

    override suspend fun savePreset(preset: SoundPreset) = room.savePreset(com.soundist.core.model.Preset(preset.id, preset.name, preset.tracks.map { com.soundist.core.model.MixTrack(it.key, it.value) }, System.currentTimeMillis(), preset.purposeLabel, preset.builtIn))
    override suspend fun deletePreset(id: String) = room.deletePreset(id)
    override suspend fun reorderPresets(ids: List<String>) = room.reorderPresets(ids)
    override suspend fun saveSound(sound: AmbientSound) = room.setFavorite(sound.id, sound.favorite)
    override suspend fun saveStation(station: FeatureRadioStation) = room.saveStation(station.domain())
    override suspend fun deleteStation(id: String, deleteCopies: Boolean) {
        // 删除本地频道时按需清理私有音频副本；保留副本时留下 CustomRadioFile 记录作为后续存储管理入口。
        offlineContent.observeCustomFiles(id).first().forEach { file ->
            if (deleteCopies) {
                runCatching { File(file.privatePath).takeIf { it.exists() }?.delete() }
                offlineContent.deleteCustomFile(file.id)
            }
        }
        room.deleteStation(id)
    }
    override suspend fun reorderStations(ids: List<String>) = room.reorderStations(ids)

    private fun mergeBuiltIns(stored: List<SoundPreset>): List<SoundPreset> {
        val custom = stored.filterNot { it.builtIn }
        val overrides = stored.filter { it.builtIn }.associateBy { it.id }
        return FeatureSoundCatalog.builtInPresets.map { overrides[it.id] ?: it } + custom
    }

    private fun mergeStations(stored: List<FeatureRadioStation>): List<FeatureRadioStation> {
        val builtInIds = com.soundist.feature.listening.RadioCatalog.initial.mapTo(hashSetOf()) { it.id }
        // 内置频道是随版本发布的只读产品内容。旧数据库快照不能覆盖新版本的曲目许可、
        // 生成层或官方环境配方；用户自建频道仍完整保留。
        return com.soundist.feature.listening.RadioCatalog.initial + stored.filterNot { it.id in builtInIds }
    }
}

class Media3ListeningAudioController(
    private val engine: Media3AudioEngine,
    private val room: RoomSoundRepository,
    private val records: RoomRecordsRepository,
    private val playbackService: PlaybackServiceController,
    private val backgroundPlaybackEnabled: () -> Boolean,
    private val fadeSeconds: () -> Int,
    private val onNotificationPermissionRequired: () -> Unit,
    /** 环境声后端（阶段 B）。默认 Media3；feature flag 置 true 时注入 [MiniaudioAmbientMixer]。
     *  miniaudio 失败时可变（自动回退切到 Media3），故用 var。 */
    @Volatile private var ambientMixer: AmbientMixer = Media3AmbientMixer(engine),
) : ListeningAudioController {
    private val catalogue = com.soundist.core.model.SoundCatalog.sounds.associateBy { it.id }
    private val ambientEvents = linkedMapOf<String, PlaybackEvent>()
    private var selectedRadioId: String? = null
    /** 播放事件跨暂停保持：同一频道的暂停/恢复复用同一事件，累计真实收听时长，避免大量零时长记录。 */
    private var radioEvent: PlaybackEvent? = null
    private var radioSegmentResumedAt: Long? = null
    private var radioListenedMs: Long = 0L
    private val radioEventScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /** One authority for reconcile/play/pause/fallback/focus commands. */
    private val ambientCommandMutex = Mutex()
    /** 自动回退只执行一次（setAmbient 与 collection 都在 Main 串行，故普通标志即可）。 */
    @Volatile private var fallbackDone = false
    /** 最近一次环境声轨道集（回退时用 Media3 恢复逐轨音量）。 */
    @Volatile private var lastAmbientTracks: Map<String, Float> = emptyMap()
    /** null means the controller has not restored a composition yet; empty means a known empty mix. */
    private val ambientSelection = MutableStateFlow<Map<String, Float>?>(null)
    /** 生成频道环境轨自动化独立于 UI/Room：只把瞬时增益推给混音器，不污染用户保存的基础音量。 */
    private var generatedAmbientJob: Job? = null
    private var generatedAmbientRevision = 0L
    /** transient 焦点丢失后是否应恢复环境声（GAIN 时按此意图决定是否 play）。 */
    @Volatile private var resumeAmbientAfterFocusGain = false

    /** 睡眠/专注必须取得自动回退后的当前后端，不能长期持有启动时的失效 mixer。 */
    internal fun currentAmbientMixer(): AmbientMixer = ambientMixer

    /** 有序回退：冻结+停止 miniaudio → 切 Media3 → 用 Media3 恢复当前轨道与逐轨音量，并按原播放意图恢复。仅执行一次。 */
    private suspend fun fallbackToMedia3() {
        if (fallbackDone || ambientMixer !is MiniaudioAmbientMixer) return
        fallbackDone = true
        val failedMixer = ambientMixer as MiniaudioAmbientMixer
        val wasPlaying = failedMixer.playing
        val fallback = Media3AmbientMixer(engine)
        ambientMixer = fallback
        failedMixer.stop() /* 停止并释放 miniaudio（含设备与 PCM），避免双引擎 */
        engine.releaseSessionFocus() /* 释放 miniaudio 曾获取的共享焦点 */
        val tracks = lastAmbientTracks
        if (tracks.isNotEmpty()) {
            fallback.reconcile(tracks) { id -> catalogue[id]?.assetUri }
            if (wasPlaying) fallback.play(fadeSeconds().toFloat()) /* 回退后按原意图续播，不静音 */
        }
        android.util.Log.e("SoundistMiniaudio", "环境声自动回退到 Media3（wasPlaying=$wasPlaying）：${failedMixer.lastError?.message ?: "native 致命错误"}")
    }

    init {
        engine.ambientGraphController = object : AmbientGraphController {
            override fun resumeAmbientGraph() {
                radioEventScope.launch {
                    ambientCommandMutex.withLock {
                        val mixer = ambientMixer
                        if (mixer is MiniaudioAmbientMixer && !engine.acquireSessionFocus()) return@withLock
                        mixer.play(0f)
                        engine.publishAmbientGraphPlaying(mixer.playing)
                    }
                    if (ambientMixer.playing) beginAmbientEvents(lastAmbientTracks.keys)
                }
            }

            override fun pauseAmbientGraph() {
                radioEventScope.launch {
                    ambientCommandMutex.withLock {
                        ambientMixer.pause(0f)
                        engine.publishAmbientGraphPlaying(false)
                        if (ambientMixer is MiniaudioAmbientMixer) engine.releaseSessionFocus()
                    }
                    closeAllAmbientEvents("paused")
                }
            }
        }
        // 引擎终态（全失败 ERROR / 单曲播完 IDLE）正确结束播放事件；暂停不结束事件。
        radioEventScope.launch {
            engine.state.map { it.radioPlayback }.distinctUntilChanged().collect { state ->
                when (state) {
                    RadioPlaybackState.ERROR -> endRadioEvent("failed")
                    RadioPlaybackState.IDLE -> endRadioEvent("natural")
                    else -> Unit
                }
            }
        }
        // 环境声音量模型实时驱动：master/ambient/duck/muted 任一变化 → 推给后端（Media3 为 no-op，
        // miniaudio 写原生 master 增益）。覆盖 setAmbient 的 master、焦点 duck 实时变化、静音切换、
        // sleepFade 渐弱（走 masterVolume 或 ambientVolume）。
        radioEventScope.launch {
            combine(
                engine.state.map { AmbientVolumeInput(it.masterVolume, it.ambientVolume, 1f, it.muted) },
                engine.duckMultiplier,
            ) { base, duck -> base.copy(duck = duck) }
                .distinctUntilChanged()
                .collect { v ->
                    // 音量是实时控制信号，不能排在轨道解码/reconcile 的互斥锁后面。
                    // 两个后端的 applyVolume 都是非阻塞、线程安全的目标值更新；后端替换时读取最新引用即可。
                    ambientMixer.applyVolume(v.master, v.ambient, v.duck, v.muted)
                }
        }
        // 周期健康检查：retire overflow / native 致命错误 → checkHealth 触发 fail-safe（停设备+FAILED）
        // → 回退到 Media3。仅对 miniaudio 后端有意义。
        radioEventScope.launch {
            while (true) {
                delay(1_000)
                ambientCommandMutex.withLock {
                    if (ambientMixer is MiniaudioAmbientMixer && !NativeAudioCore.checkHealth()) {
                        fallbackToMedia3()
                    }
                }
            }
        }
        // Records 页面需要看到仍在播放的会话。每秒只做一次 Room 批量 upsert，
        // 不让 UI 轮询音频引擎，也不把音量拖动变成数据库写入。
        radioEventScope.launch {
            while (true) {
                checkpointPlaybackEvents()
                delay(1_000)
            }
        }
        // 环境声后端的中断协调（miniaudio 需要；Media3 环境声由引擎内部处理焦点）。
        // AudioFocus 属于整个 Soundist 会话：焦点丢失/拔耳机时暂停当前 ambient backend，
        // transient loss 记录恢复意图，GAIN 时仅按意图恢复。
        engine.ambientInterruptionListener = object : AmbientInterruptionListener {
            override fun onFocusLoss() {
                resumeAmbientAfterFocusGain = false
                radioEventScope.launch {
                    ambientCommandMutex.withLock {
                        if (ambientMixer is MiniaudioAmbientMixer) ambientMixer.pause(0f)
                    }
                    closeAllAmbientEvents("interrupted")
                }
            }
            override fun onFocusLossTransient() {
                radioEventScope.launch {
                    ambientCommandMutex.withLock {
                        if (ambientMixer is MiniaudioAmbientMixer) {
                            resumeAmbientAfterFocusGain = ambientMixer.playing
                            ambientMixer.pause(0f)
                        }
                    }
                    closeAllAmbientEvents("interrupted")
                }
            }
            override fun onFocusGain() {
                radioEventScope.launch {
                    ambientCommandMutex.withLock {
                        if (ambientMixer is MiniaudioAmbientMixer && resumeAmbientAfterFocusGain) {
                            resumeAmbientAfterFocusGain = false
                            ambientMixer.play(0f)
                        }
                    }
                    if (ambientMixer.playing) beginAmbientEvents(lastAmbientTracks.keys)
                }
            }
            override fun onBecomingNoisy() {
                resumeAmbientAfterFocusGain = false
                radioEventScope.launch {
                    ambientCommandMutex.withLock {
                        if (ambientMixer is MiniaudioAmbientMixer) ambientMixer.pause(0f)
                    }
                    closeAllAmbientEvents("interrupted")
                }
            }
        }
    }

    /** 整体播放图回流：通知栏、耳机按键与页面共享同一真实状态。 */
    override val playbackGraph: Flow<PlaybackGraphState> = combine(engine.state, ambientSelection) { state, tracks ->
            PlaybackGraphState(
                ambientPlaying = state.ambientPlaying,
                radioPlaying = state.radioPlaying || state.externalPlaying,
                globalPlaying = state.masterPlaying,
                ambientTracks = tracks,
            )
        }
        .distinctUntilChanged()

    /** 电台状态回流：把 Media3 引擎的播放器事件映射为 ViewModel 可消费的电台状态。 */
    override val radioEvents: Flow<RadioEngineState> = engine.state
        .map { s ->
            RadioEngineState(
                state = when (s.radioPlayback) {
                    RadioPlaybackState.IDLE -> PlaybackState.IDLE
                    RadioPlaybackState.LOADING, RadioPlaybackState.BUFFERING -> PlaybackState.LOADING
                    RadioPlaybackState.READY, RadioPlaybackState.PLAYING -> PlaybackState.PLAYING
                    RadioPlaybackState.PAUSED, RadioPlaybackState.ENDED -> PlaybackState.PAUSED
                    RadioPlaybackState.ERROR -> PlaybackState.ERROR
                },
                stationId = selectedRadioId,
                trackIndex = s.radioStationIndex,
                errorMessage = s.radioError,
                errorType = s.radioErrorType?.name,
            )
        }
        .distinctUntilChanged()

    /** 环境声轨失败回流：只对「新失败」的声音发事件；声音被移除（错误清除）后移除记录，允许再次失败再次通知。 */
    private val reportedAmbientFailures = mutableSetOf<String>()
    override val ambientErrors: Flow<AmbientErrorEvent> = engine.state
        .map { it.ambientErrors }
        .distinctUntilChanged()
        .transform { errors ->
            reportedAmbientFailures.retainAll(errors.keys)
            for ((id, message) in errors) {
                if (reportedAmbientFailures.add(id)) emit(AmbientErrorEvent(id, message))
            }
        }

    override suspend fun setAmbient(tracks: List<AmbientSound>, playing: Boolean, masterVolume: Float) {
        setAmbientInternal(tracks, playing, masterVolume, 48L)
    }

    override suspend fun setAmbientWithTransition(
        tracks: List<AmbientSound>,
        playing: Boolean,
        masterVolume: Float,
        transitionMillis: Long,
    ) {
        setAmbientInternal(tracks, playing, masterVolume, transitionMillis.coerceIn(40L, 80L))
    }

    private suspend fun setAmbientInternal(
        tracks: List<AmbientSound>,
        playing: Boolean,
        masterVolume: Float,
        transitionMillis: Long,
    ) {
        ambientCommandMutex.withLock {
            val enabled = tracks.filter { it.active }
            withContext(Dispatchers.Main.immediate) {
                engine.updatePlaybackLabels(ambient = enabled.joinToString("、") { it.name }.ifBlank { "未选择环境声" })
            }
            val enabledIds = enabled.mapTo(linkedSetOf()) { it.id }
            lastAmbientTracks = enabled.associate { it.id to it.volume }
            ambientSelection.value = lastAmbientTracks
            ambientMixer.reconcile(lastAmbientTracks, transitionMillis) { id -> catalogue[id]?.assetUri }
            withContext(Dispatchers.Main.immediate) { engine.setMasterVolume(masterVolume) }
            if (ambientMixer.lastError != null) fallbackToMedia3()
            val backendWasPlaying = ambientMixer.playing
            if (playing && enabled.isNotEmpty()) {
                // 轨道音量/集合更新不等于重新播放。旧实现每次 reconcile 都再次触发用户配置的
                // 3 秒淡入，导致滑杆拖到 0 后仍被旧 automation 回写、快速点击时命令堆积。
                if (!backendWasPlaying) {
                    if (ambientMixer is MiniaudioAmbientMixer && !engine.acquireSessionFocus()) {
                        throw IllegalStateException("环境声播放权限暂时不可用")
                    }
                    ambientMixer.play(fadeSeconds().toFloat())
                }
                engine.publishAmbientGraphPlaying(true)
                startBackgroundServiceIfEnabled()
                beginAmbientEvents(enabledIds)
                ambientEvents.keys.filterNot(enabledIds::contains).toList().forEach { closeAmbientEvent(it) }
            } else {
                if (backendWasPlaying) {
                    ambientMixer.pause(fadeSeconds().toFloat())
                    if (ambientMixer is MiniaudioAmbientMixer) engine.releaseSessionFocus()
                }
                engine.publishAmbientGraphPlaying(false)
                if (!engine.state.value.radioPlaying) playbackService.stop()
                ambientEvents.keys.toList().forEach { closeAmbientEvent(it) }
            }
            room.savePlayback(com.soundist.core.model.PlaybackSnapshot(enabled.map { com.soundist.core.model.MixTrack(it.id, it.volume) }, selectedRadioId, playing || engine.state.value.radioPlaying, System.currentTimeMillis(), masterVolume, engine.state.value.ambientVolume, engine.state.value.radioVolume, radioTrackIndex = engine.state.value.radioStationIndex))
        }
    }

    override suspend fun clearGeneratedAmbientAutomation() {
        generatedAmbientRevision += 1
        generatedAmbientJob?.cancel()
        generatedAmbientJob = null
        ambientCommandMutex.withLock { ambientMixer.setPans(emptyMap()) }
    }

    override suspend fun setGeneratedAmbient(
        arrangement: GeneratedArrangement,
        catalogue: List<AmbientSound>,
        playing: Boolean,
        masterVolume: Float,
        transitionMillis: Long,
    ) {
        val revision = ++generatedAmbientRevision
        generatedAmbientJob?.cancel()
        generatedAmbientJob = null

        if (arrangement.ambientMode == "current") {
            ambientCommandMutex.withLock { ambientMixer.setPans(emptyMap()) }
            setAmbientWithTransition(catalogue, playing && catalogue.any { it.active }, masterVolume, transitionMillis)
            return
        }

        val recipeById = arrangement.ambientTracks.filter { it.enabled }.associateBy { it.soundId }
        val recipeSounds = catalogue.map { sound ->
            val recipe = recipeById[sound.id]
            if (recipe == null) sound.copy(active = false)
            else sound.copy(
                active = true,
                // 预载保持播放器/解码器就绪；真正入场包络由下方运行时立即接管。
                volume = maxOf(recipe.volume.coerceIn(0, 100) / 100f, 0.002f),
            )
        }
        setAmbientWithTransition(recipeSounds, playing && recipeById.isNotEmpty(), masterVolume, transitionMillis)
        ambientCommandMutex.withLock {
            ambientMixer.setPans(recipeById.mapValues { (_, track) -> track.pan.toFloat() })
        }
        if (!playing || recipeById.isEmpty()) return

        val startedAt = System.nanoTime()
        generatedAmbientJob = radioEventScope.launch {
            while (revision == generatedAmbientRevision) {
                val elapsed = (System.nanoTime() - startedAt).coerceAtLeast(0L) / 1_000_000_000.0
                val runtime = generatedAmbientRuntime(arrangement, elapsed)
                ambientCommandMutex.withLock {
                    if (revision != generatedAmbientRevision) return@withLock
                    lastAmbientTracks = runtime
                    ambientMixer.reconcile(runtime) { id -> catalogue.firstOrNull { it.id == id }?.localAssetUri
                        ?: this@Media3ListeningAudioController.catalogue[id]?.assetUri }
                    if (ambientMixer.lastError != null) fallbackToMedia3()
                }
                delay(80)
            }
        }
    }

    override suspend fun setMasterVolume(volume: Float) {
        withContext(Dispatchers.Main.immediate) {
            engine.cancelVolumeAutomation()
            engine.setMasterVolume(volume)
        }
    }

    override suspend fun setAmbientVolume(volume: Float) {
        withContext(Dispatchers.Main.immediate) {
            engine.cancelVolumeAutomation()
            engine.setAmbientVolume(volume)
        }
    }

    /** 冷启动/场景恢复环境声：经当前 AmbientMixer（不直接走 engine，避免绕过 miniaudio 产生双引擎）。
     *  恢复用户最后的逐轨音量 + master，并按 playing 意图决定是否播放。 */
    suspend fun restoreAmbient(tracks: List<com.soundist.core.model.MixTrack>, masterVolume: Float, playing: Boolean) {
        ambientCommandMutex.withLock {
            val enabled = tracks.filter { it.enabled }.associate { it.soundId to it.volume }
            lastAmbientTracks = enabled
            ambientSelection.value = enabled
            ambientMixer.reconcile(enabled) { id -> catalogue[id]?.assetUri }
            withContext(Dispatchers.Main.immediate) { engine.setMasterVolume(masterVolume) }
            if (ambientMixer.lastError != null) fallbackToMedia3()
            if (playing && enabled.isNotEmpty()) {
                if (ambientMixer is MiniaudioAmbientMixer && !engine.acquireSessionFocus()) return@withLock
                ambientMixer.play(fadeSeconds().toFloat())
            } else {
                ambientMixer.pause(fadeSeconds().toFloat())
                if (ambientMixer is MiniaudioAmbientMixer) engine.releaseSessionFocus()
            }
        }
    }

    /** 进入后台且禁用后台播放时，暂停当前环境声后端（Media3 的 radio 由 engine.pause 处理）。 */
    fun pauseAmbientForBackground() {
        radioEventScope.launch {
            ambientCommandMutex.withLock {
                ambientMixer.pause(0f)
                if (ambientMixer is MiniaudioAmbientMixer) engine.releaseSessionFocus()
            }
        }
    }

    override suspend fun setRadioVolume(volume: Float) {
        withContext(Dispatchers.Main.immediate) {
            engine.cancelVolumeAutomation()
            engine.setRadioVolume(volume)
        }
    }

    override suspend fun updatePlaybackLabels(radio: String?) {
        withContext(Dispatchers.Main.immediate) { engine.updatePlaybackLabels(radio = radio) }
    }

    override suspend fun ensureBackgroundPlayback() {
        startBackgroundServiceIfEnabled()
    }

    override suspend fun playRadio(station: FeatureRadioStation, trackIndex: Int) {
        clearGeneratedAmbientAutomation()
        // 同一频道暂停后恢复：沿用当前播放器（保留曲目位置、队列与音量），不重建播放列表、不重复创建播放事件。
        val resumeSameStation = selectedRadioId == station.id &&
            engine.state.value.radioPlayback == RadioPlaybackState.PAUSED &&
            engine.state.value.radioTrackCount > 0
        if (resumeSameStation) {
            beginRadioEvent(
                station,
                station.tracks.getOrNull(trackIndex)?.id ?: station.localAudio.getOrNull(trackIndex)?.uri,
                station.sourceKind.name,
            )
            withContext(Dispatchers.Main.immediate) {
                engine.playRadio()
                engine.cancelVolumeAutomation()
                engine.setRadioVolume(engine.state.value.radioVolume)
            }
            startBackgroundServiceIfEnabled()
            val state = engine.state.value
            room.savePlayback(com.soundist.core.model.PlaybackSnapshot(state.tracks, station.id, true, System.currentTimeMillis(), state.masterVolume, state.ambientVolume, state.radioVolume, radioTrackIndex = state.radioStationIndex))
            return
        }
        val sources = buildPlayableSources(station)
        if (sources.isEmpty()) {
            // 频道没有任何可播放音源：明确错误，禁止假播放。
            val reason = when (station.sourceKind) {
                FeatureRadioSourceKind.LOCAL -> "本地音频文件已不可用，请重新选择本地音频"
                FeatureRadioSourceKind.STREAM -> "音频流地址无效或已被移除"
                else -> "「${station.name}」暂无可播放音频源"
            }
            throw RadioPlaybackException(RadioErrorType.PACK_NOT_INSTALLED, reason)
        }
        // 非生成频道没有编排声像：环境声轨全部复位居中（切出生成电台后的状态清理）。
        withContext(Dispatchers.Main.immediate) { engine.setAmbientPans(emptyMap()) }
        endRadioEvent("skipped")
        selectedRadioId = station.id
        beginRadioEvent(
            station,
            station.tracks.getOrNull(trackIndex)?.id ?: station.localAudio.getOrNull(trackIndex)?.uri,
            station.sourceKind.name,
        )
        engine.playRadioPlaylist(
            tracks = sources.map { RadioTrackSource(id = it.first, stationIndex = it.second, uri = it.third) },
            startIndex = trackIndex,
            transitionMode = station.transitionMode,
            transitionSeconds = station.transitionSeconds,
            autoplay = false,
        )
        withContext(Dispatchers.Main.immediate) {
            val targetVolume = engine.state.value.radioVolume
            if (fadeSeconds() > 0) engine.setRadioVolume(0f)
            engine.playRadio()
            if (fadeSeconds() > 0) engine.fadeTo(FadeTarget.RADIO, targetVolume, fadeSeconds() * 1_000L)
        }
        startBackgroundServiceIfEnabled()
        val state = engine.state.value
        room.savePlayback(com.soundist.core.model.PlaybackSnapshot(state.tracks, station.id, true, System.currentTimeMillis(), state.masterVolume, state.ambientVolume, state.radioVolume, radioTrackIndex = state.radioStationIndex))
    }

    /**
     * 组装频道可播放源（Triple<itemId, stationIndex, uri>），规则见
     * feature/listening `RadioStation.playableSources()`：仅允许 asset://、content://、file://
     * 与应用私有目录；http(s) 仅限用户主动添加的音频流频道（STREAM），官方曲目 mediaUrl 一律
     * 不作为生产播放地址（禁止热链）。
     */
    private fun buildPlayableSources(station: FeatureRadioStation): List<Triple<String, Int, String>> = station.playableSources()

    override suspend fun pauseRadio() {
        // 立即暂停（引擎同步发布 PAUSED，UI 立即反馈）；不阻塞在淡出延迟上，避免快速点击竞态。
        withContext(Dispatchers.Main.immediate) {
            engine.pauseRadio()
            engine.cancelVolumeAutomation()
            engine.setRadioVolume(engine.state.value.radioVolume)
        }
        if (!engine.state.value.ambientPlaying) playbackService.stop()
        markRadioPaused()
        val state = engine.state.value
        room.savePlayback(com.soundist.core.model.PlaybackSnapshot(state.tracks, selectedRadioId, state.ambientPlaying, System.currentTimeMillis(), state.masterVolume, state.ambientVolume, state.radioVolume, radioTrackIndex = state.radioStationIndex))
    }
    override suspend fun stopRadio() {
        withContext(Dispatchers.Main.immediate) { engine.stopRadio() }
        if (!engine.state.value.ambientPlaying) playbackService.stop()
        endRadioEvent("stopped")
        val state = engine.state.value
        room.savePlayback(com.soundist.core.model.PlaybackSnapshot(state.tracks, null, state.ambientPlaying, System.currentTimeMillis(), state.masterVolume, state.ambientVolume, state.radioVolume))
    }
    override suspend fun stopAll() {
        clearGeneratedAmbientAutomation()
        ambientCommandMutex.withLock {
            lastAmbientTracks = emptyMap()
            ambientSelection.value = emptyMap()
            ambientMixer.stop() /* 停止当前环境声后端（miniaudio 或 Media3），不能只停 engine */
            if (ambientMixer is MiniaudioAmbientMixer) engine.releaseSessionFocus()
            withContext(Dispatchers.Main.immediate) { engine.stop() }
        }
        ambientEvents.keys.toList().forEach { closeAmbientEvent(it) }
        endRadioEvent("stopped")
        playbackService.stop()
        val state = engine.state.value
        room.savePlayback(com.soundist.core.model.PlaybackSnapshot(state.tracks, null, false, System.currentTimeMillis(), state.masterVolume, state.ambientVolume, state.radioVolume))
    }

    private fun newPlaybackEvent(kind: PlaybackKind, sourceId: String, trackId: String? = null, sourceKind: String? = null) = PlaybackEvent(
        id = UUID.randomUUID().toString(), kind = kind, sourceId = sourceId,
        startedAt = System.currentTimeMillis(), endedAt = null, activeSeconds = 0, completed = false,
        trackId = trackId, sourceKind = sourceKind,
    )

    private suspend fun checkpointPlaybackEvents() {
        val now = System.currentTimeMillis()
        val snapshots = buildList {
            ambientEvents.values.forEach { event ->
                val elapsed = (now - event.startedAt).coerceAtLeast(0L)
                add(event.copy(activeSeconds = elapsed / 1_000L, listenedMs = elapsed))
            }
            radioEvent?.let { event ->
                val elapsed = radioListenedMs + ((radioSegmentResumedAt?.let { now - it } ?: 0L).coerceAtLeast(0L))
                add(event.copy(activeSeconds = elapsed / 1_000L, listenedMs = elapsed))
            }
        }
        records.savePlaybackEvents(snapshots)
    }

    private suspend fun beginAmbientEvents(ids: Collection<String>) {
        ids.forEach { id -> if (id !in ambientEvents) {
            val event = newPlaybackEvent(PlaybackKind.AMBIENT, id)
            ambientEvents[id] = event
            records.savePlaybackEvent(event)
        } }
    }

    private suspend fun closeAllAmbientEvents(reason: String) {
        ambientEvents.keys.toList().forEach { id ->
            ambientEvents.remove(id)?.let { records.savePlaybackEvent(it.closed(reason)) }
        }
    }
    private suspend fun closeAmbientEvent(id: String) = ambientEvents.remove(id)?.let { records.savePlaybackEvent(it.closed("stopped")) } ?: Unit

    /** 电台会话开始/续播：同一频道跨暂停复用同一事件；切换频道/停止/错误/播完才结束。 */
    private suspend fun beginRadioEvent(station: FeatureRadioStation, trackId: String?, sourceKind: String?) {
        if (radioEvent != null) {
            radioSegmentResumedAt = System.currentTimeMillis()
            radioEvent = radioEvent?.copy(completionReason = null)
            records.savePlaybackEvent(requireNotNull(radioEvent))
            return
        }
        radioEvent = newPlaybackEvent(
            kind = if (station.sourceKind == FeatureRadioSourceKind.LOCAL) PlaybackKind.LOCAL else PlaybackKind.RADIO,
            sourceId = station.id,
            trackId = trackId,
            sourceKind = sourceKind,
        )
        radioListenedMs = 0L
        radioSegmentResumedAt = System.currentTimeMillis()
        records.savePlaybackEvent(requireNotNull(radioEvent))
    }

    /** 暂停：累计真实收听时长（暂停时间不计入 listenedMs），保留同一事件。 */
    private suspend fun markRadioPaused() {
        val resumedAt = radioSegmentResumedAt ?: return
        radioListenedMs += (System.currentTimeMillis() - resumedAt).coerceAtLeast(0L)
        radioSegmentResumedAt = null
        radioEvent?.let { event ->
            val paused = event.copy(activeSeconds = radioListenedMs / 1_000L, listenedMs = radioListenedMs, completionReason = "paused")
            radioEvent = paused
            records.savePlaybackEvent(paused)
        }
    }

    /** 结束电台会话（停止/切频道/错误/播完）。 */
    private suspend fun endRadioEvent(reason: String) {
        val event = radioEvent ?: return
        val now = System.currentTimeMillis()
        val total = radioListenedMs + ((radioSegmentResumedAt?.let { now - it } ?: 0L).coerceAtLeast(0L))
        radioSegmentResumedAt = null
        radioEvent = null
        records.savePlaybackEvent(event.copy(endedAt = now, activeSeconds = total / 1_000L, listenedMs = total, completionReason = reason, completed = true))
    }

    private fun PlaybackEvent.closed(reason: String): PlaybackEvent {
        val now = System.currentTimeMillis()
        return copy(
            endedAt = now,
            activeSeconds = ((now - startedAt) / 1_000L).coerceAtLeast(1L),
            listenedMs = (now - startedAt).coerceAtLeast(0L),
            completionReason = reason,
            completed = true,
        )
    }
    private fun startBackgroundServiceIfEnabled() {
        if (!backgroundPlaybackEnabled()) return
        when (playbackService.startForActivePlayback()) {
            PlaybackServiceStartResult.NotificationPermissionRequired -> onNotificationPermissionRequired()
            else -> Unit
        }
    }
}

/** 确定性环境轨编排：同一 seed 在每个段落窗口得到同一结果，不按渲染帧随机开关。 */
private fun generatedAmbientRuntime(arrangement: GeneratedArrangement, elapsedSeconds: Double): Map<String, Float> {
    val windowSeconds = maxOf(30.0, arrangement.sectionMinutes.coerceAtLeast(1) * 60.0)
    val sceneEnergy = generatedSceneEnergy(arrangement, elapsedSeconds)
    return arrangement.ambientTracks.filter { it.enabled }.associate { track ->
        val afterEntry = elapsedSeconds - track.entryDelaySeconds.coerceAtLeast(0f)
        val windowIndex = if (afterEntry <= 0.0) 0L else kotlin.math.floor(afterEntry / windowSeconds).toLong()
        val inWindow = if (afterEntry <= 0.0) 0.0 else afterEntry - windowIndex * windowSeconds
        val available = afterEntry >= 0.0 && stableAmbientChance(arrangement.seed, track.soundId, windowIndex) <= track.probability.coerceIn(0, 100) / 100.0
        val activeSeconds = if (track.durationMinutes > 0) minOf(windowSeconds, track.durationMinutes * 60.0) else windowSeconds
        val fadeIn = track.fadeInSeconds.coerceAtLeast(0f).toDouble()
        val fadeOut = track.fadeOutSeconds.coerceAtLeast(0f).toDouble()
        val attack = if (fadeIn <= 0.0) 1.0 else (inWindow / fadeIn).coerceIn(0.0, 1.0)
        val release = if (fadeOut <= 0.0) {
            if (inWindow <= activeSeconds) 1.0 else 0.0
        } else {
            ((activeSeconds - inWindow) / fadeOut).coerceIn(0.0, 1.0)
        }
        val relation = when (track.relationship) {
            "follow" -> 0.62 + sceneEnergy * 0.38
            "avoid" -> 1.0 - sceneEnergy * 0.38
            "alternate" -> if (((inWindow / maxOf(12.0, windowSeconds / 4.0)).toLong() and 1L) == 0L) 1.0 else 0.42
            else -> 1.0
        }
        val envelope = if (available && inWindow <= activeSeconds) minOf(attack, release) else 0.0
        track.soundId to (track.volume.coerceIn(0, 100) / 100.0 * envelope * relation).toFloat().coerceIn(0f, 1f)
    }
}

private fun generatedSceneEnergy(arrangement: GeneratedArrangement, elapsedSeconds: Double): Double {
    val scenes = arrangement.scenes
    if (scenes.isNotEmpty()) {
        val total = scenes.sumOf { it.durationMinutes.coerceAtLeast(1) * 60.0 }.coerceAtLeast(1.0)
        var cursor = ((elapsedSeconds % total) + total) % total
        for (scene in scenes) {
            val duration = scene.durationMinutes.coerceAtLeast(1) * 60.0
            if (cursor < duration) return scene.energy.coerceIn(0, 100) / 100.0
            cursor -= duration
        }
    }
    return when (arrangement.arc) {
        "steady" -> 0.52
        "journey" -> {
            val phase = (elapsedSeconds % 108.0) / 108.0
            if (phase < 0.55) 0.32 + phase / 0.55 * 0.48 else 0.8 - (phase - 0.55) / 0.45 * 0.44
        }
        else -> 0.5 + kotlin.math.sin(elapsedSeconds * (2.0 * Math.PI / 26.0)) * 0.22
    }.coerceIn(0.0, 1.0)
}

private fun stableAmbientChance(seed: String, soundId: String, window: Long): Double {
    var value = 0xcbf29ce484222325UL.toLong()
    "$seed|$soundId|$window".forEach { char -> value = (value xor char.code.toLong()) * 0x100000001b3L }
    return ((value ushr 11) and ((1L shl 53) - 1)).toDouble() / (1L shl 53).toDouble()
}

/** 生成电台渲染器统一入口：既是被播器（GeneratedAudioRenderer），也是外部播放焦点控制器（ExternalPlaybackController）。
 *  Kotlin [NativeGeneratedAudioRenderer] 与 native [NativeEngineGenerativeRenderer] 两个实现共用。 */
interface GeneratedPlaybackController : GeneratedAudioRenderer, ExternalPlaybackController {
    /** 两个父接口都声明 setVolume(Float)，这里统一合并为单一抽象方法，消除多重继承冲突。 */
    override fun setVolume(value: Float)
}

class RecordedGeneratedAudioRenderer(
    private val delegate: GeneratedPlaybackController,
    private val records: RoomRecordsRepository,
    private val engine: Media3AudioEngine,
    private val playbackService: PlaybackServiceController,
    private val backgroundPlaybackEnabled: () -> Boolean,
    private val fadeSeconds: () -> Int,
    private val onNotificationPermissionRequired: () -> Unit,
) : GeneratedAudioRenderer {
    private val rendererScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val rendererEvents = MutableStateFlow(RadioEngineState())
    override val radioEvents: Flow<RadioEngineState> = rendererEvents.asStateFlow()
    /** 播放事件跨暂停保持：同一频道暂停/恢复复用同一事件，累计真实收听时长。 */
    private var active: PlaybackEvent? = null
    private var segmentResumedAt: Long? = null
    private var listenedMs: Long = 0L
    init {
        rendererScope.launch {
            delegate.radioEvents.collect { event ->
                rendererEvents.value = event
                when (event.state) {
                    PlaybackState.LOADING -> engine.publishExternalPlaybackState(selected = true, playing = false)
                    PlaybackState.PLAYING, PlaybackState.AUDIBLE -> engine.publishExternalPlaybackState(selected = true, playing = true)
                    PlaybackState.PAUSED -> engine.publishExternalPlaybackState(selected = true, playing = false)
                    PlaybackState.IDLE -> engine.publishExternalPlaybackState(selected = false, playing = false)
                    PlaybackState.ERROR -> engine.publishExternalPlaybackState(selected = false, playing = false, error = event.errorMessage ?: "持续声场播放失败")
                }
            }
        }
    }
    override suspend fun play(station: FeatureRadioStation, activeAmbient: List<AmbientSound>) {
        val resuming = active?.sourceId == station.id
        if (!resuming) endActive("skipped")
        check(engine.beginExternalPlayback(delegate)) { "无法获取音频焦点" }
        try {
            delegate.play(station, activeAmbient)
        } catch (failure: Throwable) {
            withContext(Dispatchers.Main.immediate) { engine.stopExternalPlayback() }
            throw failure
        }
        // 环境声配方、声像与自动化由 Media3ListeningAudioController 单点管理。
        // 这里不能再次写声像，否则“保留我的环境声”会被频道编排覆盖。
        if (backgroundPlaybackEnabled()) when (playbackService.startForActivePlayback()) {
            PlaybackServiceStartResult.NotificationPermissionRequired -> onNotificationPermissionRequired()
            else -> Unit
        }
        if (resuming) segmentResumedAt = System.currentTimeMillis() else beginActive(station)
    }
    override suspend fun pause() {
        // 立即暂停（渲染器事件回流 PAUSED），不阻塞在淡出延迟上。
        delegate.pause()
        withContext(Dispatchers.Main.immediate) { engine.pauseExternalPlayback() }
        if (!engine.state.value.masterPlaying) playbackService.stop()
        markPaused()
    }
    override suspend fun stop() {
        withContext(Dispatchers.Main.immediate) { engine.stopExternalPlayback() }
        if (!engine.state.value.masterPlaying) playbackService.stop()
        endActive("stopped")
    }
    override suspend fun previewTimbre(timbre: String) = delegate.previewTimbre(timbre)
    override fun setVolume(value: Float) = delegate.setVolume(value)
    private suspend fun beginActive(station: FeatureRadioStation) {
        active = PlaybackEvent(UUID.randomUUID().toString(), PlaybackKind.RADIO, station.id, System.currentTimeMillis(), null, 0, false, sourceKind = station.sourceKind.name)
        listenedMs = 0L
        segmentResumedAt = System.currentTimeMillis()
        records.savePlaybackEvent(requireNotNull(active))
    }
    private suspend fun markPaused() {
        val resumedAt = segmentResumedAt ?: return
        listenedMs += (System.currentTimeMillis() - resumedAt).coerceAtLeast(0L)
        segmentResumedAt = null
        active?.let { records.savePlaybackEvent(it.copy(listenedMs = listenedMs)) }
    }
    private suspend fun endActive(reason: String) {
        val event = active ?: return
        val now = System.currentTimeMillis()
        val total = listenedMs + ((segmentResumedAt?.let { now - it } ?: 0L).coerceAtLeast(0L))
        segmentResumedAt = null
        active = null
        records.savePlaybackEvent(event.copy(endedAt = now, activeSeconds = total / 1_000L, listenedMs = total, completionReason = reason, completed = true))
    }
}

private fun FeatureRadioStation.domain() = com.soundist.core.model.RadioStation(
    id = id,
    name = name,
    streamUri = url,
    artworkUri = artworkUri,
    isFavorite = false,
    description = description,
    sourceKind = when (sourceKind) {
        FeatureRadioSourceKind.GENERATED -> com.soundist.core.model.RadioSourceKind.GENERATED
        FeatureRadioSourceKind.OFFICIAL -> com.soundist.core.model.RadioSourceKind.OFFICIAL
        FeatureRadioSourceKind.LOCAL -> com.soundist.core.model.RadioSourceKind.LOCAL
        FeatureRadioSourceKind.STREAM -> com.soundist.core.model.RadioSourceKind.CUSTOM
    },
    genre = genre,
    purpose = purposes.joinToString("|"),
    updatedAt = System.currentTimeMillis(),
    payloadJson = stationPayloadJson(),
)

private fun com.soundist.core.model.RadioStation.feature(): FeatureRadioStation {
    val payload = payloadJson?.let { runCatching { JSONObject(it) }.getOrNull() }
    val local = payload?.optJSONArray("localAudio").objects().map { item ->
        LocalAudioSelection(item.text("uri"), item.text("displayName"), item.optLong("sizeBytes"), item.text("mimeType"), item.optInt("durationSeconds"))
    }
    val arrangement = payload?.optJSONObject("generatorArrangement")?.generatedArrangement()
    return FeatureRadioStation(
        id = id,
        name = name,
        description = description,
        group = when (sourceKind) {
            com.soundist.core.model.RadioSourceKind.GENERATED -> RadioGroup.GENERATED
            com.soundist.core.model.RadioSourceKind.OFFICIAL -> RadioGroup.OFFICIAL
            else -> RadioGroup.CUSTOM
        },
        source = payload?.optString("source")?.takeIf(String::isNotBlank) ?: sourceKind.name,
        url = streamUri,
        artworkUri = artworkUri,
        genre = genre.ifBlank { "氛围" },
        sourceKind = when (sourceKind) {
            com.soundist.core.model.RadioSourceKind.GENERATED -> FeatureRadioSourceKind.GENERATED
            com.soundist.core.model.RadioSourceKind.OFFICIAL -> FeatureRadioSourceKind.OFFICIAL
            com.soundist.core.model.RadioSourceKind.LOCAL -> FeatureRadioSourceKind.LOCAL
            com.soundist.core.model.RadioSourceKind.CUSTOM -> FeatureRadioSourceKind.STREAM
        },
        purposes = purpose.split('|').filter(String::isNotBlank),
        durationLabel = payload?.optString("durationLabel").orEmpty(),
        tracks = payload?.optJSONArray("tracks").objects().map(JSONObject::radioTrack),
        layers = payload?.optJSONArray("layers").objects().map(JSONObject::generatedLayer),
        generatorPresetId = payload?.nullableText("generatorPresetId"),
        generatorSettings = payload?.optJSONObject("generatorSettings")?.generatorSettings(),
        generatorArrangement = arrangement,
        localAudio = local,
        custom = payload?.optBoolean("custom", sourceKind == com.soundist.core.model.RadioSourceKind.CUSTOM || sourceKind == com.soundist.core.model.RadioSourceKind.LOCAL)
            ?: (sourceKind == com.soundist.core.model.RadioSourceKind.CUSTOM || sourceKind == com.soundist.core.model.RadioSourceKind.LOCAL),
    )
}

private fun FeatureRadioStation.stationPayloadJson() = JSONObject().apply {
    put("version", 1)
    put("source", source)
    put("durationLabel", durationLabel)
    put("custom", custom)
    put("generatorPresetId", generatorPresetId ?: JSONObject.NULL)
    put("tracks", tracks.toJsonArray { it.json() })
    put("layers", layers.toJsonArray { it.json() })
    put("localAudio", localAudio.toJsonArray { JSONObject().put("uri", it.uri).put("displayName", it.displayName).put("sizeBytes", it.sizeBytes).put("mimeType", it.mimeType).put("durationSeconds", it.durationSeconds) })
    generatorSettings?.let { put("generatorSettings", it.json()) }
    generatorArrangement?.let { put("generatorArrangement", it.json()) }
}.toString()

private fun RadioTrack.json() = JSONObject().put("id", id).put("title", title).put("artist", artist).put("durationLabel", durationLabel)
    .put("mediaUrl", mediaUrl).put("sourcePage", sourcePage).put("remoteCacheKey", remoteCacheKey).put("localAssetUri", localAssetUri ?: JSONObject.NULL)
    .also { result -> license?.let { result.put("license", it.json()) } }
private fun TrackLicense.json() = JSONObject().put("name", name).put("author", author).put("sourceName", sourceName).put("sourcePage", sourcePage).put("licenseUrl", licenseUrl).put("attributionText", attributionText)
private fun GeneratorSettings.json() = JSONObject().put("tempo", tempo).put("density", density).put("variation", variation).put("entryDelaySeconds", entryDelaySeconds).put("eventDurationSeconds", eventDurationSeconds).put("gain", gain).put("ambientResponse", ambientResponse).put("adaptive", adaptive)
private fun GeneratedScene.json() = JSONObject().put("id", id).put("name", name).put("durationMinutes", durationMinutes).put("energy", energy).put("density", density).put("brightness", brightness).put("space", space)
private fun GeneratedAmbientTrack.json() = JSONObject().put("soundId", soundId).put("name", name).put("category", category).put("enabled", enabled).put("volume", volume).put("pan", pan).put("entryDelaySeconds", entryDelaySeconds).put("durationMinutes", durationMinutes).put("probability", probability).put("fadeInSeconds", fadeInSeconds).put("fadeOutSeconds", fadeOutSeconds).put("relationship", relationship)
private fun GeneratedLayer.json() = JSONObject().put("id", id).put("name", name).put("type", type).put("volume", volume).put("probability", probability).put("timbre", timbre).put("enabled", enabled).put("solo", solo).put("pan", pan).put("entryDelaySeconds", entryDelaySeconds).put("durationSeconds", durationSeconds).put("density", density).put("rhythm", rhythm).put("register", register).put("scale", scale).put("fadeInSeconds", fadeInSeconds).put("fadeOutSeconds", fadeOutSeconds).put("ambientResponse", ambientResponse).put("relationship", relationship).put("relationshipTargetId", relationshipTargetId ?: JSONObject.NULL)
private fun GeneratedArrangement.json() = JSONObject().put("seed", seed).put("sectionMinutes", sectionMinutes).put("arc", arc).put("layers", layers.toJsonArray { it.json() }).put("ambientTracks", ambientTracks.toJsonArray { it.json() }).put("scenes", scenes.toJsonArray { it.json() }).put("ambientMode", ambientMode)

private fun JSONObject.radioTrack(): RadioTrack {
    val license = optJSONObject("license")?.let { TrackLicense(it.text("name"), it.text("author"), it.text("sourceName"), it.text("sourcePage"), it.text("licenseUrl"), it.text("attributionText")) }
    return RadioTrack(text("id"), text("title"), text("artist"), text("durationLabel"), text("mediaUrl"), text("sourcePage"), text("remoteCacheKey"), nullableText("localAssetUri"), license)
}
private fun JSONObject.generatorSettings() = GeneratorSettings(optInt("tempo", 54), optInt("density", 42), optInt("variation", 48), optDouble("entryDelaySeconds", 1.4).toFloat(), optDouble("eventDurationSeconds", 3.2).toFloat(), optInt("gain", 72), optInt("ambientResponse", 70), optBoolean("adaptive", true))
private fun JSONObject.generatedScene() = GeneratedScene(text("id"), text("name"), optInt("durationMinutes"), optInt("energy"), optInt("density"), optInt("brightness"), optInt("space"))
private fun JSONObject.generatedAmbient() = GeneratedAmbientTrack(text("soundId"), text("name"), text("category"), optBoolean("enabled", true), optInt("volume"), optInt("pan"), optDouble("entryDelaySeconds").toFloat(), optInt("durationMinutes"), optInt("probability", 100), optDouble("fadeInSeconds", 2.0).toFloat(), optDouble("fadeOutSeconds", 3.0).toFloat(), text("relationship", "independent"))
private fun JSONObject.generatedLayer() = GeneratedLayer(text("id"), text("name"), text("type"), optInt("volume"), optInt("probability"), text("timbre", "air-pad"), optBoolean("enabled", true), optBoolean("solo"), optInt("pan"), optDouble("entryDelaySeconds").toFloat(), optDouble("durationSeconds").toFloat(), optInt("density"), text("rhythm", "free"), text("register", "middle"), text("scale", "pentatonic"), optDouble("fadeInSeconds").toFloat(), optDouble("fadeOutSeconds").toFloat(), optInt("ambientResponse", 64), text("relationship", "independent"), nullableText("relationshipTargetId"))
private fun JSONObject.generatedArrangement() = GeneratedArrangement(text("seed"), optInt("sectionMinutes"), text("arc"), optJSONArray("layers").objects().map(JSONObject::generatedLayer), optJSONArray("ambientTracks").objects().map(JSONObject::generatedAmbient), optJSONArray("scenes").objects().map(JSONObject::generatedScene), text("ambientMode", "preset"))
private fun JSONObject.text(key: String, fallback: String = "") = optString(key, fallback)
private fun JSONObject.nullableText(key: String) = if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)
private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
private inline fun <T> Iterable<T>.toJsonArray(transform: (T) -> JSONObject) = JSONArray().also { array -> forEach { array.put(transform(it)) } }

fun createProductivityDependencies(context: Context, services: SoundistServices, audioEngine: Media3AudioEngine, ambientMixer: () -> AmbientMixer, onNotificationPermissionRequired: () -> Unit, reviewNoteWriter: ((FocusReviewNote) -> Unit)? = null, focusAudioSnapshot: () -> FocusAudioSnapshot = { FocusAudioSnapshot() }): ProductivityDependencies {
    val sleepAudio = Media3SleepAudioController(audioEngine, ambientMixer)
    val repository = FeatureRoomProductivityRepository(services.productivity, sleepAudio, FocusSettingsStore(context))
    // App.tsx quickPresets：内置（FeatureSoundCatalog.builtInPresets）+ 已存自定义，供默认声场下拉使用。
    val scenePresets = MutableStateFlow(emptyList<Pair<String, String>>())
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        services.sounds.observePresets().collect { stored ->
            val custom = stored.filterNot { it.builtIn }.map { it.id to it.name }
            val overrides = stored.filter { it.builtIn }.associate { it.id to it.name }
            scenePresets.value = FeatureSoundCatalog.builtInPresets.map { it.id to (overrides[it.id] ?: it.name) } + custom
        }
    }
    return ProductivityDependencies(
        repository = repository,
        reminders = AndroidReminderAdapter(context, onNotificationPermissionRequired),
        focusController = RoomFocusSessionController(services.productivity),
        focusPersistence = RoomFocusPersistenceStore(services.productivity),
        focusAlerts = AndroidFocusTransitionAlerts(context, onNotificationPermissionRequired),
        focusScenes = RoomFocusSceneController(services.sounds, audioEngine, ambientMixer),
        sleepAudio = sleepAudio,
        habitChecks = RoomHabitCheckStore(services.productivity),
        reviewNoteWriter = reviewNoteWriter,
        focusAudioSnapshot = focusAudioSnapshot,
        scenePresets = scenePresets,
    )
}

private class AndroidFocusTransitionAlerts(
    private val context: Context,
    private val permissionRequired: () -> Unit,
) : FocusTransitionAlerts {
    override suspend fun schedule(phase: SessionPhase, triggerAtEpochMillis: Long) {
        try {
            ReminderScheduler.scheduleFocusTransition(context, phase, triggerAtEpochMillis)
        } catch (_: SecurityException) {
            PendingFocusTransitionPermission.remember(phase, triggerAtEpochMillis)
            withContext(Dispatchers.Main) { permissionRequired() }
        }
    }

    override suspend fun notifyNow(phase: SessionPhase, triggerAtEpochMillis: Long) {
        ReminderScheduler.deliverFocusTransition(context, phase, triggerAtEpochMillis)
    }

    override suspend fun cancel() {
        PendingFocusTransitionPermission.clear()
        ReminderScheduler.cancelFocusTransition(context)
    }
}

private object PendingFocusTransitionPermission {
    @Volatile private var request: Pair<SessionPhase, Long>? = null
    fun remember(phase: SessionPhase, triggerAtEpochMillis: Long) {
        request = phase to triggerAtEpochMillis
    }
    fun clear() { request = null }
    fun retry(context: Context) {
        val pending = request ?: return
        if (pending.second > System.currentTimeMillis()) {
            ReminderScheduler.scheduleFocusTransition(context, pending.first, pending.second)
        }
        request = null
    }
}

private class FocusSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("soundist-focus-settings", Context.MODE_PRIVATE)
    fun load() = FocusSessionState(
        timerMode = runCatching { TimerMode.valueOf(prefs.getString("timerMode", TimerMode.COUNTDOWN.name)!!) }.getOrDefault(TimerMode.COUNTDOWN),
        phase = runCatching { SessionPhase.valueOf(prefs.getString("phase", SessionPhase.FOCUS.name)!!) }.getOrDefault(SessionPhase.FOCUS),
        focusMinutes = prefs.getInt("focusMinutes", 25).coerceIn(1, 240),
        breakMinutes = prefs.getInt("breakMinutes", 5).coerceIn(1, 120),
        cycle = com.soundist.feature.productivity.FocusCycle(
            round = prefs.getInt("round", 1).coerceAtLeast(1),
            rounds = prefs.getInt("rounds", 4).coerceIn(1, 12),
            longBreakMinutes = prefs.getInt("longBreakMinutes", 15).coerceIn(1, 120),
            autoBreak = prefs.getBoolean("autoBreak", true),
            autoFocus = prefs.getBoolean("autoFocus", false),
        ),
    )
    fun save(focus: FocusSessionState) {
        prefs.edit()
            .putString("timerMode", focus.timerMode.name)
            .putString("phase", focus.phase.name)
            .putInt("focusMinutes", focus.focusMinutes)
            .putInt("breakMinutes", focus.breakMinutes)
            .putInt("round", focus.cycle.round)
            .putInt("rounds", focus.cycle.rounds)
            .putInt("longBreakMinutes", focus.cycle.longBreakMinutes)
            .putBoolean("autoBreak", focus.cycle.autoBreak)
            .putBoolean("autoFocus", focus.cycle.autoFocus)
            .apply()
    }
}

private class FeatureRoomProductivityRepository(
    private val room: RoomProductivityRepository,
    private val sleepAudio: Media3SleepAudioController,
    private val focusSettings: FocusSettingsStore,
) : ProductivityRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutable = MutableStateFlow(ProductivityState(focus = focusSettings.load()))
    private val writeMutex = Mutex()
    override val state: StateFlow<ProductivityState> = mutable

    init {
        scope.launch {
            combine(
                combine(room.observeTodos(), room.observePlans(), room.observeHabits(), room.observeCountdowns(), room.observeFocusSessions()) { todos, plans, habits, countdowns, sessions ->
                    listOf(todos, plans, habits, countdowns, sessions)
                },
                room.observeHabitChecks(),
            ) { collections, checks ->
                @Suppress("UNCHECKED_CAST") val todos = collections[0] as List<com.soundist.core.model.Todo>
                @Suppress("UNCHECKED_CAST") val plans = collections[1] as List<com.soundist.core.model.Plan>
                @Suppress("UNCHECKED_CAST") val habits = collections[2] as List<com.soundist.core.model.Habit>
                @Suppress("UNCHECKED_CAST") val countdowns = collections[3] as List<com.soundist.core.model.Countdown>
                @Suppress("UNCHECKED_CAST") val sessions = collections[4] as List<com.soundist.core.model.FocusSession>
                val zone = ZoneId.systemDefault()
                val today = java.time.LocalDate.now(zone)
                val todayCounts = checks.filter { Instant.ofEpochMilli(it.checkedAt).atZone(zone).toLocalDate() == today }
                    .groupBy { it.habitId }.mapValues { (_, values) -> values.sumOf { it.value } }
                // `focus-active` is the resumable controller row, not a historical record.
                // Older builds could leave it terminal as well as writing the review record;
                // excluding it prevents duplicate records and double-counted habit minutes.
                val historicalSessions = sessions.filter { it.id != ACTIVE_FOCUS_SESSION_ID }
                val completedHabitSessions = historicalSessions.filter { it.status == DomainSessionStatus.COMPLETED && it.targetKind == FocusTargetKind.HABIT && it.targetId != null }
                val todayMinutes = completedHabitSessions.filter { Instant.ofEpochMilli(it.completedAt ?: it.startedAt).atZone(zone).toLocalDate() == today }
                    .groupBy { it.targetId!! }.mapValues { (_, values) -> values.sumOf { it.completionMinutes.takeIf { minutes -> minutes > 0 } ?: (it.elapsedSeconds / 60L).toInt() } }
                val active = sessions.firstOrNull { it.status == DomainSessionStatus.RUNNING || it.status == DomainSessionStatus.PAUSED || it.status == DomainSessionStatus.REVIEW }
                mutable.value.copy(
                    todos = todos.map { it.feature() },
                    plans = plans.map { it.feature() },
                    habits = habits.map { habit -> habit.feature(todayCounts[habit.id] ?: 0, todayMinutes[habit.id] ?: 0, habitStreak(habit, checks, completedHabitSessions, zone, today)) },
                    habitChecks = checks.map { com.soundist.feature.productivity.HabitCheck(it.id, it.habitId, it.checkedAt, it.value) },
                    countdowns = countdowns.map { it.feature() },
                    focusRecords = historicalSessions.filter { it.status == DomainSessionStatus.COMPLETED || it.status == DomainSessionStatus.CANCELLED }.map { it.featureRecord() },
                    focus = active?.featureSession() ?: mutable.value.focus,
                )
            }.combine(combine(room.observeSleepRoutines(), room.observeSleepSessions()) { routines, sessions -> routines to sessions }) { state, sleep ->
                sleep.second.firstOrNull { it.status == DomainSessionStatus.RUNNING || it.status == DomainSessionStatus.PAUSED }
                    ?.takeIf { it.volumeSnapshotCaptured }?.let(sleepAudio::loadPersistedSnapshot)
                state.copy(
                    sleepRoutines = sleep.first.map { it.feature() },
                    sleepRecords = sleep.second.filter { it.status == DomainSessionStatus.COMPLETED || it.status == DomainSessionStatus.CANCELLED }.map { it.featureRecord() },
                    sleep = sleep.second.firstOrNull { it.status == DomainSessionStatus.RUNNING || it.status == DomainSessionStatus.PAUSED }?.feature() ?: state.sleep,
                )
            }.collect { loaded -> mutable.value = loaded }
        }
    }

    override suspend fun update(transform: (ProductivityState) -> ProductivityState) = writeMutex.withLock {
        val before = mutable.value
        val after = transform(before)
        mutable.value = after
        persistDiff(before, after)
    }

    private suspend fun persistDiff(before: ProductivityState, after: ProductivityState) {
        persistList(before.todos, after.todos, { it.id }, { room.saveTodo(it.domain()) }, room::deleteTodo)
        persistList(before.plans, after.plans, { it.id }, { room.savePlan(it.domain()) }, room::deletePlan)
        persistList(before.habits, after.habits, { it.id }, { room.saveHabit(it.domain()) }, room::deleteHabit)
        persistList(before.countdowns, after.countdowns, { it.id }, { room.saveCountdown(it.domain()) }, room::deleteCountdown)
        persistList(before.focusRecords, after.focusRecords, { it.id }, { room.saveSession(it.domain()) }, { })
        persistList(before.sleepRoutines, after.sleepRoutines, { it.id }, { room.saveSleepRoutine(it.domain()) }, room::deleteSleepRoutine)
        persistList(before.sleepRecords, after.sleepRecords, { it.id }, { room.saveSleepSession(it.domain()) }, { })
        if (!before.focus.hasSameSettings(after.focus)) focusSettings.save(after.focus)
        if (before.focus != after.focus && after.focus.startedAtEpochMillis != null) room.saveSession(after.focus.domain())
        if (before.sleep != after.sleep && after.sleep.startedAtEpochMillis != null && after.sleep.endsAtEpochMillis != null) room.saveSleepSession(after.sleep.domain(sleepAudio.snapshotValues()))
    }

    private suspend fun <T> persistList(before: List<T>, after: List<T>, id: (T) -> String, save: suspend (T) -> Unit, delete: suspend (String) -> Unit) {
        val old = before.associateBy(id); val next = after.associateBy(id)
        after.filter { old[id(it)] != it }.forEach { save(it) }
        old.keys.filterNot(next::containsKey).forEach { delete(it) }
    }
}

private class AndroidReminderAdapter(private val context: Context, private val permissionRequired: () -> Unit) : FeatureReminderScheduler {
    override suspend fun replace(ownerId: String, triggerAtEpochMillis: Long, title: String) {
        try {
            ReminderScheduler.schedule(context, ownerId, title, triggerAtEpochMillis, exact = true)
        } catch (error: SecurityException) {
            PendingReminderPermission.remember(ownerId, triggerAtEpochMillis, title)
            withContext(Dispatchers.Main) { permissionRequired() }
        }
    }
    override suspend fun cancel(ownerId: String) = ReminderScheduler.cancel(context, ownerId)
}

private object PendingReminderPermission {
    @Volatile private var request: Triple<String, Long, String>? = null
    fun remember(ownerId: String, triggerAtEpochMillis: Long, title: String) { request = Triple(ownerId, triggerAtEpochMillis, title) }
    fun retry(context: Context) {
        val pending = request ?: return
        if (pending.second <= System.currentTimeMillis()) { request = null; return }
        ReminderScheduler.schedule(context, pending.first, pending.third, pending.second, exact = true)
        request = null
    }
}

internal fun retryPendingReminderAfterPermission(context: Context) {
    PendingReminderPermission.retry(context)
    PendingFocusTransitionPermission.retry(context)
}

private class RoomFocusSessionController(private val room: RoomProductivityRepository) : FocusSessionController {
    override suspend fun started(session: FocusSessionState) = room.saveSession(session.domain())
    override suspend fun paused(session: FocusSessionState) = room.saveSession(session.domain())
    override suspend fun stopped() = Unit
}

private class RoomHabitCheckStore(private val room: RoomProductivityRepository) : HabitCheckStore {
    override suspend fun save(check: com.soundist.feature.productivity.HabitCheck) = room.saveHabitCheck(com.soundist.core.model.HabitCheck(check.id, check.habitId, check.checkedAtEpochMillis, check.value))
    override suspend fun delete(checkId: String) = room.deleteHabitCheck(checkId)
}

private class RoomFocusPersistenceStore(private val room: RoomProductivityRepository) : FocusPersistenceStore {
    override suspend fun persist(event: FocusPersistenceEvent) {
        val terminal = event.kind in setOf(FocusPersistenceEventKind.COMPLETED, FocusPersistenceEventKind.INTERRUPTED, FocusPersistenceEventKind.RESET)
        val active = event.session.domain(event.occurredAtEpochMillis).copy(
            status = when (event.kind) {
                FocusPersistenceEventKind.STARTED -> DomainSessionStatus.RUNNING
                FocusPersistenceEventKind.PAUSED -> DomainSessionStatus.PAUSED
                FocusPersistenceEventKind.REVIEW_REQUESTED -> DomainSessionStatus.REVIEW
                // The reserved controller row only supports process restoration. A separate
                // immutable record is written below when the review is saved.
                FocusPersistenceEventKind.COMPLETED, FocusPersistenceEventKind.INTERRUPTED, FocusPersistenceEventKind.RESET -> DomainSessionStatus.IDLE
            },
            completedAt = event.occurredAtEpochMillis.takeIf { terminal },
        )
        room.saveSession(active)
        event.record?.let { room.saveSession(it.domain()) }
    }
}

private class RoomFocusSceneController(
    private val sounds: com.soundist.core.database.RoomSoundRepository,
    private val audio: Media3AudioEngine,
    private val ambientMixer: () -> AmbientMixer,
) : FocusSceneController {
    override suspend fun applyPreset(id: String) {
        val preset = sounds.observePresets().first().firstOrNull { it.id == id } ?: return
        val catalogue = com.soundist.core.model.SoundCatalog.sounds.associateBy { it.id }
        val tracks = preset.tracks.filter { it.enabled }.associate { it.soundId to it.volume }
        // 专注预设统一经 AmbientMixer（不直接 audio.setTrack/playAmbient，避免开启 miniaudio 时双引擎）。
        val mixer = ambientMixer()
        mixer.reconcile(tracks) { soundId -> catalogue[soundId]?.assetUri }
        if (mixer is MiniaudioAmbientMixer && !audio.acquireSessionFocus()) {
            android.util.Log.e("SoundistMiniaudio", "专注预设 AudioFocus 被拒绝")
        }
        mixer.play(0f)
    }
}

private const val ACTIVE_FOCUS_SESSION_ID = "focus-active"

private fun FocusSessionState.hasSameSettings(other: FocusSessionState): Boolean =
    timerMode == other.timerMode && phase == other.phase && focusMinutes == other.focusMinutes && breakMinutes == other.breakMinutes && cycle == other.cycle

private fun habitStreak(
    habit: com.soundist.core.model.Habit,
    checks: List<com.soundist.core.model.HabitCheck>,
    sessions: List<com.soundist.core.model.FocusSession>,
    zone: ZoneId,
    today: java.time.LocalDate,
): Int {
    val countByDay = checks.filter { it.habitId == habit.id }.groupBy { Instant.ofEpochMilli(it.checkedAt).atZone(zone).toLocalDate() }.mapValues { (_, rows) -> rows.sumOf { it.value } }
    val minutesByDay = sessions.filter { it.targetId == habit.id }.groupBy { Instant.ofEpochMilli(it.completedAt ?: it.startedAt).atZone(zone).toLocalDate() }.mapValues { (_, rows) -> rows.sumOf { it.completionMinutes.takeIf { minutes -> minutes > 0 } ?: (it.elapsedSeconds / 60L).toInt() } }
    fun scheduled(day: java.time.LocalDate) = (day.dayOfWeek.value % 7) in habit.weekdays
    fun complete(day: java.time.LocalDate) = when (habit.metric) {
        com.soundist.core.model.HabitMetric.MINUTES -> (minutesByDay[day] ?: 0) >= habit.dailyTarget
        else -> (countByDay[day] ?: 0) >= habit.dailyTarget
    }
    var day = today
    if (scheduled(day) && !complete(day)) day = day.minusDays(1)
    var streak = 0
    var inspected = 0
    while (inspected++ < 3_660 && (habit.weekdays.isNotEmpty())) {
        if (scheduled(day)) {
            if (!complete(day)) break
            streak++
        }
        day = day.minusDays(1)
    }
    return streak
}

private class Media3SleepAudioController(
    private val audio: Media3AudioEngine,
    private val ambientMixer: () -> AmbientMixer,
) : SleepAudioController {
    private var master = 1f; private var ambient = 1f; private var radio = .8f
    override suspend fun snapshotVolumes(): Boolean { val state = audio.state.value; master = state.masterVolume; ambient = state.ambientVolume; radio = state.radioVolume; return true }
    fun loadPersistedSnapshot(session: com.soundist.core.model.SleepSession) {
        master = session.masterVolumeSnapshot; ambient = session.ambientVolumeSnapshot; radio = session.radioVolumeSnapshot
    }
    fun snapshotValues() = Triple(master, ambient, radio)
    override suspend fun beginFade(target: SleepTarget, durationMillis: Long) {
        // 渐弱统一走引擎语义音量 fadeToZero：ALL/AMBIENT 分别淡 master/ambient，miniaudio 经
        // controller 的 state+duck 流实时跟随（master×ambient×duck×muted）。
        withContext(Dispatchers.Main.immediate) {
            audio.fadeToZero(
                when (target) { SleepTarget.ALL -> FadeTarget.ALL; SleepTarget.AMBIENT -> FadeTarget.AMBIENT; SleepTarget.RADIO -> FadeTarget.RADIO },
                durationMillis,
            )
        }
    }
    override suspend fun stop(target: SleepTarget) {
        withContext(Dispatchers.Main.immediate) { audio.cancelVolumeAutomation() }
        when (target) {
            SleepTarget.ALL -> {
                ambientMixer().stop()
                withContext(Dispatchers.Main.immediate) { audio.stop() }
            }
            SleepTarget.AMBIENT -> ambientMixer().stop()
            SleepTarget.RADIO -> withContext(Dispatchers.Main.immediate) { audio.pauseRadio() }
        }
    }
    override suspend fun restoreSnapshot() = withContext(Dispatchers.Main.immediate) {
        audio.cancelVolumeAutomation()
        audio.setMasterVolume(master)
        audio.setAmbientVolume(ambient)
        audio.setRadioVolume(radio)
    }
}

private fun hhMmOf(millis: Long?): String = millis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) } ?: ""
private fun com.soundist.core.model.Todo.feature() = Todo(id, title, if (longTerm) TodoKind.LONG_TERM else TodoKind.ONE_OFF, completed, dueAt, allDay, priority.feature(), reminderAt?.let { ReminderOffset.AT_TIME } ?: ReminderOffset.NONE, estimatedMinutes ?: 25, planId, note, defaultSceneId, archived, position.toInt(), dueTime = if (allDay) "" else hhMmOf(dueAt))
private fun Todo.domain() = com.soundist.core.model.Todo(id, text, done, dueAtEpochMillis, System.currentTimeMillis(), kind == TodoKind.LONG_TERM, allDay, priority.domain(), dueAtEpochMillis.takeIf { reminder != ReminderOffset.NONE }, estimatedMinutes, planId, note, defaultSceneId, archived, order.toDouble())
private fun com.soundist.core.model.Plan.feature() = FocusPlan(id, title, PlanScope.WEEK, Instant.ofEpochMilli(startAt).atZone(ZoneId.systemDefault()).toLocalDate(), endAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }, targetMinutes, accumulatedMinutes, description, milestone, reminderAt?.let { ReminderOffset.AT_TIME } ?: ReminderOffset.NONE, defaultSceneId, archived, position.toInt())
private fun FocusPlan.domain() = com.soundist.core.model.Plan(id, title, (startDate ?: java.time.LocalDate.now()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), endDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(), System.currentTimeMillis(), targetMinutes, doneMinutes, milestone, description, endDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli().takeIf { reminder != ReminderOffset.NONE }, defaultSceneId, archived, order.toDouble())
private fun com.soundist.core.model.Habit.feature(todayCount: Int, todayMinutes: Int, calculatedStreak: Int) = Habit(id, name, weekdays, metric.feature(), if (metric == com.soundist.core.model.HabitMetric.MINUTES) dailyTarget else 30, dailyTarget, calculatedStreak, todayMinutes, todayCount, reminderAt?.let { ReminderOffset.AT_TIME } ?: ReminderOffset.NONE, reminderHour, reminderMinute, null, defaultSceneId, archived, position.toInt())
private fun Habit.domain() = com.soundist.core.model.Habit(id, title, weekdays.size, System.currentTimeMillis(), weekdays, metric.domain(), if (metric == HabitMetric.MINUTES) targetMinutes else targetCount, streak, java.time.ZonedDateTime.now().with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.of(weekdays.minOrNull()?.let { if (it == 0) 7 else it } ?: 1))).withHour(reminderHour).withMinute(reminderMinute).withSecond(0).withNano(0).toInstant().toEpochMilli().takeIf { reminder != ReminderOffset.NONE }, defaultSceneId, archived, order.toDouble(), reminderHour, reminderMinute)
private fun com.soundist.core.model.Countdown.feature() = CountdownEvent(id, title, targetAt, note, dailyMinutes, reminderAt?.let { ReminderOffset.AT_TIME } ?: ReminderOffset.NONE, planId, milestone, investedMinutes, archived, position.toInt(), targetTime = hhMmOf(targetAt))
private fun CountdownEvent.domain() = com.soundist.core.model.Countdown(id, title, targetAtEpochMillis, System.currentTimeMillis(), note, dailyMinutes, investedMinutes, planId, milestone, targetAtEpochMillis.takeIf { reminder != ReminderOffset.NONE }, archived, order.toDouble())
private fun com.soundist.core.model.SleepRoutine.feature() = SleepRoutine(id, name, minutes, target.feature(), fadeMinutes, endMode.feature(), clockTime?.substringBefore(':')?.toIntOrNull() ?: 23, clockTime?.substringAfter(':')?.toIntOrNull() ?: 30)
private fun SleepRoutine.domain() = com.soundist.core.model.SleepRoutine(id, name, minutes, target.domain(), fadeMinutes, mode.domain(), "%02d:%02d".format(clockHour, clockMinute), updatedAt = System.currentTimeMillis())
private fun com.soundist.core.model.SleepSession.feature() = SleepSession(if (status == DomainSessionStatus.RUNNING) SleepStatus.RUNNING else SleepStatus.IDLE, startedAt, targetEndAt, ((targetEndAt - startedAt) / 60_000L).toInt(), SleepEndMode.DURATION, target = target.feature(), fadeMinutes = fadeMinutes, volumeSnapshotCaptured = volumeSnapshotCaptured)
private fun SleepSession.domain(volumes: Triple<Float, Float, Float> = Triple(1f, 1f, .8f)) = com.soundist.core.model.SleepSession("sleep-active", startedAtEpochMillis ?: System.currentTimeMillis(), endsAtEpochMillis ?: System.currentTimeMillis(), null, target.domain(), fadeMinutes, if (status == SleepStatus.RUNNING) DomainSessionStatus.RUNNING else DomainSessionStatus.CANCELLED, volumeSnapshotCaptured = volumeSnapshotCaptured, masterVolumeSnapshot = volumes.first, ambientVolumeSnapshot = volumes.second, radioVolumeSnapshot = volumes.third)
private fun com.soundist.core.model.SleepSession.featureRecord() = SleepRecord(id, startedAt, ((targetEndAt - startedAt) / 60_000L).toInt(), target.feature(), fadeMinutes, status == DomainSessionStatus.COMPLETED)
private fun SleepRecord.domain() = com.soundist.core.model.SleepSession(id, startedAtEpochMillis, startedAtEpochMillis + plannedMinutes * 60_000L, startedAtEpochMillis + plannedMinutes * 60_000L, target.domain(), fadeMinutes, if (completed) DomainSessionStatus.COMPLETED else DomainSessionStatus.CANCELLED)
private fun com.soundist.core.model.FocusSession.featureRecord() = FocusRecord(id, FocusTarget(targetKind.feature(), targetId ?: "free", label ?: "自由专注"), startedAt, (elapsedSeconds / 60L).toInt(), if (status == DomainSessionStatus.COMPLETED) CompletionStatus.COMPLETED else CompletionStatus.INTERRUPTED, timerMode.feature(), review.orEmpty(), soundSnapshot.map { FocusSoundSnapshot(it.soundId, it.volume) }, radioSnapshotId, ambientMode, audioSource)
private fun FocusRecord.domain() = com.soundist.core.model.FocusSession(id, SessionKind.FOCUS, if (status == CompletionStatus.COMPLETED) DomainSessionStatus.COMPLETED else DomainSessionStatus.CANCELLED, startedAtEpochMillis, null, null, startedAtEpochMillis + minutes * 60_000L, target.name, target.kind.domain(), target.id.takeUnless { it == "free" }, timerMode.domain(), minutes * 60L, note, sounds.map { com.soundist.core.model.MixTrack(it.soundId, it.volume) }, radio, ambientMode = ambientMode, audioSource = audioSource)
private fun FocusSessionState.domain(now: Long = System.currentTimeMillis()): com.soundist.core.model.FocusSession {
    val configured = (if (phase == SessionPhase.FOCUS) focusMinutes else breakMinutes) * 60_000L
    val start = startedAtEpochMillis ?: now - accumulatedBeforeStartMillis
    val targetEnd = if (timerMode == TimerMode.COUNTDOWN && status == SessionStatus.RUNNING) now + (configured - accumulatedBeforeStartMillis).coerceAtLeast(0L) else null
    return com.soundist.core.model.FocusSession("focus-active", SessionKind.FOCUS, status.domain(), start, targetEnd, now.takeIf { status == SessionStatus.PAUSED }, null, target.name, target.kind.domain(), target.id.takeUnless { it == "free" }, timerMode.domain(), accumulatedBeforeStartMillis / 1000L, phase = phase.name, focusMinutes = focusMinutes, breakMinutes = breakMinutes, cycleRound = cycle.round, cycleRounds = cycle.rounds, longBreakMinutes = cycle.longBreakMinutes, autoBreak = cycle.autoBreak, autoFocus = cycle.autoFocus, completionMinutes = completionMinutes)
}
private fun com.soundist.core.model.FocusSession.featureSession() = FocusSessionState(timerMode.feature(), SessionPhase.valueOf(phase), when(status){DomainSessionStatus.RUNNING->SessionStatus.RUNNING;DomainSessionStatus.PAUSED->SessionStatus.PAUSED;DomainSessionStatus.REVIEW->SessionStatus.REVIEW;else->SessionStatus.IDLE}, focusMinutes, breakMinutes, startedAt.takeIf { status == DomainSessionStatus.RUNNING }, elapsedSeconds * 1_000L, FocusTarget(targetKind.feature(), targetId ?: "free", label ?: "自由专注"), com.soundist.feature.productivity.FocusCycle(cycleRound, cycleRounds, longBreakMinutes, autoBreak, autoFocus), completionMinutes)
private fun Priority.domain() = com.soundist.core.model.Priority.valueOf(name); private fun com.soundist.core.model.Priority.feature() = Priority.valueOf(name)
private fun HabitMetric.domain() = com.soundist.core.model.HabitMetric.valueOf(name); private fun com.soundist.core.model.HabitMetric.feature() = HabitMetric.valueOf(name)
private fun SleepTarget.domain() = com.soundist.core.model.SleepTarget.valueOf(name); private fun com.soundist.core.model.SleepTarget.feature() = SleepTarget.valueOf(name)
private fun SleepEndMode.domain() = com.soundist.core.model.SleepEndMode.valueOf(name); private fun com.soundist.core.model.SleepEndMode.feature() = SleepEndMode.valueOf(name)
private fun TimerMode.domain() = FocusTimerMode.valueOf(name); private fun FocusTimerMode.feature() = TimerMode.valueOf(name)
private fun TargetKind.domain() = FocusTargetKind.valueOf(name); private fun FocusTargetKind.feature() = TargetKind.valueOf(name)
private fun SessionStatus.domain() = when (this) { SessionStatus.IDLE -> DomainSessionStatus.IDLE; SessionStatus.RUNNING -> DomainSessionStatus.RUNNING; SessionStatus.PAUSED -> DomainSessionStatus.PAUSED; SessionStatus.REVIEW -> DomainSessionStatus.REVIEW }
