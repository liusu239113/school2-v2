package com.arktools.xiao.ui.principal

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.arktools.xiao.domain.engine.CorruptActResult
import com.arktools.xiao.domain.engine.CorruptionOption
import com.arktools.xiao.domain.engine.InvestigationEvent
import com.arktools.xiao.domain.engine.RiskLevel
import com.arktools.xiao.domain.model.CorruptionType
import com.arktools.xiao.domain.model.Principal
import com.arktools.xiao.domain.autohandle.AutoHandleConfig
import com.arktools.xiao.domain.autohandle.AutoHandledRecord
import com.arktools.xiao.domain.autohandle.AutoStrategy
import com.arktools.xiao.domain.suggestion.Suggestion
import com.arktools.xiao.domain.suggestion.SuggestionCategory
import com.arktools.xiao.domain.suggestion.SuggestionStatus
import com.arktools.xiao.domain.suggestion.SuggestionUrgency
import com.arktools.xiao.domain.suggestion.SubmitterType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrincipalOfficeScreen(viewModel: PrincipalOfficeViewModel = hiltViewModel()) {
    val principal by viewModel.principalState.collectAsState()
    val school by viewModel.schoolState.collectAsState()
    val availableActions by viewModel.availableActions.collectAsState()
    val lastResult by viewModel.lastResult.collectAsState()
    val investigationEvent by viewModel.investigationEvent.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val suggestionActionResult by viewModel.suggestionActionResult.collectAsState()
    val autoHandleConfig by viewModel.autoHandleConfig.collectAsState()
    val autoHandledRecords by viewModel.autoHandledRecords.collectAsState()
    val autoHandledCount by viewModel.autoHandledCount.collectAsState()

    var showConfirmDialog by remember { mutableStateOf<CorruptionOption?>(null) }
    var showIgnoreConfirm by remember { mutableStateOf<Suggestion?>(null) }

    // 操作结果对话框
    lastResult?.let { result ->
        ResultDialog(result = result, onDismiss = { viewModel.dismissResult() })
    }

    // 调查事件对话框
    investigationEvent?.let { event ->
        InvestigationDialog(event = event, onDismiss = { viewModel.dismissInvestigation() })
    }

    // 确认操作对话框
    showConfirmDialog?.let { option ->
        ConfirmActionDialog(
            option = option,
            onConfirm = {
                viewModel.executeCorruptAction(option)
                showConfirmDialog = null
            },
            onDismiss = { showConfirmDialog = null }
        )
    }

    // 忽略建议确认对话框
    showIgnoreConfirm?.let { suggestion ->
        AlertDialog(
            onDismissRequest = { showIgnoreConfirm = null },
            title = { Text("确认忽略") },
            text = { Text("忽略此建议会导致提建议者的${if (suggestion.submitterType == SubmitterType.TEACHER) "忠诚度" else "满意度"}下降，确定忽略吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.ignoreSuggestion(suggestion.id)
                    showIgnoreConfirm = null
                }) { Text("忽略", color = Color(0xFFF44336)) }
            },
            dismissButton = {
                TextButton(onClick = { showIgnoreConfirm = null }) { Text("取消") }
            }
        )
    }

    // 意见箱操作结果提示
    suggestionActionResult?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissSuggestionResult() },
            title = { Text("意见箱") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissSuggestionResult() }) { Text("确定") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 校长个人状态卡片
        item(key = "principal_status") {
            PrincipalStatusCard(
                principal = principal,
                campusLevel = school?.campusLevel ?: 1,
                onDonateToSchool = viewModel::donateToSchool
            )
        }

        // 逮捕状态警告
        if (principal.isArrested) {
            item(key = "arrested_warning") {
                ArrestedWarningCard()
            }
        }
        // 停职状态警告
        else if (principal.isSuspended) {
            item(key = "suspended_warning") {
                SuspendedWarningCard(daysLeft = principal.suspendedDaysLeft)
            }
        }

        // 灰色操作区域（逮捕/停职时不可用）
        if (!principal.isSuspended && !principal.isArrested) {
            item(key = "corrupt_header") {
                Text(
                    text = "💼 私下操作",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = "以下操作可增加个人收入，但有被纪委调查的风险",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            if (availableActions.isEmpty()) {
                item(key = "corrupt_empty") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = if (principal.corruptActsThisMonth > 0)
                                "本月操作次数已达上限，风头太紧，下个月再说吧..."
                            else
                                "当前没有可执行的操作（学校规模太小或条件不满足）",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                items(availableActions, key = { "corrupt_${it.type.name}" }) { option ->
                    CorruptActionCard(
                        option = option,
                        onClick = { showConfirmDialog = option }
                    )
                }
            }
        }

        // 个人消费区
        item(key = "spending_header") {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "🏠 个人消费",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "用个人资金享受生活（不违规，但来源可疑时容易被举报）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        item(key = "spending_section") {
            PersonalSpendingSection(
                personalFunds = principal.personalFunds,
                purchasedItems = principal.purchasedLuxuryItems,
                onPurchase = viewModel::purchasePersonalItem
            )
        }

        // 意见箱区域 - 合并为单一 item 确保一定渲染
        item(key = "suggestion_box_section") {
            Log.d("PrincipalOffice", "=== 意见箱 item 正在渲染 === suggestions.size=${suggestions.size}")

            Spacer(modifier = Modifier.height(16.dp))

            // 意见箱标题卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF3F0FF)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "📮 意见箱",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF3949AB)
                        )
                        if (suggestions.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFF44336), RoundedCornerShape(50))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${suggestions.size}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "师生匿名/实名反馈，不处理将影响对方忠诚度/满意度",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    if (suggestions.isEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "📭 当前没有新的意见建议",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "建议将在每月初由师生提交",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            // 如果有意见，直接在同一个 item 内渲染所有意见卡片
            suggestions.forEach { suggestion ->
                Spacer(modifier = Modifier.height(12.dp))
                SuggestionCard(
                    suggestion = suggestion,
                    onResolve = { viewModel.resolveSuggestion(suggestion.id) },
                    onIgnore = { showIgnoreConfirm = suggestion }
                )
            }
        }

        // 事件自动处理配置区
        item(key = "auto_handle_section") {
            AutoHandleConfigSection(
                config = autoHandleConfig,
                autoHandledCount = autoHandledCount,
                recentRecords = autoHandledRecords,
                onConfigChanged = { viewModel.updateAutoHandleConfig(it) },
                onToggleEnabled = { viewModel.toggleAutoHandleEnabled(it) },
                onResetStats = { viewModel.resetAutoHandleStats() }
            )
        }

        // 风险提示
        item(key = "risk_info") {
            RiskInfoCard(principal = principal)
        }

        // 底部留白
        item(key = "bottom_spacer") { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
private fun PrincipalStatusCard(
    principal: Principal,
    campusLevel: Int,
    onDonateToSchool: suspend (Double) -> Boolean
) {
    val actionScope = rememberCoroutineScope()
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
                        listOf(Color(0xFF37474F), Color(0xFF455A64))
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Text(
                    "校长办公室",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "个人事务与灰色地带",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(16.dp))

                // 个人资金
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("个人资金", fontSize = 12.sp, color = Color.White.copy(0.8f))
                        Text(
                            "¥${String.format("%.1f", principal.personalFunds)}万",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF66BB6A)
                        )
                        // v2.8: 显示校长月薪
                        val monthlySalary = com.arktools.xiao.domain.engine.GameBalanceConfig.getPrincipalMonthlySalary(campusLevel)
                        Text(
                            "月薪 ¥${String.format("%.1f", monthlySalary)}万",
                            fontSize = 11.sp,
                            color = Color.White.copy(0.6f)
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("历史总额", fontSize = 12.sp, color = Color.White.copy(0.8f))
                        Text(
                            "¥${String.format("%.1f", principal.totalEmbezzled)}万",
                            fontSize = 16.sp,
                            color = Color(0xFFFFAB40)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 捐献给学校按钮
                var showDonateDialog by remember { mutableStateOf(false) }
                var donateAmountText by remember { mutableStateOf("") }
                OutlinedButton(
                    onClick = { showDonateDialog = true; donateAmountText = "" },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = principal.personalFunds > 0,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF66BB6A))
                ) {
                    Text("💰 捐献给学校", fontSize = 13.sp)
                }
                if (showDonateDialog) {
                    AlertDialog(
                        onDismissRequest = { showDonateDialog = false },
                        title = { Text("捐献个人资金给学校") },
                        text = {
                            Column {
                                Text("当前个人资金: ${String.format("%.1f", principal.personalFunds)}万", fontSize = 13.sp)
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = donateAmountText,
                                    onValueChange = { donateAmountText = it.filter { c -> c.isDigit() || c == '.' } },
                                    label = { Text("捐献金额（万）") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text("捐献后可获得少量声誉加成", fontSize = 11.sp, color = Color.Gray)
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val amount = donateAmountText.toDoubleOrNull() ?: 0.0
                                actionScope.launch {
                                    if (onDonateToSchool(amount)) {
                                        showDonateDialog = false
                                    }
                                }
                            }) { Text("确认捐献") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDonateDialog = false }) { Text("取消") }
                        }
                    )
                }

                Spacer(Modifier.height(12.dp))

                // 属性条
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AttributeChip("腐败值", principal.corruptionLevel, Color(0xFFEF5350))
                    AttributeChip("人脉", principal.connectionLevel, Color(0xFF42A5F5))
                    AttributeChip("理想", principal.idealismLevel, Color(0xFF66BB6A))
                    AttributeChip("声望", principal.personalReputation, Color(0xFFFFCA28))
                }

                // 被调查记录
                if (principal.timesInvestigated > 0) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "⚠️ 已被调查 ${principal.timesInvestigated} 次" +
                                if (principal.timesCaughtMajor > 0) " | 重大处分 ${principal.timesCaughtMajor} 次" else "",
                        fontSize = 11.sp,
                        color = Color(0xFFFF8A65)
                    )
                }
            }
        }
    }
}

