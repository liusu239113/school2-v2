package com.arktools.xiao.ui.theme

import androidx.compose.ui.graphics.Color

val Primary = Color(0xFF1E96C8)            // 青蓝主色（全局 UI 主色）
val PrimaryDark = Color(0xFF14648C)         // 青蓝描边/按压色
val PrimaryCyanLight = Color(0xFFDCF1FB)    // 青蓝浅底
val AccentGreen = Color(0xFF2E9B78)
val AccentRed = Color(0xFFD95C5C)
val AccentOrange = Color(0xFFD49A45)
val BackgroundLight = Color(0xFFF4F7FA)
val BackgroundDark = Color(0xFF0B1724)
val CardDark = Color(0xFF14283B)
val TextPrimaryLight = Color(0xFF182635)
val TextSecondaryLight = Color(0xFF4A5D70)
// 白底像素卡上的正文色。历史命名 Dark 实际被当成白字用，导致标题看不见。
val PanelInk = Color(0xFF122033)
val PanelMuted = Color(0xFF4A5D70)
val TextOnDark = Color(0xFFFFFFFF)
val TextOnDarkMuted = Color(0xFFD5E2EE)
@Deprecated("白卡上不要再用，改用 PanelInk")
val TextPrimaryDark = PanelInk
@Deprecated("白卡上不要再用，改用 PanelMuted")
val TextSecondaryDark = PanelMuted
