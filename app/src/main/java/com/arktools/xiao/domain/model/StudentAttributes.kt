package com.arktools.xiao.domain.model

import kotlin.random.Random

/**
 * 学生五维属性系统
 *
 * 五个维度全面刻画学生个体差异:
 * - intelligence: 智力 — 学习速度、考试成绩、研究能力
 * - physical: 体力 — 出勤率、运动成绩、抗疲劳、健康
 * - social: 社交 — 人际关系、班级凝聚力贡献、满意度韧性
 * - creativity: 创造力 — 艺术/创新课程加成、竞赛创新分、活动表现
 * - morality: 品德 — 纪律遵守、作弊概率、班级风气贡献、抗腐蚀
 *
 * 每个维度取值范围 [0, 100]，初始入学时在 [30, 70] 随机分布（受家庭背景影响）
 * 设施、课程、教师、班风会持续影响五维成长
 */
data class StudentAttributes(
    val intelligence: Float = 50f,
    val physical: Float = 50f,
    val social: Float = 50f,
    val creativity: Float = 50f,
    val morality: Float = 50f
) {
    /** 五维总分 (0~500) */
    val totalScore: Float get() = intelligence + physical + social + creativity + morality

    /** 五维均分 (0~100) */
    val averageScore: Float get() = totalScore / 5f

    /** 综合评级 */
    val grade: AttributeGrade
        get() = when {
            averageScore >= 85f -> AttributeGrade.S
            averageScore >= 75f -> AttributeGrade.A
            averageScore >= 60f -> AttributeGrade.B
            averageScore >= 45f -> AttributeGrade.C
            else -> AttributeGrade.D
        }

    /** 最强维度 */
    val strongestDimension: AttributeDimension
        get() {
            val max = maxOf(intelligence, physical, social, creativity, morality)
            return when (max) {
                intelligence -> AttributeDimension.INTELLIGENCE
                physical -> AttributeDimension.PHYSICAL
                social -> AttributeDimension.SOCIAL
                creativity -> AttributeDimension.CREATIVITY
                else -> AttributeDimension.MORALITY
            }
        }

    /** 最弱维度 */
    val weakestDimension: AttributeDimension
        get() {
            val min = minOf(intelligence, physical, social, creativity, morality)
            return when (min) {
                intelligence -> AttributeDimension.INTELLIGENCE
                physical -> AttributeDimension.PHYSICAL
                social -> AttributeDimension.SOCIAL
                creativity -> AttributeDimension.CREATIVITY
                else -> AttributeDimension.MORALITY
            }
        }

    /** 某个维度的数值 */
    fun getValue(dimension: AttributeDimension): Float = when (dimension) {
        AttributeDimension.INTELLIGENCE -> intelligence
        AttributeDimension.PHYSICAL -> physical
        AttributeDimension.SOCIAL -> social
        AttributeDimension.CREATIVITY -> creativity
        AttributeDimension.MORALITY -> morality
    }

    /**
     * 应用变化量，带递减回报机制
     * 属性越高增长越难，模拟学习边际收益递减：
     * - 60以下：正常增长
     * - 60~75：增长×0.55
     * - 75~85：增长×0.25
     * - 85~93：增长×0.08
     * - 93以上：增长×0.02（几乎不可能自然达到）
     *
     * 现实对标：绝大部分学生3年后智力在55~75之间，
     * 只有顶尖学生(初始高+勤奋+天才)能到80+
     */
    fun applyDelta(
        dIntelligence: Float = 0f,
        dPhysical: Float = 0f,
        dSocial: Float = 0f,
        dCreativity: Float = 0f,
        dMorality: Float = 0f
    ): StudentAttributes = StudentAttributes(
        intelligence = (intelligence + diminishingGain(intelligence, dIntelligence)).coerceIn(0f, 100f),
        physical = (physical + diminishingGain(physical, dPhysical)).coerceIn(0f, 100f),
        social = (social + diminishingGain(social, dSocial)).coerceIn(0f, 100f),
        creativity = (creativity + diminishingGain(creativity, dCreativity)).coerceIn(0f, 100f),
        morality = (morality + diminishingGain(morality, dMorality)).coerceIn(0f, 100f)
    )

    companion object {
        /**
         * 递减回报：属性越高增长越难
         * 负值（衰减）不受递减影响，保持原速
         */
        private fun diminishingGain(currentValue: Float, delta: Float): Float {
            if (delta <= 0f) return delta  // 衰减不打折
            val factor = when {
                currentValue >= 93f -> 0.02f   // 93+几乎不可能自然涨
                currentValue >= 85f -> 0.08f   // 85~93极难
                currentValue >= 75f -> 0.25f   // 75~85困难
                currentValue >= 60f -> 0.55f   // 60~75中等阻力
                else -> 1.0f                   // 60以下正常增长
            }
            return delta * factor
        }

        /**
         * 生成入学新生的五维属性
         * 基础范围 [35, 65]，受家庭背景档位影响
         */
        fun generateForNewStudent(backgroundTier: BackgroundTier = BackgroundTier.NORMAL): StudentAttributes {
            val (baseMin, baseMax) = when (backgroundTier) {
                BackgroundTier.POOR -> 25f to 55f
                BackgroundTier.NORMAL -> 35f to 65f
                BackgroundTier.GOOD -> 45f to 75f
                BackgroundTier.EXCELLENT -> 55f to 85f
            }
            return StudentAttributes(
                intelligence = Random.nextFloat() * (baseMax - baseMin) + baseMin,
                physical = Random.nextFloat() * (baseMax - baseMin) + baseMin,
                social = Random.nextFloat() * (baseMax - baseMin) + baseMin,
                creativity = Random.nextFloat() * (baseMax - baseMin) + baseMin,
                morality = Random.nextFloat() * (baseMax - baseMin) + baseMin
            )
        }

        /**
         * 从旧模型迁移（兼容已有存档）
         * talent (0.6~1.0) → intelligence (60~100)
         * motivation (0.7~1.0) → 均匀分配到 social 和 morality
         */
        fun migrateFromLegacy(talent: Float, motivation: Float): StudentAttributes {
            val intel = (talent * 100f).coerceIn(0f, 100f)
            val socialVal = (motivation * 70f).coerceIn(0f, 100f)
            val moralVal = (motivation * 65f).coerceIn(0f, 100f)
            return StudentAttributes(
                intelligence = intel,
                physical = Random.nextFloat() * 30f + 40f,  // 随机补充
                social = socialVal,
                creativity = Random.nextFloat() * 30f + 35f,
                morality = moralVal
            )
        }
    }
}

