package com.arktools.xiao.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arktools.xiao.ui.theme.AccentGreen
import com.arktools.xiao.ui.theme.AccentRed

@Composable
fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    isPositive: Boolean? = null
) {
    Card(
        modifier = modifier.padding(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = when (isPositive) {
                    true -> AccentGreen
                    false -> AccentRed
                    null -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@Composable
fun AnimatedStatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    isPositive: Boolean? = null,
    animationDelay: Int = 0
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(
            animationSpec = tween(durationMillis = 300, delayMillis = animationDelay)
        ) + scaleIn(
            initialScale = 0.8f,
            animationSpec = tween(durationMillis = 300, delayMillis = animationDelay)
        ),
        modifier = modifier
    ) {
        StatCard(
            title = title,
            value = value,
            isPositive = isPositive
        )
    }
}
