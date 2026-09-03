package com.arktools.xiao.domain.competitor

import com.arktools.xiao.domain.model.School
import com.arktools.xiao.domain.model.SchoolTier
import com.arktools.xiao.domain.model.schoolTier
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.random.Random

/**
 * 竞争引擎 —— 驱动所有 AI 对手的行为和成长
 *
 * 每月更新一次: 成长、策略调整、对玩家的反应
 * 玩家越强 → 对手反应越激烈 (弹性难度)
 */
@Singleton
class CompetitorEngine @Inject constructor() {

    private val competitors = CompetitorRegistry.createCompetitors().toMutableList()

    private val _competitorState = MutableStateFlow<List<Competitor>>(competitors.toList())
    val competitorState: StateFlow<List<Competitor>> = _competitorState.asStateFlow()

    private val _competitorEvents = MutableSharedFlow<CompetitorEvent>()
    val competitorEvents: SharedFlow<CompetitorEvent> = _competitorEvents.asSharedFlow()

    /**
     * 每月更新所有AI对手 (在GameEngine月结算时调用)
     * @param school 玩家学校状态
     * @param gameYear 当前游戏年份
     * @return 本月产生的竞争事件
     */
    suspend fun monthlyUpdate(school: School, gameYear: Int): List<CompetitorEvent> {
        val events = mutableListOf<CompetitorEvent>()
        val yearsSinceStart = (gameYear - school.foundedYear).coerceAtLeast(0)

        competitors.filter { it.isActive }.forEach { competitor ->
            // 1. 自然成长
            applyNaturalGrowth(competitor, yearsSinceStart)

            // 2. 策略行为
            applyStrategyBehavior(competitor, yearsSinceStart)

            // 3. 对玩家的反应
            val event = reactToPlayer(competitor, school)
            if (event != null) events.add(event)

            // 4. 生存检测
            checkSurvival(competitor, gameYear)

            // 5. 冷却递减
            if (competitor.specialEventCooldown > 0) competitor.specialEventCooldown--
        }

        _competitorState.value = competitors.toList()
        events.forEach { _competitorEvents.emit(it) }
        return events
    }

    /**
     * 自然成长 - 基于基础成长率和年份
     */
    private fun applyNaturalGrowth(competitor: Competitor, yearsSinceStart: Int) {
        val growthMultiplier = competitor.baseGrowthRate * competitor.morale
        val yearBonus = 1.0f + yearsSinceStart * 0.02f  // 年份越长成长越快

        // 声誉成长
        val repGrowth = (50 * growthMultiplier * yearBonus).toLong()
        competitor.reputation += repGrowth + Random.nextLong(0, repGrowth / 2 + 1)

        // 资金成长
        val cashGrowth = 20.0 * growthMultiplier * yearBonus
        competitor.cash += cashGrowth + Random.nextDouble(0.0, cashGrowth / 2)

        // 学生数量增长
        val studentGrowth = (5 * growthMultiplier * yearBonus).toInt()
        competitor.studentCount += studentGrowth + Random.nextInt(0, studentGrowth + 1)

        // 星级缓慢提升
        if (competitor.starRating < 5.0f && Random.nextFloat() < 0.1f) {
            competitor.starRating = (competitor.starRating + 0.1f).coerceAtMost(5.0f)
        }
    }

