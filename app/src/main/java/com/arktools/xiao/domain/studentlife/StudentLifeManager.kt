package com.arktools.xiao.domain.studentlife

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 学生生活系统
 * 管理宿舍、食堂、健康、心理四大生活维度
 * 生活满意度影响学业表现和学校口碑
 */

enum class LifeAspect(val displayName: String, val icon: String, val description: String) {
    DORMITORY("宿舍", "🏠", "住宿条件、设施完善度"),
    CAFETERIA("食堂", "🍽️", "饮食质量、菜品丰富度"),
    HEALTH("健康", "💊", "医疗保障、运动设施"),
    PSYCHOLOGY("心理", "🧠", "心理辅导、压力管理")
}

enum class FacilityQuality(
    val displayName: String,
    val satisfactionBase: Float,
    val costMultiplier: Float,
    val requiredSchoolLevel: Int,  // 需要学校等级才能升级到此档
    val upgradeCost: Long,         // 升级到此档的费用（万元）v2.9: 大幅提高
    val baseMaintenanceCost: Long  // 此档每月基础维护费（万元）v2.9: 大幅提高
) {
    POOR("简陋", 20f, 0.5f, 1, 0L, 2L),
    BASIC("基础", 40f, 1.0f, 1, 20L, 5L),
    STANDARD("标准", 60f, 1.5f, 2, 80L, 15L),
    GOOD("良好", 75f, 2.5f, 3, 300L, 40L),
    EXCELLENT("优秀", 90f, 4.0f, 4, 1000L, 80L),
    PREMIUM("顶级", 98f, 7.0f, 5, 3000L, 150L)
}

data class LifeFacility(
    val aspect: LifeAspect,
    var quality: FacilityQuality = FacilityQuality.BASIC,
    var capacity: Int = 50,            // 容纳人数
    var currentLoad: Int = 0,          // 当前使用人数
    var maintenanceLevel: Float = 100f, // 维护度(0-100)，逐月衰减
    var monthlyMaintenanceCost: Long = 5L,  // 单位：万元，默认=BASIC档
    var lastUpgradeYear: Int = 0,
    var staffCount: Int = 1,           // 工作人员数
    var specialPrograms: MutableList<String> = mutableListOf()  // 特色项目
)

data class LifeSatisfactionScore(
    val aspect: LifeAspect,
    val score: Float,     // 0-100
    val trend: Float,     // 月变化趋势
    val issues: List<String>  // 当前问题
)

data class StudentLifeState(
    val facilities: Map<LifeAspect, LifeFacility> = emptyMap(),
    val overallSatisfaction: Float = 50f,
    val satisfactionScores: Map<LifeAspect, LifeSatisfactionScore> = emptyMap(),
    val monthlyExpenses: Long = 0L,
    val issues: List<LifeIssue> = emptyList(),
    val programs: List<SpecialProgram> = emptyList(),
    val academicImpact: Float = 0f,
    val retentionImpact: Float = 0f,
    val lastProcessedYear: Int = 0,
    val lastProcessedMonth: Int = 0
)

data class LifeIssue(
    val id: String,
    val aspect: LifeAspect,
    val title: String,
    val description: String,
    val severity: IssueSeverity,
    val satisfactionPenalty: Float,
    var resolved: Boolean = false
)

enum class IssueSeverity(val displayName: String, val color: String) {
    LOW("轻微", "yellow"),
    MEDIUM("中等", "orange"),
    HIGH("严重", "red"),
    CRITICAL("紧急", "darkred")
}

data class SpecialProgram(
    val id: String,
    val name: String,
    val aspect: LifeAspect,
    val description: String,
    val monthlyCost: Long,
    val satisfactionBoost: Float,
    var active: Boolean = true
)

data class LifeMonthlyResult(
    val totalExpenses: Long = 0,
    val satisfactionChange: Float = 0f,
    val newIssues: List<LifeIssue> = emptyList(),
    val resolvedIssues: List<LifeIssue> = emptyList(),
    val academicImpact: Float = 0f,
    val events: List<LifeEvent> = emptyList()
)

sealed class LifeEvent {
    data class FacilityDegraded(val aspect: LifeAspect, val newMaintenance: Float) : LifeEvent()
    data class IssueOccurred(val issue: LifeIssue) : LifeEvent()
    data class ProgramEffect(val program: String, val boost: Float) : LifeEvent()
    data class OvercrowdingAlert(val aspect: LifeAspect, val loadPercent: Int) : LifeEvent()
}

@Singleton
class StudentLifeManager @Inject constructor() {

    private val _state = MutableStateFlow(StudentLifeState())
    val state: StateFlow<StudentLifeState> = _state.asStateFlow()

