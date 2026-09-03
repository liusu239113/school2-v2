package com.arktools.xiao.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "teachers")
data class TeacherEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val gender: String = "MALE",
    val level: String,
    val role: String,
    val teaching: Int,
    val research: Int,
    val management: Int,
    val psychology: Int,
    val salary: Double,
    val fatigue: Int,
    val loyalty: Int,
    val isWorking: Boolean,
    val isOnVacation: Boolean,
    val hireDate: Long,
    val schoolId: String,
    val traits: String = "",  // Comma-separated TeacherTrait names
    val avatarIndex: Int = 1,  // 1-4, for avatar variety
    val pendingResignation: Boolean = false,  // 离职申请等待审批
    val experiencePoints: Int = 0  // 教师经验值
)
