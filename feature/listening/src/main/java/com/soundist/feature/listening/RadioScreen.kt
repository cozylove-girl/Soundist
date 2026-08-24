package com.soundist.feature.listening

import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.DialogProperties
import com.soundist.core.designsystem.SoundistColors
import com.soundist.core.designsystem.SoundSlider
import com.soundist.core.designsystem.SoundistSelect
import com.soundist.core.designsystem.pause
import com.soundist.core.designsystem.radio
import com.soundist.core.designsystem.play
import com.soundist.core.designsystem.activity
import com.soundist.core.designsystem.audioLines
import com.soundist.core.designsystem.chevronDown
import com.soundist.core.designsystem.chevronUp
import com.soundist.core.designsystem.disc3
import com.soundist.core.designsystem.gripVertical
import com.soundist.core.designsystem.headphones
import com.soundist.core.designsystem.listMusic
import com.soundist.core.designsystem.moreHorizontal
import com.soundist.core.designsystem.music2
import com.soundist.core.designsystem.pencilLine
import com.soundist.core.designsystem.plus
import com.soundist.core.designsystem.redo2
import com.soundist.core.designsystem.save
import com.soundist.core.designsystem.share2
import com.soundist.core.designsystem.folderInput
import com.soundist.core.designsystem.search
import com.soundist.core.designsystem.slidersHorizontal
import com.soundist.core.designsystem.sparkles
import com.soundist.core.designsystem.trash2
import com.soundist.core.designsystem.undo2
import com.soundist.core.designsystem.upload
import com.soundist.core.designsystem.x
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val RadioLight = Color(0xFFE3BC8D)
private val ErrorText = Color(0xFFE09A9C)

/** Tailwind `border-t` — top edge only. */
private fun Modifier.borderTop(width: Dp, color: Color): Modifier = drawBehind {
    val w = width.toPx()
    drawRect(color, topLeft = Offset(0f, 0f), size = Size(size.width, w))
}
/** Tailwind `border-b` — bottom edge only. */
private fun Modifier.borderBottom(width: Dp, color: Color): Modifier = drawBehind {
    val w = width.toPx()
    drawRect(color, topLeft = Offset(0f, size.height - w), size = Size(size.width, w))
}
/** Tailwind `border-y` — top + bottom edges only. */
private fun Modifier.borderY(width: Dp, color: Color): Modifier = drawBehind {
    val w = width.toPx()
    drawRect(color, topLeft = Offset(0f, 0f), size = Size(size.width, w))
    drawRect(color, topLeft = Offset(0f, size.height - w), size = Size(size.width, w))
}

/** App.tsx sourceLabel (5686–5692). */
private fun sourceLabel(station: RadioStation): String = when (station.sourceKind) {
    RadioSourceKind.GENERATED -> "生成声场"
    RadioSourceKind.OFFICIAL -> "开放音乐精选"
    RadioSourceKind.LOCAL -> "本地音频"
    RadioSourceKind.STREAM -> "直接音频流"
}

/** App.tsx sectionTitle (5693). */
private fun sectionTitle(group: RadioGroup): String = when (group) {
    RadioGroup.GENERATED -> "持续变化的声音"
    RadioGroup.OFFICIAL -> "人工筛选的开放录音"
    RadioGroup.CUSTOM -> "你的频道"
}

/** App.tsx radioStatusLabel (5702–5709). */
internal fun radioStatusLabel(state: PlaybackState): String = when (state) {
    PlaybackState.IDLE -> "未播放"
    PlaybackState.LOADING -> "载入中"
    PlaybackState.PLAYING -> "正在播放"
    PlaybackState.AUDIBLE -> "正在播放"
    PlaybackState.PAUSED -> "已暂停"
    PlaybackState.ERROR -> "播放失败"
}

/** 正在播放卡片按钮的图标种类（供 UI 与测试共用）。 */
internal enum class RadioButtonKind { PLAY, PAUSE, RETRY, PROGRESS }

/** 正在播放卡片按钮规格：五种播放状态 → 图标种类 / 无障碍文案（单一权威来源，禁止各自猜状态）。 */
internal data class RadioButtonSpec(val kind: RadioButtonKind, val contentDescription: String)

internal fun radioPlaybackButtonSpec(playback: PlaybackState): RadioButtonSpec? = when (playback) {
    PlaybackState.LOADING -> RadioButtonSpec(RadioButtonKind.PROGRESS, "正在载入电台")
    PlaybackState.PLAYING, PlaybackState.AUDIBLE -> RadioButtonSpec(RadioButtonKind.PAUSE, "暂停电台")
    PlaybackState.PAUSED -> RadioButtonSpec(RadioButtonKind.PLAY, "继续播放电台")
    PlaybackState.ERROR -> RadioButtonSpec(RadioButtonKind.RETRY, "重试播放电台")
    PlaybackState.IDLE -> null
}

/** App.tsx now-playing 卡片右侧播放/暂停/载入/重试按钮。固定 44×44dp、图标 16–18dp，切换状态不位移。 */
@Composable
private fun RadioPlaybackButton(playback: PlaybackState, onToggle: () -> Unit) {
    val spec = radioPlaybackButtonSpec(playback)
    Box(
        Modifier.size(44.dp).background(SoundistColors.Warm, RoundedCornerShape(50))
            .border(1.dp, SoundistColors.Warm.copy(alpha = 0.35f), RoundedCornerShape(50))
            .semantics { spec?.let { contentDescription = it.contentDescription } }
            .clickable { onToggle() },
        contentAlignment = Alignment.Center,
    ) {
        when (spec?.kind) {
            RadioButtonKind.PROGRESS -> CircularProgressIndicator(Modifier.size(18.dp), color = SoundistColors.Abyss, strokeWidth = 2.dp)
            RadioButtonKind.PAUSE -> Icon(pause, spec.contentDescription, Modifier.size(16.dp), tint = SoundistColors.Abyss)
            RadioButtonKind.PLAY -> Icon(play, spec.contentDescription, Modifier.size(16.dp), tint = SoundistColors.Abyss)
            RadioButtonKind.RETRY -> Icon(redo2, spec.contentDescription, Modifier.size(16.dp), tint = SoundistColors.Abyss)
            null -> Unit
        }
    }
}

/** App.tsx RadioSourceMark (990–996). */
@Composable
private fun RadioSourceMark(sourceKind: RadioSourceKind, active: Boolean = false) {
    val size = if (active) 24.dp else 20.dp
    val icon = when (sourceKind) {
        RadioSourceKind.LOCAL -> music2
        RadioSourceKind.STREAM -> radio
        RadioSourceKind.GENERATED -> sparkles
        RadioSourceKind.OFFICIAL -> disc3
    }
    val tint = when (sourceKind) {
        RadioSourceKind.LOCAL, RadioSourceKind.GENERATED -> SoundistColors.TealSoft
        else -> SoundistColors.Warm
    }
    Icon(icon, null, Modifier.size(size), tint = tint)
}

/** App.tsx RadioArtwork (998–1003)。本地 content:// / file:// 封面解码；https 需网络库，未集成时退回来源图标。 */
@Composable
private fun RadioArtwork(station: RadioStation, active: Boolean = false) {
    LocalArtworkOrMark(station.artworkUri, station.sourceKind, active, "${station.name}封面")
}

/** 本地封面（content:// / file://）显示图片，否则退回来源图标。自定义表单与列表共用，保证按最新 artworkUri 即时刷新。 */
@Composable
private fun LocalArtworkOrMark(uri: String?, sourceKind: RadioSourceKind, active: Boolean = false, contentDescription: String = "封面") {
    val bitmap = uri?.let { rememberLocalArtwork(it) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { RadioSourceMark(sourceKind, active) }
    }
}

/**
 * 按 uri 键解码本地封面位图；uri 变化（用户重新选图/保存新封面）时立即重新解码。
 * 返回 null 表示网络地址或解码失败，由调用方退回来源图标。
 */
@Composable
private fun rememberLocalArtwork(uri: String): Bitmap? {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) { decodeLocalArtwork(context, uri) }
    }
    return bitmap
}

/** 解码本地封面：先读边界再按 inSampleSize 采样，避免大尺寸图库照片直接解码 OOM 导致封面显示失败。 */
private fun decodeLocalArtwork(context: android.content.Context, uri: String): Bitmap? = runCatching {
    val open: () -> java.io.InputStream? = {
        if (uri.startsWith("content://")) context.contentResolver.openInputStream(Uri.parse(uri))
        else runCatching { java.io.FileInputStream(uri.removePrefix("file://")) }.getOrNull()
    }
    val bounds = open()?.use { stream ->
        BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeStream(stream, null, it) }
    } ?: return@runCatching null
    val sample = artworkSampleSize(bounds.outWidth, bounds.outHeight, 1024)
    open()?.use { stream -> BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
}.getOrNull()

private fun artworkSampleSize(width: Int, height: Int, maxSize: Int): Int {
    var sample = 1
    var w = width
    var h = height
    while (w > maxSize || h > maxSize) { w /= 2; h /= 2; sample *= 2 }
    return sample
}

