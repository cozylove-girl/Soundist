package com.soundist.app

import android.Manifest
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.OpenableColumns
import android.content.Intent
import android.app.Activity
import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import android.content.res.Configuration
import android.view.HapticFeedbackConstants
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import com.soundist.core.audio.Media3AudioRuntime
import com.soundist.core.audio.PlaybackServiceController
import com.soundist.core.designsystem.PlaybackIndicator
import com.soundist.core.designsystem.archiveRestore
import com.soundist.core.designsystem.barChart2
import com.soundist.core.designsystem.home
import com.soundist.core.designsystem.music2
import com.soundist.core.designsystem.radio
import com.soundist.core.designsystem.timer
import com.soundist.core.designsystem.SoundistBottomBar
import com.soundist.core.designsystem.SoundistColors
import com.soundist.core.designsystem.SoundistNavItem
import com.soundist.core.designsystem.SoundistTheme
import com.soundist.core.designsystem.SoundistSelect
import com.soundist.core.designsystem.chevronDown
import com.soundist.core.designsystem.moon
import com.soundist.core.designsystem.save
import com.soundist.core.designsystem.settings2
import com.soundist.core.designsystem.x
import com.soundist.feature.listening.ListeningDestination
import com.soundist.feature.listening.ListeningRoute
import com.soundist.feature.listening.ListeningViewModel
import com.soundist.feature.listening.ListeningViewModelFactory
import com.soundist.feature.listening.StationArtworkPicker
import com.soundist.feature.listening.StationAudioPicker
import com.soundist.feature.listening.LocalAudioSelection
import com.soundist.feature.listening.PlaybackState
import com.soundist.feature.listening.RadioSourceKind
import com.soundist.feature.listening.label
import com.soundist.feature.listening.isRadioActive
import com.soundist.feature.notes.AndroidNoteRecorder
import com.soundist.feature.notes.AppPrivateNoteAssetStore
import com.soundist.feature.notes.AttachmentPickerRequest
import com.soundist.feature.notes.AttachmentSelection
import com.soundist.feature.notes.CoreNotesRepository
import com.soundist.feature.notes.NoteAttachmentPicker
import com.soundist.feature.notes.NoteContext
import com.soundist.feature.notes.NoteContextProvider
import com.soundist.feature.notes.NoteContextSnapshot
import com.soundist.feature.notes.NotesRoute
import com.soundist.feature.notes.NotesViewModel
import com.soundist.feature.notes.NotesViewModelFactory
import com.soundist.feature.productivity.LocalProductivityDependencies
import com.soundist.feature.productivity.LocalFocusQuickNoteWriter
import com.soundist.feature.productivity.FocusReviewNote
import com.soundist.feature.productivity.FocusAudioSnapshot
import com.soundist.feature.productivity.FocusSoundSnapshot
import com.soundist.feature.productivity.FocusTarget
import com.soundist.feature.productivity.ProductivityRoute
import com.soundist.feature.productivity.ProductivitySleepHost
import com.soundist.feature.productivity.rememberGlobalFocusTimerText
import com.soundist.feature.productivity.SessionStatus
import com.soundist.feature.productivity.SleepStatus
import com.soundist.feature.productivity.WorkspacePage
import com.soundist.feature.records.AggregatingRecordsRepository
import com.soundist.feature.records.ChannelRecordMetadata
import com.soundist.feature.records.CoreRecordsEventSource
import com.soundist.feature.records.RecordsRoute
import com.soundist.feature.records.RecordsViewModel
import com.soundist.feature.records.RecordsViewModelFactory
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.soundist.core.database.DatabaseMaintenance
import com.soundist.core.database.DatabaseSnapshot
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private val showLaunchPage = launchPagePending.compareAndSet(true, false)
    private val launchContentReady = AtomicBoolean(false)
    private val launchStartedAtMillis = SystemClock.elapsedRealtime()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition {
            showLaunchPage && (
                !launchContentReady.get() ||
                    SystemClock.elapsedRealtime() - launchStartedAtMillis < SYSTEM_SPLASH_MIN_DURATION_MILLIS
                )
        }
        splashScreen.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .setDuration(SYSTEM_SPLASH_EXIT_DURATION_MILLIS)
                .withEndAction(provider::remove)
                .start()
        }
        super.onCreate(savedInstanceState)
        // 强制深色状态栏/导航栏样式（白色图标），避免浅色系统下图标变黑、与深色 App 背景冲突。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            SoundistLaunchHost(
                application = application as SoundistApplication,
                showLaunchPage = showLaunchPage,
                launchNotBeforeMillis = launchStartedAtMillis +
                    SYSTEM_SPLASH_MIN_DURATION_MILLIS +
                    SYSTEM_SPLASH_EXIT_DURATION_MILLIS,
                onLaunchContentReady = { launchContentReady.set(true) },
            )
        }
    }

    private companion object {
        // Resets only when Android creates a new app process. Activity recreation and
        // returning from the background must not replay the branded launch page.
        val launchPagePending = AtomicBoolean(true)
    }
}

