package com.arktools.xiaozhang.ui.parent

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.arktools.xiaozhang.R
import com.arktools.xiaozhang.domain.parent.*
import com.arktools.xiaozhang.ui.components.PixelButton
import com.arktools.xiaozhang.ui.components.PixelButtonStyle

@Composable
fun ParentScreen(viewModel: ParentViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var showMeetingDialog by remember { mutableStateOf(false) }

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
                                listOf(Color(0xFFFF8F00), Color(0xFFF57C00))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Text("家长满意度", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("综合满意度", fontSize = 12.sp, color = Color.White.copy(0.8f))
                                Text(
                                    "${state.overallSatisfaction.toInt()}分",
                                    fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("口碑等级", fontSize = 12.sp, color = Color.White.copy(0.8f))
                                Text(
                                    state.wordOfMouth.displayName,
                                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { state.overallSatisfaction / 100f },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = Color.White,
                            trackColor = Color.White.copy(0.3f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatChip("信任度", "${state.trustLevel.toInt()}", Color.White)
                            StatChip("沟通分", "${state.communicationScore.toInt()}", Color.White)
                            StatChip("待处理", "${viewModel.getPendingComplaintCount()}", Color.White)
                            StatChip("趋势", if (state.monthlyTrend >= 0) "↑" else "↓", Color.White)
                        }
                    }
                }
            }
        }

        // 操作按钮
        item {
            PixelButton(
                onClick = { showMeetingDialog = true },
                text = "安排家长会",
                style = PixelButtonStyle.PRIMARY,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 投诉列表
        val pendingComplaints = state.complaints.filter { it.status == ComplaintStatus.PENDING }
        if (pendingComplaints.isNotEmpty()) {
            item {
                Text("待处理投诉 (${pendingComplaints.size})",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            items(pendingComplaints) { complaint ->
                ComplaintCard(
                    complaint = complaint,
                    onResolve = { viewModel.resolveComplaint(complaint.id) },
                    onIgnore = { viewModel.ignoreComplaint(complaint.id) }
                )
            }
        }

        // 已安排会议
        val upcomingMeetings = state.meetings.filter { !it.isCompleted }
        if (upcomingMeetings.isNotEmpty()) {
            item {
                Text("已安排会议", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            items(upcomingMeetings) { meeting ->
                MeetingCard(
                    meeting = meeting,
                    onComplete = { viewModel.completeMeeting(meeting.id) }
                )
            }
        }

        // 最近事件
        if (state.recentEvents.isNotEmpty()) {
            item {
                Text("最近动态", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            items(state.recentEvents) { event ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null,
                            tint = Color(0xFFFF8F00), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(event, fontSize = 14.sp)
                    }
                }
            }
        }

        // 历史会议
        val completedMeetings = state.meetings.filter { it.isCompleted }.takeLast(5)
        if (completedMeetings.isNotEmpty()) {
            item {
                Text("会议记录", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            items(completedMeetings) { meeting ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(meeting.type.displayName, fontWeight = FontWeight.Medium)
                            Text("出席率: ${(meeting.attendanceRate * 100).toInt()}%",
                                fontSize = 12.sp, color = Color.Gray)
                        }
                        Text("+${meeting.satisfactionGained.toInt()}满意度",
                            color = Color(0xFF388E3C), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }

    if (showMeetingDialog) {
        MeetingDialog(
            meetingTypes = viewModel.getMeetingTypes(),
            onSchedule = { type ->
                viewModel.scheduleMeeting(type, 2024, 1, 100)
                showMeetingDialog = false
            },
            onDismiss = { showMeetingDialog = false }
        )
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 10.sp, color = color.copy(0.8f))
    }
}

@Composable
private fun ComplaintCard(
    complaint: Complaint,
    onResolve: () -> Unit,
    onIgnore: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                complaint.severity >= 4 -> Color(0xFFFFEBEE)
                complaint.severity >= 3 -> Color(0xFFFFF8E1)
                else -> Color(0xFFF5F5F5)
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(complaint.type.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "严重度: ${complaint.severity}/5",
                    fontSize = 12.sp,
                    color = if (complaint.severity >= 4) Color.Red else Color(0xFFF57C00)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(complaint.description, fontSize = 13.sp, color = Color.DarkGray)
            Spacer(Modifier.height(4.dp))
            Text("投诉人: ${complaint.parentName}（学生${complaint.studentName}）",
                fontSize = 11.sp, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelButton(
                    onClick = onResolve,
                    text = "处理",
                    style = PixelButtonStyle.CONFIRM,
                    modifier = Modifier.weight(1f)
                )
                PixelButton(
                    onClick = onIgnore,
                    text = "忽略",
                    style = PixelButtonStyle.CANCEL,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MeetingCard(meeting: ParentMeeting, onComplete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(meeting.type.displayName, fontWeight = FontWeight.Medium)
                Text("费用: ¥${meeting.cost.toInt()}", fontSize = 12.sp, color = Color.Gray)
            }
            PixelButton(
                onClick = onComplete,
                text = "举办",
                style = PixelButtonStyle.PRIMARY
            )
        }
    }
}

@Composable
private fun MeetingDialog(
    meetingTypes: List<MeetingType>,
    onSchedule: (MeetingType) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.dialog_bg),
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
                    text = "安排家长会",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    meetingTypes.forEach { type ->
                        Card(
                            onClick = { onSchedule(type) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(type.displayName, fontWeight = FontWeight.Bold)
                                Text("满意度+${type.satisfactionBoost.toInt()} | 费用¥${type.costPerParent.toInt()}/人",
                                    fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    PixelButton(
                        onClick = onDismiss,
                        text = "取消",
                        style = PixelButtonStyle.CANCEL
                    )
                }
            }
        }
    }
}
