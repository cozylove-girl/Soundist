package com.soundist.app

import org.junit.Test
import java.io.File

/** 复刻 Mulberry32 + fnv1a，打印前 40 个随机数（对照 Rust 影子，验证随机序列是否一致）。 */
class RawRandTest {
    private class Mulberry32(private var seed: Int) {
        fun next(): Double {
            seed += 0x6d2b79f5
            var v = seed
            v = (v xor (v ushr 15)) * (v or 1)
            v = v xor (v + ((v xor (v ushr 7)) * (v or 61)))
            return ((v xor (v ushr 14)).toLong() and 0xffffffffL) / 4294967296.0
        }
    }
    private fun fnv1a(s: String): Int {
        var h = 0x811c9dc5.toInt()
        s.forEach { h = h xor it.code; h *= 16777619 }
        return h
    }

    @Test
    fun print_raw_rand() {
        val r = Mulberry32(fnv1a("rain-piano-soundist-02"))
        val sb = StringBuilder()
        repeat(40) { sb.append("%.15f ".format(r.next())) }
        File("build/kotlin_rand.txt").writeText(sb.toString().trimEnd() + "\n")
    }
}