/** App.tsx now-playing wave bars (5758–5767). CSS waveBar keyframes: height 18%↔82%, ease-in-out, infinite alternate, per-bar duration 0.7+(i%5)*0.18s and delay i*0.06s. */
@Composable
private fun RadioWaveBars(active: Boolean) {
    Row(
        Modifier.fillMaxWidth().height(20.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(28) { i -> RadioWaveBar(active, i) }
    }
}

@Composable
private fun RowScope.RadioWaveBar(active: Boolean, i: Int) {
    val transition = rememberInfiniteTransition(label = "waveBar$i")
    val fraction by transition.animateFloat(
        initialValue = 0.18f, targetValue = 0.82f,
        animationSpec = infiniteRepeatable(
            animation = tween(700 + (i % 5) * 180, easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(i * 60),
        ),
        label = "waveBar$i",
    )
    val height = if (active) fraction else 0.18f
    Box(
        Modifier.weight(1f).fillMaxHeight(height.coerceIn(0.02f, 1f))
            .background(SoundistColors.Warm.copy(alpha = if (active) 0.35f else 0.35f * 0.45f), RoundedCornerShape(2.dp)),
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun RadioScreen(state: ListeningState, dispatch: (ListeningAction) -> Unit, modifier: Modifier = Modifier, artworkPicker: StationArtworkPicker? = null, audioPicker: StationAudioPicker? = null, reduceMotion: Boolean = false) {
    val current = state.stations.firstOrNull { it.id == state.selectedStationId }
    // App.tsx 2696–2701 currentRadioTracks/currentLocalFiles/currentRadioPlaylistLength/normalizedRadioTrackIndex.
    val currentTracks = current?.tracks.orEmpty()
    val currentLocalFiles = current?.localAudio.orEmpty()
    val playlistLength = if (currentTracks.isNotEmpty()) currentTracks.size else currentLocalFiles.size
    val normalizedTrackIndex = if (playlistLength > 0) state.radioTrackIndex % playlistLength else 0
    val currentTrack = currentTracks.getOrNull(normalizedTrackIndex)
    val currentLocalFile = currentLocalFiles.getOrNull(normalizedTrackIndex)
    val sourceStations = state.sourceStations()
    val personalStations = state.stations.filter { it.custom }
    // Purpose chips stay on one horizontally scrollable row. This avoids a lone trailing chip
    // while preserving the full filter set and 44-ish dp touch targets.
    val availableGroups = listOf("全部", "古典", "器乐", "节拍", "氛围", "人声").filter { group -> group == "全部" || sourceStations.any { it.catalogGroup == group } }
    val availablePurposes = listOf("全部用途") + sourceStations.flatMap { it.purposes }.distinct()
    val visibleStations = state.visibleStations()
    // App.tsx radioPlaying ≈ non-idle; radioPlaybackActive ≈ actually playing.
    val radioPlaying = state.radioPlayback != PlaybackState.IDLE
    val radioPlaybackActive = state.radioPlayback.isRadioActive

    Column(modifier.fillMaxSize().background(SoundistColors.Abyss)) {
        // Now playing (App.tsx 5723–5771)
        if (current != null && radioPlaying) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)
                    .background(
                        Brush.linearGradient(listOf(SoundistColors.Warm.copy(alpha = 0.075f), Color(0xFA101A1B))),
                        RoundedCornerShape(12.dp),
                    )
                    .border(1.dp, SoundistColors.Warm.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(16.dp),
            ) {
                Text(radioStatusLabel(state.radioPlayback), color = SoundistColors.Warm.copy(alpha = 0.8f), fontSize = 11.sp, letterSpacing = 1.98.sp, modifier = Modifier.padding(bottom = 12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(56.dp).background(SoundistColors.Warm.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                            .border(1.dp, SoundistColors.Warm.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) { RadioArtwork(current, active = true) }
                    Column(Modifier.weight(1f)) {
                        Text(current.name, color = SoundistColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (currentTrack != null) "${currentTrack.title} · ${currentTrack.artist}" else (currentLocalFile?.displayName ?: current.description),
                            color = SoundistColors.TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(current.genre, color = SoundistColors.Warm.copy(alpha = 0.85f), fontSize = 11.sp, modifier = Modifier.background(SoundistColors.Warm.copy(alpha = 0.1f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp))
                            Text(
                                (if (playlistLength > 0) "${normalizedTrackIndex + 1} / ${playlistLength}" else sourceLabel(current)) + if (current.durationLabel.isNotBlank()) " · ${current.durationLabel}" else "",
                                color = SoundistColors.TextSecondary, fontSize = 11.sp,
                            )
                        }
                    }
                    RadioPlaybackButton(state.radioPlayback) { dispatch(ListeningAction.ToggleRadio) }
                }
                Spacer(Modifier.height(12.dp))
                RadioWaveBars(radioPlaybackActive)
            }
        }

        // Playback error banner (App.tsx 5773–5779)
        if (state.radioPlayback == PlaybackState.ERROR) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp).heightIn(min = 44.dp)
                    .background(Color(0xFFD57478).copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFD57478).copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(activity, null, Modifier.size(16.dp), tint = ErrorText)
                Text(state.operationError ?: "播放失败", color = ErrorText, fontSize = 11.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
                Box(Modifier.size(36.dp).clickable { dispatch(ListeningAction.ClearError) }, contentAlignment = Alignment.Center) { Icon(x, "关闭播放错误", Modifier.size(14.dp), tint = ErrorText) }
            }
        }

        // Source tabs (App.tsx 5781–5788)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp).heightIn(min = 44.dp)
                .background(SoundistColors.DeepSea, RoundedCornerShape(8.dp))
                .border(1.dp, SoundistColors.Divider, RoundedCornerShape(8.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(RadioGroup.GENERATED, RadioGroup.OFFICIAL, RadioGroup.CUSTOM).forEach { group ->
                val label = when (group) { RadioGroup.GENERATED -> "持续声场"; RadioGroup.OFFICIAL -> "开放精选"; RadioGroup.CUSTOM -> "我的频道" }
                Box(
                    Modifier.weight(1f).heightIn(min = 40.dp)
                        .background(if (state.radioGroup == group) SoundistColors.RaisedStrong else Color.Transparent, RoundedCornerShape(6.dp))
                        .clickable { dispatch(ListeningAction.SetRadioGroup(group)); dispatch(ListeningAction.SetRadioGenre("全部")); dispatch(ListeningAction.SetRadioPurpose("全部用途")) },
                    contentAlignment = Alignment.Center,
                ) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (state.radioGroup == group) SoundistColors.Text else SoundistColors.TextMuted) }
            }
        }

        // Search (App.tsx 5790–5802)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp).heightIn(min = 40.dp)
                .background(SoundistColors.Raised, RoundedCornerShape(8.dp))
                .border(1.dp, SoundistColors.Divider, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(search, null, Modifier.size(14.dp), tint = SoundistColors.TextMuted)
            BasicTextField(
                state.radioQuery, { dispatch(ListeningAction.SetRadioQuery(it)) }, Modifier.weight(1f), singleLine = true,
                textStyle = TextStyle(color = SoundistColors.Text, fontSize = 12.sp),
                decorationBox = { inner -> if (state.radioQuery.isBlank()) Text("搜索频道、作品或乐器", color = SoundistColors.TextMuted, fontSize = 12.sp); inner() },
            )
            if (state.radioQuery.isNotEmpty()) {
                Box(Modifier.size(32.dp).clickable { dispatch(ListeningAction.SetRadioQuery("")) }, contentAlignment = Alignment.Center) { Icon(x, "清除搜索", Modifier.size(14.dp), tint = SoundistColors.TextMuted) }
            }
        }

        // Genre filter (App.tsx 5804–5816)
        if (availableGroups.size > 1 && state.radioGroup != RadioGroup.GENERATED) {
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                availableGroups.forEach { group ->
                    val active = state.radioGenre == group
                    Box(
                        Modifier.heightIn(min = 32.dp).background(if (active) SoundistColors.Teal.copy(alpha = 0.1f) else Color.Transparent, RoundedCornerShape(50))
                            .border(1.dp, if (active) SoundistColors.Teal.copy(alpha = 0.45f) else SoundistColors.Divider, RoundedCornerShape(50))
                            .clickable { dispatch(ListeningAction.SetRadioGenre(group)) }.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(group, fontSize = 10.sp, color = if (active) SoundistColors.TealSoft else SoundistColors.TextMuted) }
                }
            }
        }

        // Purpose filter (App.tsx 5818–5831)
        if (availablePurposes.size > 1 && state.radioGroup != RadioGroup.CUSTOM) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                availablePurposes.forEach { purpose ->
                    val active = state.radioPurpose == purpose
                    Box(
                        Modifier.heightIn(min = 34.dp).background(if (active) SoundistColors.Warm.copy(alpha = 0.08f) else Color.Transparent, RoundedCornerShape(6.dp))
                            .border(1.dp, if (active) SoundistColors.Warm.copy(alpha = 0.45f) else SoundistColors.Divider, RoundedCornerShape(6.dp))
                            .clickable { dispatch(ListeningAction.SetRadioPurpose(purpose)) }.padding(horizontal = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(purpose, fontSize = 10.sp, color = if (active) RadioLight else SoundistColors.TextMuted) }
                }
            }
        }

        // Generated editor entry (App.tsx 5833–5841)
        if (state.radioGroup == RadioGroup.GENERATED) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp).heightIn(min = 44.dp)
                    .background(SoundistColors.Teal.copy(alpha = 0.045f), RoundedCornerShape(8.dp))
                    .border(1.dp, SoundistColors.Teal.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                    .clickable {
                        val target = current?.takeIf { it.sourceKind == RadioSourceKind.GENERATED } ?: sourceStations.firstOrNull()
                        target?.let { dispatch(ListeningAction.OpenGeneratorEditor(it)) }
                    }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(slidersHorizontal, null, Modifier.size(16.dp), tint = SoundistColors.TealSoft)
                    Text("声场编排器", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = SoundistColors.Text)
                }
                Text("配方 + 高级编排", fontSize = 10.sp, color = SoundistColors.TextMuted)
            }
        }

        // Mine action buttons (App.tsx 5843–5859)
        if (state.radioGroup == RadioGroup.CUSTOM) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MineActionButton(upload, "本地音频", { dispatch(ListeningAction.OpenCustomRadio(RadioSourceKind.LOCAL)) }, Modifier.weight(1f))
                MineActionButton(radio, "音频流", { dispatch(ListeningAction.OpenCustomRadio(RadioSourceKind.STREAM)) }, Modifier.weight(1f))
                MineActionButton(listMusic, "管理", { dispatch(ListeningAction.OpenManageChannels) }, Modifier.weight(1f))
            }
        }

        // Section title (App.tsx 6216)
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(sectionTitle(state.radioGroup), fontSize = 11.sp, letterSpacing = 1.76.sp, color = SoundistColors.TextMuted)
            Text("${visibleStations.size} 个", fontSize = 10.sp, color = SoundistColors.TextMuted)
        }

        // Station list (App.tsx 6217–6251)
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(visibleStations, key = { it.id }) { station -> RadioStationRow(station, state, dispatch, reduceMotion) }
            if (visibleStations.isEmpty()) item { RadioEmptyState(state) }
        }
    }

    state.stationDetailsId?.let { id -> state.stations.firstOrNull { it.id == id }?.let { StationDetails(it, state, dispatch) } }
    if (state.generatorControlsOpen) GeneratorEditor(state, dispatch)
    if (state.generatorDiscardConfirmOpen) GeneratorDiscardConfirm(dispatch)
    if (state.customRadioOpen) CustomRadioForm(state, dispatch, artworkPicker, audioPicker)
    if (state.manageChannelsOpen) ManageChannels(state, dispatch)
    if (state.deleteStationConfirmId != null) DeleteStationConfirm(state, dispatch)
    state.notice?.let { notice -> NoticeOverlay(notice) { dispatch(ListeningAction.ClearNotice) } }
}

@Composable
private fun MineActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.heightIn(min = 44.dp).background(SoundistColors.Raised, RoundedCornerShape(8.dp))
            .border(1.dp, SoundistColors.Divider, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(14.dp), tint = SoundistColors.TextSecondary)
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SoundistColors.TextSecondary)
    }
}

@Composable
private fun RadioEmptyState(state: ListeningState) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(music2, null, Modifier.size(24.dp), tint = SoundistColors.TextMuted)
        Text(
            if (state.radioQuery.isNotBlank() || state.radioGenre != "全部" || state.radioPurpose != "全部用途") "没有匹配的频道"
            else when (state.radioGroup) { RadioGroup.GENERATED -> "还没有持续声场"; RadioGroup.OFFICIAL -> "还没有开放精选"; RadioGroup.CUSTOM -> "还没有我的频道" },
            color = SoundistColors.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            if (state.radioQuery.isNotBlank() || state.radioGenre != "全部" || state.radioPurpose != "全部用途") "尝试清除搜索或切换类型、用途。"
            else when (state.radioGroup) {
                RadioGroup.GENERATED -> "创建一份可持续变化的声场编排。"
                RadioGroup.OFFICIAL -> "开放录音通过审核后会出现在这里。"
                RadioGroup.CUSTOM -> "导入本地音频、添加直接流，或保存一份生成编排。"
            },
            color = SoundistColors.TextMuted, fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp),
        )
    }
}

