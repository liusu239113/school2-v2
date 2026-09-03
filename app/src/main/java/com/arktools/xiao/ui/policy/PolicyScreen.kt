package com.arktools.xiao.ui.policy

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.arktools.xiao.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arktools.xiao.domain.policy.*
import com.arktools.xiao.ui.components.LegacyPageHeader
import com.arktools.xiao.ui.components.PixelGameBackground
import com.arktools.xiao.ui.components.PixelIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolicyScreen(
    viewModel: PolicyViewModel = hiltViewModel()
) {
    val policies by viewModel.policies.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()
    val effects = viewModel.getPolicyEffects()

    PixelGameBackground {
        LegacyPageHeader("学校政策")
    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 效果总览
        item {
            PolicyEffectsSummary(effects)
        }

        // 学费政策
        item {
            PolicySection(
                title = "学费定价",
                description = "影响收入和招生数量"
            ) {
                TuitionLevel.entries.forEach { level ->
                    PolicyOption(
                        icon = level.icon,
                        name = level.displayName,
                        description = level.description,
                        isSelected = policies.tuitionLevel == level,
                        onClick = { viewModel.setTuitionLevel(level) }
                    )
                }
            }
        }

        // 注：招生规模已统一到「治院→教学配置」的教学班容量，不再在政策页重复设置
        // 注：奖学金已统一到"奖学金管理"专属页面，不再在政策页重复设置

        // 考试难度
        item {
            PolicySection(
                title = "考试难度",
                description = "影响学术声誉和退学率"
            ) {
                ExamDifficulty.entries.forEach { diff ->
                    PolicyOption(
                        icon = diff.icon,
                        name = diff.displayName,
                        description = diff.description,
                        isSelected = policies.examDifficulty == diff,
                        onClick = { viewModel.setExamDifficulty(diff) }
                    )
                }
            }
        }

        // 教师薪资
        item {
            PolicySection(
                title = "教师薪资",
                description = "影响教学质量和运营成本"
            ) {
                TeacherPayPolicy.entries.forEach { policy ->
                    PolicyOption(
                        icon = policy.icon,
                        name = policy.displayName,
                        description = policy.description,
                        isSelected = policies.teacherPayPolicy == policy,
                        onClick = { viewModel.setTeacherPayPolicy(policy) }
                    )
                }
            }
        }

        // 课外活动
        item {
            PolicySection(
                title = "课外活动",
                description = "影响学生满意度和声誉"
            ) {
                ExtracurricularPolicy.entries.forEach { policy ->
                    PolicyOption(
                        icon = policy.icon,
                        name = policy.displayName,
                        description = policy.description,
                        isSelected = policies.extracurricularPolicy == policy,
                        onClick = { viewModel.setExtracurricularPolicy(policy) }
                    )
                }
            }
        }

        // 招生策略
        item {
            PolicySection(
                title = "招生策略",
                description = "影响生源质量和数量"
            ) {
                AdmissionPolicy.entries.forEach { policy ->
                    PolicyOption(
                        icon = policy.icon,
                        name = policy.displayName,
                        description = policy.description,
                        isSelected = policies.admissionPolicy == policy,
                        onClick = { viewModel.setAdmissionPolicy(policy) }
                    )
                }
            }
        }

        item {
            PolicySection(
                title = "年度专项预算",
                description = "共10点，分到教学、科研、校园生活和社会合作。投入越高，对应线越强，每月专项开支也越高。"
            ) {
                val allocation = policies.budgetAllocation
                Text(
                    text = "已分配 ${allocation.totalPoints()}/${BudgetAllocation.TOTAL_POINTS} 点 · 每月约 ${"%.1f".format(allocation.monthlyCostWan())} 万",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                BudgetLine.entries.forEach { line ->
                    val value = when (line) {
                        BudgetLine.TEACHING -> allocation.teachingWeight
                        BudgetLine.RESEARCH -> allocation.researchWeight
                        BudgetLine.CAMPUS_LIFE -> allocation.campusLifeWeight
                        BudgetLine.SOCIETY -> allocation.societyWeight
                    }
                    BudgetLineRow(
                        line = line,
                        value = value,
                        canIncrease = allocation.totalPoints() < BudgetAllocation.TOTAL_POINTS && value < BudgetAllocation.TOTAL_POINTS,
                        canDecrease = value > 0,
                        onDecrease = { viewModel.adjustBudget(line, -1) },
                        onIncrease = { viewModel.adjustBudget(line, 1) }
                    )
                }
            }
        }

        // 年度办学方针：教学、科研、就业、扩张之间的长期取舍
        item {
            PolicySection(
                title = "年度办学方针",
                description = "决定本学年把资源压到哪条经营线上"
            ) {
                UniversityStrategy.entries.forEach { strategy ->
                    PolicyOption(
                        icon = strategy.icon,
                        name = strategy.displayName,
                        description = strategy.description + "\n" + strategy.effectSummary,
                        isSelected = policies.universityStrategy == strategy,
                        onClick = { viewModel.setUniversityStrategy(strategy) }
                    )
                }
            }
        }

        // 年度招生定位：招生数量、生源质量和社会责任的取舍
        item {
            PolicySection(
                title = "年度招生定位",
                description = "每年招生季生效，影响规模、生源质量与长期口碑"
            ) {
                EnrollmentPlan.entries.forEach { plan ->
                    PolicyOption(
                        icon = plan.icon,
                        name = plan.displayName,
                        description = plan.description,
                        isSelected = policies.enrollmentPlan == plan,
                        onClick = { viewModel.setEnrollmentPlan(plan) }
                    )
                }
            }
        }

        item {
            Image(
                painter = painterResource(id = R.drawable.banner_admission_v2),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        }

        item {
            PolicySection(
                title = "报考大类计划",
                description = "共10点，决定9月新生先进入文史、理学、工学还是经管。大二再按学院进入具体专业。"
            ) {
                val plan = policies.admissionTrackPlan
                Text(
                    text = "已分配 ${plan.totalPoints()}/${com.arktools.xiao.domain.model.AdmissionTrackPlan.TOTAL_POINTS} 点",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                com.arktools.xiao.domain.model.AdmissionTrack.entries.forEach { track ->
                    val value = plan.weightOf(track)
                    AdmissionTrackRow(
                        track = track,
                        value = value,
                        canIncrease = plan.totalPoints() < com.arktools.xiao.domain.model.AdmissionTrackPlan.TOTAL_POINTS && value < com.arktools.xiao.domain.model.AdmissionTrackPlan.TOTAL_POINTS,
                        canDecrease = value > 0,
                        onDecrease = { viewModel.adjustAdmissionTrack(track, -1) },
                        onIncrease = { viewModel.adjustAdmissionTrack(track, 1) }
                    )
                }
            }
        }

        item {
            Image(
                painter = painterResource(id = R.drawable.banner_annual_goal_v2),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        }

        item {
            PolicySection(
                title = "本学年目标",
                description = "6月会按这项考核。达标给声誉和专项拨款，未达标扣声誉。"
            ) {
                val campusLevel by viewModel.campusLevel.collectAsState()
                AnnualGoal.entries.forEach { goal ->
                    PolicyOption(
                        icon = goal.icon,
                        name = goal.displayName,
                        description = goal.description + "\n" +
                            goal.requirementSummary(campusLevel) + "\n" +
                            goal.rewardSummary(campusLevel),
                        isSelected = policies.collegeDevelopment.annualGoal == goal,
                        onClick = { viewModel.setAnnualGoal(goal) }
                    )
                }
            }
        }

        item {
            PolicySection(
                title = "学院经营",
                description = "学院建设统一从校园地图选址并施工；本页只查看已竣工学院，并管理其课程与后续项目。"
            ) {
                operationMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                CollegeType.entries.forEach { type ->
                    val founded = policies.collegeDevelopment.founded.contains(type)
                    val constructionDays = policies.collegeDevelopment.constructingColleges[type.name]
                    CollegeFoundRow(
                        type = type,
                        founded = founded,
                        constructionDays = constructionDays
                    )
                }
                if (policies.collegeDevelopment.founded.contains(CollegeType.MEDICINE)) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_hospital_building),
                            contentDescription = null,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("附属医院", fontWeight = FontWeight.SemiBold)
                            Text(
                                "投入300万建设：每月带来诊疗收入（15万+0.2万/医学类学生）、声誉+2，并触发医学实习事件",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (policies.collegeDevelopment.affiliatedHospital) {
                            Text("已建成", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                        } else {
                            Text("请在校园地图建造", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (policies.collegeDevelopment.founded.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_core_course),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("专业核心课", fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "每门25万，每学院最多3门；核心课越齐，该学院学生掌握度与毕业表现越好",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    policies.collegeDevelopment.founded.forEach { college ->
                        val count = policies.collegeDevelopment.coreCourses[college.name] ?: 0
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                college.displayName,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "$count/3",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (count >= 3) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (count < 3) {
                                Text(
                                    "开设",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .padding(start = 10.dp)
                                        .clickable { viewModel.openCoreCourse(college) }
                                )
                            }
                        }
                    }
                }
                if (policies.collegeDevelopment.founded.any {
                        it == CollegeType.SCIENCE || it == CollegeType.ENGINEERING || it == CollegeType.MEDICINE
                    }
                ) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_graduate_program),
                            contentDescription = null,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("硕博点", fontWeight = FontWeight.SemiBold)
                            Text(
                                "投入200万启动（研究型大学校园3级，其他层次校园5级）：每月导师经费、声誉+3、科研+1天，并触发研究生事件",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (policies.collegeDevelopment.graduateProgram) {
                            Text("已启动", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                        } else {
                            OutlinedButton(onClick = { viewModel.launchGraduateProgram() }) {
                                Text("启动")
                            }
                        }
                    }
                }
            }
        }

        // 重置按钮
        item {
            OutlinedButton(
                onClick = { viewModel.resetToDefaults() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("重置为默认政策")
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    } // PixelGameBackground
}

@Composable
private fun PolicyEffectsSummary(effects: PolicyEffects) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "政策效果总览",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                EffectChip("收入", formatMultiplier(effects.tuitionMultiplier), effects.tuitionMultiplier >= 1f)
                EffectChip("招生", formatMultiplier(effects.enrollmentMultiplier), effects.enrollmentMultiplier >= 1f)
                EffectChip("质量", formatMultiplier(effects.qualityMultiplier), effects.qualityMultiplier >= 1f)
                EffectChip("开支", formatMultiplier(effects.expenseMultiplier), effects.expenseMultiplier <= 1f)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                EffectChip("满意度", formatModifier(effects.satisfactionModifier), effects.satisfactionModifier >= 0f)
                EffectChip("声誉", "${if (effects.reputationModifier >= 0) "+" else ""}${effects.reputationModifier}/月", effects.reputationModifier >= 0)
                EffectChip("退学率", formatModifier(effects.dropoutRateModifier * 100f), effects.dropoutRateModifier <= 0f)
            }
        }
    }
}

@Composable
private fun EffectChip(label: String, value: String, isPositive: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (isPositive) Color(0xFF4CAF50) else Color(0xFFF44336)
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PolicySection(
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .animateContentSize()
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun PolicyOption(
    icon: String,
    name: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PixelIcon(emoji = icon, size = 20.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun BudgetLineRow(
    line: BudgetLine,
    value: Int,
    canIncrease: Boolean,
    canDecrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(line.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(line.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDecrease, enabled = canDecrease) {
            Text("-", fontWeight = FontWeight.Bold)
        }
        Text(
            "$value",
            modifier = Modifier.width(24.dp),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall
        )
        IconButton(onClick = onIncrease, enabled = canIncrease) {
            Text("+", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AdmissionTrackRow(
    track: com.arktools.xiao.domain.model.AdmissionTrack,
    value: Int,
    canIncrease: Boolean,
    canDecrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("${track.icon} ${track.displayName}", fontWeight = FontWeight.SemiBold)
            Text(
                "${track.description} 对应${track.college.displayName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDecrease, enabled = canDecrease) {
            Text("-", fontWeight = FontWeight.Bold)
        }
        Text(
            "$value",
            modifier = Modifier.width(24.dp),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall
        )
        IconButton(onClick = onIncrease, enabled = canIncrease) {
            Text("+", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CollegeFoundRow(
    type: CollegeType,
    founded: Boolean,
    constructionDays: Int?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = collegeIconRes(type)),
            contentDescription = type.displayName,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(type.displayName, fontWeight = FontWeight.SemiBold)
            Text(
                "${type.description} 校园${type.unlockLevel}级解锁 · 地图开工 ${type.foundingCostWan.toInt()}万 · 每月 ${"%.1f".format(type.monthlyCostWan)}万",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (founded) {
            Text("已竣工", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
        } else if (constructionDays != null) {
            Text("施工中 ${constructionDays}天", color = Color(0xFFE08A2E), fontWeight = FontWeight.Bold)
        } else {
            Text("请到校园地图开工", color = Color(0xFF617386), fontWeight = FontWeight.Bold)
        }
    }
}

private fun collegeIconRes(type: CollegeType): Int = when (type) {
    CollegeType.LIBERAL_ARTS -> R.drawable.ic_college_liberal
    CollegeType.SCIENCE -> R.drawable.ic_college_science
    CollegeType.ENGINEERING -> R.drawable.ic_college_engineering
    CollegeType.BUSINESS -> R.drawable.ic_college_business
    CollegeType.ARTS -> R.drawable.ic_college_arts
    CollegeType.MEDICINE -> R.drawable.ic_college_medicine
}

private fun formatMultiplier(value: Float): String {
    return if (value >= 1f) "×${String.format("%.2f", value)}" else "×${String.format("%.2f", value)}"
}

private fun formatModifier(value: Float): String {
    return if (value >= 0) "+${String.format("%.1f", value)}" else String.format("%.1f", value)
}
