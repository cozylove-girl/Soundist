package com.soundist.feature.listening

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.soundist.core.designsystem.SoundistColors
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.lerp
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.pow
import kotlin.random.Random

/** Four native Canvas layers equivalent to AmbientDrift, CosmicDust, NebulaCore and Galaxy. */
@Composable
fun DeepSeaCanvas(state: ListeningState, onSoundClick: (String) -> Unit, modifier: Modifier = Modifier, reduceMotion: Boolean = false) {
    val engine = remember { DeepSeaParticleEngine(seed = 0x50A1D157) }
    val clock = remember { DeepSeaAnimationClock() }
    val latestState by rememberUpdatedState(state)
    // `frameNanos` is only an invalidation trigger so the Canvas redraws every frame; the animation
    // phase and interpolation come from simulationTimeSeconds / interpolationAlpha below, never from
    // the absolute frame timestamp (which would drift with the refresh rate and after pause/resume).
    var frameNanos by remember { mutableLongStateOf(0L) }
    var simulationTimeSeconds by remember { mutableDoubleStateOf(0.0) }
    var interpolationAlpha by remember { mutableFloatStateOf(0f) }
    val activeSounds = state.sounds.filter { it.active }.take(20)
    // App.tsx CosmicDustCanvas intensity, preserving its source grouping exactly:
    // master * environment * (0.4 + averageActiveSound * 0.6).
    val averageActiveSound = activeSounds.map { it.volume }.average().toFloat().takeIf { !it.isNaN() } ?: 0f
    val intensity = (state.globalVolume * state.environmentVolume * (.4f + averageActiveSound * .6f)).coerceIn(0f, 1f)

    // Foreground resume: forget the last frame timestamp so pause/background time is never folded
    // into the simulation clock. Re-entering composition gets a fresh `clock` from remember above.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) clock.reset()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            val current = latestState
            val active = current.sounds.filter { it.active }.take(20)
            val average = active.map { it.volume }.average().toFloat().takeIf { !it.isNaN() } ?: 0f
            engine.step(current.ambientPlaying || current.radioPlayback.isRadioActive, current.ambientPlaying,
                (current.globalVolume * current.environmentVolume * (.4f + average * .6f)).coerceIn(0f, 1f), true)
            clock.reset()
            simulationTimeSeconds = 0.0
            interpolationAlpha = 0f
            frameNanos = 0L
        } else {
            while (isActive) {
                withFrameNanos { now ->
                    val current = latestState
                    val active = current.sounds.filter { it.active }.take(20)
                    val average = active.map { it.volume }.average().toFloat().takeIf { !it.isNaN() } ?: 0f
                    val intensity = (current.globalVolume * current.environmentVolume * (.4f + average * .6f)).coerceIn(0f, 1f)
                    val anyPlayback = current.ambientPlaying || current.radioPlayback.isRadioActive
                    // Advance the fixed-step clock (returns how many 1/60 s steps to run this frame).
                    val steps = clock.advance(now)
                    repeat(steps) { engine.step(anyPlayback, current.ambientPlaying, intensity, false) }
                    simulationTimeSeconds = clock.simulationTimeSeconds
                    interpolationAlpha = clock.interpolationAlpha
                    frameNanos = now
                }
            }
        }
    }

    // 渲染时刻 = 固定步长模拟时间 + 本帧插值（interpolationAlpha × 1/60s）。分析型呼吸/星云/电台波纹
    // 用它取值，在 60/90/120Hz 下插值平滑，不因固定 1/60s 步长而 stutter。
    // 固定步长已保证刷新率一致；该系数只校准真机与最终 Web 预览的主观节奏，
    // 不是按设备刷新率动态调速，也不会受音量影响。
    val renderTime = (simulationTimeSeconds + interpolationAlpha.toDouble() * (1.0 / 60.0)) * 0.72
    Box(modifier.graphicsLayer { clip = false }, contentAlignment = Alignment.Center) {
        // App.tsx breathing ambient glow (inset -18%, radial-gradient, blur 22px).
        BreathingGlow(state.ambientPlaying, state.ambientPlaying || state.radioPlayback.isRadioActive, reduceMotion, renderTime)
        // App.tsx AmbientDriftCanvas is the 224px core plus an 80px overflow on every side.
        // At the 390px baseline this is a 384px field, i.e. effectively the full page width.
        Canvas(Modifier.requiredSize(384.dp).graphicsLayer { clip = false }) {
            @Suppress("UNUSED_VARIABLE") val invalidate = frameNanos
            // AmbientDriftCanvas: 460 persistent cyan/gold particles, wrap and center repulsion.
            // Sizes are in App.tsx CSS px (0.45–1.5), so scale = width/384 converts them to this 384dp canvas in px.
            drawDrift(engine.drift, size, size.width / 384f, interpolationAlpha)
        }

        // App.tsx CosmicDustCanvas is independently expanded by 40px on every core edge.
        Canvas(Modifier.requiredSize(304.dp).graphicsLayer { clip = false }) {
            @Suppress("UNUSED_VARIABLE") val invalidate = frameNanos
            // CosmicDustCanvas: core emission, outward velocity, swirl, lifetime/culling and 1250 cap.
            // Particle positions are in the App.tsx 304px coordinate space (coreRadius = 304*0.248 = 75.392,
            // maxRadius = 304*0.52 = 158.08), so scale = width/304 converts them to this 304dp canvas in px.
            drawDust(engine.dust, Offset(size.width / 2f, size.height / 2f), size.width / 304f, state.ambientPlaying, intensity, interpolationAlpha)
        }

        // The signed-off core remains exactly 224dp and keeps its own 300-unit drawing space.
        Canvas(Modifier.size(224.dp)) {
            @Suppress("UNUSED_VARIABLE") val invalidate = frameNanos
            val side = min(size.width, size.height)
            val center = Offset(size.width / 2f, size.height / 2f)
            val scale = side / 300f
            // Phase clock = 渲染时刻 renderTime（固定步长模拟时间 + 本帧插值），不是绝对帧时间戳，
            // 因此 60/90/120Hz 一致、暂停/恢复后无缝续上；插值项让分析型动画逐帧平滑。
            val t = renderTime.toFloat()
            val coreRadius = side * .36f // 108 / 300
            val radioActive = state.radioPlayback.isRadioActive

            // DeepSeaNebulaCoreCanvas: breathing shell, off-axis shade and rim.
            val breathe = 1f + sin(t * .72f) * .012f * if (state.ambientPlaying) 1f else .34f
            val r = coreRadius * breathe
            // App.tsx drawSphere wraps the shell/shade/rim in translate(cx,cy);scale(breathe);translate(-cx,-cy),
            // so the gradient centre offset, radius and stops all inflate with breathe (C2).
            // CanvasGradient has two independent circles. Brush.radialGradient has only one
            // centre, so draw the exact two-circle isolines instead of shifting a concentric
            // gradient. This preserves cx-34/cy-46/r8 -> cx/cy/r118 and source stop positions.
            drawTwoCircleRadialGradient(
                clipCenter = center,
                clipRadius = r,
                innerCenter = center - Offset(34f * scale * breathe, 46f * scale * breathe),
                innerRadius = 8f * scale * breathe,
                outerCenter = center,
                outerRadius = 118f * scale * breathe,
                stops = arrayOf(
                    0f to Color(0x2991D3C5),
                    .32f to Color(0x4755B6A3),
                    .66f to Color(0xC7183C36),
                    1f to Color(0xFE080B0D),
                ),
            )
            // shade: frontend stops 0 / 0.6 / 1 (inner radius 0) — 0.6, not 0.5 (C3); scaled by breathe (C2).
            drawCircle(Brush.radialGradient(*arrayOf(0f to Color(0x38000000), .6f to Color(0x0D000000), 1f to Color.Transparent), center = center + Offset(44f * scale * breathe, 62f * scale * breathe), radius = 118f * scale * breathe), r, center)
            // rim: rgba(145,211,197,0.2) lineWidth 1.05, drawn inside the frontend's scale(breathe) context.
            drawCircle(Color(0xFF91D3C5).copy(alpha = .20f), r, center, style = Stroke(1.05f * breathe * scale))

            // Sixteen drifting, flattened nebula clouds clipped by the sphere.
            val environmentLight = sqrt(state.environmentVolume.coerceIn(0f, 1f))
            val nebulaStrength = if (state.ambientPlaying) .68f + environmentLight * .4f else .52f
            val sphereClip = Path().apply { addOval(Rect(center - Offset(coreRadius, coreRadius), center + Offset(coreRadius, coreRadius))) }
            clipPath(sphereClip) {
                deepSeaNebulaBlobs(t).forEach { blob ->
                    val blobCenter = blob.center(center, scale)
                    rotate(blob.rotationDegrees(t), blobCenter) {
                        drawOval(
                            Brush.radialGradient(colorStops = arrayOf(0f to Color(0xFF91D3C5).copy(alpha = .038f * nebulaStrength), .52f to Color(0xFF55B6A3).copy(alpha = .018f * nebulaStrength), 1f to Color.Transparent), center = blobCenter, radius = blob.rx * scale),
                            topLeft = blobCenter - Offset(blob.rx * 1.35f * scale, blob.ry * .64f * scale),
                            size = Size(blob.rx * 2.7f * scale, blob.ry * 1.28f * scale), blendMode = BlendMode.Screen,
                        )
                    }
                }

            // Four soft dashed rings with wobble and time-based dash drift.
                repeat(4) { i ->
                val ring = (24f + i * 17f + sin(t * (if (state.ambientPlaying) .42f else .18f) + i * .8f) * 2f) * scale
                drawCircle(Color(0xFF91D3C5).copy(alpha = 0.9f * ((if (state.ambientPlaying) .14f else .065f) - i * .018f)), ring, center,
                    style = Stroke(if (i == 0) 1.15f * scale else .85f * scale, pathEffect = PathEffect.dashPathEffect(floatArrayOf(22f, 20f, 7f, 18f).map { it * scale }.toFloatArray(), -t * (5f + i * 1.7f) * scale)))
                }

            // 9 deterministic orbiting pearls（前端 drawBeaconDots，source-over 无混合）
                val beaconRand = SineNoise(17)
                val beaconBase = if (state.ambientPlaying) .10f + environmentLight * .06f else .075f
                repeat(9) { i ->
                val a = (i / 9f) * (PI * 2).toFloat() + beaconRand.next() * .28f + sin(t * .22f + i) * .035f
                val rr = 62f + (beaconRand.next() - .5f) * 18f + sin(t * .38f + i) * 2f
                val twinkle = max(0f, sin(t * 1.4f + i * 1.7f))
                val fillAlpha = if (i % 5 == 0) .8f else .78f
                val col = if (i % 5 == 0) Color(0xFF95CBBB) else Color(0xFF91D3C5)
                drawCircle(col.copy(alpha = (beaconBase + twinkle * .16f) * fillAlpha), (1.05f + beaconRand.next() * 1.1f) * scale, center + Offset(cos(a) * rr * scale, sin(a) * rr * .92f * scale))
                }
            // Beacon core plus ring（前端 drawBeaconCore）
                val pulse = .5f + sin(t * 1.05f) * .5f
            val power = if (state.ambientPlaying) .68f + environmentLight * .4f else .34f
            drawCircle(Brush.radialGradient(*arrayOf(0f to Color(0xFF91D3C5).copy(alpha = .16f * power + pulse * .09f), .42f to Color(0xFF55B6A3).copy(alpha = .09f * power), 1f to Color.Transparent), center = center, radius = (if (state.ambientPlaying) 37f else 32f) * scale), (if (state.ambientPlaying) 37f else 32f) * scale, center)
            drawCircle(Color(0xFF91D3C5).copy(alpha = 0.8f * (.14f + pulse * .18f * power)), (13f + pulse * 3.5f) * scale, center, style = Stroke(.9f * scale))

            // RadioEcho: 3 expanding amber pulse rings, 7 segmented echoes and 10 pearls.
                if (radioActive) {
                repeat(3) { i ->
                    val progress = (t * (.22f + i * .025f) + i / 3f) % 1f
                    val rr = (28f + progress * 60f) * scale
                    val alpha = (1f - progress) * (.04f + state.radioVolume * .075f) * 1.48f
                    drawCircle(SoundistColors.Warm.copy(alpha = 0.78f * alpha), rr, center, style = Stroke(.7f * scale), blendMode = BlendMode.Screen)
                    repeat(7) { j ->
                        val drift = sin(t * .22f + i * 1.2f + j * .74f) * .12f * 57.2958f
                        val emphasis = .72f + max(0f, sin(t * .72f + j * 1.9f + i)) * .28f
                        val anchor = j / 7f * 360f + t * 6.3025357f + i * 21.772397f
                        val span = (.32f + progress * .08f) * 57.2958f
                        drawArc(Color(0xFFE3BC8D).copy(alpha = 0.94f * (1f - progress) * (.07f + state.radioVolume * .15f) * emphasis * 1.48f), anchor - span + drift, span * 2f, false, topLeft = center - Offset(rr, rr), size = Size(rr * 2f, rr * 2f), style = Stroke(.9f * scale, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 24f).map { it * scale }.toFloatArray(), -t * (5f + j * .4f) * scale)), blendMode = BlendMode.Screen)
                    }
                }
                repeat(10) { i ->
                    val a = i / 10f * (PI * 2).toFloat() + t * .08f
                    val blink = max(0f, sin(t * 1.35f + i * 1.4f)); val rr = 42f + (i % 3) * 12f + sin(t * .33f + i) * 2f
                    drawCircle(Color(0xFFE3BC8D).copy(alpha = 0.9f * (.08f + blink * .14f) * state.radioVolume * 1.48f), (.95f + blink * .35f) * scale, center + Offset(cos(a) * rr * scale, sin(a) * rr * scale), blendMode = BlendMode.Screen)
                }
                }

                // drawInteriorFinish: both source gradients and both source arcs.
                drawCircle(Brush.radialGradient(colorStops = arrayOf(0f to Color(0x1291D3C5), .48f to Color(0x0655B6A3), 1f to Color.Transparent), center = center - Offset(38f * scale, 48f * scale), radius = 112f * scale), coreRadius, center)
                drawCircle(Brush.radialGradient(colorStops = arrayOf(0f to Color(0x33000000), .58f to Color(0x0F000000), 1f to Color.Transparent), center = center + Offset(18f * scale, 70f * scale), radius = 112f * scale), coreRadius, center)
                val finishAlpha = .1f + sin(t * .7f) * .025f
                drawArc(Color(0xFF91D3C5).copy(alpha = finishAlpha * .7f), 194.4f, 118.8f, false, topLeft = center - Offset(94f * scale, 94f * scale), size = Size(188f * scale, 188f * scale), style = Stroke(.9f * scale))
                drawArc(Color(0xFF91D3C5).copy(alpha = finishAlpha * .7f), -25.2f, 68.4f, false, topLeft = center - Offset(72f * scale, 72f * scale), size = Size(144f * scale, 144f * scale), style = Stroke(.9f * scale))
            }
        }

        // SoundscapeGalaxy uses the Web SVG's 236-unit coordinate space and 16/12dp icon body.
        BoxWithConstraints(Modifier.size(224.dp).graphicsLayer { clip = false }) {
            Canvas(Modifier.fillMaxSize().graphicsLayer { clip = false }) {
                val svgScale = size.width / 236f
                activeSounds.forEachIndexed { index, sound ->
                    val stored = state.constellation.firstOrNull { it.soundId == sound.id }
                    val point = stored?.let { GalaxyPoint(it.x, it.y) } ?: galaxyPlacement(sound.id, index, activeSounds.size)
                    val effective = (sound.volume * state.environmentVolume * state.globalVolume).coerceIn(0f, 1f)
                    val level = effective.toDouble().pow(.82).toFloat()
                    val color = galaxyNodeColor(sound.id, index)
                    val node = Offset(point.x * 236f * svgScale, point.y * 236f * svgScale)
                    val glow = .018f + level * .64f
                    val halo = .035f + level * .88f
                    val body = .12f + level * .88f
                    val bodyColor = galaxyNodeBodyColor(color, level)
                    // 光晕圆 r=9.5：fill color opacity glow + drop-shadow(0 0 5px color)，source-over。
                    // drop-shadow 是圆盘的高斯模糊：内部峰值≈glow、边缘(r=9.5)≈0.5×glow、拖尾到 17 单位。
                    val glowRadius = 17f
                    drawCircle(
                        Brush.radialGradient(colorStops = gaussianShadowStops(color, 9.5f, glowRadius, glow), center = node, radius = glowRadius * svgScale),
                        radius = glowRadius * svgScale, center = node,
                    )
                    drawCircle(color.copy(alpha = glow), 9.5f * svgScale, node)
                    // 外环 r=8.4：stroke color width .85 opacity halo
                    drawCircle(color.copy(alpha = halo), 8.4f * svgScale, node, style = Stroke(.85f * svgScale))
                    // 实体圆 r=5.5：fill color opacity body + drop-shadow(0 0 (2.5+6vl)px color99)，source-over。
                    // shadow 色 color99（alpha .6）× 元素 opacity body → 峰值 body*.6；高斯拖尾到 R + 1.5×(2.5+6vl)。
                    val bodyBlur = 2.5f + 6f * level
                    val bodyTail = 5.5f + 1.5f * bodyBlur
                    drawCircle(
                        Brush.radialGradient(colorStops = gaussianShadowStops(bodyColor, 5.5f, bodyTail, body * .6f), center = node, radius = bodyTail * svgScale),
                        radius = bodyTail * svgScale, center = node,
                    )
                    drawCircle(bodyColor.copy(alpha = body), 5.5f * svgScale, node)
                    // 内芯 r=2.1：rgba(145,211,197,.1+vl*.48)
                    drawCircle(Color(0xFF91D3C5).copy(alpha = .1f + level * .48f), 2.1f * svgScale, node)
                }
            }
            activeSounds.forEachIndexed { index, sound ->
                val stored = state.constellation.firstOrNull { it.soundId == sound.id }
                val point = stored?.let { GalaxyPoint(it.x, it.y) } ?: galaxyPlacement(sound.id, index, activeSounds.size)
                val effective = (sound.volume * state.environmentVolume * state.globalVolume).coerceIn(0f, 1f)
                val volumeLevel = effective.toDouble().pow(.82).toFloat()
                val iconSize = 16.dp * (224f / 236f)
                val iconPadding = 2.dp * (224f / 236f)
                androidx.compose.material3.Icon(
                    soundIcon(sound.id), "定位${sound.name}混音轨道",
                    tint = SoundistColors.Abyss.copy(alpha = .8f * (.38f + volumeLevel * .62f)),
                    modifier = Modifier.offset(maxWidth * point.x - iconSize / 2, maxHeight * point.y - iconSize / 2)
                        .size(iconSize).clickable { onSoundClick(sound.id) }.padding(iconPadding),
                )
            }
        }
    }
}

