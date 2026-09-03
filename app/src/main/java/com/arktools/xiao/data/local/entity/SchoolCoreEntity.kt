package com.arktools.xiao.data.local.entity

/**
 * schools 表的轻量核心投影。Manager JSON 位于 school_manager_states，
 * 任何常规学校读取均不再把大型 JSON 拉进 CursorWindow。
 */
data class SchoolCoreEntity(
    val id: String,
    val name: String,
    val principalName: String,
    val cash: Double,
    val marketCap: Double,
    val reputation: Long,
    val starRating: Float,
    val foundedYear: Int,
    val currentYear: Int,
    val currentMonth: Int,
    val currentDay: Int,
    val campusLevel: Int,
    val levelUpYear: Int,
    val maxTeachers: Int,
    val branchSchools: Int,
    val hasOwnTextbook: Boolean,
    val hasOwnTech: Boolean,
    val totalCoursesReleased: Int,
    val totalRevenue: Double,
    val wasNearBankrupt: Boolean,
    val principalJson: String,
    val lastYearEndProcessingYear: Int,
    val lastMonthlySettlementYear: Int,
    val lastMonthlySettlementMonth: Int,
    val lastSaveTime: Long
)
