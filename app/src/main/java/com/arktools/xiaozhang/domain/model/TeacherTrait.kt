package com.arktools.xiaozhang.domain.model

/**
 * Teacher traits add personality and strategic depth to hiring decisions.
 * Each teacher can have 1-3 traits that affect gameplay.
 */
enum class TeacherTrait(
    val displayName: String,
    val description: String,
    val category: TraitCategory
) {
    // Positive traits
    PASSIONATE("热爱教育", "教学技能成长速度+50%", TraitCategory.POSITIVE),
    PATIENT("耐心十足", "学生满意度+20%，招生加成", TraitCategory.POSITIVE),
    INNOVATIVE("善于创新", "课程评分+0.5，研究方法更容易解锁", TraitCategory.POSITIVE),
    CHARISMATIC("人格魅力", "招生加成+30%，忠诚度不易下降", TraitCategory.POSITIVE),
    HARDWORKING("勤奋刻苦", "备课速度+25%，但疲劳积累快10%", TraitCategory.POSITIVE),
    MENTOR("良师益友", "团队中其他教师技能成长+20%", TraitCategory.POSITIVE),
    RESEARCHER("学术型", "研究效率+40%，但教学速度-10%", TraitCategory.POSITIVE),
    EXPERIENCED("经验丰富", "问题发现率+50%，课程质量更稳定", TraitCategory.POSITIVE),

    // Neutral traits (double-edged)
    PERFECTIONIST("完美主义", "课程评分+1.0但备课时间+30%", TraitCategory.NEUTRAL),
    INTROVERT("内向性格", "独立备课效率+30%，团队配合-15%", TraitCategory.NEUTRAL),
    STRICT("严格要求", "教学质量+15%，但学生满意度-10%", TraitCategory.NEUTRAL),
    POPULAR("人气教师", "招生+40%，但容易被竞争对手挖角", TraitCategory.NEUTRAL),

    // Negative traits (cost less to hire)
    LAZY("消极怠工", "备课速度-20%，但薪资要求低", TraitCategory.NEGATIVE),
    IMPATIENT("急躁冲动", "容易与同事产生矛盾，团队效率-10%", TraitCategory.NEGATIVE),
    OUTDATED("固守传统", "对新教学方法抵触，研究加成-50%", TraitCategory.NEGATIVE),
    GREEDY("唯利是图", "薪资满足阈值+50%，忠诚度波动大", TraitCategory.NEGATIVE)
}

enum class TraitCategory {
    POSITIVE, NEUTRAL, NEGATIVE
}

/**
 * Teacher growth system: teachers improve over time through experience.
 */
object TeacherGrowth {
    /**
     * Calculate daily skill growth for a teacher.
     * Growth is affected by traits, fatigue, and current skill level.
     */
    fun calculateDailyGrowth(teacher: Teacher): SkillGrowth {
        // Base growth decreases as skill increases (diminishing returns)
        val avgSkill = teacher.averageSkill
        val baseGrowthRate = (100 - avgSkill).coerceAtLeast(5) * 0.002f

        // Fatigue penalty - tired teachers don't grow
        val fatiguePenalty = if (teacher.fatigue > 60) 0.5f else 1.0f

        // Trait bonuses
        val traitMultiplier = getGrowthTraitMultiplier(teacher)

        val growthRate = baseGrowthRate * fatiguePenalty * traitMultiplier

        // Different skills grow at different rates based on teacher role
        return when (teacher.role.category) {
            SubjectCategory.SCIENCE -> SkillGrowth(
                teachingGrowth = growthRate * 0.8f,
                researchGrowth = growthRate * 1.3f,
                managementGrowth = growthRate * 0.7f,
                psychologyGrowth = growthRate * 0.9f
            )
            SubjectCategory.LITERATURE -> SkillGrowth(
                teachingGrowth = growthRate * 1.2f,
                researchGrowth = growthRate * 0.8f,
                managementGrowth = growthRate * 0.9f,
                psychologyGrowth = growthRate * 1.1f
            )
            SubjectCategory.ART, SubjectCategory.SPORTS -> SkillGrowth(
                teachingGrowth = growthRate * 1.0f,
                researchGrowth = growthRate * 0.6f,
                managementGrowth = growthRate * 0.8f,
                psychologyGrowth = growthRate * 1.2f
            )
            SubjectCategory.LANGUAGE -> SkillGrowth(
                teachingGrowth = growthRate * 1.1f,
                researchGrowth = growthRate * 0.9f,
                managementGrowth = growthRate * 1.0f,
                psychologyGrowth = growthRate * 1.0f
            )
        }
    }

    private fun getGrowthTraitMultiplier(teacher: Teacher): Float {
        var multiplier = 1.0f
        teacher.traits.forEach { trait ->
            when (trait) {
                TeacherTrait.PASSIONATE -> multiplier *= 1.5f
                TeacherTrait.HARDWORKING -> multiplier *= 1.25f
                TeacherTrait.LAZY -> multiplier *= 0.8f
                TeacherTrait.OUTDATED -> multiplier *= 0.7f
                else -> {}
            }
        }
        return multiplier
    }

    /**
     * Generate random traits for a new teacher based on their level.
     * Higher level teachers tend to have more positive traits.
     */
    fun generateTraits(level: TeacherLevel): List<TeacherTrait> {
        val traitCount = when (level) {
            TeacherLevel.C -> 1
            TeacherLevel.B -> 2
            TeacherLevel.A -> 2
            TeacherLevel.S -> 3
        }

        val positiveChance = when (level) {
            TeacherLevel.C -> 0.3f
            TeacherLevel.B -> 0.5f
            TeacherLevel.A -> 0.7f
            TeacherLevel.S -> 0.85f
        }

        val allTraits = TeacherTrait.values().toList()
        val selected = mutableListOf<TeacherTrait>()

        repeat(traitCount) {
            val roll = kotlin.random.Random.nextFloat()
            val pool = when {
                roll < positiveChance -> allTraits.filter { it.category == TraitCategory.POSITIVE }
                roll < positiveChance + 0.3f -> allTraits.filter { it.category == TraitCategory.NEUTRAL }
                else -> allTraits.filter { it.category == TraitCategory.NEGATIVE }
            }
            val candidate = pool.filter { it !in selected }.randomOrNull()
            if (candidate != null) {
                selected.add(candidate)
            }
        }

        return selected
    }
}

data class SkillGrowth(
    val teachingGrowth: Float,
    val researchGrowth: Float,
    val managementGrowth: Float,
    val psychologyGrowth: Float
)
