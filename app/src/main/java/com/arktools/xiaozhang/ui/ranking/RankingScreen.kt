package com.arktools.xiaozhang.ui.ranking

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arktools.xiaozhang.domain.competitor.CompetitorPersonality
import com.arktools.xiaozhang.domain.competitor.CompetitorStrategy
import com.arktools.xiaozhang.ui.theme.AccentGreen
import com.arktools.xiaozhang.ui.theme.AccentOrange
import com.arktools.xiaozhang.ui.theme.AccentRed
import com.arktools.xiaozhang.ui.animation.AnimationConstants
import com.arktools.xiaozhang.ui.animation.cardTapAnimation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingScreen(
    viewModel: RankingViewModel = hiltViewModel()
) {
    val rankings by viewModel.rankings.collectAsState()
    val currentSchool by viewModel.currentSchool.collectAsState()
    val playerRank by viewModel.playerRank.collectAsState()
    val stockPrice by viewModel.stockPrice.collectAsState()
    val priceChange by viewModel.priceChange.collectAsState()
    val selectedCompetitor by viewModel.selectedCompetitor.collectAsState()
    val competitiveEdge by viewModel.competitiveEdge.collectAsState()

    // Competitor detail bottom sheet
    val capturedCompetitor = selectedCompetitor
    if (capturedCompetitor != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissCompetitorDetail() },
            sheetState = sheetState
        ) {
            CompetitorDetailSheet(detail = capturedCompetitor)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "竞争排行",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "${rankings.size} 所学校",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 玩家排名摘要
            item {
                PlayerRankSummaryCard(
                    schoolName = currentSchool?.name ?: "我的学校",
                    rank = playerRank,
                    totalCompetitors = rankings.size,
                    stockPrice = stockPrice,
                    priceChange = priceChange
                )
            }

            // 竞争力分析面板 - 展示教研加成对排名的推动力
            item {
                CompetitiveEdgeCard(edge = competitiveEdge)
            }

            item {
                Text(
                    text = "教育巨头排行",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Text(
                    text = "点击对手查看详情",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 排行榜列表
            itemsIndexed(rankings) { index, item ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(
                        animationSpec = tween(
                            AnimationConstants.defaultDuration,
                            delayMillis = index * AnimationConstants.entranceDelay + 100
                        )
                    ) + slideInVertically(
                        animationSpec = tween(
                            AnimationConstants.defaultDuration,
                            delayMillis = index * AnimationConstants.entranceDelay + 100
                        ),
                        initialOffsetY = { it / 8 }
                    )
                ) {
                    RankingItemCard(
                        item = item,
                        onClick = { viewModel.selectCompetitor(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerRankSummaryCard(
    schoolName: String,
    rank: Int,
    totalCompetitors: Int,
    stockPrice: Double,
    priceChange: Double
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .cardTapAnimation(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = schoolName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "当前排名",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = when (rank) {
                            1 -> Color(0xFFFFD700) // 金色
                            2 -> Color(0xFFC0C0C0) // 银色
                            3 -> Color(0xFFCD7F32) // 铜色
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "#$rank",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = " / $totalCompetitors",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StockStatItem("股价", String.format("%.2f", stockPrice))
                StockStatItem(
                    "涨跌幅",
                    "${if (priceChange >= 0) "+" else ""}${String.format("%.1f", priceChange)}%",
                    priceChange >= 0
                )
                StockStatItem(
                    "排名趋势",
                    if (rank <= 3) "领先" else "追赶中"
                )
            }
        }
    }
}

@Composable
private fun StockStatItem(label: String, value: String, isPositive: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = if (isPositive) AccentGreen else MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun RankingItemCard(item: RankingViewModel.RankingItem, onClick: () -> Unit = {}) {
    val isPlayer = item.isPlayer
    val borderColor = if (isPlayer) MaterialTheme.colorScheme.primary else Color.Transparent
    val bgColor = if (isPlayer) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isPlayer) { onClick() }
            .then(
                if (isPlayer) Modifier.border(
                    width = 2.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(12.dp)
                ) else Modifier
            ),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPlayer) 4.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 排名
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        when (item.rank) {
                            1 -> Color(0xFFFFD700)
                            2 -> Color(0xFFC0C0C0)
                            3 -> Color(0xFFCD7F32)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
            ) {
                Text(
                    text = "${item.rank}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (item.rank <= 3) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isPlayer) FontWeight.Bold else FontWeight.Normal
                    )
                    if (isPlayer) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "你",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (item.strategy != null && !isPlayer) {
                        Spacer(modifier = Modifier.width(6.dp))
                        StrategyTag(strategy = item.strategy)
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "声誉: ${item.reputation}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "学生: ${item.studentCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 星级
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = AccentOrange,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = String.format("%.1f", item.starRating),
                    style = MaterialTheme.typography.titleSmall,
                    color = AccentOrange
                )
            }
        }
    }
}

@Composable
private fun StrategyTag(strategy: CompetitorStrategy) {
    val (color, text) = when (strategy) {
        CompetitorStrategy.AGGRESSIVE -> Pair(AccentRed, "激进")
        CompetitorStrategy.STEADY -> Pair(AccentGreen, "稳健")
        CompetitorStrategy.QUALITY -> Pair(Color(0xFF9C27B0), "精品")
        CompetitorStrategy.BUDGET -> Pair(AccentOrange, "低价")
        CompetitorStrategy.INNOVATION -> Pair(Color(0xFF2196F3), "创新")
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    )
}

// ===== 竞争对手详情底部弹窗 =====

@Composable
private fun CompetitorDetailSheet(detail: RankingViewModel.CompetitorDetail) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // 头部：名称 + 排名
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = detail.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "「${detail.motto}」",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        when (detail.rank) {
                            1 -> Color(0xFFFFD700)
                            2 -> Color(0xFFC0C0C0)
                            3 -> Color(0xFFCD7F32)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
            ) {
                Text(
                    text = "#${detail.rank}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (detail.rank <= 3) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 策略与性格标签
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val strategyColor = when (detail.strategy) {
                CompetitorStrategy.AGGRESSIVE -> AccentRed
                CompetitorStrategy.STEADY -> AccentGreen
                CompetitorStrategy.QUALITY -> Color(0xFF9C27B0)
                CompetitorStrategy.BUDGET -> AccentOrange
                CompetitorStrategy.INNOVATION -> Color(0xFF2196F3)
            }
            Box(
                modifier = Modifier
                    .background(strategyColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .border(1.dp, strategyColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "策略: ${detail.strategy.displayName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = strategyColor,
                    fontWeight = FontWeight.SemiBold
                )
            }

            val personalityColor = when (detail.personality) {
                CompetitorPersonality.FRIENDLY -> AccentGreen
                CompetitorPersonality.NEUTRAL -> Color.Gray
                CompetitorPersonality.HOSTILE -> AccentRed
                CompetitorPersonality.CUNNING -> Color(0xFFFF9800)
            }
            Box(
                modifier = Modifier
                    .background(personalityColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .border(1.dp, personalityColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "性格: ${detail.personality.displayName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = personalityColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // 核心数据
        Text(
            text = "学校数据",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CompetitorStatItem(
                icon = Icons.Default.Star,
                label = "声誉",
                value = "${detail.reputation}",
                color = AccentOrange
            )
            CompetitorStatItem(
                icon = Icons.Default.People,
                label = "学生",
                value = "${detail.studentCount}",
                color = Color(0xFF2196F3)
            )
            CompetitorStatItem(
                icon = Icons.Default.MenuBook,
                label = "班级",
                value = "${detail.courseCount}",
                color = MaterialTheme.colorScheme.primary
            )
            CompetitorStatItem(
                icon = Icons.Default.Groups,
                label = "教师",
                value = "${detail.teacherCount}",
                color = AccentGreen
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CompetitorStatItem(
                icon = Icons.Default.School,
                label = "校舍",
                value = "Lv.${detail.campusLevel}",
                color = Color(0xFF607D8B)
            )
            CompetitorStatItem(
                icon = Icons.Default.Star,
                label = "星级",
                value = String.format("%.1f星", detail.starRating),
                color = AccentOrange
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 士气进度条
        Text(
            text = "竞争士气",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LinearProgressIndicator(
                progress = { detail.morale },
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = when {
                    detail.morale >= 0.7f -> AccentGreen
                    detail.morale >= 0.4f -> AccentOrange
                    else -> AccentRed
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "${(detail.morale * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = when {
                    detail.morale >= 0.7f -> AccentGreen
                    detail.morale >= 0.4f -> AccentOrange
                    else -> AccentRed
                }
            )
        }
        Text(
            text = when {
                detail.morale >= 0.8f -> "士气高涨，竞争意愿强"
                detail.morale >= 0.5f -> "状态正常，按策略稳步发展"
                else -> "士气低迷，可能放缓扩张"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 策略描述
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "策略分析",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = detail.strategy.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun CompetitorStatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
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

@Composable
private fun CompetitiveEdgeCard(edge: RankingViewModel.CompetitiveEdge) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "教研竞争力",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "${edge.unlockedCount}/${edge.totalCount} 项研究",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { edge.researchProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = AccentGreen,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Key bonuses in a compact grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                EdgeBonusChip("教学", edge.teachingQualityBonus, AccentGreen)
                EdgeBonusChip("招生", edge.enrollmentBonus, AccentOrange)
                EdgeBonusChip("收入", edge.revenueBonus, Color(0xFFFFD700))
                EdgeBonusChip("降本", edge.costReductionBonus, MaterialTheme.colorScheme.secondary)
            }

            if (edge.unlockedCount == 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "解锁教研项目可永久提升竞争力，助你超越对手！",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun EdgeBonusChip(label: String, value: Float, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (value > 0f) "+${(value * 100).toInt()}%" else "—",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (value > 0f) FontWeight.Bold else FontWeight.Normal,
            color = if (value > 0f) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
        )
    }
}
