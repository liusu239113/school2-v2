package com.arktools.xiaozhang.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stock_price_history",
    indices = [
        Index(
            value = ["schoolId", "stockId", "gameDay"],
            unique = true
        )
    ]
)
data class StockPriceHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val stockId: String,
    val schoolId: String,
    val gameDay: Int,
    val open: Double,
    val close: Double,
    val high: Double,
    val low: Double
)
