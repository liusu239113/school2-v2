package com.arktools.xiao.ui.studentlife

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arktools.xiao.domain.studentlife.*

@Composable
fun StudentLifeScreen(
    viewModel: StudentLifeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val actionMessage by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(actionMessage) {
        actionMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { padding ->
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 总览卡片
        item {
            OverallSatisfactionCard(state)
        }

        // 影响指标
        item {
            ImpactIndicatorCard(state)
        }

        // 四大设施卡片
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "生活设施",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                val hasNeedRepair = state.facilities.values.any { it.maintenanceLevel < 100f }
                FilledTonalButton(
                    onClick = { viewModel.repairAllFacilities() },
                    enabled = hasNeedRepair,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Build, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("一键维修", fontSize = 12.sp)
                }
            }
        }

        items(LifeAspect.entries) { aspect ->
            val facility = state.facilities[aspect]
            val score = state.satisfactionScores[aspect]
            if (facility != null) {
                FacilityCard(
                    facility = facility,
                    score = score,
                    canUpgrade = viewModel.canUpgradeFacility(aspect),
                    upgradeCost = viewModel.getUpgradeCost(aspect),
                    expandCost = viewModel.getExpandCost(aspect, 20),
                    onUpgrade = { viewModel.upgradeFacility(aspect) },
                    onRepair = { viewModel.repairFacility(aspect) },
                    onExpand = { viewModel.expandCapacity(aspect, 20) }
                )
            }
        }

        // 特色项目
        item {
            Text(
                "特色项目",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            ProgramsCard(state, viewModel)
        }

        // 当前问题
        if (state.issues.any { !it.resolved }) {
            item {
                Text(
                    "当前问题",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }

            item {
                IssuesCard(state.issues.filter { !it.resolved })
            }
        }

        // 底部间距
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
    } // Scaffold
}

@Composable
private fun OverallSatisfactionCard(state: StudentLifeState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF00897B), Color(0xFF26A69A))
                    ),
                    shape = RoundedCornerShape(16.dp)
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
                            "学生生活质量",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "月度开支: ¥${state.monthlyExpenses}万/月",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${state.overallSatisfaction.toInt()}",
                            color = Color.White,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "总满意度",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 四维度进度条
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LifeAspect.entries.forEach { aspect ->
                        val score = state.satisfactionScores[aspect]
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                aspect.icon,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { (score?.score ?: 50f) / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "${score?.score?.toInt() ?: 50}",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImpactIndicatorCard(state: StudentLifeState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ImpactChip(
                label = "学业影响",
                value = state.academicImpact,
                icon = Icons.Default.School
            )
            ImpactChip(
                label = "留存影响",
                value = state.retentionImpact,
                icon = Icons.Default.Groups
            )
            ImpactChip(
                label = "未解决问题",
                value = state.issues.count { !it.resolved }.toFloat(),
                icon = Icons.Default.Warning,
                isCount = true
            )
        }
    }
}

