package com.arktools.xiao.domain.repository

import com.arktools.xiao.domain.model.BonusType
import com.arktools.xiao.domain.model.TeachingMethod
import kotlinx.coroutines.flow.Flow

enum class TeachingMethodUnlockStatus {
    SUCCESS,
    ALREADY_UNLOCKED,
    INSUFFICIENT_FUNDS,
    TIER_LOCKED,
    PREREQUISITE_LOCKED,
    UNAVAILABLE
}

data class TeachingMethodUnlockResult(
    val status: TeachingMethodUnlockStatus,
    val method: TeachingMethod? = null,
    val requiredUnlocks: Int = 0,
    val unlockedCount: Int = 0,
    val availableCash: Double = 0.0
)

interface ResearchRepository {
    fun getMethodsFlow(): Flow<List<TeachingMethod>>
    suspend fun getMethods(): List<TeachingMethod>
    suspend fun getUnlockedMethods(): List<TeachingMethod>
    suspend fun getMethodById(methodId: String): TeachingMethod?
    suspend fun unlockMethod(methodId: String): TeachingMethodUnlockResult
    suspend fun advanceResearchDay(): List<TeachingMethod>
    suspend fun initializeDefaultMethods(schoolId: String)
    suspend fun getUnlockedMethodBonus(methodIds: List<String>): Float
    suspend fun getUnlockedBonusByType(bonusType: BonusType): Float
    suspend fun deleteAll()
}