    fun syncCampusCapacity(dormBeds: Int, canteenSeats: Int) {
        _state.update { state ->
            val facilities = state.facilities.toMutableMap()
            facilities[LifeAspect.DORMITORY]?.let { dorm ->
                facilities[LifeAspect.DORMITORY] = dorm.copy(capacity = dormBeds.coerceAtLeast(1))
            }
            facilities[LifeAspect.CAFETERIA]?.let { cafe ->
                facilities[LifeAspect.CAFETERIA] = cafe.copy(capacity = canteenSeats.coerceAtLeast(1))
            }
            state.copy(facilities = facilities)
        }
    }

    fun reset() {
        val initialFacilities = LifeAspect.entries.associateWith { aspect ->
            LifeFacility(
                aspect = aspect,
                quality = FacilityQuality.BASIC,
                capacity = 50,
                monthlyMaintenanceCost = when (aspect) {
                    LifeAspect.DORMITORY -> 3L
                    LifeAspect.CAFETERIA -> 4L
                    LifeAspect.HEALTH -> 2L
                    LifeAspect.PSYCHOLOGY -> 2L
                }
            )
        }
        _state.value = StudentLifeState(facilities = initialFacilities)
        recalculateSatisfaction()
    }

    private val random = java.util.Random()

    // 可开设的特色项目（monthlyCost 单位：万元）
    // 费用显著提高，有实质成本压力
    private val availablePrograms = listOf(
        SpecialProgram("prog_01", "营养早餐计划", LifeAspect.CAFETERIA, "为学生提供营养均衡的免费早餐", 8L, 6f),
        SpecialProgram("prog_02", "心理热线服务", LifeAspect.PSYCHOLOGY, "24小时心理咨询热线", 6L, 7f),
        SpecialProgram("prog_03", "晨跑打卡", LifeAspect.HEALTH, "组织学生每日晨跑，增强体质", 5L, 5f),
        SpecialProgram("prog_04", "宿舍文化建设", LifeAspect.DORMITORY, "美化宿舍环境，举办宿舍评比", 6L, 5f),
        SpecialProgram("prog_05", "减压工作坊", LifeAspect.PSYCHOLOGY, "定期举办压力管理工作坊", 12L, 9f),
        SpecialProgram("prog_06", "有机蔬菜基地", LifeAspect.CAFETERIA, "校内自种有机蔬菜供应食堂", 15L, 7f),
        SpecialProgram("prog_07", "健身房免费开放", LifeAspect.HEALTH, "学生可免费使用校内健身设施", 18L, 8f),
        SpecialProgram("prog_08", "智能宿舍系统", LifeAspect.DORMITORY, "安装智能门禁、空调控制系统", 25L, 12f)
    )

    init {
        val initialFacilities = LifeAspect.entries.associateWith { aspect ->
            LifeFacility(
                aspect = aspect,
                quality = FacilityQuality.BASIC,
                capacity = 50,
                monthlyMaintenanceCost = when (aspect) {  // 单位：万元 — 初始BASIC档每月维护费
                    LifeAspect.DORMITORY -> 1L   // 宿舍维护费（水电+保洁）
                    LifeAspect.CAFETERIA -> 1L   // 食堂费用（食材+人工）
                    LifeAspect.HEALTH -> 1L      // 医务室
                    LifeAspect.PSYCHOLOGY -> 1L   // 心理辅导室
                }
            )
        }
        _state.update { it.copy(facilities = initialFacilities) }
        recalculateSatisfaction()
    }

    /**
     * 获取可开设的项目列表
     */
    fun getAvailablePrograms(): List<SpecialProgram> {
        val activeIds = _state.value.programs.filter { it.active }.map { it.id }
        return availablePrograms.filter { it.id !in activeIds }
    }

    /**
     * 开设特色项目
     */
    fun activateProgram(programId: String): Boolean {
        val program = availablePrograms.find { it.id == programId }
            ?: return false
        var activated = false
        _state.update { state ->
            if (state.programs.any { it.id == programId && it.active }) {
                return@update state
            }
            activated = true
            state.copy(
                programs = state.programs.filterNot { it.id == programId } +
                    program.copy(active = true)
            )
        }
        if (activated) recalculateSatisfaction()
        return activated
    }

    /**
     * 关闭特色项目
     */
    fun deactivateProgram(programId: String): Boolean {
        var deactivated = false
        _state.update { state ->
            if (state.programs.none { it.id == programId && it.active }) {
                return@update state
            }
            deactivated = true
            state.copy(programs = state.programs.map {
                if (it.id == programId) it.copy(active = false) else it
            })
        }
        if (deactivated) recalculateSatisfaction()
        return deactivated
    }

