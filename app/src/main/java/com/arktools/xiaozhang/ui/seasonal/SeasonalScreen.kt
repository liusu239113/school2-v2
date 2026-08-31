package com.arktools.xiaozhang.ui.seasonal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arktools.xiaozhang.domain.seasonal.*
import com.arktools.xiaozhang.ui.theme.AccentGreen
import com.arktools.xiaozhang.ui.theme.AccentOrange
import com.arktools.xiaozhang.ui.theme.AccentRed

@Composable
fun SeasonalScreen(
    viewModel: SeasonalViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 季节与年度统计头部
        item {
            SeasonHeader(
                currentSeason = state.currentSeason,
                yearlyStats = state.yearlyStats
            )
        }

        // 立即举办（专属小游戏玩法）
        item {
            val hostMessage by viewModel.hostMessage.collectAsState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text("立即举办（明天开幕，可玩小游戏）", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                viewModel.quickHostTypes.forEach { type ->
                    val costWan = type.baseCost / 10000.0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(type.displayName, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(
                                type.description,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        TextButton(onClick = { viewModel.hostActivity(type) }) {
                            Text("办 · " + costWan.toInt() + "万", fontSize = 12.sp)
                        }
                    }
                }
                hostMessage?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        it,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.consumeHostMessage() }
                    )
                }
            }
        }

        // 待审批活动（需要校长处理）
        val pendingActivities = state.activities.filter {
            it.phase == ActivityPhase.PENDING_APPROVAL
        }
        if (pendingActivities.isNotEmpty()) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "待审批",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Badge(
                        containerColor = AccentRed
                    ) {
                        Text(
                            text = "${pendingActivities.size}",
                            color = Color.White
                        )
                    }
                }
            }
            items(pendingActivities, key = { it.id }) { activity ->
                PendingApprovalCard(
                    activity = activity,
                    onApproveClick = { viewModel.triggerApproval(activity.id) }
                )
            }
        }

        // 已批准并进行中的活动
        val activeActivities = state.activities.filter {
            it.phase in listOf(ActivityPhase.PREPARING, ActivityPhase.ACTIVE)
        }
        if (activeActivities.isNotEmpty()) {
            item {
                Text(
                    text = "进行中",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(activeActivities, key = { it.id }) { activity ->
                ActiveActivityCard(activity = activity)
            }
        }

        // 已完成活动
        if (state.completedThisYear.isNotEmpty()) {
            item {
                Text(
                    text = "本年度已完成 (${state.completedThisYear.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(state.completedThisYear, key = { it.id }) { activity ->
                CompletedActivityCard(activity = activity)
            }
        }

        // 活动日历预览
        item {
            ActivityCalendarPreview()
        }

        // 空状态
        if (pendingActivities.isEmpty() && activeActivities.isEmpty() && state.completedThisYear.isEmpty()) {
            item {
                EmptySeasonalState()
            }
        }
    }
}

@Composable
private fun SeasonHeader(
    currentSeason: Season,
    yearlyStats: YearlyActivityStats
) {
    val seasonColor = when (currentSeason) {
        Season.SPRING -> Color(0xFF4CAF50)
        Season.SUMMER -> Color(0xFFFF9800)
        Season.AUTUMN -> Color(0xFFE65100)
        Season.WINTER -> Color(0xFF1976D2)
    }
    val seasonIcon = when (currentSeason) {
        Season.SPRING -> Icons.Default.Park
        Season.SUMMER -> Icons.Default.WbSunny
        Season.AUTUMN -> Icons.Default.Forest
        Season.WINTER -> Icons.Default.AcUnit
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = seasonColor.copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = seasonIcon,
                    contentDescription = null,
                    tint = seasonColor,
                    modifier = Modifier.size(36.dp)
                )
                Column {
                    Text(
                        text = currentSeason.displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = seasonColor
                    )
                    Text(
                        text = "季节活动管理（审批制）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (yearlyStats.totalActivities > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatChip(label = "已举办", value = "${yearlyStats.totalActivities}次")
                    StatChip(label = "总花费", value = "¥${yearlyStats.totalSpent / 1000}K")
                    StatChip(label = "声誉+", value = "${yearlyStats.totalReputationGained}")
                }
                if (yearlyStats.bestActivity != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "最佳活动: ${yearlyStats.bestActivity}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentGreen,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 待审批活动卡片 - 显示活动信息和剩余审批时间
 * 点击"审批"按钮重新触发 ChoiceEvent 弹窗
 */
@Composable
private fun PendingApprovalCard(
    activity: SeasonalActivity,
    onApproveClick: () -> Unit
) {
    val remainingDays = SeasonalActivityManager.APPROVAL_TIMEOUT_DAYS - activity.approvalWaitDays
    val urgencyColor = when {
        remainingDays <= 3 -> AccentRed
        remainingDays <= 7 -> AccentOrange
        else -> Color(0xFF2196F3)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = urgencyColor.copy(alpha = 0.05f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Assignment,
                        contentDescription = null,
                        tint = urgencyColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = activity.type.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = "待签字",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = urgencyColor.copy(alpha = 0.15f),
                        labelColor = urgencyColor
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = activity.type.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 活动信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    val costWan = activity.type.baseCost / 10000.0
                    Text(
                        text = "预算: ${String.format("%.1f", costWan)}万（标准）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "声誉: +${activity.type.baseReputationGain}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentGreen
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "剩余 ${remainingDays} 天",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = urgencyColor
                    )
                    Text(
                        text = "超时将自动过期",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 审批倒计时进度条
            val timeProgress = activity.approvalWaitDays.toFloat() / SeasonalActivityManager.APPROVAL_TIMEOUT_DAYS
            LinearProgressIndicator(
                progress = { timeProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = urgencyColor,
                trackColor = urgencyColor.copy(alpha = 0.1f)
            )

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onApproveClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = urgencyColor
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "签字审批")
            }
        }
    }
}

/**
 * 已批准的进行中活动卡片
 */
@Composable
private fun ActiveActivityCard(activity: SeasonalActivity) {
    val phaseColor = when (activity.phase) {
        ActivityPhase.PREPARING -> AccentOrange
        ActivityPhase.ACTIVE -> AccentGreen
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(phaseColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = activity.type.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = activity.scale.displayName,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = activity.phase.displayName,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = phaseColor.copy(alpha = 0.1f),
                            labelColor = phaseColor
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 进度条
            when (activity.phase) {
                ActivityPhase.PREPARING -> {
                    val progress = activity.preparationProgress.toFloat() / activity.type.preparationDays
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("筹备进度", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.width(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp),
                            color = AccentOrange
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "${activity.preparationProgress}/${activity.type.preparationDays}天",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                ActivityPhase.ACTIVE -> {
                    val progress = activity.durationProgress.toFloat() / activity.type.durationDays
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("活动进行", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.width(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp),
                            color = AccentGreen
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "${activity.durationProgress}/${activity.type.durationDays}天",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                else -> {}
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 费用预估
            val cost = (activity.type.baseCost * activity.scale.costMultiplier).toLong()
            val costWan = cost / 10000.0
            Text(
                text = "预算: ${String.format("%.1f", costWan)}万 · 已签字批准",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CompletedActivityCard(activity: SeasonalActivity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activity.type.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                val costWan = activity.actualCost / 10000.0
                Text(
                    text = "${activity.scale.displayName} · 花费${String.format("%.1f", costWan)}万",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                activity.specialOutcome?.let { outcome ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = outcome,
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentGreen,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "+${activity.reputationGained}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = AccentGreen
                )
                Text(
                    text = "声誉",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ActivityCalendarPreview() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "年度活动日历",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "活动到期时将发送审批通知，需校长签字批准后方可举办",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            val seasons = listOf(
                Season.SPRING to ActivityType.entries.filter { it.season == Season.SPRING },
                Season.SUMMER to ActivityType.entries.filter { it.season == Season.SUMMER },
                Season.AUTUMN to ActivityType.entries.filter { it.season == Season.AUTUMN },
                Season.WINTER to ActivityType.entries.filter { it.season == Season.WINTER }
            )

            seasons.forEach { (season, activities) ->
                val color = when (season) {
                    Season.SPRING -> Color(0xFF4CAF50)
                    Season.SUMMER -> Color(0xFFFF9800)
                    Season.AUTUMN -> Color(0xFFE65100)
                    Season.WINTER -> Color(0xFF1976D2)
                }
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(color, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${season.displayName}: ",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                    Text(
                        text = activities.joinToString("、") { "${it.triggerMonth}月${it.displayName}" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySeasonalState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Event,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "暂无活动",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "季节活动到期时将以审批通知形式送达，\n需校长签字批准后方可举办。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