internal data class DriftParticle(var x: Float, var y: Float, var vx: Float, var vy: Float, val size: Float, val hue: Float, val light: Float, val alpha: Float) {
    var prevX: Float = x
    var prevY: Float = y
}
internal data class DustParticle(var x: Float, var y: Float, val vx: Float, val vy: Float, val size: Float, var life: Float, val hue: Float, val light: Float) {
    var prevX: Float = x
    var prevY: Float = y
    var prevLife: Float = life
}

/**
 * Fixed-timestep animation clock for the deep-sea canvas.
 *
 * Real frame deltas from `withFrameNanos` are measured in seconds (Double) and accumulated; the
 * simulation only advances in whole fixed steps of [fixedStepSeconds] (1/60 s), exactly as the old
 * 60fps per-frame increments were written. Every device — 60/90/120 Hz — therefore runs the same
 * number of simulation steps per wall-clock second, and every animation phase derives from
 * [simulationTimeSeconds], never from the absolute frame timestamp.
 *
 * Robustness:
 * - [reset] forgets the previous frame timestamp (first frame, re-entering composition, foreground
 *   resume) so pause/background time is never folded into the accumulator.
 * - dt is clamped to [maxDtSeconds]; a huge delta (e.g. a long pause) can therefore inject only a
 *   bounded amount of time.
 * - At most [maxStepsPerFrame] steps run per frame; when the accumulator still holds a whole step
 *   after that cap, the whole backlog is discarded while the sub-step remainder (< one fixed step) is
 *   kept for interpolation — background time is not chased.
 */