    /**
     * 检查设施是否可以升级（需满足学校等级要求）
     */
    fun canUpgradeFacility(aspect: LifeAspect, schoolLevel: Int): Boolean {
        val facility = _state.value.facilities[aspect] ?: return false
        val currentQualityIndex = FacilityQuality.entries.indexOf(facility.quality)
        if (currentQualityIndex >= FacilityQuality.entries.size - 1) return false
        val nextQuality = FacilityQuality.entries[currentQualityIndex + 1]
        return schoolLevel >= nextQuality.requiredSchoolLevel
    }

    /**
     * 获取升级费用（不执行升级）
     */
    fun getUpgradeCost(aspect: LifeAspect): Long {
        val facility = _state.value.facilities[aspect] ?: return 0L
        val currentQualityIndex = FacilityQuality.entries.indexOf(facility.quality)
        if (currentQualityIndex >= FacilityQuality.entries.size - 1) return 0L
        return FacilityQuality.entries[currentQualityIndex + 1].upgradeCost
    }

    /**
     * 应用已完成付款的设施升级。费用和等级在提交时重新校验，避免过期预览改变设施状态。
     */
    fun applyFacilityUpgrade(aspect: LifeAspect, schoolLevel: Int, expectedCost: Long): Boolean {
        if (expectedCost <= 0L) return false
        var applied = false
        _state.update { state ->
            val facilities = state.facilities.toMutableMap()
            val facility = facilities[aspect] ?: return@update state
            val currentQualityIndex =
                FacilityQuality.entries.indexOf(facility.quality)
            if (currentQualityIndex >= FacilityQuality.entries.size - 1) {
                return@update state
            }
            val nextQuality =
                FacilityQuality.entries[currentQualityIndex + 1]
            if (schoolLevel < nextQuality.requiredSchoolLevel ||
                nextQuality.upgradeCost != expectedCost
            ) {
                return@update state
            }
            facilities[aspect] = facility.copy(
                quality = nextQuality,
                maintenanceLevel = 100f,
                capacity = (facility.capacity * 1.2f).toInt(),
                monthlyMaintenanceCost = nextQuality.baseMaintenanceCost
            )
            applied = true
            state.copy(facilities = facilities)
        }
        if (applied) recalculateSatisfaction()
        return applied
    }

    /**
     * 升级设施（需要学校等级满足条件）
     * 返回升级费用，0表示无法升级
     */
    fun upgradeFacility(aspect: LifeAspect, schoolLevel: Int): Long {
        val currentState = _state.value
        val facility = currentState.facilities[aspect] ?: return 0L
        val currentQualityIndex = FacilityQuality.entries.indexOf(facility.quality)
        if (currentQualityIndex >= FacilityQuality.entries.size - 1) return 0L

        val nextQuality = FacilityQuality.entries[currentQualityIndex + 1]

        // 学校等级门槛检查
        if (schoolLevel < nextQuality.requiredSchoolLevel) return 0L

        val upgradeCost = nextQuality.upgradeCost

        _state.update { state ->
            val facilities = state.facilities.toMutableMap()
            val updated = facility.copy(
                quality = nextQuality,
                maintenanceLevel = 100f,
                capacity = (facility.capacity * 1.2f).toInt(),  // 升级增加20%容量
                monthlyMaintenanceCost = nextQuality.baseMaintenanceCost  // 维护费随档次大幅增加
            )
            facilities[aspect] = updated
            state.copy(facilities = facilities)
        }
        recalculateSatisfaction()
        return upgradeCost
    }

    /**
     * 获取扩容费用预估（不执行扩容）
     */
    fun getExpandCost(aspect: LifeAspect, additionalCapacity: Int): Long {
        val facility = _state.value.facilities[aspect] ?: return 0L
        val unitCostPer10 = when (facility.quality) {
            FacilityQuality.POOR -> 2L
            FacilityQuality.BASIC -> 3L
            FacilityQuality.STANDARD -> 5L
            FacilityQuality.GOOD -> 8L
            FacilityQuality.EXCELLENT -> 12L
            FacilityQuality.PREMIUM -> 20L
        }
        val batches = ((additionalCapacity + 9) / 10).toLong()
        return batches * unitCostPer10
    }

    /**
     * 应用已完成付款的扩容。
     */
    fun applyCapacityExpansion(aspect: LifeAspect, additionalCapacity: Int, expectedCost: Long): Boolean {
        if (additionalCapacity <= 0 || expectedCost <= 0L) return false
        if (getExpandCost(aspect, additionalCapacity) != expectedCost) return false
        var applied = false
        _state.update { state ->
            val facilities = state.facilities.toMutableMap()
            val facility = facilities[aspect] ?: return@update state
            facilities[aspect] = facility.copy(
                capacity = facility.capacity + additionalCapacity,
                monthlyMaintenanceCost = facility.monthlyMaintenanceCost +
                    (facility.quality.baseMaintenanceCost * additionalCapacity / 250).coerceAtLeast(1L)
            )
            applied = true
            state.copy(facilities = facilities)
        }
        if (applied) recalculateSatisfaction()
        return applied
    }

