package com.arktools.xiaozhang.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.arktools.xiaozhang.data.local.entity.SchoolManagerStateEntity

@Dao
interface SchoolManagerStateDao {
    @Query("SELECT * FROM school_manager_states WHERE schoolId = :schoolId")
    suspend fun getStates(schoolId: String): List<SchoolManagerStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStates(states: List<SchoolManagerStateEntity>)

    @Query("DELETE FROM school_manager_states WHERE schoolId = :schoolId")
    suspend fun deleteBySchoolId(schoolId: String)

    @Query("DELETE FROM school_manager_states")
    suspend fun deleteAll()
}
