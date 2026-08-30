package com.arktools.xiaozhang.domain.graduate

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 研究生院（硕博培养）：
 * - 9 月按名额招硕士/博士（名额由校园等级与学科评级决定）
 * - 每位在读研究生按月给学校带来科研经费；有导师进度快一倍
 * - 修满学制毕业授学位：硕士 +40 声誉、博士 +120 声誉
 * - 状态内嵌 policyJson 持久化（graduateStateJson），不改数据库
 */
@Serializable
data class GradStudent(
    val id: String,
    val name: String,
    val type: String,               // MASTER / PHD
    val disciplineId: String,
    val advisorId: String? = null,
    val yearsDone: Double = 0.0,
    val quality: Int = 60
) {
    val needYears: Double get() = if (type == "PHD") 4.0 else 3.0
    val typeName: String get() = if (type == "PHD") "博士" else "硕士"
}

@Singleton
class GraduateSchoolManager @Inject constructor() {

    @Serializable
    data class ManagerState(
        val students: List<GradStudent> = emptyList(),
        val totalGraduated: Int = 0,
        val lastIntakeYear: Int = 0
    )

    private val _state = MutableStateFlow(ManagerState())
    val state: StateFlow<ManagerState> = _state.asStateFlow()

    companion object {
        fun masterQuota(campusLevel: Int, ratedAB: Int): Int =
            if (campusLevel < 3) 0 else (2 + campusLevel + ratedAB).coerceAtMost(16)

        fun phdQuota(campusLevel: Int, ratedAPlus: Int): Int =
            if (campusLevel < 4) 0 else (ratedAPlus + (if (campusLevel >= 5) 2 else 0)).coerceAtMost(8)

        /** 导师可带研究生数（按职称） */
        fun advisorCapacity(levelName: String): Int = when (levelName) {
            "S" -> 4
            "A" -> 3
            "B" -> 2
            else -> 1
        }

        private val SURNAMES = listOf(
            "王", "李", "张", "刘", "陈", "杨", "赵", "黄", "周", "吴",
            "徐", "孙", "胡", "朱", "高", "林", "何", "郭", "马", "罗"
        )
        private val GIVEN = listOf(
            "志远", "思齐", "若曦", "浩然", "雨桐", "子墨", "嘉树", "清扬",
            "明轩", "语汐", "承宇", "书瑶", "一鸣", "楠溪", "砚秋", "牧遥",
            "云舒", "砚舟", "若谷", "望舒"
        )

        fun randomName(rng: kotlin.random.Random = kotlin.random.Random): String =
            SURNAMES[rng.nextInt(SURNAMES.size)] + GIVEN[rng.nextInt(GIVEN.size)]
    }

    /** 每月科研经费（万元）：硕士 0.2 / 博士 0.5 */
    fun monthlyIncomeWan(): Double =
        _state.value.students.sumOf { if (it.type == "PHD") 0.5 else 0.2 }

    /** 每月推进；返回本届毕业名单。无导师进度减半。 */
    fun advanceMonth(advisorLoad: Map<String, Int>): List<GradStudent> {
        val st = _state.value
        if (st.students.isEmpty()) return emptyList()
        val graduated = mutableListOf<GradStudent>()
        val updated = st.students.mapNotNull { s ->
            val hasAdvisor = s.advisorId != null && (advisorLoad[s.advisorId] ?: 0) > 0
            val next = s.yearsDone + if (hasAdvisor) 1.0 / 12.0 else 1.0 / 24.0
            if (next >= s.needYears) {
                graduated.add(s)
                null
            } else {
                s.copy(yearsDone = next)
            }
        }
        _state.value = st.copy(
            students = updated,
            totalGraduated = st.totalGraduated + graduated.size
        )
        return graduated
    }

    fun intake(list: List<GradStudent>, year: Int) {
        if (list.isEmpty()) return
        val st = _state.value
        _state.value = st.copy(students = st.students + list, lastIntakeYear = year)
    }

    fun assignAdvisor(studentId: String, teacherId: String): Boolean {
        val st = _state.value
        if (st.students.none { it.id == studentId }) return false
        _state.value = st.copy(
            students = st.students.map {
                if (it.id == studentId) it.copy(advisorId = teacherId) else it
            }
        )
        return true
    }

    fun advisorLoad(): Map<String, Int> = _state.value.students
        .mapNotNull { it.advisorId }
        .groupingBy { it }
        .eachCount()

    fun toJson(): String = runCatching { Json.encodeToString(_state.value) }.getOrDefault("")

    fun restoreFromJson(raw: String) {
        if (raw.isBlank()) return
        runCatching {
            _state.value = Json { ignoreUnknownKeys = true }.decodeFromString<ManagerState>(raw)
        }
    }

    fun reset() {
        _state.value = ManagerState()
    }
}
