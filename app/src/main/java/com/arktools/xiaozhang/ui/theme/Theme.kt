package com.arktools.xiaozhang.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/** 像素风全局形状：所有 Material 组件零圆角（PixelForge 规则 #1） */
private val PixelShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp)
)

private val FixedColorScheme = lightColorScheme(
    primary = Primary,
    secondary = AccentGreen,
    tertiary = AccentOrange,
    background = BackgroundLight,
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F1F1),
    primaryContainer = PrimaryCyanLight,
    secondaryContainer = Color(0xFFDDF2EA),
    errorContainer = Color(0xFFFFE0DE),
    outline = TextSecondaryLight,
    outlineVariant = Color(0xFFE0E0E0),
    onPrimary = TextPrimaryDark,
    onSecondary = TextPrimaryDark,
    onTertiary = TextPrimaryDark,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
    onSurfaceVariant = TextSecondaryLight,
    onPrimaryContainer = TextPrimaryLight,
    onSecondaryContainer = TextPrimaryLight,
    onErrorContainer = TextPrimaryLight
)

@Composable
fun SchoolTycoonTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FixedColorScheme,
        typography = Typography,
        shapes = PixelShapes,
        content = content
    )
}
