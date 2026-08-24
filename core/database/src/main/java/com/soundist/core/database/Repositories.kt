package com.soundist.core.database

import androidx.room.withTransaction
import com.soundist.core.model.Attachment
import com.soundist.core.model.AttachmentKind
import com.soundist.core.model.Countdown
import com.soundist.core.model.CustomRadioFile
import com.soundist.core.model.FocusSession
import com.soundist.core.model.FocusTargetKind
import com.soundist.core.model.FocusTimerMode
import com.soundist.core.model.Habit
import com.soundist.core.model.HabitCheck
import com.soundist.core.model.HabitMetric
import com.soundist.core.model.MixTrack
import com.soundist.core.model.Note
import com.soundist.core.model.NoteBlock
import com.soundist.core.model.NoteBlockKind
import com.soundist.core.model.Notebook
import com.soundist.core.model.NotesRepository
import com.soundist.core.model.Plan
import com.soundist.core.model.PlaybackEvent
import com.soundist.core.model.PlaybackKind
import com.soundist.core.model.PlaybackSnapshot
import com.soundist.core.model.Preset
import com.soundist.core.model.Priority
import com.soundist.core.model.ProductivityRepository
import com.soundist.core.model.RadioSourceKind
import com.soundist.core.model.RadioStation
import com.soundist.core.model.RecordsRepository
import com.soundist.core.model.SessionKind
import com.soundist.core.model.SessionStatus
import com.soundist.core.model.SleepEndMode
import com.soundist.core.model.SleepRoutine
import com.soundist.core.model.SleepSession
import com.soundist.core.model.SleepTarget
import com.soundist.core.model.Sound
import com.soundist.core.model.SoundCatalog
import com.soundist.core.model.SoundCategory
import com.soundist.core.model.SoundRepository
import com.soundist.core.model.SyncState
import com.soundist.core.model.Todo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

internal val codec = Json { ignoreUnknownKeys = true; encodeDefaults = true }
private val tracksCodec = ListSerializer(MixTrack.serializer())
private val stringSetCodec = SetSerializer(String.serializer())
private val intSetCodec = SetSerializer(Int.serializer())

class RoomSoundRepository(private val db: SoundistDatabase) : SoundRepository {
    override fun observeSounds() = db.sounds().observeAll().map { rows -> rows.map { Sound(it.id, it.name, SoundCategory.valueOf(it.category), it.assetUri, it.defaultVolume, it.favorite) } }
    override fun observePresets(): Flow<List<Preset>> = db.listening().observePresets().map { rows -> rows.map { Preset(it.id, it.name, codec.decodeFromString(tracksCodec, it.tracksJson), it.updatedAt, it.purposeLabel, it.builtIn) } }
    override fun observeStations(): Flow<List<RadioStation>> = db.listening().observeStations().map { rows -> rows.map(::radioStation) }
    override fun observePlayback(): Flow<PlaybackSnapshot?> = db.listening().observePlayback().map { row -> row?.let { PlaybackSnapshot(codec.decodeFromString(tracksCodec, it.tracksJson), it.radioId, it.playing, it.updatedAt, it.masterVolume, it.ambientVolume, it.radioVolume, it.radioTrackIndex) } }
    override suspend fun setFavorite(soundId: String, favorite: Boolean) = db.sounds().favorite(soundId, favorite)
    override suspend fun savePreset(preset: Preset) = db.listening().savePreset(PresetEntity(preset.id, preset.name, codec.encodeToString(tracksCodec, preset.tracks), preset.purposeLabel, preset.builtIn, preset.updatedAt))
    override suspend fun deletePreset(id: String) = db.listening().deletePreset(id)
    override suspend fun saveStation(station: RadioStation) = db.listening().saveStation(station.entity())
    override suspend fun deleteStation(id: String) = db.listening().deleteStation(id)
    override suspend fun savePlayback(snapshot: PlaybackSnapshot) = db.listening().savePlayback(PlaybackEntity(tracksJson = codec.encodeToString(tracksCodec, snapshot.tracks), radioId = snapshot.radioId, playing = snapshot.playing, masterVolume = snapshot.masterVolume, ambientVolume = snapshot.ambientVolume, radioVolume = snapshot.radioVolume, radioTrackIndex = snapshot.radioTrackIndex, updatedAt = snapshot.updatedAt))
    suspend fun reorderPresets(ids: List<String>) = db.withTransaction { ids.forEachIndexed { index, id -> db.listening().positionPreset(id, ids.size.toLong() - index) } }
    suspend fun reorderStations(ids: List<String>) = db.withTransaction { ids.forEachIndexed { index, id -> db.listening().positionStation(id, index.toDouble()) } }
    suspend fun seedCatalogue() {
        if (db.sounds().count() == 0) db.sounds().seed(SoundCatalog.sounds.map { SoundEntity(it.id, it.name, it.category.name, it.assetUri, it.defaultVolume) })
        // H1：前端 ALL_SOUNDS 全 volume=0 active=false，全新安装不应有任何活跃声源。
        // 不再播种 6 轨 PlaybackSnapshot——无播放行时 UI 走默认空混音（0 声源）。
    }
}

