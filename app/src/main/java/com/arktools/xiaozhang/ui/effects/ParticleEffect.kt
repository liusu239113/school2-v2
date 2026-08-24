package com.arktools.xiaozhang.ui.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class ParticleType {
    COIN,
    STAR,
    CONFETTI
}

data class Particle(
    val x: Float,
    val y: Float,
    val type: ParticleType,
    val velocityX: Float,
    val velocityY: Float,
    var life: Float = 1f,
    val color: Color = when (type) {
        ParticleType.COIN -> Color(0xFFFFD700)
        ParticleType.STAR -> Color(0xFFFFEB3B)
        ParticleType.CONFETTI -> Random.nextColor()
    },
    val size: Float = when (type) {
        ParticleType.COIN -> Random.nextFloat() * 8f + 8f
        ParticleType.STAR -> Random.nextFloat() * 6f + 6f
        ParticleType.CONFETTI -> Random.nextFloat() * 10f + 4f
    }
) {
    fun update(delta: Float): Particle {
        return copy(
            x = x + velocityX * delta,
            y = y + velocityY * delta,
            life = (life - delta * 0.5f).coerceAtLeast(0f)
        )
    }
}

fun Random.nextColor(): Color {
    return Color(
        red = nextFloat(),
        green = nextFloat(),
        blue = nextFloat(),
        alpha = 1f
    )
}

@Composable
fun ParticleEffect(
    modifier: Modifier = Modifier,
    type: ParticleType,
    originX: Float,
    originY: Float,
    count: Int = 20,
    onFinished: () -> Unit = {}
) {
    val progress = remember { Animatable(0f) }

    val particles = remember {
        (0 until count).map {
            val angle = Random.nextFloat() * Math.PI * 2
            val speed = when (type) {
                ParticleType.COIN -> Random.nextFloat() * 200f + 100f
                ParticleType.STAR -> Random.nextFloat() * 150f + 80f
                ParticleType.CONFETTI -> Random.nextFloat() * 250f + 50f
            }.toFloat()
            Particle(
                x = originX,
                y = originY,
                type = type,
                velocityX = (cos(angle) * speed).toFloat(),
                velocityY = (sin(angle) * speed - 100f).toFloat()
            )
        }
    }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 1200,
                easing = FastOutSlowInEasing
            )
        )
        onFinished()
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val currentProgress = progress.value
        particles.forEach { particle ->
            val updated = particle.update(currentProgress)
            if (updated.life > 0) {
                drawParticle(updated)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawParticle(particle: Particle) {
    val alpha = particle.life
    val center = Offset(particle.x, particle.y)

    when (particle.type) {
        ParticleType.COIN -> {
            drawCircle(
                color = particle.color.copy(alpha = alpha),
                radius = particle.size,
                center = center
            )
            drawCircle(
                color = Color.White.copy(alpha = alpha * 0.5f),
                radius = particle.size * 0.5f,
                center = center
            )
        }
        ParticleType.STAR -> {
            drawStar(center, particle.size, particle.color.copy(alpha = alpha))
        }
        ParticleType.CONFETTI -> {
            drawRect(
                color = particle.color.copy(alpha = alpha),
                topLeft = center,
                size = Size(particle.size, particle.size * 0.5f)
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStar(
    center: Offset,
    radius: Float,
    color: Color
) {
    val path = Path()
    val innerRadius = radius * 0.4f
    val points = 5
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) radius else innerRadius
        val angle = Math.PI / points * i - Math.PI / 2
        val x = center.x + (r * cos(angle)).toFloat()
        val y = center.y + (r * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

@Composable
fun CoinEffect(
    modifier: Modifier = Modifier,
    originX: Float,
    originY: Float,
    onFinished: () -> Unit = {}
) {
    ParticleEffect(
        modifier = modifier,
        type = ParticleType.COIN,
        originX = originX,
        originY = originY,
        count = 15,
        onFinished = onFinished
    )
}

@Composable
fun StarEffect(
    modifier: Modifier = Modifier,
    originX: Float,
    originY: Float,
    onFinished: () -> Unit = {}
) {
    ParticleEffect(
        modifier = modifier,
        type = ParticleType.STAR,
        originX = originX,
        originY = originY,
        count = 20,
        onFinished = onFinished
    )
}

@Composable
fun ConfettiEffect(
    modifier: Modifier = Modifier,
    originX: Float,
    originY: Float,
    onFinished: () -> Unit = {}
) {
    ParticleEffect(
        modifier = modifier,
        type = ParticleType.CONFETTI,
        originX = originX,
        originY = originY,
        count = 30,
        onFinished = onFinished
    )
}