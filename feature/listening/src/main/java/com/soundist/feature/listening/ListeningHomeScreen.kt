package com.soundist.feature.listening

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import com.soundist.core.designsystem.SoundistColors
import com.soundist.core.designsystem.SoundistSelect
import com.soundist.core.designsystem.arrowLeft
import com.soundist.core.designsystem.folderInput
import com.soundist.core.designsystem.pause
import com.soundist.core.designsystem.play
import com.soundist.core.designsystem.radio
import com.soundist.core.designsystem.waves
import com.soundist.core.designsystem.chevronDown
import com.soundist.core.designsystem.chevronUp
import com.soundist.core.designsystem.listMusic
import com.soundist.core.designsystem.shuffle
import com.soundist.core.designsystem.trash2
import com.soundist.core.designsystem.x

/** App.tsx renderHome() (lines 5125–5556), ported 1:1. */
@Composable
fun ListeningHomeScreen(
    state: ListeningState,
    dispatch: (ListeningAction) -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    animationScale: Float = 1f,
    onOpenSounds: () -> Unit = {},
) {
    var presetDialog by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }
    var presetPurpose by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val activeSounds = state.sounds.filter { it.active }
    val anyPlayback = state.ambientPlaying || state.radioPlayback.isRadioActive
    val anyPlaybackRequested = anyPlayback || state.radioPlayback == PlaybackState.LOADING
    val radioActive = state.radioPlayback.isRadioActive || state.radioPlayback == PlaybackState.LOADING
    val subtitle = when {
        state.ambientPlaying -> "环境声播放中"
        state.radioPlayback.isRadioActive -> "电台播放中"
        state.radioPlayback == PlaybackState.LOADING -> "电台载入中"
        else -> "已暂停"
    }
    LaunchedEffect(state.highlightedSoundId, state.mixerExpanded) {
        val target = activeSounds.indexOfFirst { it.id == state.highlightedSoundId }
        if (state.mixerExpanded && target >= 0) listState.animateScrollToItem(8 + target)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 32.dp),
        state = listState,
    ) {
        // Soundscape title — text-center pt-2 pb-5
        item {
            Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("当前声场", color = SoundistColors.TextMuted, fontSize = 11.sp, lineHeight = 16.5.sp, letterSpacing = 2.75.sp, modifier = Modifier.padding(bottom = 4.dp))
                Text(state.sceneName, color = SoundistColors.Text, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif)
                Text("${activeSounds.size} 个声源 · $subtitle", color = SoundistColors.TextSecondary, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }

        // Logo Orb with stardust — flex justify-center mb-5, inner w-56 h-56
        item {
            Box(Modifier.fillMaxWidth().padding(bottom = 20.dp), contentAlignment = Alignment.Center) {
                DeepSeaCanvas(
                    state,
                    onSoundClick = { dispatch(ListeningAction.HighlightSound(it)) },
                    modifier = Modifier.size(224.dp),
                    reduceMotion = reduceMotion,
                )
            }
        }

        // Play / Pause buttons — gap-6 mb-5
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                HomePlayButton(
                    size = 48.dp, borderWidth = 1.dp, iconSize = 16.dp, shadowElevation = 14.dp,
                    active = state.ambientPlaying,
                    activeBackground = SolidColor(Color(0xB8183C36)),
                    inactiveBackground = SolidColor(Color(0xE0161E21)),
                    activeBorder = Color(0x7A55B6A3),
                    inactiveBorder = Color(0x9E43565A),
                    glowColor = Color(0x1A55B6A3),
                    icon = waves,
                    activeIconColor = Color(0xBF55B6A3),
                    inactiveIconColor = SoundistColors.TextSecondary,
                    activeDescription = "暂停环境声",
                    inactiveDescription = "播放环境声",
                    onClick = { dispatch(ListeningAction.ToggleAmbient) },
                )
                HomePlayButton(
                    size = 62.dp, borderWidth = 1.5.dp, iconSize = 24.dp, shadowElevation = 20.dp,
                    active = anyPlayback,
                    activeBackground = Brush.linearGradient(listOf(Color(0xFA1E282B), Color(0xF5161E21))),
                    inactiveBackground = SolidColor(Color(0xE0161E21)),
                    activeBorder = Color(0x9EA9B3AF),
                    inactiveBorder = Color(0xAD43565A),
                    glowColor = Color(0x47000000),
                    icon = if (anyPlaybackRequested) pause else play,
                    activeIconColor = SoundistColors.Text,
                    inactiveIconColor = SoundistColors.TextSecondary,
                    activeDescription = "全部暂停",
                    inactiveDescription = "全部播放",
                    iconOffsetX = if (anyPlaybackRequested) 0.dp else 2.dp,
                    iconTint = if (anyPlaybackRequested) SoundistColors.Text else SoundistColors.TextSecondary,
                    onClick = { dispatch(ListeningAction.ToggleGlobal) },
                )
                HomePlayButton(
                    size = 48.dp, borderWidth = 1.dp, iconSize = 16.dp, shadowElevation = 14.dp,
                    active = radioActive,
                    activeBackground = SolidColor(Color(0xB843301F)),
                    inactiveBackground = SolidColor(Color(0xE0161E21)),
                    activeBorder = Color(0x85C99662),
                    inactiveBorder = Color(0x9E43565A),
                    glowColor = Color(0x1AC99662),
                    icon = radio,
                    activeIconColor = SoundistColors.Warm,
                    inactiveIconColor = SoundistColors.TextSecondary,
                    activeDescription = "暂停电台",
                    inactiveDescription = "播放电台",
                    onClick = { dispatch(ListeningAction.ToggleRadio) },
                )
            }
        }

        // Master volume strip — grid-cols-2 gap-2 mb-5
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MasterStripButton(shuffle, "随机环境声") { dispatch(ListeningAction.RandomizeScene) }
                MasterStripButton(listMusic, "环境声预设") { presetDialog = !presetDialog; if (state.presetManagerOpen) dispatch(ListeningAction.TogglePresetManager) }
            }
        }

        // Quick sound scenes — 快捷声场
        item {
            Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("快捷声场", color = SoundistColors.TextMuted, fontSize = 11.sp, letterSpacing = 1.76.sp)
                    Box(Modifier.heightIn(min = 40.dp).clickable { presetDialog = false; if (!state.presetManagerOpen) dispatch(ListeningAction.TogglePresetManager) }.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                        Text("管理", color = SoundistColors.TealSoft, fontSize = 11.sp)
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(end = 40.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.presets.forEach { preset ->
                        val previewSounds = preset.tracks.keys.mapNotNull { id -> state.sounds.firstOrNull { it.id == id } }
                        QuickSceneCard(preset.name, presetPurposeLabel(preset), previewSounds, preset.tracks.size, state.sceneName == preset.name) { dispatch(ListeningAction.ApplyPreset(preset.id)) }
                    }
                }
            }
        }

        // Volume card — rounded-xl border bg-surface px-3.5 py-3.5 mb-5
        item {
            Box(Modifier.fillMaxWidth().padding(bottom = 20.dp)) { HomeVolumeCard(state, dispatch) }
        }

        // Divider — border-t mb-4
        item {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(SoundistColors.Divider))
                Spacer(Modifier.height(16.dp))
            }
        }

        // Mixer section header — mb-3
        item {
            MixerHeader(state, activeSounds.size, dispatch)
        }

        // Mixer expanded content — space-y-2.5, wrapped by mb-4 on the section
        if (state.mixerExpanded) {
            if (activeSounds.isEmpty()) {
                item { Box(Modifier.padding(bottom = 10.dp)) { EmptyMixer(onOpenSounds) } }
            } else {
                items(activeSounds, key = { it.id }) { sound ->
                    Box(Modifier.padding(bottom = 10.dp)) { LiteralMixerRow(sound, sound.id == state.highlightedSoundId, dispatch) }
                }
            }
            item { Box(Modifier.padding(bottom = 16.dp)) { AddSoundButton(onOpenSounds) } }
        }

        // Share — border-t pt-3
        item { ShareCurrentScene(state, dispatch) }
    }

    if (presetDialog) {
        SavePresetSheet(presetName, { presetName = it }, presetPurpose, { presetPurpose = it.take(6) }, activeSounds.size, { presetDialog = false }) {
            dispatch(ListeningAction.SavePresetWithPurpose(presetName, presetPurpose)); presetDialog = false; presetName = ""; presetPurpose = ""
        }
    }
    if (state.presetManagerOpen) PresetManager(state, dispatch)
    state.operationError?.let { message ->
        AlertDialog(onDismissRequest = { dispatch(ListeningAction.ClearError) }, title = { Text("未能完成操作", color = SoundistColors.Text) }, text = { Text(message, color = SoundistColors.TextSecondary) }, confirmButton = { TextButton(onClick = { dispatch(ListeningAction.ClearError) }) { Text("知道了", color = SoundistColors.Text) } })
    }
}

