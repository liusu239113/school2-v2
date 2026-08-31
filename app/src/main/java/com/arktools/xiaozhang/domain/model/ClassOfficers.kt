package com.arktools.xiaozhang.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 班级班干部映射：classId -> (学生ID, 学生姓名)。
 * 班级结构为运行时重建，班长走 policyJson 持久化（与学业导师映射同思路）。
 */
object ClassOfficers {
    @Serializable
    data class Officer(val studentId: String, val name: String)

    private val json = Json { ignoreUnknownKeys = true }

    fun decode(raw: String): Map<String, Pair<String, String>> =
        if (raw.isBlank()) emptyMap()
        else runCatching {
            json.decodeFromString<Map<String, Officer>>(raw).mapValues {
                it.value.studentId to it.value.name
            }
        }.getOrDefault(emptyMap())

    fun encode(map: Map<String, Pair<String, String>>): String =
        runCatching {
            json.encodeToString(map.mapValues { Officer(it.value.first, it.value.second) })
        }.getOrDefault("")
}
