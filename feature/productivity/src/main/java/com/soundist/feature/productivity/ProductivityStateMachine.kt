package com.soundist.feature.productivity

object FocusStateMachine {
    fun elapsedMillis(session: FocusSessionState, now: Long): Long =
        session.accumulatedBeforeStartMillis + if (session.status == SessionStatus.RUNNING) (now - requireNotNull(session.startedAtEpochMillis)).coerceAtLeast(0) else 0

    fun configuredMillis(session: FocusSessionState): Long = when {
        session.phase == SessionPhase.FOCUS -> session.focusMinutes
        session.cycle.round >= session.cycle.rounds -> session.cycle.longBreakMinutes
        else -> session.breakMinutes
    } * 60_000L

    fun displayMillis(session: FocusSessionState, now: Long): Long = when (session.timerMode) {
        TimerMode.STOPWATCH -> elapsedMillis(session, now)
        TimerMode.COUNTDOWN -> (configuredMillis(session) - elapsedMillis(session, now)).coerceAtLeast(0)
    }

    fun start(session: FocusSessionState, now: Long): FocusSessionState {
        require(session.status == SessionStatus.IDLE || session.status == SessionStatus.PAUSED)
        return session.copy(status = SessionStatus.RUNNING, startedAtEpochMillis = now)
    }

    fun pause(session: FocusSessionState, now: Long): FocusSessionState {
        require(session.status == SessionStatus.RUNNING)
        return session.copy(status = SessionStatus.PAUSED, accumulatedBeforeStartMillis = elapsedMillis(session, now), startedAtEpochMillis = null)
    }

    fun reset(session: FocusSessionState): FocusSessionState = session.copy(status = SessionStatus.IDLE, startedAtEpochMillis = null, accumulatedBeforeStartMillis = 0, completionMinutes = 0)

    fun requestReview(session: FocusSessionState, now: Long): FocusSessionState {
        val elapsed = elapsedMillis(session, now)
        if (elapsed < 30_000) return reset(session)
        return session.copy(status = SessionStatus.REVIEW, startedAtEpochMillis = null, accumulatedBeforeStartMillis = elapsed, completionMinutes = kotlin.math.round(elapsed / 60_000.0).toInt().coerceAtLeast(1))
    }

    /** App.tsx `finishCurrentTimer(false)` while in break phase: end the break, return to focus. */
    fun finishBreak(session: FocusSessionState): FocusSessionState {
        val nextRound = if (session.cycle.round >= session.cycle.rounds) 1 else session.cycle.round + 1
        return reset(session).copy(phase = SessionPhase.FOCUS, cycle = session.cycle.copy(round = nextRound))
    }

    fun naturalCompletion(session: FocusSessionState, now: Long): FocusSessionState? {
        if (session.status != SessionStatus.RUNNING || session.timerMode != TimerMode.COUNTDOWN || displayMillis(session, now) > 0) return null
        if (session.phase == SessionPhase.FOCUS) return requestReview(session, now)
        val nextRound = if (session.cycle.round >= session.cycle.rounds) 1 else session.cycle.round + 1
        return reset(session).copy(phase = SessionPhase.FOCUS, cycle = session.cycle.copy(round = nextRound))
    }
}

object SleepStateMachine {
    fun start(config: SleepSession, now: Long, resolvedEnd: Long): SleepSession = config.copy(status = SleepStatus.RUNNING, startedAtEpochMillis = config.startedAtEpochMillis ?: now, endsAtEpochMillis = resolvedEnd, plannedMinutes = ((resolvedEnd - now + 59_999) / 60_000).toInt().coerceAtLeast(1))
    fun remainingMillis(session: SleepSession, now: Long): Long = ((session.endsAtEpochMillis ?: now) - now).coerceAtLeast(0)
    fun extend(session: SleepSession, minutes: Int): SleepSession = session.copy(endsAtEpochMillis = requireNotNull(session.endsAtEpochMillis) + minutes.coerceAtLeast(1) * 60_000L, plannedMinutes = (session.plannedMinutes + minutes).coerceAtMost(480))
    fun stop(session: SleepSession): SleepSession = session.copy(status = SleepStatus.IDLE, startedAtEpochMillis = null, endsAtEpochMillis = null)
}
