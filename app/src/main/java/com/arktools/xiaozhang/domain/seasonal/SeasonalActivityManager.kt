package com.arktools.xiaozhang.domain.seasonal

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 季节活动系统（审批制）
 * 活动到达触发月时生成审批请求，校长需要选择规模并签字批准后才会开始筹备。
 * 流程：通知 → 校长选择规模 → 签字批准 → 筹备 → 举办 → 结算
 */

enum class Season(val displayName: String) {
    SPRING("春季"),
    SUMMER("夏季"),
    AUTUMN("秋季"),
    WINTER("冬季")
}

enum class ActivityType(
    val displayName: String,
    val description: String,
    val season: Season,
    val triggerMonth: Int,       // 触发月份(1-12)
    val preparationDays: Int,    // 准备天数
    val durationDays: Int,       // 持续天数
    val baseCost: Long,          // 基础费用（元）
    val baseReputationGain: Int, // 基础声誉收益
    val satisfactionBoost: Float // 学生满意度加成
) {
    // 春季活动
    // baseCost 单位：元（GameEngine 中 /10000 转万元）
    OPENING_CEREMONY(
        "开学典礼", "新学期开学仪式，欢迎新生入学",
        Season.SPRING, 2, 5, 1, 20000L, 10, 3f
    ),
    SPRING_OUTING(
        "春游活动", "师生集体春游，增强凝聚力",
        Season.SPRING, 3, 7, 2, 35000L, 8, 5f
    ),
    SCIENCE_FAIR(
        "科技展览", "学生科技作品展示与评比",
        Season.SPRING, 4, 14, 3, 60000L, 20, 4f
    ),

    // 夏季活动
    SPORTS_DAY(
        "运动会", "全校运动会，体育竞技盛典",
        Season.SUMMER, 5, 10, 2, 80000L, 25, 6f
    ),
    SUMMER_CAMP(
        "夏令营", "暑期特色营地活动",
        Season.SUMMER, 7, 7, 5, 100000L, 15, 8f
    ),
    ART_EXHIBITION(
        "艺术展", "学生艺术作品年度展览",
        Season.SUMMER, 6, 10, 3, 50000L, 18, 4f
    ),

    // 秋季活动
    CULTURAL_FESTIVAL(
        "文化节", "多元文化展示与交流盛会",
        Season.AUTUMN, 9, 14, 3, 120000L, 35, 7f
    ),
    DEBATE_TOURNAMENT(
        "辩论赛", "校际辩论锦标赛",
        Season.AUTUMN, 10, 10, 2, 40000L, 22, 3f
    ),
    PARENT_DAY(
        "家长开放日", "邀请家长参观校园",
        Season.AUTUMN, 11, 5, 1, 30000L, 15, 2f
    ),

    // 冬季活动
    NEW_YEAR_GALA(
        "新年晚会", "迎新年文艺汇演",
        Season.WINTER, 12, 14, 1, 70000L, 20, 6f
    ),
    GRADUATION_CEREMONY(
        "毕业典礼", "隆重的毕业生送别仪式",
        Season.SUMMER, 6, 7, 1, 60000L, 30, 5f
    ),
    CHARITY_EVENT(
        "慈善义卖", "师生慈善募捐活动",
        Season.WINTER, 12, 7, 2, 25000L, 12, 4f
    )
}

enum class ActivityPhase(val displayName: String) {
    /** 等待校长审批（新增：需校长签字批准） */
    PENDING_APPROVAL("待审批"),
    /** 已批准，筹备中 */
    PREPARING("筹备中"),
    /** 活动进行中 */
    ACTIVE("进行中"),
    /** 活动已完成 */
    COMPLETED("已结束"),
    /** 校长拒绝举办 */
    REJECTED("已驳回"),
    /** 审批超时未处理（自动过期） */
    EXPIRED("已过期")
}

enum class ActivityScale(
    val displayName: String,
    val costMultiplier: Float,
    val rewardMultiplier: Float
) {
    MINIMAL("简朴", 0.5f, 0.5f),
    STANDARD("标准", 1.0f, 1.0f),
    GRAND("隆重", 1.8f, 1.5f),
    SPECTACULAR("盛大", 3.0f, 2.2f)
}

