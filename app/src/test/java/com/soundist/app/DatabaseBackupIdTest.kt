package com.soundist.app

import com.soundist.core.database.DatabaseSnapshot
import com.soundist.core.database.NoteEntity
import com.soundist.core.database.TodoEntity
import com.soundist.core.database.HabitEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class DatabaseBackupIdTest {
    private fun snapshotWith(noteId: String) = DatabaseSnapshot(
        exportedAtEpochMillis = 1L,
        presets = emptyList(), radios = emptyList(), playback = emptyList(),
        focusSessions = emptyList(),
        todos = listOf(TodoEntity("todo-uuid", "标题", false, null, 1L, false, true, "MEDIUM", null, 25, null, "", null, false, 0.0)),
        plans = emptyList(),
        habits = listOf(HabitEntity("habit-uuid", "习惯", 7, 1L, "[1]", "MINUTES", 30, 0, null, null, false, 0.0)),
        habitChecks = emptyList(), countdowns = emptyList(),
        sleepRoutines = emptyList(), sleepSessions = emptyList(), notebooks = emptyList(),
        notes = listOf(NoteEntity(noteId, null, "标题", "正文", false, null, 1L, 1L, false, "[]", null, 0.0)),
        noteBlocks = emptyList(), attachments = emptyList(), playbackEvents = emptyList(),
    )

    @Test
    fun idsRoundTrip() {
        val decoded = decodeDatabaseSnapshot(encodeDatabaseSnapshot(snapshotWith("note-uuid-1")))
        assertEquals("note-uuid-1", decoded.notes[0].id)
        assertEquals("todo-uuid", decoded.todos[0].id)
        assertEquals("habit-uuid", decoded.habits[0].id)
    }

    @Test
    fun blankIdIsRejected() {
        try {
            decodeDatabaseSnapshot(encodeDatabaseSnapshot(snapshotWith("   ")))
            throw AssertionError("应该因为空 ID 抛异常")
        } catch (e: IllegalArgumentException) {
            assertEquals("笔记 中存在空 ID", e.message)
        }
    }
}