    /**
     * 扩容（费用随现有容量递增，不再是固定1万/人）
     * 每10人为一批次，费用 = 批次数 × 单价（随质量档次递增）
     */
    fun expandCapacity(aspect: LifeAspect, additionalCapacity: Int): Long {
        val facility = _state.value.facilities[aspect] ?: return 0L
        // 单价随设施质量递增: BASIC=3万/10人, STANDARD=5万, GOOD=8万, EXCELLENT=12万, PREMIUM=20万
        val unitCostPer10 = when (facility.quality) {
            FacilityQuality.POOR -> 2L
            FacilityQuality.BASIC -> 3L
            FacilityQuality.STANDARD -> 5L
            FacilityQuality.GOOD -> 8L
            FacilityQuality.EXCELLENT -> 12L
            FacilityQuality.PREMIUM -> 20L
        }
        val batches = ((additionalCapacity + 9) / 10).toLong()  // 向上取整到10人批次
        val cost = batches * unitCostPer10

        _state.update { state ->
            val facilities = state.facilities.toMutableMap()
            val f = facilities[aspect] ?: return@update state
            facilities[aspect] = f.copy(
                capacity = f.capacity + additionalCapacity,
                // 扩容后维护费线性增加（每增加50人，固定增加基础档维护费的20%）
                // 避免多次扩容导致指数级增长
                monthlyMaintenanceCost = f.monthlyMaintenanceCost +
                    (f.quality.baseMaintenanceCost * additionalCapacity / 250).coerceAtLeast(1L)
            )
            state.copy(facilities = facilities)
        }
        return cost
    }

    /**
     * 更新学生人数（影响负载）
     */
    fun updateStudentCount(studentCount: Int) {
        _state.update { state ->
            val facilities = state.facilities.toMutableMap()
            LifeAspect.entries.forEach { aspect ->
                val facility = facilities[aspect] ?: return@forEach
                facilities[aspect] = facility.copy(currentLoad = studentCount)
            }
            state.copy(facilities = facilities)
        }
        recalculateSatisfaction()
    }

    fun hasProcessedMonth(year: Int, month: Int): Boolean {
        val state = _state.value
        return state.lastProcessedYear == year &&
            state.lastProcessedMonth == month
    }

