package com.arktools.xiao.data.repository

import com.arktools.xiao.data.local.dao.CourseDao
import com.arktools.xiao.data.local.entity.CourseEntity
import com.arktools.xiao.data.pref.SettingsDataStore
import com.arktools.xiao.domain.model.CourseProject
import com.arktools.xiao.domain.model.CourseStatus
import com.arktools.xiao.domain.model.DistrictType
import com.arktools.xiao.domain.model.Subject
import com.arktools.xiao.domain.model.CourseTheme
import com.arktools.xiao.domain.model.CourseType
import com.arktools.xiao.domain.model.CourseScale
import com.arktools.xiao.domain.repository.CourseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class CourseRepositoryImpl @Inject constructor(
    private val courseDao: CourseDao,
    private val settingsDataStore: SettingsDataStore
) : CourseRepository {

    override fun getCoursesFlow(): Flow<List<CourseProject>> {
        return settingsDataStore.schoolId.map { it ?: "" }
            .flatMapLatest { schoolId ->
                courseDao.getCoursesBySchoolFlow(schoolId).map { list ->
                    list.map { it.toDomain() }
                }
            }
    }

    override suspend fun getCourses(): List<CourseProject> {
        val schoolId = settingsDataStore.schoolId.first() ?: return emptyList()
        return courseDao.getCoursesBySchool(schoolId).map { it.toDomain() }
    }

    override suspend fun getCourseById(courseId: String): CourseProject? {
        val schoolId = settingsDataStore.schoolId.first() ?: return null
        return courseDao.getCourseById(schoolId, courseId)?.toDomain()
    }

    override suspend fun getCoursesByStatus(status: CourseStatus): List<CourseProject> {
        val schoolId = settingsDataStore.schoolId.first() ?: return emptyList()
        return courseDao.getCoursesByStatus(schoolId, status.name).map { it.toDomain() }
    }

    override suspend fun getActiveCourses(): List<CourseProject> {
        val schoolId = settingsDataStore.schoolId.first() ?: return emptyList()
        return courseDao.getActiveCourses(schoolId).map { it.toDomain() }
    }

    override suspend fun getReleasedCourses(): List<CourseProject> {
        val schoolId = settingsDataStore.schoolId.first() ?: return emptyList()
        return courseDao.getReleasedCourses(schoolId).map { it.toDomain() }
    }

    override suspend fun createCourse(course: CourseProject) {
        val schoolId = settingsDataStore.schoolId.first() ?: return
        courseDao.insertCourse(course.toEntity(schoolId))
    }

    override suspend fun advancePreparation(
        courseId: String,
        progressDelta: Float,
        shouldComplete: Boolean,
        designScore: Float,
        qualityScore: Float,
        releaseDate: Long,
        releaseYear: Int,
        releaseMonth: Int
    ): Boolean {
        val schoolId = settingsDataStore.schoolId.first() ?: return false
        return courseDao.advancePreparation(
            schoolId = schoolId,
            courseId = courseId,
            progressDelta = progressDelta,
            shouldComplete = shouldComplete,
            designScore = designScore,
            qualityScore = qualityScore,
            releaseDate = releaseDate,
            releaseYear = releaseYear,
            releaseMonth = releaseMonth
        )
    }

    override suspend fun deleteCourse(courseId: String) {
        val schoolId = settingsDataStore.schoolId.first() ?: return
        courseDao.deleteCourse(schoolId, courseId)
    }

    override suspend fun releaseCourse(
        courseId: String,
        expectedStatus: CourseStatus,
        releaseDate: Long,
        releaseYear: Int,
        releaseMonth: Int
    ): Boolean {
        val schoolId = settingsDataStore.schoolId.first() ?: return false
        return courseDao.releaseFromStatus(
            schoolId = schoolId,
            courseId = courseId,
            expectedStatus = expectedStatus.name,
            releaseDate = releaseDate,
            releaseYear = releaseYear,
            releaseMonth = releaseMonth
        ) == 1
    }

    override suspend fun closeCourse(courseId: String): Boolean {
        val schoolId = settingsDataStore.schoolId.first() ?: return false
        return courseDao.closeCourse(schoolId, courseId) == 1
    }



    override suspend fun fixBugs(courseId: String) {
        val schoolId = settingsDataStore.schoolId.first() ?: return
        withContext(Dispatchers.IO) {
            courseDao.fixBugs(schoolId, courseId)
        }
    }

    override suspend fun updateMarketingSpend(courseId: String, amount: Double) {
        val schoolId = settingsDataStore.schoolId.first() ?: return
        withContext(Dispatchers.IO) {
            courseDao.updateMarketingSpend(schoolId, courseId, amount)
        }
    }

    override suspend fun deleteAll() {
        val schoolId = settingsDataStore.schoolId.first() ?: return
        courseDao.deleteCoursesBySchool(schoolId)
    }

    private fun CourseEntity.toDomain(): CourseProject {
        return CourseProject(
            id = id,
            name = name,
            subject = try { Subject.valueOf(subject) } catch (_: Exception) { Subject.entries.first() },
            theme = try { CourseTheme.valueOf(theme) } catch (_: Exception) { CourseTheme.entries.first() },
            courseType = try { CourseType.valueOf(courseType) } catch (_: Exception) { CourseType.entries.first() },
            targetDistrict = try { DistrictType.valueOf(targetDistrict) } catch (_: Exception) { DistrictType.entries.first() },
            scale = try { CourseScale.valueOf(scale) } catch (_: Exception) { CourseScale.entries.first() },
            preparationProgress = preparationProgress,
            problemCount = problemCount,
            qualityScore = qualityScore,
            designScore = designScore,
            status = try { CourseStatus.valueOf(status) } catch (_: Exception) { CourseStatus.entries.first() },
            teamIds = try { Json.decodeFromString(teamIdsJson) } catch (_: Exception) { emptyList() },
            methodIds = try { Json.decodeFromString(methodIdsJson) } catch (_: Exception) { emptyList() },
            ipId = ipId,
            enrollment = enrollment,
            revenue = revenue,
            monthlyEnrollment = monthlyEnrollment,
            releaseDate = releaseDate,
            releaseYear = releaseYear,
            releaseMonth = releaseMonth,
            heat = heat,
            marketingSpend = marketingSpend
        )
    }

    private fun CourseProject.toEntity(schoolId: String): CourseEntity {
        return CourseEntity(
            id = id,
            name = name,
            subject = subject.name,
            theme = theme.name,
            courseType = courseType.name,
            targetDistrict = targetDistrict.name,
            scale = scale.name,
            preparationProgress = preparationProgress,
            problemCount = problemCount,
            qualityScore = qualityScore,
            designScore = designScore,
            status = status.name,
            teamIdsJson = Json.encodeToString(teamIds),
            methodIdsJson = Json.encodeToString(methodIds),
            ipId = ipId,
            enrollment = enrollment,
            revenue = revenue,
            monthlyEnrollment = monthlyEnrollment,
            releaseDate = releaseDate,
            releaseYear = releaseYear,
            releaseMonth = releaseMonth,
            heat = heat,
            marketingSpend = marketingSpend,
            schoolId = schoolId
        )
    }
}
