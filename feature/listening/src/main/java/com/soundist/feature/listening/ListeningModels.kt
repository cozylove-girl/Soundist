package com.soundist.feature.listening

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow

enum class ListeningDestination { HOME, SOUNDS, RADIO }
enum class SoundFilter { ALL, CURRENT, FAVORITES, NATURE, RAIN, ANIMALS, URBAN, PLACES, TRANSPORT, THINGS, NOISE }
enum class RadioGroup { OFFICIAL, GENERATED, CUSTOM }
enum class PlaybackState { IDLE, LOADING, PLAYING, AUDIBLE, PAUSED, ERROR }
enum class RadioSourceKind { OFFICIAL, GENERATED, STREAM, LOCAL }

data class AmbientSound(
    val id: String,
    val name: String,
    val category: SoundFilter,
    val volume: Float = 0f,
    val active: Boolean = false,
    val favorite: Boolean = false,
    val mediaPackKey: String = "ambient/$id",
    val localAssetUri: String? = null,
)

data class SoundPreset(
    val id: String,
    val name: String,
    val tracks: Map<String, Float>,
    val builtIn: Boolean = false,
    val purposeLabel: String = "自由",
)

/** App.tsx inferPresetPurposeLabel: /眠|夜|雨/ → 助眠, /写|学|专注|工作/ → 专注, else 自由. */
fun inferPresetPurposeLabel(name: String): String = when {
    Regex("眠|夜|雨").containsMatchIn(name) -> "助眠"
    Regex("写|学|专注|工作").containsMatchIn(name) -> "专注"
    else -> "自由"
}

/** App.tsx presetPurposeLabel: explicit label, else inferred from name. */
fun presetPurposeLabel(preset: SoundPreset): String =
    preset.purposeLabel.ifBlank { inferPresetPurposeLabel(preset.name) }
data class TrackLicense(val name:String,val author:String,val sourceName:String,val sourcePage:String,val licenseUrl:String,val attributionText:String="")

data class RadioTrack(
    val id: String,
    val title: String,
    val artist: String,
    val durationLabel: String = "",
    /** Provenance/download input only. Production playback must not hotlink it. */
    val mediaUrl: String = "",
    val sourcePage: String = "",
    val remoteCacheKey: String = "radio/$id",
    val localAssetUri: String? = null,
    val license: TrackLicense? = null,
    val instruments: List<String> = emptyList(),
    val era: String = "",
    val focusFit: String = "",
    /** 是否进入首发可见目录（基础 APK 内置 + 可下载离线包）。false = 未分包曲目，不显示。 */
    val released: Boolean = true,
)
data class GeneratorSettings(val tempo:Int=54,val density:Int=42,val variation:Int=48,val entryDelaySeconds:Float=1.4f,val eventDurationSeconds:Float=3.2f,val gain:Int=72,val ambientResponse:Int=70,val adaptive:Boolean=true)
data class GeneratedScene(val id:String,val name:String,val durationMinutes:Int,val energy:Int,val density:Int,val brightness:Int,val space:Int)
data class GeneratedAmbientTrack(val soundId:String,val name:String,val category:String,val enabled:Boolean=true,val volume:Int,val pan:Int=0,val entryDelaySeconds:Float=0f,val durationMinutes:Int=0,val probability:Int=100,val fadeInSeconds:Float=2f,val fadeOutSeconds:Float=3f,val relationship:String="independent")
data class GeneratedLayer(
    val id:String,val name:String,val type:String,val volume:Int,val probability:Int,
    val timbre:String="air-pad",val enabled:Boolean=true,val solo:Boolean=false,val pan:Int=0,
    val entryDelaySeconds:Float=if(type=="atmosphere")0f else 2f,val durationSeconds:Float=if(type=="atmosphere")12f else 3.4f,
    val density:Int=if(type=="atmosphere")24 else 38,val rhythm:String="free",val register:String=if(type=="atmosphere")"low" else "middle",
    val scale:String="pentatonic",val fadeInSeconds:Float=if(type=="atmosphere")3.5f else .12f,val fadeOutSeconds:Float=if(type=="atmosphere")4.5f else 2.2f,
    val ambientResponse:Int=64,val relationship:String="independent",val relationshipTargetId:String?=null,
)
data class GeneratedArrangement(
    val seed:String,
    val sectionMinutes:Int,
    val arc:String,
    val layers:List<GeneratedLayer>,
    val ambientTracks:List<GeneratedAmbientTrack> = emptyList(),
    val scenes:List<GeneratedScene> = emptyList(),
    /** preset = 使用频道保存的环境配方；current = 保留用户当前声场，仅切换生成音乐。 */
    val ambientMode:String = "preset",
)

