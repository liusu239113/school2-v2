package com.arktools.xiaozhang.ui.government

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.arktools.xiaozhang.R
import com.arktools.xiaozhang.domain.government.*
import com.arktools.xiaozhang.ui.components.PixelButton
import com.arktools.xiaozhang.ui.components.PixelButtonStyle

@Composable
fun GovernmentScreen(viewModel: GovernmentViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var showRectificationDialog by remember { mutableStateOf(false) }
    var selectedInspectionId by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 当前等级卡片
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF1565C0), Color(0xFF0D47A1))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Text("政府督导评估", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("当前评级", fontSize = 12.sp, color = Color.White.copy(0.8f))
                                Text(
                                    state.currentGrade.displayName,
                                    fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("连续优秀", fontSize = 12.sp, color = Color.White.copy(0.8f))
                                Text(
                                    "${state.consecutiveGoodGrades}次",
                                    fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            InfoChip("下次检查", "${state.nextInspectionMonth}月")
                            InfoChip("累计补贴", "¥${(state.subsidyReceived / 1000).toInt()}K")
                            InfoChip("累计罚款", "¥${(state.finesAccumulated / 1000).toInt()}K")
                        }
                    }
                }
            }
        }

        // 最近评分详情
        val latestScores = viewModel.getLatestInspectionScores()
        if (latestScores.isNotEmpty()) {
            item {
                Text("最近评估详情", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            items(latestScores) { score ->
                ScoreDimensionCard(score)
            }
        }

        // 整改任务
        val activeTasks = state.rectificationTasks.filter { !it.isCompleted }
        if (activeTasks.isNotEmpty()) {
            item {
                Text("进行中的整改 (${activeTasks.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            items(activeTasks) { task ->
                RectificationTaskCard(task)
            }
        }

        // 需要整改的检查
        val needRectification = state.inspections.filter { it.rectificationRequired && it.resultGrade != null }
            .takeLast(1)
        if (needRectification.isNotEmpty()) {
            item {
                val inspection = needRectification.first()
                PixelButton(
                    text = "启动整改措施",
                    style = PixelButtonStyle.PRIMARY,
                    onClick = {
                        selectedInspectionId = inspection.id
                        showRectificationDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 历史检查记录
        val history = state.inspections.takeLast(6).reversed()
        if (history.isNotEmpty()) {
            item {
                Text("检查历史", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            items(history) { inspection ->
                InspectionHistoryCard(inspection)
            }
        }

        // 最近事件
        if (state.recentEvents.isNotEmpty()) {
            item {
                Text("最近动态", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            items(state.recentEvents) { event ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Campaign, contentDescription = null, tint = Color(0xFF1565C0), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(event, fontSize = 14.sp)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }

    if (showRectificationDialog) {
        RectificationDialog(
            availableTypes = viewModel.getAvailableRectifications(selectedInspectionId),
            onSelect = { type ->
                viewModel.startRectification(type, selectedInspectionId, 1)
                showRectificationDialog = false
            },
            onDismiss = { showRectificationDialog = false }
        )
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(label, fontSize = 10.sp, color = Color.White.copy(0.8f))
    }
}

@Composable
private fun ScoreDimensionCard(score: InspectionScore) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(score.dimension.displayName, fontWeight = FontWeight.Medium)
                Text(
                    "${score.score.toInt()}分",
                    color = when {
                        score.score >= 80 -> Color(0xFF388E3C)
                        score.score >= 60 -> Color(0xFFF57C00)
                        else -> Color(0xFFD32F2F)
                    },
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { score.score / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = when {
                    score.score >= 80 -> Color(0xFF388E3C)
                    score.score >= 60 -> Color(0xFFF57C00)
                    else -> Color(0xFFD32F2F)
                }
            )
            Spacer(Modifier.height(4.dp))
            Text(score.comment, fontSize = 12.sp, color = Color.Gray)
            score.issues.forEach { issue ->
                Text("• $issue", fontSize = 11.sp, color = Color(0xFFD32F2F))
            }
        }
    }
}

@Composable
private fun RectificationTaskCard(task: RectificationTask) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(task.type.displayName, fontWeight = FontWeight.Medium)
                Text("${task.progress.toInt()}%", fontWeight = FontWeight.Bold,
                    color = if (task.progress >= 80) Color(0xFF388E3C) else Color(0xFFF57C00))
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { task.progress / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = Color(0xFF1565C0)
            )
            Text("费用: ¥${task.cost.toInt()}", fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun InspectionHistoryCard(inspection: Inspection) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(inspection.resultGrade?.color ?: 0xFF9E9E9E).copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("${inspection.year}年${inspection.month}月 ${inspection.type.displayName}",
                    fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text("总分: ${inspection.totalScore.toInt()}", fontSize = 12.sp, color = Color.Gray)
            }
            Text(
                inspection.resultGrade?.displayName ?: "待评",
                fontWeight = FontWeight.Bold,
                color = Color(inspection.resultGrade?.color ?: 0xFF9E9E9E)
            )
        }
    }
}

@Composable
private fun RectificationDialog(
    availableTypes: List<RectificationType>,
    onSelect: (RectificationType) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
        ) {
            Image(
                painter = painterResource(id = R.drawable.dialog_bg),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.FillBounds
            )
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "选择整改措施",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (availableTypes.isEmpty()) {
                        Text("所有整改措施已启动", color = Color.Gray)
                    } else {
                        availableTypes.forEach { type ->
                            Card(
                                onClick = { onSelect(type) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(type.displayName, fontWeight = FontWeight.Bold)
                                    Text(
                                        "费用¥${type.cost.toInt()} | 评分提升+${type.improvementScore.toInt()}",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PixelButton(
                        text = "取消",
                        style = PixelButtonStyle.CANCEL,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
