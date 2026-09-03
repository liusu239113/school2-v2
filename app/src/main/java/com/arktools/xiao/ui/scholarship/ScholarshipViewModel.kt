package com.arktools.xiao.ui.scholarship

import androidx.lifecycle.ViewModel
import com.arktools.xiao.domain.scholarship.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ScholarshipViewModel @Inject constructor(
    private val scholarshipManager: ScholarshipManager
) : ViewModel() {

    val state: StateFlow<ScholarshipState> = scholarshipManager.state

    fun createScholarship(
        name: String,
        tier: ScholarshipTier,
        criteria: ScholarshipCriteria,
        amountPerStudent: Double,
        maxRecipients: Int,
        year: Int,
        description: String
    ) {
        scholarshipManager.createScholarship(name, tier, criteria, amountPerStudent, maxRecipients, year, description)
    }

    fun createFromTemplate(templateIndex: Int, year: Int) {
        scholarshipManager.createFromTemplate(templateIndex, year)
    }

    fun cancelScholarship(scholarshipId: String) {
        scholarshipManager.cancelScholarship(scholarshipId)
    }

    fun getTemplates(year: Int): List<Scholarship> = scholarshipManager.getTemplates(year)
}
