package com.arktools.xiao.domain.reputation

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
 * 多维声誉系统
 * 将总声誉拆解为：学术、体育、艺术、社会服务、管理五大维度
 * 每个维度独立成长，各有不同影响因子
 */

enum class ReputationDimension(
    val displayName: String,
    val icon: String,
    val description: String,
    val maxLevel: Int = 100
) {
    ACADEMIC("学术声誉", "📚", "教学质量、科研成果、学生学业表现", 100),
    SPORTS("体育声誉", "⚽", "体育设施、运动赛事、体育特长生培养", 100),
    ARTS("艺术声誉", "🎨", "艺术课程、文化活动、艺术成就展示", 100),
    SOCIAL_SERVICE("社会服务声誉", "🤝", "社区贡献、志愿服务、公益项目", 100),
    MANAGEMENT("管理声誉", "🏛️", "学校治理、校园安全、家长满意度", 100)
}

data class DimensionReputation(
    val dimension: ReputationDimension,
    var score: Float = 0f,           // 当前分数(0-1000)
    var level: Int = 1,              // 等级(1-100)
    var momentum: Float = 0f,        // 成长动量(-10到+10)
    var monthlyGrowth: Float = 0f,   // 本月增长量
    var totalContribution: Long = 0, // 对总声誉的累计贡献
    var achievements: MutableList<String> = mutableListOf(),  // 该维度成就
    var milestones: Int = 0          // 里程碑数
)

data class ReputationBreakdown(
    val totalReputation: Long = 0,
    val dimensions: Map<ReputationDimension, DimensionReputation> = emptyMap(),
    val dominantDimension: ReputationDimension? = null,
    val weakestDimension: ReputationDimension? = null,
    val schoolTitle: String = "普通学校",
    val monthlyChange: Long = 0,
    val balanceBonus: Float = 0f,    // 均衡发展奖励倍率
    val recentEvents: List<ReputationEvent> = emptyList()
)

data class ReputationEvent(
    val dimension: ReputationDimension,
    val change: Float,
    val reason: String,
    val timestamp: String = ""
)

data class ReputationMonthlyResult(
    val totalGrowth: Long = 0,
    val dimensionGrowths: Map<ReputationDimension, Float> = emptyMap(),
    val newTitle: String? = null,
    val newMilestones: List<String> = emptyList(),
    val balanceBonus: Float = 0f
)

@Singleton
class ReputationManager @Inject constructor() {

    private val _state = MutableStateFlow(ReputationBreakdown())
    val state: StateFlow<ReputationBreakdown> = _state.asStateFlow()

    fun reset() {
        _state.value = ReputationBreakdown()
    }

    private val dimensionWeights = mapOf(
        ReputationDimension.ACADEMIC to 0.35f,
        ReputationDimension.SPORTS to 0.15f,
        ReputationDimension.ARTS to 0.15f,
        ReputationDimension.SOCIAL_SERVICE to 0.15f,
        ReputationDimension.MANAGEMENT to 0.20f
    )

    // 学校称号阈值: Triple(声誉门槛, 称号, 最低学校等级)
    private val titleThresholds = listOf(
        Triple(50000L, "传奇名校", 6),
        Triple(30000L, "顶级学府", 5),
        Triple(20000L, "一流名校", 4),
        Triple(12000L, "知名学校", 3),
        Triple(8000L, "优秀学校", 3),
        Triple(5000L, "良好学校", 2),
        Triple(3000L, "成长中学校", 2),
        Triple(1500L, "发展中学校", 1),
        Triple(500L, "新兴学校", 1),
        Triple(0L, "普通学校", 1)
    )

    init {
        val initialDimensions = ReputationDimension.entries.associateWith {
            DimensionReputation(dimension = it, score = 100f, level = 1)
        }
        _state.update { it.copy(dimensions = initialDimensions) }
    }

    /**
     * 为特定维度添加声誉值
     */
    fun addDimensionReputation(dimension: ReputationDimension, amount: Float, reason: String) {
        _state.update { state ->
            val dims = state.dimensions.toMutableMap()
            val dim = dims[dimension]?.copy() ?: DimensionReputation(dimension = dimension)

            dim.score = (dim.score + amount).coerceAtLeast(0f)
            dim.monthlyGrowth += amount
            dim.momentum = (dim.momentum + amount * 0.1f).coerceIn(-10f, 10f)

            // 更新等级
            val newLevel = calculateLevel(dim.score)
            dim.level = newLevel

            dims[dimension] = dim

            val event = ReputationEvent(dimension, amount, reason)
            val events = (state.recentEvents + event).takeLast(20)

            state.copy(dimensions = dims, recentEvents = events)
        }
    }

    /**
     * 批量添加声誉（用于事件等一次性影响多个维度）
     */
    fun addMultipleDimensions(changes: Map<ReputationDimension, Pair<Float, String>>) {
        changes.forEach { (dim, pair) ->
            addDimensionReputation(dim, pair.first, pair.second)
        }
    }

