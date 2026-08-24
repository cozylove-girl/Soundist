package com.soundist.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao interface SoundDao {
    @Query("SELECT * FROM sounds ORDER BY category,name") fun observeAll(): Flow<List<SoundEntity>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun seed(items: List<SoundEntity>)
    @Query("UPDATE sounds SET favorite=:favorite WHERE id=:id") suspend fun favorite(id: String, favorite: Boolean)
    @Query("SELECT COUNT(*) FROM sounds") suspend fun count(): Int
}

@Dao interface ListeningDao {
    @Query("SELECT * FROM presets ORDER BY updatedAt DESC") fun observePresets(): Flow<List<PresetEntity>>
    @Upsert suspend fun savePreset(value: PresetEntity)
    @Query("DELETE FROM presets WHERE id=:id") suspend fun deletePreset(id: String)
    @Query("UPDATE presets SET updatedAt=:position WHERE id=:id") suspend fun positionPreset(id: String, position: Long)
    @Query("SELECT * FROM radio_stations WHERE enabled=1 ORDER BY position,name") fun observeStations(): Flow<List<RadioEntity>>
    @Upsert suspend fun saveStation(value: RadioEntity)
    @Query("DELETE FROM radio_stations WHERE id=:id") suspend fun deleteStation(id: String)
    @Query("UPDATE radio_stations SET position=:position WHERE id=:id") suspend fun positionStation(id: String, position: Double)
    @Query("SELECT * FROM playback_snapshot WHERE `key`='current' LIMIT 1") fun observePlayback(): Flow<PlaybackEntity?>
    @Upsert suspend fun savePlayback(value: PlaybackEntity)
}

@Dao interface ProductivityDao {
    @Query("SELECT * FROM todos ORDER BY archived,completed,position,dueAt") fun observeTodos(): Flow<List<TodoEntity>>
    @Upsert suspend fun saveTodo(value: TodoEntity)
    @Query("DELETE FROM todos WHERE id=:id") suspend fun deleteTodo(id: String)
    @Query("SELECT * FROM plans ORDER BY archived,position,startAt") fun observePlans(): Flow<List<PlanEntity>>
    @Upsert suspend fun savePlan(value: PlanEntity)
    @Query("DELETE FROM plans WHERE id=:id") suspend fun deletePlan(id: String)
    @Query("SELECT * FROM habits ORDER BY archived,position,name") fun observeHabits(): Flow<List<HabitEntity>>
    @Upsert suspend fun saveHabit(value: HabitEntity)
    @Query("DELETE FROM habits WHERE id=:id") suspend fun deleteHabit(id: String)
    @Query("SELECT * FROM habit_checks ORDER BY checkedAt DESC") fun observeHabitChecks(): Flow<List<HabitCheckEntity>>
    @Upsert suspend fun saveHabitCheck(value: HabitCheckEntity)
    @Query("DELETE FROM habit_checks WHERE id=:id") suspend fun deleteHabitCheck(id: String)
    @Query("SELECT * FROM countdowns ORDER BY archived,position,targetAt") fun observeCountdowns(): Flow<List<CountdownEntity>>
    @Upsert suspend fun saveCountdown(value: CountdownEntity)
    @Query("DELETE FROM countdowns WHERE id=:id") suspend fun deleteCountdown(id: String)
    @Query("SELECT * FROM focus_sessions WHERE status IN ('RUNNING','PAUSED','REVIEW') ORDER BY startedAt DESC LIMIT 1") fun observeActiveSession(): Flow<FocusSessionEntity?>
    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC") fun observeFocusSessions(): Flow<List<FocusSessionEntity>>
    @Upsert suspend fun saveSession(value: FocusSessionEntity)
    @Query("SELECT * FROM sleep_routines ORDER BY updatedAt DESC") fun observeSleepRoutines(): Flow<List<SleepRoutineEntity>>
    @Upsert suspend fun saveSleepRoutine(value: SleepRoutineEntity)
    @Query("DELETE FROM sleep_routines WHERE id=:id") suspend fun deleteSleepRoutine(id: String)
    @Query("SELECT * FROM sleep_sessions ORDER BY startedAt DESC") fun observeSleepSessions(): Flow<List<SleepSessionEntity>>
    @Upsert suspend fun saveSleepSession(value: SleepSessionEntity)
}

