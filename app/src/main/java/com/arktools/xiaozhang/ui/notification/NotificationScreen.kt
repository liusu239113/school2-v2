package com.arktools.xiaozhang.ui.notification

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arktools.xiaozhang.domain.model.GameNotification
import com.arktools.xiaozhang.domain.model.NotificationPriority
import com.arktools.xiaozhang.domain.model.NotificationType
import com.arktools.xiaozhang.ui.components.PixelIcon

@Composable
fun NotificationScreen(
    viewModel: NotificationViewModel = hiltViewModel(),
    onNavigateToTab: (Int) -> Unit = {}
) {
    val notifications by viewModel.notifications.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState()
    var selectedFilter by remember { mutableStateOf<NotificationType?>(null) }

    val filteredNotifications = if (selectedFilter != null) {
        notifications.filter { it.type == selectedFilter }
    } else {
        notifications
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "通知中心",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                if (unreadCount > 0) {
                    Text(
                        text = "$unreadCount 条未读",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (unreadCount > 0) {
                TextButton(onClick = { viewModel.markAllAsRead() }) {
                    Icon(
                        Icons.Default.DoneAll,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("全部已读")
                }
            }
        }

        // Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == null,
                onClick = { selectedFilter = null },
                label = { Text("全部") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
            FilterChip(
                selected = selectedFilter == NotificationType.FINANCIAL,
                onClick = { selectedFilter = if (selectedFilter == NotificationType.FINANCIAL) null else NotificationType.FINANCIAL },
                label = { Text("财务") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF2E7D32).copy(alpha = 0.15f)
                )
            )
            FilterChip(
                selected = selectedFilter == NotificationType.TEACHER,
                onClick = { selectedFilter = if (selectedFilter == NotificationType.TEACHER) null else NotificationType.TEACHER },
                label = { Text("教师") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF1565C0).copy(alpha = 0.15f)
                )
            )
            FilterChip(
                selected = selectedFilter == NotificationType.CRISIS,
                onClick = { selectedFilter = if (selectedFilter == NotificationType.CRISIS) null else NotificationType.CRISIS },
                label = { Text("危机") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFFC62828).copy(alpha = 0.15f)
                )
            )
        }

        if (filteredNotifications.isEmpty()) {
            EmptyNotificationState(selectedFilter)
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(
                    items = filteredNotifications,
                    key = { _, item -> item.id }
                ) { index, notification ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(200, delayMillis = index * 30)) +
                                slideInVertically(tween(200, delayMillis = index * 30)) { it / 4 }
                    ) {
                        NotificationCard(
                            notification = notification,
                            onClick = {
                                viewModel.markAsRead(notification.id)
                                notification.actionTabIndex?.let { onNavigateToTab(it) }
                            }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: GameNotification,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (!notification.isRead)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
        else
            MaterialTheme.colorScheme.surface,
        animationSpec = tween(300),
        label = "notifBg"
    )

    val (icon, iconColor) = getNotificationIcon(notification.type)
    val priorityColor = when (notification.priority) {
        NotificationPriority.URGENT -> Color(0xFFC62828)
        NotificationPriority.HIGH -> Color(0xFFE65100)
        NotificationPriority.NORMAL -> MaterialTheme.colorScheme.onSurface
        NotificationPriority.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (!notification.isRead) 2.dp else 0.dp
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Icon Badge
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (!notification.isRead) FontWeight.SemiBold else FontWeight.Normal,
                        color = priorityColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (notification.gameYear > 0) {
                        Text(
                            text = "${notification.gameYear}年${notification.gameMonth}月",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = notification.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Action button
                notification.actionLabel?.let { label ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.clickable(onClick = onClick)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Unread indicator
            if (!notification.isRead) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
private fun EmptyNotificationState(filter: NotificationType?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PixelIcon(emoji = "📭", size = 48.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (filter != null) "该分类暂无通知" else "暂无通知",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "继续经营学校，重要事件会在这里通知你",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun getNotificationIcon(type: NotificationType): Pair<ImageVector, Color> {
    return when (type) {
        NotificationType.FINANCIAL -> Icons.Default.AccountBalance to Color(0xFF2E7D32)
        NotificationType.TEACHER -> Icons.Default.Groups to Color(0xFF1565C0)
        NotificationType.STUDENT -> Icons.Default.School to Color(0xFF6A1B9A)
        NotificationType.COURSE -> Icons.Default.EmojiEvents to Color(0xFFE65100)
        NotificationType.MILESTONE -> Icons.Default.EmojiEvents to Color(0xFF6A1B9A)
        NotificationType.CRISIS -> Icons.Default.Warning to Color(0xFFC62828)
        NotificationType.COMPETITOR -> Icons.Default.Campaign to Color(0xFF00695C)
        NotificationType.MARKET -> Icons.Default.TrendingUp to Color(0xFF37474F)
    }
}
