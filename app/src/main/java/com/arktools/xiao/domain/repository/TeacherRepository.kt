package com.arktools.xiao.domain.repository

import com.arktools.xiao.domain.model.Teacher
import com.arktools.xiao.domain.model.TeacherLevel
import kotlinx.coroutines.flow.Flow

enum class PaidTrainingStatus {
    SUCCESS,
    NO_EFFECT,
    INSUFFICIENT_FUNDS,
    TEACHER_UNAVAILABLE,
    SCHOOL_UNAVAILABLE
}

data class PaidTrainingResult(
    val status: PaidTrainingStatus,
    val cost: Double = 0.0,
    val successRate: Double = 0.0
)

data class TeacherDevelopmentProfileUpdate(
    val teacherId: String,
    val level: TeacherLevel,
    val profileSkillLevel: Float,
    val primarilyTeaching: Boolean
)

interface TeacherRepository {
    fun getTeachersFlow(): Flow<List<Teacher>>
    suspend fun getTeachers(): List<Teacher>
    suspend fun getTeacherById(teacherId: String): Teacher?
    suspend fun hireTeacher(teacher: Teacher)
    suspend fun fireTeacher(teacherId: String)
    suspend fun saveDailyState(previous: Teacher, updated: Teacher): Boolean
    suspend fun trainTeacher(teacherId: String): Boolean
    suspend fun performPaidTraining(teacherId: String): PaidTrainingResult
    suspend fun addSkillGrowth(
        teacherId: String,
        teachingGain: Int,
        researchGain: Int,
        managementGain: Int,
        psychologyGain: Int
    ): Boolean
    suspend fun adjustSalary(teacherId: String, newSalary: Double): Boolean
    suspend fun adjustAllLoyalty(delta: Int): Int
    suspend fun adjustLoyalty(
        teacherId: String,
        delta: Int,
        minimum: Int = 0
    ): Boolean
    suspend fun retainWithRaise(
        teacherId: String,
        raiseFraction: Double
    ): Boolean
    suspend fun approveRaise(
        teacherId: String,
        raisePercent: Double
    ): Boolean
    suspend fun renewContract(
        teacherId: String,
        newSalary: Double
    ): Boolean
    suspend fun updateLevel(
        teacherId: String,
        level: TeacherLevel
    ): Boolean
    suspend fun syncDevelopmentProfile(
        teacherId: String,
        level: TeacherLevel,
        profileSkillLevel: Float,
        primarilyTeaching: Boolean
    ): Boolean
    suspend fun commitDevelopmentState(
        expense: Double,
        teacherDevJson: String,
        departedTeacherIds: List<String>,
        profileUpdates: List<TeacherDevelopmentProfileUpdate>,
        pressureJson: String? = null,
        timetableJson: String? = null
    ): Boolean
    fun generateCandidates(level: TeacherLevel, count: Int): List<Teacher>
    suspend fun deleteAll()
}
