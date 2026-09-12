package com.arktools.xiao.domain.engine

import com.arktools.xiao.domain.model.Facility
import com.arktools.xiao.domain.model.FacilityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FacilityMaintenanceGraceTest {

    @Test
    fun newHallDoesNotDecayOrBreakInFirstSixMonths() {
        val manager = PressureSystemManager()
        val hall = Facility(
            type = FacilityType.AUDITORIUM,
            level = 1,
            condition = 100f,
            operationalMonths = 0
        )
        repeat(6) {
            val events = manager.checkFacilityMaintenance(listOf(hall), campusLevel = 1)
            assertTrue(events.isEmpty())
            assertEquals(100f, hall.condition, 0.01f)
        }
        assertEquals(6, hall.operationalMonths)
    }

    @Test
    fun oldHallCanDecayAfterGrace() {
        val manager = PressureSystemManager()
        val hall = Facility(
            type = FacilityType.DORMITORY,
            level = 1,
            condition = 100f,
            operationalMonths = 6
        )
        manager.checkFacilityMaintenance(listOf(hall), campusLevel = 1)
        assertTrue(hall.condition < 100f)
        assertTrue(hall.operationalMonths >= 7)
    }
}
