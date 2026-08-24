package com.arktools.xiaozhang.ui.conference

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.arktools.xiaozhang.R
import com.arktools.xiaozhang.domain.conference.*
import com.arktools.xiaozhang.ui.components.PixelButton
import com.arktools.xiaozhang.ui.components.PixelButtonStyle
import com.arktools.xiaozhang.ui.components.PixelIcon

@Composable
fun ConferenceScreen(
    viewModel: ConferenceViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val createResult by viewModel.createResult.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(createResult) {
        createResult?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearCreateResult()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 总览头卡
        item {
            ConferenceOverviewCard(state, viewModel.getSchoolLevel())
        }

        // 学术指标
        item {
            AcademicMetricsCard(state)
        }

        // 新建会议按钮
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "会议列表",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                PixelButton(
                    text = "发起会议",
                    style = PixelButtonStyle.PRIMARY,
                    onClick = { showCreateDialog = true }
                )
            }
        }

        // 进行中/筹备中
        val active = state.conferences.filter {
            it.status == ConferenceStatus.PLANNING || it.status == ConferenceStatus.IN_PROGRESS
        }
        if (active.isNotEmpty()) {
            item {
                Text("进行中 (${active.size})", style = MaterialTheme.typography.labelLarge, color = Color(0xFFFF9800))
            }
            items(active) { conf -> ConferenceCard(conf) }
        }

        // 已完成（最新10个）
        val completed = state.conferences.filter { it.status == ConferenceStatus.COMPLETED }.takeLast(10)
        if (completed.isNotEmpty()) {
            item {
                Text("已完成 (${completed.size})", style = MaterialTheme.typography.labelLarge, color = Color(0xFF4CAF50))
            }
            items(completed.reversed()) { conf -> ConferenceCard(conf) }
        }

        // 学术合作伙伴
        if (state.academicPartners.isNotEmpty()) {
            item {
                Text(
                    "学术合作网络",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            item {
                PartnersCard(state.academicPartners)
            }
        }

        // 事件日志
        if (state.events.isNotEmpty()) {
            item {
                Text("学术动态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(state.events.take(8)) { event ->
                ConferenceEventRow(event)
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }

    if (showCreateDialog) {
        CreateConferenceDialog(
            viewModel = viewModel,
            onConfirm = { type, role, field ->
                viewModel.createConference(type, role, field)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }
}

@Composable
private fun ConferenceOverviewCard(state: AcademicConferenceState, schoolLevel: Int) {
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
                        colors = listOf(Color(0xFF4A148C), Color(0xFF7B1FA2))
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
                            "学术会议中心",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "学术影响力: ${state.academicInfluence}/1000",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.15f)
                    ) {
                        Text(
                            "Lv.${schoolLevel}",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            color = Color(0xFFFFE082),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = { state.academicInfluence / 1000f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFFCE93D8),
                    trackColor = Color.White.copy(alpha = 0.2f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem("主办", "${state.totalConferencesHosted}", Color.White)
                    StatItem("参会", "${state.totalConferencesAttended}", Color(0xFFCE93D8))
                    StatItem("论文", "${state.totalPapersPresented}", Color(0xFFFFE082))
                    StatItem("专利", "${state.totalPatents}", Color(0xFFA5D6A7))
                    StatItem("奖项", "${state.totalAwards}", Color(0xFFFFAB91))
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
    }
}

@Composable
private fun AcademicMetricsCard(state: AcademicConferenceState) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Groups, null, tint = Color(0xFF7B1FA2), modifier = Modifier.size(24.dp))
                    Text("${state.academicPartners.size}", fontWeight = FontWeight.Bold)
                    Text("合作方", style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.TrendingUp, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
                    Text("+${state.teacherGrowthPool.toInt()}", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                    Text("教师成长", style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Science, null, tint = Color(0xFFFF9800), modifier = Modifier.size(24.dp))
                    Text(String.format("%.0f", state.researchScore), fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
                    Text("研究分", style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Work, null, tint = Color(0xFF1565C0), modifier = Modifier.size(24.dp))
                    Text("+${String.format("%.0f", state.employmentBoostPool * 100)}%", fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                    Text("就业加成", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "本月支出: ¥${String.format("%.1f", state.monthlyBudget)}万",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "学术网络: ${state.networkSize}人",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ConferenceCard(conf: Conference) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (conf.status == ConferenceStatus.COMPLETED)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    PixelIcon(emoji = conf.type.icon, size = 24.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(conf.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${conf.role.displayName} · ${conf.field.displayName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = statusColor(conf.status)
                ) {
                    Text(
                        if (conf.status == ConferenceStatus.IN_PROGRESS)
                            "${conf.status.displayName}(${conf.remainingMonths}月)"
                        else conf.status.displayName,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "参会${conf.participantCount}人",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "论文${conf.paperCount}篇",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "费用¥${String.format("%.1f", conf.totalCost)}万",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (conf.status == ConferenceStatus.COMPLETED && conf.reputationGained > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "收获: 声誉+${conf.reputationGained} · 教师+${conf.teacherGrowthGained.toInt()} · 就业+${String.format("%.0f", conf.employmentBoostGained * 100)}% · 联系+${conf.networkingGain}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.Medium
                )
                if (conf.researchOutputs.isNotEmpty()) {
                    Text(
                        "成果: ${conf.researchOutputs.groupBy { it }.map { "${it.value.size}${it.key.displayName}" }.joinToString(" ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF9800),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun PartnersCard(partners: List<AcademicPartner>) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            partners.forEach { partner ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PixelIcon(emoji = partner.field.icon, size = 16.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(partner.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "${partner.field.displayName} · ${"⭐".repeat(partner.prestige)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "×${String.format("%.2f", partner.bonusRepMultiplier)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "合作${partner.collaborationCount}次",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (partner != partners.last()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ConferenceEventRow(event: ConferenceEvent) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (event.isPositive) Icons.Default.CheckCircle else Icons.Default.Info,
            null,
            tint = if (event.isPositive) Color(0xFF4CAF50) else Color(0xFF42A5F5),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(event.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(event.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (event.reputationChange > 0) {
            Text("+${event.reputationChange}", color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CreateConferenceDialog(
    viewModel: ConferenceViewModel,
    onConfirm: (ConferenceType, ConferenceRole, AcademicField) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedType by remember { mutableStateOf(ConferenceType.WORKSHOP) }
    var selectedRole by remember { mutableStateOf(ConferenceRole.HOST) }
    var selectedField by remember { mutableStateOf(AcademicField.EDUCATION) }

    val availableTypes = remember { viewModel.getAvailableTypes() }

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
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Text(
                    text = "发起学术会议",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 会议类型（显示锁定状态和冷却）
                    Text("会议规模", fontWeight = FontWeight.Medium)
                    availableTypes.forEach { (type, lockReason) ->
                        val isLocked = lockReason != null
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedType == type,
                                onClick = { if (!isLocked) selectedType = type },
                                enabled = !isLocked
                            )
                            Column {
                                Text(
                                    "${type.icon} ${type.displayName} (¥${String.format("%.0f", type.baseCost)}万 · Lv.${type.requiredSchoolLevel})",
                                    fontSize = 13.sp,
                                    color = if (isLocked) Color.Gray else Color.Unspecified
                                )
                                if (isLocked) {
                                    Text(
                                        "🔒 $lockReason",
                                        fontSize = 11.sp,
                                        color = Color(0xFFE53935)
                                    )
                                } else {
                                    Text(
                                        "就业+${String.format("%.0f", type.employmentBoost * 100)}% · 冷却${type.cooldownMonths}月",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    // 角色
                    Text("参与角色", fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ConferenceRole.entries.forEach { role ->
                            FilterChip(
                                selected = selectedRole == role,
                                onClick = { selectedRole = role },
                                label = { Text(role.displayName, fontSize = 11.sp) }
                            )
                        }
                    }

                    HorizontalDivider()

                    // 领域
                    Text("学术领域", fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AcademicField.entries.take(4).forEach { field ->
                            FilterChip(
                                selected = selectedField == field,
                                onClick = { selectedField = field },
                                label = { Text("${field.icon}", fontSize = 12.sp) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AcademicField.entries.drop(4).forEach { field ->
                            FilterChip(
                                selected = selectedField == field,
                                onClick = { selectedField = field },
                                label = { Text("${field.icon}", fontSize = 12.sp) }
                            )
                        }
                    }

                    // 费用预估和效果
                    val cost = selectedType.baseCost * selectedRole.costMultiplier
                    val repGain = (selectedType.baseReputation * selectedRole.reputationMultiplier).toInt()
                    val empGain = selectedType.employmentBoost * selectedRole.employmentMultiplier
                    val canAfford = viewModel.canAfford(selectedType, selectedRole)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (canAfford) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "费用: ¥${String.format("%.1f", cost)}万 · 声誉+$repGain · 就业+${String.format("%.0f", empGain * 100)}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = if (canAfford) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                            if (!canAfford) {
                                Text(
                                    "资金不足！",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFC62828),
                                    fontWeight = FontWeight.Bold
                                )
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
                    val lockReason = availableTypes.firstOrNull { it.first == selectedType }?.second
                    val canCreate = lockReason == null && viewModel.canAfford(selectedType, selectedRole)
                    PixelButton(
                        text = "确认发起",
                        style = if (canCreate) PixelButtonStyle.CONFIRM else PixelButtonStyle.CANCEL,
                        onClick = { if (canCreate) onConfirm(selectedType, selectedRole, selectedField) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private fun statusColor(status: ConferenceStatus): Color = when (status) {
    ConferenceStatus.PLANNING -> Color(0xFF42A5F5)
    ConferenceStatus.IN_PROGRESS -> Color(0xFFFF9800)
    ConferenceStatus.COMPLETED -> Color(0xFF4CAF50)
    ConferenceStatus.CANCELLED -> Color(0xFF9E9E9E)
}
