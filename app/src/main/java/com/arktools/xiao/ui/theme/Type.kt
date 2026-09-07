package com.arktools.xiao.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.arktools.xiao.R

/**
 * 全局字体：默认像素体，设置里可切粗像素 / 等宽像素 / 系统黑体。
 */
val PixelRegularFamily = FontFamily(
    Font(R.font.fusion_pixel_zh_regular, FontWeight.Normal),
    Font(R.font.fusion_pixel_zh_regular, FontWeight.Medium),
    Font(R.font.fusion_pixel_zh_bold, FontWeight.SemiBold),
    Font(R.font.fusion_pixel_zh_bold, FontWeight.Bold),
    Font(R.font.fusion_pixel_zh_bold, FontWeight.Black)
)

val PixelBoldFamily = FontFamily(
    Font(R.font.fusion_pixel_zh_bold, FontWeight.Normal),
    Font(R.font.fusion_pixel_zh_bold, FontWeight.Medium),
    Font(R.font.fusion_pixel_zh_bold, FontWeight.SemiBold),
    Font(R.font.fusion_pixel_zh_bold, FontWeight.Bold),
    Font(R.font.fusion_pixel_zh_bold, FontWeight.Black)
)

val PixelMonoFamily = FontFamily(
    Font(R.font.fusion_pixel_zh_mono, FontWeight.Normal),
    Font(R.font.fusion_pixel_zh_mono, FontWeight.Medium),
    Font(R.font.fusion_pixel_zh_mono, FontWeight.SemiBold),
    Font(R.font.fusion_pixel_zh_mono, FontWeight.Bold),
    Font(R.font.fusion_pixel_zh_mono, FontWeight.Black)
)

enum class GameFontPreset(
    val id: String,
    val displayName: String,
    val hint: String
) {
    PIXEL("pixel", "像素体", "默认，校园招牌感"),
    PIXEL_BOLD("pixel_bold", "粗像素", "更粗更清楚"),
    PIXEL_MONO("pixel_mono", "等宽像素", "账本数字更齐"),
    SYSTEM("system", "系统黑体", "最清楚，偏现代");

    companion object {
        fun fromId(id: String?): GameFontPreset =
            entries.firstOrNull { it.id == id } ?: PIXEL
    }
}

fun fontFamilyOf(preset: GameFontPreset): FontFamily = when (preset) {
    GameFontPreset.PIXEL -> PixelRegularFamily
    GameFontPreset.PIXEL_BOLD -> PixelBoldFamily
    GameFontPreset.PIXEL_MONO -> PixelMonoFamily
    GameFontPreset.SYSTEM -> FontFamily.SansSerif
}

val LocalGameFontFamily = staticCompositionLocalOf { PixelRegularFamily }

/** 兼容旧引用：默认仍是像素体。主题会再覆盖成当前选择。 */
val GameFontFamily: FontFamily get() = PixelRegularFamily

fun gameTypography(family: FontFamily): Typography = Typography(
    displayLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
    displayMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    displaySmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    headlineLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    headlineSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
    titleLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 14.sp)
)

val Typography = gameTypography(PixelRegularFamily)
