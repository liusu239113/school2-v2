package com.arktools.xiaozhang.domain.expansion

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
 * 学校扩建系统
 * 管理新校区规划、建设进度、容纳量扩展
 * 支持多阶段建设、资金分期投入
 */

enum class CampusZoneType(
    val displayName: String,
    val icon: String,
    val baseCapacity: Int,
    val baseCostWan: Double,   // 单位：万元，与全系统统一（v2.9: 大幅提高建造费用）
    val buildMonths: Int,
    val description: String
) {
    TEACHING_BUILDING("教学楼", "🏫", 200, 200.0, 6, "标准教学楼，含教室和办公室"),
    LABORATORY("实验楼", "🔬", 80, 350.0, 8, "配备先进实验设备的专业楼"),
    DORMITORY("学生宿舍", "🏠", 300, 150.0, 5, "标准化学生公寓"),
    LIBRARY("图书馆", "📚", 150, 280.0, 7, "综合型现代图书馆"),
    SPORTS_CENTER("体育中心", "🏟️", 500, 500.0, 10, "包含体育馆、游泳池、运动场"),
    CAFETERIA("餐饮中心", "🍽️", 400, 120.0, 4, "大型食堂综合体"),
    ARTS_CENTER("艺术中心", "🎭", 120, 300.0, 7, "音乐厅、美术馆、排练厅"),
    RESEARCH_CENTER("科研中心", "🧪", 60, 600.0, 12, "高端科研实验基地"),
    ADMIN_BUILDING("行政楼", "🏢", 50, 100.0, 4, "行政办公与接待中心"),
    GARDEN("校园花园", "🌳", 0, 80.0, 3, "美化校园环境，提升满意度")
}

enum class ConstructionPhase(val displayName: String, val progressPercent: Float) {
    PLANNING("规划设计", 0f),
    FOUNDATION("地基施工", 15f),
    STRUCTURE("主体建设", 45f),
    INTERIOR("内部装修", 75f),
    EQUIPMENT("设备安装", 90f),
    COMPLETED("竣工验收", 100f)
}

enum class CampusLevel(
    val displayName: String,
    val maxZones: Int,
    val unlockCostWan: Double,  // 单位：万元，与全系统统一
    val capacityBonus: Float,
    val description: String
) {
    SINGLE_CAMPUS("单校区", 5, 0.0, 1.0f, "初始校区，空间有限"),
    EXPANDED("扩展校区", 8, 300.0, 1.2f, "扩大现有校区面积"),
    DUAL_CAMPUS("双校区", 12, 1000.0, 1.5f, "开设第二校区"),
    MULTI_CAMPUS("多校区", 18, 3000.0, 1.8f, "多个校区协同运营"),
    EDUCATION_PARK("教育园区", 24, 8000.0, 2.2f, "政府合作教育产业园"),
    UNIVERSITY_TOWN("大学城", 32, 20000.0, 2.8f, "形成教育产业集群"),
    EDUCATION_GROUP("教育集团", 40, 50000.0, 3.5f, "跨区域教育集团总部"),
    NATIONAL_BRAND("全国名校", 50, 120000.0, 4.5f, "全国顶尖教育品牌旗舰")
}

data class CampusZone(
    val id: String,
    val type: CampusZoneType,
    val name: String,
    var phase: ConstructionPhase = ConstructionPhase.PLANNING,
    var progress: Float = 0f,           // 0-100
    var monthsElapsed: Int = 0,
    var totalInvested: Double = 0.0,    // 单位：万元
    var qualityLevel: Int = 1,          // 1-5 建设质量等级
    var maintenanceLevel: Float = 100f,
    var capacity: Int = 0,              // 当前可用容量(未完工=0)
    var completedYear: Int = 0,
    var completedMonth: Int = 0
) {
    val isCompleted: Boolean get() = phase == ConstructionPhase.COMPLETED
    val expectedCapacity: Int get() = (type.baseCapacity * qualityLevel * 0.5f).toInt() + type.baseCapacity
    val totalCostWan: Double get() = type.baseCostWan * qualityLevel  // 万元
    val remainingCostWan: Double get() = (totalCostWan - totalInvested).coerceAtLeast(0.0)
}

data class ExpansionEvent(
    val title: String,
    val message: String,
    val year: Int,
    val month: Int,
    val isPositive: Boolean = true
)

