package com.soundist.core.model

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable enum class SoundCategory { NATURE, RAIN, ANIMAL, URBAN, PLACE, TRANSPORT, OBJECT, NOISE }
@Serializable enum class PlaybackKind { AMBIENT, RADIO, LOCAL }
@Serializable enum class SessionKind { FOCUS, BREAK, SLEEP }
@Serializable enum class SessionStatus { IDLE, RUNNING, PAUSED, REVIEW, COMPLETED, CANCELLED }
@Serializable enum class SyncOperation { UPSERT, DELETE }
@Serializable enum class SyncState { PENDING, RUNNING, SUCCEEDED, FAILED }
@Serializable enum class Priority { LOW, MEDIUM, HIGH }
@Serializable enum class FocusTimerMode { COUNTDOWN, STOPWATCH }
@Serializable enum class FocusTargetKind { FREE, TODO, PLAN, HABIT, COUNTDOWN }
@Serializable enum class HabitMetric { MINUTES, COUNT, CHECK }
@Serializable enum class SleepEndMode { DURATION, CLOCK }
@Serializable enum class SleepTarget { ALL, AMBIENT, RADIO }
@Serializable enum class NoteBlockKind { PARAGRAPH, HEADING, QUOTE, CHECKLIST, IMAGE, AUDIO, DRAWING, FILE, INTERNAL_LINK }
@Serializable enum class AttachmentKind { IMAGE, AUDIO, DRAWING, FILE }
@Serializable enum class RadioSourceKind { GENERATED, OFFICIAL, CUSTOM, LOCAL }

@Serializable
data class Sound(
    val id: String,
    val name: String,
    val category: SoundCategory,
    val assetUri: String,
    val defaultVolume: Float = .5f,
    val isFavorite: Boolean = false,
)

@Serializable
data class MixTrack(
    val soundId: String,
    val volume: Float,
    val muted: Boolean = false,
    val enabled: Boolean = true,
)

@Serializable
data class Preset(
    val id: String,
    val name: String,
    val tracks: List<MixTrack>,
    val updatedAt: Long,
    val purposeLabel: String? = null,
    val builtIn: Boolean = false,
)

@Serializable
data class RadioStation(
    val id: String,
    val name: String,
    val streamUri: String,
    val artworkUri: String?,
    val isFavorite: Boolean,
    val description: String = "",
    val sourceKind: RadioSourceKind = RadioSourceKind.CUSTOM,
    val genre: String = "",
    val purpose: String = "",
    val position: Double = 0.0,
    val enabled: Boolean = true,
    val updatedAt: Long = 0L,
    /**
     * Versioned, opaque station-specific state. The database deliberately does not
     * interpret this JSON so generator settings, generated tracks and local-audio
     * metadata survive persistence and can evolve independently of the core model.
     */
    val payloadJson: String? = null,
)

/** A user-imported audio file attached to a custom radio channel. */
@Serializable
data class CustomRadioFile(
    val id: String,
    val stationId: String,
    val displayName: String,
    val privatePath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val sha256: String,
    val sortIndex: Int,
)

@Serializable
data class PlaybackSnapshot(
    val tracks: List<MixTrack>,
    val radioId: String?,
    val playing: Boolean,
    val updatedAt: Long,
    val masterVolume: Float = .8f,
    val ambientVolume: Float = 1f,
    val radioVolume: Float = .8f,
    /** 上次播放的电台曲目索引（station.tracks / station.localAudio 坐标），用于恢复。 */
    val radioTrackIndex: Int = 0,
)

@Serializable
data class FocusSession(
    val id: String,
    val kind: SessionKind,
    val status: SessionStatus,
    val startedAt: Long,
    val targetEndAt: Long?,
    val pausedAt: Long?,
    val completedAt: Long?,
    val label: String?,
    val targetKind: FocusTargetKind = FocusTargetKind.FREE,
    val targetId: String? = null,
    val timerMode: FocusTimerMode = FocusTimerMode.COUNTDOWN,
    val elapsedSeconds: Long = 0L,
    val review: String? = null,
    val soundSnapshot: List<MixTrack> = emptyList(),
    val radioSnapshotId: String? = null,
    val phase: String = "FOCUS",
    val focusMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val cycleRound: Int = 1,
    val cycleRounds: Int = 4,
    val longBreakMinutes: Int = 15,
    val autoBreak: Boolean = true,
    val autoFocus: Boolean = false,
    val completionMinutes: Int = 0,
    /** PERSONAL / CHANNEL; null for records created before audio-context capture. */
    val ambientMode: String? = null,
    /** USER_AMBIENT / CHANNEL_RECIPE / GENERATED_CHANNEL / RADIO / SILENT / UNRECORDED. */
    val audioSource: String = "UNRECORDED",
)

