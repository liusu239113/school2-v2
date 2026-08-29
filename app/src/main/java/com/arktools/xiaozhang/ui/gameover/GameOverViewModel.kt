package com.arktools.xiaozhang.ui.gameover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiaozhang.domain.engine.BailoutResult
import com.arktools.xiaozhang.domain.engine.CrisisState
import com.arktools.xiaozhang.domain.engine.FailureCondition
import com.arktools.xiaozhang.domain.engine.GameEngine
import com.arktools.xiaozhang.domain.engine.GameOverDetector
import com.arktools.xiaozhang.domain.engine.GameOverReason
import com.arktools.xiaozhang.domain.engine.HealthReport
import com.arktools.xiaozhang.domain.model.schoolOwnership
import com.arktools.xiaozhang.domain.model.schoolTier
import com.arktools.xiaozhang.domain.repository.SchoolRepository
import com.arktools.xiaozhang.domain.repository.StudentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.arktools.xiaozhang.util.safeLaunch

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
                    _bailoutMessage.value = "救助成功！获得 +${result.cashGrant.toInt()}万 +${result.reputationGrant}声誉"
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
                } ?: ""
            )
            gameOverDetector.confirmGameOver(reason)
            gameEngine.confirmGameOver()
        }
    }

    fun clearBailoutMessage() {
        _bailoutMessage.value = null
    }
}
