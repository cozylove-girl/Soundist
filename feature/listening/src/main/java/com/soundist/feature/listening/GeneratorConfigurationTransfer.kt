package com.soundist.feature.listening

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

private const val GENERATOR_CONFIGURATION_VERSION = 1
private const val MAX_GENERATOR_CONFIGURATION_BYTES = 512 * 1024

data class GeneratorConfiguration(
    val name: String,
    val settings: GeneratorSettings,
    val arrangement: GeneratedArrangement,
)

internal fun shareGeneratorConfiguration(context: Context, configuration: GeneratorConfiguration) {
    val json = JSONObject().apply {
        put("schema", "soundist.generator-configuration")
        put("version", GENERATOR_CONFIGURATION_VERSION)
        put("name", configuration.name)
        put("settings", configuration.settings.toJson())
        put("arrangement", configuration.arrangement.toJson())
    }
    val exportDir = File(context.cacheDir, "shared").apply { mkdirs() }
    val safeName = configuration.name.ifBlank { "soundist-generator" }
        .replace(Regex("[^\\p{L}\\p{N}._-]+"), "-")
        .trim('-')
        .take(48)
        .ifBlank { "soundist-generator" }
    val file = File(exportDir, "$safeName.soundist-generator.json")
    file.writeText(json.toString(2), Charsets.UTF_8)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_TITLE, configuration.name)
        putExtra(Intent.EXTRA_TEXT, "Soundist 声场编排配置：${configuration.name}")
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享声场编排配置"))
}

internal fun readGeneratorConfiguration(context: Context, uri: Uri): GeneratorConfiguration {
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= MAX_GENERATOR_CONFIGURATION_BYTES) { "编排配置文件过大" }
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    } ?: error("无法打开这个编排配置")
    val root = runCatching { JSONObject(bytes.toString(Charsets.UTF_8)) }
        .getOrElse { error("这不是有效的 Soundist 编排配置") }
    require(root.optString("schema") == "soundist.generator-configuration") { "这不是 Soundist 编排配置" }
    require(root.optInt("version") in 1..GENERATOR_CONFIGURATION_VERSION) { "这个编排配置版本暂不支持" }
    val settings = root.optJSONObject("settings")?.generatorSettings()
        ?: error("编排配置缺少整体设置")
    val arrangement = root.optJSONObject("arrangement")?.generatedArrangement()
        ?: error("编排配置缺少编排内容")
    require(arrangement.layers.isNotEmpty()) { "编排配置至少需要一个音乐层" }
    require(arrangement.layers.size <= 24) { "编排配置包含过多音乐层" }
    require(arrangement.ambientTracks.size <= 84) { "编排配置包含过多环境声轨" }
    require(arrangement.scenes.size <= 12) { "编排配置包含过多场景" }
    return GeneratorConfiguration(
        name = root.optString("name", "导入的声场编排").trim().take(40).ifBlank { "导入的声场编排" },
        settings = settings,
        arrangement = arrangement,
    )
}

private fun GeneratorSettings.toJson() = JSONObject()
    .put("tempo", tempo)
    .put("density", density)
    .put("variation", variation)
    .put("entryDelaySeconds", entryDelaySeconds.toDouble())
    .put("eventDurationSeconds", eventDurationSeconds.toDouble())
    .put("gain", gain)
    .put("ambientResponse", ambientResponse)
    .put("adaptive", adaptive)

private fun GeneratedArrangement.toJson() = JSONObject()
    .put("seed", seed)
    .put("sectionMinutes", sectionMinutes)
    .put("arc", arc)
    .put("ambientMode", ambientMode)
    .put("layers", JSONArray().apply { layers.forEach { put(it.toJson()) } })
    .put("ambientTracks", JSONArray().apply { ambientTracks.forEach { put(it.toJson()) } })
    .put("scenes", JSONArray().apply { scenes.forEach { put(it.toJson()) } })

private fun GeneratedLayer.toJson() = JSONObject()
    .put("id", id).put("name", name).put("type", type).put("volume", volume)
    .put("probability", probability).put("timbre", timbre).put("enabled", enabled)
    .put("solo", solo).put("pan", pan).put("entryDelaySeconds", entryDelaySeconds.toDouble())
    .put("durationSeconds", durationSeconds.toDouble()).put("density", density).put("rhythm", rhythm)
    .put("register", register).put("scale", scale).put("fadeInSeconds", fadeInSeconds.toDouble())
    .put("fadeOutSeconds", fadeOutSeconds.toDouble()).put("ambientResponse", ambientResponse)
    .put("relationship", relationship).put("relationshipTargetId", relationshipTargetId ?: JSONObject.NULL)

private fun GeneratedAmbientTrack.toJson() = JSONObject()
    .put("soundId", soundId).put("name", name).put("category", category).put("enabled", enabled)
    .put("volume", volume).put("pan", pan).put("entryDelaySeconds", entryDelaySeconds.toDouble())
    .put("durationMinutes", durationMinutes).put("probability", probability)
    .put("fadeInSeconds", fadeInSeconds.toDouble()).put("fadeOutSeconds", fadeOutSeconds.toDouble())
    .put("relationship", relationship)

private fun GeneratedScene.toJson() = JSONObject()
    .put("id", id).put("name", name).put("durationMinutes", durationMinutes)
    .put("energy", energy).put("density", density).put("brightness", brightness).put("space", space)

