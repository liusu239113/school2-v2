package com.arktools.xiao.ui.report

import androidx.lifecycle.ViewModel
import com.arktools.xiao.domain.finance.*
import com.arktools.xiao.domain.model.MonthlyReport
import com.arktools.xiao.domain.model.StatisticsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class ReportUiState(
    val recentMonths: List<MonthlyReport> = emptyList(),
    val hasData: Boolean = false,
    val selectedChart: ChartType = ChartType.REVENUE,
    // 增长率
    val revenueGrowth: Float? = null,
    val enrollmentGrowth: Float? = null,
    val reputationGrowth: Float? = null,
    val profitGrowth: Float? = null
)

enum class ChartType(val displayName: String, val emoji: String) {
    REVENUE("收支", "收"),
    ENROLLMENT("招生", "生"),
    REPUTATION("声誉", "誉"),
    PROFIT("利润", "利"),
    QUALITY("教学质量", "教"),
    TEACHER_SATISFACTION("师资满意度", "师"),
    CASH_BALANCE("现金余额", "余")
}

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val financialReportManager: FinancialReportManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    // 财务状态
    val financialState: StateFlow<FinancialState> = financialReportManager.state

    init {
        refresh()
    }

    fun refresh() {
        val months = StatisticsManager.getRecentMonths(12)
        val growths = calculateGrowths(months)
        _uiState.value = ReportUiState(
            recentMonths = months,
            hasData = months.isNotEmpty(),
            revenueGrowth = growths.first,
            enrollmentGrowth = growths.second,
            reputationGrowth = growths.third,
            profitGrowth = growths.fourth
        )
    }

    fun selectChart(type: ChartType) {
        _uiState.value = _uiState.value.copy(selectedChart = type)
    }

    // === 财务管理功能 ===

    fun setBudget(category: ExpenseCategory, amount: Double) {
        financialReportManager.setBudget(category, amount)
    }

    fun getBudgetExecution(): Map<ExpenseCategory, BudgetExecution> {
        return financialReportManager.getBudgetExecution()
    }

    fun getIncomeBreakdown(): List<CategoryBreakdown> {
        return financialReportManager.getIncomeBreakdown()
    }

    fun getExpenseBreakdown(): List<CategoryBreakdown> {
        return financialReportManager.getExpenseBreakdown()
    }

    // === 数据可视化辅助 ===

    /**
     * 雷达图数据：学校综合维度评分 (0-100)
     * 维度：财务、声誉、招生、教学质量、师资满意度
     */
    fun getRadarData(): RadarChartData {
        val months = _uiState.value.recentMonths
        if (months.isEmpty()) return RadarChartData()
        val latest = months.last()

        // 财务评分：利润率映射 [-50%~100%] → [0~100]
        val profitRate = if (latest.revenue > 0) (latest.profit / latest.revenue * 100) else 0.0
        val financeScore = ((profitRate + 50) / 150 * 100).coerceIn(0.0, 100.0).toFloat()

        // 声誉评分：对数映射 [0~50000] → [0~100]
        val reputationScore = (kotlin.math.ln(latest.reputation.toDouble().coerceAtLeast(1.0)) / kotlin.math.ln(50000.0) * 100)
            .coerceIn(0.0, 100.0).toFloat()

        // 招生评分：映射 [0~500] → [0~100]
        val enrollmentScore = (latest.enrollment.toFloat() / 500f * 100f).coerceIn(0f, 100f)

        // 教学质量：直接百分比
        val qualityScore = latest.averageCourseQuality.coerceIn(0f, 100f)

        // 师资满意度：直接百分比
        val satisfactionScore = latest.averageTeacherSatisfaction.coerceIn(0f, 100f)

        return RadarChartData(
            finance = financeScore,
            reputation = reputationScore,
            enrollment = enrollmentScore,
            quality = qualityScore,
            satisfaction = satisfactionScore
        )
    }

    /**
     * 柱状图数据：近N月收支对比
     */
    fun getBarChartData(count: Int = 6): List<BarChartItem> {
        val months = _uiState.value.recentMonths.takeLast(count)
        return months.map { report ->
            BarChartItem(
                label = "${report.month}月",
                income = report.revenue,
                expense = report.expenses
            )
        }
    }

    /**
     * 环形图数据：支出占比
     */
    fun getExpenseDonutData(): List<DonutSegment> {
        val breakdown = financialReportManager.getExpenseBreakdown()
        if (breakdown.isEmpty()) {
            // 从月报数据估算
            val months = _uiState.value.recentMonths
            if (months.isEmpty()) return emptyList()
            val latest = months.last()
            // 固定估算比例
            return listOf(
                DonutSegment("教师薪资", 55f, 0xFF4CAF50.toInt()),
                DonutSegment("设施维护", 20f, 0xFF2196F3.toInt()),
                DonutSegment("教学经费", 15f, 0xFFFF9800.toInt()),
                DonutSegment("其他", 10f, 0xFF9E9E9E.toInt())
            )
        }
        val colors = listOf(
            0xFF4CAF50.toInt(), 0xFF2196F3.toInt(), 0xFFFF9800.toInt(),
            0xFF9C27B0.toInt(), 0xFFF44336.toInt(), 0xFF00BCD4.toInt()
        )
        return breakdown.mapIndexed { index, item ->
            DonutSegment(
                label = item.name,
                percentage = item.percentage.toFloat(),
                color = colors[index % colors.size]
            )
        }
    }

    // === 私有辅助 ===

    private data class GrowthData(
        val first: Float?,
        val second: Float?,
        val third: Float?,
        val fourth: Float?
    )

    private fun calculateGrowths(months: List<MonthlyReport>): GrowthData {
        if (months.size < 2) return GrowthData(null, null, null, null)
        val current = months.last()
        val prev = months[months.size - 2]

        fun growthRate(cur: Double, pre: Double): Float? {
            if (pre == 0.0) return if (cur > 0) 100f else null
            return ((cur - pre) / pre * 100).toFloat()
        }

        return GrowthData(
            first = growthRate(current.revenue, prev.revenue),
            second = growthRate(current.enrollment.toDouble(), prev.enrollment.toDouble()),
            third = growthRate(current.reputation.toDouble(), prev.reputation.toDouble()),
            fourth = growthRate(current.profit, prev.profit)
        )
    }
}

// === 可视化数据模型 ===

data class RadarChartData(
    val finance: Float = 0f,
    val reputation: Float = 0f,
    val enrollment: Float = 0f,
    val quality: Float = 0f,
    val satisfaction: Float = 0f
) {
    val dimensions: List<Pair<String, Float>>
        get() = listOf(
            "财务" to finance,
            "声誉" to reputation,
            "招生" to enrollment,
            "教学" to quality,
            "师资" to satisfaction
        )

    val average: Float
        get() = (finance + reputation + enrollment + quality + satisfaction) / 5f
}

data class BarChartItem(
    val label: String,
    val income: Double,
    val expense: Double
)

data class DonutSegment(
    val label: String,
    val percentage: Float,
    val color: Int
)
