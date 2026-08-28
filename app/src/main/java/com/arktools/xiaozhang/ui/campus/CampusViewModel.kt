package com.arktools.xiaozhang.ui.campus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiaozhang.audio.AudioManager
import com.arktools.xiaozhang.domain.engine.GameBalanceConfig
import com.arktools.xiaozhang.domain.engine.GameEngine
import com.arktools.xiaozhang.domain.engine.SchoolDecision
import com.arktools.xiaozhang.domain.model.Facility
import com.arktools.xiaozhang.domain.model.FacilityBonusCalculator
import com.arktools.xiaozhang.domain.model.FacilityType
import com.arktools.xiaozhang.domain.policy.CollegeType
import com.arktools.xiaozhang.domain.repository.SchoolRepository
import com.arktools.xiaozhang.domain.repository.StudentRepository
import com.arktools.xiaozhang.domain.repository.TeacherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import com.arktools.xiaozhang.util.safeLaunch

/**
 * 建筑式校园主视图的 ViewModel。
 * 建造/升级均复用既有原子写回逻辑（mutateSchool），数值公式与设施页一致。
 */
@HiltViewModel
class CampusViewModel @Inject constructor(
    private val schoolRepository: SchoolRepository,
    private val policyManager: com.arktools.xiaozhang.domain.policy.SchoolPolicyManager,
    private val gameEngine: GameEngine,
    private val audioManager: AudioManager,
    private val teacherRepository: TeacherRepository,
    private val studentRepository: StudentRepository,
    private val teachingManager: com.arktools.xiaozhang.domain.teaching.TeachingManager
) : ViewModel() {

    data class CampusBuilding(
        val id: String,
        val displayName: String,
        val drawableRes: Int,
        val kind: Kind,
        val facility: Facility? = null,
        val college: CollegeType? = null
    ) {
        enum class Kind { ADMIN, COLLEGE, FACILITY }
    }

    data class CampusUiState(
        val cash: Double = 0.0,
        val reputation: Long = 0,
        val campusLevel: Int = 1,
        val foundedColleges: List<CollegeType> = emptyList(),
        val affiliatedHospital: Boolean = false,
        val facilities: List<Facility> = emptyList(),
        val maxFacilities: Int = 5,
        val selected: CampusBuilding? = null,
        val showBuildMenu: Boolean = false,
        val message: String? = null
    ) {
        val upgradeCampusCost: Double
            get() = GameBalanceConfig.getCampusUpgradeCost(campusLevel)
    }

    private val _state = MutableStateFlow(CampusUiState())
    val state: StateFlow<CampusUiState> = _state.asStateFlow()

    init {
        viewModelScope.safeLaunch {
            schoolRepository.getSchoolFlow().collect { school ->
                if (school == null) return@collect
                _state.value = _state.value.copy(
                    cash = school.cash,
                    reputation = school.reputation,
                    campusLevel = school.campusLevel,
                    foundedColleges = policyManager.policies.value.collegeDevelopment.founded,
                    affiliatedHospital = policyManager.policies.value.collegeDevelopment.affiliatedHospital,
                    facilities = school.facilities,
                    maxFacilities = GameBalanceConfig.getMaxFacilitiesForLevel(school.campusLevel)
                )
            }
        }
        viewModelScope.safeLaunch {
            policyManager.policies.collect { p ->
                _state.value = _state.value.copy(
                    foundedColleges = p.collegeDevelopment.founded,
                    affiliatedHospital = p.collegeDevelopment.affiliatedHospital
                )
            }
        }
    }

    fun selectBuilding(building: CampusBuilding) {
        _state.value = _state.value.copy(selected = building)
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selected = null)
    }

    fun openBuildMenu() {
        _state.value = _state.value.copy(showBuildMenu = true)
    }

    fun closeBuildMenu() {
        _state.value = _state.value.copy(showBuildMenu = false)
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun foundCollege(type: CollegeType) {
        viewModelScope.safeLaunch {
            val result = gameEngine.foundCollege(type)
            _state.value = _state.value.copy(message = result.message)
            if (result.success) {
                audioManager.playCollegeFound()
                gameEngine.notifyFactionDecision(SchoolDecision.BUILD_FACILITY)
            } else {
                audioManager.playEventNegative()
            }
        }
    }

    fun buyFacility(type: FacilityType) {
        viewModelScope.safeLaunch {
            val result = schoolRepository.mutateSchool { school ->
                val max = GameBalanceConfig.getMaxFacilitiesForLevel(school.campusLevel)
                if (school.facilities.size >= max) {
                    _state.value = _state.value.copy(
                        message = "建筑数量已达当前等级上限（${max}），先升级校园"
                    )
                    return@mutateSchool false
                }
                if (school.facilities.any { it.type == type }) {
                    _state.value = _state.value.copy(message = "${type.displayName} 已建成")
                    return@mutateSchool false
                }
                if (school.cash < type.baseCost) {
                    _state.value = _state.value.copy(
                        message = "资金不足！需要 ${type.baseCost.toInt()} 万元"
                    )
                    return@mutateSchool false
                }
                school.cash -= type.baseCost
                school.facilities.add(
                    Facility(type = type, level = 1, condition = 100f)
                )
                true
            }
            if (result != null) {
                audioManager.playBuildFacility()
                gameEngine.notifyFactionDecision(SchoolDecision.BUILD_FACILITY)
                _state.value = _state.value.copy(message = "${type.displayName} 建成！")
            } else {
                audioManager.playEventNegative()
            }
        }
    }

    fun upgradeFacility(facilityId: String) {
        viewModelScope.safeLaunch {
            var name = ""
            var lv = 0
            val result = schoolRepository.mutateSchool { school ->
                val idx = school.facilities.indexOfFirst { it.id == facilityId }
                if (idx == -1) return@mutateSchool false
                val facility = school.facilities[idx]
                val type = facility.type
                if (facility.level >= type.maxLevel) {
                    _state.value = _state.value.copy(message = "${type.displayName} 已达最大等级")
                    return@mutateSchool false
                }
                val cost = FacilityBonusCalculator.getUpgradeCost(facility)
                if (school.cash < cost) {
                    _state.value = _state.value.copy(
                        message = "资金不足！升级需要 ${String.format("%.1f", cost)} 万元"
                    )
                    return@mutateSchool false
                }
                school.cash -= cost
                val upgraded = facility.copy(level = facility.level + 1)
                school.facilities[idx] = upgraded
                name = type.displayName
                lv = upgraded.level
                true
            }
            if (result != null) {
                audioManager.playLevelUp()
                _state.value = _state.value.copy(message = "$name 升级到 Lv.$lv！")
            } else {
                audioManager.playEventNegative()
            }
        }
    }

    fun upgradeCampus() {
        viewModelScope.safeLaunch {
            val school = schoolRepository.getSchool() ?: return@safeLaunch
            if (school.campusLevel >= GameBalanceConfig.MAX_SCHOOL_LEVEL) {
                _state.value = _state.value.copy(message = "校园已达最高等级！")
                return@safeLaunch
            }
            val req = GameBalanceConfig.getUpgradeRequirements(school.campusLevel + 1)
            val teacherCount = teacherRepository.getTeachers().size
            val classCount = teachingManager.config.totalClasses
            val studentCount = studentRepository.getActiveStudentCount()
            val yearsAtLevel = school.currentYear - school.levelUpYear
            val failures = buildList {
                if (school.cash < req.cashCost) add("资金 ${req.cashCost.toInt()}万")
                if (school.reputation < req.minReputation) add("声誉 ${req.minReputation}")
                if (teacherCount < req.minTeachers) add("教师 ${req.minTeachers}人")
                if (classCount < req.minClasses) add("班级 ${req.minClasses}个")
                if (studentCount < req.minStudents) add("学生 ${req.minStudents}人")
                if (yearsAtLevel < req.minYearsAtCurrentLevel) add("运营满 ${req.minYearsAtCurrentLevel}年")
            }
            if (failures.isNotEmpty()) {
                _state.value = _state.value.copy(
                    message = "升级条件不足：${failures.joinToString("、")}"
                )
                audioManager.playEventNegative()
                return@safeLaunch
            }
            schoolRepository.upgradeCampus()
            gameEngine.notifyFactionDecision(SchoolDecision.EXPAND_CAMPUS)
            audioManager.playLevelUp()
            val newLevel = schoolRepository.getSchool()?.campusLevel ?: school.campusLevel
            val unlocked = GameBalanceConfig.getNewlyUnlockedModules(school.campusLevel, newLevel)
            val unlockText = if (unlocked.isNotEmpty()) {
                " 新开放：${unlocked.joinToString("、") { it.displayName }}"
            } else ""
            _state.value = _state.value.copy(
                message = "校园升级成功！当前 Lv.$newLevel（赠送1间教室）$unlockText"
            )
        }
    }

    companion object {
        fun collegeDrawable(type: CollegeType): Int = when (type) {
            CollegeType.LIBERAL_ARTS -> com.arktools.xiaozhang.R.drawable.bld_liberal
            CollegeType.ART -> com.arktools.xiaozhang.R.drawable.bld_art
            CollegeType.MEDICINE -> com.arktools.xiaozhang.R.drawable.bld_medicine
            else -> com.arktools.xiaozhang.R.drawable.bld_generic
        }

        fun facilityDrawable(type: FacilityType): Int = when (type) {
            FacilityType.LIBRARY -> com.arktools.xiaozhang.R.drawable.bld_library
            FacilityType.DORMITORY -> com.arktools.xiaozhang.R.drawable.bld_dorm
            else -> com.arktools.xiaozhang.R.drawable.bld_generic
        }
    }
}
