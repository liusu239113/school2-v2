package com.arktools.xiao.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

import androidx.compose.ui.window.Dialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import com.arktools.xiao.domain.model.Subject
import com.arktools.xiao.ui.components.EmptyState

@Composable
fun TimetableScreen(
    viewModel: TimetableViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    com.arktools.xiao.ui.components.PixelGameBackground {
    Column(modifier = Modifier.fillMaxSize()) {
    com.arktools.xiao.ui.components.LegacyPageHeader("学期课表")
    Scaffold(containerColor = Color.Transparent) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
        ) {
            // 班级选择
            if (uiState.classes.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.CalendarMonth,
                    title = "暂无班级",
                    description = "学校还没有开设班级，招生后会自动分配班级"
                )
                return@Scaffold
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                items(uiState.classes) { cls ->
                    FilterChip(
                        selected = cls.id == uiState.selectedClassId,
                        onClick = { viewModel.selectClass(cls.id) },
                        label = { Text(cls.displayName, fontSize = 12.sp) }
                    )
                }
            }

            // 课表操作栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = uiState.classes.find { it.id == uiState.selectedClassId }?.let {
                        "班型：${it.classTier.displayName}"
                    } ?: "",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                androidx.compose.material3.TextButton(
                    onClick = { viewModel.openSubjectSettings() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("调整课时", fontSize = 12.sp)
                }
            }

            // 调课提示
            val hint = uiState.swapHint
            if (hint != null) {
                Text(
                    hint,
                    fontSize = 12.sp,
                    color = if (hint.contains("成功")) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            } else {
                Text(
                    "点击课程格子可调课",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            val timetable = uiState.currentTimetable
            if (timetable == null) {
                EmptyState(
                    icon = Icons.Default.CalendarMonth,
                    title = "暂无课表",
                    description = "课表将在每学期初自动生成（2月/9月）"
                )
            } else {
                // 课表网格
                TimetableGrid(
                    timetable = timetable,
                    selectedSlot = uiState.selectedSlot,
                    onSlotClick = { day, period -> viewModel.onSlotClick(day, period) }
                )
            }
        }
    }

    // 课时调整弹窗
    if (uiState.showSubjectSettings) {
        SubjectSettingsDialog(
            hours = uiState.currentSubjectHours,
            error = uiState.subjectSettingsError,
            onDismiss = { viewModel.closeSubjectSettings() },
            onAdjust = { subject, delta -> viewModel.adjustSubjectHours(subject, delta) },
            onSave = { viewModel.saveSubjectHours() },
            onReset = { viewModel.resetSubjectHours() }
        )
    }
    }
    }
}

@Composable
private fun TimetableGrid(
    timetable: com.arktools.xiao.domain.timetable.WeeklyTimetable,
    selectedSlot: SlotPosition? = null,
    onSlotClick: (dayOfWeek: Int, periodIndex: Int) -> Unit = { _, _ -> }
) {
    val periodLabels = listOf("第1节", "第2节", "第3节", "第4节", "第5节", "第6节", "第7节", "第8节")
    val cellWidth = 72.dp
    val headerHeight = 36.dp
    val cellHeight = 52.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            // 左上角空格
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(headerHeight)
                    .border(0.5.dp, Color.LightGray)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text("节次", fontSize = 10.sp, color = Color.Gray)
            }

            // 星期标头
            timetable.days.forEach { day ->
                Box(
                    modifier = Modifier
                        .width(cellWidth)
                        .height(headerHeight)
                        .border(0.5.dp, Color.LightGray)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        day.dayName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // 每节课
        for (periodIndex in 0 until 8) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                // 节次标签
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(cellHeight)
                        .border(0.5.dp, Color.LightGray)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(periodLabels[periodIndex], fontSize = 9.sp, color = Color.Gray)
                }

                // 每天的课程
                timetable.days.forEach { day ->
                    val slot = day.periods.getOrNull(periodIndex)
                    val isSelected = selectedSlot != null &&
                            selectedSlot.dayOfWeek == day.dayOfWeek &&
                            selectedSlot.periodIndex == periodIndex
                    val bgColor = if (isSelected) Color(0xFFFFD54F) else getSubjectColor(slot?.subject)
                    val borderColor = if (isSelected) Color(0xFFFF6F00) else Color.LightGray
                    val borderWidth = if (isSelected) 2.dp else 0.5.dp
                    Box(
                        modifier = Modifier
                            .width(cellWidth)
                            .height(cellHeight)
                            .border(borderWidth, borderColor, RoundedCornerShape(if (isSelected) 4.dp else 0.dp))
                            .background(bgColor)
                            .clickable { onSlotClick(day.dayOfWeek, periodIndex) }
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                slot?.displayName ?: "自习",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (slot?.teacherName != null && slot.subject != null) {
                                Text(
                                    slot.teacherName,
                                    fontSize = 8.sp,
                                    color = Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // 午休分隔
            if (periodIndex == 3) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("— 午休 —", fontSize = 10.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun getSubjectColor(subject: Subject?): Color {
    return when (subject) {
        Subject.CHINESE -> Color(0xFFFFF3E0)
        Subject.MATH -> Color(0xFFE3F2FD)
        Subject.ENGLISH -> Color(0xFFE8F5E9)
        Subject.PHYSICS -> Color(0xFFF3E5F5)
        Subject.CHEMISTRY -> Color(0xFFE0F7FA)
        Subject.BIOLOGY -> Color(0xFFF1F8E9)
        Subject.HISTORY -> Color(0xFFFBE9E7)
        Subject.GEOGRAPHY -> Color(0xFFEDE7F6)
        Subject.POLITICS -> Color(0xFFFFF8E1)
        Subject.PE -> Color(0xFFE8EAF6)
        Subject.ART -> Color(0xFFFCE4EC)
        Subject.MUSIC -> Color(0xFFE0F2F1)
        null -> Color(0xFFF5F5F5)
    }
}

@Composable
private fun SubjectSettingsDialog(
    hours: Map<Subject, Int>,
    error: String?,
    onDismiss: () -> Unit,
    onAdjust: (Subject, Int) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit
) {
    val totalSlots = 5 * 8
    val currentTotal = hours.values.sum()
    val remaining = totalSlots - currentTotal

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "调整科目课时",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "每周共 ${totalSlots} 节，已分配 ${currentTotal} 节，剩余 ${remaining} 节为自习",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        fontSize = 12.sp,
                        color = Color(0xFFD32F2F)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Subject.entries.forEach { subject ->
                        val count = hours.getOrDefault(subject, 0)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(getSubjectColor(subject), RoundedCornerShape(3.dp))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(subject.displayName, fontSize = 13.sp)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                androidx.compose.material3.IconButton(
                                    onClick = { onAdjust(subject, -1) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Text("−", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    text = "$count",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(24.dp),
                                    textAlign = TextAlign.Center
                                )
                                androidx.compose.material3.IconButton(
                                    onClick = { onAdjust(subject, 1) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = onReset,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("恢复默认", fontSize = 12.sp)
                    }
                    androidx.compose.material3.TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消", fontSize = 12.sp)
                    }
                    androidx.compose.material3.Button(
                        onClick = onSave,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("保存", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
