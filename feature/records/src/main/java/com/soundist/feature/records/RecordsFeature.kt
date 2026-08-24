package com.soundist.feature.records

import com.soundist.core.designsystem.activity
import com.soundist.core.designsystem.audioLines
import com.soundist.core.designsystem.circleAlert
import com.soundist.core.designsystem.clock3
import com.soundist.core.designsystem.fileText
import com.soundist.core.designsystem.moon
import com.soundist.core.designsystem.radio
import com.soundist.core.designsystem.trendingUp
import com.soundist.core.designsystem.waves
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Paint as NativePaint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

enum class FocusTargetKind { FREE, TODO, PLAN, HABIT, COUNTDOWN }
enum class SessionStatus { COMPLETED, INTERRUPTED }

data class FocusEvent(
    val id: String,
    val targetKind: FocusTargetKind,
    val targetId: String,
    val targetName: String,
    val startedAt: Long,
    val minutes: Int,
    val status: SessionStatus,
    val note: String? = null,
    val soundIds: List<String> = emptyList(),
    val soundNames: List<String> = emptyList(),
    val soundCategories: Map<String, String> = emptyMap(),
    val radioId: String? = null,
    val radioName: String? = null,
    val ambientMode: String? = null,
    val audioSource: String = "UNRECORDED",
)

data class SoundUsageEvent(
    val id: String,
    val soundId: String,
    val soundName: String,
    val category: String,
    val startedAt: Long,
    val durationMinutes: Int,
    val focusEventId: String? = null,
)

data class RadioUsageEvent(
    val id: String,
    val stationId: String,
    val stationName: String,
    val genre: String,
    val startedAt: Long,
    val durationMinutes: Int,
    val focusEventId: String? = null,
)

enum class SleepTarget { ALL, AMBIENT, RADIO }
enum class SleepStatus { COMPLETED, CANCELLED }

data class SleepEvent(
    val id: String,
    val startedAt: Long,
    val plannedMinutes: Int,
    val target: SleepTarget,
    val fadeMinutes: Int,
    val status: SleepStatus,
)

data class RecordsEvents(
    val focus: List<FocusEvent> = emptyList(),
    val sounds: List<SoundUsageEvent> = emptyList(),
    val radios: List<RadioUsageEvent> = emptyList(),
    val sleeps: List<SleepEvent> = emptyList(),
    val savedSleepRoutineCount: Int = 0,
)

data class ChannelRecordMetadata(
    val name: String,
    val genre: String,
) {
    val subtitle: String get() = genre.ifBlank { "电台" }
}

interface RecordsEventSource { val events: Flow<RecordsEvents> }

class MutableRecordsEventSource(initial: RecordsEvents = RecordsEvents()) : RecordsEventSource {
    private val mutableEvents = MutableStateFlow(initial)
    override val events: StateFlow<RecordsEvents> = mutableEvents.asStateFlow()
    fun emit(value: RecordsEvents) { mutableEvents.value = value }
}

class CoreRecordsEventSource(
    repository: com.soundist.core.model.RecordsRepository,
    soundNames: Map<String, String> = emptyMap(),
    soundCategories: Map<String, String> = emptyMap(),
    channelMetadata: Flow<Map<String, ChannelRecordMetadata>> = flowOf(emptyMap()),
    savedSleepRoutineCount: Flow<Int> = flowOf(0),
) : RecordsEventSource {
    override val events: Flow<RecordsEvents> = combine(
        repository.observeFocusSessions(),
        repository.observeSleepSessions(),
        repository.observePlaybackEvents(),
        channelMetadata,
        savedSleepRoutineCount,
    ) { focusSessions, sleepSessions, playbackEvents, channels, routineCount ->
        val focus = focusSessions
            .filter { it.kind == com.soundist.core.model.SessionKind.FOCUS && it.status in setOf(com.soundist.core.model.SessionStatus.COMPLETED, com.soundist.core.model.SessionStatus.CANCELLED) }
            .map { session ->
                FocusEvent(
                    id = session.id,
                    targetKind = FocusTargetKind.valueOf(session.targetKind.name),
                    targetId = session.targetId.orEmpty(),
                    targetName = session.label?.takeIf(String::isNotBlank) ?: if (session.targetKind == com.soundist.core.model.FocusTargetKind.FREE) "自由专注" else "未命名目标",
                    startedAt = session.startedAt,
                    minutes = (session.elapsedSeconds / 60).toInt(),
                    status = if (session.status == com.soundist.core.model.SessionStatus.COMPLETED) SessionStatus.COMPLETED else SessionStatus.INTERRUPTED,
                    note = session.review,
                    soundIds = session.soundSnapshot.filter { it.enabled }.map { it.soundId },
                    soundNames = session.soundSnapshot.filter { it.enabled }.map { soundNames[it.soundId] ?: it.soundId },
                    soundCategories = session.soundSnapshot.filter { it.enabled }.associate { track ->
                        (soundNames[track.soundId] ?: track.soundId) to (soundCategories[track.soundId] ?: "未分类")
                    },
                    radioId = session.radioSnapshotId,
                    radioName = session.radioSnapshotId?.let { channels[it]?.name ?: it },
                    ambientMode = session.ambientMode,
                    audioSource = session.audioSource,
                )
            }
        val focusById = focus.associateBy { it.id }
        val sounds = playbackEvents.filter { it.kind == com.soundist.core.model.PlaybackKind.AMBIENT }.map { event ->
            val related = focusById.values.firstOrNull { focusEvent -> event.startedAt in focusEvent.startedAt..(focusEvent.startedAt + focusEvent.minutes * 60_000L) }
            SoundUsageEvent(event.id, event.sourceId, soundNames[event.sourceId] ?: event.sourceId, soundCategories[event.sourceId] ?: "未分类", event.startedAt, (event.activeSeconds / 60).toInt(), related?.id)
        }
        val radios = playbackEvents.filter { it.kind == com.soundist.core.model.PlaybackKind.RADIO }.map { event ->
            val related = focusById.values.firstOrNull { focusEvent -> event.startedAt in focusEvent.startedAt..(focusEvent.startedAt + focusEvent.minutes * 60_000L) }
            val channel = channels[event.sourceId]
            RadioUsageEvent(event.id, event.sourceId, channel?.name ?: event.sourceId, channel?.subtitle ?: "电台", event.startedAt, (event.activeSeconds / 60).toInt(), related?.id)
        }
        val sleeps = sleepSessions.map { session ->
            SleepEvent(
                id = session.id,
                startedAt = session.startedAt,
                plannedMinutes = ((session.targetEndAt - session.startedAt).coerceAtLeast(0) / 60_000).toInt(),
                target = SleepTarget.valueOf(session.target.name),
                fadeMinutes = session.fadeMinutes,
                status = if (session.status == com.soundist.core.model.SessionStatus.COMPLETED) SleepStatus.COMPLETED else SleepStatus.CANCELLED,
            )
        }
        RecordsEvents(focus, sounds, radios, sleeps, routineCount.coerceAtLeast(0))
    }
}

enum class StatsRange { TODAY, LAST_7_DAYS, MONTH, CUSTOM }
enum class StatsView { OVERVIEW, DETAILS }
enum class DetailSection { FOCUS, SOUNDS, RADIO, SLEEP }
enum class TrendMetric { FOCUS, AUDIO }
enum class RecordsLoadStatus { LOADING, READY, ERROR }

data class RecordsQuery(
    val range: StatsRange = StatsRange.LAST_7_DAYS,
    val customStart: LocalDate = LocalDate.now().minusDays(6),
    val customEnd: LocalDate = LocalDate.now(),
    val view: StatsView = StatsView.OVERVIEW,
    val detail: DetailSection = DetailSection.FOCUS,
    val trendMetric: TrendMetric = TrendMetric.FOCUS,
)

data class PieSlice(val label: String, val minutes: Int, val percentage: Float, val color: Color)
data class TrendPoint(val label: String, val focusHours: Float, val audioHours: Float)
data class TargetRelation(val name: String, val minutes: Int, val sessions: Int, val completion: Int, val dominantSound: String)
data class SoundRank(val name: String, val category: String, val minutes: Int, val sessions: Int)
data class RadioRank(val name: String, val genre: String, val minutes: Int, val sessions: Int)
data class SoundRelation(val name: String, val minutes: Int, val sessions: Int, val targetCount: Int, val completion: Int)
data class ContextMoment(val label: String, val sounds: List<String>, val percentage: Int)
data class DurationBucket(val label: String, val count: Int)
data class TimeBucket(val label: String, val count: Int)

