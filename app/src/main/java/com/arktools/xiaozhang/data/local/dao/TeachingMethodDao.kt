package com.arktools.xiaozhang.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.arktools.xiaozhang.data.local.entity.TeachingMethodEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TeachingMethodDao {
    @Query("SELECT * FROM teaching_methods WHERE schoolId = :schoolId")
    fun getMethodsBySchoolFlow(schoolId: String): Flow<List<TeachingMethodEntity>>

    @Query("SELECT * FROM teaching_methods WHERE schoolId = :schoolId")
    suspend fun getMethodsBySchool(schoolId: String): List<TeachingMethodEntity>

    @Query("SELECT * FROM teaching_methods WHERE schoolId = :schoolId AND isUnlocked = 1")
    suspend fun getUnlockedMethods(schoolId: String): List<TeachingMethodEntity>

    @Query("SELECT * FROM teaching_methods WHERE schoolId = :schoolId AND id = :methodId")
    suspend fun getMethodById(schoolId: String, methodId: String): TeachingMethodEntity?

    @Query(
        "UPDATE teaching_methods SET isResearching = 1, " +
            "remainingResearchDays = researchDays " +
            "WHERE schoolId = :schoolId AND id = :methodId " +
            "AND isUnlocked = 0 AND isResearching = 0"
    )
    suspend fun startResearch(schoolId: String, methodId: String): Int

    @Query(
        "UPDATE teaching_methods SET remainingResearchDays = remainingResearchDays - 1 " +
            "WHERE schoolId = :schoolId AND isResearching = 1 " +
            "AND remainingResearchDays > 0"
    )
    suspend fun advanceResearchDay(schoolId: String): Int

    @Query(
        "UPDATE teaching_methods SET isUnlocked = 1, isResearching = 0, " +
            "remainingResearchDays = 0 WHERE schoolId = :schoolId " +
            "AND isResearching = 1 AND remainingResearchDays <= 0"
    )
    suspend fun completeReadyResearch(schoolId: String): Int

    @Query(
        "UPDATE teaching_methods SET isUnlocked = 1 " +
            "WHERE schoolId = :schoolId AND id = :methodId AND isUnlocked = 0"
    )
    suspend fun unlockMethod(schoolId: String, methodId: String): Int

    @Query(
        "SELECT COUNT(*) FROM teaching_methods " +
            "WHERE schoolId = :schoolId AND isUnlocked = 1"
    )
    suspend fun countUnlockedMethods(schoolId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMethod(method: TeachingMethodEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMethods(methods: List<TeachingMethodEntity>)

    @Update
    suspend fun updateMethod(method: TeachingMethodEntity)

    @Query("DELETE FROM teaching_methods WHERE schoolId = :schoolId")
    suspend fun deleteMethodsBySchool(schoolId: String)
}
