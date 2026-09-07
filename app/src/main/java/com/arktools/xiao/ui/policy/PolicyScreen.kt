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
import com.arktools.xiao.ui.components.PixelButton
import com.arktools.xiao.ui.components.PixelButtonStyle
import com.arktools.xiao.ui.components.PixelGameBackground
import com.arktools.xiao.ui.components.PixelHardPanel
import com.arktools.xiao.ui.components.PixelIcon
import com.arktools.xiao.ui.theme.AccentGreen
import com.arktools.xiao.ui.theme.AccentRed
import com.arktools.xiao.ui.theme.TextPrimaryDark
import com.arktools.xiao.ui.theme.TextSecondaryDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolicyScreen(
    viewModel: PolicyViewModel = hiltViewModel()
) {
    val policies by viewModel.policies.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()
    val effects = viewModel.getPolicyEffects()

    PixelGameBackground {
        Column(modifier = Modifier.fillMaxSize()) {
        LegacyPageHeader("大学政策")
        var policyTab by remember { mutableIntStateOf(0) }
        val tabs = listOf("学费考试", "招生", "学院")
        TabRow(
            selectedTabIndex = policyTab,
            containerColor = Color(0xFF0B1724),
            contentColor = Color.White
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = policyTab == index,
                    onClick = { policyTab = index },
                    text = { Text(title, color = Color.White) }
                )
            }
        }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PolicyEffectsSummary(effects)
        }

        if (policyTab == 0) item {
            PolicySection(
                title = "学费定价",
                description = "立刻改学费收入和招生人数"
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

        if (policyTab == 0) item {
            PolicySection(
                title = "考试难度",
                description = "立刻改学术声誉和退学压力"
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

        if (policyTab == 0) item {
            PolicySection(
                title = "教师薪资",
                description = "立刻改教学质量、教师满意度和月薪开支"
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

        if (policyTab == 0) item {
            PolicySection(
                title = "课外活动",
                description = "立刻改学生满意度和月声誉"
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

        if (policyTab == 1) item {
            PolicySection(
                title = "招生策略",
                description = "立刻改生源质量和招生人数"
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

        if (policyTab == 1) item {
            PolicySection(
                title = "年度招生定位",
                description = "招生季立刻改规模、生源质量和口碑"
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

        if (policyTab == 1) item {
            PolicySection(
                title = "报考大类计划",
                description = "最多10点，减下来的点会空出来。决定9月新生先进入文史、理学、工学还是经管。"
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

        if (policyTab == 2) item {
            PolicySection(
                title = "学院经营",
                description = "学院在校园地图开工。本页查看已竣工学院，并管理核心课和硕博点。"
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

        item {
            PixelButton(
                text = "重置为默认政策",
                onClick = { viewModel.resetToDefaults() },
                style = PixelButtonStyle.CANCEL,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
        }
    }
}

@Composable
private fun PolicyEffectsSummary(effects: PolicyEffects) {
    PixelHardPanel {
            Text(
                "政策立刻改招生、学费、退学",
                color = TextPrimaryDark,
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

@Composable
private fun EffectChip(label: String, value: String, isPositive: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontWeight = FontWeight.Bold,
            color = if (isPositive) AccentGreen else AccentRed
        )
        Text(
            label,
            color = TextSecondaryDark
        )
    }
}

@Composable
private fun PolicySection(
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    PixelHardPanel {
            Text(title, color = TextPrimaryDark, fontWeight = FontWeight.Bold)
            Text(description, color = TextSecondaryDark)
            content()
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(if (isSelected) Color(0xFFDCF1FB) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PixelIcon(emoji = icon, size = 20.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                color = TextPrimaryDark,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                description,
                color = TextSecondaryDark
            )
        }
        if (isSelected) {
            Text("✓", color = Color(0xFF1E96C8), fontWeight = FontWeight.Bold)
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
        PixelButton(
            text = "−",
            onClick = onDecrease,
            enabled = canDecrease,
            style = PixelButtonStyle.SECONDARY,
            height = 40.dp,
            modifier = Modifier.width(48.dp)
        )
        Text(
            "$value",
            modifier = Modifier.width(28.dp),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall
        )
        PixelButton(
            text = "+",
            onClick = onIncrease,
            enabled = canIncrease,
            style = PixelButtonStyle.PRIMARY,
            height = 40.dp,
            modifier = Modifier.width(48.dp)
        )
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
        PixelButton(
            text = "−",
            onClick = onDecrease,
            enabled = canDecrease,
            style = PixelButtonStyle.SECONDARY,
            height = 40.dp,
            modifier = Modifier.width(48.dp)
        )
        Text(
            "$value",
            modifier = Modifier.width(28.dp),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall
        )
        PixelButton(
            text = "+",
            onClick = onIncrease,
            enabled = canIncrease,
            style = PixelButtonStyle.PRIMARY,
            height = 40.dp,
            modifier = Modifier.width(48.dp)
        )
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
