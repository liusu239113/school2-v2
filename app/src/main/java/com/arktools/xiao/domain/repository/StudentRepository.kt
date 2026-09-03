package com.arktools.xiao.domain.repository

import com.arktools.xiao.domain.model.GradeLevel
import com.arktools.xiao.domain.model.Student
import kotlinx.coroutines.flow.Flow

enum class EnrollmentCommitResult {
    COMMITTED,
    ALREADY_COMMITTED,
    REJECTED
}

data class StudentYearEndTransition(
    val studentIds: List<String>,
    val expectedGrade: GradeLevel,
    val newGrade: GradeLevel? = null,
    val targetClassId: String? = null
)

data class GraduationProjectionCommit(
    val studentIds: List<String>,
    val cashBonus: Double,
    val reputationDelta: Long,
    val expectedLastSaveTime: Long,
    val alumniJson: String,
    val employmentJson: String,
    val pressureJson: String,
    val timetableJson: String,
    val headTeacherMapJson: String,
    val classTierMapJson: String
)

interface StudentRepository {

    // ======= 基础查询 =======
    suspend fun getActiveStudents(): List<Student>
    suspend fun getCurrentStudents(): List<Student>
    suspend fun getStudentsByCourse(courseId: String): List<Student>
    suspend fun getRecentGraduates(limit: Int = 50): List<Student>
    suspend fun getRecentDropouts(limit: Int = 20): List<Student>
    suspend fun getGraduatedStudents(): List<Student>
    suspend fun getActiveStudentCount(): Int
    suspend fun getGraduateCount(): Int
    suspend fun getCourseStudentCount(courseId: String): Int
    suspend fun getAverageSatisfaction(): Float
    suspend fun getAverageGraduateRating(): Float
    suspend fun getEnrollmentBatchCount(enrollmentYear: Int, enrollmentMonth: Int): Int

    // ======= 班级/年级查询 =======
    suspend fun getStudentsByClass(classId: String): List<Student>
    suspend fun getStudentsByGrade(gradeLevel: GradeLevel): List<Student>
    suspend fun getUnassignedStudents(): List<Student>
    suspend fun getClassStudentCount(classId: String): Int
    suspend fun getGradeStudentCount(gradeLevel: GradeLevel): Int

    // ======= 健康统计 =======
    suspend fun getSickStudentCount(): Int
    suspend fun getFatiguedStudentCount(): Int

    // ======= 班级平均五维 =======
    suspend fun getClassAvgIntelligence(classId: String): Float
    suspend fun getClassAvgPhysical(classId: String): Float
    suspend fun getClassAvgSatisfaction(classId: String): Float

    // ======= 观察 =======
    fun observeActiveStudentCount(): Flow<Int>
    fun observeGraduatedStudents(): Flow<List<Student>>
    fun observeDroppedStudents(): Flow<List<Student>>

    // ======= 变更 =======
    suspend fun enrollAssignedStudents(
        students: List<Student>,
        classTierMapJson: String,
        enrollmentYear: Int,
        enrollmentMonth: Int
    ): EnrollmentCommitResult
    suspend fun applyDailyProgress(students: List<Student>): Set<String>
    suspend fun updateAcademicScores(students: List<Student>): Int
    suspend fun adjustStudentSatisfaction(studentId: String, delta: Float): Boolean
    suspend fun adjustActiveStudentSatisfaction(delta: Float): Int
    suspend fun commitGraduationCandidates(
        students: List<Student>
    ): Boolean
    suspend fun getPendingGraduationProjections(): List<Student>
    suspend fun commitGraduationProjection(
        commit: GraduationProjectionCommit
    ): Boolean
    suspend fun resetSemesterMastery(): Int

    // ======= 班级分配 =======
    suspend fun assignStudentToClass(
        studentId: String,
        classId: String,
        gradeLevel: GradeLevel
    ): Boolean
    suspend fun commitYearEndTransitions(
        transitions: List<StudentYearEndTransition>,
        processingYear: Int,
        classTierMapJson: String
    ): Boolean
    suspend fun updateStudentMajors(updates: Map<String, String>): Int

    // ======= 清理 =======
    suspend fun deleteAll()
    suspend fun cleanupOldRecords(beforeYear: Int)
}
