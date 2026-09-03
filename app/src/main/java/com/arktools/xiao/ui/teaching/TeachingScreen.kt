package com.arktools.xiao.ui.teaching

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arktools.xiao.domain.model.*
import com.arktools.xiao.domain.teaching.TeachingState
import com.arktools.xiao.ui.components.LegacyPageHeader
import com.arktools.xiao.ui.components.PixelGameBackground

/**
 * 教学管理主界面 — 替代旧的 CourseListScreen
 *
 * 布局：
 * - 顶部概览卡片（总班数、总容量、每月成本）
 * - 教学班容量
 * - 教学强度选择
 * - 作息政策开关
 * - 特殊项目管理
 */
@Composable
fun TeachingScreen(
    viewModel: TeachingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val classroomCount by viewModel.classroomCountFlow.collectAsState(initial = 0)
    val classroomCapacity by viewModel.classroomCapacityFlow.collectAsState(initial = 0)
    val maxClassesPerTier by viewModel.maxClassesPerTierFlow.collectAsState(initial = 10)
    val totalClasses = state.config.totalClasses
    // v2.8 修复：教室容量由 classroomCapacityFlow 统一计算（考虑教室等级加成）
    // 与 GameEngine 招生约束保持一致
    val isOverCapacity = totalClasses > classroomCapacity && classroomCapacity > 0

    PixelGameBackground {
        LegacyPageHeader("教学配置")
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        // === 概览卡片 ===
        item { OverviewCard(state) }

        // === 教室容量警告 ===
        if (isOverCapacity) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("⚠️", fontSize = 18.sp)
                        Text(
                            text = "教室不足！当前教室容量最多支持${classroomCapacity}个班（含等级加成），" +
                                    "已配置${totalClasses}个班。请到设施页面建造或升级教室。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // === 教学班容量 ===
        item { SectionTitle("教学班容量（教室${classroomCount}间，容量${classroomCapacity}个教学班）") }
        item { ClassDistributionSection(viewModel, state, maxClassesPerTier, classroomCapacity) }

        // === 教学强度 ===
        item { SectionTitle("教学强度") }
        item { IntensitySection(viewModel, state) }

        // === 学院培养质量 ===
        item { SectionTitle("学院培养质量（按报考大类与专业）") }
        item { CollegeQualitySection(viewModel) }

        // === 作息政策 ===
        item { SectionTitle("作息政策") }
        item { SchedulePolicySection(viewModel, state) }

        // === 特殊项目 ===
        item { SectionTitle("特殊项目") }
        item { SpecialProgramSection(viewModel, state) }

        // === 其他配置 ===
        item { SectionTitle("其他") }
        item { OtherConfigSection(viewModel, state) }

        // 底部留白
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
    } // PixelGameBackground
}

@Composable
private fun OverviewCard(state: TeachingState) {
    val config = state.config
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("教学概览", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("总班数", "${config.totalClasses}个")
                StatItem("总容量", "${config.totalCapacity}人")
                StatItem("月成本", "%.1f万".format(config.monthlyOperatingCost()))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("强度", config.intensity.displayName)
                StatItem("方向", config.subjectTrack.displayName)
                StatItem("作息", "${config.schedulePolicies.size}项")
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        modifier = Modifier.padding(top = 4.dp)
    )
}

// ========= 教学班容量 =========

@Composable
private fun ClassDistributionSection(viewModel: TeachingViewModel, state: TeachingState, maxPerTier: Int, classroomCapacity: Int) {
    val config = state.config
    val totalClasses = config.totalClasses
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "教学班数量决定全校招生容量（每年招入约三分之一新生）。这是大学教学班，不是高中重点班。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ClassTier.entries.forEach { tier ->
                val currentCount = config.classDistribution[tier] ?: 0
                // v2.8 修复：每种班型实际可配上限 = min(学校等级允许, 当前数 + 教室剩余容量)
                // 这确保总班级数不超过教室容量
                val remainingForThisTier = if (classroomCapacity > 0) {
                    currentCount + (classroomCapacity - totalClasses).coerceAtLeast(0)
                } else {
                    maxPerTier  // 无教室时不限制（初始状态）
                }
                val effectiveMax = minOf(maxPerTier, remainingForThisTier)
                ClassTierRow(
                    tier = tier,
                    count = currentCount,
                    maxCount = effectiveMax,
                    onCountChange = { viewModel.setClassCount(tier, it) }
                )
            }
        }
    }
}

