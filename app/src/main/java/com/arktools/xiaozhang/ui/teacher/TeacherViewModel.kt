package com.arktools.xiaozhang.ui.teacher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiaozhang.audio.AudioManager
import com.arktools.xiaozhang.domain.engine.GameBalanceConfig
import com.arktools.xiaozhang.domain.engine.GameEngine
import com.arktools.xiaozhang.domain.model.Teacher
import com.arktools.xiaozhang.domain.model.TeacherLevel
import com.arktools.xiaozhang.domain.repository.PaidTrainingStatus
import com.arktools.xiaozhang.domain.repository.SchoolRepository
import com.arktools.xiaozhang.domain.repository.TeacherRepository
import com.arktools.xiaozhang.domain.teacherdev.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.arktools.xiaozhang.util.safeLaunch

enum class TeacherSortMode(
    val displayName: String,
    val description: String
) {
    DEFAULT("默认", "按入职顺序"),
    SKILL_DESC("评分", "综合评分从高到低"),
    SALARY_DESC("薪资", "薪资从高到低"),
    FATIGUE_ASC("疲劳", "疲劳从低到高"),
    LOYALTY_DESC("忠诚", "忠诚度从高到低"),
    BY_SUBJECT("科目", "按教学科目分组")
}

enum class RecruitmentChannel(
    val displayName: String,
    val cost: Double,
    val description: String
) {
    AD("广告招聘", 2.0, "C-B级为主，小概率A"),
    SCHOOL("学校合作", 5.0, "B-A级为主"),
    HEADHUNTER("猎头S池", 15.0, "A-S级为主")
}