internal class DeepSeaAnimationClock(maxStepsPerFrame: Int = 5) {
    val fixedStepSeconds: Double = 1.0 / 60.0
    private val maxDtSeconds: Double = 0.25
    private val maxStepsPerFrame: Int = maxStepsPerFrame

    /** Total simulated seconds advanced in whole fixed steps. */
    var elapsedSeconds: Double = 0.0
        private set

    /** Sub-step leftover of the current frame (< one fixed step), used for draw interpolation. */
    var accumulator: Double = 0.0
        private set

    /** Total number of fixed simulation steps executed. */
    var updateCount: Long = 0L
        private set

    /** Frame timestamp (ns) of the previous frame; 0 means "no previous frame yet". */
    var lastFrameNanos: Long = 0L
        private set

    /** Simulation clock in seconds (Double). All animation phases read this. */
    val simulationTimeSeconds: Double get() = elapsedSeconds

    /** 0..1 — how much of the current frame has elapsed before the next fixed step (draw interpolation). */
    val interpolationAlpha: Float
        get() = (accumulator / fixedStepSeconds).coerceIn(0.0, 1.0).toFloat()

    /** Forget the previous frame so the next [advance] starts with a fresh delta. */
    fun reset() { lastFrameNanos = 0L; accumulator = 0.0 }