    /**
     * 每月推进
     */
    fun advanceMonth(studentCount: Int, currentYear: Int, currentMonth: Int): LifeMonthlyResult {
        if (hasProcessedMonth(currentYear, currentMonth)) {
            return LifeMonthlyResult()
        }
        var totalExpenses = 0L
        val events = mutableListOf<LifeEvent>()
        val newIssues = mutableListOf<LifeIssue>()
        val resolvedIssues = mutableListOf<LifeIssue>()

        _state.update { state ->
            val facilities = state.facilities.toMutableMap()

            // 设施维护衰减 + 费用计算（按入住率缩放，避免空置设施收取全额费用）
            LifeAspect.entries.forEach { aspect ->
                val facility = facilities[aspect] ?: return@forEach
                val degradation = 2f + (facility.currentLoad.toFloat() / facility.capacity.coerceAtLeast(1)) * 3f
                val newMaintenance = (facility.maintenanceLevel - degradation).coerceAtLeast(0f)

                // 维护费按入住率缩放：最低30%（设施开放基础成本），满载100%
                val occupancyRatio = if (facility.capacity > 0) {
                    (facility.currentLoad.toFloat() / facility.capacity.toFloat()).coerceIn(0.3f, 1.0f)
                } else 0.3f
                val scaledCost = (facility.monthlyMaintenanceCost.toFloat() * occupancyRatio).toLong().coerceAtLeast(1L)
                totalExpenses += scaledCost

                if (newMaintenance < 30f && facility.maintenanceLevel >= 30f) {
                    events.add(LifeEvent.FacilityDegraded(aspect, newMaintenance))
                }

                // 过载检测
                val loadPercent = if (facility.capacity > 0) {
                    (facility.currentLoad * 100) / facility.capacity
                } else 100
                if (loadPercent > 120) {
                    events.add(LifeEvent.OvercrowdingAlert(aspect, loadPercent))
                }

                facilities[aspect] = facility.copy(
                    maintenanceLevel = newMaintenance,
                    currentLoad = studentCount
                )
            }

            // 特色项目费用
            val activePrograms = state.programs.filter { it.active }
            activePrograms.forEach { program ->
                totalExpenses += program.monthlyCost
                if (random.nextFloat() < 0.3f) {
                    events.add(LifeEvent.ProgramEffect(program.name, program.satisfactionBoost))
                }
            }

            val dorm = facilities[LifeAspect.DORMITORY]
            val cafe = facilities[LifeAspect.CAFETERIA]
            val dormLoad = if (dorm != null && dorm.capacity > 0) {
                dorm.currentLoad.toFloat() / dorm.capacity
            } else 0f
            val cafeLoad = if (cafe != null && cafe.capacity > 0) {
                cafe.currentLoad.toFloat() / cafe.capacity
            } else 0f
            val avgMaintenance = facilities.values.map { it.maintenanceLevel }.average().toFloat()
            val issue = pickConditionIssue(
                year = currentYear,
                month = currentMonth,
                overall = state.overallSatisfaction,
                dormLoad = dormLoad,
                cafeLoad = cafeLoad,
                avgMaintenance = avgMaintenance
            )
            if (issue != null) {
                newIssues.add(issue)
                events.add(LifeEvent.IssueOccurred(issue))
            }

            // 自动解决旧问题(15%概率 — 大多需要玩家主动处理)
            val updatedIssues = state.issues.map { issue ->
                if (!issue.resolved && random.nextFloat() < 0.15f) {
                    resolvedIssues.add(issue)
                    issue.copy(resolved = true)
                } else issue
            }

            state.copy(
                facilities = facilities,
                monthlyExpenses = totalExpenses,
                issues = updatedIssues + newIssues,
                lastProcessedYear = currentYear,
                lastProcessedMonth = currentMonth
            )
        }

        recalculateSatisfaction()

        val currentState = _state.value
        return LifeMonthlyResult(
            totalExpenses = totalExpenses,
            satisfactionChange = currentState.overallSatisfaction - 50f, // relative to baseline
            newIssues = newIssues,
            resolvedIssues = resolvedIssues,
            academicImpact = currentState.academicImpact,
            events = events
        )
    }

    /**
     * 获取单个设施维修费用，不改变设施状态。
     */
    fun getRepairCost(aspect: LifeAspect): Long {
        val facility = _state.value.facilities[aspect] ?: return 0L
        if (facility.maintenanceLevel >= 100f) return 0L
        val degradation = (100f - facility.maintenanceLevel) / 100f
        val maxRepairCost = when (facility.quality) {
            FacilityQuality.POOR -> 3L
            FacilityQuality.BASIC -> 8L
            FacilityQuality.STANDARD -> 15L
            FacilityQuality.GOOD -> 30L
            FacilityQuality.EXCELLENT -> 50L
            FacilityQuality.PREMIUM -> 80L
        }
        return (degradation * maxRepairCost).toLong().coerceAtLeast(2L)
    }

    /**
     * 应用已完成付款的单个设施维修。
     */
    fun applyFacilityRepair(aspect: LifeAspect, expectedCost: Long): Boolean {
        if (expectedCost <= 0L || getRepairCost(aspect) != expectedCost) return false
        var applied = false
        _state.update { state ->
            val facilities = state.facilities.toMutableMap()
            val facility = facilities[aspect] ?: return@update state
            if (facility.maintenanceLevel >= 100f) return@update state
            facilities[aspect] = facility.copy(maintenanceLevel = 100f)
            applied = true
            state.copy(facilities = facilities)
        }
        if (applied) recalculateSatisfaction()
        return applied
    }

    /**
     * 维修设施
     * 返回值单位：万元（与school.cash一致）
     * 维修费 = 损耗比例 × 设施档次系数，高档设施维修更贵
     */
    fun repairFacility(aspect: LifeAspect): Long {
        val facility = _state.value.facilities[aspect] ?: return 0L
        val degradation = (100f - facility.maintenanceLevel) / 100f
        // 基础维修费随设施等级递增: BASIC=8万满修, STANDARD=15万, GOOD=30万, EXCELLENT=50万, PREMIUM=80万
        val maxRepairCost = when (facility.quality) {
            FacilityQuality.POOR -> 3L
            FacilityQuality.BASIC -> 8L
            FacilityQuality.STANDARD -> 15L
            FacilityQuality.GOOD -> 30L
            FacilityQuality.EXCELLENT -> 50L
            FacilityQuality.PREMIUM -> 80L
        }
        val repairCost = (degradation * maxRepairCost).toLong().coerceAtLeast(2L)
        _state.update { state ->
            val facilities = state.facilities.toMutableMap()
            facilities[aspect] = facility.copy(maintenanceLevel = 100f)
            state.copy(facilities = facilities)
        }
        recalculateSatisfaction()
        return repairCost
    }

