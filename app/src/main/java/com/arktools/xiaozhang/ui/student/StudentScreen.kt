package com.arktools.xiaozhang.ui.student

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.EmojiPeople
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arktools.xiaozhang.domain.model.GradeLevel
import com.arktools.xiaozhang.domain.model.HealthStatus
import com.arktools.xiaozhang.domain.model.SchoolClass
import com.arktools.xiaozhang.domain.model.Student
import com.arktools.xiaozhang.domain.model.StudentAttributes
import com.arktools.xiaozhang.domain.model.StudentStatus
import com.arktools.xiaozhang.domain.model.StudentTrait
import com.arktools.xiaozhang.ui.theme.AccentGreen
import com.arktools.xiaozhang.ui.theme.AccentOrange
import com.arktools.xiaozhang.ui.theme.AccentRed
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentScreen(
    viewModel: StudentViewModel = hiltViewModel()
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("学生管理", "班级管理")

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
            0 -> StudentManageContent(viewModel = viewModel)
            1 -> ClassManageContent(viewModel = viewModel)
        }
    }
}

// ==================== 学生管理内容 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentManageContent(viewModel: StudentViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var showSearch by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // 搜索和工具栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { showSearch = !showSearch }) {
                Icon(Icons.Default.Search, contentDescription = "搜索")
            }
            TextButton(onClick = { viewModel.toggleGroupByCourse() }) {
                Icon(Icons.Default.FilterList, contentDescription = "分组", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (uiState.groupByCourse) "列表" else "分组")
            }
            Text(
                text = "在读 ${uiState.totalActiveCount}",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // 搜索栏
        AnimatedVisibility(visible = showSearch) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("搜索学生姓名或特质...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "清除")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
        }

        // 统计卡片
        StudentStatsRow(uiState)

        Spacer(modifier = Modifier.height(8.dp))

        // 筛选器
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StudentFilterStatus.entries.forEach { filter ->
                FilterChip(
                    selected = uiState.filterStatus == filter,
                    onClick = { viewModel.setFilter(filter) },
                    label = { Text(filter.displayName) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 学生列表
        val filteredStudents = viewModel.getFilteredStudents()

        if (filteredStudents.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (uiState.searchQuery.isNotEmpty()) {
                        "未找到匹配的学生"
                    } else when (uiState.filterStatus) {
                        StudentFilterStatus.ACTIVE -> "暂无在读学生\n等待9月招生季自动招生"
                        StudentFilterStatus.GRADUATED -> "暂无毕业学生"
                        StudentFilterStatus.DROPPED -> "暂无退学记录"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (uiState.groupByCourse) {
            val grouped = viewModel.getGroupedStudents()
            val expandState = remember { mutableStateMapOf<String, Boolean>() }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                grouped.forEach { (courseName, students) ->
                    val isExpanded = expandState[courseName] != false
                    item(key = "header_$courseName") {
                        CourseGroupHeader(
                            courseName = courseName,
                            studentCount = students.size,
                            isExpanded = isExpanded,
                            onClick = { expandState[courseName] = !isExpanded }
                        )
                    }
                    if (isExpanded) {
                        items(students, key = { it.id }) { student ->
                            StudentCard(
                                student = student,
                                courseName = uiState.courseNames[student.id]
                                    ?: com.arktools.xiaozhang.domain.model.UniversityAcademicCatalog.pathLabel(
                                        student.gradeLevel,
                                        student.courseId
                                    ),
                                showCourseName = false,
                                onClick = { viewModel.selectStudent(student) }
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                items(filteredStudents, key = { it.id }) { student ->
                    StudentCard(
                        student = student,
                        courseName = uiState.courseNames[student.id]
                            ?: com.arktools.xiaozhang.domain.model.UniversityAcademicCatalog.pathLabel(
                                student.gradeLevel,
                                student.courseId
                            ),
                        showCourseName = true,
                        onClick = { viewModel.selectStudent(student) }
                    )
                }
            }
        }
    }

    // 学生详情底部弹窗
    uiState.selectedStudent?.let { student ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.clearSelectedStudent() },
            sheetState = rememberModalBottomSheetState()
        ) {
            StudentDetailSheet(
                student = student,
                courseName = uiState.courseNames[student.id]
                    ?: com.arktools.xiaozhang.domain.model.UniversityAcademicCatalog.pathLabel(
                        student.gradeLevel,
                        student.courseId
                    ),
                latestScores = viewModel.getStudentLatestScores(student.id)
            )
        }
    }
}

// ==================== 班级管理内容 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClassManageContent(viewModel: StudentViewModel) {
    val classState by viewModel.classUiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(classState.message) {
        classState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearClassMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SnackbarHost(hostState = snackbarHostState)

        // 顶部统计 + 一键分配学业导师
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${viewModel.getTotalClassCount()}班 / ${viewModel.getTotalStudentCountFromClasses()}人",
                style = MaterialTheme.typography.bodyMedium
            )

            val allClasses = classState.classesByGrade.values.flatten()
            val classesWithoutHead = allClasses.count { it.headTeacherId == null && it.studentCount > 0 }
            if (classesWithoutHead > 0) {
                Button(
                    onClick = { viewModel.autoAssignAllHeadTeachers() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("一键分配学业导师(${classesWithoutHead})", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // 年级切换
        GradeTabRow(
            selectedGrade = classState.selectedGrade,
            classesByGrade = classState.classesByGrade,
            onGradeSelected = { viewModel.selectGrade(it) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 当前年级的班级列表
        val currentGradeClasses = classState.classesByGrade[classState.selectedGrade] ?: emptyList()

        if (currentGradeClasses.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "该年级暂无班级\n招生后将自动创建班级",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                items(currentGradeClasses, key = { it.id }) { schoolClass ->
                    ClassCard(
                        schoolClass = schoolClass,
                        headTeacherName = schoolClass.headTeacherId?.let { classState.teacherNames[it] },
                        onClick = { viewModel.selectClass(schoolClass) }
                    )
                }
            }
        }
    }

    // 班级详情底部弹窗
    classState.selectedClass?.let { schoolClass ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.clearSelectedClass() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            ClassDetailSheet(
                schoolClass = schoolClass,
                students = classState.classStudents,
                headTeacherName = schoolClass.headTeacherId?.let { classState.teacherNames[it] },
                onAssignHeadTeacher = { viewModel.showHeadTeacherDialog() },
                onStudentClick = { viewModel.selectClassStudent(it) }
            )
        }
    }

    // 学生详情底部弹窗（班级内）
    classState.selectedStudent?.let { student ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.clearClassSelectedStudent() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            StudentDetailWithRadar(student = student)
        }
    }

    // 学业导师任命对话框
    if (classState.showHeadTeacherDialog) {
        HeadTeacherAssignDialog(
            currentTeacherId = classState.selectedClass?.headTeacherId,
            availableTeachers = classState.availableTeachers,
            onAssign = { viewModel.assignHeadTeacher(it) },
            onRemove = { viewModel.removeHeadTeacher() },
            onDismiss = { viewModel.dismissHeadTeacherDialog() }
        )
    }
}

// ==================== 共用组件 ====================

@Composable
private fun CourseGroupHeader(
    courseName: String,
    studentCount: Int,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.School,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = courseName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${studentCount}人",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StudentStatsRow(uiState: StudentUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatMiniCard(
            modifier = Modifier.weight(1f),
            label = "在读",
            value = "${uiState.totalActiveCount}",
            icon = Icons.Default.EmojiPeople,
            color = AccentGreen
        )
        StatMiniCard(
            modifier = Modifier.weight(1f),
            label = "毕业",
            value = "${uiState.totalGraduateCount}",
            icon = Icons.Default.School,
            color = MaterialTheme.colorScheme.primary
        )
        StatMiniCard(
            modifier = Modifier.weight(1f),
            label = "满意度",
            value = "${uiState.averageSatisfaction.toInt()}%",
            icon = Icons.Default.TrendingUp,
            color = if (uiState.averageSatisfaction >= 60f) AccentGreen else AccentRed
        )
        StatMiniCard(
            modifier = Modifier.weight(1f),
            label = "口碑",
            value = String.format("%.1f", uiState.averageGraduateRating),
            icon = Icons.Default.Star,
            color = AccentOrange
        )
    }
}

@Composable
private fun StatMiniCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudentCard(
    student: Student,
    courseName: String?,
    showCourseName: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .animateContentSize(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = student.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        val statusColor = when (student.status) {
                            StudentStatus.ENROLLED -> AccentOrange
                            StudentStatus.STUDYING -> AccentGreen
                            StudentStatus.GRADUATED -> MaterialTheme.colorScheme.primary
                            StudentStatus.DROPPED -> AccentRed
                        }
                        Text(text = student.status.displayName, style = MaterialTheme.typography.labelSmall, color = statusColor)
                    }
                    if (showCourseName && courseName != null) {
                        Text(text = courseName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                student.review?.let { review ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "${review.rating}/5", color = AccentOrange, style = MaterialTheme.typography.bodySmall)
                        if (student.academicScore > 0f) {
                            Text(text = "${student.academicScore.toInt()}分", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (student.traits.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    student.traits.forEach { trait -> TraitChip(trait = trait) }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (student.status == StudentStatus.STUDYING || student.status == StudentStatus.ENROLLED) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { student.semesterMastery / 100f },
                        modifier = Modifier.weight(1f).height(6.dp),
                        color = AccentGreen,
                        trackColor = AccentGreen.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "掌握度 ${student.semesterMastery.toInt()}%", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "满意度", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(4.dp))
                val satColor = when {
                    student.satisfaction >= 70f -> AccentGreen
                    student.satisfaction >= 40f -> AccentOrange
                    else -> AccentRed
                }
                LinearProgressIndicator(
                    progress = { student.satisfaction / 100f },
                    modifier = Modifier.width(60.dp).height(4.dp),
                    color = satColor,
                    trackColor = satColor.copy(alpha = 0.2f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "${student.satisfaction.toInt()}", style = MaterialTheme.typography.labelSmall, color = satColor)
            }
        }
    }
}

@Composable
private fun TraitChip(trait: StudentTrait) {
    val chipColor = if (trait.isPositive) AccentGreen.copy(alpha = 0.15f) else AccentRed.copy(alpha = 0.12f)
    val textColor = if (trait.isPositive) AccentGreen else AccentRed

    Surface(shape = RoundedCornerShape(12.dp), color = chipColor) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(textColor))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = trait.displayName, style = MaterialTheme.typography.labelSmall, color = textColor, fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudentDetailSheet(
    student: Student,
    courseName: String,
    latestScores: List<com.arktools.xiaozhang.domain.exam.StudentScore> = emptyList()
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = student.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(12.dp))
            val statusColor = when (student.status) {
                StudentStatus.ENROLLED -> AccentOrange
                StudentStatus.STUDYING -> AccentGreen
                StudentStatus.GRADUATED -> MaterialTheme.colorScheme.primary
                StudentStatus.DROPPED -> AccentRed
            }
            Surface(shape = RoundedCornerShape(12.dp), color = statusColor.copy(alpha = 0.15f)) {
                Text(
                    text = student.status.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "${student.gradeLevel.displayName} · $courseName", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (student.traits.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "学生特质", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            student.traits.forEach { trait ->
                TraitDetailRow(trait = trait)
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "学习属性", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        DetailRow("天赋", String.format("%.0f%%", student.talent * 100))
        DetailRow("学习动力", String.format("%.0f%%", student.motivation * 100))
        DetailRow("满意度", "${student.satisfaction.toInt()}/100")
        DetailRow("学期掌握度", "${student.semesterMastery.toInt()}%")
        if (student.academicScore > 0f) { DetailRow("学业成绩", "${student.academicScore.toInt()}分") }

        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "时间线", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        DetailRow("入学时间", "${student.enrollYear}年${student.enrollMonth}月")
        if (student.graduateYear != null) { DetailRow("毕业时间", "${student.graduateYear}年${student.graduateMonth}月") }

        if (latestScores.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "最近考试成绩", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.padding(12.dp)) {
                    latestScores.forEach { score ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = score.subject.displayName, style = MaterialTheme.typography.bodyMedium)
                            val scoreColor = when {
                                score.normalizedScore >= 90f -> AccentGreen
                                score.normalizedScore >= 70f -> AccentOrange
                                else -> AccentRed
                            }
                            Text(
                                text = "${score.score.toInt()} / ${score.maxScore.toInt()} (${score.grade})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = scoreColor
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    val avgScore = latestScores.map { it.normalizedScore }.average()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "平均得分率", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(text = String.format("%.1f%%", avgScore), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        student.review?.let { review ->
            Spacer(modifier = Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "口碑评价", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "${review.rating}/5", color = AccentOrange)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "\"${review.comment}\"", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "声誉影响: ${if (review.reputationImpact >= 0) "+" else ""}${review.reputationImpact}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (review.reputationImpact >= 0) AccentGreen else AccentRed
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun TraitDetailRow(trait: StudentTrait) {
    val bgColor = if (trait.isPositive) AccentGreen.copy(alpha = 0.08f) else AccentRed.copy(alpha = 0.06f)
    val iconColor = if (trait.isPositive) AccentGreen else AccentRed

    Surface(shape = RoundedCornerShape(8.dp), color = bgColor) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(iconColor))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = trait.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = iconColor)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = trait.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

// ==================== 班级管理组件 ====================

@Composable
private fun GradeTabRow(
    selectedGrade: GradeLevel,
    classesByGrade: Map<GradeLevel, List<SchoolClass>>,
    onGradeSelected: (GradeLevel) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GradeLevel.entries.forEach { grade ->
            val count = classesByGrade[grade]?.sumOf { it.studentCount } ?: 0
            FilterChip(
                selected = selectedGrade == grade,
                onClick = { onGradeSelected(grade) },
                label = { Text("${grade.displayName} (${count}人)") }
            )
        }
    }
}

@Composable
private fun ClassCard(
    schoolClass: SchoolClass,
    headTeacherName: String?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).animateContentSize(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = schoolClass.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (schoolClass.gradeRanking in 1..3) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(18.dp),
                                tint = when (schoolClass.gradeRanking) { 1 -> Color(0xFFFFD700); 2 -> Color(0xFFC0C0C0); else -> Color(0xFFCD7F32) }
                            )
                            Text(text = "第${schoolClass.gradeRanking}名", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        text = if (headTeacherName != null) "学业导师: $headTeacherName" else "未指定学业导师",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (headTeacherName != null) MaterialTheme.colorScheme.onSurfaceVariant else AccentOrange
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "${schoolClass.studentCount}/${schoolClass.maxCapacity}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(text = "人数", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            AttributeBarsCompact(schoolClass)
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MiniStat("班风", "${schoolClass.classSpirit.toInt()}", AccentGreen)
                MiniStat("纪律", "${schoolClass.disciplineScore.toInt()}", MaterialTheme.colorScheme.primary)
                MiniStat("凝聚力", "${schoolClass.cohesion.toInt()}", AccentOrange)
                MiniStat("综合", String.format("%.1f", schoolClass.overallScore), Color(0xFF9C27B0))
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AttributeBarsCompact(schoolClass: SchoolClass) {
    val dimensions = listOf(
        "智力" to schoolClass.avgIntelligence, "体力" to schoolClass.avgPhysical,
        "社交" to schoolClass.avgSocial, "创造力" to schoolClass.avgCreativity, "品德" to schoolClass.avgMorality
    )
    val colors = listOf(Color(0xFF2196F3), Color(0xFF4CAF50), Color(0xFFFF9800), Color(0xFF9C27B0), Color(0xFFE91E63))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        dimensions.forEachIndexed { index, (name, value) ->
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "${value.toInt()}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = colors[index])
                LinearProgressIndicator(
                    progress = { value / 100f },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = colors[index],
                    trackColor = colors[index].copy(alpha = 0.2f)
                )
                Text(text = name, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ========== 班级详情 Sheet ==========

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClassDetailSheet(
    schoolClass: SchoolClass,
    students: List<Student>,
    headTeacherName: String?,
    onAssignHeadTeacher: () -> Unit,
    onStudentClick: (Student) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = schoolClass.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(12.dp))
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(text = "${schoolClass.studentCount}/${schoolClass.maxCapacity}人", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (headTeacherName != null) "学业导师: $headTeacherName" else "未指定学业导师",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onAssignHeadTeacher) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (headTeacherName != null) "更换" else "任命")
                    }
                }
            }
        }

        item {
            Text(text = "班级五维均值", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            RadarChart(attributes = schoolClass.averageAttributes, modifier = Modifier.fillMaxWidth().aspectRatio(1.3f))
        }

        item {
            Text(text = "班级指标", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatColumn("班风", schoolClass.classSpirit, AccentGreen)
                StatColumn("纪律", schoolClass.disciplineScore, MaterialTheme.colorScheme.primary)
                StatColumn("凝聚力", schoolClass.cohesion, AccentOrange)
                StatColumn("满意度", schoolClass.avgSatisfaction, Color(0xFF9C27B0))
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "班级学生", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(text = "${students.size}人", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (students.isEmpty()) {
            item { Text(text = "暂无学生", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(students, key = { it.id }) { student ->
                ClassStudentRow(student = student, onClick = { onStudentClick(student) })
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun StatColumn(label: String, value: Float, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "${value.toInt()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Spacer(modifier = Modifier.height(2.dp))
        LinearProgressIndicator(progress = { value / 100f }, modifier = Modifier.width(60.dp).height(4.dp), color = color, trackColor = color.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ClassStudentRow(student: Student, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val gradeColor = Color(student.attributeGrade.color)
            Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(gradeColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Text(text = student.attributeGrade.displayName, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = gradeColor)
            }
            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = student.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    if (student.healthStatus != HealthStatus.HEALTHY) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(4.dp), color = AccentRed.copy(alpha = 0.15f)) {
                            Text(text = student.healthStatus.displayName, style = MaterialTheme.typography.labelSmall, color = AccentRed, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                }
                Text(
                    text = "智${student.attributes.intelligence.toInt()} 体${student.attributes.physical.toInt()} 社${student.attributes.social.toInt()} 创${student.attributes.creativity.toInt()} 德${student.attributes.morality.toInt()}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val satColor = when { student.satisfaction >= 70f -> AccentGreen; student.satisfaction >= 40f -> AccentOrange; else -> AccentRed }
            Text(text = "${student.satisfaction.toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = satColor)
        }
    }
}

// ========== 学生详情（含雷达图）==========

@Composable
private fun StudentDetailWithRadar(student: Student) {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = student.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(12.dp))
            val gradeColor = Color(student.attributeGrade.color)
            Surface(shape = RoundedCornerShape(8.dp), color = gradeColor.copy(alpha = 0.15f)) {
                Text(text = "${student.attributeGrade.displayName}级", style = MaterialTheme.typography.labelMedium, color = gradeColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${com.arktools.xiaozhang.domain.model.UniversityAcademicCatalog.pathLabel(student.gradeLevel, student.courseId)} | 家庭: ${student.backgroundTier.displayName}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "五维属性", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        RadarChart(attributes = student.attributes, modifier = Modifier.fillMaxWidth().aspectRatio(1.3f))

        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "健康与生活", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        HealthRow("健康状态", student.healthStatus.displayName, if (student.healthStatus == HealthStatus.HEALTHY) AccentGreen else AccentRed)
        HealthRow("饮食质量", "${student.mealQuality.toInt()}/100", if (student.mealQuality >= 60f) AccentGreen else AccentOrange)
        HealthRow("住宿满意度", "${student.dormSatisfaction.toInt()}/100", if (student.dormSatisfaction >= 60f) AccentGreen else AccentOrange)
        HealthRow("运动量", "${student.exerciseLevel.toInt()}/100", if (student.exerciseLevel >= 40f) AccentGreen else AccentRed)

        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "学业信息", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        HealthRow("学期掌握度", "${student.semesterMastery.toInt()}%", MaterialTheme.colorScheme.primary)
        HealthRow("满意度", "${student.satisfaction.toInt()}/100", if (student.satisfaction >= 60f) AccentGreen else AccentRed)
        if (student.academicScore > 0f) { HealthRow("学业成绩", "${student.academicScore.toInt()}分", MaterialTheme.colorScheme.primary) }
        HealthRow("入学时间", "${student.enrollYear}年${student.enrollMonth}月", MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun HealthRow(label: String, value: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = color)
    }
}

// ========== 五维雷达图 ==========

@Composable
fun RadarChart(attributes: StudentAttributes, modifier: Modifier = Modifier) {
    val dimensions = listOf(
        "智力" to attributes.intelligence, "体力" to attributes.physical,
        "社交" to attributes.social, "创造力" to attributes.creativity, "品德" to attributes.morality
    )
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier = modifier.padding(24.dp)) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val radius = minOf(centerX, centerY) * 0.85f
        val angleStep = 2 * PI / 5
        val startAngle = -PI / 2

        for (level in 1..5) {
            val levelRadius = radius * level / 5
            val gridPath = Path()
            for (i in 0 until 5) {
                val angle = startAngle + i * angleStep
                val x = centerX + (levelRadius * cos(angle)).toFloat()
                val y = centerY + (levelRadius * sin(angle)).toFloat()
                if (i == 0) gridPath.moveTo(x, y) else gridPath.lineTo(x, y)
            }
            gridPath.close()
            drawPath(path = gridPath, color = surfaceVariant.copy(alpha = 0.15f), style = Stroke(width = 1f))
        }

        for (i in 0 until 5) {
            val angle = startAngle + i * angleStep
            val endX = centerX + (radius * cos(angle)).toFloat()
            val endY = centerY + (radius * sin(angle)).toFloat()
            drawLine(color = surfaceVariant.copy(alpha = 0.2f), start = Offset(centerX, centerY), end = Offset(endX, endY), strokeWidth = 1f)
        }

        val dataPath = Path()
        for (i in 0 until 5) {
            val angle = startAngle + i * angleStep
            val value = dimensions[i].second / 100f
            val x = centerX + (radius * value * cos(angle)).toFloat()
            val y = centerY + (radius * value * sin(angle)).toFloat()
            if (i == 0) dataPath.moveTo(x, y) else dataPath.lineTo(x, y)
        }
        dataPath.close()

        drawPath(path = dataPath, color = primaryColor.copy(alpha = 0.2f))
        drawPath(path = dataPath, color = primaryColor, style = Stroke(width = 2f))

        for (i in 0 until 5) {
            val angle = startAngle + i * angleStep
            val value = dimensions[i].second / 100f
            val x = centerX + (radius * value * cos(angle)).toFloat()
            val y = centerY + (radius * value * sin(angle)).toFloat()
            drawCircle(color = primaryColor, radius = 4f, center = Offset(x, y))
        }

        drawContext.canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply {
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = 28f
                color = android.graphics.Color.GRAY
                isAntiAlias = true
            }
            for (i in 0 until 5) {
                val angle = startAngle + i * angleStep
                val labelRadius = radius + 32f
                val x = centerX + (labelRadius * cos(angle)).toFloat()
                val y = centerY + (labelRadius * sin(angle)).toFloat() + 10f
                drawText("${dimensions[i].first} ${dimensions[i].second.toInt()}", x, y, paint)
            }
        }
    }
}
