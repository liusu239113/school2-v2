package com.arktools.xiaozhang.domain.autohandle

import android.util.Log
import com.arktools.xiaozhang.domain.model.GameEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 事件自动处理管理器
 * 
 * 根据校长办公室配置的策略，自动处理各类游戏事件，
 * 减少玩家需要手动点击的弹窗数量。
 */
@Singleton
class AutoHandleManager @Inject constructor() {

    companion object {
        private const val TAG = "AutoHandleManager"
        private const val MAX_RECORDS = 50  // 最多保留50条自动处理记录
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _config = MutableStateFlow(AutoHandleConfig())
    val config: StateFlow<AutoHandleConfig> = _config.asStateFlow()

    private val _recentRecords = MutableStateFlow<List<AutoHandledRecord>>(emptyList())
    val recentRecords: StateFlow<List<AutoHandledRecord>> = _recentRecords.asStateFlow()

    /** 自动处理的事件统计 */
    private val _autoHandledCount = MutableStateFlow(0)
    val autoHandledCount: StateFlow<Int> = _autoHandledCount.asStateFlow()

    /**
     * 加载配置（从 JSON 字符串恢复）
     */
    fun loadConfig(configJson: String?) {
        if (configJson.isNullOrBlank()) return
        try {
            _config.value = json.decodeFromString<AutoHandleConfig>(configJson)
            Log.d(TAG, "配置已加载: enabled=${_config.value.enabled}")
        } catch (e: Exception) {
            Log.e(TAG, "配置加载失败，使用默认值", e)
        }
    }

    /**
     * 保存配置为 JSON 字符串
     */
    fun saveConfigToJson(): String {
        return json.encodeToString(_config.value)
    }

    /**
     * 更新配置
     */
    fun updateConfig(newConfig: AutoHandleConfig) {
        _config.value = newConfig
        Log.d(TAG, "配置已更新: enabled=${newConfig.enabled}")
    }

    /**
     * 判断事件是否应该被自动处理
     * @return 自动处理结果，null 表示不自动处理（需要弹窗）
     */
    fun shouldAutoHandle(event: GameEvent): AutoHandleResult? {
        val cfg = _config.value
        if (!cfg.enabled) return null

        return when (event) {
            is GameEvent.ChoiceEvent -> getChoiceAutoAction(event, cfg)
            is GameEvent.PositiveEvent -> {
                if (cfg.positiveAutoClose) {
                    AutoHandleResult.AutoClose
                } else null
            }
            is GameEvent.NegativeEvent -> {
                if (cfg.negativeAutoClose) {
                    AutoHandleResult.AutoClose
                } else null
            }
            is GameEvent.MilestoneEvent -> {
                if (cfg.milestoneAutoClose) {
                    AutoHandleResult.AutoClose
                } else null
            }
            else -> null
        }
    }

    /**
     * 判断选择类事件的自动处理动作
     */
    private fun getChoiceAutoAction(event: GameEvent.ChoiceEvent, cfg: AutoHandleConfig): AutoHandleResult? {
        // 分类识别：根据事件标题/内容判断类型
        val strategy = categorizeChoiceEvent(event, cfg)

        return when (strategy) {
            AutoStrategy.MANUAL -> null
            AutoStrategy.AUTO_APPROVE -> {
                // 自动批准：选第一个选项（通常是同意/批准）
                if (event.choices.isNotEmpty()) {
                    AutoHandleResult.AutoChoice(choiceIndex = 0)
                } else null
            }
            AutoStrategy.AUTO_REJECT -> {
                // 自动拒绝：选最后一个选项（通常是拒绝/驳回）
                if (event.choices.isNotEmpty()) {
                    AutoHandleResult.AutoChoice(choiceIndex = event.choices.lastIndex)
                } else null
            }
        }
    }

    /**
     * 根据事件内容分类，返回对应的处理策略
     */
    private fun categorizeChoiceEvent(event: GameEvent.ChoiceEvent, cfg: AutoHandleConfig): AutoStrategy {
        val title = event.title
        val message = event.message

        // 突发危机：始终返回用户配置（默认 MANUAL，强烈建议手动）
        if (title.startsWith("[突发危机]") || title.startsWith("[危机进展]")) {
            return cfg.crisisStrategy
        }

        // 教师加薪请求
        if (title.contains("加薪") || title.contains("涨薪") || message.contains("请求加薪")) {
            return cfg.teacherRaiseStrategy
        }

        // 教师续约请求
        if (title.contains("续约") || title.contains("合同到期") || message.contains("合同即将到期")) {
            return cfg.teacherRenewalStrategy
        }

        // 教师离职请求
        if (title.contains("离职") || title.contains("辞职") || message.contains("提出离职")) {
            return cfg.teacherResignStrategy
        }

        // 活动审批
        if (title.contains("活动") || title.contains("审批") && message.contains("活动")) {
            return cfg.activityApprovalStrategy
        }

        // 社团审批
        if (title.contains("社团") || message.contains("社团申请")) {
            return cfg.clubApprovalStrategy
        }

        // 其他选择事件
        return cfg.otherChoiceStrategy
    }

    /**
     * 记录自动处理的事件
     */
    fun recordAutoHandle(event: GameEvent, action: String) {
        val record = AutoHandledRecord(
            eventTitle = event.title,
            eventType = when (event) {
                is GameEvent.ChoiceEvent -> "选择"
                is GameEvent.PositiveEvent -> "正面"
                is GameEvent.NegativeEvent -> "负面"
                is GameEvent.MilestoneEvent -> "里程碑"
                else -> "其他"
            },
            action = action
        )
        val current = _recentRecords.value.toMutableList()
        current.add(0, record)
        if (current.size > MAX_RECORDS) {
            _recentRecords.value = current.take(MAX_RECORDS)
        } else {
            _recentRecords.value = current
        }
        _autoHandledCount.value++
    }

    /**
     * 重置统计
     */
    fun resetStats() {
        _autoHandledCount.value = 0
        _recentRecords.value = emptyList()
    }
}

/**
 * 自动处理结果
 */
sealed class AutoHandleResult {
    /** 自动关闭（信息类事件） */
    data object AutoClose : AutoHandleResult()
    /** 自动选择（选择类事件） */
    data class AutoChoice(val choiceIndex: Int) : AutoHandleResult()
}
