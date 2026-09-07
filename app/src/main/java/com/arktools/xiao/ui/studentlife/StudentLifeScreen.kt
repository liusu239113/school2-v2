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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.arktools.xiao.ui.theme.PanelInk
import com.arktools.xiao.ui.theme.PanelMuted
import com.arktools.xiao.ui.theme.PrimaryDark
import com.arktools.xiao.ui.theme.TextOnDark

@Composable
fun StudentLifeScreen(
    viewModel: StudentLifeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val actionMessage by viewModel.message.collectAsState()
    val openIssues = state.issues.filter { !it.resolved }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    actionMessage?.let { message ->
        PixelAlertDialog(
            onDismissRequest = { viewModel.consumeMessage() },
            title = if (message.contains("不足") || message.contains("不够") || message.contains("失败")) "还没做完" else "学生生活",
            text = message,
            confirmText = "知道了",
            onConfirm = { viewModel.consumeMessage() }
        )
    }

    PixelGameBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            LegacyPageHeader("学生生活")
            TabRow(
                selectedTabIndex = tab,
                containerColor = PrimaryDark,
                contentColor = TextOnDark,
                indicator = { positions ->
                    if (tab in positions.indices) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(positions[tab]),
                            color = Color(0xFFFFD54F)
                        )
                    }
                }
            ) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("宿舍食堂医务心理", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = {
                        Text(
                            if (openIssues.isEmpty()) "生活投诉" else "生活投诉 ${openIssues.size}",
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            }
            when (tab) {
                0 -> LogisticsDesk(state, viewModel)
                else -> ComplaintDesk(state, openIssues, viewModel) { tab = 0 }
            }
        }
    }
}

