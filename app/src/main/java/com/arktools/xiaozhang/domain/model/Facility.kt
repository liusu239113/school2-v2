package com.arktools.xiaozhang.domain.model

import kotlinx.serialization.Serializable

/**
 * School facilities provide passive bonuses to teaching, reputation, and enrollment.
 * Players purchase facilities to improve their school's capabilities.
 */
@Serializable
data class Facility(
    val type: FacilityType,
    var level: Int = 1,
    var condition: Float = 100f,  // deteriorates over time, needs maintenance
    val id: String = java.util.UUID.randomUUID().toString()  // 唯一标识，防止同类型设施操作错位
) {
    val maintenanceCost: Double
        get() = type.baseMaintenance * level

    val isOperational: Boolean
        get() = condition > 20f
}

enum class FacilityType(
    val displayName: String,
    val description: String,
    val baseCost: Double,       // 建设成本（万元）
    val baseMaintenance: Double, // 月维护费（万元）
    val maxLevel: Int,
    val category: FacilityCategory
) {
    // Teaching facilities — 建设成本适中，维护费合理（占学费收入10-20%为宜）
    CLASSROOM("标准教室", "提供班级槽位(Lv1:3班,Lv2:4班,Lv3:6班,Lv4:7班,Lv5:9班)，招生加成+5%/级", 15.0, 0.5, 5, FacilityCategory.TEACHING),
    MULTIMEDIA_ROOM("多媒体教室", "现代化教学设备，教学质量+10%/级", 35.0, 1.2, 3, FacilityCategory.TEACHING),
    LABORATORY("实验室", "理科实验设施，理科课程评分+15%/级，教学质量+5%/级", 50.0, 1.8, 3, FacilityCategory.TEACHING),
    COMPUTER_LAB("计算机房", "信息技术设施，理科课程评分+10%/级，教学质量+5%/级，学生智力/创造力+", 40.0, 1.5, 3, FacilityCategory.TEACHING),
    ART_STUDIO("艺术工作室", "美术/音乐教学，艺术课程评分+15%/级，教学质量+5%/级", 25.0, 0.8, 3, FacilityCategory.TEACHING),

    // Support facilities
    LIBRARY("图书馆", "课程研发效率+10%/级，学生智力+", 30.0, 1.0, 5, FacilityCategory.SUPPORT),
    SPORTS_FIELD("运动场", "体育设施，招生加成+5%/级，学生体质+", 45.0, 1.2, 3, FacilityCategory.SUPPORT),
    CANTEEN("食堂", "降低教师疲劳积累-15%/级，学生体质+", 20.0, 0.8, 3, FacilityCategory.SUPPORT),
    DORMITORY("宿舍楼", "住宿条件，招生加成+20%/级，学生社交+", 80.0, 2.0, 3, FacilityCategory.SUPPORT),

    // Prestige facilities
    AUDITORIUM("大礼堂", "声誉增长+5%/级，事件奖励加成+20%/级，学生社交+", 100.0, 2.5, 2, FacilityCategory.PRESTIGE),
    CONFERENCE_CENTER("会议中心", "学术声誉+8%/级，事件奖励加成+10%/级", 60.0, 1.8, 3, FacilityCategory.PRESTIGE),
    EMPLOYMENT_CENTER("就业指导中心", "毕业就业率+6%/级，学生社交+", 45.0, 1.5, 3, FacilityCategory.SUPPORT),
    GARDEN("校园花园", "教师忠诚度衰减-20%/级，学生品德+", 12.0, 0.4, 3, FacilityCategory.PRESTIGE),
    GATE("校门/门面", "学校形象，声誉增长+10%/级", 8.0, 0.2, 3, FacilityCategory.PRESTIGE)
}

enum class FacilityCategory(val displayName: String) {
    TEACHING("教学设施"),
    SUPPORT("配套设施"),
    PRESTIGE("形象设施")
}

