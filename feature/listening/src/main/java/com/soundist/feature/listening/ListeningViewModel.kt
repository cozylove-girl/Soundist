package com.soundist.feature.listening

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

class ListeningViewModel(
    private val repository: ListeningRepository = StatefulListeningRepository(),
    private val audio: ListeningAudioController = StatefulAudioController(),
    private val sceneGenerator: RandomSoundSceneGenerator = RandomSoundSceneGenerator(),
    private val generatedRenderer: GeneratedAudioRenderer? = null,
    private val audioImporter: StationAudioImporter? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(ListeningState().withConstellation())
    val state: StateFlow<ListeningState> = _state.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    /** 首次从仓库加载完成标志（宿主用它等待加载后再恢复电台）。 */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** 电台命令串行化：不在原生加载/启停中途取消协程；排队命令只执行最后一次用户意图。 */
    private val radioCommandMutex = Mutex()
    private var radioCommandJob: Job? = null
    private var radioCommandRevision = 0L
    /** 单音色试听协程：快速连点多个音色时取消旧试听，最后一次点击优先（旧试听约 5.2 秒，不能阻塞新点击）。 */
    private var auditionJob: Job? = null
    private var ambientSyncJob: Job? = null
    private var ambientSyncRevision = 0L
    private var volumeSyncJob: Job? = null
    private var volumeSyncRevision = 0L
    /** 编排器热更新与播放命令共用 radio mutex，但不参与“最后一次播放意图”淘汰。 */
    private var generatorLiveUpdateJob: Job? = null
    /** Dedicated last-intent-wins token for rapid channel/personal ambient mode switches. */
    private var ambientModeRevision = 0L
    private var ambientModeSwitchJob: Job? = null
    private val soundPersistenceJobs = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch {
            val restored = repository.load()
            _state.value = restored.copy(
                personalSounds = restored.personalSounds.ifEmpty { restored.sounds },
            ).withConstellation()
            _loaded.value = true
            // 音量唯一真源是引擎（Media3 applyVolumes = master×radio×duck×mute×sleepFade）。
            // ViewModel 只同步语义值给引擎，不再直接写 generatedRenderer.setVolume。
            syncVolumes()
        }
        // 电台播放状态以播放器/渲染器事件为准（Media3 引擎与生成渲染器事件回流），ViewModel 不得乐观置位 PLAYING。
        viewModelScope.launch {
            merge(audio.radioEvents, generatedRenderer?.radioEvents ?: emptyFlow()).collect { event ->
                _state.update { current -> applyRadioEngineEvent(current, event) }
            }
        }
        // Notification/headset controls bypass Compose actions. Mirror the real engine graph after
        // repository restoration so UI controls and the Deep Sea visual cannot remain in a stale
        // playing state after audio has already paused (or vice versa).
        viewModelScope.launch {
            combine(_loaded, audio.playbackGraph) { loaded, graph -> loaded to graph }.collect { (loaded, graph) ->
                if (loaded) {
                    _state.update { current ->
                        val restoredSounds = graph.ambientTracks?.let { tracks ->
                            current.sounds.map { sound ->
                                tracks[sound.id]?.let { volume -> sound.copy(active = true, volume = volume) }
                                    ?: sound.copy(active = false)
                            }
                        } ?: current.sounds
                        val generatedPresetActive = current.generatedArrangementForCurrentStation()
                            ?.ambientMode == "preset"
                        current.copy(
                            sounds = restoredSounds,
                            personalSounds = if (generatedPresetActive) {
                                current.personalCatalogue()
                            } else {
                                restoredSounds
                            },
                            ambientPlaying = graph.ambientPlaying,
                            globalPlaying = graph.globalPlaying,
                        ).withConstellation()
                    }
                }
            }
        }
        // 环境声轨解码失败回流：失败的声音从混音移除（保留音量），不显示假「播放中」，并给出可理解错误信息。
        viewModelScope.launch {
            audio.ambientErrors.collect(::handleAmbientError)
        }
    }

    /** 环境声轨解码失败：该声音不能继续显示「播放中」——从混音移除（保留音量便于之后恢复），其余声音继续。 */
    private fun handleAmbientError(event: AmbientErrorEvent) {
        val sound = state.value.sounds.firstOrNull { it.id == event.soundId } ?: return
        if (!sound.active) return
        changeSound(event.soundId) { it.copy(active = false) }
        mutate {
            copy(
                ambientPlaying = sounds.any { it.active },
                globalPlaying = sounds.any { it.active } || radioPlayback.isRadioActive,
                operationError = event.message,
                sceneName = sceneGenerator.titleFor(sounds.filter { it.active }.map { it.id }),
            ).withConstellation()
        }
        syncAmbient()
    }

    /** 播放器/渲染器事件回流：页面按钮、文字、波形、可点击状态全部以真实播放结果为准。 */
    private fun applyRadioEngineEvent(current: ListeningState, event: RadioEngineState): ListeningState {
        // 普通电台与生成电台共用一个 UI 状态流。切换后，旧后端的 PAUSED/IDLE/ERROR 可能稍晚到达；
        // 这些事件不得把新频道的 LOADING/PLAYING 状态覆盖掉，也不得把选择跳回旧频道。
        if (event.stationId != null && current.selectedStationId != null && event.stationId != current.selectedStationId) return current
        if (event.stationId == null && event.state == PlaybackState.IDLE && current.radioPlayback == PlaybackState.LOADING) return current
        return current.copy(
        radioPlayback = event.state,
        // 引擎 IDLE 事件不带频道上下文（stationId == null），不得用它覆盖持久化的曲目索引。
        radioTrackIndex = if (event.stationId != null || event.state != PlaybackState.IDLE) event.trackIndex else current.radioTrackIndex,
        operationError = if (event.state == PlaybackState.ERROR) event.errorMessage?.takeIf { it.isNotBlank() } ?: current.operationError else current.operationError,
        notice = if (event.state == PlaybackState.LOADING && !event.errorMessage.isNullOrBlank()) event.errorMessage else current.notice,
        globalPlaying = when (event.state) {
            PlaybackState.PLAYING, PlaybackState.AUDIBLE -> true
            PlaybackState.PAUSED, PlaybackState.ERROR -> current.ambientPlaying
            else -> current.globalPlaying
        },
        selectedStationId = event.stationId ?: current.selectedStationId,
    )
    }

    /** 恢复上次播放状态（宿主在 autoResume 开启且快照标记电台正在播放时调用）。 */
    fun restoreRadioPlayback() {
        val station = currentStation() ?: return
        if (state.value.radioPlayback.isRadioActive || state.value.radioPlayback == PlaybackState.LOADING) return
        playRadio(station)
    }

    fun dispatch(action: ListeningAction) {
        val generatorArrangementBefore = state.value.editorGeneratorArrangement
        when (action) {
            is ListeningAction.Navigate -> mutate { copy(destination = action.destination) }
            ListeningAction.ToggleAmbient -> {
                mutate { copy(ambientPlaying = !ambientPlaying, globalPlaying = !ambientPlaying || radioPlayback.isRadioActive).withConstellation() }
                syncAmbient()
            }
            ListeningAction.ToggleGlobal -> {
                // App.tsx toggleAllPlayback: setIsPlaying(next) + requestRadioPlayback(next) — toggles BOTH ambient and radio.
                val willPlay = !state.value.globalPlaying
                if (willPlay) {
                    val station = currentStation()
                    mutate { copy(globalPlaying = true, ambientPlaying = sounds.any { it.active }).withConstellation() }
                    syncAmbient()
                    if (station != null) playRadio(station)
                } else {
                    // 停止是确定的引擎命令；电台终态 IDLE 由引擎/渲染器事件回流（stopAll/stop），不乐观置位 PAUSED。
                    mutate { copy(globalPlaying = false, ambientPlaying = false).withConstellation() }
                    submitRadioCommand {
                        runCatching { audio.stopAll(); generatedRenderer?.stop() }
                            .onFailure { if (it is CancellationException) throw it; fail(it) }
                    }
                }
            }
            ListeningAction.ToggleRadio -> {
                val selected = currentStation()
                if (selected == null) { mutate { copy(operationError = "请先选择一个电台") }; return }
                when (state.value.radioPlayback) {
                    // PLAYING（音频时钟已启动）与 AUDIBLE（已出声）再点 = 暂停。
                    PlaybackState.PLAYING, PlaybackState.AUDIBLE -> pauseRadio()
                    // 载入中再点 = 明确取消载入（与暂停不同：回到 IDLE，不进入 PAUSED）。
                    PlaybackState.LOADING -> cancelRadioLoad()
                    else -> playRadio(selected)
                }
            }
            ListeningAction.ToggleMixer -> mutate { copy(mixerExpanded = !mixerExpanded) }
            is ListeningAction.SetMasterVolume -> {
                val next = action.volume.coerceIn(0f, 1f)
                mutate { copy(globalVolume = next) }
                syncVolumes()
            }
            is ListeningAction.SetEnvironmentVolume -> {
                val next = action.volume.coerceIn(0f, 1f)
                mutate { copy(environmentVolume = next) }
                syncVolumes()
            }
            is ListeningAction.SetRadioVolume -> {
                val next = action.volume.coerceIn(0f, 1f)
                mutate { copy(radioVolume = next) }
                // 只同步语义值给引擎；最终增益（master×radio×duck×mute×sleepFade）由引擎 applyVolumes 统一算一次。
                syncVolumes()
            }
            is ListeningAction.SetSoundVolume -> {
                changeSound(action.id, persistDelayMillis = 180L) { it.copy(volume = action.volume.coerceIn(0f, 1f)) }
                syncAmbient()
            }
            is ListeningAction.ToggleSound -> {
                // App.tsx toggleSound: active flipped, volume = active ? 0 : 58 (percent → 0.58f).
                changeSound(action.id) { it.copy(active = !it.active, volume = if (it.active) 0f else .58f) }
                mutate { copy(ambientPlaying = sounds.any { it.active }, globalPlaying = sounds.any { it.active } || radioPlayback.isRadioActive).withConstellation() }
                syncAmbient()
            }
            is ListeningAction.RemoveSound -> {
                changeSound(action.id) { it.copy(active = false) }
                mutate { copy(highlightedSoundId = null, ambientPlaying = sounds.any { it.active }).withConstellation() }
                syncAmbient()
            }
            is ListeningAction.ToggleFavorite -> changeSoundMetadata(action.id) { it.copy(favorite = !it.favorite) }
            is ListeningAction.SetFilter -> mutate { copy(filter = action.filter) }
            is ListeningAction.SetQuery -> mutate { copy(query = action.query) }
            ListeningAction.RandomizeScene -> {
                val generated = sceneGenerator.create(state.value.sounds, state.value.sceneSignature)
                mutate { copy(sounds = sounds.map { sound -> generated.sounds[sound.id]?.let { sound.copy(active = true, volume = it) } ?: sound.copy(active = false) }, ambientPlaying = true, globalPlaying = true, sceneName = generated.title, sceneSignature = generated.signature, constellation = emptyList()).withConstellation() }
                commitEffectiveSoundMix(); syncAmbient()
            }
            is ListeningAction.SavePreset -> {
                // App.tsx saveQuickPreset: [preset, ...p] 前插 + setCurrentPreset(name) + showNotice.
                val name = action.name.trim()
                val active = state.value.sounds.filter { it.active }
                if (name.isEmpty()) { mutate { copy(operationError = "请输入预设标题") }; return }
                if (state.value.presets.any { it.name == name }) { mutate { copy(operationError = "这个标题已经存在，请换一个") }; return }
                if (active.isEmpty()) { mutate { copy(operationError = "当前没有可保存的声源") }; return }
                val preset = SoundPreset("custom-${System.nanoTime()}", name, active.associate { it.id to it.volume })
                mutate { copy(presets = listOf(preset) + presets, sceneName = name, notice = "预设「$name」已保存") }
                launchCatching { repository.savePreset(preset) }
            }
        is ListeningAction.SavePresetWithPurpose -> {
                // App.tsx saveQuickPreset (purpose): 前插 + setCurrentPreset(name) + showNotice.
                val name = action.name.trim()
                val active = state.value.sounds.filter { it.active }
                if (name.isEmpty()) { mutate { copy(operationError = "请输入预设标题") }; return }
                if (state.value.presets.any { it.name == name }) { mutate { copy(operationError = "这个标题已经存在，请换一个") }; return }
                if (active.isEmpty()) { mutate { copy(operationError = "当前没有可保存的声源") }; return }
                val preset = SoundPreset("custom-${System.nanoTime()}", name, active.associate { it.id to it.volume }, purposeLabel = action.purpose.trim())
                mutate { copy(presets = listOf(preset) + presets, sceneName = name, notice = "预设「$name」已保存") }
            launchCatching { repository.savePreset(preset) }
        }
        is ListeningAction.ImportPreset -> {
            val knownIds = state.value.sounds.mapTo(mutableSetOf()) { it.id }
            val validTracks = action.preset.tracks
                .filterKeys { it in knownIds }
                .mapValues { (_, volume) -> volume.coerceIn(0f, 1f) }
            if (validTracks.isEmpty()) {
                mutate { copy(operationError = "导入文件里没有当前版本可用的声音") }
                return
            }
            val baseName = action.preset.name.trim().ifBlank { "导入声场" }.take(40)
            val existingNames = state.value.presets.mapTo(mutableSetOf()) { it.name }
            var importedName = baseName
            var suffix = 2
            while (importedName in existingNames) importedName = "$baseName $suffix".also { suffix++ }
            val preset = action.preset.copy(
                id = "imported-${System.nanoTime()}",
                name = importedName,
                tracks = validTracks,
                builtIn = false,
                purposeLabel = action.preset.purposeLabel.trim().ifBlank { inferPresetPurposeLabel(importedName) }.take(12),
            )
            mutate {
                copy(
                    presets = listOf(preset) + presets,
                    sounds = sounds.map { sound -> sound.copy(active = sound.id in preset.tracks, volume = preset.tracks[sound.id] ?: 0f) },
                    ambientPlaying = true,
                    globalPlaying = true,
                    sceneName = preset.name,
                    notice = "已导入并切换到「${preset.name}」",
                ).withConstellation()
            }
            commitEffectiveSoundMix()
            launchCatching { repository.savePreset(preset) }
            syncAmbient()
        }
        is ListeningAction.UpdatePreset -> {
                if(action.preset.builtIn){mutate{copy(operationError="内置声场请先复制后编辑")};return}
                mutate{copy(presets=presets.map{if(it.id==action.preset.id)action.preset else it})};launchCatching{repository.savePreset(action.preset)}
            }
            is ListeningAction.DeletePreset -> mutate {
                // App.tsx deletePreset: 内置 showNotice("内置声场不会删除，可以复制后再编辑")；自定义 showNotice("已删除预设「name」").
                val preset = presets.firstOrNull { it.id == action.id } ?: return@mutate this
                if (preset.builtIn) copy(notice = "内置声场不会删除，可以复制后再编辑")
                else copy(presets = presets.filterNot { it.id == action.id }, notice = "已删除预设「${preset.name}」").also { launchCatching { repository.deletePreset(action.id) } }
            }
            is ListeningAction.DuplicatePreset -> mutate {
                // App.tsx duplicatePreset: [copy, ...list] 前插 + setEditingPresetId(copy.id) + showNotice.
                val source = presets.firstOrNull { it.id == action.id } ?: return@mutate this
                val copy = source.copy(id = "custom-${System.nanoTime()}", name = "${source.name} 副本", builtIn = false)
                launchCatching { repository.savePreset(copy) }
                copy(presets = listOf(copy) + presets, notice = "已创建可编辑副本")
            }
            is ListeningAction.MovePreset -> mutate {
                val reordered = presets.move(action.id, action.delta); launchCatching { repository.reorderPresets(reordered.map { it.id }) }; copy(presets = reordered)
            }
            ListeningAction.TogglePresetManager -> mutate { copy(presetManagerOpen = !presetManagerOpen) }
            is ListeningAction.ApplyPreset -> mutate {
                val preset = presets.firstOrNull { it.id == action.id } ?: return@mutate this
                copy(sounds = sounds.map { it.copy(active = it.id in preset.tracks, volume = preset.tracks[it.id] ?: 0f) }, sceneName = preset.name, notice = "已切换到「${preset.name}」").withConstellation()
            }.also { commitEffectiveSoundMix(); syncAmbient() }
            is ListeningAction.HighlightSound -> {
                mutate { copy(highlightedSoundId = action.id, mixerExpanded = true) }
                viewModelScope.launch { delay(1800); mutate { if (highlightedSoundId == action.id) copy(highlightedSoundId = null) else this } }
            }
            is ListeningAction.SetRadioGroup -> mutate { copy(radioGroup = action.group) }
            is ListeningAction.SetRadioQuery -> mutate { copy(radioQuery = action.query) }
            is ListeningAction.SetRadioGenre -> mutate { copy(radioGenre = action.genre) }
            is ListeningAction.SetRadioPurpose -> mutate { copy(radioPurpose = action.purpose) }
            is ListeningAction.PlayStation -> {
                val station = state.value.stations.firstOrNull { it.id == action.id } ?: return
                val isCurrent = currentStation()?.id == station.id
                when {
                    isCurrent && state.value.radioPlayback.isRadioActive -> pauseRadio()
                    isCurrent && state.value.radioPlayback == PlaybackState.LOADING -> cancelRadioLoad()
                    else -> playRadio(station)
                }
            }
            is ListeningAction.PlayStationTrack -> playStationTrack(action.stationId, action.trackIndex)
            ListeningAction.PreviousRadioTrack -> changeRadioTrack(-1)
            ListeningAction.NextRadioTrack -> changeRadioTrack(1)
            is ListeningAction.OpenStationDetails -> mutate { copy(stationDetailsId = action.id) }
            ListeningAction.ClearError -> mutate { copy(operationError = null, radioPlayback = if (radioPlayback == PlaybackState.ERROR) PlaybackState.IDLE else radioPlayback) }
            ListeningAction.ClearNotice -> mutate { copy(notice = null) }
            is ListeningAction.ShowNotice -> mutate { copy(notice = action.text) }
            // ── Generator editor (App.tsx 4540–4704) ─────────────────────────
            is ListeningAction.OpenGeneratorEditor -> openGeneratorEditor(action.station)
            is ListeningAction.CloseGeneratorEditor -> closeGeneratorEditor(action.discard)
            ListeningAction.RequestCloseGeneratorEditor -> requestCloseGeneratorEditor()
            ListeningAction.UndoGeneratorChange -> {
                val previous = state.value.generatorPast.lastOrNull() ?: return
                mutate { copy(generatorPast = generatorPast.dropLast(1), generatorFuture = (listOfNotNull(editorGeneratorArrangement) + generatorFuture).take(30), editorGeneratorArrangement = previous) }
            }
            ListeningAction.RedoGeneratorChange -> {
                val next = state.value.generatorFuture.firstOrNull() ?: return
                mutate { copy(generatorFuture = generatorFuture.drop(1), generatorPast = generatorPast.takeLast(29) + listOfNotNull(editorGeneratorArrangement), editorGeneratorArrangement = next) }
            }
            is ListeningAction.SetGeneratorDraftName -> mutate { copy(generatorDraftName = action.name) }
            is ListeningAction.UpdateGeneratorSettings -> mutate { copy(editorGeneratorSettings = action.updater(editorGeneratorSettings)) }
            is ListeningAction.CommitGeneratorArrangement -> mutate {
                val cur = editorGeneratorArrangement ?: return@mutate this
                val next = action.updater(cur)
                if (next == cur || next.toString() == cur.toString()) this
                else copy(editorGeneratorArrangement = next, generatorPast = generatorPast.takeLast(29) + cur, generatorFuture = emptyList())
            }
            is ListeningAction.SetGeneratorAmbientMode -> setGeneratorAmbientMode(action.mode)
            ListeningAction.RestoreChannelAmbientDefault -> restoreChannelAmbientDefault()
            // 拖动中限频实时更新：只改编排，不写撤销栈、不碰 generatorFuture。
            is ListeningAction.UpdateGeneratorArrangementLive -> mutate {
                val cur = editorGeneratorArrangement ?: return@mutate this
                copy(editorGeneratorArrangement = action.updater(cur))
            }
            // 松手时一次性提交：以拖动开始前的 base 为撤销基址，写一条撤销（回到拖动前），同时落进编辑器编排状态。
            is ListeningAction.FinalizeGeneratorArrangement -> mutate {
                val cur = editorGeneratorArrangement ?: return@mutate this
                val next = action.updater(cur)
                copy(editorGeneratorArrangement = next, generatorPast = generatorPast.takeLast(29) + action.base, generatorFuture = emptyList())
            }
            is ListeningAction.SelectGeneratorLayer -> mutate { copy(selectedGeneratorLayerId = action.id) }
            is ListeningAction.SelectGeneratorScene -> mutate { copy(selectedGeneratorSceneId = action.id) }
            is ListeningAction.SelectAmbientTrack -> mutate { copy(selectedAmbientTrackId = action.soundId) }
            ListeningAction.ToggleGeneratorAdvanced -> mutate { copy(generatorAdvancedOpen = !generatorAdvancedOpen) }
            ListeningAction.CloseGeneratorDiscardConfirm -> mutate { copy(generatorDiscardConfirmOpen = false) }
            is ListeningAction.AddGeneratorLayer -> {
                val layer = newGeneratorLayer(action.type, "${action.type}-${System.nanoTime()}")
                mutate {
                    val arr = editorGeneratorArrangement ?: return@mutate this
                    val next = arr.copy(layers = arr.layers + layer.copy(relationshipTargetId = if (layer.relationship == "independent") null else arr.layers.firstOrNull()?.id))
                    copy(editorGeneratorArrangement = next, generatorPast = generatorPast.takeLast(29) + arr, generatorFuture = emptyList(), selectedGeneratorLayerId = layer.id)
                }
            }
            is ListeningAction.RemoveGeneratorLayer -> mutate {
                val arr = editorGeneratorArrangement ?: return@mutate this
                if (arr.layers.size <= 1) return@mutate this
                val layers = arr.layers.filter { it.id != action.layerId }.map { layer ->
                    if (layer.relationshipTargetId == action.layerId) layer.copy(relationshipTargetId = arr.layers.firstOrNull { c -> c.id != action.layerId && c.id != layer.id }?.id) else layer
                }
                copy(editorGeneratorArrangement = arr.copy(layers = layers), generatorPast = generatorPast.takeLast(29) + arr, generatorFuture = emptyList(), selectedGeneratorLayerId = layers.firstOrNull()?.id)
            }
            is ListeningAction.DuplicateGeneratorLayer -> mutate {
                val arr = editorGeneratorArrangement ?: return@mutate this
                val index = arr.layers.indexOfFirst { it.id == action.layerId }
                if (index < 0) return@mutate this
                val source = arr.layers[index]
                val copyLayer = source.copy(id = "${source.type}-${System.nanoTime()}", name = "${source.name} 副本")
                val layers = arr.layers.toMutableList().also { it.add(index + 1, copyLayer) }
                copy(editorGeneratorArrangement = arr.copy(layers = layers), generatorPast = generatorPast.takeLast(29) + arr, generatorFuture = emptyList(), selectedGeneratorLayerId = copyLayer.id)
            }
            is ListeningAction.MoveGeneratorLayer -> mutate {
                val arr = editorGeneratorArrangement ?: return@mutate this
                val index = arr.layers.indexOfFirst { it.id == action.layerId }
                val target = index + action.direction
                if (index < 0 || target < 0 || target >= arr.layers.size) return@mutate this
                val layers = arr.layers.toMutableList()
                val tmp = layers[index]; layers[index] = layers[target]; layers[target] = tmp
                copy(editorGeneratorArrangement = arr.copy(layers = layers), generatorPast = generatorPast.takeLast(29) + arr, generatorFuture = emptyList())
            }
            is ListeningAction.AddGeneratorAmbientTrack -> {
                val sound = state.value.sounds.firstOrNull { it.id == action.soundId } ?: return
                mutate {
                    val arr = editorGeneratorArrangement ?: return@mutate this
                    if (arr.ambientTracks.any { it.soundId == action.soundId }) return@mutate this
                    val track = ambientTrackFromSound(sound.copy(active = true))
                    copy(editorGeneratorArrangement = arr.copy(ambientTracks = arr.ambientTracks + track, ambientMode = "preset"), generatorPast = generatorPast.takeLast(29) + arr, generatorFuture = emptyList(), selectedAmbientTrackId = action.soundId)
                }
            }
            is ListeningAction.RemoveGeneratorAmbientTrack -> mutate {
                val arr = editorGeneratorArrangement ?: return@mutate this
                val tracks = arr.ambientTracks.filter { it.soundId != action.soundId }
                copy(editorGeneratorArrangement = arr.copy(ambientTracks = tracks), generatorPast = generatorPast.takeLast(29) + arr, generatorFuture = emptyList(), selectedAmbientTrackId = tracks.firstOrNull()?.soundId)
            }
            ListeningAction.LoadCurrentAmbient -> mutate {
                val arr = editorGeneratorArrangement ?: return@mutate this
                val tracks = personalCatalogue().filter { it.active }.map(::ambientTrackFromSound)
                copy(editorGeneratorArrangement = arr.copy(ambientTracks = tracks, ambientMode = "preset"), generatorPast = generatorPast.takeLast(29) + arr, generatorFuture = emptyList(), selectedAmbientTrackId = tracks.firstOrNull()?.soundId)
            }
            is ListeningAction.AuditionGeneratorTimbre -> {
                val renderer = generatedRenderer
                if (renderer == null) {
                    mutate { copy(operationError = "音色试听引擎尚未就绪") }
                    return
                }
                // 快速连点多个音色：立即打断旧的试听（取消旧协程 → 渲染器 previewTimbre 的 finally 停止/释放旧静态
                // AudioTrack），最后一次点击优先。旧实现 `if (previewingTimbre != null) return` 会忽略新点击，必须等
                // 约 5.2 秒旧试听自然结束才能再试听，快速连点只有第一个音色发声。
                auditionJob?.cancel()
                mutate { copy(previewingTimbre = action.timbre) }
                // App.tsx previewGeneratedTimbre：真实试听（NativeGeneratedAudioRenderer 渲染约 5 秒），完成后清除指示态。
                auditionJob = viewModelScope.launch {
                    try {
                        renderer.previewTimbre(action.timbre)
                    } catch (error: CancellationException) {
                        throw error // 被更新的点击取消：立即中止，不清除新试听的指示态。
                    } catch (error: Throwable) {
                        mutate { copy(operationError = error.message ?: "无法试听该音色") }
                    }
                    mutate { if (previewingTimbre == action.timbre) copy(previewingTimbre = null) else this }
                }
            }
            ListeningAction.PreviewGeneratorDraft -> {
                val snapshot = state.value
                val sourceId = snapshot.generatorSourceStationId
                val arrangement = snapshot.editorGeneratorArrangement
                if (sourceId == null || arrangement == null) {
                    mutate { copy(operationError = "当前没有可试听的编排") }
                    return
                }
                if (arrangement.layers.none { it.enabled && it.type !in setOf("ambient", "modulation") }) {
                    mutate { copy(operationError = "当前编排没有已启用的音色层") }
                    return
                }
                val station = snapshot.stations.firstOrNull { it.id == sourceId }
                if (station == null) {
                    mutate { copy(operationError = "原始持续声场已不存在") }
                    return
                }
                // 试听必须渲染「当前草稿」而不是源频道已保存的编排。
                // playRadio 会在编排器打开时把草稿 settings/arrangement 合并进播放对象（含暂停后恢复路径）。
                mutate { copy(selectedStationId = sourceId, radioTrackIndex = 0) }
                playRadio(station)
            }
            is ListeningAction.ImportGeneratorConfiguration -> {
                val current = state.value.editorGeneratorArrangement ?: return
                val imported = action.configuration
                val knownSounds = state.value.sounds.associateBy { it.id }
                val normalized = normalizeGeneratedArrangement(imported.arrangement, "custom-lab").let { arrangement ->
                    arrangement.copy(
                        ambientTracks = arrangement.ambientTracks.mapNotNull { track ->
                            knownSounds[track.soundId]?.let { sound ->
                                track.copy(name = sound.name, category = sound.category.label())
                            }
                        },
                    )
                }
                mutate {
                    copy(
                        generatorDraftName = imported.name,
                        editorGeneratorSettings = imported.settings,
                        editorGeneratorArrangement = normalized,
                        generatorPast = generatorPast.takeLast(29) + current,
                        generatorFuture = emptyList(),
                        selectedGeneratorLayerId = normalized.layers.firstOrNull()?.id,
                        selectedAmbientTrackId = normalized.ambientTracks.firstOrNull()?.soundId,
                        selectedGeneratorSceneId = normalized.scenes.firstOrNull()?.id,
                        notice = "已导入编排配置",
                    )
                }
            }
            ListeningAction.SaveGeneratedCopy -> saveGeneratedCopy()
            ListeningAction.RestoreGeneratorTemplate -> {
                val preset = state.value.stations.firstOrNull { it.id == state.value.generatorSourceStationId }?.generatorPresetId ?: "custom-lab"
                val arrangement = normalizeGeneratedArrangement(createDefaultGeneratedArrangement(preset), preset)
                mutate {
                    copy(editorGeneratorSettings = DEFAULT_GENERATED_RADIO_SETTINGS, editorGeneratorArrangement = arrangement, generatorPast = generatorPast.takeLast(29) + listOfNotNull(editorGeneratorArrangement), generatorFuture = emptyList(), selectedGeneratorLayerId = arrangement.layers.firstOrNull()?.id, selectedAmbientTrackId = null, selectedGeneratorSceneId = arrangement.scenes.firstOrNull()?.id)
                }
            }
            // ── Custom form (App.tsx 6010–6100) ──────────────────────────────
            is ListeningAction.OpenCustomRadio -> mutate { copy(editingRadioId = null, radioDraft = RadioDraft(sourceKind = action.sourceKind), radioDraftAudio = emptyList(), radioStorageLabel = "", radioFormError = "", customRadioOpen = true, manageChannelsOpen = false) }
            ListeningAction.CloseCustomRadioEditor -> mutate { copy(customRadioOpen = false, radioFormError = "") }
            is ListeningAction.EditRadio -> editRadio(action.station)
            is ListeningAction.UpdateRadioDraft -> mutate { copy(radioDraft = action.updater(radioDraft), radioFormError = "") }
            is ListeningAction.UpdateRadioUrl -> mutate { copy(radioDraft = radioDraft.copy(url = action.url), radioFormError = if (Regex("youtube\\.com|youtu\\.be|bilibili\\.com|b23\\.tv", RegexOption.IGNORE_CASE).containsMatchIn(action.url)) "请填写直接音频文件或 Icecast 地址，不支持隐藏视频网站播放器" else "") }
            is ListeningAction.AddRadioDraftAudio -> mutate {
                // App.tsx 5062-5073/5096-5102：storage.estimate 的「本地剩余约 X · 本次待保存 Y」。
                val incomingBytes = action.items.sumOf { it.sizeBytes }
                val remaining = availableBytes()
                if (remaining > 0 && (radioDraftAudio.sumOf { it.sizeBytes } + incomingBytes) > remaining * 0.92) {
                    copy(radioStorageLabel = "本地剩余约 ${formatStorageSize(remaining)}", radioFormError = "这些文件超过当前设备可安全使用的本地空间，请减少文件或清理存储")
                } else {
                    copy(radioDraftAudio = radioDraftAudio + action.items, radioStorageLabel = "本地剩余约 ${formatStorageSize(remaining)} · 本次待保存 ${formatStorageSize(incomingBytes)}")
                }
            }
            is ListeningAction.MoveRadioDraftAudio -> mutate {
                val idx = radioDraftAudio.indexOfFirst { it.id == action.id }
                val target = idx + action.direction
                if (idx < 0 || target < 0 || target >= radioDraftAudio.size) return@mutate this
                val list = radioDraftAudio.toMutableList()
                val tmp = list[idx]; list[idx] = list[target]; list[target] = tmp
                copy(radioDraftAudio = list)
            }
            is ListeningAction.RemoveRadioDraftAudio -> mutate { copy(radioDraftAudio = radioDraftAudio.filterNot { it.id == action.id }) }
            ListeningAction.SaveCustomRadio -> saveCustomRadio()
            // ── Manage channels (App.tsx 6168–6214) ───────────────────────────
            ListeningAction.OpenManageChannels -> mutate { copy(manageChannelsOpen = true) }
            ListeningAction.CloseManageChannels -> mutate { copy(manageChannelsOpen = false, channelMenuId = null) }
            is ListeningAction.SetChannelMenuId -> mutate { copy(channelMenuId = action.id) }
            is ListeningAction.SetDraggingRadio -> mutate { copy(draggingRadioId = action.id) }
            is ListeningAction.MoveCustomRadio -> {
                val customStations = state.value.stations.filter { it.custom }.toMutableList()
                val idx = customStations.indexOfFirst { it.id == action.id }
                val target = idx + action.direction
                if (idx >= 0 && target >= 0 && target < customStations.size) {
                    val tmp = customStations[idx]; customStations[idx] = customStations[target]; customStations[target] = tmp
                    var i = 0
                    val reordered = state.value.stations.map { if (it.custom) customStations[i++] else it }
                    mutate { copy(stations = reordered) }
                    launchCatching { repository.reorderStations(reordered.map { it.id }) }
                }
            }
            is ListeningAction.DeleteRadio -> mutate { copy(deleteStationConfirmId = action.id) }
            ListeningAction.CancelDeleteRadio -> mutate { copy(deleteStationConfirmId = null) }
            is ListeningAction.ConfirmDeleteRadio -> confirmDeleteStation(action.deleteCopies)
        }
        val generatorArrangementAfter = state.value.editorGeneratorArrangement
        if (action !is ListeningAction.SetGeneratorAmbientMode &&
            action != ListeningAction.RestoreChannelAmbientDefault &&
            generatorArrangementBefore != null && generatorArrangementAfter != null &&
            (generatorArrangementBefore.ambientMode != generatorArrangementAfter.ambientMode ||
                generatorArrangementBefore.ambientTracks != generatorArrangementAfter.ambientTracks)
        ) {
            syncChannelAmbientSessionFromEditor(adjusted = true)
        }
        if (action is ListeningAction.SetGeneratorAmbientMode || action == ListeningAction.RestoreChannelAmbientDefault) {
            scheduleAmbientModeSwitch()
        } else if (action.requiresGeneratorLiveUpdate()) scheduleGeneratorLiveUpdate()
    }

    // ── Generator editor helpers ────────────────────────────────────────────
    private fun generatorDraftKey(settings: GeneratorSettings, arrangement: GeneratedArrangement?, name: String): String = "$settings|$arrangement|$name"

    private fun ListeningState.personalCatalogue(): List<AmbientSound> = personalSounds.ifEmpty { sounds }

    private fun ListeningState.arrangementFor(station: RadioStation): GeneratedArrangement {
        val presetId = station.generatorPresetId ?: "custom-lab"
        val base = normalizeGeneratedArrangement(station.generatorArrangement, presetId)
        if (generatorControlsOpen && generatorSourceStationId == station.id) {
            editorGeneratorArrangement?.let { return it }
        }
        val session = channelAmbientSessions[station.id] ?: return base
        return base.copy(
            ambientTracks = applyChannelAmbientPatch(base.ambientTracks, session.patch),
            ambientMode = session.ambientMode,
        )
    }

    private fun ListeningState.generatedArrangementForCurrentStation(): GeneratedArrangement? {
        val station = stations.firstOrNull { it.id == selectedStationId }
            ?.takeIf { it.sourceKind == RadioSourceKind.GENERATED }
            ?: return null
        return arrangementFor(station)
    }

    private fun ListeningState.materializeAmbient(arrangement: GeneratedArrangement): List<AmbientSound> {
        val personal = personalCatalogue()
        if (arrangement.ambientMode == "current") return personal
        val byId = arrangement.ambientTracks.associateBy { it.soundId }
        return personal.map { sound ->
            val track = byId[sound.id]
            if (track == null) sound.copy(active = false)
            else sound.copy(
                active = track.enabled,
                volume = (track.volume / 100f).coerceIn(0f, 1f),
            )
        }
    }

    private fun applyChannelAmbientPatch(
        defaults: List<GeneratedAmbientTrack>,
        patch: ChannelAmbientPatch,
    ): List<GeneratedAmbientTrack> {
        val overrides = patch.overrides.associateBy { it.soundId }
        val inherited = defaults
            .filterNot { it.soundId in patch.removedSoundIds }
            .map { overrides[it.soundId] ?: it }
        val defaultIds = defaults.mapTo(hashSetOf()) { it.soundId }
        return inherited + patch.overrides.filterNot { it.soundId in defaultIds }
    }

    private fun channelAmbientPatch(
        defaults: List<GeneratedAmbientTrack>,
        effective: List<GeneratedAmbientTrack>,
    ): ChannelAmbientPatch {
        val defaultsById = defaults.associateBy { it.soundId }
        val effectiveById = effective.associateBy { it.soundId }
        return ChannelAmbientPatch(
            overrides = effective.filter { track -> defaultsById[track.soundId] != track },
            removedSoundIds = defaultsById.keys - effectiveById.keys,
        )
    }

    private fun syncChannelAmbientSessionFromEditor(adjusted: Boolean? = null) {
        val snapshot = state.value
        val stationId = snapshot.generatorSourceStationId ?: return
        val arrangement = snapshot.editorGeneratorArrangement ?: return
        val station = snapshot.stations.firstOrNull { it.id == stationId } ?: return
        val base = normalizeGeneratedArrangement(station.generatorArrangement, station.generatorPresetId ?: "custom-lab")
        val patch = channelAmbientPatch(base.ambientTracks, arrangement.ambientTracks)
        val old = snapshot.channelAmbientSessions[stationId]
        val session = ChannelAmbientSession(
            patch = patch,
            ambientMode = arrangement.ambientMode,
            adjusted = adjusted ?: old?.adjusted ?: (patch.overrides.isNotEmpty() || patch.removedSoundIds.isNotEmpty()),
        )
        mutate {
            val effective = if (selectedStationId == stationId) materializeAmbient(arrangement) else sounds
            val shouldPlayAmbient = when {
                selectedStationId != stationId -> ambientPlaying
                radioPlayback.isRadioActive || radioPlayback == PlaybackState.LOADING -> effective.any { it.active }
                else -> ambientPlaying && effective.any { it.active }
            }
            copy(
                channelAmbientSessions = channelAmbientSessions + (stationId to session),
                sounds = effective,
                ambientPlaying = shouldPlayAmbient,
                globalPlaying = shouldPlayAmbient || radioPlayback.isRadioActive,
            ).withConstellation()
        }
    }

    private fun setGeneratorAmbientMode(mode: String) {
        if (mode != "preset" && mode != "current") return
        if (state.value.editorGeneratorArrangement?.ambientMode == mode) return
        ambientModeRevision += 1
        mutate {
            val arrangement = editorGeneratorArrangement ?: return@mutate this
            copy(
                editorGeneratorArrangement = arrangement.copy(ambientMode = mode),
                notice = if (mode == "preset") "已切换至频道声场" else "已恢复我的环境声",
            )
        }
        syncChannelAmbientSessionFromEditor()
    }

    private fun restoreChannelAmbientDefault() {
        val snapshot = state.value
        val station = snapshot.stations.firstOrNull { it.id == snapshot.generatorSourceStationId } ?: return
        val base = normalizeGeneratedArrangement(station.generatorArrangement, station.generatorPresetId ?: "custom-lab")
        ambientModeRevision += 1
        mutate {
            copy(
                editorGeneratorArrangement = base.copy(ambientMode = "preset"),
                generatorPast = generatorPast.takeLast(29) + listOfNotNull(editorGeneratorArrangement),
                generatorFuture = emptyList(),
                selectedAmbientTrackId = base.ambientTracks.firstOrNull()?.soundId,
            )
        }
        syncChannelAmbientSessionFromEditor(adjusted = false)
    }

    private fun openGeneratorEditor(station: RadioStation) {
        if (station.generatorPresetId == null) return
        val s = state.value
        val snapshot = GeneratorSnapshot(
            radioId = s.selectedStationId,
            radioPlaying = s.radioPlayback != PlaybackState.IDLE,
            settings = s.editorGeneratorSettings,
            arrangement = s.editorGeneratorArrangement,
            sounds = s.sounds,
            environmentPlaying = s.ambientPlaying,
            channelAmbientSessions = s.channelAmbientSessions,
        )
        val baseArrangement = s.arrangementFor(station)
        val arrangement = if (station.custom || baseArrangement.ambientTracks.isNotEmpty()) baseArrangement
        else baseArrangement.copy(ambientTracks = s.sounds.filter { it.active }.map(::ambientTrackFromSound))
        val settings = station.generatorSettings ?: DEFAULT_GENERATED_RADIO_SETTINGS
        val draftName = if (station.custom) station.name else "${station.name} · 我的版本"
        mutate {
            copy(
                generatorSnapshot = snapshot,
                generatorSourceStationId = station.id,
                generatorBaseline = generatorDraftKey(settings, arrangement, draftName),
                editorGeneratorSettings = settings,
                editorGeneratorArrangement = arrangement,
                selectedGeneratorLayerId = arrangement.layers.firstOrNull()?.id,
                selectedAmbientTrackId = arrangement.ambientTracks.firstOrNull()?.soundId,
                selectedGeneratorSceneId = arrangement.scenes.firstOrNull()?.id,
                generatorPast = emptyList(),
                generatorFuture = emptyList(),
                generatorDraftName = draftName,
                editingGeneratorRadioId = if (station.custom) station.id else null,
                generatorAdvancedOpen = false,
                generatorDiscardConfirmOpen = false,
                generatorControlsOpen = true,
                manageChannelsOpen = false,
            )
        }
    }

    private fun closeGeneratorEditor(discard: Boolean) {
        val snapshot = state.value.generatorSnapshot
        mutate {
            var s = copy(generatorSnapshot = null, generatorBaseline = null, generatorSourceStationId = null, editingGeneratorRadioId = null, generatorDiscardConfirmOpen = false, generatorControlsOpen = false, generatorPast = emptyList(), generatorFuture = emptyList())
            if (discard && snapshot != null) {
                s = s.copy(
                    selectedStationId = snapshot.radioId,
                    // 电台播放状态以引擎/渲染器事件回流为准：打开编排器时电台并未被暂停，放弃时不得乐观置位 LOADING。
                    editorGeneratorSettings = snapshot.settings,
                    editorGeneratorArrangement = snapshot.arrangement,
                    sounds = snapshot.sounds,
                    channelAmbientSessions = snapshot.channelAmbientSessions,
                    ambientPlaying = snapshot.environmentPlaying,
                    globalPlaying = snapshot.radioPlaying || snapshot.environmentPlaying,
                )
            }
            s
        }
        syncAmbient()
    }

    private fun requestCloseGeneratorEditor() {
        val current = generatorDraftKey(state.value.editorGeneratorSettings, state.value.editorGeneratorArrangement, state.value.generatorDraftName)
        if (state.value.generatorBaseline != null && current != state.value.generatorBaseline) mutate { copy(generatorDiscardConfirmOpen = true) }
        else closeGeneratorEditor(true)
    }

    private fun saveGeneratedCopy() {
        val name = state.value.generatorDraftName.trim()
        if (name.isEmpty()) { mutate { copy(notice = "请先为生成频道命名") }; return }
        val source = state.value.stations.firstOrNull { it.id == state.value.generatorSourceStationId && it.generatorPresetId != null } ?: currentStation()
        val stationId = state.value.editingGeneratorRadioId ?: "generated-custom-${System.nanoTime()}"
        val arr = state.value.editorGeneratorArrangement
        val station = RadioStation(
            id = stationId,
            name = name,
            description = if ((arr?.ambientTracks?.size ?: 0) > 0) "保存了环境声轨与生成编排，可随时恢复并继续调整。" else "由你编排、可随当前环境声持续变化的私人生成频道。",
            group = RadioGroup.CUSTOM,
            source = "本机生成",
            url = "generated://$stationId",
            genre = "声场 DIY",
            sourceKind = RadioSourceKind.GENERATED,
            purposes = listOf("自定义"),
            generatorPresetId = source?.generatorPresetId ?: "custom-lab",
            generatorSettings = state.value.editorGeneratorSettings,
            generatorArrangement = arr?.copy(layers = arr.layers.map { it.copy() }, ambientTracks = arr.ambientTracks.map { it.copy() }, scenes = arr.scenes.map { it.copy() }),
            durationLabel = "无限",
            catalogGroup = "私人",
            custom = true,
        )
        val wasEditing = state.value.editingGeneratorRadioId != null
        mutate {
            copy(
                stations = if (editingGeneratorRadioId != null) stations.map { if (it.id == editingGeneratorRadioId) station else it } else listOf(station) + stations,
                selectedStationId = station.id,
                radioGroup = RadioGroup.CUSTOM,
                radioGenre = "全部",
                notice = "生成频道「$name」已${if (wasEditing) "更新" else "保存"}",
            )
        }
        launchCatching { repository.saveStation(station) }
        closeGeneratorEditor(false)
    }

    private fun editRadio(station: RadioStation) {
        when {
            station.sourceKind == RadioSourceKind.GENERATED -> openGeneratorEditor(station)
            !station.custom -> mutate { copy(stationDetailsId = station.id, manageChannelsOpen = false) }
            else -> mutate {
                copy(
                    editingRadioId = station.id,
                    radioDraft = RadioDraft(
                        name = station.name,
                        desc = station.description,
                        genre = station.genre,
                        url = station.url,
                        sourceKind = if (station.sourceKind == RadioSourceKind.LOCAL) RadioSourceKind.LOCAL else RadioSourceKind.STREAM,
                        imageUrl = station.artworkUri,
                    ),
                    radioDraftAudio = station.localAudio.map { item -> RadioDraftAudioItem(id = item.uri, fileName = item.displayName, mimeType = item.mimeType, sizeBytes = item.sizeBytes, validation = "ready") },
                    customRadioOpen = true,
                    manageChannelsOpen = false,
                    radioFormError = "",
                )
            }
        }
    }

    private fun saveCustomRadio() {
        val s = state.value
        val radioDraft = s.radioDraft
        val radioDraftAudio = s.radioDraftAudio
        val editingRadioId = s.editingRadioId
        val name = radioDraft.name.trim()
        val url = radioDraft.url.trim()
        if (name.isEmpty()) { mutate { copy(radioFormError = "请输入频道标题") }; return }
        if (radioDraft.sourceKind == RadioSourceKind.LOCAL && radioDraftAudio.isEmpty()) { mutate { copy(radioFormError = "请至少选择一个本地音频文件") }; return }
        if (radioDraft.sourceKind == RadioSourceKind.LOCAL && radioDraftAudio.any { it.validation == "checking" }) { mutate { copy(radioFormError = "音频仍在检测，请稍候再保存") }; return }
        if (radioDraft.sourceKind == RadioSourceKind.LOCAL && radioDraftAudio.any { it.validation == "invalid" }) { mutate { copy(radioFormError = "播放列表包含当前设备无法解码的文件，请移除后再保存") }; return }
        if (radioDraft.sourceKind == RadioSourceKind.STREAM) {
            if (url.isEmpty()) { mutate { copy(radioFormError = "请输入直接音频流地址") }; return }
            val parsed = runCatching { java.net.URI(url) }.getOrNull()
            val schemeOk = parsed?.scheme?.lowercase() in setOf("http", "https")
            val host = parsed?.host.orEmpty()
            if (!schemeOk) { mutate { copy(radioFormError = "请输入有效的 http(s) 音频地址") }; return }
            if (Regex("youtube\\.com|youtu\\.be|bilibili\\.com|b23\\.tv", RegexOption.IGNORE_CASE).containsMatchIn(host)) { mutate { copy(radioFormError = "视频网站页面不是直接音频流，请使用音频文件或 Icecast 地址") }; return }
        }
        val stationId = editingRadioId ?: "custom-radio-${System.nanoTime()}"
        val localFiles = if (radioDraft.sourceKind == RadioSourceKind.LOCAL) radioDraftAudio.map { LocalAudioSelection(uri = it.id, displayName = it.fileName, sizeBytes = it.sizeBytes, mimeType = it.mimeType, durationSeconds = it.durationSeconds) } else emptyList()
        val wasEditing = editingRadioId != null
        val buildStation: (List<LocalAudioSelection>) -> RadioStation = { files ->
            RadioStation(
                id = stationId,
                name = name,
                description = radioDraft.desc.trim().ifEmpty { if (radioDraft.sourceKind == RadioSourceKind.LOCAL) "保存在本机的私人播放列表" else "你添加的直接音频流" },
                group = RadioGroup.CUSTOM,
                source = if (radioDraft.sourceKind == RadioSourceKind.LOCAL) "本地音频" else "直接音频流",
                url = if (radioDraft.sourceKind == RadioSourceKind.STREAM) url else "",
                artworkUri = radioDraft.imageUrl,
                genre = radioDraft.genre.trim().ifEmpty { "自定义" },
                sourceKind = radioDraft.sourceKind,
                durationLabel = if (files.size > 1) "${files.size} 首" else "",
                localAudio = files,
                transitionMode = if (files.size > 1) "crossfade" else "",
                transitionSeconds = if (files.size > 1) 2.0 else 0.0,
                catalogGroup = "私人",
                custom = true,
            )
        }
        val importer = audioImporter
        if (radioDraft.sourceKind == RadioSourceKind.LOCAL && importer != null) {
            // 导入文件：先复制到私有目录、校验后写库，再以私有 file:// 路径落盘（不长期依赖 content://）。
            mutate { copy(radioSaving = true) }
            viewModelScope.launch {
                runCatching {
                    val imported = importer.import(stationId, localFiles)
                    if (imported.isEmpty()) error("本地音频导入失败，请重新选择文件")
                    buildStation(imported)
                }.onSuccess { commitCustomRadio(it, wasEditing) }
                    .onFailure { fail(it); mutate { copy(radioSaving = false) } }
            }
        } else {
            commitCustomRadio(buildStation(localFiles), wasEditing)
        }
    }

    /** 保存自定义频道并更新 UI 状态（import 完成后或非本地频道）。 */
    private fun commitCustomRadio(station: RadioStation, wasEditing: Boolean) {
        mutate {
            copy(
                stations = if (wasEditing) stations.map { if (it.id == station.id) station else it } else listOf(station) + stations,
                selectedStationId = station.id,
                radioTrackIndex = 0,
                radioPlayback = PlaybackState.LOADING,
                radioGroup = RadioGroup.CUSTOM,
                customRadioOpen = false,
                editingRadioId = null,
                radioDraft = RadioDraft(),
                radioDraftAudio = emptyList(),
                radioSaving = false,
                notice = if (wasEditing) "频道信息与播放列表已更新" else "私人音频频道已添加",
            )
        }
        launchCatching { repository.saveStation(station) }
    }

    /**
     * Commits a whole mix to the owner selected by the current mode.  Channel
     * mode records a session recipe; personal mode updates the durable Sounds
     * page mix.  This keeps random scenes/imported presets from leaking across
     * the same boundary as individual card edits.
     */
    private fun commitEffectiveSoundMix() {
        val snapshot = state.value
        val station = snapshot.stations.firstOrNull { it.id == snapshot.selectedStationId }
        val arrangement = station
            ?.takeIf { it.sourceKind == RadioSourceKind.GENERATED }
            ?.let { snapshot.arrangementFor(it) }
        if (station != null && arrangement?.ambientMode == "preset") {
            val tracks = snapshot.sounds.filter { it.active }.map(::ambientTrackFromSound)
            val nextArrangement = arrangement.copy(ambientTracks = tracks)
            val base = normalizeGeneratedArrangement(station.generatorArrangement, station.generatorPresetId ?: "custom-lab")
            val patch = channelAmbientPatch(base.ambientTracks, tracks)
            mutate {
                copy(
                    channelAmbientSessions = channelAmbientSessions + (
                        station.id to ChannelAmbientSession(patch, "preset", adjusted = true)
                    ),
                    editorGeneratorArrangement = if (generatorSourceStationId == station.id) nextArrangement else editorGeneratorArrangement,
                )
            }
        } else {
            mutate { copy(personalSounds = sounds) }
            snapshot.sounds.forEach(::persistSound)
        }
    }

    private fun changeSound(id: String, persistDelayMillis: Long = 0L, transform: (AmbientSound) -> AmbientSound) {
        val snapshot = state.value
        val station = snapshot.stations.firstOrNull { it.id == snapshot.selectedStationId }
        val arrangement = station
            ?.takeIf { it.sourceKind == RadioSourceKind.GENERATED }
            ?.let { snapshot.arrangementFor(it) }
        if (station != null && arrangement?.ambientMode == "preset") {
            var changed: AmbientSound? = null
            mutate {
                val nextSounds = sounds.map { sound ->
                    if (sound.id == id) transform(sound).also { changed = it } else sound
                }
                val value = changed ?: return@mutate this
                val nextTrack = ambientTrackFromSound(value).copy(
                    enabled = value.active,
                    volume = (value.volume * 100f).roundToInt().coerceIn(0, 100),
                )
                val existing = arrangement.ambientTracks.any { it.soundId == id }
                val nextTracks = if (existing) {
                    arrangement.ambientTracks.map { track ->
                        if (track.soundId == id) track.copy(enabled = nextTrack.enabled, volume = nextTrack.volume) else track
                    }
                } else {
                    arrangement.ambientTracks + nextTrack
                }
                val nextArrangement = arrangement.copy(ambientTracks = nextTracks)
                val base = normalizeGeneratedArrangement(station.generatorArrangement, station.generatorPresetId ?: "custom-lab")
                val patch = channelAmbientPatch(base.ambientTracks, nextTracks)
                copy(
                    sounds = nextSounds,
                    channelAmbientSessions = channelAmbientSessions + (
                        station.id to ChannelAmbientSession(patch, "preset", adjusted = true)
                    ),
                    editorGeneratorArrangement = if (generatorSourceStationId == station.id) nextArrangement else editorGeneratorArrangement,
                ).withConstellation()
            }
            return
        }

        var changed: AmbientSound? = null
        mutate {
            val personal = personalCatalogue()
            val nextPersonal = personal.map { if (it.id == id) transform(it).also { value -> changed = value } else it }
            copy(personalSounds = nextPersonal, sounds = nextPersonal).withConstellation()
        }
        changed?.let { sound ->
            soundPersistenceJobs.remove(id)?.cancel()
            soundPersistenceJobs[id] = viewModelScope.launch {
                if (persistDelayMillis > 0L) delay(persistDelayMillis)
                runCatching { repository.saveSound(sound) }.onFailure { if (it !is CancellationException) fail(it) }
                soundPersistenceJobs.remove(id)
            }
        }
    }

    private fun changeSoundMetadata(id: String, transform: (AmbientSound) -> AmbientSound) {
        var persisted: AmbientSound? = null
        mutate {
            val nextPersonal = personalCatalogue().map { sound ->
                if (sound.id == id) transform(sound).also { persisted = it } else sound
            }
            val favorite = nextPersonal.firstOrNull { it.id == id }?.favorite
            copy(
                personalSounds = nextPersonal,
                sounds = sounds.map { sound -> if (sound.id == id && favorite != null) sound.copy(favorite = favorite) else sound },
            )
        }
        persisted?.let(::persistSound)
    }
    private fun persistSound(sound: AmbientSound) = launchCatching { repository.saveSound(sound) }
    private fun syncAmbient() {
        ambientSyncRevision += 1
        if (ambientSyncJob?.isActive == true) return
        ambientSyncJob = viewModelScope.launch {
            try {
                do {
                    val revision = ambientSyncRevision
                    val snapshot = state.value
                    runCatching {
                        val arrangement = snapshot.generatedArrangementForCurrentStation()
                        if (arrangement != null && snapshot.radioPlayback != PlaybackState.IDLE && snapshot.radioPlayback != PlaybackState.ERROR) {
                            audio.setGeneratedAmbient(
                                arrangement = arrangement,
                                catalogue = snapshot.sounds,
                                playing = snapshot.ambientPlaying,
                                masterVolume = snapshot.globalVolume,
                            )
                        } else {
                            audio.setAmbient(snapshot.sounds, snapshot.ambientPlaying, snapshot.globalVolume)
                        }
                    }.onFailure {
                        if (it is CancellationException) throw it
                        fail(it)
                    }
                } while (revision != ambientSyncRevision)
            } finally {
                ambientSyncJob = null
            }
        }
    }
    private fun syncVolumes() {
        volumeSyncRevision += 1
        if (volumeSyncJob?.isActive == true) return
        volumeSyncJob = viewModelScope.launch {
            try {
                do {
                    val revision = volumeSyncRevision
                    val snapshot = state.value
                    runCatching {
                        audio.setMasterVolume(snapshot.globalVolume)
                        audio.setAmbientVolume(snapshot.environmentVolume)
                        audio.setRadioVolume(snapshot.radioVolume)
                    }.onFailure { if (it !is CancellationException) fail(it) }
                } while (revision != volumeSyncRevision)
            } finally {
                volumeSyncJob = null
            }
        }
    }

    /**
     * 正在试听当前生成频道时，把编辑器草稿实时推送到生成器和环境声自动化。
     * 16ms 合并同一帧内的连续控件事件；真正的防爆音 ramp 由 native 生成器和 AmbientMixer 完成。
     */
    private fun scheduleGeneratorLiveUpdate() {
        val current = state.value
        if (!current.generatorControlsOpen || !current.radioPlayback.isRadioActive) return
        if (current.selectedStationId != current.generatorSourceStationId) return
        generatorLiveUpdateJob?.cancel()
        generatorLiveUpdateJob = viewModelScope.launch {
            delay(16)
            pushGeneratorLiveUpdate()
        }
    }

    /**
     * Mode changes are never cancelled in the middle of a backend reconcile.
     * A single worker serializes them and only applies the newest revision, so
     * rapid taps cannot make an older mode arrive after the final selection.
     */
    private fun scheduleAmbientModeSwitch() {
        if (ambientModeSwitchJob?.isActive == true) return
        ambientModeSwitchJob = viewModelScope.launch {
            try {
                while (true) {
                    val revision = ambientModeRevision
                    delay(16)
                    if (revision != ambientModeRevision) continue
                    pushGeneratorLiveUpdate(revision)
                    if (revision == ambientModeRevision) break
                }
            } finally {
                ambientModeSwitchJob = null
            }
        }
    }

    private suspend fun pushGeneratorLiveUpdate(expectedAmbientModeRevision: Long? = null) {
        radioCommandMutex.withLock {
            if (expectedAmbientModeRevision != null && expectedAmbientModeRevision != ambientModeRevision) return@withLock
            val snapshot = state.value
            val source = snapshot.stations.firstOrNull { it.id == snapshot.generatorSourceStationId }
                ?: return@withLock
            if (!snapshot.generatorControlsOpen || !snapshot.radioPlayback.isRadioActive ||
                snapshot.selectedStationId != source.id || source.sourceKind != RadioSourceKind.GENERATED
            ) return@withLock
            val arrangement = snapshot.editorGeneratorArrangement
                ?: createDefaultGeneratedArrangement(source.generatorPresetId ?: "custom-lab")
            val effective = source.copy(
                generatorSettings = snapshot.editorGeneratorSettings,
                generatorArrangement = arrangement,
            )
            val scaledAmbient = snapshot.sounds.filter { it.active }
                .map { it.copy(volume = it.volume * snapshot.environmentVolume) }
            runCatching {
                (generatedRenderer ?: error("持续声场渲染器尚未由宿主接入"))
                    .play(effective, scaledAmbient)
                if (expectedAmbientModeRevision != null && expectedAmbientModeRevision != ambientModeRevision) return@runCatching
                audio.setGeneratedAmbient(
                    arrangement = arrangement,
                    catalogue = snapshot.sounds,
                    playing = true,
                    masterVolume = snapshot.globalVolume,
                    transitionMillis = 60L,
                )
            }.onFailure {
                if (it is CancellationException) throw it
                fail(it)
            }
        }
    }
    private fun playRadio(station: RadioStation) {
        val s = state.value
        // 编排器打开时，生成频道的「试听」与「暂停后恢复」都必须渲染当前草稿而不是源频道已保存编排：
        // 否则改层/改参数后点试听、或暂停试听后恢复，听到的仍是旧编排。
        val effective = if (station.sourceKind == RadioSourceKind.GENERATED) {
            station.copy(
                generatorSettings = if (s.generatorControlsOpen && s.generatorSourceStationId == station.id) {
                    s.editorGeneratorSettings
                } else {
                    station.generatorSettings
                },
                generatorArrangement = s.arrangementFor(station),
            )
        } else {
            station
        }
        val effectiveSounds = effective.generatorArrangement?.let { s.materializeAmbient(it) }
            ?: if (s.generatedArrangementForCurrentStation()?.ambientMode == "preset") s.personalCatalogue() else s.sounds
        val hasEffectiveAmbient = effectiveSounds.any { it.active }
        // 本地频道以 localAudio 为曲目坐标，官方/生成频道以 tracks 为坐标。
        val maxIndex = max(0, if (effective.localAudio.isNotEmpty()) effective.localAudio.lastIndex else effective.tracks.lastIndex)
        val nextIndex = if (effective.id == s.selectedStationId) s.radioTrackIndex.coerceIn(0, maxIndex) else 0
        mutate {
            copy(
                selectedStationId = effective.id,
                radioTrackIndex = nextIndex,
                radioPlayback = PlaybackState.LOADING,
                sounds = effectiveSounds,
                ambientPlaying = if (effective.sourceKind == RadioSourceKind.GENERATED) hasEffectiveAmbient else ambientPlaying,
                globalPlaying = if (effective.sourceKind == RadioSourceKind.GENERATED) hasEffectiveAmbient || globalPlaying else globalPlaying,
            ).withConstellation()
        }
        if (effective.sourceKind != RadioSourceKind.GENERATED) syncAmbient()
        submitRadioCommand {
            runCatching {
                audio.updatePlaybackLabels(radio = effective.name)
                if (effective.sourceKind == RadioSourceKind.GENERATED) {
                    // 切换后端时先彻底停止另一条播放链。否则 Media3 与生成器会同时持有焦点并分别回写状态，
                    // 快速切页/反复点击后可出现 UI 显示播放但环境声或生成声实际静音。
                    audio.stopRadio()
                    val arrangement = effective.generatorArrangement ?: createDefaultGeneratedArrangement(effective.generatorPresetId ?: "custom-lab")
                    // 先启动生成层，首发音色不再被环境轨解码阻塞；随后加载频道环境配方。
                    val current = state.value
                    val scaled = current.sounds.filter { it.active }.map { it.copy(volume = it.volume * current.environmentVolume) }
                    (generatedRenderer ?: error("持续声场渲染器尚未由宿主接入")).play(effective, scaled)
                    audio.setGeneratedAmbient(arrangement, current.sounds, playing = true, masterVolume = current.globalVolume)
                    audio.ensureBackgroundPlayback()
                } else {
                    audio.clearGeneratedAmbientAutomation()
                    generatedRenderer?.stop()
                    audio.playRadio(effective, nextIndex)
                }
            }.onSuccess {
                // 播放状态一律由播放器/渲染器事件回流（Media3 引擎与生成渲染器），此处不得乐观置位 PLAYING。
            }.onFailure {
                if (it is CancellationException) throw it
                fail(it)
            }
        }
    }
    /** 详情面板曲目点击：设为当前频道，并从该曲目 index 开始播放（播完继续下一首、到尾循环由引擎播放列表保证）。 */
    private fun playStationTrack(stationId: String, trackIndex: Int) {
        val station = state.value.stations.firstOrNull { it.id == stationId } ?: return
        val maxIndex = max(0, if (station.localAudio.isNotEmpty()) station.localAudio.lastIndex else station.tracks.lastIndex)
        val index = trackIndex.coerceIn(0, maxIndex)
        // 先置为当前频道 + 目标曲目索引，再走 playRadio：playRadio 会把 radioTrackIndex 作为起始曲目传给引擎。
        mutate { copy(selectedStationId = stationId, radioTrackIndex = index) }
        playRadio(station)
    }

    private fun changeRadioTrack(delta: Int) {
        val station = currentStation() ?: return
        if (station.tracks.isEmpty()) return
        val next = (state.value.radioTrackIndex + delta + station.tracks.size) % station.tracks.size
        mutate { copy(radioTrackIndex = next) }
        playRadio(station)
    }
    private fun pauseRadio() {
        val generated = currentStation()?.sourceKind == RadioSourceKind.GENERATED
        // 暂停是确定性命令：UI 立即给「已暂停」反馈，音频随后由引擎/渲染器停止并事件回流（幂等确认）。
        mutate { copy(radioPlayback = PlaybackState.PAUSED, globalPlaying = ambientPlaying) }
        submitRadioCommand {
            runCatching {
                if (generated) generatedRenderer?.pause() ?: error("持续声场渲染器尚未由宿主接入") else audio.pauseRadio()
            }.onFailure {
                if (it is CancellationException) throw it
                fail(it)
            }
        }
    }
    /** 载入中再点 = 明确取消载入：回到 IDLE（不是暂停，不保留可恢复位置）。 */
    private fun cancelRadioLoad() {
        mutate { copy(radioPlayback = PlaybackState.IDLE) }
        submitRadioCommand {
            runCatching {
                if (currentStation()?.sourceKind == RadioSourceKind.GENERATED) generatedRenderer?.stop()
                else audio.stopRadio()
            }.onFailure {
                if (it is CancellationException) throw it
                fail(it)
            }
        }
    }
    /**
     * 串行执行电台命令：新意图使尚未开始的旧命令失效，但不会中断已经进入原生加载/启停流程的命令。
     * 这样既避免并发启动多个播放器，也不会把一次设备切换截断在半完成状态。
     */
    private fun submitRadioCommand(block: suspend () -> Unit) {
        val revision = ++radioCommandRevision
        radioCommandJob = viewModelScope.launch {
            radioCommandMutex.withLock {
                if (revision != radioCommandRevision) return@withLock
                block()
            }
        }
    }
    private fun currentStation() = state.value.stations.firstOrNull { it.id == state.value.selectedStationId }

    /** C8：执行删除（deleteCopies 控制是否同时删除应用私有音频副本）。 */
    private fun confirmDeleteStation(deleteCopies: Boolean) {
        val id = state.value.deleteStationConfirmId ?: return
        val deleted = state.value.stations.firstOrNull { it.id == id } ?: return
        val wasCurrent = state.value.selectedStationId == id
        val next = state.value.stations.filter { it.id != id }
        // 删除正在播放的频道必须停止其音频：否则播放器仍持有已删除频道的播放列表，
        // UI 已切到其它频道而声音继续（对齐 App.tsx deleteRadio 的 requestRadioPlayback(false)）。
        if (wasCurrent) {
            submitRadioCommand {
                runCatching {
                    if (deleted.sourceKind == RadioSourceKind.GENERATED) generatedRenderer?.stop() else audio.stopRadio()
                }.onFailure {
                    if (it is CancellationException) throw it
                    fail(it)
                }
            }
        }
        mutate {
            copy(
                stations = next,
                selectedStationId = if (wasCurrent) next.firstOrNull()?.id else selectedStationId,
                deleteStationConfirmId = null,
                notice = "已删除频道「${deleted.name}」" + if (!deleteCopies) "（已保留应用内音频副本）" else "",
            )
        }
        launchCatching { repository.deleteStation(id, deleteCopies) }
    }
    private fun launchCatching(block: suspend () -> Unit) = viewModelScope.launch { runCatching { block() }.onFailure { fail(it) } }
    private fun fail(error: Throwable) = mutate { copy(operationError = error.message ?: "操作失败，请重试", radioPlayback = if (radioPlayback == PlaybackState.LOADING) PlaybackState.ERROR else radioPlayback) }
    private fun mutate(transform: ListeningState.() -> ListeningState) = _state.update(transform)

    private fun ListeningState.withConstellation(): ListeningState {
        val ids = sounds.filter { it.active }.take(20).map { it.id }
        // App.tsx placementRef: keep existing nodes' spots; only new ids get placed.
        val seeds = constellation.filter { it.soundId in ids }.associate { it.soundId to GalaxyPoint(it.x, it.y) }
        val positions = galaxyPlacements(ids, seeds = seeds)
        return copy(constellation = ids.map { id -> positions.getValue(id).let { ConstellationPoint(id, it.x, it.y) } })
    }
}