    /**
     * 获取一键维修全部设施的总费用，不改变设施状态。
     */
    fun getRepairAllCost(): Long {
        return _state.value.facilities.values.sumOf { facility ->
            if (facility.maintenanceLevel >= 100f) {
                0L
            } else {
                ((100f - facility.maintenanceLevel) / 100f * facility.monthlyMaintenanceCost * 2)
                    .toLong()
                    .coerceAtLeast(1L)
            }
        }
    }

    /**
     * 应用已完成付款的一键维修。
     */
    fun applyRepairAll(expectedCost: Long): Boolean {
        if (expectedCost <= 0L || getRepairAllCost() != expectedCost) return false
        var applied = false
        _state.update { state ->
            val facilities = state.facilities.toMutableMap()
            facilities.forEach { (aspect, facility) ->
                if (facility.maintenanceLevel < 100f) {
                    facilities[aspect] = facility.copy(maintenanceLevel = 100f)
                    applied = true
                }
            }
            state.copy(facilities = facilities)
        }
        if (applied) recalculateSatisfaction()
        return applied
    }

    /**
     * 一键维修所有设施（将维护度恢复到100%）
     * @return 维修总费用（万元）
     */
    fun repairAllFacilities(): Long {
        var totalCost = 0L
        _state.update { state ->
            val facilities = state.facilities.toMutableMap()
            LifeAspect.entries.forEach { aspect ->
                val facility = facilities[aspect] ?: return@forEach
                if (facility.maintenanceLevel < 100f) {
                    // 维修费 = (100 - 当前维护度) / 100 * 月维护费 * 2
                    val repairCost = ((100f - facility.maintenanceLevel) / 100f * facility.monthlyMaintenanceCost * 2).toLong().coerceAtLeast(1L)
                    totalCost += repairCost
                    facilities[aspect] = facility.copy(maintenanceLevel = 100f)
                }
            }
            state.copy(facilities = facilities)
        }
        recalculateSatisfaction()
        return totalCost
    }

    private fun recalculateSatisfaction() {
        _state.update { state ->
            val scores = mutableMapOf<LifeAspect, LifeSatisfactionScore>()
            var totalScore = 0f

            LifeAspect.entries.forEach { aspect ->
                val facility = state.facilities[aspect]
                val baseScore = facility?.quality?.satisfactionBase ?: 40f

                // 维护度影响
                val maintenanceFactor = (facility?.maintenanceLevel ?: 50f) / 100f

                // 过载惩罚
                val loadRatio = if ((facility?.capacity ?: 1) > 0) {
                    (facility?.currentLoad?.toFloat() ?: 0f) / (facility?.capacity?.toFloat() ?: 1f)
                } else 1f
                val loadPenalty = when {
                    loadRatio > 1.5f -> -20f
                    loadRatio > 1.2f -> -10f
                    loadRatio > 1.0f -> -5f
                    else -> 0f
                }

                // 特色项目加成
                val programBoost = state.programs
                    .filter { it.active && it.aspect == aspect }
                    .sumOf { it.satisfactionBoost.toDouble() }.toFloat()

                // 问题惩罚
                val issuePenalty = state.issues
                    .filter { !it.resolved && it.aspect == aspect }
                    .sumOf { it.satisfactionPenalty.toDouble() }.toFloat()

                val finalScore = (baseScore * maintenanceFactor + programBoost + loadPenalty - issuePenalty)
                    .coerceIn(0f, 100f)

                val issues = mutableListOf<String>()
                if (maintenanceFactor < 0.5f) issues.add("设施老化严重")
                if (loadRatio > 1.2f) issues.add("容量不足，过于拥挤")
                if (issuePenalty > 10f) issues.add("存在未解决的问题")

                scores[aspect] = LifeSatisfactionScore(
                    aspect = aspect,
                    score = finalScore,
                    trend = 0f, // simplified
                    issues = issues
                )
                totalScore += finalScore
            }

            val overall = totalScore / LifeAspect.entries.size

            // 学业影响: 满意度>70加成，<40惩罚
            val academicImpact = when {
                overall >= 85f -> 15f
                overall >= 70f -> 8f
                overall >= 55f -> 2f
                overall >= 40f -> -5f
                else -> -15f
            }

            // 留存率影响
            val retentionImpact = when {
                overall >= 80f -> 5f
                overall >= 60f -> 2f
                overall >= 40f -> -3f
                else -> -8f
            }

            state.copy(
                satisfactionScores = scores,
                overallSatisfaction = overall,
                academicImpact = academicImpact,
                retentionImpact = retentionImpact
            )
        }
    }

