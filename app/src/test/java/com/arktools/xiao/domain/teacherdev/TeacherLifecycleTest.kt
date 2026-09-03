package com.arktools.xiao.domain.teacherdev

import com.arktools.xiao.domain.model.TeacherLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TeacherLifecycleTest {

    private fun syncData(id: String = "teacher-1") = TeacherSyncData(
        id = id,
        legacyId = 1L,
        name = "王老师",
        subject = "数学",
        skill = 400f,
        level = TeacherLevel.B
    )

    @Test
    fun serviceTimeAccumulatesAcrossMonths() {
        val manager = TeacherDevelopmentManager()
        manager.syncTeachers(listOf(syncData()), 2026)
        repeat(12) { month ->
            manager.advanceMonth(2026, month + 1, 500L)
        }
        val profile = manager.state.value.teacherProfiles.single()
        assertEquals(12, profile.monthsOfService)
        assertEquals(1, profile.yearsOfService)
    }

    @Test
    fun retirementOnlyOccursDuringJanuarySettlement() {
        val manager = TeacherDevelopmentManager()
        manager.syncTeachers(listOf(syncData()), 2026)
        val profile = manager.state.value.teacherProfiles.single()
        val old = profile.copy(birthYear = 1960, retirementAge = 65, monthsSinceLastPromotion = 4)
        val state = manager.snapshotState().copy(teacherProfiles = listOf(old))
        manager.restoreSnapshot(state)

        val december = manager.advanceMonth(2026, 12, 500L)
        assertTrue(december.departures.none { it.reason == TeacherDepartureReason.RETIRED })
        val january = manager.advanceMonth(2027, 1, 500L)
        assertTrue(january.departures.any { it.reason == TeacherDepartureReason.RETIRED })
        assertTrue(manager.state.value.formerFaculty.any { it.teacherId == old.teacherId })
    }
}
