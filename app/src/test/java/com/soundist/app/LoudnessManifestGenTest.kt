package com.soundist.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * 任务七（阶段 E）：响度 manifest 的**校验测试**——只读已提交的 src/main/assets/loudness_manifest.json，
 * 校验格式完整性、字段齐全、边界与确定性；**绝不写 src/main/assets**。
 *
 * manifest 的生成已拆出到 [LoudnessRealSampleMeasurementTest]（普通 JUnit 只写到 build/loudness/，
 * 审核后手动复制到 src/main/assets），本测试只验证提交态文件的格式与内容一致性。
 *
 * 注意：JVM 单测无 Android 框架的 org.json.JSONObject，故用纯字符串/正则校验，不依赖 android.* 。
 */
class LoudnessManifestGenTest {
    private val allPresets = listOf("deep-sea","moon-tide","night-train","endless-focus","warm-study","rain-piano","morning-mist","signal-garden","bamboo-strings","custom-lab")

    private fun resolveManifestFile(): File {
        val userDir = System.getProperty("user.dir")
        val candidates = listOf(
            File(userDir, "src/main/assets"),
            File(userDir, "app/src/main/assets"),
            File(userDir, "apps/android-native/app/src/main/assets"),
        )
        val dir = candidates.firstOrNull { it.isDirectory }
            ?: error("找不到 assets 目录（user.dir=$userDir，候选 $candidates）")
        return File(dir, "loudness_manifest.json")
    }

    @Test fun manifest_is_present_complete_and_well_formed() {
        val f = resolveManifestFile()
        assertTrue("manifest 必须存在：${f.absolutePath}", f.isFile)
        val text = f.readText()
        assertTrue("缺 schemaVersion", text.contains("\"schemaVersion\""))
        assertTrue("缺 measuredBy", text.contains("\"measuredBy\""))
        // 阶段 E 元数据字段
        assertTrue("缺 measurementRevision", text.contains("\"measurementRevision\""))
        assertTrue("缺 engineVersion", text.contains("\"engineVersion\""))
        assertTrue("缺 samplePackVersion", text.contains("\"samplePackVersion\""))
        assertTrue("缺 contentHash", text.contains("\"contentHash\""))
        // 全部 preset 齐全，且每个都有关键字段
        allPresets.forEach { preset ->
            assertTrue("缺 preset=$preset", text.contains("\"$preset\""))
            val segment = text.substringAfter("\"$preset\":").substringBefore("},")
            listOf("category", "targetLufs", "measuredLufs", "trimDb", "truePeakDbTp", "clamped").forEach { field ->
                assertTrue("$preset 缺 $field", segment.contains("\"$field\""))
            }
        }
    }

    @Test fun true_peak_is_bounded() {
        val text = resolveManifestFile().readText()
        val regex = Regex("\"truePeakDbTp\":\\s*(-?[0-9.]+)")
        var count = 0
        regex.findAll(text).forEach { m ->
            val tp = m.groupValues[1].toDouble()
            assertTrue("truePeak 必须 ≤ 0 dBTP，实际 $tp", tp <= 0.0)
            count++
        }
        assertTrue("应校验到 10 个 preset 的 truePeak，实际 $count", count == 10)
    }

    @Test fun trim_consistency_target_equals_measured_plus_trim() {
        val text = resolveManifestFile().readText()
        val regex = Regex("\"([a-z-]+)\":\\s*\\{[^}]*\"targetLufs\":\\s*(-?[0-9.]+)[^}]*\"measuredLufs\":\\s*(-?[0-9.]+)[^}]*\"trimDb\":\\s*(-?[0-9.]+)[^}]*\"clamped\":\\s*(true|false)")
        var count = 0
        regex.findAll(text).forEach { m ->
            val preset = m.groupValues[1]
            val target = m.groupValues[2].toDouble()
            val measured = m.groupValues[3].toDouble()
            val trim = m.groupValues[4].toDouble()
            val clamped = m.groupValues[5] == "true"
            val recon = measured + trim
            assertTrue("$preset 的 trim 不一致：target=$target recon=$recon（clamped=$clamped）", abs(target - recon) < 0.5 || clamped)
            count++
        }
        assertTrue("应校验到 10 个 preset，实际 $count", count == 10)
    }

    @Test fun categories_are_valid() {
        val text = resolveManifestFile().readText()
        val regex = Regex("\"category\":\\s*\"([a-z]+)\"")
        val cats = regex.findAll(text).map { it.groupValues[1] }.toList()
        assertTrue("应有 10 个 category，实际 ${cats.size}", cats.size == 10)
        cats.forEach { cat -> assertTrue("非法 category=$cat", cat in setOf("sleep", "focus", "rest")) }
    }
}