    private fun pickConditionIssue(
        year: Int,
        month: Int,
        overall: Float,
        dormLoad: Float,
        cafeLoad: Float,
        avgMaintenance: Float
    ): LifeIssue? {
        data class Candidate(
            val aspect: LifeAspect,
            val title: String,
            val reason: String,
            val severity: IssueSeverity
        )
        val pool = mutableListOf<Candidate>()
        if (dormLoad >= 1.0f) {
            pool += Candidate(
                LifeAspect.DORMITORY, "宿舍挤到加床",
                "床位已经住满（负载 ${(dormLoad * 100).toInt()}%），走廊加床引发投诉。",
                IssueSeverity.HIGH
            )
        }
        if (dormLoad >= 0.85f || avgMaintenance < 55f) {
            pool += Candidate(
                LifeAspect.DORMITORY, "宿舍漏水",
                "住宿偏满或设施老化，卫生间渗水。",
                IssueSeverity.MEDIUM
            )
        }
        if (cafeLoad >= 1.0f) {
            pool += Candidate(
                LifeAspect.CAFETERIA, "食堂排队过长",
                "餐位不够（负载 ${(cafeLoad * 100).toInt()}%），学生吃不上热饭。",
                IssueSeverity.HIGH
            )
        }
        if (cafeLoad >= 0.8f) {
            pool += Candidate(
                LifeAspect.CAFETERIA, "学生投诉菜品单一",
                "食堂超负荷，窗口只能反复出同样的菜。",
                IssueSeverity.LOW
            )
        }
        if (overall < 45f) {
            pool += Candidate(
                LifeAspect.PSYCHOLOGY, "校园霸凌事件",
                "整体满意度只有 ${overall.toInt()}，矛盾没人管，出现欺凌投诉。",
                IssueSeverity.CRITICAL
            )
        } else if (overall < 60f) {
            pool += Candidate(
                LifeAspect.PSYCHOLOGY, "考试压力过大投诉",
                "满意度 ${overall.toInt()}，学生觉得没人听他们说话。",
                IssueSeverity.MEDIUM
            )
        }
        if (avgMaintenance < 40f) {
            pool += Candidate(
                LifeAspect.HEALTH, "运动设施损坏",
                "维护度掉到 ${avgMaintenance.toInt()}，器材带伤运行。",
                IssueSeverity.MEDIUM
            )
        }
        if (month in listOf(1, 2, 12) && overall < 70f) {
            pool += Candidate(
                LifeAspect.HEALTH, "流感季节爆发",
                "冬春季叠加满意度不高，医务室挤满人。",
                IssueSeverity.HIGH
            )
        }
        if (pool.isEmpty()) return null
        val template = pool[random.nextInt(pool.size)]
        val penalty = when (template.severity) {
            IssueSeverity.LOW -> 3f
            IssueSeverity.MEDIUM -> 7f
            IssueSeverity.HIGH -> 12f
            IssueSeverity.CRITICAL -> 20f
        }
        return LifeIssue(
            id = "issue_${year}_${month}_${random.nextInt(1000)}",
            aspect = template.aspect,
            title = template.title,
            description = template.reason,
            severity = template.severity,
            satisfactionPenalty = penalty
        )
    }

    // ==================== 持久化 ====================

    fun snapshotState(): StudentLifeState = _state.value.deepCopy()

    fun restoreSnapshot(snapshot: StudentLifeState) {
        _state.value = snapshot.deepCopy()
    }

    private fun StudentLifeState.deepCopy(): StudentLifeState {
        return copy(
            facilities = facilities.mapValues { (_, facility) ->
                facility.copy(
                    specialPrograms = facility.specialPrograms.toMutableList()
                )
            },
            satisfactionScores = satisfactionScores.mapValues { (_, score) ->
                score.copy(issues = score.issues.toList())
            },
            issues = issues.map { it.copy() },
            programs = programs.map { it.copy() }
        )
    }

    /**
     * 将当前学生生活状态序列化为 JSON 字符串（用于存档）
     */
    fun toJson(): String {
        val state = _state.value
        val persistData = StudentLifePersistData(
            facilities = state.facilities.map { (aspect, f) ->
                FacilityPersist(
                    aspect = aspect.name,
                    quality = f.quality.name,
                    capacity = f.capacity,
                    maintenanceLevel = f.maintenanceLevel,
                    monthlyMaintenanceCost = f.monthlyMaintenanceCost,
                    lastUpgradeYear = f.lastUpgradeYear,
                    staffCount = f.staffCount
                )
            },
            activePrograms = state.programs.filter { it.active }.map { it.id },
            issues = state.issues.map { issue ->
                LifeIssuePersist(
                    id = issue.id,
                    aspect = issue.aspect.name,
                    title = issue.title,
                    description = issue.description,
                    severity = issue.severity.name,
                    satisfactionPenalty = issue.satisfactionPenalty,
                    resolved = issue.resolved
                )
            },
            monthlyExpenses = state.monthlyExpenses,
            lastProcessedYear = state.lastProcessedYear,
            lastProcessedMonth = state.lastProcessedMonth
        )
        return try {
            Json.encodeToString(persistData)
        } catch (_: Exception) { "" }
    }

