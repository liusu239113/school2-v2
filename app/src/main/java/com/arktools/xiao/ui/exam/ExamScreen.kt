package com.arktools.xiao.ui.exam

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arktools.xiao.domain.exam.ExamRecord
import com.arktools.xiao.domain.exam.ExamType
import com.arktools.xiao.ui.components.EmptyState
import com.arktools.xiao.ui.theme.AccentGreen
import com.arktools.xiao.ui.theme.AccentOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamScreen(
    viewModel: ExamViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    com.arktools.xiao.ui.components.PixelGameBackground {
    Column(modifier = Modifier.fillMaxSize()) {
    com.arktools.xiao.ui.components.LegacyPageHeader("考试管理")
    Scaffold(
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("考试不是看板", fontWeight = FontWeight.Bold)
                        Text(
                            "分数进学业分，影响毕业、奖学金和声誉。课表加某科课时，该科下次考试更高。不点辅导就按班型和师资硬考。",
                            fontSize = 12.sp
                        )
                        Text(
                            "下次考试辅导 +${uiState.coachingBonus.toInt()} 分（上限 +12）",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (uiState.message.isNotBlank()) {
                            Text(uiState.message, fontSize = 12.sp, color = AccentOrange)
                        }
                        androidx.compose.material3.Button(onClick = { viewModel.buyCoaching() }) {
                            Text("花 4 万买考前辅导（下次全校 +3）")
                        }
                    }
                }
            }
            if (uiState.examHistory.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Assessment,
                        title = "还没考过",
                        description = "阶段考核 3/5/9/11 月，期中 4/10 月，期末 1/7 月。去课表加课时，或在这里买辅导。"
                    )
                }
            }
            uiState.latestExam?.let { exam ->
                item {
                    LatestExamCard(exam)
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "历次考试",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(uiState.examHistory.reversed()) { exam ->
                ExamHistoryItem(exam)
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
    }
    }
}

@Composable
private fun LatestExamCard(exam: ExamRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "最近考试",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                exam.displayTitle,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("参考人数", "${exam.participantCount}人")
                StatItem("平均分", String.format("%.1f", exam.averageScore))
                StatItem("等级", getGradeFromScore(exam.averageScore))
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (exam.averageScore / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = getScoreColor(exam.averageScore),
                trackColor = Color.LightGray.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun ExamHistoryItem(exam: ExamRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 考试类型图标
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(getExamTypeColor(exam.type).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when (exam.type) {
                        ExamType.FINAL_EXAM -> Icons.Default.School
                        ExamType.MIDTERM -> Icons.Default.Assessment
                        ExamType.MONTHLY_TEST -> Icons.Default.TrendingUp
                    },
                    contentDescription = null,
                    tint = getExamTypeColor(exam.type),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    exam.displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "${exam.participantCount}人参加",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    String.format("%.1f", exam.averageScore),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = getScoreColor(exam.averageScore)
                )
                Text(
                    getGradeFromScore(exam.averageScore),
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, fontSize = 11.sp, color = Color.Gray)
    }
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
    else -> Color(0xFFE53935)
}

private fun getExamTypeColor(type: ExamType): Color = when (type) {
    ExamType.FINAL_EXAM -> Color(0xFF1565C0)
    ExamType.MIDTERM -> Color(0xFF6A1B9A)
    ExamType.MONTHLY_TEST -> Color(0xFF2E7D32)
}