data class SeasonalActivity(
    val id: String,
    val type: ActivityType,
    val year: Int,
    val phase: ActivityPhase = ActivityPhase.PENDING_APPROVAL,
    val scale: ActivityScale = ActivityScale.STANDARD,
    val preparationProgress: Int = 0,   // 准备进度(天)
    val durationProgress: Int = 0,      // 举办进度(天)
    val actualCost: Long = 0L,
    val reputationGained: Int = 0,
    val satisfactionGained: Float = 0f,
    val specialOutcome: String? = null,
    /** 审批等待天数（超过15天自动过期） */
    val approvalWaitDays: Int = 0
)

data class SeasonalActivityState(
    val currentSeason: Season = Season.SPRING,
    val activities: List<SeasonalActivity> = emptyList(),
    val completedThisYear: List<SeasonalActivity> = emptyList(),
    val yearlyStats: YearlyActivityStats = YearlyActivityStats()
)

data class YearlyActivityStats(
    val totalActivities: Int = 0,
    val totalSpent: Long = 0L,
    val totalReputationGained: Int = 0,
    val totalSatisfactionBoosted: Float = 0f,
    val bestActivity: String? = null
)

data class ActivityResult(
    val activity: SeasonalActivity,
    val reputationGain: Int,
    val satisfactionBoost: Float,
    val cashSpent: Long,
    val specialMessage: String?
)

/** advanceDay 的复合返回值 */
data class DayAdvanceResult(
    /** 当天完成的活动（产生结算奖励） */
    val completedResults: List<ActivityResult>,
    /** 当天刚进入 ACTIVE 阶段的活动（触发迷你游戏） */
    val newlyActiveActivities: List<SeasonalActivity>
)

@Singleton
class SeasonalActivityManager @Inject constructor() {

    companion object {
        /** 审批超时天数：超过此天数未处理则自动过期 */
        const val APPROVAL_TIMEOUT_DAYS = 15
    }

    private val _state = MutableStateFlow(SeasonalActivityState())
    val state: StateFlow<SeasonalActivityState> = _state.asStateFlow()

    private val random = java.util.Random()
    private var lastGeneratedYear = -1

    /** 小游戏表现分数缓存：activityId → performanceScore (0.0~1.0) */
    private val miniGameScores = mutableMapOf<String, Float>()

    /** 月度活动费用累计（元），供财务报表使用，每月结算后重置 */
    private var _monthlyExpenses: Long = 0L

    /** 获取并重置当月活动费用累计（元） */
    fun consumeMonthlyExpenses(): Long {
        val expenses = _monthlyExpenses
        _monthlyExpenses = 0L
        return expenses
    }

    /**
     * 记录小游戏表现分数（由 MiniGameViewModel 在小游戏完成时调用）
     * @param activityId 对应的季节活动 ID
     * @param score 表现分数 0.0~1.0
     */
    fun applyMiniGamePerformance(activityId: String, score: Float) {
        miniGameScores[activityId] = score.coerceIn(0f, 1f)
    }

    fun reset() {
        _state.value = SeasonalActivityState()
        lastGeneratedYear = -1
        miniGameScores.clear()
        _monthlyExpenses = 0L
    }

    fun getSeason(month: Int): Season {
        return when (month) {
            3, 4, 5 -> Season.SPRING
            6, 7, 8 -> Season.SUMMER
            9, 10, 11 -> Season.AUTUMN
            else -> Season.WINTER
        }
    }

    /**
     * 每月初调用，生成当月待审批的季节活动
     * 返回新生成的待审批活动列表（GameEngine据此发送审批通知给校长）
     */
    fun onMonthStart(year: Int, month: Int, schoolReputation: Long): List<SeasonalActivity> {
        val season = getSeason(month)
        _state.update { it.copy(currentSeason = season) }

        // 新年度重置年度统计
        if (year != lastGeneratedYear) {
            lastGeneratedYear = year
            _state.update { it.copy(
                completedThisYear = emptyList(),
                yearlyStats = YearlyActivityStats()
            ) }
        }

        // 查找本月触发的活动
        val monthActivities = ActivityType.entries.filter { it.triggerMonth == month }
        val newActivities = mutableListOf<SeasonalActivity>()

        for (type in monthActivities) {
            // 检查是否已存在（含已驳回/过期的，同年同类型只生成一次）
            val exists = _state.value.activities.any {
                it.type == type && it.year == year
            } || _state.value.completedThisYear.any {
                it.type == type && it.year == year
            }
            if (!exists) {
                val activity = SeasonalActivity(
                    id = "${type.name}_${year}_$month",
                    type = type,
                    year = year,
                    phase = ActivityPhase.PENDING_APPROVAL,
                    scale = suggestScale(schoolReputation)
                )
                newActivities.add(activity)
            }
        }

        if (newActivities.isNotEmpty()) {
            _state.update { state ->
                state.copy(activities = state.activities + newActivities)
            }
        }

        return newActivities
    }