@Composable
private fun ComplaintDesk(
    state: StudentLifeState,
    openIssues: List<LifeIssue>,
    viewModel: StudentLifeViewModel,
    onOpenLogistics: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            PixelHardPanel {
                Text("生活投诉", color = PanelInk, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "从治院或行政楼进学生生活后，积压投诉在这一栏处理。点按钮会直接加床、加窗口、换菜或开辅导，做完再检查结案。",
                    color = PanelMuted,
                    fontSize = 12.sp
                )
                Text(
                    "全校生活满意度 ${state.overallSatisfaction.toInt()} · 未结案 ${openIssues.size} 件",
                    color = PanelInk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
        if (openIssues.isEmpty()) {
            item {
                PixelHardPanel {
                    Text("暂时没有生活投诉", color = PanelInk, fontWeight = FontWeight.Bold)
                    Text("宿舍挤、食堂差、医务坏会进这一栏。要先加床、换菜或开辅导，再点检查结案。", color = PanelMuted, fontSize = 12.sp)
                    PixelButton(
                        text = "回宿舍食堂医务心理",
                        onClick = onOpenLogistics,
                        style = PixelButtonStyle.SECONDARY,
                        height = 40.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            items(openIssues, key = { it.id }) { issue ->
                ComplaintCaseCard(
                    issue = issue,
                    onAct = { viewModel.actOnComplaint(issue) },
                    onCheck = { viewModel.resolveIssue(issue.id) }
                )
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun ComplaintCaseCard(
    issue: LifeIssue,
    onAct: () -> Unit,
    onCheck: () -> Unit
) {
    val desk = facilityDesk(issue.aspect)
    PixelHardPanel {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(desk.shortName, color = PrimaryDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(issue.severity.displayName, color = severityColor(issue.severity), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Text(issue.title, color = PanelInk, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(issue.description, color = PanelMuted, fontSize = 12.sp)
        Text("处理地点：${desk.fullName}", color = PanelInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("要做的事：${actionWork(issue.requiredAction)}", color = Color(0xFF14648C), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("没做完就检查，结案失败，满意度 ${issue.satisfactionPenalty.toInt()}", color = AccentRed, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelButton(
                text = actionLabel(issue.requiredAction),
                onClick = onAct,
                style = PixelButtonStyle.PRIMARY,
                height = 44.dp,
                modifier = Modifier.weight(1.2f)
            )
            PixelButton(
                text = "检查结案",
                onClick = onCheck,
                style = PixelButtonStyle.CONFIRM,
                height = 44.dp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LogisticsDesk(
    state: StudentLifeState,
    viewModel: StudentLifeViewModel
) {
    val programs = state.programs + viewModel.getAvailablePrograms().filter { available ->
        state.programs.none { it.id == available.id }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            PixelHardPanel {
                Text("学生生活", color = PanelInk, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "宿舍、食堂、医务室、心理辅导站都要先在校园建楼。楼里可以花钱加位并增加月维护；加满了再去校园建新楼。专项默认关闭，开办才扣月费。",
                    color = PanelMuted,
                    fontSize = 12.sp
                )
                Text(
                    "月开支 ¥${state.monthlyExpenses}万 · 学业 ${signed(state.academicImpact)} · 留存 ${signed(state.retentionImpact)}",
                    color = PanelInk,
                    fontSize = 12.sp
                )
            }
        }
        items(LifeAspect.entries, key = { it.name }) { aspect ->
            val facility = state.facilities[aspect] ?: return@items
            FacilityDutyCard(
                facility = facility,
                score = state.satisfactionScores[aspect],
                programs = programs.filter { it.aspect == aspect },
                canUpgrade = viewModel.canUpgradeFacility(aspect),
                upgradeCost = viewModel.getUpgradeCost(aspect),
                expandCost = viewModel.getExpandCost(aspect, 20),
                onUpgrade = { viewModel.upgradeFacility(aspect) },
                onRepair = { viewModel.repairFacility(aspect) },
                onExpand = { viewModel.expandCapacity(aspect, 20) },
                onAdExpand = { viewModel.expandCapacityByAd(aspect, 20) },
                onToggleProgram = { program, active ->
                    if (active) viewModel.deactivateProgram(program.id)
                    else viewModel.activateProgram(program.id)
                }
            )
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun FacilityDutyCard(
    facility: LifeFacility,
    score: LifeSatisfactionScore?,
    programs: List<SpecialProgram>,
    canUpgrade: Boolean,
    upgradeCost: Long,
    expandCost: Long,
    onUpgrade: () -> Unit,
    onRepair: () -> Unit,
    onExpand: () -> Unit,
    onAdExpand: () -> Unit,
    onToggleProgram: (SpecialProgram, Boolean) -> Unit
) {
    val desk = facilityDesk(facility.aspect)
    val loadPercent = if (facility.capacity > 0) (facility.currentLoad * 100) / facility.capacity else 100
    val maxed = facility.quality == FacilityQuality.PREMIUM
    PixelHardPanel {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(desk.fullName, color = PanelInk, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(desk.duty, color = PanelMuted, fontSize = 12.sp)
                Text("档次 ${facility.quality.displayName} · ${desk.staffLabel} ${facility.staffCount}人", color = PrimaryDark, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${score?.score?.toInt() ?: 0}",
                    color = satisfactionColor(score?.score ?: 0f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                )
                Text("满意度", color = PanelMuted, fontSize = 11.sp)
            }
        }
        BarRow(desk.maintainLabel, facility.maintenanceLevel / 100f, "${facility.maintenanceLevel.toInt()}%", maintenanceColor(facility.maintenanceLevel))
        BarRow(
            desk.capacityLabel,
            (facility.currentLoad.toFloat() / facility.capacity.coerceAtLeast(1)).coerceAtMost(1.2f) / 1.2f,
            "${facility.currentLoad}/${facility.capacity}${desk.capacityUnit}",
            if (loadPercent > 100) AccentRed else Color(0xFF14648C)
        )
        if (!score?.issues.isNullOrEmpty()) {
            Text(score!!.issues.joinToString(" · "), color = AccentRed, fontSize = 11.sp)
        }
        Text("月维护 ¥${facility.monthlyMaintenanceCost}万", color = PanelMuted, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PixelButton(
                text = when {
                    maxed -> "已满级"
                    !canUpgrade -> "校园等级不够"
                    else -> "升级档次 ¥${upgradeCost}万"
                },
                onClick = onUpgrade,
                enabled = canUpgrade && !maxed,
                style = PixelButtonStyle.PRIMARY,
                height = 42.dp,
                modifier = Modifier.weight(1.2f)
            )
            PixelButton(
                text = if (facility.maintenanceLevel < 80f) desk.repairLabel else "暂无需修",
                onClick = onRepair,
                enabled = facility.maintenanceLevel < 80f,
                style = PixelButtonStyle.SECONDARY,
                height = 42.dp,
                modifier = Modifier.weight(1f)
            )
        }
        PixelButton(
            text = desk.expandLabel + " ¥${expandCost}万",
            onClick = onExpand,
            style = PixelButtonStyle.CONFIRM,
            height = 42.dp,
            modifier = Modifier.fillMaxWidth()
        )
        val activity = LocalContext.current as? android.app.Activity
        PixelButton(
            text = "看广告免费" + desk.expandLabel,
            onClick = {
                if (activity != null) {
                    com.arktools.adsdk.AdHelper.showRewardAd(
                        activity = activity,
                        onRewarded = onAdExpand
                    )
                }
            },
            enabled = activity != null,
            style = PixelButtonStyle.SECONDARY,
            height = 42.dp,
            modifier = Modifier.fillMaxWidth()
        )
        if (programs.isNotEmpty()) {
            Text(desk.programTitle, color = PanelInk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            programs.forEach { program ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(program.name, color = PanelInk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            "${program.description} · ¥${program.monthlyCost}万/月",
                            color = PanelMuted,
                            fontSize = 11.sp
                        )
                    }
                    PixelButton(
                        text = if (program.active) "停办" else "开办",
                        onClick = { onToggleProgram(program, program.active) },
                        style = if (program.active) PixelButtonStyle.DANGER else PixelButtonStyle.PRIMARY,
                        height = 36.dp,
                        modifier = Modifier.fillMaxWidth(0.28f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BarRow(label: String, progress: Float, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = PanelInk, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(0.22f))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.weight(1f).height(8.dp),
            color = color,
            trackColor = Color(0x33122633)
        )
        Text(value, color = PanelInk, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

private data class FacilityDesk(
    val shortName: String,
    val fullName: String,
    val duty: String,
    val staffLabel: String,
    val maintainLabel: String,
    val capacityLabel: String,
    val capacityUnit: String,
    val repairLabel: String,
    val expandLabel: String,
    val programTitle: String
)

private fun facilityDesk(aspect: LifeAspect): FacilityDesk = when (aspect) {
    LifeAspect.DORMITORY -> FacilityDesk(
        shortName = "宿舍楼",
        fullName = "学生宿舍楼",
        duty = "管床位、水电和楼层维护。楼里还能加床，加满了再去校园建新宿舍。专项默认关着，开了每月扣维护费。",
        staffLabel = "宿管",
        maintainLabel = "楼况",
        capacityLabel = "床位",
        capacityUnit = "床",
        repairLabel = "修宿舍",
        expandLabel = "加 20 张床",
        programTitle = "宿舍专项"
    )
    LifeAspect.CAFETERIA -> FacilityDesk(
        shortName = "食堂",
        fullName = "第一食堂",
        duty = "管窗口、菜谱和排队。饭菜差就换菜，排长队就加窗口。",
        staffLabel = "厨工",
        maintainLabel = "卫生",
        capacityLabel = "餐位",
        capacityUnit = "座",
        repairLabel = "整食堂",
        expandLabel = "加 20 个餐位",
        programTitle = "食堂菜谱 / 窗口专项"
    )
    LifeAspect.HEALTH -> FacilityDesk(
        shortName = "医务室",
        fullName = "校医务室",
        duty = "先在校园建造菜单建医务室，再在这栋楼里加接诊位。不是加运动场位置。运动馆坏了走维修。",
        staffLabel = "校医",
        maintainLabel = "设施",
        capacityLabel = "接诊",
        capacityUnit = "人",
        repairLabel = "修医务室",
        expandLabel = "加 20 接诊位",
        programTitle = "健康专项"
    )
    LifeAspect.PSYCHOLOGY -> FacilityDesk(
        shortName = "心理站",
        fullName = "心理辅导站",
        duty = "先在校园建造菜单建心理辅导站，再在这栋楼里加辅导名额。投诉心理压力，必须从这里开辅导。",
        staffLabel = "咨询师",
        maintainLabel = "运转",
        capacityLabel = "名额",
        capacityUnit = "人",
        repairLabel = "整站点",
        expandLabel = "加 20 个名额",
        programTitle = "心理辅导专项"
    )
}

private fun actionLabel(action: ComplaintAction): String = when (action) {
    ComplaintAction.EXPAND_DORM -> "去宿舍楼加床"
    ComplaintAction.REPAIR_DORM -> "去宿舍楼维修"
    ComplaintAction.EXPAND_CANTEEN -> "去食堂加窗口"
    ComplaintAction.CHANGE_MENU -> "去食堂换菜谱"
    ComplaintAction.OPEN_COUNSELING -> "去建心理辅导站"
    ComplaintAction.REPAIR_GYM -> "去修体育馆"
    ComplaintAction.OPEN_CLINIC -> "去建医务室"
}

private fun actionWork(action: ComplaintAction): String = when (action) {
    ComplaintAction.EXPAND_DORM -> "在校园宿舍楼加床或再建一栋，床位必须超过现住人数"
    ComplaintAction.REPAIR_DORM -> "把校园宿舍楼楼况修到 90 以上"
    ComplaintAction.EXPAND_CANTEEN -> "在校园食堂加窗口或再建一栋，餐位必须超过现排队人数"
    ComplaintAction.CHANGE_MENU -> "先有食堂楼，再开办营养餐或有机菜专项"
    ComplaintAction.OPEN_COUNSELING -> "先在校园建心理辅导站，再开热线或减压工作坊"
    ComplaintAction.REPAIR_GYM -> "把校园体育馆楼况修到 90 以上"
    ComplaintAction.OPEN_CLINIC -> "先在校园建医务室，再把这栋楼修到 90 以上开诊"
}

private fun signed(value: Float): String {
    val n = value.toInt()
    return if (n >= 0) "+$n%" else "$n%"
}

private fun satisfactionColor(score: Float): Color = when {
    score >= 80f -> AccentGreen
    score >= 60f -> Color(0xFF2E7D32)
    score >= 40f -> AccentOrange
    else -> AccentRed
}

private fun maintenanceColor(level: Float): Color = when {
    level >= 70f -> AccentGreen
    level >= 40f -> AccentOrange
    else -> AccentRed
}

private fun severityColor(severity: IssueSeverity): Color = when (severity) {
    IssueSeverity.LOW -> Color(0xFFB26A00)
    IssueSeverity.MEDIUM -> AccentOrange
    IssueSeverity.HIGH -> AccentRed
    IssueSeverity.CRITICAL -> Color(0xFF7B1FA2)
}
