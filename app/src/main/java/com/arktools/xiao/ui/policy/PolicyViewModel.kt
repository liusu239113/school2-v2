package com.arktools.xiao.ui.policy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiao.audio.AudioManager
import com.arktools.xiao.domain.engine.GameEngine
import com.arktools.xiao.domain.engine.SchoolDecision
import com.arktools.xiao.domain.policy.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import com.arktools.xiao.util.safeLaunch

@HiltViewModel
class PolicyViewModel @Inject constructor(
    private val policyManager: SchoolPolicyManager,
    private val gameEngine: GameEngine,
    private val audioManager: AudioManager,
    private val schoolRepository: com.arktools.xiao.domain.repository.SchoolRepository
) : ViewModel() {

    val policies: StateFlow<SchoolPolicies> = policyManager.policies

    private val _campusLevel = MutableStateFlow(1)
    /** 当前校园等级：年度目标达标条件按等级换算展示。 */
    val campusLevel: StateFlow<Int> = _campusLevel.asStateFlow()

    init {
        viewModelScope.safeLaunch {
            schoolRepository.getSchoolFlow().collect { school ->
                if (school != null) _campusLevel.value = school.campusLevel
            }
        }
    }

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    fun setTuitionLevel(level: TuitionLevel) {
        val oldLevel = policyManager.policies.value.tuitionLevel
        policyManager.setTuitionLevel(level)
        persistPolicies()
        if (level.ordinal > oldLevel.ordinal) {
            gameEngine.notifyFactionDecision(SchoolDecision.RAISE_TUITION)
        }
        announceEffects()
    }

    fun setExamDifficulty(difficulty: ExamDifficulty) {
        policyManager.setExamDifficulty(difficulty)
        persistPolicies()
        if (difficulty == ExamDifficulty.CHALLENGING || difficulty == ExamDifficulty.RIGOROUS) {
            gameEngine.notifyFactionDecision(SchoolDecision.STRICT_DISCIPLINE)
        }
        announceEffects()
    }
    fun setTeacherPayPolicy(policy: TeacherPayPolicy) {
        policyManager.setTeacherPayPolicy(policy)
        persistPolicies()
        announceEffects()
    }
    fun setExtracurricularPolicy(policy: ExtracurricularPolicy) {
        policyManager.setExtracurricularPolicy(policy)
        persistPolicies()
        if (policy == ExtracurricularPolicy.RICH || policy == ExtracurricularPolicy.WORLD_CLASS) {
            gameEngine.notifyFactionDecision(SchoolDecision.CLUB_ACTIVITY)
        }
        announceEffects()
    }
    fun setAdmissionPolicy(policy: AdmissionPolicy) {
        policyManager.setAdmissionPolicy(policy)
        persistPolicies()
        announceEffects()
    }
    fun setEnrollmentPlan(plan: EnrollmentPlan) {
        policyManager.setEnrollmentPlan(plan)
        persistPolicies()
        announceEffects()
    }
    fun setUniversityStrategy(strategy: UniversityStrategy) {
        policyManager.setUniversityStrategy(strategy)
        persistPolicies()
        announceEffects()
    }
    fun adjustBudget(line: BudgetLine, delta: Int) {
        val next = policyManager.policies.value.budgetAllocation.adjust(line, delta)
        policyManager.setBudgetAllocation(next)
        persistPolicies()
        audioManager.playBudgetSlide()
    }
    fun setAnnualGoal(goal: AnnualGoal) {
        policyManager.setAnnualGoal(goal)
        persistPolicies()
    }

    private fun persistPolicies() {
        viewModelScope.safeLaunch {
            schoolRepository.mutateSchool { school ->
                school.policyJson = policyManager.toJson()
                true
            }
        }
    }

    private fun announceEffects() {
        val e = policyManager.getPolicyEffects()
        val enrollPct = ((e.enrollmentMultiplier - 1f) * 100f).toInt()
        val qualityPct = ((e.qualityMultiplier - 1f) * 100f).toInt()
        val tuitionPct = ((e.tuitionMultiplier - 1f) * 100f).toInt()
        fun signed(n: Int) = if (n >= 0) "+$n%" else "$n%"
        _operationMessage.value =
            "已生效：学费收入 ${signed(tuitionPct)} · 招生 ${signed(enrollPct)} · 培养质量 ${signed(qualityPct)} · 月声誉 ${if (e.reputationModifier >= 0) "+" else ""}${e.reputationModifier} · 专项开支 ${"%.1f".format(e.monthlySpecialBudgetCost)}万"
    }
    fun playUiClick() = audioManager.playButtonClick()
    fun adjustAdmissionTrack(track: com.arktools.xiao.domain.model.AdmissionTrack, delta: Int) {
        val next = policyManager.policies.value.admissionTrackPlan.adjust(track, delta)
        policyManager.setAdmissionTrackPlan(next)
        persistPolicies()
        audioManager.playBudgetSlide()
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

    fun consumeOperationMessage() {
        _operationMessage.value = null
    }

    fun getPolicyEffects(): PolicyEffects = policyManager.getPolicyEffects()
    fun resetToDefaults() = policyManager.resetToDefaults()
}
