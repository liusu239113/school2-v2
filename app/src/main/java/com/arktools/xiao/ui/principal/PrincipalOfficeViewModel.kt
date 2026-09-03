package com.arktools.xiao.ui.principal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiao.domain.engine.CorruptActResult
import com.arktools.xiao.domain.engine.CorruptionOption
import com.arktools.xiao.domain.engine.InvestigationEvent
import com.arktools.xiao.domain.engine.GameEngine
import com.arktools.xiao.domain.model.*
import com.arktools.xiao.domain.repository.SchoolRepository
import com.arktools.xiao.domain.suggestion.Suggestion
import com.arktools.xiao.domain.suggestion.SuggestionPenalty
import com.arktools.xiao.domain.autohandle.AutoHandleConfig
import com.arktools.xiao.domain.autohandle.AutoHandleManager
import com.arktools.xiao.domain.autohandle.AutoHandledRecord
import com.arktools.xiao.data.pref.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import com.arktools.xiao.util.safeLaunch

@HiltViewModel
class PrincipalOfficeViewModel @Inject constructor(
    private val gameEngine: GameEngine,
    private val schoolRepository: SchoolRepository,
    private val autoHandleManager: AutoHandleManager,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val principalState: StateFlow<Principal> = gameEngine.principalFlow

    val schoolState: StateFlow<School?> = schoolRepository.getSchoolFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // 当前可用的贪污操作列表
    private val _availableActions = MutableStateFlow<List<CorruptionOption>>(emptyList())
    val availableActions: StateFlow<List<CorruptionOption>> = _availableActions.asStateFlow()

    // 操作结果反馈
    private val _lastResult = MutableStateFlow<CorruptActResult?>(null)
    val lastResult: StateFlow<CorruptActResult?> = _lastResult.asStateFlow()

    // 调查事件
    private val _investigationEvent = MutableStateFlow<InvestigationEvent?>(null)
    val investigationEvent: StateFlow<InvestigationEvent?> = _investigationEvent.asStateFlow()

    // === 意见箱 ===
    private val _suggestions = MutableStateFlow<List<Suggestion>>(emptyList())
    val suggestions: StateFlow<List<Suggestion>> = _suggestions.asStateFlow()

    private val _suggestionActionResult = MutableStateFlow<String?>(null)
    val suggestionActionResult: StateFlow<String?> = _suggestionActionResult.asStateFlow()

    // === 事件自动处理 ===
    val autoHandleConfig: StateFlow<AutoHandleConfig> = autoHandleManager.config
    val autoHandledRecords: StateFlow<List<AutoHandledRecord>> = autoHandleManager.recentRecords
    val autoHandledCount: StateFlow<Int> = autoHandleManager.autoHandledCount

    init {
        // 监听 school 变化刷新可用操作
        viewModelScope.safeLaunch {
            schoolState.collect { school ->
                if (school != null) {
                    refreshAvailableActions(school)
                    refreshSuggestions()
                }
            }
        }
        // 加载自动处理配置
        viewModelScope.safeLaunch {
            val configJson = settingsDataStore.getAutoHandleConfig()
            autoHandleManager.loadConfig(configJson)
        }
    }

    fun refreshAvailableActions(school: School? = null) {
        val s = school ?: schoolState.value ?: return
        val principal = principalState.value
        _availableActions.value = gameEngine.corruptionManager.getAvailableCorruptActions(principal, s)
    }

    /**
     * 刷新意见箱列表
     */
    fun refreshSuggestions() {
        _suggestions.value = gameEngine.suggestionBoxManager.getPendingSuggestions()
    }

    /**
     * 执行贪污操作
     */
    fun executeCorruptAction(option: CorruptionOption) {
        viewModelScope.safeLaunch {
            val outcome = gameEngine.executeCorruptAction(option)
            _lastResult.value = outcome.result
            _investigationEvent.value = outcome.investigationEvent
            val latestSchool = schoolRepository.getSchool()
            if (latestSchool != null) {
                refreshAvailableActions(latestSchool)
            }
        }
    }

    /**
     * 采纳建议
     */
    fun resolveSuggestion(suggestionId: Int) {
        val message = gameEngine.suggestionBoxManager.resolveSuggestion(suggestionId)
        _suggestionActionResult.value = message
        refreshSuggestions()
    }

    /**
     * 忽略建议（会有惩罚）
     */
    fun ignoreSuggestion(suggestionId: Int) {
        val penalty = gameEngine.suggestionBoxManager.ignoreSuggestion(suggestionId)
        if (penalty != null) {
            _suggestionActionResult.value = "已忽略建议，${penalty.submitterName}的${
                if (penalty.submitterType == com.arktools.xiao.domain.suggestion.SubmitterType.TEACHER) "忠诚度" else "满意度"
            }将下降${penalty.penaltyAmount.toInt()}点"
        } else {
            _suggestionActionResult.value = "操作失败"
        }
        refreshSuggestions()
    }

    fun dismissSuggestionResult() {
        _suggestionActionResult.value = null
    }

    /**
     * 用个人资金购买物品（消费）。只有 principalJson 事务提交成功后才更新界面状态。
     */
    suspend fun purchasePersonalItem(itemName: String, cost: Double): Boolean =
        gameEngine.purchasePersonalItem(itemName, cost)

    fun dismissResult() {
        _lastResult.value = null
    }

    fun dismissInvestigation() {
        _investigationEvent.value = null
    }

    // ======== 事件自动处理配置管理 ========

    /**
     * 更新自动处理配置并持久化
     */
    fun updateAutoHandleConfig(newConfig: AutoHandleConfig) {
        autoHandleManager.updateConfig(newConfig)
        viewModelScope.safeLaunch {
            settingsDataStore.setAutoHandleConfig(autoHandleManager.saveConfigToJson())
        }
    }

    /**
     * 切换自动处理总开关
     */
    fun toggleAutoHandleEnabled(enabled: Boolean) {
        val current = autoHandleConfig.value
        updateAutoHandleConfig(current.copy(enabled = enabled))
    }

    /**
     * 重置自动处理统计
     */
    fun resetAutoHandleStats() {
        autoHandleManager.resetStats()
    }

    /**
     * 将个人资金捐献给学校。校长资金、学校现金和声望在同一事务中提交。
     */
    suspend fun donateToSchool(amount: Double): Boolean =
        gameEngine.donatePersonalFundsToSchool(amount)
}
