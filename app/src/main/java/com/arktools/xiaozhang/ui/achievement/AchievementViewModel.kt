package com.arktools.xiaozhang.ui.achievement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiaozhang.domain.achievement.Achievement
import com.arktools.xiaozhang.domain.achievement.AchievementCategory
import com.arktools.xiaozhang.domain.achievement.AchievementManager
import com.arktools.xiaozhang.domain.milestone.Milestone
import com.arktools.xiaozhang.domain.milestone.MilestoneCategory
import com.arktools.xiaozhang.domain.milestone.MilestoneManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.arktools.xiaozhang.util.safeLaunch

data class AchievementUiState(
    val allAchievements: List<Achievement> = emptyList(),
    val unlockedCount: Int = 0,
    val totalCount: Int = 0,
    val progress: Float = 0f,
    val selectedCategory: AchievementCategory? = null
)

@HiltViewModel
class AchievementViewModel @Inject constructor(
    private val achievementManager: AchievementManager,
    private val milestoneManager: MilestoneManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AchievementUiState())
    val uiState: StateFlow<AchievementUiState> = _uiState.asStateFlow()

    // ========== 里程碑相关 ==========
    val milestones: StateFlow<List<Milestone>> = milestoneManager.milestoneState

    private val _milestoneSelectedCategory = MutableStateFlow<MilestoneCategory?>(null)
    val milestoneSelectedCategory: StateFlow<MilestoneCategory?> = _milestoneSelectedCategory.asStateFlow()

    private val _overallProgress = MutableStateFlow(0f)
    val overallProgress: StateFlow<Float> = _overallProgress.asStateFlow()

    init {
        viewModelScope.safeLaunch {
            achievementManager.unlockedAchievements.collect {
                refreshState()
            }
        }
        viewModelScope.safeLaunch {
            milestoneManager.milestoneState.collect {
                _overallProgress.value = milestoneManager.getOverallProgress()
            }
        }
        refreshState()
    }

    private fun refreshState() {
        val all = achievementManager.getAll()
        val unlocked = all.count { it.unlocked }
        _uiState.value = _uiState.value.copy(
            allAchievements = all,
            unlockedCount = unlocked,
            totalCount = all.size,
            progress = achievementManager.getProgress()
        )
    }

    fun selectCategory(category: AchievementCategory?) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
    }

    fun getFilteredAchievements(): List<Achievement> {
        val state = _uiState.value
        return if (state.selectedCategory == null) {
            state.allAchievements
        } else {
            state.allAchievements.filter { it.category == state.selectedCategory }
        }
    }

    // ========== 里程碑方法 ==========
    fun selectMilestoneCategory(category: MilestoneCategory?) {
        _milestoneSelectedCategory.value = category
    }

    fun getFilteredMilestones(milestones: List<Milestone>, category: MilestoneCategory?): List<Milestone> {
        return if (category == null) milestones else milestones.filter { it.category == category }
    }
}
