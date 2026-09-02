package com.soundist.feature.records

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class DefaultRecordsEventAggregatorTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val aggregator = DefaultRecordsEventAggregator(zone)
    private val now = LocalDateTime.of(2026, 8, 13, 20, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun pieHasExactlyTodoAndFreeSlices() {
        val events = RecordsEvents(focus = listOf(
            focus("a", FocusTargetKind.TODO, "阅读", 30),
            focus("b", FocusTargetKind.FREE, "任意名称", 20),
            focus("c", FocusTargetKind.HABIT, "背单词", 40),
        ))

        val result = aggregator.aggregate(events, RecordsQuery(range = StatsRange.TODAY), now)

        assertEquals(listOf("待办专注", "自由专注"), result.pie.map { it.label })
        assertEquals(30, result.todoMinutes)
        assertEquals(20, result.freeMinutes)
        assertEquals(50, result.pie.sumOf { it.minutes })
        assertEquals(90, result.totalMinutes)
    }

    @Test fun emptyEventsStayEmptyInsteadOfReturningDemoData() {
        val result = aggregator.aggregate(RecordsEvents(), RecordsQuery(range = StatsRange.LAST_7_DAYS), now)

        assertTrue(result.selectedFocus.isEmpty())
        assertTrue(result.pie.isEmpty())
        assertTrue(result.sounds.isEmpty())
        assertEquals(0, result.totalMinutes)
    }

    @Test fun soundAndTargetRelationsComeFromActualSharedEvents() {
        val focus = focus("session", FocusTargetKind.TODO, "写报告", 45)
        val result = aggregator.aggregate(
            RecordsEvents(
                focus = listOf(focus),
                sounds = listOf(SoundUsageEvent("sound", "rain", "小雨", "雨声", focus.startedAt, 45, focus.id)),
            ),
            RecordsQuery(range = StatsRange.TODAY),
            now,
        )

        assertEquals("小雨", result.targets.single().dominantSound)
        assertEquals(45, result.sounds.single().minutes)
    }

    @Test fun zeroMinuteEventsDoNotCreateEmptyPieSlices() {
        val result = aggregator.aggregate(
            RecordsEvents(focus = listOf(
                focus("todo", FocusTargetKind.TODO, "写作", 0),
                focus("free", FocusTargetKind.FREE, "任意", 20),
            )),
            RecordsQuery(range = StatsRange.TODAY),
            now,
        )

        assertEquals(listOf("自由专注"), result.pie.map { it.label })
        assertEquals(100f, result.pie.single().percentage)
    }

    @Test fun rangeExcludesFutureAndOlderEvents() {
        val yesterday = now - 24 * 60 * 60 * 1000L
        val tomorrow = now + 24 * 60 * 60 * 1000L
        val events = RecordsEvents(focus = listOf(
            focusAt("today", FocusTargetKind.TODO, "今日", 25, now - 60_000),
            focusAt("yesterday", FocusTargetKind.TODO, "昨日", 30, yesterday),
            focusAt("future", FocusTargetKind.FREE, "未来", 40, tomorrow),
        ))

        val result = aggregator.aggregate(events, RecordsQuery(range = StatsRange.TODAY), now)

        assertEquals(listOf("today"), result.selectedFocus.map { it.id })
        assertEquals(25, result.totalMinutes)
    }

    @Test fun recordsRootUsesOneTaggedLazyColumnForWholePageScrolling() {
        assertEquals("LazyColumn:$RECORDS_PAGE_SCROLL_TEST_TAG", recordsPageScrollSemantics)
        assertTrue(RECORDS_PAGE_BOTTOM_INSET_DP >= 104)
    }

    @Test fun pieLabelsStaySeparatedOnEachSideEvenForTinySlices() {
        val placements = resolvePieLabelPlacements(listOf(1f, 1f, 48f, 50f), height = 300f, minimumGap = 44f)

        assertEquals(4, placements.size)
        placements.groupBy { it.onRight }.values.forEach { side ->
            val positions = side.map { it.y }.sorted()
            positions.zipWithNext().forEach { (first, second) -> assertTrue(second - first >= 43.9f) }
            assertTrue(positions.all { it in 0f..300f })
        }
    }

    @Test fun trendTapSelectsNearestBoundedDataPoint() {
        assertEquals(0, nearestTrendPointIndex(-20f, width = 300f, count = 7, plotLeft = 30f, plotRight = 290f))
        assertEquals(3, nearestTrendPointIndex(160f, width = 300f, count = 7, plotLeft = 30f, plotRight = 290f))
        assertEquals(6, nearestTrendPointIndex(400f, width = 300f, count = 7, plotLeft = 30f, plotRight = 290f))
        assertNull(nearestTrendPointIndex(10f, width = 300f, count = 0))
    }

    @Test fun heatmapDescriptionIncludesExactDateAndMinutes() {
        val date = java.time.LocalDate.of(2026, 8, 13)

        assertEquals("2026年8月13日，使用45分钟", heatmapDayDescription(date, 45))
        assertEquals("2026年8月13日，无记录", heatmapDayDescription(date, 0))
    }

    @Test fun repositoryExposesLoadingThenErrorAndSupportsRetry() {
        val failing = object : RecordsEventSource {
            override val events: Flow<RecordsEvents> = flow { throw IllegalStateException("数据库不可用") }
        }
        val repository = AggregatingRecordsRepository(failing, aggregator, Clock.fixed(Instant.ofEpochMilli(now), zone))
        try {
            repeat(100) { if (repository.state.value.loadStatus == RecordsLoadStatus.ERROR) return@repeat else Thread.sleep(5) }

            assertEquals(RecordsLoadStatus.ERROR, repository.state.value.loadStatus)
            assertEquals("数据库不可用", repository.state.value.loadError)
            repository.retry()
        } finally { repository.close() }
    }

    @Test fun radioRhythmUsesRealWeekdayMinutes() {
        val monday = LocalDateTime.of(2026, 8, 10, 9, 0).atZone(zone).toInstant().toEpochMilli()
        val thursday = LocalDateTime.of(2026, 8, 13, 21, 0).atZone(zone).toInstant().toEpochMilli()
        val result = aggregator.aggregate(
            RecordsEvents(radios = listOf(
                RadioUsageEvent("m", "r", "夜航", "氛围", monday, 20),
                RadioUsageEvent("t", "r", "夜航", "氛围", thursday, 35),
            )),
            RecordsQuery(range = StatsRange.LAST_7_DAYS),
            now,
        )

        assertEquals(listOf(20, 0, 0, 35, 0, 0, 0), result.radioWeekdayMinutes)
    }

    @Test fun soundCompletionRelationsOnlyUseActuallyLinkedFocusEvents() {
        val completed = focus("completed", FocusTargetKind.TODO, "写报告", 45)
        val interrupted = focus("interrupted", FocusTargetKind.FREE, "自由专注", 20).copy(status = SessionStatus.INTERRUPTED)
        val result = aggregator.aggregate(
            RecordsEvents(
                focus = listOf(completed, interrupted),
                sounds = listOf(
                    SoundUsageEvent("a", "rain", "小雨", "雨声", completed.startedAt, 45, completed.id),
                    SoundUsageEvent("b", "rain", "小雨", "雨声", interrupted.startedAt, 20, interrupted.id),
                    SoundUsageEvent("orphan", "rain", "小雨", "雨声", completed.startedAt, 99, "missing"),
                ),
            ),
            RecordsQuery(range = StatsRange.TODAY),
            now,
        )

        val relation = result.soundRelations.single()
        assertEquals(2, relation.sessions)
        assertEquals(2, relation.targetCount)
        assertEquals(65, relation.minutes)
        assertEquals(50, relation.completion)
    }

    @Test fun standaloneAmbientPlaybackUsesRealPlaybackDuration() {
        val result = aggregator.aggregate(
            RecordsEvents(sounds = listOf(SoundUsageEvent("sound", "river", "河流", "自然", now - 90_000, 1, durationSeconds = 90))),
            RecordsQuery(range = StatsRange.TODAY),
            now,
        )

        assertEquals(90L, result.ambientSeconds)
        assertEquals(90L, result.sounds.single().seconds)
        assertTrue(result.selectedFocus.isEmpty())
    }

    @Test fun sleepAndPlaybackRespectSelectedRange() {
        val old = now - 10 * 24 * 60 * 60 * 1000L
        val result = aggregator.aggregate(
            RecordsEvents(
                sounds = listOf(
                    SoundUsageEvent("today", "rain", "小雨", "雨声", now - 60_000, 1),
                    SoundUsageEvent("old", "wind", "微风", "自然", old, 20),
                ),
                sleeps = listOf(
                    SleepEvent("today", now - 60_000, 30, SleepTarget.ALL, 5, SleepStatus.COMPLETED),
                    SleepEvent("old", old, 60, SleepTarget.ALL, 5, SleepStatus.COMPLETED),
                ),
            ),
            RecordsQuery(range = StatsRange.TODAY),
            now,
        )

        assertEquals(listOf("today"), result.selectedSounds.map { it.id })
        assertEquals(listOf("today"), result.selectedSleeps.map { it.id })
    }

    @Test fun customDailyAverageUsesWholeRangeBeyondThirtyOneDays() {
        val start = java.time.LocalDate.of(2026, 6, 1)
        val end = java.time.LocalDate.of(2026, 8, 13)
        val result = aggregator.aggregate(
            RecordsEvents(focus = listOf(focus("session", FocusTargetKind.FREE, "自由专注", 74))),
            RecordsQuery(range = StatsRange.CUSTOM, customStart = start, customEnd = end),
            now,
        )

        assertEquals(1, result.dailyAverageMinutes)
        assertEquals(74, result.activityDays.size)
    }

    private fun focus(id: String, kind: FocusTargetKind, name: String, minutes: Int) = FocusEvent(
        id, kind, id, name, now - 60_000, minutes, SessionStatus.COMPLETED,
    )

    private fun focusAt(id: String, kind: FocusTargetKind, name: String, minutes: Int, startedAt: Long) = FocusEvent(
        id, kind, id, name, startedAt, minutes, SessionStatus.COMPLETED,
    )
}
