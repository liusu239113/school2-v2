package com.arktools.xiao.domain.employment

import com.arktools.xiao.domain.model.UniversityTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmploymentMarketTest {

    @Test
    fun registerGraduateIsIdempotentForHistoricalBackfill() {
        val market = EmploymentMarket()
        val first = market.registerGraduate(
            name = "张三",
            year = 2020,
            month = 6,
            gaoKaoScore = 650f,
            universityTier = UniversityTier.NORMAL_985,
            universityName = "测试大学",
            satisfaction = 80f
        )
        val second = market.registerGraduate(
            name = "张三",
            year = 2020,
            month = 6,
            gaoKaoScore = 650f,
            universityTier = UniversityTier.NORMAL_985,
            universityName = "测试大学",
            satisfaction = 80f
        )
        assertTrue(first)
        assertFalse(second)
        assertEquals(1, market.state.value.graduates.size)
    }

    @Test
    fun differentStudentIdsRemainDistinctWhenLegacyFieldsMatch() {
        val market = EmploymentMarket()
        val first = market.registerGraduate(
            studentId = "student-1",
            name = "同名学生",
            year = 2026,
            month = 6,
            gaoKaoScore = 650f,
            universityTier = UniversityTier.NORMAL_985,
            universityName = "测试大学",
            satisfaction = 80f
        )
        val second = market.registerGraduate(
            studentId = "student-2",
            name = "同名学生",
            year = 2026,
            month = 6,
            gaoKaoScore = 650f,
            universityTier = UniversityTier.NORMAL_985,
            universityName = "测试大学",
            satisfaction = 80f
        )

        assertTrue(first)
        assertTrue(second)
        assertEquals(
            setOf("student-1", "student-2"),
            market.state.value.graduates.mapNotNull { it.studentId }.toSet()
        )
    }

    @Test
    fun historicalGraduateIsUpgradedWithStableStudentId() {
        val market = EmploymentMarket()
        market.registerGraduate(
            studentId = null,
            name = "历史学生",
            year = 2020,
            month = 6,
            gaoKaoScore = 650f,
            universityTier = UniversityTier.NORMAL_985,
            universityName = "测试大学",
            satisfaction = 80f
        )
        val added = market.registerGraduate(
            studentId = "student-stable-id",
            name = "历史学生",
            year = 2020,
            month = 6,
            gaoKaoScore = 650f,
            universityTier = UniversityTier.NORMAL_985,
            universityName = "测试大学",
            satisfaction = 80f
        )

        assertFalse(added)
        assertEquals(1, market.state.value.graduates.size)
        assertEquals(
            "student-stable-id",
            market.state.value.graduates.single().studentId
        )
    }

    @Test
    fun calibrateHistoricalGraduatesCompletesOverdueUniversityStudents() {
        val market = EmploymentMarket()
        market.registerGraduate(
            name = "李四",
            year = 2018,
            month = 6,
            gaoKaoScore = 700f,
            universityTier = UniversityTier.TOP_985,
            universityName = "顶尖大学",
            satisfaction = 90f
        )

        val result = market.calibrateHistoricalGraduates(currentYear = 2026, currentMonth = 6)
        assertTrue(result.changed)
        assertEquals(1, result.graduatedCount)

        val graduate = market.state.value.graduates.single()
        assertTrue(graduate.status != GraduateStatus.IN_UNIVERSITY)
        assertEquals(graduate.universityDuration, graduate.monthsInUniversity)
    }

    @Test
    fun calibrateHistoricalGraduatesUpdatesInProgressMonths() {
        val market = EmploymentMarket()
        market.registerGraduate(
            name = "王五",
            year = 2025,
            month = 6,
            gaoKaoScore = 620f,
            universityTier = UniversityTier.FIRST_TIER,
            universityName = "普通本科",
            satisfaction = 70f
        )

        val result = market.calibrateHistoricalGraduates(currentYear = 2026, currentMonth = 6)
        assertTrue(result.changed)
        val graduate = market.state.value.graduates.single()
        assertEquals(GraduateStatus.IN_UNIVERSITY, graduate.status)
        assertEquals(12, graduate.monthsInUniversity)
    }
}