class RoomProductivityRepository(private val db: SoundistDatabase) : ProductivityRepository {
    override fun observeTodos() = db.productivity().observeTodos().map { rows -> rows.map(::todo) }
    override fun observePlans() = db.productivity().observePlans().map { rows -> rows.map(::plan) }
    override fun observeHabits() = db.productivity().observeHabits().map { rows -> rows.map(::habit) }
    override fun observeHabitChecks() = db.productivity().observeHabitChecks().map { rows -> rows.map { HabitCheck(it.id, it.habitId, it.checkedAt, it.value) } }
    override fun observeCountdowns() = db.productivity().observeCountdowns().map { rows -> rows.map(::countdown) }
    override fun observeActiveSession() = db.productivity().observeActiveSession().map { it?.let(::focusSession) }
    override fun observeFocusSessions() = db.productivity().observeFocusSessions().map { rows -> rows.map(::focusSession) }
    override fun observeSleepRoutines() = db.productivity().observeSleepRoutines().map { rows -> rows.map(::sleepRoutine) }
    override fun observeSleepSessions() = db.productivity().observeSleepSessions().map { rows -> rows.map(::sleepSession) }
    override suspend fun saveTodo(todo: Todo) = db.productivity().saveTodo(todo.entity())
    override suspend fun deleteTodo(id: String) = db.productivity().deleteTodo(id)
    override suspend fun savePlan(plan: Plan) = db.productivity().savePlan(plan.entity())
    override suspend fun deletePlan(id: String) = db.productivity().deletePlan(id)
    override suspend fun saveHabit(habit: Habit) = db.productivity().saveHabit(habit.entity())
    override suspend fun saveHabitCheck(check: HabitCheck) = db.productivity().saveHabitCheck(HabitCheckEntity(check.id, check.habitId, check.checkedAt, check.value))
    override suspend fun deleteHabitCheck(id: String) = db.productivity().deleteHabitCheck(id)
    override suspend fun deleteHabit(id: String) = db.productivity().deleteHabit(id)
    override suspend fun saveCountdown(countdown: Countdown) = db.productivity().saveCountdown(countdown.entity())
    override suspend fun deleteCountdown(id: String) = db.productivity().deleteCountdown(id)
    override suspend fun saveSession(session: FocusSession) = db.productivity().saveSession(session.entity())
    override suspend fun saveSleepRoutine(routine: SleepRoutine) = db.productivity().saveSleepRoutine(routine.entity())
    override suspend fun deleteSleepRoutine(id: String) = db.productivity().deleteSleepRoutine(id)
    override suspend fun saveSleepSession(session: SleepSession) = db.productivity().saveSleepSession(session.entity())
}