/**
 * Session-only changes layered over a generated station's immutable ambient recipe.
 * [overrides] contains added or modified tracks; [removedSoundIds] records tracks
 * explicitly removed from the station recipe.  The user's personal mix is never
 * represented here.
 */
data class ChannelAmbientPatch(
    val overrides: List<GeneratedAmbientTrack> = emptyList(),
    val removedSoundIds: Set<String> = emptySet(),
)

/**
 * A generated station owns a session-local ambient recipe.  It is intentionally
 * separate from [ListeningState.personalSounds]: editing a station must never
 * overwrite the user's Sounds-page mix.
 */
data class ChannelAmbientSession(
    val patch: ChannelAmbientPatch = ChannelAmbientPatch(),
    val ambientMode: String = "preset",
    val adjusted: Boolean = false,
)

/** App.tsx generatorEditorSnapshotRef (4542–4549)：打开编排器时保存的播放快照，放弃时恢复。 */
data class GeneratorSnapshot(
    val radioId: String?,
    val radioPlaying: Boolean,
    val settings: GeneratorSettings,
    val arrangement: GeneratedArrangement?,
    val sounds: List<AmbientSound>,
    val environmentPlaying: Boolean,
    val channelAmbientSessions: Map<String, ChannelAmbientSession>,
)

/** App.tsx EMPTY_RADIO_DRAFT (radioCatalog.ts)。sourceKind 仅取 local/stream。 */
data class RadioDraft(
    val name: String = "",
    val desc: String = "",
    val genre: String = "",
    val url: String = "",
    val sourceKind: RadioSourceKind = RadioSourceKind.LOCAL,
    val imageUrl: String? = null,
    val storageKey: String = "",
    val fileName: String = "",
    val mimeType: String = "",
)

/** App.tsx RadioDraftAudio 条目（5051–5083）。validation: ready/checking/invalid/warning。 */
data class RadioDraftAudioItem(
    val id: String,
    val storageKey: String = "",
    val fileName: String,
    val mimeType: String = "audio/*",
    val sizeBytes: Long,
    val durationSeconds: Int = 0,
    val validation: String = "checking",
    val validationMessage: String = "",
)

data class RadioStation(
    val id: String,
    val name: String,
    val description: String,
    val group: RadioGroup,
    val source: String,
    val url: String,
    val artworkUri: String? = null,
    val genre: String = "氛围",
    val sourceKind: RadioSourceKind = RadioSourceKind.OFFICIAL,
    val purposes: List<String> = emptyList(),
    val durationLabel: String = "",
    val tracks: List<RadioTrack> = emptyList(),
    val layers: List<GeneratedLayer> = emptyList(),
    val generatorPresetId: String? = null,
    val generatorSettings: GeneratorSettings? = null,
    val generatorArrangement: GeneratedArrangement? = null,
    /** Persisted content:// selections for user-owned local channels. */
    val localAudio: List<LocalAudioSelection> = emptyList(),
    val custom: Boolean = false,
    /** App.tsx catalogGroup — normalized group for the 音乐类型 filter chips (全部/古典/器乐/节拍/氛围/人声). */
    val catalogGroup: String = "",
    /** App.tsx station-level license (used when a station has no per-track license). */
    val license: TrackLicense? = null,
    val transitionMode: String = "",
    val transitionSeconds: Double = 0.0,
)

data class ConstellationPoint(val soundId: String, val x: Float, val y: Float)

