package com.arktools.xiaozhang.ui.stock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.Image
import androidx.hilt.navigation.compose.hiltViewModel
import com.arktools.xiaozhang.R
import com.arktools.xiaozhang.domain.model.Stock
import com.arktools.xiaozhang.ui.components.PixelButton
import com.arktools.xiaozhang.ui.components.PixelButtonStyle
import com.arktools.xiaozhang.domain.model.StockHolding
import com.arktools.xiaozhang.domain.model.StockPricePoint
import com.arktools.xiaozhang.domain.model.StockTrend
import com.arktools.xiaozhang.ui.theme.AccentGreen
import com.arktools.xiaozhang.ui.theme.AccentOrange
import com.arktools.xiaozhang.ui.theme.AccentRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(
    viewModel: StockViewModel = hiltViewModel()
) {
    val stocks by viewModel.stocks.collectAsState()
    val holdings by viewModel.holdings.collectAsState()
    val selectedStock by viewModel.selectedStock.collectAsState()
    val showBuyDialog by viewModel.showBuyDialog.collectAsState()
    val showSellDialog by viewModel.showSellDialog.collectAsState()
    val buyQuantityText by viewModel.buyQuantityText.collectAsState()
    val sellQuantityText by viewModel.sellQuantityText.collectAsState()
    val priceHistory by viewModel.priceHistory.collectAsState()
    val campusLevel by viewModel.campusLevel.collectAsState()
    val buyError by viewModel.buyError.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshHoldings()
    }

    // 未解锁时显示锁定界面
    if (campusLevel < StockViewModel.STOCK_UNLOCK_LEVEL) {
        StockLockedScreen(currentLevel = campusLevel, requiredLevel = StockViewModel.STOCK_UNLOCK_LEVEL)
        return
    }

    val totalInvested = holdings.sumOf { it.totalCost }
    val totalValue = holdings.sumOf { it.currentValue }
    val totalPL = holdings.sumOf { it.profitLoss }
    val totalPLPercent = if (totalInvested > 0) totalPL / totalInvested * 100 else 0.0

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("股票投资") },
                actions = {
                    Icon(
                        imageVector = Icons.Default.ShowChart,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Portfolio summary card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "投资组合",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = String.format("%.1f", totalInvested),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text("投入(万)", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = String.format("%.1f", totalValue),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text("市值(万)", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = String.format("%+.1f", totalPL),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (totalPL >= 0) AccentGreen else AccentRed
                            )
                            Text(
                                text = String.format("%+.1f%%", totalPLPercent),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (totalPLPercent >= 0) AccentGreen else AccentRed
                            )
                        }
                    }

                    // Per-holding breakdown
                    if (holdings.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        holdings.filter { it.shares > 0 }.forEach { holding ->
                            val stock = stocks.find { it.id == holding.stockId }
                            if (stock != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${stock.name} ×${holding.shares}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = String.format("%+.1f%%", holding.profitLossPercent),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (holding.profitLoss >= 0) AccentGreen else AccentRed
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Text(
                text = "股票行情",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // 个人资金提示
            Text(
                text = "💰 股票投资使用校长个人资金，盈亏自负。可在「校长办公室」将个人资金捐献给学校。",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF1565C0),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                lineHeight = 16.sp
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp,
                    vertical = 4.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(stocks) { stock ->
                    StockCard(
                        stock = stock,
                        holding = holdings.find { it.stockId == stock.id },
                        onClick = { viewModel.onStockClick(stock) }
                    )
                }
            }
        }
    }

    selectedStock?.let { stock ->
        val holding = holdings.find { it.stockId == stock.id }
        StockDetailBottomSheet(
            stock = stock,
            holding = holding,
            priceHistory = priceHistory,
            onDismiss = { viewModel.clearSelectedStock() },
            onBuy = { viewModel.showBuyDialog() },
            onSell = { viewModel.showSellDialog() }
        )
    }

    if (showBuyDialog) {
        selectedStock?.let { stock ->
            val currentCash by viewModel.currentCash.collectAsState()
            BuyDialog(
                stock = stock,
                quantityText = buyQuantityText,
                availableCash = viewModel.personalFunds,
                maxInvestment = viewModel.maxInvestmentPerTrade,
                buyError = buyError,
                onQuantityTextChange = { viewModel.setBuyQuantityText(it) },
                onConfirm = { viewModel.buyStock() },
                onDismiss = { viewModel.dismissBuyDialog(); viewModel.clearBuyError() }
            )
        }
    }

    if (showSellDialog) {
        selectedStock?.let { stock ->
            val holding = holdings.find { it.stockId == stock.id }
            SellDialog(
                stock = stock,
                holding = holding,
                quantityText = sellQuantityText,
                onQuantityTextChange = { viewModel.setSellQuantityText(it) },
                onConfirm = { viewModel.sellStock() },
                onDismiss = { viewModel.dismissSellDialog() }
            )
        }
    }

    // 股票投资现已合法化（使用个人资金），不再有贪污风险
}

@Composable
private fun StockCard(
    stock: Stock,
    holding: StockHolding?,
    onClick: () -> Unit
) {
    val trendColor = when (stock.trend) {
        StockTrend.UP -> AccentGreen
        StockTrend.DOWN -> AccentRed
        StockTrend.STABLE -> AccentOrange
    }
    val trendText = when (stock.trend) {
        StockTrend.UP -> "▲"
        StockTrend.DOWN -> "▼"
        StockTrend.STABLE -> "─"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stock.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                color = trendColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stock.sector,
                            style = MaterialTheme.typography.labelSmall,
                            color = trendColor
                        )
                    }
                }
                if (holding != null && holding.shares > 0) {
                    Text(
                        text = "持仓: ${holding.shares}股 | 盈亏: ${String.format("%+.1f%%", holding.profitLossPercent)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (holding.profitLoss >= 0) AccentGreen else AccentRed
                    )
                }
                // Mini sparkline from price history
                if (stock.priceHistory.size >= 2) {
                    Spacer(modifier = Modifier.height(4.dp))
                    MiniSparkline(
                        pricePoints = stock.priceHistory.takeLast(30),
                        color = trendColor,
                        modifier = Modifier
                            .width(80.dp)
                            .height(24.dp)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = String.format("%.2f", stock.currentPrice),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = trendColor
                )
                Text(
                    text = "$trendText ${stock.trend.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = trendColor
                )
            }
        }
    }
}

