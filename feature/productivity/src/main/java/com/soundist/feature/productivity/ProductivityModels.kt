package com.soundist.feature.productivity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class WorkspacePage { FOCUS, ORGANIZER, NOTES }
enum class OrganizerPage(val label: String) { TODAY("今天"), TODOS("待办"), PLANS("计划"), HABITS("习惯"), COUNTDOWNS("倒计日") }
enum class TargetKind { FREE, TODO, PLAN, HABIT, COUNTDOWN }
enum class TimerMode { COUNTDOWN, STOPWATCH }
enum class SessionPhase { FOCUS, BREAK }
enum class SessionStatus { IDLE, RUNNING, PAUSED, REVIEW }
enum class CompletionStatus { COMPLETED, INTERRUPTED }
enum class FocusPersistenceEventKind { STARTED, PAUSED, REVIEW_REQUESTED, COMPLETED, INTERRUPTED, RESET }
enum class TodoKind { ONE_OFF, LONG_TERM }
enum class Priority { LOW, MEDIUM, HIGH }
enum class ReminderOffset { NONE, AT_TIME, TEN_MINUTES, ONE_HOUR, ONE_DAY }
enum class PlanScope { TODAY, WEEK }
enum class HabitMetric { MINUTES, COUNT, CHECK }
enum class SleepEndMode { DURATION, CLOCK }
enum class SleepTarget(val label: String) { ALL("全部声音"), AMBIENT("环境声"), RADIO("电台") }
enum class SleepStatus { IDLE, RUNNING, EDITING }

data class FocusTarget(val kind: TargetKind = TargetKind.FREE, val id: String = "free", val name: String = "自由专注", val meta: String = "不绑定任何计划")
data class FocusCycle(val round: Int = 1, val rounds: Int = 4, val longBreakMinutes: Int = 15, val autoBreak: Boolean = true, val autoFocus: Boolean = false)
data class FocusSessionState(
    val timerMode: TimerMode = TimerMode.COUNTDOWN,
    val phase: SessionPhase = SessionPhase.FOCUS,
    val status: SessionStatus = SessionStatus.IDLE,
    val focusMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val startedAtEpochMillis: Long? = null,
    val accumulatedBeforeStartMillis: Long = 0,
    val target: FocusTarget = FocusTarget(),
    val cycle: FocusCycle = FocusCycle(),
    val completionMinutes: Int = 0,
)
data class FocusRecord(
    val id: String = UUID.randomUUID().toString(), val target: FocusTarget,
    val startedAtEpochMillis: Long, val minutes: Int, val status: CompletionStatus,
    val timerMode: TimerMode, val note: String = "", val sounds: List<FocusSoundSnapshot> = emptyList(), val radio: String? = null,
    /** PERSONAL / CHANNEL; null means this legacy record never captured the mode. */
    val ambientMode: String? = null,
    /** USER_AMBIENT / CHANNEL_RECIPE / GENERATED_CHANNEL / RADIO / SILENT / UNRECORDED. */
    val audioSource: String = "UNRECORDED",
)
data class FocusSoundSnapshot(val soundId: String, val volume: Float)
data class FocusAudioSnapshot(
    val sounds: List<FocusSoundSnapshot> = emptyList(),
    val radioId: String? = null,
    val ambientMode: String? = null,
    val audioSource: String = "UNRECORDED",
)
/** App.tsx `saveFocusSession` 复盘笔记负载。 */
data class FocusReviewNote(
    val targetKind: TargetKind,
    val targetId: String,
    val targetName: String,
    val text: String,
)
data class FocusPersistenceEvent(
    val kind: FocusPersistenceEventKind,
    val session: FocusSessionState,
    val occurredAtEpochMillis: Long,
    val record: FocusRecord? = null,
)

data class Todo(
    val id: String = UUID.randomUUID().toString(), val text: String, val kind: TodoKind = TodoKind.ONE_OFF,
    val done: Boolean = false, val dueAtEpochMillis: Long? = null, val allDay: Boolean = true,
    val priority: Priority = Priority.MEDIUM, val reminder: ReminderOffset = ReminderOffset.NONE,
    val estimatedMinutes: Int = 25, val planId: String? = null, val note: String = "",
    val defaultSceneId: String? = null, val archived: Boolean = false, val order: Int = 0,
    /** App.tsx `dueTime`（"HH:mm"，allDay 时为空串）。时间已并入 [dueAtEpochMillis]。 */
    val dueTime: String = "",
)
data class FocusPlan(
    val id: String = UUID.randomUUID().toString(), val title: String, val scope: PlanScope = PlanScope.WEEK,
    val startDate: LocalDate? = null, val endDate: LocalDate? = null, val targetMinutes: Int = 90,
    val doneMinutes: Int = 0, val description: String = "", val milestone: String = "",
    val reminder: ReminderOffset = ReminderOffset.NONE, val defaultSceneId: String? = null,
    val archived: Boolean = false, val order: Int = 0,
)
data class Habit(
    val id: String = UUID.randomUUID().toString(), val title: String, val weekdays: Set<Int> = (0..6).toSet(),
    val metric: HabitMetric = HabitMetric.MINUTES, val targetMinutes: Int = 30, val targetCount: Int = 1,
    val streak: Int = 0, val todayMinutes: Int = 0, val todayCount: Int = 0,
    val reminder: ReminderOffset = ReminderOffset.NONE, val reminderHour: Int = 9, val reminderMinute: Int = 0, val startDate: LocalDate? = null,
    val defaultSceneId: String? = null, val archived: Boolean = false, val order: Int = 0,
)
data class HabitCheck(
    val id: String = UUID.randomUUID().toString(),
    val habitId: String,
    val checkedAtEpochMillis: Long,
    val value: Int,
)
data class CountdownEvent(
    val id: String = UUID.randomUUID().toString(), val title: String, val targetAtEpochMillis: Long,
    val note: String = "", val dailyMinutes: Int = 30, val reminder: ReminderOffset = ReminderOffset.NONE,
    val planId: String? = null, val milestone: String = "", val investedMinutes: Int = 0,
    val archived: Boolean = false, val order: Int = 0,
    /** App.tsx `targetTime`（"HH:mm"）。时间已并入 [targetAtEpochMillis]。 */
    val targetTime: String = "",
)

