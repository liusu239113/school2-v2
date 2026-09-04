package com.arktools.xiao.ui.scholarship

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiao.domain.repository.SchoolRepository
import com.arktools.xiao.domain.scholarship.*
import com.arktools.xiao.util.safeLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ScholarshipViewModel @Inject constructor(
    private val scholarshipManager: ScholarshipManager,
    private val schoolRepository: SchoolRepository
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
        persistScholarships()
    }

    fun createFromTemplate(templateIndex: Int, year: Int) {
        scholarshipManager.createFromTemplate(templateIndex, year)
        persistScholarships()
    }

    fun cancelScholarship(scholarshipId: String) {
        scholarshipManager.cancelScholarship(scholarshipId)
        persistScholarships()
    }

    fun adjustRecipients(scholarshipId: String, delta: Int) {
        scholarshipManager.adjustRecipients(scholarshipId, delta)
        persistScholarships()
    }

    fun getTemplates(year: Int): List<Scholarship> = scholarshipManager.getTemplates(year)

    private fun persistScholarships() {
        viewModelScope.safeLaunch {
            schoolRepository.mutateSchool { school ->
                school.scholarshipJson = scholarshipManager.toJson()
                true
            }
        }
    }
}
