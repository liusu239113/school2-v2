package com.arktools.xiaozhang.ui.student

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiaozhang.domain.engine.GameEngine
import com.arktools.xiaozhang.domain.model.GradeLevel
import com.arktools.xiaozhang.domain.model.SchoolClass
import com.arktools.xiaozhang.domain.model.Student
import com.arktools.xiaozhang.domain.model.StudentStatus
import com.arktools.xiaozhang.domain.model.Teacher
import com.arktools.xiaozhang.domain.exam.ExamManager
import com.arktools.xiaozhang.domain.exam.StudentScore
import com.arktools.xiaozhang.domain.repository.CourseRepository
import com.arktools.xiaozhang.domain.repository.StudentRepository
import com.arktools.xiaozhang.domain.repository.TeacherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.arktools.xiaozhang.util.safeLaunch

// ===== 学生管理 UI 状态 =====
data class StudentUiState(
    val activeStudents: List<Student> = emptyList(),
    val recentGraduates: List<Student> = emptyList(),
    val recentDropouts: List<Student> = emptyList(),
    val totalActiveCount: Int = 0,
    val totalGraduateCount: Int = 0,
    val averageSatisfaction: Float = 0f,
    val averageGraduateRating: Float = 0f,
    val selectedStudent: Student? = null,
    val filterStatus: StudentFilterStatus = StudentFilterStatus.ACTIVE,
    val searchQuery: String = "",
    val courseNames: Map<String, String> = emptyMap(),
    val groupByCourse: Boolean = false
)

enum class StudentFilterStatus(val displayName: String) {
    ACTIVE("在读"),
    GRADUATED("已毕业"),
    DROPPED("已退学")
}