    /**
     * 立即举办：跳过审批与漫长筹备，次日正式开幕（触发对应小游戏）。
     * @return (是否成功, 提示信息)
     */
    fun hostNow(
        type: ActivityType,
        year: Int,
        month: Int,
        scale: ActivityScale = ActivityScale.STANDARD
    ): kotlin.Pair<Boolean, String> {
        val visible = _state.value.activities.any {
            it.type == type &&
                it.phase in listOf(
                    ActivityPhase.PENDING_APPROVAL,
                    ActivityPhase.PREPARING,
                    ActivityPhase.ACTIVE
                )
        }
        if (visible) return kotlin.Pair(false, "该活动已在流程中，不能重复举办")
        val activity = SeasonalActivity(
            id = type.name + "_" + year + "_" + month + "_now",
            type = type,
            year = year,
            phase = ActivityPhase.PREPARING,
            scale = scale,
            preparationProgress = (type.preparationDays - 1).coerceAtLeast(0),
            actualCost = (type.baseCost * scale.costMultiplier).toLong()
        )
        _state.update { it.copy(activities = it.activities + activity) }
        return kotlin.Pair(true, "已开始筹备" + type.displayName + "，明天正式开幕！")
    }

    /** hostNow 的回滚：移除刚插入的筹备活动（扣款失败时用） */
    fun cancelHostNow(type: ActivityType) {
        _state.update { state ->
            state.copy(activities = state.activities.filterNot {
                it.type == type && it.id.endsWith("_now") && it.phase == ActivityPhase.PREPARING
            })
        }
    }

    /**
     * 校长批准活动（需签字）
     * @param activityId 活动ID
     * @param approvedScale 批准的规模（校长可选择不同于建议的规模）
     * @return true=批准成功, false=活动不存在或状态不对
     */
    fun approveActivity(activityId: String, approvedScale: ActivityScale): Boolean {
        _state.update { state ->
            state.copy(activities = state.activities.map {
                if (it.id == activityId && it.phase == ActivityPhase.PENDING_APPROVAL) {
                    it.copy(
                        phase = ActivityPhase.PREPARING,
                        scale = approvedScale,
                        preparationProgress = 0
                    )
                } else it
            })
        }
        // 在 update 完成后检查结果（避免 CAS 重试导致的变量状态错误）
        return _state.value.activities.any { it.id == activityId && it.phase == ActivityPhase.PREPARING }
    }

    /**
     * 校长驳回活动
     * 声誉小幅下降（教师/学生失望）
     * @return 声誉惩罚值（正数）
     */
    fun rejectActivity(activityId: String): Int {
        // 先读取活动信息计算惩罚值（在 update 外部，避免 CAS 重试问题）
        val activity = _state.value.activities.find {
            it.id == activityId && it.phase == ActivityPhase.PENDING_APPROVAL
        } ?: return 0
        val penalty = (activity.type.baseReputationGain * 0.3f).toInt().coerceAtLeast(2)

        _state.update { state ->
            state.copy(activities = state.activities.map {
                if (it.id == activityId && it.phase == ActivityPhase.PENDING_APPROVAL) {
                    it.copy(phase = ActivityPhase.REJECTED)
                } else it
            })
        }
        return penalty
    }

