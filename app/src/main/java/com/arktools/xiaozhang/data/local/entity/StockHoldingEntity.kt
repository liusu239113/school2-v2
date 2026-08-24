package com.arktools.xiaozhang.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stock_holdings",
    indices = [Index(value = ["schoolId", "stockId"], unique = true)]
)
data class StockHoldingEntity(
    @PrimaryKey
    val id: String,
    val stockId: String,
    val shares: Int,
    val avgBuyPrice: Double,
    val schoolId: String
)