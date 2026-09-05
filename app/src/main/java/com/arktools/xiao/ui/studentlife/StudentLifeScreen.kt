package com.arktools.xiao.ui.studentlife

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arktools.xiao.domain.studentlife.ComplaintAction
import com.arktools.xiao.domain.studentlife.FacilityQuality
import com.arktools.xiao.domain.studentlife.IssueSeverity
import com.arktools.xiao.domain.studentlife.LifeAspect
import com.arktools.xiao.domain.studentlife.LifeFacility
import com.arktools.xiao.domain.studentlife.LifeIssue
import com.arktools.xiao.domain.studentlife.LifeSatisfactionScore
import com.arktools.xiao.domain.studentlife.SpecialProgram
import com.arktools.xiao.domain.studentlife.StudentLifeState
import com.arktools.xiao.ui.components.LegacyPageHeader
import com.arktools.xiao.ui.components.PixelAlertDialog
import com.arktools.xiao.ui.components.PixelButton
import com.arktools.xiao.ui.components.PixelButtonStyle
import com.arktools.xiao.ui.components.PixelGameBackground
import com.arktools.xiao.ui.components.PixelHardPanel
import com.arktools.xiao.ui.theme.AccentGreen
import com.arktools.xiao.ui.theme.AccentOrange
import com.arktools.xiao.ui.theme.AccentRed
import com.arktools.xiao.ui.theme.TextPrimaryDark
import com.arktools.xiao.ui.theme.TextSecondaryDark