@Composable
private fun AttributeChip(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = Color.White.copy(0.7f))
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.3f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$value",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun ArrestedWarningCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🚔", fontSize = 48.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "校长已被逮捕",
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF1744),
                fontSize = 20.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "纪检监察机关已将你移送司法机关处理。\n所有个人资产已被没收，学校声誉严重受损。\n你的校长生涯已经结束。",
                fontSize = 14.sp,
                color = Color(0xFFCFD8DC),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "⚖️ 贪婪终有代价",
                fontSize = 13.sp,
                color = Color(0xFF78909C),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SuspendedWarningCard(daysLeft: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🚨", fontSize = 32.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "停职反省中",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFC62828),
                    fontSize = 16.sp
                )
                Text(
                    "剩余 $daysLeft 天 | 期间无法执行任何灰色操作",
                    fontSize = 13.sp,
                    color = Color(0xFFD32F2F)
                )
            }
        }
    }
}

@Composable
private fun CorruptActionCard(option: CorruptionOption, onClick: () -> Unit) {
    val riskColor = when (option.riskLevel) {
        RiskLevel.LOW -> Color(0xFF4CAF50)
        RiskLevel.MEDIUM -> Color(0xFFFF9800)
        RiskLevel.HIGH -> Color(0xFFF44336)
        RiskLevel.EXTREME -> Color(0xFF9C27B0)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, riskColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 操作图标
            Text(
                text = getCorruptionIcon(option.type),
                fontSize = 28.sp
            )

            Spacer(Modifier.width(12.dp))

            // 操作描述
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.type.displayName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = option.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 收益
                    if (option.amount > 0) {
                        Text(
                            text = "+${String.format("%.1f", option.amount)}万",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                        Spacer(Modifier.width(8.dp))
                    } else if (option.amount < 0) {
                        Text(
                            text = "${String.format("%.1f", option.amount)}万",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC62828)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    // 额外收益
                    if (option.connectionGain > 0) {
                        Text("人脉+${option.connectionGain}", fontSize = 11.sp, color = Color(0xFF1565C0))
                        Spacer(Modifier.width(8.dp))
                    }
                    if (option.reputationGain > 0) {
                        Text("声誉+${option.reputationGain}", fontSize = 11.sp, color = Color(0xFF6A1B9A))
                    }
                }
            }

            // 风险标签
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(riskColor.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = option.riskLevel.displayName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = riskColor
                )
            }
        }
    }
}

