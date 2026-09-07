package com.arktools.xiao.ui.gameover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiao.domain.engine.BailoutResult
import com.arktools.xiao.domain.engine.CrisisState
import com.arktools.xiao.domain.engine.FailureCondition
import com.arktools.xiao.domain.engine.GameEngine
import com.arktools.xiao.domain.engine.GameOverDetector
import com.arktools.xiao.domain.engine.GameOverReason
import com.arktools.xiao.domain.engine.HealthReport
import com.arktools.xiao.domain.model.schoolOwnership
import com.arktools.xiao.domain.model.schoolTier
import com.arktools.xiao.domain.model.promotionHistoryText
import com.arktools.xiao.domain.repository.SchoolRepository
import com.arktools.xiao.domain.repository.StudentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.arktools.xiao.util.safeLaunch

@HiltViewModel
class GameOverViewModel @Inject constructor(
    private val gameEngine: GameEngine,
    private val gameOverDetector: GameOverDetector,
    private val schoolRepository: SchoolRepository,
    private val studentRepository: StudentRepository
) : ViewModel() {

    val crisisState: StateFlow<CrisisState> = gameOverDetector.crisisState
    val gameOverReason: StateFlow<GameOverReason?> = gameOverDetector.gameOverReason
    val activeConditions: StateFlow<List<FailureCondition>> = gameOverDetector.activeConditions

    private val _healthReport = MutableStateFlow<HealthReport?>(null)
    val healthReport: StateFlow<HealthReport?> = _healthReport.asStateFlow()

    private val _bailoutMessage = MutableStateFlow<String?>(null)
    val bailoutMessage: StateFlow<String?> = _bailoutMessage.asStateFlow()

    init {
        viewModelScope.safeLaunch {
            gameOverDetector.crisisState.collect { state ->
                if (state == CrisisState.CRITICAL || state == CrisisState.WARNING) {
                    refreshHealthReport()
                }
            }
        }
    }

    private suspend fun refreshHealthReport() {
        val school = schoolRepository.getSchool() ?: return
        _healthReport.value = gameOverDetector.getHealthReport(school)
    }

    fun acceptBailout() {
        viewModelScope.safeLaunch {
            when (val result = gameOverDetector.executeBailout()) {
                is BailoutResult.SUCCESS -> {
                    _bailoutMessage.value = if (result.debtCleared > 0) {
                        "救助成功！已清掉欠款 ${result.debtCleared.toInt()}万，并注入办学经费 ${com.arktools.xiao.domain.engine.GameOverDetector.BAILOUT_CASH_GRANT.toInt()}万，声誉+${result.reputationGrant}"
                    } else {
                        "救助成功！获得办学经费 +${result.cashGrant.toInt()}万 +${result.reputationGrant}声誉"
                    }
                    schoolRepository.mutateSchool { school ->
                        gameEngine.financialReportManager.recordIncome(
                            com.arktools.xiao.domain.finance.IncomeCategory.OTHER_INCOME,
                            result.cashGrant,
                            if (result.debtCleared > 0) {
                                "看广告救助：清债 ${result.debtCleared.toInt()}万 + 办学经费 ${com.arktools.xiao.domain.engine.GameOverDetector.BAILOUT_CASH_GRANT.toInt()}万"
                            } else {
                                "看广告救助：办学经费 +${result.cashGrant.toInt()}万"
                            }
                        )
                        school.financialReportJson = gameEngine.financialReportManager.toJson()
                        true
                    }
                    gameEngine.resumeFromCrisis()
                    refreshHealthReport()
                }
                is BailoutResult.NO_BAILOUTS_LEFT -> {
                    _bailoutMessage.value = "已无剩余救助次数"
                }
                is BailoutResult.FAILED -> {
                    _bailoutMessage.value = "救助失败"
                }
            }
        }
    }

    fun declineBailout() {
        viewModelScope.safeLaunch {
            val school = schoolRepository.getSchool()
            val graduateCount = studentRepository.getGraduateCount()
            val reason = GameOverReason(
                conditions = gameOverDetector.activeConditions.value,
                finalCash = school?.cash ?: 0.0,
                finalReputation = school?.reputation ?: 0L,
                totalYearsPlayed = (school?.currentYear ?: 1) - (school?.foundedYear ?: 1),
                totalStudentsGraduated = graduateCount,
                peakReputation = school?.reputation ?: 0L,
                peakCash = school?.totalRevenue ?: 0.0,
                schoolTypeName = school?.let {
                    it.schoolTier().displayName + "·" + it.schoolOwnership().displayName
                } ?: "",
                promotionHistoryText = school?.promotionHistoryText() ?: ""
            )
            gameOverDetector.confirmGameOver(reason)
            gameEngine.confirmGameOver()
        }
    }

    fun clearBailoutMessage() {
        _bailoutMessage.value = null
    }
}
