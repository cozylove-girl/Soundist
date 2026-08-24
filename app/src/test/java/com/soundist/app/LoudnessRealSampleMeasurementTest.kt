package com.soundist.app

import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log

/**
 * 任务七：离线「真实采样」响度测量工具。
 *
 * 从 app/src/main/assets/instruments/vsco 目录读取真实 VSCO 采样，按运行期 sampleFor 就近选样，
 * 用 renderPresetPcm(sampleFor=...) 渲染每个 preset 90 秒真实立体声：
 * 1) 写 16-bit PCM WAV 到 app/build/loudness/&lt;preset&gt;.wav（供 FFmpeg ebur128 复核）；
 * 2) 用自写 EBU R128（BS.1770-4 K 加权 + 门限 + 4x 过采样 True Peak）测 Integrated LUFS / True Peak；
 * 3) 按专注/休息/助眠三档目标算 trimDb 并重写 app/src/main/assets/loudness_manifest.json。
 *
 * 说明：Integrated LUFS 来自自写 EBU R128（measuredBy=self-write-ebur128-real-samples），非 FFmpeg/libebur128
 * 权威结论；最终以 FFmpeg 对 WAV 的 ebur128 结果为准（本机无 FFmpeg，见 loudness/README）。
 */
class LoudnessRealSampleMeasurementTest {
    private val targets = mapOf("sleep" to -55.0, "focus" to -40.0, "rest" to -45.0)
    private val category = mapOf(
        "deep-sea" to "sleep", "moon-tide" to "sleep", "night-train" to "sleep",
        "endless-focus" to "focus", "warm-study" to "focus",
        "rain-piano" to "rest", "morning-mist" to "rest", "signal-garden" to "rest",
        "bamboo-strings" to "rest", "custom-lab" to "rest",
    )

    @Test
    fun measure_real_samples_and_regenerate_manifest() {
        val realSamples = SAMPLE_TIMBRES.values
            .flatten()
            .distinctBy { it.path }
            .associate { ref ->
                val file = File("src/main/assets/${ref.path}")
                require(file.exists()) { "缺少真实采样：${file.absolutePath}" }
                ref.path to decodeWav(file.readBytes())
            }
        val sampleFor: (String, Double) -> SampleHit? = sampleFor@{ timbre, frequency ->
            val refs = SAMPLE_TIMBRES[timbre] ?: return@sampleFor null
            val ref = refs.minBy { abs(12 * log(frequency / it.frequency, 2.0)) }
            realSamples[ref.path]?.let { SampleHit(it, ref.frequency) }
        }

        val outDir = File("build/loudness").apply { mkdirs() }
        val reports = LinkedHashMap<String, LoudnessReport>()
        val tsv = StringBuilder("preset\tcategory\tintegratedLufs\ttruePeakDbTp\ttrimDb\n")
        category.keys.forEach { preset ->
            val pcm = NativeGeneratedAudioRenderer.renderPresetPcm(preset, 90 * 44100, sampleFor = sampleFor)
            writeWav(File(outDir, "$preset.wav"), pcm, 44100, 2)
            val r = NativeGeneratedAudioRenderer.measurePresetLoudness(preset, 90, sampleFor = sampleFor)
            reports[preset] = r
            val trim = trimDbFor(preset, r)
            println("REAL $preset -> IL=${"%.2f".format(r.integratedLufs)} TP=${"%.2f".format(r.truePeakDbTp)} trim=${"%.2f".format(trim)}")
            tsv.append("%s\t%s\t%.2f\t%.2f\t%.2f\n".format(preset, category.getValue(preset), r.integratedLufs, r.truePeakDbTp, trim))
        }
        File("build/loudness/real_loudness_report.tsv").writeText(tsv.toString())
        writeManifest(reports)
    }

    private fun trimDbFor(preset: String, r: LoudnessReport): Double {
        val target = targets.getValue(category.getValue(preset))
        var trim = target - r.integratedLufs
        if (r.truePeakDbTp + trim > -1.0) trim = -1.0 - r.truePeakDbTp  // true peak 上限保护
        return trim
    }

