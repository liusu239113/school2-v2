package com.arktools.xiao.ui.government

import androidx.lifecycle.ViewModel
import com.arktools.xiao.domain.government.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class GovernmentViewModel @Inject constructor(
    private val governmentInspectionManager: GovernmentInspectionManager
) : ViewModel() {

    val state: StateFlow<GovernmentState> = governmentInspectionManager.state

    fun startRectification(type: RectificationType, inspectionId: String, currentMonth: Int): Double {
        return governmentInspectionManager.startRectification(type, inspectionId, currentMonth)
    }

    fun getAvailableRectifications(inspectionId: String): List<RectificationType> {
        return governmentInspectionManager.getAvailableRectifications(inspectionId)
    }

    fun getLatestInspectionScores(): List<InspectionScore> {
        return governmentInspectionManager.getLatestInspectionScores()
    }
}
