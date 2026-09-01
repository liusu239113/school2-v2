package com.arktools.xiaozhang.domain.finance

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 学校财务报表管理系统
 * - 月度/年度收支报表
 * - 收入来源分析（学费、研究、活动、赞助等）
 * - 支出分类分析（教师薪资、设施维护、活动经费等）
 * - 财务健康指标
 * - 预算规划与执行
 * - 历史趋势分析
 */
@Singleton
class FinancialReportManager @Inject constructor() {

    private val _state = MutableStateFlow(FinancialState())
    val state: StateFlow<FinancialState> = _state.asStateFlow()

    fun reset() {
        _state.value = FinancialState()
    }

    companion object {
        const val MAX_MONTHLY_RECORDS = 24
        const val MAX_YEARLY_RECORDS = 10
    }

    /**
     * 记录收入
     */
    fun recordIncome(category: IncomeCategory, amount: Double, description: String = "") {
        if (amount <= 0) return
        _state.update { state ->
            val currentMonth = state.currentMonthReport
            val updatedIncomes = currentMonth.incomes.toMutableMap()
            updatedIncomes[category] = (updatedIncomes[category] ?: 0.0) + amount
            state.copy(
                currentMonthReport = currentMonth.copy(
                    incomes = updatedIncomes,
                    totalIncome = updatedIncomes.values.sum()
                )
            )
        }
    }

    /**
     * 记录支出
     */
    fun recordExpense(category: ExpenseCategory, amount: Double, description: String = "") {
        if (amount <= 0) return
        _state.update { state ->
            val currentMonth = state.currentMonthReport
            val updatedExpenses = currentMonth.expenses.toMutableMap()
            updatedExpenses[category] = (updatedExpenses[category] ?: 0.0) + amount
            state.copy(
                currentMonthReport = currentMonth.copy(
                    expenses = updatedExpenses,
                    totalExpense = updatedExpenses.values.sum()
                )
            )
        }
    }

    /**
     * 设置预算
     */
    fun setBudget(category: ExpenseCategory, amount: Double) {
        _state.update { state ->
            val updatedBudgets = state.budgets.toMutableMap()
            updatedBudgets[category] = amount
            state.copy(budgets = updatedBudgets)
        }
    }

    /**
     * 月末结算 - 归档当月报表，开始新月
     */
    fun closeMonth(year: Int, month: Int, cashBalance: Double) {
        _state.update { state ->
            val report = state.currentMonthReport.copy(
                year = year,
                month = month,
                netProfit = state.currentMonthReport.totalIncome - state.currentMonthReport.totalExpense,
                cashBalance = cashBalance
            )

            val updatedHistory = (listOf(report) + state.monthlyHistory).take(MAX_MONTHLY_RECORDS)

            // 计算财务健康指标
            val healthScore = calculateHealthScore(report, cashBalance)

            // 如果是12月，生成年度报表
            val updatedYearly = if (month == 12) {
                val yearlyReport = generateYearlyReport(year, updatedHistory)
                (listOf(yearlyReport) + state.yearlyReports).take(MAX_YEARLY_RECORDS)
            } else {
                state.yearlyReports
            }

            // 计算趋势
            val incomeTrend = calculateTrend(updatedHistory.map { it.totalIncome })
            val expenseTrend = calculateTrend(updatedHistory.map { it.totalExpense })
            val profitTrend = calculateTrend(updatedHistory.map { it.netProfit })

            state.copy(
                currentMonthReport = MonthlyReport(),
                monthlyHistory = updatedHistory,
                yearlyReports = updatedYearly,
                financialHealth = healthScore,
                incomeTrend = incomeTrend,
                expenseTrend = expenseTrend,
                profitTrend = profitTrend
            )
        }
    }

