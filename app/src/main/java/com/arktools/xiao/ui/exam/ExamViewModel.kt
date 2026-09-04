package com.arktools.xiao.ui.exam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiao.domain.engine.GameEngine
import com.arktools.xiao.domain.exam.ExamRecord
import com.arktools.xiao.domain.exam.StudentScore
import com.arktools.xiao.domain.repository.SchoolRepository
import com.arktools.xiao.util.safeLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExamUiState(
    val examHistory: List<ExamRecord> = emptyList(),
    val latestExam: ExamRecord? = null,
    val selectedExamScores: List<StudentScore> = emptyList(),
    val selectedExamId: String? = null,
    val coachingBonus: Float = 0f,
    val message: String = ""
)

@HiltViewModel
class ExamViewModel @Inject constructor(
    private val gameEngine: GameEngine,
    private val schoolRepository: SchoolRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExamUiState())
    val uiState: StateFlow<ExamUiState> = _uiState.asStateFlow()

    init {
        refreshData()
        viewModelScope.launch {
            gameEngine.gameDaySignal.collect {
                refreshData()
            }
        }
    }

    fun refreshData() {
        val history = gameEngine.examManager.getExamHistory()
        val latest = gameEngine.examManager.getLatestExam()
        _uiState.value = _uiState.value.copy(
            examHistory = history,
            latestExam = latest,
            coachingBonus = gameEngine.examManager.coachingBonus()
        )
    }

    fun selectExam(examId: String) {
        _uiState.value = _uiState.value.copy(selectedExamId = examId)
    }

    /** 花 4 万买下次考试全校 +3 分。不点就按当前课表和班型硬考。 */
    fun buyCoaching() {
        viewModelScope.safeLaunch {
            val school = schoolRepository.getSchool() ?: return@safeLaunch
            if (school.cash < 4.0) {
                _uiState.value = _uiState.value.copy(message = "经费不够：考前辅导要 4 万。")
                return@safeLaunch
            }
            if (gameEngine.examManager.coachingBonus() >= 12f) {
                _uiState.value = _uiState.value.copy(message = "下次考试加分已经顶满（+12）。")
                return@safeLaunch
            }
            schoolRepository.deductCash(4.0)
            gameEngine.examManager.buyCoaching(3f)
            schoolRepository.mutateSchool { latest ->
                latest.examJson = gameEngine.examManager.toJson()
                true
            }
            _uiState.value = _uiState.value.copy(
                coachingBonus = gameEngine.examManager.coachingBonus(),
                message = "已买考前辅导：下次考试全校 +3 分（累计 +${gameEngine.examManager.coachingBonus().toInt()}），扣 4 万。考完清零。"
            )
        }
    }
}
