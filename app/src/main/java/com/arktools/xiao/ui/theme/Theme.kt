package com.arktools.xiao.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/** 像素风全局形状：所有 Material 组件零圆角 */
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
    onPrimary = TextOnDark,
    onSecondary = TextOnDark,
    onTertiary = TextOnDark,
    onBackground = PanelInk,
    onSurface = PanelInk,
    onSurfaceVariant = PanelMuted,
    onPrimaryContainer = PanelInk,
    onSecondaryContainer = PanelInk,
    onErrorContainer = PanelInk
)

@Composable
fun SchoolTycoonTheme(
    fontPreset: GameFontPreset = GameFontPreset.PIXEL,
    content: @Composable () -> Unit
) {
    val family = fontFamilyOf(fontPreset)
    MaterialTheme(
        colorScheme = FixedColorScheme,
        typography = gameTypography(family),
        shapes = PixelShapes
    ) {
        CompositionLocalProvider(
            LocalGameFontFamily provides family,
            LocalTextStyle provides TextStyle(
                fontFamily = family,
                color = PanelInk
            )
        ) {
            content()
        }
    }
}