@Composable
private fun HomePlayButton(
    size: Dp,
    borderWidth: Dp,
    iconSize: Dp,
    shadowElevation: Dp,
    active: Boolean,
    activeBackground: Brush,
    inactiveBackground: Brush,
    activeBorder: Color,
    inactiveBorder: Color,
    glowColor: Color,
    icon: ImageVector,
    activeIconColor: Color,
    inactiveIconColor: Color,
    activeDescription: String,
    inactiveDescription: String,
    iconOffsetX: Dp = 0.dp,
    iconTint: Color? = null,
    onClick: () -> Unit,
) {
    val shape = CircleShape
    Box(
        Modifier.size(size)
            .shadow(if (active) shadowElevation else 0.dp, shape, clip = true, ambientColor = glowColor, spotColor = glowColor)
            .background(if (active) activeBackground else inactiveBackground, shape)
            .border(borderWidth, if (active) activeBorder else inactiveBorder, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, if (active) activeDescription else inactiveDescription, Modifier.size(iconSize).offset(x = iconOffsetX), tint = iconTint ?: (if (active) activeIconColor else inactiveIconColor))
    }
}

@Composable
private fun RowScope.MasterStripButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
            .background(SoundistColors.Raised)
            .border(1.dp, SoundistColors.Divider, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(14.dp), tint = SoundistColors.TealSoft)
        Spacer(Modifier.width(6.dp))
        Text(label, color = SoundistColors.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp)
    }
}