    /**
     * 获取预算执行情况
     */
    fun getBudgetExecution(): Map<ExpenseCategory, BudgetExecution> {
        val state = _state.value
        val result = mutableMapOf<ExpenseCategory, BudgetExecution>()
        state.budgets.forEach { (category, budget) ->
            val spent = state.currentMonthReport.expenses[category] ?: 0.0
            result[category] = BudgetExecution(
                category = category,
                budgeted = budget,
                spent = spent,
                remaining = budget - spent,
                executionRate = if (budget > 0) (spent / budget * 100).coerceAtMost(200.0) else 0.0
            )
        }
        return result
    }

    /**
     * 获取有效月报（当月有数据则用当月，否则用最近归档月报）
     */
    private fun getEffectiveReport(): MonthlyReport {
        val state = _state.value
        val current = state.currentMonthReport
        return if (current.totalIncome > 0 || current.totalExpense > 0) {
            current
        } else {
            state.monthlyHistory.firstOrNull() ?: current
        }
    }

    /**
     * 获取收入构成分析
     */
    fun getIncomeBreakdown(): List<CategoryBreakdown> {
        val report = getEffectiveReport()
        val total = report.totalIncome.coerceAtLeast(1.0)
        return report.incomes.map { (category, amount) ->
            CategoryBreakdown(
                name = category.displayName,
                amount = amount,
                percentage = amount / total * 100
            )
        }.sortedByDescending { it.amount }
    }

    /**
     * 获取支出构成分析
     */
    fun getExpenseBreakdown(): List<CategoryBreakdown> {
        val report = getEffectiveReport()
        val total = report.totalExpense.coerceAtLeast(1.0)
        return report.expenses.map { (category, amount) ->
            CategoryBreakdown(
                name = category.displayName,
                amount = amount,
                percentage = amount / total * 100
            )
        }.sortedByDescending { it.amount }
    }

    private fun calculateHealthScore(report: MonthlyReport, cashBalance: Double): FinancialHealth {
        val profitMargin = if (report.totalIncome > 0) {
            report.netProfit / report.totalIncome
        } else -1.0

        val score = when {
            cashBalance <= 0 -> 10f
            profitMargin < -0.3 -> 25f
            profitMargin < -0.1 -> 40f
            profitMargin < 0 -> 55f
            profitMargin < 0.1 -> 65f
            profitMargin < 0.2 -> 75f
            profitMargin < 0.3 -> 85f
            else -> 95f
        }

        val level = when {
            score >= 80f -> HealthLevel.EXCELLENT
            score >= 60f -> HealthLevel.GOOD
            score >= 40f -> HealthLevel.WARNING
            else -> HealthLevel.CRITICAL
        }

        return FinancialHealth(
            score = score,
            level = level,
            profitMargin = (profitMargin * 100).toFloat(),
            cashReserveMonths = if (report.totalExpense > 0) {
                (cashBalance / report.totalExpense).toFloat()
            } else 99f
        )
    }

    private fun generateYearlyReport(year: Int, monthlyHistory: List<MonthlyReport>): YearlyReport {
        val yearReports = monthlyHistory.filter { it.year == year }
        return YearlyReport(
            year = year,
            totalIncome = yearReports.sumOf { it.totalIncome },
            totalExpense = yearReports.sumOf { it.totalExpense },
            netProfit = yearReports.sumOf { it.netProfit },
            averageMonthlyIncome = if (yearReports.isNotEmpty()) {
                yearReports.sumOf { it.totalIncome } / yearReports.size
            } else 0.0,
            averageMonthlyExpense = if (yearReports.isNotEmpty()) {
                yearReports.sumOf { it.totalExpense } / yearReports.size
            } else 0.0,
            bestMonth = yearReports.maxByOrNull { it.netProfit },
            worstMonth = yearReports.minByOrNull { it.netProfit }
        )
    }

    private fun calculateTrend(values: List<Double>): TrendDirection {
        if (values.size < 3) return TrendDirection.STABLE
        val recent = values.take(3).average()
        val older = values.drop(3).take(3).average()
        val diff = recent - older
        return when {
            diff > older * 0.1 -> TrendDirection.UP
            diff < -older * 0.1 -> TrendDirection.DOWN
            else -> TrendDirection.STABLE
        }
    }