data class ListeningState(
    val destination: ListeningDestination = ListeningDestination.HOME,
    val sounds: List<AmbientSound> = SoundCatalog.items,
    /** Last user-owned mix from the Sounds page; generated station recipes never overwrite it. */
    val personalSounds: List<AmbientSound> = emptyList(),
    /** Session edits for generated stations, keyed by station id. */
    val channelAmbientSessions: Map<String, ChannelAmbientSession> = emptyMap(),
    val filter: SoundFilter = SoundFilter.ALL,
    val query: String = "",
    /** App.tsx radioSearch — kept separate from the sounds search query. */
    val radioQuery: String = "",
    val ambientPlaying: Boolean = false,
    val radioPlayback: PlaybackState = PlaybackState.IDLE,
    val globalPlaying: Boolean = false,
    /** App.tsx masterVolume.  Values are stored in source percent / 100. */
    val globalVolume: Float = .8f,
    /** App.tsx environmentVolume.  Kept separate from the master strip. */
    val environmentVolume: Float = 1f,
    /** App.tsx radioVolume.  Kept separate from the master strip. */
    val radioVolume: Float = .8f,
    val mixerExpanded: Boolean = false,
    val highlightedSoundId: String? = null,
    val sceneName: String = "夜雨森林",
    val presets: List<SoundPreset> = SoundCatalog.builtInPresets,
    val stations: List<RadioStation> = RadioCatalog.initial,
    val radioGroup: RadioGroup = RadioGroup.GENERATED,
    val selectedStationId: String? = null,
    val radioTrackIndex: Int = 0,
    val stationDetailsId: String? = null,
    val presetManagerOpen: Boolean = false,
    val radioGenre: String = "全部",
    val radioPurpose: String = "全部用途",
    // Generator editor (App.tsx 4540–4704)
    val generatorControlsOpen: Boolean = false,
    val generatorDraftName: String = "",
    val editorGeneratorSettings: GeneratorSettings = GeneratorSettings(),
    val editorGeneratorArrangement: GeneratedArrangement? = null,
    val generatorSourceStationId: String? = null,
    val generatorBaseline: String? = null,
    val generatorSnapshot: GeneratorSnapshot? = null,
    val selectedGeneratorLayerId: String? = null,
    val selectedAmbientTrackId: String? = null,
    val selectedGeneratorSceneId: String? = null,
    val editingGeneratorRadioId: String? = null,
    val generatorPast: List<GeneratedArrangement> = emptyList(),
    val generatorFuture: List<GeneratedArrangement> = emptyList(),
    val generatorAdvancedOpen: Boolean = false,
    val generatorDiscardConfirmOpen: Boolean = false,
    val previewingTimbre: String? = null,
    // Custom form (App.tsx 6010–6100)
    val customRadioOpen: Boolean = false,
    val editingRadioId: String? = null,
    val radioDraft: RadioDraft = RadioDraft(),
    val radioDraftAudio: List<RadioDraftAudioItem> = emptyList(),
    val radioStorageLabel: String = "",
    val radioFormError: String = "",
    val radioSaving: Boolean = false,
    val radioImportProgress: Int = 0,
    // Manage channels (App.tsx 6168–6214)
    val manageChannelsOpen: Boolean = false,
    val channelMenuId: String? = null,
    val draggingRadioId: String? = null,
    /** C8：待确认删除的频道 id（非空时显示三选项确认弹窗）。 */
    val deleteStationConfirmId: String? = null,
    val operationError: String? = null,
    /** App.tsx showNotice 的非错误提示（保存/删除/试听失败等）。 */
    val notice: String? = null,
    val sceneSignature: String = "",
    val constellation: List<ConstellationPoint> = emptyList(),
)

