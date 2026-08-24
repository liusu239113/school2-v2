package com.arktools.xiaozhang.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.arktools.xiaozhang.data.local.entity.CourseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses WHERE schoolId = :schoolId")
    fun getCoursesBySchoolFlow(schoolId: String): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE schoolId = :schoolId")
    suspend fun getCoursesBySchool(schoolId: String): List<CourseEntity>

    @Query("SELECT * FROM courses WHERE schoolId = :schoolId AND id = :courseId")
    suspend fun getCourseById(schoolId: String, courseId: String): CourseEntity?

    @Query("SELECT * FROM courses WHERE schoolId = :schoolId AND status = :status")
    suspend fun getCoursesByStatus(schoolId: String, status: String): List<CourseEntity>

    @Query("SELECT * FROM courses WHERE schoolId = :schoolId AND status IN ('PREPARING', 'TESTING')")
    suspend fun getActiveCourses(schoolId: String): List<CourseEntity>

    @Query("SELECT * FROM courses WHERE schoolId = :schoolId AND status = 'RELEASED'")
    suspend fun getReleasedCourses(schoolId: String): List<CourseEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCourse(course: CourseEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCourses(courses: List<CourseEntity>)

    @Query(
        "UPDATE courses SET preparationProgress = " +
            "MIN(100.0, preparationProgress + :progressDelta) " +
            "WHERE schoolId = :schoolId AND id = :courseId " +
            "AND status = 'PREPARING'"
    )
    suspend fun incrementPreparationProgress(
        schoolId: String,
        courseId: String,
        progressDelta: Float
    ): Int

    @Query(
        "UPDATE courses SET preparationProgress = 100.0, " +
            "designScore = :designScore, qualityScore = :qualityScore, " +
            "status = 'RELEASED', releaseDate = :releaseDate, " +
            "releaseYear = :releaseYear, releaseMonth = :releaseMonth " +
            "WHERE schoolId = :schoolId AND id = :courseId " +
            "AND status = 'PREPARING' AND preparationProgress >= 100.0"
    )
    suspend fun completePreparation(
        schoolId: String,
        courseId: String,
        designScore: Float,
        qualityScore: Float,
        releaseDate: Long,
        releaseYear: Int,
        releaseMonth: Int
    ): Int

    @Transaction
    suspend fun advancePreparation(
        schoolId: String,
        courseId: String,
        progressDelta: Float,
        shouldComplete: Boolean,
        designScore: Float,
        qualityScore: Float,
        releaseDate: Long,
        releaseYear: Int,
        releaseMonth: Int
    ): Boolean {
        if (
            incrementPreparationProgress(
                schoolId,
                courseId,
                progressDelta
            ) != 1
        ) {
            return false
        }
        if (!shouldComplete) {
            return false
        }
        return completePreparation(
            schoolId,
            courseId,
            designScore,
            qualityScore,
            releaseDate,
            releaseYear,
            releaseMonth
        ) == 1
    }

    @Query(
        "UPDATE courses SET status = 'RELEASED', " +
            "releaseDate = :releaseDate, releaseYear = :releaseYear, " +
            "releaseMonth = :releaseMonth " +
            "WHERE schoolId = :schoolId AND id = :courseId " +
            "AND status = :expectedStatus"
    )
    suspend fun releaseFromStatus(
        schoolId: String,
        courseId: String,
        expectedStatus: String,
        releaseDate: Long,
        releaseYear: Int,
        releaseMonth: Int
    ): Int

    @Query(
        "UPDATE courses SET status = 'CLOSED' " +
            "WHERE schoolId = :schoolId AND id = :courseId " +
            "AND status != 'CLOSED'"
    )
    suspend fun closeCourse(schoolId: String, courseId: String): Int

    @Query("DELETE FROM courses WHERE schoolId = :schoolId AND id = :courseId")
    suspend fun deleteCourse(schoolId: String, courseId: String)

    @Query("DELETE FROM courses WHERE schoolId = :schoolId")
    suspend fun deleteCoursesBySchool(schoolId: String)

    @Query("UPDATE courses SET problemCount = problemCount - 1 WHERE schoolId = :schoolId AND id = :courseId AND problemCount > 0")
    suspend fun fixBugs(schoolId: String, courseId: String)

    @Query("UPDATE courses SET marketingSpend = :amount WHERE schoolId = :schoolId AND id = :courseId")
    suspend fun updateMarketingSpend(schoolId: String, courseId: String, amount: Double)
}
