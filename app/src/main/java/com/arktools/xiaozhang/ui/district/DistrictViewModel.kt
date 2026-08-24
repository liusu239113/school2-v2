package com.arktools.xiaozhang.ui.district

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiaozhang.domain.engine.GameBalanceConfig
import com.arktools.xiaozhang.domain.engine.GameEngine
import com.arktools.xiaozhang.domain.engine.SchoolDecision
import com.arktools.xiaozhang.domain.expansion.CampusExpansionManager
import com.arktools.xiaozhang.domain.expansion.CampusExpansionState
import com.arktools.xiaozhang.domain.expansion.CampusZoneType
import com.arktools.xiaozhang.domain.model.DistrictType
import com.arktools.xiaozhang.domain.model.School
import com.arktools.xiaozhang.domain.repository.CourseRepository
import com.arktools.xiaozhang.domain.repository.SchoolRepository
import com.arktools.xiaozhang.domain.repository.StudentRepository
import com.arktools.xiaozhang.domain.repository.TeacherRepository
import com.arktools.xiaozhang.domain.teaching.TeachingManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import com.arktools.xiaozhang.util.safeLaunch

@HiltViewModel
class DistrictViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    private val schoolRepository: SchoolRepository,
    private val teacherRepository: TeacherRepository,
    private val studentRepository: StudentRepository,
    private val expansionManager: CampusExpansionManager,
    private val teachingManager: TeachingManager,
    private val gameEngine: GameEngine
) : ViewModel() {

    // ===== 学区管理 =====
    private val _districtStats = MutableStateFlow<Map<DistrictType, DistrictStats>>(emptyMap())
    val districtStats: StateFlow<Map<DistrictType, DistrictStats>> = _districtStats.asStateFlow()

    private val _school = MutableStateFlow<School?>(null)
    val school: StateFlow<School?> = _school.asStateFlow()

    private val _upgradeMessage = MutableStateFlow<String?>(null)
    val upgradeMessage: StateFlow<String?> = _upgradeMessage.asStateFlow()

    // ===== 校区扩建 =====
    val expansionState: StateFlow<CampusExpansionState> = expansionManager.state

    init {
        loadDistrictStats()
        loadSchool()
    }

    private fun loadSchool() {
        viewModelScope.safeLaunch {
            schoolRepository.getSchoolFlow().collect {
                _school.value = it
            }
        }
    }

    private fun loadDistrictStats() {
        viewModelScope.safeLaunch {
            courseRepository.getCoursesFlow().collect { courses ->
                val stats = DistrictType.entries.associateWith { district ->
                    val districtCourses = courses.filter { it.targetDistrict == district }
                    DistrictStats(
                        courseCount = districtCourses.size,
                        totalEnrollment = districtCourses.sumOf { it.enrollment },
                        totalRevenue = districtCourses.sumOf { it.revenue },
                        averageScore = if (districtCourses.isNotEmpty()) {
                            districtCourses.map { it.qualityScore }.average().toFloat()
                        } else 0f
                    )
                }
                _districtStats.value = stats
            }
        }
    }

    fun upgradeCampus() {
        viewModelScope.safeLaunch {
            val school = schoolRepository.getSchool() ?: return@safeLaunch
            if (school.campusLevel >= GameBalanceConfig.MAX_SCHOOL_LEVEL) {
                _upgradeMessage.value = "校舍已达最高等级！"
                return@safeLaunch
            }
            val req = GameBalanceConfig.getUpgradeRequirements(school.campusLevel + 1)
            val teacherCount = teacherRepository.getTeachers().size
            val classCount = teachingManager.config.totalClasses
            val studentCount = studentRepository.getActiveStudentCount()
            val yearsAtLevel = school.currentYear - school.levelUpYear

            if (school.cash < req.cashCost) {
                _upgradeMessage.value = "资金不足！需要 ${req.cashCost.toInt()}万（当前 ${school.cash.toInt()}万）"
                return@safeLaunch
            }
            if (school.reputation < req.minReputation) {
                _upgradeMessage.value = "声望不足！需要 ${req.minReputation}（当前 ${school.reputation}）"
                return@safeLaunch
            }
            if (req.minTeachers > 0 && teacherCount < req.minTeachers) {
                _upgradeMessage.value = "教师不足！需要 ${req.minTeachers} 人（当前 ${teacherCount} 人）"
                return@safeLaunch
            }
            if (req.minClasses > 0 && classCount < req.minClasses) {
                _upgradeMessage.value = "班级不足！需要 ${req.minClasses} 个班（当前 ${classCount} 个班）。请到「教学」页面增加班型配置。"
                return@safeLaunch
            }
            if (req.minStudents > 0 && studentCount < req.minStudents) {
                _upgradeMessage.value = "学生不足！需要 ${req.minStudents} 人（当前 ${studentCount} 人）"
                return@safeLaunch
            }
            if (req.minYearsAtCurrentLevel > 0 && yearsAtLevel < req.minYearsAtCurrentLevel) {
                _upgradeMessage.value = "需在当前等级运营满 ${req.minYearsAtCurrentLevel} 年（已运营 ${yearsAtLevel} 年）"
                return@safeLaunch
            }
            schoolRepository.upgradeCampus()
            _school.value = schoolRepository.getSchool()
            gameEngine.notifyFactionDecision(SchoolDecision.EXPAND_CAMPUS)
            _upgradeMessage.value = "校舍升级成功！当前 Lv.${(_school.value?.campusLevel ?: (school.campusLevel + 1))}（赠送1间教室）"
        }
    }

    suspend fun getUpgradeConditions(): List<UpgradeCondition> {
        // 从数据库重新读取最新数据，避免 _school.value 的 Flow 延迟
        val school = schoolRepository.getSchool() ?: _school.value ?: return emptyList()
        if (school.campusLevel >= GameBalanceConfig.MAX_SCHOOL_LEVEL) return emptyList()

        val req = GameBalanceConfig.getUpgradeRequirements(school.campusLevel + 1)
        val teacherCount = teacherRepository.getTeachers().size
        val classCount = teachingManager.config.totalClasses
        val studentCount = studentRepository.getActiveStudentCount()
        val yearsAtLevel = school.currentYear - school.levelUpYear

        val conditions = mutableListOf<UpgradeCondition>()
        conditions.add(UpgradeCondition("资金", "${school.cash.toInt()}万", "${req.cashCost.toInt()}万", school.cash >= req.cashCost))
        conditions.add(UpgradeCondition("声望", "${school.reputation}", "${req.minReputation}", school.reputation >= req.minReputation))
        if (req.minTeachers > 0) {
            conditions.add(UpgradeCondition("教师", "${teacherCount}人", "${req.minTeachers}人", teacherCount >= req.minTeachers))
        }
        if (req.minClasses > 0) {
            conditions.add(UpgradeCondition("班级", "${classCount}个", "${req.minClasses}个", classCount >= req.minClasses))
        }
        if (req.minStudents > 0) {
            conditions.add(UpgradeCondition("学生", "${studentCount}人", "${req.minStudents}人", studentCount >= req.minStudents))
        }
        if (req.minYearsAtCurrentLevel > 0) {
            conditions.add(UpgradeCondition("运营年数", "${yearsAtLevel}年", "${req.minYearsAtCurrentLevel}年", yearsAtLevel >= req.minYearsAtCurrentLevel))
        }
        return conditions
    }

    fun clearUpgradeMessage() {
        _upgradeMessage.value = null
    }

    fun isDistrictUnlocked(district: DistrictType): Boolean {
        val school = _school.value ?: return false
        return GameBalanceConfig.isDistrictUnlocked(district, school.campusLevel, school.reputation)
    }

    fun getEffectiveMaxCourses(district: DistrictType): Int {
        val school = _school.value ?: return district.maxConcurrentCourses
        return district.maxConcurrentCourses + GameBalanceConfig.getDistrictCourseBonus(school.campusLevel)
    }

    fun getEffectiveExposure(district: DistrictType): Double {
        val school = _school.value ?: return district.baseExposure
        return district.baseExposure * GameBalanceConfig.getDistrictExposureBonus(school.campusLevel)
    }

    fun getEffectiveCommission(district: DistrictType): Double {
        val school = _school.value ?: return district.commissionRate
        return district.commissionRate * GameBalanceConfig.getDistrictCommissionDiscount(school.campusLevel)
    }

    // ===== 校区扩建方法 =====

    /**
     * 升级校区扩建等级（单校区→扩展校区→双校区→...）
     * 条件：资金足够 + 已建成建筑数达到当前等级上限的60%
     */
    fun upgradeCampusExpansionLevel() {
        viewModelScope.safeLaunch {
            _upgradeMessage.value =
                gameEngine.upgradeCampusExpansionLevel().message
        }
    }

    fun getAvailableZoneTypes() = expansionManager.getAvailableZoneTypes()

    fun startConstruction(
        type: CampusZoneType,
        name: String = type.displayName,
        quality: Int = 1
    ) {
        viewModelScope.safeLaunch {
            _upgradeMessage.value = gameEngine.startCampusConstruction(
                type,
                name,
                quality
            ).message
        }
    }

    fun investInZone(zoneId: String, requestedAmountWan: Double) {
        viewModelScope.safeLaunch {
            _upgradeMessage.value = gameEngine.investInCampusZone(
                zoneId,
                requestedAmountWan
            ).message
        }
    }

    fun repairZone(zoneId: String) {
        viewModelScope.safeLaunch {
            _upgradeMessage.value =
                gameEngine.repairCampusZone(zoneId).message
        }
    }

    fun upgradeZoneQuality(zoneId: String) {
        viewModelScope.safeLaunch {
            _upgradeMessage.value =
                gameEngine.upgradeCampusZoneQuality(zoneId).message
        }
    }

    fun getUpgradeQualityCost(zoneId: String): Double = expansionManager.getUpgradeQualityCost(zoneId)

    fun getCapacityUsagePercent() = expansionManager.getCapacityUsagePercent()

    // ===== 数据类 =====
    data class DistrictStats(
        val courseCount: Int,
        val totalEnrollment: Long,
        val totalRevenue: Double,
        val averageScore: Float
    )

    data class UpgradeCondition(
        val label: String,
        val current: String,
        val required: String,
        val met: Boolean
    )
}