    /**
     * 每日推进活动进度
     * 只推进已批准（PREPARING/ACTIVE）的活动
     * 待审批活动增加等待天数，超时自动过期
     * @return DayAdvanceResult 包含完成结果和新进入ACTIVE的活动
     */
    fun advanceDay(year: Int, month: Int, day: Int): DayAdvanceResult {
        val results = mutableListOf<ActivityResult>()
        val newlyActive = mutableListOf<SeasonalActivity>()

        _state.update { state ->
            val updatedActivities = state.activities.map { activity ->
                if (activity.year != year) return@map activity

                when (activity.phase) {
                    ActivityPhase.PENDING_APPROVAL -> {
                        // 等待审批：增加等待天数，超时过期
                        val newWaitDays = activity.approvalWaitDays + 1
                        if (newWaitDays > APPROVAL_TIMEOUT_DAYS) {
                            activity.copy(phase = ActivityPhase.EXPIRED, approvalWaitDays = newWaitDays)
                        } else {
                            activity.copy(approvalWaitDays = newWaitDays)
                        }
                    }
                    ActivityPhase.PREPARING -> {
                        val newProgress = activity.preparationProgress + 1
                        if (newProgress >= activity.type.preparationDays) {
                            val activeActivity = activity.copy(
                                phase = ActivityPhase.ACTIVE,
                                preparationProgress = newProgress,
                                durationProgress = 0
                            )
                            newlyActive.add(activeActivity)
                            activeActivity
                        } else {
                            activity.copy(preparationProgress = newProgress)
                        }
                    }
                    ActivityPhase.ACTIVE -> {
                        val newDuration = activity.durationProgress + 1
                        if (newDuration >= activity.type.durationDays) {
                            // 活动结束，计算结果
                            val result = calculateResult(activity)
                            results.add(result)
                            _monthlyExpenses += result.cashSpent
                            activity.copy(
                                phase = ActivityPhase.COMPLETED,
                                durationProgress = newDuration,
                                actualCost = result.cashSpent,
                                reputationGained = result.reputationGain,
                                satisfactionGained = result.satisfactionBoost,
                                specialOutcome = result.specialMessage
                            )
                        } else {
                            activity.copy(durationProgress = newDuration)
                        }
                    }
                    else -> activity
                }
            }

            // 将完成的活动移到已完成列表，清理过期/驳回的
            val justCompleted = updatedActivities.filter {
                it.phase == ActivityPhase.COMPLETED && it !in state.completedThisYear
            }
            val stillActive = updatedActivities.filter {
                it.phase !in listOf(ActivityPhase.COMPLETED, ActivityPhase.REJECTED, ActivityPhase.EXPIRED)
            }

            val newCompleted = state.completedThisYear + justCompleted
            val newStats = calculateYearlyStats(newCompleted)

            state.copy(
                activities = stillActive,
                completedThisYear = newCompleted,
                yearlyStats = newStats
            )
        }

        return DayAdvanceResult(
            completedResults = results,
            newlyActiveActivities = newlyActive
        )
    }

    /**
     * 获取待审批的活动列表
     */
    fun getPendingApprovalActivities(): List<SeasonalActivity> {
        return _state.value.activities.filter { it.phase == ActivityPhase.PENDING_APPROVAL }
    }

    /**
     * 获取当前进行中（筹备+举办）的活动
     */
    fun getActiveActivities(): List<SeasonalActivity> {
        return _state.value.activities.filter {
            it.phase in listOf(ActivityPhase.PREPARING, ActivityPhase.ACTIVE)
        }
    }

    /**
     * 获取所有可见活动（待审批+筹备+进行中）
     */
    fun getAllVisibleActivities(): List<SeasonalActivity> {
        return _state.value.activities.filter {
            it.phase in listOf(ActivityPhase.PENDING_APPROVAL, ActivityPhase.PREPARING, ActivityPhase.ACTIVE)
        }
    }

    private fun calculateResult(activity: SeasonalActivity): ActivityResult {
        val costMultiplier = activity.scale.costMultiplier
        val rewardMultiplier = activity.scale.rewardMultiplier

        val baseCost = activity.type.baseCost
        val actualCost = (baseCost * costMultiplier).toLong()

        // 小游戏表现乘数：0.0~1.0 映射为 0.6~1.4（表现差则减益，表现好则加益）
        // 没有小游戏分数的活动默认1.0（不受影响）
        val miniGameScore = miniGameScores.remove(activity.id)
        val miniGameMultiplier = if (miniGameScore != null) {
            0.6f + miniGameScore * 0.8f  // score=0 → 0.6x, score=0.5 → 1.0x, score=1.0 → 1.4x
        } else {
            1.0f
        }

        val baseRep = activity.type.baseReputationGain
        // 随机波动±20%
        val repVariance = 1.0f + (random.nextFloat() * 0.4f - 0.2f)
        val actualRep = (baseRep * rewardMultiplier * repVariance * miniGameMultiplier).toInt()

        val baseSatisfaction = activity.type.satisfactionBoost
        val actualSatisfaction = baseSatisfaction * rewardMultiplier * miniGameMultiplier

        // 特殊结果(10%概率出彩，5%概率出问题)
        val specialRoll = random.nextFloat()
        val specialMessage = when {
            specialRoll < 0.10f -> getPositiveOutcome(activity.type)
            specialRoll > 0.95f -> getNegativeOutcome(activity.type)
            else -> null
        }

        // 特殊结果对声誉的额外影响
        val specialRepBonus = when {
            specialRoll < 0.10f -> (actualRep * 0.5f).toInt()
            specialRoll > 0.95f -> -(actualRep * 0.3f).toInt()
            else -> 0
        }

        return ActivityResult(
            activity = activity,
            reputationGain = (actualRep + specialRepBonus).coerceAtLeast(0),
            satisfactionBoost = actualSatisfaction,
            cashSpent = actualCost,
            specialMessage = specialMessage
        )
    }

