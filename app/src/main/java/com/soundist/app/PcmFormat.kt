package com.soundist.app

import kotlin.math.floor

/**
 * 纯 JVM PCM 格式工具（阶段 A）：在非实时线程把解码后的交错 float PCM 统一到设备格式
 * （设备采样率 × 设备声道），保证原生回调只读设备格式交错 PCM、真实保留 mono/stereo。
 *
 * 这些函数是纯函数、无副作用、确定性，可被普通 JUnit 直接测试（44.1↔48kHz、mono↔stereo）。
 * 实际调用发生在 [NativeAudioCore] 的专用控制线程；原生回调绝不执行任何格式转换。
 */
object PcmFormat {

    /**
     * 线性插值重采样（不改声道数）。source 为交错 float PCM，srcChannels 声道，srcRate→dstRate。
     * 相同采样率直接返回副本（保证调用方可以安全持有结果，不共享源数组）。
     * 输出帧数 = floor(srcFrames * dstRate / srcRate)。
     */
    fun resampleLinear(source: FloatArray, srcChannels: Int, srcRate: Int, dstRate: Int): FloatArray {
        require(srcChannels >= 1) { "srcChannels 必须 >= 1" }
        require(srcRate > 0 && dstRate > 0) { "采样率必须 > 0（src=$srcRate dst=$dstRate）" }
        require(source.size % srcChannels == 0) { "源长度不是 srcChannels 的整数倍" }
        if (srcRate == dstRate) return source.copyOf()
        val srcFrames = source.size / srcChannels
        val dstFrames = ((srcFrames.toLong() * dstRate) / srcRate).toInt().coerceAtLeast(0)
        val out = FloatArray(dstFrames * srcChannels)
        if (dstFrames == 0) return out
        val ratio = srcRate.toDouble() / dstRate
        for (f in 0 until dstFrames) {
            val srcPos = f * ratio
            val i0 = floor(srcPos).toInt().coerceIn(0, srcFrames - 1)
            val i1 = (i0 + 1).coerceAtMost(srcFrames - 1)
            val frac = (srcPos - i0).toFloat()
            for (c in 0 until srcChannels) {
                val a = source[i0 * srcChannels + c]
                val b = source[i1 * srcChannels + c]
                out[f * srcChannels + c] = a + (b - a) * frac
            }
        }
        return out
    }

    /**
     * 声道转换（1↔2）。mono→stereo 时 L=R=mono；stereo→mono 时取 (L+R)/2。
     * 相同声道数直接返回副本。仅支持 1/2 声道（设备常见配置）。
     */
    fun convertChannels(source: FloatArray, srcChannels: Int, dstChannels: Int): FloatArray {
        require(srcChannels in 1..2 && dstChannels in 1..2) { "仅支持 1/2 声道（src=$srcChannels dst=$dstChannels）" }
        require(source.size % srcChannels == 0) { "源长度不是 srcChannels 的整数倍" }
        if (srcChannels == dstChannels) return source.copyOf()
        val srcFrames = source.size / srcChannels
        val out = FloatArray(srcFrames * dstChannels)
        if (srcChannels == 1) { // mono -> stereo
            for (f in 0 until srcFrames) {
                val m = source[f]
                out[f * 2] = m
                out[f * 2 + 1] = m
            }
        } else { // stereo -> mono
            for (f in 0 until srcFrames) {
                out[f] = (source[f * 2] + source[f * 2 + 1]) * 0.5f
            }
        }
        return out
    }

    /**
     * 组合：先重采样到设备采样率，再转设备声道数。产物为设备格式交错 float PCM。
     * 顺序：先重采样（时间轴）再转声道（幅度），二者可交换且结果一致；此处固定为先重采样。
     */
    fun toDeviceFormat(source: FloatArray, srcChannels: Int, srcRate: Int, dstChannels: Int, dstRate: Int): FloatArray {
        val resampled = resampleLinear(source, srcChannels, srcRate, dstRate)
        return convertChannels(resampled, srcChannels, dstChannels)
    }
}