@HiltViewModel
class TeacherViewModel @Inject constructor(
    private val teacherRepository: TeacherRepository,
    private val schoolRepository: SchoolRepository,
    private val audioManager: AudioManager,
    private val teacherDevManager: TeacherDevelopmentManager,
    private val gameEngine: GameEngine
) : ViewModel() {

    private val _teachers = MutableStateFlow<List<Teacher>>(emptyList())
    val teachers: StateFlow<List<Teacher>> = _teachers.asStateFlow()

    private val _sortMode = MutableStateFlow(TeacherSortMode.DEFAULT)
    val sortMode: StateFlow<TeacherSortMode> = _sortMode.asStateFlow()

    /** 用于展示的教师列表（已排序，不包含分组标题） */
    val displayTeachers: StateFlow<List<Teacher>> = combine(_teachers, _sortMode) { list, mode ->
        sortTeachers(list, mode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 按科目分组的教师列表 */
    val teachersBySubject: StateFlow<Map<String, List<Teacher>>> = _teachers.map { list ->
        list.groupBy { it.role.displayName }
            .toSortedMap()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _candidates = MutableStateFlow<List<Teacher>>(emptyList())
    val candidates: StateFlow<List<Teacher>> = _candidates.asStateFlow()

    private val _showHireDialog = MutableStateFlow(false)
    val showHireDialog: StateFlow<Boolean> = _showHireDialog.asStateFlow()

    private val _selectedTeacher = MutableStateFlow<Teacher?>(null)
    val selectedTeacher: StateFlow<Teacher?> = _selectedTeacher.asStateFlow()

    private val _selectedChannel = MutableStateFlow<RecruitmentChannel?>(null)
    val selectedChannel: StateFlow<RecruitmentChannel?> = _selectedChannel.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _schoolLevel = MutableStateFlow(1)
    val schoolLevel: StateFlow<Int> = _schoolLevel.asStateFlow()

    /** 当前游戏绝对天数，用于计算教师入职时长 */
    private val _currentGameDay = MutableStateFlow(0L)
    val currentGameDay: StateFlow<Long> = _currentGameDay.asStateFlow()

    // ========== 教师发展相关 ==========
    val devState: StateFlow<TeacherDevState> = teacherDevManager.state

    fun clearError() {
        _errorMessage.value = null
    }

    init {
        loadTeachers()
        // 持续收集学校数据，更新当前游戏天数（用于教师入职时长计算）
        viewModelScope.safeLaunch {
            schoolRepository.getSchoolFlow().collect { school ->
                if (school != null) {
                    _currentGameDay.value = school.currentYear.toLong() * 360 + (school.currentMonth - 1) * 30 + school.currentDay
                    _schoolLevel.value = school.campusLevel
                }
            }
        }
    }

    private fun loadTeachers() {
        viewModelScope.safeLaunch {
            teacherRepository.getTeachersFlow().collect {
                _teachers.value = it
            }
        }
    }

    fun onHireClick() {
        audioManager.playButtonClick()
        _showHireDialog.value = true
        _candidates.value = emptyList()
        _selectedChannel.value = null
        viewModelScope.safeLaunch {
            val school = schoolRepository.getSchool()
            _schoolLevel.value = school?.campusLevel ?: 1
        }
    }

    fun onHireDialogDismiss() {
        _showHireDialog.value = false
        _candidates.value = emptyList()
        _selectedChannel.value = null
    }

    fun selectChannel(channel: RecruitmentChannel) {
        viewModelScope.safeLaunch {
            _errorMessage.value = null
            // 原子操作：检查余额 + 扣款，避免 TOCTOU 竞态
            val result = schoolRepository.mutateSchool { school ->
                if (school.cash < channel.cost) {
                    _errorMessage.value = "资金不足! 需要${String.format("%.1f", channel.cost)}万"
                    return@mutateSchool false
                }
                school.cash -= channel.cost
                true
            }
            if (result != null) {
                _selectedChannel.value = channel
                generateCandidates(channel)
            }
        }
    }

    private fun generateCandidates(channel: RecruitmentChannel) {
        // 人脉影响候选人质量：人脉越高，高级教师出现概率越大
        val connectionLevel = gameEngine.principal.connectionLevel
        val connectionBonus = connectionLevel / 100.0 // 0.0 ~ 1.0

        val candidates = when (channel) {
            RecruitmentChannel.AD -> {
                // 基础: C*3, B*2, A*1; 人脉高时加入更多B和A
                val levels = mutableListOf(TeacherLevel.C, TeacherLevel.C, TeacherLevel.C, TeacherLevel.B, TeacherLevel.B, TeacherLevel.A)
                if (connectionBonus > 0.3) levels.add(TeacherLevel.B) // 人脉>30: 多一个B
                if (connectionBonus > 0.6) levels.add(TeacherLevel.A) // 人脉>60: 多一个A
                val selectedLevels = (1..3).map { levels.random() }
                selectedLevels.map { teacherRepository.generateCandidates(it, 1).first() }
            }
            RecruitmentChannel.SCHOOL -> {
                val levels = mutableListOf(TeacherLevel.B, TeacherLevel.B, TeacherLevel.A, TeacherLevel.A, TeacherLevel.A, TeacherLevel.S)
                if (connectionBonus > 0.4) levels.add(TeacherLevel.S) // 人脉>40: 多一个S
                if (connectionBonus > 0.7) levels.add(TeacherLevel.S) // 人脉>70: 再多一个S
                val selectedLevels = (1..3).map { levels.random() }
                selectedLevels.map { teacherRepository.generateCandidates(it, 1).first() }
            }
            RecruitmentChannel.HEADHUNTER -> {
                val levels = mutableListOf(TeacherLevel.A, TeacherLevel.A, TeacherLevel.A, TeacherLevel.S, TeacherLevel.S, TeacherLevel.S)
                if (connectionBonus > 0.5) levels.add(TeacherLevel.S) // 人脉>50: 更多S
                if (connectionBonus > 0.8) { levels.add(TeacherLevel.S); levels.add(TeacherLevel.S) } // 人脉>80: S占绝对多数
                val selectedLevels = (1..3).map { levels.random() }
                selectedLevels.map { teacherRepository.generateCandidates(it, 1).first() }
            }
        }
        _candidates.value = candidates
    }

    fun hireTeacher(teacher: Teacher) {
        viewModelScope.safeLaunch {
            val hiringFee = GameBalanceConfig.getHiringFee(teacher.level)
            // 原子操作：检查余额 + 扣款 + 获取游戏日期
            var gameDay = 0L
            val result = schoolRepository.mutateSchool { school ->
                if (school.cash < hiringFee) {
                    _errorMessage.value = "资金不足！招聘${teacher.level.name}级教师需要猎头费 ${hiringFee} 万元"
                    return@mutateSchool false
                }
                school.cash -= hiringFee
                gameDay = school.currentYear.toLong() * 360 + (school.currentMonth - 1) * 30 + school.currentDay
                true
            }
            if (result != null) {
                // 设置入职日期为当前游戏绝对天数
                teacher.hireDate = gameDay
                teacherRepository.hireTeacher(teacher)
                // 刷新课表，让新教师出现在对应科目的课程表中
                gameEngine.refreshTimetablesForTeacherChange()
                // 通知派系系统：招聘了教师（高薪=A/S级）
                if (teacher.level in listOf(TeacherLevel.A, TeacherLevel.S)) {
                    gameEngine.notifyFactionDecision(com.arktools.xiaozhang.domain.engine.SchoolDecision.HIRE_EXPENSIVE_TEACHER)
                }
                _showHireDialog.value = false
                _candidates.value = emptyList()
                _selectedChannel.value = null
                audioManager.playTeacherHire()
            }
        }
    }

    fun fireTeacher(teacherId: String) {
        viewModelScope.safeLaunch {
            teacherRepository.fireTeacher(teacherId)
            // 刷新课表，解雇后该科目显示"待聘"
            gameEngine.refreshTimetablesForTeacherChange()
            // 通知派系系统：解雇教师
            gameEngine.notifyFactionDecision(com.arktools.xiaozhang.domain.engine.SchoolDecision.FIRE_TEACHER)
            _selectedTeacher.value = null
        }
    }

    fun trainTeacher(teacherId: String) {
        viewModelScope.safeLaunch {
            val result = teacherRepository.performPaidTraining(teacherId)
            when (result.status) {
                PaidTrainingStatus.SUCCESS -> {
                    _errorMessage.value = null
                    audioManager.playLevelUp()
                }
                PaidTrainingStatus.NO_EFFECT -> {
                    _errorMessage.value =
                        "培训未达预期效果（成功率${(result.successRate * 100).toInt()}%），费用已消耗"
                }
                PaidTrainingStatus.INSUFFICIENT_FUNDS -> {
                    _errorMessage.value =
                        "资金不足！培训需要 ${result.cost} 万元"
                }
                PaidTrainingStatus.TEACHER_UNAVAILABLE -> {
                    _errorMessage.value = "教师状态已变化，无法培训"
                }
                PaidTrainingStatus.SCHOOL_UNAVAILABLE -> {
                    _errorMessage.value = "学校存档不可用，请重试"
                }
            }
        }
    }

    /**
     * 一键培训：批量培训所有教师，返回结果摘要
     */
    data class BatchTrainResult(
        val totalCount: Int,
        val successCount: Int,
        val failCount: Int,
        val totalCost: Double,
        val insufficientFunds: Boolean = false
    )

    private val _batchTrainResult = MutableStateFlow<BatchTrainResult?>(null)
    val batchTrainResult: StateFlow<BatchTrainResult?> = _batchTrainResult.asStateFlow()

    fun clearBatchTrainResult() {
        _batchTrainResult.value = null
    }

    fun batchTrainAll() {
        viewModelScope.safeLaunch {
            val allTeachers = _teachers.value
            if (allTeachers.isEmpty()) {
                _errorMessage.value = "没有教师可培训"
                return@safeLaunch
            }

            var successCount = 0
            var failCount = 0
            var totalCost = 0.0

            for (teacher in allTeachers) {
                val result = teacherRepository.performPaidTraining(teacher.id)
                when (result.status) {
                    PaidTrainingStatus.SUCCESS -> {
                        totalCost += result.cost
                        successCount++
                    }
                    PaidTrainingStatus.NO_EFFECT -> {
                        totalCost += result.cost
                        failCount++
                    }
                    PaidTrainingStatus.INSUFFICIENT_FUNDS -> {
                        _batchTrainResult.value = BatchTrainResult(
                            totalCount = successCount + failCount,
                            successCount = successCount,
                            failCount = failCount,
                            totalCost = totalCost,
                            insufficientFunds = true
                        )
                        return@safeLaunch
                    }
                    PaidTrainingStatus.TEACHER_UNAVAILABLE -> {
                        continue
                    }
                    PaidTrainingStatus.SCHOOL_UNAVAILABLE -> {
                        _errorMessage.value = "学校存档不可用，请重试"
                        return@safeLaunch
                    }
                }
            }

            audioManager.playLevelUp()
            _batchTrainResult.value = BatchTrainResult(
                totalCount = allTeachers.size,
                successCount = successCount,
                failCount = failCount,
                totalCost = totalCost
            )
        }
    }

    fun adjustSalary(teacherId: String, newSalary: Double) {
        viewModelScope.safeLaunch {
            teacherRepository.adjustSalary(teacherId, newSalary)
        }
    }

    fun selectTeacher(teacher: Teacher) {
        _selectedTeacher.value = teacher
    }

    fun clearSelectedTeacher() {
        _selectedTeacher.value = null
    }

    fun setSortMode(mode: TeacherSortMode) {
        _sortMode.value = mode
    }

    /**
     * 根据排序模式对教师列表排序（按科目分组模式返回原序，由UI单独处理分组）
     */
    private fun sortTeachers(list: List<Teacher>, mode: TeacherSortMode): List<Teacher> {
        return when (mode) {
            TeacherSortMode.DEFAULT -> list
            TeacherSortMode.SKILL_DESC -> list.sortedByDescending { it.averageSkill }
            TeacherSortMode.SALARY_DESC -> list.sortedByDescending { it.salary }
            TeacherSortMode.FATIGUE_ASC -> list.sortedBy { it.fatigue }
            TeacherSortMode.LOYALTY_DESC -> list.sortedByDescending { it.loyalty }
            TeacherSortMode.BY_SUBJECT -> list.sortedBy { it.role.displayName }
        }
    }

    // ========== 教师发展方法 ==========

    fun startTraining(
        teacherId: String,
        program: TrainingProgram,
        onResult: (com.arktools.xiaozhang.domain.engine.ManagedOperationResult) -> Unit
    ) {
        viewModelScope.safeLaunch {
            val result = gameEngine.startTeacherDevelopmentTraining(
                teacherId,
                program
            )
            if (result.success) {
                gameEngine.notifyFactionDecision(
                    com.arktools.xiaozhang.domain.engine.SchoolDecision.TEACHER_TRAINING
                )
            }
            onResult(result)
        }
    }

    fun tryPromote(
        teacherId: String,
        onResult: (PromotionResult) -> Unit
    ) {
        viewModelScope.safeLaunch {
            onResult(gameEngine.promoteTeacherDevelopment(teacherId))
        }
    }

    fun conductEvaluation(
        teacherId: String,
        onResult: (com.arktools.xiaozhang.domain.teacherdev.EvaluationResult?) -> Unit
    ) {
        viewModelScope.safeLaunch {
            onResult(gameEngine.evaluateTeacherDevelopment(teacherId))
        }
    }

    fun getAvailablePrograms(): List<TrainingProgram> {
        return teacherDevManager.getAvailablePrograms()
    }

    fun getPromotionRequirements(
        teacherId: String
    ): PromotionRequirements? {
        return teacherDevManager.getPromotionRequirements(teacherId)
    }
}
