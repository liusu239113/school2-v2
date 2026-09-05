package com.arktools.xiao.ui.scholarship

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arktools.xiao.domain.scholarship.Scholarship
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

private fun formatScholarshipAmount(amount: Double): String {
    return when {
        amount >= 1.0 -> "¥${amount.toInt()}万"
        amount > 0 -> "¥${(amount * 10000).toInt()}元"
        else -> "¥0"
    }
}

@Composable
fun ScholarshipScreen(viewModel: ScholarshipViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var showTemplateDialog by remember { mutableStateOf(false) }
    val empty = state.scholarships.isEmpty()

    PixelGameBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            LegacyPageHeader("奖助学金")
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    PixelHardPanel {
                        Text("开学季必设", color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            if (empty) "3月/9月没奖，生源会走、退学升高。设立立刻加招生和留存。"
                            else "已设立立刻加招生和留存，3月/9月按名额发钱换声誉。加名额立刻加招生，减名额立刻少开支。",
                            color = if (empty) AccentRed else TextSecondaryDark,
                            fontSize = 12.sp
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatCol("已设立", "${state.scholarships.size}项")
                            StatCol("累计发放", formatScholarshipAmount(state.totalAwarded))
                            StatCol("招生加成", "+${(state.studentAttractionBonus * 100).toInt()}%")
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatCol("留存加成", "+${(state.retentionBonus * 100).toInt()}%")
                            StatCol("声誉加成", "+${state.reputationBonus}")
                            StatCol("预算总额", formatScholarshipAmount(state.totalBudgetAllocated))
                        }
                    }
                }

                item {
                    PixelButton(
                        onClick = { showTemplateDialog = true },
                        text = if (empty) "立刻设立奖学金" else "再设一项奖学金",
                        style = PixelButtonStyle.PRIMARY,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (state.yearlyStats.totalRecipients > 0) {
                    item {
                        PixelHardPanel {
                            Text("本期发放", color = TextPrimaryDark, fontWeight = FontWeight.Bold)
                            Text(
                                "获奖 ${state.yearlyStats.totalRecipients} 人 · ${formatScholarshipAmount(state.yearlyStats.totalAmount)}",
                                color = TextSecondaryDark,
                                fontSize = 12.sp
                            )
                            if (state.yearlyStats.topStudentName.isNotEmpty()) {
                                Text(
                                    "最佳 ${state.yearlyStats.topStudentName}  GPA ${String.format("%.2f", state.yearlyStats.avgGpa)}",
                                    color = AccentGreen,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                if (state.scholarships.isNotEmpty()) {
                    item { Text("在设奖项", color = TextPrimaryDark, fontWeight = FontWeight.Bold) }
                    items(state.scholarships, key = { it.id }) { scholarship ->
                        PixelHardPanel {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(scholarship.name, color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(scholarship.tier.displayName, color = Color(scholarship.tier.color), fontSize = 11.sp)
                                }
                                PixelButton(
                                    text = "取消",
                                    onClick = { viewModel.cancelScholarship(scholarship.id) },
                                    style = PixelButtonStyle.DANGER,
                                    height = 36.dp
                                )
                            }
                            Text(scholarship.description, color = TextSecondaryDark, fontSize = 12.sp)
                            Text(
                                "${scholarship.criteria.displayName} · ${formatScholarshipAmount(scholarship.amountPerStudent)}/人 × ${scholarship.maxRecipients}名",
                                color = Color(0xFF14648C),
                                fontSize = 12.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PixelButton(text = "名额-", onClick = { viewModel.adjustRecipients(scholarship.id, -1) }, style = PixelButtonStyle.SECONDARY, height = 36.dp)
                                Text("${scholarship.maxRecipients}", color = TextPrimaryDark, fontWeight = FontWeight.Bold)
                                PixelButton(text = "名额+", onClick = { viewModel.adjustRecipients(scholarship.id, 1) }, style = PixelButtonStyle.PRIMARY, height = 36.dp)
                            }
                        }
                    }
                }

                val recent = state.recipients.takeLast(8).reversed()
                if (recent.isNotEmpty()) {
                    item { Text("最近获奖", color = TextPrimaryDark, fontWeight = FontWeight.Bold) }
                    items(recent) { recipient ->
                        PixelHardPanel {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(recipient.studentName, color = TextPrimaryDark, fontWeight = FontWeight.Bold)
                                    Text(recipient.scholarshipName, color = TextSecondaryDark, fontSize = 11.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(formatScholarshipAmount(recipient.amount), color = AccentOrange, fontWeight = FontWeight.Bold)
                                    Text("GPA ${String.format("%.1f", recipient.gpa)}", color = TextSecondaryDark, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                if (state.recentEvents.isNotEmpty()) {
                    item { Text("动态", color = TextPrimaryDark, fontWeight = FontWeight.Bold) }
                    items(state.recentEvents) { event ->
                        PixelHardPanel { Text(event, color = TextPrimaryDark, fontSize = 13.sp) }
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    if (showTemplateDialog) {
        val templates = viewModel.getTemplates(2024)
        val existing = state.scholarships.map { it.name }
        PixelAlertDialog(
            onDismissRequest = { showTemplateDialog = false },
            title = "设立奖学金",
            text = "选一项立刻生效：招生加成和留存马上变。",
            confirmText = "关闭",
            onConfirm = { showTemplateDialog = false },
            content = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 280.dp)
                ) {
                    itemsIndexed(templates) { index, template ->
                        val already = template.name in existing
                        PixelHardPanel {
                            Text(template.name + if (already) "（已设）" else "", color = TextPrimaryDark, fontWeight = FontWeight.Bold)
                            Text(template.description, color = TextSecondaryDark, fontSize = 11.sp)
                            Text(
                                "${formatScholarshipAmount(template.amountPerStudent)}/人 × ${template.maxRecipients}名",
                                color = Color(0xFF14648C),
                                fontSize = 11.sp
                            )
                            if (!already) {
                                PixelButton(
                                    text = "设立这项",
                                    onClick = {
                                        viewModel.createFromTemplate(index, 2024)
                                        showTemplateDialog = false
                                    },
                                    style = PixelButtonStyle.PRIMARY,
                                    height = 36.dp,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun StatCol(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, color = TextSecondaryDark, fontSize = 11.sp)
    }
}