@Composable
private fun SoundistLaunchHost(
    application: SoundistApplication,
    showLaunchPage: Boolean,
    launchNotBeforeMillis: Long,
    onLaunchContentReady: () -> Unit,
) {
    var launchVisible by remember { mutableStateOf(showLaunchPage) }
    var launchReady by remember { mutableStateOf(!showLaunchPage) }
    var launchProgress by remember { mutableStateOf(if (showLaunchPage) 0f else 1f) }
    val context = LocalContext.current
    val reducedMotion = remember(context) { context.systemAnimationScale() == 0f }

    LaunchedEffect(showLaunchPage) {
        if (showLaunchPage && !launchReady) {
            // Layout completion is not a draw guarantee. Keep the Android splash
            // above Compose until the launch artwork has survived two display
            // frames, preventing stale task-preview -> icon -> artwork flashes.
            withFrameNanos { }
            withFrameNanos { }
            launchReady = true
            onLaunchContentReady()
        }
    }

    LaunchedEffect(showLaunchPage, launchReady, reducedMotion) {
        if (showLaunchPage && launchReady) {
            val splashRemainingMillis = (
                launchNotBeforeMillis - SystemClock.elapsedRealtime()
            ).coerceAtLeast(0L)
            if (splashRemainingMillis > 0L) delay(splashRemainingMillis)
            if (reducedMotion) {
                delay(800)
            } else {
                var startedAtNanos = 0L
                do {
                    withFrameNanos { frameNanos ->
                        if (startedAtNanos == 0L) startedAtNanos = frameNanos
                        launchProgress = (
                            (frameNanos - startedAtNanos).toDouble() / LAUNCH_MOTION_DURATION_NANOS
                        ).toFloat().coerceIn(0f, 1f)
                    }
                } while (launchProgress < 1f)
            }
            launchVisible = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SoundistApp(application)
        AnimatedVisibility(
            visible = launchVisible,
            exit = fadeOut(animationSpec = tween(durationMillis = 140)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF010513)),
            ) {
                val handoff = if (reducedMotion) 0f else smoothStep(launchPhase(launchProgress, 0.91f, 1f))
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        alpha = 1f - handoff * 0.985f
                        val settleScale = 1.018f - handoff * 0.018f
                        scaleX = settleScale
                        scaleY = settleScale
                    },
                ) {
                    Image(
                        painter = painterResource(R.drawable.launch_page),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (!reducedMotion) {
                        LaunchPageMotion(
                            progress = launchProgress,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LaunchPageMotion(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        // The authored launch image is 841x1870 (approximately 9:20). Mirror
        // ContentScale.Crop so every overlay remains attached to the artwork.
        val imageScale = max(size.width / 841f, size.height / 1870f)
        val imageOffset = Offset(
            x = (size.width - 841f * imageScale) / 2f,
            y = (size.height - 1870f * imageScale) / 2f,
        )
        fun imagePoint(x: Float, y: Float) = Offset(
            x = imageOffset.x + x * imageScale,
            y = imageOffset.y + y * imageScale,
        )
        fun cubicPoint(
            value: Float,
            start: Offset,
            control1: Offset,
            control2: Offset,
            end: Offset,
        ): Offset {
            val inverse = 1f - value
            val inverse2 = inverse * inverse
            val value2 = value * value
            return Offset(
                x = inverse2 * inverse * start.x +
                    3f * inverse2 * value * control1.x +
                    3f * inverse * value2 * control2.x +
                    value2 * value * end.x,
                y = inverse2 * inverse * start.y +
                    3f * inverse2 * value * control1.y +
                    3f * inverse * value2 * control2.y +
                    value2 * value * end.y,
            )
        }
        fun drawSourcePolyline(
            points: List<Pair<Float, Float>>,
            color: Color,
            strokeWidth: Float,
        ) {
            points.zipWithNext().forEach { (start, end) ->
                drawLine(
                    color = color,
                    start = imagePoint(start.first, start.second),
                    end = imagePoint(end.first, end.second),
                    strokeWidth = strokeWidth,
                )
            }
        }

        val gold = Color(0xFFD8B878)
        val goldSoft = Color(0xFFE8C98E)
        val teal = Color(0xFF78D6C5)
        val moonlight = Color(0xFFE8F3EE)

        // The sky wakes in depth before the first visible signal. These glows
        // follow existing painted cloud masses instead of adding new scenery.
        val atmosphere = launchPhase(progress, start = 0f, end = 0.22f)
        if (atmosphere > 0f) {
            val atmospherePulse = sin(PI.toFloat() * atmosphere).coerceAtLeast(0f)
            listOf(
                Triple(188f, 418f, 250f),
                Triple(638f, 506f, 225f),
                Triple(415f, 860f, 315f),
            ).forEachIndexed { index, (sourceX, sourceY, sourceRadius) ->
                val center = imagePoint(
                    sourceX + sin(progress * 5f + index) * 3.5f,
                    sourceY - cos(progress * 4f + index) * 2.5f,
                )
                val radius = sourceRadius * imageScale
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            teal.copy(alpha = atmospherePulse * (0.055f + index * 0.008f)),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = radius,
                    ),
                    center = center,
                    radius = radius,
                )
            }
        }

        // The front paw is the clear origin of the first sound response.
        val paw = imagePoint(258f, 1316f)
        val ignition = launchPhase(progress, start = 0.035f, end = 0.25f)
        if (ignition > 0f && ignition < 1f) {
            val pulse = sin(PI.toFloat() * ignition).coerceAtLeast(0f)
            val glowRadius = (22f + 86f * easeOutCubic(ignition)) * imageScale
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        goldSoft.copy(alpha = pulse * 0.54f),
                        gold.copy(alpha = pulse * 0.17f),
                        Color.Transparent,
                    ),
                    center = paw,
                    radius = glowRadius,
                ),
                radius = glowRadius,
                center = paw,
            )
            drawCircle(
                color = moonlight.copy(alpha = pulse * 0.95f),
                radius = max(2f, 2.7f * imageScale),
                center = paw,
            )
        }

        // One coherent wavefront carries the launch. A distant echo follows the
        // same anchor and perspective instead of splitting into unrelated rings.
        val mainRipple = launchPhase(progress, start = 0.12f, end = 0.46f)
        if (mainRipple > 0f && mainRipple < 1f) {
            val eased = easeOutCubic(mainRipple)
            val radius = (18f + 258f * eased) * imageScale
            val envelope = sin(PI.toFloat() * mainRipple).coerceAtLeast(0f)
            val topLeft = Offset(paw.x - radius, paw.y - radius * 0.21f)
            val rippleSize = Size(radius * 2f, radius * 0.42f)
            drawOval(
                color = gold.copy(alpha = envelope * 0.19f),
                topLeft = topLeft,
                size = rippleSize,
                style = Stroke(width = max(3f, 5.4f * imageScale)),
            )
            drawOval(
                color = goldSoft.copy(alpha = envelope * 0.88f),
                topLeft = topLeft,
                size = rippleSize,
                style = Stroke(width = max(1.4f, 2.15f * imageScale)),
            )
        }

        val distantEcho = launchPhase(progress, start = 0.30f, end = 0.56f)
        if (distantEcho > 0f && distantEcho < 1f) {
            val eased = easeOutCubic(distantEcho)
            val radius = (92f + 286f * eased) * imageScale
            val envelope = sin(PI.toFloat() * distantEcho).coerceAtLeast(0f)
            drawOval(
                color = teal.copy(alpha = envelope * 0.22f),
                topLeft = Offset(paw.x - radius, paw.y - radius * 0.21f),
                size = Size(radius * 2f, radius * 0.42f),
                style = Stroke(width = max(1f, 1.35f * imageScale)),
            )
        }

        // Horizontal refraction glints make the painted reflection respond to
        // the ripples while keeping the flattened artwork intact.
        val refraction = launchPhase(progress, start = 0.17f, end = 0.50f)
        if (refraction > 0f && refraction < 1f) {
            val envelope = sin(PI.toFloat() * refraction).coerceAtLeast(0f)
            repeat(5) { row ->
                val sourceY = 1396f + row * 58f
                val halfWidth = 112f + row * 18f
                val centerX = 415f + sin(refraction * 9f + row * 0.8f) * (4f + row)
                val segments = 12
                repeat(segments) { segment ->
                    val startRatio = segment / segments.toFloat()
                    val endRatio = (segment + 1) / segments.toFloat()
                    val startX = centerX - halfWidth + halfWidth * 2f * startRatio
                    val endX = centerX - halfWidth + halfWidth * 2f * endRatio
                    val startWave = sin(startRatio * PI.toFloat() * 3f + refraction * 7f + row) * 2.2f
                    val endWave = sin(endRatio * PI.toFloat() * 3f + refraction * 7f + row) * 2.2f
                    val segmentAlpha = (1f - kotlin.math.abs(startRatio - 0.5f) * 1.55f)
                        .coerceAtLeast(0f)
                    drawLine(
                        color = if (row % 3 == 0) {
                            goldSoft.copy(alpha = envelope * segmentAlpha * 0.20f)
                        } else {
                            teal.copy(alpha = envelope * segmentAlpha * 0.10f)
                        },
                        start = imagePoint(startX, sourceY + startWave),
                        end = imagePoint(endX, sourceY + endWave),
                        strokeWidth = max(0.9f, 1.15f * imageScale),
                    )
                }
            }
        }

        // A warm signal visibly travels from the water to the existing sky
        // rings. It is a finite journey, not a permanent decorative trail.
        val ascent = launchPhase(progress, start = 0.35f, end = 0.65f)
        if (ascent > 0f && ascent < 1f) {
            val pathStart = paw
            val pathControl1 = imagePoint(206f, 1160f)
            val pathControl2 = imagePoint(344f, 924f)
            val pathEnd = imagePoint(290f, 760f)
            val head = easeOutCubic(ascent)
            val tail = (head - 0.19f).coerceAtLeast(0f)
            val segments = 18
            repeat(segments) { index ->
                val localStart = tail + (head - tail) * (index / segments.toFloat())
                val localEnd = tail + (head - tail) * ((index + 1) / segments.toFloat())
                val segmentAlpha = ((index + 1) / segments.toFloat()) *
                    sin(PI.toFloat() * ascent).coerceAtLeast(0f)
                drawLine(
                    color = gold.copy(alpha = segmentAlpha * 0.58f),
                    start = cubicPoint(localStart, pathStart, pathControl1, pathControl2, pathEnd),
                    end = cubicPoint(localEnd, pathStart, pathControl1, pathControl2, pathEnd),
                    strokeWidth = max(1f, 1.45f * imageScale),
                )
            }
            val headPoint = cubicPoint(head, pathStart, pathControl1, pathControl2, pathEnd)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(goldSoft.copy(alpha = 0.82f), Color.Transparent),
                    center = headPoint,
                    radius = 13f * imageScale,
                ),
                center = headPoint,
                radius = 13f * imageScale,
            )
            drawCircle(
                color = moonlight.copy(alpha = 0.92f),
                center = headPoint,
                radius = max(1.5f, 2.1f * imageScale),
            )
        }

        // The signal closes the authored rings from one arrival point. This is
        // the single visual peak, not a collection of independently orbiting arcs.
        val ringCenter = imagePoint(420f, 623f)
        val ringRadii = floatArrayOf(154f, 194f, 236f)
        ringRadii.forEachIndexed { index, sourceRadius ->
            val ring = launchPhase(
                progress = progress,
                start = 0.53f + index * 0.025f,
                end = 0.84f + index * 0.018f,
            )
            if (ring > 0f && ring < 1f) {
                val radius = sourceRadius * imageScale
                val closure = easeOutCubic((ring / 0.68f).coerceIn(0f, 1f))
                val reveal = (ring / 0.14f).coerceIn(0f, 1f)
                val fade = 1f - launchPhase(ring, 0.80f, 1f)
                val alpha = reveal * fade * (0.82f - index * 0.12f)
                val arrivalAngle = 132f
                listOf(-1f, 1f).forEach { direction ->
                    val sweep = direction * 180f * closure
                    drawArc(
                        color = if (index == 1) {
                            goldSoft.copy(alpha = alpha)
                        } else {
                            teal.copy(alpha = alpha * 0.88f)
                        },
                        startAngle = arrivalAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(ringCenter.x - radius, ringCenter.y - radius),
                        size = Size(radius * 2f, radius * 2f),
                        style = Stroke(width = max(1.2f, (2.05f - index * 0.22f) * imageScale)),
                    )
                    val glintAngle = Math.toRadians((arrivalAngle + sweep).toDouble())
                    val glint = Offset(
                        x = ringCenter.x + cos(glintAngle).toFloat() * radius,
                        y = ringCenter.y + sin(glintAngle).toFloat() * radius,
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(goldSoft.copy(alpha = alpha * 0.72f), Color.Transparent),
                            center = glint,
                            radius = 8f * imageScale,
                        ),
                        center = glint,
                        radius = 8f * imageScale,
                    )
                    drawCircle(
                        color = moonlight.copy(alpha = alpha),
                        center = glint,
                        radius = max(1.2f, 1.8f * imageScale),
                    )
                }
            }
        }

        // Existing stars answer the passing signal without becoming a second
        // competing animation system.
        val stars = listOf(
            173f to 118f, 334f to 96f, 580f to 165f, 716f to 248f,
            104f to 354f, 354f to 274f, 474f to 315f, 698f to 444f,
            72f to 770f, 742f to 840f, 112f to 1030f, 734f to 1090f,
            104f to 1426f, 714f to 1450f, 186f to 1640f, 644f to 1705f,
        )
        stars.forEachIndexed { index, (sourceX, sourceY) ->
            val star = launchPhase(
                progress = progress,
                start = 0.31f + index * 0.006f,
                end = 0.82f + index * 0.003f,
            )
            if (star > 0f && star < 1f) {
                val pulse = sin(PI.toFloat() * star).coerceAtLeast(0f)
                val drift = sin((star * PI.toFloat() * 2f) + index) * 0.8f * imageScale
                val center = imagePoint(sourceX, sourceY) + Offset(drift, -drift * 0.35f)
                val ray = (3.8f + (index % 3) * 0.8f) * imageScale
                val alpha = pulse * (0.46f + (index % 4) * 0.045f)
                drawCircle(
                    color = goldSoft.copy(alpha = alpha),
                    radius = max(1.1f, 1.45f * imageScale),
                    center = center,
                )
                drawLine(
                    color = goldSoft.copy(alpha = alpha * 0.55f),
                    start = Offset(center.x - ray, center.y),
                    end = Offset(center.x + ray, center.y),
                    strokeWidth = max(0.9f, 1f * imageScale),
                )
                drawLine(
                    color = goldSoft.copy(alpha = alpha * 0.45f),
                    start = Offset(center.x, center.y - ray),
                    end = Offset(center.x, center.y + ray),
                    strokeWidth = max(0.9f, 1f * imageScale),
                )
            }
        }

        // The fox receives one restrained rim-light pass at the moment the sky
        // and water answer each other. The body itself never deforms.
        val rimLight = launchPhase(progress, start = 0.62f, end = 0.85f)
        if (rimLight > 0f && rimLight < 1f) {
            val alpha = sin(PI.toFloat() * rimLight).coerceAtLeast(0f)
            drawSourcePolyline(
                points = listOf(
                    326f to 830f, 298f to 870f, 272f to 940f, 249f to 1032f,
                    251f to 1136f, 260f to 1234f, 258f to 1316f,
                ),
                color = moonlight.copy(alpha = alpha * 0.30f),
                strokeWidth = max(1.1f, 1.55f * imageScale),
            )
            drawSourcePolyline(
                points = listOf(
                    465f to 912f, 423f to 832f, 391f to 744f, 402f to 620f,
                    441f to 514f, 523f to 474f, 611f to 544f, 654f to 662f,
                ),
                color = goldSoft.copy(alpha = alpha * 0.22f),
                strokeWidth = max(1f, 1.4f * imageScale),
            )
        }

        // Ring closure triggers one whole-scene resonance. The stars, fox edge,
        // and water answer together, then settle instead of launching more effects.
        val resonance = launchPhase(progress, start = 0.67f, end = 0.90f)
        if (resonance > 0f && resonance < 1f) {
            val envelope = sin(PI.toFloat() * resonance).coerceAtLeast(0f)
            val center = imagePoint(420f, 655f)
            val radius = (92f + 315f * smoothStep(resonance)) * imageScale
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        goldSoft.copy(alpha = envelope * 0.14f),
                        teal.copy(alpha = envelope * 0.11f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = radius,
                ),
                center = center,
                radius = radius,
            )

            // Preserve the release after ring closure as two restrained echoes,
            // not a complete geometric circle expanding across the artwork.
            val echoRadius = (225f + 72f * smoothStep(resonance)) * imageScale
            val echoTopLeft = Offset(center.x - echoRadius, center.y - echoRadius * 0.84f)
            val echoSize = Size(echoRadius * 2f, echoRadius * 1.68f)
            listOf(205f to goldSoft, 25f to teal).forEach { (startAngle, color) ->
                drawArc(
                    color = color.copy(alpha = envelope * 0.055f),
                    startAngle = startAngle,
                    sweepAngle = 108f,
                    useCenter = false,
                    topLeft = echoTopLeft,
                    size = echoSize,
                    style = Stroke(width = max(2.2f, 3.4f * imageScale)),
                )
                drawArc(
                    color = color.copy(alpha = envelope * 0.15f),
                    startAngle = startAngle,
                    sweepAngle = 108f,
                    useCenter = false,
                    topLeft = echoTopLeft,
                    size = echoSize,
                    style = Stroke(width = max(0.9f, 1.15f * imageScale)),
                )
            }
            listOf(0, 2, 5, 7, 9, 11, 13, 15).forEachIndexed { index, starIndex ->
                val source = stars[starIndex]
                val starCenter = imagePoint(source.first, source.second)
                val starAlpha = envelope * (0.24f + index % 3 * 0.045f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(goldSoft.copy(alpha = starAlpha), Color.Transparent),
                        center = starCenter,
                        radius = (7f + index % 2 * 2f) * imageScale,
                    ),
                    center = starCenter,
                    radius = (7f + index % 2 * 2f) * imageScale,
                )
            }

            val waterCenter = imagePoint(420f, 1484f)
            val waterRadius = (108f + 275f * smoothStep(resonance)) * imageScale
            drawOval(
                color = goldSoft.copy(alpha = envelope * 0.20f),
                topLeft = Offset(waterCenter.x - waterRadius, waterCenter.y - waterRadius * 0.15f),
                size = Size(waterRadius * 2f, waterRadius * 0.30f),
                style = Stroke(width = max(1f, 1.35f * imageScale)),
            )
        }

        // A restrained sea-glass wash hands the authored image to the real home
        // screen. It does not imitate or duplicate the Deep Sea Nebula.
        val handoff = launchPhase(progress, start = 0.88f, end = 0.995f)
        if (handoff > 0f && handoff < 1f) {
            val center = imagePoint(420f, 735f)
            val eased = smoothStep(handoff)
            val radius = (68f + 505f * eased) * imageScale
            val alpha = sin(PI.toFloat() * handoff).coerceAtLeast(0f) * 0.14f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        teal.copy(alpha = alpha),
                        teal.copy(alpha = alpha * 0.28f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
    }
}