/**
 * 奢侈品数据：名称、描述、价格(万)、分类
 */
private data class LuxuryItem(
    val name: String,
    val desc: String,
    val cost: Double,
    val category: String
)

private val luxuryItems = listOf(
    // === 豪车系列 ===
    LuxuryItem("🚗 大众途关", "入门豪车，先充个门面", 25.0, "豪车"),
    LuxuryItem("🚗 奔驰S大G", "气场两米八，谁看谁害怕", 60.0, "豪车"),
    LuxuryItem("🚗 保时洁卡宴", "教育局停车场最靓的仔", 90.0, "豪车"),
    LuxuryItem("🚗 兰博大牛", "V12轰鸣，全区都知道校长来了", 350.0, "豪车"),
    LuxuryItem("🚗 法拉牛488", "红色跃马，接送孩子都拉风", 280.0, "豪车"),
    LuxuryItem("🚗 劳斯莱撕幻影", "后排老板座，批阅文件专用", 500.0, "豪车"),
    LuxuryItem("🚗 迈巴赫S680", "双色车身，司机标配白手套", 200.0, "豪车"),
    LuxuryItem("🚗 宾利添越", "英伦贵族范，校友会指定座驾", 250.0, "豪车"),
    LuxuryItem("🚗 布加迪威航", "全球限量，跑得比教育局查账还快", 1500.0, "豪车"),

    // === 豪宅系列 ===
    LuxuryItem("🏠 市中心大平层", "180平，合作区房不是梦", 80.0, "豪宅"),
    LuxuryItem("🏠 湖景别野", "独栋带花园，周末烧烤好去处", 200.0, "豪宅"),
    LuxuryItem("🏠 江景顶层复式", "俯瞰全城，格局打开了", 350.0, "豪宅"),
    LuxuryItem("🏠 汤臣二品", "魔都顶流，邻居都是大人物", 800.0, "豪宅"),
    LuxuryItem("🏠 海南度假别野", "面朝大海，春暖花开", 150.0, "豪宅"),
    LuxuryItem("🏠 欧洲古堡庄园", "带葡萄酒庄，退休养老专用", 1200.0, "豪宅"),

    // === 名表系列 ===
    LuxuryItem("⌚ 浪琴优雅", "入门瑞表，低调奢华", 5.0, "名表"),
    LuxuryItem("⌚ 欧米伽海马", "深潜300米，虽然只用来看时间", 8.0, "名表"),
    LuxuryItem("⌚ 劳力仕水鬼", "绿水鬼一表难求，懂的都懂", 15.0, "名表"),
    LuxuryItem("⌚ 百达翡丽鹦鹉螺", "没人能拥有它，只是替下一代保管", 150.0, "名表"),
    LuxuryItem("⌚ 理查磨坊", "戴在手上的F1赛车", 300.0, "名表"),

    // === 奢侈品/包包 ===
    LuxuryItem("👜 LW旅行箱", "出差必备，行李箱都要有logo", 8.0, "奢侈品"),
    LuxuryItem("👜 爱马氏柏金包", "老婆生日礼物，维稳经费", 30.0, "奢侈品"),
    LuxuryItem("👜 香奈鹅CF", "送情人专用，人脉+15", 12.0, "奢侈品"),

    // === 人情世故 ===
    LuxuryItem("🎁 黔台年份酒", "十箱起囤，送礼硬通货", 10.0, "人情世故"),
    LuxuryItem("🎁 高档会所年卡", "人脉都是在桑拿房里谈出来的", 20.0, "人情世故"),
    LuxuryItem("🎁 送领导字画", "齐白石？反正领导说是真的", 50.0, "人情世故"),
    LuxuryItem("🎁 海外高尔夫", "跟教育厅领导切磋球技", 35.0, "人情世故"),

    // === 子女教育 ===
    LuxuryItem("🎓 子女国际学校", "从小接轨国际，赢在起跑线", 40.0, "子女教育"),
    LuxuryItem("🎓 子女出国留学", "常春藤不是梦，先砸钱再说", 150.0, "子女教育"),
    LuxuryItem("🎓 海外置业(合作区)", "伦敦/悉尼合作区房，双保险", 500.0, "子女教育"),
)

