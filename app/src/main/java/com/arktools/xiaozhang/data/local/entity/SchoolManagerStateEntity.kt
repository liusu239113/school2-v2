package com.arktools.xiaozhang.data.local.entity

import androidx.room.Entity

/**
 * 大型 Manager 状态与 School 核心数据分离存储，避免单条 schools 记录超过 CursorWindow 上限。
 */
@Entity(
    tableName = "school_manager_states",
    primaryKeys = ["schoolId", "stateKey"]
)
data class SchoolManagerStateEntity(
    val schoolId: String,
    val stateKey: String,
    val payload: String,
    val updatedAt: Long
)
