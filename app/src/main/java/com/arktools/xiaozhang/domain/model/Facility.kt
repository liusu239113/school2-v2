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

/**
 * 自由建造容量：同一类型可重复落座，规模扩张必须靠多栋楼，而不是每种只建一座。
 * 宿舍/食堂按床位和餐位卡住体验；教室按班槽卡住招生。
 */
object FacilityCapacity {
    fun bedsPerDorm(level: Int): Int = 80 + (level - 1).coerceAtLeast(0) * 40

    fun seatsPerCanteen(level: Int): Int = 120 + (level - 1).coerceAtLeast(0) * 60

    fun classSlots(level: Int): Int = when (level.coerceAtLeast(1)) {
        1 -> 3
        2 -> 4
        3 -> 6
        4 -> 7
        else -> 9
    }

    fun totalBeds(facilities: List<Facility>): Int =
        facilities.filter { it.type == FacilityType.DORMITORY && it.isOperational }
            .sumOf { bedsPerDorm(it.level) }

    fun totalCanteenSeats(facilities: List<Facility>): Int =
        facilities.filter { it.type == FacilityType.CANTEEN && it.isOperational }
            .sumOf { seatsPerCanteen(it.level) }

    fun totalClassSlots(facilities: List<Facility>): Int =
        facilities.filter { it.type == FacilityType.CLASSROOM && it.isOperational }
            .sumOf { classSlots(it.level) }

    fun occupancyRatio(students: Int, capacity: Int): Float {
        if (capacity <= 0) return if (students <= 0) 0f else 2f
        return students.toFloat() / capacity.toFloat()
    }

    fun overcrowdingPenalty(ratio: Float): Float = when {
        ratio <= 1f -> 0f
        ratio <= 1.25f -> 8f
        ratio <= 1.6f -> 16f
        else -> 28f
    }

    /** 同类型第 n 栋（从 0 起）的造价递增，避免开局一次买齐。 */
    fun repeatCost(type: FacilityType, existingCount: Int): Double {
        if (!type.repeatable) return type.baseCost
        val bump = 1.0 + existingCount * 0.35
        return type.baseCost * bump
    }

    fun canRepeat(type: FacilityType): Boolean = type.repeatable
}

enum class FacilityType(
    val displayName: String,
    val description: String,
    val baseCost: Double,       // 建设成本（万元）
    val baseMaintenance: Double, // 月维护费（万元）
    val maxLevel: Int,
    val category: FacilityCategory,
    val repeatable: Boolean = false
) {
    // Teaching facilities — 建设成本适中，维护费合理（占学费收入10-20%为宜）
    CLASSROOM("标准教室", "提供班级槽位(Lv1:3班,Lv2:4班,Lv3:6班,Lv4:7班,Lv5:9班)，可重复建造扩容", 18.0, 0.6, 5, FacilityCategory.TEACHING, repeatable = true),
    MULTIMEDIA_ROOM("多媒体教室", "现代化教学设备，教学质量+10%/级", 35.0, 1.2, 3, FacilityCategory.TEACHING),
    LABORATORY("实验室", "理科实验设施，理科课程评分+15%/级，教学质量+5%/级", 50.0, 1.8, 3, FacilityCategory.TEACHING),
    COMPUTER_LAB("计算机房", "信息技术设施，理科课程评分+10%/级，教学质量+5%/级，学生智力/创造力+", 40.0, 1.5, 3, FacilityCategory.TEACHING),
    ART_STUDIO("艺术工作室", "美术/音乐教学，艺术课程评分+15%/级，教学质量+5%/级", 25.0, 0.8, 3, FacilityCategory.TEACHING),

    // Support facilities
    LIBRARY("图书馆", "课程研发效率+10%/级，学生智力+", 30.0, 1.0, 5, FacilityCategory.SUPPORT),
    SPORTS_FIELD("运动场", "体育设施，招生加成+5%/级，学生体质+", 45.0, 1.2, 3, FacilityCategory.SUPPORT),
    CANTEEN("食堂", "餐位决定饮食质量。可重复建造扩容", 28.0, 1.0, 3, FacilityCategory.SUPPORT, repeatable = true),
    DORMITORY("宿舍楼", "床位决定住宿体验。可重复建造扩容", 95.0, 2.4, 3, FacilityCategory.SUPPORT, repeatable = true),

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
                FacilityType.CLASSROOM -> enrollment += 0.02f * levelMultiplier
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
                FacilityType.DORMITORY -> enrollment += 0.06f * levelMultiplier
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
