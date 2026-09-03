package com.arktools.xiao.domain.alumni

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlumniNetworkTest {

    @Test
    fun legacySummaryWithoutSettlementStateIsNotRepaid() {
        val network = AlumniNetwork()
        network.restoreFromJson(
            """
            {
              "graduationSummaries": [
                {
                  "year": 2023,
                  "totalStudents": 30,
                  "averageScore": 520.0,
                  "highestScore": 680.0,
                  "bengkeRate": 80.0,
                  "key985Count": 3,
                  "qingbeiCount": 1
                }
              ]
            }
            """.trimIndent()
        )

        assertTrue(network.getPendingGraduationSettlementYears().isEmpty())
        assertTrue(network.isGraduationSettlementCompleted(2023))
    }

    @Test
    fun newSummaryRemainsPendingUntilCompleted() {
        val network = AlumniNetwork()
        network.recordGraduationBatch(
            year = 2024,
            totalStudents = 20,
            averageScore = 510f,
            highestScore = 700f,
            bengkeRate = 75f,
            key985Count = 2,
            qingbeiCount = 1,
            topStudents = emptyList(),
            universityDistribution = mapOf("本科" to 15)
        )

        assertEquals(setOf(2024), network.getPendingGraduationSettlementYears())
        assertFalse(network.isGraduationSettlementCompleted(2024))
        assertTrue(
            network.completeGraduationSettlement(
                year = 2024,
                cashBonus = 80.0,
                reputationDelta = 12L
            )
        )

        assertTrue(network.getPendingGraduationSettlementYears().isEmpty())
        assertTrue(network.isGraduationSettlementCompleted(2024))
        val summary = network.graduationSummaries.value.single()
        assertEquals(80.0, summary.settledCashBonus, 0.0)
        assertEquals(12L, summary.settledReputationDelta)
    }

    @Test
    fun pendingAndCompletedSettlementStatesSurviveJsonRoundTrip() {
        val pending = AlumniNetwork()
        pending.recordGraduationBatch(
            year = 2026,
            totalStudents = 15,
            averageScore = 530f,
            highestScore = 710f,
            bengkeRate = 80f,
            key985Count = 3,
            qingbeiCount = 1,
            topStudents = emptyList(),
            universityDistribution = emptyMap()
        )

        val pendingRestored = AlumniNetwork()
        pendingRestored.restoreFromJson(pending.toJson())
        assertEquals(
            setOf(2026),
            pendingRestored.getPendingGraduationSettlementYears()
        )

        assertTrue(
            pendingRestored.completeGraduationSettlement(
                year = 2026,
                cashBonus = 110.0,
                reputationDelta = 25L
            )
        )
        val completedRestored = AlumniNetwork()
        completedRestored.restoreFromJson(pendingRestored.toJson())

        assertTrue(
            completedRestored
                .getPendingGraduationSettlementYears()
                .isEmpty()
        )
        assertTrue(
            completedRestored.isGraduationSettlementCompleted(2026)
        )
        val summary = completedRestored.graduationSummaries.value.single()
        assertEquals(110.0, summary.settledCashBonus, 0.0)
        assertEquals(25L, summary.settledReputationDelta)
    }

    @Test
    fun rebuildingCompletedSummaryDoesNotReopenSettlement() {
        val network = AlumniNetwork()
        network.recordGraduationBatch(
            year = 2025,
            totalStudents = 10,
            averageScore = 500f,
            highestScore = 650f,
            bengkeRate = 70f,
            key985Count = 1,
            qingbeiCount = 0,
            topStudents = emptyList(),
            universityDistribution = emptyMap()
        )
        assertTrue(network.completeGraduationSettlement(2025, 10.0, 3L))

        network.recordGraduationBatch(
            year = 2025,
            totalStudents = 12,
            averageScore = 505f,
            highestScore = 660f,
            bengkeRate = 75f,
            key985Count = 2,
            qingbeiCount = 0,
            topStudents = emptyList(),
            universityDistribution = emptyMap()
        )

        val summary = network.graduationSummaries.value.single()
        assertEquals(true, summary.settlementCompleted)
        assertEquals(10.0, summary.settledCashBonus, 0.0)
        assertEquals(3L, summary.settledReputationDelta)
        assertFalse(network.completeGraduationSettlement(2025, 20.0, 6L))
    }
}
