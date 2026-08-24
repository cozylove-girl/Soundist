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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

private val readableBackupJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
}

internal fun encodeDatabaseSnapshot(snapshot: DatabaseSnapshot): String {
    val root = JsonObject(
    linkedMapOf(
        "schemaVersion" to JsonPrimitive(2),
        "exportedAtEpochMillis" to JsonPrimitive(snapshot.exportedAtEpochMillis),
        "presets" to JsonArray(snapshot.presets.map { row -> JsonObject(mapOf(
            "id" to JsonPrimitive(row.id), "name" to JsonPrimitive(row.name),
            "tracksJson" to JsonPrimitive(row.tracksJson), "purposeLabel" to nullable(row.purposeLabel),
            "builtIn" to JsonPrimitive(row.builtIn), "updatedAt" to JsonPrimitive(row.updatedAt),
        )) }),
        "radioStations" to JsonArray(snapshot.radios.map { row -> JsonObject(mapOf(
            "id" to JsonPrimitive(row.id), "name" to JsonPrimitive(row.name), "streamUri" to JsonPrimitive(row.streamUri),
            "artworkUri" to nullable(row.artworkUri), "favorite" to JsonPrimitive(row.favorite),
            "description" to JsonPrimitive(row.description), "sourceKind" to JsonPrimitive(row.sourceKind),
            "genre" to JsonPrimitive(row.genre), "purpose" to JsonPrimitive(row.purpose),
            "position" to JsonPrimitive(row.position), "enabled" to JsonPrimitive(row.enabled), "updatedAt" to JsonPrimitive(row.updatedAt),
            "payloadJson" to nullable(row.payloadJson),
        )) }),
        "playback" to JsonArray(snapshot.playback.map { row -> JsonObject(mapOf(
            "key" to JsonPrimitive(row.key), "tracksJson" to JsonPrimitive(row.tracksJson), "radioId" to nullable(row.radioId),
            "playing" to JsonPrimitive(row.playing), "masterVolume" to JsonPrimitive(row.masterVolume),
            "ambientVolume" to JsonPrimitive(row.ambientVolume), "radioVolume" to JsonPrimitive(row.radioVolume), "radioTrackIndex" to JsonPrimitive(row.radioTrackIndex), "updatedAt" to JsonPrimitive(row.updatedAt),
        )) }),
        "focusSessions" to JsonArray(snapshot.focusSessions.map { row -> JsonObject(mapOf(
            "id" to JsonPrimitive(row.id), "kind" to JsonPrimitive(row.kind), "status" to JsonPrimitive(row.status),
            "startedAt" to JsonPrimitive(row.startedAt), "targetEndAt" to nullableLong(row.targetEndAt),
            "pausedAt" to nullableLong(row.pausedAt), "completedAt" to nullableLong(row.completedAt),
            "label" to nullable(row.label), "targetKind" to JsonPrimitive(row.targetKind), "targetId" to nullable(row.targetId),
            "timerMode" to JsonPrimitive(row.timerMode), "elapsedSeconds" to JsonPrimitive(row.elapsedSeconds),
            "review" to nullable(row.review), "soundSnapshotJson" to JsonPrimitive(row.soundSnapshotJson),
            "radioSnapshotId" to nullable(row.radioSnapshotId), "phase" to JsonPrimitive(row.phase),
            "focusMinutes" to JsonPrimitive(row.focusMinutes), "breakMinutes" to JsonPrimitive(row.breakMinutes),
            "cycleRound" to JsonPrimitive(row.cycleRound), "cycleRounds" to JsonPrimitive(row.cycleRounds),
            "longBreakMinutes" to JsonPrimitive(row.longBreakMinutes), "autoBreak" to JsonPrimitive(row.autoBreak),
            "autoFocus" to JsonPrimitive(row.autoFocus), "completionMinutes" to JsonPrimitive(row.completionMinutes),
            "ambientMode" to nullable(row.ambientMode), "audioSource" to JsonPrimitive(row.audioSource),
        )) }),
        "todos" to JsonArray(snapshot.todos.map { row -> JsonObject(mapOf(
            "id" to JsonPrimitive(row.id), "title" to JsonPrimitive(row.title), "completed" to JsonPrimitive(row.completed),
            "dueAt" to nullableLong(row.dueAt), "updatedAt" to JsonPrimitive(row.updatedAt), "longTerm" to JsonPrimitive(row.longTerm),
            "allDay" to JsonPrimitive(row.allDay), "priority" to JsonPrimitive(row.priority), "reminderAt" to nullableLong(row.reminderAt),
            "estimatedMinutes" to nullableInt(row.estimatedMinutes), "planId" to nullable(row.planId), "note" to JsonPrimitive(row.note),
            "defaultSceneId" to nullable(row.defaultSceneId), "archived" to JsonPrimitive(row.archived), "position" to JsonPrimitive(row.position),
        )) }),
        "plans" to JsonArray(snapshot.plans.map { row -> JsonObject(mapOf(
            "id" to JsonPrimitive(row.id), "title" to JsonPrimitive(row.title), "startAt" to JsonPrimitive(row.startAt),
            "endAt" to nullableLong(row.endAt), "updatedAt" to JsonPrimitive(row.updatedAt), "targetMinutes" to JsonPrimitive(row.targetMinutes),
            "accumulatedMinutes" to JsonPrimitive(row.accumulatedMinutes), "milestone" to JsonPrimitive(row.milestone),
            "description" to JsonPrimitive(row.description), "reminderAt" to nullableLong(row.reminderAt),
            "defaultSceneId" to nullable(row.defaultSceneId), "archived" to JsonPrimitive(row.archived), "position" to JsonPrimitive(row.position),
        )) }),
        "habits" to JsonArray(snapshot.habits.map { row -> JsonObject(mapOf(
            "id" to JsonPrimitive(row.id), "name" to JsonPrimitive(row.name), "targetPerWeek" to JsonPrimitive(row.targetPerWeek),
            "updatedAt" to JsonPrimitive(row.updatedAt), "weekdaysJson" to JsonPrimitive(row.weekdaysJson), "metric" to JsonPrimitive(row.metric),
            "dailyTarget" to JsonPrimitive(row.dailyTarget), "streak" to JsonPrimitive(row.streak), "reminderAt" to nullableLong(row.reminderAt),
            "defaultSceneId" to nullable(row.defaultSceneId), "archived" to JsonPrimitive(row.archived), "position" to JsonPrimitive(row.position),
            "reminderHour" to JsonPrimitive(row.reminderHour), "reminderMinute" to JsonPrimitive(row.reminderMinute),
        )) }),
        "habitChecks" to JsonArray(snapshot.habitChecks.map { row -> JsonObject(mapOf(
            "id" to JsonPrimitive(row.id), "habitId" to JsonPrimitive(row.habitId), "checkedAt" to JsonPrimitive(row.checkedAt), "value" to JsonPrimitive(row.value),
        )) }),
        "countdowns" to JsonArray(snapshot.countdowns.map { row -> JsonObject(mapOf(
            "id" to JsonPrimitive(row.id), "title" to JsonPrimitive(row.title), "targetAt" to JsonPrimitive(row.targetAt),
            "updatedAt" to JsonPrimitive(row.updatedAt), "note" to JsonPrimitive(row.note), "dailyMinutes" to JsonPrimitive(row.dailyMinutes),
            "investedMinutes" to JsonPrimitive(row.investedMinutes), "planId" to nullable(row.planId), "milestone" to JsonPrimitive(row.milestone),
            "reminderAt" to nullableLong(row.reminderAt), "archived" to JsonPrimitive(row.archived), "position" to JsonPrimitive(row.position),
        )) }),
        "sleepRoutines" to JsonArray(snapshot.sleepRoutines.map { row -> JsonObject(mapOf(
            "id" to JsonPrimitive(row.id), "name" to JsonPrimitive(row.name), "minutes" to JsonPrimitive(row.minutes),
            "target" to JsonPrimitive(row.target), "fadeMinutes" to JsonPrimitive(row.fadeMinutes), "endMode" to JsonPrimitive(row.endMode),
            "clockTime" to nullable(row.clockTime), "sceneId" to nullable(row.sceneId), "enabled" to JsonPrimitive(row.enabled), "updatedAt" to JsonPrimitive(row.updatedAt),
        )) }),
        "sleepSessions" to JsonArray(snapshot.sleepSessions.map { row -> JsonObject(mapOf(
            "id" to JsonPrimitive(row.id), "startedAt" to JsonPrimitive(row.startedAt), "targetEndAt" to JsonPrimitive(row.targetEndAt),
            "actualEndAt" to nullableLong(row.actualEndAt), "target" to JsonPrimitive(row.target), "fadeMinutes" to JsonPrimitive(row.fadeMinutes),
            "status" to JsonPrimitive(row.status), "routineId" to nullable(row.routineId), "volumeSnapshotCaptured" to JsonPrimitive(row.volumeSnapshotCaptured),
            "masterVolumeSnapshot" to JsonPrimitive(row.masterVolumeSnapshot), "ambientVolumeSnapshot" to JsonPrimitive(row.ambientVolumeSnapshot),
            "radioVolumeSnapshot" to JsonPrimitive(row.radioVolumeSnapshot),
        )) }),
        "notebooks" to JsonArray(snapshot.notebooks.map { row -> JsonObject(mapOf(
            "id" to JsonPrimitive(row.id), "title" to JsonPrimitive(row.title), "updatedAt" to JsonPrimitive(row.updatedAt),
            "position" to JsonPrimitive(row.position), "accent" to JsonPrimitive(row.accent), "iconKey" to JsonPrimitive(row.iconKey),
        )) }),
        "notes" to JsonArray(snapshot.notes.map { row -> JsonObject(mapOf(
            "id" to JsonPrimitive(row.id), "notebookId" to nullable(row.notebookId), "title" to JsonPrimitive(row.title),
            "body" to JsonPrimitive(row.body), "archived" to JsonPrimitive(row.archived), "deletedAt" to nullableLong(row.deletedAt),
            "updatedAt" to JsonPrimitive(row.updatedAt), "createdAt" to JsonPrimitive(row.createdAt), "pinned" to JsonPrimitive(row.pinned),
            "tagsJson" to JsonPrimitive(row.tagsJson), "originalNotebookId" to nullable(row.originalNotebookId), "position" to JsonPrimitive(row.position),
        )) }),
        "noteBlocks" to JsonArray(snapshot.noteBlocks.map { row -> JsonObject(mapOf(
            "id" to JsonPrimitive(row.id), "noteId" to JsonPrimitive(row.noteId), "kind" to JsonPrimitive(row.kind),
            "text" to JsonPrimitive(row.text), "checked" to JsonPrimitive(row.checked), "assetId" to nullable(row.assetId),
            "linkedNoteId" to nullable(row.linkedNoteId), "position" to JsonPrimitive(row.position), "revision" to JsonPrimitive(row.revision),
        )) }),
        "attachments" to JsonArray(snapshot.attachments.map { row -> JsonObject(mapOf(
            "id" to JsonPrimitive(row.id), "noteId" to JsonPrimitive(row.noteId), "mimeType" to JsonPrimitive(row.mimeType),
            "localUri" to JsonPrimitive(row.localUri), "remotePath" to nullable(row.remotePath), "updatedAt" to JsonPrimitive(row.updatedAt),
            "kind" to JsonPrimitive(row.kind), "displayName" to JsonPrimitive(row.displayName), "sizeBytes" to JsonPrimitive(row.sizeBytes),
            "durationSeconds" to nullableLong(row.durationSeconds), "width" to nullableInt(row.width), "height" to nullableInt(row.height),
            "thumbnailUri" to nullable(row.thumbnailUri), "checksum" to nullable(row.checksum), "uploadState" to JsonPrimitive(row.uploadState),
        )) }),
        "playbackEvents" to JsonArray(snapshot.playbackEvents.map { row -> JsonObject(mapOf(
            "id" to JsonPrimitive(row.id), "kind" to JsonPrimitive(row.kind), "sourceId" to JsonPrimitive(row.sourceId),
            "startedAt" to JsonPrimitive(row.startedAt), "endedAt" to nullableLong(row.endedAt), "activeSeconds" to JsonPrimitive(row.activeSeconds),
            "completed" to JsonPrimitive(row.completed), "trackId" to nullable(row.trackId), "sourceKind" to nullable(row.sourceKind),
            "listenedMs" to JsonPrimitive(row.listenedMs), "completionReason" to nullable(row.completionReason),
        )) }),
        ),
    )
    return readableBackupJson.encodeToString(JsonObject.serializer(), root)
}

