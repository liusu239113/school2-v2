package com.arktools.xiaozhang.domain.teacherdev

import com.arktools.xiaozhang.domain.model.Gender
import com.arktools.xiaozhang.domain.model.Teacher
import com.arktools.xiaozhang.domain.model.TeacherLevel
import com.arktools.xiaozhang.domain.model.TeacherRole
import kotlinx.serialization.Serializable

@Serializable
enum class TalentChannel { AD, SCHOOL, HEADHUNTER }

@Serializable
enum class TalentSource { MARKET, ALUMNI_RETURN }

@Serializable
enum class TalentStatus { AVAILABLE, HIRED, EXPIRED }

@Serializable
enum class TeacherCareerPhase(val displayName: String) {
    YOUNG("青年教师"),
    CORE("骨干教师"),
    SENIOR("资深教师"),
    PRE_RETIREMENT("临近退休"),
    RETIRED("已退休")
}

@Serializable
data class TeacherTalentCandidate(
    val id: String,
    val teacher: TeacherTalentSnapshot,
    val source: TalentSource,
    val sourceAlumniId: String? = null,
    val status: TalentStatus,
    val poolYear: Int
)

@Serializable
data class TeacherTalentSnapshot(
    val id: String,
    val name: String,
    val gender: String,
    val level: String,
    val role: String,
    val teaching: Int,
    val research: Int,
    val management: Int,
    val psychology: Int,
    val salary: Double,
    val avatarIndex: Int
) {
    fun toTeacher(): Teacher = Teacher(
        id = id,
        name = name,
        gender = Gender.valueOf(gender),
        level = TeacherLevel.valueOf(level),
        role = TeacherRole.valueOf(role),
        teaching = teaching,
        research = research,
        management = management,
        psychology = psychology,
        salary = salary,
        avatarIndex = avatarIndex
    )

    companion object {
        fun fromTeacher(teacher: Teacher): TeacherTalentSnapshot = TeacherTalentSnapshot(
            id = teacher.id,
            name = teacher.name,
            gender = teacher.gender.name,
            level = teacher.level.name,
            role = teacher.role.name,
            teaching = teacher.teaching,
            research = teacher.research,
            management = teacher.management,
            psychology = teacher.psychology,
            salary = teacher.salary,
            avatarIndex = teacher.avatarIndex
        )
    }
}

@Serializable
data class FormerFacultyRecord(
    val teacherId: String,
    val name: String,
    val departureYear: Int,
    val reason: String,
    val finalTitle: String,
    val yearsOfService: Int
)
