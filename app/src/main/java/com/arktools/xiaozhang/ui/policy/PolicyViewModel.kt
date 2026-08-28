package com.arktools.xiaozhang.ui.policy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiaozhang.audio.AudioManager
import com.arktools.xiaozhang.domain.engine.GameEngine
import com.arktools.xiaozhang.domain.engine.SchoolDecision
import com.arktools.xiaozhang.domain.policy.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import com.arktools.xiaozhang.util.safeLaunch

@HiltViewModel
class PolicyViewModel @Inject constructor(
    private val policyManager: SchoolPolicyManager,
    private val gameEngine: GameEngine,
    private val audioManager: AudioManager
) : ViewModel() {

    val policies: StateFlow<SchoolPolicies> = policyManager.policies

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    fun setTuitionLevel(level: TuitionLevel) {
        val oldLevel = policyManager.policies.value.tuitionLevel
        policyManager.setTuitionLevel(level)
        // 学费上调时通知派系
        if (level.ordinal > oldLevel.ordinal) {
            gameEngine.notifyFactionDecision(SchoolDecision.RAISE_TUITION)
        }
    }

    fun setExamDifficulty(difficulty: ExamDifficulty) {
        policyManager.setExamDifficulty(difficulty)
        // 严格考试标准 → 通知保守派
        if (difficulty == ExamDifficulty.CHALLENGING || difficulty == ExamDifficulty.RIGOROUS) {
            gameEngine.notifyFactionDecision(SchoolDecision.STRICT_DISCIPLINE)
        }
    }
    fun setTeacherPayPolicy(policy: TeacherPayPolicy) = policyManager.setTeacherPayPolicy(policy)
    fun setExtracurricularPolicy(policy: ExtracurricularPolicy) {
        policyManager.setExtracurricularPolicy(policy)
        // 增加课外活动 → 通知改革派
        if (policy == ExtracurricularPolicy.RICH || policy == ExtracurricularPolicy.WORLD_CLASS) {
            gameEngine.notifyFactionDecision(SchoolDecision.CLUB_ACTIVITY)
        }
    }
    fun setAdmissionPolicy(policy: AdmissionPolicy) = policyManager.setAdmissionPolicy(policy)
    fun setEnrollmentPlan(plan: EnrollmentPlan) = policyManager.setEnrollmentPlan(plan)
    fun setUniversityStrategy(strategy: UniversityStrategy) = policyManager.setUniversityStrategy(strategy)
    fun adjustBudget(line: BudgetLine, delta: Int) {
        val next = policyManager.policies.value.budgetAllocation.adjust(line, delta)
        policyManager.setBudgetAllocation(next)
        audioManager.playBudgetSlide()
    }
    fun setAnnualGoal(goal: AnnualGoal) = policyManager.setAnnualGoal(goal)
    fun adjustAdmissionTrack(track: com.arktools.xiaozhang.domain.model.AdmissionTrack, delta: Int) {
        val next = policyManager.policies.value.admissionTrackPlan.adjust(track, delta)
        policyManager.setAdmissionTrackPlan(next)
        audioManager.playBudgetSlide()
    }
    fun foundCollege(type: CollegeType) {
        viewModelScope.safeLaunch {
            val result = gameEngine.foundCollege(type)
            _operationMessage.value = result.message
            if (result.success) {
                audioManager.playCollegeFound()
                gameEngine.notifyFactionDecision(SchoolDecision.BUILD_FACILITY)
            } else {
                audioManager.playEventNegative()
            }
        }
    }

    fun openCoreCourse(college: CollegeType) {
        viewModelScope.safeLaunch {
            val result = gameEngine.openCoreCourse(college)
            _operationMessage.value = result.message
            if (result.success) {
                audioManager.playCourseCreate()
            } else {
                audioManager.playEventNegative()
            }
        }
    }

    fun launchGraduateProgram() {
        viewModelScope.safeLaunch {
            val result = gameEngine.launchGraduateProgram()
            _operationMessage.value = result.message
            if (result.success) {
                audioManager.playLevelUp()
            } else {
                audioManager.playEventNegative()
            }
        }
    }

    fun buildAffiliatedHospital() {
        viewModelScope.safeLaunch {
            val result = gameEngine.buildAffiliatedHospital()
            _operationMessage.value = result.message
            if (result.success) {
                audioManager.playBuildFacility()
                gameEngine.notifyFactionDecision(SchoolDecision.BUILD_FACILITY)
            } else {
                audioManager.playEventNegative()
            }
        }
    }
    fun consumeOperationMessage() {
        _operationMessage.value = null
    }

    fun getPolicyEffects(): PolicyEffects = policyManager.getPolicyEffects()
    fun resetToDefaults() = policyManager.resetToDefaults()
}