data class CampusExpansionState(
    val currentLevel: CampusLevel = CampusLevel.SINGLE_CAMPUS,
    val zones: List<CampusZone> = emptyList(),
    val totalCapacity: Int = 300,
    val usedCapacity: Int = 0,
    val monthlyMaintenanceCost: Double = 0.0,
    val events: List<ExpansionEvent> = emptyList(),
    val totalInvestment: Double = 0.0,
    val completedZones: Int = 0,
    val constructingZones: Int = 0,
    val lastProcessedYear: Int = 0,
    val lastProcessedMonth: Int = 0
)

data class ExpansionMonthlyResult(
    val maintenanceCost: Double = 0.0,  // 万元
    val newCompletions: List<CampusZone> = emptyList(),
    val phaseAdvances: List<Pair<String, ConstructionPhase>> = emptyList(),
    val events: List<ExpansionEvent> = emptyList(),
    val capacityGain: Int = 0
)

data class CampusExpansionSnapshot(
    val state: CampusExpansionState,
    val nextZoneId: Int
)

@Singleton
class CampusExpansionManager @Inject constructor() {

    private val _state = MutableStateFlow(CampusExpansionState())
    val state: StateFlow<CampusExpansionState> = _state.asStateFlow()

    fun reset() {
        _state.value = CampusExpansionState()
        nextZoneId = 1
    }

    private var nextZoneId = 1

    /**
     * 获取可建设的区域类型（未超出上限）
     */
    fun getAvailableZoneTypes(): List<CampusZoneType> {
        val current = _state.value
        if (current.zones.size >= current.currentLevel.maxZones) return emptyList()
        return CampusZoneType.entries.toList()
    }

    /**
     * 开始建设新区域
     */
    fun startConstruction(
        type: CampusZoneType,
        name: String = type.displayName,
        qualityLevel: Int = 1
    ): CampusZone? {
        val current = _state.value
        if (current.zones.size >= current.currentLevel.maxZones) return null

        val zone = CampusZone(
            id = "zone_${nextZoneId++}",
            type = type,
            name = name,
            qualityLevel = qualityLevel.coerceIn(1, 5),
            phase = ConstructionPhase.PLANNING
        )

        _state.update { state ->
            state.copy(
                zones = state.zones + zone,
                constructingZones = state.constructingZones + 1
            )
        }
        return zone
    }

    /**
     * 获取本次投资实际需要支付的金额，不改变建设状态。
     */
    fun getInvestmentAmount(zoneId: String, requestedAmountWan: Double): Double {
        if (!requestedAmountWan.isFinite() || requestedAmountWan <= 0.0) return 0.0
        val zone = _state.value.zones.find { it.id == zoneId } ?: return 0.0
        if (zone.isCompleted || zone.remainingCostWan <= 0.0) return 0.0
        return requestedAmountWan.coerceAtMost(zone.remainingCostWan)
    }

    /**
     * 注入已完成扣款的资金到在建项目。
     */
    fun investInZone(zoneId: String, requestedAmountWan: Double): Double {
        if (!requestedAmountWan.isFinite() || requestedAmountWan <= 0.0) return 0.0

        var acceptedAmount = 0.0
        _state.update { state ->
            val zones = state.zones.map { zone ->
                if (zone.id == zoneId && !zone.isCompleted && zone.remainingCostWan > 0.0) {
                    acceptedAmount = requestedAmountWan.coerceAtMost(zone.remainingCostWan)
                    zone.copy(totalInvested = zone.totalInvested + acceptedAmount)
                } else {
                    zone
                }
            }
            if (acceptedAmount > 0.0) {
                state.copy(
                    zones = zones,
                    totalInvestment = state.totalInvestment + acceptedAmount
                )
            } else {
                state
            }
        }
        return acceptedAmount
    }

    /**
     * 升级校区等级
     * @return 升级费用（万元），0表示已满级
     */
    fun upgradeCampusLevel(): Double {
        val current = _state.value
        val currentIndex = CampusLevel.entries.indexOf(current.currentLevel)
        if (currentIndex >= CampusLevel.entries.size - 1) return 0.0

        val nextLevel = CampusLevel.entries[currentIndex + 1]
        _state.update { it.copy(currentLevel = nextLevel) }
        return nextLevel.unlockCostWan
    }