    fun toJson(): String {
        val state = _state.value
        val root = JSONObject()
        // current month
        root.put("current", monthlyReportToJson(state.currentMonthReport))
        // history
        val histArr = JSONArray()
        for (r in state.monthlyHistory) histArr.put(monthlyReportToJson(r))
        root.put("history", histArr)
        // yearly
        val yearArr = JSONArray()
        for (y in state.yearlyReports) {
            val yObj = JSONObject()
            yObj.put("year", y.year)
            yObj.put("totalIncome", y.totalIncome)
            yObj.put("totalExpense", y.totalExpense)
            yObj.put("netProfit", y.netProfit)
            yObj.put("avgIncome", y.averageMonthlyIncome)
            yObj.put("avgExpense", y.averageMonthlyExpense)
            yearArr.put(yObj)
        }
        root.put("yearly", yearArr)
        return root.toString()
    }

    fun restoreFromJson(json: String) {
        if (json.isBlank()) return
        try {
            val root = JSONObject(json)
            val current = jsonToMonthlyReport(root.optJSONObject("current"))
            val histArr = root.optJSONArray("history") ?: JSONArray()
            val history = mutableListOf<MonthlyReport>()
            for (i in 0 until histArr.length()) {
                history.add(jsonToMonthlyReport(histArr.getJSONObject(i)))
            }
            val yearArr = root.optJSONArray("yearly") ?: JSONArray()
            val yearly = mutableListOf<YearlyReport>()
            for (i in 0 until yearArr.length()) {
                val yObj = yearArr.getJSONObject(i)
                yearly.add(YearlyReport(
                    year = yObj.getInt("year"),
                    totalIncome = yObj.getDouble("totalIncome"),
                    totalExpense = yObj.getDouble("totalExpense"),
                    netProfit = yObj.getDouble("netProfit"),
                    averageMonthlyIncome = yObj.optDouble("avgIncome", 0.0),
                    averageMonthlyExpense = yObj.optDouble("avgExpense", 0.0),
                    bestMonth = null,
                    worstMonth = null
                ))
            }
            _state.value = FinancialState(
                currentMonthReport = current,
                monthlyHistory = history,
                yearlyReports = yearly
            )
        } catch (e: Exception) {
            throw IllegalArgumentException("FinancialReportManager.restoreFromJson failed", e)
        }
    }

    private fun monthlyReportToJson(r: MonthlyReport): JSONObject {
        val obj = JSONObject()
        obj.put("year", r.year)
        obj.put("month", r.month)
        obj.put("totalIncome", r.totalIncome)
        obj.put("totalExpense", r.totalExpense)
        obj.put("netProfit", r.netProfit)
        obj.put("cashBalance", r.cashBalance)
        val incObj = JSONObject()
        for ((k, v) in r.incomes) incObj.put(k.name, v)
        obj.put("incomes", incObj)
        val expObj = JSONObject()
        for ((k, v) in r.expenses) expObj.put(k.name, v)
        obj.put("expenses", expObj)
        return obj
    }

    private fun jsonToMonthlyReport(obj: JSONObject?): MonthlyReport {
        if (obj == null) return MonthlyReport()
        val incomes = mutableMapOf<IncomeCategory, Double>()
        val incObj = obj.optJSONObject("incomes")
        if (incObj != null) {
            for (key in incObj.keys()) {
                try { incomes[IncomeCategory.valueOf(key)] = incObj.getDouble(key) } catch (_: Exception) {}
            }
        }
        val expenses = mutableMapOf<ExpenseCategory, Double>()
        val expObj = obj.optJSONObject("expenses")
        if (expObj != null) {
            for (key in expObj.keys()) {
                try { expenses[ExpenseCategory.valueOf(key)] = expObj.getDouble(key) } catch (_: Exception) {}
            }
        }
        return MonthlyReport(
            year = obj.optInt("year", 0),
            month = obj.optInt("month", 0),
            incomes = incomes,
            expenses = expenses,
            totalIncome = obj.optDouble("totalIncome", 0.0),
            totalExpense = obj.optDouble("totalExpense", 0.0),
            netProfit = obj.optDouble("netProfit", 0.0),
            cashBalance = obj.optDouble("cashBalance", 0.0)
        )
    }
}

