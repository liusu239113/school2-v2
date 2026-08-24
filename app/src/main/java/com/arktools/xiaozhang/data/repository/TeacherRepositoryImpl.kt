package com.arktools.xiaozhang.data.repository

import androidx.room.withTransaction
import com.arktools.xiaozhang.data.local.AppDatabase
import com.arktools.xiaozhang.data.local.dao.TeacherDao
import com.arktools.xiaozhang.data.local.entity.TeacherEntity
import com.arktools.xiaozhang.data.pref.SettingsDataStore
import com.arktools.xiaozhang.domain.model.Gender
import com.arktools.xiaozhang.domain.model.SubjectCategory
import com.arktools.xiaozhang.domain.model.Teacher
import com.arktools.xiaozhang.domain.model.TeacherLevel
import com.arktools.xiaozhang.domain.model.TeacherRole
import com.arktools.xiaozhang.domain.model.TeacherTrait
import com.arktools.xiaozhang.domain.engine.GameBalanceConfig
import com.arktools.xiaozhang.domain.repository.PaidTrainingResult
import com.arktools.xiaozhang.domain.repository.PaidTrainingStatus
import com.arktools.xiaozhang.domain.repository.TeacherDevelopmentProfileUpdate
import com.arktools.xiaozhang.domain.repository.TeacherRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.random.Random

class TeacherRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val teacherDao: TeacherDao,
    private val settingsDataStore: SettingsDataStore
) : TeacherRepository {

    override fun getTeachersFlow(): Flow<List<Teacher>> = flow {
        // 瞬时数据库异常不能把"有教师"误报成"无教师"，异常时保持旧值并自动重连
        while (true) {
            try {
                settingsDataStore.schoolId.map { schoolId ->
                    schoolId ?: ""
                }.flatMapLatest { schoolId ->
                    teacherDao.getTeachersBySchoolFlow(schoolId).map { list ->
                        list.map { it.toDomain() }
                    }
                }.collect { emit(it) }
            } catch (e: Exception) {
                android.util.Log.e("TeacherRepo", "Flow error suppressed (data preserved), retrying: ${e.message}")
            }
            delay(1000)
        }
    }

    override suspend fun getTeachers(): List<Teacher> {
        val schoolId = settingsDataStore.schoolId.first() ?: return emptyList()
        return teacherDao.getTeachersBySchool(schoolId).map { it.toDomain() }
    }

    override suspend fun getTeacherById(teacherId: String): Teacher? {
        val schoolId = settingsDataStore.schoolId.first() ?: return null
        return teacherDao.getTeacherById(schoolId, teacherId)?.toDomain()
    }

    override suspend fun hireTeacher(teacher: Teacher) {
        val schoolId = settingsDataStore.schoolId.first() ?: return
        teacherDao.insertTeacher(teacher.toEntity(schoolId))
    }

    override suspend fun fireTeacher(teacherId: String) {
        val schoolId = settingsDataStore.schoolId.first() ?: return
        teacherDao.deleteTeacher(schoolId, teacherId)
    }

    override suspend fun saveDailyState(
        previous: Teacher,
        updated: Teacher
    ): Boolean {
        val schoolId = settingsDataStore.schoolId.first() ?: return false
        if (previous.id != updated.id) return false
        return teacherDao.compareAndSetDailyState(
            schoolId = schoolId,
            teacherId = previous.id,
            expectedFatigue = previous.fatigue,
            expectedLoyalty = previous.loyalty,
            expectedIsOnVacation = previous.isOnVacation,
            expectedPendingResignation = previous.pendingResignation,
            expectedTeaching = previous.teaching,
            expectedResearch = previous.research,
            expectedManagement = previous.management,
            expectedPsychology = previous.psychology,
            expectedExperiencePoints = previous.experiencePoints,
            fatigue = updated.fatigue,
            loyalty = updated.loyalty,
            isOnVacation = updated.isOnVacation,
            pendingResignation = updated.pendingResignation,
            teaching = updated.teaching,
            research = updated.research,
            management = updated.management,
            psychology = updated.psychology,
            experiencePoints = updated.experiencePoints
        ) == 1
    }

    override suspend fun trainTeacher(teacherId: String): Boolean {
        val schoolId = settingsDataStore.schoolId.first() ?: return false
        return teacherDao.applyTraining(
            schoolId = schoolId,
            teacherId = teacherId,
            teachingGain = Random.nextInt(20, 50),
            researchGain = Random.nextInt(20, 50),
            managementGain = Random.nextInt(20, 50),
            psychologyGain = Random.nextInt(20, 50),
            fatigueGain = 10
        ) == 1
    }

    override suspend fun performPaidTraining(
        teacherId: String
    ): PaidTrainingResult {
        val schoolId = settingsDataStore.getSchoolId()
        if (schoolId.isBlank()) {
            return PaidTrainingResult(
                PaidTrainingStatus.SCHOOL_UNAVAILABLE
            )
        }

        return database.withTransaction {
            val schoolDao = database.schoolDao()
            val school = schoolDao.getSchoolCore()
            if (school == null || school.id != schoolId) {
                return@withTransaction PaidTrainingResult(
                    PaidTrainingStatus.SCHOOL_UNAVAILABLE
                )
            }
            val teacher = teacherDao.getTeacherById(
                schoolId,
                teacherId
            )
            if (teacher == null || !teacher.isWorking) {
                return@withTransaction PaidTrainingResult(
                    PaidTrainingStatus.TEACHER_UNAVAILABLE
                )
            }

            val averageSkill = (
                teacher.teaching + teacher.research +
                    teacher.management + teacher.psychology
            ) / 4
            val cost = GameBalanceConfig.getTrainingCost(averageSkill)
            val successRate =
                GameBalanceConfig.getTrainingSuccessRate(averageSkill)
            if (!cost.isFinite() || cost < 0.0 ||
                !successRate.isFinite() || successRate !in 0.0..1.0
            ) {
                return@withTransaction PaidTrainingResult(
                    PaidTrainingStatus.TEACHER_UNAVAILABLE
                )
            }
            if (school.cash < cost) {
                return@withTransaction PaidTrainingResult(
                    status = PaidTrainingStatus.INSUFFICIENT_FUNDS,
                    cost = cost,
                    successRate = successRate
                )
            }

            val hasEffect = Random.nextDouble() < successRate
            if (hasEffect) {
                check(
                    teacherDao.applyTraining(
                        schoolId = schoolId,
                        teacherId = teacherId,
                        teachingGain = Random.nextInt(20, 50),
                        researchGain = Random.nextInt(20, 50),
                        managementGain = Random.nextInt(20, 50),
                        psychologyGain = Random.nextInt(20, 50),
                        fatigueGain = 10
                    ) == 1
                ) { "Paid teacher training update failed" }
            }

            check(
                schoolDao.deductCashIfEnough(
                    schoolId,
                    cost,
                    System.currentTimeMillis()
                ) == 1
            ) { "Paid teacher training payment failed" }

            PaidTrainingResult(
                status = if (hasEffect) {
                    PaidTrainingStatus.SUCCESS
                } else {
                    PaidTrainingStatus.NO_EFFECT
                },
                cost = cost,
                successRate = successRate
            )
        }
    }

    override suspend fun addSkillGrowth(
        teacherId: String,
        teachingGain: Int,
        researchGain: Int,
        managementGain: Int,
        psychologyGain: Int
    ): Boolean {
        val schoolId = settingsDataStore.schoolId.first() ?: return false
        return teacherDao.addSkillGrowth(
            schoolId,
            teacherId,
            teachingGain,
            researchGain,
            managementGain,
            psychologyGain
        ) == 1
    }

    override suspend fun adjustSalary(
        teacherId: String,
        newSalary: Double
    ): Boolean {
        val schoolId = settingsDataStore.schoolId.first() ?: return false
        return teacherDao.adjustSalary(schoolId, teacherId, newSalary)
    }

    override suspend fun adjustAllLoyalty(delta: Int): Int {
        val schoolId = settingsDataStore.schoolId.first() ?: return 0
        return teacherDao.adjustAllLoyalty(schoolId, delta)
    }

    override suspend fun adjustLoyalty(
        teacherId: String,
        delta: Int,
        minimum: Int
    ): Boolean {
        val schoolId = settingsDataStore.schoolId.first() ?: return false
        return teacherDao.adjustLoyalty(
            schoolId,
            teacherId,
            delta,
            minimum
        ) == 1
    }

    override suspend fun retainWithRaise(
        teacherId: String,
        raiseFraction: Double
    ): Boolean {
        val schoolId = settingsDataStore.schoolId.first() ?: return false
        return teacherDao.retainWithRaise(
            schoolId,
            teacherId,
            raiseFraction
        ) == 1
    }

    override suspend fun approveRaise(
        teacherId: String,
        raisePercent: Double
    ): Boolean {
        val schoolId = settingsDataStore.schoolId.first() ?: return false
        return teacherDao.approveRaise(
            schoolId,
            teacherId,
            raisePercent
        ) == 1
    }

    override suspend fun renewContract(
        teacherId: String,
        newSalary: Double
    ): Boolean {
        val schoolId = settingsDataStore.schoolId.first() ?: return false
        return teacherDao.renewContract(
            schoolId,
            teacherId,
            newSalary
        ) == 1
    }

    override suspend fun updateLevel(
        teacherId: String,
        level: TeacherLevel
    ): Boolean {
        val schoolId = settingsDataStore.schoolId.first() ?: return false
        return teacherDao.updateLevel(
            schoolId,
            teacherId,
            level.name
        ) == 1
    }

    override suspend fun syncDevelopmentProfile(
        teacherId: String,
        level: TeacherLevel,
        profileSkillLevel: Float,
        primarilyTeaching: Boolean
    ): Boolean {
        val schoolId = settingsDataStore.schoolId.first() ?: return false
        return teacherDao.syncDevelopmentProfile(
            schoolId,
            teacherId,
            level.name,
            profileSkillLevel,
            primarilyTeaching
        )
    }

    override suspend fun commitDevelopmentState(
        expense: Double,
        teacherDevJson: String,
        departedTeacherIds: List<String>,
        profileUpdates: List<TeacherDevelopmentProfileUpdate>,
        pressureJson: String?,
        timetableJson: String?
    ): Boolean {
        if (!expense.isFinite() || expense < 0.0 ||
            teacherDevJson.isBlank() ||
            departedTeacherIds.distinct().size != departedTeacherIds.size ||
            profileUpdates.map { it.teacherId }.distinct().size !=
            profileUpdates.size ||
            (pressureJson != null && pressureJson.isBlank()) ||
            (timetableJson != null && timetableJson.isBlank()) ||
            ((pressureJson == null) != (timetableJson == null))
        ) {
            return false
        }
        val schoolId = settingsDataStore.getSchoolId()
        if (schoolId.isBlank()) return false

        return database.withTransaction {
            val school = database.schoolDao().getSchoolCore()
            if (school == null || school.id != schoolId) {
                return@withTransaction false
            }

            if (departedTeacherIds.isNotEmpty()) {
                check(
                    teacherDao.deleteTeachersByIds(
                        schoolId,
                        departedTeacherIds
                    ) == departedTeacherIds.size
                ) { "Teacher development departure delete failed" }
            }

            profileUpdates.forEach { update ->
                check(
                    teacherDao.syncDevelopmentProfile(
                        schoolId = schoolId,
                        teacherId = update.teacherId,
                        level = update.level.name,
                        profileSkillLevel = update.profileSkillLevel,
                        primarilyTeaching = update.primarilyTeaching
                    )
                ) { "Teacher development profile update failed" }
            }

            check(
                database.schoolDao().commitTeacherDevelopmentState(
                    schoolId = schoolId,
                    expense = expense,
                    teacherDevJson = teacherDevJson,
                    pressureJson = pressureJson,
                    timetableJson = timetableJson,
                    now = System.currentTimeMillis()
                ) == 1
            ) { "Teacher development state commit failed" }
            true
        }
    }

    override fun generateCandidates(level: TeacherLevel, count: Int): List<Teacher> {
        val maleFirstNames = listOf("王", "李", "张", "刘", "陈", "杨", "赵", "黄", "周", "吴", "徐", "孙", "胡", "朱", "高", "林")
        val femaleFirstNames = listOf("王", "李", "张", "刘", "陈", "杨", "赵", "黄", "周", "吴", "徐", "孙", "胡", "朱", "高", "林")
        val maleLastNames = listOf("伟", "强", "磊", "军", "洋", "勇", "杰", "涛", "明", "浩", "鹏", "峰", "超", "博", "昊", "宇")
        val femaleLastNames = listOf("芳", "娜", "敏", "静", "丽", "艳", "娟", "萍", "玲", "婷", "雪", "梅", "琳", "颖", "薇", "莹")

        return List(count) {
            val gender = if (Random.nextBoolean()) Gender.MALE else Gender.FEMALE
            val firstName = if (gender == Gender.MALE) maleFirstNames.random() else femaleFirstNames.random()
            val lastName = if (gender == Gender.MALE) maleLastNames.random() else femaleLastNames.random()
            val name = firstName + lastName
            val avatarIndex = Random.nextInt(1, 5) // 1-4

            val baseSkill = when (level) {
                TeacherLevel.C -> Random.nextInt(100, 300)
                TeacherLevel.B -> Random.nextInt(250, 500)
                TeacherLevel.A -> Random.nextInt(450, 700)
                TeacherLevel.S -> Random.nextInt(650, 900)
            }

            Teacher(
                name = name,
                gender = gender,
                level = level,
                role = TeacherRole.entries.random(),
                teaching = baseSkill + Random.nextInt(-50, 50),
                research = baseSkill + Random.nextInt(-50, 50),
                management = baseSkill + Random.nextInt(-50, 50),
                psychology = baseSkill + Random.nextInt(-50, 50),
                salary = when (level) {
                    TeacherLevel.C -> Random.nextDouble(0.3, 0.5)   // 3000-5000元
                    TeacherLevel.B -> Random.nextDouble(0.5, 1.0)   // 5000-10000元
                    TeacherLevel.A -> Random.nextDouble(1.0, 1.5)   // 10000-15000元
                    TeacherLevel.S -> Random.nextDouble(1.5, 2.0)   // 15000-20000元
                }.let { Math.round(it * 100.0) / 100.0 },
                avatarIndex = avatarIndex
            )
        }
    }

    override suspend fun deleteAll() {
        val schoolId = settingsDataStore.schoolId.first() ?: return
        teacherDao.deleteTeachersBySchool(schoolId)
    }

    private fun TeacherEntity.toDomain(): Teacher {
        return Teacher(
            id = id,
            name = name,
            gender = try { Gender.valueOf(gender) } catch (_: Exception) { Gender.MALE },
            level = try { TeacherLevel.valueOf(level) } catch (_: Exception) { TeacherLevel.entries.first() },
            role = try { TeacherRole.valueOf(role) } catch (_: Exception) { TeacherRole.entries.first() },
            teaching = teaching,
            research = research,
            management = management,
            psychology = psychology,
            salary = salary,
            fatigue = fatigue,
            loyalty = loyalty,
            isWorking = isWorking,
            isOnVacation = isOnVacation,
            hireDate = hireDate,
            traits = if (traits.isBlank()) emptyList()
                else traits.split(",").mapNotNull { name ->
                    try { TeacherTrait.valueOf(name.trim()) } catch (_: Exception) { null }
                },
            experiencePoints = experiencePoints,
            avatarIndex = avatarIndex,
            pendingResignation = pendingResignation
        )
    }

    private fun Teacher.toEntity(schoolId: String): TeacherEntity {
        return TeacherEntity(
            id = id,
            name = name,
            gender = gender.name,
            level = level.name,
            role = role.name,
            teaching = teaching,
            research = research,
            management = management,
            psychology = psychology,
            salary = salary,
            fatigue = fatigue,
            loyalty = loyalty,
            isWorking = isWorking,
            isOnVacation = isOnVacation,
            hireDate = hireDate,
            schoolId = schoolId,
            traits = traits.joinToString(",") { it.name },
            avatarIndex = avatarIndex,
            pendingResignation = pendingResignation,
            experiencePoints = experiencePoints
        )
    }
}