@Composable
private fun PersonalSpendingSection(
    personalFunds: Double,
    purchasedItems: List<String>,
    onPurchase: suspend (String, Double) -> Boolean
) {
    val actionScope = rememberCoroutineScope()
    val categories = luxuryItems.groupBy { it.category }
    var expandedCategory by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // ===== 已购奢侈品统计区 =====
        if (purchasedItems.isNotEmpty()) {
            val purchasedCost = luxuryItems.filter { it.name in purchasedItems }.sumOf { it.cost }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("👑 已购奢侈品", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF4CAF50), RoundedCornerShape(50))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "${purchasedItems.size}件",
                                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "总消费 ¥${String.format("%.0f", purchasedCost)}万",
                        fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.height(8.dp))
                    // 列出已购物品名称（按分类分组）
                    val grouped = purchasedItems.groupBy { itemName ->
                        luxuryItems.find { it.name == itemName }?.category ?: "其他"
                    }
                    grouped.forEach { (category, items) ->
                        Text(
                            "${category} ${items.size}件",
                            fontSize = 11.sp,
                            color = Color(0xFFA5D6A7),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        categories.forEach { (category, items) ->
            val isExpanded = expandedCategory == category
            val affordableCount = items.count { personalFunds >= it.cost }

            // 分类标题（可折叠）
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedCategory = if (isExpanded) null else category },
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF37474F)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        category,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${items.size}件 · 可买${affordableCount}件",
                        fontSize = 11.sp,
                        color = Color.White.copy(0.7f)
                    )
                    Text(
                        if (isExpanded) " ▼" else " ▶",
                        fontSize = 11.sp,
                        color = Color.White.copy(0.7f)
                    )
                }
            }

            // 展开的商品列表
            if (isExpanded) {
                items.forEach { item ->
                    val isAlreadyPurchased = item.name in purchasedItems
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isAlreadyPurchased) Color(0xFFE8F5E9)
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    item.desc,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                                )
                            }
                            if (isAlreadyPurchased) {
                                Text("已购买 ✓", fontSize = 12.sp, color = Color(0xFF2E7D32))
                            } else {
                                Button(
                                    onClick = {
                                        actionScope.launch {
                                            onPurchase(item.name, item.cost)
                                        }
                                    },
                                    enabled = personalFunds >= item.cost,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (personalFunds >= item.cost)
                                            Color(0xFF455A64) else Color(0xFFBDBDBD)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        if (item.cost >= 100) "${item.cost.toInt()}万"
                                        else "${item.cost}万",
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RiskInfoCard(principal: Principal) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "⚠️ 风险评估",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFFE65100)
            )
            Spacer(Modifier.height(8.dp))
            val riskFactors = mutableListOf<String>()
            if (principal.corruptionLevel > 50) riskFactors.add("腐败值过高(${principal.corruptionLevel})，被调查概率大增")
            if (principal.timesInvestigated >= 2) riskFactors.add("多次被调查，已被重点关注")
            if (principal.recentCorruptActs.size >= 3) riskFactors.add("近期操作频繁(${principal.recentCorruptActs.size}次)，证据链长")
            if (principal.connectionLevel < 20) riskFactors.add("人脉不足，出事没人帮忙摆平")
            if (riskFactors.isEmpty()) riskFactors.add("当前风险较低，但任何贪腐行为都有暴露可能")

            riskFactors.forEach { factor ->
                Text(
                    text = "• $factor",
                    fontSize = 12.sp,
                    color = Color(0xFF795548),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

// === 对话框 ===

@Composable
private fun ConfirmActionDialog(
    option: CorruptionOption,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val riskColor = when (option.riskLevel) {
        RiskLevel.LOW -> Color(0xFF4CAF50)
        RiskLevel.MEDIUM -> Color(0xFFFF9800)
        RiskLevel.HIGH -> Color(0xFFF44336)
        RiskLevel.EXTREME -> Color(0xFF9C27B0)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "确认操作",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(option.description, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("风险等级：", fontSize = 13.sp)
                    Text(
                        option.riskLevel.displayName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = riskColor
                    )
                }
                if (option.amount > 0) {
                    Text(
                        "预期收益：+${String.format("%.1f", option.amount)}万",
                        fontSize = 13.sp,
                        color = Color(0xFF2E7D32)
                    )
                }
                Text(
                    "知情人数：${option.witnessCount}人",
                    fontSize = 13.sp,
                    color = Color(0xFF795548)
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    "⚠️ 操作有被当场发现的风险，一旦暴露将受到处分！",
                    fontSize = 12.sp,
                    color = Color(0xFFF44336)
                )

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("算了")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF455A64))
                    ) {
                        Text("干了")
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultDialog(result: CorruptActResult, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (result.success) {
                    Text("💰", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "操作成功",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF2E7D32)
                    )
                    Spacer(Modifier.height(8.dp))
                    if (result.personalGain > 0) {
                        Text(
                            "个人资金 +${String.format("%.1f", result.personalGain)}万",
                            fontSize = 14.sp
                        )
                    }
                    Text(
                        "目前没有被发现，但证据留下了...",
                        fontSize = 12.sp,
                        color = Color(0xFF795548)
                    )
                } else {
                    Text("🚨", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "当场被发现！",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFFC62828)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        result.exposureMessage,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = Color(0xFFD32F2F)
                    )
                }
                Spacer(Modifier.height(20.dp))
                Button(onClick = onDismiss) {
                    Text("知道了")
                }
            }
        }
    }
}

