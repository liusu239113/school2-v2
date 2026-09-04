package com.arktools.xiao.ui.teacher

import com.arktools.xiao.ui.components.PixelNineSlice
import com.arktools.xiao.domain.engine.GameBalanceConfig
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.arktools.xiao.R
import com.arktools.xiao.domain.model.Teacher
import com.arktools.xiao.domain.model.TeacherLevel
import com.arktools.xiao.domain.model.TraitCategory
import com.arktools.xiao.domain.teacherdev.*
import com.arktools.xiao.ui.components.PixelAlertDialog
import com.arktools.xiao.ui.components.PixelButton
import com.arktools.xiao.ui.components.PixelButtonStyle
import com.arktools.xiao.ui.theme.AccentGreen
import com.arktools.xiao.ui.theme.AccentOrange
import com.arktools.xiao.ui.theme.AccentRed
import com.arktools.xiao.ui.animation.cardTapAnimation
import com.arktools.xiao.ui.utils.TeacherAvatarHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherListScreen(
    viewModel: TeacherViewModel = hiltViewModel()
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("教师团队", "教师发展")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTabIndex) {
            0 -> TeacherTeamContent(viewModel = viewModel)
            1 -> TeacherDevContent(viewModel = viewModel)
        }
    }
}

// ========== Tab 0: 教师团队 ==========

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeacherTeamContent(
    viewModel: TeacherViewModel
) {
    val teachers by viewModel.teachers.collectAsState()
    val displayTeachers by viewModel.displayTeachers.collectAsState()
    val teachersBySubject by viewModel.teachersBySubject.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val devState by viewModel.devState.collectAsState()
    val showHireDialog by viewModel.showHireDialog.collectAsState()
    val selectedTeacher by viewModel.selectedTeacher.collectAsState()
    val currentGameDay by viewModel.currentGameDay.collectAsState()
    val batchTrainResult by viewModel.batchTrainResult.collectAsState()
    val facultyCoverage by viewModel.collegeFacultyCoverage.collectAsState()
    val headClasses by viewModel.headClasses.collectAsState()
    var showBatchTrainConfirm by remember { mutableStateOf(false) }

    fun classSummaryFor(teacherId: String): String? {
        val list = headClasses[teacherId] ?: return null
        if (list.isEmpty()) return null
        val names = list.joinToString("、") { it.displayName }
        val students = list.sumOf { it.studentCount }
        return "学业导师·$names · $students 人 · 在标准教室授课"
    }

    // 一键培训确认弹窗（先显示预算预估）
    if (showBatchTrainConfirm) {
        val estimatedCost = teachers.sumOf { GameBalanceConfig.getTrainingCost(it.averageSkill) }
        PixelAlertDialog(
            onDismissRequest = { showBatchTrainConfirm = false },
            title = "一键培训预算",
            text = buildString {
                append("将培训全部 ${teachers.size} 名教师\n")
                append("预计总花费：约 ${"%.1f".format(estimatedCost)} 万\n\n")
                append("（费用按各教师技能水平计算，资金不足时会自动停止）")
            },
            confirmText = "开始培训",
            dismissText = "取消",
            onConfirm = {
                showBatchTrainConfirm = false
                viewModel.batchTrainAll()
            },
            onDismiss = { showBatchTrainConfirm = false }
        )
    }

    // 一键培训结果弹窗
    batchTrainResult?.let { result ->
        PixelAlertDialog(
            onDismissRequest = { viewModel.clearBatchTrainResult() },
            title = "一键培训完成",
            text = buildString {
                append("培训人数：${result.totalCount}\n")
                append("成功：${result.successCount} 人\n")
                append("未达预期：${result.failCount} 人\n")
                append("总花费：${"%.1f".format(result.totalCost)} 万")
                if (result.insufficientFunds) {
                    append("\n\n⚠️ 资金不足，部分教师未培训")
                }
            },
            confirmText = "确定",
            onConfirm = { viewModel.clearBatchTrainResult() }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "教师团队",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = { showBatchTrainConfirm = true },
                        enabled = teachers.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("一键培训", fontSize = 12.sp)
                    }
                    Text(
                        text = "${teachers.size} 人",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.onHireClick() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "招聘教师")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (teachers.isEmpty()) {
                EmptyTeacherState()
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 排序/分组选择器
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(TeacherSortMode.entries.toTypedArray()) { mode ->
                            val selected = sortMode == mode
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.setSortMode(mode) },
                                label = { Text(mode.displayName, fontSize = 11.sp) }
                            )
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                    item {
                        FacultyCoverageCard(facultyCoverage)
                    }
                    if (sortMode == TeacherSortMode.BY_SUBJECT) {
                        teachersBySubject.forEach { (subject, group) ->
                            item {
                                Text(
                                    text = "$subject (${group.size}人)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(group) { teacher ->
                                TeacherCard(
                                    teacher = teacher,
                                    trainingCredits = devState.teacherProfiles
                                        .find { it.teacherId == teacher.id }
                                        ?.trainingCredits ?: 0,
                                    isOnTraining = devState.teacherProfiles
                                        .find { it.teacherId == teacher.id }
                                        ?.isOnTraining ?: false,
                                    headClassSummary = classSummaryFor(teacher.id),
                                    onClick = { viewModel.selectTeacher(teacher) }
                                )
                            }
                        }
                    } else {
                        items(displayTeachers) { teacher ->
                            TeacherCard(
                                teacher = teacher,
                                trainingCredits = devState.teacherProfiles
                                    .find { it.teacherId == teacher.id }
                                    ?.trainingCredits ?: 0,
                                isOnTraining = devState.teacherProfiles
                                    .find { it.teacherId == teacher.id }
                                    ?.isOnTraining ?: false,
                                headClassSummary = classSummaryFor(teacher.id),
                                onClick = { viewModel.selectTeacher(teacher) }
                            )
                        }
                    }
                }
            }
        }
    }
    }

    if (showHireDialog) {
        HireTeacherDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.onHireDialogDismiss() }
        )
    }

    selectedTeacher?.let { teacher ->
        TeacherDetailBottomSheet(
            teacher = teacher,
            currentGameDay = currentGameDay,
            onDismiss = { viewModel.clearSelectedTeacher() },
            onFire = { viewModel.fireTeacher(teacher.id) },
            onTrain = { viewModel.trainTeacher(teacher.id) },
            onAdjustSalary = { newSalary ->
                viewModel.adjustSalary(teacher.id, newSalary)
            }
        )
    }
}