/** App.tsx station list row (6218–6247). */
@Composable
private fun RadioStationRow(station: RadioStation, state: ListeningState, dispatch: (ListeningAction) -> Unit, reduceMotion: Boolean) {
    val isCurrent = state.selectedStationId == station.id
    val playing = isCurrent && state.radioPlayback.isRadioActive
    val borderColor = when {
        isCurrent && playing -> SoundistColors.Warm.copy(alpha = 0.45f)
        isCurrent -> SoundistColors.DividerStrong
        else -> SoundistColors.Divider
    }
    val background = when {
        isCurrent && playing -> Color(0x7343301F) // rgba(67,48,31,0.45)
        isCurrent -> SoundistColors.RaisedStrong
        else -> SoundistColors.Raised
    }
    Column(Modifier.fillMaxWidth().background(background, RoundedCornerShape(12.dp)).border(1.dp, borderColor, RoundedCornerShape(12.dp))) {
        Row(
            Modifier.fillMaxWidth().clickable { dispatch(ListeningAction.PlayStation(station.id)) }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(40.dp).background(
                    if (isCurrent && playing) SoundistColors.Warm.copy(alpha = 0.12f) else if (isCurrent) SoundistColors.RaisedStrong else SoundistColors.DeepSea,
                    RoundedCornerShape(12.dp),
                ),
                contentAlignment = Alignment.Center,
            ) { RadioArtwork(station, active = isCurrent) }
            Column(Modifier.weight(1f)) {
                Text(station.name, color = if (isCurrent && playing) RadioLight else SoundistColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp)
                Text(station.description, color = SoundistColors.TextSecondary, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
            Column(Modifier.padding(vertical = 0.dp), horizontalAlignment = Alignment.End) {
                Text(
                    if (isCurrent) (if (state.radioPlayback != PlaybackState.IDLE) radioStatusLabel(state.radioPlayback) else "已选择") else station.genre,
                    color = if (isCurrent && playing) SoundistColors.Warm else SoundistColors.TextSecondary, fontSize = 11.sp,
                )
                Text(
                    station.durationLabel.ifBlank {
                        when (station.sourceKind) {
                            RadioSourceKind.LOCAL -> "本机"
                            RadioSourceKind.STREAM -> "LIVE"
                            else -> if (station.tracks.isNotEmpty()) "${station.tracks.size} 首" else "持续生成"
                        }
                    },
                    color = SoundistColors.TextMuted, fontSize = 10.sp,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 36.dp).borderTop(1.dp, SoundistColors.Divider.copy(alpha = 0.65f)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stationFooterLabel(station), color = SoundistColors.TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Box(Modifier.heightIn(min = 36.dp).clickable { dispatch(ListeningAction.OpenStationDetails(station.id)) }.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                Text("详情", fontSize = 10.sp, color = SoundistColors.TextSecondary)
            }
        }
    }
}

/** App.tsx station footer label (6243). */
private fun stationFooterLabel(station: RadioStation): String = when {
    station.tracks.isNotEmpty() -> "${station.tracks.size} 首 · ${station.purposes.take(2).joinToString(" / ").ifBlank { station.genre }}"
    station.sourceKind == RadioSourceKind.GENERATED -> {
        val layerCount = station.generatorArrangement?.layers?.size ?: station.layers.size
        val ambientCount = station.generatorArrangement?.ambientTracks?.size ?: 0
        if (ambientCount > 0) "$layerCount 个编排层 · $ambientCount 条环境声轨" else "$layerCount 个编排层"
    }
    station.sourceKind == RadioSourceKind.LOCAL -> "${station.localAudio.size} 首 · 仅本机"
    else -> "私人直接音频流"
}

/** App.tsx GENERATOR_OPTION_LABELS arc entries (2408–2410) for the 演化 stat. */
private fun arcLabel(arc: String): String = when (arc) {
    "steady" -> "平稳"
    "breathing" -> "呼吸"
    "journey" -> "旅程"
    else -> arc
}

/** App.tsx license detail dialog (6102–6166). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun StationDetails(station: RadioStation, state: ListeningState, dispatch: (ListeningAction) -> Unit) {
    var sourcesExpanded by remember(station.id) { mutableStateOf(false) }
    val active = state.selectedStationId == station.id
    val arrangement = station.generatorArrangement
    val tracks = station.tracks
    // App.tsx 许可区 <a href={license.url/sourcePage} target="_blank">：用系统浏览器打开许可证页 / 来源页。
    val context = LocalContext.current
    val openLink: (String) -> Unit = { url ->
        if (url.isNotBlank()) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
    ModalBottomSheet(
        onDismissRequest = { dispatch(ListeningAction.OpenStationDetails(null)) },
        containerColor = SoundistColors.Raised,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f)) {
            // Header (6106–6110)
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(48.dp).background(SoundistColors.RaisedStrong, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { RadioArtwork(station, active) }
                Column(Modifier.weight(1f)) {
                    Text(station.name, color = SoundistColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(station.description, color = SoundistColors.TextSecondary, fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Box(Modifier.size(44.dp).clickable { dispatch(ListeningAction.OpenStationDetails(null)) }, contentAlignment = Alignment.Center) { Icon(x, "关闭频道详情", Modifier.size(16.dp), tint = SoundistColors.TextMuted) }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(SoundistColors.Divider))
            LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp)) {
                // Genre / purpose chips (6112–6115)
                item {
                    FlowRow(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.background(SoundistColors.RaisedStrong, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 4.dp)) { Text(station.genre, color = SoundistColors.TextSecondary, fontSize = 10.sp) }
                        station.purposes.forEach { purpose ->
                            Box(Modifier.border(1.dp, SoundistColors.Divider, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 4.dp)) { Text(purpose, color = SoundistColors.TextSecondary, fontSize = 10.sp) }
                        }
                    }
                }
                // Action buttons (6116–6125)
                item {
                    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f).heightIn(min = 44.dp).background(SoundistColors.Warm, RoundedCornerShape(8.dp)).clickable { dispatch(ListeningAction.PlayStation(station.id)); dispatch(ListeningAction.OpenStationDetails(null)) }, contentAlignment = Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(play, null, Modifier.size(14.dp), tint = SoundistColors.Abyss)
                                Text("播放频道", color = SoundistColors.Abyss, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        when {
                            station.sourceKind == RadioSourceKind.GENERATED -> Box(Modifier.weight(1f).heightIn(min = 44.dp).border(1.dp, SoundistColors.Divider, RoundedCornerShape(8.dp)).clickable { dispatch(ListeningAction.OpenStationDetails(null)); dispatch(ListeningAction.OpenGeneratorEditor(station)) }, contentAlignment = Alignment.Center) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(slidersHorizontal, null, Modifier.size(14.dp), tint = SoundistColors.TextSecondary)
                                    Text("调整并另存", color = SoundistColors.TextSecondary, fontSize = 12.sp)
                                }
                            }
                            station.custom -> Box(Modifier.weight(1f).heightIn(min = 44.dp).border(1.dp, SoundistColors.Divider, RoundedCornerShape(8.dp)).clickable { dispatch(ListeningAction.OpenStationDetails(null)); dispatch(ListeningAction.EditRadio(station)) }, contentAlignment = Alignment.Center) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(pencilLine, null, Modifier.size(14.dp), tint = SoundistColors.TextSecondary)
                                    Text("编辑频道", color = SoundistColors.TextSecondary, fontSize = 12.sp)
                                }
                            }
                            else -> Box(Modifier.weight(1f).heightIn(min = 44.dp).border(1.dp, SoundistColors.Divider, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                                Text("${tracks.size} 首 · ${if (station.transitionMode == "crossfade") "${fmtSeconds(station.transitionSeconds)} 秒淡化衔接" else "自然衔接"}", color = SoundistColors.TextMuted, fontSize = 10.sp)
                            }
                        }
                    }
                }
                // Tracks (6127–6136)
                if (tracks.isNotEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("曲目", color = SoundistColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text("${tracks.size} 首", color = SoundistColors.TextMuted, fontSize = 10.sp)
                            }
                        }
                    }
                    items(tracks, key = { it.id }) { item ->
                        val index = tracks.indexOf(item)
                        // 详情曲目点击：该频道从该曲目 index 开始播 + 继续循环（ViewModel playStationTrack → playRadio，电台以 tracks 为坐标）。
                        val isCurrent = state.selectedStationId == station.id && state.radioTrackIndex == index
                        val isPlaying = isCurrent && (state.radioPlayback.isRadioActive || state.radioPlayback == PlaybackState.LOADING)
                        Row(
                            Modifier.fillMaxWidth()
                                .then(if (isCurrent) Modifier.background(SoundistColors.RaisedStrong, RoundedCornerShape(8.dp)) else Modifier)
                                .clickable { dispatch(ListeningAction.PlayStationTrack(station.id, index)) }
                                .padding(vertical = 8.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("${index + 1}", color = if (isCurrent) SoundistColors.Warm else SoundistColors.TextMuted, fontSize = 10.sp, modifier = Modifier.width(20.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.title, color = if (isCurrent) SoundistColors.Warm else SoundistColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 15.sp)
                                Text(item.artist, color = SoundistColors.TextSecondary, fontSize = 10.sp, lineHeight = 13.sp, modifier = Modifier.padding(top = 2.dp))
                            }
                            if (isPlaying) Icon(pause, "正在播放", Modifier.size(14.dp), tint = SoundistColors.Warm)
                            else Text(item.durationLabel.ifBlank { if (item.focusFit == "deep") "低干扰" else "聆听" }, color = SoundistColors.TextMuted, fontSize = 10.sp)
                        }
                    }
                }
                // Arrangement (6138–6144)
                if (arrangement != null) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("持续变化方式", color = SoundistColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text("${arrangement.layers.size} 层", color = SoundistColors.TextMuted, fontSize = 10.sp)
                            }
                            Text("段落、留白和事件密度会缓慢变化；启用声场响应后，当前环境声会影响音区、空间与节奏，但不会突然切换。", color = SoundistColors.TextSecondary, fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 8.dp))
                            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatCell("演化", arcLabel(arrangement.arc), Modifier.weight(1f))
                                StatCell("场景", "${arrangement.scenes.size}", Modifier.weight(1f))
                                StatCell("环境声", "${arrangement.ambientTracks.size}", Modifier.weight(1f))
                            }
                            Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                                arrangement.layers.forEach { layer ->
                                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Column(Modifier.weight(1f)) {
                                            Text(layer.name, color = SoundistColors.Text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${timbreLabel(layer.timbre)} · ${layer.probability}% 概率", color = SoundistColors.TextMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 2.dp))
                                        }
                                        Text("${layer.volume}%", color = SoundistColors.TextSecondary, fontSize = 10.sp)
                                    }
                                }
                            }
                            if (arrangement.ambientTracks.isNotEmpty()) {
                                FlowRow(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    arrangement.ambientTracks.forEach { track ->
                                        Box(Modifier.border(1.dp, SoundistColors.Divider, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 4.dp)) { Text("${track.name} ${track.volume}%", color = SoundistColors.TextSecondary, fontSize = 9.sp) }
                                    }
                                }
                            }
                        }
                    }
                }
                // Custom private source (6146–6149)
                if (station.custom) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                            Text("私人来源", color = SoundistColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            if (station.sourceKind == RadioSourceKind.LOCAL) {
                                Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                    station.localAudio.forEachIndexed { index, file ->
                                        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("${index + 1}", color = SoundistColors.TextMuted, fontSize = 9.sp, modifier = Modifier.width(20.dp))
                                            Text(file.displayName, color = SoundistColors.TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                            Text(formatAudioDuration(file.durationSeconds), color = SoundistColors.TextMuted, fontSize = 9.sp)
                                        }
                                    }
                                }
                            } else {
                                Text(station.url.ifBlank { "直接音频流" }, color = SoundistColors.TextSecondary, fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    }
                }
                // Sources & production (6151–6162)
                if (tracks.isNotEmpty() || station.license != null) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { sourcesExpanded = !sourcesExpanded }, contentAlignment = Alignment.CenterStart) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("来源与制作", color = SoundistColors.Text, fontSize = 12.sp)
                                        Text("逐曲保留作者、录音来源与开放许可", color = SoundistColors.TextMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                                    }
                                    Icon(chevronDown, "展开来源", Modifier.size(16.dp).rotate(if (sourcesExpanded) 180f else 0f), tint = SoundistColors.TextMuted)
                                }
                            }
                            if (sourcesExpanded) {
                                Column(Modifier.fillMaxWidth()) {
                                    val entries: List<Pair<String, TrackLicense>> = if (tracks.isNotEmpty()) tracks.mapNotNull { it.license?.let { l -> it.title to l } }
                                    else station.license?.let { listOf(station.name to it) } ?: emptyList()
                                    entries.forEach { (title, license) ->
                                        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Column(Modifier.weight(1f)) { Text(title, color = SoundistColors.Text, fontSize = 10.sp); Text(license.author, color = SoundistColors.TextMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp)) }
                                                Text(license.name, color = SoundistColors.TealSoft, fontSize = 10.sp, textDecoration = TextDecoration.Underline, modifier = Modifier.clickable { openLink(license.licenseUrl) }.padding(horizontal = 4.dp, vertical = 2.dp))
                                            }
                                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(license.sourceName, color = SoundistColors.TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                                Text("原始页面", color = SoundistColors.TextSecondary, fontSize = 10.sp, textDecoration = TextDecoration.Underline, modifier = Modifier.clickable { openLink(license.sourcePage) }.padding(horizontal = 4.dp, vertical = 2.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(SoundistColors.DeepSea, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = SoundistColors.TextMuted, fontSize = 10.sp)
        Text(value, color = SoundistColors.Text, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

private const val MAX_LOCAL_AUDIO_BYTES = 200L * 1024L * 1024L
internal fun List<LocalAudioSelection>.validLocalAudioSelections() = filter {
    it.sizeBytes in 1..MAX_LOCAL_AUDIO_BYTES && it.mimeType.startsWith("audio/")
}

// ── Shared overlay / field primitives (App.tsx 5862, 5997, 6011, 6169 fixed overlays) ──

/** App.tsx formatFileSize (2296–2300). */
private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "未知大小"
    if (bytes < 1024L * 1024L) return "${maxOf(1, (bytes / 1024.0).roundToInt())} KB"
    val mb = bytes / 1024.0 / 1024.0
    return if (bytes > 10L * 1024L * 1024L) "${mb.roundToInt()} MB" else "${(mb * 10.0).roundToInt() / 10.0} MB"
}

/** App.tsx formatAudioDuration (2302–2306). */
private fun formatAudioDuration(seconds: Int): String {
    if (seconds <= 0) return "时长待识别"
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

/** App.tsx slider 数值显示：整数值去掉多余 ".0"。 */
private fun fmt(value: Float): String = if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()

/** 过渡秒数显示：整数值去掉多余 ".0"（前端 transition.seconds 为小数）。 */
private fun fmtSeconds(value: Double): String = if (value == value.toInt().toDouble()) value.toInt().toString() else value.toString()

/** App.tsx 0.5 步长（seconds 滑杆）。 */
private fun halfStep(value: Float): Float = (value * 2f).roundToInt() / 2f

private val AmberWarning = Color(0xCCFCD34D) // text-amber-300/80
private val RoseText = Color(0xFFFDA4AF)      // text-rose-300

/** App.tsx 88dvh 面板高度。 */
@Composable
private fun maxPanelHeight(): Dp {
    val configuration = LocalConfiguration.current
    return (configuration.screenHeightDp * 0.88f).dp
}

/** App.tsx fixed inset-0 items-end justify-center 遮罩 + 底部居中面板。 */
@Composable
private fun AppOverlay(scrimAlpha: Float, onDismiss: (() -> Unit)?, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = { onDismiss?.invoke() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrimAlpha)), contentAlignment = Alignment.BottomCenter) {
            if (onDismiss != null) {
                Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss))
            }
            content()
        }
    }
}

/** App.tsx 文本/数字输入框（<input>）。 */
@Composable
private fun GenInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 40.dp,
    background: Color = SoundistColors.DeepSea,
    placeholder: String = "",
    textColor: Color = SoundistColors.Text,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().heightIn(min = minHeight)
            .background(background, RoundedCornerShape(8.dp))
            .border(1.dp, SoundistColors.Divider, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp),
        singleLine = true,
        textStyle = TextStyle(color = textColor, fontSize = 12.sp),
        cursorBrush = SolidColor(SoundistColors.TealSoft),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, color = SoundistColors.TextMuted, fontSize = 12.sp)
                inner()
            }
        },
    )
}

/** App.tsx 带顶部小标签的字段容器。 */
@Composable
private fun GenField(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier) {
        Text(label, color = SoundistColors.TextMuted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 4.dp))
        content()
    }
}

/** App.tsx <select> 下拉。 */
@Composable
private fun GenSelect(
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 44.dp,
    background: Color = SoundistColors.DeepSea,
    valueColor: Color = SoundistColors.TextSecondary,
) {
    SoundistSelect(
        value = value,
        options = options,
        onSelect = onSelect,
        modifier = modifier.fillMaxWidth(),
        minHeight = minHeight,
        background = background,
        valueColor = valueColor,
    )
}

/** App.tsx accent 复选框。 */
@Composable
private fun GenCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.size(20.dp),
        colors = CheckboxDefaults.colors(checkedColor = SoundistColors.Teal, uncheckedColor = SoundistColors.DividerStrong, checkmarkColor = SoundistColors.Abyss),
    )
}

/** 编排器滑块拖动中「限频实时更新」的固定节流间隔（毫秒）。30–60Hz 取 33Hz≈30ms，真节流（非 debounce）。 */
private const val LIVE_THROTTLE_MS = 30L

/** App.tsx sound-slider 范围滑杆 (accent-color: #55B6A3 原生滑杆)。 */
@Composable
private fun GenSlider(value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>, modifier: Modifier = Modifier) {
    SoundSlider(value = value, onValueChange = onValueChange, valueRange = valueRange, modifier = modifier)
}

/**
 * 编排器滑块（阶段 2b）：拖动中本地临时值每帧跟手（不逐像素写外部状态）+ 固定节流实时发参数；
 * 松手（onValueChangeFinished）保证最终值已发出。用于「无需撤销」的编排参数（速度/密度/变化/声场响应等设置）。
 *
 * [key] 为调用点传入的稳定标识（参数名 / layer id / scene id / ambient soundId），切换 layer/scene 时
 * remember 状态随之重置，不会继承上一项的本地值。
 */
