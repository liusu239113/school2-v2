package com.arktools.xiaozhang.ui.graduate

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
import androidx.compose.foundation.layout.width
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
import com.arktools.xiaozhang.domain.graduate.GradStudent
import java.util.Locale

/**
 * 研究生院：在读硕博、导师分配、进度与科研经费。
 * 深底白字，与外联/治院/学科建设同风格。
 */
@Composable
fun GraduateScreen(
    viewModel: GraduateViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1724))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("研究生院", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "硕博生按月带来科研经费；分配导师后修业进度翻倍，毕业授予学位提升声誉",
                color = Color(0xFFB8C7D6),
                fontSize = 13.sp
            )
        }

        state.message?.let { msg ->
            item {
                Text(
                    msg,
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xCC14648C))
                        .padding(10.dp)
                        .clickable { viewModel.consumeMessage() }
                )
            }
        }

        if (!state.programOn) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF3A2A18))
                        .padding(14.dp)
                ) {
                    Text("硕博点尚未启动", color = Color(0xFFFFD54F), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "前往「治院 → 大学政策」申请硕博点：研究型大学校园 Lv.3，其他层次 Lv.5，投入 200 万启动。" +
                            "启动后每年 9 月按名额招收硕士/博士（名额由校园等级与学科评级决定）。",
                        color = Color(0xFFB8C7D6),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            val masters = state.rows.count { it.student.type == "MASTER" }
            val phds = state.rows.count { it.student.type == "PHD" }
            val income = state.rows.sumOf { if (it.student.type == "PHD") 0.5 else 0.2 }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF12283C))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Stat("在读", "${state.rows.size} 人（硕$masters/博$phds）")
                    Stat("今年名额", "硕${state.quotaMaster}/博${state.quotaPhd}")
                    Stat("月科研经费", String.format(Locale.CHINA, "%.1f 万", income))
                }
            }

            val unassigned = state.rows.filter { it.advisorName == null }
            val assigned = state.rows.filter { it.advisorName != null }

            if (unassigned.isNotEmpty()) {
                item {
                    Text("待分配导师（进度减半）", color = Color(0xFFE0A05A), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                items(unassigned.size) { i ->
                    StudentCard(unassigned[i], showAssign = true) { viewModel.openPicker(it) }
                }
            }
            if (assigned.isNotEmpty()) {
                item {
                    Text("培养中", color = Color(0xFF1E96C8), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                items(assigned.size) { i ->
                    StudentCard(assigned[i], showAssign = false) { viewModel.openPicker(it) }
                }
            }
            if (state.rows.isEmpty()) {
                item {
                    Text(
                        "暂无在读研究生。每年 9 月按名额自动招生，学科评级越高名额越多。",
                        color = Color(0xFF8FA6BB),
                        fontSize = 13.sp
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    state.pickingStudentId?.let { studentId ->
        AdvisorPickerDialog(
            options = state.teachers,
            onPick = { viewModel.assign(studentId, it) },
            onDismiss = { viewModel.closePicker() }
        )
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, color = Color(0xFF8FA6BB), fontSize = 11.sp)
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StudentCard(
    row: GraduateViewModel.StudentRow,
    showAssign: Boolean,
    onAssign: (String) -> Unit
) {
    val s: GradStudent = row.student
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF12283C))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(if (s.type == "PHD") Color(0xFF5A3A62) else Color(0xFF1E3A5C))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(s.typeName, color = Color(0xFFFFD54F), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            Text(row.disciplineName, color = Color(0xFF8FA6BB), fontSize = 12.sp)
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                if (s.advisorId != null) {
                    "导师：${row.advisorName} · 进度 ${"%.1f".format(s.yearsDone)}/${"%.0f".format(s.needYears)} 年"
                } else {
                    "进度 ${"%.1f".format(s.yearsDone)}/${"%.0f".format(s.needYears)} 年（无导师，进度减半）"
                },
                color = Color(0xFFB8C7D6),
                fontSize = 12.sp
            )
            if (showAssign) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1E96C8))
                        .clickable { onAssign(s.id) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("分配导师", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AdvisorPickerDialog(
    options: List<GraduateViewModel.TeacherOption>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择学业导师") },
        text = {
            LazyColumn(modifier = Modifier.height(320.dp)) {
                if (options.isEmpty()) {
                    item { Text("暂无在职教师", color = Color(0xFF8FA6BB), fontSize = 13.sp) }
                }
                items(options.size) { i ->
                    val opt = options[i]
                    val canPick = opt.remaining > 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = canPick) { onPick(opt.teacher.id) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${opt.teacher.name} · ${opt.teacher.level.name} 级",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "已带 ${opt.load}/${opt.capacity} 人",
                                fontSize = 11.sp,
                                color = Color(0xFF8FA6BB)
                            )
                        }
                        Text(
                            if (canPick) "指派" else "已满",
                            color = if (canPick) Color(0xFF1E96C8) else Color(0xFF8C8C8C),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            Text(
                "关闭",
                modifier = Modifier
                    .clickable { onDismiss() }
                    .padding(8.dp),
                color = Color(0xFF1E96C8),
                fontWeight = FontWeight.Bold
            )
        }
    )
}
