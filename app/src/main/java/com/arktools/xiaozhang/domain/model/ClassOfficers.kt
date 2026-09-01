package com.arktools.xiaozhang.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 班干部岗位。每个岗位由学生当前属性决定资格，并在月结时产生班级效果。 */
@Serializable
enum class ClassOfficerRole(val displayName: String) {
    MONITOR("班长"),
    STUDY_COMMITTEE("学习委员"),
    LIFE_COMMITTEE("生活委员"),
    ARTS_COMMITTEE("文艺委员"),
    SPORTS_COMMITTEE("体育委员"),
    MENTAL_HEALTH_COMMITTEE("心理委员");

    fun qualification(student: Student): OfficerQualification {
        val a = student.attributes
        val score = when (this) {
            MONITOR -> a.social * 0.55f + a.morality * 0.45f
            STUDY_COMMITTEE -> a.intelligence * 0.8f + a.morality * 0.2f
            LIFE_COMMITTEE -> a.social * 0.65f + a.morality * 0.35f
            ARTS_COMMITTEE -> a.creativity * 0.8f + a.social * 0.2f
            SPORTS_COMMITTEE -> a.physical * 0.8f + a.social * 0.2f
            MENTAL_HEALTH_COMMITTEE -> a.social * 0.55f + a.morality * 0.45f
        }
        val required = when (this) {
            MONITOR, MENTAL_HEALTH_COMMITTEE -> 58f
            else -> 60f
        }
        return OfficerQualification(score, required, score >= required)
    }
}

data class OfficerQualification(
    val score: Float,
    val required: Float,
    val eligible: Boolean
)

@Serializable
data class ClassOfficer(
    val studentId: String,
    val name: String
)

/**
 * 班级班干部映射：classId -> role -> officer。
 * decode() 兼容旧版本 classId -> Officer 的单班长存档。
 */
object ClassOfficers {
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(raw: String): Map<String, Map<ClassOfficerRole, ClassOfficer>> {
        if (raw.isBlank()) return emptyMap()
        val current = runCatching {
            json.decodeFromString<Map<String, Map<String, ClassOfficer>>>(raw).mapValues { (_, roles) ->
                roles.mapNotNull { (roleName, officer) ->
                    runCatching { ClassOfficerRole.valueOf(roleName) }.getOrNull()?.let { it to officer }
                }.toMap()
            }
        }.getOrNull()
        if (current != null) return current

        return runCatching {
            json.decodeFromString<Map<String, ClassOfficer>>(raw).mapValues { (_, officer) ->
                mapOf(ClassOfficerRole.MONITOR to officer)
            }
        }.getOrDefault(emptyMap())
    }

    fun encode(map: Map<String, Map<ClassOfficerRole, ClassOfficer>>): String =
        runCatching {
            json.encodeToString(
                map.mapValues { (_, roles) -> roles.mapKeys { (role, _) -> role.name } }
            )
        }.getOrDefault("")

    fun validForClass(
        classId: String,
        allOfficers: Map<String, Map<ClassOfficerRole, ClassOfficer>>,
        students: List<Student>
    ): Map<ClassOfficerRole, Pair<ClassOfficer, Student>> {
        val activeById = students
            .filter { it.classId == classId && it.status in listOf(StudentStatus.ENROLLED, StudentStatus.STUDYING) }
            .associateBy { it.id }
        return allOfficers[classId].orEmpty().mapNotNull { (role, officer) ->
            val student = activeById[officer.studentId] ?: return@mapNotNull null
            if (!role.qualification(student).eligible) return@mapNotNull null
            role to (officer to student)
        }.toMap()
    }
}
