package com.arktools.xiao.ui.studentlife

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiao.domain.engine.GameEngine
import com.arktools.xiao.domain.repository.SchoolRepository
import com.arktools.xiao.domain.studentlife.LifeAspect
import com.arktools.xiao.domain.studentlife.StudentLifeManager
import com.arktools.xiao.domain.studentlife.StudentLifeState
import com.arktools.xiao.util.safeLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class StudentLifeViewModel @Inject constructor(
    private val studentLifeManager: StudentLifeManager,
    private val schoolRepository: SchoolRepository,
    private val gameEngine: GameEngine
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val state: StateFlow<StudentLifeState> = studentLifeManager.state

    val schoolLevel: StateFlow<Int> = schoolRepository.getSchoolFlow()
        .map { it?.campusLevel ?: 1 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1)

    fun getAvailablePrograms() = studentLifeManager.getAvailablePrograms()

    fun activateProgram(programId: String) {
        viewModelScope.safeLaunch {
            _message.value = gameEngine.setStudentLifeProgramActive(
                programId,
                true
            ).message
        }
    }

    fun deactivateProgram(programId: String) {
        viewModelScope.safeLaunch {
            _message.value = gameEngine.setStudentLifeProgramActive(
                programId,
                false
            ).message
        }
    }

    fun upgradeFacility(aspect: LifeAspect) {
        viewModelScope.safeLaunch {
            _message.value =
                gameEngine.upgradeStudentLifeFacility(aspect).message
        }
    }

    fun canUpgradeFacility(aspect: LifeAspect): Boolean {
        return studentLifeManager.canUpgradeFacility(aspect, schoolLevel.value)
    }

    fun getUpgradeCost(aspect: LifeAspect): Long = studentLifeManager.getUpgradeCost(aspect)

    fun repairFacility(aspect: LifeAspect) {
        viewModelScope.safeLaunch {
            _message.value =
                gameEngine.repairStudentLifeFacility(aspect).message
        }
    }

    fun repairAllFacilities() {
        viewModelScope.safeLaunch {
            _message.value =
                gameEngine.repairAllStudentLifeFacilities().message
        }
    }

    fun getExpandCost(aspect: LifeAspect, additional: Int): Long {
        return studentLifeManager.getExpandCost(aspect, additional)
    }

    fun expandCapacity(aspect: LifeAspect, additional: Int) {
        viewModelScope.safeLaunch {
            _message.value = gameEngine.expandStudentLifeCapacity(
                aspect,
                additional
            ).message
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
