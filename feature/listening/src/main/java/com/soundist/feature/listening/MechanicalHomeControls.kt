package com.soundist.feature.listening

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundist.core.designsystem.SoundistColors
import com.soundist.core.designsystem.radio
import com.soundist.core.designsystem.volume2
import com.soundist.core.designsystem.plus
import com.soundist.core.designsystem.share2
import com.soundist.core.designsystem.folderInput
import com.soundist.core.designsystem.slidersHorizontal
import com.soundist.core.designsystem.x
import kotlin.math.abs

/** Literal Compose counterparts for the final renderHome() blocks in App.tsx. */

/** CSS `border: 1px dashed <color>` on a rounded shape. */
private fun Modifier.dashedBorder(width: Dp, color: Color, radius: Dp, dashWidth: Dp = 4.dp, dashGap: Dp = 4.dp): Modifier = this.drawBehind {
    val stroke = Stroke(width.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashWidth.toPx(), dashGap.toPx()), 0f))
    drawRoundRect(color = color, topLeft = Offset.Zero, size = size, cornerRadius = CornerRadius(radius.toPx(), radius.toPx()), style = stroke)
}

@Composable
internal fun HomeVolumeCard(state: ListeningState, dispatch: (ListeningAction) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SoundistColors.Raised)
            .border(1.dp, SoundistColors.Divider, RoundedCornerShape(12.dp)).padding(14.dp),
    ) {
        VolumeLabelRow("总音量", slidersHorizontal, 8.dp, 10.dp, state.globalVolume)
        VolumeSlider(state.globalVolume) { dispatch(ListeningAction.SetMasterVolume(it)) }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(SoundistColors.Divider.copy(alpha = .7f)))
        Spacer(Modifier.height(16.dp))
        VolumeLabelRow("环境声音量", volume2, 6.dp, 8.dp, state.environmentVolume)
        VolumeSlider(state.environmentVolume) { dispatch(ListeningAction.SetEnvironmentVolume(it)) }
        Spacer(Modifier.height(16.dp))
        VolumeLabelRow("电台音量", radio, 6.dp, 8.dp, state.radioVolume)
        VolumeSlider(state.radioVolume) { dispatch(ListeningAction.SetRadioVolume(it)) }
    }
}

@Composable
private fun VolumeLabelRow(label: String, icon: ImageVector, gap: Dp, bottomPadding: Dp, value: Float) {
    Row(Modifier.fillMaxWidth().padding(bottom = bottomPadding), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(14.dp), tint = SoundistColors.TextSecondary)
            Text(label, color = SoundistColors.TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Text("${(value * 100).toInt()}%", color = SoundistColors.TealSoft, fontSize = 12.sp, lineHeight = 16.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
internal fun VolumeSlider(value: Float, modifier: Modifier = Modifier, onChange: (Float) -> Unit) {
    var adjusting by remember { mutableStateOf(false) }
    BoxWithConstraints(
        modifier.height(48.dp).pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val thumb = 14.dp.toPx()
                fun fractionAt(x: Float) = ((x - thumb / 2f) / (size.width - thumb).coerceAtLeast(1f)).coerceIn(0f, 1f)
                var dragging = false
                var pendingValue = value
                var lastDispatchAt = 0L
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (dragging) {
                                change.consume()
                                onChange(pendingValue)
                            }
                            break
                        }
                        val dx = change.position.x - down.position.x
                        val dy = change.position.y - down.position.y
                        if (!dragging) {
                            // 先判断手势方向。纵向滚动必须交还页面，只有明确的水平拖动才接管音量。
                            if (abs(dy) > viewConfiguration.touchSlop && abs(dy) > abs(dx)) break
                            if (abs(dx) > viewConfiguration.touchSlop && abs(dx) >= abs(dy)) {
                                dragging = true
                                adjusting = true
                            }
                        }
                        if (dragging) {
                            change.consume()
                            pendingValue = fractionAt(change.position.x)
                            val now = android.os.SystemClock.uptimeMillis()
                            if (now - lastDispatchAt >= 30L) {
                                lastDispatchAt = now
                                onChange(pendingValue)
                            }
                        }
                    }
                } finally {
                    adjusting = false
                }
            }
        },
        contentAlignment = Alignment.CenterStart,
    ) {
        val frac = value.coerceIn(0f, 1f)
        val trackH = if (adjusting) 3.dp else 2.dp
        Box(Modifier.fillMaxWidth().height(trackH).background(Color(0x1A43565A), CircleShape))
        Box(Modifier.fillMaxWidth(frac).height(trackH).background(SoundistColors.Teal, CircleShape))
        // Thumb knob (App.tsx .soundist-slider::-webkit-slider-thumb): 14px, #55B6A3, glow rgba(85,182,163,.55), border 1.5px rgba(8,11,13,.7).
        // data-adjusting=true → scale(1.14), glow 0 0 10px rgba(85,182,163,.62).
        val baseThumbD = 14.dp
        val thumbD = if (adjusting) baseThumbD * 1.14f else baseThumbD
        val blur = if (adjusting) 10.dp else 8.dp
        val glowAlpha = if (adjusting) 0.62f else 0.55f
        // Native range travel is inset by the unscaled thumb radius. CSS transform only
        // enlarges the painted thumb; it does not change the value-to-position mapping.
        val centerX = baseThumbD / 2 + (maxWidth - baseThumbD) * frac
        val outerD = thumbD + blur * 2
        Box(
            Modifier.offset(x = centerX - outerD / 2).size(outerD).drawBehind {
                val thumbR = thumbD.toPx() / 2f
                val blurPx = blur.toPx()
                val outerR = thumbR + blurPx
                val solidStop = (thumbR / outerR).coerceIn(0f, 1f)
                drawCircle(
                    Brush.radialGradient(
                        colorStops = arrayOf(0f to SoundistColors.Teal.copy(alpha = glowAlpha), solidStop to SoundistColors.Teal.copy(alpha = glowAlpha), 1f to Color.Transparent),
                        center = center,
                        radius = outerR,
                    ),
                    radius = outerR,
                    center = center,
                )
                drawCircle(SoundistColors.Teal, radius = thumbR, center = center)
                val borderWidth = 1.5.dp.toPx()
                drawCircle(Color(0xB3080B0D), radius = thumbR - borderWidth / 2f, center = center, style = Stroke(width = borderWidth))
            },
        )
    }
}

