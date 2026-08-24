package com.arktools.xiaozhang.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stocks")
data class StockEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val basePrice: Double,
    val currentPrice: Double,
    val volatility: Double,
    val trend: String,
    val sector: String,
    val schoolId: String
)