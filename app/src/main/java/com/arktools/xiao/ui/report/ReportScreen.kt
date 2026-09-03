package com.arktools.xiao.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.arktools.xiao.R
import com.arktools.xiao.domain.finance.*
import com.arktools.xiao.domain.finance.MonthlyReport as FinanceMonthlyReport
import com.arktools.xiao.domain.model.MonthlyReport
import com.arktools.xiao.ui.components.PixelButton
import com.arktools.xiao.ui.components.PixelButtonStyle
import com.arktools.xiao.ui.components.PixelIcon
import com.arktools.xiao.ui.theme.AccentGreen
import com.arktools.xiao.ui.theme.AccentOrange
import com.arktools.xiao.ui.theme.AccentRed
import com.arktools.xiao.ui.utils.FormatUtils

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportScreen(
    viewModel: ReportViewModel = hiltViewModel()
) {
    // 每次页面显示时刷新数据，确保最新统计可见
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    val state by viewModel.uiState.collectAsState()
    val finState by viewModel.financialState.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部 Tab 切换
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = { Text("数据趋势") },
                icon = { Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = { Text("财务管理") },
                icon = { Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        when (selectedTabIndex) {
            0 -> DataTrendContent(state = state, viewModel = viewModel)
            1 -> FinanceContent(finState = finState, viewModel = viewModel)
        }
    }
}

// ========== 数据趋势页 ==========

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DataTrendContent(
    state: ReportUiState,
    viewModel: ReportViewModel
) {
    if (!state.hasData) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PixelIcon(emoji = "📊", size = 48.dp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "暂无数据",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "经营满一个月后开始记录数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { GrowthSummaryRow(state = state) }
        // 雷达图：综合评分
        item { RadarChartCard(data = viewModel.getRadarData()) }
        item {
            ChartTypeSelector(
                selectedType = state.selectedChart,
                onTypeSelected = { viewModel.selectChart(it) }
            )
        }
        item { ChartCard(months = state.recentMonths, chartType = state.selectedChart) }
        // 柱状图：收支对比
        item { BarChartCard(data = viewModel.getBarChartData()) }
        // 环形图：支出构成
        item { DonutChartCard(data = viewModel.getExpenseDonutData()) }
        item { SummaryCard(months = state.recentMonths) }
        item { MonthlyBreakdownCard(months = state.recentMonths) }
    }
}

// ========== 财务管理页 ==========

@Composable
private fun FinanceContent(
    finState: FinancialState,
    viewModel: ReportViewModel
) {
    var showBudgetDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 财务健康概览
        item { FinancialHealthCard(finState) }

        // 本月收支摘要（若当月为空则显示最近一次月报）
        item {
            val displayReport = if (finState.currentMonthReport.totalIncome > 0 || finState.currentMonthReport.totalExpense > 0) {
                finState.currentMonthReport
            } else {
                finState.monthlyHistory.firstOrNull() ?: finState.currentMonthReport
            }
            MonthFinanceSummaryCard(
                displayReport,
                finState.incomeTrend,
                finState.expenseTrend,
                finState.profitTrend
            )
        }

        // 预算管理按钮
        item {
            PixelButton(
                text = "预算管理",
                style = PixelButtonStyle.PRIMARY,
                onClick = { showBudgetDialog = true },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 收入构成
        item { Text("收入来源", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        item { IncomeBreakdownCard(viewModel.getIncomeBreakdown()) }

        // 支出构成
        item { Text("支出分类", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        item { ExpenseBreakdownCard(viewModel.getExpenseBreakdown()) }

        // 预算执行
        val budgetExecution = viewModel.getBudgetExecution()
        if (budgetExecution.isNotEmpty()) {
            item { Text("预算执行", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            items(budgetExecution.values.toList()) { exec ->
                BudgetExecutionCard(exec)
            }
        }

        // 年度报表
        if (finState.yearlyReports.isNotEmpty()) {
            item { Text("年度报表", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            items(finState.yearlyReports) { report ->
                YearlyReportCard(report)
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }

    if (showBudgetDialog) {
        BudgetDialog(
            currentBudgets = finState.budgets,
            onConfirm = { budgets ->
                budgets.forEach { (category, amount) ->
                    viewModel.setBudget(category, amount)
                }
                showBudgetDialog = false
            },
            onDismiss = { showBudgetDialog = false }
        )
    }
}

// ===== 增长率摘要 =====

@Composable
private fun GrowthSummaryRow(state: ReportUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GrowthChip(label = "收入", growth = state.revenueGrowth)
        GrowthChip(label = "招生", growth = state.enrollmentGrowth)
        GrowthChip(label = "声誉", growth = state.reputationGrowth)
        GrowthChip(label = "利润", growth = state.profitGrowth)
    }
}

@Composable
private fun GrowthChip(label: String, growth: Float?) {
    val displayGrowth = growth ?: 0f
    val isPositive = displayGrowth >= 0
    val color = if (isPositive) AccentGreen else AccentRed

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                contentDescription = null,
                tint = color,
                modifier = Modifier.height(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (growth != null) "${if (isPositive) "+" else ""}${String.format("%.1f", displayGrowth)}%" else "—",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }
    }
}

// ===== 图表类型选择器 =====

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChartTypeSelector(
    selectedType: ChartType,
    onTypeSelected: (ChartType) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ChartType.entries.forEach { type ->
            val chipColor = when (type) {
                ChartType.REVENUE -> AccentGreen
                ChartType.ENROLLMENT -> Color(0xFF2196F3)
                ChartType.REPUTATION -> AccentOrange
                ChartType.PROFIT -> Color(0xFF9C27B0)
                ChartType.QUALITY -> Color(0xFF00BCD4)
                ChartType.TEACHER_SATISFACTION -> Color(0xFFFF9800)
                ChartType.CASH_BALANCE -> Color(0xFF607D8B)
            }
            FilterChip(
                selected = selectedType == type,
                onClick = { onTypeSelected(type) },
                label = { Text(type.displayName, style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = chipColor.copy(alpha = 0.2f)
                )
            )
        }
    }
}

// ===== 图表卡片 =====

@Composable
private fun ChartCard(months: List<MonthlyReport>, chartType: ChartType) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val title = when (chartType) {
                ChartType.REVENUE -> "收入/支出趋势"
                ChartType.ENROLLMENT -> "学生人数趋势"
                ChartType.REPUTATION -> "声誉趋势"
                ChartType.PROFIT -> "月利润趋势"
                ChartType.QUALITY -> "教学质量趋势"
                ChartType.TEACHER_SATISFACTION -> "教师满意度趋势"
                ChartType.CASH_BALANCE -> "现金余额趋势"
            }
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "近${months.size}个月",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            LineChart(
                months = months,
                chartType = chartType,
                modifier = Modifier.fillMaxWidth().height(220.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))
            when (chartType) {
                ChartType.REVENUE -> {
                    Row {
                        LegendItem(color = AccentGreen, label = "收入")
                        Spacer(modifier = Modifier.width(16.dp))
                        LegendItem(color = AccentRed, label = "支出")
                    }
                }
                ChartType.ENROLLMENT -> LegendItem(color = Color(0xFF2196F3), label = "在读学生")
                ChartType.REPUTATION -> LegendItem(color = AccentOrange, label = "声誉值")
                ChartType.PROFIT -> {
                    Row {
                        LegendItem(color = Color(0xFF9C27B0), label = "利润")
                        Spacer(modifier = Modifier.width(16.dp))
                        LegendItem(color = Color.Gray.copy(alpha = 0.5f), label = "零线")
                    }
                }
                ChartType.QUALITY -> LegendItem(color = Color(0xFF00BCD4), label = "平均教学质量")
                ChartType.TEACHER_SATISFACTION -> LegendItem(color = Color(0xFFFF9800), label = "教师满意度")
                ChartType.CASH_BALANCE -> LegendItem(color = Color(0xFF607D8B), label = "现金余额")
            }
        }
    }
}

// ===== 折线图 =====

@Composable
private fun LineChart(months: List<MonthlyReport>, chartType: ChartType, modifier: Modifier = Modifier) {
    if (months.isEmpty()) return

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val paddingLeft = 60f
        val paddingBottom = 28f
        val paddingTop = 8f
        val chartWidth = width - paddingLeft
        val chartHeight = height - paddingBottom - paddingTop

        val (dataLines, minVal, maxVal) = getChartData(months, chartType)
        val range = (maxVal - minVal).coerceAtLeast(1.0)

        val yLabelPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 20f
            textAlign = android.graphics.Paint.Align.RIGHT
        }

        for (i in 0..4) {
            val fraction = i.toFloat() / 4f
            val y = paddingTop + chartHeight * (1f - fraction)
            val value = minVal + range * fraction
            drawLine(
                color = Color.Gray.copy(alpha = 0.12f),
                start = Offset(paddingLeft, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
            drawContext.canvas.nativeCanvas.drawText(
                formatYLabel(value, chartType),
                paddingLeft - 8f,
                y + 6f,
                yLabelPaint
            )
        }

        if (chartType == ChartType.PROFIT && minVal < 0 && maxVal > 0) {
            val zeroY = paddingTop + chartHeight * (1f - (-minVal / range).toFloat())
            drawLine(
                color = Color.Gray.copy(alpha = 0.4f),
                start = Offset(paddingLeft, zeroY),
                end = Offset(width, zeroY),
                strokeWidth = 1.5f
            )
        }

        dataLines.forEach { (data, color) ->
            drawDataLine(data, minVal, range, chartWidth, chartHeight, paddingLeft, paddingTop, color)
        }

        val xLabelPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 22f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val step = when {
            months.size > 8 -> 3
            months.size > 5 -> 2
            else -> 1
        }
        for (i in months.indices step step) {
            val x = paddingLeft + chartWidth * i / (months.size - 1).coerceAtLeast(1)
            drawContext.canvas.nativeCanvas.drawText("${months[i].month}月", x, height - 2f, xLabelPaint)
        }
    }
}

private data class ChartDataResult(
    val lines: List<Pair<List<Double>, Color>>,
    val minVal: Double,
    val maxVal: Double
)

private fun getChartData(months: List<MonthlyReport>, chartType: ChartType): ChartDataResult {
    return when (chartType) {
        ChartType.REVENUE -> {
            val rev = months.map { it.revenue }
            val exp = months.map { it.expenses }
            val maxV = maxOf(rev.maxOrNull() ?: 0.0, exp.maxOrNull() ?: 0.0)
            ChartDataResult(listOf(rev to AccentGreen, exp to AccentRed), 0.0, maxV.coerceAtLeast(1.0))
        }
        ChartType.ENROLLMENT -> {
            val data = months.map { it.enrollment.toDouble() }
            ChartDataResult(listOf(data to Color(0xFF2196F3)), 0.0, (data.maxOrNull() ?: 1.0).coerceAtLeast(1.0))
        }
        ChartType.REPUTATION -> {
            val data = months.map { it.reputation.toDouble() }
            ChartDataResult(listOf(data to AccentOrange), 0.0, (data.maxOrNull() ?: 1.0).coerceAtLeast(1.0))
        }
        ChartType.PROFIT -> {
            val data = months.map { it.profit }
            val minV = (data.minOrNull() ?: 0.0)
            val maxV = (data.maxOrNull() ?: 0.0)
            ChartDataResult(
                listOf(data to Color(0xFF9C27B0)),
                if (minV < 0) minV * 1.1 else 0.0,
                maxV.coerceAtLeast(1.0) * 1.1
            )
        }
        ChartType.QUALITY -> {
            val data = months.map { it.averageCourseQuality.toDouble() }
            ChartDataResult(listOf(data to Color(0xFF00BCD4)), 0.0, 100.0)
        }
        ChartType.TEACHER_SATISFACTION -> {
            val data = months.map { it.averageTeacherSatisfaction.toDouble() }
            ChartDataResult(listOf(data to Color(0xFFFF9800)), 0.0, 100.0)
        }
        ChartType.CASH_BALANCE -> {
            val data = months.map { it.cashBalance }
            val minV = (data.minOrNull() ?: 0.0)
            val maxV = (data.maxOrNull() ?: 0.0)
            ChartDataResult(
                listOf(data to Color(0xFF607D8B)),
                if (minV < 0) minV * 1.1 else 0.0,
                maxV.coerceAtLeast(1.0) * 1.05
            )
        }
    }
}

private fun formatYLabel(value: Double, chartType: ChartType): String {
    return when (chartType) {
        ChartType.QUALITY, ChartType.TEACHER_SATISFACTION -> "${value.toInt()}%"
        ChartType.ENROLLMENT -> "${value.toLong()}"
        ChartType.REPUTATION -> {
            if (value >= 10000) "${String.format("%.0f", value / 10000)}万"
            else if (value >= 1000) "${String.format("%.1f", value / 1000)}k"
            else "${value.toLong()}"
        }
        else -> {
            if (kotlin.math.abs(value) >= 1000000) "${String.format("%.1f", value / 1000000)}M"
            else if (kotlin.math.abs(value) >= 1000) "${String.format("%.0f", value / 1000)}k"
            else "${value.toLong()}"
        }
    }
}

private fun DrawScope.drawDataLine(
    data: List<Double>, minVal: Double, range: Double,
    chartWidth: Float, chartHeight: Float, paddingLeft: Float, paddingTop: Float, color: Color
) {
    if (data.size < 2) return
    val path = Path()
    data.forEachIndexed { index, value ->
        val x = paddingLeft + chartWidth * index / (data.size - 1).coerceAtLeast(1)
        val normalizedY = ((value - minVal) / range).coerceIn(0.0, 1.0)
        val y = paddingTop + chartHeight * (1f - normalizedY.toFloat())
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path = path, color = color, style = Stroke(width = 3f, cap = StrokeCap.Round))
    data.forEachIndexed { index, value ->
        val x = paddingLeft + chartWidth * index / (data.size - 1).coerceAtLeast(1)
        val normalizedY = ((value - minVal) / range).coerceIn(0.0, 1.0)
        val y = paddingTop + chartHeight * (1f - normalizedY.toFloat())
        drawCircle(color = color, radius = 4f, center = Offset(x, y))
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.height(3.dp).width(16.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ===== 统计摘要 =====

@Composable
private fun SummaryCard(months: List<MonthlyReport>) {
    val totalRevenue = months.sumOf { it.revenue }
    val totalExpenses = months.sumOf { it.expenses }
    val totalProfit = totalRevenue - totalExpenses
    val avgEnrollment = if (months.isNotEmpty()) months.map { it.enrollment }.average().toLong() else 0L
    val avgQuality = if (months.isNotEmpty()) months.map { it.averageCourseQuality }.average() else 0.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "统计摘要", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatSummaryItem(label = "总收入", value = FormatUtils.formatCash(totalRevenue), color = AccentGreen)
                StatSummaryItem(label = "总支出", value = FormatUtils.formatCash(totalExpenses), color = AccentRed)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatSummaryItem(
                    label = "净利润",
                    value = FormatUtils.formatCash(totalProfit),
                    color = if (totalProfit >= 0) AccentGreen else AccentRed
                )
                StatSummaryItem(label = "平均招生", value = "${avgEnrollment}人", color = Color(0xFF2196F3))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatSummaryItem(
                    label = "平均教学质量",
                    value = "${String.format("%.1f", avgQuality)}%",
                    color = Color(0xFF00BCD4)
                )
                StatSummaryItem(
                    label = "利润率",
                    value = if (totalRevenue > 0) "${String.format("%.1f", totalProfit / totalRevenue * 100)}%" else "—",
                    color = if (totalProfit >= 0) AccentGreen else AccentRed
                )
            }
        }
    }
}

@Composable
private fun StatSummaryItem(label: String, value: String, color: Color) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
    }
}

// ===== 月度明细 =====

@Composable
private fun MonthlyBreakdownCard(months: List<MonthlyReport>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "月度明细", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                Text("月份", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.9f))
                Text("收入", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.1f))
                Text("支出", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.1f))
                Text("利润", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.1f))
                Text("学生", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
            }
            val reversedMonths = months.reversed()
            reversedMonths.take(8).forEachIndexed { index, report ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${report.month}月", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.9f))
                    Text(FormatUtils.formatCash(report.revenue), style = MaterialTheme.typography.bodySmall, color = AccentGreen, modifier = Modifier.weight(1.1f))
                    Text(FormatUtils.formatCash(report.expenses), style = MaterialTheme.typography.bodySmall, color = AccentRed, modifier = Modifier.weight(1.1f))
                    Text(
                        FormatUtils.formatCash(report.profit),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = if (report.profit >= 0) Color(0xFF9C27B0) else AccentRed,
                        modifier = Modifier.weight(1.1f)
                    )
                    Text("${report.enrollment}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.7f))
                }
            }
        }
    }
}