// ===== 班级管理 UI 状态 =====
data class ClassUiState(
    val classesByGrade: Map<GradeLevel, List<SchoolClass>> = emptyMap(),
    val selectedClass: SchoolClass? = null,
    val classStudents: List<Student> = emptyList(),
    val selectedStudent: Student? = null,
    val availableTeachers: List<Teacher> = emptyList(),
    val teacherNames: Map<String, String> = emptyMap(),
    val selectedGrade: GradeLevel = GradeLevel.GRADE_1,
    val showHeadTeacherDialog: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class StudentViewModel @Inject constructor(
    private val studentRepository: StudentRepository,
    private val courseRepository: CourseRepository,
    private val examManager: ExamManager,
    private val gameEngine: GameEngine,
    private val teacherRepository: TeacherRepository
) : ViewModel() {

    // ===== 学生管理状态 =====
    private val _uiState = MutableStateFlow(StudentUiState())
    val uiState: StateFlow<StudentUiState> = _uiState.asStateFlow()

    // ===== 班级管理状态 =====
    private val _classUiState = MutableStateFlow(ClassUiState())
    val classUiState: StateFlow<ClassUiState> = _classUiState.asStateFlow()

    init {
        loadStudentData()
        loadCourseNames()
        observeStudentCount()
        observeClasses()
        loadTeacherNames()
    }

    // ==================== 学生管理功能 ====================

    private fun loadStudentData() {
        viewModelScope.safeLaunch {
            studentRepository.observeGraduatedStudents().collect { graduates ->
                _uiState.value = _uiState.value.copy(recentGraduates = graduates)
            }
        }
        viewModelScope.safeLaunch {
            studentRepository.observeDroppedStudents().collect { dropouts ->
                _uiState.value = _uiState.value.copy(recentDropouts = dropouts)
            }
        }
        viewModelScope.safeLaunch {
            val active = studentRepository.getActiveStudents()
            val activeCount = studentRepository.getActiveStudentCount()
            val graduateCount = studentRepository.getGraduateCount()
            val avgSatisfaction = studentRepository.getAverageSatisfaction()
            val avgRating = studentRepository.getAverageGraduateRating()

            _uiState.value = _uiState.value.copy(
                activeStudents = active,
                totalActiveCount = activeCount,
                totalGraduateCount = graduateCount,
                averageSatisfaction = avgSatisfaction,
                averageGraduateRating = avgRating
            )
        }
    }

    private fun loadCourseNames() {
        viewModelScope.safeLaunch {
            // 改为加载学生的年级/班级信息作为分组名
            val classes = gameEngine.classes
            val nameMap = mutableMapOf<String, String>()
            // 为每个学生的 courseId 提供显示名（兼容旧UI字段）
            // 同时为每个学生ID构建 "年级·班级" 的映射
            val students = _uiState.value.activeStudents + _uiState.value.recentGraduates
            students.forEach { student ->
                val classInfo = classes.find { it.id == student.classId }
                val gradeName = student.gradeLevel.displayName
                val className = classInfo?.displayName ?: "未分班"
                nameMap[student.courseId] = "$gradeName"  // courseId 分组时显示年级
                nameMap[student.id] = "$gradeName · $className"  // 学生维度显示详细
            }
            // 确保 "GENERAL" courseId 有默认名
            nameMap["GENERAL"] = "大学通识"
            _uiState.value = _uiState.value.copy(courseNames = nameMap)
        }
    }

    private fun observeStudentCount() {
        viewModelScope.safeLaunch {
            studentRepository.observeActiveStudentCount().collect { count ->
                _uiState.value = _uiState.value.copy(totalActiveCount = count)
                refreshData()
            }
        }
    }

    fun refreshData() {
        loadStudentData()
        loadCourseNames()
    }

    fun selectStudent(student: Student) {
        _uiState.value = _uiState.value.copy(selectedStudent = student)
    }

    fun clearSelectedStudent() {
        _uiState.value = _uiState.value.copy(selectedStudent = null)
    }

    fun setFilter(filter: StudentFilterStatus) {
        _uiState.value = _uiState.value.copy(filterStatus = filter)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun toggleGroupByCourse() {
        _uiState.value = _uiState.value.copy(groupByCourse = !_uiState.value.groupByCourse)
    }

    fun getFilteredStudents(): List<Student> {
        val state = _uiState.value
        val baseList = when (state.filterStatus) {
            StudentFilterStatus.ACTIVE -> state.activeStudents
            StudentFilterStatus.GRADUATED -> state.recentGraduates
            StudentFilterStatus.DROPPED -> state.recentDropouts
        }
        if (state.searchQuery.isBlank()) return baseList
        val query = state.searchQuery.lowercase()
        return baseList.filter { student ->
            student.name.lowercase().contains(query) ||
                student.traits.any { it.displayName.lowercase().contains(query) } ||
                student.gradeLevel.displayName.lowercase().contains(query)
        }
    }

    fun getGroupedStudents(): Map<String, List<Student>> {
        val students = getFilteredStudents()
        return students.groupBy { student ->
            student.gradeLevel.displayName
        }
    }

    fun getStudentLatestScores(studentId: String): List<StudentScore> {
        return examManager.getStudentLatestScores(studentId)
    }

    fun getStudentAllScores(studentId: String): List<StudentScore> {
        return examManager.getStudentScores(studentId)
    }

    // ==================== 班级管理功能 ====================

    private fun observeClasses() {
        viewModelScope.safeLaunch {
            gameEngine.classesFlow.collect { classes ->
                val grouped = classes.groupBy { it.gradeLevel }
                    .toSortedMap(compareBy { it.order })
                _classUiState.value = _classUiState.value.copy(classesByGrade = grouped)

                val selected = _classUiState.value.selectedClass
                if (selected != null) {
                    val updated = classes.find { it.id == selected.id }
                    if (updated != null) {
                        _classUiState.value = _classUiState.value.copy(selectedClass = updated)
                    }
                }
            }
        }
    }

    private fun loadTeacherNames() {
        viewModelScope.safeLaunch {
            teacherRepository.getTeachersFlow().collect { teachers ->
                val nameMap = teachers.associate { it.id to it.name }
                // 获取所有已担任班主任的教师ID（排除当前选中班级的班主任，方便重新分配）
                val selectedClassId = _classUiState.value.selectedClass?.id
                val assignedHeadTeacherIds = gameEngine.classes
                    .filter { it.headTeacherId != null && it.id != selectedClassId }
                    .mapNotNull { it.headTeacherId }
                    .toSet()
                _classUiState.value = _classUiState.value.copy(
                    teacherNames = nameMap,
                    availableTeachers = teachers.filter {
                        it.isWorking && !it.isOnVacation && it.id !in assignedHeadTeacherIds
                    }
                )
            }
        }
    }

    fun selectGrade(grade: GradeLevel) {
        _classUiState.value = _classUiState.value.copy(selectedGrade = grade)
    }

    fun selectClass(schoolClass: SchoolClass) {
        _classUiState.value = _classUiState.value.copy(selectedClass = schoolClass)
        loadClassStudents(schoolClass.id)
    }

    fun clearSelectedClass() {
        _classUiState.value = _classUiState.value.copy(
            selectedClass = null,
            classStudents = emptyList()
        )
    }

    fun selectClassStudent(student: Student) {
        _classUiState.value = _classUiState.value.copy(selectedStudent = student)
    }

    fun clearClassSelectedStudent() {
        _classUiState.value = _classUiState.value.copy(selectedStudent = null)
    }

    fun showHeadTeacherDialog() {
        // 打开对话框时重新计算可选老师（排除已在其他班担任班主任的）
        refreshAvailableTeachers()
        _classUiState.value = _classUiState.value.copy(showHeadTeacherDialog = true)
    }

    private fun refreshAvailableTeachers() {
        val selectedClassId = _classUiState.value.selectedClass?.id
        val assignedHeadTeacherIds = gameEngine.classes
            .filter { it.headTeacherId != null && it.id != selectedClassId }
            .mapNotNull { it.headTeacherId }
            .toSet()
        viewModelScope.safeLaunch {
            val teachers = teacherRepository.getTeachers()
            _classUiState.value = _classUiState.value.copy(
                availableTeachers = teachers.filter {
                    it.isWorking && !it.isOnVacation && it.id !in assignedHeadTeacherIds
                }
            )
        }
    }

    fun dismissHeadTeacherDialog() {
        _classUiState.value = _classUiState.value.copy(showHeadTeacherDialog = false)
    }

    fun assignHeadTeacher(teacherId: String) {
        val selectedClass = _classUiState.value.selectedClass ?: return
        val teacher = _classUiState.value.availableTeachers.find { it.id == teacherId } ?: return
        viewModelScope.safeLaunch {
            gameEngine.classManager.assignHeadTeacher(selectedClass, teacher, gameEngine.classes)
            gameEngine.notifyClassesChanged()
            gameEngine.saveHeadTeacherMap()
            // 班主任工作量折减后立即全校重排，避免科任冲突
            gameEngine.refreshTimetablesForTeacherChange()
            dismissHeadTeacherDialog()
        }
    }

    fun removeHeadTeacher() {
        val selectedClass = _classUiState.value.selectedClass ?: return
        viewModelScope.safeLaunch {
            selectedClass.headTeacherId = null
            gameEngine.notifyClassesChanged()
            gameEngine.saveHeadTeacherMap()
            gameEngine.refreshTimetablesForTeacherChange()
            dismissHeadTeacherDialog()
        }
    }

    fun autoAssignAllHeadTeachers() {
        viewModelScope.safeLaunch {
            val allClasses = gameEngine.classes
            val classesNeedingHead = allClasses.filter { it.headTeacherId == null && it.studentCount > 0 }
            if (classesNeedingHead.isEmpty()) {
                _classUiState.value = _classUiState.value.copy(message = "所有班级已有班主任，无需分配")
                return@safeLaunch
            }

            val teachers = teacherRepository.getTeachers()
            val assignments = gameEngine.classManager.autoAssignHeadTeachers(allClasses, teachers)

            if (assignments.isEmpty()) {
                _classUiState.value = _classUiState.value.copy(message = "没有可用的教师来担任班主任")
                return@safeLaunch
            }

            gameEngine.notifyClassesChanged()
            gameEngine.saveHeadTeacherMap()
            gameEngine.refreshTimetablesForTeacherChange()
            _classUiState.value = _classUiState.value.copy(
                message = "一键分配完成！已为 ${assignments.size} 个班级分配班主任"
            )
        }
    }

    fun clearClassMessage() {
        _classUiState.value = _classUiState.value.copy(message = null)
    }

    private fun loadClassStudents(classId: String) {
        viewModelScope.safeLaunch {
            val students = studentRepository.getStudentsByClass(classId)
            _classUiState.value = _classUiState.value.copy(classStudents = students)
        }
    }

    fun getGradeRanking(grade: GradeLevel): List<SchoolClass> {
        return _classUiState.value.classesByGrade[grade]
            ?.sortedByDescending { it.overallScore }
            ?: emptyList()
    }

    fun getTotalClassCount(): Int {
        return _classUiState.value.classesByGrade.values.sumOf { it.size }
    }

    fun getTotalStudentCountFromClasses(): Int {
        return _classUiState.value.classesByGrade.values.flatten().sumOf { it.studentCount }
    }
}