@Composable
private fun ValueCommitSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    key: Any,
    update: (Float) -> Unit,
) {
    var dragging by remember(key) { mutableStateOf(false) }
    var localValue by remember(key) { mutableFloatStateOf(value) }
    var startValue by remember(key) { mutableFloatStateOf(value) }
    var lastEmitMs by remember(key) { mutableLongStateOf(0L) }
    var lastEmitValue by remember(key) { mutableFloatStateOf(value) }
    // 非拖动时同步外部 value（reset/undo 后本地值不陈旧），拖动中保持本地值。
    if (!dragging && localValue != value) {
        localValue = value
    }
    SoundSlider(
        value = if (dragging) localValue else value,
        valueRange = valueRange,
        modifier = modifier,
        onValueChange = { v ->
            if (!dragging) { dragging = true; startValue = localValue }
            localValue = v
            // 固定节流（30–60Hz）：连续拖动期间持续发参数，不 cancel 不 debounce。
            val now = SystemClock.uptimeMillis()
            if (now - lastEmitMs >= LIVE_THROTTLE_MS) {
                lastEmitMs = now
                lastEmitValue = v
                update(v)
            }
        },
        onValueChangeFinished = {
            dragging = false
            // 松手保证最终值已发出（若最后一次节流发送不是最终值，补发一次）。
            if (localValue != lastEmitValue) {
                lastEmitValue = localValue
                update(localValue)
            }
        },
    )
}

/**
 * 编排器编排滑块（阶段 2b）：拖动中本地临时值每帧跟手 + 固定节流实时发编排
 * （UpdateGeneratorArrangementLive，不写撤销栈）；松手时以拖动开始前的 base 编排一次性提交
 * （FinalizeGeneratorArrangement → 只写一条撤销、落进编辑器状态）。
 *
 * [key] 为调用点传入的稳定标识（layer id / scene id / ambient soundId），切换时 remember 状态重置，
 * 不会继承上一项的本地值。
 */
@Composable
private fun ArrangementCommitSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    key: Any,
    arrangement: GeneratedArrangement,
    update: (GeneratedArrangement, Float) -> GeneratedArrangement,
    dispatch: (ListeningAction) -> Unit,
) {
    val latestArrangement by rememberUpdatedState(arrangement)
    var dragging by remember(key) { mutableStateOf(false) }
    var localValue by remember(key) { mutableFloatStateOf(value) }
    var startValue by remember(key) { mutableFloatStateOf(value) }
    var base by remember(key) { mutableStateOf<GeneratedArrangement?>(null) }
    var lastEmitMs by remember(key) { mutableLongStateOf(0L) }
    var lastEmitValue by remember(key) { mutableFloatStateOf(value) }
    // 非拖动时同步外部 value（reset/undo 后本地值不陈旧），拖动中保持本地值。
    if (!dragging && localValue != value) {
        localValue = value
    }
    SoundSlider(
        value = if (dragging) localValue else value,
        valueRange = valueRange,
        modifier = modifier,
        onValueChange = { v ->
            // 首次变化即拖动开始：记录拖动前值 + 拖动前的编排（此刻固定节流实时更新尚未发出，状态仍为拖动前）。
            if (!dragging) { dragging = true; startValue = localValue; base = latestArrangement }
            localValue = v
            // 固定节流（30–60Hz）：连续拖动期间持续发 live 更新，不 cancel 不 debounce。
            val now = SystemClock.uptimeMillis()
            if (now - lastEmitMs >= LIVE_THROTTLE_MS) {
                lastEmitMs = now
                lastEmitValue = v
                dispatch(ListeningAction.UpdateGeneratorArrangementLive { a -> update(a, v) })
            }
        },
        onValueChangeFinished = {
            val final = localValue
            val b = base
            dragging = false
            base = null
            if (final != startValue) {
                // 有变化才一次性提交并写一条撤销（回到拖动前），松手最终值随提交落进编辑器状态。
                dispatch(ListeningAction.FinalizeGeneratorArrangement(b ?: latestArrangement) { a -> update(a, final) })
                lastEmitValue = final
            } else if (final != lastEmitValue) {
                // 拖回原值：撤销栈不写，但把最终值同步给 live 更新，保证外部状态与拇指一致。
                dispatch(ListeningAction.UpdateGeneratorArrangementLive { a -> update(a, final) })
                lastEmitValue = final
            }
        },
    )
}

/** App.tsx 带标签 + 数值读数的滑杆字段（滑块内容由调用方以 slot 提供，支持编排器松手提交）。 */
@Composable
private fun GenSliderField(
    label: String,
    valueText: String,
    modifier: Modifier = Modifier,
    slider: @Composable (Modifier) -> Unit,
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = SoundistColors.TextSecondary, fontSize = 10.sp)
            Text(valueText, color = SoundistColors.Text, fontSize = 10.sp)
        }
        slider(Modifier.fillMaxWidth())
    }
}

/** App.tsx showNotice 无动作提示条 (8473–8493)。前端 2800ms 自动消失。 */
@Composable
private fun NoticeOverlay(message: String, onDismiss: () -> Unit) {
    LaunchedEffect(message) {
        delay(2800)
        onDismiss()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 94.dp).heightIn(min = 48.dp)
                .background(SoundistColors.RaisedStrong, RoundedCornerShape(12.dp))
                .border(1.dp, SoundistColors.Teal.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(message, color = SoundistColors.Text, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
            Box(Modifier.size(44.dp).clickable { onDismiss() }, contentAlignment = Alignment.Center) { Icon(x, "关闭提示", Modifier.size(16.dp), tint = SoundistColors.TextMuted) }
        }
    }
}

