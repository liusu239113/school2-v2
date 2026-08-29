package com.arktools.xiaozhang.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.arktools.xiaozhang.data.local.entity.StudentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {

    // ======= 基础查询 =======

    @Query("SELECT * FROM students WHERE schoolId = :schoolId AND status IN ('ENROLLED', 'STUDYING')")
    suspend fun getActiveStudents(schoolId: String): List<StudentEntity>

    @Query("SELECT * FROM students WHERE schoolId = :schoolId AND status IN ('ENROLLED', 'STUDYING')")
    suspend fun getCurrentStudents(schoolId: String): List<StudentEntity>

    @Query("SELECT * FROM students WHERE schoolId = :schoolId")
    suspend fun getAllStudents(schoolId: String): List<StudentEntity>

    @Query("SELECT * FROM students WHERE schoolId = :schoolId AND courseId = :courseId AND status IN ('ENROLLED', 'STUDYING')")
    suspend fun getStudentsByCourse(schoolId: String, courseId: String): List<StudentEntity>

    @Query("SELECT * FROM students WHERE schoolId = :schoolId AND status = 'GRADUATED' ORDER BY graduateYear DESC, graduateMonth DESC LIMIT :limit")
    suspend fun getRecentGraduates(schoolId: String, limit: Int = 50): List<StudentEntity>

    @Query("SELECT * FROM students WHERE schoolId = :schoolId AND status = 'DROPPED' ORDER BY graduateYear DESC, graduateMonth DESC LIMIT :limit")
    suspend fun getRecentDropouts(schoolId: String, limit: Int = 20): List<StudentEntity>

    @Query("SELECT * FROM students WHERE schoolId = :schoolId AND status = 'GRADUATED' ORDER BY graduateYear DESC, graduateMonth DESC, id")
    fun observeGraduatedStudents(schoolId: String): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE schoolId = :schoolId AND status = 'DROPPED' ORDER BY graduateYear DESC, graduateMonth DESC, id")
    fun observeDroppedStudents(schoolId: String): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE schoolId = :schoolId AND status = 'GRADUATED'")
    suspend fun getGraduatedStudents(schoolId: String): List<StudentEntity>

    @Query(
        "SELECT * FROM students WHERE schoolId = :schoolId " +
            "AND status = 'GRADUATED' AND graduationProjectionState = 0 " +
            "ORDER BY graduateYear, graduateMonth, id"
    )
    suspend fun getPendingGraduationProjections(
        schoolId: String
    ): List<StudentEntity>

    // ======= 班级相关查询 =======

    @Query("SELECT * FROM students WHERE schoolId = :schoolId AND classId = :classId AND status IN ('ENROLLED', 'STUDYING')")
    suspend fun getStudentsByClass(schoolId: String, classId: String): List<StudentEntity>

    @Query("SELECT * FROM students WHERE schoolId = :schoolId AND gradeLevel = :gradeLevel AND status IN ('ENROLLED', 'STUDYING')")
    suspend fun getStudentsByGrade(schoolId: String, gradeLevel: String): List<StudentEntity>

    @Query("SELECT * FROM students WHERE schoolId = :schoolId AND classId IS NULL AND status IN ('ENROLLED', 'STUDYING')")
    suspend fun getUnassignedStudents(schoolId: String): List<StudentEntity>

    @Query("SELECT COUNT(*) FROM students WHERE schoolId = :schoolId AND classId = :classId AND status IN ('ENROLLED', 'STUDYING')")
    suspend fun getClassStudentCount(schoolId: String, classId: String): Int

    @Query("SELECT COUNT(*) FROM students WHERE schoolId = :schoolId AND gradeLevel = :gradeLevel AND status IN ('ENROLLED', 'STUDYING')")
    suspend fun getGradeStudentCount(schoolId: String, gradeLevel: String): Int

    @Query(
        "SELECT COUNT(*) FROM students WHERE schoolId = :schoolId " +
            "AND enrollYear = :enrollmentYear AND enrollMonth = :enrollmentMonth"
    )
    suspend fun countEnrollmentBatch(
        schoolId: String,
        enrollmentYear: Int,
        enrollmentMonth: Int
    ): Int

    // ======= 统计查询 =======

    @Query("SELECT COUNT(*) FROM students WHERE schoolId = :schoolId AND status IN ('ENROLLED', 'STUDYING')")
    suspend fun getActiveStudentCount(schoolId: String): Int

    @Query("SELECT COUNT(*) FROM students WHERE schoolId = :schoolId AND status = 'GRADUATED'")
    suspend fun getGraduateCount(schoolId: String): Int

    @Query("SELECT COUNT(*) FROM students WHERE schoolId = :schoolId AND courseId = :courseId AND status IN ('ENROLLED', 'STUDYING')")
    suspend fun getCourseStudentCount(schoolId: String, courseId: String): Int

    @Query("SELECT AVG(satisfaction) FROM students WHERE schoolId = :schoolId AND status IN ('ENROLLED', 'STUDYING')")
    suspend fun getAverageSatisfaction(schoolId: String): Float?

    @Query("SELECT AVG(reviewRating) FROM students WHERE schoolId = :schoolId AND status = 'GRADUATED' AND reviewRating IS NOT NULL")
    suspend fun getAverageGraduateRating(schoolId: String): Float?

    // 班级平均五维
    @Query("SELECT AVG(intelligence) FROM students WHERE schoolId = :schoolId AND classId = :classId AND status IN ('ENROLLED', 'STUDYING')")
    suspend fun getClassAvgIntelligence(schoolId: String, classId: String): Float?

    @Query("SELECT AVG(physical) FROM students WHERE schoolId = :schoolId AND classId = :classId AND status IN ('ENROLLED', 'STUDYING')")
    suspend fun getClassAvgPhysical(schoolId: String, classId: String): Float?

    @Query("SELECT AVG(satisfaction) FROM students WHERE schoolId = :schoolId AND classId = :classId AND status IN ('ENROLLED', 'STUDYING')")
    suspend fun getClassAvgSatisfaction(schoolId: String, classId: String): Float?

    // 健康统计
    @Query("SELECT COUNT(*) FROM students WHERE schoolId = :schoolId AND healthStatus = 'SICK' AND status IN ('ENROLLED', 'STUDYING')")
    suspend fun getSickStudentCount(schoolId: String): Int

    @Query("SELECT COUNT(*) FROM students WHERE schoolId = :schoolId AND healthStatus = 'FATIGUED' AND status IN ('ENROLLED', 'STUDYING')")
    suspend fun getFatiguedStudentCount(schoolId: String): Int

    // ======= 响应式 =======

    @Query("SELECT * FROM students WHERE schoolId = :schoolId AND status IN ('ENROLLED', 'STUDYING')")
    fun observeCurrentStudents(schoolId: String): Flow<List<StudentEntity>>

    @Query("SELECT COUNT(*) FROM students WHERE schoolId = :schoolId AND status IN ('ENROLLED', 'STUDYING')")
    fun observeActiveStudentCount(schoolId: String): Flow<Int>

    // ======= 写入 =======

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStudents(students: List<StudentEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStudent(student: StudentEntity)

    @Query(
        "UPDATE students SET status = :status, " +
            "intelligence = :intelligence, physical = :physical, " +
            "social = :social, creativity = :creativity, morality = :morality, " +
            "healthStatus = :healthStatus, mealQuality = :mealQuality, " +
            "dormSatisfaction = :dormSatisfaction, exerciseLevel = :exerciseLevel, " +
            "consecutiveSickDays = :consecutiveSickDays, " +
            "semesterMastery = :semesterMastery, satisfaction = :satisfaction, " +
            "graduateYear = CASE WHEN :status = 'DROPPED' " +
            "THEN :graduateYear ELSE graduateYear END, " +
            "graduateMonth = CASE WHEN :status = 'DROPPED' " +
            "THEN :graduateMonth ELSE graduateMonth END, " +
            "reviewRating = :reviewRating, reviewComment = :reviewComment, " +
            "reviewReputationImpact = :reviewReputationImpact " +
            "WHERE schoolId = :schoolId AND id = :studentId " +
            "AND status IN ('ENROLLED', 'STUDYING')"
    )
    suspend fun applyDailyProgress(
        schoolId: String,
        studentId: String,
        status: String,
        intelligence: Float,
        physical: Float,
        social: Float,
        creativity: Float,
        morality: Float,
        healthStatus: String,
        mealQuality: Float,
        dormSatisfaction: Float,
        exerciseLevel: Float,
        consecutiveSickDays: Int,
        semesterMastery: Float,
        satisfaction: Float,
        graduateYear: Int?,
        graduateMonth: Int?,
        reviewRating: Int?,
        reviewComment: String?,
        reviewReputationImpact: Long?
    ): Int

    @Transaction
    suspend fun applyDailyProgresses(
        schoolId: String,
        students: List<StudentEntity>
    ): Set<String> {
        val updatedIds = mutableSetOf<String>()
        for (student in students) {
            if (
                student.schoolId == schoolId &&
                applyDailyProgress(
                    schoolId = schoolId,
                    studentId = student.id,
                    status = student.status,
                    intelligence = student.intelligence,
                    physical = student.physical,
                    social = student.social,
                    creativity = student.creativity,
                    morality = student.morality,
                    healthStatus = student.healthStatus,
                    mealQuality = student.mealQuality,
                    dormSatisfaction = student.dormSatisfaction,
                    exerciseLevel = student.exerciseLevel,
                    consecutiveSickDays = student.consecutiveSickDays,
                    semesterMastery = student.semesterMastery,
                    satisfaction = student.satisfaction,
                    graduateYear = student.graduateYear,
                    graduateMonth = student.graduateMonth,
                    reviewRating = student.reviewRating,
                    reviewComment = student.reviewComment,
                    reviewReputationImpact = student.reviewReputationImpact
                ) == 1
            ) {
                updatedIds.add(student.id)
            }
        }
        return updatedIds
    }

    @Query(
        "UPDATE students SET academicScore = :academicScore " +
            "WHERE schoolId = :schoolId AND id = :studentId " +
            "AND status IN ('ENROLLED', 'STUDYING')"
    )
    suspend fun updateAcademicScore(
        schoolId: String,
        studentId: String,
        academicScore: Float
    ): Int

    @Transaction
    suspend fun updateAcademicScores(
        schoolId: String,
        scoresByStudentId: Map<String, Float>
    ): Int {
        var updated = 0
        for ((studentId, score) in scoresByStudentId) {
            updated += updateAcademicScore(schoolId, studentId, score)
        }
        return updated
    }

    @Query(
        "UPDATE students SET satisfaction = " +
            "MIN(100.0, MAX(0.0, satisfaction + :delta)) " +
            "WHERE schoolId = :schoolId AND id = :studentId " +
            "AND status IN ('ENROLLED', 'STUDYING')"
    )
    suspend fun adjustStudentSatisfaction(
        schoolId: String,
        studentId: String,
        delta: Float
    ): Int

    @Query(
        "UPDATE students SET satisfaction = " +
            "MIN(100.0, MAX(0.0, satisfaction + :delta)) " +
            "WHERE schoolId = :schoolId " +
            "AND status IN ('ENROLLED', 'STUDYING')"
    )
    suspend fun adjustActiveStudentSatisfaction(
        schoolId: String,
        delta: Float
    ): Int

    @Query(
        "UPDATE students SET status = 'DROPPED', " +
            "graduateYear = :dropYear, graduateMonth = :dropMonth " +
            "WHERE schoolId = :schoolId AND id = :studentId " +
            "AND status IN ('ENROLLED', 'STUDYING')"
    )
    suspend fun markDroppedIfActive(
        schoolId: String,
        studentId: String,
        dropYear: Int,
        dropMonth: Int
    ): Int

    @Query(
        "UPDATE students SET gaoKaoScore = :gaoKaoScore, " +
            "universityTier = :universityTier, " +
            "admittedUniversity = :admittedUniversity, status = 'GRADUATED', " +
            "graduateYear = :graduateYear, graduateMonth = :graduateMonth, " +
            "reviewRating = :reviewRating, reviewComment = :reviewComment, " +
            "reviewReputationImpact = :reviewReputationImpact, " +
            "graduationProjectionState = 0 " +
            "WHERE schoolId = :schoolId AND id = :studentId " +
            "AND status IN ('ENROLLED', 'STUDYING')"
    )
    suspend fun graduateIfActive(
        schoolId: String,
        studentId: String,
        gaoKaoScore: Float,
        universityTier: String?,
        admittedUniversity: String?,
        graduateYear: Int,
        graduateMonth: Int,
        reviewRating: Int?,
        reviewComment: String?,
        reviewReputationImpact: Long?
    ): Int

    @Transaction
    suspend fun graduateStudentsIfActive(
        schoolId: String,
        students: List<StudentEntity>
    ): Int {
        var updated = 0
        students.forEach { student ->
            val graduateYear = student.graduateYear
                ?: error("Missing graduate year")
            val graduateMonth = student.graduateMonth
                ?: error("Missing graduate month")
            updated += graduateIfActive(
                schoolId = schoolId,
                studentId = student.id,
                gaoKaoScore = student.gaoKaoScore,
                universityTier = student.universityTier,
                admittedUniversity = student.admittedUniversity,
                graduateYear = graduateYear,
                graduateMonth = graduateMonth,
                reviewRating = student.reviewRating,
                reviewComment = student.reviewComment,
                reviewReputationImpact =
                    student.reviewReputationImpact
            )
        }
        return updated
    }

    @Query(
        "UPDATE students SET graduationProjectionState = 1 " +
            "WHERE schoolId = :schoolId AND id IN (:studentIds) " +
            "AND status = 'GRADUATED' AND graduationProjectionState = 0"
    )
    suspend fun markGraduationProjectionsCompleted(
        schoolId: String,
        studentIds: List<String>
    ): Int

    @Query(
        "UPDATE students SET semesterMastery = 0.0 " +
            "WHERE schoolId = :schoolId " +
            "AND status IN ('ENROLLED', 'STUDYING')"
    )
    suspend fun resetSemesterMastery(schoolId: String): Int

    // 仅允许给仍在读且年级符合预期的学生编入教学班
    @Query(
        "UPDATE students SET classId = :classId " +
            "WHERE schoolId = :schoolId AND id = :studentId " +
            "AND gradeLevel = :expectedGradeLevel " +
            "AND status IN ('ENROLLED', 'STUDYING')"
    )
    suspend fun assignStudentToClass(
        schoolId: String,
        studentId: String,
        classId: String,
        expectedGradeLevel: String
    ): Int

    @Query(
        "UPDATE students SET courseId = :courseId " +
            "WHERE schoolId = :schoolId AND id = :studentId " +
            "AND status IN ('ENROLLED', 'STUDYING')"
    )
    suspend fun updateStudentCourseId(
        schoolId: String,
        studentId: String,
        courseId: String
    ): Int

    @Query(
        "UPDATE students SET " +
            "gradeLevel = CASE WHEN :newGradeLevel IS NULL " +
            "THEN gradeLevel ELSE :newGradeLevel END, " +
            "classId = CASE WHEN :targetClassId IS NULL " +
            "THEN classId ELSE :targetClassId END, " +
            "lastPromotionYear = :processingYear " +
            "WHERE schoolId = :schoolId AND id IN (:studentIds) " +
            "AND gradeLevel = :expectedGrade " +
            "AND lastPromotionYear < :processingYear " +
            "AND status IN ('ENROLLED', 'STUDYING')"
    )
    suspend fun applyYearEndTransition(
        schoolId: String,
        studentIds: List<String>,
        expectedGrade: String,
        newGradeLevel: String?,
        targetClassId: String?,
        processingYear: Int
    ): Int

    // ======= 删除/清理 =======

    @Query("DELETE FROM students WHERE schoolId = :schoolId")
    suspend fun deleteBySchool(schoolId: String)

    @Query(
        "DELETE FROM students WHERE schoolId = :schoolId " +
            "AND ((status = 'GRADUATED' AND graduationProjectionState = 1) " +
            "OR status = 'DROPPED') AND graduateYear < :beforeYear"
    )
    suspend fun cleanupOldRecords(schoolId: String, beforeYear: Int)
}