private fun launchPhase(progress: Float, start: Float, end: Float): Float =
    ((progress - start) / (end - start)).coerceIn(0f, 1f)

private fun easeOutCubic(value: Float): Float {
    val inverse = 1f - value
    return 1f - inverse * inverse * inverse
}

private fun smoothStep(value: Float): Float {
    val clamped = value.coerceIn(0f, 1f)
    return clamped * clamped * (3f - 2f * clamped)
}

private const val SYSTEM_SPLASH_MIN_DURATION_MILLIS = 650L
private const val SYSTEM_SPLASH_EXIT_DURATION_MILLIS = 220L
private const val LAUNCH_MOTION_DURATION_NANOS = 2_980_000_000.0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoundistApp(application: SoundistApplication) {
    val services = application.services
    val activity = LocalActivity.current
    val audioEngine = remember { Media3AudioRuntime.get(application) }
    val listeningRepository = remember { RoomListeningRepository(services.sounds, services.offlineContent) }
    var pendingPlaybackServiceStart by remember { mutableStateOf(false) }
    var pendingReminderPermission by remember { mutableStateOf(false) }
    val playbackService = remember { PlaybackServiceController(application) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && pendingPlaybackServiceStart) playbackService.startForActivePlayback()
        if (granted && pendingReminderPermission) application.applicationScope.launch { retryPendingReminderAfterPermission(application) }
        pendingPlaybackServiceStart = false
        pendingReminderPermission = false
    }
    val preferencesStore = remember { AppPreferences(application) }
    var preferences by remember { mutableStateOf(preferencesStore.load()) }
    // 环境声后端（阶段 B）：feature flag 关闭走 Media3（默认，已验证）；置 true 走 miniaudio。
    // 提升到独立 remember，供 listeningAudio 与 productivityDependencies（睡眠）共用同一后端。
    val ambientMixer = remember {
        if (MiniaudioFeatureFlags.ambientEnabled) {
            MiniaudioAmbientMixer { uri -> loadAssetBytes(application.assets, uri) }
        } else {
            Media3AmbientMixer(audioEngine)
        }
    }
    val listeningAudio = remember {
        Media3ListeningAudioController(
            audioEngine,
            services.sounds,
            services.records,
            playbackService,
            { preferences.backgroundPlayback },
            { preferences.fadeSeconds },
            {
                pendingPlaybackServiceStart = true
                if (android.os.Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            ambientMixer,
        )
    }
    val generatedAudio = remember {
        // 生成电台后端：native 引擎（阶段 E）由 feature flag 选择，关闭时走 Kotlin 渲染器（安全回退）。
        val renderer: GeneratedPlaybackController =
            if (MiniaudioFeatureFlags.generativeNativeEnabled) FailoverGeneratedPlaybackController(application)
            else NativeGeneratedAudioRenderer(application)
        RecordedGeneratedAudioRenderer(
            renderer, services.records, audioEngine, playbackService,
            { preferences.backgroundPlayback }, { preferences.fadeSeconds },
        ) {
            pendingPlaybackServiceStart = true
            if (android.os.Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val customRadioStore = remember { CustomRadioStore(application, services.offlineContent) }
    val listeningVm: ListeningViewModel = viewModel(factory = remember { ListeningViewModelFactory(listeningRepository, listeningAudio, generatedAudio, customRadioStore) })
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, preferences.backgroundPlayback) {
        if (!preferences.backgroundPlayback) playbackService.stop()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !preferences.backgroundPlayback) {
                audioEngine.pause()
                listeningAudio.pauseAmbientForBackground() /* miniaudio 后端也要暂停 */
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        services.sounds.observePlayback().firstOrNull()?.let { snapshot ->
            // 冷启动恢复统一经 controller 的 AmbientMixer（不直接走 engine），
            // 避免启用 miniaudio 后仍走 Media3 或双引擎同时播放。
            val autoResumeAmbient = preferences.autoResume && snapshot.playing && snapshot.tracks.isNotEmpty()
            listeningAudio.restoreAmbient(snapshot.tracks, snapshot.masterVolume, autoResumeAmbient)
            if (autoResumeAmbient && preferences.backgroundPlayback) playbackService.startForActivePlayback()
            // 电台：恢复上次播放状态（频道、曲目索引由仓库加载；这里只在 autoResume 下继续播放，
            // 与前端「原生应用可通过后台媒体服务响应 autoResume」一致）。
            if (preferences.autoResume && snapshot.playing && snapshot.radioId != null) {
                listeningVm.loaded.first { it }
                listeningVm.restoreRadioPlayback()
                if (preferences.backgroundPlayback) playbackService.startForActivePlayback()
            }
        }
    }
    val focusTargetSource = remember { arrayOfNulls<(() -> FocusTarget)?>(1) }
    val notesVm: NotesViewModel = viewModel(factory = remember {
        NotesViewModelFactory(
            CoreNotesRepository(services.notes),
            AppPrivateNoteAssetStore(),
            AndroidNoteRecorder(application),
            contextProvider = NoteContextProvider {
                focusTargetSource[0]?.invoke()?.let { target ->
                    val ls = listeningVm.state.value
                    val soundNames = ls.sounds.filter { it.active }.map { it.name }
                    val radioName = if (ls.radioPlayback.isRadioActive) ls.stations.firstOrNull { it.id == ls.selectedStationId }?.name else null
                    NoteContextSnapshot(NoteContext(targetKind = target.kind.name.lowercase(), targetId = target.id, targetName = target.name, soundNames = soundNames, radioName = radioName))
                }
            },
        )
    })
    val reviewNoteWriter: ((FocusReviewNote) -> Unit)? = remember(notesVm, listeningVm) {
        { rn ->
            val ls = listeningVm.state.value
            val soundNames = ls.sounds.filter { it.active }.map { it.name }
            val radioName = if (ls.radioPlayback.isRadioActive) ls.stations.firstOrNull { it.id == ls.selectedStationId }?.name else null
            notesVm.addNote(
                title = "${rn.targetName} · 专注复盘",
                text = rn.text,
                notebookId = "nb4",
                tags = setOf("专注复盘"),
                context = NoteContext(targetKind = rn.targetKind.name.lowercase(), targetId = rn.targetId, targetName = rn.targetName, soundNames = soundNames, radioName = radioName),
            )
        }
    }
    val productivityDependencies = remember {
        createProductivityDependencies(
            application, services, audioEngine, { listeningAudio.currentAmbientMixer() },
            onNotificationPermissionRequired = {
                pendingReminderPermission = true
                if (android.os.Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            reviewNoteWriter = reviewNoteWriter,
            focusAudioSnapshot = {
                val listening = listeningVm.state.value
                val radioActive = listening.radioPlayback != PlaybackState.IDLE &&
                    listening.radioPlayback != PlaybackState.ERROR
                val station = listening.stations.firstOrNull { it.id == listening.selectedStationId }
                val generated = station?.takeIf { it.sourceKind == RadioSourceKind.GENERATED && radioActive }
                val ambientMode = generated?.let { selected ->
                    listening.channelAmbientSessions[selected.id]?.ambientMode
                        ?: selected.generatorArrangement?.ambientMode
                        ?: "preset"
                }
                val activeSounds = listening.sounds.filter { it.active && it.volume > 0f }
                val channelType = when {
                    generated != null -> "GENERATED_CHANNEL"
                    station?.custom == true || station?.sourceKind in setOf(RadioSourceKind.LOCAL, RadioSourceKind.STREAM) -> "MY_CHANNEL"
                    radioActive -> "OPEN_SELECTION"
                    else -> null
                }
                val source = when {
                    generated != null && ambientMode != "current" && activeSounds.isNotEmpty() -> "CHANNEL_RECIPE"
                    generated != null && activeSounds.isNotEmpty() -> "GENERATED_CHANNEL_WITH_USER_AMBIENT"
                    generated != null -> "GENERATED_CHANNEL"
                    channelType != null && activeSounds.isNotEmpty() -> "${channelType}_WITH_USER_AMBIENT"
                    activeSounds.isNotEmpty() -> "USER_AMBIENT"
                    channelType != null -> channelType
                    else -> "SILENT"
                }
                FocusAudioSnapshot(
                    sounds = activeSounds.map { FocusSoundSnapshot(it.id, it.volume) },
                    radioId = listening.selectedStationId.takeIf { radioActive },
                    ambientMode = when {
                        generated == null -> if (activeSounds.isNotEmpty()) "PERSONAL" else null
                        ambientMode == "current" -> "PERSONAL"
                        else -> "CHANNEL"
                    },
                    audioSource = source,
                )
            },
        )
    }
    focusTargetSource[0] = { productivityDependencies.repository.state.value.focus.target }
    val recordsVm: RecordsViewModel = viewModel(factory = remember {
        RecordsViewModelFactory(
            AggregatingRecordsRepository(
                CoreRecordsEventSource(
                    services.records,
                    soundNames = com.soundist.feature.listening.SoundCatalog.items.associate { it.id to it.name },
                    soundCategories = com.soundist.feature.listening.SoundCatalog.items.associate { it.id to it.category.label() },
                    // Records 页电台统计显示真实频道名（id → name 随频道增删改实时更新）。
                    channelMetadata = listeningVm.state
                        .map { state ->
                            state.stations.associate { station ->
                                station.id to ChannelRecordMetadata(station.name, station.genre)
                            }
                        }
                        .distinctUntilChanged(),
                    savedSleepRoutineCount = services.productivity.observeSleepRoutines().map { it.size },
                ),
            ),
        )
    })

    var pendingAttachment by remember { mutableStateOf<Pair<AttachmentPickerRequest, (Result<List<AttachmentSelection>>) -> Unit>?>(null) }
    val openDocuments = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val pending = pendingAttachment ?: return@rememberLauncherForActivityResult
        pendingAttachment = null
        pending.second(Result.success(uris.take(pending.first.maximumItems).map { uri ->
            AttachmentSelection(uri, application.displayName(uri), pending.first.type)
        }))
    }
    val attachmentPicker = remember {
        NoteAttachmentPicker { request, complete ->
            pendingAttachment = request to complete
            openDocuments.launch(request.mimeTypes.toTypedArray())
        }
    }

    var pendingArtwork by remember { mutableStateOf<CompletableDeferred<String?>?>(null) }
    val openArtwork = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val result = uri?.let { selected ->
            runCatching { application.contentResolver.takePersistableUriPermission(selected, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            selected.toString()
        }
        pendingArtwork?.complete(result)
        pendingArtwork = null
    }
    val artworkPicker = remember {
        StationArtworkPicker {
            val deferred = CompletableDeferred<String?>()
            pendingArtwork = deferred
            openArtwork.launch(arrayOf("image/*"))
            deferred.await()
        }
    }

    var pendingAudioPick by remember { mutableStateOf<CompletableDeferred<List<LocalAudioSelection>>?>(null) }
    val openStationAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val result = uris.mapNotNull { selected ->
            runCatching {
                application.contentResolver.takePersistableUriPermission(selected, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val type = application.contentResolver.getType(selected).orEmpty()
                val size = application.contentResolver.query(selected, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getLong(0) else 0L
                } ?: 0L
                val durationSeconds = runCatching {
                    val mmr = MediaMetadataRetriever()
                    try {
                        mmr.setDataSource(application, selected)
                        ((mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000L).toInt()
                    } finally { mmr.release() }
                }.getOrDefault(0)
                LocalAudioSelection(selected.toString(), application.displayName(selected), size, type, durationSeconds)
            }.getOrNull()
        }
        pendingAudioPick?.complete(result)
        pendingAudioPick = null
    }
    val stationAudioPicker = remember {
        StationAudioPicker {
            val deferred = CompletableDeferred<List<LocalAudioSelection>>()
            pendingAudioPick = deferred
            openStationAudio.launch(arrayOf("audio/*"))
            deferred.await()
        }
    }

    var recordAfterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val continuation = recordAfterPermission
        recordAfterPermission = null
        if (granted) continuation?.invoke() else notesVm.reportRecordingError("需要麦克风权限才能创建录音笔记")
    }

    var destination by remember { mutableStateOf(AppDestination.HOME) }
    var notesEditorActive by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var exportNotice by remember { mutableStateOf<ShellNotice?>(null) }
    var pendingExportJson by remember { mutableStateOf<String?>(null) }
    var pendingImportSnapshot by remember { mutableStateOf<DatabaseSnapshot?>(null) }
    val exportDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val payload = pendingExportJson
        pendingExportJson = null
        if (uri != null && payload != null) runCatching {
            val output = application.contentResolver.openOutputStream(uri, "w")
                ?: error("无法打开导出文件")
            output.bufferedWriter(Charsets.UTF_8).use { it.write(payload) }
        }.onSuccess { exportNotice = ShellNotice("已导出本地数据备份") }.onFailure { exportNotice = ShellNotice("导出失败：${it.message ?: "无法写入文件"}") }
    }
    val importDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) application.applicationScope.launch {
            val result = runCatching {
                val json = withContext(Dispatchers.IO) {
                    val resolver = application.contentResolver
                    resolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                        if (descriptor.length > 32L * 1024 * 1024) error("备份文件超过 32 MB")
                    }
                    val input = resolver.openInputStream(uri) ?: error("无法读取备份文件")
                    input.bufferedReader(Charsets.UTF_8).use { reader ->
                        val text = reader.readText()
                        if (text.length > 32 * 1024 * 1024) error("备份文件超过 32 MB")
                        text
                    }
                }
                decodeDatabaseSnapshot(json)
            }
            withContext(Dispatchers.Main) {
                result.onSuccess { pendingImportSnapshot = it }
                    .onFailure { exportNotice = ShellNotice("导入失败：${it.message ?: "文件无效或无法读取"}") }
            }
        }
    }
    val navItems = remember {
        listOf(
            SoundistNavItem(AppDestination.HOME.key, "主页", home),
            SoundistNavItem(AppDestination.SOUNDS.key, "声音", music2),
            SoundistNavItem(AppDestination.RADIO.key, "电台", radio),
            SoundistNavItem(AppDestination.FOCUS.key, "专注", timer),
            SoundistNavItem(AppDestination.RECORDS.key, "记录", barChart2),
        )
    }
    val listeningState by listeningVm.state.collectAsState()
    val ambientHeaderPlaying = listeningState.ambientPlaying
    val radioHeaderPlaying = listeningState.radioPlayback.isRadioActive
    val productivityState by productivityDependencies.repository.state.collectAsState()
    val focusTimerText = rememberGlobalFocusTimerText(productivityDependencies)
    val sleepActive = productivityState.sleep.status == SleepStatus.RUNNING
    var sleepNow by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sleepActive) {
        if (sleepActive) { while (true) { sleepNow = System.currentTimeMillis(); delay(1000) } }
        else sleepNow = System.currentTimeMillis()
    }
    val sleepRemainingMinutes = if (sleepActive) {
        val end = productivityState.sleep.endsAtEpochMillis
        if (end != null) (((end - sleepNow) + 59_999L) / 60_000L).toInt().coerceAtLeast(1) else 1
    } else 0

    SoundistTheme {
        CompositionLocalProvider(
            LocalProductivityDependencies provides productivityDependencies,
            LocalFocusQuickNoteWriter provides { value ->
                val selected = notesVm.state.value.selectedNotebookId
                val title = if (value.length > 22) value.take(22) + "…" else value
                notesVm.addNote(title = title, text = value, notebookId = selected)
            },
        ) {
            Box(
                Modifier.fillMaxSize().background(SoundistColors.Abyss),
                contentAlignment = Alignment.TopCenter,
            ) {
              Box(
                Modifier.widthIn(max = 390.dp).fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to SoundistColors.DeepSea,
                        .34f to SoundistColors.Abyss,
                        1f to SoundistColors.Abyss,
                    ),
                ),
            ) {
                Column(Modifier.fillMaxSize()) {
                    AppHeader(
                        onSettings = { showSettings = true },
                        onSleep = { showSleep = !showSleep },
                        ambientPlaying = ambientHeaderPlaying,
                        radioPlaying = radioHeaderPlaying,
                        reducedMotion = preferences.reducedMotion(application),
                        sleepActive = sleepActive,
                        sleepRemainingMinutes = sleepRemainingMinutes,
                        focusTimerText = focusTimerText,
                    )
                    HorizontalDivider(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        color = SoundistColors.Divider.copy(alpha = .7f),
                    )
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (destination) {
                            AppDestination.HOME -> ListeningRoute(ListeningDestination.HOME, injectedViewModel = listeningVm, reduceMotion = preferences.reducedMotion(application), onOpenSounds = { destination = AppDestination.SOUNDS })
                            AppDestination.SOUNDS -> ListeningRoute(ListeningDestination.SOUNDS, injectedViewModel = listeningVm, reduceMotion = preferences.reducedMotion(application))
                            AppDestination.RADIO -> ListeningRoute(ListeningDestination.RADIO, injectedViewModel = listeningVm, artworkPicker = artworkPicker, audioPicker = stationAudioPicker, reduceMotion = preferences.reducedMotion(application), systemAnimationScale = application.systemAnimationScale())
                            AppDestination.FOCUS -> ProductivityRoute(
                                notesEditorActive = notesEditorActive,
                                notesContent = {
                                    NotesRoute(
                                        vm = notesVm,
                                        attachmentPicker = attachmentPicker,
                                        onEditorStateChange = { notesEditorActive = it },
                                        confirmPermanentDeletes = preferences.confirmDestructive,
                                        onRecordPermissionRequest = { afterGrant ->
                                            recordAfterPermission = afterGrant
                                            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                                        },
                                    )
                                },
                            )
                            AppDestination.RECORDS -> RecordsRoute(vm = recordsVm)
                        }
                    }
                    SoundistBottomBar(navItems, destination.key) { key -> AppDestination.from(key)?.let { destination = it } }
                }
                ProductivitySleepHost(productivityDependencies, visible = showSleep, onDismiss = { showSleep = false })
                if (showSettings) SettingsSheet(
                    value = preferences,
                    onChange = {
                        preferences = it; preferencesStore.save(it)
                        if (it.haptics) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    },
                    onDismiss = { showSettings = false },
                    onExport = {
                        pendingExportJson = encodeDatabaseSnapshot(DatabaseMaintenance(application.database).snapshot())
                        exportDocument.launch("soundist-backup-${java.time.LocalDate.now()}.json")
                    },
                    onImport = { importDocument.launch(arrayOf("application/json", "text/*")) },
                )
                exportNotice?.let { notice -> AppNotice(notice, onDismiss = { exportNotice = null }) }
                pendingImportSnapshot?.let { snapshot ->
                    AlertDialog(
                        onDismissRequest = { pendingImportSnapshot = null },
                        title = { Text("导入本地数据？", color = SoundistColors.Text) },
                        text = { Text("导入将覆盖当前的全部声场、笔记、待办、习惯与睡眠数据，且无法撤销。", color = SoundistColors.TextSecondary) },
                        confirmButton = { TextButton({
                            application.applicationScope.launch {
                                val result = runCatching {
                                    // Stop live backends before replacing rows observed by the UI.
                                    listeningAudio.stopAll()
                                    generatedAudio.stop()
                                    DatabaseMaintenance(application.database).restore(snapshot)
                                }
                                withContext(Dispatchers.Main) {
                                    result.onSuccess { exportNotice = ShellNotice("已导入本地数据备份") }
                                        .onFailure { exportNotice = ShellNotice("导入失败：${it.message ?: "无法写入"}") }
                                }
                            }
                            pendingImportSnapshot = null
                        }) { Text("导入", color = SoundistColors.TealSoft) } },
                        dismissButton = { TextButton({ pendingImportSnapshot = null }) { Text("取消", color = SoundistColors.TextSecondary) } },
                        containerColor = SoundistColors.Raised,
                    )
                }
              }
            }
        }
    }
}