@Composable
private fun InvestigationDialog(event: InvestigationEvent, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("📋", fontSize = 48.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "纪委调查",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFFC62828)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    event.message,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))

                if (event.fineAmount > 0) {
                    Text("罚款：${String.format("%.1f", event.fineAmount)}万", fontSize = 13.sp, color = Color(0xFFC62828))
                }
                if (event.reputationLoss > 0) {
                    Text("声誉损失：-${event.reputationLoss}", fontSize = 13.sp, color = Color(0xFFE65100))
                }
                if (event.suspensionDays > 0) {
                    Text("停职：${event.suspensionDays}天", fontSize = 13.sp, color = Color(0xFF6A1B9A))
                }

                Spacer(Modifier.height(20.dp))
                Button(onClick = onDismiss) {
                    Text("接受处分")
                }
            }
        }
    }
}

// === 工具函数 ===

private fun getCorruptionIcon(type: CorruptionType): String {
    return when (type) {
        CorruptionType.EMBEZZLE -> "💰"
        CorruptionType.KICKBACK -> "🤝"
        CorruptionType.SELL_ADMISSION -> "🎫"
        CorruptionType.FAKE_NUMBERS -> "📊"
        CorruptionType.NEPOTISM -> "👥"
        CorruptionType.WAGE_SKIM -> "✂️"
        CorruptionType.GRADE_FRAUD -> "📝"
        CorruptionType.COVER_UP -> "🙈"
        CorruptionType.BRIBE_INSPECTOR -> "💎"
        CorruptionType.MISUSE_RESEARCH_FUNDS -> "🔬"
    }
}

