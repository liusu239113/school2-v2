package com.arktools.xiaozhang.ui.seasonal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiaozhang.domain.engine.GameEngine
import com.arktools.xiaozhang.domain.seasonal.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.arktools.xiaozhang.util.safeLaunch

@HiltViewModel
class SeasonalViewModel @Inject constructor(
    private val gameEngine: GameEngine
) : ViewModel() {

    val state: StateFlow<SeasonalActivityState> = gameEngine.seasonalActivityManager.state

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
