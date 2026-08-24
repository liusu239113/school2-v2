package com.arktools.xiaozhang.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val subject: String,
    val theme: String,
    val courseType: String,
    val targetDistrict: String,
    val scale: String,
    val preparationProgress: Float,
    val problemCount: Int,
    val qualityScore: Float,
    val designScore: Float,
    val status: String,
    val teamIdsJson: String,
    val methodIdsJson: String,
    val ipId: String?,

    val enrollment: Long,
    val revenue: Double,
    val monthlyEnrollment: Long,
    val releaseDate: Long?,
    val releaseYear: Int?,
    val releaseMonth: Int?,
    val heat: Float,
    val marketingSpend: Double,
    val schoolId: String
)
