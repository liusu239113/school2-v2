package com.arktools.xiao.data.repository

import androidx.room.withTransaction
import com.arktools.xiao.data.local.AppDatabase
import com.arktools.xiao.data.local.dao.StudentDao
import com.arktools.xiao.data.local.entity.StudentEntity
import com.arktools.xiao.data.pref.SettingsDataStore
import com.arktools.xiao.domain.model.BackgroundTier
import com.arktools.xiao.domain.model.GradeLevel
import com.arktools.xiao.domain.model.HealthStatus
import com.arktools.xiao.domain.model.Student
import com.arktools.xiao.domain.model.StudentAttributes
import com.arktools.xiao.domain.model.StudentReview
import com.arktools.xiao.domain.model.StudentStatus
import com.arktools.xiao.domain.model.StudentTrait
import com.arktools.xiao.domain.repository.EnrollmentCommitResult
import com.arktools.xiao.domain.repository.GraduationProjectionCommit
import com.arktools.xiao.domain.repository.StudentRepository
import com.arktools.xiao.domain.repository.StudentYearEndTransition
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class StudentRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val studentDao: StudentDao,
    private val settingsDataStore: SettingsDataStore
) : StudentRepository {

    // ======= 基础查询 =======

    override suspend fun getActiveStudents(): List<Student> {
        val schoolId = settingsDataStore.getSchoolId()
        return studentDao.getActiveStudents(schoolId).map { it.toDomain() }
    }

    override suspend fun getCurrentStudents(): List<Student> {
        val schoolId = settingsDataStore.getSchoolId()
        return studentDao.getCurrentStudents(schoolId).map { it.toDomain() }
    }

    override suspend fun getStudentsByCourse(courseId: String): List<Student> {
        val schoolId = settingsDataStore.getSchoolId()
        return studentDao.getStudentsByCourse(schoolId, courseId).map { it.toDomain() }
    }

    override suspend fun getRecentGraduates(limit: Int): List<Student> {
        val schoolId = settingsDataStore.getSchoolId()
        return studentDao.getRecentGraduates(schoolId, limit).map { it.toDomain() }
    }

    override suspend fun getRecentDropouts(limit: Int): List<Student> {
        val schoolId = settingsDataStore.getSchoolId()
        return studentDao.getRecentDropouts(schoolId, limit).map { it.toDomain() }
    }

    override suspend fun getGraduatedStudents(): List<Student> {
        val schoolId = settingsDataStore.getSchoolId()
        return studentDao.getGraduatedStudents(schoolId).map { it.toDomain() }
    }

    override suspend fun getActiveStudentCount(): Int {
        val schoolId = settingsDataStore.getSchoolId()
        return studentDao.getActiveStudentCount(schoolId)
    }

    override suspend fun getGraduateCount(): Int {
        val schoolId = settingsDataStore.getSchoolId()
        return studentDao.getGraduateCount(schoolId)
    }

    override suspend fun getCourseStudentCount(courseId: String): Int {
        val schoolId = settingsDataStore.getSchoolId()
        return studentDao.getCourseStudentCount(schoolId, courseId)
    }

    override suspend fun getAverageSatisfaction(): Float {
        val schoolId = settingsDataStore.getSchoolId()
        return studentDao.getAverageSatisfaction(schoolId) ?: 70f
    }

    override suspend fun getAverageGraduateRating(): Float {
        val schoolId = settingsDataStore.getSchoolId()
        return studentDao.getAverageGraduateRating(schoolId) ?: 0f
    }

    override suspend fun getEnrollmentBatchCount(
        enrollmentYear: Int,
        enrollmentMonth: Int
    ): Int {
        val schoolId = settingsDataStore.getSchoolId()
        return studentDao.countEnrollmentBatch(schoolId, enrollmentYear, enrollmentMonth)
    }

    // ======= 班级/年级查询 =======

    override suspend fun getStudentsByClass(classId: String): List<Student> {
        val schoolId = settingsDataStore.getSchoolId()
        return studentDao.getStudentsByClass(schoolId, classId).map { it.toDomain() }
    }

    override suspend fun getStudentsByGrade(gradeLevel: GradeLevel): List<Student> {
        val schoolId = settingsDataStore.getSchoolId()
        return studentDao.getStudentsByGrade(schoolId, gradeLevel.name).map { it.toDomain() }
    }

    override suspend fun getUnassignedStudents(): List<Student> {
        val schoolId = settingsDataStore.getSchoolId()
        return studentDao.getUnassignedStudents(schoolId).map { it.toDomain() }
    }

    override suspend fun getClassStudentCount(classId: String): Int {
        val schoolId = settingsDataStore.getSchoolId()
        return studentDao.getClassStudentCount(schoolId, classId)
    }

    override suspend fun getGradeStudentCount(gradeLevel: GradeLevel): Int {
        val schoolId = settingsDataStore.getSchoolId()
        return studentDao.getGradeStudentCount(schoolId, gradeLevel.name)
    }

    // ======= 健康统计 =======

    override suspend fun getSickStudentCount(): Int {
        val schoolId = settingsDataStore.getSchoolId()
        return studentDao.getSickStudentCount(schoolId)
    }

    override suspend fun getFatiguedStudentCount(): Int {
        val schoolId = settingsDataStore.getSchoolId()
        return studentDao.getFatiguedStudentCount(schoolId)
    }

    // ======= 班级平均五维 =======

    override suspend fun getClassAvgIntelligence(classId: String): Float {
        val schoolId = settingsDataStore.getSchoolId()
        return studentDao.getClassAvgIntelligence(schoolId, classId) ?: 50f
    }

    override suspend fun getClassAvgPhysical(classId: String): Float {
        val schoolId = settingsDataStore.getSchoolId()
        return studentDao.getClassAvgPhysical(schoolId, classId) ?: 50f
    }

    override suspend fun getClassAvgSatisfaction(classId: String): Float {
        val schoolId = settingsDataStore.getSchoolId()
        return studentDao.getClassAvgSatisfaction(schoolId, classId) ?: 70f
    }

    // ======= 观察 =======

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeActiveStudentCount(): Flow<Int> = flow {
        // 瞬时数据库异常不能把"有学生"误报成"无学生"，异常时保持旧值并自动重连
        while (true) {
            try {
                settingsDataStore.schoolId.flatMapLatest { id ->
                    if (id.isNullOrEmpty()) flowOf(0)
                    else studentDao.observeActiveStudentCount(id)
                }.collect { emit(it) }
            } catch (e: Exception) {
                android.util.Log.e("StudentRepo", "Flow error suppressed (data preserved), retrying: ${e.message}")
            }
            delay(1000)
        }
    }

    override fun observeGraduatedStudents(): Flow<List<Student>> =
        settingsDataStore.schoolId.flatMapLatest { id ->
            if (id.isNullOrEmpty()) flowOf(emptyList())
            else studentDao.observeGraduatedStudents(id).map { list ->
                list.map { it.toDomain() }
            }
        }

    override fun observeDroppedStudents(): Flow<List<Student>> =
        settingsDataStore.schoolId.flatMapLatest { id ->
            if (id.isNullOrEmpty()) flowOf(emptyList())
            else studentDao.observeDroppedStudents(id).map { list ->
                list.map { it.toDomain() }
            }
        }

    // ======= 变更 =======

    override suspend fun enrollAssignedStudents(
        students: List<Student>,
        classTierMapJson: String,
        enrollmentYear: Int,
        enrollmentMonth: Int
    ): EnrollmentCommitResult {
        val schoolId = settingsDataStore.getSchoolId()
        if (schoolId.isBlank() || students.isEmpty() ||
            classTierMapJson.isBlank() || enrollmentYear <= 0 ||
            enrollmentMonth !in 1..12 ||
            students.map { it.id }.distinct().size != students.size ||
            students.any {
                it.schoolId != schoolId || it.classId.isNullOrBlank() ||
                    it.gradeLevel != GradeLevel.GRADE_1 ||
                    it.status !in setOf(
                        StudentStatus.ENROLLED,
                        StudentStatus.STUDYING
                    ) ||
                    it.enrollYear != enrollmentYear ||
                    it.enrollMonth != enrollmentMonth
            }
        ) {
            return EnrollmentCommitResult.REJECTED
        }

        return database.withTransaction {
            val school = database.schoolDao().getSchoolCore()
            if (school == null || school.id != schoolId ||
                school.currentYear != enrollmentYear ||
                school.currentMonth != enrollmentMonth
            ) {
                return@withTransaction EnrollmentCommitResult.REJECTED
            }
            // 招生按目标人数补齐：同批已有少量学生不代表整批招生已完成。
            // GameEngine 会先扣除已有人数，因此这里允许补插剩余学生。
            studentDao.insertStudents(students.map { it.toEntity() })
            check(
                database.schoolDao().updateClassTierMap(
                    schoolId = schoolId,
                    classTierMapJson = classTierMapJson,
                    now = System.currentTimeMillis()
                ) == 1
            ) { "Enrollment class state commit failed" }
            EnrollmentCommitResult.COMMITTED
        }
    }

    override suspend fun applyDailyProgress(
        students: List<Student>
    ): Set<String> {
        val schoolId = settingsDataStore.getSchoolId()
        if (students.any { it.schoolId != schoolId }) return emptySet()
        return studentDao.applyDailyProgresses(
            schoolId,
            students.map { it.toEntity() }
        )
    }

    override suspend fun updateAcademicScores(
        students: List<Student>
    ): Int {
        val schoolId = settingsDataStore.getSchoolId()
        if (students.any { it.schoolId != schoolId }) return 0
        return studentDao.updateAcademicScores(
            schoolId,
            students.associate { it.id to it.academicScore }
        )
    }

    override suspend fun adjustStudentSatisfaction(
        studentId: String,
        delta: Float
    ): Boolean {
        val schoolId = settingsDataStore.getSchoolId()
        return studentDao.adjustStudentSatisfaction(
            schoolId,
            studentId,
            delta
        ) == 1
    }

    override suspend fun adjustActiveStudentSatisfaction(
        delta: Float
    ): Int {
        val schoolId = settingsDataStore.getSchoolId()
        return studentDao.adjustActiveStudentSatisfaction(
            schoolId,
            delta
        )
    }

    override suspend fun commitGraduationCandidates(
        students: List<Student>
    ): Boolean {
        if (students.isEmpty()) return true
        val schoolId = settingsDataStore.getSchoolId()
        if (schoolId.isBlank() ||
            students.map { it.id }.distinct().size != students.size ||
            students.any {
                it.schoolId != schoolId ||
                    it.status != StudentStatus.GRADUATED ||
                    it.graduateYear == null || it.graduateMonth == null ||
                    !it.gaoKaoScore.isFinite() ||
                    it.gaoKaoScore !in 0f..750f
            }
        ) {
            return false
        }
        return database.withTransaction {
            val school = database.schoolDao().getSchoolCore()
            if (school == null || school.id != schoolId) {
                return@withTransaction false
            }
            check(
                studentDao.graduateStudentsIfActive(
                    schoolId,
                    students.map { it.toEntity() }
                ) == students.size
            ) { "Graduation candidate batch update failed" }
            true
        }
    }

    override suspend fun getPendingGraduationProjections(): List<Student> {
        val schoolId = settingsDataStore.getSchoolId()
        if (schoolId.isBlank()) return emptyList()
        return studentDao.getPendingGraduationProjections(schoolId)
            .map { it.toDomain() }
    }

    override suspend fun commitGraduationProjection(
        commit: GraduationProjectionCommit
    ): Boolean {
        val schoolId = settingsDataStore.getSchoolId()
        if (schoolId.isBlank() ||
            commit.studentIds.any(String::isBlank) ||
            commit.studentIds.distinct().size != commit.studentIds.size ||
            !commit.cashBonus.isFinite() || commit.cashBonus < 0.0 ||
            commit.expectedLastSaveTime <= 0L ||
            commit.alumniJson.isBlank() ||
            commit.employmentJson.isBlank() ||
            commit.pressureJson.isBlank() ||
            commit.timetableJson.isBlank()
        ) {
            return false
        }
        return database.withTransaction {
            val school = database.schoolDao().getSchoolCore()
            if (school == null || school.id != schoolId) {
                return@withTransaction false
            }
            val completed = if (commit.studentIds.isEmpty()) {
                0
            } else {
                commit.studentIds.chunked(900).sumOf { chunk ->
                    studentDao.markGraduationProjectionsCompleted(
                        schoolId,
                        chunk
                    )
                }
            }
            check(completed == commit.studentIds.size) {
                "Graduation projection acknowledgement failed"
            }
            check(
                database.schoolDao().commitGraduationProjection(
                    schoolId = schoolId,
                    cashBonus = commit.cashBonus,
                    reputationDelta = commit.reputationDelta,
                    expectedLastSaveTime = commit.expectedLastSaveTime,
                    alumniJson = commit.alumniJson,
                    employmentJson = commit.employmentJson,
                    pressureJson = commit.pressureJson,
                    timetableJson = commit.timetableJson,
                    headTeacherMapJson = commit.headTeacherMapJson,
                    classTierMapJson = commit.classTierMapJson,
                    now = System.currentTimeMillis()
                ) == 1
            ) { "Graduation projection state commit failed" }
            true
        }
    }

    override suspend fun resetSemesterMastery(): Int {
        val schoolId = settingsDataStore.getSchoolId()
        return studentDao.resetSemesterMastery(schoolId)
    }

    // ======= 班级分配 =======

    override suspend fun assignStudentToClass(
        studentId: String,
        classId: String,
        gradeLevel: GradeLevel
    ): Boolean {
        val schoolId = settingsDataStore.getSchoolId()
        if (schoolId.isBlank() || studentId.isBlank() || classId.isBlank()) {
            return false
        }
        return studentDao.assignStudentToClass(
            schoolId,
            studentId,
            classId,
            gradeLevel.name
        ) == 1
    }

    override suspend fun commitYearEndTransitions(
        transitions: List<StudentYearEndTransition>,
        processingYear: Int,
        classTierMapJson: String
    ): Boolean {
        val schoolId = settingsDataStore.getSchoolId()
        val allStudentIds = transitions.flatMap { it.studentIds }
        if (schoolId.isBlank() || processingYear <= 0 ||
            classTierMapJson.isBlank() ||
            allStudentIds.distinct().size != allStudentIds.size ||
            transitions.any { transition ->
                transition.studentIds.isEmpty() ||
                    transition.studentIds.any(String::isBlank) ||
                    transition.studentIds.distinct().size !=
                    transition.studentIds.size ||
                    (transition.newGrade != null &&
                        transition.expectedGrade.nextGrade !=
                        transition.newGrade) ||
                    (transition.targetClassId != null &&
                        transition.targetClassId.isBlank())
            }
        ) {
            return false
        }

        return database.withTransaction {
            val school = database.schoolDao().getSchoolCore()
            if (school == null || school.id != schoolId ||
                school.currentYear != processingYear ||
                school.currentMonth < 6 ||
                school.lastYearEndProcessingYear >= processingYear
            ) {
                return@withTransaction false
            }

            transitions.forEach { transition ->
                transition.studentIds.chunked(900).forEach { chunk ->
                    check(
                        studentDao.applyYearEndTransition(
                            schoolId = schoolId,
                            studentIds = chunk,
                            expectedGrade = transition.expectedGrade.name,
                            newGradeLevel = transition.newGrade?.name,
                            targetClassId = transition.targetClassId,
                            processingYear = processingYear
                        ) == chunk.size
                    ) { "Student year-end transition failed" }
                }
            }
            check(
                database.schoolDao().completeStudentYearEnd(
                    schoolId = schoolId,
                    processingYear = processingYear,
                    classTierMapJson = classTierMapJson,
                    now = System.currentTimeMillis()
                ) == 1
            ) { "Student year-end class state commit failed" }
            true
        }
    }

    override suspend fun updateStudentMajors(updates: Map<String, String>): Int {
        val schoolId = settingsDataStore.getSchoolId()
        if (schoolId.isBlank() || updates.isEmpty()) return 0
        var changed = 0
        updates.forEach { (studentId, courseId) ->
            if (studentId.isBlank() || courseId.isBlank()) return@forEach
            changed += studentDao.updateStudentCourseId(schoolId, studentId, courseId)
        }
        return changed
    }

    // ======= 清理 =======

    override suspend fun deleteAll() {
        val schoolId = settingsDataStore.getSchoolId()
        studentDao.deleteBySchool(schoolId)
    }

    override suspend fun cleanupOldRecords(beforeYear: Int) {
        val schoolId = settingsDataStore.getSchoolId()
        studentDao.cleanupOldRecords(schoolId, beforeYear)
    }

    // ======= Mapping Functions =======

    private fun StudentEntity.toDomain(): Student {
        return Student(
            id = id,
            name = name,
            courseId = courseId,
            schoolId = schoolId,
            classId = classId,
            gradeLevel = try { GradeLevel.valueOf(gradeLevel) } catch (_: Exception) { GradeLevel.GRADE_1 },
            attributes = StudentAttributes(
                intelligence = intelligence,
                physical = physical,
                social = social,
                creativity = creativity,
                morality = morality
            ),
            backgroundTier = try { BackgroundTier.valueOf(backgroundTier) } catch (_: Exception) { BackgroundTier.NORMAL },
            talent = talent,
            motivation = motivation,
            traits = try {
                traitsJson.removeSurrounding("[", "]")
                    .split(",")
                    .filter { it.isNotBlank() }
                    .map { StudentTrait.valueOf(it.trim().removeSurrounding("\"")) }
            } catch (_: Exception) { emptyList() },
            status = try { StudentStatus.valueOf(status) } catch (_: Exception) { StudentStatus.ENROLLED },
            semesterMastery = semesterMastery,
            satisfaction = satisfaction,
            academicScore = academicScore,
            gaoKaoScore = gaoKaoScore,
            admittedUniversity = admittedUniversity,
            universityTier = universityTier?.let {
                try { com.arktools.xiao.domain.model.UniversityTier.valueOf(it) } catch (_: Exception) { null }
            },
            healthStatus = try { HealthStatus.valueOf(healthStatus) } catch (_: Exception) { HealthStatus.HEALTHY },
            mealQuality = mealQuality,
            dormSatisfaction = dormSatisfaction,
            exerciseLevel = exerciseLevel,
            consecutiveSickDays = consecutiveSickDays,
            enrollYear = enrollYear,
            enrollMonth = enrollMonth,
            lastPromotionYear = lastPromotionYear,
            graduateYear = graduateYear,
            graduateMonth = graduateMonth,
            graduationProjectionState = graduationProjectionState,
            review = if (reviewRating != null) StudentReview(
                rating = reviewRating,
                comment = reviewComment ?: "",
                reputationImpact = reviewReputationImpact ?: 0L
            ) else null
        )
    }

    private fun Student.toEntity(): StudentEntity {
        return StudentEntity(
            id = id,
            name = name,
            courseId = courseId,
            schoolId = schoolId,
            classId = classId,
            gradeLevel = gradeLevel.name,
            intelligence = attributes.intelligence,
            physical = attributes.physical,
            social = attributes.social,
            creativity = attributes.creativity,
            morality = attributes.morality,
            backgroundTier = backgroundTier.name,
            talent = talent,
            motivation = motivation,
            traitsJson = "[${traits.joinToString(",") { "\"${it.name}\"" }}]",
            status = status.name,
            semesterMastery = semesterMastery,
            satisfaction = satisfaction,
            academicScore = academicScore,
            gaoKaoScore = gaoKaoScore,
            admittedUniversity = admittedUniversity,
            universityTier = universityTier?.name,
            healthStatus = healthStatus.name,
            mealQuality = mealQuality,
            dormSatisfaction = dormSatisfaction,
            exerciseLevel = exerciseLevel,
            consecutiveSickDays = consecutiveSickDays,
            enrollYear = enrollYear,
            enrollMonth = enrollMonth,
            lastPromotionYear = lastPromotionYear,
            graduateYear = graduateYear,
            graduateMonth = graduateMonth,
            graduationProjectionState = graduationProjectionState,
            reviewRating = review?.rating,
            reviewComment = review?.comment,
            reviewReputationImpact = review?.reputationImpact
        )
    }
}