@Composable
private fun ImpactChip(
    label: String,
    value: Float,
    icon: ImageVector,
    isCount: Boolean = false
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (isCount) {
                if (value > 0) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)
            } else {
                if (value >= 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
            },
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            if (isCount) "${value.toInt()}" else "${if (value >= 0) "+" else ""}${value.toInt()}%",
            fontWeight = FontWeight.Bold,
            color = if (isCount) {
                if (value > 0) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)
            } else {
                if (value >= 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
            }
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FacilityCard(
    facility: LifeFacility,
    score: LifeSatisfactionScore?,
    canUpgrade: Boolean = true,
    upgradeCost: Long = 0L,
    expandCost: Long = 0L,
    onUpgrade: () -> Unit,
    onRepair: () -> Unit,
    onExpand: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 头部：名称 + 等级
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(facility.aspect.icon, fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            facility.aspect.displayName,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            facility.quality.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = qualityColor(facility.quality)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${score?.score?.toInt() ?: 0}分",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = satisfactionColor(score?.score ?: 0f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 维护度进度条
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("维护度", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(48.dp))
                LinearProgressIndicator(
                    progress = { facility.maintenanceLevel / 100f },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = maintenanceColor(facility.maintenanceLevel),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "${facility.maintenanceLevel.toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 容量信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("容量", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(48.dp))
                val loadPercent = if (facility.capacity > 0) {
                    (facility.currentLoad * 100) / facility.capacity
                } else 100
                LinearProgressIndicator(
                    progress = { (facility.currentLoad.toFloat() / facility.capacity.coerceAtLeast(1)).coerceAtMost(1.5f) / 1.5f },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (loadPercent > 100) MaterialTheme.colorScheme.error else Color(0xFF42A5F5),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "${facility.currentLoad}/${facility.capacity}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (loadPercent > 100) MaterialTheme.colorScheme.error else Color.Unspecified
                )
            }

            // 问题标签
            if (score != null && score.issues.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    score.issues.forEach { issue ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                issue,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isMaxQuality = facility.quality == FacilityQuality.PREMIUM
                OutlinedButton(
                    onClick = { onUpgrade() },
                    modifier = Modifier.weight(1f),
                    enabled = canUpgrade && !isMaxQuality,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.ArrowUpward, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (isMaxQuality) "已满级"
                        else if (!canUpgrade) "等级不足"
                        else "升级 ¥${upgradeCost}万",
                        fontSize = 11.sp
                    )
                }
                OutlinedButton(
                    onClick = { onRepair() },
                    modifier = Modifier.weight(1f),
                    enabled = facility.maintenanceLevel < 80f,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Build, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("维修", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { onExpand() },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+20人 ¥${expandCost}万", fontSize = 11.sp)
                }
            }

            // 月度维护费
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "月维护费: ¥${facility.monthlyMaintenanceCost}万/月 · 员工: ${facility.staffCount}人",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProgramsCard(state: StudentLifeState, viewModel: StudentLifeViewModel) {
    val activePrograms = state.programs.filter { it.active }
    val availablePrograms = viewModel.getAvailablePrograms()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 已激活项目
            if (activePrograms.isNotEmpty()) {
                Text(
                    "已开设 (${activePrograms.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF4CAF50)
                )
                Spacer(modifier = Modifier.height(8.dp))
                activePrograms.forEach { program ->
                    ProgramRow(
                        program = program,
                        isActive = true,
                        onToggle = { viewModel.deactivateProgram(program.id) }
                    )
                }
            }

            if (activePrograms.isNotEmpty() && availablePrograms.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            }

            // 可开设项目
            if (availablePrograms.isNotEmpty()) {
                Text(
                    "可开设 (${availablePrograms.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                availablePrograms.forEach { program ->
                    ProgramRow(
                        program = program,
                        isActive = false,
                        onToggle = { viewModel.activateProgram(program.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgramRow(
    program: SpecialProgram,
    isActive: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                program.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                "${program.aspect.displayName} · ¥${program.monthlyCost}万/月 · +${program.satisfactionBoost.toInt()}满意度",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isActive) {
            FilledTonalButton(
                onClick = onToggle,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text("关闭", fontSize = 12.sp)
            }
        } else {
            Button(
                onClick = onToggle,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("开设", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun IssuesCard(issues: List<LifeIssue>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            issues.forEach { issue ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = severityColor(issue.severity)
                    ) {
                        Text(
                            issue.severity.displayName,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${issue.aspect.icon} ${issue.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            issue.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "-${issue.satisfactionPenalty.toInt()}",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                if (issue != issues.last()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }
}

// 辅助颜色函数
@Composable
private fun qualityColor(quality: FacilityQuality): Color = when (quality) {
    FacilityQuality.POOR -> MaterialTheme.colorScheme.error
    FacilityQuality.BASIC -> Color(0xFF9E9E9E)
    FacilityQuality.STANDARD -> Color(0xFF42A5F5)
    FacilityQuality.GOOD -> Color(0xFF66BB6A)
    FacilityQuality.EXCELLENT -> Color(0xFFAB47BC)
    FacilityQuality.PREMIUM -> Color(0xFFFFD700)
}

private fun satisfactionColor(score: Float): Color = when {
    score >= 80f -> Color(0xFF4CAF50)
    score >= 60f -> Color(0xFF8BC34A)
    score >= 40f -> Color(0xFFFF9800)
    else -> Color(0xFFF44336)
}

private fun maintenanceColor(level: Float): Color = when {
    level >= 70f -> Color(0xFF4CAF50)
    level >= 40f -> Color(0xFFFF9800)
    else -> Color(0xFFF44336)
}

private fun severityColor(severity: IssueSeverity): Color = when (severity) {
    IssueSeverity.LOW -> Color(0xFFFFC107)
    IssueSeverity.MEDIUM -> Color(0xFFFF9800)
    IssueSeverity.HIGH -> Color(0xFFF44336)
    IssueSeverity.CRITICAL -> Color(0xFF9C27B0)
}
