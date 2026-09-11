package com.arktools.xiao.domain.engine

import com.arktools.xiao.domain.model.*
import com.arktools.xiao.domain.model.HealthStatus
import kotlin.random.Random

/**
 * 设施对学生个体的每日直接影响
 *
 * 核心改进: 设施不再只产生抽象加成(FacilityBonuses)，
 * 而是每日直接作用到每个学生的五维属性、健康、饮食、运动等。
 *
 * 设施→学生 影响映射:
 * | 设施 | 主要影响维度 | 次要影响 |
 * |------|------------|---------|
 * | 食堂 | 体力↑, 社交↑ | 饮食质量, 生病预防 |
 * | 运动场 | 体力↑ | 运动量, 健康恢复 |
 * | 宿舍 | 体力↑ | 住宿满意度, 疲劳恢复 |
 * | 图书馆 | 智力↑, 品德↑ | — |
 * | 艺术工作室 | 创造力↑ | — |
 * | 花园 | 社交↑, 品德↑ | 心情恢复 |
 * | 大礼堂 | 社交↑, 创造力↑ | — |
 * | 多媒体教室 | 智力↑ | — |
 * | 实验室 | 智力↑, 创造力↑ | — |
 * | 计算机房 | 智力↑, 创造力↑ | — |
 */
object FacilityStudentEffect {

    /**
     * 每日结算: 设施对单个学生的综合影响
     * 应在 GameEngine.updateStudentProgress() 的每日tick中调用
     *
     * @return 修改后的学生 (copy)
     */
    fun applyDailyEffects(student: Student, facilities: List<Facility>): Student {
        return applyDailyEffects(student, compileCampusEffects(facilities))
    }