    /**
     * Consume one real frame at [now] (ns). Returns how many fixed steps to run (0..[maxStepsPerFrame])
     * and advances [elapsedSeconds]/[updateCount] accordingly. If the accumulator still holds a whole
     * step after the cap, the whole-step backlog is dropped but the sub-step remainder is retained.
     */
    fun advance(now: Long): Int {
        if (lastFrameNanos == 0L) lastFrameNanos = now
        val dt = ((now - lastFrameNanos) / 1_000_000_000.0).coerceAtMost(maxDtSeconds)
        lastFrameNanos = now
        accumulator += dt
        var steps = 0
        while (accumulator >= fixedStepSeconds && steps < maxStepsPerFrame) {
            accumulator -= fixedStepSeconds
            steps++
        }
        if (accumulator >= fixedStepSeconds) accumulator %= fixedStepSeconds
        updateCount += steps
        elapsedSeconds += steps * fixedStepSeconds
        return steps
    }
}

internal class DeepSeaParticleEngine(seed: Int) {
    private val random = Random(seed)
    val drift = MutableList(460) {
        DriftParticle(random.nextFloat(), random.nextFloat(), (random.nextFloat() - .5f) * .0032f, (random.nextFloat() - .5f) * .0032f, .45f + random.nextFloat() * 1.05f, if (random.nextFloat() > .82f) 38f else 168f + random.nextFloat() * 32f, 46f + random.nextFloat() * 26f, .21f + random.nextFloat() * .27f)
    }
    val dust = ArrayList<DustParticle>(1250)
    private var frame = 0f
    /** 粒子发射分数 carry：把每步不足 1 个粒子的 fraction 累积到下一帧，避免 .toInt() 丢弃导致漂移。 */
    private var emissionCarry = 0f
    /** Fixed-step frequency (steps per second) at which the tuning constants below were set. */
    private val baselineHz = 60f

    /**
     * Advance one simulation step of [dtSeconds] seconds. Every tuning constant is expressed per
     * baseline second (baselineHz = 60) and scaled by `dtSeconds * baselineHz` — which is exactly
     * 1.0 for the fixed 1/60 s step, so 60fps behaviour is bit-identical to the legacy per-step
     * constants, while the same step() call can be driven at any fixed dt without changing the
     * visual speed.
     */
    fun step(anyPlayback: Boolean, isPlaying: Boolean, intensity: Float, reducedMotion: Boolean, dtSeconds: Float = 1f / 60f) {
        val dtSteps = dtSeconds * baselineHz
        val driftScale = if (reducedMotion) 0f else if (anyPlayback) .72f else .32f
        drift.forEach { p ->
            p.prevX = p.x; p.prevY = p.y
            p.vx += (random.nextFloat() - .5f) * .00072f * driftScale; p.vy += (random.nextFloat() - .5f) * .00072f * driftScale
            // per-second damping (per baseline second), compounded over dtSteps steps
            val damp = (if (anyPlayback) .982f else .988f).pow(dtSteps)
            p.vx *= damp; p.vy *= damp
            val speed = hypot(p.vx, p.vy); val maxSpeed = if (anyPlayback) .0062f else .0028f
            if (speed > maxSpeed) { p.vx = p.vx / speed * maxSpeed; p.vy = p.vy / speed * maxSpeed }
            p.x += p.vx * driftScale; p.y += p.vy * driftScale
            var wrapped = false
            if (p.x < -.05f) { p.x = 1.05f; wrapped = true } else if (p.x > 1.05f) { p.x = -.05f; wrapped = true }
            if (p.y < -.05f) { p.y = 1.05f; wrapped = true } else if (p.y > 1.05f) { p.y = -.05f; wrapped = true }
            if (wrapped) { p.prevX = p.x; p.prevY = p.y }
            val dx = p.x - .5f; val dy = p.y - .5f; val centerDistance = hypot(dx, dy); val cd = if (centerDistance == 0f) 1f else centerDistance
            if (cd < .2f) { val push = (.2f - cd) * .0011f * driftScale; p.vx += dx / cd * push; p.vy += dy / cd * push }
        }
        // 发射率 = 每秒粒子数（baseline 60Hz 调参换算：82/step × 60 = 4920/s）+ 分数 carry，
        // 保证不同帧率/步长下累计发射数精确一致（60fps 密度不变）。
        val activityScale = (.18f + intensity.coerceIn(0f, 1f) * .82f)
        val particlesPerSecond = (if (reducedMotion) 150f else if (isPlaying) 64f else 12f) * activityScale * baselineHz
        emissionCarry += particlesPerSecond * dtSeconds
        val emitCount = emissionCarry.toInt()
        emissionCarry -= emitCount
        repeat(emitCount) { i ->
            val theta = i / emitCount.toFloat() * (PI * 2).toFloat() + frame * .013f
            val wave = 1f + max(0f, sin(frame * .052f + i * .65f)) * .65f
            val speed = .06f + random.nextFloat() * .48f; val jitter = (random.nextFloat() - .5f) * .42f
            dust += DustParticle(cos(theta) * (75.392f + random.nextFloat() * 8f * wave), sin(theta) * (75.392f + random.nextFloat() * 8f * wave), cos(theta + jitter) * speed * wave, sin(theta + jitter) * speed * wave, .28f + random.nextFloat() * .9f, 78f + random.nextFloat() * 22f, if (random.nextFloat() < .92f) 164f + random.nextFloat() * 24f else 34f + random.nextFloat() * 10f, 34f + random.nextFloat() * 22f)
        }
        if (dust.size > 1250) dust.subList(0, dust.size - 1250).clear()
        val iterator = dust.iterator(); val motion = if (reducedMotion) 0f else if (isPlaying) .68f else .24f
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.prevX = p.x; p.prevY = p.y; p.prevLife = p.life
            val swirl = .006f * motion; val c = cos(swirl); val s = sin(swirl)
            val rx = p.x * c - p.y * s; val ry = p.x * s + p.y * c
            p.x = rx + p.vx * motion; p.y = ry + p.vy * motion
            // per-second life decay (per baseline second), scaled to this step
            p.life -= if (reducedMotion) 0f else (if (isPlaying) .44f else .24f) * dtSteps
            val distance = hypot(p.x, p.y)
            // Compounding Bernoulli cull per baseline step: P(cull) = 1-(1-p)^n; exact at n = dtSteps = 1.
            val cullProb = (100f - p.life) / 360f
            val cullThreshold = if (dtSteps == 1f) cullProb else 1f - (1f - cullProb).pow(dtSteps)
            val randomCull = random.nextFloat() < cullThreshold
            if (distance < 74.63808f || distance > 158.08f || p.life <= 0f || randomCull) iterator.remove()
        }
        // frame phase advances in baseline-step units, so the emission angle/wave speed is dt-invariant.
        frame += dtSteps
        @Suppress("UNUSED_VARIABLE") val level = sqrt(intensity.coerceIn(0f, 1f))
    }
}