@Serializable
data class Todo(
    val id: String,
    val title: String,
    val completed: Boolean,
    val dueAt: Long?,
    val updatedAt: Long,
    val longTerm: Boolean = false,
    val allDay: Boolean = false,
    val priority: Priority = Priority.MEDIUM,
    val reminderAt: Long? = null,
    val estimatedMinutes: Int? = null,
    val planId: String? = null,
    val note: String = "",
    val defaultSceneId: String? = null,
    val archived: Boolean = false,
    val position: Double = 0.0,
)

@Serializable
data class Plan(
    val id: String,
    val title: String,
    val startAt: Long,
    val endAt: Long?,
    val updatedAt: Long,
    val targetMinutes: Int = 0,
    val accumulatedMinutes: Int = 0,
    val milestone: String = "",
    val description: String = "",
    val reminderAt: Long? = null,
    val defaultSceneId: String? = null,
    val archived: Boolean = false,
    val position: Double = 0.0,
)

@Serializable
data class Habit(
    val id: String,
    val name: String,
    val targetPerWeek: Int,
    val updatedAt: Long,
    val weekdays: Set<Int> = emptySet(),
    val metric: HabitMetric = HabitMetric.MINUTES,
    val dailyTarget: Int = 1,
    val streak: Int = 0,
    val reminderAt: Long? = null,
    val defaultSceneId: String? = null,
    val archived: Boolean = false,
    val position: Double = 0.0,
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
)

@Serializable data class HabitCheck(val id: String, val habitId: String, val checkedAt: Long, val value: Int = 1)

@Serializable
data class Countdown(
    val id: String,
    val title: String,
    val targetAt: Long,
    val updatedAt: Long,
    val note: String = "",
    val dailyMinutes: Int = 0,
    val investedMinutes: Int = 0,
    val planId: String? = null,
    val milestone: String = "",
    val reminderAt: Long? = null,
    val archived: Boolean = false,
    val position: Double = 0.0,
)

@Serializable data class SleepRoutine(val id: String, val name: String, val minutes: Int, val target: SleepTarget, val fadeMinutes: Int, val endMode: SleepEndMode = SleepEndMode.DURATION, val clockTime: String? = null, val sceneId: String? = null, val enabled: Boolean = true, val updatedAt: Long)
@Serializable data class SleepSession(
    val id: String, val startedAt: Long, val targetEndAt: Long, val actualEndAt: Long? = null,
    val target: SleepTarget, val fadeMinutes: Int, val status: SessionStatus, val routineId: String? = null,
    val volumeSnapshotCaptured: Boolean = false, val masterVolumeSnapshot: Float = 1f,
    val ambientVolumeSnapshot: Float = 1f, val radioVolumeSnapshot: Float = .8f,
)

@Serializable data class Notebook(val id: String, val title: String, val updatedAt: Long, val position: Double = 0.0, val accent: Long = 0xFF7F8C87, val iconKey: String = "bookOpen")
@Serializable data class NoteBlock(val id: String, val noteId: String, val kind: NoteBlockKind, val text: String = "", val checked: Boolean = false, val assetId: String? = null, val linkedNoteId: String? = null, val position: Double, val revision: Long = 0L)
@Serializable data class Note(val id: String, val notebookId: String?, val title: String, val body: String, val archived: Boolean, val deletedAt: Long?, val updatedAt: Long, val createdAt: Long = updatedAt, val pinned: Boolean = false, val tags: Set<String> = emptySet(), val originalNotebookId: String? = null, val position: Double = 0.0)
@Serializable data class Attachment(val id: String, val noteId: String, val mimeType: String, val localUri: String, val remotePath: String?, val updatedAt: Long, val kind: AttachmentKind = AttachmentKind.FILE, val displayName: String = "", val sizeBytes: Long = 0L, val durationSeconds: Long? = null, val width: Int? = null, val height: Int? = null, val thumbnailUri: String? = null, val checksum: String? = null, val uploadState: SyncState = SyncState.PENDING)

