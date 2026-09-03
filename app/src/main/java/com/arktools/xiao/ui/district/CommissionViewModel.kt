package com.arktools.xiao.ui.district

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiao.audio.AudioManager
import com.arktools.xiao.domain.engine.GameEngine
import com.arktools.xiao.domain.partner.PartnerCommission
import com.arktools.xiao.domain.policy.CollegeType
import com.arktools.xiao.domain.repository.SchoolRepository
import com.arktools.xiao.util.safeLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * 企业合作委托 ViewModel：要约列表 + 执行中列表 + 接单/谢绝。
 */
@HiltViewModel
class CommissionViewModel @Inject constructor(
    private val schoolRepository: SchoolRepository,
    private val gameEngine: GameEngine,
    private val audioManager: AudioManager
) : ViewModel() {

    data class UiState(
        val offers: List<PartnerCommission> = emptyList(),
        val active: List<PartnerCommission> = emptyList(),
        val completedCount: Int = 0,
        val failedCount: Int = 0,
        val reputation: Long = 0L,
        val campusLevel: Int = 1,
        val foundedColleges: Set<String> = emptySet(),
        val operationalFacilities: Set<String> = emptySet(),
        val message: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.safeLaunch {
            schoolRepository.getSchoolFlow().collect { school ->
                if (school == null) return@collect
                refresh(school.cash >= 0, school.reputation, school.campusLevel)
            }
        }
        viewModelScope.safeLaunch {
            gameEngine.partnerCommissionManager.state.collect { rebuild() }
        }
    }

    private var cachedCash: Double = 0.0

    private fun refresh(@Suppress("UNUSED_PARAMETER") cash: Boolean, reputation: Long, campusLevel: Int) {
        val cur = _state.value
        _state.value = cur.copy(reputation = reputation, campusLevel = campusLevel)
        rebuild()
    }

    private fun rebuild() {
        viewModelScope.safeLaunch {
            val school = schoolRepository.getSchool()
            val manager = gameEngine.partnerCommissionManager
            val st = manager.state.value
            val founded = gameEngine.policyManager.policies.value.collegeDevelopment.founded
            val facilities = school?.facilities ?: emptyList()
            _state.value = _state.value.copy(
                offers = st.offers,
                active = st.active,
                completedCount = st.completedCount,
                failedCount = st.failedCount,
                reputation = school?.reputation ?: 0L,
                campusLevel = school?.campusLevel ?: 1,
                foundedColleges = founded.map { it.name }.toSet(),
                operationalFacilities = facilities
                    .filter { it.isOperational }
                    .map { it.type.name }
                    .toSet()
            )
        }
    }

    fun requirementBlocked(commission: PartnerCommission): String? =
        gameEngine.partnerCommissionManager.canAccept(
            commission,
            _state.value.reputation,
            _state.value.foundedColleges
        ) { typeName -> typeName in _state.value.operationalFacilities }

    fun accept(id: String) {
        audioManager.playButtonClick()
        viewModelScope.safeLaunch {
            val result = gameEngine.acceptPartnerCommission(id)
            _state.value = _state.value.copy(message = result.message)
            if (result.success) audioManager.playBuildFacility()
            else audioManager.playEventNegative()
            rebuild()
        }
    }

    fun decline(id: String) {
        audioManager.playButtonClick()
        viewModelScope.safeLaunch {
            val result = gameEngine.declinePartnerCommission(id)
            _state.value = _state.value.copy(message = result.message)
            rebuild()
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
