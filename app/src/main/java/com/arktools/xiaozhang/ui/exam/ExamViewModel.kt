package com.arktools.xiaozhang.ui.exam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiaozhang.domain.engine.GameEngine
import com.arktools.xiaozhang.domain.exam.ExamRecord
import com.arktools.xiaozhang.domain.exam.StudentScore
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
    val selectedExamId: String? = null
)

@HiltViewModel
class ExamViewModel @Inject constructor(
    private val gameEngine: GameEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExamUiState())
    val uiState: StateFlow<ExamUiState> = _uiState.asStateFlow()

    init {
        refreshData()
        // 监听游戏日推进信号，自动刷新考试成绩
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
            latestExam = latest
        )
    }

    fun selectExam(examId: String) {
        _uiState.value = _uiState.value.copy(selectedExamId = examId)
    }
}