@Composable
private fun AppHeader(
    onSettings: () -> Unit,
    onSleep: () -> Unit,
    ambientPlaying: Boolean,
    radioPlaying: Boolean,
    reducedMotion: Boolean,
    sleepActive: Boolean,
    sleepRemainingMinutes: Int,
    focusTimerText: String?,
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(46.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Soundist",
            color = SoundistColors.Text,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            letterSpacing = .35.sp,
        )
        Text(" 声境", color = SoundistColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Normal)
        focusTimerText?.let { value ->
            Spacer(Modifier.width(10.dp))
            Text(value, color = SoundistColors.TealSoft, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.width(24.dp).height(44.dp), contentAlignment = Alignment.Center) {
            PlaybackIndicator(
                ambient = ambientPlaying,
                radio = radioPlaying,
                reducedMotion = reducedMotion,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        HeaderAction(settings2, "打开设置", onSettings)
        HeaderSleepButton(sleepActive, sleepRemainingMinutes, onSleep)
    }
}

@Composable
private fun HeaderSleepButton(sleepActive: Boolean, remainingMinutes: Int, click: () -> Unit) {
    IconButton(click, Modifier.size(44.dp)) {
        Box(
            Modifier.size(32.dp).clip(CircleShape)
                .background(if (sleepActive) SoundistColors.Teal.copy(alpha = .18f) else SoundistColors.Raised)
                .border(1.dp, if (sleepActive) SoundistColors.Teal.copy(alpha = .35f) else SoundistColors.Divider, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(moon, "睡眠定时", Modifier.size(14.dp), tint = if (sleepActive) SoundistColors.Teal else SoundistColors.TextMuted)
            if (sleepActive) {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp).height(16.dp).widthIn(min = 16.dp)
                        .clip(CircleShape).background(SoundistColors.Teal).padding(horizontal = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$remainingMinutes", color = SoundistColors.Abyss, fontSize = 8.sp, fontWeight = FontWeight.Bold, style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
                }
            }
        }
    }
}

@Composable
private fun HeaderAction(icon: ImageVector, label: String, click: () -> Unit) {
    IconButton(click, Modifier.size(44.dp)) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(SoundistColors.Raised)
                .border(1.dp, SoundistColors.Divider, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, Modifier.size(14.dp), tint = SoundistColors.TextMuted) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(value: AppPreferenceState, onChange: (AppPreferenceState) -> Unit, onDismiss: () -> Unit, onExport: suspend () -> Unit, onImport: () -> Unit) {
    val scope = rememberCoroutineScope()
    val maxH = LocalConfiguration.current.screenHeightDp.dp * 0.88f
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.fillMaxSize().clickable(onClick = onDismiss, indication = null, interactionSource = remember { MutableInteractionSource() }))
        Column(
            // Frontend `shadow-2xl` (0 25px 50px -12px rgba(0,0,0,0.25)); Compose elevation is the closest platform equivalent.
            Modifier.fillMaxWidth().widthIn(max = 390.dp).heightIn(max = maxH)
                .shadow(25.dp, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), clip = false, ambientColor = Color(0x40000000), spotColor = Color(0x40000000))
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(SoundistColors.Raised)
                .border(1.dp, SoundistColors.Divider, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("设置", color = SoundistColors.Text, fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
                    Text("播放、动效与本地数据", Modifier.padding(top = 2.dp), color = SoundistColors.TextMuted, fontSize = 11.sp, lineHeight = 16.5.sp)
                }
                Box(Modifier.size(44.dp).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) { Icon(x, "关闭设置", Modifier.size(16.dp), tint = SoundistColors.TextMuted) }
            }
            HorizontalDivider(color = SoundistColors.Divider)
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 28.dp)) {
                SettingsSectionLabel("播放", top = 16)
                PreferenceRow("后台继续播放", "切换页面或锁屏后保持声场", value.backgroundPlayback) { onChange(value.copy(backgroundPlayback = it)) }
                PreferenceRow("恢复上次播放状态", "浏览器允许自动播放时恢复；否则保留组合并等待点击播放", value.autoResume) { onChange(value.copy(autoResume = it)) }
                SettingSelect(
                    "音量淡入淡出",
                    if (value.fadeSeconds == 0) "关闭" else "${value.fadeSeconds} 秒",
                    listOf(0 to "关闭", 1 to "1 秒", 2 to "2 秒", 4 to "4 秒"),
                ) { onChange(value.copy(fadeSeconds = it)) }
                HorizontalDivider(color = SoundistColors.Divider)
                SettingsSectionLabel("视觉与反馈", top = 16)
                SettingSelect(
                    "动态效果",
                    when (value.motion) { MotionPreference.SYSTEM -> "跟随系统"; MotionPreference.REDUCED -> "减少动态效果"; MotionPreference.FULL -> "完整动态效果" },
                    listOf(MotionPreference.SYSTEM to "跟随系统", MotionPreference.REDUCED to "减少动态效果", MotionPreference.FULL to "完整动态效果"),
                ) { onChange(value.copy(motion = it)) }
                SettingsValueRow("触感反馈", if (value.haptics) "开启" else "关闭", topDivider = true) { onChange(value.copy(haptics = !value.haptics)) }
                HorizontalDivider(color = SoundistColors.Divider)
                SettingsSectionLabel("数据与安全", top = 16)
                SettingsValueRow("永久删除前确认", if (value.confirmDestructive) "开启" else "关闭") { onChange(value.copy(confirmDestructive = !value.confirmDestructive)) }
                Box(
                    Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 44.dp)
                        .border(1.dp, SoundistColors.Divider, RoundedCornerShape(8.dp))
                        .clickable { scope.launch { onExport() } },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(save, null, Modifier.size(16.dp), tint = SoundistColors.TextSecondary)
                        Text("导出本地数据", color = SoundistColors.TextSecondary, fontSize = 12.sp)
                    }
                }
                Box(
                    Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 44.dp)
                        .border(1.dp, SoundistColors.Divider, RoundedCornerShape(8.dp))
                        .clickable { onImport() },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(archiveRestore, null, Modifier.size(16.dp), tint = SoundistColors.TextSecondary)
                        Text("导入本地数据", color = SoundistColors.TextSecondary, fontSize = 12.sp)
                    }
                }
                Text("备份包含声场、笔记、待办、习惯、专注与睡眠等数据；不含附件图片、录音、手写和本地音频文件。", Modifier.padding(top = 8.dp), color = SoundistColors.TextMuted, fontSize = 10.sp, lineHeight = 15.sp)
            }
        }
    }
}


