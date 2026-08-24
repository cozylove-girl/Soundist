package com.soundist.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.isActive

private fun pathOf(d: String): Path = PathParser().parsePathString(d).toPath()

private val ambientWavePaths = headerWavesPaths.map { pathOf(it) }
private val radioSignalPaths = headerRadioPaths.map { pathOf(it) }
private val audioLinePaths = headerAudioLinesPaths.map { pathOf(it) }

private val strokeStyle = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun norm(t: Float): Float = ((t % 1f) + 1f) % 1f

/** Exact CSS `cubic-bezier(x1,y1,x2,y2)` evaluator (Newton iteration on the x polynomial). */
private fun cssBezier(t: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    if (t <= 0f) return 0f
    if (t >= 1f) return 1f
    var u = t
    repeat(6) {
        val x = bezierX(u, x1, x2)
        val dx = bezierDX(u, x1, x2)
        if (kotlin.math.abs(dx) < 1e-6f) return bezierY(u, y1, y2)
        u -= (x - t) / dx
        u = u.coerceIn(0f, 1f)
    }
    return bezierY(u, y1, y2)
}

private fun bezierX(u: Float, x1: Float, x2: Float): Float {
    val v = 1f - u
    return 3f * v * v * u * x1 + 3f * v * u * u * x2 + u * u * u
}

private fun bezierY(u: Float, y1: Float, y2: Float): Float {
    val v = 1f - u
    return 3f * v * v * u * y1 + 3f * v * u * u * y2 + u * u * u
}

private fun bezierDX(u: Float, x1: Float, x2: Float): Float {
    val v = 1f - u
    return 3f * v * v * x1 + 6f * v * u * (x2 - x1) + 3f * u * u * (1f - x2)
}

/** CSS `ease-in-out` (headerAmbientWave / headerMixedLine keyframes). */
private fun easeInOut(t: Float): Float = cssBezier(t, 0.42f, 0f, 0.58f, 1f)

/** CSS `ease-out` (headerRadioSignal keyframes). */
private fun easeOut(t: Float): Float = cssBezier(t, 0f, 0f, 0.58f, 1f)

/** Container transition curve `cubic-bezier(0.16,1,0.3,1)` from `.header-source-indicator`. */
private fun easeOutFast(t: Float): Float = cssBezier(t, 0.16f, 1f, 0.3f, 1f)

/** Radio pulse keyframes (CSS): 0,18% → 0.3 ; 52% → 1 ; 100% → 0.38. */
private fun radioOpacity(t: Float): Float = when {
    t < 0.18f -> 0.3f
    t < 0.52f -> 0.3f + 0.7f * easeOut((t - 0.18f) / 0.34f)
    else -> 1f - 0.62f * easeOut((t - 0.52f) / 0.48f)
}

private val mixedDelays = floatArrayOf(0f, -720f, -540f, -360f, -180f, 0f)

/**
 * 1:1 of the frontend `HeaderPlaybackIndicator`:
 *  - ambient → lucide Waves, 3 paths drift translateX ±0.45 with 1.8s phase offsets
 *  - radio   → lucide Radio, arcs pulse opacity (inner arcs, outer arcs +160ms), center dot static
 *  - mixed   → lucide AudioLines, 6 lines scaleY 0.62→1 / opacity 0.48→1 (900ms alternate),
 *              lines 1&6 in radio colour, the rest in ambient-light
 * reducedMotion → all paths static at full opacity (frontend `.is-reduced path { animation: none }`).
 */
