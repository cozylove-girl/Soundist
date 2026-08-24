package com.soundist.core.designsystem

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Returns a copy of [this] with every path's stroke width scaled by [factor].
 * The frontend NAV icons render at `strokeWidth={active ? 2 : 1.5}`; the generated
 * ImageVectors bake 1.5, so the active icon is scaled by 4/3 to reach 2.
 */
private fun ImageVector.withStrokeWidth(factor: Float): ImageVector {
    if (factor == 1f) return this
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = defaultWidth,
        defaultHeight = defaultHeight,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
    )
    fun rebuild(nodes: Iterable<VectorNode>, b: ImageVector.Builder) {
        nodes.forEach { node ->
            when (node) {
                is VectorPath -> b.addPath(
                    pathData = node.pathData,
                    pathFillType = node.pathFillType,
                    name = node.name,
                    fill = node.fill,
                    fillAlpha = node.fillAlpha,
                    stroke = node.stroke,
                    strokeAlpha = node.strokeAlpha,
                    strokeLineWidth = node.strokeLineWidth * factor,
                    strokeLineCap = node.strokeLineCap,
                    strokeLineJoin = node.strokeLineJoin,
                    strokeLineMiter = node.strokeLineMiter,
                    trimPathStart = node.trimPathStart,
                    trimPathEnd = node.trimPathEnd,
                    trimPathOffset = node.trimPathOffset,
                )
                is VectorGroup -> {
                    b.addGroup(
                        name = node.name,
                        rotate = node.rotation,
                        pivotX = node.pivotX,
                        pivotY = node.pivotY,
                        scaleX = node.scaleX,
                        scaleY = node.scaleY,
                        translationX = node.translationX,
                        translationY = node.translationY,
                        clipPathData = node.clipPathData,
                    )
                    rebuild(node, b)
                    b.clearGroup()
                }
                else -> Unit
            }
        }
    }
    rebuild(root, builder)
    return builder.build()
}

data class SoundistNavItem(val key: String, val label: String, val icon: ImageVector)

@Composable
fun SoundistBottomBar(items: List<SoundistNavItem>, selectedKey: String, onSelect: (String) -> Unit) {
    val safeBottom = maxOf(12.dp, WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
    Column(Modifier.fillMaxWidth().background(Color(0xF50E1417)).padding(bottom = safeBottom)) {
        HorizontalDivider(color = Color(0x7343565A))
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp, end = 4.dp, top = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            items.forEach { item ->
                val selected = item.key == selectedKey
                val interactionSource = remember { MutableInteractionSource() }
                val pressed by interactionSource.collectIsPressedAsState()
                Column(
                    Modifier.sizeIn(minWidth = 56.dp, minHeight = 56.dp)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            role = Role.Tab,
                        ) { onSelect(item.key) }
                        // Frontend `active:scale-[0.98]` press feedback.
                        .graphicsLayer {
                            val s = if (pressed) 0.98f else 1f
                            scaleX = s; scaleY = s
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp), // frontend gap-0.5
                ) {
                    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            // Active stroke width 2, inactive 1.5 (frontend NAV `strokeWidth={active ? 2 : 1.5}`).
                            if (selected) item.icon.withStrokeWidth(4f / 3f) else item.icon,
                            contentDescription = item.label,
                            modifier = Modifier.size(19.dp),
                            tint = if (selected) SoundistColors.Text else SoundistColors.TextMuted,
                        )
                    }
                    Text(
                        item.label,
                        color = if (selected) SoundistColors.Text else SoundistColors.TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                    if (selected) {
                        // Frontend dot: `w-1 h-1 bg-[var(--ambient)] -mt-0.5` with `boxShadow: 0 0 5px rgba(85,182,163,0.9)`.
                        Box(
                            Modifier.offset(y = (-0.5).dp).size(4.dp)
                                .shadow(5.dp, CircleShape, clip = false, ambientColor = Color(0xE655B6A3), spotColor = Color(0xE655B6A3))
                                .clip(CircleShape).background(SoundistColors.Teal),
                        )
                    }
                }
            }
        }
    }
}
