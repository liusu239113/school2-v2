package com.arktools.xiaozhang.ui.research

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiaozhang.audio.AudioManager
import com.arktools.xiaozhang.domain.model.BonusType
import com.arktools.xiaozhang.domain.model.TeachingMethod
import com.arktools.xiaozhang.domain.repository.ResearchRepository
import com.arktools.xiaozhang.domain.repository.TeachingMethodUnlockStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import com.arktools.xiaozhang.domain.model.MethodCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.arktools.xiaozhang.util.safeLaunch

/**
 * 加成汇总数据，用于 UI 展示各类型总加成
 */
data class BonusSummary(
    val teachingQuality: Float = 0f,
    val researchSpeed: Float = 0f,
    val enrollment: Float = 0f,
    val revenue: Float = 0f,
    val teacherLoyalty: Float = 0f,
    val costReduction: Float = 0f
) {
    val totalBonusCount: Int get() = listOf(
        teachingQuality, researchSpeed, enrollment, revenue, teacherLoyalty, costReduction
    ).count { it > 0f }
}

@HiltViewModel
class ResearchViewModel @Inject constructor(
    private val researchRepository: ResearchRepository,
    private val audioManager: AudioManager,
    private val policyManager: com.arktools.xiaozhang.domain.policy.SchoolPolicyManager,
    private val schoolRepository: com.arktools.xiaozhang.domain.repository.SchoolRepository,
    private val gameEngine: com.arktools.xiaozhang.domain.engine.GameEngine
) : ViewModel() {

    // ===== 科研课题链 =====

    data class ChainUiState(
        val definitions: List<com.arktools.xiaozhang.domain.research.ResearchChainManager.ChainDef> =
            emptyList(),
        val programs: Map<String, com.arktools.xiaozhang.domain.research.ResearchChainManager.ChainProgress> =
            emptyMap(),
        val completedChains: List<String> = emptyList(),
        val qualityBonus: Float = 0f,
        val message: String? = null
    )

    private val _chainUi = MutableStateFlow(ChainUiState())
    val chainUi: StateFlow<ChainUiState> = _chainUi.asStateFlow()

    init {
        viewModelScope.safeLaunch {
            schoolRepository.getSchoolFlow().collect {
                refreshChainUi()
            }
        }
    }

    private fun refreshChainUi() {
        val manager = policyManager.researchChainManager
        _chainUi.value = _chainUi.value.copy(
            definitions = manager.definitions(),
            programs = manager.snapshotState().programs,
            completedChains = manager.snapshotState().completedChains,
            qualityBonus = manager.qualityBonus()
        )
    }

    fun startChain(chainId: String) {
        viewModelScope.safeLaunch {
            val result = gameEngine.startResearchProgram(chainId)
            _chainUi.value = _chainUi.value.copy(message = result.message)
            refreshChainUi()
            if (result.success) audioManager.playResearchUnlock()
        }
    }

    fun consumeChainMessage() {
        _chainUi.value = _chainUi.value.copy(message = null)
    }

    private val _allMethods = MutableStateFlow<List<TeachingMethod>>(emptyList())

    private val _selectedCategory = MutableStateFlow<MethodCategory?>(null)
    val selectedCategory: StateFlow<MethodCategory?> = _selectedCategory.asStateFlow()

    private val _methods = MutableStateFlow<List<TeachingMethod>>(emptyList())
    val methods: StateFlow<List<TeachingMethod>> = _methods.asStateFlow()

    private val _unlockedMethods = MutableStateFlow<List<TeachingMethod>>(emptyList())
    val unlockedMethods: StateFlow<List<TeachingMethod>> = _unlockedMethods.asStateFlow()

    private val _bonusSummary = MutableStateFlow(BonusSummary())
    val bonusSummary: StateFlow<BonusSummary> = _bonusSummary.asStateFlow()

    private val _selectedMethod = MutableStateFlow<TeachingMethod?>(null)
    val selectedMethod: StateFlow<TeachingMethod?> = _selectedMethod.asStateFlow()

    // 最近开始研究的方法（用于显示研究已启动反馈）
    private val _recentlyUnlocked = MutableStateFlow<TeachingMethod?>(null)
    val recentlyUnlocked: StateFlow<TeachingMethod?> = _recentlyUnlocked.asStateFlow()

    fun clearRecentlyUnlocked() { _recentlyUnlocked.value = null }

    init {
        loadMethods()
        observeFiltered()
    }

    private fun loadMethods() {
        viewModelScope.safeLaunch {
            researchRepository.getMethodsFlow().collect { allMethods ->
                _allMethods.value = allMethods
                val unlocked = allMethods.filter { it.isUnlocked }
                _unlockedMethods.value = unlocked
                // 计算加成汇总
                _bonusSummary.value = BonusSummary(
                    teachingQuality = unlocked.filter { it.bonusType == BonusType.TEACHING_QUALITY }.sumOf { it.bonusValue.toDouble() }.toFloat(),
                    researchSpeed = unlocked.filter { it.bonusType == BonusType.RESEARCH_SPEED }.sumOf { it.bonusValue.toDouble() }.toFloat(),
                    enrollment = unlocked.filter { it.bonusType == BonusType.ENROLLMENT }.sumOf { it.bonusValue.toDouble() }.toFloat(),
                    revenue = unlocked.filter { it.bonusType == BonusType.REVENUE }.sumOf { it.bonusValue.toDouble() }.toFloat(),
                    teacherLoyalty = unlocked.filter { it.bonusType == BonusType.TEACHER_LOYALTY }.sumOf { it.bonusValue.toDouble() }.toFloat(),
                    costReduction = unlocked.filter { it.bonusType == BonusType.COST_REDUCTION }.sumOf { it.bonusValue.toDouble() }.toFloat()
                )
            }
        }
    }

    private fun observeFiltered() {
        viewModelScope.safeLaunch {
            combine(_allMethods, _selectedCategory) { all, category ->
                if (category == null) all
                else all.filter { it.category == category }
            }.collect { filtered ->
                _methods.value = filtered
            }
        }
    }

    fun selectCategory(category: MethodCategory?) {
        _selectedCategory.value = category
    }

    fun selectMethod(method: TeachingMethod) {
        _selectedMethod.value = method
    }

    fun clearSelectedMethod() {
        _selectedMethod.value = null
    }

    private val _unlockError = MutableStateFlow<String?>(null)
    val unlockError: StateFlow<String?> = _unlockError.asStateFlow()

    fun clearUnlockError() { _unlockError.value = null }

    /**
     * 解锁教学方法
     * 约束：
     * 1. 资金充足检查
     * 2. 分层门槛：高级方法需要先解锁一定数量的低级方法
     *    - Tier 1 (cost <= 10万): 无需前置
     *    - Tier 2 (cost 11-25万): 需先解锁3个方法
     *    - Tier 3 (cost 26-50万): 需先解锁6个方法
     *    - Tier 4 (cost 51-100万): 需先解锁10个方法
     *    - Tier 5 (cost > 100万): 需先解锁15个方法
     */
    fun unlockMethod(methodId: String) {
        viewModelScope.safeLaunch {
            val result = researchRepository.unlockMethod(methodId)
            when (result.status) {
                TeachingMethodUnlockStatus.SUCCESS -> {
                    val method = result.method ?: return@safeLaunch
                    _selectedMethod.value = null
                    _unlockError.value = null
                    _recentlyUnlocked.value = method
                    audioManager.playResearchUnlock()
                }
                TeachingMethodUnlockStatus.ALREADY_UNLOCKED -> {
                    _unlockError.value = "该教学方法已经解锁"
                }
                TeachingMethodUnlockStatus.INSUFFICIENT_FUNDS -> {
                    val cost = result.method?.cost ?: 0.0
                    _unlockError.value =
                        "资金不足！需要 ${cost}万，当前仅有 ${"%.1f".format(result.availableCash)}万"
                }
                TeachingMethodUnlockStatus.TIER_LOCKED -> {
                    _unlockError.value =
                        "需要先解锁至少 ${result.requiredUnlocks} 项教学方法才能研究此项（当前已解锁 ${result.unlockedCount} 项）"
                }
                TeachingMethodUnlockStatus.PREREQUISITE_LOCKED -> {
                    _unlockError.value = "尚未解锁此教学方法要求的前置研究"
                }
                TeachingMethodUnlockStatus.UNAVAILABLE -> {
                    _unlockError.value = "教学方法状态已变化，请重试"
                }
            }
        }
    }
}