private fun nullable(value: String?) = if (value == null) kotlinx.serialization.json.JsonNull else JsonPrimitive(value)
private fun nullableLong(value: Long?) = if (value == null) kotlinx.serialization.json.JsonNull else JsonPrimitive(value)
private fun nullableInt(value: Int?) = if (value == null) kotlinx.serialization.json.JsonNull else JsonPrimitive(value)

internal fun decodeDatabaseSnapshot(json: String): DatabaseSnapshot {
    val root = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull()
        ?: throw IllegalArgumentException("备份文件不是有效的 JSON")
    val schemaVersion = root.int("schemaVersion", 0)
    if (schemaVersion !in 1..2) throw IllegalArgumentException("不支持的 Soundist 备份版本：$schemaVersion")
    val requiredSections = setOf("presets", "radioStations", "playback", "focusSessions", "todos", "plans", "habits", "habitChecks", "countdowns", "sleepRoutines", "sleepSessions", "notebooks", "notes", "noteBlocks", "attachments", "playbackEvents")
    if (!requiredSections.all { root[it] is JsonArray }) throw IllegalArgumentException("备份文件不完整，缺少必要数据表")
    return DatabaseSnapshot(
        exportedAtEpochMillis = root.long("exportedAtEpochMillis"),
        presets = root.arr("presets").map { PresetEntity(it.str("id"), it.str("name"), it.str("tracksJson"), it.strOrNull("purposeLabel"), it.bool("builtIn"), it.long("updatedAt")) },
        radios = root.arr("radioStations").map { RadioEntity(it.str("id"), it.str("name"), it.str("streamUri"), it.strOrNull("artworkUri"), it.bool("favorite"), it.str("description"), it.str("sourceKind"), it.str("genre"), it.str("purpose"), it.dbl("position"), it.bool("enabled"), it.long("updatedAt"), it.strOrNull("payloadJson")) },
        playback = root.arr("playback").map { PlaybackEntity(it.str("key", "current"), it.str("tracksJson"), it.strOrNull("radioId"), it.bool("playing"), it.flt("masterVolume", 0.8f).coerceIn(0f, 1f), it.flt("ambientVolume", 1f).coerceIn(0f, 1f), it.flt("radioVolume", 0.8f).coerceIn(0f, 1f), it.int("radioTrackIndex").coerceAtLeast(0), it.long("updatedAt")) },
        focusSessions = root.arr("focusSessions").map { FocusSessionEntity(it.str("id"), it.str("kind"), it.str("status"), it.long("startedAt"), it.longOrNull("targetEndAt"), it.longOrNull("pausedAt"), it.longOrNull("completedAt"), it.strOrNull("label"), it.str("targetKind"), it.strOrNull("targetId"), it.str("timerMode"), it.long("elapsedSeconds"), it.strOrNull("review"), it.str("soundSnapshotJson"), it.strOrNull("radioSnapshotId"), it.str("phase", "FOCUS"), it.int("focusMinutes", 25), it.int("breakMinutes", 5), it.int("cycleRound", 1), it.int("cycleRounds", 4), it.int("longBreakMinutes", 15), it.bool("autoBreak", true), it.bool("autoFocus"), it.int("completionMinutes"), it.strOrNull("ambientMode"), it.str("audioSource", "UNRECORDED")) },
        todos = root.arr("todos").map { TodoEntity(it.str("id"), it.str("title"), it.bool("completed"), it.longOrNull("dueAt"), it.long("updatedAt"), it.bool("longTerm"), it.bool("allDay"), it.str("priority"), it.longOrNull("reminderAt"), it.intOrNull("estimatedMinutes"), it.strOrNull("planId"), it.str("note"), it.strOrNull("defaultSceneId"), it.bool("archived"), it.dbl("position")) },
        plans = root.arr("plans").map { PlanEntity(it.str("id"), it.str("title"), it.long("startAt"), it.longOrNull("endAt"), it.long("updatedAt"), it.int("targetMinutes"), it.int("accumulatedMinutes"), it.str("milestone"), it.str("description"), it.longOrNull("reminderAt"), it.strOrNull("defaultSceneId"), it.bool("archived"), it.dbl("position")) },
        habits = root.arr("habits").map { HabitEntity(it.str("id"), it.str("name"), it.int("targetPerWeek"), it.long("updatedAt"), it.str("weekdaysJson"), it.str("metric"), it.int("dailyTarget"), it.int("streak"), it.longOrNull("reminderAt"), it.strOrNull("defaultSceneId"), it.bool("archived"), it.dbl("position"), it.int("reminderHour", 9), it.int("reminderMinute")) },
        habitChecks = root.arr("habitChecks").map { HabitCheckEntity(it.str("id"), it.str("habitId"), it.long("checkedAt"), it.int("value")) },
        countdowns = root.arr("countdowns").map { CountdownEntity(it.str("id"), it.str("title"), it.long("targetAt"), it.long("updatedAt"), it.str("note"), it.int("dailyMinutes"), it.int("investedMinutes"), it.strOrNull("planId"), it.str("milestone"), it.longOrNull("reminderAt"), it.bool("archived"), it.dbl("position")) },
        sleepRoutines = root.arr("sleepRoutines").map { SleepRoutineEntity(it.str("id"), it.str("name"), it.int("minutes"), it.str("target"), it.int("fadeMinutes"), it.str("endMode"), it.strOrNull("clockTime"), it.strOrNull("sceneId"), it.bool("enabled"), it.long("updatedAt")) },
        sleepSessions = root.arr("sleepSessions").map { SleepSessionEntity(it.str("id"), it.long("startedAt"), it.long("targetEndAt"), it.longOrNull("actualEndAt"), it.str("target"), it.int("fadeMinutes"), it.str("status"), it.strOrNull("routineId"), it.bool("volumeSnapshotCaptured"), it.flt("masterVolumeSnapshot", 1f), it.flt("ambientVolumeSnapshot", 1f), it.flt("radioVolumeSnapshot", 0.8f)) },
        notebooks = root.arr("notebooks").map { NotebookEntity(it.str("id"), it.str("title"), it.long("updatedAt"), it.dbl("position"), it.long("accent", 0xFF7F8C87), it.str("iconKey", "bookOpen")) },
        notes = root.arr("notes").map { NoteEntity(it.str("id"), it.strOrNull("notebookId"), it.str("title"), it.str("body"), it.bool("archived"), it.longOrNull("deletedAt"), it.long("updatedAt"), it.long("createdAt"), it.bool("pinned"), it.str("tagsJson"), it.strOrNull("originalNotebookId"), it.dbl("position")) },
        noteBlocks = root.arr("noteBlocks").map { NoteBlockEntity(it.str("id"), it.str("noteId"), it.str("kind"), it.str("text"), it.bool("checked"), it.strOrNull("assetId"), it.strOrNull("linkedNoteId"), it.dbl("position"), it.long("revision")) },
        attachments = root.arr("attachments").map {
            val uploadState = it.str("uploadState", "PENDING").let { state -> if (state == "LOCAL") "PENDING" else state }
            AttachmentEntity(it.str("id"), it.str("noteId"), it.str("mimeType"), it.str("localUri"), it.strOrNull("remotePath"), it.long("updatedAt"), it.str("kind"), it.str("displayName"), it.long("sizeBytes"), it.longOrNull("durationSeconds"), it.intOrNull("width"), it.intOrNull("height"), it.strOrNull("thumbnailUri"), it.strOrNull("checksum"), uploadState)
        },
        playbackEvents = root.arr("playbackEvents").map { PlaybackEventEntity(it.str("id"), it.str("kind"), it.str("sourceId"), it.long("startedAt"), it.longOrNull("endedAt"), it.long("activeSeconds"), it.bool("completed"), it.strOrNull("trackId"), it.strOrNull("sourceKind"), it.long("listenedMs"), it.strOrNull("completionReason")) },
    ).validated()
}