/**
 * 五维维度枚举
 */
enum class AttributeDimension(val displayName: String, val icon: String) {
    INTELLIGENCE("智力", "🧠"),
    PHYSICAL("体力", "💪"),
    SOCIAL("社交", "🤝"),
    CREATIVITY("创造力", "🎨"),
    MORALITY("品德", "⭐")
}

/**
 * 综合评级
 */
enum class AttributeGrade(val displayName: String, val color: Long) {
    S("卓越", 0xFFFFD700),  // 金色
    A("优秀", 0xFF4CAF50),  // 绿色
    B("良好", 0xFF2196F3),  // 蓝色
    C("一般", 0xFFFF9800),  // 橙色
    D("较差", 0xFFF44336)   // 红色
}

/**
 * 家庭背景档位（影响初始五维分布）
 */
enum class BackgroundTier(val displayName: String, val probability: Float) {
    POOR("困难家庭", 0.15f),
    NORMAL("普通家庭", 0.50f),
    GOOD("小康家庭", 0.25f),
    EXCELLENT("优越家庭", 0.10f);

    companion object {
        /** 按概率随机抽取 */
        fun randomByProbability(): BackgroundTier {
            val roll = Random.nextFloat()
            var cumulative = 0f
            for (tier in entries) {
                cumulative += tier.probability
                if (roll <= cumulative) return tier
            }
            return NORMAL
        }
    }
}

/**
 * 学生健康状态
 */
enum class HealthStatus(val displayName: String, val learningMultiplier: Float) {
    HEALTHY("健康", 1.0f),
    FATIGUED("疲劳", 0.8f),
    SICK("生病", 0.3f),
    INJURED("受伤", 0.5f);

    val canAttendClass: Boolean get() = this != SICK
}
