package com.arktools.xiao.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.arktools.xiao.data.local.entity.StockEntity
import com.arktools.xiao.data.local.entity.StockHoldingEntity
import com.arktools.xiao.data.local.entity.StockPriceHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StockDao {
    @Query("SELECT * FROM stocks WHERE schoolId = :schoolId")
    fun getStocksFlow(schoolId: String): Flow<List<StockEntity>>

    @Query("SELECT * FROM stocks WHERE schoolId = :schoolId")
    suspend fun getStocks(schoolId: String): List<StockEntity>

    @Query("SELECT * FROM stocks WHERE schoolId = :schoolId AND id = :stockId")
    suspend fun getStockById(schoolId: String, stockId: String): StockEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStock(stock: StockEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStocks(stocks: List<StockEntity>)

    @Update
    suspend fun updateStock(stock: StockEntity)

    @Query("UPDATE stocks SET currentPrice = :newPrice, trend = :trend WHERE schoolId = :schoolId AND id = :stockId")
    suspend fun updateStockPrice(
        schoolId: String,
        stockId: String,
        newPrice: Double,
        trend: String
    )

    @Query("UPDATE stocks SET currentPrice = currentPrice * (1 + :changePercent / 100.0) WHERE schoolId = :schoolId")
    suspend fun updateAllStockPrices(schoolId: String, changePercent: Double)

    @Query("DELETE FROM stocks WHERE schoolId = :schoolId")
    suspend fun deleteStocksBySchool(schoolId: String)

    @Query("SELECT * FROM stock_holdings WHERE schoolId = :schoolId")
    fun getHoldingsFlow(schoolId: String): Flow<List<StockHoldingEntity>>

    @Query("SELECT * FROM stock_holdings WHERE schoolId = :schoolId")
    suspend fun getHoldings(schoolId: String): List<StockHoldingEntity>

    @Query("SELECT * FROM stock_holdings WHERE schoolId = :schoolId AND stockId = :stockId")
    suspend fun getHolding(schoolId: String, stockId: String): StockHoldingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHolding(holding: StockHoldingEntity)

    @Update
    suspend fun updateHolding(holding: StockHoldingEntity)

    @Query("DELETE FROM stock_holdings WHERE schoolId = :schoolId AND stockId = :stockId")
    suspend fun deleteHolding(schoolId: String, stockId: String)

    @Query("DELETE FROM stock_holdings WHERE schoolId = :schoolId")
    suspend fun deleteHoldingsBySchool(schoolId: String)

    // K线数据
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPriceHistory(entry: StockPriceHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPriceHistories(entries: List<StockPriceHistoryEntity>)

    @Query("SELECT * FROM stock_price_history WHERE stockId = :stockId AND schoolId = :schoolId ORDER BY gameDay DESC, id DESC LIMIT :limit")
    suspend fun getPriceHistory(stockId: String, schoolId: String, limit: Int): List<StockPriceHistoryEntity>

    @Query("DELETE FROM stock_price_history WHERE schoolId = :schoolId")
    suspend fun deletePriceHistoryBySchool(schoolId: String)

    /**
     * 清理过老的股价历史记录，每支股票只保留最近 maxDays 天的数据。
     * 防止表无限膨胀导致数据库文件过大。
     */
    @Query("""
        DELETE FROM stock_price_history 
        WHERE schoolId = :schoolId 
        AND gameDay < :minDay
    """)
    suspend fun pruneOldPriceHistory(schoolId: String, minDay: Int)

    // 获取指定板块的所有股票
    @Query("SELECT * FROM stocks WHERE schoolId = :schoolId AND sector = :sector")
    suspend fun getStocksBySector(schoolId: String, sector: String): List<StockEntity>
}