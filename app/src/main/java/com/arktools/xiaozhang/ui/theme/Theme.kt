package com.arktools.xiaozhang.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FixedColorScheme = lightColorScheme(
    primary = Primary,
    secondary = AccentGreen,
    tertiary = AccentOrange,
    background = BackgroundLight,
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F1F1),
    primaryContainer = Color(0xFFDCEAF7),
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
        content = content
    )
}
