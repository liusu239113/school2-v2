package com.arktools.xiao.domain.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassOfficersTest {

    @Test
    fun migratesLegacyMonitorRecord() {
        val legacy = Json.encodeToString(
            mapOf("class-1" to ClassOfficer("student-1", "张三"))
        )

        val decoded = ClassOfficers.decode(legacy)

        assertEquals("student-1", decoded["class-1"]?.get(ClassOfficerRole.MONITOR)?.studentId)
    }

    @Test
    fun roundTripsMultipleRoles() {
        val source = mapOf(
            "class-1" to mapOf(
                ClassOfficerRole.MONITOR to ClassOfficer("student-1", "张三"),
                ClassOfficerRole.STUDY_COMMITTEE to ClassOfficer("student-2", "李四")
            )
        )

        assertEquals(source, ClassOfficers.decode(ClassOfficers.encode(source)))
    }

    @Test
    fun roleQualificationUsesStudentAttributes() {
        val strong = Student(
            id = "student-1",
            name = "张三",
            courseId = "course",
            schoolId = "school",
            classId = "class-1",
            attributes = StudentAttributes(intelligence = 80f, morality = 65f)
        )
        val weak = strong.copy(
            id = "student-2",
            attributes = StudentAttributes(intelligence = 40f, morality = 40f)
        )

        assertTrue(ClassOfficerRole.STUDY_COMMITTEE.qualification(strong).eligible)
        assertFalse(ClassOfficerRole.STUDY_COMMITTEE.qualification(weak).eligible)
    }
}