data class SleepRoutine(
    val id: String = UUID.randomUUID().toString(), val name: String, val minutes: Int,
    val target: SleepTarget, val fadeMinutes: Int, val mode: SleepEndMode = SleepEndMode.DURATION,
    val clockHour: Int = 23, val clockMinute: Int = 30,
)
data class SleepSession(
    val status: SleepStatus = SleepStatus.IDLE, val startedAtEpochMillis: Long? = null,
    val endsAtEpochMillis: Long? = null, val plannedMinutes: Int = 30,
    val endMode: SleepEndMode = SleepEndMode.DURATION, val clockHour: Int = 23, val clockMinute: Int = 30,
    val target: SleepTarget = SleepTarget.ALL, val fadeMinutes: Int = 10, val volumeSnapshotCaptured: Boolean = false,
)
data class SleepRecord(val id: String = UUID.randomUUID().toString(), val startedAtEpochMillis: Long, val plannedMinutes: Int, val target: SleepTarget, val fadeMinutes: Int, val completed: Boolean)

data class ProductivityState(
    val workspacePage: WorkspacePage = WorkspacePage.FOCUS,
    val organizerPage: OrganizerPage = OrganizerPage.TODAY,
    val focus: FocusSessionState = FocusSessionState(), val focusRecords: List<FocusRecord> = emptyList(),
    val todos: List<Todo> = emptyList(), val plans: List<FocusPlan> = emptyList(),
    val habits: List<Habit> = emptyList(), val habitChecks: List<HabitCheck> = emptyList(), val countdowns: List<CountdownEvent> = emptyList(),
    val sleep: SleepSession = SleepSession(), val sleepRoutines: List<SleepRoutine> = emptyList(),
    val sleepRecords: List<SleepRecord> = emptyList(), val archiveOpen: Boolean = false,
)

interface ProductivityRepository {
    val state: StateFlow<ProductivityState>
    suspend fun update(transform: (ProductivityState) -> ProductivityState)
}
interface ReminderScheduler {
    suspend fun replace(ownerId: String, triggerAtEpochMillis: Long, title: String)
    suspend fun cancel(ownerId: String)
}
interface HabitCheckStore {
    suspend fun save(check: HabitCheck)
    suspend fun delete(checkId: String)
}
interface FocusSessionController {
    suspend fun started(session: FocusSessionState)
    suspend fun paused(session: FocusSessionState)
    suspend fun stopped()
}
interface FocusTransitionAlerts {
    suspend fun schedule(phase: SessionPhase, triggerAtEpochMillis: Long)
    suspend fun notifyNow(phase: SessionPhase, triggerAtEpochMillis: Long)
    suspend fun cancel()
}
object NoOpFocusTransitionAlerts : FocusTransitionAlerts {
    override suspend fun schedule(phase: SessionPhase, triggerAtEpochMillis: Long) = Unit
    override suspend fun notifyNow(phase: SessionPhase, triggerAtEpochMillis: Long) = Unit
    override suspend fun cancel() = Unit
}
interface FocusPersistenceStore { suspend fun persist(event: FocusPersistenceEvent) }
interface FocusSceneController {
    /** Apply an existing listening preset. Implementations must keep the current scene when id is null. */
    suspend fun applyPreset(id: String)
}
interface SleepAudioController {
    /** Returns true only after a durable pre-sleep volume snapshot was captured. */
    suspend fun snapshotVolumes(): Boolean
    suspend fun beginFade(target: SleepTarget, durationMillis: Long)
    suspend fun stop(target: SleepTarget)
    suspend fun restoreSnapshot()
}
interface Clock { fun now(): Long }
object SystemClock : Clock { override fun now(): Long = System.currentTimeMillis() }

data class ProductivityDependencies(
    val repository: ProductivityRepository,
    val reminders: ReminderScheduler,
    val focusController: FocusSessionController,
    val focusPersistence: FocusPersistenceStore,
    val focusScenes: FocusSceneController,
    val sleepAudio: SleepAudioController,
    val habitChecks: HabitCheckStore,
    val focusAlerts: FocusTransitionAlerts = NoOpFocusTransitionAlerts,
    val clock: Clock = SystemClock,
    val reviewNoteWriter: ((FocusReviewNote) -> Unit)? = null,
    /** Captures the audible composition at review time so records remain self-contained. */
    val focusAudioSnapshot: () -> FocusAudioSnapshot = { FocusAudioSnapshot() },
    /** App.tsx quickPresets（id → name）——默认声场下拉的选项来源。 */
    val scenePresets: StateFlow<List<Pair<String, String>>> = MutableStateFlow(emptyList()),
)
