package com.arktools.xiaozhang.ui.hiring

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import com.arktools.xiaozhang.domain.model.Teacher
import com.arktools.xiaozhang.domain.model.TeacherLevel
import com.arktools.xiaozhang.ui.teacher.RecruitmentChannel
import com.arktools.xiaozhang.ui.teacher.TeacherViewModel

/**
 * 人事：候选人三选一。
 * 选渠道（扣渠道费）→ 亮 3 张候选卡 → 点卡聘用（扣猎头费），选 1 弃 2。
 * 全部实底卡面，数字深底白字胶囊，杜绝压图。
 */
@Composable
fun HiringScreen(
    viewModel: TeacherViewModel = hiltViewModel()
) {
    val teachers by viewModel.teachers.collectAsState()
    val candidates by viewModel.candidates.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1724))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("人事", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "在编教师 ${teachers.size} 人 · 每学期发布一次招聘，三选一",
                color = Color(0xFFB8C7D6),
                fontSize = 13.sp
            )
        }

        item {
            Text("① 选择招聘渠道（扣渠道费，决定候选质量）", color = Color(0xFFB8C7D6), fontSize = 13.sp)
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecruitmentChannel.entries.forEach { channel ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.White)
                            .clickable { viewModel.selectChannel(channel) }
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            channel.displayName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF182635)
                        )
                        Text(
                            "${channel.cost.toInt()}万",
                            fontSize = 12.sp,
                            color = Color(0xFF1E96C8),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            channel.description,
                            fontSize = 10.sp,
                            color = Color(0xFF617386),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        item {
            Text("② 候选人三选一（点卡聘用，扣猎头费）", color = Color(0xFFB8C7D6), fontSize = 13.sp)
        }

        if (candidates.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(20.dp)
                ) {
                    Text(
                        "先选择上方渠道，系统将亮出 3 名候选人",
                        fontSize = 13.sp,
                        color = Color(0xFF617386)
                    )
                }
            }
        } else {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    candidates.forEach { teacher ->
                        CandidateCard(
                            teacher = teacher,
                            modifier = Modifier.weight(1f),
                            onHire = { viewModel.hireTeacher(teacher) }
                        )
                    }
                }
            }
        }

        errorMessage?.let { msg ->
            item {
                Text(
                    msg,
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xCC8C2F2F))
                        .padding(10.dp)
                        .clickable { viewModel.clearError() }
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun CandidateCard(
    teacher: Teacher,
    modifier: Modifier = Modifier,
    onHire: () -> Unit
) {
    Column(
        modifier = modifier
            .background(Color.White)
            .clickable(onClick = onHire)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .background(levelColor(teacher.level))
                .padding(horizontal = 10.dp, vertical = 2.dp)
        ) {
            Text(
                teacher.level.name,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            teacher.name,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF182635),
            maxLines = 1
        )
        Text(
            teacher.role.displayName,
            fontSize = 11.sp,
            color = Color(0xFF617386),
            maxLines = 1
        )
        Text(
            "综合 ${teacher.averageSkill}",
            fontSize = 12.sp,
            color = Color(0xFF182635)
        )
        Box(
            modifier = Modifier
                .background(Color(0xFF0B2038))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                "聘用",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun levelColor(level: TeacherLevel): Color = when (level) {
    TeacherLevel.S -> Color(0xFFD95C5C)
    TeacherLevel.A -> Color(0xFFD49A45)
    TeacherLevel.B -> Color(0xFF1E96C8)
    else -> Color(0xFF617386)
}
