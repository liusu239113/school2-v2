package com.arktools.xiaozhang.domain.model

import com.arktools.xiaozhang.ui.campus.CampusBuildTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassFacilityAssignmentsTest {

    @Test
    fun preservesValidAssignmentsAndFillsRemainingCapacity() {
        val result = ClassFacilityAssignments.reconcile(
            classIds = listOf("c1", "c2", "c3"),
            rooms = listOf("room-a" to 2, "room-b" to 2),
            existing = mapOf("c1" to "room-b", "stale" to "room-a")
        )

        assertEquals("room-b", result["c1"])
        assertEquals(3, result.size)
        assertFalse("stale" in result)
    }

    @Test
    fun neverExceedsRoomCapacity() {
        val result = ClassFacilityAssignments.reconcile(
            classIds = listOf("c1", "c2", "c3"),
            rooms = listOf("room-a" to 1),
            existing = emptyMap()
        )

        assertEquals(1, result.size)
        assertTrue(result.values.all { it == "room-a" })
    }

    @Test
    fun campusSpecsExposePrerequisitesAndDownstreamUses() {
        val lab = CampusBuildTypes.facilitySpec(FacilityType.LABORATORY)
        val conference = CampusBuildTypes.facilitySpec(FacilityType.CONFERENCE_CENTER)

        assertTrue(lab!!.prerequisiteColleges.contains(com.arktools.xiaozhang.domain.policy.CollegeType.SCIENCE))
        assertEquals("解锁理科实验课与应用科研", lab.downstream)
        assertTrue(conference!!.prerequisiteFacilities.contains(FacilityType.LIBRARY))
        assertTrue(conference.downstream.isNotBlank())
    }

    @Test
    fun campusPlacementRejectsTerrainAndOutOfBoundsCells() {
        val spec = CampusBuildTypes.facilitySpec(FacilityType.CLASSROOM)!!
        val placed = listOf(
            CampusBuildTypes.PlacedBuilding("F_CLASSROOM", 4, 4, facilityId = "room-1")
        )

        assertTrue(CampusBuildTypes.occupies(placed.single(), spec, 4, 4))
        assertFalse(CampusBuildTypes.occupies(placed.single(), spec, 6, 6))
        assertFalse(CampusBuildTypes.inUnlockedArea(-1, 0, 1))
        assertTrue(CampusBuildTypes.inUnlockedArea(4, 3, 1))
        assertEquals(48 * 32, CampusBuildTypes.GRID_W * CampusBuildTypes.GRID_H)
    }
}