internal data class GalaxyPoint(val x: Float, val y: Float)
internal fun galaxyPlacement(id: String, index: Int, count: Int): GalaxyPoint {
    val ids = List(index) { "preceding-$it" } + id
    return galaxyPlacements(ids, count)[id] ?: GalaxyPoint(-64f / 236f, 232f / 236f)
}

internal fun galaxyPlacements(ids: List<String>, count: Int = ids.size, seeds: Map<String, GalaxyPoint> = emptyMap()): Map<String, GalaxyPoint> {
    val placed = LinkedHashMap<String, GalaxyPoint>()
    // App.tsx placementRef: nodes already positioned keep their spot; only new ids are placed
    // (and they avoid the existing ones). This stops the whole galaxy from reshuffling on any
    // sound toggle.
    ids.forEach { id -> seeds[id]?.let { placed[id] = it } }
    val minGap = if (count > 15) 21f else if (count > 10) 25f else 32f
    // Math.random in App.tsx is replaced only by a seeded stream so placement persists on Android.
    // Candidate bands, retries, relaxed-gap retry and fallback zones remain mechanically identical.
    val random = Random(ids.fold(0x50A1D157) { seed, id -> seed * 31 + id.hashCode() })
    fun between(minimum: Float, maximum: Float) = minimum + random.nextFloat() * (maximum - minimum)
    fun allowed(x: Float, y: Float) = hypot(x - 118f, y - 118f) > 112f && !(x > 8f && x < 228f && y > -82f && y < 42f)
    fun candidate(): GalaxyPoint {
        val band = random.nextFloat()
        val x = between(-64f, 300f)
        val y = when {
            band < .28f -> between(-76f, 118f - 112f * .72f)
            band < .54f -> between(118f + 112f * .72f, 232f)
            else -> between(-76f, 232f)
        }
        return GalaxyPoint(x / 236f, y / 236f)
    }
    ids.forEach { id ->
        if (placed.containsKey(id)) return@forEach
        var accepted: GalaxyPoint? = null
        var attempt = 0
        while (attempt < 1600 && accepted == null) {
            val next = candidate()
            val x = next.x * 236f
            val y = next.y * 236f
            val gap = if (attempt < 900) minGap else 24f
            if (allowed(x, y) && placed.values.all { hypot(it.x * 236f - x, it.y * 236f - y) > gap }) accepted = GalaxyPoint(x / 236f, y / 236f)
            attempt++
        }
        if (accepted == null) {
            repeat(500) {
                val next = candidate()
                if (accepted == null && allowed(next.x * 236f, next.y * 236f)) accepted = next
            }
        }
        if (accepted == null) {
            repeat(16) {
                val next = when (random.nextInt(4)) {
                    0 -> GalaxyPoint(between(-64f, 300f) / 236f, between(-76f, 118f - 112f * .72f) / 236f)
                    1 -> GalaxyPoint(between(-64f, 300f) / 236f, between(118f + 112f * .72f, 232f) / 236f)
                    2 -> GalaxyPoint(between(-64f, 118f - 112f * .78f) / 236f, between(-76f, 232f) / 236f)
                    else -> GalaxyPoint(between(118f + 112f * .78f, 300f) / 236f, between(-76f, 232f) / 236f)
                }
                if (accepted == null && allowed(next.x * 236f, next.y * 236f)) accepted = next
            }
        }
        placed[id] = accepted ?: GalaxyPoint(-64f / 236f, 232f / 236f)
    }
    return placed
}

/** App.tsx `makeNoise(seed)` — sine-map deterministic PRNG, one `next()` per `rand()` call. */
private class SineNoise(seed: Int) {
    private var x = sin(seed * 999.0) * 10000.0
    fun next(): Float { x = sin(x) * 10000.0; return (x - floor(x)).toFloat() }
}

private data class NebulaBlob(val base: Float, val drift: Float, val dist: Float, val rx: Float, val ry: Float) {
    fun center(center: Offset, scale: Float) = center + Offset(cos(base + drift) * dist * scale, sin(base * .92f + drift) * dist * .62f * scale)
    fun rotationDegrees(t: Float): Float = (base + t * .03f) * (180f / PI).toFloat()
}

