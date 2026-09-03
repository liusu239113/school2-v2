package com.arktools.xiao.ui.marketing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiao.domain.engine.GameEngine
import com.arktools.xiao.domain.engine.SchoolDecision
import com.arktools.xiao.domain.model.MarketingCampaign
import com.arktools.xiao.domain.model.MarketingCalculator
import com.arktools.xiao.domain.model.MarketingChannel
import com.arktools.xiao.domain.repository.SchoolRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.arktools.xiao.util.safeLaunch

data class MarketingUiState(
    val campaigns: List<MarketingCampaign> = emptyList(),
    val schoolCash: Double = 0.0,
    val totalMonthlyCost: Double = 0.0,
    val enrollmentBoost: Double = 0.0,
    val reputationBoost: Double = 0.0,
    val showCreateDialog: Boolean = false,
    val selectedChannel: MarketingChannel? = null,
    val budgetInput: Double = 0.0,
    val message: String? = null
)

@HiltViewModel
class MarketingViewModel @Inject constructor(
    private val schoolRepository: SchoolRepository,
    private val gameEngine: GameEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(MarketingUiState())
    val uiState: StateFlow<MarketingUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.safeLaunch {
            schoolRepository.getSchoolFlow().collect { school ->
                if (school != null) {
                    val campaigns = school.marketingCampaigns.filter { it.isActive }
                    _uiState.value = _uiState.value.copy(
                        campaigns = school.marketingCampaigns.toList(),
                        schoolCash = school.cash,
                        totalMonthlyCost = MarketingCalculator.getTotalMonthlyCost(school.marketingCampaigns),
                        enrollmentBoost = (MarketingCalculator.getEnrollmentMultiplier(campaigns) - 1.0) * 100,
                        reputationBoost = MarketingCalculator.getReputationBoost(campaigns).toDouble()
                    )
                }
            }
        }
    }

    fun showCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = true)
    }

    fun dismissCreateDialog() {
        _uiState.value = _uiState.value.copy(
            showCreateDialog = false,
            selectedChannel = null,
            budgetInput = 0.0
        )
    }

    fun selectChannel(channel: MarketingChannel) {
        _uiState.value = _uiState.value.copy(
            selectedChannel = channel,
            budgetInput = channel.minBudget
        )
    }

    fun updateBudget(budget: Double) {
        _uiState.value = _uiState.value.copy(budgetInput = budget)
    }

    fun createCampaign() {
        val state = _uiState.value
        val channel = state.selectedChannel ?: return
        val budget = state.budgetInput

        if (budget < channel.minBudget || budget > channel.maxBudget) return
        if (budget > state.schoolCash) return

        // 检查是否已有同渠道的活跃推广
        val hasActiveInSameChannel = state.campaigns.any { it.channel == channel && it.isActive }
        if (hasActiveInSameChannel) {
            _uiState.value = _uiState.value.copy(
                message = "「${channel.displayName}」已有进行中的推广，请先停止后再发起新的"
            )
            return
        }

        viewModelScope.safeLaunch {
            schoolRepository.mutateSchool { school ->
                // 再次在锁内检查（防止 UI state 和 DB 不一致）
                if (school.marketingCampaigns.any { it.channel == channel && it.isActive }) {
                    return@mutateSchool false
                }
                val campaign = MarketingCampaign(
                    courseId = "school_wide",
                    channel = channel,
                    budget = budget
                )
                school.marketingCampaigns.add(campaign)
                true
            }
            gameEngine.notifyFactionDecision(SchoolDecision.MARKETING_CAMPAIGN)
            dismissCreateDialog()
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun stopCampaign(campaign: MarketingCampaign) {
        viewModelScope.safeLaunch {
            schoolRepository.mutateSchool { school ->
                val target = school.marketingCampaigns.find { it.id == campaign.id }
                target?.isActive = false
                true
            }
        }
    }

    fun removeCampaign(campaign: MarketingCampaign) {
        viewModelScope.safeLaunch {
            schoolRepository.mutateSchool { school ->
                school.marketingCampaigns.removeAll { it.id == campaign.id }
                true
            }
        }
    }
}
