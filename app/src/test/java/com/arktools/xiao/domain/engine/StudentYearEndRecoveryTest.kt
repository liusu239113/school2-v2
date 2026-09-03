package com.arktools.xiao.domain.engine

import com.arktools.xiao.domain.model.GradeLevel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentYearEndRecoveryTest {

    @Test
    fun gradeThreeStudentDoesNotGraduateBeforeJuneOfFourthSchoolYear() {
        assertFalse(
            isStudentGraduationDue(
                gradeLevel = GradeLevel.GRADE_3,
                enrollYear = 1988,
                processingYear = 1991,
                processingMonth = 5,
                graduationGrade = GradeLevel.GRADE_3
            )
        )
    }

    @Test
    fun gradeThreeStudentGraduatesFromJuneOfFourthSchoolYear() {
        assertTrue(
            isStudentGraduationDue(
                gradeLevel = GradeLevel.GRADE_3,
                enrollYear = 1988,
                processingYear = 1991,
                processingMonth = 6,
                graduationGrade = GradeLevel.GRADE_3
            )
        )
    }

    @Test
    fun overdueGradeThreeStudentCanRecoverAfterGraduationYear() {
        assertTrue(
            isStudentGraduationDue(
                gradeLevel = GradeLevel.GRADE_3,
                enrollYear = 1988,
                processingYear = 1992,
                processingMonth = 1,
                graduationGrade = GradeLevel.GRADE_3
            )
        )
    }

    @Test
    fun legacyGradeThreeStudentWithUnknownEnrollmentYearWaitsUntilJune() {
        assertFalse(
            isStudentGraduationDue(
                gradeLevel = GradeLevel.GRADE_3,
                enrollYear = 0,
                processingYear = 1991,
                processingMonth = 5,
                graduationGrade = GradeLevel.GRADE_3
            )
        )
        assertTrue(
            isStudentGraduationDue(
                gradeLevel = GradeLevel.GRADE_3,
                enrollYear = 0,
                processingYear = 1991,
                processingMonth = 6,
                graduationGrade = GradeLevel.GRADE_3
            )
        )
    }

    @Test
    fun monthlySettlementRecoversWhenFirstDayWasMissed() {
        assertTrue(
            isMonthlySettlementDue(
                currentYear = 1991,
                currentMonth = 6,
                currentDay = 1,
                lastSettlementYear = 1991,
                lastSettlementMonth = 5
            )
        )
        assertFalse(
            isMonthlySettlementDue(
                currentYear = 1991,
                currentMonth = 6,
                currentDay = 1,
                lastSettlementYear = 1991,
                lastSettlementMonth = 6
            )
        )
        assertTrue(
            isMonthlySettlementDue(
                currentYear = 1991,
                currentMonth = 6,
                currentDay = 2,
                lastSettlementYear = 1991,
                lastSettlementMonth = 5
            )
        )
    }

    @Test
    fun fourYearStudentGraduatesInFifthCalendarYear() {
        assertFalse(
            isStudentGraduationDue(
                gradeLevel = GradeLevel.GRADE_4,
                enrollYear = 2024,
                processingYear = 2027,
                processingMonth = 12,
                graduationGrade = GradeLevel.GRADE_4
            )
        )
        assertTrue(
            isStudentGraduationDue(
                gradeLevel = GradeLevel.GRADE_4,
                enrollYear = 2024,
                processingYear = 2028,
                processingMonth = 6,
                graduationGrade = GradeLevel.GRADE_4
            )
        )
    }

    @Test
    fun monthlySettlementRecoversAcrossYearBoundary() {
        assertTrue(
            isMonthlySettlementDue(
                currentYear = 1992,
                currentMonth = 1,
                currentDay = 1,
                lastSettlementYear = 1991,
                lastSettlementMonth = 12
            )
        )
    }

    @Test
    fun nonGradeThreeStudentNeverGraduatesThroughRecovery() {
        assertFalse(
            isStudentGraduationDue(
                gradeLevel = GradeLevel.GRADE_2,
                enrollYear = 1987,
                processingYear = 1992,
                processingMonth = 12
            )
        )
    }
}