@Composable
fun StudentLifeScreen(
    viewModel: StudentLifeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val actionMessage by viewModel.message.collectAsState()
    val openIssues = state.issues.filter { !it.resolved }

    actionMessage?.let { message ->
        PixelAlertDialog(
            onDismissRequest = { viewModel.consumeMessage() },
            title = if (message.contains("不足") || message.contains("不够") || message.contains("失败")) "无法执行" else "学生生活",
            text = message,
            confirmText = "知道了",
            onConfirm = { viewModel.consumeMessage() }
        )
    }

    PixelGameBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            LegacyPageHeader("学生生活")
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    PixelHardPanel {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("校园生活台账", color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(
                                    "宿舍床位、食堂窗口、医务和心理。投诉必须先做对应建设，再点检查结案。",
                                    color = TextSecondaryDark,
                                    fontSize = 12.sp
                                )
                                Text(
                                    "月开支 ¥${state.monthlyExpenses}万 · 学业 ${signed(state.academicImpact)} · 留存 ${signed(state.retentionImpact)}",
                                    color = TextPrimaryDark,
                                    fontSize = 12.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${state.overallSatisfaction.toInt()}",
                                    color = satisfactionColor(state.overallSatisfaction),
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("总满意度", color = TextSecondaryDark, fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LifeAspect.entries.forEach { aspect ->
                                val score = state.satisfactionScores[aspect]?.score ?: 50f
                                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(aspect.displayName, color = TextPrimaryDark, fontSize = 11.sp)
                                    LinearProgressIndicator(
                                        progress = { (score / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth().height(6.dp),
                                        color = satisfactionColor(score),
                                        trackColor = Color(0x33182635)
                                    )
                                    Text("${score.toInt()}", color = TextSecondaryDark, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                if (openIssues.isNotEmpty()) {
                    item {
                        Text("未结案投诉（先做建设）", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)
                    }
                    items(openIssues, key = { it.id }) { issue ->
                        ComplaintPanel(
                            issue = issue,
                            onAct = { viewModel.actOnComplaint(issue) },
                            onCheck = { viewModel.resolveIssue(issue.id) }
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("宿舍食堂医务心理", color = TextPrimaryDark, fontWeight = FontWeight.Bold)
                        val needRepair = state.facilities.values.any { it.maintenanceLevel < 100f }
                        PixelButton(
                            text = "一键维修",
                            onClick = { viewModel.repairAllFacilities() },
                            enabled = needRepair,
                            style = PixelButtonStyle.SECONDARY,
                            height = 40.dp,
                            modifier = Modifier.fillMaxWidth(0.42f)
                        )
                    }
                }

                items(LifeAspect.entries, key = { it.name }) { aspect ->
                    val facility = state.facilities[aspect] ?: return@items
                    FacilityPanel(
                        facility = facility,
                        score = state.satisfactionScores[aspect],
                        canUpgrade = viewModel.canUpgradeFacility(aspect),
                        upgradeCost = viewModel.getUpgradeCost(aspect),
                        expandCost = viewModel.getExpandCost(aspect, 20),
                        onUpgrade = { viewModel.upgradeFacility(aspect) },
                        onRepair = { viewModel.repairFacility(aspect) },
                        onExpand = { viewModel.expandCapacity(aspect, 20) }
                    )
                }

                item {
                    Text("换菜 / 开辅导从这里开专项", color = TextPrimaryDark, fontWeight = FontWeight.Bold)
                }
                item {
                    ProgramsPanel(state, viewModel)
                }
                item { Spacer(Modifier.height(28.dp)) }
            }
        }
    }
}

@Composable
private fun ComplaintPanel(
    issue: LifeIssue,
    onAct: () -> Unit,
    onCheck: () -> Unit
) {
    PixelHardPanel {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(issue.title, color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(issue.severity.displayName, color = severityColor(issue.severity), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Text(issue.description, color = TextSecondaryDark, fontSize = 12.sp)
        Text("结案条件：${issue.requiredHint}", color = Color(0xFF14648C), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("满意度 ${issue.satisfactionPenalty.toInt()}，没做建设点检查会失败", color = AccentRed, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelButton(
                text = actionLabel(issue.requiredAction),
                onClick = onAct,
                style = PixelButtonStyle.PRIMARY,
                height = 40.dp,
                modifier = Modifier.weight(1f)
            )
            PixelButton(
                text = "检查结案",
                onClick = onCheck,
                style = PixelButtonStyle.CONFIRM,
                height = 40.dp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FacilityPanel(
    facility: LifeFacility,
    score: LifeSatisfactionScore?,
    canUpgrade: Boolean,
    upgradeCost: Long,
    expandCost: Long,
    onUpgrade: () -> Unit,
    onRepair: () -> Unit,
    onExpand: () -> Unit
) {
    val loadPercent = if (facility.capacity > 0) (facility.currentLoad * 100) / facility.capacity else 100
    val maxed = facility.quality == FacilityQuality.PREMIUM
    PixelHardPanel {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(facility.aspect.displayName, color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(facility.quality.displayName, color = qualityColor(facility.quality), fontSize = 12.sp)
            }
            Text(
                "${score?.score?.toInt() ?: 0}分",
                color = satisfactionColor(score?.score ?: 0f),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }
        BarRow("维护", facility.maintenanceLevel / 100f, "${facility.maintenanceLevel.toInt()}%", maintenanceColor(facility.maintenanceLevel))
        BarRow(
            "容量",
            (facility.currentLoad.toFloat() / facility.capacity.coerceAtLeast(1)).coerceAtMost(1.2f) / 1.2f,
            "${facility.currentLoad}/${facility.capacity}",
            if (loadPercent > 100) AccentRed else Color(0xFF42A5F5)
        )
        if (!score?.issues.isNullOrEmpty()) {
            Text(score!!.issues.joinToString(" · "), color = AccentRed, fontSize = 11.sp)
        }
        Text("月维护 ¥${facility.monthlyMaintenanceCost}万 · 员工 ${facility.staffCount}人", color = TextSecondaryDark, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PixelButton(
                text = if (maxed) "已满级" else if (!canUpgrade) "等级不足" else "升级 ¥${upgradeCost}万",
                onClick = onUpgrade,
                enabled = canUpgrade && !maxed,
                style = PixelButtonStyle.PRIMARY,
                height = 40.dp,
                modifier = Modifier.weight(1f)
            )
            PixelButton(
                text = "维修",
                onClick = onRepair,
                enabled = facility.maintenanceLevel < 80f,
                style = PixelButtonStyle.SECONDARY,
                height = 40.dp,
                modifier = Modifier.weight(1f)
            )
            PixelButton(
                text = "+20人 ¥${expandCost}万",
                onClick = onExpand,
                style = PixelButtonStyle.CONFIRM,
                height = 40.dp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ProgramsPanel(state: StudentLifeState, viewModel: StudentLifeViewModel) {
    val active = state.programs.filter { it.active }
    val available = viewModel.getAvailablePrograms()
    PixelHardPanel {
        if (active.isEmpty() && available.isEmpty()) {
            Text("没有可开专项", color = TextSecondaryDark, fontSize = 12.sp)
            return@PixelHardPanel
        }
        if (active.isNotEmpty()) {
            Text("已开设", color = AccentGreen, fontWeight = FontWeight.Bold)
            active.forEach { ProgramRow(it, true) { viewModel.deactivateProgram(it.id) } }
        }
        if (available.isNotEmpty()) {
            Text("可开设（换菜开营养餐，辅导开减压工作坊）", color = TextSecondaryDark, fontSize = 12.sp)
            available.forEach { ProgramRow(it, false) { viewModel.activateProgram(it.id) } }
        }
    }
}

@Composable
private fun ProgramRow(program: SpecialProgram, isActive: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(program.name, color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(
                "${program.aspect.displayName} · ¥${program.monthlyCost}万/月 · +${program.satisfactionBoost.toInt()}满意度",
                color = TextSecondaryDark,
                fontSize = 11.sp
            )
        }
        PixelButton(
            text = if (isActive) "关闭" else "开设",
            onClick = onToggle,
            style = if (isActive) PixelButtonStyle.DANGER else PixelButtonStyle.PRIMARY,
            height = 36.dp,
            modifier = Modifier.fillMaxWidth(0.28f)
        )
    }
}

@Composable
private fun BarRow(label: String, progress: Float, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextSecondaryDark, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(0.16f))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.weight(1f).height(8.dp),
            color = color,
            trackColor = Color(0x33182635)
        )
        Text(value, color = TextPrimaryDark, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

private fun actionLabel(action: ComplaintAction): String = when (action) {
    ComplaintAction.EXPAND_DORM -> "立刻扩宿舍"
    ComplaintAction.REPAIR_DORM -> "立刻修宿舍"
    ComplaintAction.EXPAND_CANTEEN -> "立刻加窗口"
    ComplaintAction.CHANGE_MENU -> "立刻换菜谱"
    ComplaintAction.OPEN_COUNSELING -> "立刻开辅导"
    ComplaintAction.REPAIR_GYM -> "立刻修运动"
    ComplaintAction.OPEN_CLINIC -> "立刻开医务"
}

private fun signed(value: Float): String {
    val n = value.toInt()
    return if (n >= 0) "+$n%" else "$n%"
}

private fun qualityColor(quality: FacilityQuality): Color = when (quality) {
    FacilityQuality.POOR -> AccentRed
    FacilityQuality.BASIC -> Color(0xFF9E9E9E)
    FacilityQuality.STANDARD -> Color(0xFF42A5F5)
    FacilityQuality.GOOD -> AccentGreen
    FacilityQuality.EXCELLENT -> Color(0xFFAB47BC)
    FacilityQuality.PREMIUM -> Color(0xFFFFD700)
}

private fun satisfactionColor(score: Float): Color = when {
    score >= 80f -> AccentGreen
    score >= 60f -> Color(0xFF8BC34A)
    score >= 40f -> AccentOrange
    else -> AccentRed
}

private fun maintenanceColor(level: Float): Color = when {
    level >= 70f -> AccentGreen
    level >= 40f -> AccentOrange
    else -> AccentRed
}

private fun severityColor(severity: IssueSeverity): Color = when (severity) {
    IssueSeverity.LOW -> Color(0xFFFFC107)
    IssueSeverity.MEDIUM -> AccentOrange
    IssueSeverity.HIGH -> AccentRed
    IssueSeverity.CRITICAL -> Color(0xFF9C27B0)
}
