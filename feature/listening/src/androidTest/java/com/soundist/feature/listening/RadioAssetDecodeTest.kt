package com.soundist.feature.listening

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Build gate: every packaged radio asset under assets/radio must be a real,
 * decodable audio file (MediaExtractor finds an audio track, and
 * MediaMetadataRetriever reports a non-zero duration). HTTP download success is
 * NOT enough — this test asserts the packaged bytes actually decode.
 */
@RunWith(AndroidJUnit4::class)
class RadioAssetDecodeTest {

    private val audioExtensions = setOf("ogg", "oga", "mp3", "flac", "wav", "aac", "m4a")

    @Test
    fun everyPackagedRadioAssetHasDecodableAudioTrack() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val files = context.assets.list("radio").orEmpty()
        val audioFiles = files.filter { it.substringAfterLast('.', "").lowercase() in audioExtensions }
        assertTrue("assets/radio 应至少有一个音频文件", audioFiles.isNotEmpty())

        val failures = mutableListOf<String>()
        for (fileName in audioFiles) {
            var decodable = false
            var durationMs = 0L
            try {
                context.assets.openFd("radio/$fileName").use { afd ->
                    val extractor = MediaExtractor()
                    extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    for (i in 0 until extractor.trackCount) {
                        val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
                        if (mime.startsWith("audio/")) {
                            decodable = true
                            break
                        }
                    }
                    extractor.release()
                }
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(context.assets.openFd("radio/$fileName").fileDescriptor)
                    durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                }
            } catch (t: Throwable) {
                decodable = false
            }
            if (!decodable || durationMs <= 0L) {
                failures.add("$fileName (decodable=$decodable durationMs=$durationMs)")
            }
        }
        assertTrue("以下音频无法解码或时长为 0：\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