    /**
     * 先把全校楼况压成一次校园效果，再作用到每个学生。
     * 避免后期几千学生 × 几十栋楼的双重循环把 6 月 tick 卡死。
     */
    fun compileCampusEffects(facilities: List<Facility>): CampusEffects {
        val operational = facilities.filter { it.isOperational }
        if (operational.isEmpty()) return CampusEffects.EMPTY

        var dIntelligence = 0f
        var dPhysical = 0f
        var dSocial = 0f
        var dCreativity = 0f
        var dMorality = 0f
        var mealGain = 0f
        var dormGain = 0f
        var exerciseGain = 0f
        var canteenSickChance = 0f
        var sportsFatigueHeal = 0f
        var dormFatigueHeal = 0f
        var dormSickHeal = 0f
        var clinicSickHeal = 0f
        var counselingFatigueHeal = 0f
        val types = mutableSetOf<FacilityType>()
        val typeIndex = mutableMapOf<FacilityType, Int>()

        operational.sortedByDescending { it.level }.forEach { facility ->
            types += facility.type
            val index = typeIndex[facility.type] ?: 0
            typeIndex[facility.type] = index + 1
            val power = facility.level * BASE_POWER_PER_LEVEL
            val conditionFactor = facility.condition / 100f
            val effectivePower = power * conditionFactor * FacilityCapacity.diminishing(index)
            when (facility.type) {
                FacilityType.CONFERENCE_CENTER -> {
                    dSocial += effectivePower * 0.10f
                    dMorality += effectivePower * 0.05f
                }
                FacilityType.EMPLOYMENT_CENTER -> {
                    dSocial += effectivePower * 0.12f
                }
                FacilityType.INCUBATOR -> {
                    dSocial += effectivePower * 0.08f
                    dMorality += effectivePower * 0.04f
                }
                FacilityType.INTERNATIONAL_CENTER -> {
                    dSocial += effectivePower * 0.08f
                    dCreativity += effectivePower * 0.06f
                }
                FacilityType.LOGISTICS_CENTER -> Unit
                FacilityType.CANTEEN -> {
                    mealGain += effectivePower * CANTEEN_MEAL_QUALITY_GAIN
                    dPhysical += effectivePower * CANTEEN_PHYSICAL_GAIN
                    dSocial += effectivePower * CANTEEN_SOCIAL_GAIN
                    if (facility.condition < 40f) {
                        canteenSickChance += CANTEEN_SICK_PROBABILITY
                    }
                }
                FacilityType.SPORTS_FIELD -> {
                    exerciseGain += effectivePower * SPORTS_EXERCISE_GAIN
                    dPhysical += effectivePower * SPORTS_PHYSICAL_GAIN
                    sportsFatigueHeal += effectivePower * 0.1f
                }
                FacilityType.DORMITORY -> {
                    dormGain += effectivePower * DORM_SATISFACTION_GAIN
                    dPhysical += effectivePower * DORM_PHYSICAL_GAIN
                    dormFatigueHeal += effectivePower * 0.15f
                    if (facility.level >= 2) {
                        dormSickHeal += effectivePower * 0.05f
                    }
                }
                FacilityType.LIBRARY -> {
                    dIntelligence += effectivePower * LIBRARY_INTELLIGENCE_GAIN
                    dMorality += effectivePower * LIBRARY_MORALITY_GAIN
                }
                FacilityType.ART_STUDIO -> {
                    dCreativity += effectivePower * ART_CREATIVITY_GAIN
                }
                FacilityType.MULTIMEDIA_ROOM -> {
                    dIntelligence += effectivePower * MULTIMEDIA_INTELLIGENCE_GAIN
                }
                FacilityType.LABORATORY -> {
                    dIntelligence += effectivePower * LAB_INTELLIGENCE_GAIN
                    dCreativity += effectivePower * LAB_CREATIVITY_GAIN
                }
                FacilityType.COMPUTER_LAB -> {
                    dIntelligence += effectivePower * COMPUTER_INTELLIGENCE_GAIN
                    dCreativity += effectivePower * COMPUTER_CREATIVITY_GAIN
                }
                FacilityType.GARDEN -> {
                    dSocial += effectivePower * GARDEN_SOCIAL_GAIN
                    dMorality += effectivePower * GARDEN_MORALITY_GAIN
                }
                FacilityType.AUDITORIUM -> {
                    dSocial += effectivePower * AUDITORIUM_SOCIAL_GAIN
                    dCreativity += effectivePower * AUDITORIUM_CREATIVITY_GAIN
                }
                FacilityType.CLASSROOM -> {
                    dIntelligence += effectivePower * CLASSROOM_INTELLIGENCE_GAIN
                }
                FacilityType.GATE -> {
                    dMorality += effectivePower * GATE_MORALITY_GAIN
                }
                FacilityType.CLINIC -> {
                    clinicSickHeal += effectivePower * 0.18f
                    dPhysical += effectivePower * 0.08f
                }
                FacilityType.COUNSELING -> {
                    counselingFatigueHeal += effectivePower * 0.12f
                    dMorality += effectivePower * 0.08f
                    dSocial += effectivePower * 0.04f
                }
            }
        }

        if (FacilityType.SPORTS_FIELD !in types) {
            dPhysical -= NATURAL_PHYSICAL_DECAY
        }
        if (FacilityType.GARDEN !in types && FacilityType.AUDITORIUM !in types) {
            dSocial -= NATURAL_SOCIAL_DECAY
        }

        return CampusEffects(
            dIntelligence = dIntelligence,
            dPhysical = dPhysical,
            dSocial = dSocial,
            dCreativity = dCreativity,
            dMorality = dMorality,
            mealGain = mealGain,
            dormGain = dormGain,
            exerciseGain = exerciseGain,
            canteenSickChance = canteenSickChance.coerceAtMost(0.08f),
            sportsFatigueHeal = sportsFatigueHeal.coerceAtMost(0.6f),
            dormFatigueHeal = dormFatigueHeal.coerceAtMost(0.6f),
            dormSickHeal = dormSickHeal.coerceAtMost(0.4f),
            clinicSickHeal = clinicSickHeal.coerceAtMost(0.6f),
            counselingFatigueHeal = counselingFatigueHeal.coerceAtMost(0.5f)
        )
    }