sealed interface ListeningAction {
    data class Navigate(val destination: ListeningDestination) : ListeningAction
    data object ToggleAmbient : ListeningAction
    data object ToggleGlobal : ListeningAction
    data object ToggleRadio : ListeningAction
    data object ToggleMixer : ListeningAction
    data class SetMasterVolume(val volume: Float) : ListeningAction
    data class SetEnvironmentVolume(val volume: Float) : ListeningAction
    data class SetRadioVolume(val volume: Float) : ListeningAction
    data class SetSoundVolume(val id: String, val volume: Float) : ListeningAction
    data class ToggleSound(val id: String) : ListeningAction
    data class RemoveSound(val id: String) : ListeningAction
    data class ToggleFavorite(val id: String) : ListeningAction
    data class SetFilter(val filter: SoundFilter) : ListeningAction
    data class SetQuery(val query: String) : ListeningAction
    data object RandomizeScene : ListeningAction
    data class SavePreset(val name: String) : ListeningAction
    data class SavePresetWithPurpose(val name:String,val purpose:String):ListeningAction
    data class ImportPreset(val preset: SoundPreset) : ListeningAction
    data class UpdatePreset(val preset:SoundPreset):ListeningAction
    data class DeletePreset(val id: String) : ListeningAction
    data class DuplicatePreset(val id: String) : ListeningAction
    data class MovePreset(val id: String, val delta: Int) : ListeningAction
    data object TogglePresetManager : ListeningAction
    data class ApplyPreset(val id: String) : ListeningAction
    data class HighlightSound(val id: String) : ListeningAction
    data class SetRadioGroup(val group: RadioGroup) : ListeningAction
    data class SetRadioQuery(val query: String) : ListeningAction
    data class SetRadioGenre(val genre: String) : ListeningAction
    data class SetRadioPurpose(val purpose: String) : ListeningAction
    data class PlayStation(val id: String) : ListeningAction
    /** 详情面板曲目点击：设为当前频道并从指定曲目 index 开始播放，播完继续下一首、到尾循环。 */
    data class PlayStationTrack(val stationId: String, val trackIndex: Int) : ListeningAction
    data object PreviousRadioTrack : ListeningAction
    data object NextRadioTrack : ListeningAction
    data class OpenStationDetails(val id: String?) : ListeningAction
    data object ClearError : ListeningAction
    data object ClearNotice : ListeningAction
    data class ShowNotice(val text: String) : ListeningAction
    // Generator editor (App.tsx 4540–4704)
    data class OpenGeneratorEditor(val station: RadioStation) : ListeningAction
    data class CloseGeneratorEditor(val discard: Boolean) : ListeningAction
    data object RequestCloseGeneratorEditor : ListeningAction
    data object UndoGeneratorChange : ListeningAction
    data object RedoGeneratorChange : ListeningAction
    data class SetGeneratorDraftName(val name: String) : ListeningAction
    data class UpdateGeneratorSettings(val updater: (GeneratorSettings) -> GeneratorSettings) : ListeningAction
    data class CommitGeneratorArrangement(val updater: (GeneratedArrangement) -> GeneratedArrangement) : ListeningAction
    data class SetGeneratorAmbientMode(val mode: String) : ListeningAction
    data object RestoreChannelAmbientDefault : ListeningAction
    /** 编排器滑块拖动中的限频实时更新：只改编辑器编排，不写撤销栈（松手时由 [FinalizeGeneratorArrangement] 一次性写一条撤销）。 */
    data class UpdateGeneratorArrangementLive(val updater: (GeneratedArrangement) -> GeneratedArrangement) : ListeningAction
    /** 编排器滑块松手提交：base 为拖动开始前的编排（一次撤销回到拖动前），updater 生成最终编排。 */
    data class FinalizeGeneratorArrangement(val base: GeneratedArrangement, val updater: (GeneratedArrangement) -> GeneratedArrangement) : ListeningAction
    data class SelectGeneratorLayer(val id: String?) : ListeningAction
    data class SelectGeneratorScene(val id: String?) : ListeningAction
    data class SelectAmbientTrack(val soundId: String?) : ListeningAction
    data object ToggleGeneratorAdvanced : ListeningAction
    data object CloseGeneratorDiscardConfirm : ListeningAction
    data class AddGeneratorLayer(val type: String) : ListeningAction
    data class RemoveGeneratorLayer(val layerId: String) : ListeningAction
    data class DuplicateGeneratorLayer(val layerId: String) : ListeningAction
    data class MoveGeneratorLayer(val layerId: String, val direction: Int) : ListeningAction
    data class AddGeneratorAmbientTrack(val soundId: String) : ListeningAction
    data class RemoveGeneratorAmbientTrack(val soundId: String) : ListeningAction
    data object LoadCurrentAmbient : ListeningAction
    data class AuditionGeneratorTimbre(val timbre: String) : ListeningAction
    data object PreviewGeneratorDraft : ListeningAction
    data class ImportGeneratorConfiguration(val configuration: GeneratorConfiguration) : ListeningAction
    data object SaveGeneratedCopy : ListeningAction
    data object RestoreGeneratorTemplate : ListeningAction
    // Custom form (App.tsx 6010–6100)
    data class OpenCustomRadio(val sourceKind: RadioSourceKind) : ListeningAction
    data object CloseCustomRadioEditor : ListeningAction
    data class EditRadio(val station: RadioStation) : ListeningAction
    data class UpdateRadioDraft(val updater: (RadioDraft) -> RadioDraft) : ListeningAction
    data class UpdateRadioUrl(val url: String) : ListeningAction
    data class AddRadioDraftAudio(val items: List<RadioDraftAudioItem>) : ListeningAction
    data class MoveRadioDraftAudio(val id: String, val direction: Int) : ListeningAction
    data class RemoveRadioDraftAudio(val id: String) : ListeningAction
    data object SaveCustomRadio : ListeningAction
    // Manage channels (App.tsx 6168–6214)
    data object OpenManageChannels : ListeningAction
    data object CloseManageChannels : ListeningAction
    data class SetChannelMenuId(val id: String?) : ListeningAction
    data class MoveCustomRadio(val id: String, val direction: Int) : ListeningAction
    data class DeleteRadio(val id: String) : ListeningAction
    data class ConfirmDeleteRadio(val deleteCopies: Boolean) : ListeningAction
    data object CancelDeleteRadio : ListeningAction
    data class SetDraggingRadio(val id: String?) : ListeningAction
}