private fun JSONObject.generatorSettings() = GeneratorSettings(
    tempo = optInt("tempo", 54).coerceIn(30, 120),
    density = optInt("density", 42).coerceIn(0, 100),
    variation = optInt("variation", 48).coerceIn(0, 100),
    entryDelaySeconds = optDouble("entryDelaySeconds", 1.4).toFloat().coerceIn(0f, 30f),
    eventDurationSeconds = optDouble("eventDurationSeconds", 3.2).toFloat().coerceIn(.1f, 60f),
    gain = optInt("gain", 72).coerceIn(0, 100),
    ambientResponse = optInt("ambientResponse", 70).coerceIn(0, 100),
    adaptive = optBoolean("adaptive", true),
)

private fun JSONObject.generatedArrangement(): GeneratedArrangement {
    val rawLayers = optJSONArray("layers").objects().mapIndexed { index, item -> item.generatedLayer(index) }
    val uniqueLayers = rawLayers.distinctBy { it.id }
    val ids = uniqueLayers.map { it.id }.toSet()
    val layers = uniqueLayers.map { layer ->
        layer.copy(
            relationshipTargetId = layer.relationshipTargetId?.takeIf { it in ids && it != layer.id },
            relationship = if (layer.relationshipTargetId != null && layer.relationshipTargetId !in ids) "independent" else layer.relationship,
        )
    }
    return GeneratedArrangement(
        seed = optString("seed", "imported-soundist").take(80),
        sectionMinutes = optInt("sectionMinutes", 4).coerceIn(1, 60),
        arc = optString("arc").takeIf { it in setOf("steady", "breathing", "journey") } ?: "breathing",
        layers = layers,
        ambientTracks = optJSONArray("ambientTracks").objects().mapIndexed { index, item -> item.generatedAmbient(index) }.distinctBy { it.soundId },
        scenes = optJSONArray("scenes").objects().mapIndexed { index, item -> item.generatedScene(index) }.distinctBy { it.id },
        ambientMode = optString("ambientMode").takeIf { it == "preset" || it == "current" } ?: "preset",
    )
}

private fun JSONObject.generatedLayer(index: Int): GeneratedLayer {
    val type = optString("type").takeIf { it in setOf("notes", "rhythm", "atmosphere", "voice") } ?: "notes"
    val relationship = optString("relationship").takeIf { it in setOf("independent", "alternate", "avoid", "follow") } ?: "independent"
    return GeneratedLayer(
        id = optString("id").trim().take(80).ifBlank { "imported-layer-$index" },
        name = optString("name", "编排层 ${index + 1}").trim().take(40).ifBlank { "编排层 ${index + 1}" },
        type = type,
        volume = optInt("volume", 42).coerceIn(0, 100),
        probability = optInt("probability", 58).coerceIn(0, 100),
        timbre = normalizeGeneratedTimbre(optString("timbre"), type),
        enabled = optBoolean("enabled", true),
        solo = optBoolean("solo"),
        pan = optInt("pan").coerceIn(-100, 100),
        entryDelaySeconds = optDouble("entryDelaySeconds", 2.0).toFloat().coerceIn(0f, 60f),
        durationSeconds = optDouble("durationSeconds", 3.4).toFloat().coerceIn(.1f, 120f),
        density = optInt("density", 38).coerceIn(0, 100),
        rhythm = optString("rhythm").takeIf { it in setOf("free", "whole", "half", "quarter", "eighth") } ?: "free",
        register = optString("register").takeIf { it in setOf("low", "middle", "high", "wide") } ?: "middle",
        scale = optString("scale").takeIf { it in setOf("pentatonic", "major", "minor", "dorian") } ?: "pentatonic",
        fadeInSeconds = optDouble("fadeInSeconds", 1.2).toFloat().coerceIn(0f, 30f),
        fadeOutSeconds = optDouble("fadeOutSeconds", 2.2).toFloat().coerceIn(0f, 30f),
        ambientResponse = optInt("ambientResponse", 64).coerceIn(0, 100),
        relationship = relationship,
        relationshipTargetId = optString("relationshipTargetId").takeIf(String::isNotBlank),
    )
}

private fun JSONObject.generatedAmbient(index: Int) = GeneratedAmbientTrack(
    soundId = optString("soundId").trim().take(80).ifBlank { "missing-$index" },
    name = optString("name", "环境声 ${index + 1}").trim().take(40),
    category = optString("category").trim().take(24),
    enabled = optBoolean("enabled", true),
    volume = optInt("volume", 42).coerceIn(0, 100),
    pan = optInt("pan").coerceIn(-100, 100),
    entryDelaySeconds = optDouble("entryDelaySeconds").toFloat().coerceIn(0f, 300f),
    durationMinutes = optInt("durationMinutes").coerceIn(0, 24 * 60),
    probability = optInt("probability", 100).coerceIn(0, 100),
    fadeInSeconds = optDouble("fadeInSeconds", 2.0).toFloat().coerceIn(0f, 60f),
    fadeOutSeconds = optDouble("fadeOutSeconds", 3.0).toFloat().coerceIn(0f, 60f),
    relationship = optString("relationship").takeIf { it in setOf("independent", "alternate", "avoid", "follow") } ?: "independent",
)

private fun JSONObject.generatedScene(index: Int) = GeneratedScene(
    id = optString("id").trim().take(80).ifBlank { "imported-scene-$index" },
    name = optString("name", "场景 ${index + 1}").trim().take(40).ifBlank { "场景 ${index + 1}" },
    durationMinutes = optInt("durationMinutes", 4).coerceIn(1, 120),
    energy = optInt("energy", 42).coerceIn(0, 100),
    density = optInt("density", 42).coerceIn(0, 100),
    brightness = optInt("brightness", 48).coerceIn(0, 100),
    space = optInt("space", 60).coerceIn(0, 100),
)

private fun JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) optJSONObject(index)?.let(::add)
    }
}
