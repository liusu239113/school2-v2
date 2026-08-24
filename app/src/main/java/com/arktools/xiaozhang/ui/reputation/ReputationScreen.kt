package com.arktools.xiaozhang.ui.reputation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arktools.xiaozhang.domain.reputation.*

@Composable
fun ReputationScreen(
    viewModel: ReputationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 顶部总览卡片
        item {
            ReputationHeaderCard(state)
        }

        // 各维度详情
        item {
            Text(
                "声誉维度详情",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(ReputationDimension.entries) { dimension ->
            val dimData = state.dimensions[dimension]
            if (dimData != null) {
                DimensionCard(
                    dimData = dimData,
                    isDominant = dimension == state.dominantDimension,
                    isWeakest = dimension == state.weakestDimension
                )
            }
        }

        // 均衡度分析
        item {
            BalanceAnalysisCard(state)
        }

        // 最近变动记录
        if (state.recentEvents.isNotEmpty()) {
            item {
                Text(
                    "近期声誉变动",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(state.recentEvents.reversed().take(10)) { event ->
                ReputationEventRow(event)
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun ReputationHeaderCard(state: ReputationBreakdown) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF6A1B9A), Color(0xFF4A148C))
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "学校声誉",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            state.schoolTitle,
                            color = Color(0xFFCE93D8),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${state.totalReputation}",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val changeColor = if (state.monthlyChange >= 0) Color(0xFFA5D6A7) else Color(0xFFEF9A9A)
                        Text(
                            "${if (state.monthlyChange >= 0) "+" else ""}${state.monthlyChange}/月",
                            color = changeColor,
                            fontSize = 13.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // 五维雷达简要
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    state.dimensions.forEach { (dim, data) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(dim.icon, fontSize = 20.sp)
                            Text(
                                "Lv.${data.level}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                dim.displayName.take(2),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DimensionCard(
    dimData: DimensionReputation,
    isDominant: Boolean,
    isWeakest: Boolean
) {
    val dimensionColor = when (dimData.dimension) {
        ReputationDimension.ACADEMIC -> Color(0xFF1565C0)
        ReputationDimension.SPORTS -> Color(0xFF2E7D32)
        ReputationDimension.ARTS -> Color(0xFFE65100)
        ReputationDimension.SOCIAL_SERVICE -> Color(0xFF6A1B9A)
        ReputationDimension.MANAGEMENT -> Color(0xFF37474F)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDominant) dimensionColor.copy(alpha = 0.05f)
            else MaterialTheme.colorScheme.surface
        ),
        border = if (isDominant) androidx.compose.foundation.BorderStroke(
            2.dp, dimensionColor.copy(alpha = 0.3f)
        ) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(dimData.dimension.icon, fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                dimData.dimension.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (isDominant) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Badge(containerColor = dimensionColor) {
                                    Text("优势", fontSize = 9.sp, color = Color.White)
                                }
                            }
                            if (isWeakest) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Badge(containerColor = Color(0xFFF44336)) {
                                    Text("短板", fontSize = 9.sp, color = Color.White)
                                }
                            }
                        }
                        Text(
                            dimData.dimension.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "Lv.${dimData.level}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = dimensionColor
                    )
                    val growthText = if (dimData.monthlyGrowth >= 0)
                        "+${String.format("%.1f", dimData.monthlyGrowth)}"
                    else String.format("%.1f", dimData.monthlyGrowth)
                    Text(
                        "$growthText/月",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (dimData.monthlyGrowth >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 分数进度条
            val progress = (dimData.score % 50f) / 50f  // 当前等级内进度
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = dimensionColor,
                    trackColor = dimensionColor.copy(alpha = 0.15f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 动量指示
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "动量：${String.format("%.1f", dimData.momentum)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "总贡献：${dimData.totalContribution}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "里程碑：${dimData.milestones}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BalanceAnalysisCard(state: ReputationBreakdown) {
    val scores = state.dimensions.values.map { it.score }
    val maxScore = scores.maxOrNull() ?: 0f
    val minScore = scores.minOrNull() ?: 0f
    val balanceRatio = if (maxScore > 0) minScore / maxScore else 1f

    val (statusText, statusColor) = when {
        balanceRatio >= 0.8f -> "均衡发展" to Color(0xFF4CAF50)
        balanceRatio >= 0.6f -> "较为均衡" to Color(0xFF2196F3)
        balanceRatio >= 0.4f -> "发展偏科" to Color(0xFFFF9800)
        else -> "严重偏科" to Color(0xFFF44336)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "发展均衡度",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    statusText,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { balanceRatio.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            val bonusText = when {
                state.balanceBonus > 0 -> "均衡奖励：+${(state.balanceBonus * 100).toInt()}% 声誉成长"
                state.balanceBonus < 0 -> "偏科惩罚：${(state.balanceBonus * 100).toInt()}% 声誉成长"
                else -> "无额外加成"
            }
            Text(
                bonusText,
                style = MaterialTheme.typography.bodySmall,
                color = if (state.balanceBonus >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 建议
            val weakest = state.weakestDimension
            if (weakest != null && balanceRatio < 0.6f) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFF3E0)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "建议：加强「${weakest.displayName}」方面的投入，提升均衡度可获得额外声誉奖励",
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE65100)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReputationEventRow(event: ReputationEvent) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(event.dimension.icon, fontSize = 18.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    event.reason,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    event.dimension.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val changeColor = if (event.change >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
            Text(
                "${if (event.change >= 0) "+" else ""}${String.format("%.0f", event.change)}",
                color = changeColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}
