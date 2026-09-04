package com.arktools.xiao.ui.hiring

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.arktools.xiao.R
import com.arktools.xiao.ui.utils.TeacherAvatarHelper
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.arktools.xiao.domain.model.Teacher
import com.arktools.xiao.domain.model.TeacherLevel
import com.arktools.xiao.ui.teacher.RecruitmentChannel
import com.arktools.xiao.ui.teacher.TeacherViewModel

/**
 * 人事：候选人三选一。
 * 选渠道（扣渠道费）→ 亮 3 张候选卡 → 点卡聘用（扣猎头费），选 1 弃 2。
 * 全部实底卡面，数字深底白字胶囊，杜绝压图。
 */
@Composable
fun HiringScreen(
    viewModel: TeacherViewModel = hiltViewModel(),
    onNavigateTo: (Int) -> Unit = {}
) {
    val teachers by viewModel.teachers.collectAsState()
    val candidates by viewModel.candidates.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val devState by viewModel.devState.collectAsState()
    val availableTalent = devState.talentPool.filter { it.status == com.arktools.xiao.domain.teacherdev.TalentStatus.AVAILABLE }

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
                "在编教师 ${teachers.size} 人 · ${devState.talentPoolYear} 年度固定人才池",
                color = Color(0xFFB8C7D6),
                fontSize = 13.sp
            )
            val quotaText = TeacherLevel.entries.joinToString(" · ") { level ->
                "$level ${availableTalent.count { it.teacher.level == level.name }}人"
            }
            Text(
                "剩余：$quotaText · 校友返校 ${availableTalent.count { it.source.name == "ALUMNI_RETURN" }}人",
                color = Color(0xFF7FC8E8),
                fontSize = 12.sp
            )
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF1E3A5C))
                        .clickable { onNavigateTo(48) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) { Text("在编教师/位置", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF1E3A5C))
                        .clickable { onNavigateTo(8) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) { Text("班级与学业导师", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }
        }

        item {
            Text("① 解锁年度招聘渠道（每年每渠道只收费一次）", color = Color(0xFFB8C7D6), fontSize = 13.sp)
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
            Text("② 从固定人才池聘用（聘用后该候选人永久离池）", color = Color(0xFFB8C7D6), fontSize = 13.sp)
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
                        "请选择上方渠道查看本年度固定候选人",
                        fontSize = 13.sp,
                        color = Color(0xFF617386)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val activity = LocalContext.current as? android.app.Activity
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF1E96C8))
                            .clickable(enabled = activity != null) {
                                if (activity != null) {
                                    com.arktools.adsdk.AdHelper.showRewardAd(
                                        activity = activity,
                                        onRewarded = { viewModel.refreshTalentPoolByAd() },
                                        onFailed = { },
                                        onLoadStart = { },
                                        onComplete = { }
                                    )
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "看广告 立即刷新人才池",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            // 三列网格：头像完整居中显示，不再整宽裁切
            val rows = candidates.chunked(3)
            items(rows.size, key = { "talent-row-$it" }) { rowIndex ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rows[rowIndex].forEach { teacher ->
                        CandidateCard(
                            teacher = teacher,
                            sourceLabel = viewModel.talentSourceLabel(teacher.id),
                            modifier = Modifier.weight(1f),
                            onHire = { viewModel.hireTeacher(teacher) }
                        )
                    }
                    repeat(3 - rows[rowIndex].size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("聘用失败") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("知道了")
                }
            }
        )
    }
}

@Composable
private fun CandidateCard(
    teacher: Teacher,
    sourceLabel: String,
    modifier: Modifier = Modifier,
    onHire: () -> Unit
) {
    Column(
        modifier = modifier.background(Color.White)
    ) {
        // 头像：正方形完整显示整脸，不再裁切
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Color(0xFFDCEFF8))
        ) {
            Image(
                painter = painterResource(id = TeacherAvatarHelper.getAvatarResId(teacher)),
                contentDescription = teacher.name,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp),
                contentScale = ContentScale.Fit
            )
            // 等级角标（左上）
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(levelColor(teacher.level))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    teacher.level.name,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (sourceLabel == "校友返校") {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(Color(0xFF2E7D32))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text("校友", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                teacher.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF182635),
                maxLines = 1
            )
            Text(
                teacher.role.displayName,
                fontSize = 10.sp,
                color = Color(0xFF1E96C8),
                maxLines = 1
            )
            Text(
                "综合 ${teacher.averageSkill}",
                fontSize = 10.sp,
                color = Color(0xFF617386)
            )
            Text(
                "月薪 ${String.format("%.2f", teacher.salary)}万",
                fontSize = 10.sp,
                color = Color(0xFF617386)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0B2038))
                    .clickable(onClick = onHire)
                    .padding(vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "聘 用",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun levelColor(level: TeacherLevel): Color = when (level) {
    TeacherLevel.S -> Color(0xFFD95C5C)
    TeacherLevel.A -> Color(0xFFD49A45)
    TeacherLevel.B -> Color(0xFF1E96C8)
    else -> Color(0xFF617386)
}