@Composable
private fun PreferenceRow(title: String, detail: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { change(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = SoundistColors.Text, fontSize = 14.sp, lineHeight = 21.sp)
            Text(detail, Modifier.padding(top = 0.5.dp), color = SoundistColors.TextMuted, fontSize = 11.sp, lineHeight = 16.5.sp)
        }
        ExplicitToggle(checked)
    }
    HorizontalDivider(color = SoundistColors.Divider.copy(alpha = .7f))
}

@Composable
private fun SettingsSectionLabel(label: String, top: Int) {
    Text(
        label,
        Modifier.padding(top = top.dp, bottom = 8.dp),
        color = SoundistColors.TextMuted,
        fontSize = 11.sp,
        letterSpacing = 1.54.sp,
    )
}

@Composable
private fun ExplicitToggle(checked: Boolean) {
    Box(
        Modifier.size(width = 44.dp, height = 24.dp)
            .clip(CircleShape)
            .background(if (checked) SoundistColors.Teal.copy(alpha = .25f) else SoundistColors.SurfaceLow)
            .border(1.dp, if (checked) SoundistColors.Teal.copy(alpha = .45f) else SoundistColors.DividerStrong, CircleShape),
    ) {
        Box(
            Modifier.offset(x = if (checked) 20.dp else 2.dp, y = 2.dp).size(18.dp).clip(CircleShape)
                .background(if (checked) SoundistColors.TealSoft else SoundistColors.TextMuted),
        )
    }
}

