package com.arktools.xiaozhang.domain.timetable

import com.arktools.xiaozhang.domain.model.ClassTier
import com.arktools.xiaozhang.domain.model.GradeLevel
import com.arktools.xiaozhang.domain.model.SchoolClass
import com.arktools.xiaozhang.domain.model.Teacher
import com.arktools.xiaozhang.domain.model.TeacherLevel
import com.arktools.xiaozhang.domain.model.TeacherRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableManagerTest {

    @Test
    fun regenerateAllTimetablesAvoidsSameTeacherSameSlotAcrossClasses() {
        val manager = TimetableManager()
        val classes = listOf(
            schoolClass("c1", 1),
            schoolClass("c2", 2),
            schoolClass("c3", 3)
        )
        val teachers = listOf(
            teacher("t-math-1", "数学甲", TeacherRole.MATH),
            teacher("t-math-2", "数学乙", TeacherRole.MATH),
            teacher("t-chinese-1", "语文甲", TeacherRole.CHINESE),
            teacher("t-chinese-2", "语文乙", TeacherRole.CHINESE),
            teacher("t-english-1", "英语甲", TeacherRole.ENGLISH),
            teacher("t-english-2", "英语乙", TeacherRole.ENGLISH),
            teacher("t-physics", "物理甲", TeacherRole.PHYSICS),
            teacher("t-chem", "化学甲", TeacherRole.CHEMISTRY),
            teacher("t-bio", "生物甲", TeacherRole.BIOLOGY),
            teacher("t-hist", "历史甲", TeacherRole.HISTORY),
            teacher("t-geo", "地理甲", TeacherRole.GEOGRAPHY),
            teacher("t-pol", "政治甲", TeacherRole.POLITICS),
            teacher("t-pe", "体育甲", TeacherRole.PE),
            teacher("t-art", "美术甲", TeacherRole.ART),
            teacher("t-music", "音乐甲", TeacherRole.MUSIC)
        )

        manager.regenerateAllTimetables(classes, teachers)
        val conflicts = manager.listConflicts()
        assertTrue("expected no conflicts but got $conflicts", conflicts.isEmpty())
    }

    @Test
    fun headTeacherLoadPenaltyIsReflectedInConflictFreeSchedule() {
        val manager = TimetableManager()
        val classes = listOf(
            schoolClass("c1", 1, headTeacherId = "t-math-1"),
            schoolClass("c2", 2)
        )
        val teachers = listOf(
            teacher("t-math-1", "数学甲", TeacherRole.MATH),
            teacher("t-math-2", "数学乙", TeacherRole.MATH),
            teacher("t-chinese-1", "语文甲", TeacherRole.CHINESE),
            teacher("t-english-1", "英语甲", TeacherRole.ENGLISH),
            teacher("t-physics", "物理甲", TeacherRole.PHYSICS),
            teacher("t-chem", "化学甲", TeacherRole.CHEMISTRY),
            teacher("t-bio", "生物甲", TeacherRole.BIOLOGY),
            teacher("t-hist", "历史甲", TeacherRole.HISTORY),
            teacher("t-geo", "地理甲", TeacherRole.GEOGRAPHY),
            teacher("t-pol", "政治甲", TeacherRole.POLITICS),
            teacher("t-pe", "体育甲", TeacherRole.PE)
        )
        manager.regenerateAllTimetables(classes, teachers)
        assertEquals(0, manager.listConflicts().size)
        assertEquals(2, manager.getAllTimetables().size)
    }

    private fun schoolClass(id: String, number: Int, headTeacherId: String? = null): SchoolClass {
        return SchoolClass(
            id = id,
            schoolId = "school-1",
            gradeLevel = GradeLevel.GRADE_1,
            classNumber = number,
            classTier = ClassTier.NORMAL,
            headTeacherId = headTeacherId,
            studentCount = 40
        )
    }

    private fun teacher(id: String, name: String, role: TeacherRole): Teacher {
        return Teacher(
            id = id,
            name = name,
            level = TeacherLevel.B,
            role = role,
            teaching = 70,
            research = 60,
            management = 50,
            psychology = 50,
            salary = 1.2,
            isWorking = true
        )
    }
}