data class RecordsState(
    val query: RecordsQuery = RecordsQuery(),
    val rangeLabel: String = "最近 7 天",
    val selectedFocus: List<FocusEvent> = emptyList(),
    val selectedSounds: List<SoundUsageEvent> = emptyList(),
    val selectedRadios: List<RadioUsageEvent> = emptyList(),
    val selectedSleeps: List<SleepEvent> = emptyList(),
    val totalMinutes: Int = 0,
    val dailyAverageMinutes: Int = 0,
    val completedCount: Int = 0,
    val completionRate: Int = 0,
    val todoMinutes: Int = 0,
    val freeMinutes: Int = 0,
    val pie: List<PieSlice> = emptyList(),
    val fullPie: List<PieSlice> = emptyList(),
    val mostInvestedTarget: String = "自由专注",
    val trend: List<TrendPoint> = emptyList(),
    val targets: List<TargetRelation> = emptyList(),
    val sounds: List<SoundRank> = emptyList(),
    val radios: List<RadioRank> = emptyList(),
    val soundRelations: List<SoundRelation> = emptyList(),
    val contextMoments: List<ContextMoment> = emptyList(),
    val radioWeekdayMinutes: List<Int> = List(7) { 0 },
    val durationBuckets: List<DurationBucket> = emptyList(),
    val timeBuckets: List<TimeBucket> = emptyList(),
    val heatmapMinutes: List<Int> = emptyList(),
    val heatmapMonth: LocalDate = LocalDate.now().withDayOfMonth(1),
    val sleepRoutineCount: Int = 0,
    val loadStatus: RecordsLoadStatus = RecordsLoadStatus.READY,
    val loadError: String? = null,
) {
    val insight: String get() = if (selectedFocus.isEmpty()) "这段时间还没有专注记录，完成一次计时后会在这里形成趋势。" else "完成率 $completionRate%，最常投入的是「$mostInvestedTarget」。"
}

fun interface RecordsEventAggregator { fun aggregate(events: RecordsEvents, query: RecordsQuery, nowMillis: Long): RecordsState }

class DefaultRecordsEventAggregator(private val zoneId: ZoneId = ZoneId.systemDefault()) : RecordsEventAggregator {
    override fun aggregate(events: RecordsEvents, query: RecordsQuery, nowMillis: Long): RecordsState {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val start = when (query.range) {
            StatsRange.TODAY -> today
            StatsRange.LAST_7_DAYS -> today.minusDays(6)
            StatsRange.MONTH -> today.withDayOfMonth(1)
            StatsRange.CUSTOM -> minOf(query.customStart, query.customEnd)
        }
        val end = when (query.range) { StatsRange.CUSTOM -> maxOf(query.customStart, query.customEnd); else -> today }
        val startMillis = start.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endMillis = end.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
        fun inRange(value: Long) = value in startMillis..endMillis

        val focus = events.focus.filter { inRange(it.startedAt) }.sortedByDescending { it.startedAt }
        val sounds = events.sounds.filter { inRange(it.startedAt) }
        val radios = events.radios.filter { inRange(it.startedAt) }
        val sleeps = events.sleeps
        val days = (end.toEpochDay() - start.toEpochDay() + 1).toInt().coerceIn(1, 31)
        val totalMinutes = focus.sumOf { it.minutes.coerceAtLeast(0) }
        val completed = focus.count { it.status == SessionStatus.COMPLETED }
        val pieFocus = focus.filter {
            it.minutes > 0 && (it.targetKind == FocusTargetKind.TODO || it.targetKind == FocusTargetKind.FREE)
        }
        val pieTotal = pieFocus.sumOf { it.minutes }.coerceAtLeast(1)
        val todoMinutes = pieFocus.filter { it.targetKind == FocusTargetKind.TODO }.sumOf { it.minutes }
        val freeMinutes = pieFocus.filter { it.targetKind == FocusTargetKind.FREE }.sumOf { it.minutes }
        val palette = listOf(
            Color(0xFFD8849B), Color(0xFF64AFA1), Color(0xFFA7D3C8), Color(0xFF738FA4),
            Color(0xFFC99662), Color(0xFFA292C1), Color(0xFF7F8C87),
        )
        val groupedPie = pieFocus.groupBy { if (it.targetKind == FocusTargetKind.FREE) "自由专注" else "待办专注" }
            .mapValues { (_, sessions) -> sessions.sumOf { it.minutes } }
            .toList()
            .sortedByDescending { it.second }
        val mostInvestedTarget = groupedPie.firstOrNull()?.first ?: "自由专注"
        val fullPie = groupedPie.mapIndexed { index, (name, minutes) -> PieSlice(name, minutes, percent1(minutes, pieTotal), palette[index % palette.size]) }
        val pie = fullPie.sortedByDescending(PieSlice::percentage)

        // App.tsx `trendDayCount = Math.min(31, Math.max(1, Math.ceil((rangeEnd - rangeStart)/86400000) + 1))`
        // 近 7 天 rangeEnd=Date.now() → ceil(6.x)+1 = 8 个点；安卓旧实现只算 7 个点。
        val trendEnd = if (query.range == StatsRange.CUSTOM) endMillis else nowMillis
        val trendDayCount = minOf(31, maxOf(1, ceil((trendEnd - startMillis).toDouble() / 86_400_000.0).toInt() + 1))
        val trend = (0 until trendDayCount).map { index ->
            val dayStartMillis = trendEnd - (trendDayCount - 1 - index) * 86_400_000L
            val date = Instant.ofEpochMilli(dayStartMillis).atZone(zoneId).toLocalDate()
            val dayFocus = focus.filter { it.startedAt.localDate() == date }
            TrendPoint(
                label = if (query.range == StatsRange.MONTH || trendDayCount > 10) date.dayOfMonth.toString() else "${date.monthValue}/${date.dayOfMonth}",
                focusHours = roundHours(dayFocus.sumOf { it.minutes }),
                audioHours = roundHours(dayFocus.filter { it.soundIds.isNotEmpty() || it.radioId != null }.sumOf { it.minutes }),
            )
        }

        // New records carry an immutable session snapshot. Older records only have
        // playback events, so merge both sources by focus id and de-duplicate names.
        val effectiveSoundsByFocus = focus.associate { session ->
            val legacyNames = sounds.filter { it.focusEventId == session.id }.map(SoundUsageEvent::soundName)
            session.id to (session.soundNames + legacyNames).distinct()
        }
        val effectiveRadiosByFocus = focus.associate { session ->
            val legacyNames = radios.filter { it.focusEventId == session.id }.map(RadioUsageEvent::stationName)
            session.id to (listOfNotNull(session.radioName) + legacyNames).distinct()
        }
        val categoryBySoundName = focus.flatMap { it.soundCategories.entries }.associate { it.key to it.value }
        val soundRanks = focus.flatMap { session ->
            effectiveSoundsByFocus.getValue(session.id).map { Triple(it, session.minutes, session.id) }
        }
            .groupBy { it.first }
            .map { (name, rows) -> SoundRank(name, categoryBySoundName[name] ?: sounds.firstOrNull { it.soundName == name }?.category ?: "未分类", rows.sumOf { it.second }, rows.map { it.third }.distinct().size) }
            .sortedByDescending { it.minutes }
        val radioRanks = focus.flatMap { session ->
            effectiveRadiosByFocus.getValue(session.id).map { Triple(it, session.minutes, session.id) }
        }
            .groupBy { it.first }
            .map { (name, rows) -> RadioRank(name, radios.firstOrNull { it.stationName == name }?.genre ?: "电台", rows.sumOf { it.second }, rows.map { it.third }.distinct().size) }
            .sortedByDescending { it.minutes }
        val targets = focus.groupBy { if (it.targetKind == FocusTargetKind.FREE) "自由专注" else it.targetName }.map { (name, sessions) ->
            val related = sessions.flatMap { session ->
                effectiveSoundsByFocus.getValue(session.id) +
                    effectiveRadiosByFocus.getValue(session.id).map { "电台 · $it" }
            }
            val fallback = when {
                sessions.all { it.audioSource == "SILENT" } -> "静音"
                sessions.any { it.audioSource == "UNRECORDED" } -> "未记录声音"
                else -> "无环境声"
            }
            TargetRelation(name, sessions.sumOf { it.minutes }, sessions.size, (sessions.count { it.status == SessionStatus.COMPLETED } * 100.0 / sessions.size.coerceAtLeast(1)).roundToInt(), related.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: fallback)
        }.sortedByDescending { it.minutes }.take(4)
        val focusById = focus.associateBy(FocusEvent::id)
        val soundRelations = focus.flatMap { session ->
            effectiveSoundsByFocus.getValue(session.id).map { it to session }
        }.groupBy { it.first }.map { (name, rows) ->
            val sessions = rows.map { it.second }.distinctBy(FocusEvent::id)
            SoundRelation(
                name = name,
                minutes = sessions.sumOf { it.minutes },
                sessions = sessions.size,
                targetCount = sessions.map { it.targetName }.distinct().size,
                completion = (sessions.count { it.status == SessionStatus.COMPLETED } * 100.0 / sessions.size.coerceAtLeast(1)).roundToInt(),
            )
        }.sortedByDescending { it.minutes }.take(5)
        val momentRanges = listOf("清晨" to 0..7, "上午" to 8..11, "下午" to 12..17, "夜间" to 18..23)
        val momentMinutes = momentRanges.map { (_, hours) -> focus.filter { it.startedAt.hour() in hours }.sumOf { it.minutes } }
        val maximumMoment = momentMinutes.maxOrNull()?.coerceAtLeast(1) ?: 1
        val contextMoments = momentRanges.mapIndexed { index, (label, hours) ->
            val sessions = focus.filter { it.startedAt.hour() in hours }
            val names = sessions.flatMap { effectiveSoundsByFocus.getValue(it.id) }
                .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(2).map { it.key }
            ContextMoment(label, names.ifEmpty { listOf("暂无记录") }, (momentMinutes[index] * 100.0 / maximumMoment).roundToInt())
        }
        val radioWeekdayMinutes = (1..7).map { weekday ->
            radios.filter { it.startedAt.dayOfWeekValue() == weekday }.sumOf { it.durationMinutes.coerceAtLeast(0) }
        }

        val monthStart = today.withDayOfMonth(1)
        val monthDays = today.lengthOfMonth()
        val heatmap = (1..monthDays).map { day -> events.focus.filter { it.startedAt.localDate() == monthStart.withDayOfMonth(day) }.sumOf { it.minutes } }
        val durationBuckets = listOf(
            DurationBucket("<25", focus.count { it.minutes < 25 }), DurationBucket("25-44", focus.count { it.minutes in 25..44 }),
            DurationBucket("45-59", focus.count { it.minutes in 45..59 }), DurationBucket("60+", focus.count { it.minutes >= 60 }),
        )
        val timeBuckets = listOf(
            TimeBucket("清晨", focus.count { it.startedAt.hour() < 8 }), TimeBucket("上午", focus.count { it.startedAt.hour() in 8..11 }),
            TimeBucket("下午", focus.count { it.startedAt.hour() in 12..17 }), TimeBucket("夜间", focus.count { it.startedAt.hour() >= 18 }),
        )
        val rangeLabel = when (query.range) {
            StatsRange.TODAY -> today.format(DateTimeFormatter.ISO_LOCAL_DATE)
            StatsRange.LAST_7_DAYS -> "最近 7 天"
            StatsRange.MONTH -> "${today.year}年${today.monthValue.toString().padStart(2, '0')}月"
            StatsRange.CUSTOM -> "$start 至 $end"
        }
        return RecordsState(
            query = query, rangeLabel = rangeLabel, selectedFocus = focus, selectedSounds = sounds, selectedRadios = radios, selectedSleeps = sleeps,
            totalMinutes = totalMinutes, dailyAverageMinutes = (totalMinutes.toDouble() / days).roundToInt(), completedCount = completed,
            completionRate = if (focus.isEmpty()) 0 else (completed * 100.0 / focus.size).roundToInt(),
            todoMinutes = todoMinutes, freeMinutes = freeMinutes, pie = pie, fullPie = fullPie, mostInvestedTarget = mostInvestedTarget, trend = trend, targets = targets,
            sounds = soundRanks, radios = radioRanks, soundRelations = soundRelations, contextMoments = contextMoments,
            radioWeekdayMinutes = radioWeekdayMinutes, durationBuckets = durationBuckets, timeBuckets = timeBuckets,
            heatmapMinutes = heatmap, heatmapMonth = monthStart, sleepRoutineCount = events.savedSleepRoutineCount,
        )
    }