@Composable
private fun SettingsValueRow(label: String, value: String, topDivider: Boolean = false, onClick: () -> Unit) {
    // Frontend 触感反馈 row is `border-y border-[var(--border)]/70` (divider above AND below); other rows are border-b only.
    if (topDivider) HorizontalDivider(color = SoundistColors.Divider.copy(alpha = .7f))
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), color = SoundistColors.Text, fontSize = 14.sp)
        Text(value, color = SoundistColors.TealSoft, fontSize = 12.sp)
    }
    HorizontalDivider(color = SoundistColors.Divider.copy(alpha = .7f))
}

@Composable
private fun <T> SettingSelect(label: String, value: String, options: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    Text(label, Modifier.padding(top = 12.dp, bottom = 4.dp), color = SoundistColors.TextSecondary, fontSize = 11.sp)
    val selectedKey = options.firstOrNull { it.second == value }?.first ?: options.first().first
    SoundistSelect(
        value = selectedKey,
        options = options,
        onSelect = onSelect,
        modifier = Modifier.fillMaxWidth(),
        minHeight = 44.dp,
        background = SoundistColors.RaisedStrong,
        fontSize = 14.sp,
    )
}

private data class ShellNotice(val message: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

@Composable
private fun AppNotice(notice: ShellNotice, onDismiss: () -> Unit) {
    // Frontend auto-dismisses after 5200 ms with an action, 2800 ms without (App.tsx `showNotice`).
    LaunchedEffect(notice) {
        delay(if (notice.action != null) 5200L else 2800L)
        onDismiss()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Row(
            // Frontend `boxShadow: 0 8px 24px rgba(0,0,0,0.28)`.
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 94.dp).heightIn(min = 48.dp)
                .shadow(24.dp, RoundedCornerShape(12.dp), clip = false, ambientColor = Color(0x47000000), spotColor = Color(0x47000000))
                .clip(RoundedCornerShape(12.dp)).background(SoundistColors.RaisedStrong)
                .border(1.dp, SoundistColors.Teal.copy(alpha = .2f), RoundedCornerShape(12.dp))
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(notice.message, Modifier.weight(1f), color = SoundistColors.Text, fontSize = 12.sp, lineHeight = 18.sp)
            if (notice.action != null && notice.actionLabel != null) {
                Box(
                    Modifier.heightIn(min = 44.dp).padding(horizontal = 12.dp).clip(RoundedCornerShape(8.dp))
                        .border(1.dp, SoundistColors.Teal.copy(alpha = .25f), RoundedCornerShape(8.dp))
                        .clickable { notice.action?.invoke(); onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(notice.actionLabel!!, color = SoundistColors.TealSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            IconButton(onDismiss, Modifier.size(44.dp)) { Icon(x, "关闭提示", Modifier.size(16.dp), tint = SoundistColors.TextMuted) }
        }
    }
}

private enum class AppDestination(val key: String) {
    HOME("home"), SOUNDS("sounds"), RADIO("radio"), FOCUS("focus"), RECORDS("records");
    companion object { fun from(key: String) = entries.firstOrNull { it.key == key } }
}

private fun SoundistApplication.displayName(uri: Uri): String {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) return cursor.getString(0).orEmpty().ifBlank { "附件" }
    }
    return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "附件" } ?: "附件"
}

private fun AppPreferenceState.reducedMotion(context: Context): Boolean = when (motion) {
    MotionPreference.REDUCED -> true
    MotionPreference.FULL -> false
    MotionPreference.SYSTEM -> runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
}

private fun Context.systemAnimationScale(): Float = runCatching {
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f).coerceAtLeast(0f)
}.getOrDefault(1f)

private fun SoundistApplication.clearPrivateNoteMedia() {
    listOf("note-attachments", "note-drawings", "note-recordings").forEach { child ->
        filesDir.resolve(child).takeIf { it.exists() && it.isDirectory }?.deleteRecursively()
    }
}
