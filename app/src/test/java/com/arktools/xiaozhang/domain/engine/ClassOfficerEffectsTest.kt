package com.arktools.xiaozhang.domain.engine

import com.arktools.xiaozhang.domain.model.ClassOfficer
import com.arktools.xiaozhang.domain.model.ClassOfficerRole
import com.arktools.xiaozhang.domain.model.ClassTier
import com.arktools.xiaozhang.domain.model.GradeLevel
import com.arktools.xiaozhang.domain.model.SchoolClass
import com.arktools.xiaozhang.domain.model.Student
import com.arktools.xiaozhang.domain.model.StudentAttributes
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassOfficerEffectsTest {

    @Test
    fun qualifiedOfficersImproveDerivedClassMetrics() {
        val classId = "class-1"
        val student = Student(
            id = "student-1",
            name = "张三",
            courseId = "course",
            schoolId = "school",
            classId = classId,
            attributes = StudentAttributes(
                intelligence = 85f,
                physical = 80f,
                social = 82f,
                creativity = 78f,
                morality = 80f
            ),
            academicScore = 70f,
            satisfaction = 70f
        )
        val without = SchoolClass(
            id = classId,
            schoolId = "school",
            gradeLevel = GradeLevel.GRADE_1,
            classNumber = 1,
            classTier = ClassTier.NORMAL
        )
        val withOfficers = without.copy()
        val manager = ClassManager()

        manager.updateClassMetrics(listOf(without), listOf(student), emptyList())
        manager.updateClassMetrics(
            listOf(withOfficers),
            listOf(student),
            emptyList(),
            mapOf(
                classId to mapOf(
                    ClassOfficerRole.MONITOR to ClassOfficer(student.id, student.name),
                    ClassOfficerRole.STUDY_COMMITTEE to ClassOfficer(student.id, student.name)
                )
            )
        )

        assertTrue(withOfficers.classSpirit > without.classSpirit)
        assertTrue(withOfficers.cohesion > without.cohesion)
        assertTrue(withOfficers.avgAcademicScore > without.avgAcademicScore)
    }
}