    /**
     * 策略行为 - 根据竞争策略做对应的特化成长
     */
    private fun applyStrategyBehavior(competitor: Competitor, yearsSinceStart: Int) {
        when (competitor.strategy) {
            CompetitorStrategy.AGGRESSIVE -> {
                // 激进扩张: 更多学生和课程，但声誉增长更不稳定
                competitor.studentCount += Random.nextInt(3, 10)
                if (Random.nextFloat() < 0.15f) competitor.courseCount++
                if (Random.nextFloat() < 0.1f) {
                    competitor.reputation -= Random.nextLong(50, 200)  // 偶尔口碑受损
                }
            }
            CompetitorStrategy.STEADY -> {
                // 稳健: 各项均衡增长
                if (Random.nextFloat() < 0.1f) competitor.courseCount++
                if (Random.nextFloat() < 0.08f) competitor.teacherCount++
            }
            CompetitorStrategy.QUALITY -> {
                // 精品: 声誉快但学生少
                competitor.reputation += Random.nextLong(20, 80)
                if (Random.nextFloat() < 0.15f) {
                    competitor.starRating = (competitor.starRating + 0.05f).coerceAtMost(5.0f)
                }
            }
            CompetitorStrategy.BUDGET -> {
                // 低价: 学生多但声誉低
                competitor.studentCount += Random.nextInt(5, 15)
                competitor.reputation -= Random.nextLong(0, 30)
            }
            CompetitorStrategy.INNOVATION -> {
                // 创新: 前期慢后期快
                val innovationBonus = (1.0f + yearsSinceStart * 0.05f).coerceAtMost(2.5f)
                competitor.reputation += (30 * innovationBonus).toLong()
                if (yearsSinceStart > 3 && Random.nextFloat() < 0.2f) {
                    competitor.studentCount += Random.nextInt(10, 30)
                }
            }
        }

        // 校区升级
        val upgradeCash = getUpgradeCost(competitor.campusLevel)
        if (competitor.cash >= upgradeCash && competitor.campusLevel < 10 && Random.nextFloat() < 0.05f) {
            competitor.campusLevel++
            competitor.cash -= upgradeCash
            competitor.teacherCount += 2
        }
    }

    /**
     * 对玩家的反应 - 产生竞争事件
     */
    private suspend fun reactToPlayer(competitor: Competitor, school: School): CompetitorEvent? {
        if (competitor.specialEventCooldown > 0) return null
        if (competitor.personality == CompetitorPersonality.FRIENDLY && Random.nextFloat() > 0.05f) return null

        val playerAhead = school.reputation > competitor.reputation * 1.2
        val competitorAhead = competitor.reputation > school.reputation * 1.5

        // 敌对/狡猾AI在玩家领先时会发起攻击
        if (playerAhead && competitor.aggressiveness > Random.nextFloat()) {
            competitor.specialEventCooldown = 6  // 6个月冷却

            return when {
                competitor.personality == CompetitorPersonality.HOSTILE && Random.nextFloat() < 0.4f -> {
                    competitor.morale -= 0.1f
                    CompetitorEvent.PriceWar(
                        competitorName = competitor.name,
                        reputationLoss = Random.nextLong(50, 150)
                    )
                }
                competitor.personality == CompetitorPersonality.CUNNING && Random.nextFloat() < 0.3f -> {
                    CompetitorEvent.TalentPoaching(
                        competitorName = competitor.name,
                        targetTeacher = ""  // GameEngine会选择具体教师
                    )
                }
                else -> {
                    CompetitorEvent.MarketExpansion(
                        competitorName = competitor.name,
                        studentLoss = Random.nextInt(3, 10)
                    )
                }
            }
        }

        // 友善AI偶尔提供合作机会
        if (competitor.personality == CompetitorPersonality.FRIENDLY && Random.nextFloat() < 0.02f) {
            competitor.specialEventCooldown = 12
            return CompetitorEvent.Partnership(
                competitorName = competitor.name,
                reputationGain = Random.nextLong(100, 300)
            )
        }

        return null
    }

    /**
     * 生存检测 - AI对手可能因资金或声誉崩溃而退出竞争
     */
    private suspend fun checkSurvival(competitor: Competitor, gameYear: Int) {
        // 声誉过低或资金耗尽 → 退出
        val failureChance = when {
            competitor.reputation <= 0 -> 0.5f
            competitor.cash < -100 -> 0.3f
            competitor.studentCount <= 0 -> 0.2f
            competitor.morale < 0.2f -> 0.1f
            else -> 0f
        }

        if (failureChance > 0 && Random.nextFloat() < failureChance) {
            competitor.isActive = false
            competitor.eliminatedYear = gameYear

            // 对手倒闭 → 玩家获得部分学生和声誉
            val event = CompetitorEvent.CompetitorCollapse(
                competitorName = competitor.name,
                studentGain = (competitor.studentCount * 0.1f).toInt().coerceAtLeast(5),
                reputationGain = (competitor.reputation * 0.05f).toLong().coerceAtLeast(50L)
            )
            _competitorEvents.emit(event)
        }
    }

