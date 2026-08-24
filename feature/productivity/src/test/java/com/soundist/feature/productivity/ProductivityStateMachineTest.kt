package com.soundist.feature.productivity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProductivityStateMachineTest {
    @Test fun countdownUsesAbsoluteTimeAcrossProcessGap() {
        val running = FocusStateMachine.start(FocusSessionState(focusMinutes = 25), 1_000)
        assertEquals(20 * 60_000L, FocusStateMachine.displayMillis(running, 5 * 60_000 + 1_000))
    }

    @Test fun pauseAccumulatesAndResumeDoesNotDoubleCount() {
        val first = FocusStateMachine.start(FocusSessionState(timerMode = TimerMode.STOPWATCH), 1_000)
        val paused = FocusStateMachine.pause(first, 61_000)
        val resumed = FocusStateMachine.start(paused, 121_000)
        assertEquals(90_000L, FocusStateMachine.elapsedMillis(resumed, 151_000))
    }

    @Test fun sessionsUnderThirtySecondsAreDiscarded() {
        val running = FocusStateMachine.start(FocusSessionState(), 10_000)
        val result = FocusStateMachine.requestReview(running, 39_000)
        assertEquals(SessionStatus.IDLE, result.status)
        assertEquals(0, result.completionMinutes)
    }

    @Test fun completedBreakAdvancesCycleAndReturnsToFocus() {
        val session = FocusSessionState(phase = SessionPhase.BREAK, breakMinutes = 1, status = SessionStatus.RUNNING, startedAtEpochMillis = 0, cycle = FocusCycle(round = 2, rounds = 4))
        val completed = FocusStateMachine.naturalCompletion(session, 60_000)!!
        assertEquals(SessionPhase.FOCUS, completed.phase)
        assertEquals(3, completed.cycle.round)
        assertEquals(SessionStatus.IDLE, completed.status)
    }

    @Test fun runningCountdownDoesNotFinishEarly() {
        val session = FocusSessionState(focusMinutes = 1, status = SessionStatus.RUNNING, startedAtEpochMillis = 0)
        assertNull(FocusStateMachine.naturalCompletion(session, 59_999))
    }

    @Test fun sleepRemainingAndExtensionUseAbsoluteEnd() {
        val started = SleepStateMachine.start(SleepSession(plannedMinutes = 30), 1_000, 1_801_000)
        assertEquals(900_000L, SleepStateMachine.remainingMillis(started, 901_000))
        val extended = SleepStateMachine.extend(started, 15)
        assertEquals(2_701_000L, extended.endsAtEpochMillis)
        assertEquals(45, extended.plannedMinutes)
    }

    @Test fun reminderOffsetsResolveAgainstAbsoluteDueTime() {
        val due = 200_000_000L
        assertEquals(due, reminderTime(ReminderOffset.AT_TIME, due))
        assertEquals(due - 600_000, reminderTime(ReminderOffset.TEN_MINUTES, due))
        assertEquals(due - 3_600_000, reminderTime(ReminderOffset.ONE_HOUR, due))
        assertEquals(due - 86_400_000, reminderTime(ReminderOffset.ONE_DAY, due))
        assertNull(reminderTime(ReminderOffset.NONE, due))
        assertNull(reminderTime(ReminderOffset.ONE_DAY, null))
    }

    @Test fun restoredSleepUsesPersistedAbsoluteEndNotConfiguredDuration() {
        val restored = SleepSession(status = SleepStatus.RUNNING, startedAtEpochMillis = 1_000, endsAtEpochMillis = 61_000, plannedMinutes = 480)
        assertEquals(10_000, SleepStateMachine.remainingMillis(restored, 51_000))
        assertEquals(0, SleepStateMachine.remainingMillis(restored, 70_000))
    }

    @Test fun habitReminderUsesNextSelectedWeekdayAndConcreteTime() {
        val zone = java.time.ZoneId.systemDefault()
        val monday = java.time.LocalDate.of(2026, 8, 10).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val habit = Habit(title="Read",weekdays=setOf(2),reminder=ReminderOffset.AT_TIME,reminderHour=9,reminderMinute=30)
        val expected = java.time.LocalDate.of(2026, 8, 11).atTime(9,30).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected,nextHabitReminder(habit,monday))
    }
}
