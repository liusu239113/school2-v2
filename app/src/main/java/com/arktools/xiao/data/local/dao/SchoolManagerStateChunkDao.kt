package com.arktools.xiao.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.arktools.xiao.data.local.entity.SchoolManagerStateChunkEntity

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

    /** 只清理指定 Manager 的分片，供增量落库使用（未变化的 Manager 保持原行不动）。 */
    @Query(
        "DELETE FROM school_manager_state_chunks " +
            "WHERE schoolId = :schoolId AND stateKey IN (:stateKeys)"
    )
    suspend fun deleteByKeys(schoolId: String, stateKeys: List<String>)

    @Query("DELETE FROM school_manager_state_chunks")
    suspend fun deleteAll()
}