private fun deepSeaNebulaBlobs(t: Float): List<NebulaBlob> {
    val rand = SineNoise(21)
    return List(16) { i ->
        val base = rand.next() * (PI * 2).toFloat()
        val drift = sin(t * (.18f + rand.next() * .08f) + i) * .2f
        val rx = 22f + rand.next() * 58f
        val ry = 8f + rand.next() * 24f
        val dist = 8f + rand.next() * 56f
        NebulaBlob(base, drift, dist, rx, ry)
    }
}
/** CSS `hsla(h,s,l%,1)` → sRGB ARGB (alpha 255), exactly as the browser parses the frontend's fillStyle. */
private fun hslArgb(hue: Float, saturation: Float, light: Float): Int {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val l = (light / 100f).coerceIn(0f, 1f)
    fun hue2rgb(p: Float, q: Float, t0: Float): Float {
        var t = t0
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 1f / 2f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }
    val (r, g, b) = if (s <= 0f) {
        Triple(l, l, l)
    } else {
        val q = if (l < .5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q
        Triple(hue2rgb(p, q, h / 360f + 1f / 3f), hue2rgb(p, q, h / 360f), hue2rgb(p, q, h / 360f - 1f / 3f))
    }
    fun byte(v: Float): Int = (v * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (byte(r) shl 16) or (byte(g) shl 8) or byte(b)
}

/**
 * Premultiplies sRGB [argb] by [alpha], baking the alpha into RGB with A forced to 255. The native
 * drawVertices blend (`BlendMode.ADD` / CSS `lighter`) adds colour*alpha, and with A constant at 255
 * skia's straight and premultiplied interpolation coincide, so the per-vertex contribution is exactly
 * `argb * alpha` regardless of how the platform handles per-vertex alpha.
 */
private fun premultiplyArgb(argb: Int, alpha: Float): Int {
    val a = alpha.coerceIn(0f, 1f)
    if (a <= 0f) return 0xFF000000.toInt()
    fun scale(byte: Int): Int = min(255, (byte * a + 0.5f).toInt())
    val r = scale((argb shr 16) and 0xFF)
    val g = scale((argb shr 8) and 0xFF)
    val b = scale(argb and 0xFF)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
private fun galaxyNodeColor(id: String, index: Int): Color = when { listOf("fire", "cafe", "restaurant").any(id::contains) -> Color(0xFFC99662); listOf("rain", "water", "waves").any(id::contains) -> Color(0xFF738FA4); listOf("bird", "wind", "tree").any(id::contains) -> Color(0xFF7FAE87); else -> listOf(Color(0xFF64AFA1), Color(0xFF738FA4), Color(0xFFC99662), Color(0xFF7FAE87), Color(0xFFA292C1), Color(0xFFD8849B), Color(0xFFA7D3C8), Color(0xFF7F8C87))[index % 8] }

/**
 * App.tsx body node: `filter: brightness(0.68 + level*1.18) saturate(0.84 + level*0.38)`.
 * Both are per-pixel colour transforms on a solid fill, so they are folded into the fill colour:
 * brightness multiplies each channel, saturate applies the CSS luminance-preserving matrix.
 */
private fun galaxyNodeBodyColor(base: Color, level: Float): Color {
    val brightness = .54f + level * 1.52f
    val sat = .78f + level * .48f
    // CSS filter brightness/saturate operate on linear-light RGB, not sRGB channels (G3).
    fun srgbToLinear(c: Float): Float = if (c <= .04045f) c / 12.92f else ((c + .055f) / 1.055f).pow(2.4f)
    fun linearToSrgb(c: Float): Float = if (c <= .0031308f) c * 12.92f else 1.055f * c.pow(1f / 2.4f) - .055f
    val lr = srgbToLinear(base.red); val lg = srgbToLinear(base.green); val lb = srgbToLinear(base.blue)
    val br = lr * brightness; val bg = lg * brightness; val bb = lb * brightness
    val r2 = (0.213f + 0.787f * sat) * br + (0.715f - 0.715f * sat) * bg + (0.072f - 0.072f * sat) * bb
    val g2 = (0.213f - 0.213f * sat) * br + (0.715f + 0.285f * sat) * bg + (0.072f - 0.072f * sat) * bb
    val b2 = (0.213f - 0.213f * sat) * br + (0.715f - 0.715f * sat) * bg + (0.072f + 0.928f * sat) * bb
    return Color(linearToSrgb(r2).coerceIn(0f, 1f), linearToSrgb(g2).coerceIn(0f, 1f), linearToSrgb(b2).coerceIn(0f, 1f), 1f)
}

/** Abramowitz–Stegun 7.1.26 error function (max abs error ≈1.5e-7). */
private fun erf(x: Float): Float {
    val t = 1f / (1f + 0.3275911f * abs(x))
    val y = 1f - (((((1.061405429f * t - 1.453152027f) * t) + 1.421413741f) * t - 0.284496736f) * t + 0.254829592f) * t * exp(-(x * x))
    return if (x >= 0f) y else -y
}

/**
 * Colour stops for a canvas/CSS drop-shadow: the blurred copy of a filled disc of radius [discRadius].
 * Profile P(r)=0.5·(1+erf((discRadius-r)/(σ√2))) with σ=(tail-discRadius)/3, so P=1 at the centre,
 * 0.5 at the disc edge and ≈0 at the tail. Samples straddle the edge so the piecewise-linear
 * interpolation of the radial gradient tracks the Gaussian roll-off. [peakAlpha] is the shadow's peak.
 */
private fun gaussianShadowStops(color: Color, discRadius: Float, tailRadius: Float, peakAlpha: Float): Array<Pair<Float, Color>> {
    val sigma = (tailRadius - discRadius) / 3f
    val s2 = sigma * sqrt(2f)
    val radii = floatArrayOf(
        0f,
        discRadius - 2f * sigma, discRadius - sigma, discRadius,
        discRadius + .5f * sigma, discRadius + sigma, discRadius + 1.5f * sigma,
        discRadius + 2f * sigma, discRadius + 2.5f * sigma, tailRadius,
    ).filter { it >= 0f }.distinct().sorted()
    return Array(radii.size) { i ->
        val r = radii[i].coerceAtMost(tailRadius)
        val p = .5f * (1f + erf((discRadius - r) / s2))
        (r / tailRadius) to color.copy(alpha = (peakAlpha * p).coerceIn(0f, 1f))
    }
}

/**
 * Reusable mesh buffer that batches many soft-dot particles into ONE native `drawVertices` call.
 *
 * Each particle is a centre vertex + three rings:
 *   ring 0 at radius R                 -> ring colour (hard fill disc, or Gaussian sample)
 *   ring 1 at radius R + mid           -> mid colour  (intermediate Gaussian sample)
 *   ring 2 at radius R + tail          -> transparent (Gaussian tail end)
 * With tail = 0 every ring collapses onto R and only the flat disc renders (AmbientDrift / dust fill).
 * With tail > 0 the rim falls off piecewise-linearly through the mid sample — a Gaussian-shaped
 * drop-shadow tail instead of the old hard (R + shadowBlur) disc.
 *
 * Colours are stored premultiplied into RGB with alpha = 255, so the ADD (lighter) blend adds exactly
 * colour * alpha. Per-particle hue/light/alpha/size stay exact while the whole field is a single draw op.
 */
private class ParticleMeshBuffer(segments: Int) {
    val segmentCount = segments
    private val cosT = FloatArray(segments) { i -> cos(i / segments.toFloat() * 2f * PI.toFloat()) }
    private val sinT = FloatArray(segments) { i -> sin(i / segments.toFloat() * 2f * PI.toFloat()) }
    var positions = FloatArray(0)
        private set
    var colors = IntArray(0)
        private set
    var indices = ShortArray(0)
        private set
    var vertexCount = 0
        private set
    var indexCount = 0
        private set

    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        style = android.graphics.Paint.Style.FILL
        color = 0xFFFFFFFF.toInt()
        @Suppress("DEPRECATION")
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD)
    }

    fun reset() {
        vertexCount = 0
        indexCount = 0
    }

    fun ensureCapacity(maxParticles: Int) {
        val needV = maxParticles * (1 + 3 * segmentCount)
        if (positions.size < needV * 2) {
            positions = FloatArray(needV * 2)
            colors = IntArray(needV)
        }
        val needI = maxParticles * segmentCount * 15
        if (indices.size < needI) indices = ShortArray(needI)
    }

    /**
     * Appends one particle as a centre vertex + three rings:
     *   ring 0 at [radius]            -> [ringPremul]   (hard disc edge / Gaussian midpoint start)
     *   ring 1 at [radius]+[mid]      -> [midPremul]    (intermediate Gaussian sample)
     *   ring 2 at [radius]+[tail]     -> transparent    (Gaussian tail end)
     * With [tail]=0 all rings collapse onto [radius] and only the flat disc fan renders (AmbientDrift /
     * dust fill). With [tail]>0 the rim falls off piecewise-linearly through [mid] — a Gaussian-shaped
     * drop-shadow tail instead of the old hard (R + shadowBlur) disc.
     * All colours are RGB-premultiplied (alpha baked to 255) so the ADD (lighter) blend adds colour*alpha.
     */
    fun add(cx: Float, cy: Float, radius: Float, tail: Float, mid: Float, centerPremul: Int, ringPremul: Int, midPremul: Int) {
        val n = segmentCount
        val base = vertexCount
        positions[base * 2] = cx
        positions[base * 2 + 1] = cy
        colors[base] = centerPremul
        val midR = radius + mid
        val outer = radius + tail
        for (i in 0 until n) {
            val c = cosT[i]; val s = sinT[i]
            val disc = base + 1 + i
            positions[disc * 2] = cx + c * radius
            positions[disc * 2 + 1] = cy + s * radius
            colors[disc] = ringPremul
            val midRing = base + 1 + n + i
            positions[midRing * 2] = cx + c * midR
            positions[midRing * 2 + 1] = cy + s * midR
            colors[midRing] = midPremul
            val out = base + 1 + 2 * n + i
            positions[out * 2] = cx + c * outer
            positions[out * 2 + 1] = cy + s * outer
            colors[out] = 0xFF000000.toInt()
        }
        var idx = indexCount
        for (i in 0 until n) {
            val j = if (i + 1 == n) 0 else i + 1
            val center = base.toShort()
            val di = (base + 1 + i).toShort()
            val dj = (base + 1 + j).toShort()
            val mi = (base + 1 + n + i).toShort()
            val mj = (base + 1 + n + j).toShort()
            val oi = (base + 1 + 2 * n + i).toShort()
            val oj = (base + 1 + 2 * n + j).toShort()
            indices[idx++] = center; indices[idx++] = di; indices[idx++] = dj
            indices[idx++] = di; indices[idx++] = dj; indices[idx++] = mi
            indices[idx++] = dj; indices[idx++] = mj; indices[idx++] = mi
            indices[idx++] = mi; indices[idx++] = mj; indices[idx++] = oi
            indices[idx++] = mj; indices[idx++] = oj; indices[idx++] = oi
        }
        indexCount = idx
        vertexCount = base + 1 + 3 * n
    }
}

/** Issues the buffered mesh as a single additive draw op (no-op when empty). */
private fun DrawScope.drawParticles(mesh: ParticleMeshBuffer) {
    if (mesh.vertexCount == 0) return
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawVertices(
            android.graphics.Canvas.VertexMode.TRIANGLES,
            mesh.vertexCount,
            mesh.positions, 0,
            null, 0,
            mesh.colors, 0,
            mesh.indices, 0,
            mesh.indexCount,
            mesh.paint,
        )
    }
}