// ======= 意见箱卡片 =======

@Composable
private fun SuggestionCard(
    suggestion: Suggestion,
    onResolve: () -> Unit,
    onIgnore: () -> Unit
) {
    val urgencyColor = Color(suggestion.urgency.colorArgb)
    val categoryIcon = suggestion.category.icon

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = urgencyColor.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 头部：类别 + 紧急度
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(categoryIcon, fontSize = 18.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        suggestion.category.displayName,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = urgencyColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        suggestion.urgency.displayName,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = urgencyColor
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // 标题
            Text(
                suggestion.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )

            Spacer(Modifier.height(6.dp))

            // 内容
            Text(
                suggestion.content,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(8.dp))

            // 提交者信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (suggestion.isAnonymous) "👤 匿名${suggestion.submitterType.displayName}"
                           else "👤 ${suggestion.submitterName}（${suggestion.submitterType.displayName}）",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    text = "${suggestion.createdYear}年${suggestion.createdMonth}月",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }

            // 相关数据
            if (suggestion.relatedInfo.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "📊 ${suggestion.relatedInfo}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }

            Spacer(Modifier.height(12.dp))

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onIgnore,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFF44336)
                    ),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("忽略", fontSize = 13.sp)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onResolve,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    ),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("采纳", fontSize = 13.sp)
                }
            }
        }
    }
}

