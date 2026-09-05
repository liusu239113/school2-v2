package com.arktools.xiao.ui.studentlife

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiao.domain.engine.GameEngine
import com.arktools.xiao.domain.repository.SchoolRepository
import com.arktools.xiao.domain.studentlife.ComplaintAction
import com.arktools.xiao.domain.studentlife.LifeAspect
import com.arktools.xiao.domain.studentlife.LifeIssue
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

    fun resolveIssue(issueId: String) {
        viewModelScope.safeLaunch {
            _message.value = gameEngine.resolveStudentLifeIssue(issueId).message
        }
    }

    /** 投诉卡片上的「立刻去做」：扩容/维修/开专项，做完再点检查结案。 */
    fun actOnComplaint(issue: LifeIssue) {
        viewModelScope.safeLaunch {
            _message.value = when (issue.requiredAction) {
                ComplaintAction.EXPAND_DORM ->
                    gameEngine.expandStudentLifeCapacity(LifeAspect.DORMITORY, 20).message
                ComplaintAction.REPAIR_DORM ->
                    gameEngine.repairStudentLifeFacility(LifeAspect.DORMITORY).message
                ComplaintAction.EXPAND_CANTEEN ->
                    gameEngine.expandStudentLifeCapacity(LifeAspect.CAFETERIA, 20).message
                ComplaintAction.CHANGE_MENU -> {
                    val prog = studentLifeManager.getAvailablePrograms()
                        .firstOrNull { it.aspect == LifeAspect.CAFETERIA }
                    if (prog != null) gameEngine.setStudentLifeProgramActive(prog.id, true).message
                    else "食堂专项已经开着，点检查结案"
                }
                ComplaintAction.OPEN_COUNSELING -> {
                    val prog = studentLifeManager.getAvailablePrograms()
                        .firstOrNull { it.aspect == LifeAspect.PSYCHOLOGY }
                    if (prog != null) gameEngine.setStudentLifeProgramActive(prog.id, true).message
                    else "心理专项已经开着，点检查结案"
                }
                ComplaintAction.REPAIR_GYM, ComplaintAction.OPEN_CLINIC ->
                    gameEngine.repairStudentLifeFacility(LifeAspect.HEALTH).message
            }
        }
    }
}
