package com.arktools.xiaozhang.ui.seasonal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiaozhang.audio.AudioManager
import com.arktools.xiaozhang.domain.engine.GameEngine
import com.arktools.xiaozhang.domain.repository.SchoolRepository
import com.arktools.xiaozhang.domain.seasonal.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.arktools.xiaozhang.util.safeLaunch

@HiltViewModel
class SeasonalViewModel @Inject constructor(
    private val gameEngine: GameEngine,
    private val schoolRepository: SchoolRepository,
    private val audioManager: AudioManager
) : ViewModel() {

    val state: StateFlow<SeasonalActivityState> = gameEngine.seasonalActivityManager.state

    private val _hostMessage = MutableStateFlow<String?>(null)
    val hostMessage = _hostMessage.asStateFlow()

    /** 可立即举办的活动（有专属小游戏玩法） */
    val quickHostTypes: List<ActivityType> = listOf(
        ActivityType.SPORTS_DAY,
        ActivityType.DEBATE_TOURNAMENT,
        ActivityType.SCIENCE_FAIR,
        ActivityType.CULTURAL_FESTIVAL
    )

    fun hostActivity(type: ActivityType) {
        viewModelScope.safeLaunch {
            audioManager.playButtonClick()
            val school = schoolRepository.getSchool() ?: return@safeLaunch
            val costWan = type.baseCost / 10000.0
            if (school.cash < costWan) {
                _hostMessage.value = "资金不足！举办" + type.displayName + "需要 " + costWan.toInt() + " 万"
                audioManager.playEventNegative()
                return@safeLaunch
            }
            val pre = gameEngine.seasonalActivityManager.hostNow(type, school.currentYear, school.currentMonth)
            if (!pre.first) {
                _hostMessage.value = pre.second
                return@safeLaunch
            }
            // 活动结束时由 GameEngine 统一扣除 actualCost，避免立即举办路径重复扣款。
            audioManager.playBuildFacility()
            _hostMessage.value = pre.second
        }
    }

    fun consumeHostMessage() {
        _hostMessage.value = null
    }

    /**
     * 获取待审批的活动（UI展示红点提醒用）
     */
    fun getPendingActivities(): List<SeasonalActivity> {
        return gameEngine.seasonalActivityManager.getPendingApprovalActivities()
    }

    /**
     * 获取所有可见活动（待审批+筹备+进行中）
     */
    fun getAllVisibleActivities(): List<SeasonalActivity> {
        return gameEngine.seasonalActivityManager.getAllVisibleActivities()
    }

    /**
     * 获取正在进行的活动（筹备+举办中）
     */
    fun getActiveActivities(): List<SeasonalActivity> {
        return gameEngine.seasonalActivityManager.getActiveActivities()
    }

    /**
     * 重新触发活动审批弹窗
     * 解决 ChoiceEvent 只弹一次、玩家错过后在 SeasonalScreen 上"点不了"的问题
     */
    fun triggerApproval(activityId: String) {
        viewModelScope.safeLaunch {
            gameEngine.retriggerActivityApproval(activityId)
        }
    }
}
