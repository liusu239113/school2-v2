package com.arktools.xiao.ui.conference

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiao.domain.conference.AcademicConferenceManager
import com.arktools.xiao.domain.conference.AcademicConferenceState
import com.arktools.xiao.domain.conference.AcademicField
import com.arktools.xiao.domain.conference.ConferenceRole
import com.arktools.xiao.domain.conference.ConferenceType
import com.arktools.xiao.domain.engine.GameEngine
import com.arktools.xiao.domain.repository.SchoolRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConferenceViewModel @Inject constructor(
    private val conferenceManager: AcademicConferenceManager,
    private val schoolRepository: SchoolRepository,
    private val gameEngine: GameEngine
) : ViewModel() {

    val state: StateFlow<AcademicConferenceState> = conferenceManager.state

    val schoolLevel: StateFlow<Int> = schoolRepository.getSchoolFlow()
        .map { it?.campusLevel ?: 1 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1)

    private val schoolYear: StateFlow<Int> = schoolRepository.getSchoolFlow()
        .map { it?.currentYear ?: 2024 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 2024)

    private val schoolMonth: StateFlow<Int> = schoolRepository.getSchoolFlow()
        .map { it?.currentMonth ?: 1 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1)

    private val schoolCash: StateFlow<Double> = schoolRepository.getSchoolFlow()
        .map { it?.cash ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    fun getSchoolLevel(): Int = schoolLevel.value

    fun getCurrentYear(): Int = schoolYear.value

    fun getCurrentMonth(): Int = schoolMonth.value

    private val _createResult = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val createResult: StateFlow<String?> = _createResult

    fun clearCreateResult() {
        _createResult.value = null
    }

    fun createConference(
        type: ConferenceType,
        role: ConferenceRole,
        field: AcademicField
    ) {
        viewModelScope.launch {
            _createResult.value = gameEngine.startAcademicConference(type, role, field).message
        }
    }

    /**
     * 获取可用会议类型及其可用状态
     */
    fun getAvailableTypes(): List<Pair<ConferenceType, String?>> {
        val absMonth = schoolYear.value * 12 + schoolMonth.value
        return conferenceManager.getAvailableConferenceTypes(schoolLevel.value, absMonth)
    }

    /**
     * 检查是否有足够资金举办
     */
    fun canAfford(type: ConferenceType, role: ConferenceRole): Boolean {
        val cost = type.baseCost * role.costMultiplier
        return schoolCash.value >= cost
    }
}