/** App.tsx 声场编排器 (5861–5994)。 */
@Composable
private fun GeneratorEditor(state: ListeningState, dispatch: (ListeningAction) -> Unit) {
    val arrangement = state.editorGeneratorArrangement ?: return
    val settings = state.editorGeneratorSettings
    val context = LocalContext.current
    val importConfigurationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { readGeneratorConfiguration(context, uri) }
            .onSuccess { dispatch(ListeningAction.ImportGeneratorConfiguration(it)) }
            .onFailure { dispatch(ListeningAction.ShowNotice(it.message ?: "无法读取这个编排配置")) }
    }
    val layers = arrangement.layers
    val ambientTracks = arrangement.ambientTracks
    val scenes = arrangement.scenes.ifEmpty { createGeneratorScenes(arrangement.arc) }
    val selectedLayer = layers.firstOrNull { it.id == state.selectedGeneratorLayerId } ?: layers.firstOrNull()
    val selectedScene = scenes.firstOrNull { it.id == state.selectedGeneratorSceneId } ?: scenes.firstOrNull()
    val selectedAmbientTrack = ambientTracks.firstOrNull { it.soundId == state.selectedAmbientTrackId }
    val availableAmbientSounds = state.sounds.filter { sound -> ambientTracks.none { it.soundId == sound.id } }
    val channelAmbientAdjusted = state.generatorSourceStationId
        ?.let { state.channelAmbientSessions[it]?.adjusted }
        ?: false
    val radioPlaying = state.radioPlayback != PlaybackState.IDLE
    val previewingSource = radioPlaying && state.selectedStationId == state.generatorSourceStationId
    // 试听按钮图标按真实播放状态切换：暂停后应显示播放图标（不是继续显示暂停图标）。
    val previewingActive = state.radioPlayback.isRadioActive && previewingSource
    val totalSceneMinutes = scenes.sumOf { it.durationMinutes }
    val commit: ((GeneratedArrangement) -> GeneratedArrangement) -> Unit = { updater -> dispatch(ListeningAction.CommitGeneratorArrangement(updater)) }
    val updateLayer: (String, (GeneratedLayer) -> GeneratedLayer) -> Unit = { id, transform ->
        commit { arr -> arr.copy(layers = arr.layers.map { if (it.id == id) transform(it) else it }) }
    }
    val updateTrack: (String, (GeneratedAmbientTrack) -> GeneratedAmbientTrack) -> Unit = { soundId, transform ->
        commit { arr -> arr.copy(ambientTracks = arr.ambientTracks.map { if (it.soundId == soundId) transform(it) else it }) }
    }
    val updateScene: (String, (GeneratedScene) -> GeneratedScene) -> Unit = { id, transform ->
        commit { arr -> arr.copy(scenes = arr.scenes.map { if (it.id == id) transform(it) else it }) }
    }

    AppOverlay(scrimAlpha = 0.65f, onDismiss = { dispatch(ListeningAction.RequestCloseGeneratorEditor) }) {
        Column(
            Modifier.widthIn(max = 390.dp).fillMaxWidth().heightIn(max = maxPanelHeight())
                .background(SoundistColors.Raised, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .border(1.dp, SoundistColors.Divider, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
        ) {
            // Header (5865–5872)
            Row(
                Modifier.fillMaxWidth().border(width = 0.dp, color = Color.Transparent).background(SoundistColors.Raised, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .borderBottom(1.dp, SoundistColors.Divider).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("声场编排器", color = SoundistColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("先确定整体听感，再用场景、音色和环境声轨组织长时变化", color = SoundistColors.TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Row(Modifier.padding(start = 8.dp)) {
                    Box(Modifier.size(width = 36.dp, height = 44.dp).alpha(if (state.generatorPast.isEmpty()) 0.25f else 1f).clickable(enabled = state.generatorPast.isNotEmpty()) { dispatch(ListeningAction.UndoGeneratorChange) }, contentAlignment = Alignment.Center) { Icon(undo2, "撤销编排修改", Modifier.size(14.dp), tint = SoundistColors.TextMuted) }
                    Box(Modifier.size(width = 36.dp, height = 44.dp).alpha(if (state.generatorFuture.isEmpty()) 0.25f else 1f).clickable(enabled = state.generatorFuture.isNotEmpty()) { dispatch(ListeningAction.RedoGeneratorChange) }, contentAlignment = Alignment.Center) { Icon(redo2, "重做编排修改", Modifier.size(14.dp), tint = SoundistColors.TextMuted) }
                    Box(Modifier.size(44.dp).clickable { dispatch(ListeningAction.RequestCloseGeneratorEditor) }, contentAlignment = Alignment.Center) { Icon(x, "关闭声场编排器", Modifier.size(16.dp), tint = SoundistColors.TextMuted) }
                }
            }
            // Scrollable body (5873–5991)
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 28.dp)) {
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("声场配方", color = SoundistColors.Text, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text("适合快速创建", color = SoundistColors.TextMuted, fontSize = 9.sp)
                }
                if (state.generatorAdvancedOpen) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GenField("保存名称", Modifier.weight(1f)) {
                            GenInput(state.generatorDraftName, { dispatch(ListeningAction.SetGeneratorDraftName(it)) })
                        }
                        GenField("随机种子", Modifier.width(112.dp)) {
                            GenInput(arrangement.seed, { v -> commit { it.copy(seed = v) } }, minHeight = 44.dp)
                        }
                    }
                } else {
                    GenField("保存名称") { GenInput(state.generatorDraftName, { dispatch(ListeningAction.SetGeneratorDraftName(it)) }) }
                }
                // Arc + section length (5879–5884)
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.weight(1f).background(SoundistColors.DeepSea, RoundedCornerShape(8.dp)).border(1.dp, SoundistColors.Divider, RoundedCornerShape(8.dp)).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        listOf("steady" to "平稳", "breathing" to "呼吸", "journey" to "旅程").forEach { (arc, label) ->
                            Box(
                                Modifier.weight(1f).heightIn(min = 44.dp).background(if (arrangement.arc == arc) SoundistColors.RaisedStrong else Color.Transparent, RoundedCornerShape(6.dp))
                                    .clickable { val sc = createGeneratorScenes(arc); commit { it.copy(arc = arc, scenes = sc) }; dispatch(ListeningAction.SelectGeneratorScene(sc.firstOrNull()?.id)) },
                                contentAlignment = Alignment.Center,
                            ) { Text(label, color = if (arrangement.arc == arc) SoundistColors.Text else SoundistColors.TextMuted, fontSize = 10.sp) }
                        }
                    }
                    GenField("段落长度", Modifier.width(92.dp)) {
                        GenSelect(arrangement.sectionMinutes.toString(), listOf("2" to "2 分钟", "4" to "4 分钟", "8" to "8 分钟"), { v -> commit { it.copy(sectionMinutes = v.toInt()) } })
                    }
                }
                Text(
                    when (arrangement.arc) {
                        "steady" -> "平稳：维持低波动密度，适合长时阅读与编码。"
                        "breathing" -> "呼吸：约每 26 秒完成一次短周期疏密起伏。"
                        else -> "旅程：按引入、展开、开阔与回落四段推进，音区和密度会同步变化。"
                    },
                    color = SoundistColors.TextMuted, fontSize = 10.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp),
                )
                // Adaptive (5889–5892)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 16.dp).borderY(1.dp, SoundistColors.Divider).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("随环境声变化", color = SoundistColors.Text, fontSize = 12.sp)
                        Text("水、风、暖色场景与城市声会分别影响时值、音色和节奏", color = SoundistColors.TextMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    GenCheckbox(settings.adaptive, { dispatch(ListeningAction.UpdateGeneratorSettings { s -> s.copy(adaptive = it) }) })
                }
                // Settings sliders (5893–5895)：grid-cols-2 gap-x-4 gap-y-3 → 列距 16、行距 12，末行后无额外间距。
                Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        GenSliderField("速度", "${settings.tempo}BPM", Modifier.weight(1f)) { m ->
                            ValueCommitSlider(settings.tempo.toFloat(), 30f..120f, m, key = "tempo") { v -> dispatch(ListeningAction.UpdateGeneratorSettings { s -> s.copy(tempo = v.roundToInt()) }) }
                        }
                        GenSliderField("整体密度", "${settings.density}%", Modifier.weight(1f)) { m ->
                            ValueCommitSlider(settings.density.toFloat(), 10f..90f, m, key = "density") { v -> dispatch(ListeningAction.UpdateGeneratorSettings { s -> s.copy(density = v.roundToInt()) }) }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        GenSliderField("自动变化", "${settings.variation}%", Modifier.weight(1f)) { m ->
                            ValueCommitSlider(settings.variation.toFloat(), 0f..100f, m, key = "variation") { v -> dispatch(ListeningAction.UpdateGeneratorSettings { s -> s.copy(variation = v.roundToInt()) }) }
                        }
                        GenSliderField("声场响应", "${settings.ambientResponse}%", Modifier.weight(1f)) { m ->
                            ValueCommitSlider(settings.ambientResponse.toFloat(), 0f..100f, m, key = "ambientResponse") { v -> dispatch(ListeningAction.UpdateGeneratorSettings { s -> s.copy(ambientResponse = v.roundToInt()) }) }
                        }
                    }
                }
                // Ambient tracks (5897–5923)
                Column(Modifier.fillMaxWidth().padding(top = 20.dp).borderY(1.dp, SoundistColors.Divider).padding(vertical = 16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("环境声轨", color = SoundistColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text("保存具体声源和每轨音量；再次播放私人频道时会恢复这组声场。", color = SoundistColors.TextMuted, fontSize = 10.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                        Box(Modifier.heightIn(min = 44.dp).border(1.dp, SoundistColors.Divider, RoundedCornerShape(6.dp)).clickable { dispatch(ListeningAction.LoadCurrentAmbient) }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                            Text("载入当前", color = SoundistColors.TextSecondary, fontSize = 10.sp)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp).background(SoundistColors.DeepSea, RoundedCornerShape(8.dp)).border(1.dp, SoundistColors.Divider, RoundedCornerShape(8.dp)).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        listOf("preset" to "跟随频道声场", "current" to "保留我的环境声").forEach { (mode, label) ->
                            Box(
                                Modifier.weight(1f).heightIn(min = 42.dp)
                                    .background(if (arrangement.ambientMode == mode) SoundistColors.RaisedStrong else Color.Transparent, RoundedCornerShape(6.dp))
                                    .clickable { dispatch(ListeningAction.SetGeneratorAmbientMode(mode)) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(label, color = if (arrangement.ambientMode == mode) SoundistColors.Text else SoundistColors.TextMuted, fontSize = 10.sp)
                            }
                        }
                    }
                    Text(
                        if (arrangement.ambientMode == "current") "当前使用声音页的个人组合；频道配方仍被保留，切回后继续使用本次调整。"
                        else if (channelAmbientAdjusted) "频道声场 · 已调整。新增、移除和音量变化会立即作用，但不会覆盖你的个人组合。"
                        else "频道声场 · 默认配方。执行逐轨入场、概率、淡入淡出和关系编排。",
                        color = SoundistColors.TextMuted, fontSize = 9.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 8.dp),
                    )
                    if (arrangement.ambientMode == "preset" && channelAmbientAdjusted) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Box(
                                Modifier.heightIn(min = 40.dp)
                                    .border(1.dp, SoundistColors.Divider, RoundedCornerShape(6.dp))
                                    .clickable { dispatch(ListeningAction.RestoreChannelAmbientDefault) }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("恢复频道默认", color = SoundistColors.TextSecondary, fontSize = 10.sp)
                            }
                        }
                    }
                    GenField("添加环境声", Modifier.padding(top = 12.dp)) {
                        GenSelect("", listOf("" to "从 84 种环境声中选择") + availableAmbientSounds.map { it.id to "${it.name} · ${it.category.label()}" }, { v -> if (v.isNotEmpty()) dispatch(ListeningAction.AddGeneratorAmbientTrack(v)) })
                    }
                    if (ambientTracks.isNotEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(top = 12.dp).borderY(1.dp, SoundistColors.Divider)) {
                            ambientTracks.forEachIndexed { trackIndex, track ->
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                        .then(if (trackIndex > 0) Modifier.borderTop(1.dp, SoundistColors.Divider) else Modifier)
                                        .background(if (selectedAmbientTrack?.soundId == track.soundId) SoundistColors.Teal.copy(alpha = 0.035f) else Color.Transparent)
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    GenCheckbox(track.enabled, { updateTrack(track.soundId) { it.copy(enabled = !it.enabled) } })
                                    Column(Modifier.width(82.dp).heightIn(min = 44.dp).clickable { dispatch(ListeningAction.SelectAmbientTrack(track.soundId)) }, verticalArrangement = Arrangement.Center) {
                                        Text(track.name, color = SoundistColors.Text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(track.category, color = SoundistColors.TextMuted, fontSize = 9.sp)
                                    }
                                    ArrangementCommitSlider(
                                        track.volume.toFloat(), 0f..100f, Modifier.weight(1f), key = track.soundId, arrangement,
                                        update = { a, v -> a.copy(ambientTracks = a.ambientTracks.map { if (it.soundId == track.soundId) it.copy(volume = v.roundToInt()) else it }) },
                                        dispatch = dispatch,
                                    )
                                    Text("${track.volume}", color = SoundistColors.TextMuted, fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.width(28.dp))
                                    Box(Modifier.size(40.dp).clickable { dispatch(ListeningAction.RemoveGeneratorAmbientTrack(track.soundId)) }, contentAlignment = Alignment.Center) { Icon(trash2, "移除${track.name}", Modifier.size(14.dp), tint = SoundistColors.TextMuted) }
                                }
                            }
                        }
                    } else {
                        Text("尚未绑定环境声。频道仍可运行生成音乐，也可以在播放时响应当前外部声场。", color = SoundistColors.TextMuted, fontSize = 10.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 12.dp).border(1.dp, SoundistColors.Divider, RoundedCornerShape(8.dp)).padding(12.dp))
                    }
                    if (state.generatorAdvancedOpen && selectedAmbientTrack != null) {
                        Column(Modifier.fillMaxWidth().padding(top = 12.dp).background(SoundistColors.DeepSea, RoundedCornerShape(8.dp)).border(1.dp, SoundistColors.Divider, RoundedCornerShape(8.dp)).padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${selectedAmbientTrack.name} · 逐轨混音", color = SoundistColors.Text, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Text("随频道恢复", color = SoundistColors.TextMuted, fontSize = 9.sp)
                            }
                            GenSliderField("左右位置", "${selectedAmbientTrack.pan}", Modifier.fillMaxWidth().padding(top = 12.dp)) { m ->
                                ArrangementCommitSlider(selectedAmbientTrack.pan.toFloat(), -100f..100f, m, key = selectedAmbientTrack.soundId, arrangement,
                                    update = { a, v -> a.copy(ambientTracks = a.ambientTracks.map { if (it.soundId == selectedAmbientTrack.soundId) it.copy(pan = v.roundToInt()) else it }) },
                                    dispatch = dispatch)
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                GenSliderField("入场延迟", "${selectedAmbientTrack.entryDelaySeconds.roundToInt()}秒", Modifier.weight(1f)) { m ->
                                    ArrangementCommitSlider(selectedAmbientTrack.entryDelaySeconds, 0f..60f, m, key = "${selectedAmbientTrack.soundId}-entry", arrangement,
                                        update = { a, v -> a.copy(ambientTracks = a.ambientTracks.map { if (it.soundId == selectedAmbientTrack.soundId) it.copy(entryDelaySeconds = v) else it }) }, dispatch = dispatch)
                                }
                                GenSliderField("持续时间", if (selectedAmbientTrack.durationMinutes == 0) "整段" else "${selectedAmbientTrack.durationMinutes}分", Modifier.weight(1f)) { m ->
                                    ArrangementCommitSlider(selectedAmbientTrack.durationMinutes.toFloat(), 0f..30f, m, key = "${selectedAmbientTrack.soundId}-duration", arrangement,
                                        update = { a, v -> a.copy(ambientTracks = a.ambientTracks.map { if (it.soundId == selectedAmbientTrack.soundId) it.copy(durationMinutes = v.roundToInt()) else it }) }, dispatch = dispatch)
                                }
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                GenSliderField("出现概率", "${selectedAmbientTrack.probability}%", Modifier.weight(1f)) { m ->
                                    ArrangementCommitSlider(selectedAmbientTrack.probability.toFloat(), 0f..100f, m, key = "${selectedAmbientTrack.soundId}-probability", arrangement,
                                        update = { a, v -> a.copy(ambientTracks = a.ambientTracks.map { if (it.soundId == selectedAmbientTrack.soundId) it.copy(probability = v.roundToInt()) else it }) }, dispatch = dispatch)
                                }
                                GenField("编排关系", Modifier.weight(1f)) {
                                    GenSelect(selectedAmbientTrack.relationship, listOf("independent" to "独立", "follow" to "跟随场景", "avoid" to "避让音乐", "alternate" to "交替出现"),
                                        { v -> updateTrack(selectedAmbientTrack.soundId) { it.copy(relationship = v) } })
                                }
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                GenSliderField("淡入", "${selectedAmbientTrack.fadeInSeconds.roundToInt()}秒", Modifier.weight(1f)) { m ->
                                    ArrangementCommitSlider(selectedAmbientTrack.fadeInSeconds, 0f..12f, m, key = "${selectedAmbientTrack.soundId}-fade-in", arrangement,
                                        update = { a, v -> a.copy(ambientTracks = a.ambientTracks.map { if (it.soundId == selectedAmbientTrack.soundId) it.copy(fadeInSeconds = v) else it }) }, dispatch = dispatch)
                                }
                                GenSliderField("淡出", "${selectedAmbientTrack.fadeOutSeconds.roundToInt()}秒", Modifier.weight(1f)) { m ->
                                    ArrangementCommitSlider(selectedAmbientTrack.fadeOutSeconds, 0f..12f, m, key = "${selectedAmbientTrack.soundId}-fade-out", arrangement,
                                        update = { a, v -> a.copy(ambientTracks = a.ambientTracks.map { if (it.soundId == selectedAmbientTrack.soundId) it.copy(fadeOutSeconds = v) else it }) }, dispatch = dispatch)
                                }
                            }
                            Text("所有参数都进入实际音频执行链；概率按段落和随机种子确定，不会每帧抖动。", color = SoundistColors.TextMuted, fontSize = 9.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 10.dp))
                        }
                    }
                }
                // Advanced toggle (5925–5928)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(top = 20.dp).borderY(1.dp, SoundistColors.Divider)
                        .clickable { dispatch(ListeningAction.ToggleGeneratorAdvanced) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("高级编排", color = SoundistColors.Text, fontSize = 12.sp)
                        Text("管理真实采样、合成层、场景、入场、概率与层间关系", color = SoundistColors.TextMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    Icon(if (state.generatorAdvancedOpen) chevronUp else chevronDown, null, Modifier.size(16.dp), tint = SoundistColors.TextMuted)
                }
                if (state.generatorAdvancedOpen) {
                    // Scene timeline (5931–5940)
                    Column(Modifier.fillMaxWidth().padding(top = 20.dp).borderTop(1.dp, SoundistColors.Divider).padding(top = 16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("场景时间线", color = SoundistColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text("$totalSceneMinutes 分钟循环", color = SoundistColors.TextMuted, fontSize = 10.sp)
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            scenes.forEachIndexed { index, scene ->
                                Column(
                                    Modifier.widthIn(min = 84.dp).heightIn(min = 40.dp)
                                        .background(if (selectedScene?.id == scene.id) SoundistColors.Teal.copy(alpha = 0.08f) else SoundistColors.DeepSea, RoundedCornerShape(6.dp))
                                        .border(1.dp, if (selectedScene?.id == scene.id) SoundistColors.Teal.copy(alpha = 0.45f) else SoundistColors.Divider, RoundedCornerShape(6.dp))
                                        .clickable { dispatch(ListeningAction.SelectGeneratorScene(scene.id)) }
                                        .padding(horizontal = 8.dp),
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text("${index + 1}. ${scene.name}", color = SoundistColors.Text, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${scene.durationMinutes} 分钟 · 能量 ${scene.energy}", color = SoundistColors.TextMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                        }
                        if (selectedScene != null) {
                            Column(Modifier.fillMaxWidth().padding(top = 12.dp).background(SoundistColors.DeepSea, RoundedCornerShape(8.dp)).border(1.dp, SoundistColors.Divider, RoundedCornerShape(8.dp)).padding(12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    GenField("场景名称", Modifier.weight(1f)) {
                                        GenInput(selectedScene.name, { v -> updateScene(selectedScene.id) { it.copy(name = v) } }, minHeight = 40.dp, background = SoundistColors.Raised)
                                    }
                                    GenField("持续分钟", Modifier.width(92.dp)) {
                                        GenInput(selectedScene.durationMinutes.toString(), { v -> v.toIntOrNull()?.let { n -> updateScene(selectedScene.id) { it.copy(durationMinutes = maxOf(1, n)) } } }, minHeight = 40.dp, background = SoundistColors.Raised)
                                    }
                                }
                                Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        GenSliderField("能量", "${selectedScene.energy}%", Modifier.weight(1f)) { m ->
                                            ArrangementCommitSlider(selectedScene.energy.toFloat(), 0f..100f, m, key = selectedScene.id, arrangement,
                                                update = { a, v -> a.copy(scenes = a.scenes.map { if (it.id == selectedScene.id) it.copy(energy = v.roundToInt()) else it }) },
                                                dispatch = dispatch)
                                        }
                                        GenSliderField("事件密度", "${selectedScene.density}%", Modifier.weight(1f)) { m ->
                                            ArrangementCommitSlider(selectedScene.density.toFloat(), 0f..100f, m, key = selectedScene.id, arrangement,
                                                update = { a, v -> a.copy(scenes = a.scenes.map { if (it.id == selectedScene.id) it.copy(density = v.roundToInt()) else it }) },
                                                dispatch = dispatch)
                                        }
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        GenSliderField("明亮度", "${selectedScene.brightness}%", Modifier.weight(1f)) { m ->
                                            ArrangementCommitSlider(selectedScene.brightness.toFloat(), 0f..100f, m, key = selectedScene.id, arrangement,
                                                update = { a, v -> a.copy(scenes = a.scenes.map { if (it.id == selectedScene.id) it.copy(brightness = v.roundToInt()) else it }) },
                                                dispatch = dispatch)
                                        }
                                        GenSliderField("空间感", "${selectedScene.space}%", Modifier.weight(1f)) { m ->
                                            ArrangementCommitSlider(selectedScene.space.toFloat(), 0f..100f, m, key = selectedScene.id, arrangement,
                                                update = { a, v -> a.copy(scenes = a.scenes.map { if (it.id == selectedScene.id) it.copy(space = v.roundToInt()) else it }) },
                                                dispatch = dispatch)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Timbre catalog (5941–5962)
                    Column(Modifier.fillMaxWidth().padding(top = 20.dp).borderTop(1.dp, SoundistColors.Divider).padding(top = 16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("可用音色", color = SoundistColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text("点击试听", color = SoundistColors.TextMuted, fontSize = 10.sp)
                        }
                        Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf("真实乐器", "合成音色", "节奏", "人声纹理", "信号").forEach { family ->
                                val timbres = GENERATED_TIMBRES.filter { it.family == family }
                                if (timbres.isNotEmpty()) {
                                    Column {
                                        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(family, color = SoundistColors.TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.08.sp)
                                            Text("${timbres.size} 种", color = SoundistColors.TextMuted, fontSize = 9.sp)
                                        }
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            timbres.chunked(2).forEach { rowItems ->
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    rowItems.forEach { item ->
                                                        Row(
                                                            Modifier.weight(1f).heightIn(min = 56.dp).background(SoundistColors.DeepSea, RoundedCornerShape(8.dp))
                                                                .border(1.dp, SoundistColors.Divider, RoundedCornerShape(8.dp))
                                                                .clickable { dispatch(ListeningAction.AuditionGeneratorTimbre(item.id)) }
                                                                .padding(horizontal = 12.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                        ) {
                                                            Column(Modifier.weight(1f)) {
                                                                Text(item.label, color = SoundistColors.Text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                                Text(if (item.engine == "sample") "CC0 真实采样" else "程序合成", color = SoundistColors.TextMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 2.dp))
                                                            }
                                                            if (state.previewingTimbre == item.id) Icon(audioLines, null, Modifier.size(14.dp), tint = SoundistColors.TealSoft)
                                                            else Icon(play, null, Modifier.size(14.dp), tint = SoundistColors.TextMuted)
                                                        }
                                                    }
                                                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Text("这里完整列出当前引擎实际可播放的音色。钢琴、竖琴、长笛与弦乐来自 VSCO 2 CE；其余项目明确标记为程序合成，不以不存在的真实乐器能力命名。", color = SoundistColors.TextMuted, fontSize = 9.sp, lineHeight = 13.sp, modifier = Modifier.padding(top = 12.dp))
                    }
                    // Layers (5963–5969)
                    Column(Modifier.fillMaxWidth().padding(top = 20.dp).borderTop(1.dp, SoundistColors.Divider).padding(top = 16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("编排层", color = SoundistColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text("${layers.count { it.enabled }} 层启用", color = SoundistColors.TextMuted, fontSize = 10.sp)
                        }
                        Column(Modifier.fillMaxWidth().padding(top = 8.dp).borderY(1.dp, SoundistColors.Divider)) {
                            layers.forEachIndexed { index, layer ->
                                val isSelected = selectedLayer?.id == layer.id
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                        .then(if (index > 0) Modifier.borderTop(1.dp, SoundistColors.Divider) else Modifier)
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    GenCheckbox(layer.enabled, { updateLayer(layer.id) { it.copy(enabled = !it.enabled) } })
                                    Column(Modifier.weight(1f).heightIn(min = 44.dp).clickable { dispatch(ListeningAction.SelectGeneratorLayer(layer.id)) }, verticalArrangement = Arrangement.Center) {
                                        Text(layer.name, color = if (isSelected) SoundistColors.Text else SoundistColors.TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${generatorLayerTypeLabel(layer.type)} · ${timbreLabel(layer.timbre)}", color = SoundistColors.TextMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                                    }
                                    Text("${layer.volume}", color = SoundistColors.TextMuted, fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.width(28.dp))
                                    Box(
                                        Modifier.size(44.dp).clickable {
                                            updateLayer(layer.id) { it.copy(solo = !it.solo) }
                                            dispatch(ListeningAction.PreviewGeneratorDraft)
                                        },
                                        contentAlignment = Alignment.Center,
                                    ) { Icon(headphones, if (layer.solo) "取消独奏${layer.name}" else "独奏${layer.name}", Modifier.size(14.dp), tint = if (layer.solo) SoundistColors.TealSoft else SoundistColors.TextMuted) }
                                    if (isSelected) {
                                        Box(Modifier.size(width = 36.dp, height = 44.dp).alpha(if (index == 0) 0.2f else 1f).clickable(enabled = index > 0) { dispatch(ListeningAction.MoveGeneratorLayer(layer.id, -1)) }, contentAlignment = Alignment.Center) { Icon(chevronUp, "上移${layer.name}", Modifier.size(14.dp), tint = SoundistColors.TextMuted) }
                                        Box(Modifier.size(width = 36.dp, height = 44.dp).alpha(if (index == layers.lastIndex) 0.2f else 1f).clickable(enabled = index < layers.lastIndex) { dispatch(ListeningAction.MoveGeneratorLayer(layer.id, 1)) }, contentAlignment = Alignment.Center) { Icon(chevronDown, "下移${layer.name}", Modifier.size(14.dp), tint = SoundistColors.TextMuted) }
                                        Box(Modifier.size(width = 36.dp, height = 44.dp).clickable { dispatch(ListeningAction.DuplicateGeneratorLayer(layer.id)) }, contentAlignment = Alignment.Center) { Icon(plus, "复制${layer.name}", Modifier.size(14.dp), tint = SoundistColors.TextMuted) }
                                    }
                                    Box(Modifier.size(44.dp).alpha(if (layers.size <= 1) 0.25f else 1f).clickable(enabled = layers.size > 1) { dispatch(ListeningAction.RemoveGeneratorLayer(layer.id)) }, contentAlignment = Alignment.Center) { Icon(trash2, "删除${layer.name}", Modifier.size(14.dp), tint = SoundistColors.TextMuted) }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            GENERATOR_LAYER_TYPES.forEach { (type, label) ->
                                Box(Modifier.weight(1f).heightIn(min = 44.dp).border(1.dp, SoundistColors.Divider, RoundedCornerShape(6.dp)).clickable { dispatch(ListeningAction.AddGeneratorLayer(type)) }, contentAlignment = Alignment.Center) {
                                    Text("+$label", color = SoundistColors.TextSecondary, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                    // Selected layer editor (5971–5980)
                    if (selectedLayer != null) {
                        Column(Modifier.fillMaxWidth().padding(top = 20.dp).borderTop(1.dp, SoundistColors.Divider).padding(top = 16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GenField("元素名称", Modifier.weight(1f)) {
                                    GenInput(selectedLayer.name, { v -> updateLayer(selectedLayer.id) { it.copy(name = v) } }, minHeight = 44.dp)
                                }
                                GenField("实际音色", Modifier.width(144.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        GenSelect(
                                            selectedLayer.timbre,
                                            GENERATED_TIMBRES.filter { selectedLayer.type in it.layerTypes }.map { it.id to it.label },
                                            { v -> updateLayer(selectedLayer.id) { it.copy(timbre = normalizeGeneratedTimbre(v, it.type)) } },
                                            Modifier.weight(1f),
                                            minHeight = 44.dp,
                                            valueColor = SoundistColors.Text,
                                        )
                                        Box(Modifier.size(44.dp).border(1.dp, SoundistColors.Divider, RoundedCornerShape(8.dp)).clickable { dispatch(ListeningAction.AuditionGeneratorTimbre(selectedLayer.timbre)) }, contentAlignment = Alignment.Center) { Icon(play, "试听${timbreLabel(selectedLayer.timbre)}", Modifier.size(14.dp), tint = SoundistColors.TealSoft) }
                                    }
                                }
                            }
                            Text(GENERATED_TIMBRES.firstOrNull { it.id == selectedLayer.timbre }?.description ?: "", color = SoundistColors.TextMuted, fontSize = 10.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp))
                            Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    GenSliderField("音量", "${selectedLayer.volume}%", Modifier.weight(1f)) { m ->
                                        ArrangementCommitSlider(selectedLayer.volume.toFloat(), 0f..100f, m, key = selectedLayer.id, arrangement,
                                            update = { a, v -> a.copy(layers = a.layers.map { if (it.id == selectedLayer.id) it.copy(volume = v.roundToInt()) else it }) },
                                            dispatch = dispatch)
                                    }
                                    GenSliderField("声像", "${selectedLayer.pan}", Modifier.weight(1f)) { m ->
                                        ArrangementCommitSlider(selectedLayer.pan.toFloat(), -100f..100f, m, key = selectedLayer.id, arrangement,
                                            update = { a, v -> a.copy(layers = a.layers.map { if (it.id == selectedLayer.id) it.copy(pan = v.roundToInt()) else it }) },
                                            dispatch = dispatch)
                                    }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    GenSliderField("入场延迟", "${fmt(selectedLayer.entryDelaySeconds)}秒", Modifier.weight(1f)) { m ->
                                        ArrangementCommitSlider(selectedLayer.entryDelaySeconds, 0f..30f, m, key = selectedLayer.id, arrangement,
                                            update = { a, v -> a.copy(layers = a.layers.map { if (it.id == selectedLayer.id) it.copy(entryDelaySeconds = halfStep(v)) else it }) },
                                            dispatch = dispatch)
                                    }
                                    GenSliderField("持续时间", "${fmt(selectedLayer.durationSeconds)}秒", Modifier.weight(1f)) { m ->
                                        ArrangementCommitSlider(selectedLayer.durationSeconds, 0.5f..30f, m, key = selectedLayer.id, arrangement,
                                            update = { a, v -> a.copy(layers = a.layers.map { if (it.id == selectedLayer.id) it.copy(durationSeconds = halfStep(v)) else it }) },
                                            dispatch = dispatch)
                                    }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    GenSliderField("出现概率", "${selectedLayer.probability}%", Modifier.weight(1f)) { m ->
                                        ArrangementCommitSlider(selectedLayer.probability.toFloat(), 0f..100f, m, key = selectedLayer.id, arrangement,
                                            update = { a, v -> a.copy(layers = a.layers.map { if (it.id == selectedLayer.id) it.copy(probability = v.roundToInt()) else it }) },
                                            dispatch = dispatch)
                                    }
                                    GenSliderField("事件密度", "${selectedLayer.density}%", Modifier.weight(1f)) { m ->
                                        ArrangementCommitSlider(selectedLayer.density.toFloat(), 0f..100f, m, key = selectedLayer.id, arrangement,
                                            update = { a, v -> a.copy(layers = a.layers.map { if (it.id == selectedLayer.id) it.copy(density = v.roundToInt()) else it }) },
                                            dispatch = dispatch)
                                    }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    GenSliderField("淡入", "${fmt(selectedLayer.fadeInSeconds)}秒", Modifier.weight(1f)) { m ->
                                        ArrangementCommitSlider(selectedLayer.fadeInSeconds, 0f..12f, m, key = selectedLayer.id, arrangement,
                                            update = { a, v -> a.copy(layers = a.layers.map { if (it.id == selectedLayer.id) it.copy(fadeInSeconds = halfStep(v)) else it }) },
                                            dispatch = dispatch)
                                    }
                                    GenSliderField("淡出", "${fmt(selectedLayer.fadeOutSeconds)}秒", Modifier.weight(1f)) { m ->
                                        ArrangementCommitSlider(selectedLayer.fadeOutSeconds, 0f..12f, m, key = selectedLayer.id, arrangement,
                                            update = { a, v -> a.copy(layers = a.layers.map { if (it.id == selectedLayer.id) it.copy(fadeOutSeconds = halfStep(v)) else it }) },
                                            dispatch = dispatch)
                                    }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    GenSliderField("环境响应", "${selectedLayer.ambientResponse}%", Modifier.weight(1f)) { m ->
                                        ArrangementCommitSlider(selectedLayer.ambientResponse.toFloat(), 0f..100f, m, key = selectedLayer.id, arrangement,
                                            update = { a, v -> a.copy(layers = a.layers.map { if (it.id == selectedLayer.id) it.copy(ambientResponse = v.roundToInt()) else it }) },
                                            dispatch = dispatch)
                                    }
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GenField("节拍划分", Modifier.weight(1f)) {
                                    GenSelect(selectedLayer.rhythm, listOf("free", "whole", "half", "quarter", "eighth").map { it to (GENERATOR_OPTION_LABELS[it] ?: it) }, { v -> updateLayer(selectedLayer.id) { it.copy(rhythm = v) } })
                                }
                                GenField("音区", Modifier.weight(1f)) {
                                    GenSelect(selectedLayer.register, listOf("low", "middle", "high", "wide").map { it to (GENERATOR_OPTION_LABELS[it] ?: it) }, { v -> updateLayer(selectedLayer.id) { it.copy(register = v) } })
                                }
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GenField("音阶", Modifier.weight(1f)) {
                                    GenSelect(selectedLayer.scale, listOf("pentatonic", "major", "minor", "dorian").map { it to (GENERATOR_OPTION_LABELS[it] ?: it) }, { v -> updateLayer(selectedLayer.id) { it.copy(scale = v) } })
                                }
                                Spacer(Modifier.weight(1f))
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GenField("层间关系", Modifier.weight(1f)) {
                                    GenSelect(
                                        selectedLayer.relationship,
                                        listOf("independent" to "独立运行", "alternate" to "与目标交替", "avoid" to "避让目标", "follow" to "跟随目标"),
                                        { v ->
                                            val target = layers.firstOrNull { it.id != selectedLayer.id }?.id
                                            updateLayer(selectedLayer.id) { it.copy(relationship = v, relationshipTargetId = if (v == "independent") null else it.relationshipTargetId ?: target) }
                                        },
                                    )
                                }
                                if (selectedLayer.relationship != "independent") {
                                    GenField("关系目标", Modifier.weight(1f)) {
                                        GenSelect(selectedLayer.relationshipTargetId ?: "", layers.filter { it.id != selectedLayer.id }.map { it.id to it.name }, { v -> updateLayer(selectedLayer.id) { it.copy(relationshipTargetId = v) } })
                                    }
                                }
                            }
                        }
                    }
                }
                // Preview / save (5983–5990)
                Column(Modifier.fillMaxWidth().padding(top = 20.dp).borderTop(1.dp, SoundistColors.Divider).padding(top = 12.dp)) {
                    Text("试听时的实际声场", color = SoundistColors.TextSecondary, fontSize = 11.sp)
                    Text(
                        if (arrangement.ambientMode == "current") {
                            state.sounds.filter { it.active }.joinToString(" · ") { "${it.name} ${(it.volume * 100).roundToInt()}%" }.ifEmpty { "保留模式已开启，但当前声音页没有启用环境声" }
                        } else if (ambientTracks.isNotEmpty()) ambientTracks.joinToString(" · ") { "${it.name} ${it.volume}%" } else "当前没有绑定环境声，生成器按独立编排运行",
                        color = SoundistColors.TextMuted, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(top = 16.dp).background(SoundistColors.Warm.copy(alpha = 0.07f), RoundedCornerShape(8.dp))
                        .border(1.dp, SoundistColors.Warm.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .clickable { if (previewingSource) dispatch(ListeningAction.ToggleRadio) else dispatch(ListeningAction.PreviewGeneratorDraft) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(if (previewingActive) pause else play, null, Modifier.size(14.dp), tint = SoundistColors.Warm)
                    Spacer(Modifier.width(8.dp))
                    Text(if (previewingActive) "暂停试听" else "试听当前编排", color = SoundistColors.Warm, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GeneratorTransferButton(folderInput, "导入配置", Modifier.weight(1f)) {
                        importConfigurationLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    }
                    GeneratorTransferButton(share2, "分享配置", Modifier.weight(1f)) {
                        runCatching {
                            shareGeneratorConfiguration(
                                context,
                                GeneratorConfiguration(state.generatorDraftName, settings, arrangement),
                            )
                        }.onFailure { dispatch(ListeningAction.ShowNotice(it.message ?: "暂时无法分享编排配置")) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.width(108.dp).heightIn(min = 44.dp).border(1.dp, SoundistColors.Divider, RoundedCornerShape(8.dp)).clickable { dispatch(ListeningAction.RestoreGeneratorTemplate) }, contentAlignment = Alignment.Center) {
                        Text("恢复模板", color = SoundistColors.TextSecondary, fontSize = 12.sp)
                    }
                    Box(
                        Modifier.weight(1f).heightIn(min = 44.dp).background(SoundistColors.Teal.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .border(1.dp, SoundistColors.Teal.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                            .clickable { dispatch(ListeningAction.SaveGeneratedCopy) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(save, null, Modifier.size(14.dp), tint = SoundistColors.TealSoft)
                            Text(if (state.editingGeneratorRadioId != null) "保存频道修改" else "另存为我的频道", color = SoundistColors.TealSoft, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneratorTransferButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.heightIn(min = 44.dp).border(1.dp, SoundistColors.Divider, RoundedCornerShape(8.dp)).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(14.dp), tint = SoundistColors.TextMuted)
        Spacer(Modifier.width(6.dp))
        Text(label, color = SoundistColors.TextSecondary, fontSize = 11.sp)
    }
}

/** App.tsx 放弃编排修改确认 (5996–6008)。 */
@Composable
private fun GeneratorDiscardConfirm(dispatch: (ListeningAction) -> Unit) {
    AppOverlay(scrimAlpha = 0.70f, onDismiss = { dispatch(ListeningAction.CloseGeneratorDiscardConfirm) }) {
        Column(
            Modifier.widthIn(max = 390.dp).fillMaxWidth().background(SoundistColors.Raised, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .border(1.dp, SoundistColors.Divider, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Text("放弃本次编排修改？", color = SoundistColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("尚未保存的场景、音色、环境声轨和参数会被丢弃，当前正在播放的频道不会受影响。", color = SoundistColors.TextMuted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).heightIn(min = 44.dp).border(1.dp, SoundistColors.Divider, RoundedCornerShape(8.dp)).clickable { dispatch(ListeningAction.CloseGeneratorDiscardConfirm) }, contentAlignment = Alignment.Center) {
                    Text("继续编辑", color = SoundistColors.TextSecondary, fontSize = 12.sp)
                }
                Box(Modifier.weight(1f).heightIn(min = 44.dp).background(SoundistColors.Danger.copy(alpha = 0.1f), RoundedCornerShape(8.dp)).border(1.dp, SoundistColors.Danger.copy(alpha = 0.4f), RoundedCornerShape(8.dp)).clickable { dispatch(ListeningAction.CloseGeneratorEditor(true)) }, contentAlignment = Alignment.Center) {
                    Text("放弃修改", color = SoundistColors.Danger, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** App.tsx 添加/编辑私人频道 (6010–6100)。 */
@Composable
private fun CustomRadioForm(state: ListeningState, dispatch: (ListeningAction) -> Unit, artworkPicker: StationArtworkPicker?, audioPicker: StationAudioPicker?) {
    val scope = rememberCoroutineScope()
    val draft = state.radioDraft
    val canSave = !state.radioSaving && !(draft.sourceKind == RadioSourceKind.LOCAL && state.radioDraftAudio.any { it.validation == "checking" })
    var localAudioError by remember { mutableStateOf("") }
    val selectedBytes = state.radioDraftAudio.sumOf { it.sizeBytes }
    AppOverlay(scrimAlpha = 0.60f, onDismiss = { dispatch(ListeningAction.CloseCustomRadioEditor) }) {
        Column(
            Modifier.widthIn(max = 390.dp).fillMaxWidth().heightIn(max = maxPanelHeight())
                .background(SoundistColors.Raised, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .border(1.dp, SoundistColors.Divider, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 28.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(if (state.editingRadioId != null) "编辑私人频道" else "添加私人频道", color = SoundistColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("音频由你管理，不会上传到 Soundist 服务器", color = SoundistColors.TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                }
                Box(Modifier.size(44.dp).clickable { dispatch(ListeningAction.CloseCustomRadioEditor) }, contentAlignment = Alignment.Center) { Icon(x, "关闭自定义电台表单", Modifier.size(16.dp), tint = SoundistColors.TextMuted) }
            }
            Row(
                Modifier.fillMaxWidth().background(SoundistColors.DeepSea, RoundedCornerShape(8.dp)).border(1.dp, SoundistColors.Divider, RoundedCornerShape(8.dp)).padding(4.dp).padding(bottom = 0.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(RadioSourceKind.LOCAL to "本地音频", RadioSourceKind.STREAM to "直接音频流").forEach { (kind, label) ->
                    Box(
                        Modifier.weight(1f).heightIn(min = 40.dp).background(if (draft.sourceKind == kind) SoundistColors.RaisedStrong else Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable { dispatch(ListeningAction.UpdateRadioDraft { it.copy(sourceKind = kind) }) },
                        contentAlignment = Alignment.Center,
                    ) { Text(label, color = if (draft.sourceKind == kind) SoundistColors.Text else SoundistColors.TextMuted, fontSize = 12.sp) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).background(SoundistColors.RaisedStrong, RoundedCornerShape(12.dp))
                        .border(1.dp, SoundistColors.DividerStrong.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                        .clickable { if (artworkPicker != null) scope.launch { artworkPicker.pick(draft.imageUrl)?.let { uri -> dispatch(ListeningAction.UpdateRadioDraft { it.copy(imageUrl = uri) }) } } },
                    contentAlignment = Alignment.Center,
                ) { LocalArtworkOrMark(draft.imageUrl, draft.sourceKind, active = true) }
                Column(Modifier.weight(1f)) {
                    Text("自定义封面", color = SoundistColors.Text, fontSize = 12.sp)
                    Text("点击左侧方块选择图片；未上传时使用来源图标。", color = SoundistColors.TextMuted, fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (draft.sourceKind == RadioSourceKind.LOCAL) {
                    Column {
                        Text("本地播放列表 *", color = SoundistColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).background(SoundistColors.RaisedStrong, RoundedCornerShape(8.dp))
                                .border(1.dp, SoundistColors.Divider, RoundedCornerShape(8.dp))
                                .clickable {
                                    if (audioPicker != null) scope.launch {
                                        val picked = audioPicker.pickMultiple()
                                        if (picked.isNotEmpty()) {
                                            // App.tsx handleRadioAudio：80 文件上限 + inspectAudioFile 校验。
                                            if (state.radioDraftAudio.size + picked.size > 80) {
                                                localAudioError = "单个私人频道最多保留 80 个文件，请拆分为多个频道"
                                                return@launch
                                            }
                                            localAudioError = ""
                                            val items = picked.map { sel ->
                                                RadioDraftAudioItem(
                                                    id = sel.uri, fileName = sel.displayName, mimeType = sel.mimeType,
                                                    sizeBytes = sel.sizeBytes, durationSeconds = sel.durationSeconds,
                                                    validation = when {
                                                        !sel.mimeType.startsWith("audio/") -> "invalid"
                                                        sel.sizeBytes !in 1..MAX_LOCAL_AUDIO_BYTES -> "invalid"
                                                        sel.durationSeconds <= 0 -> "invalid"
                                                        else -> "ready"
                                                    },
                                                    validationMessage = when {
                                                        !sel.mimeType.startsWith("audio/") -> "文件类型不支持，请选择音频文件"
                                                        sel.sizeBytes <= 0 -> "空文件无法导入"
                                                        sel.sizeBytes > MAX_LOCAL_AUDIO_BYTES -> "文件超过 200MB 限制"
                                                        sel.durationSeconds <= 0 -> "无法解码的音频文件，请移除"
                                                        else -> ""
                                                    },
                                                )
                                            }
                                            dispatch(ListeningAction.AddRadioDraftAudio(items))
                                        }
                                    }
                                }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(upload, null, Modifier.size(16.dp), tint = SoundistColors.TealSoft)
                            Text(if (state.radioDraftAudio.isNotEmpty()) "继续添加音频 · 当前 ${state.radioDraftAudio.size} 首" else "选择一个或多个本地音频", color = SoundistColors.Text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Icon(plus, null, Modifier.size(14.dp), tint = SoundistColors.TextMuted)
                        }
                        Text("支持 MP3、M4A/AAC、WAV、OGG/Opus、FLAC、WebM。文件仅保存在当前设备，解码能力由系统决定。", color = SoundistColors.TextMuted, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 4.dp))
                        if (state.radioStorageLabel.isNotEmpty()) Text(state.radioStorageLabel, color = SoundistColors.TextMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
                        if (state.radioDraftAudio.isNotEmpty()) Text("已选 ${state.radioDraftAudio.size} 首 · 共 ${formatFileSize(selectedBytes)}", color = SoundistColors.TextMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
                        if (localAudioError.isNotEmpty()) Text(localAudioError, color = RoseText, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        if (state.radioDraftAudio.isNotEmpty()) {
                            Column(Modifier.fillMaxWidth().padding(top = 8.dp).background(SoundistColors.DeepSea, RoundedCornerShape(8.dp)).border(1.dp, SoundistColors.Divider, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp)) {
                                state.radioDraftAudio.forEachIndexed { index, item ->
                                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).border(if (index == state.radioDraftAudio.lastIndex) 0.dp else 1.dp, SoundistColors.Divider).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Column(Modifier.width(28.dp)) {
                                            Box(Modifier.width(28.dp).height(24.dp).clickable(enabled = index > 0) { dispatch(ListeningAction.MoveRadioDraftAudio(item.id, -1)) }, contentAlignment = Alignment.Center) { Icon(chevronUp, "上移${item.fileName}", Modifier.size(14.dp), tint = if (index == 0) SoundistColors.TextMuted.copy(alpha = 0.2f) else SoundistColors.TextMuted) }
                                            Box(Modifier.width(28.dp).height(24.dp).clickable(enabled = index < state.radioDraftAudio.lastIndex) { dispatch(ListeningAction.MoveRadioDraftAudio(item.id, 1)) }, contentAlignment = Alignment.Center) { Icon(chevronDown, "下移${item.fileName}", Modifier.size(14.dp), tint = if (index == state.radioDraftAudio.lastIndex) SoundistColors.TextMuted.copy(alpha = 0.2f) else SoundistColors.TextMuted) }
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text(item.fileName, color = SoundistColors.Text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            val metaColor = when (item.validation) { "invalid" -> ErrorText; "warning" -> AmberWarning; else -> SoundistColors.TextMuted }
                                            Text(if (item.validation == "checking") "正在检测…" else item.validationMessage.ifEmpty { "${formatAudioDuration(item.durationSeconds)} · ${formatFileSize(item.sizeBytes)}" }, color = metaColor, fontSize = 9.sp, modifier = Modifier.padding(top = 2.dp))
                                        }
                                        Box(Modifier.size(40.dp).clickable { dispatch(ListeningAction.RemoveRadioDraftAudio(item.id)) }, contentAlignment = Alignment.Center) { Icon(x, "移除${item.fileName}", Modifier.size(14.dp), tint = SoundistColors.TextMuted) }
                                    }
                                }
                            }
                        }
                        if (state.radioSaving) {
                            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                Box(Modifier.fillMaxWidth().height(4.dp).background(SoundistColors.RaisedStrong, RoundedCornerShape(50))) {
                                    Box(Modifier.fillMaxWidth((state.radioImportProgress / 100f).coerceIn(0f, 1f)).fillMaxHeight().background(SoundistColors.Teal, RoundedCornerShape(50)))
                                }
                                Text("正在保存到本地 ${state.radioImportProgress}%", color = SoundistColors.TextMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                } else {
                    Column {
                        Text("直接音频地址 *", color = SoundistColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
                        GenInput(draft.url, { dispatch(ListeningAction.UpdateRadioUrl(it)) }, minHeight = 44.dp, background = SoundistColors.RaisedStrong, placeholder = "https://example.com/live.mp3")
                        Text("支持 MP3/AAC/OGG、HLS(.m3u8) 与 Icecast 直播地址，播放器自动识别；请使用你有权播放的地址，视频网站页面不能作为后台音频。", color = SoundistColors.TextMuted, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Column {
                    Text("标题 *", color = SoundistColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
                    GenInput(draft.name, { v -> dispatch(ListeningAction.UpdateRadioDraft { it.copy(name = v) }) }, minHeight = 44.dp, background = SoundistColors.RaisedStrong, placeholder = "例如：深夜书房")
                }
                Column {
                    Text("介绍", color = SoundistColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
                    GenInput(draft.desc, { v -> dispatch(ListeningAction.UpdateRadioDraft { it.copy(desc = v) }) }, minHeight = 44.dp, background = SoundistColors.RaisedStrong, placeholder = "简单描述频道内容")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("类型", color = SoundistColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
                        GenInput(draft.genre, { v -> dispatch(ListeningAction.UpdateRadioDraft { it.copy(genre = v) }) }, minHeight = 44.dp, background = SoundistColors.RaisedStrong, placeholder = "冥想、古典等")
                    }
                    Box(
                        Modifier.heightIn(min = 44.dp).background(Color(0xFF183C36), RoundedCornerShape(8.dp))
                            .border(1.dp, SoundistColors.Teal.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp)
                            .alpha(if (canSave) 1f else 0.45f).clickable(enabled = canSave) { dispatch(ListeningAction.SaveCustomRadio) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(save, null, Modifier.size(14.dp), tint = SoundistColors.TealSoft)
                            Text(if (state.radioSaving) "保存中" else if (state.editingRadioId != null) "更新" else "保存", color = SoundistColors.TealSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (state.radioFormError.isNotEmpty()) Text(state.radioFormError, color = RoseText, fontSize = 12.sp)
            }
        }
    }
}

/** C8：删除频道确认弹窗（三选项）。默认高亮「删除频道并删除应用内副本」。 */
@Composable
private fun DeleteStationConfirm(state: ListeningState, dispatch: (ListeningAction) -> Unit) {
    val station = state.stations.firstOrNull { it.id == state.deleteStationConfirmId } ?: return
    AppOverlay(scrimAlpha = 0.60f, onDismiss = { dispatch(ListeningAction.CancelDeleteRadio) }) {
        Column(
            Modifier.widthIn(max = 390.dp).fillMaxWidth()
                .background(SoundistColors.Raised, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .border(1.dp, SoundistColors.Divider, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .padding(horizontal = 16.dp).padding(top = 20.dp, bottom = 28.dp),
        ) {
            Text("删除频道「${station.name}」？", color = SoundistColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "删除的是 Soundist 应用私有目录中的副本，不会删除你手机里的原始音频文件。",
                color = SoundistColors.TextMuted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp),
            )
            Box(
                Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(8.dp))
                    .background(SoundistColors.Teal.copy(alpha = .18f))
                    .border(1.dp, SoundistColors.Teal.copy(alpha = .4f), RoundedCornerShape(8.dp))
                    .clickable { dispatch(ListeningAction.ConfirmDeleteRadio(true)) }
                    .padding(horizontal = 12.dp, vertical = 13.dp),
            ) {
                Text("删除频道并删除应用内音频副本", color = SoundistColors.TealSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(
                Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(8.dp))
                    .border(1.dp, SoundistColors.Divider, RoundedCornerShape(8.dp))
                    .clickable { dispatch(ListeningAction.ConfirmDeleteRadio(false)) }
                    .padding(horizontal = 12.dp, vertical = 13.dp),
            ) {
                Text("仅删除频道，保留应用内音频副本", color = SoundistColors.Text, fontSize = 13.sp)
            }
            Box(
                Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(8.dp))
                    .clickable { dispatch(ListeningAction.CancelDeleteRadio) }
                    .padding(horizontal = 12.dp, vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("取消", color = SoundistColors.TextSecondary, fontSize = 13.sp)
            }
        }
    }
}

/** App.tsx 频道管理 (6168–6214)。 */
@Composable
private fun ManageChannels(state: ListeningState, dispatch: (ListeningAction) -> Unit) {
    val personalStations = state.stations.filter { it.custom }
    AppOverlay(scrimAlpha = 0.60f, onDismiss = { dispatch(ListeningAction.CloseManageChannels) }) {
        Column(
            Modifier.widthIn(max = 390.dp).fillMaxWidth().height(maxPanelHeight())
                .background(SoundistColors.Raised, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .border(1.dp, SoundistColors.Divider, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
        ) {
            Row(
                Modifier.fillMaxWidth().background(SoundistColors.Raised, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).borderBottom(1.dp, SoundistColors.Divider).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("管理我的频道", color = SoundistColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("长按手柄排序，点击频道编辑内容", color = SoundistColors.TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                }
                Box(Modifier.size(44.dp).clickable { dispatch(ListeningAction.CloseManageChannels) }, contentAlignment = Alignment.Center) { Icon(x, "关闭频道管理", Modifier.size(16.dp), tint = SoundistColors.TextMuted) }
            }
            val scrollState = rememberScrollState()
            val scrollScope = rememberCoroutineScope()
            var containerTop by remember { mutableStateOf(0f) }
            var containerBottom by remember { mutableStateOf(0f) }
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(scrollState)
                    .onGloballyPositioned { bounds ->
                        containerTop = bounds.boundsInWindow().top
                        containerBottom = bounds.boundsInWindow().bottom
                    }
                    .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            ) {
                personalStations.forEachIndexed { index, station ->
                    ManageChannelRow(station, index, personalStations.lastIndex, state, dispatch, scrollState, containerTop, containerBottom, scrollScope)
                }
                if (personalStations.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(listMusic, null, Modifier.size(24.dp), tint = SoundistColors.TextMuted)
                        Text("还没有私人频道", color = SoundistColors.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
                        Text("先导入本地音频、添加直接流，或保存一份生成编排。", color = SoundistColors.TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

/** App.tsx 频道管理行 (6183–6208)。 */
@Composable
private fun ManageChannelRow(
    station: RadioStation, index: Int, lastIndex: Int, state: ListeningState, dispatch: (ListeningAction) -> Unit,
    scrollState: ScrollState, containerTop: Float, containerBottom: Float, scrollScope: CoroutineScope,
) {
    val dragging = state.draggingRadioId == station.id
    Column(Modifier.fillMaxWidth().background(if (dragging) SoundistColors.RaisedStrong else Color.Transparent)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .border(if (index != lastIndex) 1.dp else 0.dp, SoundistColors.Divider.copy(alpha = 0.7f))
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChannelDragHandle(station, dispatch) { pointerY ->
                // App.tsx updateChannelDrag：距视口上/下边缘 56px 内以 ±18px 自动滚动。
                if (pointerY < containerTop + 56f) scrollScope.launch { scrollState.scrollBy(-18f) }
                if (pointerY > containerBottom - 56f) scrollScope.launch { scrollState.scrollBy(18f) }
            }
            Box(Modifier.size(36.dp).background(SoundistColors.RaisedStrong, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { RadioArtwork(station) }
            Column(Modifier.weight(1f).clickable { dispatch(ListeningAction.EditRadio(station)) }.padding(vertical = 4.dp)) {
                Text(station.name, color = SoundistColors.Text, fontSize = 12.sp)
                Text("${sourceLabel(station)} · ${station.genre}", color = SoundistColors.TextSecondary, fontSize = 11.sp)
            }
            Box(Modifier.size(width = 40.dp, height = 44.dp).clickable { dispatch(ListeningAction.PlayStation(station.id)) }, contentAlignment = Alignment.Center) { Icon(play, "播放${station.name}", Modifier.size(14.dp), tint = SoundistColors.Warm) }
            Box(Modifier.size(width = 36.dp, height = 44.dp)) {
                Box(Modifier.fillMaxSize().clickable { dispatch(ListeningAction.SetChannelMenuId(if (state.channelMenuId == station.id) null else station.id)) }, contentAlignment = Alignment.Center) { Icon(moreHorizontal, "${station.name}更多操作", Modifier.size(16.dp), tint = SoundistColors.TextMuted) }
                DropdownMenu(
                    expanded = state.channelMenuId == station.id,
                    onDismissRequest = { dispatch(ListeningAction.SetChannelMenuId(null)) },
                    containerColor = SoundistColors.RaisedStrong,
                    modifier = Modifier.width(144.dp).background(SoundistColors.RaisedStrong, RoundedCornerShape(8.dp)).border(1.dp, SoundistColors.DividerStrong, RoundedCornerShape(8.dp)),
                ) {
                    DropdownMenuItem(
                        text = { Text(if (station.sourceKind == RadioSourceKind.GENERATED) "打开编排" else "编辑信息", color = SoundistColors.Text, fontSize = 11.sp) },
                        onClick = { dispatch(ListeningAction.EditRadio(station)); dispatch(ListeningAction.SetChannelMenuId(null)) },
                    )
                    DropdownMenuItem(
                        text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(chevronUp, null, Modifier.size(14.dp), tint = SoundistColors.TextSecondary); Text("上移", color = SoundistColors.TextSecondary, fontSize = 11.sp) } },
                        enabled = index > 0,
                        onClick = { dispatch(ListeningAction.MoveCustomRadio(station.id, -1)); dispatch(ListeningAction.SetChannelMenuId(null)) },
                    )
                    DropdownMenuItem(
                        text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(chevronDown, null, Modifier.size(14.dp), tint = SoundistColors.TextSecondary); Text("下移", color = SoundistColors.TextSecondary, fontSize = 11.sp) } },
                        enabled = index < lastIndex,
                        onClick = { dispatch(ListeningAction.MoveCustomRadio(station.id, 1)); dispatch(ListeningAction.SetChannelMenuId(null)) },
                    )
                    if (station.custom) {
                        DropdownMenuItem(
                            text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(trash2, null, Modifier.size(14.dp), tint = ErrorText); Text("删除频道", color = ErrorText, fontSize = 11.sp) } },
                            onClick = { dispatch(ListeningAction.DeleteRadio(station.id)); dispatch(ListeningAction.SetChannelMenuId(null)) },
                        )
                    }
                }
            }
        }
    }
}

/** App.tsx 长按拖动手柄 (6184–6192)。beginChannelDrag 260ms 后 vibrate(10)；updateChannelDrag 边缘自动滚动；endChannelDrag showNotice("频道顺序已更新")。 */
@Composable
private fun ChannelDragHandle(station: RadioStation, dispatch: (ListeningAction) -> Unit, edgeScroll: (Float) -> Unit) {
    val haptic = LocalHapticFeedback.current
    var accum by remember(station.id) { mutableStateOf(0f) }
    var handleTop by remember(station.id) { mutableStateOf(0f) }
    Box(
        Modifier.size(width = 32.dp, height = 44.dp)
            .onGloballyPositioned { handleTop = it.boundsInWindow().top }
            .pointerInput(station.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        accum = 0f
                        dispatch(ListeningAction.SetChannelMenuId(null))
                        dispatch(ListeningAction.SetDraggingRadio(station.id))
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragEnd = {
                        accum = 0f
                        dispatch(ListeningAction.SetDraggingRadio(null))
                        dispatch(ListeningAction.ShowNotice("频道顺序已更新"))
                    },
                    onDragCancel = { accum = 0f; dispatch(ListeningAction.SetDraggingRadio(null)) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accum += dragAmount.y
                        val step = 64f
                        while (accum <= -step) { dispatch(ListeningAction.MoveCustomRadio(station.id, -1)); accum += step }
                        while (accum >= step) { dispatch(ListeningAction.MoveCustomRadio(station.id, 1)); accum -= step }
                        edgeScroll(handleTop + change.position.y)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) { Icon(gripVertical, "长按拖动${station.name}", Modifier.size(16.dp), tint = SoundistColors.TextMuted) }
}