@Dao interface NotesDao {
    @Query("SELECT * FROM notebooks ORDER BY position,title") fun observeNotebooks(): Flow<List<NotebookEntity>>
    @Upsert suspend fun saveNotebook(value: NotebookEntity)
    @Query("DELETE FROM notebooks WHERE id=:id") suspend fun deleteNotebook(id: String)
    @Query("SELECT * FROM notes WHERE (:includeDeleted OR deletedAt IS NULL) ORDER BY pinned DESC,updatedAt DESC") fun observeNotes(includeDeleted: Boolean): Flow<List<NoteEntity>>
    @Upsert suspend fun saveNote(value: NoteEntity)
    @Query("SELECT * FROM note_blocks WHERE noteId=:noteId ORDER BY position") fun observeBlocks(noteId: String): Flow<List<NoteBlockEntity>>
    @Query("DELETE FROM note_blocks WHERE noteId=:noteId") suspend fun deleteBlocks(noteId: String)
    @Upsert suspend fun saveBlocks(value: List<NoteBlockEntity>)
    @Query("SELECT * FROM attachments WHERE noteId=:noteId ORDER BY updatedAt") fun observeAttachments(noteId: String): Flow<List<AttachmentEntity>>
    @Upsert suspend fun saveAttachment(value: AttachmentEntity)
    @Query("DELETE FROM attachments WHERE id=:id") suspend fun deleteAttachment(id: String)
    @Query("UPDATE notes SET deletedAt=:at,updatedAt=:at WHERE id=:id") suspend fun trash(id: String, at: Long)
    @Query("UPDATE notes SET deletedAt=NULL,updatedAt=:at WHERE id=:id") suspend fun restore(id: String, at: Long)
    @Query("DELETE FROM notes WHERE id=:id") suspend fun deleteForever(id: String)
}

@Dao interface RecordsDao {
    @Query("SELECT * FROM playback_events ORDER BY startedAt DESC") fun observePlaybackEvents(): Flow<List<PlaybackEventEntity>>
    @Upsert suspend fun savePlaybackEvent(value: PlaybackEventEntity)
}

@Dao interface CustomRadioFileDao {
    @Query("SELECT * FROM custom_radio_files WHERE stationId=:stationId ORDER BY sortIndex,displayName") fun observeFilesByStation(stationId: String): Flow<List<CustomRadioFileEntity>>
    @Upsert suspend fun saveFile(value: CustomRadioFileEntity)
    @Query("DELETE FROM custom_radio_files WHERE id=:id") suspend fun deleteFile(id: String)
    @Query("UPDATE custom_radio_files SET sortIndex=:sortIndex WHERE id=:id") suspend fun positionFile(id: String, sortIndex: Int)
}

@Dao interface SyncDao {
    @Query("SELECT * FROM sync_queue WHERE state IN ('PENDING','FAILED') AND nextAttemptAt<=:now ORDER BY createdAt LIMIT :limit") suspend fun pending(now: Long, limit: Int = 50): List<SyncQueueEntity>
    @Upsert suspend fun upsert(item: SyncQueueEntity): Long
    @Query("UPDATE sync_queue SET state=:state,attempts=:attempts,nextAttemptAt=:next,lastError=:error WHERE id=:id") suspend fun update(id: Long, state: String, attempts: Int, next: Long, error: String?)
    @Query("DELETE FROM sync_queue WHERE id=:id") suspend fun delete(id: Long)
    @Query("DELETE FROM sync_queue WHERE operationId IN (:operationIds)") suspend fun acknowledge(operationIds: Set<String>)
    @Query("UPDATE sync_queue SET state='REJECTED',lastError=:code WHERE operationId=:operationId") suspend fun reject(operationId: String, code: String)
    @Upsert suspend fun saveConflicts(items: List<SyncConflictEntity>)
    @Query("SELECT * FROM sync_conflicts WHERE userId=:userId ORDER BY recordedAt DESC") suspend fun conflicts(userId: String): List<SyncConflictEntity>
    @Query("SELECT * FROM sync_cursors WHERE userId=:userId LIMIT 1") suspend fun cursor(userId: String): SyncCursorEntity?
    @Upsert suspend fun saveCursor(cursor: SyncCursorEntity)
    @Query("SELECT * FROM sync_revisions WHERE entityType=:entityType AND entityId=:entityId LIMIT 1") suspend fun revision(entityType: String, entityId: String): SyncRevisionEntity?
    @Upsert suspend fun saveRevision(revision: SyncRevisionEntity)
}

