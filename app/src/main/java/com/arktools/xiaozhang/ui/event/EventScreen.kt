package com.arktools.xiaozhang.ui.event

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arktools.xiaozhang.domain.model.EventType
import com.arktools.xiaozhang.domain.model.GameEvent
import com.arktools.xiaozhang.ui.theme.AccentGreen
import com.arktools.xiaozhang.ui.theme.AccentOrange
import com.arktools.xiaozhang.ui.theme.AccentRed
import com.arktools.xiaozhang.ui.theme.Primary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EventScreen(
    viewModel: EventViewModel = hiltViewModel()
) {
    val eventHistory by viewModel.eventHistory.collectAsState()
    var selectedFilter by remember { mutableStateOf<EventType?>(null) }

    val filteredEvents = if (selectedFilter == null) {
        eventHistory.reversed()
    } else {
        eventHistory.reversed().filter { it.type == selectedFilter }
    }

    // Statistics
    val positiveCount = eventHistory.count { it.type == EventType.POSITIVE }
    val negativeCount = eventHistory.count { it.type == EventType.NEGATIVE }
    val choiceCount = eventHistory.count { it.type == EventType.CHOICE }
    val milestoneCount = eventHistory.count { it.type == EventType.MILESTONE }
    val totalCount = eventHistory.size

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = Primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("事件记录")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Event statistics summary
            item {
                EventStatisticsSummary(
                    totalCount = totalCount,
                    positiveCount = positiveCount,
                    negativeCount = negativeCount,
                    choiceCount = choiceCount,
                    milestoneCount = milestoneCount
                )
            }

            // Category filter chips
            item {
                EventFilterRow(
                    selectedFilter = selectedFilter,
                    onFilterChanged = { selectedFilter = it },
                    positiveCount = positiveCount,
                    negativeCount = negativeCount,
                    choiceCount = choiceCount,
                    milestoneCount = milestoneCount
                )
            }

            // Empty state
            if (filteredEvents.isEmpty()) {
                item {
                    EmptyEventState(hasFilter = selectedFilter != null)
                }
            }

            // Event timeline list
            itemsIndexed(filteredEvents) { index, event ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(300, delayMillis = index * 50)) +
                            slideInVertically(
                                animationSpec = tween(300, delayMillis = index * 50),
                                initialOffsetY = { it / 4 }
                            )
                ) {
                    EventTimelineCard(
                        event = event,
                        isFirst = index == 0,
                        isLast = index == filteredEvents.lastIndex
                    )
                }
            }

            // Bottom spacer
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun EventStatisticsSummary(
    totalCount: Int,
    positiveCount: Int,
    negativeCount: Int,
    choiceCount: Int,
    milestoneCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "事件总览",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "共 $totalCount 条",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatBadge(
                    icon = Icons.Default.TrendingUp,
                    label = "好事",
                    count = positiveCount,
                    color = AccentGreen
                )
                StatBadge(
                    icon = Icons.Default.TrendingDown,
                    label = "坏事",
                    count = negativeCount,
                    color = AccentRed
                )
                StatBadge(
                    icon = Icons.Default.Help,
                    label = "抉择",
                    count = choiceCount,
                    color = AccentOrange
                )
                StatBadge(
                    icon = Icons.Default.EmojiEvents,
                    label = "里程碑",
                    count = milestoneCount,
                    color = Primary
                )
            }

            // Positive/Negative ratio bar
            if (totalCount > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                val positiveRatio = positiveCount.toFloat() / totalCount
                val negativeRatio = negativeCount.toFloat() / totalCount

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "运势",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            if (positiveRatio > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(positiveRatio)
                                        .height(8.dp)
                                        .background(AccentGreen)
                                )
                            }
                            if (negativeRatio > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(negativeRatio)
                                        .height(8.dp)
                                        .background(AccentRed)
                                )
                            }
                            val otherRatio = 1f - positiveRatio - negativeRatio
                            if (otherRatio > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(otherRatio)
                                        .height(8.dp)
                                        .background(Color.Transparent)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    val luck = if (totalCount > 0) {
                        ((positiveCount - negativeCount).toFloat() / totalCount * 100).toInt()
                    } else 0
                    Text(
                        text = if (luck >= 0) "+$luck%" else "$luck%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (luck >= 0) AccentGreen else AccentRed
                    )
                }
            }
        }
    }
}