    fun applyDailyEffects(student: Student, campus: CampusEffects): Student {
        var attrs = student.attributes
        var health = student.healthStatus
        var mealQ = student.mealQuality
        var dormSat = student.dormSatisfaction
        var exercise = student.exerciseLevel
        var sickDays = student.consecutiveSickDays
        val traitMods = calculateTraitModifiers(student.traits)

        mealQ = (mealQ + campus.mealGain).coerceAtMost(100f)
        dormSat = (dormSat + campus.dormGain).coerceAtMost(100f)
        exercise = (exercise + campus.exerciseGain).coerceAtMost(100f)
        attrs = attrs.applyDelta(
            dIntelligence = campus.dIntelligence * traitMods.intelligenceMod,
            dPhysical = campus.dPhysical * traitMods.physicalMod,
            dSocial = campus.dSocial * traitMods.socialMod,
            dCreativity = campus.dCreativity * traitMods.creativityMod,
            dMorality = campus.dMorality * traitMods.moralityMod
        )

        if (health == HealthStatus.HEALTHY &&
            campus.canteenSickChance > 0f &&
            Random.nextFloat() < campus.canteenSickChance
        ) {
            health = HealthStatus.SICK
            sickDays = 0
        }
        if (health == HealthStatus.FATIGUED) {
            val fatigueHeal = campus.sportsFatigueHeal +
                campus.dormFatigueHeal +
                campus.counselingFatigueHeal
            if (fatigueHeal > 0f && Random.nextFloat() < fatigueHeal) {
                health = HealthStatus.HEALTHY
            }
        }
        if (health == HealthStatus.SICK) {
            val sickHeal = campus.clinicSickHeal + campus.dormSickHeal
            if (sickHeal > 0f && Random.nextFloat() < sickHeal) {
                health = HealthStatus.HEALTHY
                sickDays = 0
            }
        }

        val (newHealth, newSickDays) = updateHealthState(
            health,
            sickDays,
            attrs,
            mealQ,
            exercise
        )
        return student.copy(
            attributes = attrs,
            healthStatus = newHealth,
            mealQuality = mealQ,
            dormSatisfaction = dormSat,
            exerciseLevel = exercise,
            consecutiveSickDays = newSickDays
        )
    }

    data class CampusEffects(
        val dIntelligence: Float = 0f,
        val dPhysical: Float = 0f,
        val dSocial: Float = 0f,
        val dCreativity: Float = 0f,
        val dMorality: Float = 0f,
        val mealGain: Float = 0f,
        val dormGain: Float = 0f,
        val exerciseGain: Float = 0f,
        val canteenSickChance: Float = 0f,
        val sportsFatigueHeal: Float = 0f,
        val dormFatigueHeal: Float = 0f,
        val dormSickHeal: Float = 0f,
        val clinicSickHeal: Float = 0f,
        val counselingFatigueHeal: Float = 0f
    ) {
        companion object {
            val EMPTY = CampusEffects()
        }
    }

        /**
     * 健康状态自然流转
     */
    private fun updateHealthState(
        current: HealthStatus,
        sickDays: Int,
        attrs: StudentAttributes,
        mealQuality: Float,
        exerciseLevel: Float
    ): Pair<HealthStatus, Int> {
        var health = current
        var days = sickDays

        when (health) {
            HealthStatus.HEALTHY -> {
                // 体力极低 → 可能变疲劳
                if (attrs.physical < 25f && Random.nextFloat() < 0.03f) {
                    health = HealthStatus.FATIGUED
                }
                // 饮食极差 → 可能生病
                if (mealQuality < 20f && Random.nextFloat() < 0.02f) {
                    health = HealthStatus.SICK
                    days = 0
                }
            }
            HealthStatus.FATIGUED -> {
                // 体力恢复到50以上 → 自动恢复
                if (attrs.physical >= 50f) {
                    health = HealthStatus.HEALTHY
                }
                // 运动量高 → 加速恢复
                if (exerciseLevel > 60f && Random.nextFloat() < 0.1f) {
                    health = HealthStatus.HEALTHY
                }
            }
            HealthStatus.SICK -> {
                days++
                // 3~7天自然恢复概率递增
                val recoveryChance = when {
                    days >= 7 -> 0.5f
                    days >= 5 -> 0.3f
                    days >= 3 -> 0.15f
                    else -> 0.05f
                }
                // 体力高的学生恢复更快
                val physicalBonus = attrs.physical / 500f
                if (Random.nextFloat() < recoveryChance + physicalBonus) {
                    health = HealthStatus.HEALTHY
                    days = 0
                }
            }
            HealthStatus.INJURED -> {
                days++
                // 受伤恢复较慢(5~14天)
                val recoveryChance = when {
                    days >= 14 -> 0.6f
                    days >= 10 -> 0.3f
                    days >= 5 -> 0.1f
                    else -> 0.02f
                }
                if (Random.nextFloat() < recoveryChance) {
                    health = HealthStatus.HEALTHY
                    days = 0
                }
            }
        }

        return health to days
    }