// ======= 事件自动处理配置区 =======

@Composable
private fun AutoHandleConfigSection(
    config: AutoHandleConfig,
    autoHandledCount: Int,
    recentRecords: List<AutoHandledRecord>,
    onConfigChanged: (AutoHandleConfig) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onResetStats: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showRecords by remember { mutableStateOf(false) }

    Spacer(modifier = Modifier.height(16.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF1F8E9)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题行 + 总开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🤖 事件自动处理",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF33691E)
                        )
                        if (autoHandledCount > 0) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF4CAF50), RoundedCornerShape(50))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "已处理$autoHandledCount",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text(
                        text = "自动批准/拒绝事件，减少弹窗打扰",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Switch(
                    checked = config.enabled,
                    onCheckedChange = { onToggleEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF4CAF50)
                    )
                )
            }

            // 开启后显示详细配置
            AnimatedVisibility(
                visible = config.enabled,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFF33691E).copy(alpha = 0.2f))
                    Spacer(Modifier.height(12.dp))

                    // 展开/收起详细配置
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "分类策略配置",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color(0xFF33691E)
                        )
                        Text(
                            if (expanded) "▼ 收起" else "▶ 展开",
                            fontSize = 12.sp,
                            color = Color(0xFF33691E).copy(alpha = 0.7f)
                        )
                    }

                    AnimatedVisibility(
                        visible = expanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Spacer(Modifier.height(4.dp))

                            // 选择类事件策略
                            Text(
                                "📋 选择类事件",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = Color(0xFF455A64)
                            )

                            StrategyRow(
                                label = "教师加薪",
                                icon = "💰",
                                strategy = config.teacherRaiseStrategy,
                                onChanged = { onConfigChanged(config.copy(teacherRaiseStrategy = it)) }
                            )
                            StrategyRow(
                                label = "教师续约",
                                icon = "📝",
                                strategy = config.teacherRenewalStrategy,
                                onChanged = { onConfigChanged(config.copy(teacherRenewalStrategy = it)) }
                            )
                            StrategyRow(
                                label = "教师离职",
                                icon = "👋",
                                strategy = config.teacherResignStrategy,
                                onChanged = { onConfigChanged(config.copy(teacherResignStrategy = it)) }
                            )
                            StrategyRow(
                                label = "活动审批",
                                icon = "🎉",
                                strategy = config.activityApprovalStrategy,
                                onChanged = { onConfigChanged(config.copy(activityApprovalStrategy = it)) }
                            )
                            StrategyRow(
                                label = "社团审批",
                                icon = "🏫",
                                strategy = config.clubApprovalStrategy,
                                onChanged = { onConfigChanged(config.copy(clubApprovalStrategy = it)) }
                            )
                            StrategyRow(
                                label = "突发危机",
                                icon = "🚨",
                                strategy = config.crisisStrategy,
                                onChanged = { onConfigChanged(config.copy(crisisStrategy = it)) },
                                warning = true
                            )
                            StrategyRow(
                                label = "其他事件",
                                icon = "📌",
                                strategy = config.otherChoiceStrategy,
                                onChanged = { onConfigChanged(config.copy(otherChoiceStrategy = it)) }
                            )

                            Spacer(Modifier.height(8.dp))

                            // 信息类事件
                            Text(
                                "📢 信息类事件（自动关闭不弹窗）",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = Color(0xFF455A64)
                            )

                            AutoCloseRow(
                                label = "正面事件",
                                icon = "✅",
                                checked = config.positiveAutoClose,
                                onChanged = { onConfigChanged(config.copy(positiveAutoClose = it)) }
                            )
                            AutoCloseRow(
                                label = "负面事件",
                                icon = "⚠️",
                                checked = config.negativeAutoClose,
                                onChanged = { onConfigChanged(config.copy(negativeAutoClose = it)) }
                            )
                            AutoCloseRow(
                                label = "里程碑事件",
                                icon = "🏆",
                                checked = config.milestoneAutoClose,
                                onChanged = { onConfigChanged(config.copy(milestoneAutoClose = it)) }
                            )
                        }
                    }

                    // 最近自动处理记录
                    if (recentRecords.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = Color(0xFF33691E).copy(alpha = 0.2f))
                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showRecords = !showRecords }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "最近自动处理记录",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF33691E)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${recentRecords.size}条",
                                    fontSize = 11.sp,
                                    color = Color(0xFF33691E).copy(alpha = 0.6f)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (showRecords) "▼" else "▶",
                                    fontSize = 11.sp,
                                    color = Color(0xFF33691E).copy(alpha = 0.6f)
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = showRecords,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                recentRecords.take(10).forEach { record ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                Color.White.copy(alpha = 0.5f),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                record.eventTitle,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1
                                            )
                                            Text(
                                                "[${record.eventType}] ${getActionDisplayName(record.action)}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }

                                // 清空按钮
                                TextButton(
                                    onClick = onResetStats,
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text(
                                        "清空记录",
                                        fontSize = 12.sp,
                                        color = Color(0xFF795548)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StrategyRow(
    label: String,
    icon: String,
    strategy: AutoStrategy,
    onChanged: (AutoStrategy) -> Unit,
    warning: Boolean = false
) {
    var showDropdown by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (warning) {
                Spacer(Modifier.width(4.dp))
                Text("(慎)", fontSize = 10.sp, color = Color(0xFFF44336))
            }
        }

        Box {
            Surface(
                modifier = Modifier.clickable { showDropdown = true },
                shape = RoundedCornerShape(6.dp),
                color = getStrategyColor(strategy).copy(alpha = 0.15f)
            ) {
                Text(
                    text = strategy.displayName,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = getStrategyColor(strategy)
                )
            }

            DropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { showDropdown = false }
            ) {
                AutoStrategy.entries.forEach { s ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(s.displayName, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    s.description,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        },
                        onClick = {
                            onChanged(s)
                            showDropdown = false
                        },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(getStrategyColor(s), RoundedCornerShape(50))
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoCloseRow(
    label: String,
    icon: String,
    checked: Boolean,
    onChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChanged,
            modifier = Modifier.height(28.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF66BB6A)
            )
        )
    }
}

private fun getStrategyColor(strategy: AutoStrategy): Color {
    return when (strategy) {
        AutoStrategy.MANUAL -> Color(0xFF78909C)
        AutoStrategy.AUTO_APPROVE -> Color(0xFF4CAF50)
        AutoStrategy.AUTO_REJECT -> Color(0xFFF44336)
    }
}

private fun getActionDisplayName(action: String): String {
    return when (action) {
        "auto_approve" -> "自动批准"
        "auto_reject" -> "自动拒绝"
        "auto_close" -> "自动关闭"
        else -> action
    }
}
