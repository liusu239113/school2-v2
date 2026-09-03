package com.arktools.xiao.domain.model

import java.util.UUID

data class Teacher(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val gender: Gender = Gender.MALE,
    val level: TeacherLevel,
    val role: TeacherRole,
    var teaching: Int,
    var research: Int,
    var management: Int,
    var psychology: Int,
    var salary: Double,
    var fatigue: Int = 0,
    var loyalty: Int = 75,
    var isWorking: Boolean = true,
    var isOnVacation: Boolean = false,
    var hireDate: Long = 0L,  // 游戏绝对天数 (year*360 + (month-1)*30 + day)，雇佣时由 ViewModel 设置
    val traits: List<TeacherTrait> = emptyList(),
    var experiencePoints: Int = 0,
    val avatarIndex: Int = 1,  // 1-4, used for unique avatar selection
    var pendingResignation: Boolean = false  // 离职申请已提交，等待校长审批

) {
    val averageSkill: Int
        get() = (teaching + research + management + psychology) / 4

    val monthlySalary: Double
        get() = salary

    val hasPositiveTraits: Boolean
        get() = traits.any { it.category == TraitCategory.POSITIVE }

    val traitDescription: String
        get() = if (traits.isEmpty()) "无特殊特质" else traits.joinToString("、") { it.displayName }
}

enum class TeacherLevel {
    C, B, A, S
}

enum class TeacherRole(val displayName: String, val category: SubjectCategory) {
    CHINESE("语文教师", SubjectCategory.LITERATURE),
    MATH("数学教师", SubjectCategory.SCIENCE),
    ENGLISH("英语教师", SubjectCategory.LANGUAGE),
    PHYSICS("物理教师", SubjectCategory.SCIENCE),
    CHEMISTRY("化学教师", SubjectCategory.SCIENCE),
    BIOLOGY("生物教师", SubjectCategory.SCIENCE),
    HISTORY("历史教师", SubjectCategory.LITERATURE),
    GEOGRAPHY("地理教师", SubjectCategory.LITERATURE),
    POLITICS("政治教师", SubjectCategory.LITERATURE),
    ART("美术教师", SubjectCategory.ART),
    PE("体育教师", SubjectCategory.SPORTS),
    MUSIC("音乐教师", SubjectCategory.ART)
}

enum class SubjectCategory {
    LITERATURE, SCIENCE, LANGUAGE, ART, SPORTS
}

enum class Gender {
    MALE, FEMALE
}

enum class TeacherState {
    IDLE, TIRED, DISSATISFIED, WANT_LEAVE, INSPIRED
}
