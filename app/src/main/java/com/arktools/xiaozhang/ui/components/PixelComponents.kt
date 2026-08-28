package com.arktools.xiaozhang.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.arktools.xiaozhang.R

/**
 * Pixel art themed UI components for consistent visual style across the app.
 * All components use the pixel art assets from drawable-nodpi.
 */

/**
 * Pixel art game page background wrapper.
 * Wraps entire screen content with bg_game_page as background.
 * Use this as the root container for all in-game screens.
 */
@Composable
fun PixelGameBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    val grassTile = remember(R.drawable.tile_grass) {
        BitmapFactory.decodeResource(context.resources, R.drawable.tile_grass).asImageBitmap()
    }
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ts = 64
            var cx = 0
            while (cx < size.width.toInt()) {
                var cy = 0
                while (cy < size.height.toInt()) {
                    drawImage(
                        image = grassTile,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(grassTile.width, grassTile.height),
                        dstOffset = IntOffset(cx, cy),
                        dstSize = IntSize(ts, ts),
                        filterQuality = FilterQuality.None
                    )
                    cy += ts
                }
                cx += ts
            }
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            content = content
        )
    }
}

enum class PixelButtonStyle {
    PRIMARY,    // Orange - main actions
    SECONDARY,  // Blue-purple - secondary actions
    CONFIRM,    // Green - confirm/accept
    DANGER,     // Red - delete/exit/dangerous
    CANCEL      // Gray - cancel/dismiss
}

/**
 * Pixel art style button with image background.
 * Use this throughout the app for consistent button look.
 */
@Composable
fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PixelButtonStyle = PixelButtonStyle.PRIMARY,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 56.dp
) {
    val btnRes = when (style) {
        PixelButtonStyle.PRIMARY -> R.drawable.btn_primary
        PixelButtonStyle.SECONDARY -> R.drawable.btn_secondary
        PixelButtonStyle.CONFIRM -> R.drawable.btn_confirm
        PixelButtonStyle.DANGER -> R.drawable.btn_danger
        PixelButtonStyle.CANCEL -> R.drawable.btn_cancel
    }

    Box(
        modifier = modifier
            .widthIn(min = 72.dp)
            .heightIn(min = height)
    ) {
        PixelNineSlice(
            res = btnRes,
            slice = 32,
            modifier = Modifier.matchParentSize(),
            alpha = if (enabled) 1f else 0.5f
        )

        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().heightIn(min = height),
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = Color.White.copy(alpha = 0.5f)
            ),
            shape = RectangleShape,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

/**
 * Pixel art style card container.
 * Use for module grid cards, info panels, etc.
 */
@Composable
fun PixelCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier) {
        PixelNineSlice(
                res = R.drawable.card_bg,
                slice = 48,
                modifier = Modifier.matchParentSize()
            )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            content = content
        )
    }
}

/**
 * Pixel art style dialog/popup container.
 * Uses dialog_bg.png (opaque, with border included) as single background image,
 * stretched to fill the entire container via FillBounds.
 */
@Composable
fun PixelDialogBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier) {
        PixelNineSlice(
                res = R.drawable.dialog_bg,
                slice = 48,
                modifier = Modifier.matchParentSize()
            )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            content = content
        )
    }
}

/**
 * Pixel art style top info bar.
 * Use for showing cash/reputation/campus status.
 */
@Composable
fun PixelInfoBar(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp)
    ) {
        PixelNineSlice(
            res = R.drawable.bar_bg,
            slice = 24,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

/**
 * Pixel art style alert dialog.
 * Uses dialog_bg.png (opaque, border included) as single background with FillBounds.
 */
@Composable
fun PixelAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    text: String? = null,
    confirmText: String = "确定",
    dismissText: String? = null,
    onConfirm: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    confirmStyle: PixelButtonStyle = PixelButtonStyle.CONFIRM,
    dismissStyle: PixelButtonStyle = PixelButtonStyle.CANCEL,
    content: (@Composable () -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .padding(16.dp)
        ) {
            // Single background image with border included, fill the container
            PixelNineSlice(
                res = R.drawable.dialog_bg,
                slice = 48,
                modifier = Modifier.matchParentSize()
            )
            // Content on top of background
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center,
                    color = Color(0xFFE7F1F8)
                )

                if (text != null) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = Color(0xFFC7D9E8)
                    )
                }

                if (content != null) {
                    content()
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (dismissText != null && onDismiss != null) {
                        PixelButton(
                            text = dismissText,
                            onClick = onDismiss,
                            style = dismissStyle,
                            modifier = Modifier.weight(1f),
                            height = 44.dp
                        )
                    }
                    PixelButton(
                        text = confirmText,
                        onClick = onConfirm,
                        style = confirmStyle,
                        modifier = Modifier.weight(1f),
                        height = 44.dp
                    )
                }
            }
        }
    }
}