/**
 * App.tsx AmbientDriftCanvas draw() (1509–1512): per-particle
 * `fillStyle=hsla(p.hue,58%,p.light%,p.alpha)` + `arc(p.size)` + `fill()` under `lighter`.
 * One-to-one colour/size/alpha per particle, batched into one additive draw op (460 hard discs).
 */
private fun DrawScope.drawTwoCircleRadialGradient(
    clipCenter: Offset,
    clipRadius: Float,
    innerCenter: Offset,
    innerRadius: Float,
    outerCenter: Offset,
    outerRadius: Float,
    stops: Array<Pair<Float, Color>>,
) {
    fun colorAt(value: Float): Color {
        val t = value.coerceIn(0f, 1f)
        if (t <= stops.first().first) return stops.first().second
        if (t >= stops.last().first) return stops.last().second
        val upperIndex = stops.indexOfFirst { it.first >= t }.coerceAtLeast(1)
        val lower = stops[upperIndex - 1]
        val upper = stops[upperIndex]
        val local = ((t - lower.first) / (upper.first - lower.first).coerceAtLeast(.000001f)).coerceIn(0f, 1f)
        return lerp(lower.second, upper.second, local)
    }

    val clip = Path().apply {
        addOval(Rect(clipCenter - Offset(clipRadius, clipRadius), clipCenter + Offset(clipRadius, clipRadius)))
    }
    clipPath(clip) {
        drawCircle(stops.last().second, clipRadius, clipCenter)
        // A Canvas two-circle radial gradient is the family C(t)=lerp(C0,C1,t),
        // R(t)=lerp(R0,R1,t). 384 isolines leave sub-pixel steps at the 224dp core.
        for (step in 384 downTo 0) {
            val t = step / 384f
            val c = Offset(
                innerCenter.x + (outerCenter.x - innerCenter.x) * t,
                innerCenter.y + (outerCenter.y - innerCenter.y) * t,
            )
            val radius = innerRadius + (outerRadius - innerRadius) * t
            drawCircle(colorAt(t), radius, c, blendMode = BlendMode.Src)
        }
    }
}

