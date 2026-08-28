package com.arktools.xiaozhang.ui.teaching

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiaozhang.domain.engine.GameBalanceConfig
import com.arktools.xiaozhang.domain.model.*
import com.arktools.xiaozhang.domain.repository.SchoolRepository
import com.arktools.xiaozhang.domain.teaching.TeachingManager
import com.arktools.xiaozhang.domain.teaching.TeachingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TeachingViewModel @Inject constructor(
    private val teachingManager: TeachingManager,
    private val schoolRepository: SchoolRepository,
    private val studentRepository: com.arktools.xiaozhang.domain.repository.StudentRepository,
    private val policyManager: com.arktools.xiaozhang.domain.policy.SchoolPolicyManager
) : ViewModel() {

    val state: StateFlow<TeachingState> = teachingManager.state

    val config: TeachingConfig get() = teachingManager.config

    /** 各学院/专业的真实培养质量：来自在读学生的满意度与学业分 */
    data class CollegeTrainingQuality(
        val collegeName: String,
        val studentCount: Int,
        val avgSatisfaction: Float,
        val avgAcademicScore: Float,
        val majorSummary: String
    )

    val collegeQuality: StateFlow<List<CollegeTrainingQuality>> =
        kotlinx.coroutines.flow.combine(
            studentRepository.observeActiveStudentCount(),
            policyManager.policies
        ) { _, _ -> true }
            .map {
                val students = studentRepository.getActiveStudents()
                students.groupBy { student ->
                    com.arktools.xiaozhang.domain.model.UniversityAcademicCatalog.collegeName(student.courseId)
                }.map { (college, list) ->
                    CollegeTrainingQuality(
                        collegeName = college,
                        studentCount = list.size,
                        avgSatisfaction = if (list.isEmpty()) 0f
                        else list.map { it.satisfaction }.average().toFloat(),
                        avgAcademicScore = if (list.isEmpty()) 0f
                        else list.map { it.academicScore }.average().toFloat(),
                        majorSummary = list.groupingBy { student ->
                            com.arktools.xiaozhang.domain.model.UniversityAcademicCatalog.displayName(student.courseId)
                        }.eachCount().entries.sortedByDescending { it.value }
                            .take(2).joinToString("、") { "${it.key}${it.value}人" }
                    )
                }.sortedByDescending { it.studentCount }
            }
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    /** 教室数量 Flow（用于 UI 提示）—— 返回实际间数 */
    val classroomCountFlow = schoolRepository.getSchoolFlow().map { school ->
        school?.facilities?.count { it.type == FacilityType.CLASSROOM && it.isOperational } ?: 0
    }

    /**
     * 教室有效容量 Flow（最大总班级数，考虑等级加成）
     * 与 GameEngine 招生约束一致：每间教室 Lv1=3班, Lv2=4班, Lv3=6班, Lv4=7班, Lv5=9班
     * 公式：sumOf { ((level+1)/2.0) * 3.0 }.toInt()
     */
    val classroomCapacityFlow = schoolRepository.getSchoolFlow().map { school ->
        school?.facilities
            ?.filter { it.type == FacilityType.CLASSROOM && it.isOperational }
            ?.sumOf { ((it.level + 1) / 2.0).coerceAtLeast(1.0) * 3.0 }?.toInt() ?: 0
    }

    /** 每种班型最大班级数（根据学校等级动态调整） */
    val maxClassesPerTierFlow = schoolRepository.getSchoolFlow().map { school ->
        GameBalanceConfig.getMaxClassesPerTierForLevel(school?.campusLevel ?: 1)
    }

    // ========= 班型管理 =========

    fun setClassCount(tier: ClassTier, count: Int) {
        teachingManager.setClassCount(tier, count)
        persistConfig()
    }

    fun getClassCount(tier: ClassTier): Int {
        return config.classDistribution[tier] ?: 0
    }

    // ========= 教学强度 =========

    fun setIntensity(intensity: TeachingIntensity) {
        teachingManager.setIntensity(intensity)
        persistConfig()
    }

    // ========= 文理分科 =========

    fun setSubjectTrack(track: SubjectTrack) {
        teachingManager.setSubjectTrack(track)
        persistConfig()
    }

    // ========= 作息政策 =========

    fun toggleSchedulePolicy(policy: SchedulePolicy) {
        teachingManager.toggleSchedulePolicy(policy)
        persistConfig()
    }

    fun isPolicyEnabled(policy: SchedulePolicy): Boolean {
        return policy in config.schedulePolicies
    }

    // ========= 特殊项目 =========

    fun addSpecialProgram(program: SpecialProgram): Boolean {
        val added = teachingManager.addSpecialProgram(program)
        if (added) persistConfig()
        return added
    }

    fun removeSpecialProgram(program: SpecialProgram) {
        teachingManager.removeSpecialProgram(program)
        persistConfig()
    }

    fun isProgramActive(program: SpecialProgram): Boolean {
        return program in config.specialPrograms
    }

    // ========= 其他配置 =========

    fun setScienceRatio(ratio: Float) {
        teachingManager.setScienceRatio(ratio)
        persistConfig()
    }

    fun setWeeklyPEHours(hours: Int) {
        teachingManager.setWeeklyPEHours(hours)
        persistConfig()
    }

    fun setMonthlyExamFrequency(freq: Int) {
        teachingManager.setMonthlyExamFrequency(freq)
        persistConfig()
    }

    // ========= 计算属性 =========

    fun totalSetupCost(): Double = teachingManager.totalSetupCost()
    fun monthlyOperatingCost(): Double = teachingManager.monthlyOperatingCost()
    fun isConfigured(): Boolean = teachingManager.isConfigured()

    // ========= 持久化 =========

    /**
     * 将当前教学配置立即持久化到数据库
     * 修复：之前只在月度结算时保存，玩家配置后退出会丢失
     */
    private fun persistConfig() {
        viewModelScope.launch {
            schoolRepository.mutateSchool { school ->
                school.teachingConfigJson = teachingManager.toJson()
                true
            }
        }
    }
}