    /**
     * 获取排行榜数据 (玩家+AI)
     */
    fun getRankings(school: School, playerStudentCount: Int = 0): List<RankingEntry> {
        val entries = mutableListOf<RankingEntry>()

        // 添加玩家
        entries.add(
            RankingEntry(
                name = school.name,
                reputation = school.reputation,
                studentCount = playerStudentCount,
                starRating = school.starRating,
                isPlayer = true,
                isActive = true
            )
        )

        // 添加AI对手 - 只显示同一校舍等级层的对手（±1级范围内）
        // 研究型大学额外可见研究型同侪池，其他层次仅见通用对手池
        val playerLevel = school.campusLevel
        val showResearchPool = school.schoolTier() == SchoolTier.RESEARCH
        competitors.forEach { competitor ->
            val poolVisible = competitor.pool != "RESEARCH" || showResearchPool
            if (poolVisible && competitor.campusLevel in (playerLevel - 1)..(playerLevel + 1)) {
                entries.add(
                    RankingEntry(
                        name = competitor.name,
                        reputation = competitor.reputation,
                        studentCount = competitor.studentCount,
                        starRating = competitor.starRating,
                        isPlayer = false,
                        isActive = competitor.isActive,
                        strategy = competitor.strategy
                    )
                )
            }
        }

        return entries.filter { it.isActive }.sortedByDescending { it.reputation }
    }

    /**
     * 获取玩家当前排名
     */
    fun getPlayerRank(school: School): Int {
        val rankings = getRankings(school)
        return rankings.indexOfFirst { it.isPlayer } + 1
    }

    private fun getUpgradeCost(currentLevel: Int): Double {
        return 200.0 * (1.8.pow(currentLevel.toDouble()))
    }

    /**
     * 重置 (新游戏)
     */
    fun reset() {
        val fresh = CompetitorRegistry.createCompetitors()
        competitors.clear()
        competitors.addAll(fresh)
        _competitorState.value = competitors.toList()
    }

    // ====== 持久化 ======

    private val json = Json { ignoreUnknownKeys = true }

    fun toJson(): String {
        val persist = competitors.map { c ->
            CompetitorPersist(
                id = c.id,
                reputation = c.reputation,
                cash = c.cash,
                studentCount = c.studentCount,
                courseCount = c.courseCount,
                teacherCount = c.teacherCount,
                campusLevel = c.campusLevel,
                starRating = c.starRating,
                morale = c.morale,
                isActive = c.isActive,
                eliminatedYear = c.eliminatedYear,
                specialEventCooldown = c.specialEventCooldown
            )
        }
        return json.encodeToString(persist)
    }

    fun restoreFromJson(jsonStr: String) {
        if (jsonStr.isBlank()) return
        try {
            val persistList = json.decodeFromString<List<CompetitorPersist>>(jsonStr)
            val registry = CompetitorRegistry.createCompetitors().associateBy { it.id }
            competitors.clear()
            // 恢复已保存的竞争对手状态
            persistList.forEach { p ->
                val template = registry[p.id] ?: return@forEach
                val restored = template.copy(
                    reputation = p.reputation,
                    cash = p.cash,
                    studentCount = p.studentCount,
                    courseCount = p.courseCount,
                    teacherCount = p.teacherCount,
                    campusLevel = p.campusLevel,
                    starRating = p.starRating,
                    morale = p.morale,
                    isActive = p.isActive,
                    eliminatedYear = p.eliminatedYear,
                    specialEventCooldown = p.specialEventCooldown
                )
                competitors.add(restored)
            }
            // 如果注册表新增了对手（版本更新），补充初始状态
            registry.values.filter { reg -> competitors.none { it.id == reg.id } }
                .forEach { competitors.add(it) }
            _competitorState.value = competitors.toList()
        } catch (e: Exception) {
            throw IllegalArgumentException("CompetitorEngine.restoreFromJson failed", e)
        }
    }
}

@Serializable
data class CompetitorPersist(
    val id: String,
    val reputation: Long,
    val cash: Double,
    val studentCount: Int,
    val courseCount: Int,
    val teacherCount: Int,
    val campusLevel: Int,
    val starRating: Float,
    val morale: Float,
    val isActive: Boolean,
    val eliminatedYear: Int? = null,
    val specialEventCooldown: Int = 0
)

data class RankingEntry(
    val name: String,
    val reputation: Long,
    val studentCount: Int,
    val starRating: Float,
    val isPlayer: Boolean,
    val isActive: Boolean,
    val strategy: CompetitorStrategy? = null
)