    fun hasProcessedMonth(year: Int, month: Int): Boolean {
        val state = _state.value
        return state.lastProcessedYear == year &&
            state.lastProcessedMonth == month
    }

    /**
     * 月度推进
     */
    fun advanceMonth(currentYear: Int, currentMonth: Int, studentCount: Int): ExpansionMonthlyResult {
        if (hasProcessedMonth(currentYear, currentMonth)) {
            return ExpansionMonthlyResult()
        }
        val completions = mutableListOf<CampusZone>()
        val phaseAdvances = mutableListOf<Pair<String, ConstructionPhase>>()
        val events = mutableListOf<ExpansionEvent>()
        var maintenanceCost = 0.0
        var capacityGain = 0

        _state.update { state ->
            val zones = state.zones.map { zone ->
                if (zone.isCompleted) {
                    // 已完工建筑维护：月维护=总造价2%（万元）
                    // 例：教学楼50万建造费 → 月维护 50*0.02 = 1.0万
                    val mCost = zone.type.baseCostWan * 0.02
                    maintenanceCost += mCost
                    val newMaint = (zone.maintenanceLevel - 1.5f).coerceAtLeast(0f)
                    zone.copy(maintenanceLevel = newMaint)
                } else {
                    // 在建项目进度推进
                    val fundingRatio = if (zone.totalCostWan > 0) {
                        (zone.totalInvested / zone.totalCostWan).toFloat()
                    } else 0f

                    // 建设启动需至少投入总预算10%，达到后每月稳定推进。
                    // 原逻辑随阶段抬高资金门槛，玩家分次投资时会长期“完全不动”且没有反馈。
                    if (fundingRatio < 0.10f) {
                        val missing = (zone.totalCostWan * 0.10 - zone.totalInvested)
                            .coerceAtLeast(0.0)
                        events.add(ExpansionEvent(
                            title = "${zone.name}等待资金",
                            message = "项目已投入${String.format("%.1f", zone.totalInvested)}万，还需${String.format("%.1f", missing)}万才能开工。",
                            year = currentYear,
                            month = currentMonth,
                            isPositive = false
                        ))
                        return@map zone
                    }

                    val monthlyProgress = 100f / zone.type.buildMonths
                    val newProgress = (zone.progress + monthlyProgress).coerceAtMost(100f)
                    val newMonths = zone.monthsElapsed + 1

                    // 检查阶段推进
                    val newPhase = when {
                        newProgress >= 100f -> ConstructionPhase.COMPLETED
                        newProgress >= 90f -> ConstructionPhase.EQUIPMENT
                        newProgress >= 75f -> ConstructionPhase.INTERIOR
                        newProgress >= 45f -> ConstructionPhase.STRUCTURE
                        newProgress >= 15f -> ConstructionPhase.FOUNDATION
                        else -> ConstructionPhase.PLANNING
                    }

                    if (newPhase != zone.phase) {
                        phaseAdvances.add(zone.name to newPhase)
                    }

                    val updatedZone = if (newPhase == ConstructionPhase.COMPLETED) {
                        val capacity = zone.expectedCapacity
                        capacityGain += capacity
                        completions.add(zone.copy(
                            phase = newPhase,
                            progress = 100f,
                            capacity = capacity,
                            completedYear = currentYear,
                            completedMonth = currentMonth
                        ))
                        events.add(ExpansionEvent(
                            title = "${zone.name}竣工",
                            message = "${zone.name}建设完成！新增容纳量${capacity}人",
                            year = currentYear,
                            month = currentMonth,
                            isPositive = true
                        ))
                        zone.copy(
                            phase = newPhase,
                            progress = 100f,
                            monthsElapsed = newMonths,
                            capacity = capacity,
                            completedYear = currentYear,
                            completedMonth = currentMonth
                        )
                    } else {
                        zone.copy(
                            phase = newPhase,
                            progress = newProgress,
                            monthsElapsed = newMonths
                        )
                    }
                    updatedZone
                }
            }

            val totalCap = state.totalCapacity + capacityGain
            val completed = zones.count { it.isCompleted }
            val constructing = zones.count { !it.isCompleted }

            state.copy(
                zones = zones,
                totalCapacity = totalCap,
                usedCapacity = studentCount,
                monthlyMaintenanceCost = maintenanceCost,
                completedZones = completed,
                constructingZones = constructing,
                events = (events + state.events).take(50),
                lastProcessedYear = currentYear,
                lastProcessedMonth = currentMonth
            )
        }

        return ExpansionMonthlyResult(
            maintenanceCost = maintenanceCost,
            newCompletions = completions,
            phaseAdvances = phaseAdvances,
            events = events,
            capacityGain = capacityGain
        )
    }

