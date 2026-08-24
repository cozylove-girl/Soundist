package com.soundist.core.database

import androidx.room.withTransaction

data class DatabaseSnapshot(
    val exportedAtEpochMillis: Long,
    val presets: List<PresetEntity>, val radios: List<RadioEntity>, val playback: List<PlaybackEntity>,
    val focusSessions: List<FocusSessionEntity>, val todos: List<TodoEntity>, val plans: List<PlanEntity>,
    val habits: List<HabitEntity>, val habitChecks: List<HabitCheckEntity>, val countdowns: List<CountdownEntity>,
    val sleepRoutines: List<SleepRoutineEntity>, val sleepSessions: List<SleepSessionEntity>,
    val notebooks: List<NotebookEntity>, val notes: List<NoteEntity>, val noteBlocks: List<NoteBlockEntity>,
    val attachments: List<AttachmentEntity>, val playbackEvents: List<PlaybackEventEntity>,
)

class DatabaseMaintenance(private val db: SoundistDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    /** Consistent read snapshot for app-layer JSON encoding/export. Local file URIs remain private metadata. */
    suspend fun snapshot(): DatabaseSnapshot = db.withTransaction {
        val dao = db.maintenance()
        // 不备份"进行中/暂停/复盘"的活跃会话与睡眠定时：恢复一个几天前的"运行中"计时器会导致
        // 启动时按过期时间戳对账，触发异常。历史（已完成/已取消）记录才是有意义的备份内容。
        val historicalFocus = dao.focusSessions().filterNot { it.status in setOf("RUNNING", "PAUSED", "REVIEW") }
        val historicalSleep = dao.sleepSessions().filterNot { it.status in setOf("RUNNING", "PAUSED") }
        DatabaseSnapshot(clock(), dao.presets(), dao.radios(), dao.playback(), historicalFocus, dao.todos(), dao.plans(), dao.habits(), dao.habitChecks(), dao.countdowns(), dao.sleepRoutines(), historicalSleep, dao.notebooks(), dao.notes(), dao.noteBlocks(), dao.attachments(), dao.playbackEvents())
    }

    /** Deletes user data and sync metadata atomically. The immutable 84-sound catalog is retained. */
    suspend fun clearAllUserData() = db.withTransaction {
        val dao = db.maintenance()
        dao.clearNoteBlocks(); dao.clearAttachments(); dao.clearNotes(); dao.clearNotebooks()
        dao.clearHabitChecks(); dao.clearFocusSessions(); dao.clearSleepSessions(); dao.clearPlaybackEvents()
        dao.clearTodos(); dao.clearPlans(); dao.clearHabits(); dao.clearCountdowns(); dao.clearSleepRoutines()
        dao.clearPlayback(); dao.clearPresets(); dao.clearRadios()
        dao.clearQueue(); dao.clearConflicts(); dao.clearCursors(); dao.clearRevisions()
    }

    /** Replaces all user data with the given snapshot atomically. */
    suspend fun restore(snapshot: DatabaseSnapshot) = db.withTransaction {
        val dao = db.maintenance()
        dao.clearNoteBlocks(); dao.clearAttachments(); dao.clearNotes(); dao.clearNotebooks()
        dao.clearHabitChecks(); dao.clearFocusSessions(); dao.clearSleepSessions(); dao.clearPlaybackEvents()
        dao.clearTodos(); dao.clearPlans(); dao.clearHabits(); dao.clearCountdowns(); dao.clearSleepRoutines()
        dao.clearPlayback(); dao.clearPresets(); dao.clearRadios()
        dao.clearQueue(); dao.clearConflicts(); dao.clearCursors(); dao.clearRevisions()
        dao.upsertPresets(snapshot.presets)
        dao.upsertRadios(snapshot.radios)
        // Imported backups are untrusted input. Never restore a live timer or an
        // autoplaying audio snapshot from a different process/session.
        dao.upsertPlayback(snapshot.playback.map { it.copy(playing = false, radioId = null) })
        dao.upsertFocusSessions(snapshot.focusSessions.filterNot { it.status in setOf("RUNNING", "PAUSED", "REVIEW") })
        dao.upsertTodos(snapshot.todos)
        dao.upsertPlans(snapshot.plans)
        dao.upsertHabits(snapshot.habits)
        dao.upsertHabitChecks(snapshot.habitChecks)
        dao.upsertCountdowns(snapshot.countdowns)
        dao.upsertSleepRoutines(snapshot.sleepRoutines)
        dao.upsertSleepSessions(snapshot.sleepSessions.filterNot { it.status in setOf("RUNNING", "PAUSED") })
        dao.upsertNotebooks(snapshot.notebooks)
        dao.upsertNotes(snapshot.notes)
        dao.upsertNoteBlocks(snapshot.noteBlocks)
        dao.upsertAttachments(snapshot.attachments)
        dao.upsertPlaybackEvents(snapshot.playbackEvents)
    }
}