    private fun getPositiveOutcome(type: ActivityType): String {
        return when (type) {
            ActivityType.OPENING_CEREMONY -> "开学典礼获得媒体报道，知名度大增！"
            ActivityType.SPRING_OUTING -> "春游活动被评为最佳校外实践，家长好评如潮！"
            ActivityType.SCIENCE_FAIR -> "学生作品获得省级科技创新奖！"
            ActivityType.SPORTS_DAY -> "校运动会打破多项纪录，体育特色彰显！"
            ActivityType.SUMMER_CAMP -> "夏令营学员创意作品登上热搜！"
            ActivityType.ART_EXHIBITION -> "艺术展吸引多家画廊关注，作品被收藏！"
            ActivityType.CULTURAL_FESTIVAL -> "文化节视频全网传播，学校声名远扬！"
            ActivityType.DEBATE_TOURNAMENT -> "辩论队勇夺冠军，逻辑思维教育获认可！"
            ActivityType.PARENT_DAY -> "家长满意度极高，口碑推荐大幅增加！"
            ActivityType.NEW_YEAR_GALA -> "新年晚会节目登上电视台，全市瞩目！"
            ActivityType.GRADUATION_CEREMONY -> "毕业典礼感人至深，校友捐赠意愿提升！"
            ActivityType.CHARITY_EVENT -> "慈善活动募集超预期善款，社会责任感获赞！"
        }
    }

    private fun getNegativeOutcome(type: ActivityType): String {
        return when (type) {
            ActivityType.OPENING_CEREMONY -> "典礼当天突降暴雨，部分环节被迫取消。"
            ActivityType.SPRING_OUTING -> "春游途中有学生轻微受伤，部分家长有意见。"
            ActivityType.SCIENCE_FAIR -> "评审标准引发争议，部分学生不满。"
            ActivityType.SPORTS_DAY -> "运动会出现轻微安全事故，需加强管理。"
            ActivityType.SUMMER_CAMP -> "夏令营部分活动因天气取消，体验打折。"
            ActivityType.ART_EXHIBITION -> "展览布置出现失误，部分作品受损。"
            ActivityType.CULTURAL_FESTIVAL -> "文化节预算超支，财务压力增大。"
            ActivityType.DEBATE_TOURNAMENT -> "辩论赛裁判判罚引争议，赛后出现投诉。"
            ActivityType.PARENT_DAY -> "部分设施来不及修缮，家长提出改进意见。"
            ActivityType.NEW_YEAR_GALA -> "晚会音响设备故障，演出效果受影响。"
            ActivityType.GRADUATION_CEREMONY -> "典礼组织略显混乱，时间严重超出预定。"
            ActivityType.CHARITY_EVENT -> "义卖物资准备不足，部分摊位冷清。"
        }
    }

    private fun suggestScale(reputation: Long): ActivityScale {
        return when {
            reputation > 5000 -> ActivityScale.GRAND
            reputation > 2000 -> ActivityScale.STANDARD
            else -> ActivityScale.MINIMAL
        }
    }

    private fun calculateYearlyStats(completed: List<SeasonalActivity>): YearlyActivityStats {
        if (completed.isEmpty()) return YearlyActivityStats()
        val bestActivity = completed.maxByOrNull { it.reputationGained }
        return YearlyActivityStats(
            totalActivities = completed.size,
            totalSpent = completed.sumOf { it.actualCost },
            totalReputationGained = completed.sumOf { it.reputationGained },
            totalSatisfactionBoosted = completed.sumOf { it.satisfactionGained.toDouble() }.toFloat(),
            bestActivity = bestActivity?.type?.displayName
        )
    }

