package com.soundist.feature.productivity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ProductivityViewModel(private val dependencies: ProductivityDependencies) : ViewModel() {
    val state: StateFlow<ProductivityState> = dependencies.repository.state
    val clock: Clock get() = dependencies.clock
    private var timerReconciliationJob: Job? = null

    /** Starts at most one reconciliation loop for this shared ViewModel instance. */
    fun ensureTimerReconciliation() {
        if (timerReconciliationJob?.isActive == true) return
        if (state.value.focus.status != SessionStatus.RUNNING && state.value.sleep.status == SleepStatus.IDLE) return
        timerReconciliationJob = viewModelScope.launch {
            while (state.value.focus.status == SessionStatus.RUNNING || state.value.sleep.status != SleepStatus.IDLE) {
                reconcileTimerNow()
                reconcileSleepNow()
                delay(500)
            }
        }
    }

    fun navigate(page: WorkspacePage) = update { it.copy(workspacePage = page, organizerPage = if (page == WorkspacePage.ORGANIZER && it.workspacePage != WorkspacePage.ORGANIZER) OrganizerPage.TODAY else it.organizerPage) }
    fun organizer(page: OrganizerPage) = update { it.copy(organizerPage = page) }
    fun archiveManager(open: Boolean) = update { it.copy(archiveOpen = open) }
    fun configureTimer(mode: TimerMode? = null, phase: SessionPhase? = null, focusMinutes: Int? = null, breakMinutes: Int? = null, cycle: FocusCycle? = null) = viewModelScope.launch {
        dependencies.focusAlerts.cancel()
        dependencies.repository.update { s ->
            s.copy(focus = FocusStateMachine.reset(s.focus).copy(
                timerMode = mode ?: s.focus.timerMode, phase = phase ?: s.focus.phase,
                focusMinutes = focusMinutes?.coerceIn(1, 240) ?: s.focus.focusMinutes,
                breakMinutes = breakMinutes?.coerceIn(1, 120) ?: s.focus.breakMinutes,
                cycle = cycle ?: s.focus.cycle,
            ))
        }
    }
    fun bind(target: FocusTarget, suggestedMinutes: Int? = null) = viewModelScope.launch {
        dependencies.focusAlerts.cancel()
        val s = state.value
        val scene = when(target.kind) { TargetKind.TODO -> s.todos.find { it.id == target.id }?.defaultSceneId; TargetKind.PLAN -> s.plans.find { it.id == target.id }?.defaultSceneId; TargetKind.HABIT -> s.habits.find { it.id == target.id }?.defaultSceneId; else -> null }
        dependencies.repository.update { it.copy(workspacePage = WorkspacePage.FOCUS, focus = FocusStateMachine.reset(it.focus).copy(target = target, focusMinutes = suggestedMinutes?.coerceIn(1, 240) ?: it.focus.focusMinutes)) }
        scene?.let { dependencies.focusScenes.applyPreset(it) }
    }
    fun unbind() = bind(FocusTarget())
    fun toggleTimer() = viewModelScope.launch {
        val current = state.value.focus
        val next = if (current.status == SessionStatus.RUNNING) FocusStateMachine.pause(current, clock.now()) else FocusStateMachine.start(current, clock.now())
        dependencies.repository.update { it.copy(focus = next) }
        dependencies.focusPersistence.persist(FocusPersistenceEvent(if(next.status==SessionStatus.RUNNING) FocusPersistenceEventKind.STARTED else FocusPersistenceEventKind.PAUSED,next,clock.now()))
        if (next.status == SessionStatus.RUNNING) dependencies.focusController.started(next) else dependencies.focusController.paused(next)
        syncFocusAlert(next)
        if (next.status == SessionStatus.RUNNING) ensureTimerReconciliation()
    }
    fun resetTimer() = viewModelScope.launch { val next=FocusStateMachine.reset(state.value.focus);dependencies.focusAlerts.cancel();dependencies.focusPersistence.persist(FocusPersistenceEvent(FocusPersistenceEventKind.RESET,next,clock.now()));dependencies.repository.update { it.copy(focus = next) };dependencies.focusController.stopped() }
    fun finishTimer() = viewModelScope.launch { val current=state.value.focus; val next=if(current.phase==SessionPhase.BREAK)FocusStateMachine.finishBreak(current) else FocusStateMachine.requestReview(current,clock.now());dependencies.focusAlerts.cancel();dependencies.focusPersistence.persist(FocusPersistenceEvent(if(next.status==SessionStatus.REVIEW)FocusPersistenceEventKind.REVIEW_REQUESTED else FocusPersistenceEventKind.RESET,next,clock.now()));dependencies.repository.update{it.copy(focus=next)} }
    fun reconcileTimer() = viewModelScope.launch { reconcileTimerNow() }
    private suspend fun reconcileTimerNow() {
        val current = state.value.focus
        val now = clock.now()
        val next = FocusStateMachine.naturalCompletion(current, now) ?: return
        val triggerAt = focusCompletionAt(current)
        dependencies.focusAlerts.notifyNow(current.phase, triggerAt)
        dependencies.focusAlerts.cancel()
        val provisionalRecord = if (current.phase == SessionPhase.FOCUS && next.status == SessionStatus.REVIEW) {
            val audioSnapshot = dependencies.focusAudioSnapshot()
            val startedAt = (current.startedAtEpochMillis ?: now) - current.accumulatedBeforeStartMillis
            FocusRecord(
                id = "focus-auto-$startedAt",
                target = current.target,
                startedAtEpochMillis = startedAt,
                minutes = next.completionMinutes,
                status = CompletionStatus.COMPLETED,
                timerMode = current.timerMode,
                sounds = audioSnapshot.sounds,
                radio = audioSnapshot.radioId,
                ambientMode = audioSnapshot.ambientMode,
                audioSource = audioSnapshot.audioSource,
            )
        } else null
        dependencies.focusPersistence.persist(
            FocusPersistenceEvent(
                if (next.status == SessionStatus.REVIEW) FocusPersistenceEventKind.REVIEW_REQUESTED else FocusPersistenceEventKind.RESET,
                next,
                now,
            ),
        )
        dependencies.repository.update { state ->
            state.copy(
                focus = next,
                focusRecords = provisionalRecord?.let { record -> listOf(record) + state.focusRecords.filterNot { it.id == record.id } }
                    ?: state.focusRecords,
            )
        }
        if (current.phase == SessionPhase.BREAK && next.cycle.autoFocus) {
            val running = FocusStateMachine.start(next, now)
            dependencies.repository.update { it.copy(focus = running) }
            dependencies.focusController.started(running)
            syncFocusAlert(running)
        } else dependencies.focusController.stopped()
    }
    fun saveReview(note: String, status: CompletionStatus, completeBoundTodo: Boolean) = viewModelScope.launch {
        val snapshot = state.value
        val focus = snapshot.focus
        if (focus.status != SessionStatus.REVIEW) return@launch
        val audioSnapshot = dependencies.focusAudioSnapshot()
        val provisional = snapshot.focusRecords.firstOrNull {
            it.id.startsWith("focus-auto-") && it.target == focus.target && it.minutes == focus.completionMinutes
        }
        val record = FocusRecord(
            id = provisional?.id ?: java.util.UUID.randomUUID().toString(),
            target = focus.target,
            startedAtEpochMillis = provisional?.startedAtEpochMillis ?: (clock.now() - focus.accumulatedBeforeStartMillis),
            minutes = focus.completionMinutes,
            status = status,
            timerMode = focus.timerMode,
            note = note.trim(),
            sounds = provisional?.sounds ?: audioSnapshot.sounds,
            radio = provisional?.radio ?: audioSnapshot.radioId,
            ambientMode = provisional?.ambientMode ?: audioSnapshot.ambientMode,
            audioSource = provisional?.audioSource?.takeUnless { it == "UNRECORDED" } ?: audioSnapshot.audioSource,
        )
        dependencies.focusPersistence.persist(FocusPersistenceEvent(if(status==CompletionStatus.COMPLETED)FocusPersistenceEventKind.COMPLETED else FocusPersistenceEventKind.INTERRUPTED,focus,clock.now(),record))
        if (note.trim().isNotEmpty()) dependencies.reviewNoteWriter?.invoke(FocusReviewNote(focus.target.kind, focus.target.id, focus.target.name, note.trim()))
        dependencies.repository.update { s ->
            var todos = s.todos
            var plans = s.plans
            var habits = s.habits
            var countdowns = s.countdowns
            if (status == CompletionStatus.COMPLETED) when (focus.target.kind) {
                TargetKind.TODO -> if (completeBoundTodo) todos = todos.map { if (it.id == focus.target.id) it.copy(done = true) else it }
                TargetKind.PLAN -> plans = plans.map { if (it.id == focus.target.id) it.copy(doneMinutes = (it.doneMinutes + focus.completionMinutes).coerceAtMost(it.targetMinutes)) else it }
                TargetKind.HABIT -> habits = habits.map { if (it.id == focus.target.id) it.copy(todayMinutes = it.todayMinutes + focus.completionMinutes) else it }
                TargetKind.COUNTDOWN -> countdowns = countdowns.map { if (it.id == focus.target.id) it.copy(investedMinutes = it.investedMinutes + focus.completionMinutes) else it }
                TargetKind.FREE -> Unit
            }
            val cycle = focus.cycle
            val nextPhase = if (status == CompletionStatus.COMPLETED && cycle.autoBreak) SessionPhase.BREAK else SessionPhase.FOCUS
            s.copy(todos = todos, plans = plans, habits = habits, countdowns = countdowns, focusRecords = listOf(record) + s.focusRecords.filterNot { it.id == record.id }, focus = FocusStateMachine.reset(focus).copy(phase = nextPhase))
        }
        dependencies.focusController.stopped()
        val next = state.value.focus
        if (next.phase == SessionPhase.BREAK && next.cycle.autoBreak) {
            val running = FocusStateMachine.start(next, clock.now())
            dependencies.repository.update { it.copy(focus = running) }
            dependencies.focusController.started(running)
            syncFocusAlert(running)
        }
    }

    private suspend fun syncFocusAlert(session: FocusSessionState) {
        if (session.status != SessionStatus.RUNNING || session.timerMode != TimerMode.COUNTDOWN) {
            dependencies.focusAlerts.cancel()
            return
        }
        val remaining = (focusCompletionAt(session) - clock.now()).coerceAtLeast(0L)
        if (remaining == 0L) dependencies.focusAlerts.cancel()
        else dependencies.focusAlerts.schedule(session.phase, focusCompletionAt(session))
    }

    private fun focusCompletionAt(session: FocusSessionState): Long =
        requireNotNull(session.startedAtEpochMillis) +
            (FocusStateMachine.configuredMillis(session) - session.accumulatedBeforeStartMillis).coerceAtLeast(0L)

    fun saveTodo(value: Todo) = saveEntity(value.id, value.reminder, value.dueAtEpochMillis, value.text) { s -> s.copy(todos = upsert(s.todos, value) { it.id }) }
    fun toggleTodo(id: String) = update { s -> s.copy(todos = s.todos.map { if (it.id == id) it.copy(done = !it.done) else it }) }
    fun deleteTodo(id: String) = removeAndCancel(id) { s -> s.copy(todos = s.todos.filterNot { it.id == id }) }
    fun savePlan(value: FocusPlan) = saveEntity(value.id, value.reminder, value.endDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(), value.title) { s -> s.copy(plans = upsert(s.plans, value) { it.id }) }
    fun deletePlan(id: String) = removeAndCancel(id) { s -> s.copy(plans = s.plans.filterNot { it.id == id }) }
    fun saveHabit(value: Habit) = saveEntity(value.id, value.reminder, nextHabitReminder(value, clock.now()), value.title) { s -> s.copy(habits = upsert(s.habits, value) { it.id }) }
    fun deleteHabit(id: String) = removeAndCancel(id) { s -> s.copy(habits = s.habits.filterNot { it.id == id }) }
    fun checkHabit(id: String, value: Int = 1) = viewModelScope.launch {
        state.value.habits.firstOrNull { it.id == id } ?: return@launch
        val check = HabitCheck(habitId = id, checkedAtEpochMillis = clock.now(), value = value.coerceAtLeast(1))
        dependencies.habitChecks.save(check)
        dependencies.repository.update { s -> s.copy(
            habitChecks = listOf(check) + s.habitChecks,
        ) }
    }
    fun undoHabitCheck(id: String) = viewModelScope.launch {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val latest = state.value.habitChecks
            .filter { it.habitId == id && Instant.ofEpochMilli(it.checkedAtEpochMillis).atZone(zone).toLocalDate() == today }
            .maxByOrNull { it.checkedAtEpochMillis } ?: return@launch
        dependencies.habitChecks.delete(latest.id)
        dependencies.repository.update { s -> s.copy(habitChecks = s.habitChecks.filterNot { it.id == latest.id }) }
    }
    fun saveCountdown(value: CountdownEvent) = saveEntity(value.id, value.reminder, value.targetAtEpochMillis, value.title) { s -> s.copy(countdowns = upsert(s.countdowns, value) { it.id }) }
    fun deleteCountdown(id: String) = removeAndCancel(id) { s -> s.copy(countdowns = s.countdowns.filterNot { it.id == id }) }
    fun archive(kind: TargetKind, id: String, archived: Boolean) = viewModelScope.launch {
        dependencies.reminders.cancel(id)
        dependencies.repository.update { s -> when (kind) {
            TargetKind.TODO -> s.copy(todos = s.todos.map { if (it.id == id) it.copy(archived = archived) else it })
            TargetKind.PLAN -> s.copy(plans = s.plans.map { if (it.id == id) it.copy(archived = archived) else it })
            TargetKind.HABIT -> s.copy(habits = s.habits.map { if (it.id == id) it.copy(archived = archived) else it })
            TargetKind.COUNTDOWN -> s.copy(countdowns = s.countdowns.map { if (it.id == id) it.copy(archived = archived) else it })
            TargetKind.FREE -> s
        } }
    }
    fun move(kind: TargetKind, id: String, delta: Int) = update { s -> when (kind) {
        TargetKind.TODO -> s.copy(todos = reorder(s.todos, id, delta) { it.id }.mapIndexed { i, v -> v.copy(order = i) })
        TargetKind.PLAN -> s.copy(plans = reorder(s.plans, id, delta) { it.id }.mapIndexed { i, v -> v.copy(order = i) })
        TargetKind.HABIT -> s.copy(habits = reorder(s.habits, id, delta) { it.id }.mapIndexed { i, v -> v.copy(order = i) })
        TargetKind.COUNTDOWN -> s.copy(countdowns = reorder(s.countdowns, id, delta) { it.id }.mapIndexed { i, v -> v.copy(order = i) })
        TargetKind.FREE -> s
    } }

    /** App.tsx 睡眠配置值更新（endMode/分钟/时钟/范围/渐弱）：不暂停计时，不改编辑态。 */
    fun configureSleep(session: SleepSession) = update { it.copy(sleep = session.copy(status = it.sleep.status)) }
    /** App.tsx「调整/收起调整」：仅切换编辑态（EDITING=RUNNING+editing），倒计时不冻结。 */
    fun toggleSleepAdjust() = update { it.copy(sleep = when (it.sleep.status) {
        SleepStatus.RUNNING -> it.sleep.copy(status = SleepStatus.EDITING)
        SleepStatus.EDITING -> it.sleep.copy(status = SleepStatus.RUNNING)
        else -> it.sleep
    }) }
    fun startSleep() = viewModelScope.launch {
        val now = clock.now(); val config = state.value.sleep
        val end = if (config.endMode == SleepEndMode.DURATION) now + config.plannedMinutes.coerceIn(1, 480) * 60_000L else resolveNextClock(now, config.clockHour, config.clockMinute)
        val captured = config.volumeSnapshotCaptured || dependencies.sleepAudio.snapshotVolumes()
        if (!captured) return@launch
        dependencies.repository.update { it.copy(sleep = SleepStateMachine.start(config.copy(volumeSnapshotCaptured=true), now, end)) }
        ensureTimerReconciliation()
    }
    fun extendSleep(minutes: Int = 15) = update { it.copy(sleep = SleepStateMachine.extend(it.sleep, minutes)) }
    fun reconcileSleep() = viewModelScope.launch { reconcileSleepNow() }
    private suspend fun reconcileSleepNow() {
        val current = state.value.sleep; val now = clock.now(); val end = current.endsAtEpochMillis ?: return
        if (current.status == SleepStatus.IDLE) return
        val fadeStart = end - current.fadeMinutes * 60_000L
        if (now >= end) {
            dependencies.sleepAudio.stop(current.target)
            dependencies.repository.update { s -> s.copy(sleepRecords = listOf(SleepRecord(startedAtEpochMillis = current.startedAtEpochMillis ?: now, plannedMinutes = current.plannedMinutes, target = current.target, fadeMinutes = current.fadeMinutes, completed = true)) + s.sleepRecords, sleep = SleepStateMachine.stop(current)) }
        } else if (now >= fadeStart && current.fadeMinutes > 0) dependencies.sleepAudio.beginFade(current.target, end - now)
    }
    fun cancelSleep() = viewModelScope.launch {
        val current = state.value.sleep; dependencies.sleepAudio.restoreSnapshot()
        dependencies.repository.update { s -> s.copy(sleepRecords = if (current.startedAtEpochMillis != null) listOf(SleepRecord(startedAtEpochMillis = current.startedAtEpochMillis, plannedMinutes = current.plannedMinutes, target = current.target, fadeMinutes = current.fadeMinutes, completed = false)) + s.sleepRecords else s.sleepRecords, sleep = SleepStateMachine.stop(current)) }
    }
    fun saveRoutine(name: String) { if (name.isBlank()) return; update { s -> s.copy(sleepRoutines = listOf(SleepRoutine(name = name.trim(), minutes = s.sleep.plannedMinutes, target = s.sleep.target, fadeMinutes = s.sleep.fadeMinutes, mode = s.sleep.endMode, clockHour = s.sleep.clockHour, clockMinute = s.sleep.clockMinute)) + s.sleepRoutines) } }
    fun loadRoutine(value: SleepRoutine) = configureSleep(state.value.sleep.copy(plannedMinutes = value.minutes, target = value.target, fadeMinutes = value.fadeMinutes, endMode = value.mode, clockHour = value.clockHour, clockMinute = value.clockMinute))
    fun deleteRoutine(id: String) = update { it.copy(sleepRoutines = it.sleepRoutines.filterNot { r -> r.id == id }) }

    private fun update(transform: (ProductivityState) -> ProductivityState) = viewModelScope.launch { dependencies.repository.update(transform) }
    private fun saveEntity(id: String, reminder: ReminderOffset, baseTime: Long?, title: String, transform: (ProductivityState) -> ProductivityState) = viewModelScope.launch {
        dependencies.repository.update(transform); dependencies.reminders.cancel(id)
        reminderTime(reminder, baseTime)?.let { dependencies.reminders.replace(id, it, title) }
    }
    private fun removeAndCancel(id: String, transform: (ProductivityState) -> ProductivityState) = viewModelScope.launch { dependencies.reminders.cancel(id); dependencies.repository.update(transform) }
}