    /**
     * 从 JSON 恢复学生生活状态（加载存档时调用）
     */
    fun restoreFromJson(json: String) {
        if (json.isBlank()) return
        try {
            val data = Json.decodeFromString<StudentLifePersistData>(json)
            require(data.facilities.map { it.aspect }.distinct().size ==
                data.facilities.size
            ) { "Duplicate student life facility" }
            require(data.activePrograms.distinct().size ==
                data.activePrograms.size
            ) { "Duplicate student life program" }
            require(data.issues.map { it.id }.distinct().size ==
                data.issues.size
            ) { "Duplicate student life issue" }
            require(data.monthlyExpenses >= 0L) {
                "Invalid student life monthly expenses"
            }
            val restoredFacilities = data.facilities.associate { fp ->
                val aspect = LifeAspect.valueOf(fp.aspect)
                val quality = FacilityQuality.valueOf(fp.quality)
                require(fp.capacity > 0) { "Invalid facility capacity" }
                require(fp.maintenanceLevel.isFinite() &&
                    fp.maintenanceLevel in 0f..100f
                ) { "Invalid facility maintenance" }
                require(fp.monthlyMaintenanceCost >= 0L) {
                    "Invalid facility maintenance cost"
                }
                aspect to LifeFacility(
                    aspect = aspect,
                    quality = quality,
                    capacity = fp.capacity,
                    maintenanceLevel = fp.maintenanceLevel,
                    monthlyMaintenanceCost = fp.monthlyMaintenanceCost,
                    lastUpgradeYear = fp.lastUpgradeYear,
                    staffCount = fp.staffCount
                )
            }
            val unknownPrograms = data.activePrograms.filter { id ->
                availablePrograms.none { it.id == id }
            }
            require(unknownPrograms.isEmpty()) {
                "Unknown student life programs: $unknownPrograms"
            }
            val restoredPrograms = data.activePrograms.map { id ->
                requireNotNull(availablePrograms.find { it.id == id })
                    .copy(active = true)
            }
            val restoredIssues = data.issues.map { issue ->
                require(issue.id.isNotBlank()) {
                    "Blank student life issue id"
                }
                require(issue.title.isNotBlank()) {
                    "Blank student life issue title"
                }
                require(issue.satisfactionPenalty.isFinite() &&
                    issue.satisfactionPenalty >= 0f
                ) { "Invalid student life issue penalty" }
                LifeIssue(
                    id = issue.id,
                    aspect = LifeAspect.valueOf(issue.aspect),
                    title = issue.title,
                    description = issue.description,
                    severity = IssueSeverity.valueOf(issue.severity),
                    satisfactionPenalty = issue.satisfactionPenalty,
                    resolved = issue.resolved
                )
            }
            _state.update { state ->
                state.copy(
                    facilities = state.facilities + restoredFacilities,
                    programs = restoredPrograms,
                    issues = restoredIssues,
                    monthlyExpenses = data.monthlyExpenses,
                    lastProcessedYear = data.lastProcessedYear,
                    lastProcessedMonth = data.lastProcessedMonth
                )
            }
            recalculateSatisfaction()
        } catch (e: Exception) {
            throw IllegalArgumentException("StudentLifeManager.restoreFromJson failed", e)
        }
    }
}

@Serializable
data class StudentLifePersistData(
    val facilities: List<FacilityPersist> = emptyList(),
    val activePrograms: List<String> = emptyList(),
    val issues: List<LifeIssuePersist> = emptyList(),
    val monthlyExpenses: Long = 0L,
    val lastProcessedYear: Int = 0,
    val lastProcessedMonth: Int = 0
)

@Serializable
data class LifeIssuePersist(
    val id: String,
    val aspect: String,
    val title: String,
    val description: String,
    val severity: String,
    val satisfactionPenalty: Float,
    val resolved: Boolean = false
)

@Serializable
data class FacilityPersist(
    val aspect: String,
    val quality: String,
    val capacity: Int,
    val maintenanceLevel: Float = 100f,
    val monthlyMaintenanceCost: Long = 5L,
    val lastUpgradeYear: Int = 0,
    val staffCount: Int = 1
)
