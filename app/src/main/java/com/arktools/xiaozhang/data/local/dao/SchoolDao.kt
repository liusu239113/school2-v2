package com.arktools.xiaozhang.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.arktools.xiaozhang.data.local.entity.SchoolEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SchoolDao {
    @Query(
        "SELECT id, name, principalName, cash, marketCap, reputation, starRating, " +
            "foundedYear, currentYear, currentMonth, currentDay, campusLevel, levelUpYear, " +
            "maxTeachers, branchSchools, hasOwnTextbook, hasOwnTech, totalCoursesReleased, " +
            "totalRevenue, wasNearBankrupt, principalJson, lastYearEndProcessingYear, " +
            "lastMonthlySettlementYear, lastMonthlySettlementMonth, lastSaveTime " +
            "FROM schools LIMIT 1"
    )
    fun getSchoolCoreFlow(): Flow<com.arktools.xiaozhang.data.local.entity.SchoolCoreEntity?>

    @Query(
        "SELECT id, name, principalName, cash, marketCap, reputation, starRating, " +
            "foundedYear, currentYear, currentMonth, currentDay, campusLevel, levelUpYear, " +
            "maxTeachers, branchSchools, hasOwnTextbook, hasOwnTech, totalCoursesReleased, " +
            "totalRevenue, wasNearBankrupt, principalJson, lastYearEndProcessingYear, " +
            "lastMonthlySettlementYear, lastMonthlySettlementMonth, lastSaveTime " +
            "FROM schools LIMIT 1"
    )
    suspend fun getSchoolCore(): com.arktools.xiaozhang.data.local.entity.SchoolCoreEntity?

    @Query("SELECT * FROM schools LIMIT 1")
    fun getSchoolFlow(): Flow<SchoolEntity?>

    @Query("SELECT * FROM schools LIMIT 1")
    suspend fun getSchool(): SchoolEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchool(school: SchoolEntity)

    @Update
    suspend fun updateSchool(school: SchoolEntity)

    @Query("UPDATE schools SET principalJson = :principalJson, lastSaveTime = :lastSaveTime WHERE id = :schoolId")
    suspend fun updatePrincipalState(
        schoolId: String,
        principalJson: String,
        lastSaveTime: Long
    ): Int

    @Query(
        "UPDATE schools SET cash = cash - :amount, " +
            "lastSaveTime = CASE WHEN lastSaveTime >= :now " +
            "THEN lastSaveTime + 1 ELSE :now END " +
            "WHERE id = :schoolId AND cash >= :amount"
    )
    suspend fun deductCashIfEnough(
        schoolId: String,
        amount: Double,
        now: Long
    ): Int

    @Query(
        "UPDATE schools SET lastSaveTime = " +
            "CASE WHEN lastSaveTime >= :now THEN lastSaveTime + 1 ELSE :now END " +
            "WHERE id = :schoolId"
    )
    suspend fun touchRevision(schoolId: String, now: Long): Int

    @Query(
        "UPDATE schools SET classTierMapJson = :classTierMapJson, " +
            "lastSaveTime = CASE WHEN lastSaveTime >= :now " +
            "THEN lastSaveTime + 1 ELSE :now END " +
            "WHERE id = :schoolId"
    )
    suspend fun updateClassTierMap(
        schoolId: String,
        classTierMapJson: String,
        now: Long
    ): Int

    @Query(
        "UPDATE schools SET classTierMapJson = :classTierMapJson, " +
            "lastYearEndProcessingYear = :processingYear, " +
            "lastSaveTime = CASE WHEN lastSaveTime >= :now " +
            "THEN lastSaveTime + 1 ELSE :now END " +
            "WHERE id = :schoolId " +
            "AND currentYear = :processingYear AND currentMonth >= 6 " +
            "AND lastYearEndProcessingYear < :processingYear"
    )
    suspend fun completeStudentYearEnd(
        schoolId: String,
        processingYear: Int,
        classTierMapJson: String,
        now: Long
    ): Int

    @Query(
        "UPDATE schools SET cash = cash + :cashBonus, " +
            "reputation = MAX(0, reputation + :reputationDelta), " +
            "alumniJson = :alumniJson, " +
            "employmentJson = :employmentJson, " +
            "pressureJson = :pressureJson, " +
            "timetableJson = :timetableJson, " +
            "headTeacherMapJson = :headTeacherMapJson, " +
            "classTierMapJson = :classTierMapJson, " +
            "lastSaveTime = CASE WHEN lastSaveTime >= :now " +
            "THEN lastSaveTime + 1 ELSE :now END " +
            "WHERE id = :schoolId AND lastSaveTime = :expectedLastSaveTime"
    )
    suspend fun commitGraduationProjection(
        schoolId: String,
        cashBonus: Double,
        reputationDelta: Long,
        expectedLastSaveTime: Long,
        alumniJson: String,
        employmentJson: String,
        pressureJson: String,
        timetableJson: String,
        headTeacherMapJson: String,
        classTierMapJson: String,
        now: Long
    ): Int

    @Query(
        "UPDATE schools SET cash = MAX(-100.0, cash - :expense), " +
            "teacherDevJson = :teacherDevJson, " +
            "pressureJson = CASE WHEN :pressureJson IS NULL " +
            "THEN pressureJson ELSE :pressureJson END, " +
            "timetableJson = CASE WHEN :timetableJson IS NULL " +
            "THEN timetableJson ELSE :timetableJson END, " +
            "lastSaveTime = CASE WHEN lastSaveTime >= :now " +
            "THEN lastSaveTime + 1 ELSE :now END " +
            "WHERE id = :schoolId"
    )
    suspend fun commitTeacherDevelopmentState(
        schoolId: String,
        expense: Double,
        teacherDevJson: String,
        pressureJson: String?,
        timetableJson: String?,
        now: Long
    ): Int

    @Query("DELETE FROM schools")
    suspend fun deleteAll()

    @Query("DELETE FROM teachers")
    suspend fun deleteAllTeachers()

    @Query("DELETE FROM courses")
    suspend fun deleteAllCourses()

    @Query("DELETE FROM teaching_methods")
    suspend fun deleteAllTeachingMethods()

    @Query("DELETE FROM stocks")
    suspend fun deleteAllStocks()

    @Query("DELETE FROM stock_holdings")
    suspend fun deleteAllStockHoldings()

    @Query("DELETE FROM stock_price_history")
    suspend fun deleteAllStockPriceHistory()

    @Query("DELETE FROM students")
    suspend fun deleteAllStudents()

    @Transaction
    suspend fun clearGameTables() {
        deleteAllStockPriceHistory()
        deleteAllStockHoldings()
        deleteAllStocks()
        deleteAllStudents()
        deleteAllTeachingMethods()
        deleteAllCourses()
        deleteAllTeachers()
        deleteAll()
    }

    @Transaction
    suspend fun replaceWithNewSchool(school: SchoolEntity) {
        clearGameTables()
        insertSchool(school)
    }
}
