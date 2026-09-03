package com.arktools.xiao.domain.seasonal

import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonalActivityPersistenceTest {

    @Test
    fun miniGameScoresSurviveSerialization() {
        val manager = SeasonalActivityManager()
        manager.applyMiniGamePerformance("activity-1", 0.85f)

        val json = manager.toJson()
        val data = Json { ignoreUnknownKeys = true }
            .decodeFromString<SeasonalPersistData>(json)

        assertEquals(0.85f, data.miniGameScores["activity-1"] ?: 0f, 0.0001f)
    }

    @Test
    fun resetClearsPersistedMiniGameScoresAndExpenses() {
        val manager = SeasonalActivityManager()
        manager.applyMiniGamePerformance("activity-1", 1f)
        manager.reset()

        val json = manager.toJson()
        val data = Json { ignoreUnknownKeys = true }
            .decodeFromString<SeasonalPersistData>(json)

        assertTrue(data.miniGameScores.isEmpty())
        assertEquals(0L, data.monthlyExpenses)
    }
}
