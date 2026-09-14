package com.arktools.xiao.domain.autohandle

import com.arktools.xiao.domain.model.GameEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 行政楼任职后的事件自动审批。
 * 没人任职的职位一律弹窗；只有任命了教师、并且该职位策略不是手动，才会代批。
 */
@Singleton
class AutoHandleManager @Inject constructor() {

    companion object {
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
        } catch (_: Exception) {
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
    }

    /**
     * 判断事件是否应该被自动处理
     * @return 自动处理结果，null 表示不自动处理（需要弹窗）
     */
    fun shouldAutoHandle(event: GameEvent): AutoHandleResult? {
        val cfg = _config.value
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
    enum class AdminOffice {
        PERSONNEL, STUDENT_AFFAIRS, LOGISTICS, NONE
    }

    fun officeFor(event: GameEvent): AdminOffice {
        if (event !is GameEvent.ChoiceEvent) return AdminOffice.NONE
        val title = event.title
        val message = event.message
        return when {
            title.startsWith("[突发危机]") || title.startsWith("[危机进展]") -> AdminOffice.NONE
            // 人事处：教师相关
            title.contains("加薪") || title.contains("涨薪") || message.contains("请求加薪") -> AdminOffice.PERSONNEL
            title.contains("续约") || title.contains("合同到期") || message.contains("合同即将到期") -> AdminOffice.PERSONNEL
            title.contains("离职") || title.contains("辞职") || message.contains("提出离职") -> AdminOffice.PERSONNEL
            title.contains("教师故事") || title.contains("挖角") -> AdminOffice.PERSONNEL
            // 后勤处：楼相关
            title.contains("设施维修") || title.contains("水管") || title.contains("维修：") -> AdminOffice.LOGISTICS
            // 学工处：学生相关 + 月度校务
            title.contains("校长月度决策") -> AdminOffice.STUDENT_AFFAIRS
            // 学生日常事件（早恋/网络沉迷/欺凌/作弊/心理等）全部归学工处，
            // 老版本这里没有规则，导致"安排了老师也选了自动同意，这些弹窗照旧刷屏"
            title.startsWith("学生事件") || title.contains("学生") -> AdminOffice.STUDENT_AFFAIRS
            title.contains("食堂") || title.contains("宿舍") ||
                title.contains("餐位") || title.contains("床位") -> AdminOffice.STUDENT_AFFAIRS
            title.contains("心理") || title.contains("健康") -> AdminOffice.STUDENT_AFFAIRS
            title.contains("活动") || (title.contains("审批") && message.contains("活动")) -> AdminOffice.STUDENT_AFFAIRS
            title.contains("社团") || message.contains("社团申请") -> AdminOffice.STUDENT_AFFAIRS
            // 收尾：仍未归类的选择事件由学工处兜底（见 fallbackOffice），
            // 保证"安排了干部 + 自动同意"能覆盖全部日常事件，而不是只有被关键词命中的那些。
            else -> AdminOffice.NONE
        }
    }

    /**
     * 未归类选择事件的兜底科室：优先学工处，其次任意一位在职干部。
     *
     * 老版本 getChoiceAutoAction 里 "office == NONE → return null" 会让所有没被关键词命中的
     * 事件强制弹窗，玩家的自动审批配置形同虚设。这里让"有人任职"就能兜住杂项事件，
     * 具体处理方式仍由「其他事件」策略决定（默认手动，不会误批）。
     */
    private fun fallbackOffice(cfg: AutoHandleConfig): AdminOffice = when {
        cfg.studentAffairsOfficerId.isNotBlank() -> AdminOffice.STUDENT_AFFAIRS
        cfg.logisticsOfficerId.isNotBlank() -> AdminOffice.LOGISTICS
        cfg.personnelOfficerId.isNotBlank() -> AdminOffice.PERSONNEL
        else -> AdminOffice.NONE
    }

    private fun officerIdFor(office: AdminOffice, cfg: AutoHandleConfig): String = when (office) {
        AdminOffice.PERSONNEL -> cfg.personnelOfficerId
        AdminOffice.STUDENT_AFFAIRS -> cfg.studentAffairsOfficerId
        AdminOffice.LOGISTICS -> cfg.logisticsOfficerId
        AdminOffice.NONE -> ""
    }

    /**
     * 解析事件由谁代批。
     * - 已归类到具体科室的：必须该科室有在职干部，否则继续弹窗（空缺的科室不代批，保持职责到人）。
     * - 未归类的事件：退回到任意一位在职干部兜底，避免新加的事件类型没人管、只能刷屏。
     */
    private fun resolveOffice(event: GameEvent.ChoiceEvent, cfg: AutoHandleConfig): AdminOffice {
        val categorized = officeFor(event)
        if (categorized != AdminOffice.NONE) {
            return if (officerIdFor(categorized, cfg).isNotBlank()) categorized else AdminOffice.NONE
        }
        return fallbackOffice(cfg)
    }

    private fun getChoiceAutoAction(event: GameEvent.ChoiceEvent, cfg: AutoHandleConfig): AutoHandleResult? {
        val office = resolveOffice(event, cfg)
        if (office == AdminOffice.NONE) return null
        val strategy = categorizeChoiceEvent(event, cfg)
        return when (strategy) {
            AutoStrategy.MANUAL -> null
            AutoStrategy.AUTO_APPROVE -> {
                // 自动批准：选第一个选项（通常是同意/批准）
                if (event.choices.isNotEmpty()) {
                    // 活动审批按「默认规模」选择（简朴/标准/隆重/盛大），其它事件仍选第一项
                    val idx = if (event.title.contains("活动审批")) {
                        cfg.activityDefaultScale.coerceIn(0, (event.choices.size - 2).coerceAtLeast(0))
                    } else 0
                    AutoHandleResult.AutoChoice(choiceIndex = idx)
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

        // 教师故事 / 被挖角
        if (title.contains("教师故事") || title.contains("挖角")) {
            return cfg.teacherStoryStrategy
        }

        // 设施维修优先于学生投诉（"设施维修：宿舍楼"里也带"宿舍"字样）
        if (title.contains("设施维修") || title.contains("水管") || title.contains("维修：")) {
            return cfg.logisticsRepairStrategy
        }

        // 校长月度决策
        if (title.contains("校长月度决策")) {
            return cfg.monthlyDecisionStrategy
        }

        // 学生日常事件（早恋、网络沉迷、校园欺凌、考试作弊、心理危机）
        // 只按"学生事件："前缀判定，避免把"学生食堂投诉"这类误判到日常事件分类
        if (title.startsWith("学生事件")) {
            return cfg.studentDailyStrategy
        }

        // 学生吃住投诉（食堂、宿舍、健康、心理）
        if (title.contains("食堂") || title.contains("宿舍") ||
            title.contains("餐位") || title.contains("床位") ||
            title.contains("心理") || title.contains("健康")
        ) {
            return cfg.studentWelfareStrategy
        }

        // 活动审批
        if (title.contains("活动") || title.contains("审批") && message.contains("活动")) {
            return cfg.activityApprovalStrategy
        }

        // 社团审批
        if (title.contains("社团") || message.contains("社团申请")) {
            return cfg.clubApprovalStrategy
        }
        return cfg.otherChoiceStrategy
    }

    fun officerNameHint(office: AdminOffice): String = when (office) {
        AdminOffice.PERSONNEL -> "人事处"
        AdminOffice.STUDENT_AFFAIRS -> "学工处"
        AdminOffice.LOGISTICS -> "后勤处"
        AdminOffice.NONE -> ""
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