interface ListeningRepository {
    suspend fun load(): ListeningState
    suspend fun savePreset(preset: SoundPreset)
    suspend fun deletePreset(id: String)
    suspend fun reorderPresets(ids: List<String>)
    suspend fun saveSound(sound: AmbientSound)
    suspend fun saveStation(station: RadioStation)
    suspend fun deleteStation(id: String, deleteCopies: Boolean = true)
    suspend fun reorderStations(ids: List<String>)
}

/** A real process-local store used by previews/tests. Release composition should inject Room. */
class StatefulListeningRepository(initial: ListeningState = ListeningState()) : ListeningRepository {
    private var snapshot = initial
    override suspend fun load() = snapshot
    override suspend fun savePreset(preset: SoundPreset) { snapshot = snapshot.copy(presets = snapshot.presets.filterNot { it.id == preset.id } + preset) }
    override suspend fun deletePreset(id: String) { snapshot = snapshot.copy(presets = snapshot.presets.filterNot { it.id == id }) }
    override suspend fun reorderPresets(ids: List<String>) { val byId = snapshot.presets.associateBy { it.id }; snapshot = snapshot.copy(presets = ids.mapNotNull(byId::get)) }
    override suspend fun saveSound(sound: AmbientSound) { snapshot = snapshot.copy(sounds = snapshot.sounds.map { if (it.id == sound.id) sound else it }) }
    override suspend fun saveStation(station: RadioStation) { snapshot = snapshot.copy(stations = snapshot.stations.filterNot { it.id == station.id } + station) }
    override suspend fun deleteStation(id: String, deleteCopies: Boolean) { snapshot = snapshot.copy(stations = snapshot.stations.filterNot { it.id == id }) }
    override suspend fun reorderStations(ids: List<String>) { val byId = snapshot.stations.associateBy { it.id }; snapshot = snapshot.copy(stations = ids.mapNotNull(byId::get)) }
}

/**
 * 播放器事件回流的电台状态（宿主把 Media3 播放器事件映射过来）。
 * ViewModel 的 radioPlayback / radioTrackIndex 必须以该流为准，不得乐观置位。
 */
data class RadioEngineState(
    val state: PlaybackState = PlaybackState.IDLE,
    val stationId: String? = null,
    /** 当前曲目在频道（station.tracks / station.localAudio）中的索引。 */
    val trackIndex: Int = 0,
    /** 错误文本；终态错误（state == ERROR）应展示，跳曲过渡错误（state == LOADING）仅提示。 */
    val errorMessage: String? = null,
    val errorType: String? = null,
)