/**
 * Calculates combined bonuses from all school facilities.
 */
object FacilityBonusCalculator {

    data class FacilityBonuses(
        val teachingQualityBonus: Float = 0f,     // % boost to course quality
        val enrollmentBonus: Float = 0f,           // % boost to enrollment
        val researchBonus: Float = 0f,             // % boost to research speed
        val fatigueReduction: Float = 0f,          // % reduction in fatigue rate
        val loyaltyDecayReduction: Float = 0f,     // % reduction in loyalty loss
        val reputationGrowthBonus: Float = 0f,     // % boost to reputation gain
        val eventRewardBonus: Float = 0f,          // % boost to event cash/rep rewards
        val scienceBonus: Float = 0f,              // bonus for science subjects
        val programmingBonus: Float = 0f,          // bonus for programming
        val artBonus: Float = 0f                   // bonus for art
    )

    fun calculate(facilities: List<Facility>): FacilityBonuses {
        var teachingQuality = 0f
        var enrollment = 0f
        var research = 0f
        var fatigueReduction = 0f
        var loyaltyDecay = 0f
        var reputationGrowth = 0f
        var eventReward = 0f
        var science = 0f
        var programming = 0f
        var art = 0f

        facilities.filter { it.isOperational }.forEach { facility ->
            val levelMultiplier = facility.level.toFloat()
            when (facility.type) {
                FacilityType.CLASSROOM -> enrollment += 0.05f * levelMultiplier
                FacilityType.MULTIMEDIA_ROOM -> teachingQuality += 0.10f * levelMultiplier
                FacilityType.LABORATORY -> {
                    teachingQuality += 0.05f * levelMultiplier
                    science += 0.15f * levelMultiplier
                }
                FacilityType.COMPUTER_LAB -> {
                    teachingQuality += 0.05f * levelMultiplier
                    programming += 0.20f * levelMultiplier
                }
                FacilityType.ART_STUDIO -> {
                    teachingQuality += 0.05f * levelMultiplier
                    art += 0.15f * levelMultiplier
                }
                FacilityType.LIBRARY -> research += 0.10f * levelMultiplier
                FacilityType.SPORTS_FIELD -> enrollment += 0.05f * levelMultiplier
                FacilityType.CANTEEN -> fatigueReduction += 0.15f * levelMultiplier
                FacilityType.DORMITORY -> enrollment += 0.20f * levelMultiplier
                FacilityType.AUDITORIUM -> {
                    eventReward += 0.20f * levelMultiplier
                    reputationGrowth += 0.05f * levelMultiplier
                }
                FacilityType.GARDEN -> loyaltyDecay += 0.20f * levelMultiplier
                FacilityType.GATE -> reputationGrowth += 0.10f * levelMultiplier
                FacilityType.CONFERENCE_CENTER -> {
                    reputationGrowth += 0.08f * levelMultiplier
                    eventReward += 0.10f * levelMultiplier
                }
                FacilityType.EMPLOYMENT_CENTER -> reputationGrowth += 0.06f * levelMultiplier
            }
        }

        return FacilityBonuses(
            teachingQualityBonus = teachingQuality,
            enrollmentBonus = enrollment,
            researchBonus = research,
            fatigueReduction = fatigueReduction,
            loyaltyDecayReduction = loyaltyDecay,
            reputationGrowthBonus = reputationGrowth,
            eventRewardBonus = eventReward,
            scienceBonus = science,
            programmingBonus = programming,
            artBonus = art
        )
    }

    /**
     * Calculate the upgrade cost for a facility.
     */
    fun getUpgradeCost(facility: Facility): Double {
        return facility.type.baseCost * (facility.level + 1) * 1.5
    }

    /**
     * Calculate total monthly maintenance for all facilities.
     */
    fun getTotalMaintenance(facilities: List<Facility>): Double {
        return facilities.sumOf { it.maintenanceCost }
    }
}