@Serializable data class PlaybackEvent(
    val id: String,
    val kind: PlaybackKind,
    val sourceId: String,
    val startedAt: Long,
    val endedAt: Long?,
    val activeSeconds: Long,
    val completed: Boolean,
    /** Track-level attribution for radio/local playback (e.g. generated track id). */
    val trackId: String? = null,
    /** Opaque source-kind discriminator stored with the event (e.g. GENERATED/CUSTOM). */
    val sourceKind: String? = null,
    /** Actual milliseconds listened for this event (may differ from wall-clock activeSeconds). */
    val listenedMs: Long = 0,
    /** Why playback ended: natural/stopped/failed/skipped. */
    val completionReason: String? = null,
)
@Serializable data class SyncRequest(val id: Long, val entityType: String, val entityId: String, val operation: SyncOperation, val payload: String?, val attempts: Int)

interface SoundRepository {
    fun observeSounds(): Flow<List<Sound>>
    fun observePresets(): Flow<List<Preset>>
    fun observeStations(): Flow<List<RadioStation>>
    fun observePlayback(): Flow<PlaybackSnapshot?>
    suspend fun setFavorite(soundId: String, favorite: Boolean)
    suspend fun savePreset(preset: Preset)
    suspend fun deletePreset(id: String)
    suspend fun saveStation(station: RadioStation)
    suspend fun deleteStation(id: String)
    suspend fun savePlayback(snapshot: PlaybackSnapshot)
}

interface ProductivityRepository {
    fun observeTodos(): Flow<List<Todo>>
    fun observePlans(): Flow<List<Plan>>
    fun observeHabits(): Flow<List<Habit>>
    fun observeHabitChecks(): Flow<List<HabitCheck>>
    fun observeCountdowns(): Flow<List<Countdown>>
    fun observeActiveSession(): Flow<FocusSession?>
    fun observeFocusSessions(): Flow<List<FocusSession>>
    fun observeSleepRoutines(): Flow<List<SleepRoutine>>
    fun observeSleepSessions(): Flow<List<SleepSession>>
    suspend fun saveTodo(todo: Todo)
    suspend fun deleteTodo(id: String)
    suspend fun savePlan(plan: Plan)
    suspend fun deletePlan(id: String)
    suspend fun saveHabit(habit: Habit)
    suspend fun saveHabitCheck(check: HabitCheck)
    suspend fun deleteHabitCheck(id: String)
    suspend fun deleteHabit(id: String)
    suspend fun saveCountdown(countdown: Countdown)
    suspend fun deleteCountdown(id: String)
    suspend fun saveSession(session: FocusSession)
    suspend fun saveSleepRoutine(routine: SleepRoutine)
    suspend fun deleteSleepRoutine(id: String)
    suspend fun saveSleepSession(session: SleepSession)
}

interface NotesRepository {
    fun observeNotebooks(): Flow<List<Notebook>>
    fun observeNotes(includeDeleted: Boolean = false): Flow<List<Note>>
    fun observeBlocks(noteId: String): Flow<List<NoteBlock>>
    fun observeAttachments(noteId: String): Flow<List<Attachment>>
    suspend fun saveNotebook(notebook: Notebook)
    suspend fun deleteNotebook(id: String)
    suspend fun saveNote(note: Note)
    suspend fun saveBlocks(noteId: String, blocks: List<NoteBlock>)
    suspend fun saveAttachment(attachment: Attachment)
    suspend fun deleteAttachment(id: String)
    suspend fun moveToTrash(noteId: String, at: Long)
    suspend fun restore(noteId: String, at: Long)
    suspend fun deleteForever(noteId: String)
}

interface RecordsRepository {
    fun observeFocusSessions(): Flow<List<FocusSession>>
    fun observeSleepSessions(): Flow<List<SleepSession>>
    fun observePlaybackEvents(): Flow<List<PlaybackEvent>>
    suspend fun savePlaybackEvent(event: PlaybackEvent)
    suspend fun savePlaybackEvents(events: List<PlaybackEvent>) {
        events.forEach { savePlaybackEvent(it) }
    }
}