    /**
     * 每月推进声誉系统
     * @param schoolReputation 当前总声誉（用于同步）
     * @param teacherQuality 教师质量指标(0-100)
     * @param facilityLevel 设施等级(1-10)
     * @param studentSatisfaction 学生满意度(0-100)
     * @param employmentRate 就业率(0-1)，影响学术和社会服务声誉
     * @param governmentGradeOrdinal 政府评级序号(0=AAA, 5=D)，影响管理声誉
     * @param schoolLevel 学校等级(1-6)，影响称号门槛
     */
    fun advanceMonth(
        schoolReputation: Long,
        teacherQuality: Float,
        facilityLevel: Int,
        studentSatisfaction: Float,
        currentYear: Int,
        currentMonth: Int,
        employmentRate: Float = 0f,
        governmentGradeOrdinal: Int = 3,
        schoolLevel: Int = 1,
        teachingQualityBonus: Float = 0f,
        sportsInvestmentScore: Float = 0f,
        artsInvestmentScore: Float = 0f,
        legacyBonuses: Long = 0L
    ): ReputationMonthlyResult {
        var totalGrowth = 0L
        val dimGrowths = mutableMapOf<ReputationDimension, Float>()
        val newMilestones = mutableListOf<String>()

        _state.update { state ->
            val dims = state.dimensions.toMutableMap()

            // 各维度被动成长
            ReputationDimension.entries.forEach { dimension ->
                val dim = dims[dimension]?.copy() ?: DimensionReputation(dimension = dimension)
                val passiveGrowth = calculatePassiveGrowth(
                    dimension, dim, teacherQuality, facilityLevel, studentSatisfaction,
                    employmentRate, governmentGradeOrdinal, teachingQualityBonus,
                    sportsInvestmentScore, artsInvestmentScore
                )

                // 动量效应：正动量带来额外增长
                val momentumBonus = dim.momentum * 0.5f
                val finalGrowth = passiveGrowth + momentumBonus

                dim.score = (dim.score + finalGrowth).coerceAtLeast(0f)
                dim.monthlyGrowth = finalGrowth
                dim.momentum *= 0.9f  // 动量衰减

                // 等级检查
                val newLevel = calculateLevel(dim.score)
                if (newLevel > dim.level) {
                    val milestoneName = "${dimension.displayName} 达到 Lv.$newLevel"
                    newMilestones.add(milestoneName)
                    dim.milestones++
                }
                dim.level = newLevel

                // 计算对总声誉的贡献
                val weight = dimensionWeights[dimension] ?: 0.2f
                val contribution = (finalGrowth * weight * 10).toLong()
                dim.totalContribution += contribution
                totalGrowth += contribution

                dimGrowths[dimension] = finalGrowth
                dims[dimension] = dim
            }

            // 均衡奖励：各维度差距越小，奖励越高
            val scores = dims.values.map { it.score }
            val maxScore = scores.max()
            val minScore = scores.min()
            val balanceRatio = if (maxScore > 0) minScore / maxScore else 1f
            val balanceBonus = when {
                balanceRatio >= 0.8f -> 0.20f  // 均衡发展+20%
                balanceRatio >= 0.6f -> 0.10f  // 较均衡+10%
                balanceRatio >= 0.4f -> 0.0f   // 一般
                else -> -0.05f                  // 严重偏科-5%
            }

            val balanceBonusGrowth = (totalGrowth * balanceBonus).toLong()
            totalGrowth += balanceBonusGrowth

            // 旧月度公式的并列加成（日历/营销/满意度/奖学金/政策×设施乘数），
            // 统一经由本函数单一出口结算，避免双引擎并行加值
            if (legacyBonuses != 0L) {
                totalGrowth += legacyBonuses
            }

            // 确定优势/弱势维度
            val dominant = dims.maxByOrNull { it.value.score }?.key
            val weakest = dims.minByOrNull { it.value.score }?.key

            // 更新学校称号（需满足声誉+学校等级双门槛）
            val effectiveReputation = schoolReputation + totalGrowth
            val title = titleThresholds.firstOrNull {
                effectiveReputation >= it.first && schoolLevel >= it.third
            }?.second ?: "普通学校"

            state.copy(
                totalReputation = effectiveReputation,
                dimensions = dims,
                dominantDimension = dominant,
                weakestDimension = weakest,
                schoolTitle = title,
                monthlyChange = totalGrowth,
                balanceBonus = balanceBonus
            )
        }

        val newTitle = if (newMilestones.isNotEmpty()) _state.value.schoolTitle else null

        return ReputationMonthlyResult(
            totalGrowth = totalGrowth,
            dimensionGrowths = dimGrowths,
            newTitle = newTitle,
            newMilestones = newMilestones,
            balanceBonus = _state.value.balanceBonus
        )
    }

    /**
     * 获取声誉概要用于显示
     */
    fun getSchoolTitle(): String = _state.value.schoolTitle

    /**
     * 获取特定维度的等级
     */
    fun getDimensionLevel(dimension: ReputationDimension): Int {
        return _state.value.dimensions[dimension]?.level ?: 1
    }

