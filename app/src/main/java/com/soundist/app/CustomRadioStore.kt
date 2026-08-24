package com.soundist.app

import android.content.Context
import android.net.Uri
import com.soundist.core.database.RoomOfflineContentRepository
import com.soundist.core.model.CustomRadioFile
import com.soundist.feature.listening.LocalAudioSelection
import com.soundist.feature.listening.StationAudioImporter
import com.soundist.feature.listening.audioFileExtension
import com.soundist.feature.listening.sha256Hex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** 把用户挑选的外部 content:// 音频复制到应用私有目录，并写入 CustomRadioFile 跟踪记录。 */
class CustomRadioStore(
    context: Context,
    private val offlineContent: RoomOfflineContentRepository,
) : StationAudioImporter {
    private val appContext = context.applicationContext

    override suspend fun import(stationId: String, files: List<LocalAudioSelection>): List<LocalAudioSelection> =
        withContext(Dispatchers.IO) {
            files.mapIndexedNotNull { index, file ->
                when {
                    file.uri.startsWith("content://") -> copyToPrivate(file)?.let { (privateUri, sha, size) ->
                        offlineContent.saveCustomFile(
                            CustomRadioFile(
                                id = "$stationId#$index",
                                stationId = stationId,
                                displayName = file.displayName,
                                privatePath = privateUri.removePrefix("file://"),
                                mimeType = file.mimeType,
                                sizeBytes = size,
                                durationMs = file.durationSeconds * 1000L,
                                sha256 = sha,
                                sortIndex = index,
                            ),
                        )
                        file.copy(uri = privateUri, sizeBytes = size)
                    }
                    // 已是私有 file://（编辑重存）：保留原路径，不重复复制。
                    else -> file
                }
            }
        }

    /** 复制单个 content:// 到 filesDir/custom-radio/，返回 file:// 私有路径 + SHA-256 + 大小；失败返回 null。 */
    private fun copyToPrivate(file: LocalAudioSelection): Triple<String, String, Long>? = runCatching {
        val uri = Uri.parse(file.uri)
        val target = File(appContext.filesDir, "custom-radio").apply { mkdirs() }
            .resolve("${UUID.randomUUID()}.${audioFileExtension(file.displayName)}")
        appContext.contentResolver.openInputStream(uri).use { source ->
            requireNotNull(source) { "无法读取所选音频" }
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            target.outputStream().use { out ->
                val buffer = ByteArray(8192)
                while (true) {
                    val n = source.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    digest.update(buffer, 0, n)
                    size += n
                }
            }
            Triple("file://${target.absolutePath}", sha256Hex(digest.digest()), size)
        }
    }.getOrNull()
}
