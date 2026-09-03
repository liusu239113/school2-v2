package com.arktools.xiao.domain.clubactivity

import org.junit.Assert.assertEquals
import org.junit.Test

class ClubActivityMonthlyResultTest {

    @Test
    fun combinesYuanActivityBudgetAndWanCompetitionFee() {
        val result = ClubActivityMonthlyResult(
            activityBudgetYuan = 5_000L,
            competitionRegistrationWan = 50.0
        )

        assertEquals(50.5, result.totalExpenseWan, 0.0001)
    }

    @Test
    fun convertsActivityBudgetFromYuanOnlyOnce() {
        val result = ClubActivityMonthlyResult(activityBudgetYuan = 12_500L)

        assertEquals(1.25, result.totalExpenseWan, 0.0001)
    }
}