/** 环境声轨播放失败回流：soundId 对应的音频文件解码/载入失败，宿主从每个环境声播放器事件映射。 */
data class AmbientErrorEvent(val soundId: String, val message: String)

/**
 * The audio graph is the single source of truth for controls outside the app UI, including the
 * media notification and headset buttons.  ViewModels mirror this state instead of assuming that
 * every playback change originated from a Compose action.
 */
data class PlaybackGraphState(
    val ambientPlaying: Boolean = false,
    val radioPlaying: Boolean = false,
    val globalPlaying: Boolean = false,
    /** The actual ambient composition owned by the audio backend; null until it is known. */
    val ambientTracks: Map<String, Float>? = null,
)

/**
 * 电台「实际出声/正在播放」语义：PLAYING = 音频时钟已启动（AudioTrack 开始写、调度时钟在跑）；
 * AUDIBLE = 已产生可感知声音（更精确的「已出声」）。两者对 UI 的播放控制等价（暂停/波形/可点击），
 * 因此用这一个谓词统一判定，禁止在 UI 各处只认 PLAYING 而漏掉 AUDIBLE。
 */
val PlaybackState.isRadioActive: Boolean get() = this == PlaybackState.PLAYING || this == PlaybackState.AUDIBLE

interface ListeningAudioController {
    /** 播放器事件回流的电台状态（生产实现由 Media3 引擎事件映射；预览实现自行维护）。 */
    val radioEvents: Flow<RadioEngineState>
    /** 通知栏、耳机按键和应用内控制共享的真实播放图状态。 */
    val playbackGraph: Flow<PlaybackGraphState> get() = emptyFlow()
    /** 环境声轨解码失败回流（soundId → 错误信息）。失败的声音不再计入播放中，其余声音继续。 */
    val ambientErrors: Flow<AmbientErrorEvent> get() = emptyFlow()
    suspend fun setAmbient(tracks: List<AmbientSound>, playing: Boolean, masterVolume: Float)
    /** Mode switches use one explicit short transition instead of the user's sleep/start fade. */
    suspend fun setAmbientWithTransition(
        tracks: List<AmbientSound>,
        playing: Boolean,
        masterVolume: Float,
        transitionMillis: Long,
    ) = setAmbient(tracks, playing, masterVolume)
    /** 应用生成频道的环境配方；生产实现会执行逐轨时间、概率、淡入淡出与关系参数。 */
    suspend fun setGeneratedAmbient(
        arrangement: GeneratedArrangement,
        catalogue: List<AmbientSound>,
        playing: Boolean,
        masterVolume: Float,
        transitionMillis: Long = 60L,
    ) {
        val byId = arrangement.ambientTracks.associateBy { it.soundId }
        val tracks = if (arrangement.ambientMode == "current") catalogue else catalogue.map { sound ->
            val recipe = byId[sound.id]
            if (recipe == null) sound.copy(active = false)
            else sound.copy(active = recipe.enabled, volume = recipe.volume.coerceIn(0, 100) / 100f)
        }
        setAmbientWithTransition(tracks, playing && tracks.any { it.active }, masterVolume, transitionMillis)
    }
    /** 离开生成频道时停止逐轨自动化，保留当前实际声场。 */
    suspend fun clearGeneratedAmbientAutomation() {}
    suspend fun setMasterVolume(volume: Float) {}
    suspend fun setAmbientVolume(volume: Float) {}
    /** 同步电台音量给引擎（生成电台与 Media3 电台共用 applyVolumes 的音量通路），使引擎成为音量单一权威。默认空实现。 */
    suspend fun setRadioVolume(volume: Float) {}
    /** 更新系统通知/锁屏中的当前声音上下文；预览实现可忽略。 */
    suspend fun updatePlaybackLabels(radio: String? = null) {}
    /** 生成频道没有环境轨时也要显式启动同一后台媒体会话。 */
    suspend fun ensureBackgroundPlayback() {}
    suspend fun playRadio(station: RadioStation, trackIndex: Int)
    suspend fun pauseRadio()
    /** 停止电台并回到 IDLE（区别于暂停：清空电台会话，不保留可恢复的播放位置）。 */
    suspend fun stopRadio()
    suspend fun stopAll()
}

