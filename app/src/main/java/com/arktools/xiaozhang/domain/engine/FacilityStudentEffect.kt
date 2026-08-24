package com.arktools.xiaozhang.domain.engine

import com.arktools.xiaozhang.domain.model.*
import com.arktools.xiaozhang.domain.model.HealthStatus
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
        if (facilities.isEmpty()) return student

        var attrs = student.attributes
        var health = student.healthStatus
        var mealQ = student.mealQuality
        var dormSat = student.dormSatisfaction
        var exercise = student.exerciseLevel
        var sickDays = student.consecutiveSickDays

        // 计算特质修正系数
        val traitMods = calculateTraitModifiers(student.traits)

        facilities.filter { it.isOperational }.forEach { facility ->
            val power = facility.level * BASE_POWER_PER_LEVEL
            val conditionFactor = facility.condition / 100f  // 设施状态折损

            val effectivePower = power * conditionFactor

            when (facility.type) {
                FacilityType.CANTEEN -> {
                    // 食堂: 体力恢复 + 社交（吃饭聊天） + 饮食质量
                    mealQ = (mealQ + effectivePower * CANTEEN_MEAL_QUALITY_GAIN).coerceAtMost(100f)
                    attrs = attrs.applyDelta(
                        dPhysical = effectivePower * CANTEEN_PHYSICAL_GAIN * traitMods.physicalMod,
                        dSocial = effectivePower * CANTEEN_SOCIAL_GAIN * traitMods.socialMod
                    )
                    // 食堂差(condition<40) → 学生可能生病
                    if (facility.condition < 40f && Random.nextFloat() < CANTEEN_SICK_PROBABILITY) {
                        if (health == HealthStatus.HEALTHY) {
                            health = HealthStatus.SICK
                            sickDays = 0
                        }
                    }
                }

                FacilityType.SPORTS_FIELD -> {
                    // 运动场: 体力大幅提升 + 运动量
                    exercise = (exercise + effectivePower * SPORTS_EXERCISE_GAIN).coerceAtMost(100f)
                    attrs = attrs.applyDelta(
                        dPhysical = effectivePower * SPORTS_PHYSICAL_GAIN * traitMods.physicalMod
                    )
                    // 运动可以帮助从疲劳恢复
                    if (health == HealthStatus.FATIGUED && Random.nextFloat() < effectivePower * 0.1f) {
                        health = HealthStatus.HEALTHY
                    }
                }

                FacilityType.DORMITORY -> {
                    // 宿舍: 住宿满意度 + 体力恢复 + 疲劳恢复
                    dormSat = (dormSat + effectivePower * DORM_SATISFACTION_GAIN).coerceAtMost(100f)
                    attrs = attrs.applyDelta(
                        dPhysical = effectivePower * DORM_PHYSICAL_GAIN * traitMods.physicalMod
                    )
                    // 好宿舍加速生病/疲劳恢复
                    if (health == HealthStatus.FATIGUED && Random.nextFloat() < effectivePower * 0.15f) {
                        health = HealthStatus.HEALTHY
                    }
                    if (health == HealthStatus.SICK && facility.level >= 2 && Random.nextFloat() < effectivePower * 0.05f) {
                        health = HealthStatus.HEALTHY
                        sickDays = 0
                    }
                }

                FacilityType.LIBRARY -> {
                    // 图书馆: 智力 + 品德
                    attrs = attrs.applyDelta(
                        dIntelligence = effectivePower * LIBRARY_INTELLIGENCE_GAIN * traitMods.intelligenceMod,
                        dMorality = effectivePower * LIBRARY_MORALITY_GAIN * traitMods.moralityMod
                    )
                }

                FacilityType.ART_STUDIO -> {
                    // 艺术工作室: 创造力大幅提升
                    attrs = attrs.applyDelta(
                        dCreativity = effectivePower * ART_CREATIVITY_GAIN * traitMods.creativityMod
                    )
                }

                FacilityType.MULTIMEDIA_ROOM -> {
                    // 多媒体教室: 智力提升（辅助教学）
                    attrs = attrs.applyDelta(
                        dIntelligence = effectivePower * MULTIMEDIA_INTELLIGENCE_GAIN * traitMods.intelligenceMod
                    )
                }

                FacilityType.LABORATORY -> {
                    // 实验室: 智力 + 创造力（实验探索）
                    attrs = attrs.applyDelta(
                        dIntelligence = effectivePower * LAB_INTELLIGENCE_GAIN * traitMods.intelligenceMod,
                        dCreativity = effectivePower * LAB_CREATIVITY_GAIN * traitMods.creativityMod
                    )
                }

                FacilityType.COMPUTER_LAB -> {
                    // 计算机房: 智力 + 创造力
                    attrs = attrs.applyDelta(
                        dIntelligence = effectivePower * COMPUTER_INTELLIGENCE_GAIN * traitMods.intelligenceMod,
                        dCreativity = effectivePower * COMPUTER_CREATIVITY_GAIN * traitMods.creativityMod
                    )
                }

                FacilityType.GARDEN -> {
                    // 花园: 社交 + 品德（环境熏陶）
                    attrs = attrs.applyDelta(
                        dSocial = effectivePower * GARDEN_SOCIAL_GAIN * traitMods.socialMod,
                        dMorality = effectivePower * GARDEN_MORALITY_GAIN * traitMods.moralityMod
                    )
                }

                FacilityType.AUDITORIUM -> {
                    // 大礼堂: 社交 + 创造力（活动/演出参与）
                    attrs = attrs.applyDelta(
                        dSocial = effectivePower * AUDITORIUM_SOCIAL_GAIN * traitMods.socialMod,
                        dCreativity = effectivePower * AUDITORIUM_CREATIVITY_GAIN * traitMods.creativityMod
                    )
                }

                FacilityType.CLASSROOM -> {
                    // 教室: 基础智力维持（有教室才能上课）
                    attrs = attrs.applyDelta(
                        dIntelligence = effectivePower * CLASSROOM_INTELLIGENCE_GAIN * traitMods.intelligenceMod
                    )
                }

                FacilityType.GATE -> {
                    // 校门: 品德微量提升（仪式感）
                    attrs = attrs.applyDelta(
                        dMorality = effectivePower * GATE_MORALITY_GAIN * traitMods.moralityMod
                    )
                }
            }
        }

        // 自然衰减（没有对应设施的维度会缓慢下降）
        attrs = applyNaturalDecay(attrs, facilities)

        // 健康状态自然流转
        val (newHealth, newSickDays) = updateHealthState(health, sickDays, attrs, mealQ, exercise)

        return student.copy(
            attributes = attrs,
            healthStatus = newHealth,
            mealQuality = mealQ,
            dormSatisfaction = dormSat,
            exerciseLevel = exercise,
            consecutiveSickDays = newSickDays
        )
    }

    /**
     * 自然衰减: 缺少对应设施时维度缓慢下降
     */
    private fun applyNaturalDecay(attrs: StudentAttributes, facilities: List<Facility>): StudentAttributes {
        val operational = facilities.filter { it.isOperational }.map { it.type }.toSet()

        var dPhysical = 0f
        var dSocial = 0f

        // 没有运动场且没有体育课 → 体力每日微降
        if (FacilityType.SPORTS_FIELD !in operational) {
            dPhysical -= NATURAL_PHYSICAL_DECAY
        }
        // 极其孤立（无花园无大礼堂）→ 社交微降
        if (FacilityType.GARDEN !in operational && FacilityType.AUDITORIUM !in operational) {
            dSocial -= NATURAL_SOCIAL_DECAY
        }

        return if (dPhysical != 0f || dSocial != 0f) {
            attrs.applyDelta(dPhysical = dPhysical, dSocial = dSocial)
        } else {
            attrs
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
