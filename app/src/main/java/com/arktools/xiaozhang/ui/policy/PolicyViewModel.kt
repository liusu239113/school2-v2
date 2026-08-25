package com.arktools.xiaozhang.ui.policy

import androidx.lifecycle.ViewModel
import com.arktools.xiaozhang.domain.engine.GameEngine
import com.arktools.xiaozhang.domain.engine.SchoolDecision
import com.arktools.xiaozhang.domain.policy.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PolicyViewModel @Inject constructor(
    private val policyManager: SchoolPolicyManager,
    private val gameEngine: GameEngine
) : ViewModel() {

    val policies: StateFlow<SchoolPolicies> = policyManager.policies

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

    fun getPolicyEffects(): PolicyEffects = policyManager.getPolicyEffects()
    fun resetToDefaults() = policyManager.resetToDefaults()
}