    private fun calculatePassiveGrowth(
        dimension: ReputationDimension,
        dim: DimensionReputation,
        teacherQuality: Float,
        facilityLevel: Int,
        studentSatisfaction: Float,
        employmentRate: Float = 0f,
        governmentGradeOrdinal: Int = 3,
        teachingQualityBonus: Float = 0f,
        sportsInvestmentScore: Float = 0f,
        artsInvestmentScore: Float = 0f
    ): Float {
        val baseGrowth = 0.35f  // 被动声誉极慢，主动经营（竞赛/委托/奖学金发放）才是主通道

        // 政府评级因子：AAA(0)=1.3, AA(1)=1.15, A(2)=1.05, B(3)=1.0, C(4)=0.9, D(5)=0.75
        val govFactor = when (governmentGradeOrdinal) {
            0 -> 1.3f
            1 -> 1.15f
            2 -> 1.05f
            3 -> 1.0f
            4 -> 0.9f
            else -> 0.75f
        }

        val qualityMultiplier = when (dimension) {
            ReputationDimension.ACADEMIC -> {
                // 学术声誉受教师质量 + 就业率 + 教学配置质量影响
                val empBonus = (employmentRate * 0.3f).coerceAtMost(0.3f)
                val teachingBonus = (teachingQualityBonus / 10f * 0.3f).coerceIn(0f, 0.3f)
                (teacherQuality / 100f * 1.3f + empBonus + teachingBonus)
            }
            ReputationDimension.SPORTS -> (sportsInvestmentScore.coerceIn(0f, 100f) / 100f * 1.2f)
                .coerceAtLeast(facilityLevel / 10f * 0.3f) // 保底保留部分设施加成
            ReputationDimension.ARTS -> {
                val artsScore = artsInvestmentScore.coerceIn(0f, 100f)
                val baseArts = (teacherQuality / 100f + studentSatisfaction / 100f) * 0.6f
                baseArts + artsScore / 100f * 0.4f
            }
            ReputationDimension.SOCIAL_SERVICE -> {
                // 社会服务声誉受就业率正向影响（毕业生回馈社会）
                val empBonus = (employmentRate * 0.4f).coerceAtMost(0.3f)
                studentSatisfaction / 100f * 0.8f + empBonus
            }
            ReputationDimension.MANAGEMENT -> {
                // 管理声誉直接受政府评级影响
                (teacherQuality / 100f * 0.3f + facilityLevel / 10f * 0.2f +
                    studentSatisfaction / 100f * 0.2f) * govFactor
            }
        }

        // 高等级成长减速(对数衰减)
        val levelPenalty = 1.0f / (1.0f + dim.level * 0.02f)

        return baseGrowth * qualityMultiplier * levelPenalty
    }

    private fun calculateLevel(score: Float): Int {
        // 每50分一级，最高100级
        return ((score / 50f).toInt() + 1).coerceIn(1, 100)
    }

    fun toJson(): String {
        return try {
            val state = _state.value
            val data = ReputationPersistData(
                dimensions = state.dimensions.map { (dim, rep) ->
                    DimensionPersist(
                        dimension = dim.name,
                        score = rep.score,
                        level = rep.level,
                        momentum = rep.momentum,
                        totalContribution = rep.totalContribution,
                        milestones = rep.milestones,
                        achievements = rep.achievements.toList()
                    )
                },
                schoolTitle = state.schoolTitle,
                recentEvents = state.recentEvents.map { it.reason }
            )
            Json.encodeToString(data)
        } catch (_: Exception) { "" }
    }

    fun restoreFromJson(json: String) {
        if (json.isBlank()) return
        try {
            val data = Json { ignoreUnknownKeys = true }.decodeFromString<ReputationPersistData>(json)
            val dimensions = data.dimensions.mapNotNull { dp ->
                val dim = try { ReputationDimension.valueOf(dp.dimension) } catch (_: Exception) { return@mapNotNull null }
                dim to DimensionReputation(
                    dimension = dim,
                    score = dp.score,
                    level = dp.level,
                    momentum = dp.momentum,
                    totalContribution = dp.totalContribution,
                    milestones = dp.milestones,
                    achievements = dp.achievements.toMutableList()
                )
            }.toMap()
            if (dimensions.isNotEmpty()) {
                _state.update { it.copy(dimensions = dimensions, schoolTitle = data.schoolTitle, recentEvents = emptyList()) }
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("ReputationManager.restoreFromJson failed", e)
        }
    }
}

@Serializable
data class ReputationPersistData(
    val dimensions: List<DimensionPersist> = emptyList(),
    val schoolTitle: String = "",
    val recentEvents: List<String> = emptyList()
)

@Serializable
data class DimensionPersist(
    val dimension: String,
    val score: Float = 100f,
    val level: Int = 1,
    val momentum: Float = 0f,
    val totalContribution: Long = 0L,
    val milestones: Int = 0,
    val achievements: List<String> = emptyList()
)