// ========== Tab 1: 教师发展 ==========

@Composable
private fun TeacherDevContent(
    viewModel: TeacherViewModel
) {
    val state by viewModel.devState.collectAsState()
    var showTrainingDialog by remember { mutableStateOf(false) }
    var selectedTeacherId by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 概览
            item {
                TeacherDevOverviewCard(state)
            }

            // 培训中
            if (state.activeTrainings.isNotEmpty()) {
                item {
                    Text("培训进行中", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                items(state.activeTrainings) { training ->
                    TrainingCard(training)
                }
            }

            // 教师列表
            item {
                Text("教师发展档案", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            items(state.teacherProfiles.sortedByDescending { it.title.ordinal }) { profile ->
                TeacherProfileCard(
                    profile = profile,
                    onTrain = {
                        selectedTeacherId = profile.teacherId
                        showTrainingDialog = true
                    },
                    onPromote = {
                        viewModel.tryPromote(profile.teacherId) { result ->
                            scope.launch {
                                val msg = when (result) {
                                    PromotionResult.SUCCESS -> "晋升成功！"
                                    PromotionResult.MAX_LEVEL -> "已达最高职称"
                                    PromotionResult.PERSISTENCE_FAILED -> "晋升保存失败，请重试"
                                    PromotionResult.INSUFFICIENT_SENIORITY -> "任职年限不足"
                                    PromotionResult.LOW_EVALUATION -> "评估分数不足"
                                    PromotionResult.INSUFFICIENT_CREDITS -> "培训学分不足"
                                    PromotionResult.NOT_FOUND -> "教师不存在"
                                }
                                snackbarHostState.showSnackbar(msg)
                            }
                        }
                    },
                    onEvaluate = {
                        viewModel.conductEvaluation(profile.teacherId) { result ->
                            scope.launch {
                                val message = if (result == null) {
                                    "评估保存失败，请重试"
                                } else if (result.nextTitleName == null) {
                                    "评估${String.format("%.1f", result.score)}分，已达最高职称"
                                } else if (result.promotionEligibleByScore) {
                                    "评估${String.format("%.1f", result.score)}分，已达到${result.nextTitleName}晋升评估门槛${result.requiredScore?.toInt()}分"
                                } else {
                                    "评估${String.format("%.1f", result.score)}分，距离${result.nextTitleName}晋升门槛还差${((result.requiredScore ?: 0f) - result.score).coerceAtLeast(0f).toInt()}分"
                                }
                                snackbarHostState.showSnackbar(message)
                            }
                        }
                    },
                    promotionReqs = viewModel.getPromotionRequirements(profile.teacherId)
                )
            }

            // 事件日志
            if (state.recentEvents.isNotEmpty()) {
                item {
                    Text("发展记录", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                items(state.recentEvents.take(10)) { event ->
                    TeacherDevEventRow(event)
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    if (showTrainingDialog) {
        TrainingProgramDialog(
            programs = viewModel.getAvailablePrograms(),
            onConfirm = { program ->
                val teacherId = selectedTeacherId
                if (teacherId == null) {
                    showTrainingDialog = false
                } else {
                    viewModel.startTraining(teacherId, program) { result ->
                        scope.launch {
                            snackbarHostState.showSnackbar(result.message)
                        }
                    }
                    showTrainingDialog = false
                    selectedTeacherId = null
                }
            },
            onDismiss = {
                showTrainingDialog = false
                selectedTeacherId = null
            }
        )
    }
}

// ========== 教师团队相关组件 ==========

@Composable
private fun TeacherCard(
    teacher: Teacher,
    trainingCredits: Int = 0,
    isOnTraining: Boolean = false,
    headClassSummary: String? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .cardTapAnimation()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = TeacherAvatarHelper.getAvatarResId(teacher)),
                contentDescription = teacher.name,
                modifier = Modifier.size(56.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = teacher.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TeacherLevelBadge(level = teacher.level)
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = teacher.role.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (trainingCredits > 0) {
                        AssistChip(
                            onClick = {},
                            label = { Text("培训${trainingCredits}学分", fontSize = 9.sp) },
                            modifier = Modifier.height(20.dp),
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color(0xFFE3F2FD)
                            )
                        )
                    }
                    if (isOnTraining) {
                        AssistChip(
                            onClick = {},
                            label = { Text("培训中", fontSize = 9.sp) },
                            modifier = Modifier.height(20.dp),
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color(0xFFFFF3E0)
                            )
                        )
                    }
                }

                headClassSummary?.let { summary ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF1E96C8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "疲劳: ${teacher.fatigue}%",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    LinearProgressIndicator(
                        progress = { teacher.fatigue / 100f },
                        modifier = Modifier.weight(1f),
                        color = when {
                            teacher.fatigue > 80 -> AccentRed
                            teacher.fatigue > 50 -> AccentOrange
                            else -> AccentGreen
                        }
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${teacher.averageSkill}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "综合评分",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TeacherLevelBadge(level: TeacherLevel) {
    val (text, color) = when (level) {
        TeacherLevel.S -> "S" to AccentRed
        TeacherLevel.A -> "A" to AccentOrange
        TeacherLevel.B -> "B" to AccentGreen
        TeacherLevel.C -> "C" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable { }
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun EmptyTeacherState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "暂无教师",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "点击右下角按钮招聘教师",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HireTeacherDialog(
    viewModel: TeacherViewModel,
    onDismiss: () -> Unit
) {
    val candidates by viewModel.candidates.collectAsState()
    val selectedChannel by viewModel.selectedChannel.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val schoolLevel by viewModel.schoolLevel.collectAsState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.dialog_bg),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.FillBounds
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "招聘教师",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "选择招聘渠道",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                RecruitmentChannel.entries.forEach { channel ->
                    val isSelected = selectedChannel == channel
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { viewModel.selectChannel(channel) }
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.bar_bg),
                            contentDescription = null,
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.FillBounds,
                            alpha = if (isSelected) 1f else 0.6f
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = channel.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = channel.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "${String.format("%.1f", channel.cost)}万",
                                style = MaterialTheme.typography.titleSmall,
                                color = AccentOrange
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "候选人",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (candidates.isEmpty()) {
                    Text(
                        text = if (errorMessage != null) "资金不足，无法招聘" else "请先选择招聘渠道",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                } else {
                    candidates.forEach { candidate ->
                        CandidateCard(
                            candidate = candidate,
                            onHire = { viewModel.hireTeacher(candidate) },
                            schoolLevel = schoolLevel
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                PixelButton(
                    text = "关闭",
                    onClick = onDismiss,
                    style = PixelButtonStyle.CANCEL,
                    modifier = Modifier.fillMaxWidth(),
                    height = 44.dp
                )
            }
        }
    }

    errorMessage?.let { msg ->
        PixelAlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = "聘用失败",
            text = msg,
            confirmText = "知道了",
            onConfirm = { viewModel.clearError() }
        )
    }
}

@Composable
private fun CandidateCard(
    candidate: Teacher,
    onHire: () -> Unit,
    schoolLevel: Int = 1
) {
    // 大学模式：所有科目均已解锁，无需检查
    val isSubjectUnlocked = true

    Box(modifier = Modifier.fillMaxWidth()) {
        Image(
            painter = painterResource(id = R.drawable.bar_bg),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.FillBounds
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = TeacherAvatarHelper.getAvatarResId(candidate)),
                contentDescription = candidate.name,
                modifier = Modifier.size(44.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = candidate.name,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "${candidate.role.displayName} | 综合 ${candidate.averageSkill}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "月薪: ${String.format("%.2f", candidate.monthlySalary)}万",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentGreen
                )
                if (!isSubjectUnlocked) {
                    Text(
                        text = "⚠️ 该学科尚未解锁，需升级校区",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFF8800)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            PixelButton(
                text = "招聘",
                onClick = onHire,
                style = PixelButtonStyle.CONFIRM,
                modifier = Modifier.width(72.dp),
                height = 36.dp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TeacherDetailBottomSheet(
    teacher: Teacher,
    currentGameDay: Long,
    onDismiss: () -> Unit,
    onFire: () -> Unit,
    onTrain: () -> Unit,
    onAdjustSalary: (Double) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var showFireConfirm by remember { mutableStateOf(false) }
    var salarySlider by remember { mutableStateOf(teacher.salary.toFloat()) }

    val tenureDays = if (teacher.hireDate > 1_000_000L) {
        -1
    } else if (teacher.hireDate > 0L) {
        (currentGameDay - teacher.hireDate).toInt().coerceAtLeast(0)
    } else {
        0
    }
    val tenureText = when {
        tenureDays < 0 -> "资深教师"
        tenureDays < 30 -> "入职 ${tenureDays} 天"
        tenureDays < 360 -> "入职 ${tenureDays / 30} 个月"
        else -> "入职 ${tenureDays / 360} 年 ${(tenureDays % 360) / 30} 个月"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = TeacherAvatarHelper.getAvatarResId(teacher)),
                    contentDescription = teacher.name,
                    modifier = Modifier.size(56.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = teacher.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TeacherLevelBadge(level = teacher.level)
                    }
                    Text(
                        text = "${teacher.role.displayName} · $tenureText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${teacher.averageSkill}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "综合",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (teacher.traits.isNotEmpty()) {
                Text(
                    text = "教师特质",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    teacher.traits.forEach { trait ->
                        val (bgColor, textColor) = when (trait.category) {
                            TraitCategory.POSITIVE -> Color(0xFF4CAF50).copy(alpha = 0.12f) to Color(0xFF2E7D32)
                            TraitCategory.NEUTRAL -> Color(0xFFFF9800).copy(alpha = 0.12f) to Color(0xFFE65100)
                            TraitCategory.NEGATIVE -> Color(0xFFF44336).copy(alpha = 0.12f) to Color(0xFFC62828)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(bgColor)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Column {
                                Text(
                                    text = trait.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                                Text(
                                    text = trait.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = textColor.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "能力详情",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            SkillBar("教学", teacher.teaching, Color(0xFF4CAF50))
            SkillBar("研发", teacher.research, Color(0xFF2196F3))
            SkillBar("管理", teacher.management, Color(0xFF9C27B0))
            SkillBar("心理", teacher.psychology, Color(0xFFFF9800))

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("疲劳度", style = MaterialTheme.typography.labelSmall)
                    LinearProgressIndicator(
                        progress = { teacher.fatigue / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = when {
                            teacher.fatigue > 80 -> AccentRed
                            teacher.fatigue > 50 -> AccentOrange
                            else -> AccentGreen
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                    Text("${teacher.fatigue}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("忠诚度", style = MaterialTheme.typography.labelSmall)
                    LinearProgressIndicator(
                        progress = { teacher.loyalty / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = when {
                            teacher.loyalty < 30 -> AccentRed
                            teacher.loyalty < 60 -> AccentOrange
                            else -> AccentGreen
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                    Text("${teacher.loyalty}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("调整薪资", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "当前: ${String.format("%.1f", teacher.monthlySalary)}万/月",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Slider(
                value = salarySlider,
                onValueChange = { salarySlider = it },
                valueRange = 0.5f..10f,
                steps = 19
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "调整后: ${String.format("%.1f", salarySlider)}万/月",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                PixelButton(
                    text = "确认",
                    onClick = { onAdjustSalary(salarySlider.toDouble()) },
                    style = PixelButtonStyle.CONFIRM,
                    modifier = Modifier.width(72.dp),
                    height = 36.dp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val trainCost = GameBalanceConfig.getTrainingCost(teacher.averageSkill)
                val trainRate = (GameBalanceConfig.getTrainingSuccessRate(teacher.averageSkill) * 100).toInt()
                PixelButton(
                    text = "培训 (${"%.0f".format(trainCost)}万·${trainRate}%)",
                    onClick = onTrain,
                    style = PixelButtonStyle.PRIMARY,
                    modifier = Modifier.weight(1f),
                    height = 42.dp
                )
                PixelButton(
                    text = "解雇",
                    onClick = { showFireConfirm = true },
                    style = PixelButtonStyle.DANGER,
                    modifier = Modifier.weight(1f),
                    height = 42.dp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showFireConfirm) {
        PixelAlertDialog(
            onDismissRequest = { showFireConfirm = false },
            title = "确认解雇",
            text = "确定要解雇 ${teacher.name} 吗？",
            confirmText = "确认",
            dismissText = "取消",
            onConfirm = {
                onFire()
                showFireConfirm = false
            },
            onDismiss = { showFireConfirm = false },
            confirmStyle = PixelButtonStyle.DANGER,
            dismissStyle = PixelButtonStyle.CANCEL
        )
    }
}

// ========== 教师发展相关组件 ==========

@Composable
private fun TeacherDevOverviewCard(state: TeacherDevState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF1565C0), Color(0xFF0D47A1))
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Text("教师发展中心", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DevStatItem("教师总数", "${state.teacherProfiles.size}", Color.White)
                    DevStatItem("培训中", "${state.activeTrainings.size}", Color(0xFFFFD54F))
                    DevStatItem("累计晋升", "${state.totalPromotions}", Color(0xFF81C784))
                    DevStatItem("流失人数", "${state.totalDepartures}", Color(0xFFFF8A65))
                }
                Spacer(modifier = Modifier.height(12.dp))
                val titleDist = state.teacherProfiles.groupBy { it.title }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TeacherTitle.entries.forEach { title ->
                        val count = titleDist[title]?.size ?: 0
                        if (count > 0) {
                            AssistChip(
                                onClick = {},
                                label = { Text("${title.displayName}:$count", fontSize = 10.sp, color = Color.White) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = Color.White.copy(alpha = 0.2f)
                                ),
                                border = null
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DevStatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
    }
}

@Composable
private fun TrainingCard(training: ActiveTraining) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(training.teacherName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(training.program.displayName, fontSize = 12.sp, color = Color.Gray)
                }
                Text("剩${training.remainingMonths}月", fontSize = 12.sp, color = Color(0xFF1565C0))
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { 1f - training.remainingMonths.toFloat() / training.totalMonths.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = Color(0xFF1565C0),
                trackColor = Color(0xFFBBDEFB)
            )
        }
    }
}

@Composable
private fun TeacherProfileCard(
    profile: TeacherProfile,
    onTrain: () -> Unit,
    onPromote: () -> Unit,
    onEvaluate: () -> Unit,
    promotionReqs: PromotionRequirements?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(profile.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        AssistChip(
                            onClick = {},
                            label = { Text(profile.title.displayName, fontSize = 10.sp) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color(0xFFE3F2FD)
                            )
                        )
                    }
                    Text("${profile.subject} · 服务${profile.yearsOfService}年", fontSize = 12.sp, color = Color.Gray)
                }
                Text(
                    profile.turnoverRisk.displayName,
                    fontSize = 11.sp,
                    color = Color(profile.turnoverRisk.color),
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MiniStat("技能", "${profile.skillLevel.toInt()}")
                MiniStat("满意度", "${profile.satisfaction.toInt()}")
                MiniStat("评估", "${profile.evaluationScore.toInt()}")
                MiniStat("学分", "${profile.trainingCredits}")
                MiniStat("研究", "${profile.researchPoints}")
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (promotionReqs != null) {
                Text("晋升→${promotionReqs.nextTitle.displayName}", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val monthOk = promotionReqs.monthsHad >= promotionReqs.monthsNeeded
                    val evalOk = promotionReqs.evalHad >= promotionReqs.evalNeeded
                    val creditOk = promotionReqs.creditsHad >= promotionReqs.creditsNeeded
                    ReqChip("资历${promotionReqs.monthsHad}/${promotionReqs.monthsNeeded}月", monthOk)
                    ReqChip("评估${promotionReqs.evalHad.toInt()}/${promotionReqs.evalNeeded.toInt()}", evalOk)
                    ReqChip("学分${promotionReqs.creditsHad}/${promotionReqs.creditsNeeded}", creditOk)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onTrain,
                    enabled = !profile.isOnTraining,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text(if (profile.isOnTraining) "培训中" else "安排培训", fontSize = 11.sp)
                }
                FilledTonalButton(
                    onClick = onPromote,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text("尝试晋升", fontSize = 11.sp)
                }
                FilledTonalButton(
                    onClick = onEvaluate,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text("评估", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 10.sp, color = Color.Gray)
    }
}

@Composable
private fun ReqChip(text: String, met: Boolean) {
    AssistChip(
        onClick = {},
        label = { Text(text, fontSize = 9.sp) },
        leadingIcon = {
            Icon(
                if (met) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (met) Color(0xFF4CAF50) else Color.Gray
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (met) Color(0xFFE8F5E9) else Color(0xFFF5F5F5)
        )
    )
}

@Composable
private fun TeacherDevEventRow(event: TeacherDevEvent) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = when (event.type) {
            TeacherDevEventType.TRAINING_COMPLETE -> "培训"
            TeacherDevEventType.PROMOTION -> "升级"
            TeacherDevEventType.DEPARTURE -> "离职"
            TeacherDevEventType.RETIREMENT -> "荣休"
            TeacherDevEventType.AWARD -> "获奖"
            TeacherDevEventType.EVALUATION -> "评价"
        }
        Text(icon, fontSize = 16.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(event.title, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(event.description, fontSize = 11.sp, color = Color.Gray)
        }
        Text("${event.year}/${event.month}", fontSize = 10.sp, color = Color.Gray)
    }
}

@Composable
private fun TrainingProgramDialog(
    programs: List<TrainingProgram>,
    onConfirm: (TrainingProgram) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedIndex by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.8f)
        ) {
            Image(
                painter = painterResource(id = R.drawable.dialog_bg),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.FillBounds
            )
            Column(
                modifier = Modifier.padding(24.dp).fillMaxHeight()
            ) {
                Text(
                    text = "选择培训课程",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                // 列表可滚动，确保底部按钮始终可见
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    programs.forEachIndexed { index, program ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedIndex == index,
                                onClick = { selectedIndex = index }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(program.displayName, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text(program.description, fontSize = 11.sp, color = Color.Gray)
                                Text(
                                    "${program.durationMonths}个月 · ¥${program.monthlyCostWan}万/月 · 学分+${program.creditReward} · 技能+${program.skillBoost.toInt()}",
                                    fontSize = 10.sp, color = Color(0xFF1565C0)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PixelButton(
                        text = "取消",
                        style = PixelButtonStyle.CANCEL,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    PixelButton(
                        text = "确认安排",
                        style = PixelButtonStyle.CONFIRM,
                        onClick = {
                            val program = programs.getOrNull(selectedIndex) ?: return@PixelButton
                            onConfirm(program)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// ========== 通用组件 ==========

@Composable
private fun SkillBar(label: String, value: Int, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(36.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        LinearProgressIndicator(
            progress = { value / 100f },
            modifier = Modifier
                .weight(1f)
                .height(10.dp),
            color = color,
            trackColor = color.copy(alpha = 0.12f),
            strokeCap = StrokeCap.Round
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$value",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

// ========== 学院师资覆盖卡片 ==========

@Composable
private fun FacultyCoverageCard(
    coverage: com.arktools.xiao.domain.model.FacultyCoverage
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        PixelNineSlice(
                    res = R.drawable.card_bg,
                    slice = 48,
                    modifier = Modifier.matchParentSize()
                )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (coverage.coverageRatio < 1f) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_faculty_gap),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = "学院师资覆盖",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${(coverage.coverageRatio * 100).toInt()}%",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (coverage.coverageRatio >= 1f) AccentGreen else AccentOrange
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "缺编会拉低该学院学生的掌握度和毕业分数",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            coverage.lines.forEach { line ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = line.college.displayName,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (line.missingRoles.isEmpty()) {
                        Text(
                            text = "已配齐",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text(
                            text = "${line.covered}/${line.required} 缺${line.missingRoles.joinToString("、") { it.displayName }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentOrange,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
