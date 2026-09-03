package com.arktools.xiao.domain.model

import kotlinx.serialization.Serializable

data class Stock(
    val id: String,
    val name: String,
    val basePrice: Double,
    var currentPrice: Double,
    val volatility: Double,
    var trend: StockTrend,
    val sector: String,
    val priceHistory: MutableList<StockPricePoint> = mutableListOf()
)

enum class StockTrend(val displayName: String, val color: Long) {
    UP("上涨", 0xFF4CAF50),
    DOWN("下跌", 0xFFF44336),
    STABLE("平稳", 0xFFFF9800)
}

/**
 * K线数据点，记录每日股价用于图表展示
 */
@Serializable
data class StockPricePoint(
    val day: Int = 0,          // 游戏总天数
    val open: Double = 0.0,
    val close: Double = 0.0,
    val high: Double = 0.0,
    val low: Double = 0.0
)

data class StockHolding(
    val id: String,
    val stockId: String,
    val shares: Int,
    val avgBuyPrice: Double,
    var currentPrice: Double = 0.0
) {
    val totalCost: Double get() = shares * avgBuyPrice
    val currentValue: Double get() = shares * currentPrice
    val profitLoss: Double get() = currentValue - totalCost
    val profitLossPercent: Double get() = if (totalCost > 0) (currentPrice - avgBuyPrice) / avgBuyPrice * 100 else 0.0
}

data class StockMarketData(
    val stocks: List<Stock>,
    val holdings: List<StockHolding>,
    val totalInvested: Double,
    val totalValue: Double,
    val totalProfitLoss: Double
) {
    val totalProfitLossPercent: Double
        get() = if (totalInvested > 0) totalProfitLoss / totalInvested * 100 else 0.0
}

/**
 * 股票市场事件 - 政策/市场因素影响股价
 */
enum class StockEventType(
    val displayName: String,
    val description: String
) {
    POLICY_POSITIVE("利好政策", "政策面利好，教育板块上涨"),
    POLICY_NEGATIVE("利空政策", "政策面利空，教育板块下跌"),
    MARKET_BOOM("市场繁荣", "经济形势好转，整体市场上涨"),
    MARKET_CRASH("市场崩盘", "经济下行，整体市场下跌"),
    SECTOR_BOOM("板块热点", "某板块成为热点，集中上涨"),
    SECTOR_BUST("板块暴跌", "某板块出现利空，集中下跌"),
    COMPANY_SCANDAL("个股利空", "某公司出现负面新闻"),
    COMPANY_BREAKTHROUGH("个股利好", "某公司出现重大突破")
}

/**
 * 股票市场事件数据
 */
data class StockMarketEvent(
    val type: StockEventType,
    val title: String,
    val message: String,
    val affectedSector: String? = null,       // null = 影响全部, 否则影响指定板块
    val affectedStockId: String? = null,      // 个股事件时指定
    val priceImpactPercent: Double = 0.0,     // 价格影响百分比（正/负）
    val durationDays: Int = 1,                // 持续天数（渐进式影响）
    var remainingDays: Int = 0                // 剩余影响天数
)