@Composable
internal fun LiteralMixerRow(sound: AmbientSound, highlighted: Boolean, dispatch: (ListeningAction) -> Unit) {
    // App.tsx linear-gradient(135deg, rgba(24,60,54,0.88), #161E21) when highlighted,
    // else linear-gradient(135deg, #1E282B, #161E21).
    val background = if (highlighted) Brush.linearGradient(listOf(Color(0xE0183C36), Color(0xFF161E21)))
    else Brush.linearGradient(listOf(Color(0xFF1E282B), Color(0xFF161E21)))
    Column(
        Modifier.fillMaxWidth()
            .then(if (highlighted) Modifier.shadow(22.dp, RoundedCornerShape(12.dp), ambientColor = SoundistColors.Teal.copy(alpha = .22f), spotColor = SoundistColors.Teal.copy(alpha = .22f)) else Modifier)
            .clip(RoundedCornerShape(12.dp)).background(background)
            // App.tsx boxShadow "inset 0 0 0 1px rgba(145,211,197,0.12)" — inner ring, distinct from the border.
            .then(if (highlighted) Modifier.drawBehind {
                val inset = 1.dp.toPx()
                drawRoundRect(
                    color = SoundistColors.TealSoft.copy(alpha = .12f),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx()),
                )
            } else Modifier)
            // App.tsx border-[var(--ambient)]/60 = rgba(85,182,163,0.60) when highlighted.
            .border(1.dp, if (highlighted) SoundistColors.Teal.copy(alpha = .6f) else SoundistColors.Divider, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(SoundistColors.RaisedStrong), contentAlignment = Alignment.Center) {
                Icon(soundIcon(sound.id), null, Modifier.size(14.dp), tint = SoundistColors.Teal.copy(alpha = .6f))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(sound.name, color = SoundistColors.Text, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
                    Text("${(sound.volume * 100).toInt()}%", color = SoundistColors.TextMuted, fontSize = 11.sp, lineHeight = 15.sp, fontFamily = FontFamily.Monospace)
                }
                Text(sound.category.label(), color = SoundistColors.TextMuted, fontSize = 11.sp, lineHeight = 15.sp)
            }
            // 显式移除 ≠ 暂停：RemoveSound 把该声音从混音移除但保留音量（便于之后恢复），不像 ToggleSound 会清音量=0。
            Box(Modifier.size(44.dp).clickable { dispatch(ListeningAction.RemoveSound(sound.id)) }, contentAlignment = Alignment.Center) {
                Icon(x, "移除${sound.name}", Modifier.size(12.dp), tint = SoundistColors.TextSecondary)
            }
        }
        VolumeSlider(sound.volume) { dispatch(ListeningAction.SetSoundVolume(sound.id, it)) }
    }
}

@Composable
internal fun EmptyMixer(onOpenSounds: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().dashedBorder(1.dp, SoundistColors.Divider, 12.dp).padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("还没有声源", color = SoundistColors.TextMuted, fontSize = 14.sp, lineHeight = 20.sp)
        Box(
            Modifier.padding(top = 8.dp).clip(RoundedCornerShape(999.dp)).border(1.dp, SoundistColors.Teal.copy(alpha = .2f), RoundedCornerShape(999.dp))
                .clickable(onClick = onOpenSounds).padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Text("前往声音库", color = SoundistColors.Teal.copy(alpha = .6f), fontSize = 12.sp)
        }
    }
}

@Composable
internal fun AddSoundButton(onOpenSounds: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().dashedBorder(1.dp, SoundistColors.DividerStrong.copy(alpha = .7f), 12.dp)
            .clickable(onClick = onOpenSounds).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(plus, null, Modifier.size(16.dp), tint = SoundistColors.TextMuted)
        Spacer(Modifier.width(6.dp))
        Text("添加声源", color = SoundistColors.TextMuted, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
internal fun ShareCurrentScene(state: ListeningState, dispatch: (ListeningAction) -> Unit) {
    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { readSoundScene(context, uri) }
            .onSuccess { dispatch(ListeningAction.ImportPreset(it)) }
            .onFailure { dispatch(ListeningAction.ShowNotice(it.message ?: "无法读取这个声场文件")) }
    }
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(SoundistColors.Divider))
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SceneTransferAction(share2, "分享声场", Modifier.weight(1f)) {
                runCatching { shareSoundScene(context, state) }
                    .onFailure { dispatch(ListeningAction.ShowNotice(it.message ?: "暂时无法分享，请稍后重试")) }
            }
            SceneTransferAction(folderInput, "导入声场", Modifier.weight(1f)) {
                importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }
        }
    }
}

@Composable
private fun SceneTransferAction(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.defaultMinSize(minHeight = 44.dp).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(14.dp), tint = SoundistColors.TextMuted)
        Spacer(Modifier.width(6.dp))
        Text(label, color = SoundistColors.TextMuted, fontSize = 12.sp)
    }
}