    /**
     * 获取容量使用率
     */
    fun getCapacityUsagePercent(): Int {
        val s = _state.value
        return if (s.totalCapacity > 0) (s.usedCapacity * 100) / s.totalCapacity else 100
    }

    /**
     * 升级已完成建筑的质量等级
     * @return 升级费用（万元），0表示无法升级
     */
    fun upgradeZoneQuality(zoneId: String): Double {
        var cost = 0.0
        _state.update { state ->
            val zones = state.zones.map { zone ->
                if (zone.id == zoneId && zone.isCompleted && zone.qualityLevel < 5) {
                    // 升级费用 = 基础造价 * 当前等级 * 0.8（万元）
                    // 例如教学楼50万造价，从等级1→2：50*1*0.8 = 40万
                    cost = zone.type.baseCostWan * zone.qualityLevel * 0.8
                    // 升级后容量增加
                    val newQuality = zone.qualityLevel + 1
                    val newCapacity = (zone.type.baseCapacity * newQuality * 0.5f).toInt() + zone.type.baseCapacity
                    zone.copy(
                        qualityLevel = newQuality,
                        capacity = newCapacity,
                        totalInvested = zone.totalInvested + cost
                    )
                } else zone
            }
            // 计算容量差值
            val oldTotalCap = state.zones.filter { it.isCompleted }.sumOf { it.capacity }
            val newTotalCap = zones.filter { it.isCompleted }.sumOf { it.capacity }
            val capDiff = newTotalCap - oldTotalCap
            state.copy(
                zones = zones,
                totalCapacity = state.totalCapacity + capDiff,
                totalInvestment = state.totalInvestment + cost
            )
        }
        return cost
    }

    /**
     * 获取升级费用预览（万元），不执行升级
     */
    fun getUpgradeQualityCost(zoneId: String): Double {
        val zone = _state.value.zones.find { it.id == zoneId } ?: return 0.0
        if (!zone.isCompleted || zone.qualityLevel >= 5) return 0.0
        return zone.type.baseCostWan * zone.qualityLevel * 0.8
    }

    /**
     * 获取维修费用预览（万元），不执行维修。
     */
    fun getRepairCost(zoneId: String): Double {
        val zone = _state.value.zones.find { it.id == zoneId } ?: return 0.0
        if (!zone.isCompleted || zone.maintenanceLevel >= 100f) return 0.0
        return (100.0 - zone.maintenanceLevel) * zone.type.baseCostWan * 0.001
    }

    /**
     * 维修建筑
     */
    fun repairZone(zoneId: String): Double {
        var cost = 0.0
        _state.update { state ->
            val zones = state.zones.map { zone ->
                if (zone.id == zoneId && zone.isCompleted) {
                    // 维修费 = (损失百分比) * 造价(万元) * 0.001
                    cost = (100.0 - zone.maintenanceLevel) * zone.type.baseCostWan * 0.001
                    zone.copy(maintenanceLevel = 100f)
                } else zone
            }
            state.copy(zones = zones)
        }
        return cost
    }

    fun snapshotState(): CampusExpansionSnapshot {
        return CampusExpansionSnapshot(
            state = _state.value.deepCopy(),
            nextZoneId = nextZoneId
        )
    }

    fun restoreSnapshot(snapshot: CampusExpansionSnapshot) {
        _state.value = snapshot.state.deepCopy()
        nextZoneId = snapshot.nextZoneId
    }

    private fun CampusExpansionState.deepCopy(): CampusExpansionState {
        return copy(
            zones = zones.map { it.copy() },
            events = events.map { it.copy() }
        )
    }