@Composable
private fun QuickSceneCard(name: String, purpose: String, previewSounds: List<AmbientSound>, soundCount: Int, active: Boolean, onClick: () -> Unit) {
    val borderColor = if (active) SoundistColors.Teal.copy(alpha = .35f) else SoundistColors.Divider
    val background = if (active) SoundistColors.Teal.copy(alpha = .08f) else SoundistColors.Raised
    // App.tsx active:scale-[0.98].
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Column(
        Modifier.width(148.dp).heightIn(min = 82.dp).scale(if (pressed) .98f else 1f).clip(RoundedCornerShape(8.dp)).background(background)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(name, color = if (active) SoundistColors.Text else SoundistColors.TextSecondary, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Serif, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(SoundistColors.RaisedStrong).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(purpose, color = SoundistColors.TextMuted, fontSize = 9.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            previewSounds.take(3).forEach { sound ->
                Box(Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(SoundistColors.RaisedStrong), contentAlignment = Alignment.Center) {
                    Icon(soundIcon(sound.id), null, Modifier.size(12.dp), tint = SoundistColors.TealSoft)
                }
            }
            Spacer(Modifier.weight(1f))
            Text("$soundCount 声源", color = SoundistColors.TextMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun MixerHeader(state: ListeningState, activeCount: Int, dispatch: (ListeningAction) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { dispatch(ListeningAction.ToggleMixer) }.padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("混音台 · $activeCount 声源", color = SoundistColors.TextMuted, fontSize = 11.sp, letterSpacing = 2.2.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (state.mixerExpanded) "收起" else "展开", color = SoundistColors.TextMuted, fontSize = 11.sp)
            Icon(if (state.mixerExpanded) chevronUp else chevronDown, null, Modifier.size(14.dp), tint = SoundistColors.TextMuted)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavePresetSheet(name: String, onName: (String) -> Unit, purpose: String, onPurpose: (String) -> Unit, activeCount: Int, onDismiss: () -> Unit, onSave: () -> Unit) {
    // App.tsx rounded-t-2xl → 16dp top corners (ModalBottomSheet default is 28dp).
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SoundistColors.Raised, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 28.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("保存环境声预设", color = SoundistColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("当前 $activeCount 个声源将保存到快捷声场", color = SoundistColors.TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                }
                Box(Modifier.size(44.dp).offset(x = 8.dp).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Icon(x, "关闭保存预设", Modifier.size(16.dp), tint = SoundistColors.TextMuted)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SheetTextInput(name, onName, "输入预设标题", Modifier.weight(1f))
                Box(Modifier.heightIn(min = 44.dp).background(SoundistColors.Teal, RoundedCornerShape(12.dp)).clickable(onClick = onSave).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    Text("保存", color = SoundistColors.Abyss, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("用途标签（可选）", color = SoundistColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
                SheetTextInput(purpose, onPurpose, "留空则自动判断：${inferPresetPurposeLabel(name)}", Modifier.fillMaxWidth())
                Text("可输入冥想、阅读、午休等短标签，清空后恢复自动判断", color = SoundistColors.TextMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun SheetTextInput(value: String, onValue: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    Box(
        modifier.heightIn(min = 44.dp).background(SoundistColors.RaisedStrong, RoundedCornerShape(12.dp))
            .border(1.dp, SoundistColors.Divider, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value, onValue, Modifier.fillMaxWidth(), singleLine = true,
            textStyle = TextStyle(color = SoundistColors.Text, fontSize = 14.sp),
            cursorBrush = SolidColor(SoundistColors.Text),
            decorationBox = { inner -> if (value.isEmpty()) Text(placeholder, color = SoundistColors.TextMuted, fontSize = 14.sp); inner() },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetManager(state: ListeningState, dispatch: (ListeningAction) -> Unit) {
    // App.tsx editingPresetId — kept as a composable-local draft so the editor stays self-contained.
    var editing by remember { mutableStateOf<SoundPreset?>(null) }
    var duplicateSourceId by remember { mutableStateOf<String?>(null) }
    val currentState by rememberUpdatedState(state)

    // App.tsx duplicatePreset: [copy, ...list] + setEditingPresetId(copy.id) → enter edit on the copy.
    LaunchedEffect(duplicateSourceId) {
        val sourceId = duplicateSourceId ?: return@LaunchedEffect
        val before = currentState.presets.map { it.id }.toSet()
        dispatch(ListeningAction.DuplicatePreset(sourceId))
        withTimeoutOrNull(2000) {
            snapshotFlow { currentState.presets.map { it.id }.toSet() }
                .filter { ids -> ids.size == before.size + 1 && ids.any { it !in before } }
                .first()
        }
        val newId = currentState.presets.map { it.id }.firstOrNull { it !in before }
        if (newId != null) editing = currentState.presets.first { it.id == newId }
        duplicateSourceId = null
    }

    // App.tsx edits live-update quickPresets; here every edit dispatches UpdatePreset immediately.
    fun persist(updated: SoundPreset) {
        editing = updated
        dispatch(ListeningAction.UpdatePreset(updated))
    }
    fun moveTrack(soundId: String, delta: Int) {
        val current = editing ?: return
        val list = current.tracks.toList()
        val index = list.indexOfFirst { it.first == soundId }
        val target = index + delta
        if (index < 0 || target < 0 || target >= list.size) return
        val next = list.toMutableList()
        val tmp = next[index]; next[index] = next[target]; next[target] = tmp
        persist(current.copy(tracks = next.toMap()))
    }
    fun updateFromCurrent() {
        val current = editing ?: return
        val tracks = state.sounds.filter { it.active }.associate { it.id to it.volume }
        if (tracks.isEmpty()) { dispatch(ListeningAction.ShowNotice("当前没有可写入的声源")); return }
        persist(current.copy(tracks = tracks))
        dispatch(ListeningAction.ShowNotice("已用当前混音更新预设"))
    }
    fun applyAndFinish() {
        val current = editing ?: return
        dispatch(ListeningAction.ApplyPreset(current.id))
        editing = null
    }

    ModalBottomSheet(
        onDismissRequest = { dispatch(ListeningAction.TogglePresetManager); editing = null; duplicateSourceId = null },
        containerColor = SoundistColors.Raised,
        // App.tsx rounded-t-2xl → 16dp top corners.
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            // ── Header (App.tsx 5348–5356) ──
            if (editing != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(44.dp).clickable { editing = null }, contentAlignment = Alignment.Center) {
                        Icon(arrowLeft, "返回预设列表", Modifier.size(16.dp), tint = SoundistColors.TextSecondary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("编辑快捷声场", color = SoundistColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("调整声源、音量、用途与顺序", color = SoundistColors.TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    Box(Modifier.size(44.dp).clickable { dispatch(ListeningAction.TogglePresetManager); editing = null; duplicateSourceId = null }, contentAlignment = Alignment.Center) {
                        Icon(x, "关闭预设管理", Modifier.size(16.dp), tint = SoundistColors.TextMuted)
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text("环境声预设", color = SoundistColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("应用、排序或管理快捷声场", color = SoundistColors.TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    Box(Modifier.size(44.dp).clickable { dispatch(ListeningAction.TogglePresetManager) }, contentAlignment = Alignment.Center) {
                        Icon(x, "关闭预设管理", Modifier.size(16.dp), tint = SoundistColors.TextMuted)
                    }
                }
            }

            val preset = editing
            if (preset != null) {
                if (preset.builtIn) {
                    // ── Builtin view (App.tsx 5361–5397, read-only) ──
                    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(preset.name, color = SoundistColors.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("内置声场 · 复制后可编辑", color = SoundistColors.TextMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                            }
                            Box(
                                Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, SoundistColors.Teal.copy(alpha = .25f), RoundedCornerShape(8.dp))
                                    .clickable { duplicateSourceId = preset.id; dispatch(ListeningAction.DuplicatePreset(preset.id)) }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text("复制编辑", color = SoundistColors.TealSoft, fontSize = 12.sp) }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 44.dp)
                                .background(SoundistColors.RaisedStrong, RoundedCornerShape(8.dp))
                                .border(1.dp, SoundistColors.Divider, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("用途标签", color = SoundistColors.TextSecondary, fontSize = 11.sp)
                            Text("${presetPurposeLabel(preset)} · 自动", color = SoundistColors.Text, fontSize = 12.sp)
                        }
                        Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            preset.tracks.toList().forEach { (id, volume) ->
                                val sound = state.sounds.firstOrNull { it.id == id }
                                Column(
                                    Modifier.fillMaxWidth()
                                        .background(SoundistColors.RaisedStrong, RoundedCornerShape(8.dp))
                                        .border(1.dp, SoundistColors.Divider, RoundedCornerShape(8.dp)).padding(12.dp),
                                ) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(SoundistColors.Raised), contentAlignment = Alignment.Center) {
                                            if (sound != null) Icon(soundIcon(sound.id), null, Modifier.size(16.dp), tint = SoundistColors.TealSoft)
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(sound?.name ?: id, color = SoundistColors.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                        Text("${(volume * 100).toInt()}%", color = SoundistColors.TealSoft, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                    }
                                    DisabledVolumeBar(volume, Modifier.padding(top = 8.dp))
                                }
                            }
                        }
                    }
                } else {
                    // ── Custom editor (App.tsx 5361–5401, editable) ──
                    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text("预设名称", color = SoundistColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
                        SheetTextInput(preset.name, { persist(preset.copy(name = it.take(32))) }, "预设名称", Modifier.fillMaxWidth())
                        Text("用途标签", color = SoundistColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                        SheetTextInput(preset.purposeLabel, { persist(preset.copy(purposeLabel = it.take(6))) }, "留空则自动判断：${inferPresetPurposeLabel(preset.name)}", Modifier.fillMaxWidth())
                        Text("当前：${presetPurposeLabel(preset)} · ${if (preset.purposeLabel.isNotBlank()) "自定义" else "自动判断"}", color = SoundistColors.TextMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                        LazyColumn(Modifier.fillMaxWidth().height(300.dp).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(preset.tracks.toList(), key = { _, pair -> pair.first }) { index, (id, volume) ->
                                val sound = state.sounds.firstOrNull { it.id == id }
                                Column(
                                    Modifier.fillMaxWidth()
                                        .background(SoundistColors.RaisedStrong, RoundedCornerShape(8.dp))
                                        .border(1.dp, SoundistColors.Divider, RoundedCornerShape(8.dp)).padding(12.dp),
                                ) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(SoundistColors.Raised), contentAlignment = Alignment.Center) {
                                            if (sound != null) Icon(soundIcon(sound.id), null, Modifier.size(16.dp), tint = SoundistColors.TealSoft)
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(sound?.name ?: id, color = SoundistColors.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                        Text("${(volume * 100).toInt()}%", color = SoundistColors.TealSoft, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                        Spacer(Modifier.width(4.dp))
                                        IconButton({ moveTrack(id, -1) }, enabled = index > 0) { Icon(chevronUp, "上移${sound?.name ?: id}", Modifier.size(16.dp), tint = SoundistColors.TextMuted) }
                                        IconButton({ moveTrack(id, 1) }, enabled = index < preset.tracks.size - 1) { Icon(chevronDown, "下移${sound?.name ?: id}", Modifier.size(16.dp), tint = SoundistColors.TextMuted) }
                                    }
                                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        VolumeSlider(volume, Modifier.weight(1f)) { v -> persist(preset.copy(tracks = preset.tracks + (id to v))) }
                                        IconButton({ persist(preset.copy(tracks = preset.tracks - id)) }) { Icon(trash2, "从预设移除${sound?.name ?: id}", Modifier.size(16.dp), tint = SoundistColors.TextMuted) }
                                    }
                                }
                            }
                        }
                        // 添加声源 (App.tsx 5399): 默认 volume 40 (.4f)
                        Text("添加声源", color = SoundistColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                        val unadded = state.sounds.filter { it.id !in preset.tracks }
                        val placeholder = if (unadded.isEmpty()) "已包含全部声源" else "选择一个未加入的声源"
                        SoundistSelect(
                            value = "",
                            options = listOf("" to placeholder) + unadded.map { it.id to it.name },
                            onSelect = { soundId ->
                                if (soundId.isNotEmpty()) persist(preset.copy(tracks = preset.tracks + (soundId to .4f)))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            minHeight = 44.dp,
                            background = SoundistColors.RaisedStrong,
                            valueColor = SoundistColors.TextMuted,
                            fontSize = 12.sp,
                        )
                        // 用当前混音更新 + 应用并完成 (App.tsx 5400)
                        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp))
                                .border(1.dp, SoundistColors.Divider, RoundedCornerShape(8.dp)).clickable { updateFromCurrent() }, contentAlignment = Alignment.Center) {
                                Text("用当前混音更新", color = SoundistColors.TextSecondary, fontSize = 12.sp)
                            }
                            Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp))
                                .background(SoundistColors.Teal).clickable { applyAndFinish() }, contentAlignment = Alignment.Center) {
                                Text("应用并完成", color = SoundistColors.Abyss, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            } else {
                // ── List view (App.tsx 5402–5410) ──
                LazyColumn {
                    itemsIndexed(state.presets, key = { _, item -> item.id }) { index, item ->
                        Column(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, color = SoundistColors.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${item.tracks.size} 个声源 · ${presetPurposeLabel(item)}${if (item.purposeLabel.isNotBlank()) "（自定义）" else "（自动）"}", color = SoundistColors.TextMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                                }
                                IconButton({ dispatch(ListeningAction.MovePreset(item.id, -1)) }, enabled = index > 0) { Icon(chevronUp, "上移${item.name}", Modifier.size(14.dp), tint = SoundistColors.TextMuted) }
                                IconButton({ dispatch(ListeningAction.MovePreset(item.id, 1)) }, enabled = index < state.presets.lastIndex) { Icon(chevronDown, "下移${item.name}", Modifier.size(14.dp), tint = SoundistColors.TextMuted) }
                                TextButton({ editing = item }) { Text(if (item.builtIn) "查看" else "编辑", color = SoundistColors.TealSoft, fontSize = 11.sp) }
                                IconButton({ if (item.builtIn) { duplicateSourceId = item.id; dispatch(ListeningAction.DuplicatePreset(item.id)) } else dispatch(ListeningAction.DeletePreset(item.id)) }) {
                                    Icon(if (item.builtIn) folderInput else trash2, if (item.builtIn) "复制${item.name}" else "删除${item.name}", Modifier.size(14.dp), tint = SoundistColors.TextMuted)
                                }
                            }
                            if (index < state.presets.lastIndex) HorizontalDivider(color = SoundistColors.Divider.copy(alpha = .7f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Disabled preset-track slider (App.tsx SliderTrack disabled:opacity-60) — static 2px track + 14px thumb at 60%. */
@Composable
private fun DisabledVolumeBar(value: Float, modifier: Modifier = Modifier) {
    Box(modifier.height(24.dp).alpha(.6f).drawBehind {
        val frac = value.coerceIn(0f, 1f)
        val cy = size.height / 2f
        val trackH = 2.dp.toPx()
        drawLine(Color(0x1A43565A), Offset(0f, cy), Offset(size.width, cy), strokeWidth = trackH, cap = StrokeCap.Round)
        drawLine(SoundistColors.Teal, Offset(0f, cy), Offset(size.width * frac, cy), strokeWidth = trackH, cap = StrokeCap.Round)
        drawCircle(SoundistColors.Teal, radius = 7.dp.toPx(), center = Offset(size.width * frac, cy))
        drawCircle(Color(0xB3080B0D), radius = 7.dp.toPx(), center = Offset(size.width * frac, cy), style = Stroke(width = 1.5.dp.toPx()))
    })
}
