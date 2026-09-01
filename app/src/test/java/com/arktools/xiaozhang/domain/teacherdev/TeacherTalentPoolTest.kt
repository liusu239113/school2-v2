package com.arktools.xiaozhang.domain.teacherdev

import com.arktools.xiaozhang.domain.alumni.Alumnus
import com.arktools.xiaozhang.domain.alumni.CareerLevel
import com.arktools.xiaozhang.domain.alumni.CareerPath
import com.arktools.xiaozhang.domain.model.Teacher
import com.arktools.xiaozhang.domain.model.TeacherLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeacherTalentPoolTest {

    private fun generator(level: TeacherLevel, count: Int): List<Teacher> =
        List(count) { index ->
            Teacher(
                id = "${level.name}-$index",
                name = "候选$index",
                level = level,
                role = com.arktools.xiaozhang.domain.model.TeacherRole.MATH,
                teaching = 300,
                research = 300,
                management = 300,
                psychology = 300,
                salary = 1.0
            )
        }

    @Test
    fun poolIsStableWithinYearAndRefreshesNextYear() {
        val manager = TeacherDevelopmentManager()
        manager.ensureAnnualTalentPool(2026, 2, emptyList(), ::generator)
        val first = manager.state.value.talentPool.map { it.id }
        manager.ensureAnnualTalentPool(2026, 6, emptyList(), ::generator)
        assertEquals(first, manager.state.value.talentPool.map { it.id })

        manager.unlockChannel(TalentChannel.AD)
        manager.ensureAnnualTalentPool(2027, 3, emptyList(), ::generator)
        assertEquals(2027, manager.state.value.talentPoolYear)
        assertTrue(manager.state.value.unlockedTalentChannels.isEmpty())
    }

    @Test
    fun candidateConsumptionCanRollback() {
        val manager = TeacherDevelopmentManager()
        manager.ensureAnnualTalentPool(2026, 1, emptyList(), ::generator)
        val candidate = manager.candidatesForChannel(TalentChannel.AD).first()

        assertNotNull(manager.consumeTalentCandidate(candidate.id))
        assertFalse(manager.candidatesForChannel(TalentChannel.AD).any { it.id == candidate.id })
        manager.restoreTalentCandidate(candidate.id)
        assertTrue(manager.candidatesForChannel(TalentChannel.AD).any { it.id == candidate.id })
    }

    @Test
    fun hiredAlumnusDoesNotReturnNextYear() {
        val alumnus = Alumnus(
            id = "alumni-1",
            name = "李研",
            graduationRating = 5f,
            career = CareerPath.RESEARCH,
            careerLevel = CareerLevel.MIDDLE,
            successPotential = 0.9f,
            satisfaction = 90f,
            donationWillingness = 0.5f,
            monthsSinceGraduation = 36
        )
        val manager = TeacherDevelopmentManager()
        manager.ensureAnnualTalentPool(2026, 4, listOf(alumnus), ::generator)
        val returning = manager.state.value.talentPool.first { it.source == TalentSource.ALUMNI_RETURN }
        manager.consumeTalentCandidate(returning.id)
        manager.ensureAnnualTalentPool(2027, 4, listOf(alumnus), ::generator)

        assertTrue(manager.state.value.talentPool.none { it.sourceAlumniId == alumnus.id })
    }

    @Test
    fun poolAndChannelsSurviveJsonRoundTrip() {
        val manager = TeacherDevelopmentManager()
        manager.ensureAnnualTalentPool(2026, 3, emptyList(), ::generator)
        manager.unlockChannel(TalentChannel.SCHOOL)
        val json = manager.toJson()

        val restored = TeacherDevelopmentManager()
        restored.restoreFromJson(json)
        assertEquals(2026, restored.state.value.talentPoolYear)
        assertTrue(restored.isChannelUnlocked(TalentChannel.SCHOOL))
        assertEquals(manager.state.value.talentPool.size, restored.state.value.talentPool.size)
    }
}
