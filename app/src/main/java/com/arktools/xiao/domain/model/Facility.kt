package com.arktools.xiao.domain.model

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
    val id: String = java.util.UUID.randomUUID().toString(),  // 唯一标识，防止同类型设施操作错位
    var constructionDaysLeft: Int = 0
) {
    val maintenanceCost: Double
        get() = if (isConstructing) 0.0 else type.baseMaintenance * level

    val isOperational: Boolean
        get() = condition > 20f && constructionDaysLeft <= 0

    val isConstructing: Boolean
        get() = constructionDaysLeft > 0
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

    fun librarySeats(level: Int): Int = 80 + (level - 1).coerceAtLeast(0) * 40

    fun labBenches(level: Int): Int = 24 + (level - 1).coerceAtLeast(0) * 12

    fun computerSeats(level: Int): Int = 40 + (level - 1).coerceAtLeast(0) * 16

    fun sportsCapacity(level: Int): Int = 200 + (level - 1).coerceAtLeast(0) * 80

    fun studioCapacity(level: Int): Int = 20 + (level - 1).coerceAtLeast(0) * 8

    fun gardenPlots(level: Int): Int = 1 + (level - 1).coerceAtLeast(0)

    /** 第 n 栋（0 起）的边际贡献，避免十几栋实验室把加成顶爆。 */
    fun diminishing(index: Int): Float = when {
        index <= 0 -> 1f
        index == 1 -> 0.7f
        index == 2 -> 0.45f
        else -> 0.25f
    }

    fun stacked(facilities: List<Facility>, type: FacilityType, perLevel: (Int) -> Int): Int {
        return facilities
            .filter { it.type == type && it.isOperational }
            .sortedByDescending { it.level }
            .mapIndexed { index, facility -> (perLevel(facility.level) * diminishing(index)).toInt().coerceAtLeast(1) }
            .sum()
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

    fun totalLibrarySeats(facilities: List<Facility>): Int = stacked(facilities, FacilityType.LIBRARY, ::librarySeats)
    fun totalLabBenches(facilities: List<Facility>): Int = stacked(facilities, FacilityType.LABORATORY, ::labBenches)
    fun totalComputerSeats(facilities: List<Facility>): Int = stacked(facilities, FacilityType.COMPUTER_LAB, ::computerSeats)
    fun totalSportsCapacity(facilities: List<Facility>): Int = stacked(facilities, FacilityType.SPORTS_FIELD, ::sportsCapacity)
    fun totalStudioCapacity(facilities: List<Facility>): Int = stacked(facilities, FacilityType.ART_STUDIO, ::studioCapacity)
    fun totalGardenPlots(facilities: List<Facility>): Int = stacked(facilities, FacilityType.GARDEN, ::gardenPlots)

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

    /** 后勤保障中心等级对应的全校设施维护费系数（Lv0 不变，每级 -6%，封底 0.82）。 */
    fun logisticsMaintenanceFactor(level: Int): Double =
        (1.0 - 0.06 * level.coerceIn(0, 3)).coerceAtLeast(0.82)

    /** 国际交流中心等级对应的国际生学费收入系数（每级 +10%）。 */
    fun internationalIncomeMultiplier(level: Int): Double =
        1.0 + 0.10 * level.coerceIn(0, 2)
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
    CLASSROOM("标准教室", "教室学位直接决定招生人数。Lv1=90人，两间教室按两间加总。", 18.0, 0.6, 5, FacilityCategory.TEACHING, repeatable = true),
    MULTIMEDIA_ROOM("多媒体教室", "公开课和演示课场地，打开演练后教学质量上升。", 35.0, 1.2, 3, FacilityCategory.TEACHING, repeatable = true),
    LABORATORY("实验室", "理学院课题台位。打开夜间实验室可加快科研日。", 50.0, 1.8, 3, FacilityCategory.TEACHING, repeatable = true),
    COMPUTER_LAB("计算机房", "工学院机位，决定信息技术课容量和课题速度。", 40.0, 1.5, 3, FacilityCategory.TEACHING, repeatable = true),
    ART_STUDIO("艺术工作室", "艺术学院工位，打开汇演后满意度和口碑上升。", 25.0, 0.8, 3, FacilityCategory.TEACHING, repeatable = true),

    // Support facilities
    LIBRARY("图书馆", "阅览席决定科研速度。可开夜间阅览加快课题。", 30.0, 1.0, 5, FacilityCategory.SUPPORT, repeatable = true),
    SPORTS_FIELD("运动场", "运动会和体育课场地。可办校运会拉招生和满意度。", 45.0, 1.2, 3, FacilityCategory.SUPPORT, repeatable = true),
    CANTEEN("食堂", "餐位决定能不能吃上热饭。可加窗口，不够会投诉。", 28.0, 1.0, 3, FacilityCategory.SUPPORT, repeatable = true),
    DORMITORY("宿舍楼", "床位卡招生上限。点开本楼可看每层住了谁。", 95.0, 2.4, 3, FacilityCategory.SUPPORT, repeatable = true),

    // Prestige facilities
    AUDITORIUM("大礼堂", "声誉增长+5%/级，事件奖励加成+20%/级，学生社交+", 100.0, 2.5, 2, FacilityCategory.PRESTIGE),
    CONFERENCE_CENTER("会议中心", "学术声誉+8%/级，事件奖励加成+10%/级", 60.0, 1.8, 3, FacilityCategory.PRESTIGE),
    EMPLOYMENT_CENTER("就业指导中心", "提升毕业去向质量：就业中心等级与政府评级会提高高质量去向概率", 45.0, 1.5, 3, FacilityCategory.SUPPORT),
    INCUBATOR("校企合作中心", "就业体系升级：创业与深造概率再提升，衔接校企实习资源", 80.0, 2.2, 2, FacilityCategory.SUPPORT),
    INTERNATIONAL_CENTER("国际交流中心", "国际生学费收入+10%/级，保障海外合作年度声誉", 90.0, 2.6, 2, FacilityCategory.SUPPORT),
    LOGISTICS_CENTER("后勤保障中心", "全校设施月维护费-6%/级（最高-18%），降低运营压力", 70.0, 2.0, 3, FacilityCategory.SUPPORT),
    GARDEN("校园花园", "教师忠诚度衰减-20%/级，学生品德+。可重复布置园区", 12.0, 0.4, 3, FacilityCategory.PRESTIGE, repeatable = true),
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

        facilities.filter { it.isOperational }
            .groupBy { it.type }
            .forEach { (type, group) ->
                group.sortedByDescending { it.level }.forEachIndexed { index, facility ->
                    val levelMultiplier = facility.level.toFloat() * FacilityCapacity.diminishing(index)
                    when (type) {
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
                        FacilityType.INCUBATOR -> enrollment += 0.03f * levelMultiplier
                        FacilityType.INTERNATIONAL_CENTER -> reputationGrowth += 0.06f * levelMultiplier
                        FacilityType.LOGISTICS_CENTER -> { /* 效果体现在维护费折扣，不在加成面板 */ }
                    }
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
