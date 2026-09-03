package com.arktools.xiaozhang.domain.achievement

import com.arktools.xiaozhang.domain.model.School
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AchievementManager @Inject constructor() {

    private val achievements = AchievementRegistry.getAllAchievements().toMutableList()

    private val _unlockedAchievements = MutableStateFlow<List<Achievement>>(emptyList())
    val unlockedAchievements: StateFlow<List<Achievement>> = _unlockedAchievements.asStateFlow()

    private val _newAchievement = MutableSharedFlow<Achievement>()
    val newAchievement: SharedFlow<Achievement> = _newAchievement.asSharedFlow()

    /**
     * Check all achievements against current school state.
     * Called monthly (day 1). Returns the newly unlocked achievements this call,
     * so the engine can surface them as notifications.
     */
    suspend fun checkAchievements(school: School): List<Achievement> {
        val newlyUnlocked = mutableListOf<Achievement>()
        achievements.forEach { achievement ->
            if (!achievement.unlocked && achievement.condition(school)) {
                achievement.unlocked = true
                achievement.unlockTime = System.currentTimeMillis()
                newlyUnlocked.add(achievement)
            }
        }
        if (newlyUnlocked.isNotEmpty()) {
            _unlockedAchievements.value = achievements.filter { it.unlocked }
            newlyUnlocked.forEach { _newAchievement.emit(it) }
        }
        return newlyUnlocked
    }

    fun getAll(): List<Achievement> = achievements.toList()

    fun getUnlocked(): List<Achievement> = achievements.filter { it.unlocked }

    fun getProgress(): Float {
        val total = achievements.size
        val unlocked = achievements.count { it.unlocked }
        return if (total > 0) unlocked.toFloat() / total else 0f
    }

    /**
     * Reset all achievements (for new game).
     */
    fun reset() {
        achievements.forEach {
            it.unlocked = false
            it.unlockTime = 0
        }
        _unlockedAchievements.value = emptyList()
    }

    fun toJson(): String {
        return try {
            val unlocked = achievements.filter { it.unlocked }
            val data = AchievementPersistData(
                unlockedIds = unlocked.map { it.id },
                unlockTimes = unlocked.map { it.unlockTime }
            )
            Json.encodeToString(data)
        } catch (_: Exception) { "" }
    }

    fun restoreFromJson(json: String) {
        if (json.isBlank()) return
        try {
            val data = Json { ignoreUnknownKeys = true }.decodeFromString<AchievementPersistData>(json)
            data.unlockedIds.forEachIndexed { index, id ->
                achievements.find { it.id == id }?.let { achievement ->
                    achievement.unlocked = true
                    achievement.unlockTime = data.unlockTimes.getOrElse(index) { System.currentTimeMillis() }
                }
            }
            _unlockedAchievements.value = achievements.filter { it.unlocked }
        } catch (e: Exception) {
            throw IllegalArgumentException("AchievementManager.restoreFromJson failed", e)
        }
    }
}

@Serializable
data class AchievementPersistData(
    val unlockedIds: List<String> = emptyList(),
    val unlockTimes: List<Long> = emptyList()
)
