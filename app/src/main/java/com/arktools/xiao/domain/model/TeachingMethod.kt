package com.arktools.xiao.domain.model

import java.util.UUID

data class TeachingMethod(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val category: MethodCategory,
    val unlockYear: Int,
    val cost: Double,
    val researchDays: Int,
    val bonusType: BonusType,
    val bonusValue: Float,
    val prerequisiteIds: List<String> = emptyList(),
    var isUnlocked: Boolean = false,
    var isResearching: Boolean = false,
    var remainingResearchDays: Int = 0
)

enum class MethodCategory(val displayName: String) {
    PEDAGOGY("教学法"),
    PSYCHOLOGY("教育心理学"),
    TECHNOLOGY("教育科技"),
    MANAGEMENT("学校管理"),
    CURRICULUM("课程设计")
}

enum class BonusType(val displayName: String) {
    TEACHING_QUALITY("教学质量"),
    RESEARCH_SPEED("备课速度"),
    ENROLLMENT("招生人数"),
    REVENUE("收入加成"),
    TEACHER_LOYALTY("教师忠诚"),
    COST_REDUCTION("成本降低")
}