/**
 * Mini sparkline chart for showing price history
 */
@Composable
private fun MiniSparkline(
    pricePoints: List<StockPricePoint>,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (pricePoints.size < 2) return

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val prices = pricePoints.map { it.close }
        val minPrice = prices.min()
        val maxPrice = prices.max()
        val range = (maxPrice - minPrice).coerceAtLeast(0.01)

        val path = Path()
        prices.forEachIndexed { index, price ->
            val x = index.toFloat() / (prices.size - 1) * width
            val y = height - ((price - minPrice) / range * height).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2f)
        )
    }
}

/**
 * 股票涨跌曲线图：展示一段时间内的收盘价走势，带网格和填充区域
 */
@Composable
private fun PriceTrendChart(
    pricePoints: List<StockPricePoint>,
    modifier: Modifier = Modifier
) {
    if (pricePoints.size < 2) return

    val prices = pricePoints.map { it.close }
    val minPrice = prices.min()
    val maxPrice = prices.max()
    val range = (maxPrice - minPrice).coerceAtLeast(0.01)
    val startPrice = prices.first()
    val endPrice = prices.last()
    val trendColor = if (endPrice >= startPrice) AccentGreen else AccentRed

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val paddingTop = 8f
            val paddingBottom = 8f
            val chartHeight = height - paddingTop - paddingBottom

            // 网格横线
            val gridColor = Color.LightGray.copy(alpha = 0.4f)
            for (i in 0..3) {
                val y = paddingTop + chartHeight * i / 3f
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f
                )
            }

            // 价格走势路径和填充路径
            val linePath = Path()
            val fillPath = Path()
            prices.forEachIndexed { index, price ->
                val x = index.toFloat() / (prices.size - 1) * width
                val y = paddingTop + chartHeight - ((price - minPrice) / range * chartHeight).toFloat()
                if (index == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, height)
                    fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
                if (index == prices.size - 1) {
                    fillPath.lineTo(x, height)
                    fillPath.close()
                }
            }

            // 填充渐变区域
            drawPath(
                path = fillPath,
                color = trendColor.copy(alpha = 0.12f)
            )

            // 价格线
            drawPath(
                path = linePath,
                color = trendColor,
                style = Stroke(width = 2.5f)
            )

            // 首尾端点
            val firstX = 0f
            val firstY = paddingTop + chartHeight - ((prices.first() - minPrice) / range * chartHeight).toFloat()
            val lastX = width
            val lastY = paddingTop + chartHeight - ((prices.last() - minPrice) / range * chartHeight).toFloat()
            drawCircle(color = trendColor, radius = 4f, center = Offset(firstX, firstY))
            drawCircle(color = trendColor, radius = 4f, center = Offset(lastX, lastY))
        }

        // 最高价/最低价标签
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = String.format("%.2f", maxPrice),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Text(
                text = String.format("%.2f", minPrice),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StockDetailBottomSheet(
    stock: Stock,
    holding: StockHolding?,
    priceHistory: List<StockPricePoint>,
    onDismiss: () -> Unit,
    onBuy: () -> Unit,
    onSell: () -> Unit
) {
    val trendColor = when (stock.trend) {
        StockTrend.UP -> AccentGreen
        StockTrend.DOWN -> AccentRed
        StockTrend.STABLE -> AccentOrange
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.dialog_bg),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.FillBounds
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = stock.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (holding != null && holding.shares > 0) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("我的持仓", style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("持有: ${holding.shares}股")
                            Text("成本价: ${String.format("%.2f", holding.avgBuyPrice)}")
                            Text("当前价: ${String.format("%.2f", stock.currentPrice)}")
                            val profit = holding.profitLoss
                            Text(
                                text = "盈亏: ${String.format("%+.2f", profit)} (${String.format("%+.1f", holding.profitLossPercent)}%)",
                                color = if (profit >= 0) AccentGreen else AccentRed
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Text("当前价格: ${String.format("%.2f", stock.currentPrice)}", color = trendColor)
                Text("趋势: ${stock.trend.displayName}", color = trendColor)
                Text("波动率: ${String.format("%.1f", stock.volatility * 100)}%")
                Text("行业: ${stock.sector}")
                Spacer(modifier = Modifier.height(12.dp))

                // 涨跌曲线：展示最近股价走势
                if (priceHistory.size >= 2) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "近${priceHistory.size}日涨跌曲线",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            PriceTrendChart(
                                pricePoints = priceHistory,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PixelButton(
                        text = "买入",
                        onClick = onBuy,
                        style = PixelButtonStyle.CONFIRM,
                        modifier = Modifier.weight(1f),
                        height = 44.dp
                    )
                    PixelButton(
                        text = "卖出",
                        onClick = onSell,
                        style = PixelButtonStyle.DANGER,
                        modifier = Modifier.weight(1f),
                        height = 44.dp,
                        enabled = holding != null && holding.shares > 0
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                PixelButton(
                    text = "关闭",
                    onClick = onDismiss,
                    style = PixelButtonStyle.CANCEL,
                    modifier = Modifier.fillMaxWidth(),
                    height = 44.dp
                )
            }
        }
    }
}

@Composable
private fun BuyDialog(
    stock: Stock,
    quantityText: String,
    availableCash: Double,
    maxInvestment: Double = Double.MAX_VALUE,
    buyError: String? = null,
    onQuantityTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val quantity = quantityText.toIntOrNull() ?: 0
    val totalCost = stock.currentPrice * quantity
    val canAfford = totalCost <= availableCash
    val withinLimit = totalCost <= maxInvestment

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.dialog_bg),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.FillBounds
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "买入 ${stock.name}",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                // Available cash display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "可用资金",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "${String.format("%.1f", availableCash)}万",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "当前股价",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "${String.format("%.2f", stock.currentPrice)}万/股",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { onQuantityTextChange(it) },
                    label = { Text("购买数量(股)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "总价",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "${String.format("%.2f", totalCost)}万",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (canAfford) AccentOrange else AccentRed
                    )
                }
                if (!canAfford && quantity > 0) {
                    Text(
                        text = "资金不足！还差 ${String.format("%.1f", totalCost - availableCash)}万",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentRed,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (!withinLimit && quantity > 0) {
                    Text(
                        text = "超出单次投资上限（${String.format("%.0f", maxInvestment)}万），请减少数量",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentRed,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (buyError != null) {
                    Text(
                        text = buyError,
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentRed,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                // 投资上限提示（关联校园等级）+ 个人资金余额
                Text(
                    text = "单次投资上限: ${String.format("%.0f", maxInvestment)}万（校园等级×50万） · 个人资金: ${String.format("%.1f", availableCash)}万",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PixelButton(
                        text = "取消",
                        onClick = onDismiss,
                        style = PixelButtonStyle.CANCEL,
                        modifier = Modifier.weight(1f),
                        height = 44.dp
                    )
                    PixelButton(
                        text = "确认买入",
                        onClick = onConfirm,
                        style = PixelButtonStyle.CONFIRM,
                        modifier = Modifier.weight(1f),
                        height = 44.dp,
                        enabled = quantity > 0 && canAfford && withinLimit
                    )
                }
            }
        }
    }
}

@Composable
private fun SellDialog(
    stock: Stock,
    holding: StockHolding?,
    quantityText: String,
    onQuantityTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val quantity = quantityText.toIntOrNull() ?: 0
    val maxShares = holding?.shares ?: 0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.dialog_bg),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.FillBounds
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "卖出 ${stock.name}",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("当前价格: ${String.format("%.2f", stock.currentPrice)}")
                Text("可卖数量: $maxShares 股")
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PixelButton(
                        text = "25%",
                        onClick = { onQuantityTextChange((maxShares / 4).coerceAtLeast(1).toString()) },
                        style = PixelButtonStyle.PRIMARY,
                        modifier = Modifier.weight(1f),
                        height = 44.dp
                    )
                    PixelButton(
                        text = "50%",
                        onClick = { onQuantityTextChange((maxShares / 2).coerceAtLeast(1).toString()) },
                        style = PixelButtonStyle.PRIMARY,
                        modifier = Modifier.weight(1f),
                        height = 44.dp
                    )
                    PixelButton(
                        text = "全部",
                        onClick = { onQuantityTextChange(maxShares.toString()) },
                        style = PixelButtonStyle.PRIMARY,
                        modifier = Modifier.weight(1f),
                        height = 44.dp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { onQuantityTextChange(it) },
                    label = { Text("卖出数量(股)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "预计收入: ${String.format("%.2f", stock.currentPrice * quantity)}万",
                    style = MaterialTheme.typography.titleSmall,
                    color = AccentGreen
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PixelButton(
                        text = "取消",
                        onClick = onDismiss,
                        style = PixelButtonStyle.CANCEL,
                        modifier = Modifier.weight(1f),
                        height = 44.dp
                    )
                    PixelButton(
                        text = "确认卖出",
                        onClick = onConfirm,
                        style = PixelButtonStyle.DANGER,
                        modifier = Modifier.weight(1f),
                        height = 44.dp,
                        enabled = quantity > 0 && quantity <= maxShares
                    )
                }
            }
        }
    }
}

/**
 * 股市未解锁时的锁定提示界面
 */
@Composable
private fun StockLockedScreen(
    currentLevel: Int,
    requiredLevel: Int
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.TrendingUp,
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .padding(8.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )

                Text(
                    text = "股市投资",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "校园等级达到 $requiredLevel 级后解锁",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = AccentOrange
                )

                // 进度提示
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "当前等级",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "$currentLevel 级",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = AccentRed
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "需要等级",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "$requiredLevel 级",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = AccentGreen
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "提升校园等级方式：扩建设施、提升教学质量、招收更多学生",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}