internal fun reminderTime(offset: ReminderOffset, base: Long?): Long? {
    if (base == null || offset == ReminderOffset.NONE) return null
    return base - when (offset) { ReminderOffset.NONE -> 0; ReminderOffset.AT_TIME -> 0; ReminderOffset.TEN_MINUTES -> 600_000; ReminderOffset.ONE_HOUR -> 3_600_000; ReminderOffset.ONE_DAY -> 86_400_000 }
}
private fun resolveNextClock(now: Long, hour: Int, minute: Int): Long { val zone = ZoneId.systemDefault(); val current = Instant.ofEpochMilli(now).atZone(zone); var target = current.toLocalDate().atTime(hour.coerceIn(0,23), minute.coerceIn(0,59)).atZone(zone); if (!target.isAfter(current)) target = target.plusDays(1); return target.toInstant().toEpochMilli() }
internal fun nextHabitReminder(habit: Habit, now: Long): Long? { if(habit.reminder==ReminderOffset.NONE)return null;val zone=ZoneId.systemDefault();val current=Instant.ofEpochMilli(now).atZone(zone);for(offset in 0..14){val date=current.toLocalDate().plusDays(offset.toLong());if((date.dayOfWeek.value % 7) in habit.weekdays){val target=date.atTime(habit.reminderHour.coerceIn(0,23),habit.reminderMinute.coerceIn(0,59)).atZone(zone).toInstant().toEpochMilli();val trigger=reminderTime(habit.reminder,target);if(trigger!=null&&trigger>now)return trigger}};return null }
private fun <T> upsert(list: List<T>, value: T, id: (T) -> String): List<T> = if (list.any { id(it) == id(value) }) list.map { if (id(it) == id(value)) value else it } else listOf(value) + list
private fun <T> reorder(list: List<T>, id: String, delta: Int, key: (T) -> String): List<T> { val from = list.indexOfFirst { key(it) == id }; if (from < 0) return list; val to = (from + delta).coerceIn(0, list.lastIndex); return list.toMutableList().also { val v = it.removeAt(from); it.add(to, v) } }