// ===== 财务健康卡片 =====

@Composable
private fun FinancialHealthCard(state: FinancialState) {
    val health = state.financialHealth
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF2E7D32), Color(0xFF1B5E20))))
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("财务健康", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    AssistChip(
                        onClick = {},
                        label = { Text(health.level.displayName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(health.level.color).copy(alpha = 0.8f)),
                        border = null
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    HealthStat("健康评分", "${health.score.toInt()}/100", Color.White)
                    HealthStat("利润率", "${health.profitMargin.toInt()}%",
                        if (health.profitMargin >= 0) Color(0xFFA5D6A7) else Color(0xFFEF9A9A))
                    HealthStat("现金储备", "${health.cashReserveMonths.toInt()}月", Color(0xFFFFD54F))
                }
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { health.score / 100f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = Color(health.level.color),
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
private fun HealthStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
    }
}

// ===== 本月收支 =====

@Composable
private fun MonthFinanceSummaryCard(
    report: FinanceMonthlyReport,
    incomeTrend: TrendDirection,
    expenseTrend: TrendDirection,
    profitTrend: TrendDirection
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("本月收支", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                FinSummaryItem("收入", formatFinanceNumber(report.totalIncome), Color(0xFF4CAF50), incomeTrend)
                FinSummaryItem("支出", formatFinanceNumber(report.totalExpense), Color(0xFFF44336), expenseTrend)
                FinSummaryItem(
                    "净利",
                    formatFinanceNumber(report.netProfit),
                    if (report.netProfit >= 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                    profitTrend
                )
            }
        }
    }
}

