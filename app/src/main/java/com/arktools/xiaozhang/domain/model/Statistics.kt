package com.arktools.xiaozhang.domain.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Monthly statistics snapshot for tracking school performance over time.
 * Used for charts, reports, and trend analysis.
 */
data class MonthlyReport(
    val year: Int,
    val month: Int,
    val revenue: Double,
    val expenses: Double,
    val profit: Double,
    val enrollment: Long,
    val reputation: Long,
    val teacherCount: Int,
    val courseCount: Int,
    val averageCourseQuality: Float,
    val averageTeacherSatisfaction: Float,
    val cashBalance: Double
) {
    val isProfit: Boolean get() = profit > 0
}

/**
 * Aggregated school statistics for reporting.
 */
data class SchoolReport(
    val totalRevenue: Double,
    val totalExpenses: Double,
    val totalProfit: Double,
    val totalStudentsServed: Long,
    val averageMonthlyRevenue: Double,
    val averageMonthlyExpenses: Double,
    val peakEnrollment: Long,
    val peakRevenue: Double,
    val totalCoursesCreated: Int,
    val totalTeachersHired: Int,
    val monthsOperating: Int,
    val profitableMonths: Int,
    val lossMonths: Int,
    val growthRate: Float,  // % monthly growth
    val currentSeason: String,
    val recentMonths: List<MonthlyReport>
)

/**
 * Manages monthly statistics collection and reporting.
 */
object StatisticsManager {

    private val monthlyHistory = mutableListOf<MonthlyReport>()
    private var totalExpensesAccumulated = 0.0

    fun recordMonth(
        school: School,
        monthlyRevenue: Double,
        monthlyExpenses: Double,
        enrollment: Long,
        teacherCount: Int,
        courseCount: Int,
        avgQuality: Float,
        avgSatisfaction: Float
    ) {
        val report = MonthlyReport(
            year = school.currentYear,
            month = school.currentMonth,
            revenue = monthlyRevenue,
            expenses = monthlyExpenses,
            profit = monthlyRevenue - monthlyExpenses,
            enrollment = enrollment,
            reputation = school.reputation,
            teacherCount = teacherCount,
            courseCount = courseCount,
            averageCourseQuality = avgQuality,
            averageTeacherSatisfaction = avgSatisfaction,
            cashBalance = school.cash
        )
        monthlyHistory.add(report)
        totalExpensesAccumulated += monthlyExpenses

        // Keep last 120 months (10 years) of history
        if (monthlyHistory.size > 120) {
            monthlyHistory.removeAt(0)
        }
    }

    fun generateReport(school: School): SchoolReport {
        val totalRevenue = monthlyHistory.sumOf { it.revenue }
        val totalExpenses = monthlyHistory.sumOf { it.expenses }
        val totalProfit = totalRevenue - totalExpenses
        val totalStudents = monthlyHistory.sumOf { it.enrollment }

        val avgRevenue = if (monthlyHistory.isNotEmpty()) totalRevenue / monthlyHistory.size else 0.0
        val avgExpenses = if (monthlyHistory.isNotEmpty()) totalExpenses / monthlyHistory.size else 0.0

        val peakEnrollment = monthlyHistory.maxOfOrNull { it.enrollment } ?: 0L
        val peakRevenue = monthlyHistory.maxOfOrNull { it.revenue } ?: 0.0

        val profitableMonths = monthlyHistory.count { it.isProfit }
        val lossMonths = monthlyHistory.count { !it.isProfit }

        // Calculate growth rate (last 3 months vs previous 3 months)
        val growthRate = calculateGrowthRate()

        val currentSeason = com.arktools.xiaozhang.domain.engine.SemesterCalendar.getSeason(school.currentMonth).displayName

        return SchoolReport(
            totalRevenue = totalRevenue,
            totalExpenses = totalExpenses,
            totalProfit = totalProfit,
            totalStudentsServed = totalStudents,
            averageMonthlyRevenue = avgRevenue,
            averageMonthlyExpenses = avgExpenses,
            peakEnrollment = peakEnrollment,
            peakRevenue = peakRevenue,
            totalCoursesCreated = school.totalCoursesReleased,
            totalTeachersHired = 0, // can be tracked separately
            monthsOperating = monthlyHistory.size,
            profitableMonths = profitableMonths,
            lossMonths = lossMonths,
            growthRate = growthRate,
            currentSeason = currentSeason,
            recentMonths = monthlyHistory.takeLast(12)
        )
    }

    private fun calculateGrowthRate(): Float {
        if (monthlyHistory.size < 6) return 0f

        val recent3 = monthlyHistory.takeLast(3).sumOf { it.revenue }
        val previous3 = monthlyHistory.dropLast(3).takeLast(3).sumOf { it.revenue }

        return if (previous3 > 0) {
            ((recent3 - previous3) / previous3 * 100).toFloat()
        } else 0f
    }

    fun getRecentMonths(count: Int): List<MonthlyReport> {
        return monthlyHistory.takeLast(count)
    }

    fun reset() {
        monthlyHistory.clear()
        totalExpensesAccumulated = 0.0
    }

    fun toJson(): String {
        val arr = JSONArray()
        for (r in monthlyHistory) {
            val obj = JSONObject()
            obj.put("year", r.year)
            obj.put("month", r.month)
            obj.put("revenue", r.revenue)
            obj.put("expenses", r.expenses)
            obj.put("profit", r.profit)
            obj.put("enrollment", r.enrollment)
            obj.put("reputation", r.reputation)
            obj.put("teacherCount", r.teacherCount)
            obj.put("courseCount", r.courseCount)
            obj.put("avgQuality", r.averageCourseQuality.toDouble())
            obj.put("avgSatisfaction", r.averageTeacherSatisfaction.toDouble())
            obj.put("cashBalance", r.cashBalance)
            arr.put(obj)
        }
        return arr.toString()
    }

    fun restoreFromJson(json: String) {
        if (json.isBlank()) return
        try {
            val arr = JSONArray(json)
            val restoredHistory = buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        MonthlyReport(
                            year = obj.getInt("year"),
                            month = obj.getInt("month"),
                            revenue = obj.getDouble("revenue"),
                            expenses = obj.getDouble("expenses"),
                            profit = obj.getDouble("profit"),
                            enrollment = obj.getLong("enrollment"),
                            reputation = obj.getLong("reputation"),
                            teacherCount = obj.optInt("teacherCount", 0),
                            courseCount = obj.optInt("courseCount", 0),
                            averageCourseQuality = obj.optDouble("avgQuality", 0.0).toFloat(),
                            averageTeacherSatisfaction = obj.optDouble("avgSatisfaction", 0.0).toFloat(),
                            cashBalance = obj.optDouble("cashBalance", 0.0)
                        )
                    )
                }
            }
            monthlyHistory.clear()
            monthlyHistory.addAll(restoredHistory)
            totalExpensesAccumulated = restoredHistory.sumOf { it.expenses }
        } catch (e: Exception) {
            throw IllegalArgumentException("Statistics.restoreFromJson failed", e)
        }
    }
}