// ===== 数据模型 =====

data class FinancialState(
    val currentMonthReport: MonthlyReport = MonthlyReport(),
    val monthlyHistory: List<MonthlyReport> = emptyList(),
    val yearlyReports: List<YearlyReport> = emptyList(),
    val budgets: Map<ExpenseCategory, Double> = emptyMap(),
    val financialHealth: FinancialHealth = FinancialHealth(),
    val incomeTrend: TrendDirection = TrendDirection.STABLE,
    val expenseTrend: TrendDirection = TrendDirection.STABLE,
    val profitTrend: TrendDirection = TrendDirection.STABLE
)

data class MonthlyReport(
    val year: Int = 0,
    val month: Int = 0,
    val incomes: Map<IncomeCategory, Double> = emptyMap(),
    val expenses: Map<ExpenseCategory, Double> = emptyMap(),
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val netProfit: Double = 0.0,
    val cashBalance: Double = 0.0
)

data class YearlyReport(
    val year: Int,
    val totalIncome: Double,
    val totalExpense: Double,
    val netProfit: Double,
    val averageMonthlyIncome: Double,
    val averageMonthlyExpense: Double,
    val bestMonth: MonthlyReport?,
    val worstMonth: MonthlyReport?
)

enum class IncomeCategory(val displayName: String, val color: Long) {
    TUITION("学费收入", 0xFF4CAF50),
    RESEARCH_GRANT("研究经费", 0xFF2196F3),
    GOVERNMENT_SUBSIDY("政府补贴", 0xFF9C27B0),
    DONATION("社会捐赠", 0xFFFF9800),
    EVENT_REVENUE("活动收入", 0xFF00BCD4),
    FACILITY_RENTAL("设施租借", 0xFF795548),
    ALUMNI_CONTRIBUTION("校友贡献", 0xFFE91E63),
    OTHER_INCOME("其他收入", 0xFF607D8B)
}

enum class ExpenseCategory(val displayName: String, val color: Long) {
    TEACHER_SALARY("教师薪资", 0xFFF44336),
    FACILITY_MAINTENANCE("设施维护", 0xFFFF9800),
    RESEARCH_FUNDING("研究投入", 0xFF2196F3),
    ACTIVITY_COST("活动经费", 0xFF9C27B0),
    TRAINING_COST("培训费用", 0xFF00BCD4),
    EQUIPMENT("设备采购", 0xFF795548),
    UTILITIES("水电物业", 0xFF607D8B),
    LIFE_SERVICE("生活服务", 0xFF009688),
    MARKETING("招生宣传", 0xFFE91E63),
    EXPANSION("校区建设", 0xFF4CAF50),
    TEACHING_OPERATION("教学运营", 0xFF9B59B6),
    OTHER_EXPENSE("其他支出", 0xFF9E9E9E)
}

data class FinancialHealth(
    val score: Float = 50f,
    val level: HealthLevel = HealthLevel.GOOD,
    val profitMargin: Float = 0f,
    val cashReserveMonths: Float = 0f
)

enum class HealthLevel(val displayName: String, val color: Long) {
    EXCELLENT("优秀", 0xFF4CAF50),
    GOOD("良好", 0xFF8BC34A),
    WARNING("警告", 0xFFFF9800),
    CRITICAL("危险", 0xFFF44336)
}

enum class TrendDirection(val displayName: String, val icon: String) {
    UP("上升", "📈"),
    DOWN("下降", "📉"),
    STABLE("平稳", "➡️")
}

data class BudgetExecution(
    val category: ExpenseCategory,
    val budgeted: Double,
    val spent: Double,
    val remaining: Double,
    val executionRate: Double
)

data class CategoryBreakdown(
    val name: String,
    val amount: Double,
    val percentage: Double
)