@Composable
private fun ClassTierRow(tier: ClassTier, count: Int, maxCount: Int, onCountChange: (Int) -> Unit) {
    val atLimit = count >= maxCount
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(tier.displayName, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(
                "容量${tier.maxSize}人/班 | 月费${tier.monthlyCost}万/班 | " +
                        if (atLimit && count > 0) "已达上限" else "上限${maxCount}班",
                fontSize = 11.sp,
                color = if (atLimit && count > 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { if (count > 0) onCountChange(count - 1) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "减少", modifier = Modifier.size(18.dp))
            }
            Text(
                "$count",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.widthIn(min = 24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            IconButton(
                onClick = { if (count < maxCount) onCountChange(count + 1) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "增加", modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ========= 教学强度 =========

@Composable
private fun IntensitySection(viewModel: TeachingViewModel, state: TeachingState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TeachingIntensity.entries.forEach { intensity ->
                val isSelected = state.config.intensity == intensity
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { viewModel.setIntensity(intensity) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = isSelected, onClick = { viewModel.setIntensity(intensity) })
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(intensity.displayName, fontWeight = FontWeight.Medium)
                        Text(
                            intensity.description,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "成绩×${intensity.scoreMultiplier} | 满意度${intensity.satisfactionPenalty}/月 | 费用×${intensity.monthlyCostMultiplier}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

// ========= 学院培养质量 =========

@Composable
private fun CollegeQualitySection(viewModel: TeachingViewModel) {
    val qualityList by viewModel.collegeQuality.collectAsState()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = com.arktools.xiao.R.drawable.ic_major_scroll),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("专业分流与培养", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            if (qualityList.isEmpty()) {
                Text(
                    "还没有在读学生。9月招生后，这里会按学院显示培养质量。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                qualityList.forEach { quality ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(quality.collegeName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                "${quality.studentCount}人",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            quality.majorSummary.ifEmpty { "尚无具体专业（大二分专业后显示）" },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "满意度 ${quality.avgSatisfaction.toInt()}%",
                                fontSize = 11.sp,
                                color = when {
                                    quality.avgSatisfaction >= 70f -> Color(0xFF4CAF50)
                                    quality.avgSatisfaction >= 50f -> Color(0xFFFF9800)
                                    else -> Color(0xFFF44336)
                                }
                            )
                            Text(
                                "学业分 ${quality.avgAcademicScore.toInt()}",
                                fontSize = 11.sp,
                                color = when {
                                    quality.avgAcademicScore >= 70f -> Color(0xFF4CAF50)
                                    quality.avgAcademicScore >= 50f -> Color(0xFFFF9800)
                                    else -> Color(0xFFF44336)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ========= 作息政策 =========

@Composable
private fun SchedulePolicySection(viewModel: TeachingViewModel, state: TeachingState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SchedulePolicy.entries.forEach { policy ->
                val isEnabled = policy in state.config.schedulePolicies
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.toggleSchedulePolicy(policy) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(policy.displayName, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        Text(
                            "${policy.description} | 成绩+${policy.scoreBonus} | 满意度${policy.satisfactionCost}/月",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { viewModel.toggleSchedulePolicy(policy) }
                    )
                }
            }
        }
    }
}

// ========= 特殊项目 =========

@Composable
private fun SpecialProgramSection(viewModel: TeachingViewModel, state: TeachingState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SpecialProgram.entries.forEach { program ->
                val isActive = program in state.config.specialPrograms
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (isActive) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                            else Modifier
                        )
                        .clickable {
                            if (isActive) viewModel.removeSpecialProgram(program)
                            else viewModel.addSpecialProgram(program)
                        }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(program.displayName, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        Text(program.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "开设${program.setupCost}万 | 月维护${program.monthlyMaintain}万 | 限${program.maxStudents}人",
                            fontSize = 10.sp, color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Checkbox(checked = isActive, onCheckedChange = {
                        if (isActive) viewModel.removeSpecialProgram(program)
                        else viewModel.addSpecialProgram(program)
                    })
                }
            }
        }
    }
}

// ========= 其他配置 =========

@Composable
private fun OtherConfigSection(viewModel: TeachingViewModel, state: TeachingState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 理科占比
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("理科班占比", fontSize = 13.sp)
                Text("${(state.config.scienceToArtsRatio * 100).toInt()}%", fontWeight = FontWeight.Bold)
            }
            Slider(
                value = state.config.scienceToArtsRatio,
                onValueChange = { viewModel.setScienceRatio(it) },
                valueRange = 0.2f..0.9f
            )

            // 每周体育课时
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("每周体育课时", fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.setWeeklyPEHours(state.config.weeklyPEHours - 1) },
                        modifier = Modifier.size(28.dp)
                    ) { Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    Text("${state.config.weeklyPEHours}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                    IconButton(
                        onClick = { viewModel.setWeeklyPEHours(state.config.weeklyPEHours + 1) },
                        modifier = Modifier.size(28.dp)
                    ) { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) }
                }
            }

            // 每阶段考核试频率
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("每月统考次数", fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.setMonthlyExamFrequency(state.config.monthlyExamFrequency - 1) },
                        modifier = Modifier.size(28.dp)
                    ) { Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    Text("${state.config.monthlyExamFrequency}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                    IconButton(
                        onClick = { viewModel.setMonthlyExamFrequency(state.config.monthlyExamFrequency + 1) },
                        modifier = Modifier.size(28.dp)
                    ) { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) }
                }
            }
        }
    }
}