private fun DrawScope.drawDrift(particles: List<DriftParticle>, viewport: Size, scale: Float, interp: Float) {
    particles.forEach { p ->
        val x = p.prevX + (p.x - p.prevX) * interp
        val y = p.prevY + (p.y - p.prevY) * interp
        drawCircle(
            color = Color(hslArgb(p.hue, .58f, p.light)).copy(alpha = p.alpha),
            radius = p.size * scale,
            center = Offset(x * viewport.width, y * viewport.height),
            blendMode = BlendMode.Plus,
        )
    }
}

/**
 * App.tsx CosmicDustCanvas draw() (1390–1396) under `lighter`, rendered as two additive passes:
 *   Pass 1 — hard fill disc:  fillStyle hsla(hue,62%,light+4%,alpha), arc(p.size), fill().
 *   Pass 2 — shadow:          shadowColor hsla(hue,68%,light+8%,alpha*.45) for every particle.
 * shadowBlur = size>0.72 ? 1.8 : 0. With blur=0 the browser still draws the shadow as an unblurred
 * sharp copy of the fill disc, so small particles render ≈ fill(α) + shadow(α*.45) under `lighter`.
 * With blur=1.8 the shadow is the real Gaussian blur of the disc (browser σ≈blur/2=0.9): the centre
 * peak = α·.45·(1−e^{−R²/2σ²}) decays with size, the edge is ≈ half of that, and the tail is a
 * Gaussian fall-off that reaches ≈0 at R+4px (4.4σ).
 */
private fun DrawScope.drawDust(particles: List<DustParticle>, center: Offset, scale: Float, playing: Boolean, intensity: Float, interp: Float) {
    val level = sqrt(intensity.coerceIn(0f, 1f))
    val base = if (playing) .46f else .18f
    val factor = .18f + level * .82f
    // Pass 1 — true anti-aliased circles, matching ctx.arc(...); the former eight-sided
    // vertex fan visibly faceted the smallest dust and changed its brightness.
    particles.forEach { p ->
        val lx = p.prevX + (p.x - p.prevX) * interp
        val ly = p.prevY + (p.y - p.prevY) * interp
        val life = p.prevLife + (p.life - p.prevLife) * interp
        val alpha = (life / 100f).coerceIn(0f, 1f) * base * factor
        drawCircle(
            color = Color(hslArgb(p.hue, .62f, p.light + 4f)).copy(alpha = alpha),
            radius = p.size * scale,
            center = Offset(center.x + lx * scale, center.y + ly * scale),
            blendMode = BlendMode.Plus,
        )
    }
    // Pass 2 — shadow, every particle. Frontend shadowColor = hsla(hue,68%,light+8%,alpha*.45).
    //   size ≤ 0.72 (shadowBlur=0): an unblurred sharp copy of the fill disc in the shadow colour,
    //   so the particle lights up ≈ 1.45× under `lighter` (fill α + shadow α*.45).
    //   size > 0.72 (shadowBlur=1.8): the real Gaussian blur of the disc, σ=0.9. The exact blurred
    //   profile is sampled at the centre (peak = α·.45·(1−e^{−R²/2σ²}), decaying with R), the disc
    //   edge and R+1.8px; the erf step-response is normalised so the centre matches the true peak.
    //   Tail ends (≈0) at R+4px ≈ 4.4σ.
    val sigma = .9f
    particles.forEach { p ->
        val lx = p.prevX + (p.x - p.prevX) * interp
        val ly = p.prevY + (p.y - p.prevY) * interp
        val life = p.prevLife + (p.life - p.prevLife) * interp
        val alpha = (life / 100f).coerceIn(0f, 1f) * base * factor
        val shadow = hslArgb(p.hue, .68f, p.light + 8f)
        val px = center.x + lx * scale
        val py = center.y + ly * scale
        if (p.size <= .72f) {
            drawCircle(Color(shadow).copy(alpha = alpha * .45f), p.size * scale, Offset(px, py), blendMode = BlendMode.Plus)
        } else {
            val r = p.size
            val peak = alpha * .45f * (1f - exp(-r * r / (2f * sigma * sigma)))
            val discRadius = r * scale
            val tailRadius = (r + 4f) * scale
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = gaussianShadowStops(Color(shadow), discRadius, tailRadius, peak),
                    center = Offset(px, py),
                    radius = tailRadius,
                ),
                radius = tailRadius,
                center = Offset(px, py),
                blendMode = BlendMode.Plus,
            )
        }
    }
}

/**
 * App.tsx renderHome "Breathing ambient glow" div (inset -18%, radial-gradient, blur 22px).
 * breath toggles every 4000ms while anyPlayback and scale/opacity transition over 4500ms with the
 * CSS ease-in-out curve (cubic-bezier(.42,0,.58,1)). The breath phase and the eased transition are
 * read directly off the fixed-step [simulationTimeSeconds] — one single clock — instead of two
 * independent delay/animateFloat clocks, so the breathing is refresh-rate independent (identical on
 * 60/90/120 Hz) and picks up exactly where the simulation left off after pause/resume.
 * anyPlayback=false or reduceMotion shows the static idle state (scale 0.94 / opacity 0.12).
 */
@Composable
private fun BreathingGlow(isPlaying: Boolean, anyPlayback: Boolean, reduceMotion: Boolean, simulationTimeSeconds: Double) {
    // The glow only breathes while the ambient sound is actually playing (radio-only is static).
    val breathing = isPlaying && anyPlayback && !reduceMotion
    val scale: Float
    val opacity: Float
    if (!breathing) {
        scale = 0.94f
        opacity = 0.12f
    } else {
        // breath toggles every 4000ms: the first half of each 8000ms cycle holds the low target,
        // the second half the high target (breath starts false, matching the frontend).
        val window = floor(simulationTimeSeconds / 4.0)
        val breathSquare = window.toLong() % 2L == 1L
        // 0..1 progress within the current 4000ms window, eased with the CSS ease-in-out curve.
        val progress = ((simulationTimeSeconds - window * 4.0) / 4.0).toFloat().coerceIn(0f, 1f)
        val eased = CubicBezierEasing(.42f, 0f, .58f, 1f).transform(progress)
        // cubic-bezier(.42,0,.58,1) has zero slope at both ends, so this ping-pong wave is C1-smooth.
        if (breathSquare) {
            scale = 1.0f + .12f * eased
            opacity = .36f + .32f * eased
        } else {
            scale = 1.12f - .12f * eased
            opacity = .68f - .32f * eased
        }
    }
    Box(
        Modifier
            .size(304.64f.dp)
            .graphicsLayer { this.scaleX = scale; this.scaleY = scale; this.alpha = opacity }
            .blur(22.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            .background(Brush.radialGradient(0f to Color(0x1A55B6A3), .5f to Color(0x0E183C36), .74f to Color.Transparent), CircleShape),
    )
}
