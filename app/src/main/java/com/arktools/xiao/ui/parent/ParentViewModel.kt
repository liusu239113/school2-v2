package com.arktools.xiao.ui.parent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiao.domain.engine.GameEngine
import com.arktools.xiao.domain.parent.*
import com.arktools.xiao.domain.repository.SchoolRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.arktools.xiao.util.safeLaunch

@HiltViewModel
class ParentViewModel @Inject constructor(
    private val parentSatisfactionManager: ParentSatisfactionManager,
    private val schoolRepository: SchoolRepository,
    private val gameEngine: GameEngine
) : ViewModel() {

    val state: StateFlow<ParentState> = parentSatisfactionManager.state

    fun resolveComplaint(complaintId: String) {
        parentSatisfactionManager.resolveComplaint(complaintId)
    }

    fun ignoreComplaint(complaintId: String) {
        parentSatisfactionManager.ignoreComplaint(complaintId)
    }

    fun scheduleMeeting(type: MeetingType, year: Int, month: Int, studentCount: Int): Double {
        // costPerParent单位是元，转换为万元
        val costInWan = type.costPerParent * studentCount.coerceAtMost(200) * 0.3 / 10000.0
        viewModelScope.safeLaunch {
            val paid = schoolRepository.mutateSchool { school ->
                if (school.cash < costInWan) {
                    gameEngine.cashShortfallAdManager.offerIfShort(costInWan, school.cash, "家长会 · ${type.displayName}")
                    false
                } else {
                    school.cash -= costInWan
                    gameEngine.financialReportManager.recordExpense(
                        com.arktools.xiao.domain.finance.ExpenseCategory.ACTIVITY_COST,
                        costInWan,
                        "家长会 · ${type.displayName}"
                    )
                    school.financialReportJson = gameEngine.financialReportManager.toJson()
                    true
                }
            }
            // 扣款成功后才创建会议，余额不足时不会出现“免费开会”
            if (paid != null) {
                parentSatisfactionManager.scheduleMeeting(type, year, month, studentCount)
            }
        }
        return costInWan
    }

    fun completeMeeting(meetingId: String) {
        parentSatisfactionManager.completeMeeting(meetingId)
    }

    fun getMeetingTypes(): List<MeetingType> = parentSatisfactionManager.getMeetingTypes()

    fun getPendingComplaintCount(): Int = parentSatisfactionManager.getPendingComplaintCount()
}
