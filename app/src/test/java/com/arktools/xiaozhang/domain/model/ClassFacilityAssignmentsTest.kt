package com.arktools.xiaozhang.domain.model

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
}