@Composable
fun PlaybackIndicator(
    ambient: Boolean,
    radio: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val mode = when {
        ambient && radio -> 2
        radio -> 1
        ambient -> 0
        else -> -1
    }
    val label = when (mode) {
        0 -> "环境声播放中"
        1 -> "电台播放中"
        2 -> "环境声与电台播放中"
        else -> null
    }
    // Frame source: this composable has no external clock (unlike DeepSeaCanvas, which is kept
    // alive by its BreathingGlow). Writing tickNanos must be observed in COMPOSITION so the write
    // schedules a recomposition frame, which lets withFrameNanos resume and self-sustain the loop.
    var tickNanos by remember { mutableLongStateOf(0L) }
    // Mode switch crossfade: frontend `.header-source-indicator` has
    // `transition: opacity 180ms cubic-bezier(0.16,1,0.3,1)`, and the idle state sits at opacity 0.
    var fadeAlpha by remember { mutableFloatStateOf(if (mode == -1) 0f else 1f) }
    var lastMode by remember { mutableIntStateOf(-99) }
    var fadeFrom by remember { mutableFloatStateOf(if (mode == -1) 0f else 1f) }
    var fadeStartNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(mode, reducedMotion) {
        if (reducedMotion) {
            lastMode = mode
            fadeAlpha = if (mode == -1) 0f else 1f
            tickNanos = 0L
            return@LaunchedEffect
        }
        while (isActive) {
            withFrameNanos { now ->
                if (lastMode != mode) {
                    if (lastMode == -99) fadeAlpha = if (mode == -1) 0f else 1f // first composition: no fade
                    else { fadeFrom = fadeAlpha; fadeStartNanos = now }
                    lastMode = mode
                }
                val target = if (mode == -1) 0f else 1f
                if (fadeStartNanos != 0L) {
                    val elapsed = ((now - fadeStartNanos) / 1_000_000L).toFloat()
                    val progress = (elapsed / 180f).coerceIn(0f, 1f)
                    fadeAlpha = fadeFrom + (target - fadeFrom) * easeOutFast(progress)
                }
                tickNanos = now
            }
        }
    }
    @Suppress("UNUSED_VARIABLE") val frameTick = tickNanos
    val period = if (mode == 1) 1200 else 1800
    Canvas(
        modifier = (if (label != null) modifier.semantics { contentDescription = label } else modifier)
            .graphicsLayer { alpha = fadeAlpha },
    ) {
        val phase = if (reducedMotion) 0f else (((tickNanos / 1_000_000L) % period).toFloat() / period)
        when (mode) {
            0 -> drawAmbient(phase, reducedMotion)
            1 -> drawRadio(phase, reducedMotion)
            2 -> drawMixed(phase, reducedMotion)
        }
    }
}

private fun DrawScope.drawAmbient(p: Float, reduced: Boolean) {
    val s = size.width / 24f
    val color = SoundistColors.Teal
    scale(s, s, pivot = Offset.Zero) {
        for (i in ambientWavePaths.indices) {
            val phase = when (i) { 1 -> -1.2f / 1.8f; 2 -> -0.6f / 1.8f; else -> 0f }
            val osc = if (reduced) 1f else easeInOut(triangle(norm(p + phase)))
            val alpha = if (reduced) 1f else 0.42f + 0.58f * osc
            val dx = if (reduced) 0f else -0.45f + 0.9f * osc
            translate(left = dx) {
                drawPath(ambientWavePaths[i], color = color, alpha = alpha, style = strokeStyle)
            }
        }
    }
}

private fun triangle(t: Float): Float = 1f - kotlin.math.abs(2 * t - 1f)

private fun DrawScope.drawRadio(p: Float, reduced: Boolean) {
    val s = size.width / 24f
    val color = SoundistColors.Warm
    scale(s, s, pivot = Offset.Zero) {
        for (i in radioSignalPaths.indices) {
            if (i == 2) { // center dot: not animated by the frontend
                drawPath(radioSignalPaths[i], color = color, alpha = 1f, style = strokeStyle)
                continue
            }
            val delay = if (i == 0 || i == 4) 160f else 0f
            val a = if (reduced) 1f else radioOpacity(norm(p - delay / 1200f))
            drawPath(radioSignalPaths[i], color = color, alpha = a, style = strokeStyle)
        }
    }
}

private fun DrawScope.drawMixed(p: Float, reduced: Boolean) {
    val s = size.width / 24f
    scale(s, s, pivot = Offset.Zero) {
        val ms = p * 1800f
        for (i in audioLinePaths.indices) {
            val color = if (i == 0 || i == 5) SoundistColors.Warm else SoundistColors.TealSoft
            val osc: Float
            if (reduced) {
                osc = 1f
            } else {
                val ph = norm((ms - mixedDelays[i]) / 1800f) * 2f
                osc = easeInOut(if (ph < 1f) ph else 2f - ph)
            }
            val alpha = if (reduced) 1f else 0.48f + 0.52f * osc
            val sy = if (reduced) 1f else 0.62f + 0.38f * osc
            val b = audioLinePaths[i].getBounds()
            scale(1f, sy, pivot = Offset(b.center.x, b.center.y)) {
                drawPath(audioLinePaths[i], color = color, alpha = alpha, style = strokeStyle)
            }
        }
    }
}