    private fun Long.localDate() = Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()
    private fun Long.hour() = Instant.ofEpochMilli(this).atZone(zoneId).hour
    private fun Long.dayOfWeekValue() = Instant.ofEpochMilli(this).atZone(zoneId).dayOfWeek.value
}

interface RecordsRepository : AutoCloseable {
    val state: StateFlow<RecordsState>
    fun updateQuery(transform: (RecordsQuery) -> RecordsQuery)
    fun retry() = Unit
    override fun close() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class AggregatingRecordsRepository(
    source: RecordsEventSource,
    private val aggregator: RecordsEventAggregator = DefaultRecordsEventAggregator(),
    private val clock: Clock = Clock.systemDefaultZone(),
    initialQuery: RecordsQuery = RecordsQuery(),
) : RecordsRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val query = MutableStateFlow(initialQuery)
    private val retries = MutableStateFlow(0)
    private val mutableState = MutableStateFlow(aggregator.aggregate(RecordsEvents(), initialQuery, clock.millis()).copy(loadStatus = RecordsLoadStatus.LOADING))
    override val state: StateFlow<RecordsState> = mutableState.asStateFlow()

    init {
        scope.launch {
            combine(
                retries.flatMapLatest {
                    source.events.map<RecordsEvents, RecordsPayload> { RecordsPayload.Data(it) }
                        .onStart { emit(RecordsPayload.Loading) }
                        .catch { failure -> emit(RecordsPayload.Error(failure.localizedMessage ?: "记录读取失败")) }
                },
                query,
            ) { payload, current ->
                when (payload) {
                    RecordsPayload.Loading -> mutableState.value.copy(query = current, loadStatus = RecordsLoadStatus.LOADING, loadError = null)
                    is RecordsPayload.Error -> mutableState.value.copy(query = current, loadStatus = RecordsLoadStatus.ERROR, loadError = payload.message)
                    is RecordsPayload.Data -> aggregator.aggregate(payload.events, current, clock.millis()).copy(loadStatus = RecordsLoadStatus.READY, loadError = null)
                }
            }.collect { mutableState.value = it }
        }
    }
    override fun updateQuery(transform: (RecordsQuery) -> RecordsQuery) { query.value = transform(query.value) }
    override fun retry() { retries.value += 1 }
    override fun close() { scope.cancel() }
}

private sealed interface RecordsPayload {
    data object Loading : RecordsPayload
    data class Data(val events: RecordsEvents) : RecordsPayload
    data class Error(val message: String) : RecordsPayload
}

/** Compatibility repository. It is intentionally empty: production data must come from [RecordsEventSource]. */
class InMemoryRecordsRepository(initialEvents: RecordsEvents = RecordsEvents()) : RecordsRepository by AggregatingRecordsRepository(MutableRecordsEventSource(initialEvents))

class RecordsViewModel(private val repository: RecordsRepository = InMemoryRecordsRepository()) : ViewModel() {
    val state: StateFlow<RecordsState> = repository.state
    fun setRange(range: StatsRange) = repository.updateQuery { it.copy(range = range) }
    fun setView(view: StatsView) = repository.updateQuery { it.copy(view = view) }
    fun setDetail(detail: DetailSection) = repository.updateQuery { it.copy(detail = detail) }
    fun setTrend(metric: TrendMetric) = repository.updateQuery { it.copy(trendMetric = metric) }
    fun setCustomRange(start: LocalDate, end: LocalDate) = repository.updateQuery { it.copy(customStart = start, customEnd = end, range = StatsRange.CUSTOM) }
    fun retry() = repository.retry()
    override fun onCleared() { repository.close() }
}

class RecordsViewModelFactory(private val repository: RecordsRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = RecordsViewModel(repository) as T
}

const val RECORDS_PAGE_SCROLL_TEST_TAG = "records-page-scroll"
internal val recordsPageScrollSemantics: String get() = "LazyColumn:$RECORDS_PAGE_SCROLL_TEST_TAG"
internal const val RECORDS_PAGE_BOTTOM_INSET_DP = 104

@OptIn(ExperimentalFoundationApi::class)
@Composable fun RecordsRoute(vm: RecordsViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    LazyColumn(Modifier.fillMaxSize().background(CanvasColor).testTag(RECORDS_PAGE_SCROLL_TEST_TAG), contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = RECORDS_PAGE_BOTTOM_INSET_DP.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        stickyHeader { Selectors(state.query, vm) }
        when (state.loadStatus) {
            RecordsLoadStatus.LOADING -> { recordItem { RecordsLoading() }; return@LazyColumn }
            RecordsLoadStatus.ERROR -> { recordItem { RecordsError(state.loadError ?: "记录读取失败", vm::retry) }; return@LazyColumn }
            RecordsLoadStatus.READY -> Unit
        }
        recordItem { Summary(state) }
        if (state.query.view == StatsView.OVERVIEW) {
            recordItem { Insight(state.insight) }
            recordItem { FocusPieCard(state) { vm.setView(StatsView.DETAILS); vm.setDetail(DetailSection.FOCUS) } }
            recordItem { TrendCard(state, vm) }
            recordItem { TargetRelations(state.targets) }
            state.selectedFocus.firstOrNull()?.let { event -> recordItem { ReplayCard(event, state) } }
            recordItem { CompletionCard(state) }
            recordItem { BarBlock("专注长度", "每次投入落在哪个区间", state.durationBuckets.map { it.label to it.count }, ChartGreen, "分钟") }
        } else {
            recordItem { DetailTabs(state.query.detail, vm::setDetail) }
            when (state.query.detail) {
                DetailSection.FOCUS -> {
                    recordItem { MonthlyTimeOfDay(state.timeBuckets.map { it.label to it.count }, monthLabel(state.heatmapMonth)) }
                    recordItem { Heatmap(state.heatmapMonth, state.heatmapMinutes) }
                    recordItem { RecentFocus(state.selectedFocus, state.selectedSounds, state.selectedRadios) }
                }
                DetailSection.SOUNDS -> {
                    recordItem { RankSection("声音使用", state.sounds.map { Triple(it.name, it.category, "${formatMinutes(it.minutes)} · ${it.sessions}次") }, audioLines, Ambient) }
                    recordItem { FrequentSoundCombination(state.sounds) }
                    recordItem { SoundRelations(state.soundRelations) }
                    recordItem { ContextMoments(state.contextMoments) }
                    recordItem { CategoryBars(state.sounds) }
                }
                DetailSection.RADIO -> {
                    recordItem { RankSection("电台收听", state.radios.map { Triple(it.name, it.genre, "${formatMinutes(it.minutes)} · ${it.sessions}次") }, radio, Radio, "当前范围内没有电台收听记录") }
                    recordItem { RadioRhythm(state.radioWeekdayMinutes) }
                }
                DetailSection.SLEEP -> recordItem { SleepReview(state) }
            }
        }
        recordItem { Text("这些不是冷冰冰的数据，\n而是你和声音一起待过的时间。", Modifier.fillMaxWidth().padding(vertical = 10.dp), color = Muted, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif), textAlign = TextAlign.Center) }
    }
}

