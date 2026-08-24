package com.soundist.app

import com.soundist.core.database.AttachmentEntity
import com.soundist.core.database.CountdownEntity
import com.soundist.core.database.DatabaseSnapshot
import com.soundist.core.database.FocusSessionEntity
import com.soundist.core.database.HabitCheckEntity
import com.soundist.core.database.HabitEntity
import com.soundist.core.database.NoteBlockEntity
import com.soundist.core.database.NoteEntity
import com.soundist.core.database.NotebookEntity
import com.soundist.core.database.PlanEntity
import com.soundist.core.database.PlaybackEntity
import com.soundist.core.database.PlaybackEventEntity
import com.soundist.core.database.PresetEntity
import com.soundist.core.database.RadioEntity
import com.soundist.core.database.SleepRoutineEntity
import com.soundist.core.database.SleepSessionEntity
import com.soundist.core.database.TodoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseBackupRoundTripTest {

    private fun sampleSnapshot() = DatabaseSnapshot(
        exportedAtEpochMillis = 123456L,
        presets = listOf(PresetEntity("p1", "雨夜", "[{\"soundId\":\"river\",\"volume\":0.5}]", "阅读", true, 1L)),
        radios = listOf(RadioEntity("r1", "电台", "https://x", null, true, "描述", "GENERATED", "氛围", "阅读", 2.0, true, 1L, "{\"v\":1}")),
        playback = listOf(PlaybackEntity("current", "[]", "r1", true, 0.8f, 1f, 0.6f, 3, 1L)),
        focusSessions = listOf(FocusSessionEntity("fs1", "FOCUS", "COMPLETED", 1000L, null, null, 2000L, "自由专注", "TODO", "t1", "COUNTDOWN", 1500L, "复盘", "[]", null)),
        todos = listOf(TodoEntity("t1", "写作业", false, 3000L, 1L, false, true, "MEDIUM", null, 25, null, "", null, false, 0.0)),
        plans = listOf(PlanEntity("pl1", "计划", 1L, null, 1L, 90, 0, "里程碑", "说明", null, null, false, 0.0)),
        habits = listOf(HabitEntity("h1", "早睡", 7, 1L, "[1,2,3]", "MINUTES", 30, 5, null, null, false, 0.0)),
        habitChecks = listOf(HabitCheckEntity("hc1", "h1", 1L, 1)),
        countdowns = listOf(CountdownEntity("c1", "倒计时", 3000L, 1L, "说明", 30, 0, null, "里程碑", null, false, 0.0)),
        sleepRoutines = listOf(SleepRoutineEntity("sr1", "睡眠", 30, "ALL", 10, "DURATION", null, null, true, 1L)),
        sleepSessions = listOf(SleepSessionEntity("ss1", 1L, 1000L, null, "AMBIENT", 10, "COMPLETED", null)),
        notebooks = listOf(NotebookEntity("nb1", "笔记本", 1L, 0.0)),
        notes = listOf(NoteEntity("n1", "nb1", "标题", "正文", false, null, 1L, 1L, true, "[\"标签\"]", null, 0.0)),
        noteBlocks = listOf(NoteBlockEntity("nbk1", "n1", "CHECKLIST", "清单项", true, null, null, 0.0, 1L)),
        attachments = listOf(AttachmentEntity("a1", "n1", "image/jpeg", "/path", null, 1L, "IMAGE", "图片.jpg", 100L, null, null, null, null, null, "LOCAL")),
        playbackEvents = listOf(PlaybackEventEntity("pe1", "AMBIENT", "river", 1L, 2L, 60L, true, null, null)),
    )

    @Test
    fun roundTripPreservesEnumStrings() {
        val decoded = decodeDatabaseSnapshot(encodeDatabaseSnapshot(sampleSnapshot()))
        assertEquals("FOCUS", decoded.focusSessions[0].kind)
        assertEquals("COMPLETED", decoded.focusSessions[0].status)
        assertEquals("TODO", decoded.focusSessions[0].targetKind)
        assertEquals("COUNTDOWN", decoded.focusSessions[0].timerMode)
        assertEquals("MINUTES", decoded.habits[0].metric)
        assertEquals("MEDIUM", decoded.todos[0].priority)
        assertEquals("ALL", decoded.sleepRoutines[0].target)
        assertEquals("DURATION", decoded.sleepRoutines[0].endMode)
        assertEquals("AMBIENT", decoded.sleepSessions[0].target)
        assertEquals("COMPLETED", decoded.sleepSessions[0].status)
        assertEquals("GENERATED", decoded.radios[0].sourceKind)
        assertEquals("CHECKLIST", decoded.noteBlocks[0].kind)
        assertEquals("IMAGE", decoded.attachments[0].kind)
        assertEquals("PENDING", decoded.attachments[0].uploadState)
        assertEquals("AMBIENT", decoded.playbackEvents[0].kind)
        assertEquals("[\"标签\"]", decoded.notes[0].tagsJson)
        assertEquals("[{\"soundId\":\"river\",\"volume\":0.5}]", decoded.presets[0].tracksJson)
        assertEquals("自由专注", decoded.focusSessions[0].label)
    }

    @Test
    fun roundTripKeepsTextAndNumbers() {
        val decoded = decodeDatabaseSnapshot(encodeDatabaseSnapshot(sampleSnapshot()))
        assertEquals("写作业", decoded.todos[0].title)
        assertEquals(1500L, decoded.focusSessions[0].elapsedSeconds)
        assertEquals(0.8f, decoded.playback[0].masterVolume)
        assertEquals("current", decoded.playback[0].key)
        assertEquals(3, decoded.playback[0].radioTrackIndex)
        assertEquals(100L, decoded.attachments[0].sizeBytes)
        assertTrue(decoded.notes[0].pinned)
    }
}