    /**
     * 计算特质对五维成长的修正系数
     */
    private fun calculateTraitModifiers(traits: List<StudentTrait>): TraitModifiers {
        var intMod = 1.0f
        var phyMod = 1.0f
        var socMod = 1.0f
        var creMod = 1.0f
        var morMod = 1.0f

        traits.forEach { trait ->
            intMod *= trait.intelligenceMod
            phyMod *= trait.physicalMod
            socMod *= trait.socialMod
            creMod *= trait.creativityMod
            morMod *= trait.moralityMod
        }

        return TraitModifiers(intMod, phyMod, socMod, creMod, morMod)
    }

    private data class TraitModifiers(
        val intelligenceMod: Float,
        val physicalMod: Float,
        val socialMod: Float,
        val creativityMod: Float,
        val moralityMod: Float
    )

    // ======= 平衡常量 =======
    // v2.8 重新平衡：基础增长率大幅降低（原来0.012太高，3年全满）
    // 目标：5级满设施+递减回报，3年后普通学生智力约增15~25点（从50到65~75）
    // 计算：0.005 * 5级 * 1.0条件 * (0.3+0.35+0.25+0.25+0.15)=1.3 → 0.0325/天
    //       前60天正常=+1.95, 到60后打0.55折=+0.018/天 → 1080天约+20~25点
    private const val BASE_POWER_PER_LEVEL = 0.005f  // 每级设施基础效果（原0.012→0.005）

    // 食堂
    private const val CANTEEN_MEAL_QUALITY_GAIN = 4.0f
    private const val CANTEEN_PHYSICAL_GAIN = 0.4f
    private const val CANTEEN_SOCIAL_GAIN = 0.15f
    private const val CANTEEN_SICK_PROBABILITY = 0.008f

    // 运动场
    private const val SPORTS_EXERCISE_GAIN = 3.0f
    private const val SPORTS_PHYSICAL_GAIN = 0.7f

    // 宿舍
    private const val DORM_SATISFACTION_GAIN = 3.0f
    private const val DORM_PHYSICAL_GAIN = 0.3f

    // 图书馆
    private const val LIBRARY_INTELLIGENCE_GAIN = 0.3f
    private const val LIBRARY_MORALITY_GAIN = 0.1f

    // 艺术工作室
    private const val ART_CREATIVITY_GAIN = 0.6f

    // 多媒体教室
    private const val MULTIMEDIA_INTELLIGENCE_GAIN = 0.35f

    // 实验室
    private const val LAB_INTELLIGENCE_GAIN = 0.25f
    private const val LAB_CREATIVITY_GAIN = 0.2f

    // 计算机房
    private const val COMPUTER_INTELLIGENCE_GAIN = 0.25f
    private const val COMPUTER_CREATIVITY_GAIN = 0.2f

    // 花园
    private const val GARDEN_SOCIAL_GAIN = 0.2f
    private const val GARDEN_MORALITY_GAIN = 0.15f

    // 大礼堂
    private const val AUDITORIUM_SOCIAL_GAIN = 0.25f
    private const val AUDITORIUM_CREATIVITY_GAIN = 0.2f

    // 教室
    private const val CLASSROOM_INTELLIGENCE_GAIN = 0.15f

    // 校门
    private const val GATE_MORALITY_GAIN = 0.05f

    // 自然衰减（保持不变——没设施的维度照常衰退）
    private const val NATURAL_PHYSICAL_DECAY = 0.01f
    private const val NATURAL_SOCIAL_DECAY = 0.005f
}
