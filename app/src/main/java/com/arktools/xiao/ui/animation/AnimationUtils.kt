package com.arktools.xiao.ui.animation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import kotlinx.coroutines.delay

object AnimationConstants {
    val defaultDuration = 300
    val fastDuration = 150
    val slowDuration = 500
    val entranceDelay = 100
}

val springBounce = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMedium
)

val smoothEase = tween<Float>(
    durationMillis = AnimationConstants.defaultDuration,
    easing = FastOutSlowInEasing
)

val fastEase = tween<Float>(
    durationMillis = AnimationConstants.fastDuration,
    easing = FastOutSlowInEasing
)

fun Modifier.pressAnimation(): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = fastEase,
        label = "pressScale"
    )
    this
        .scale(scale)
        .then(Modifier.composed { this })
}

fun Modifier.cardTapAnimation(): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "cardTap"
    )
    this
        .scale(scale)
        .then(Modifier.composed { this })
}

@Composable
fun rememberFloatAnimatable(initialValue: Float = 0f): Animatable<Float, AnimationVector1D> {
    return remember { Animatable(initialValue) }
}

@Composable
fun generateStaggeredEntrance(itemCount: Int, initialDelay: Int = 0): List<Boolean> {
    return List(itemCount) { index ->
        var visible = remember { false }

        LaunchedEffect(Unit) {
            delay((initialDelay + index * AnimationConstants.entranceDelay).toLong())
            visible = true
        }

        visible
    }
}

@Composable
fun pulseAnimation(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    return infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    ).value
}

@Composable
fun shimmerAlpha(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    return infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer"
    ).value
}

fun crateEntranceTransition(
    index: Int,
    delayPerItem: Int = AnimationConstants.entranceDelay
): androidx.compose.animation.EnterTransition {
    return fadeIn(
        animationSpec = tween(
            durationMillis = AnimationConstants.defaultDuration,
            delayMillis = index * delayPerItem
        )
    ) + slideInVertically(
        animationSpec = tween(
            durationMillis = AnimationConstants.defaultDuration,
            delayMillis = index * delayPerItem
        ),
        initialOffsetY = { it / 8 }
    )
}

fun listItemEntranceTransition(): androidx.compose.animation.EnterTransition {
    return fadeIn(animationSpec = smoothEase) +
            slideInVertically(
                animationSpec = tween(
                    durationMillis = AnimationConstants.defaultDuration,
                    easing = FastOutSlowInEasing
                ),
                initialOffsetY = { it / 16 }
            )
}

fun listItemExitTransition(): androidx.compose.animation.ExitTransition {
    return fadeOut(animationSpec = fastEase)
}