class RoomNotesRepository(private val db: SoundistDatabase) : NotesRepository {
    override fun observeNotebooks() = db.notes().observeNotebooks().map { rows -> rows.map { Notebook(it.id, it.title, it.updatedAt, it.position, it.accent, it.iconKey) } }
    override fun observeNotes(includeDeleted: Boolean) = db.notes().observeNotes(includeDeleted).map { rows -> rows.map(::note) }
    override fun observeBlocks(noteId: String) = db.notes().observeBlocks(noteId).map { rows -> rows.map(::block) }
    override fun observeAttachments(noteId: String) = db.notes().observeAttachments(noteId).map { rows -> rows.map(::attachment) }
    override suspend fun saveNotebook(notebook: Notebook) = db.notes().saveNotebook(NotebookEntity(notebook.id, notebook.title, notebook.updatedAt, notebook.position, notebook.accent, notebook.iconKey))
    override suspend fun deleteNotebook(id: String) = db.notes().deleteNotebook(id)
    override suspend fun saveNote(note: Note) = db.notes().saveNote(note.entity())
    override suspend fun saveBlocks(noteId: String, blocks: List<NoteBlock>) = db.withTransaction { db.notes().deleteBlocks(noteId); if (blocks.isNotEmpty()) db.notes().saveBlocks(blocks.map { it.entity() }) }
    override suspend fun saveAttachment(attachment: Attachment) = db.notes().saveAttachment(attachment.entity())
    override suspend fun deleteAttachment(id: String) = db.notes().deleteAttachment(id)
    override suspend fun moveToTrash(noteId: String, at: Long) = db.notes().trash(noteId, at)
    override suspend fun restore(noteId: String, at: Long) = db.notes().restore(noteId, at)
    override suspend fun deleteForever(noteId: String) = db.notes().deleteForever(noteId)
    suspend fun seedNotes() {
        // Remove only untouched legacy samples. Matching both id and content avoids deleting a
        // user's edited copy of an old sample note during an upgrade.
        val legacySamples = mapOf(
            "note-1" to ("夜雨森林调音记录" to "小雨保持在 65%，篝火降低到 42%，鸟鸣只留在远处。适合晚间整理资料。"),
            "note-2" to ("雨窗书案的声音层次" to "前景使用轻雨，中景加入键盘，远景保留很轻的咖啡馆空间声。"),
            "note-3" to ("移动端原型反馈" to "优先检查睡眠定时、笔记详情和专注页层级。主页动效保持克制，不覆盖粒子系统。"),
            "note-4" to ("今天的专注复盘" to "完成了两次自由专注。第二次使用正计时更自然，休息阶段需要更明确的结束反馈。"),
            "note-5" to ("英语听力精听" to "完成第一遍盲听，下一次重点记录连读和弱读。"),
            "note-6" to ("线性代数错题" to "复习特征值和相似对角化，重新整理三道易错题。"),
        )
        db.notes().observeNotes(true).first().forEach { row ->
            val sample = legacySamples[row.id]
            if (sample != null && row.title == sample.first && row.body == sample.second) {
                db.notes().deleteForever(row.id)
            }
        }
        if (db.notes().observeNotes(true).first().isNotEmpty()) return
        val now = System.currentTimeMillis()
        db.notes().saveNote(
            Note(
                id = "soundist-getting-started",
                notebookId = null,
                title = "从这里开始",
                body = "把想法写下来，也可以插入清单、图片、文件、录音或手写内容。需要时，将笔记连接到一次专注或一段声场；不连接也没关系，它始终是一篇独立、完整的笔记。",
                archived = false,
                deletedAt = null,
                updatedAt = now,
                createdAt = now,
                pinned = true,
                tags = emptySet(),
                originalNotebookId = null,
                position = 0.0,
            ).entity(),
        )
    }
}

class RoomRecordsRepository(private val db: SoundistDatabase) : RecordsRepository {
    override fun observeFocusSessions() = db.productivity().observeFocusSessions().map { rows -> rows.map(::focusSession) }
    override fun observeSleepSessions() = db.productivity().observeSleepSessions().map { rows -> rows.map(::sleepSession) }
    override fun observePlaybackEvents() = db.records().observePlaybackEvents().map { rows -> rows.map { PlaybackEvent(it.id, PlaybackKind.valueOf(it.kind), it.sourceId, it.startedAt, it.endedAt, it.activeSeconds, it.completed, it.trackId, it.sourceKind, it.listenedMs, it.completionReason) } }
    override suspend fun savePlaybackEvent(event: PlaybackEvent) = db.records().savePlaybackEvent(PlaybackEventEntity(event.id, event.kind.name, event.sourceId, event.startedAt, event.endedAt, event.activeSeconds, event.completed, event.trackId, event.sourceKind, event.listenedMs, event.completionReason))
}

