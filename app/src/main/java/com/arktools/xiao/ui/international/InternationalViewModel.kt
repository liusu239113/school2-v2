package com.arktools.xiao.ui.international

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiao.audio.AudioManager
import com.arktools.xiao.domain.graduate.GraduateSchoolManager
import com.arktools.xiao.domain.international.InternationalProgramManager
import com.arktools.xiao.domain.international.PartnerDef
import com.arktools.xiao.domain.policy.SchoolPolicyManager
import com.arktools.xiao.domain.repository.SchoolRepository
import com.arktools.xiao.util.safeLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * 国际交流：签海外合作院校 → 留学生名额/年度声誉 → 冲击 Lv6 的必要条件。
 */
@HiltViewModel
class InternationalViewModel @Inject constructor(
    private val schoolRepository: SchoolRepository,
    private val policyManager: SchoolPolicyManager,
    private val audioManager: AudioManager
) : ViewModel() {

    data class UiState(
        val campusLevel: Int = 1,
        val cash: Double = 0.0,
        val reputation: Long = 0L,
        val signedIds: List<String> = emptyList(),
        val intlCount: Int = 0,
        val outgoingCount: Int = 0,
        val intlGraduated: Int = 0,
        val monthlyIncome: Double = 0.0,
        val annualRep: Long = 0L,
        val message: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.safeLaunch {
            schoolRepository.getSchoolFlow().collect { school ->
                if (school == null) return@collect
                val cur = _state.value
                _state.value = cur.copy(
                    campusLevel = school.campusLevel,
                    cash = school.cash,
                    reputation = school.reputation
                )
                rebuild()
            }
        }
        viewModelScope.safeLaunch {
            policyManager.internationalManager.state.collect { rebuild() }
        }
    }

    private fun rebuild() {
        val im = policyManager.internationalManager
        val st = im.state.value
        _state.value = _state.value.copy(
            signedIds = st.signedPartnerIds,
            intlCount = st.intlStudents.size,
            outgoingCount = st.outgoing.size,
            intlGraduated = st.totalIntlGraduated,
            monthlyIncome = im.monthlyIncomeWan(),
            annualRep = im.annualReputation()
        )
    }

    fun catalog(): List<PartnerDef> = InternationalProgramManager.CATALOG

    fun signed(id: String): Boolean = _state.value.signedIds.contains(id)

    fun sign(def: PartnerDef) {
        audioManager.playButtonClick()
        val cur = _state.value
        if (cur.signedIds.contains(def.id)) return
        if (cur.campusLevel < 5) {
            _state.value = cur.copy(message = "校园 Lv.5 解锁国际合作")
            audioManager.playEventNegative()
            return
        }
        if (cur.cash < def.feeWan) {
            _state.value = cur.copy(message = "资金不足！签约需要 ${def.feeWan.toInt()} 万")
            audioManager.playEventNegative()
            return
        }
        if (cur.reputation < def.repRequired) {
            _state.value = cur.copy(message = "声誉不足！需要 ${def.repRequired} 声誉")
            audioManager.playEventNegative()
            return
        }
        viewModelScope.safeLaunch {
            val result = schoolRepository.mutateSchool { school ->
                if (school.cash < def.feeWan) return@mutateSchool false
                school.cash -= def.feeWan
                if (!policyManager.internationalManager.signPartner(def.id)) return@mutateSchool false
                school.policyJson = policyManager.toJson()
                true
            }
            if (result != null) {
                audioManager.playCollegeFound()
                _state.value = _state.value.copy(
                    message = "已与${def.name}建立合作：每年国际生名额 +${def.intlQuota}，年度声誉 +${def.annualReputation}"
                )
            }
        }
    }

    fun dispatchOutgoing() {
        audioManager.playButtonClick()
        val cur = _state.value
        if (cur.signedIds.isEmpty()) {
            _state.value = cur.copy(message = "先签订至少一所合作院校")
            return
        }
        viewModelScope.safeLaunch {
            val partnerId = cur.signedIds.first()
            val names = (1..2).map {
                GraduateSchoolManager.randomName()
            }
            policyManager.internationalManager.dispatchOutgoing(partnerId, names)
            schoolRepository.mutateSchool { school ->
                school.policyJson = policyManager.toJson()
                true
            }
            _state.value = _state.value.copy(message = "已派出 2 名交换生，1 年后学成归国（声誉 +25/人）")
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
