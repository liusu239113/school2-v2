package com.arktools.xiaozhang.domain.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 教学班与标准教室的持久化绑定。 */
object ClassFacilityAssignments {
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(raw: String): Map<String, String> =
        if (raw.isBlank()) emptyMap()
        else runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())

    fun encode(assignments: Map<String, String>): String =
        runCatching { json.encodeToString(assignments) }.getOrDefault("")

    /**
     * 保留仍有效且未超容量的绑定，再按教室顺序填充未绑定班级。
     * rooms 参数为 facilityId -> 可容纳教学班数。
     */
    fun reconcile(
        classIds: List<String>,
        rooms: List<Pair<String, Int>>,
        existing: Map<String, String>
    ): Map<String, String> {
        val validClassIds = classIds.toSet()
        val roomCapacity = rooms.toMap()
        val counts = mutableMapOf<String, Int>()
        val result = linkedMapOf<String, String>()

        classIds.forEach { classId ->
            val roomId = existing[classId] ?: return@forEach
            val cap = roomCapacity[roomId] ?: return@forEach
            val used = counts[roomId] ?: 0
            if (classId in validClassIds && used < cap) {
                result[classId] = roomId
                counts[roomId] = used + 1
            }
        }

        classIds.filterNot { it in result }.forEach { classId ->
            val room = rooms.firstOrNull { (roomId, capacity) ->
                (counts[roomId] ?: 0) < capacity
            } ?: return@forEach
            result[classId] = room.first
            counts[room.first] = (counts[room.first] ?: 0) + 1
        }
        return result
    }
}