/** Custom radio channel audio-file persistence bridge (user-imported local audio for custom stations). */
class RoomOfflineContentRepository(private val db: SoundistDatabase) {
    fun observeCustomFiles(stationId: String): Flow<List<CustomRadioFile>> = db.customRadioFiles().observeFilesByStation(stationId).map { rows -> rows.map(::customRadioFile) }
    suspend fun saveCustomFile(file: CustomRadioFile) = db.customRadioFiles().saveFile(file.entity())
    suspend fun deleteCustomFile(id: String) = db.customRadioFiles().deleteFile(id)
    suspend fun positionCustomFile(id: String, sortIndex: Int) = db.customRadioFiles().positionFile(id, sortIndex)
}

private fun todo(row: TodoEntity) = Todo(row.id, row.title, row.completed, row.dueAt, row.updatedAt, row.longTerm, row.allDay, Priority.valueOf(row.priority), row.reminderAt, row.estimatedMinutes, row.planId, row.note, row.defaultSceneId, row.archived, row.position)
private fun Todo.entity() = TodoEntity(id, title, completed, dueAt, updatedAt, longTerm, allDay, priority.name, reminderAt, estimatedMinutes, planId, note, defaultSceneId, archived, position)
private fun plan(row: PlanEntity) = Plan(row.id, row.title, row.startAt, row.endAt, row.updatedAt, row.targetMinutes, row.accumulatedMinutes, row.milestone, row.description, row.reminderAt, row.defaultSceneId, row.archived, row.position)
private fun Plan.entity() = PlanEntity(id, title, startAt, endAt, updatedAt, targetMinutes, accumulatedMinutes, milestone, description, reminderAt, defaultSceneId, archived, position)
private fun habit(row: HabitEntity) = Habit(row.id, row.name, row.targetPerWeek, row.updatedAt, codec.decodeFromString(intSetCodec, row.weekdaysJson), HabitMetric.valueOf(row.metric), row.dailyTarget, row.streak, row.reminderAt, row.defaultSceneId, row.archived, row.position, row.reminderHour, row.reminderMinute)
private fun Habit.entity() = HabitEntity(id, name, targetPerWeek, updatedAt, codec.encodeToString(intSetCodec, weekdays), metric.name, dailyTarget, streak, reminderAt, defaultSceneId, archived, position, reminderHour, reminderMinute)
private fun countdown(row: CountdownEntity) = Countdown(row.id, row.title, row.targetAt, row.updatedAt, row.note, row.dailyMinutes, row.investedMinutes, row.planId, row.milestone, row.reminderAt, row.archived, row.position)
private fun Countdown.entity() = CountdownEntity(id, title, targetAt, updatedAt, note, dailyMinutes, investedMinutes, planId, milestone, reminderAt, archived, position)
private fun focusSession(row: FocusSessionEntity) = FocusSession(row.id, SessionKind.valueOf(row.kind), SessionStatus.valueOf(row.status), row.startedAt, row.targetEndAt, row.pausedAt, row.completedAt, row.label, FocusTargetKind.valueOf(row.targetKind), row.targetId, FocusTimerMode.valueOf(row.timerMode), row.elapsedSeconds, row.review, codec.decodeFromString(tracksCodec, row.soundSnapshotJson), row.radioSnapshotId, row.phase, row.focusMinutes, row.breakMinutes, row.cycleRound, row.cycleRounds, row.longBreakMinutes, row.autoBreak, row.autoFocus, row.completionMinutes, row.ambientMode, row.audioSource)
private fun FocusSession.entity() = FocusSessionEntity(id, kind.name, status.name, startedAt, targetEndAt, pausedAt, completedAt, label, targetKind.name, targetId, timerMode.name, elapsedSeconds, review, codec.encodeToString(tracksCodec, soundSnapshot), radioSnapshotId, phase, focusMinutes, breakMinutes, cycleRound, cycleRounds, longBreakMinutes, autoBreak, autoFocus, completionMinutes, ambientMode, audioSource)
private fun sleepRoutine(row: SleepRoutineEntity) = SleepRoutine(row.id, row.name, row.minutes, SleepTarget.valueOf(row.target), row.fadeMinutes, SleepEndMode.valueOf(row.endMode), row.clockTime, row.sceneId, row.enabled, row.updatedAt)
private fun SleepRoutine.entity() = SleepRoutineEntity(id, name, minutes, target.name, fadeMinutes, endMode.name, clockTime, sceneId, enabled, updatedAt)
private fun sleepSession(row: SleepSessionEntity) = SleepSession(row.id, row.startedAt, row.targetEndAt, row.actualEndAt, SleepTarget.valueOf(row.target), row.fadeMinutes, SessionStatus.valueOf(row.status), row.routineId, row.volumeSnapshotCaptured, row.masterVolumeSnapshot, row.ambientVolumeSnapshot, row.radioVolumeSnapshot)
private fun SleepSession.entity() = SleepSessionEntity(id, startedAt, targetEndAt, actualEndAt, target.name, fadeMinutes, status.name, routineId, volumeSnapshotCaptured, masterVolumeSnapshot, ambientVolumeSnapshot, radioVolumeSnapshot)
private fun note(row: NoteEntity) = Note(row.id, row.notebookId, row.title, row.body, row.archived, row.deletedAt, row.updatedAt, row.createdAt, row.pinned, codec.decodeFromString(stringSetCodec, row.tagsJson), row.originalNotebookId, row.position)
private fun Note.entity() = NoteEntity(id, notebookId, title, body, archived, deletedAt, updatedAt, createdAt, pinned, codec.encodeToString(stringSetCodec, tags), originalNotebookId, position)
private fun block(row: NoteBlockEntity) = NoteBlock(row.id, row.noteId, NoteBlockKind.valueOf(row.kind), row.text, row.checked, row.assetId, row.linkedNoteId, row.position, row.revision)
private fun NoteBlock.entity() = NoteBlockEntity(id, noteId, kind.name, text, checked, assetId, linkedNoteId, position, revision)
private fun attachment(row: AttachmentEntity) = Attachment(
    row.id, row.noteId, row.mimeType, row.localUri, row.remotePath, row.updatedAt,
    runCatching { AttachmentKind.valueOf(row.kind) }.getOrDefault(AttachmentKind.FILE),
    row.displayName, row.sizeBytes, row.durationSeconds, row.width, row.height, row.thumbnailUri, row.checksum,
    runCatching { SyncState.valueOf(row.uploadState) }.getOrDefault(SyncState.PENDING),
)
private fun Attachment.entity() = AttachmentEntity(id, noteId, mimeType, localUri, remotePath, updatedAt, kind.name, displayName, sizeBytes, durationSeconds, width, height, thumbnailUri, checksum, uploadState.name)
internal fun radioStation(row: RadioEntity) = RadioStation(row.id, row.name, row.streamUri, row.artworkUri, row.favorite, row.description, RadioSourceKind.valueOf(row.sourceKind), row.genre, row.purpose, row.position, row.enabled, row.updatedAt, row.payloadJson)
internal fun RadioStation.entity() = RadioEntity(id, name, streamUri, artworkUri, isFavorite, description, sourceKind.name, genre, purpose, position, enabled, updatedAt, payloadJson)
private fun customRadioFile(row: CustomRadioFileEntity) = CustomRadioFile(row.id, row.stationId, row.displayName, row.privatePath, row.mimeType, row.sizeBytes, row.durationMs, row.sha256, row.sortIndex)
private fun CustomRadioFile.entity() = CustomRadioFileEntity(id, stationId, displayName, privatePath, mimeType, sizeBytes, durationMs, sha256, sortIndex)