    fun toJson(): String {
        return try {
            val state = _state.value
            fun mapActivity(a: SeasonalActivity) = SeasonalActivityPersist(
                id = a.id,
                type = a.type.name,
                year = a.year,
                phase = a.phase.name,
                scale = a.scale.name,
                preparationProgress = a.preparationProgress,
                durationProgress = a.durationProgress,
                actualCost = a.actualCost,
                reputationGained = a.reputationGained,
                satisfactionGained = a.satisfactionGained,
                specialOutcome = a.specialOutcome,
                approvalWaitDays = a.approvalWaitDays
            )
            val data = SeasonalPersistData(
                currentSeason = state.currentSeason.name,
                activities = state.activities.map { mapActivity(it) },
                completedThisYear = state.completedThisYear.map { mapActivity(it) },
                yearlyTotalActivities = state.yearlyStats.totalActivities,
                yearlyTotalSpent = state.yearlyStats.totalSpent,
                yearlyTotalReputationGained = state.yearlyStats.totalReputationGained,
                yearlyTotalSatisfactionBoosted = state.yearlyStats.totalSatisfactionBoosted,
                yearlyBestActivity = state.yearlyStats.bestActivity,
                lastGeneratedYear = lastGeneratedYear,
                monthlyExpenses = _monthlyExpenses,
                miniGameScores = miniGameScores.toMap()
            )
            Json.encodeToString(data)
        } catch (_: Exception) { "" }
    }

    fun restoreFromJson(json: String) {
        if (json.isBlank()) return
        try {
            val data = Json { ignoreUnknownKeys = true }.decodeFromString<SeasonalPersistData>(json)
            val season = try { Season.valueOf(data.currentSeason) } catch (_: Exception) { Season.SPRING }
            fun restoreActivity(ap: SeasonalActivityPersist): SeasonalActivity? {
                val type = try { ActivityType.valueOf(ap.type) } catch (_: Exception) { return null }
                val phase = try { ActivityPhase.valueOf(ap.phase) } catch (_: Exception) { ActivityPhase.PENDING_APPROVAL }
                val scale = try { ActivityScale.valueOf(ap.scale) } catch (_: Exception) { ActivityScale.STANDARD }
                return SeasonalActivity(
                    id = ap.id,
                    type = type,
                    year = ap.year,
                    phase = phase,
                    scale = scale,
                    preparationProgress = ap.preparationProgress,
                    durationProgress = ap.durationProgress,
                    actualCost = ap.actualCost,
                    reputationGained = ap.reputationGained,
                    satisfactionGained = ap.satisfactionGained,
                    specialOutcome = ap.specialOutcome,
                    approvalWaitDays = ap.approvalWaitDays
                )
            }
            val activities = data.activities.mapNotNull { restoreActivity(it) }
            val completed = data.completedThisYear.mapNotNull { restoreActivity(it) }
            lastGeneratedYear = data.lastGeneratedYear
            _monthlyExpenses = data.monthlyExpenses
            miniGameScores.clear()
            miniGameScores.putAll(data.miniGameScores.mapValues { it.value.coerceIn(0f, 1f) })
            _state.value = SeasonalActivityState(
                currentSeason = season,
                activities = activities,
                completedThisYear = completed,
                yearlyStats = YearlyActivityStats(
                    totalActivities = data.yearlyTotalActivities,
                    totalSpent = data.yearlyTotalSpent,
                    totalReputationGained = data.yearlyTotalReputationGained,
                    totalSatisfactionBoosted = data.yearlyTotalSatisfactionBoosted,
                    bestActivity = data.yearlyBestActivity
                )
            )
        } catch (e: Exception) {
            throw IllegalArgumentException("SeasonalActivityManager.restoreFromJson failed", e)
        }
    }
}

@Serializable
data class SeasonalPersistData(
    val currentSeason: String = "SPRING",
    val activities: List<SeasonalActivityPersist> = emptyList(),
    val completedThisYear: List<SeasonalActivityPersist> = emptyList(),
    val yearlyTotalActivities: Int = 0,
    val yearlyTotalSpent: Long = 0L,
    val yearlyTotalReputationGained: Int = 0,
    val yearlyTotalSatisfactionBoosted: Float = 0f,
    val yearlyBestActivity: String? = null,
    val lastGeneratedYear: Int = -1,
    val monthlyExpenses: Long = 0L,
    val miniGameScores: Map<String, Float> = emptyMap()
)

@Serializable
data class SeasonalActivityPersist(
    val id: String,
    val type: String,
    val year: Int,
    val phase: String,
    val scale: String = "STANDARD",
    val preparationProgress: Int = 0,
    val durationProgress: Int = 0,
    val actualCost: Long = 0L,
    val reputationGained: Int = 0,
    val satisfactionGained: Float = 0f,
    val specialOutcome: String? = null,
    val approvalWaitDays: Int = 0
)