@Composable
private fun FinSummaryItem(label: String, value: String, color: Color, trend: TrendDirection) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 11.sp, color = Color.Gray)
            PixelIcon(emoji = trend.icon, size = 10.dp)
        }
    }
}

// ===== 收入/支出构成 =====

@Composable
private fun IncomeBreakdownCard(breakdown: List<CategoryBreakdown>) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (breakdown.isEmpty()) {
                Text("本月暂无收入记录", fontSize = 12.sp, color = Color.Gray)
            } else {
                breakdown.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.name, fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatFinanceNumber(item.amount), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("${item.percentage.toInt()}%", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                    LinearProgressIndicator(
                        progress = { (item.percentage / 100.0).coerceIn(0.0, 1.0).toFloat() },
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(1.5.dp)),
                        color = Color(0xFF4CAF50),
                        trackColor = Color(0xFFE8F5E9)
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpenseBreakdownCard(breakdown: List<CategoryBreakdown>) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (breakdown.isEmpty()) {
                Text("本月暂无支出记录", fontSize = 12.sp, color = Color.Gray)
            } else {
                breakdown.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.name, fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatFinanceNumber(item.amount), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("${item.percentage.toInt()}%", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                    LinearProgressIndicator(
                        progress = { (item.percentage / 100.0).coerceIn(0.0, 1.0).toFloat() },
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(1.5.dp)),
                        color = Color(0xFFF44336),
                        trackColor = Color(0xFFFFEBEE)
                    )
                }
            }
        }
    }
}