    private fun writeManifest(reports: Map<String, LoudnessReport>) {
        val sb = StringBuilder()
        sb.append("{\n  \"schemaVersion\": 2,\n  \"measuredBy\": \"self-write-ebur128-real-samples\",\n")
        sb.append("  \"measurementRevision\": 1,\n")
        sb.append("  \"engineVersion\": \"kotlin-generated-radio-02\",\n")
        sb.append("  \"samplePackVersion\": \"vsco-2-ce\",\n")
        sb.append("  \"seed\": \"arrangement-default\",\n")
        sb.append("  \"sections\": [\"head\", \"middle\", \"dense\"],\n")
        sb.append("  \"contentHash\": \"%s\",\n".format(computeSampleContentHash()))
        sb.append("  \"synthesisPath\": false,\n")
        sb.append("  \"note\": \"真实 VSCO 采样 + 自写 EBU R128；待 FFmpeg ebur128 复核后改 measuredBy=ffmpeg-ebur128-real-samples 并 bump schemaVersion。schemaVersion 仅在 JSON 格式变化时升级，测量修订只动 measurementRevision。\",\n")
        sb.append("  \"targets\": {\"sleep\": -55.0, \"focus\": -40.0, \"rest\": -45.0},\n")
        sb.append("  \"presets\": {\n")
        category.keys.forEachIndexed { i, preset ->
            val r = reports.getValue(preset)
            val trim = trimDbFor(preset, r)
            val clamped = r.truePeakDbTp + (targets.getValue(category.getValue(preset)) - r.integratedLufs) > -1.0
            val comma = if (i == category.size - 1) "" else ","
            sb.append("    \"%s\": {\"category\": \"%s\", \"targetLufs\": %.2f, \"measuredLufs\": %.2f, \"trimDb\": %.2f, \"truePeakDbTp\": %.2f, \"clamped\": %b}%s\n".format(
                preset, category.getValue(preset), targets.getValue(category.getValue(preset)), r.integratedLufs, trim, r.truePeakDbTp, clamped, comma,
            ))
        }
        sb.append("  }\n}\n")
        // 不写 src/main/assets：普通 JUnit 测试只产出到 build/，审核后由显式步骤（手动/Gradle task）复制到 src/main/assets。
        val f = File("build/loudness/loudness_manifest.json")
        f.parentFile?.mkdirs()
        f.writeText(sb.toString())
        println("WROTE ${f.absolutePath}（未写 src/main/assets；审核后复制）")
    }

    /** 采样包内容指纹（文件路径+大小+SHA-256 前若干字节），检测 samplePack 变更。 */
    private fun computeSampleContentHash(): String {
        val files = SAMPLE_TIMBRES.values.flatten().distinctBy { it.path }.map { File("src/main/assets/${it.path}") }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        files.sortedBy { it.path }.forEach { f ->
            digest.update(f.path.toByteArray(Charsets.US_ASCII))
            digest.update(f.length().toString().toByteArray(Charsets.US_ASCII))
            f.inputStream().use { input ->
                val head = ByteArray(4096)
                val n = input.read(head)
                if (n > 0) digest.update(head, 0, n)
            }
        }
        return digest.digest().take(8).joinToString("") { "%02x".format(it) }
    }

    private fun writeWav(file: File, pcm: ShortArray, sampleRate: Int, channels: Int) {
        val dataSize = pcm.size * 2
        val buf = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII)); buf.putInt(36 + dataSize); buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII)); buf.putInt(16); buf.putShort(1); buf.putShort(channels.toShort())
        buf.putInt(sampleRate); buf.putInt(sampleRate * channels * 2); buf.putShort((channels * 2).toShort()); buf.putShort(16)
        buf.put("data".toByteArray(Charsets.US_ASCII)); buf.putInt(dataSize)
        pcm.forEach(buf::putShort)
        file.writeBytes(buf.array())
    }
}