private fun LazyListScope.recordItem(content: @Composable () -> Unit) {
    item { Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) { content() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun Selectors(query: RecordsQuery, vm: RecordsViewModel) {
    var selectingStart by remember { mutableStateOf<Boolean?>(null) }
    Column(Modifier.fillMaxWidth().background(CanvasColor).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Segmented(StatsView.entries, query.view, { if (it == StatsView.OVERVIEW) "回顾总览" else "统计明细" }, vm::setView)
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(4.dp)) { StatsRange.entries.forEach { range -> SegmentButton(range.label, query.range == range, Modifier.weight(1f)) { vm.setRange(range) } } }
        if (query.range == StatsRange.CUSTOM) {
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
                DateButton("开始日期", query.customStart, Modifier.weight(1f)) { selectingStart = true }
                Text("至", style = MaterialTheme.typography.labelSmall, color = Muted)
                DateButton("结束日期", query.customEnd, Modifier.weight(1f)) { selectingStart = false }
            }
        }
        HorizontalDivider(color = BorderColor)
    }
    selectingStart?.let { start ->
        val current = if (start) query.customStart else query.customEnd
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = current.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { selectingStart = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { selected ->
                        val date = Instant.ofEpochMilli(selected).atZone(ZoneOffset.UTC).toLocalDate()
                        if (start) vm.setCustomRange(date, query.customEnd) else vm.setCustomRange(query.customStart, date)
                    }
                    selectingStart = null
                }) { Text("确定", color = AmbientLight) }
            },
            dismissButton = { TextButton(onClick = { selectingStart = null }) { Text("取消", color = Secondary) } },
        ) { DatePicker(pickerState, title = null, headline = null) }
    }
}

@Composable private fun DateButton(label: String, date: LocalDate, modifier: Modifier, click: () -> Unit) {
    OutlinedButton(click, modifier.heightIn(min = 44.dp), border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Muted)
            Text(date.toString(), style = MaterialTheme.typography.labelSmall, color = Secondary, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable private fun RecordsLoading() {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).border(1.dp, BorderColor, MaterialTheme.shapes.medium).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(18.dp), color = Ambient, strokeWidth = 2.dp)
        Text("正在读取本机记录…", Modifier.padding(start = 10.dp), style = MaterialTheme.typography.labelSmall, color = Secondary)
    }
}

@Composable private fun RecordsError(message: String, retry: () -> Unit) {
    Row(Modifier.fillMaxWidth().border(1.dp, Danger.copy(alpha = .35f), MaterialTheme.shapes.medium).background(Danger.copy(alpha = .06f), MaterialTheme.shapes.medium).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(circleAlert, null, Modifier.size(18.dp), tint = Danger)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 10.dp)) { Text("记录暂时无法读取", style = MaterialTheme.typography.labelLarge, color = Danger); Text(message, style = MaterialTheme.typography.labelSmall, color = Secondary) }
        TextButton(retry, Modifier.heightIn(min = 44.dp)) { Text("重试", color = AmbientLight) }
    }
}

@Composable private fun Summary(state: RecordsState) {
    SectionBorder {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Column { Text("专注总览", fontWeight = FontWeight.Medium, color = Primary); Text(state.rangeLabel, style = MaterialTheme.typography.labelSmall, color = Muted) }; Text(state.query.range.label, style = MaterialTheme.typography.labelSmall, color = Secondary) }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Metric("次数", state.selectedFocus.size.toString(), "次", Modifier.weight(1f)); Metric("时长", metricValue(state.totalMinutes), metricUnit(state.totalMinutes), Modifier.weight(1f)); Metric("日均", metricValue(state.dailyAverageMinutes), "${metricUnit(state.dailyAverageMinutes)}/日", Modifier.weight(1f))
        }
    }
}

@Composable private fun Insight(text: String) { Row(Modifier.fillMaxWidth().background(Ambient.copy(alpha = .05f)).drawBehind { drawLine(Ambient.copy(alpha = .45f), Offset.Zero, Offset(0f, size.height), 2.dp.toPx()) }.padding(horizontal = 12.dp, vertical = 10.dp)) { Icon(trendingUp, null, Modifier.size(16.dp), tint = AmbientLight); Column(Modifier.padding(start = 12.dp)) { Text("这一段时间的回声", style = MaterialTheme.typography.labelLarge, color = Primary); Text(text, style = MaterialTheme.typography.labelSmall, color = Secondary) } } }

@Composable private fun FocusPieCard(state: RecordsState, details: () -> Unit) {
    CardBlock {
        Text("专注时长分布", fontWeight = FontWeight.Medium, color = Primary); Text("${state.rangeLabel} · 仅待办专注与自由专注", Modifier.padding(top = 4.dp, bottom = 16.dp), style = MaterialTheme.typography.labelSmall, color = Muted)
        if (state.pie.isEmpty()) Box(Modifier.fillMaxWidth().height(256.dp), contentAlignment = Alignment.Center) { Text("完成一次专注后，\n这里会形成时长分布。", textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = Muted) }
        else SolidPieWithLabels(state.pie, Modifier.fillMaxWidth().height(380.dp))
        val countedSessions = state.selectedFocus.count { it.targetKind == FocusTargetKind.TODO || it.targetKind == FocusTargetKind.FREE }
        Text("共 $countedSessions 次 · ${formatMinutes(state.pie.sumOf { it.minutes })}", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge, color = Primary)
        OutlinedButton(details, Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).height(44.dp), shape = MaterialTheme.shapes.medium, contentPadding = PaddingValues(horizontal = 20.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = SurfaceHigh), border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)) { Text("查看专注记录", fontSize = 12.sp, color = Secondary) }
        if (state.fullPie.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = BorderColor)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.fullPie.chunked(2).forEach { rowItems ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        rowItems.forEach { slice -> Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).background(slice.color, CircleShape)); Column(Modifier.weight(1f).padding(horizontal = 8.dp)) { Text(slice.label, style = MaterialTheme.typography.labelSmall, color = Secondary); Text(formatMinutesChinese(slice.minutes), style = MaterialTheme.typography.labelSmall, color = Secondary) }; Text(percentLabel(slice.percentage), style = MaterialTheme.typography.labelSmall, color = Secondary, fontFamily = FontFamily.Monospace) } }
                    }
                }
            }
        }
    }
}

@Composable private fun SolidPieWithLabels(data: List<PieSlice>, modifier: Modifier) {
    val accessibilitySummary = data.joinToString("；") { "${it.label}${it.minutes}分钟，${it.percentage.roundToInt()}%" }
    Canvas(modifier.semantics { contentDescription = "专注时长分布：$accessibilitySummary" }) {
        val diameter = min(size.width, size.height) * if (size.width <= 360.dp.toPx()) .46f else .52f
        val center = Offset(size.width / 2, size.height / 2)
        val origin = Offset(center.x - diameter / 2, center.y - diameter / 2)
        val labelPaint = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply { color = 0xFFE9ECE9.toInt(); textSize = 11.sp.toPx(); isFakeBoldText = true }
        val valuePaint = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply { color = 0xFFA9B3AF.toInt(); textSize = 11.sp.toPx() }
        val placements = resolvePieLabelPlacements(
            percentages = data.map(PieSlice::percentage),
            height = size.height,
            minimumGap = labelPaint.textSize + valuePaint.textSize + 8.dp.toPx(),
        ).associateBy(PieLabelPlacement::index)
        var start = 270f
        data.forEachIndexed { index, slice ->
            val sweep = slice.percentage * 3.6f
            drawArc(slice.color, start, sweep, true, origin, Size(diameter, diameter))
            if (index >= 3) { start += sweep; return@forEachIndexed }
            val placement = placements.getValue(index)
            val angle = Math.toRadians((start + sweep / 2).toDouble())
            val pieRadius = diameter * .5f
            val first = center + Offset((cos(angle) * pieRadius).toFloat(), (sin(angle) * pieRadius).toFloat())
            // App.tsx echarts labelLine length=13（径向段）+ length2=9（水平段）
            val radialEnd = center + Offset((cos(angle) * (pieRadius + 13.dp.toPx())).toFloat(), (sin(angle) * (pieRadius + 13.dp.toPx())).toFloat())
            val lines = slice.label.chunked(6)
            val value = formatMinutesChinese(slice.minutes)
            val maxTextWidth = (lines.maxOfOrNull(labelPaint::measureText) ?: 0f).coerceAtLeast(valuePaint.measureText(value))
            val edgePadding = 4.dp.toPx()
            val preferredLeft = if (placement.onRight) radialEnd.x + 12.dp.toPx() else radialEnd.x - 12.dp.toPx() - maxTextWidth
            val textLeft = preferredLeft.coerceIn(edgePadding, (size.width - edgePadding - maxTextWidth).coerceAtLeast(edgePadding))
            val lineEndX = if (placement.onRight) textLeft - 3.dp.toPx() else textLeft + maxTextWidth + 3.dp.toPx()
            val end = Offset(lineEndX, placement.y)
            drawLine(slice.color.copy(alpha = .82f), first, radialEnd, 1.2.dp.toPx())
            drawLine(slice.color.copy(alpha = .82f), radialEnd, end, 1.2.dp.toPx())
            lines.forEachIndexed { lineIndex, line ->
                val textX = if (placement.onRight) textLeft else textLeft + maxTextWidth - labelPaint.measureText(line)
                drawContext.canvas.nativeCanvas.drawText(line, textX, end.y + lineIndex * 14.dp.toPx(), labelPaint)
            }
            val valueX = if (placement.onRight) textLeft else textLeft + maxTextWidth - valuePaint.measureText(value)
            drawContext.canvas.nativeCanvas.drawText(value, valueX, end.y + lines.size * 14.dp.toPx(), valuePaint)
            start += sweep
        }
    }
}