private fun ListeningAction.requiresGeneratorLiveUpdate(): Boolean = when (this) {
    ListeningAction.UndoGeneratorChange,
    ListeningAction.RedoGeneratorChange,
    ListeningAction.RestoreGeneratorTemplate,
    ListeningAction.RestoreChannelAmbientDefault,
    ListeningAction.LoadCurrentAmbient,
    is ListeningAction.SetGeneratorAmbientMode,
    is ListeningAction.UpdateGeneratorSettings,
    is ListeningAction.CommitGeneratorArrangement,
    is ListeningAction.UpdateGeneratorArrangementLive,
    is ListeningAction.FinalizeGeneratorArrangement,
    is ListeningAction.AddGeneratorLayer,
    is ListeningAction.RemoveGeneratorLayer,
    is ListeningAction.DuplicateGeneratorLayer,
    is ListeningAction.MoveGeneratorLayer,
    is ListeningAction.AddGeneratorAmbientTrack,
    is ListeningAction.RemoveGeneratorAmbientTrack -> true
    is ListeningAction.ImportGeneratorConfiguration -> true
    else -> false
}

class ListeningViewModelFactory(
    private val repository: ListeningRepository,
    private val audio: ListeningAudioController,
    private val generatedRenderer: GeneratedAudioRenderer? = null,
    private val audioImporter: StationAudioImporter? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = ListeningViewModel(repository, audio, generatedRenderer = generatedRenderer, audioImporter = audioImporter) as T
}

private fun <T> List<T>.move(id: String, delta: Int): List<T> where T : Any {
    val mutable = toMutableList(); val index = mutable.indexOfFirst { item -> when (item) { is SoundPreset -> item.id == id; is RadioStation -> item.id == id; else -> false } }
    if (index < 0) return this; val target = (index + delta).coerceIn(0, mutable.lastIndex); mutable.add(target, mutable.removeAt(index)); return mutable
}

/** App.tsx formatFileSize (2296–2300) —— 本地配额标签的容量格式。 */
private fun formatStorageSize(bytes: Long): String {
    if (bytes <= 0) return "未知大小"
    if (bytes < 1024L * 1024L) return "${maxOf(1, (bytes / 1024.0).roundToInt())} KB"
    val mb = bytes / 1024.0 / 1024.0
    return if (bytes > 10L * 1024L * 1024L) "${mb.roundToInt()} MB" else "${(mb * 10.0).roundToInt() / 10.0} MB"
}

/** navigator.storage.estimate() 近似：数据分区可用字节（StatFs）。 */
private fun availableBytes(): Long = runCatching {
    val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
    stat.availableBlocksLong * stat.blockSizeLong
}.getOrDefault(-1)
