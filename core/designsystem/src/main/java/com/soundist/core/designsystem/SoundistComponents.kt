package com.soundist.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

@Composable
fun SectionTitle(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = SoundistColors.Text)
        if (action != null && onAction != null) {
            Text(
                action,
                color = SoundistColors.TealSoft,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable(onClick = onAction).padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
fun SoundistChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // App.tsx 8043-8044：全局 button transition 180ms cubic-bezier(0.22,1,0.36,1) → 换色过渡。
    val transitionSpec = tween<androidx.compose.ui.graphics.Color>(180, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
    val background by animateColorAsState(if (selected) SoundistColors.Teal.copy(alpha = .14f) else Color.Transparent, transitionSpec, label = "chipBackground")
    val border by animateColorAsState(if (selected) SoundistColors.Teal.copy(alpha = .7f) else SoundistColors.Divider, transitionSpec, label = "chipBorder")
    val content by animateColorAsState(if (selected) SoundistColors.Teal else SoundistColors.TextMuted, transitionSpec, label = "chipContent")
    Text(
        label,
        color = content,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(background)
            .border(1.dp, border, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp),
    )
}

@Composable
fun IconControl(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    accent: Color = SoundistColors.Teal,
    modifier: Modifier = Modifier,
) {
    val color = if (active) accent else SoundistColors.TextMuted
    Column(
        modifier = modifier.semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(SoundistDimens.TouchTarget)
                .clip(CircleShape)
                .background(if (active) color.copy(alpha = .13f) else SoundistColors.DeepSea)
                .border(BorderStroke(1.dp, color.copy(alpha = if (active) .72f else .25f)), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        }
        Text(label, color = color, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun LabeledVolume(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = SoundistColors.Text)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${(value * 100).toInt()}%", color = SoundistColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                trailing?.invoke()
            }
        }
        Spacer(Modifier.height(2.dp))
        SoundSlider(value = value, onValueChange = onValueChange, valueRange = 0f..1f)
    }
}

/**
 * Exact anchored replacement for the web `<select>` fields used by Soundist.
 *
 * Material `DropdownMenu` deliberately adds its own vertical offset and computes an
 * intrinsic popup width. The web control does neither: the option panel starts at the
 * lower edge of the field and has the field's width. Keeping the geometry here avoids
 * every feature reintroducing Material defaults.
 */
@Composable
fun <T> SoundistSelect(
    value: T,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 44.dp,
    maxMenuHeight: Dp = 300.dp,
    background: Color = SoundistColors.DeepSea,
    borderColor: Color = SoundistColors.Divider,
    valueColor: Color = SoundistColors.Text,
    itemBackground: Color = SoundistColors.RaisedStrong,
    itemSelectedBackground: Color = SoundistColors.Teal.copy(alpha = .10f),
    fontSize: TextUnit = 12.sp,
    horizontalPadding: Dp = 12.dp,
    valueTextAlign: TextAlign = TextAlign.Start,
    enabled: Boolean = true,
) {
    var expanded by remember { androidx.compose.runtime.mutableStateOf(false) }
    var anchorWidthPx by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val selectedLabel = options.firstOrNull { it.first == value }?.second.orEmpty()
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .onSizeChanged { anchorWidthPx = it.width }
            .heightIn(min = minHeight)
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .clickable(enabled = enabled) { expanded = true },
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = minHeight).padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                selectedLabel,
                color = valueColor,
                fontSize = fontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = valueTextAlign,
                modifier = Modifier.weight(1f),
            )
            Canvas(Modifier.size(16.dp)) {
                val y = size.height * .43f
                val half = 3.25.dp.toPx()
                val drop = 3.dp.toPx()
                drawLine(
                    SoundistColors.TextMuted,
                    Offset(size.width / 2f - half, y),
                    Offset(size.width / 2f, y + drop),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    SoundistColors.TextMuted,
                    Offset(size.width / 2f, y + drop),
                    Offset(size.width / 2f + half, y),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        if (expanded && anchorWidthPx > 0) {
            val popupWidth = with(density) { anchorWidthPx.toDp() }
            Popup(
                popupPositionProvider = ExactSelectPositionProvider,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier
                        .width(popupWidth)
                        .heightIn(max = maxMenuHeight)
                        .shadow(10.dp, shape, clip = false)
                        .clip(shape)
                        .background(itemBackground)
                        .border(1.dp, borderColor, shape)
                        .verticalScroll(rememberScrollState()),
                ) {
                    options.forEach { (optionValue, label) ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .background(if (optionValue == value) itemSelectedBackground else Color.Transparent)
                                .clickable {
                                    expanded = false
                                    onSelect(optionValue)
                                }
                                .padding(horizontal = horizontalPadding),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                label,
                                color = if (optionValue == value) SoundistColors.TealSoft else SoundistColors.Text,
                                fontSize = fontSize,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private object ExactSelectPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val preferredX = if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.left
        } else {
            anchorBounds.right - popupContentSize.width
        }
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val x = preferredX.coerceIn(0, maxX)
        val below = anchorBounds.bottom
        val above = anchorBounds.top - popupContentSize.height
        val preferredY = if (below + popupContentSize.height <= windowSize.height) below else above
        val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(x, preferredY.coerceIn(0, maxY))
    }
}

/**
 * Native-`<input type="range">` look from the frontend `.sound-slider` (accent-color:
 * #55B6A3, App.tsx 8061-8063, no custom thumb/track): a 2px rounded teal track and a
 * 14px teal thumb, no border/glow — instead of the Material3 slider chrome.
 *
 * 手势稳定性（阶段 2b 修复）：
 *  - 外层 Box 提供 48dp 高触摸区，视觉轨道/拇指仍为 16dp 居中，拖拽不再只靠 16dp 细条命中；
 *  - `pointerInput` 仅以 `valueRange` 为 key（稳定），不再以实时 `value` 为 key —— 拖动中 value
 *    变化不会重启手势协程、中断拖动；
 *  - 单个 `awaitEachGesture` 合并 tap 与 drag：先 `awaitFirstDown`，再用
 *    `awaitHorizontalTouchSlopOrCancellation` 等横向 touch slop —— 只在明确横向拖动超过 slop 后才
 *    `consume()`，之前不消费，父级纵向滚动可正常接管；未超 slop 即松手按 tap 处理（在松手位置设值）；
 *  - 拖动中每个事件帧回调一次 [onValueChange]（拇指由调用方本地临时值驱动、每帧跟手，不逐像素写外部状态）；
 *    松手时先发一个携带松手位置最终值的 [onValueChange]，再调用一次 [onValueChangeFinished]，保证
 *    最后的 onValueChange 即最终值；
 *  - 若手势被父级/其他手势消费（如纵向滚动接管），不设置值，仅按取消调用 [onValueChangeFinished]。
 */
@Composable
fun SoundSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    var widthPx by remember { mutableFloatStateOf(0f) }
    // pointerInput 的 key 稳定，lambda 可能由旧组合捕获；用 rememberUpdatedState 保证始终读到最新回调。
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestFinished by rememberUpdatedState(onValueChangeFinished)
    val latestValue by rememberUpdatedState(value)
    val thumbPx = with(androidx.compose.ui.platform.LocalDensity.current) { 14.dp.toPx() }
    fun valueAt(x: Float): Float {
        if (widthPx <= 0f) return latestValue
        val f = ((x - thumbPx / 2f) / (widthPx - thumbPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
        return valueRange.start + f * (valueRange.endInclusive - valueRange.start)
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(valueRange) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // 只在明确横向拖动超过 touch slop 后 consume；之前不消费，父级纵向滚动可正常接管。
                    val drag = awaitHorizontalTouchSlopOrCancellation(
                        pointerId = down.id,
                        onTouchSlopReached = { change, _ -> change.consume() },
                    )
                    if (drag != null) {
                        // 拖动开始：先发起手位置的值，随后每帧回调（拇指跟手），松手发最终值。
                        latestOnValueChange(valueAt(drag.position.x))
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                // 松手：保证最后一个 onValueChange 是松手位置的最终值。
                                if (change.changedToUpIgnoreConsumed()) {
                                    change.consume()
                                    latestOnValueChange(valueAt(change.position.x))
                                }
                                break
                            }
                            // 手势被父级/其他手势消费则视为取消，不再更新值。
                            if (change.isConsumed) break
                            change.consume()
                            latestOnValueChange(valueAt(change.position.x))
                        }
                        latestFinished?.invoke()
                    } else {
                        // 未超 slop：松手即 tap（在松手位置设值）；被消费（如纵向滚动接管）则不设值，仅结束手势。
                        val change = currentEvent.changes.firstOrNull { it.id == down.id }
                        if (change != null && change.changedToUpIgnoreConsumed() && !change.isConsumed) {
                            change.consume()
                            latestOnValueChange(valueAt(change.position.x))
                        }
                        latestFinished?.invoke()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier.fillMaxWidth().height(16.dp).onSizeChanged { widthPx = it.width.toFloat() },
        ) {
            val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
            val cy = size.height / 2f
            val trackH = 2.dp.toPx()
            val thumbR = 7.dp.toPx()
            val thumbX = thumbR + fraction * (size.width - thumbR * 2f).coerceAtLeast(0f)
            // Track: 原生 range 的紧凑轨道与圆拇指（accent-color 原生，无光晕、无深色描边）。
            // 光晕 + 深色描边属于 .soundist-slider（VolumeSlider 已覆盖），GenSlider/画笔宽度不应用。
            drawLine(Color(0x1A43565A), Offset(0f, cy), Offset(size.width, cy), strokeWidth = trackH, cap = StrokeCap.Round)
            if (thumbX > 0f) drawLine(SoundistColors.Teal, Offset(0f, cy), Offset(thumbX, cy), strokeWidth = trackH, cap = StrokeCap.Round)
            drawCircle(SoundistColors.Teal, radius = thumbR, center = Offset(thumbX, cy))
        }
    }
}
