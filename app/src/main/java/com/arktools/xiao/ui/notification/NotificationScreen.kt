package com.arktools.xiao.ui.notification

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arktools.xiao.domain.model.GameNotification
import com.arktools.xiao.domain.model.NotificationPriority
import com.arktools.xiao.domain.model.NotificationType
import com.arktools.xiao.ui.components.LegacyPageHeader
import com.arktools.xiao.ui.components.PixelButton
import com.arktools.xiao.ui.components.PixelButtonStyle
import com.arktools.xiao.ui.components.PixelGameBackground
import com.arktools.xiao.ui.components.PixelHardPanel
import com.arktools.xiao.ui.theme.AccentOrange
import com.arktools.xiao.ui.theme.AccentRed
import com.arktools.xiao.ui.theme.TextPrimaryDark
import com.arktools.xiao.ui.theme.TextSecondaryDark

@Composable
fun NotificationScreen(
    viewModel: NotificationViewModel = hiltViewModel(),
    onNavigateToTab: (Int) -> Unit = {}
) {
    val notifications by viewModel.notifications.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState()
    var selectedFilter by remember { mutableStateOf<NotificationType?>(null) }
    var expandedId by remember { mutableStateOf<String?>(null) }

    val filtered = if (selectedFilter != null) {
        notifications.filter { it.type == selectedFilter }
    } else {
        notifications
    }

    PixelGameBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            LegacyPageHeader("校长待办")
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("点开就能跳到要处理的系统", color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        if (unreadCount > 0) {
                            Text("$unreadCount 条未读", color = AccentOrange, fontSize = 12.sp)
                        }
                    }
                    if (unreadCount > 0) {
                        PixelButton(
                            text = "全部已读",
                            onClick = { viewModel.markAllAsRead() },
                            style = PixelButtonStyle.SECONDARY,
                            height = 36.dp
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    FilterBtn("全部", selectedFilter == null, Modifier.weight(1f)) { selectedFilter = null }
                    FilterBtn("财务", selectedFilter == NotificationType.FINANCIAL, Modifier.weight(1f)) {
                        selectedFilter = if (selectedFilter == NotificationType.FINANCIAL) null else NotificationType.FINANCIAL
                    }
                    FilterBtn("教师", selectedFilter == NotificationType.TEACHER, Modifier.weight(1f)) {
                        selectedFilter = if (selectedFilter == NotificationType.TEACHER) null else NotificationType.TEACHER
                    }
                    FilterBtn("危机", selectedFilter == NotificationType.CRISIS, Modifier.weight(1f)) {
                        selectedFilter = if (selectedFilter == NotificationType.CRISIS) null else NotificationType.CRISIS
                    }
                }
                Spacer(Modifier.height(8.dp))

                if (filtered.isEmpty()) {
                    PixelHardPanel {
                        Text(if (selectedFilter != null) "该分类暂无待办" else "暂无待办", color = TextPrimaryDark, fontWeight = FontWeight.Bold)
                        Text("投诉、缺奖学金、就业和科研经费会进这里。点卡片再点跳转。", color = TextSecondaryDark, fontSize = 12.sp)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(filtered, key = { _, item -> item.id }) { _, notification ->
                            TodoCard(
                                notification = notification,
                                expanded = expandedId == notification.id,
                                onToggle = {
                                    viewModel.markAsRead(notification.id)
                                    expandedId = if (expandedId == notification.id) null else notification.id
                                },
                                onJump = {
                                    viewModel.markAsRead(notification.id)
                                    notification.actionTabIndex?.let { onNavigateToTab(it) }
                                }
                            )
                        }
                        item { Spacer(Modifier.height(72.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterBtn(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    PixelButton(
        text = label,
        onClick = onClick,
        style = if (selected) PixelButtonStyle.PRIMARY else PixelButtonStyle.CANCEL,
        height = 32.dp,
        modifier = modifier
    )
}

@Composable
private fun TodoCard(
    notification: GameNotification,
    expanded: Boolean,
    onToggle: () -> Unit,
    onJump: () -> Unit
) {
    val priorityColor = when (notification.priority) {
        NotificationPriority.URGENT -> AccentRed
        NotificationPriority.HIGH -> AccentOrange
        else -> TextPrimaryDark
    }
    PixelHardPanel(modifier = Modifier.clickable(onClick = onToggle)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                notification.title,
                color = priorityColor,
                fontWeight = if (!notification.isRead) FontWeight.Bold else FontWeight.Normal,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (notification.gameYear > 0) {
                Text("${notification.gameYear}年${notification.gameMonth}月", color = TextSecondaryDark, fontSize = 11.sp)
            }
        }
        Text(
            notification.message,
            color = TextSecondaryDark,
            fontSize = 12.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis
        )
        if (!notification.isRead) {
            Text("未读", color = AccentOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        if (expanded && notification.actionTabIndex != null) {
            PixelButton(
                text = notification.actionLabel ?: "前往处理",
                onClick = onJump,
                style = PixelButtonStyle.PRIMARY,
                height = 40.dp,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (notification.actionTabIndex != null) {
            Text("点开后可跳转：${notification.actionLabel ?: "相关系统"}", color = Color(0xFF14648C), fontSize = 11.sp)
        }
    }
}