/** Host bridge for Android's Photo Picker. Returning null means that selection was cancelled. */
fun interface StationArtworkPicker {
    suspend fun pick(currentUri: String?): String?
}
data class LocalAudioSelection(val uri:String,val displayName:String,val sizeBytes:Long,val mimeType:String,val durationSeconds:Int = 0)
fun interface StationAudioPicker { suspend fun pickMultiple():List<LocalAudioSelection> }

/** 宿主桥接：把用户挑选的外部 content:// 音频复制到应用私有目录，返回可长期使用的 file:// 选择（复制成功后才写库）。 */
fun interface StationAudioImporter {
    suspend fun import(stationId: String, files: List<LocalAudioSelection>): List<LocalAudioSelection>
}

fun ListeningState.visibleSounds(): List<AmbientSound> = sounds.filter { sound ->
    when (filter) {
        SoundFilter.ALL -> true
        SoundFilter.CURRENT -> sound.active
        SoundFilter.FAVORITES -> sound.favorite
        else -> sound.category == filter
    }
}.filter { query.isEmpty() || it.name.contains(query) || it.category.label().contains(query) }

/** App.tsx sourceStations (5669–5673): filter by library tab using sourceKind + custom. */
fun ListeningState.sourceStations(): List<RadioStation> = stations.filter { station ->
    when (radioGroup) {
        RadioGroup.GENERATED -> station.sourceKind == RadioSourceKind.GENERATED
        RadioGroup.OFFICIAL -> station.sourceKind == RadioSourceKind.OFFICIAL
        RadioGroup.CUSTOM -> station.custom
    }
}

fun ListeningState.visibleStations(): List<RadioStation> = sourceStations().filter { station ->
    val matchesGenre = radioGenre == "全部" || station.catalogGroup == radioGenre
    val matchesPurpose = radioPurpose == "全部用途" || radioPurpose in station.purposes
    val trackSearch = station.tracks.joinToString(" ") { "${it.title} ${it.artist} ${it.instruments.joinToString(" ")} ${it.era}" }
    val searchable = "${station.name} ${station.description} ${station.genre} ${station.purposes.joinToString(" ")} $trackSearch ${station.license?.author ?: ""}".lowercase()
    val normalizedSearch = radioQuery.trim().lowercase()
    matchesGenre && matchesPurpose && (normalizedSearch.isBlank() || searchable.contains(normalizedSearch))
}

/**
 * 组装频道可播放源（Triple<itemId, stationIndex, uri>）。生产播放器（Media3）以此构建
 * `MediaItem.fromUri(uri)` 播放列表；Media3 的 DefaultMediaSourceFactory 会按 URI 自动识别
 * MP3/AAC/OGG（Progressive）、HLS .m3u8（HlsMediaSource）、Icecast 直播流。
 *
 * 播放源规则：仅允许 asset://、content://、file:// 与应用私有目录；http(s) 仅限用户主动
 * 添加的音频流频道（STREAM）。官方/生成曲目的 mediaUrl 一律不作为生产播放地址（禁止热链）。
 */
fun RadioStation.playableSources(): List<Triple<String, Int, String>> {
    val sources = mutableListOf<Triple<String, Int, String>>()
    when (sourceKind) {
        RadioSourceKind.LOCAL -> {
            localAudio.forEachIndexed { index, item ->
                if (item.uri.isAllowedPlaybackUri()) sources += Triple(item.uri, index, item.uri)
            }
        }
        RadioSourceKind.STREAM -> {
            val streamUrl = url.trim()
            if (streamUrl.isNotBlank() && streamUrl.isAllowedPlaybackUri()) sources += Triple(id, 0, streamUrl)
        }
        else -> {
            tracks.forEachIndexed { index, track ->
                track.localAssetUri?.takeIf { it.isAllowedPlaybackUri() }?.let { sources += Triple(track.id, index, it) }
            }
        }
    }
    return sources
}