@Dao interface MaintenanceDao {
    @Query("SELECT * FROM presets") suspend fun presets(): List<PresetEntity>
    @Query("SELECT * FROM radio_stations") suspend fun radios(): List<RadioEntity>
    @Query("SELECT * FROM playback_snapshot") suspend fun playback(): List<PlaybackEntity>
    @Query("SELECT * FROM focus_sessions") suspend fun focusSessions(): List<FocusSessionEntity>
    @Query("SELECT * FROM todos") suspend fun todos(): List<TodoEntity>
    @Query("SELECT * FROM plans") suspend fun plans(): List<PlanEntity>
    @Query("SELECT * FROM habits") suspend fun habits(): List<HabitEntity>
    @Query("SELECT * FROM habit_checks") suspend fun habitChecks(): List<HabitCheckEntity>
    @Query("SELECT * FROM countdowns") suspend fun countdowns(): List<CountdownEntity>
    @Query("SELECT * FROM sleep_routines") suspend fun sleepRoutines(): List<SleepRoutineEntity>
    @Query("SELECT * FROM sleep_sessions") suspend fun sleepSessions(): List<SleepSessionEntity>
    @Query("SELECT * FROM notebooks") suspend fun notebooks(): List<NotebookEntity>
    @Query("SELECT * FROM notes") suspend fun notes(): List<NoteEntity>
    @Query("SELECT * FROM note_blocks") suspend fun noteBlocks(): List<NoteBlockEntity>
    @Query("SELECT * FROM attachments") suspend fun attachments(): List<AttachmentEntity>
    @Query("SELECT * FROM playback_events") suspend fun playbackEvents(): List<PlaybackEventEntity>
    @Upsert suspend fun upsertPresets(values: List<PresetEntity>)
    @Upsert suspend fun upsertRadios(values: List<RadioEntity>)
    @Upsert suspend fun upsertPlayback(values: List<PlaybackEntity>)
    @Upsert suspend fun upsertFocusSessions(values: List<FocusSessionEntity>)
    @Upsert suspend fun upsertTodos(values: List<TodoEntity>)
    @Upsert suspend fun upsertPlans(values: List<PlanEntity>)
    @Upsert suspend fun upsertHabits(values: List<HabitEntity>)
    @Upsert suspend fun upsertHabitChecks(values: List<HabitCheckEntity>)
    @Upsert suspend fun upsertCountdowns(values: List<CountdownEntity>)
    @Upsert suspend fun upsertSleepRoutines(values: List<SleepRoutineEntity>)
    @Upsert suspend fun upsertSleepSessions(values: List<SleepSessionEntity>)
    @Upsert suspend fun upsertNotebooks(values: List<NotebookEntity>)
    @Upsert suspend fun upsertNotes(values: List<NoteEntity>)
    @Upsert suspend fun upsertNoteBlocks(values: List<NoteBlockEntity>)
    @Upsert suspend fun upsertAttachments(values: List<AttachmentEntity>)
    @Upsert suspend fun upsertPlaybackEvents(values: List<PlaybackEventEntity>)
    @Query("DELETE FROM presets") suspend fun clearPresets()
    @Query("DELETE FROM radio_stations") suspend fun clearRadios()
    @Query("DELETE FROM playback_snapshot") suspend fun clearPlayback()
    @Query("DELETE FROM focus_sessions") suspend fun clearFocusSessions()
    @Query("DELETE FROM todos") suspend fun clearTodos()
    @Query("DELETE FROM plans") suspend fun clearPlans()
    @Query("DELETE FROM habits") suspend fun clearHabits()
    @Query("DELETE FROM habit_checks") suspend fun clearHabitChecks()
    @Query("DELETE FROM countdowns") suspend fun clearCountdowns()
    @Query("DELETE FROM sleep_routines") suspend fun clearSleepRoutines()
    @Query("DELETE FROM sleep_sessions") suspend fun clearSleepSessions()
    @Query("DELETE FROM notebooks") suspend fun clearNotebooks()
    @Query("DELETE FROM notes") suspend fun clearNotes()
    @Query("DELETE FROM note_blocks") suspend fun clearNoteBlocks()
    @Query("DELETE FROM attachments") suspend fun clearAttachments()
    @Query("DELETE FROM playback_events") suspend fun clearPlaybackEvents()
    @Query("DELETE FROM custom_radio_files") suspend fun clearCustomRadioFiles()
    @Query("DELETE FROM sync_queue") suspend fun clearQueue()
    @Query("DELETE FROM sync_conflicts") suspend fun clearConflicts()
    @Query("DELETE FROM sync_cursors") suspend fun clearCursors()
    @Query("DELETE FROM sync_revisions") suspend fun clearRevisions()
}