internal data class PieLabelPlacement(val index: Int, val onRight: Boolean, val y: Float)

internal fun resolvePieLabelPlacements(percentages: List<Float>, height: Float, minimumGap: Float): List<PieLabelPlacement> {
    if (percentages.isEmpty() || height <= 0f) return emptyList()
    var start = -90f
    val desired = percentages.mapIndexed { index, percentage ->
        val sweep = percentage.coerceAtLeast(0f) * 3.6f
        val angle = Math.toRadians((start + sweep / 2).toDouble())
        start += sweep
        PieLabelPlacement(index, cos(angle) >= 0, height / 2f + (sin(angle) * height * .34f).toFloat())
    }
    val edge = min(height * .12f, minimumGap.coerceAtLeast(1f))
    val low = edge
    val high = (height - edge).coerceAtLeast(low)
    return desired.groupBy(PieLabelPlacement::onRight).values.flatMap { side ->
        val sorted = side.sortedBy(PieLabelPlacement::y)
        val effectiveGap = if (sorted.size <= 1) 0f else min(minimumGap, (high - low) / (sorted.size - 1))
        val positions = mutableListOf<Float>()
        sorted.forEachIndexed { index, item -> positions += maxOf(item.y.coerceIn(low, high), if (index == 0) low else positions.last() + effectiveGap) }
        val overflow = (positions.lastOrNull() ?: high) - high
        if (overflow > 0f) positions.indices.forEach { positions[it] -= overflow }
        sorted.mapIndexed { index, item -> item.copy(y = positions[index].coerceIn(low, high)) }
    }.sortedBy(PieLabelPlacement::index)
}

@Composable private fun TrendCard(state: RecordsState, vm: RecordsViewModel) {
    Column(Modifier.fillMaxWidth().border(1.dp, BorderColor, MaterialTheme.shapes.large).background(Brush.linearGradient(listOf(SurfaceHigh, SurfaceColor)), MaterialTheme.shapes.large).padding(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) { Column { Text("时长趋势", fontWeight = FontWeight.Medium, color = Primary); Text("一次只比较一种时间线", Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelSmall, color = Muted) }; Row(Modifier.border(1.dp, BorderColor, MaterialTheme.shapes.medium).background(SurfaceColor, MaterialTheme.shapes.medium).padding(2.dp)) { TrendMetric.entries.forEach { metric -> TrendMetricButton(if (metric == TrendMetric.FOCUS) "专注" else "声音", state.query.trendMetric == metric) { vm.setTrend(metric) } } } }
        TrendChart(state.trend, state.query.trendMetric, Modifier.fillMaxWidth().height(110.dp))
    }
}

@Composable private fun TrendMetricButton(label: String, selected: Boolean, click: () -> Unit) {
    Box(Modifier.height(36.dp).background(if (selected) SurfaceHigh else Color.Transparent, MaterialTheme.shapes.small).clickable(onClick = click).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 11.sp, lineHeight = 15.sp, color = if (selected) Primary else Muted)
    }
}

@Composable private fun TrendChart(values: List<TrendPoint>, metric: TrendMetric, modifier: Modifier) {
    val amounts = values.map { if (metric == TrendMetric.FOCUS) it.focusHours else it.audioHours }
    val maxValue = amounts.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    var selectedIndex by remember(metric, values) { mutableStateOf<Int?>(null) }
    val semanticText = selectedIndex?.let { index -> values.getOrNull(index)?.let { "${it.label}，${formatHourValue(amounts[index])}小时" } }
        ?: "共${values.size}个数据点，最大值${formatHourValue(amounts.maxOrNull() ?: 0f)}小时。点击图表可查看数据点"
    Column(modifier) {
        Canvas(
            Modifier.weight(1f).fillMaxWidth()
                .pointerInput(values, metric) {
                    detectTapGestures { tap ->
                        selectedIndex = nearestTrendPointIndex(
                            tapX = tap.x,
                            width = size.width.toFloat(),
                            count = values.size,
                            plotLeft = 36.dp.toPx(),
                            plotRight = size.width.toFloat() - 4.dp.toPx(),
                        )
                    }
                }
                .semantics {
                    contentDescription = semanticText
                    onClick("查看下一个趋势数据点") {
                        if (values.isEmpty()) false else { selectedIndex = ((selectedIndex ?: -1) + 1) % values.size; true }
                    }
                },
        ) {
            val accent = if (metric == TrendMetric.FOCUS) Ambient else Radio
            val areaAlpha = if (metric == TrendMetric.FOCUS) .22f else .16f
            val plotLeft = 36.dp.toPx()
            val plotRight = size.width - 4.dp.toPx()
            val plotTop = 8.dp.toPx()
            val plotBottom = size.height - 8.dp.toPx()
            val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)
            val labelPaint = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply { color = 0xFFA9B3AF.toInt(); textSize = 11.sp.toPx(); textAlign = NativePaint.Align.RIGHT }
            (0..4).forEach { level ->
                val fraction = level / 4f
                val y = plotBottom - plotHeight * fraction
                drawContext.canvas.nativeCanvas.drawText(formatHourValue(maxValue * fraction), plotLeft - 6.dp.toPx(), y + labelPaint.textSize * .35f, labelPaint)
            }
            val step = (plotRight - plotLeft) / (values.size - 1).coerceAtLeast(1)
            val points = amounts.mapIndexed { index, amount -> Offset(plotLeft + index * step, plotBottom - amount * plotHeight / maxValue) }
            if (points.isNotEmpty()) {
                val area = Path().apply {
                    moveTo(points.first().x, plotBottom)
                    lineTo(points.first().x, points.first().y)
                    appendSmoothTrend(points)
                    lineTo(points.last().x, plotBottom)
                    close()
                }
                drawPath(area, Brush.verticalGradient(listOf(accent.copy(alpha = areaAlpha), accent.copy(alpha = 0f)), plotTop, plotBottom))
                val line = Path().apply { moveTo(points.first().x, points.first().y); appendSmoothTrend(points) }
                drawPath(line, accent, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
            }
            points.forEachIndexed { index, point ->
                drawCircle(if (selectedIndex == index) Primary else accent, if (selectedIndex == index) 5.dp.toPx() else 3.dp.toPx(), point)
                if (selectedIndex == index) drawCircle(accent, 8.dp.toPx(), point, style = Stroke(2.dp.toPx()))
            }
            selectedIndex?.takeIf { it in points.indices }?.let { index ->
                val point = points[index]
                drawLine(Ambient.copy(alpha = .15f), Offset(point.x, plotTop), Offset(point.x, plotBottom), 1.dp.toPx())
                val text = "${values[index].label} · ${formatHourValue(amounts[index])}"
                val tooltipPaint = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply { color = 0xFFE9ECE9.toInt(); textSize = 10.sp.toPx() }
                val tooltipWidth = (tooltipPaint.measureText(text) + 16.dp.toPx()).coerceAtMost(size.width - 8.dp.toPx())
                val tooltipHeight = tooltipPaint.textSize + 12.dp.toPx()
                val tooltipX = (point.x - tooltipWidth / 2).coerceIn(4.dp.toPx(), size.width - tooltipWidth - 4.dp.toPx())
                val tooltipY = (point.y - tooltipHeight - 10.dp.toPx()).coerceAtLeast(plotTop)
                drawRoundRect(SurfaceHigh, Offset(tooltipX, tooltipY), Size(tooltipWidth, tooltipHeight), CornerRadius(6.dp.toPx()))
                drawRoundRect(BorderStrong, Offset(tooltipX, tooltipY), Size(tooltipWidth, tooltipHeight), CornerRadius(6.dp.toPx()), style = Stroke(1.dp.toPx()))
                drawContext.canvas.nativeCanvas.drawText(text, tooltipX + 8.dp.toPx(), tooltipY + tooltipPaint.textSize + 4.dp.toPx(), tooltipPaint)
            }
        }
        val labels = if (values.size <= 8) values else values.filterIndexed { index, _ -> index == 0 || index == values.lastIndex || index % ((values.size - 1) / 5).coerceAtLeast(1) == 0 }
        Row(Modifier.fillMaxWidth().padding(start = 36.dp), Arrangement.SpaceBetween) { labels.forEach { Text(it.label, style = MaterialTheme.typography.labelSmall, color = Secondary) } }
    }
}

