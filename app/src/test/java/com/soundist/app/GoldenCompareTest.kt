package com.soundist.app

import org.junit.Test
import java.io.File

/** 打印 Kotlin 黄金调度（供与 native/Rust 移植比对）。格式：preset|tick:id,tick:id,... */
class GoldenCompareTest {
    @Test
    fun print_kotlin_golden() {
        val presets = listOf("deep-sea","rain-piano","morning-mist","endless-focus","night-train","warm-study","moon-tide","signal-garden","bamboo-strings","custom-lab")
        val sb = StringBuilder()
        presets.forEach { preset ->
            val golden = NativeGeneratedAudioRenderer.schedulerGolden(preset, 512)
            sb.append("$preset|${golden.joinToString(",")}\n")
        }
        val out = File("build/kotlin_golden.txt")
        out.writeText(sb.toString())
    }
}
