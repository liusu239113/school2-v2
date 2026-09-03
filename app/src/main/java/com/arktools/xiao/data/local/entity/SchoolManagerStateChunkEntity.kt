package com.arktools.xiao.data.local.entity

import androidx.room.Entity

/** 单个 Manager JSON 分块，确保任何 SQLite 行都小于 CursorWindow 限制。 */
@Entity(
    tableName = "school_manager_state_chunks",
    primaryKeys = ["schoolId", "stateKey", "chunkIndex"]
)
data class SchoolManagerStateChunkEntity(
    val schoolId: String,
    val stateKey: String,
    val chunkIndex: Int,
    val payload: String,
    val updatedAt: Long
)