private fun Path.appendSmoothTrend(points: List<Offset>) {
    points.zipWithNext().forEach { (from, to) ->
        val midpointX = (from.x + to.x) / 2f
        cubicTo(midpointX, from.y, midpointX, to.y, to.x, to.y)
    }
}

internal fun nearestTrendPointIndex(tapX: Float, width: Float, count: Int, plotLeft: Float = 0f, plotRight: Float = width): Int? {
    if (count <= 0 || width <= 0f) return null
    if (count == 1) return 0
    val left = plotLeft.coerceIn(0f, width)
    val right = plotRight.coerceIn(left, width)
    val fraction = ((tapX.coerceIn(left, right) - left) / (right - left).coerceAtLeast(1f)).coerceIn(0f, 1f)
    return (fraction * (count - 1)).roundToInt().coerceIn(0, count - 1)
}

@Composable private fun TargetRelations(values: List<TargetRelation>) {
    CardBlock(padding = 0.dp) {
        Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text("目标与声音关联", fontWeight = FontWeight.Medium, color = Primary); Text("不判断声音是否“提升效率”，只呈现真实共同出现的记录", style = MaterialTheme.typography.labelSmall, color = Muted) }; Icon(activity, null, Modifier.size(18.dp), tint = AmbientLight) }
        HorizontalDivider(color = BorderColor)
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text("专注目标 / 常伴声音", Modifier.weight(1f), fontSize = 10.sp, lineHeight = 14.sp, color = Muted); Text("投入", Modifier.width(70.dp), textAlign = TextAlign.End, fontSize = 10.sp, lineHeight = 14.sp, color = Muted); Text("完成", Modifier.width(54.dp), textAlign = TextAlign.End, fontSize = 10.sp, lineHeight = 14.sp, color = Muted) }
        HorizontalDivider(color = BorderColor.copy(alpha = .7f))
        if (values.isEmpty()) Text("完成专注后，这里会把目标与声音连接起来", Modifier.fillMaxWidth().padding(28.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = Muted)
        values.forEach { item -> Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.name, fontSize = 12.sp, lineHeight = 16.sp, color = Primary); Text("${item.dominantSound} · ${item.sessions} 次", Modifier.padding(top = 4.dp), fontSize = 10.sp, lineHeight = 14.sp, color = AmbientLight) }; Text("${item.minutes}m", Modifier.width(70.dp), textAlign = TextAlign.End, fontSize = 12.sp, lineHeight = 16.sp, color = Secondary, fontFamily = FontFamily.Monospace); Text("${item.completion}%", Modifier.width(54.dp), textAlign = TextAlign.End, fontSize = 12.sp, lineHeight = 16.sp, color = if (item.completion >= 75) AmbientLight else Radio, fontFamily = FontFamily.Monospace) }; HorizontalDivider(color = BorderColor.copy(alpha = .55f)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ReplayCard(event: FocusEvent, state: RecordsState) {
    SectionBorder {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Column { Text("最近一次专注回放", fontWeight = FontWeight.Medium, color = Primary); Text("把计时、声音与复盘放回同一个上下文", style = MaterialTheme.typography.labelSmall, color = Muted) }; StatusTag(event.status) }
        val relatedSounds = (event.soundNames + state.selectedSounds.filter { it.focusEventId == event.id }.map { it.soundName }).distinct()
        val relatedRadioEvent = state.selectedRadios.firstOrNull { it.focusEventId == event.id }
        val relatedRadio = event.radioName ?: relatedRadioEvent?.stationName
        ReplayLine(clock3, event.targetName, "${event.minutes} 分钟 · ${formatEventTime(event.startedAt)}")
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(40.dp).background(SurfaceHigh, CircleShape), contentAlignment = Alignment.Center) { Icon(waves, null, Modifier.size(17.dp), tint = AmbientLight) }
            if (relatedSounds.isEmpty() && relatedRadio == null) {
                Text("未使用声音", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.labelSmall, color = Muted)
            } else {
                FlowRow(Modifier.padding(start = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    relatedSounds.forEach { sound -> Box(Modifier.background(Ambient.copy(alpha = 0.08f), MaterialTheme.shapes.small).padding(horizontal = 8.dp, vertical = 4.dp)) { Text(sound, fontSize = 10.sp, lineHeight = 14.sp, color = AmbientLight) } }
                    relatedRadio?.let { station -> Box(Modifier.background(Radio.copy(alpha = 0.08f), MaterialTheme.shapes.small).padding(horizontal = 8.dp, vertical = 4.dp)) { Text("电台 · $station", fontSize = 10.sp, lineHeight = 14.sp, color = Color(0xFFD8B17D)) } }
                }
            }
        }
        event.note?.takeIf { it.isNotBlank() }?.let { note ->
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(40.dp).background(SurfaceHigh, CircleShape), contentAlignment = Alignment.Center) { Icon(fileText, null, Modifier.size(17.dp), tint = Secondary) }
                Text(note, Modifier.padding(start = 12.dp), fontSize = 12.sp, lineHeight = 18.sp, color = Secondary)
            }
        }
    }
}

@Composable private fun CompletionCard(state: RecordsState) {
    CardBlock {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Column { Text("会话质量", fontWeight = FontWeight.Medium, color = Primary); Text("完成与中断分开记录，不用时长掩盖节奏", style = MaterialTheme.typography.labelSmall, color = Muted) }; Text("${state.completionRate}%", style = MaterialTheme.typography.headlineSmall, color = AmbientLight, fontFamily = FontFamily.Monospace) }
        LinearProgressIndicator({ state.completionRate / 100f }, Modifier.fillMaxWidth().padding(top = 12.dp).height(8.dp), color = Ambient, trackColor = SurfaceHigh)
        Row(Modifier.fillMaxWidth().padding(top = 7.dp), Arrangement.SpaceBetween) { Text("完成 ${state.completedCount} 次", style = MaterialTheme.typography.labelSmall, color = Secondary); Text("中断 ${state.selectedFocus.size - state.completedCount} 次", style = MaterialTheme.typography.labelSmall, color = Secondary) }
    }
}

@Composable private fun BarBlock(title: String, subtitle: String, values: List<Pair<String, Int>>, color: Color, trailingLabel: String) {
    SectionBorder {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Column { Text(title, fontWeight = FontWeight.Medium, color = Primary); Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Muted) }; Text(trailingLabel, style = MaterialTheme.typography.labelSmall, color = Muted) }
        val max = values.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
        Row(Modifier.fillMaxWidth().height(96.dp).padding(top = 14.dp), Arrangement.spacedBy(12.dp), Alignment.Bottom) {
            values.forEach { (_, count) ->
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Text(count.toString(), fontSize = 10.sp, lineHeight = 14.sp, color = Secondary, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(5.dp))
                    Box(Modifier.fillMaxWidth().height((62f * count / max).coerceAtLeast(4f).dp).background(color, MaterialTheme.shapes.small))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 7.dp), Arrangement.spacedBy(12.dp)) {
            values.forEach { (label, _) -> Text(label, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 10.sp, lineHeight = 14.sp, color = Muted, maxLines = 1) }
        }
    }
}

