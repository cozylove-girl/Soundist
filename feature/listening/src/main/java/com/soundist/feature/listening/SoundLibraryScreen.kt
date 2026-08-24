package com.soundist.feature.listening

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundist.core.designsystem.SoundistColors
import com.soundist.core.designsystem.pause
import com.soundist.core.designsystem.play
import com.soundist.core.designsystem.heart
import com.soundist.core.designsystem.search
import com.soundist.core.designsystem.x
import kotlin.math.abs

private fun parsePath(d: String): List<PathNode> = PathParser().parsePathString(d).toNodes()

/** App.tsx Heart liked state: `fill-[#D8849B] text-[#D8849B]` — the lucide heart path filled + stroked #D8849B. */
private val heartFilled: ImageVector by lazy {
    ImageVector.Builder(
        name = "heart-filled",
        defaultWidth = 24.0.dp, defaultHeight = 24.0.dp,
        viewportWidth = 24.0f, viewportHeight = 24.0f,
    ).apply {
        addPath(
            pathData = parsePath("M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z"),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color(0xFFD8849B)),
            stroke = SolidColor(Color(0xFFD8849B)),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()
}

/** Literal Compose port of App.tsx renderSounds() (5559–5658) and SoundCard (1005–1084). */
@Composable
fun SoundLibraryScreen(state: ListeningState, dispatch: (ListeningAction) -> Unit, modifier: Modifier = Modifier) {
    val visible = state.visibleSounds()
    val active = state.sounds.filter { it.active }
    val generatedStation = state.stations.firstOrNull {
        it.id == state.selectedStationId && it.sourceKind == RadioSourceKind.GENERATED
    }
    val channelSession = generatedStation?.let { state.channelAmbientSessions[it.id] }
    val ambientMode = channelSession?.ambientMode ?: generatedStation?.generatorArrangement?.ambientMode
    Column(modifier.fillMaxSize().background(SoundistColors.Abyss)) {
        // Search (App.tsx 5567–5582)
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
            SoundSearchField(state.query, { dispatch(ListeningAction.SetQuery(it)) }, { dispatch(ListeningAction.SetQuery("")) })
        }
        // Scope + category filters (App.tsx 5585–5618)
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
            ScopeFilters(state.filter, dispatch)
            Spacer(Modifier.height(8.dp))
            CategoryFilters(state.filter, dispatch)
        }
        AmbientSourceIndicator(
            stationName = generatedStation?.name,
            channelMode = generatedStation != null && ambientMode != "current",
            adjusted = channelSession?.adjusted == true,
        )
        // Active mix strip (App.tsx 5621–5635)
        if (active.isNotEmpty()) ActiveMixStrip(active, state.ambientPlaying) { dispatch(ListeningAction.ToggleAmbient) }
        // Grid (App.tsx 5638–5655)
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
                Text("没有找到「${state.query}」", color = SoundistColors.TextMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 48.dp))
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visible, key = { it.id }) { SoundCard(it, dispatch) }
            }
        }
    }
}

