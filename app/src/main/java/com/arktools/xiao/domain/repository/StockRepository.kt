package com.arktools.xiao.domain.repository

import com.arktools.xiao.domain.model.Principal
import com.arktools.xiao.domain.model.Stock
import com.arktools.xiao.domain.model.StockHolding
import com.arktools.xiao.domain.model.StockMarketEvent
import com.arktools.xiao.domain.model.StockPricePoint
import kotlinx.coroutines.flow.Flow

data class StockTradeResult(
    val success: Boolean,
    val message: String,
    val principal: Principal? = null,
    val amount: Double = 0.0
)

interface StockRepository {
    fun getStocksFlow(): Flow<List<Stock>>
    fun getHoldingsFlow(): Flow<List<StockHolding>>
    suspend fun getStocks(): List<Stock>
    suspend fun getHoldings(): List<StockHolding>
    suspend fun initializeDefaultStocks()
    suspend fun updateStockPrices()
    suspend fun buyStock(
        stockId: String,
        shares: Int,
        principal: Principal,
        maxInvestment: Double
    ): StockTradeResult
    suspend fun sellStock(
        stockId: String,
        shares: Int,
        principal: Principal
    ): StockTradeResult
    suspend fun deleteAll()

    // 股票事件系统
    suspend fun applyMarketEvent(event: StockMarketEvent)
    suspend fun getActiveEvents(): List<StockMarketEvent>
    suspend fun tickActiveEvents()

    // K线数据
    suspend fun getPriceHistory(stockId: String, limit: Int = 60): List<StockPricePoint>
    suspend fun recordDailyPrice(gameDay: Int)
}