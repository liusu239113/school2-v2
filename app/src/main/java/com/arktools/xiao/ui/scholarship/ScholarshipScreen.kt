package com.arktools.xiao.ui.scholarship

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.arktools.xiao.R
import com.arktools.xiao.domain.scholarship.*
import com.arktools.xiao.ui.components.PixelButton
import com.arktools.xiao.ui.components.PixelButtonStyle

/**
 * 格式化奖学金金额显示（单位：万元）
 * amountPerStudent 存储的是万元单位的金额（如 0.5 = 5000元，1.0 = 1万元）
 */
private fun formatScholarshipAmount(amount: Double): String {
    return when {
        amount >= 1.0 -> "¥${amount.toInt()}万"
        amount > 0 -> "¥${(amount * 10000).toInt().let { 
            if (it >= 10000) "${it / 10000}万" 
            else "${it}元" 
        }}"
        else -> "¥0"
    }
}

@Composable
fun ScholarshipScreen(viewModel: ScholarshipViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var showTemplateDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 总览卡片
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
                                listOf(Color(0xFF7B1FA2), Color(0xFF4A148C))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Text("奖学金管理", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("已设立", fontSize = 12.sp, color = Color.White.copy(0.8f))
                                Text(
                                    "${state.scholarships.size}项",
                                    fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("累计发放", fontSize = 12.sp, color = Color.White.copy(0.8f))
                                Text(
                                    formatScholarshipAmount(state.totalAwarded),
                                    fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("招生加成", fontSize = 12.sp, color = Color.White.copy(0.8f))
                                Text(
                                    "+${(state.studentAttractionBonus * 100).toInt()}%",
                                    fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            BonusChip("留存加成", "+${(state.retentionBonus * 100).toInt()}%")
                            BonusChip("声誉加成", "+${state.reputationBonus}")
                            BonusChip("预算总额", formatScholarshipAmount(state.totalBudgetAllocated))
                        }
                    }
                }
            }
        }

        // 快速设立按钮
        item {
            PixelButton(
                onClick = { showTemplateDialog = true },
                text = "设立奖学金",
                style = PixelButtonStyle.PRIMARY,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 本期获奖统计
        if (state.yearlyStats.totalRecipients > 0) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("本期发放统计", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("获奖人数: ${state.yearlyStats.totalRecipients}")
                            Text("总金额: ${formatScholarshipAmount(state.yearlyStats.totalAmount)}")
                        }
                        if (state.yearlyStats.topStudentName.isNotEmpty()) {
                            Text("最佳学生: ${state.yearlyStats.topStudentName} (GPA ${String.format("%.2f", state.yearlyStats.avgGpa)})",
                                fontSize = 13.sp, color = Color(0xFF7B1FA2))
                        }
                    }
                }
            }
        }

        // 已设立奖学金列表
        if (state.scholarships.isNotEmpty()) {
            item {
                Text("已设立奖学金 (${state.scholarships.size})",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            items(state.scholarships) { scholarship ->
                ScholarshipCard(
                    scholarship = scholarship,
                    onCancel = { viewModel.cancelScholarship(scholarship.id) }
                )
            }
        }

        // 最近获奖记录
        val recentRecipients = state.recipients.takeLast(10).reversed()
        if (recentRecipients.isNotEmpty()) {
            item {
                Text("最近获奖记录", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            items(recentRecipients) { recipient ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(recipient.studentName, fontWeight = FontWeight.Medium)
                            Text(recipient.scholarshipName, fontSize = 12.sp, color = Color.Gray)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatScholarshipAmount(recipient.amount), fontWeight = FontWeight.Bold,
                                color = Color(0xFF7B1FA2))
                            Text("GPA ${String.format("%.1f", recipient.gpa)}", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }

        // 事件
        if (state.recentEvents.isNotEmpty()) {
            item {
                Text("动态", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            items(state.recentEvents) { event ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFF7B1FA2), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(event, fontSize = 14.sp)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }

    if (showTemplateDialog) {
        TemplateDialog(
            templates = viewModel.getTemplates(2024),
            existingNames = state.scholarships.map { it.name },
            onSelect = { index ->
                viewModel.createFromTemplate(index, 2024)
                showTemplateDialog = false
            },
            onDismiss = { showTemplateDialog = false }
        )
    }
}

@Composable
private fun BonusChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(label, fontSize = 10.sp, color = Color.White.copy(0.8f))
    }
}

@Composable
private fun ScholarshipCard(scholarship: Scholarship, onCancel: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(scholarship.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(scholarship.tier.color).copy(alpha = 0.15f)
                        ) {
                            Text(
                                scholarship.tier.displayName,
                                fontSize = 10.sp,
                                color = Color(scholarship.tier.color),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(scholarship.description, fontSize = 12.sp, color = Color.Gray)
                    Text(
                        "${scholarship.criteria.displayName} | ${formatScholarshipAmount(scholarship.amountPerStudent)}/人 × ${scholarship.maxRecipients}名额",
                        fontSize = 11.sp, color = Color(0xFF7B1FA2)
                    )
                }
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "取消", tint = Color.Gray, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun TemplateDialog(
    templates: List<Scholarship>,
    existingNames: List<String>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.dialog_bg),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.matchParentSize()
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "设立奖学金",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 320.dp)
                ) {
                    itemsIndexed(templates) { index, template ->
                        val alreadyExists = template.name in existingNames
                        Card(
                            onClick = { if (!alreadyExists) onSelect(index) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (alreadyExists) Color(0xFFEEEEEE) else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(template.name, fontWeight = FontWeight.Bold,
                                        color = if (alreadyExists) Color.Gray else Color.Unspecified)
                                    if (alreadyExists) {
                                        Spacer(Modifier.width(8.dp))
                                        Text("已设立", fontSize = 10.sp, color = Color.Gray)
                                    }
                                }
                                Text(template.description, fontSize = 12.sp, color = Color.Gray)
                                Text(
                                    "${formatScholarshipAmount(template.amountPerStudent)}/人 × ${template.maxRecipients}名额 = ${formatScholarshipAmount(template.amountPerStudent * template.maxRecipients)}",
                                    fontSize = 11.sp, color = Color(0xFF7B1FA2)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    PixelButton(
                        onClick = onDismiss,
                        text = "关闭",
                        style = PixelButtonStyle.CANCEL
                    )
                }
            }
        }
    }
}