/** App.tsx 本月专注时段分布——渐变卡片 + 柱高 `max(6, count/max*72)%`（相对 112dp 容器）。 */
@Composable private fun MonthlyTimeOfDay(values: List<Pair<String, Int>>, monthLabel: String) {
    GradientCard {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text("本月专注时段分布", fontWeight = FontWeight.Medium, color = Primary); Text(monthLabel, style = MaterialTheme.typography.labelSmall, color = Secondary) }
        val max = values.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
        Row(Modifier.fillMaxWidth().height(88.dp).padding(top = 12.dp), Arrangement.spacedBy(8.dp), Alignment.Bottom) {
            values.forEach { (_, count) ->
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Text(count.toString(), fontSize = 10.sp, lineHeight = 14.sp, color = Secondary, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(5.dp))
                    Box(Modifier.fillMaxWidth().height((52f * count / max).coerceAtLeast(5f).dp).background(Ambient.copy(alpha = .7f), androidx.compose.foundation.shape.RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 7.dp), Arrangement.spacedBy(8.dp)) {
            values.forEach { (label, _) -> Text(label, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, lineHeight = 14.sp, color = Secondary) }
        }
    }
}

@Composable private fun DetailTabs(selected: DetailSection, change: (DetailSection) -> Unit) { Segmented(DetailSection.entries, selected, { when (it) { DetailSection.FOCUS -> "专注"; DetailSection.SOUNDS -> "声音"; DetailSection.RADIO -> "电台"; DetailSection.SLEEP -> "睡眠" } }, change) }

@Composable private fun Heatmap(month: LocalDate, values: List<Int>) {
    var selectedDay by remember(month, values) { mutableStateOf<Int?>(null) }
    GradientCard {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text("本月使用热力图", fontWeight = FontWeight.Medium, color = Primary); Text("${month.year}年${month.monthValue}月 · ${values.size} 天", style = MaterialTheme.typography.labelSmall, color = Muted) }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), Arrangement.spacedBy(6.dp)) { listOf("一", "二", "三", "四", "五", "六", "日").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = Muted) } }
        val maxHeatMinutes = values.maxOrNull()?.coerceAtLeast(1) ?: 1
        val heat = values.map { (it * 5f / maxHeatMinutes).roundToInt() }
        values.chunked(7).forEachIndexed { weekIndex, week ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), Arrangement.spacedBy(6.dp)) {
                week.forEachIndexed { dayInWeek, value ->
                    val day = weekIndex * 7 + dayInWeek + 1
                    val date = month.withDayOfMonth(day)
                    val description = heatmapDayDescription(date, value)
                    Box(
                        Modifier.weight(1f).aspectRatio(1f)
                            .background(Ambient.copy(alpha = .06f + heat[day - 1] * .12f), MaterialTheme.shapes.small)
                            .border(if (selectedDay == day) 2.dp else 1.dp, if (selectedDay == day) AmbientLight else BorderColor.copy(alpha = .45f), MaterialTheme.shapes.small)
                            .pointerInput(day) { detectTapGestures { selectedDay = day } }
                            .semantics {
                                contentDescription = description
                                onClick("查看${date}的使用分钟") { selectedDay = day; true }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selectedDay == day) Text(day.toString(), style = MaterialTheme.typography.labelSmall, color = Primary, fontFamily = FontFamily.Monospace)
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        selectedDay?.let { day ->
            val value = values.getOrNull(day - 1) ?: 0
            Surface(
                Modifier.fillMaxWidth().padding(top = 10.dp).semantics { contentDescription = heatmapDayDescription(month.withDayOfMonth(day), value) },
                color = SurfaceHigh,
                shape = MaterialTheme.shapes.small,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderStrong),
            ) {
                Text(heatmapDayDescription(month.withDayOfMonth(day), value), Modifier.padding(horizontal = 12.dp, vertical = 9.dp), style = MaterialTheme.typography.labelSmall, color = Primary)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text("少", style = MaterialTheme.typography.labelSmall, color = Muted)
            (0..4).forEach { level -> Box(Modifier.padding(start = 4.dp).size(10.dp).background(Ambient.copy(alpha = .06f + level * .12f), MaterialTheme.shapes.extraSmall).border(1.dp, BorderColor.copy(alpha = .45f), MaterialTheme.shapes.extraSmall)) }
            Text("多", Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelSmall, color = Muted)
        }
    }
}

internal fun heatmapDayDescription(date: LocalDate, minutes: Int): String =
    "${date.year}年${date.monthValue}月${date.dayOfMonth}日，${if (minutes <= 0) "无记录" else "使用${minutes}分钟"}"

@Composable
private fun RecentFocus(events: List<FocusEvent>, sounds: List<SoundUsageEvent>, radios: List<RadioUsageEvent>) {
    SectionBorder {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text("最近专注", fontWeight = FontWeight.Medium, color = Primary)
            Text("完整记录", style = MaterialTheme.typography.labelSmall, color = Muted)
        }
        if (events.isEmpty()) {
            Text("该时间范围内暂无专注记录", Modifier.fillMaxWidth().padding(28.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = Muted)
        }
        events.take(8).forEach { event ->
            val snapshotParts = buildList {
                if (event.soundNames.isNotEmpty()) {
                    val prefix = if (event.audioSource == "CHANNEL_RECIPE") "频道声场" else "个人组合"
                    add("$prefix · ${event.soundNames.distinct().joinToString("、")}")
                }
                event.radioName?.let { name ->
                    add("电台 · $name")
                }
            }
            // Only legacy records may fall back to playback-event time correlation.
            val legacyParts = if (event.audioSource == "UNRECORDED") {
                sounds.filter { it.focusEventId == event.id }.map { it.soundName } +
                    radios.filter { it.focusEventId == event.id }.map { "电台 · ${it.stationName}" }
            } else emptyList()
            val used = when {
                snapshotParts.isNotEmpty() -> snapshotParts.distinct().joinToString(" · ")
                legacyParts.isNotEmpty() -> legacyParts.distinct().joinToString(" · ")
                event.audioSource == "SILENT" -> "静音"
                event.audioSource == "UNRECORDED" -> "未记录声音"
                else -> "未记录声音"
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).background((if (event.status == SessionStatus.COMPLETED) Ambient else Danger).copy(alpha = .1f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(clock3, null, Modifier.size(17.dp), tint = if (event.status == SessionStatus.COMPLETED) AmbientLight else Danger)
                }
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(event.targetName, style = MaterialTheme.typography.labelSmall, color = Primary)
                    Text("${formatEventTime(event.startedAt)} · $used", style = MaterialTheme.typography.labelSmall, color = Muted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${event.minutes} 分", style = MaterialTheme.typography.labelSmall, color = Secondary, fontFamily = FontFamily.Monospace)
                    Text(if (event.status == SessionStatus.COMPLETED) "完成" else "中断", style = MaterialTheme.typography.labelSmall, color = Muted)
                }
            }
        }
    }
}

@Composable private fun RankSection(title: String, rows: List<Triple<String, String, String>>, icon: ImageVector, accent: Color, emptyMessage: String = "当前范围内没有环境声使用记录") { SectionBorder { Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text(title, fontWeight = FontWeight.Medium, color = Primary); Text("按时长排序", style = MaterialTheme.typography.labelSmall, color = Muted) }; if (rows.isEmpty()) Text(emptyMessage, Modifier.fillMaxWidth().padding(28.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = Muted); rows.take(8).forEach { (name, subtitle, value) -> Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(18.dp), tint = accent); Column(Modifier.weight(1f).padding(horizontal = 10.dp)) { Text(name, style = MaterialTheme.typography.labelSmall, color = Primary, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Muted) }; Text(value, style = MaterialTheme.typography.labelSmall, color = Secondary, fontFamily = FontFamily.Monospace) } } } }

@Composable private fun FrequentSoundCombination(sounds: List<SoundRank>) {
    if (sounds.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text("最常相伴的组合", style = MaterialTheme.typography.labelSmall, color = Muted, letterSpacing = 1.8.sp)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row { sounds.take(3).forEach { Box(Modifier.size(36.dp).border(2.dp, SurfaceColor, CircleShape).background(SurfaceHigh, CircleShape), contentAlignment = Alignment.Center) { Icon(audioLines, null, Modifier.size(16.dp), tint = AmbientLight) } } }
            Column(Modifier.padding(start = 12.dp)) { Text("高频声音组合", style = MaterialTheme.typography.bodySmall, color = Primary); Text("${sounds.take(3).joinToString(" · ") { it.name }}，来自当前统计范围", style = MaterialTheme.typography.labelSmall, color = Secondary) }
        }
    }
}

@Composable private fun SoundRelations(values: List<SoundRelation>) {
    CardBlock(padding = 0.dp) {
        Column(Modifier.padding(16.dp)) { Text("声音与完成状态", fontWeight = FontWeight.Medium, color = Primary); Text("按实际共同出现的专注会话统计", style = MaterialTheme.typography.labelSmall, color = Muted) }
        HorizontalDivider(color = BorderColor)
        if (values.isEmpty()) Text("当前范围内没有声音与专注共同出现的记录", Modifier.fillMaxWidth().padding(28.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = Muted)
        values.forEach { item -> Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.name, style = MaterialTheme.typography.labelSmall, color = Primary); Text("${item.targetCount} 个目标 · ${item.sessions} 次", style = MaterialTheme.typography.labelSmall, color = Muted) }; Text("${item.minutes}m", Modifier.width(52.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall, color = Secondary, fontFamily = FontFamily.Monospace); Text("${item.completion}%", Modifier.width(50.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall, color = AmbientLight, fontFamily = FontFamily.Monospace) } }
    }
}

@Composable private fun ContextMoments(values: List<ContextMoment>) { SectionBorder { Text("时段与声音倾向", fontWeight = FontWeight.Medium, color = Primary); Text("查看一天中不同声音如何陪伴你", style = MaterialTheme.typography.labelSmall, color = Muted); values.forEach { item -> Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) { Text(item.label, Modifier.width(42.dp), style = MaterialTheme.typography.labelSmall, color = Secondary); Column(Modifier.weight(1f)) { Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text(item.sounds.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = Primary); Text("${item.percentage}%", style = MaterialTheme.typography.labelSmall, color = Muted, fontFamily = FontFamily.Monospace) }; LinearProgressIndicator({ item.percentage / 100f }, Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp), color = ChartGreen, trackColor = SurfaceHigh) } } } } }

@Composable private fun RadioRhythm(values: List<Int>) {
    SectionBorder {
        Text("收听节律", style = MaterialTheme.typography.labelSmall, color = Muted, letterSpacing = 2.sp)
        val max = values.maxOrNull()?.coerceAtLeast(1) ?: 1
        Row(Modifier.fillMaxWidth().height(82.dp).padding(top = 12.dp), Arrangement.spacedBy(8.dp), Alignment.Bottom) {
            values.forEach { minutes ->
                Box(Modifier.weight(1f).height((66f * minutes / max).coerceAtLeast(4f).dp).background(Radio.copy(alpha = .65f), androidx.compose.foundation.shape.RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 7.dp), Arrangement.spacedBy(8.dp)) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Text(label, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 10.sp, lineHeight = 14.sp, color = Muted)
            }
        }
    }
}

