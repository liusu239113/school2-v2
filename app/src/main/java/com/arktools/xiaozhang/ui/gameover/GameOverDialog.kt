package com.arktools.xiaozhang.ui.gameover

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arktools.xiaozhang.domain.engine.FailureCondition
import com.arktools.xiaozhang.domain.engine.GameOverReason
import com.arktools.xiaozhang.domain.engine.HealthReport
import com.arktools.xiaozhang.domain.engine.HealthStatus
import com.arktools.xiaozhang.ui.components.PixelIcon
import com.arktools.xiaozhang.ui.theme.AccentGreen
import com.arktools.xiaozhang.ui.theme.AccentOrange
import com.arktools.xiaozhang.ui.theme.AccentRed
import com.arktools.xiaozhang.ui.utils.FormatUtils

/**
 * 紧急救助弹窗 — 在CRITICAL状态时弹出
 * 玩家可选择接受救助或放弃(GameOver)
 */
@Composable
fun CrisisDialog(
    healthReport: HealthReport,
    conditions: List<FailureCondition>,
    onAcceptBailout: () -> Unit,
    onDeclineBailout: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { visible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(initialScale = 0.8f, animationSpec = tween(400)) +
                    fadeIn(animationSpec = tween(400))
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 警告图标
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = AccentRed
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "学校危机",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "你的学校正面临严重经营危机！",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 危机详情
                    CrisisConditionsList(conditions)

                    Spacer(modifier = Modifier.height(16.dp))

                    // 健康诊断
                    HealthDiagnostics(healthReport)

                    Spacer(modifier = Modifier.height(16.dp))

                    // 建议
                    if (healthReport.suggestions.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "改进建议",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                healthReport.suggestions.forEach { suggestion ->
                                    Text(
                                        text = "• $suggestion",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 操作按钮 - 看广告获得救助
                    if (healthReport.bailoutsRemaining > 0) {
                        Button(
                            onClick = onAcceptBailout,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentGreen
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "观看视频获得救助（剩余${healthReport.bailoutsRemaining}次）",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "观看短视频后将获得 +120万 +50声誉 帮助度过危机",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.6f)
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    OutlinedButton(
                        onClick = onDeclineBailout,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (healthReport.bailoutsRemaining > 0) "放弃经营" else "确认结束",
                            color = AccentRed
                        )
                    }
                }
            }
        }
    }
}

/**
 * GameOver 终结屏幕
 */
@Composable
fun GameOverScreen(
    reason: GameOverReason,
    onNewGame: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    var repentance by remember { mutableStateOf("") }
    val validCharacterCount = repentance.count { it.isLetterOrDigit() }
    LaunchedEffect(Unit) { visible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(initialScale = 0.7f, animationSpec = tween(600)) +
                    fadeIn(animationSpec = tween(600))
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(28.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PixelIcon(emoji = "🏫", size = 48.dp)

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "学校关闭",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = reason.primaryReason,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // 统计数据
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "经营记录",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            StatRow("经营年限", "${reason.totalYearsPlayed}年")
                            if (reason.schoolTypeName.isNotEmpty()) {
                                StatRow("办学类型", reason.schoolTypeName)
                            }
                            StatRow("培养毕业生", "${reason.totalStudentsGraduated}人")
                            StatRow("巅峰声誉", "${reason.peakReputation}")
                            StatRow("巅峰资金", FormatUtils.formatCash(reason.peakCash))
                            StatRow("最终资金", FormatUtils.formatCash(reason.finalCash))
                            StatRow("最终声誉", "${reason.finalReputation}")
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "重新经营前，请写一份不少于100字的悔过书。标点、空格和换行不计入字数。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = repentance,
                        onValueChange = { repentance = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        label = { Text("悔过书") },
                        supportingText = {
                            Text("有效字数：$validCharacterCount / 100")
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onNewGame,
                        enabled = validCharacterCount >= 100,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("重新开始", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CrisisConditionsList(conditions: List<FailureCondition>) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "危机详情",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = AccentRed
            )
            Spacer(modifier = Modifier.height(8.dp))
            conditions.forEach { condition ->
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = getConditionIcon(condition),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = getConditionDescription(condition),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun HealthDiagnostics(report: HealthReport) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        HealthIndicator("资金", report.cashStatus)
        HealthIndicator("声誉", report.reputationStatus)
        HealthIndicator("师资", report.teacherStatus)
        HealthIndicator("生源", report.studentStatus)
    }
}

@Composable
private fun HealthIndicator(label: String, status: HealthStatus) {
    val color = when (status) {
        HealthStatus.GOOD -> AccentGreen
        HealthStatus.FAIR -> AccentOrange
        HealthStatus.POOR -> AccentRed.copy(alpha = 0.7f)
        HealthStatus.CRITICAL -> AccentRed
    }
    val statusText = when (status) {
        HealthStatus.GOOD -> "良好"
        HealthStatus.FAIR -> "一般"
        HealthStatus.POOR -> "糟糕"
        HealthStatus.CRITICAL -> "危急"
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun getConditionIcon(condition: FailureCondition): String = when (condition) {
    FailureCondition.SUSTAINED_LOSSES -> "亏损"
    FailureCondition.BANKRUPT -> "破产"
    FailureCondition.REPUTATION_COLLAPSE -> "声誉"
    FailureCondition.ALL_TEACHERS_QUIT -> "离职"
    FailureCondition.NO_STUDENTS -> "退学"
    FailureCondition.PRINCIPAL_ARRESTED -> "逮捕"
}

private fun getConditionDescription(condition: FailureCondition): String = when (condition) {
    FailureCondition.SUSTAINED_LOSSES -> "学校连续亏损，入不敷出"
    FailureCondition.BANKRUPT -> "资金已跌破破产线"
    FailureCondition.REPUTATION_COLLAPSE -> "声誉持续低迷，无人问津"
    FailureCondition.ALL_TEACHERS_QUIT -> "教师全部离职，无力招聘"
    FailureCondition.NO_STUDENTS -> "学生全部流失，校园空空"
    FailureCondition.PRINCIPAL_ARRESTED -> "校长因贪腐被逮捕入狱，学校公款冻结"
}
