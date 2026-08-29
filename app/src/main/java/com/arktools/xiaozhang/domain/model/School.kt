package com.arktools.xiaozhang.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

data class School(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "新星学校",
    val principalName: String = "张校长",
    var tierKey: String = com.arktools.xiaozhang.domain.model.SchoolTier.APPLIED.key,       // 办学层次（专科/本科）
    var ownershipKey: String = com.arktools.xiaozhang.domain.model.SchoolOwnership.PRIVATE.key, // 办学性质（公办/民办）
    var promotionHistoryJson: String = "",  // 升格史（SchoolPromotionRecord 列表）
    var cash: Double = com.arktools.xiaozhang.domain.engine.GameBalanceConfig.INITIAL_CASH,
    var marketCap: Double = 100.0,
    var reputation: Long = com.arktools.xiaozhang.domain.engine.GameBalanceConfig.INITIAL_REPUTATION,
    var starRating: Float = 0f,
    val foundedYear: Int = com.arktools.xiaozhang.domain.engine.GameBalanceConfig.STARTING_YEAR,
    var currentYear: Int = com.arktools.xiaozhang.domain.engine.GameBalanceConfig.STARTING_YEAR,
    var currentMonth: Int = 8,  // 8月建校，9月开学招生
    var currentDay: Int = 1,
    var campusLevel: Int = com.arktools.xiaozhang.domain.engine.GameBalanceConfig.INITIAL_CAMPUS_LEVEL,
    var levelUpYear: Int = com.arktools.xiaozhang.domain.engine.GameBalanceConfig.STARTING_YEAR, // 上次升级的年份（计算在当前等级的运营年数）
    var maxTeachers: Int = com.arktools.xiaozhang.domain.engine.GameBalanceConfig.INITIAL_MAX_TEACHERS,
    var branchSchools: Int = 0,
    var hasOwnTextbook: Boolean = false,
    var hasOwnTech: Boolean = false,
    var totalCoursesReleased: Int = 0,
    var totalRevenue: Double = 0.0,
    var wasNearBankrupt: Boolean = false,
    var facilities: MutableList<Facility> = mutableListOf(),
    var studentLifeJson: String = "",
    var marketingCampaigns: MutableList<MarketingCampaign> = mutableListOf(),
    var stockInvestments: List<StockInvestment> = emptyList(),
    var reputationJson: String = "",
    var achievementJson: String = "",
    var milestoneJson: String = "",
    var teacherDevJson: String = "",
    var clubJson: String = "",
    var scholarshipJson: String = "",
    var expansionJson: String = "",
    var governmentJson: String = "",
    var parentJson: String = "",
    var policyJson: String = "",
    var seasonalJson: String = "",
    var conferenceJson: String = "",
    var clubActivityJson: String = "",
    var timetableJson: String = "",
    var examJson: String = "",
    var teachingConfigJson: String = "",
    var statisticsJson: String = "",
    var financialReportJson: String = "",
    var pressureJson: String = "",
    var competitorJson: String = "",
    var crisisJson: String = "",
    var alumniJson: String = "",
    var employmentJson: String = "",
    var headTeacherMapJson: String = "",  // classId -> teacherId 映射
    var classTierMapJson: String = "",   // classId -> ClassTier.name 映射（持久化班型）
    var principalJson: String = "",       // 校长个人系统状态（贪污、人脉、派系等）
    var suggestionBoxJson: String = "",
    var lastYearEndProcessingYear: Int =
        com.arktools.xiaozhang.domain.engine.GameBalanceConfig.STARTING_YEAR,
    var lastMonthlySettlementYear: Int =
        com.arktools.xiaozhang.domain.engine.GameBalanceConfig.STARTING_YEAR,
    var lastMonthlySettlementMonth: Int = 8,
    var lastSaveTime: Long = System.currentTimeMillis()
)

@Serializable
data class StockInvestment(
    val companyName: String,
    val shares: Int,
    val buyPrice: Double,
    var currentPrice: Double
)

data class SchoolStats(
    val cash: Double,
    val marketCap: Double,
    val reputation: Long,
    val starRating: Float,
    val currentYear: Int,
    val currentMonth: Int,
    val currentDay: Int,
    val campusLevel: Int,
    val maxTeachers: Int,
    val teacherCount: Int,
    val activeCourseCount: Int,
    val releasedCourseCount: Int
)