private fun String.isAllowedPlaybackUri(): Boolean =
    startsWith("asset://") || startsWith("content://") || startsWith("file://") ||
        // 用户主动添加的音频流频道允许网络地址（http/https）；官方曲目的 mediaUrl 永不进入播放列表。
        startsWith("http://") || startsWith("https://")

/** Stateful controller for previews. Production injects the Media3 adapter; no operation is silently dropped. */
class StatefulAudioController : ListeningAudioController {
    var ambient: List<AmbientSound> = emptyList(); private set
    var ambientPlaying = false; private set
    var radioId: String? = null; private set
    private val _radioEvents = MutableStateFlow(RadioEngineState())
    override val radioEvents: Flow<RadioEngineState> = _radioEvents
    private val _playbackGraph = MutableStateFlow(PlaybackGraphState())
    override val playbackGraph: Flow<PlaybackGraphState> = _playbackGraph
    override suspend fun setAmbient(tracks: List<AmbientSound>, playing: Boolean, masterVolume: Float) {
        ambient = tracks
        ambientPlaying = playing
        _playbackGraph.value = _playbackGraph.value.copy(
            ambientPlaying = playing,
            globalPlaying = playing || _playbackGraph.value.radioPlaying,
            ambientTracks = tracks.filter { it.active }.associate { it.id to it.volume },
        )
    }
    override suspend fun setMasterVolume(volume: Float) {}
    override suspend fun setAmbientVolume(volume: Float) {}
    override suspend fun playRadio(station: RadioStation, trackIndex: Int) {
        if (station.sourceKind == RadioSourceKind.GENERATED) throw UnsupportedOperationException("持续声场需要宿主注入 GeneratedAudioRenderer")
        val track = station.tracks.getOrNull(trackIndex)
        val playable = track?.localAssetUri ?: station.url.takeIf { it.startsWith("content://") || it.startsWith("file://") }
        requireNotNull(playable) { "音频包尚未安装：${track?.remoteCacheKey ?: station.id}" }
        require(playable.startsWith("asset://") || playable.startsWith("content://") || playable.startsWith("file://")) { "生产播放仅允许应用资产或私有缓存" }
        radioId = station.id
        _radioEvents.value = RadioEngineState(PlaybackState.PLAYING, station.id, trackIndex.coerceAtLeast(0))
        _playbackGraph.value = _playbackGraph.value.copy(radioPlaying = true, globalPlaying = true)
    }
    override suspend fun pauseRadio() {
        radioId = null
        _radioEvents.value = RadioEngineState(PlaybackState.PAUSED)
        _playbackGraph.value = _playbackGraph.value.copy(radioPlaying = false, globalPlaying = ambientPlaying)
    }
    override suspend fun stopRadio() {
        radioId = null
        _radioEvents.value = RadioEngineState()
        _playbackGraph.value = _playbackGraph.value.copy(radioPlaying = false, globalPlaying = ambientPlaying)
    }
    override suspend fun stopAll() {
        ambientPlaying = false
        radioId = null
        _radioEvents.value = RadioEngineState()
        _playbackGraph.value = PlaybackGraphState()
    }
}

/** Implement this in the host audio module; generated:// is never treated as a media URL. */
interface GeneratedAudioRenderer {
    suspend fun play(station: RadioStation, activeAmbient: List<AmbientSound>)
    suspend fun pause()
    suspend fun stop()

    /** App.tsx previewGeneratedTimbre — 单音色试听。宿主渲染器按需覆盖；默认空实现（无声音）。 */
    suspend fun previewTimbre(timbre: String) {}

    /** App.tsx 3450 startGeneratedRadio outputVolume（masterVolume/100*radioVolume/100）。宿主渲染器按需覆盖。 */
    fun setVolume(value: Float) {}

    /**
     * 生成电台真实播放状态回流（preparing/playing/paused/stopped/error）。
     * 只有 AudioTrack 真正初始化并开始写入有效 PCM 后才发 PLAYING；暂停完成、线程终止、
     * AudioTrack 错误、采样异常都要回流到 ViewModel。默认空实现（无事件）。
     */
    val radioEvents: Flow<RadioEngineState> get() = emptyFlow()
}