// ===== 预算执行 =====

@Composable
private fun BudgetExecutionCard(exec: BudgetExecution) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(exec.category.displayName, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(
                    "${exec.executionRate.toInt()}%",
                    fontSize = 12.sp,
                    color = when {
                        exec.executionRate > 100 -> Color(0xFFF44336)
                        exec.executionRate > 80 -> Color(0xFFFF9800)
                        else -> Color(0xFF4CAF50)
                    },
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { (exec.executionRate / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = when {
                    exec.executionRate > 100 -> Color(0xFFF44336)
                    exec.executionRate > 80 -> Color(0xFFFF9800)
                    else -> Color(0xFF4CAF50)
                },
                trackColor = Color(0xFFF5F5F5)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "预算${formatFinanceNumber(exec.budgeted)} · 已用${formatFinanceNumber(exec.spent)} · 余${formatFinanceNumber(exec.remaining)}",
                fontSize = 10.sp, color = Color.Gray
            )
        }
    }
}

// ===== 年度报表 =====

@Composable
private fun YearlyReportCard(report: YearlyReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("${report.year}年度报表", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("年收入: ${formatFinanceNumber(report.totalIncome)}", fontSize = 12.sp)
                    Text("年支出: ${formatFinanceNumber(report.totalExpense)}", fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "年利润: ${formatFinanceNumber(report.netProfit)}",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = if (report.netProfit >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                    Text("月均收入: ${formatFinanceNumber(report.averageMonthlyIncome)}", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }
    }
}

// ===== 预算设置对话框 =====

@Composable
private fun BudgetDialog(
    currentBudgets: Map<ExpenseCategory, Double>,
    onConfirm: (Map<ExpenseCategory, Double>) -> Unit,
    onDismiss: () -> Unit
) {
    val budgets = remember {
        mutableStateMapOf<ExpenseCategory, String>().also { map ->
            ExpenseCategory.entries.forEach { category ->
                map[category] = (currentBudgets[category]?.toLong() ?: 0L).toString()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxWidth(0.92f).wrapContentHeight()) {
            Image(
                painter = painterResource(id = R.drawable.dialog_bg),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.FillBounds
            )
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "月度预算设置",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExpenseCategory.entries.take(6).forEach { category ->
                        OutlinedTextField(
                            value = budgets[category] ?: "0",
                            onValueChange = { budgets[category] = it.filter { c -> c.isDigit() } },
                            label = { Text(category.displayName, fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            singleLine = true
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PixelButton(
                        text = "取消",
                        style = PixelButtonStyle.CANCEL,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    PixelButton(
                        text = "保存预算",
                        style = PixelButtonStyle.CONFIRM,
                        onClick = {
                            val result = budgets.mapValues { (_, v) -> v.toDoubleOrNull() ?: 0.0 }
                                .filter { it.value > 0 }
                            onConfirm(result)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// ===== 雷达图 =====

@Composable
private fun RadarChartCard(data: RadarChartData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("学校综合评分", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${String.format("%.0f", data.average)}分",
                    style = MaterialTheme.typography.titleSmall,
                    color = when {
                        data.average >= 70 -> AccentGreen
                        data.average >= 40 -> AccentOrange
                        else -> AccentRed
                    },
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Canvas(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val radius = minOf(centerX, centerY) - 40f
                val dimensions = data.dimensions
                val sides = dimensions.size
                val angleStep = (2 * Math.PI / sides).toFloat()
                val startAngle = (-Math.PI / 2).toFloat()  // 从顶部开始

                // 绘制背景网格（3层）
                for (level in 1..3) {
                    val levelRadius = radius * level / 3f
                    val gridPath = Path()
                    for (i in 0 until sides) {
                        val angle = startAngle + angleStep * i
                        val x = centerX + levelRadius * kotlin.math.cos(angle)
                        val y = centerY + levelRadius * kotlin.math.sin(angle)
                        if (i == 0) gridPath.moveTo(x, y) else gridPath.lineTo(x, y)
                    }
                    gridPath.close()
                    drawPath(
                        path = gridPath,
                        color = Color.Gray.copy(alpha = 0.15f),
                        style = Stroke(width = 1f)
                    )
                }

                // 绘制轴线
                for (i in 0 until sides) {
                    val angle = startAngle + angleStep * i
                    val endX = centerX + radius * kotlin.math.cos(angle)
                    val endY = centerY + radius * kotlin.math.sin(angle)
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.2f),
                        start = Offset(centerX, centerY),
                        end = Offset(endX, endY),
                        strokeWidth = 1f
                    )
                }

                // 绘制数据多边形
                val dataPath = Path()
                val dataColor = Color(0xFF2196F3)
                for (i in 0 until sides) {
                    val angle = startAngle + angleStep * i
                    val value = dimensions[i].second / 100f
                    val x = centerX + radius * value * kotlin.math.cos(angle)
                    val y = centerY + radius * value * kotlin.math.sin(angle)
                    if (i == 0) dataPath.moveTo(x, y) else dataPath.lineTo(x, y)
                }
                dataPath.close()

                // 填充半透明区域
                drawPath(path = dataPath, color = dataColor.copy(alpha = 0.2f))
                // 绘制边框
                drawPath(path = dataPath, color = dataColor, style = Stroke(width = 2.5f))

                // 绘制数据点
                for (i in 0 until sides) {
                    val angle = startAngle + angleStep * i
                    val value = dimensions[i].second / 100f
                    val x = centerX + radius * value * kotlin.math.cos(angle)
                    val y = centerY + radius * value * kotlin.math.sin(angle)
                    drawCircle(color = dataColor, radius = 5f, center = Offset(x, y))
                    drawCircle(color = Color.White, radius = 3f, center = Offset(x, y))
                }

                // 绘制标签
                val labelPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.DKGRAY
                    textSize = 26f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                for (i in 0 until sides) {
                    val angle = startAngle + angleStep * i
                    val labelRadius = radius + 28f
                    val x = centerX + labelRadius * kotlin.math.cos(angle)
                    val y = centerY + labelRadius * kotlin.math.sin(angle) + 8f
                    val label = "${dimensions[i].first} ${dimensions[i].second.toInt()}"
                    drawContext.canvas.nativeCanvas.drawText(label, x, y, labelPaint)
                }
            }
        }
    }
}

// ===== 柱状图 =====

@Composable
private fun BarChartCard(data: List<BarChartItem>) {
    if (data.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("收支对比", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "近${data.size}个月",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                val width = size.width
                val height = size.height
                val paddingLeft = 50f
                val paddingBottom = 30f
                val chartWidth = width - paddingLeft
                val chartHeight = height - paddingBottom

                val maxValue = data.maxOf { maxOf(it.income, it.expense) }.coerceAtLeast(1.0)
                val barGroupWidth = chartWidth / data.size
                val barWidth = barGroupWidth * 0.3f
                val gap = barGroupWidth * 0.05f

                // Y轴网格
                val yLabelPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 20f
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
                for (i in 0..4) {
                    val fraction = i.toFloat() / 4f
                    val y = chartHeight * (1f - fraction)
                    val value = maxValue * fraction
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.1f),
                        start = Offset(paddingLeft, y),
                        end = Offset(width, y),
                        strokeWidth = 1f
                    )
                    val label = if (value >= 1000) "${String.format("%.0f", value / 1000)}k"
                    else "${value.toLong()}"
                    drawContext.canvas.nativeCanvas.drawText(label, paddingLeft - 8f, y + 6f, yLabelPaint)
                }

                // 绘制柱状
                data.forEachIndexed { index, item ->
                    val groupX = paddingLeft + barGroupWidth * index + barGroupWidth * 0.15f

                    // 收入柱
                    val incomeHeight = (item.income / maxValue * chartHeight).toFloat()
                    drawRoundRect(
                        color = Color(0xFF4CAF50),
                        topLeft = Offset(groupX, chartHeight - incomeHeight),
                        size = androidx.compose.ui.geometry.Size(barWidth, incomeHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                    )

                    // 支出柱
                    val expenseHeight = (item.expense / maxValue * chartHeight).toFloat()
                    drawRoundRect(
                        color = Color(0xFFF44336),
                        topLeft = Offset(groupX + barWidth + gap, chartHeight - expenseHeight),
                        size = androidx.compose.ui.geometry.Size(barWidth, expenseHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                    )
                }

                // X轴标签
                val xLabelPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 22f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                data.forEachIndexed { index, item ->
                    val x = paddingLeft + barGroupWidth * index + barGroupWidth * 0.5f
                    drawContext.canvas.nativeCanvas.drawText(item.label, x, height - 4f, xLabelPaint)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row {
                LegendItem(color = Color(0xFF4CAF50), label = "收入")
                Spacer(modifier = Modifier.width(16.dp))
                LegendItem(color = Color(0xFFF44336), label = "支出")
            }
        }
    }
}

// ===== 环形图 =====

@Composable
private fun DonutChartCard(data: List<DonutSegment>) {
    if (data.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("支出构成", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 环形图
                Canvas(modifier = Modifier.size(140.dp)) {
                    val strokeWidth = 32f
                    val diameter = size.minDimension - strokeWidth
                    val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                    var startAngle = -90f

                    data.forEach { segment ->
                        val sweepAngle = segment.percentage / 100f * 360f
                        drawArc(
                            color = Color(segment.color),
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = topLeft,
                            size = androidx.compose.ui.geometry.Size(diameter, diameter),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                        )
                        startAngle += sweepAngle
                    }
                }

                // 图例
                Column(
                    modifier = Modifier.padding(start = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    data.forEach { segment ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color(segment.color), RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${segment.label} ${segment.percentage.toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

// ===== 工具函数 =====

private fun formatFinanceNumber(value: Double): String {
    val absValue = kotlin.math.abs(value)
    val sign = if (value < 0) "-" else ""
    return when {
        absValue >= 10000 -> "${sign}${String.format("%.1f", absValue / 10000)}亿"
        absValue >= 100 -> "${sign}${String.format("%.0f", absValue)}万"
        absValue >= 0.01 -> "${sign}${String.format("%.1f", absValue)}万"
        else -> "0"
    }
}