private fun DatabaseSnapshot.validated(): DatabaseSnapshot {
    fun requireIds(label: String, ids: List<String>) {
        require(ids.none(String::isBlank)) { "$label 中存在空 ID" }
        require(ids.size == ids.toSet().size) { "$label 中存在重复 ID" }
    }
    fun requireValues(label: String, values: Iterable<String>, allowed: Set<String>) {
        val invalid = values.firstOrNull { it !in allowed }
        require(invalid == null) { "$label 包含无法识别的值：$invalid" }
    }
    requireIds("专注记录", focusSessions.map(FocusSessionEntity::id))
    requireIds("待办", todos.map(TodoEntity::id))
    requireIds("习惯", habits.map(HabitEntity::id))
    requireIds("笔记", notes.map(NoteEntity::id))
    requireIds("附件", attachments.map(AttachmentEntity::id))
    requireValues("待办优先级", todos.map(TodoEntity::priority), setOf("LOW", "MEDIUM", "HIGH"))
    requireValues("习惯计量方式", habits.map(HabitEntity::metric), setOf("MINUTES", "COUNT", "CHECK"))
    requireValues("专注状态", focusSessions.map(FocusSessionEntity::status), setOf("IDLE", "RUNNING", "PAUSED", "REVIEW", "COMPLETED", "CANCELLED"))
    requireValues("专注类型", focusSessions.map(FocusSessionEntity::kind), setOf("FOCUS", "BREAK", "SLEEP"))
    requireValues("专注目标", focusSessions.map(FocusSessionEntity::targetKind), setOf("FREE", "TODO", "PLAN", "HABIT", "COUNTDOWN"))
    requireValues("专注计时方式", focusSessions.map(FocusSessionEntity::timerMode), setOf("COUNTDOWN", "STOPWATCH"))
    requireValues("专注阶段", focusSessions.map(FocusSessionEntity::phase), setOf("FOCUS", "BREAK"))
    requireValues("睡眠状态", sleepSessions.map(SleepSessionEntity::status), setOf("IDLE", "RUNNING", "PAUSED", "REVIEW", "COMPLETED", "CANCELLED"))
    requireValues("睡眠目标", sleepSessions.map(SleepSessionEntity::target) + sleepRoutines.map(SleepRoutineEntity::target), setOf("ALL", "AMBIENT", "RADIO"))
    requireValues("睡眠结束方式", sleepRoutines.map(SleepRoutineEntity::endMode), setOf("DURATION", "CLOCK"))
    requireValues("笔记内容块类型", noteBlocks.map(NoteBlockEntity::kind), setOf("PARAGRAPH", "HEADING", "QUOTE", "CHECKLIST", "IMAGE", "AUDIO", "DRAWING", "FILE", "INTERNAL_LINK"))
    requireValues("附件类型", attachments.map(AttachmentEntity::kind), setOf("IMAGE", "AUDIO", "DRAWING", "FILE"))
    requireValues("附件同步状态", attachments.map(AttachmentEntity::uploadState), setOf("PENDING", "RUNNING", "SUCCEEDED", "FAILED"))
    requireValues("电台类型", radios.map(RadioEntity::sourceKind), setOf("GENERATED", "OFFICIAL", "CUSTOM", "LOCAL"))
    requireValues("播放记录类型", playbackEvents.map(PlaybackEventEntity::kind), setOf("AMBIENT", "RADIO", "LOCAL"))
    val noteIds = notes.mapTo(hashSetOf(), NoteEntity::id)
    require(noteBlocks.all { it.noteId in noteIds }) { "备份包含找不到所属笔记的内容块" }
    require(attachments.all { it.noteId in noteIds }) { "备份包含找不到所属笔记的附件" }
    return this
}

private fun JsonObject.str(key: String, default: String = ""): String = (this[key] as? JsonPrimitive)?.content ?: default
private fun JsonObject.strOrNull(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.bool(key: String, default: Boolean = false): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: default
private fun JsonObject.long(key: String, default: Long = 0L): Long = (this[key] as? JsonPrimitive)?.longOrNull ?: default
private fun JsonObject.longOrNull(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
private fun JsonObject.int(key: String, default: Int = 0): Int = (this[key] as? JsonPrimitive)?.intOrNull ?: default
private fun JsonObject.intOrNull(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
private fun JsonObject.dbl(key: String, default: Double = 0.0): Double = (this[key] as? JsonPrimitive)?.doubleOrNull ?: default
private fun JsonObject.flt(key: String, default: Float = 0f): Float = (this[key] as? JsonPrimitive)?.floatOrNull ?: default
private fun JsonObject.arr(key: String): List<JsonObject> = (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