    fun toJson(): String {
        return try {
            val state = _state.value
            val data = ExpansionPersistData(
                currentLevel = state.currentLevel.name,
                zones = state.zones.map { z ->
                    ZonePersist(
                        id = z.id, type = z.type.name, name = z.name,
                        phase = z.phase.name, progress = z.progress,
                        monthsElapsed = z.monthsElapsed, totalInvested = z.totalInvested,
                        qualityLevel = z.qualityLevel, maintenanceLevel = z.maintenanceLevel,
                        capacity = z.capacity, completedYear = z.completedYear,
                        completedMonth = z.completedMonth
                    )
                },
                totalCapacity = state.totalCapacity,
                usedCapacity = state.usedCapacity,
                totalInvestment = state.totalInvestment,
                completedZones = state.completedZones,
                constructingZones = state.constructingZones,
                nextZoneId = nextZoneId,
                lastProcessedYear = state.lastProcessedYear,
                lastProcessedMonth = state.lastProcessedMonth
            )
            Json.encodeToString(data)
        } catch (_: Exception) { "" }
    }

    fun restoreFromJson(json: String) {
        if (json.isBlank()) return
        try {
            val data = Json {
                ignoreUnknownKeys = true
            }.decodeFromString<ExpansionPersistData>(json)
            val level = CampusLevel.valueOf(data.currentLevel)
            require(data.zones.map { it.id }.distinct().size ==
                data.zones.size
            ) { "Duplicate campus zone id" }
            val zones = data.zones.map { zp ->
                val type = CampusZoneType.valueOf(zp.type)
                val phase = ConstructionPhase.valueOf(zp.phase)
                require(zp.id.isNotBlank()) { "Blank campus zone id" }
                require(zp.progress.isFinite() && zp.progress in 0f..100f) {
                    "Invalid campus zone progress"
                }
                require(zp.totalInvested.isFinite() && zp.totalInvested >= 0.0) {
                    "Invalid campus zone investment"
                }
                require(zp.qualityLevel in 1..5) {
                    "Invalid campus zone quality"
                }
                require(zp.maintenanceLevel.isFinite() &&
                    zp.maintenanceLevel in 0f..100f
                ) { "Invalid campus zone maintenance" }
                require(zp.capacity >= 0) { "Invalid campus zone capacity" }
                CampusZone(
                    id = zp.id, type = type, name = zp.name, phase = phase,
                    progress = zp.progress, monthsElapsed = zp.monthsElapsed,
                    totalInvested = zp.totalInvested, qualityLevel = zp.qualityLevel,
                    maintenanceLevel = zp.maintenanceLevel, capacity = zp.capacity,
                    completedYear = zp.completedYear, completedMonth = zp.completedMonth
                )
            }
            require(data.nextZoneId > 0) { "Invalid next campus zone id" }
            require(data.totalCapacity >= 0 && data.usedCapacity >= 0) {
                "Invalid campus capacity"
            }
            require(data.totalInvestment.isFinite() &&
                data.totalInvestment >= 0.0
            ) { "Invalid total campus investment" }
            val completedZones = zones.count { it.isCompleted }
            val constructingZones = zones.size - completedZones
            require(data.completedZones == completedZones &&
                data.constructingZones == constructingZones
            ) { "Campus zone aggregate mismatch" }
            nextZoneId = data.nextZoneId
            _state.value = CampusExpansionState(
                currentLevel = level, zones = zones,
                totalCapacity = data.totalCapacity, usedCapacity = data.usedCapacity,
                monthlyMaintenanceCost = _state.value.monthlyMaintenanceCost,
                events = emptyList(),
                totalInvestment = data.totalInvestment,
                completedZones = completedZones,
                constructingZones = constructingZones,
                lastProcessedYear = data.lastProcessedYear,
                lastProcessedMonth = data.lastProcessedMonth
            )
        } catch (e: Exception) {
            throw IllegalArgumentException("CampusExpansionManager.restoreFromJson failed", e)
        }
    }
}

@Serializable
data class ExpansionPersistData(
    val currentLevel: String = "SINGLE_CAMPUS",
    val zones: List<ZonePersist> = emptyList(),
    val totalCapacity: Int = 300,
    val usedCapacity: Int = 0,
    val totalInvestment: Double = 0.0,
    val completedZones: Int = 0,
    val constructingZones: Int = 0,
    val nextZoneId: Int = 1,
    val lastProcessedYear: Int = 0,
    val lastProcessedMonth: Int = 0
)

@Serializable
data class ZonePersist(
    val id: String,
    val type: String,
    val name: String,
    val phase: String,
    val progress: Float,
    val monthsElapsed: Int,
    val totalInvested: Double,
    val qualityLevel: Int,
    val maintenanceLevel: Float,
    val capacity: Int,
    val completedYear: Int,
    val completedMonth: Int
)
