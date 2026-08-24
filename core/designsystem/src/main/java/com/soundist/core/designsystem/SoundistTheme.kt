package com.soundist.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object SoundistColors {
    // These are the literal CSS variables from the approved mobile frontend.
    val Abyss = Color(0xFF080B0D)
    val DeepSea = Color(0xFF0E1417)
    val SurfaceLow = Color(0xFF101719)
    val Raised = Color(0xFF161E21)
    val RaisedStrong = Color(0xFF1E282B)
    val Teal = Color(0xFF55B6A3)
    val TealSoft = Color(0xFF91D3C5)
    val Warm = Color(0xFFC99662)
    val Text = Color(0xFFE9ECE9)
    val TextSecondary = Color(0xFFA9B3AF)
    val TextMuted = Color(0xFF929D99)
    val Divider = Color(0xFF314044)
    val DividerStrong = Color(0xFF43565A)
    val Danger = Color(0xFFD57478)
}

object SoundistDimens {
    val PagePadding = 16.dp
    val SectionGap = 28.dp
    val ItemGap = 12.dp
    val ControlRadius = 8.dp
    val TouchTarget = 48.dp
    val BottomBarHeight = 72.dp
}

val SoundistShapes = androidx.compose.material3.Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
)

// FONT NOTE（待人工决策）：前端 `prototypes/mobile-interactive/src/styles/fonts.css` 从 Google Fonts 加载
// Sora (400/500/600)、Noto Sans SC (400/500/600)、Noto Serif SC (400/600)。仓库内未找到已下载的
// ttf/otf/woff2，且离线无法获取。待拿到字体文件后放入 `res/font/`，再在此处用
// `FontFamily(Font(R.font.sora))` / `FontFamily(Font(R.font.noto_sans_sc))` / `FontFamily(Font(R.font.noto_serif_sc))`
// 替换默认族，并把 AppHeader 的「Soundist 声境」文本改为 Noto Serif SC（前端 `fontFamily: "'Noto Serif SC', serif"`）。
// 当前 Sora 对应 FontFamily.SansSerif，Noto Sans SC 对应系统中文回退（Android 默认即 Noto Sans CJK），
// Noto Serif SC 无系统回退，「声境」因此渲染为非衬线 —— 与前端有可见差异。
val SoundistTypography = androidx.compose.material3.Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp),
)

private val SoundistScheme = darkColorScheme(
    primary = SoundistColors.Teal,
    onPrimary = SoundistColors.Abyss,
    secondary = SoundistColors.Warm,
    onSecondary = SoundistColors.Abyss,
    background = SoundistColors.Abyss,
    onBackground = SoundistColors.Text,
    surface = SoundistColors.DeepSea,
    onSurface = SoundistColors.Text,
    surfaceVariant = SoundistColors.Raised,
    onSurfaceVariant = SoundistColors.TextMuted,
    outline = SoundistColors.Divider,
    error = SoundistColors.Danger,
)

@Composable
fun SoundistTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val systemDark = isSystemInDarkTheme()
    // App.tsx root sets `color: var(--text-primary)` (#E9ECE9), so any Text without an
    // explicit color inherits the primary text color — never Material's black default.
    CompositionLocalProvider(LocalContentColor provides SoundistColors.Text) {
        MaterialTheme(
            colorScheme = SoundistScheme,
            typography = SoundistTypography,
            shapes = SoundistShapes,
            content = content,
        )
    }
}
