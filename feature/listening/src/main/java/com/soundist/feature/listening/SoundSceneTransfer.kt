package com.soundist.feature.listening

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

private const val SCENE_SCHEMA_VERSION = 1
private const val MAX_SCENE_FILE_BYTES = 256 * 1024

internal fun shareSoundScene(context: Context, state: ListeningState) {
    val active = state.sounds.filter { it.active }
    require(active.isNotEmpty()) { "当前没有可分享的环境声" }
    val json = JSONObject().apply {
        put("schema", "soundist.scene")
        put("version", SCENE_SCHEMA_VERSION)
        put("name", state.sceneName.ifBlank { "Soundist 声场" })
        put("purpose", inferPresetPurposeLabel(state.sceneName))
        put("tracks", JSONArray().apply {
            active.forEach { sound ->
                put(JSONObject().apply {
                    put("id", sound.id)
                    put("name", sound.name)
                    put("volume", sound.volume.coerceIn(0f, 1f).toDouble())
                })
            }
        })
    }
    val exportDir = File(context.cacheDir, "shared").apply { mkdirs() }
    val safeName = state.sceneName.ifBlank { "soundist-scene" }
        .replace(Regex("[^\\p{L}\\p{N}._-]+"), "-")
        .trim('-')
        .take(48)
        .ifBlank { "soundist-scene" }
    val file = File(exportDir, "$safeName.soundist.json")
    file.writeText(json.toString(2), Charsets.UTF_8)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val summary = active.joinToString(" / ") { "${it.name} ${(it.volume * 100).toInt()}%" }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_TITLE, state.sceneName)
        putExtra(Intent.EXTRA_TEXT, "${state.sceneName}\n$summary")
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享 Soundist 声场"))
}

internal fun readSoundScene(context: Context, uri: Uri): SoundPreset {
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= MAX_SCENE_FILE_BYTES) { "声场文件过大，无法导入" }
            out.write(buffer, 0, read)
        }
        out.toByteArray()
    } ?: error("无法打开这个声场文件")
    val root = runCatching { JSONObject(bytes.toString(Charsets.UTF_8)) }
        .getOrElse { error("这不是有效的 Soundist 声场文件") }
    require(root.optString("schema") == "soundist.scene") { "这不是 Soundist 声场文件" }
    require(root.optInt("version") in 1..SCENE_SCHEMA_VERSION) { "这个声场文件版本暂不支持" }
    val tracksJson = root.optJSONArray("tracks") ?: error("声场文件缺少声音列表")
    val tracks = buildMap {
        for (index in 0 until tracksJson.length()) {
            val item = tracksJson.optJSONObject(index) ?: continue
            val id = item.optString("id").trim()
            if (id.isNotEmpty()) put(id, item.optDouble("volume", 0.7).toFloat().coerceIn(0f, 1f))
        }
    }
    require(tracks.isNotEmpty()) { "声场文件里没有声音" }
    return SoundPreset(
        id = "import-pending",
        name = root.optString("name", "导入声场").take(40),
        tracks = tracks,
        purposeLabel = root.optString("purpose", "").take(12),
    )
}
