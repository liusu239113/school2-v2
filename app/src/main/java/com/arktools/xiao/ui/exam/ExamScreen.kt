package com.arktools.xiao.ui.exam

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
import com.arktools.xiao.domain.exam.ExamRecord
import com.arktools.xiao.domain.exam.ExamType
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
fun ExamScreen(
    viewModel: ExamViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.message.isNotBlank()) {
        PixelAlertDialog(
            onDismissRequest = { viewModel.clearMessage() },
            title = if (uiState.message.contains("不够") || uiState.message.contains("顶满")) "无法执行" else "考试管理",
            text = uiState.message,
            confirmText = "知道了",
            onConfirm = { viewModel.clearMessage() }
        )
    }

    PixelGameBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            LegacyPageHeader("考试管理")
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    PixelHardPanel {
                        Text("分数进学业分", color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "影响毕业、奖学金和声誉。课表加某科课时，该科下次考试更高。不点辅导就按班型和师资硬考。",
                            color = TextSecondaryDark,
                            fontSize = 12.sp
                        )
                        Text(
                            "下次考试辅导 +${uiState.coachingBonus.toInt()} 分（上限 +12）",
                            color = AccentOrange,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        PixelButton(
                            text = "花 4 万买考前辅导（下次全校 +3）",
                            onClick = { viewModel.buyCoaching() },
                            style = PixelButtonStyle.PRIMARY,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (uiState.examHistory.isEmpty()) {
                    item {
                        PixelHardPanel {
                            Text("还没考过", color = TextPrimaryDark, fontWeight = FontWeight.Bold)
                            Text("阶段考核 3/5/9/11 月，期中 4/10 月，期末 1/7 月。去课表加课时，或在这里买辅导。", color = TextSecondaryDark, fontSize = 12.sp)
                        }
                    }
                }

                uiState.latestExam?.let { exam ->
                    item { LatestExamPanel(exam) }
                }

                if (uiState.examHistory.isNotEmpty()) {
                    item { Text("历次考试", color = TextPrimaryDark, fontWeight = FontWeight.Bold) }
                    items(uiState.examHistory.reversed()) { exam ->
                        PixelHardPanel {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(exam.displayTitle, color = TextPrimaryDark, fontWeight = FontWeight.Bold)
                                    Text("${examTypeLabel(exam.type)} · ${exam.participantCount}人", color = TextSecondaryDark, fontSize = 11.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(String.format("%.1f", exam.averageScore), color = getScoreColor(exam.averageScore), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Text(getGradeFromScore(exam.averageScore), color = TextSecondaryDark, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(28.dp)) }
            }
        }
    }
}

@Composable
private fun LatestExamPanel(exam: ExamRecord) {
    PixelHardPanel {
        Text("最近考试", color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(exam.displayTitle, color = TextPrimaryDark, fontSize = 14.sp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatCol("参考", "${exam.participantCount}人")
            StatCol("均分", String.format("%.1f", exam.averageScore))
            StatCol("等级", getGradeFromScore(exam.averageScore))
        }
        LinearProgressIndicator(
            progress = { (exam.averageScore / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = getScoreColor(exam.averageScore),
            trackColor = Color(0x33182635)
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

private fun examTypeLabel(type: ExamType): String = when (type) {
    ExamType.FINAL_EXAM -> "期末"
    ExamType.MIDTERM -> "期中"
    ExamType.MONTHLY_TEST -> "阶段考核"
}

private fun getGradeFromScore(score: Float): String = when {
    score >= 90 -> "优秀"
    score >= 80 -> "良好"
    score >= 70 -> "中等"
    score >= 60 -> "及格"
    else -> "不及格"
}

private fun getScoreColor(score: Float): Color = when {
    score >= 80 -> AccentGreen
    score >= 60 -> AccentOrange
    else -> AccentRed
}
