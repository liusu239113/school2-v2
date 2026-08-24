package com.arktools.xiaozhang.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.arktools.xiaozhang.data.local.entity.TeacherEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TeacherDao {
    @Query("SELECT * FROM teachers WHERE schoolId = :schoolId")
    fun getTeachersBySchoolFlow(schoolId: String): Flow<List<TeacherEntity>>

    @Query("SELECT * FROM teachers WHERE schoolId = :schoolId")
    suspend fun getTeachersBySchool(schoolId: String): List<TeacherEntity>

    @Query("SELECT * FROM teachers WHERE schoolId = :schoolId AND id = :teacherId")
    suspend fun getTeacherById(schoolId: String, teacherId: String): TeacherEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTeacher(teacher: TeacherEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTeachers(teachers: List<TeacherEntity>)

    @Query(
        "UPDATE teachers SET fatigue = :fatigue, loyalty = :loyalty, " +
            "isOnVacation = :isOnVacation, pendingResignation = :pendingResignation, " +
            "teaching = :teaching, research = :research, " +
            "management = :management, psychology = :psychology, " +
            "experiencePoints = :experiencePoints " +
            "WHERE schoolId = :schoolId AND id = :teacherId AND isWorking = 1 " +
            "AND fatigue = :expectedFatigue AND loyalty = :expectedLoyalty " +
            "AND isOnVacation = :expectedIsOnVacation " +
            "AND pendingResignation = :expectedPendingResignation " +
            "AND teaching = :expectedTeaching AND research = :expectedResearch " +
            "AND management = :expectedManagement AND psychology = :expectedPsychology " +
            "AND experiencePoints = :expectedExperiencePoints"
    )
    suspend fun compareAndSetDailyState(
        schoolId: String,
        teacherId: String,
        expectedFatigue: Int,
        expectedLoyalty: Int,
        expectedIsOnVacation: Boolean,
        expectedPendingResignation: Boolean,
        expectedTeaching: Int,
        expectedResearch: Int,
        expectedManagement: Int,
        expectedPsychology: Int,
        expectedExperiencePoints: Int,
        fatigue: Int,
        loyalty: Int,
        isOnVacation: Boolean,
        pendingResignation: Boolean,
        teaching: Int,
        research: Int,
        management: Int,
        psychology: Int,
        experiencePoints: Int
    ): Int

    @Query(
        "UPDATE teachers SET teaching = MIN(1000, teaching + :teachingGain), " +
            "research = MIN(1000, research + :researchGain), " +
            "management = MIN(1000, management + :managementGain), " +
            "psychology = MIN(1000, psychology + :psychologyGain), " +
            "fatigue = MIN(100, fatigue + :fatigueGain) " +
            "WHERE schoolId = :schoolId AND id = :teacherId AND isWorking = 1"
    )
    suspend fun applyTraining(
        schoolId: String,
        teacherId: String,
        teachingGain: Int,
        researchGain: Int,
        managementGain: Int,
        psychologyGain: Int,
        fatigueGain: Int
    ): Int

    @Query(
        "UPDATE teachers SET teaching = MIN(1000, teaching + :teachingGain), " +
            "research = MIN(1000, research + :researchGain), " +
            "management = MIN(1000, management + :managementGain), " +
            "psychology = MIN(1000, psychology + :psychologyGain) " +
            "WHERE schoolId = :schoolId AND id = :teacherId " +
            "AND isWorking = 1 AND isOnVacation = 0"
    )
    suspend fun addSkillGrowth(
        schoolId: String,
        teacherId: String,
        teachingGain: Int,
        researchGain: Int,
        managementGain: Int,
        psychologyGain: Int
    ): Int

    @Query(
        "UPDATE teachers SET salary = :newSalary, loyalty = :newLoyalty " +
            "WHERE schoolId = :schoolId AND id = :teacherId AND isWorking = 1"
    )
    suspend fun updateSalaryAndLoyalty(
        schoolId: String,
        teacherId: String,
        newSalary: Double,
        newLoyalty: Int
    ): Int

    @Transaction
    suspend fun adjustSalary(
        schoolId: String,
        teacherId: String,
        newSalary: Double
    ): Boolean {
        val teacher = getTeacherById(schoolId, teacherId) ?: return false
        if (!teacher.isWorking || teacher.salary <= 0.0) return false
        val newLoyalty = if (newSalary >= teacher.salary) {
            (teacher.loyalty + 10).coerceAtMost(100)
        } else {
            val cutPercent = (
                (teacher.salary - newSalary) / teacher.salary * 100
            ).coerceAtMost(50.0)
            val penalty = (cutPercent * 1.5).toInt().coerceAtLeast(8)
            (teacher.loyalty - penalty).coerceAtLeast(0)
        }
        return updateSalaryAndLoyalty(
            schoolId,
            teacherId,
            newSalary,
            newLoyalty
        ) == 1
    }

    @Query(
        "UPDATE teachers SET loyalty = MIN(100, MAX(0, loyalty + :delta)) " +
            "WHERE schoolId = :schoolId AND isWorking = 1"
    )
    suspend fun adjustAllLoyalty(schoolId: String, delta: Int): Int

    @Query(
        "UPDATE teachers SET loyalty = MIN(100, MAX(:minimum, loyalty + :delta)) " +
            "WHERE schoolId = :schoolId AND id = :teacherId AND isWorking = 1"
    )
    suspend fun adjustLoyalty(
        schoolId: String,
        teacherId: String,
        delta: Int,
        minimum: Int
    ): Int

    @Query(
        "UPDATE teachers SET salary = salary * (1.0 + :raiseFraction), " +
            "loyalty = 35, pendingResignation = 0 " +
            "WHERE schoolId = :schoolId AND id = :teacherId " +
            "AND isWorking = 1 AND pendingResignation = 1"
    )
    suspend fun retainWithRaise(
        schoolId: String,
        teacherId: String,
        raiseFraction: Double
    ): Int

    @Query(
        "UPDATE teachers SET salary = salary * (1.0 + :raisePercent / 100.0), " +
            "loyalty = MIN(100, loyalty + 15) " +
            "WHERE schoolId = :schoolId AND id = :teacherId AND isWorking = 1"
    )
    suspend fun approveRaise(
        schoolId: String,
        teacherId: String,
        raisePercent: Double
    ): Int

    @Query(
        "UPDATE teachers SET salary = :newSalary, " +
            "loyalty = MIN(100, loyalty + 10) " +
            "WHERE schoolId = :schoolId AND id = :teacherId AND isWorking = 1"
    )
    suspend fun renewContract(
        schoolId: String,
        teacherId: String,
        newSalary: Double
    ): Int

    @Query(
        "UPDATE teachers SET level = :level " +
            "WHERE schoolId = :schoolId AND id = :teacherId AND isWorking = 1"
    )
    suspend fun updateLevel(
        schoolId: String,
        teacherId: String,
        level: String
    ): Int

    @Query(
        "UPDATE teachers SET level = :level, teaching = :teaching, " +
            "research = :research, management = :management, psychology = :psychology " +
            "WHERE schoolId = :schoolId AND id = :teacherId AND isWorking = 1"
    )
    suspend fun updateDevelopmentFields(
        schoolId: String,
        teacherId: String,
        level: String,
        teaching: Int,
        research: Int,
        management: Int,
        psychology: Int
    ): Int

    @Transaction
    suspend fun syncDevelopmentProfile(
        schoolId: String,
        teacherId: String,
        level: String,
        profileSkillLevel: Float,
        primarilyTeaching: Boolean
    ): Boolean {
        val teacher = getTeacherById(schoolId, teacherId) ?: return false
        if (!teacher.isWorking) return false
        val averageSkill = (
            teacher.teaching + teacher.research +
                teacher.management + teacher.psychology
        ) / 4
        val skillDelta = (profileSkillLevel - averageSkill).toInt()
        var teaching = teacher.teaching
        var research = teacher.research
        var management = teacher.management
        var psychology = teacher.psychology
        if (skillDelta > 0) {
            if (primarilyTeaching) {
                teaching = (teaching + (skillDelta * 0.6f).toInt().coerceAtLeast(1))
                    .coerceAtMost(1000)
                research = (research + (skillDelta * 0.2f).toInt()).coerceAtMost(1000)
                management = (management + (skillDelta * 0.1f).toInt()).coerceAtMost(1000)
                psychology = (psychology + (skillDelta * 0.1f).toInt()).coerceAtMost(1000)
            } else {
                teaching = (teaching + (skillDelta * 0.4f).toInt().coerceAtLeast(1))
                    .coerceAtMost(1000)
                psychology = (psychology + (skillDelta * 0.3f).toInt()).coerceAtMost(1000)
                management = (management + (skillDelta * 0.15f).toInt()).coerceAtMost(1000)
                research = (research + (skillDelta * 0.15f).toInt()).coerceAtMost(1000)
            }
        }
        return updateDevelopmentFields(
            schoolId,
            teacherId,
            level,
            teaching,
            research,
            management,
            psychology
        ) == 1
    }

    @Query("DELETE FROM teachers WHERE schoolId = :schoolId AND id IN (:teacherIds)")
    suspend fun deleteTeachersByIds(
        schoolId: String,
        teacherIds: List<String>
    ): Int

    @Query("DELETE FROM teachers WHERE schoolId = :schoolId AND id = :teacherId")
    suspend fun deleteTeacher(schoolId: String, teacherId: String)

    @Query("DELETE FROM teachers WHERE schoolId = :schoolId")
    suspend fun deleteTeachersBySchool(schoolId: String)
}