@Composable
private fun AmbientSourceIndicator(stationName: String?, channelMode: Boolean, adjusted: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp).padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(5.dp).background(if (channelMode) SoundistColors.Teal else SoundistColors.TextMuted, CircleShape))
        Column(Modifier.weight(1f)) {
            Text(
                if (channelMode) "当前声场 · ${stationName.orEmpty()}" else "当前声场 · 我的环境声",
                color = SoundistColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                if (channelMode) "频道配方${if (adjusted) " · 已调整" else ""}" else "个人组合",
                color = if (channelMode) SoundistColors.Teal.copy(alpha = .72f) else SoundistColors.TextMuted,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun SoundSearchField(value: String, onValue: (String) -> Unit, onClear: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Row(
        // App.tsx focus-within:border-[var(--ambient)]/30 transition-colors.
        Modifier.fillMaxWidth().heightIn(min = 44.dp).background(SoundistColors.Raised, RoundedCornerShape(12.dp))
            .border(1.dp, if (isFocused) SoundistColors.Teal.copy(alpha = .3f) else SoundistColors.Divider, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(search, null, Modifier.size(16.dp), tint = SoundistColors.TextMuted)
        BasicTextField(
            value, onValue, Modifier.weight(1f), singleLine = true,
            interactionSource = interactionSource,
            textStyle = TextStyle(color = SoundistColors.Text, fontSize = 14.sp),
            decorationBox = { inner -> if (value.isBlank()) Text("搜索声音...", color = SoundistColors.TextMuted, fontSize = 14.sp); inner() },
        )
        if (value.isNotEmpty()) {
            Box(Modifier.size(44.dp).offset(x = 12.dp).clickable(onClick = onClear), contentAlignment = Alignment.Center) {
                Icon(x, "清除搜索", Modifier.size(16.dp), tint = SoundistColors.TextMuted)
            }
        }
    }
}

@Composable
private fun ScopeFilters(selected: SoundFilter, dispatch: (ListeningAction) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(SoundistColors.Raised, RoundedCornerShape(12.dp))
            .border(1.dp, SoundistColors.Divider, RoundedCornerShape(12.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(SoundFilter.ALL, SoundFilter.CURRENT, SoundFilter.FAVORITES).forEach { filter ->
            Box(
                Modifier.weight(1f).heightIn(min = 40.dp).background(if (selected == filter) SoundistColors.RaisedStrong else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { dispatch(ListeningAction.SetFilter(filter)) }.padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(filter.label(), color = if (selected == filter) SoundistColors.Text else SoundistColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun CategoryFilters(selected: SoundFilter, dispatch: (ListeningAction) -> Unit) {
    val categories = listOf(SoundFilter.NATURE, SoundFilter.RAIN, SoundFilter.ANIMALS, SoundFilter.URBAN, SoundFilter.PLACES, SoundFilter.TRANSPORT, SoundFilter.THINGS, SoundFilter.NOISE)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        categories.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { filter ->
                    val active = selected == filter
                    Box(
                        Modifier.weight(1f).heightIn(min = 40.dp)
                            .background(if (active) Color(0xFF183C36) else SoundistColors.DeepSea, RoundedCornerShape(8.dp))
                            .border(1.dp, if (active) SoundistColors.Teal.copy(alpha = .45f) else SoundistColors.Divider, RoundedCornerShape(8.dp))
                            .clickable { dispatch(ListeningAction.SetFilter(filter)) }.padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        // App.tsx 分类 chip：激活 font-semibold(600)，未激活默认 400。
                        Text(filter.label(), color = if (active) SoundistColors.TealSoft else SoundistColors.TextMuted, fontSize = 11.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ActiveMixStrip(active: List<AmbientSound>, playing: Boolean, onToggle: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)
            .drawBehind {
                val c = SoundistColors.Teal.copy(alpha = .12f)
                drawLine(c, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
                drawLine(c, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            }
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("当前混音 · ${active.size}", color = SoundistColors.Teal.copy(alpha = .6f), fontSize = 11.sp, letterSpacing = 1.1.sp)
            Text(
                active.take(2).joinToString("、") { it.name } + if (active.size > 2) "等 ${active.size} 个声源" else "",
                Modifier.weight(1f), color = SoundistColors.TextSecondary, fontSize = 12.sp,
            )
            Box(Modifier.size(44.dp).clickable(onClick = onToggle), contentAlignment = Alignment.Center) {
                Icon(if (playing) pause else play, if (playing) "暂停当前混音" else "播放当前混音", Modifier.size(14.dp), tint = SoundistColors.Teal.copy(alpha = .8f))
            }
        }
    }
}

@Composable
private fun SoundCard(sound: AmbientSound, dispatch: (ListeningAction) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val cardBackground = if (sound.active) Brush.linearGradient(listOf(Color(0xEB183C36), Color(0xFF161E21))) else Brush.linearGradient(listOf(Color(0xFF161E21), Color(0xFF161E21)))
    Box(Modifier.aspectRatio(1f).background(cardBackground, shape).border(1.dp, if (sound.active) SoundistColors.Teal.copy(alpha = .35f) else SoundistColors.DividerStrong.copy(alpha = .08f), shape).drawBehind {
        if (sound.active) {
            val inset = 0.5.dp.toPx()
            drawRoundRect(
                color = Color(0x1491D3C5),
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }) {
        // Toggle button (absolute inset-0, flex col, center, gap-1.5, px-2 pb-6)
        Column(
            Modifier.fillMaxSize().clickable { dispatch(ListeningAction.ToggleSound(sound.id)) }.padding(horizontal = 8.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            Box(Modifier.size(32.dp).background(if (sound.active) SoundistColors.Teal.copy(alpha = .15f) else SoundistColors.DividerStrong.copy(alpha = .06f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(soundIcon(sound.id), null, Modifier.size(16.dp), tint = if (sound.active) SoundistColors.Teal else SoundistColors.TextSecondary)
            }
            Text(sound.name, color = SoundistColors.Text, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 4.dp))
        }
        // Active dot (absolute top-2 left-2 w-1.5 h-1.5)
        if (sound.active) Box(
            Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 8.dp).size(6.dp).drawBehind {
                val r = size.minDimension / 2f
                val glowR = r * 3f
                drawCircle(Brush.radialGradient(listOf(SoundistColors.Teal.copy(alpha = .9f), Color.Transparent), center = center, radius = glowR), radius = glowR, center = center)
                drawCircle(SoundistColors.Teal, radius = r, center = center)
            },
        )
        // Keep the favorite hit area precise so it does not steal the card's primary toggle.
        val favoriteInteraction = remember { MutableInteractionSource() }
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 2.dp).size(30.dp).clickable(
                interactionSource = favoriteInteraction,
                indication = null,
            ) { dispatch(ListeningAction.ToggleFavorite(sound.id)) },
            contentAlignment = Alignment.TopCenter,
        ) {
            Icon(
                if (sound.favorite) heartFilled else heart,
                if (sound.favorite) "取消收藏${sound.name}" else "收藏${sound.name}",
                Modifier.size(14.dp),
                tint = if (sound.favorite) Color(0xFFD8849B) else SoundistColors.TextMuted.copy(alpha = .65f),
            )
        }
        // Mini volume slider (absolute left-3 right-3 bottom-3, opacity 0 when inactive)
        MiniSlider(
            value = if (sound.active) sound.volume else 0f,
            onValue = { dispatch(ListeningAction.SetSoundVolume(sound.id, it)) },
            onTap = { dispatch(ListeningAction.ToggleSound(sound.id)) },
            enabled = sound.active,
        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp).offset(y = 3.dp).height(34.dp).alpha(if (sound.active) 1f else 0f),
        )
    }
}

@Composable
private fun MiniSlider(
    value: Float,
    onValue: (Float) -> Unit,
    onTap: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var adjusting by remember { mutableStateOf(false) }
    val gestureModifier = if (!enabled) Modifier else Modifier.pointerInput(enabled) {
        val touchSlop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val thumb = 11.dp.toPx()
            fun fractionAt(x: Float) = ((x - thumb / 2f) / (size.width - thumb).coerceAtLeast(1f)).coerceIn(0f, 1f)
            var horizontalGesture = false
            var pendingValue = value
            var lastDispatchAt = 0L
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        if (horizontalGesture) {
                            change.consume()
                            onValue(pendingValue)
                        } else {
                            // Consume the completed tap before dispatching. Without this the parent
                            // card click also fires, producing two toggles and making deselect look broken.
                            change.consume()
                            onTap()
                        }
                        break
                    }
                    if (!horizontalGesture) {
                        val dx = change.position.x - down.position.x
                        val dy = change.position.y - down.position.y
                        if (abs(dy) > touchSlop && abs(dy) > abs(dx)) return@awaitEachGesture
                        if (abs(dx) <= touchSlop || abs(dx) <= abs(dy)) continue
                        horizontalGesture = true
                        adjusting = true
                    }
                    change.consume()
                    pendingValue = fractionAt(change.position.x)
                    val now = android.os.SystemClock.uptimeMillis()
                    if (now - lastDispatchAt >= 16L) {
                        lastDispatchAt = now
                        onValue(pendingValue)
                    }
                }
            } finally {
                adjusting = false
            }
        }
    }
    BoxWithConstraints(
        modifier.then(gestureModifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        val frac = value.coerceIn(0f, 1f)
        val trackH = if (adjusting) 4.dp else 3.dp
        Box(Modifier.fillMaxWidth().height(trackH).background(SoundistColors.DividerStrong.copy(alpha = .12f), CircleShape))
        Box(Modifier.fillMaxWidth(frac).height(trackH).background(SoundistColors.Teal, CircleShape))
        val baseThumbD = 11.dp
        val thumbD = if (adjusting) 13.dp else baseThumbD
        val blur = if (adjusting) 8.dp else 7.dp
        val centerX = baseThumbD / 2 + (maxWidth - baseThumbD) * frac
        val outerD = thumbD + blur * 2
        Box(
            Modifier.offset(x = centerX - outerD / 2).size(outerD).drawBehind {
                val glowA = if (adjusting) 0.52f else 0.45f
                val thumbR = thumbD.toPx() / 2f
                val outerR = thumbR + blur.toPx()
                val solidStop = (thumbR / outerR).coerceIn(0f, 1f)
                drawCircle(
                    Brush.radialGradient(
                        colorStops = arrayOf(0f to SoundistColors.Teal.copy(alpha = glowA), solidStop to SoundistColors.Teal.copy(alpha = glowA), 1f to Color.Transparent),
                        center = center,
                        radius = outerR,
                    ),
                    radius = outerR,
                    center = center,
                )
                drawCircle(Color(0xFF91D3C5), radius = thumbR, center = center)
                val borderWidth = 1.dp.toPx()
                drawCircle(Color(0xCC080B0D), radius = thumbR - borderWidth / 2f, center = center, style = Stroke(width = borderWidth))
            },
        )
    }
}
