package com.arktools.xiaozhang.ui.facility

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiaozhang.audio.AudioManager
import com.arktools.xiaozhang.domain.model.Facility
import com.arktools.xiaozhang.domain.model.FacilityBonusCalculator
import com.arktools.xiaozhang.domain.model.FacilityCapacity
import com.arktools.xiaozhang.domain.model.FacilityCategory
import com.arktools.xiaozhang.domain.model.FacilityType
import com.arktools.xiaozhang.domain.model.School
import com.arktools.xiaozhang.domain.engine.GameBalanceConfig
import com.arktools.xiaozhang.domain.engine.GameEngine
import com.arktools.xiaozhang.domain.engine.SchoolDecision
import com.arktools.xiaozhang.domain.repository.SchoolRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.arktools.xiaozhang.util.safeLaunch

data class FacilityUiState(
    val facilities: List<Facility> = emptyList(),
    val availableToBuy: List<FacilityType> = emptyList(),
    val cash: Double = 0.0,
    val totalMaintenance: Double = 0.0,
    val bonuses: FacilityBonusCalculator.FacilityBonuses = FacilityBonusCalculator.FacilityBonuses(),
    val maxFacilities: Int = 5,
    val isAtCapacity: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class FacilityViewModel @Inject constructor(
    private val schoolRepository: SchoolRepository,
    private val audioManager: AudioManager,
    private val gameEngine: GameEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(FacilityUiState())
    val uiState: StateFlow<FacilityUiState> = _uiState.asStateFlow()

    init {
        observeSchool()
    }

    private fun observeSchool() {
        viewModelScope.safeLaunch {
            schoolRepository.getSchoolFlow().collect { school ->
                if (school != null) {
                    updateState(school)
                }
            }
        }
    }

    private fun updateState(school: School) {
        val owned = school.facilities
        val maxFacilities = GameBalanceConfig.getMaxFacilitiesForLevel(school.campusLevel)
        val atCapacity = owned.size >= maxFacilities
        val available = if (atCapacity) {
            emptyList()
        } else {
            FacilityType.values().filter { type ->
                type.repeatable || owned.none { it.type == type }
            }
        }

        _uiState.value = FacilityUiState(
            facilities = owned,
            availableToBuy = available,
            cash = school.cash,
            totalMaintenance = FacilityBonusCalculator.getTotalMaintenance(owned),
            bonuses = FacilityBonusCalculator.calculate(owned),
            maxFacilities = maxFacilities,
            isAtCapacity = atCapacity,
            message = null
        )
    }

    fun buyFacility(type: FacilityType) {
        viewModelScope.safeLaunch {
            // 使用 mutateSchool 原子操作：读取最新状态 → 扣钱+加设施 → 一次性写回
            // 避免之前 deductCash() + updateSchool() 分开调用导致的竞态条件
            val result = schoolRepository.mutateSchool { school ->
                val maxFacilities = GameBalanceConfig.getMaxFacilitiesForLevel(school.campusLevel)
                if (school.facilities.size >= maxFacilities) {
                    _uiState.value = _uiState.value.copy(message = "设施数量已达当前等级上限（${maxFacilities}/${maxFacilities}），请到「社会」页面升级大学校园以解锁更多设施位")
                    return@mutateSchool false
                }
                val existingCount = school.facilities.count { it.type == type }
                if (existingCount > 0 && !type.repeatable) {
                    _uiState.value = _uiState.value.copy(message = "${type.displayName} 已建成（该类型只需一座）")
                    return@mutateSchool false
                }
                val cost = FacilityCapacity.repeatCost(type, existingCount)
                if (school.cash < cost) {
                    _uiState.value = _uiState.value.copy(message = "资金不足！需要 ${cost.toInt()} 万元")
                    return@mutateSchool false
                }
                school.cash -= cost
                school.facilities.add(Facility(type = type, level = 1, condition = 100f))
                true
            }
            if (result != null) {
                audioManager.playBuildFacility()
                gameEngine.notifyFactionDecision(SchoolDecision.BUILD_FACILITY)
                _uiState.value = _uiState.value.copy(message = "${type.displayName} 建设完成！")
            }
        }
    }

    fun upgradeFacility(facilityId: String) {
        viewModelScope.safeLaunch {
            var upgradedName = ""
            var upgradedLevel = 0
            val result = schoolRepository.mutateSchool { school ->
                val facilityIndex = school.facilities.indexOfFirst { it.id == facilityId }
                if (facilityIndex == -1) return@mutateSchool false
                val facility = school.facilities[facilityIndex]
                val type = facility.type

                if (facility.level >= type.maxLevel) {
                    _uiState.value = _uiState.value.copy(message = "${type.displayName} 已达最大等级")
                    return@mutateSchool false
                }

                val cost = FacilityBonusCalculator.getUpgradeCost(facility)
                if (school.cash < cost) {
                    _uiState.value = _uiState.value.copy(message = "资金不足！升级需要 ${String.format("%.1f", cost)} 万元")
                    return@mutateSchool false
                }

                school.cash -= cost
                val upgraded = facility.copy(level = facility.level + 1)
                school.facilities[facilityIndex] = upgraded
                upgradedName = type.displayName
                upgradedLevel = upgraded.level
                true
            }
            if (result != null) {
                audioManager.playLevelUp()
                _uiState.value = _uiState.value.copy(message = "$upgradedName 升级到 Lv.${upgradedLevel}！")
            }
        }
    }

    fun repairAllFacilities() {
        viewModelScope.safeLaunch {
            var repairedCount = 0
            var totalCost = 0.0
            val result = schoolRepository.mutateSchool { school ->
                val needRepair = school.facilities.filter { it.condition < 95f }
                if (needRepair.isEmpty()) {
                    _uiState.value = _uiState.value.copy(message = "所有设施状态良好，无需维修")
                    return@mutateSchool false
                }

                val totalRepairCost = needRepair.sumOf { it.type.baseMaintenance * 2 }
                if (school.cash < totalRepairCost) {
                    _uiState.value = _uiState.value.copy(
                        message = "资金不足！全部维修需要 ${String.format("%.1f", totalRepairCost)} 万元"
                    )
                    return@mutateSchool false
                }

                school.cash -= totalRepairCost
                needRepair.forEach { facility ->
                    val index = school.facilities.indexOfFirst { it.id == facility.id }
                    if (index != -1) {
                        school.facilities[index] = facility.copy(condition = 100f)
                        repairedCount++
                    }
                }
                totalCost = totalRepairCost
                true
            }
            if (result != null) {
                _uiState.value = _uiState.value.copy(
                    message = "一键维修完成！修复 $repairedCount 项设施，花费 ${String.format("%.1f", totalCost)} 万元"
                )
            }
        }
    }

    fun repairFacility(facilityId: String) {
        viewModelScope.safeLaunch {
            var repairedName = ""
            val result = schoolRepository.mutateSchool { school ->
                val facilityIndex = school.facilities.indexOfFirst { it.id == facilityId }
                if (facilityIndex == -1) return@mutateSchool false
                val facility = school.facilities[facilityIndex]
                val type = facility.type

                if (facility.condition >= 95f) {
                    _uiState.value = _uiState.value.copy(message = "${type.displayName} 状态良好，无需维护")
                    return@mutateSchool false
                }

                val repairCost = type.baseMaintenance * 2
                if (school.cash < repairCost) {
                    _uiState.value = _uiState.value.copy(message = "资金不足！维修需要 ${String.format("%.1f", repairCost)} 万元")
                    return@mutateSchool false
                }

                school.cash -= repairCost
                school.facilities[facilityIndex] = facility.copy(condition = 100f)
                repairedName = type.displayName
                true
            }
            if (result != null) {
                _uiState.value = _uiState.value.copy(message = "$repairedName 维修完成！")
            }
        }
    }

    fun demolishFacility(facilityId: String) {
        viewModelScope.safeLaunch {
            var demolishedName = ""
            val result = schoolRepository.mutateSchool { school ->
                val facilityIndex = school.facilities.indexOfFirst { it.id == facilityId }
                if (facilityIndex == -1) return@mutateSchool false
                val facility = school.facilities[facilityIndex]
                val type = facility.type

                // 不允许拆除最后一间教室
                if (type == FacilityType.CLASSROOM) {
                    val classroomCount = school.facilities.count { it.type == FacilityType.CLASSROOM }
                    if (classroomCount <= 1) {
                        _uiState.value = _uiState.value.copy(message = "至少需要保留一间教室，无法拆除")
                        return@mutateSchool false
                    }
                }

                // 拆除退还30%建设费用
                val refund = type.baseCost * 0.3
                school.cash += refund
                school.facilities.removeAt(facilityIndex)
                demolishedName = type.displayName
                _uiState.value = _uiState.value.copy(
                    message = "${type.displayName} 已拆除，回收 ${String.format("%.1f", refund)} 万元"
                )
                true
            }
            if (result != null) {
                audioManager.playCashEarn()
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
