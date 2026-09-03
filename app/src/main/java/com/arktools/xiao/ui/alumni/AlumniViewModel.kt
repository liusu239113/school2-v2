package com.arktools.xiao.ui.alumni

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiao.domain.alumni.*
import com.arktools.xiao.domain.employment.EmploymentMarket
import com.arktools.xiao.domain.employment.EmploymentMarketState
import com.arktools.xiao.domain.repository.SchoolRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.arktools.xiao.util.safeLaunch

@HiltViewModel
class AlumniViewModel @Inject constructor(
    private val alumniNetwork: AlumniNetwork,
    private val schoolRepository: SchoolRepository,
    private val employmentMarket: EmploymentMarket
) : ViewModel() {

    val alumni: StateFlow<List<Alumnus>> = alumniNetwork.alumni
    val stats: StateFlow<AlumniStats> = alumniNetwork.stats
    val networkLevel: StateFlow<Int> = alumniNetwork.networkLevel
    val industryConnections: StateFlow<Map<CareerPath, Int>> = alumniNetwork.industryConnections
    val graduationSummaries: StateFlow<List<com.arktools.xiao.domain.alumni.GraduationBatchSummary>> = alumniNetwork.graduationSummaries

    val schoolLevel: StateFlow<Int> = schoolRepository.getSchoolFlow()
        .map { it?.campusLevel ?: 1 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1)

    private val _selectedFilter = MutableStateFlow<CareerPath?>(null)
    val selectedFilter: StateFlow<CareerPath?> = _selectedFilter

    private val _lastActivityResult = MutableStateFlow<AlumniActivityResult?>(null)
    val lastActivityResult: StateFlow<AlumniActivityResult?> = _lastActivityResult

    // ========== 就业市场相关 ==========
    val employmentState: StateFlow<EmploymentMarketState> = employmentMarket.state

    fun getGraduatesForDisplay() = employmentMarket.getGraduatesForDisplay()

    fun setFilter(career: CareerPath?) {
        _selectedFilter.value = career
    }

    fun getFilteredAlumni(): List<Alumnus> {
        val filter = _selectedFilter.value
        val all = alumni.value
        return if (filter == null) {
            all.sortedByDescending { it.careerLevel.ordinal }
        } else {
            all.filter { it.career == filter }.sortedByDescending { it.careerLevel.ordinal }
        }
    }

    fun getTopAlumni(count: Int = 5): List<Alumnus> {
        return alumniNetwork.getTopAlumni(count)
    }

    fun getNetworkLevelProgress(): Pair<Int, Int> {
        return alumniNetwork.getNetworkLevelProgress()
    }

    fun canHostActivity(type: AlumniActivityType): Boolean {
        return alumniNetwork.canHostActivity(type)
    }

    fun getActivityCost(type: AlumniActivityType): Long {
        return alumniNetwork.getActivityCost(type)
    }

    fun getActivityCooldown(): Int {
        return alumniNetwork.getActivityCooldown()
    }

    fun hostActivity(type: AlumniActivityType) {
        viewModelScope.safeLaunch {
            val activityCost = type.baseCost.toDouble()
            // 余额不足时不创建活动，避免“没花钱白嫖活动效果”
            val affordable = schoolRepository.mutateSchool { school ->
                school.cash >= activityCost
            } != null
            if (!affordable) {
                _lastActivityResult.value = null
                return@safeLaunch
            }

            val result = alumniNetwork.hostActivity(type)
            _lastActivityResult.value = result
            // 处理结果（捐赠加入学校资金，声誉加入学校，扣除费用 — 原子操作）
            result?.let {
                schoolRepository.mutateSchool { school ->
                    if (it.donationGained > 0) {
                        school.cash += it.donationGained / 10000.0
                    }
                    if (it.reputationGained > 0) {
                        school.reputation += it.reputationGained
                    }
                    school.cash -= activityCost
                    true
                }
            }
        }
    }

    fun dismissActivityResult() {
        _lastActivityResult.value = null
    }

    fun getIndustryBonus(career: CareerPath): Float {
        return alumniNetwork.getIndustryBonus(career)
    }
}
