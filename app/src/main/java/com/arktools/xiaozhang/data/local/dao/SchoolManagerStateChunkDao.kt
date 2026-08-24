package com.arktools.xiaozhang.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.arktools.xiaozhang.data.local.entity.SchoolManagerStateChunkEntity

@Dao
interface SchoolManagerStateChunkDao {
    @Query(
        "SELECT * FROM school_manager_state_chunks " +
            "WHERE schoolId = :schoolId ORDER BY stateKey, chunkIndex"
    )
    suspend fun getChunks(schoolId: String): List<SchoolManagerStateChunkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChunks(chunks: List<SchoolManagerStateChunkEntity>)

    @Query("DELETE FROM school_manager_state_chunks WHERE schoolId = :schoolId")
    suspend fun deleteBySchoolId(schoolId: String)

    @Query("DELETE FROM school_manager_state_chunks")
    suspend fun deleteAll()
}
