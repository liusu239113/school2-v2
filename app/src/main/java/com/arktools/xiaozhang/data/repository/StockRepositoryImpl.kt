package com.arktools.xiaozhang.data.repository

import androidx.room.withTransaction
import com.arktools.xiaozhang.data.local.AppDatabase
import com.arktools.xiaozhang.data.local.dao.StockDao
import com.arktools.xiaozhang.data.local.entity.StockEntity
import com.arktools.xiaozhang.data.local.entity.StockHoldingEntity
import com.arktools.xiaozhang.data.local.entity.StockPriceHistoryEntity
import com.arktools.xiaozhang.data.pref.SettingsDataStore
import com.arktools.xiaozhang.domain.model.Principal
import com.arktools.xiaozhang.domain.model.Stock
import com.arktools.xiaozhang.domain.model.StockHolding
import com.arktools.xiaozhang.domain.model.StockMarketEvent
import com.arktools.xiaozhang.domain.model.StockPricePoint
import com.arktools.xiaozhang.domain.model.StockTrend
import com.arktools.xiaozhang.domain.repository.StockRepository
import com.arktools.xiaozhang.domain.repository.StockTradeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random

class StockRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val stockDao: StockDao,
    private val settingsDataStore: SettingsDataStore
) : StockRepository {

    // 市场事件由同一个互斥域维护；行情计算只读取锁内复制的快照。
    private val marketEventMutex = Mutex()
    private val activeEvents = mutableListOf<StockMarketEvent>()

    override fun getStocksFlow(): Flow<List<Stock>> {
        return settingsDataStore.schoolId.flatMapLatest { schoolId ->
            if (schoolId.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                stockDao.getStocksFlow(schoolId).map { entities ->
                    entities.map { it.toDomain() }
                }
            }
        }
    }

    override fun getHoldingsFlow(): Flow<List<StockHolding>> {
        return settingsDataStore.schoolId.flatMapLatest { schoolId ->
            if (schoolId.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                stockDao.getHoldingsFlow(schoolId).map { entities ->
                    entities.map { it.toDomain() }
                }
            }
        }
    }

    override suspend fun getStocks(): List<Stock> {
        val schoolId = settingsDataStore.getSchoolId()
        return withContext(Dispatchers.IO) {
            stockDao.getStocks(schoolId).map { it.toDomain() }
        }
    }

    override suspend fun getHoldings(): List<StockHolding> {
        val schoolId = settingsDataStore.getSchoolId()
        return withContext(Dispatchers.IO) {
            stockDao.getHoldings(schoolId).map { it.toDomain() }
        }
    }

    override suspend fun initializeDefaultStocks() {
        val schoolId = settingsDataStore.getSchoolId()
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val existing = stockDao.getStocks(schoolId)
                if (existing.isNotEmpty()) return@withTransaction

                val defaultStocks = listOf(
                // === 教育行业（4只）===
                StockEntity(
                    id = UUID.randomUUID().toString(),
                    name = "新东升教育",
                    basePrice = 45.0,
                    currentPrice = 45.0,
                    volatility = 0.035,
                    trend = StockTrend.STABLE.name,
                    sector = "教育",
                    schoolId = schoolId
                ),
                StockEntity(
                    id = UUID.randomUUID().toString(),
                    name = "优未来集团",
                    basePrice = 32.0,
                    currentPrice = 32.0,
                    volatility = 0.04,
                    trend = StockTrend.STABLE.name,
                    sector = "教育",
                    schoolId = schoolId
                ),
                StockEntity(
                    id = UUID.randomUUID().toString(),
                    name = "灵猿辅导",
                    basePrice = 55.0,
                    currentPrice = 55.0,
                    volatility = 0.05,
                    trend = StockTrend.STABLE.name,
                    sector = "教育",
                    schoolId = schoolId
                ),
                StockEntity(
                    id = UUID.randomUUID().toString(),
                    name = "易学有道",
                    basePrice = 18.0,
                    currentPrice = 18.0,
                    volatility = 0.045,
                    trend = StockTrend.STABLE.name,
                    sector = "教育",
                    schoolId = schoolId
                ),
                // === 科技行业（4只）===
                StockEntity(
                    id = UUID.randomUUID().toString(),
                    name = "腾达控股",
                    basePrice = 380.0,
                    currentPrice = 380.0,
                    volatility = 0.025,
                    trend = StockTrend.STABLE.name,
                    sector = "科技",
                    schoolId = schoolId
                ),
                StockEntity(
                    id = UUID.randomUUID().toString(),
                    name = "阿里讯商",
                    basePrice = 85.0,
                    currentPrice = 85.0,
                    volatility = 0.035,
                    trend = StockTrend.STABLE.name,
                    sector = "科技",
                    schoolId = schoolId
                ),
                StockEntity(
                    id = UUID.randomUUID().toString(),
                    name = "字符跃动",
                    basePrice = 220.0,
                    currentPrice = 220.0,
                    volatility = 0.04,
                    trend = StockTrend.STABLE.name,
                    sector = "科技",
                    schoolId = schoolId
                ),
                StockEntity(
                    id = UUID.randomUUID().toString(),
                    name = "米芯科技",
                    basePrice = 15.0,
                    currentPrice = 15.0,
                    volatility = 0.045,
                    trend = StockTrend.STABLE.name,
                    sector = "科技",
                    schoolId = schoolId
                ),
                // === 消费行业（3只）===
                StockEntity(
                    id = UUID.randomUUID().toString(),
                    name = "黔州酒业",
                    basePrice = 1800.0,
                    currentPrice = 1800.0,
                    volatility = 0.02,
                    trend = StockTrend.STABLE.name,
                    sector = "消费",
                    schoolId = schoolId
                ),
                StockEntity(
                    id = UUID.randomUUID().toString(),
                    name = "江湖捞火锅",
                    basePrice = 22.0,
                    currentPrice = 22.0,
                    volatility = 0.05,
                    trend = StockTrend.STABLE.name,
                    sector = "消费",
                    schoolId = schoolId
                ),
                StockEntity(
                    id = UUID.randomUUID().toString(),
                    name = "力宁体育",
                    basePrice = 28.0,
                    currentPrice = 28.0,
                    volatility = 0.04,
                    trend = StockTrend.STABLE.name,
                    sector = "消费",
                    schoolId = schoolId
                ),
                // === 医药行业（2只）===
                StockEntity(
                    id = UUID.randomUUID().toString(),
                    name = "恒锐医药",
                    basePrice = 48.0,
                    currentPrice = 48.0,
                    volatility = 0.03,
                    trend = StockTrend.STABLE.name,
                    sector = "医药",
                    schoolId = schoolId
                ),
                StockEntity(
                    id = UUID.randomUUID().toString(),
                    name = "迈睿医疗",
                    basePrice = 320.0,
                    currentPrice = 320.0,
                    volatility = 0.025,
                    trend = StockTrend.STABLE.name,
                    sector = "医药",
                    schoolId = schoolId
                ),
                // === 金融行业（2只）===
                StockEntity(
                    id = UUID.randomUUID().toString(),
                    name = "招财银行",
                    basePrice = 35.0,
                    currentPrice = 35.0,
                    volatility = 0.02,
                    trend = StockTrend.STABLE.name,
                    sector = "金融",
                    schoolId = schoolId
                ),
                StockEntity(
                    id = UUID.randomUUID().toString(),
                    name = "神州平安",
                    basePrice = 52.0,
                    currentPrice = 52.0,
                    volatility = 0.025,
                    trend = StockTrend.STABLE.name,
                    sector = "金融",
                    schoolId = schoolId
                ),
                // === 新能源行业（2只）===
                StockEntity(
                    id = UUID.randomUUID().toString(),
                    name = "安德时代",
                    basePrice = 210.0,
                    currentPrice = 210.0,
                    volatility = 0.045,
                    trend = StockTrend.STABLE.name,
                    sector = "新能源",
                    schoolId = schoolId
                ),
                StockEntity(
                    id = UUID.randomUUID().toString(),
                    name = "比亚达",
                    basePrice = 265.0,
                    currentPrice = 265.0,
                    volatility = 0.04,
                    trend = StockTrend.STABLE.name,
                    sector = "新能源",
                    schoolId = schoolId
                ),
                // === 房地产行业（1只，高风险）===
                StockEntity(
                    id = UUID.randomUUID().toString(),
                    name = "万嘉地产",
                    basePrice = 12.0,
                    currentPrice = 12.0,
                    volatility = 0.06,
                    trend = StockTrend.STABLE.name,
                    sector = "地产",
                    schoolId = schoolId
                ),
                // === 游戏/文娱行业（2只）===
                StockEntity(
                    id = UUID.randomUUID().toString(),
                    name = "米哈森",
                    basePrice = 150.0,
                    currentPrice = 150.0,
                    volatility = 0.05,
                    trend = StockTrend.STABLE.name,
                    sector = "文娱",
                    schoolId = schoolId
                ),
                StockEntity(
                    id = UUID.randomUUID().toString(),
                    name = "C站弹幕",
                    basePrice = 16.0,
                    currentPrice = 16.0,
                    volatility = 0.055,
                    trend = StockTrend.STABLE.name,
                    sector = "文娱",
                    schoolId = schoolId
                )
                )
                stockDao.insertStocks(defaultStocks)
            }
        }
    }

    override suspend fun updateStockPrices() {
        val schoolId = settingsDataStore.getSchoolId()
        val eventSnapshot = marketEventMutex.withLock {
            activeEvents.map { it.copy() }
        }
        withContext(Dispatchers.IO) {
            val stocks = stockDao.getStocks(schoolId)
            for (stock in stocks) {
                // 真实股市模型：无上涨偏向 + 均值回归 + 动量 + 黑天鹅
                var changePercent = (Random.nextDouble() - 0.50) * stock.volatility * 100

                // 均值回归：价格偏离基准价越远，回调力量越强
                val deviation = (stock.currentPrice - stock.basePrice) / stock.basePrice
                val meanReversionForce = -deviation * 0.8  // 回归力度（百分比/日）
                changePercent += meanReversionForce

                // 动量效应：延续当前趋势的惯性（较弱）
                val momentumBonus = when (stock.trend) {
                    StockTrend.UP.name -> 0.15
                    StockTrend.DOWN.name -> -0.15
                    else -> 0.0
                }
                changePercent += momentumBonus

                // 黑天鹅事件：小概率大幅波动（2%概率触发±3-8%震荡）
                if (Random.nextDouble() < 0.02) {
                    val crashDirection = if (Random.nextBoolean()) 1.0 else -1.0
                    changePercent += crashDirection * (3.0 + Random.nextDouble() * 5.0)
                }

                // 叠加活跃事件的影响
                for (event in eventSnapshot) {
                    val dailyImpact = event.priceImpactPercent / event.durationDays.coerceAtLeast(1)
                    when {
                        // 全市场事件
                        event.affectedSector == null && event.affectedStockId == null -> {
                            changePercent += dailyImpact
                        }
                        // 板块事件
                        event.affectedSector != null && stock.sector == event.affectedSector -> {
                            changePercent += dailyImpact
                        }
                        // 个股事件
                        event.affectedStockId != null && event.affectedStockId == stock.id -> {
                            changePercent += dailyImpact
                        }
                    }
                }

                // 涨跌幅限制：单日最大±10%（模拟A股涨跌停）
                changePercent = changePercent.coerceIn(-10.0, 10.0)

                val newPrice = (stock.currentPrice * (1 + changePercent / 100.0)).coerceAtLeast(1.0)
                val trend = when {
                    changePercent > 1.0 -> StockTrend.UP.name
                    changePercent < -1.0 -> StockTrend.DOWN.name
                    else -> StockTrend.STABLE.name
                }
                stockDao.updateStockPrice(
                    schoolId,
                    stock.id,
                    newPrice,
                    trend
                )
            }
        }
    }

    override suspend fun buyStock(
        stockId: String,
        shares: Int,
        principal: Principal,
        maxInvestment: Double
    ): StockTradeResult = withContext(Dispatchers.IO) {
        if (shares <= 0) {
            return@withContext StockTradeResult(false, "购买数量必须大于 0")
        }
        val schoolId = settingsDataStore.getSchoolId()
        database.withTransaction {
            val schoolDao = database.schoolDao()
            val school = schoolDao.getSchoolCore()
                ?: return@withTransaction StockTradeResult(false, "学校存档不可用")
            if (school.id != schoolId) {
                return@withTransaction StockTradeResult(false, "当前学校已切换，请重试")
            }
            val stock = stockDao.getStockById(schoolId, stockId)
                ?: return@withTransaction StockTradeResult(false, "股票不存在")
            val totalCost = stock.currentPrice * shares
            if (!totalCost.isFinite() || totalCost <= 0.0) {
                return@withTransaction StockTradeResult(false, "交易金额无效")
            }
            if (totalCost > maxInvestment) {
                return@withTransaction StockTradeResult(
                    false,
                    "单次投资超过当前校园等级上限"
                )
            }
            if (principal.personalFunds < totalCost) {
                return@withTransaction StockTradeResult(false, "个人资金不足")
            }

            val updatedPrincipal = principal.copyForPersistence().apply {
                personalFunds -= totalCost
                version++
            }
            val holding = stockDao.getHolding(schoolId, stockId)
            if (holding == null) {
                stockDao.insertHolding(
                    StockHoldingEntity(
                        id = UUID.randomUUID().toString(),
                        stockId = stockId,
                        shares = shares,
                        avgBuyPrice = stock.currentPrice,
                        schoolId = schoolId
                    )
                )
            } else {
                if (holding.shares > Int.MAX_VALUE - shares) {
                    return@withTransaction StockTradeResult(false, "持仓数量超过上限")
                }
                val totalShares = holding.shares + shares
                val averagePrice = (
                    holding.avgBuyPrice * holding.shares + totalCost
                ) / totalShares
                stockDao.updateHolding(
                    holding.copy(
                        shares = totalShares,
                        avgBuyPrice = averagePrice
                    )
                )
            }
            val revision = maxOf(System.currentTimeMillis(), school.lastSaveTime + 1L)
            val updatedRows = schoolDao.updatePrincipalState(
                schoolId,
                Json.encodeToString(updatedPrincipal),
                revision
            )
            check(updatedRows == 1) { "Principal state update failed" }
            StockTradeResult(
                success = true,
                message = "购买成功",
                principal = updatedPrincipal,
                amount = totalCost
            )
        }
    }

    override suspend fun sellStock(
        stockId: String,
        shares: Int,
        principal: Principal
    ): StockTradeResult = withContext(Dispatchers.IO) {
        if (shares <= 0) {
            return@withContext StockTradeResult(false, "卖出数量必须大于 0")
        }
        val schoolId = settingsDataStore.getSchoolId()
        database.withTransaction {
            val schoolDao = database.schoolDao()
            val school = schoolDao.getSchoolCore()
                ?: return@withTransaction StockTradeResult(false, "学校存档不可用")
            if (school.id != schoolId) {
                return@withTransaction StockTradeResult(false, "当前学校已切换，请重试")
            }
            val stock = stockDao.getStockById(schoolId, stockId)
                ?: return@withTransaction StockTradeResult(false, "股票不存在")
            val holding = stockDao.getHolding(schoolId, stockId)
                ?: return@withTransaction StockTradeResult(false, "没有该股票持仓")
            if (holding.shares < shares) {
                return@withTransaction StockTradeResult(false, "持仓数量不足")
            }
            val totalRevenue = stock.currentPrice * shares
            if (!totalRevenue.isFinite() || totalRevenue <= 0.0) {
                return@withTransaction StockTradeResult(false, "交易金额无效")
            }
            val updatedPrincipal = principal.copyForPersistence().apply {
                personalFunds += totalRevenue
                version++
            }
            val remainingShares = holding.shares - shares
            if (remainingShares == 0) {
                stockDao.deleteHolding(schoolId, stockId)
            } else {
                stockDao.updateHolding(holding.copy(shares = remainingShares))
            }
            val revision = maxOf(System.currentTimeMillis(), school.lastSaveTime + 1L)
            val updatedRows = schoolDao.updatePrincipalState(
                schoolId,
                Json.encodeToString(updatedPrincipal),
                revision
            )
            check(updatedRows == 1) { "Principal state update failed" }
            StockTradeResult(
                success = true,
                message = "卖出成功",
                principal = updatedPrincipal,
                amount = totalRevenue
            )
        }
    }

    override suspend fun deleteAll() {
        // not needed for now
    }

    // ==================== 股票事件系统 ====================

    override suspend fun applyMarketEvent(event: StockMarketEvent) {
        val schoolId = settingsDataStore.getSchoolId()
        withContext(Dispatchers.IO) {
            // 处理 __RANDOM__ 标记：随机指定一只股票
            val resolvedEvent = if (event.affectedStockId == "__RANDOM__") {
                val stocks = stockDao.getStocks(schoolId)
                if (stocks.isNotEmpty()) {
                    event.copy(affectedStockId = stocks.random().id)
                } else event
            } else event

            val storedEvent = resolvedEvent.copy(
                remainingDays = resolvedEvent.durationDays
            )
            marketEventMutex.withLock {
                activeEvents.add(storedEvent)
            }
        }
    }

    override suspend fun getActiveEvents(): List<StockMarketEvent> =
        marketEventMutex.withLock {
            activeEvents.map { it.copy() }
        }

    override suspend fun tickActiveEvents() {
        marketEventMutex.withLock {
            activeEvents.forEach { it.remainingDays-- }
            activeEvents.removeAll { it.remainingDays <= 0 }
        }
    }

    // ==================== K线数据 ====================

    override suspend fun getPriceHistory(stockId: String, limit: Int): List<StockPricePoint> {
        val schoolId = settingsDataStore.getSchoolId()
        return withContext(Dispatchers.IO) {
            stockDao.getPriceHistory(stockId, schoolId, limit).map { entity ->
                StockPricePoint(
                    day = entity.gameDay,
                    open = entity.open,
                    close = entity.close,
                    high = entity.high,
                    low = entity.low
                )
            }.reversed()  // 按时间正序返回
        }
    }

    override suspend fun recordDailyPrice(gameDay: Int) {
        val schoolId = settingsDataStore.getSchoolId()
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val stocks = stockDao.getStocks(schoolId)
                val entries = stocks.map { stock ->
                    // 简化的K线数据：日内用轻微波动模拟开盘/最高/最低
                    val price = stock.currentPrice
                    val intraVolatility = stock.volatility * 0.5
                    val high = price * (1 + Random.nextDouble(0.0, intraVolatility))
                    val low = price * (1 - Random.nextDouble(0.0, intraVolatility))
                    val open = low + Random.nextDouble() * (high - low)

                    StockPriceHistoryEntity(
                        stockId = stock.id,
                        schoolId = schoolId,
                        gameDay = gameDay,
                        open = open,
                        close = price,
                        high = high,
                        low = low
                    )
                }
                stockDao.insertPriceHistories(entries)

                // 清理超过180个游戏日的旧记录，防止表无限膨胀
                val retentionDays = 180
                val minDay = gameDay - retentionDays
                if (minDay > 0) {
                    stockDao.pruneOldPriceHistory(schoolId, minDay)
                }
            }
        }
    }

    private fun Principal.copyForPersistence(): Principal = copy(
        recentCorruptActs = recentCorruptActs.map { it.copy() }.toMutableList(),
        connections = connections.map { it.copy() }.toMutableList(),
        purchasedLuxuryItems = purchasedLuxuryItems.toMutableList(),
        factionRelations = factionRelations.toMutableMap()
    )

    private fun StockEntity.toDomain(): Stock {
        return Stock(
            id = id,
            name = name,
            basePrice = basePrice,
            currentPrice = currentPrice,
            volatility = volatility,
            trend = try { StockTrend.valueOf(trend) } catch (e: Exception) { StockTrend.STABLE },
            sector = sector
        )
    }

    private fun StockHoldingEntity.toDomain(): StockHolding {
        return StockHolding(
            id = id,
            stockId = stockId,
            shares = shares,
            avgBuyPrice = avgBuyPrice,
            currentPrice = 0.0
        )
    }
}