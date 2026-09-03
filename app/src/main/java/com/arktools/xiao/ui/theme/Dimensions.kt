package com.arktools.xiao.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Consistent spacing and sizing constants across the app.
 * Follows 4dp grid system for Material Design consistency.
 */
object Dimensions {
    // Spacing
    val spacingXxs = 2.dp
    val spacingXs = 4.dp
    val spacingSm = 8.dp
    val spacingMd = 12.dp
    val spacingLg = 16.dp
    val spacingXl = 24.dp
    val spacingXxl = 32.dp
    val spacingHuge = 48.dp

    // Padding
    val paddingScreen = 16.dp
    val paddingCard = 12.dp
    val paddingCardLarge = 16.dp
    val paddingDialog = 24.dp
    val paddingListItem = 12.dp

    // Card
    val cardElevation = 2.dp
    val cardElevationRaised = 4.dp
    val cardCornerRadius = 12.dp
    val cardCornerRadiusSmall = 8.dp

    // Icons
    val iconSizeSmall = 16.dp
    val iconSizeMedium = 24.dp
    val iconSizeLarge = 36.dp
    val iconSizeXl = 48.dp
    val iconSizeHero = 64.dp

    // Buttons
    val buttonHeight = 48.dp
    val buttonHeightSmall = 36.dp
    val buttonCornerRadius = 8.dp

    // Status bar
    val statusBarHeight = 56.dp

    // Bottom nav
    val bottomNavHeight = 80.dp

    // Progress bars
    val progressBarHeight = 6.dp
    val progressBarHeightLarge = 10.dp

    // Dividers
    val dividerThickness = 1.dp
}

/**
 * Game-specific color meanings for consistent semantic usage.
 */
object GameColors {
    val profit = AccentGreen
    val loss = AccentRed
    val warning = AccentOrange
    val info = Primary

    val levelC = androidx.compose.ui.graphics.Color(0xFF9E9E9E)    // Gray
    val levelB = androidx.compose.ui.graphics.Color(0xFF4CAF50)    // Green
    val levelA = androidx.compose.ui.graphics.Color(0xFF2196F3)    // Blue
    val levelS = androidx.compose.ui.graphics.Color(0xFFFF9800)    // Orange/Gold

    val traitPositive = AccentGreen
    val traitNeutral = AccentOrange
    val traitNegative = AccentRed

    val seasonSpring = androidx.compose.ui.graphics.Color(0xFF66BB6A)
    val seasonSummer = androidx.compose.ui.graphics.Color(0xFFFFB74D)
    val seasonFall = androidx.compose.ui.graphics.Color(0xFFFF7043)
    val seasonWinter = androidx.compose.ui.graphics.Color(0xFF42A5F5)
}
