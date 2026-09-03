package com.arktools.xiao.domain.repository

import com.arktools.xiao.domain.model.CourseProject
import com.arktools.xiao.domain.model.CourseStatus
import kotlinx.coroutines.flow.Flow

interface CourseRepository {
    fun getCoursesFlow(): Flow<List<CourseProject>>
    suspend fun getCourses(): List<CourseProject>
    suspend fun getCourseById(courseId: String): CourseProject?
    suspend fun getCoursesByStatus(status: CourseStatus): List<CourseProject>
    suspend fun getActiveCourses(): List<CourseProject>
    suspend fun getReleasedCourses(): List<CourseProject>
    suspend fun createCourse(course: CourseProject)
    suspend fun deleteCourse(courseId: String)
    suspend fun advancePreparation(
        courseId: String,
        progressDelta: Float,
        shouldComplete: Boolean,
        designScore: Float,
        qualityScore: Float,
        releaseDate: Long,
        releaseYear: Int,
        releaseMonth: Int
    ): Boolean
    suspend fun releaseCourse(
        courseId: String,
        expectedStatus: CourseStatus,
        releaseDate: Long,
        releaseYear: Int,
        releaseMonth: Int
    ): Boolean
    suspend fun closeCourse(courseId: String): Boolean

    suspend fun fixBugs(courseId: String)
    suspend fun updateMarketingSpend(courseId: String, amount: Double)
    suspend fun deleteAll()
}