@Composable private fun CategoryBars(sounds: List<SoundRank>) { GradientCard { Text("环境声分类偏好", fontWeight = FontWeight.Medium, color = Primary); val groups = sounds.groupBy { it.category }.mapValues { it.value.sumOf(SoundRank::minutes) }.entries.sortedByDescending { it.value }; val max = groups.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1; if (groups.isEmpty()) Text("当前范围内没有可统计的声音分类", Modifier.fillMaxWidth().padding(28.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = Muted); groups.forEach { (name, minutes) -> Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text(name, style = MaterialTheme.typography.labelSmall, color = Primary); Text(formatMinutes(minutes), style = MaterialTheme.typography.labelSmall, color = Secondary) }; LinearProgressIndicator({ minutes / max.toFloat() }, Modifier.fillMaxWidth().padding(vertical = 5.dp), color = Ambient, trackColor = SurfaceHigh) } } }

@Composable private fun SleepReview(state: RecordsState) {
    val completed = state.selectedSleeps.filter { it.status == SleepStatus.COMPLETED }
    val average = if (completed.isEmpty()) 0 else completed.sumOf { it.plannedMinutes } / completed.size
    SectionBorder {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Bottom) {
            Column(Modifier.weight(1f)) { Text("睡眠定时回顾", fontWeight = FontWeight.Medium, color = Primary); Text("只统计定时方案，不推断真实睡眠质量", style = MaterialTheme.typography.labelSmall, color = Muted) }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.Bottom) { Text(average.toString(), fontSize = 24.sp, lineHeight = 32.sp, color = Primary, fontFamily = FontFamily.Monospace); Text("分", Modifier.padding(start = 4.dp), fontSize = 12.sp, lineHeight = 16.sp, color = Secondary) }
                Text("平均计划时长", Modifier.padding(top = 2.dp), fontSize = 10.sp, lineHeight = 14.sp, color = Muted)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            SleepMetric("启动次数", state.selectedSleeps.size.toString(), Primary, Modifier.weight(1f))
            SleepMetric("自然结束", completed.size.toString(), AmbientLight, Modifier.weight(1f))
            SleepMetric("已存方案", state.sleepRoutineCount.toString(), Radio, Modifier.weight(1f))
        }
    }
    CardBlock {
        Text("最近睡眠定时", fontWeight = FontWeight.Medium, color = Primary)
        Column(Modifier.padding(top = 12.dp)) {
            val recent = state.selectedSleeps.take(8)
            recent.forEachIndexed { index, sleep ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(moon, null, Modifier.size(16.dp), tint = if (sleep.status == SleepStatus.COMPLETED) AmbientLight else Muted)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("${sleep.target.label} · ${sleep.plannedMinutes} 分钟", fontSize = 12.sp, lineHeight = 16.sp, color = Primary)
                        Text("${formatEventTime(sleep.startedAt)} · 渐弱 ${sleep.fadeMinutes} 分钟", Modifier.padding(top = 2.dp), fontSize = 10.sp, lineHeight = 14.sp, color = Muted)
                    }
                    Text(if (sleep.status == SleepStatus.COMPLETED) "完成" else "取消", fontSize = 10.sp, lineHeight = 14.sp, color = if (sleep.status == SleepStatus.COMPLETED) AmbientLight else Muted)
                }
                if (index < recent.lastIndex) HorizontalDivider(color = BorderColor.copy(alpha = .7f))
            }
            if (recent.isEmpty()) Text("当前范围内没有睡眠定时记录", Modifier.fillMaxWidth().padding(28.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = Muted)
        }
    }
}

@Composable private fun SleepMetric(label: String, value: String, valueColor: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, lineHeight = 24.sp, color = valueColor, fontFamily = FontFamily.Monospace)
        Text(label, Modifier.padding(top = 2.dp), fontSize = 10.sp, lineHeight = 14.sp, color = Muted)
    }
}

@Composable private fun ReplayLine(icon: ImageVector, title: String, subtitle: String?, tint: Color = AmbientLight) { Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(40.dp).background(SurfaceHigh, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(17.dp), tint = tint) }; Column(Modifier.padding(start = 12.dp)) { Text(title, fontSize = 14.sp, lineHeight = 20.sp, color = Primary); subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Secondary) } } } }
@Composable private fun StatusTag(status: SessionStatus) { Text(if (status == SessionStatus.COMPLETED) "已完成" else "已中断", Modifier.background((if (status == SessionStatus.COMPLETED) Ambient else Danger).copy(alpha = .1f), MaterialTheme.shapes.small).padding(horizontal = 8.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = if (status == SessionStatus.COMPLETED) AmbientLight else Danger) }
@Composable private fun Metric(label: String, value: String, unit: String, modifier: Modifier) { Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) { Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) { Text(value, style = MaterialTheme.typography.headlineSmall, color = Primary, fontFamily = FontFamily.Monospace); Text(unit, style = MaterialTheme.typography.labelSmall, color = Secondary) }; Text(label, style = MaterialTheme.typography.labelSmall, color = Muted) } }
@Composable private fun SectionBorder(content: @Composable ColumnScope.() -> Unit) { Column(Modifier.fillMaxWidth()) { HorizontalDivider(color = BorderColor); Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), content = content); HorizontalDivider(color = BorderColor) } }
@Composable private fun CardBlock(padding: androidx.compose.ui.unit.Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) { Column(Modifier.fillMaxWidth().border(1.dp, BorderColor, MaterialTheme.shapes.medium).background(SurfaceLow, MaterialTheme.shapes.medium).padding(padding), content = content) }
@Composable private fun GradientCard(padding: androidx.compose.ui.unit.Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) { Column(Modifier.fillMaxWidth().border(1.dp, BorderColor, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(SurfaceHigh, SurfaceColor)), androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).padding(padding), content = content) }
@Composable private fun <T> Segmented(values: List<T>, selected: T, label: (T) -> String, change: (T) -> Unit) { Row(Modifier.fillMaxWidth().border(1.dp, BorderColor, MaterialTheme.shapes.large).background(SurfaceColor, MaterialTheme.shapes.large).padding(4.dp), Arrangement.spacedBy(4.dp)) { values.forEach { SegmentButton(label(it), selected == it, Modifier.weight(1f)) { change(it) } } } }
@Composable private fun SegmentButton(label: String, selected: Boolean, modifier: Modifier = Modifier, click: () -> Unit) { Box(modifier.heightIn(min = 40.dp).background(if (selected) SurfaceHigh else Color.Transparent, MaterialTheme.shapes.medium).border(1.dp, if (selected) BorderStrong else Color.Transparent, MaterialTheme.shapes.medium).clickable(onClick = click), contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) Primary else Muted, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) } }

private val StatsRange.label get() = when (this) { StatsRange.TODAY -> "今日"; StatsRange.LAST_7_DAYS -> "近7天"; StatsRange.MONTH -> "本月"; StatsRange.CUSTOM -> "自定义" }
private val SleepTarget.label get() = when (this) { SleepTarget.ALL -> "全部声音"; SleepTarget.AMBIENT -> "环境声"; SleepTarget.RADIO -> "电台" }
private fun formatMinutes(value: Int) = if (value >= 60) "${value / 60}h ${value % 60}m" else "${value}m"
private fun formatMinutesChinese(value: Int) = if (value >= 60) "${value / 60}小时${value % 60}分" else "${value}分钟"
private fun metricValue(value: Int) = if (value >= 60) "${value / 60}h" else value.toString()
private fun metricUnit(value: Int) = if (value >= 60) "${value % 60}m" else "m"
private fun formatEventTime(value: Long): String = DateTimeFormatter.ofPattern("M/d HH:mm").format(Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()))
private fun percent1(minutes: Int, total: Int): Float = (minutes * 1000f / total).roundToInt() / 10f
private fun percentLabel(value: Float): String { val tenths = (value * 10).roundToInt(); return if (tenths % 10 == 0) "${tenths / 10}%" else "${tenths / 10}.${tenths % 10}%" }
private fun roundHours(minutes: Int): Float = (minutes * 100f / 60f).roundToInt() / 100f
private fun formatHourValue(value: Float): String { val hundredths = (value * 100).roundToInt(); val whole = hundredths / 100; val frac = hundredths % 100; return when { frac == 0 -> "$whole"; frac % 10 == 0 -> "$whole.${frac / 10}"; else -> "$whole.${frac.toString().padStart(2, '0')}" } }
private fun monthLabel(month: LocalDate): String = "${month.year}年${month.monthValue.toString().padStart(2, '0')}月"

private val CanvasColor = Color(0xFF080B0D)
private val SurfaceLow = Color(0xFF101719)
private val SurfaceColor = Color(0xFF161E21)
private val SurfaceHigh = Color(0xFF1E282B)
private val BorderColor = Color(0xFF314044)
private val BorderStrong = Color(0xFF43565A)
private val Primary = Color(0xFFE9ECE9)
private val Secondary = Color(0xFFA9B3AF)
private val Muted = Color(0xFF929D99)
private val Ambient = Color(0xFF55B6A3)
private val AmbientLight = Color(0xFF91D3C5)
private val Radio = Color(0xFFC99662)
private val Danger = Color(0xFFD57478)
private val ChartGreen = Color(0xFF64AFA1)