@Composable
private fun StatBadge(
    icon: ImageVector,
    label: String,
    count: Int,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventFilterRow(
    selectedFilter: EventType?,
    onFilterChanged: (EventType?) -> Unit,
    positiveCount: Int,
    negativeCount: Int,
    choiceCount: Int,
    milestoneCount: Int
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FilterChip(
            selected = selectedFilter == null,
            onClick = { onFilterChanged(null) },
            label = { Text("全部") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Primary.copy(alpha = 0.15f)
            )
        )
        FilterChip(
            selected = selectedFilter == EventType.POSITIVE,
            onClick = {
                onFilterChanged(if (selectedFilter == EventType.POSITIVE) null else EventType.POSITIVE)
            },
            label = { Text("好事 ($positiveCount)") },
            leadingIcon = if (selectedFilter == EventType.POSITIVE) {
                { Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(16.dp)) }
            } else null,
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = AccentGreen.copy(alpha = 0.15f)
            )
        )
        FilterChip(
            selected = selectedFilter == EventType.NEGATIVE,
            onClick = {
                onFilterChanged(if (selectedFilter == EventType.NEGATIVE) null else EventType.NEGATIVE)
            },
            label = { Text("坏事 ($negativeCount)") },
            leadingIcon = if (selectedFilter == EventType.NEGATIVE) {
                { Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp)) }
            } else null,
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = AccentRed.copy(alpha = 0.15f)
            )
        )
        FilterChip(
            selected = selectedFilter == EventType.CHOICE,
            onClick = {
                onFilterChanged(if (selectedFilter == EventType.CHOICE) null else EventType.CHOICE)
            },
            label = { Text("抉择 ($choiceCount)") },
            leadingIcon = if (selectedFilter == EventType.CHOICE) {
                { Icon(Icons.Default.Help, contentDescription = null, modifier = Modifier.size(16.dp)) }
            } else null,
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = AccentOrange.copy(alpha = 0.15f)
            )
        )
        FilterChip(
            selected = selectedFilter == EventType.MILESTONE,
            onClick = {
                onFilterChanged(if (selectedFilter == EventType.MILESTONE) null else EventType.MILESTONE)
            },
            label = { Text("里程碑 ($milestoneCount)") },
            leadingIcon = if (selectedFilter == EventType.MILESTONE) {
                { Icon(Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(16.dp)) }
            } else null,
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Primary.copy(alpha = 0.15f)
            )
        )
    }
}

@Composable
private fun EventTimelineCard(
    event: GameEvent,
    isFirst: Boolean,
    isLast: Boolean
) {
    val (icon, iconColor, bgColor) = when (event.type) {
        EventType.POSITIVE -> Triple(Icons.Default.ThumbUp, AccentGreen, AccentGreen.copy(alpha = 0.08f))
        EventType.NEGATIVE -> Triple(Icons.Default.Warning, AccentRed, AccentRed.copy(alpha = 0.08f))
        EventType.CHOICE -> Triple(Icons.Default.Help, AccentOrange, AccentOrange.copy(alpha = 0.08f))
        EventType.MILESTONE -> Triple(Icons.Default.EmojiEvents, Primary, Primary.copy(alpha = 0.08f))
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        // Timeline indicator
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(32.dp)
        ) {
            // Top connector line
            if (!isFirst) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(8.dp)
                        .background(iconColor.copy(alpha = 0.3f))
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Circle dot
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(iconColor),
                contentAlignment = Alignment.Center
            ) {}

            // Bottom connector line
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(52.dp)
                        .background(iconColor.copy(alpha = 0.3f))
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Event card content
        Card(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = bgColor),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Event icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Title row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = event.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        EventTypeBadge(event.type)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Message
                    Text(
                        text = event.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Effects summary
                    EventEffectsSummary(event)
                }
            }
        }
    }
}

@Composable
private fun EventTypeBadge(type: EventType) {
    val (text, color) = when (type) {
        EventType.POSITIVE -> "好事" to AccentGreen
        EventType.NEGATIVE -> "坏事" to AccentRed
        EventType.CHOICE -> "抉择" to AccentOrange
        EventType.MILESTONE -> "里程碑" to Primary
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun EventEffectsSummary(event: GameEvent) {
    val effects = mutableListOf<Pair<String, Color>>()

    when (event) {
        is GameEvent.PositiveEvent -> {
            if (event.bonusCash > 0.0) {
                effects.add("+${event.bonusCash.toInt()}万" to AccentGreen)
            }
            if (event.bonusReputation > 0L) {
                effects.add("+${event.bonusReputation}声誉" to AccentGreen)
            }
            if (event.bonusTeacherSkill > 0) {
                effects.add("+${event.bonusTeacherSkill}教学" to AccentGreen)
            }
        }
        is GameEvent.NegativeEvent -> {
            if (event.penaltyCash > 0.0) {
                effects.add("-${event.penaltyCash.toInt()}万" to AccentRed)
            }
            if (event.penaltyReputation > 0L) {
                effects.add("-${event.penaltyReputation}声誉" to AccentRed)
            }
        }
        is GameEvent.ChoiceEvent -> {
            effects.add("${event.choices.size}个选项" to AccentOrange)
        }
        is GameEvent.MilestoneEvent -> {
            effects.add("达成里程碑" to Primary)
        }
    }

    if (effects.isNotEmpty()) {
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            effects.forEach { (text, color) ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun EmptyEventState(hasFilter: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (hasFilter) "没有此类事件" else "暂无事件记录",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (hasFilter) "试试切换其他分类" else "继续经营学校，事件会陆续出现",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
