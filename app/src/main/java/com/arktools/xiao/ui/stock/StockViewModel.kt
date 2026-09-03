package com.arktools.xiao.ui.stock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiao.audio.AudioManager
import com.arktools.xiao.domain.engine.GameEngine

import com.arktools.xiao.domain.model.Stock
import com.arktools.xiao.domain.model.StockHolding
import com.arktools.xiao.domain.model.StockMarketEvent
import com.arktools.xiao.domain.model.StockPricePoint
import com.arktools.xiao.domain.repository.SchoolRepository
import com.arktools.xiao.domain.repository.StockRepository

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import com.arktools.xiao.util.safeLaunch


@HiltViewModel
class StockViewModel @Inject constructor(
    private val stockRepository: StockRepository,
    private val schoolRepository: SchoolRepository,
    private val gameEngine: GameEngine,
    private val audioManager: AudioManager
) : ViewModel() {

    companion object {
        /** 股市解锁所需的最低校园等级 */
        val STOCK_UNLOCK_LEVEL = com.arktools.xiao.domain.engine.GameModule.STOCK.unlockLevel
        /** 每个校园等级允许的最大单次投资额（万） */
        const val INVEST_LIMIT_PER_LEVEL = 50.0
    }

    private val _currentCash = MutableStateFlow(0.0)
    val currentCash: StateFlow<Double> = _currentCash.asStateFlow()

    private val _campusLevel = MutableStateFlow(1)
    val campusLevel: StateFlow<Int> = _campusLevel.asStateFlow()

    /** 股市是否已解锁（校园等级 >= 3） */
    val isUnlocked: Boolean get() = _campusLevel.value >= STOCK_UNLOCK_LEVEL

    /** 当前等级允许的单次最大投资额（万） */
    val maxInvestmentPerTrade: Double get() = _campusLevel.value * INVEST_LIMIT_PER_LEVEL

    private val _stocks = MutableStateFlow<List<Stock>>(emptyList())
    val stocks: StateFlow<List<Stock>> = _stocks.asStateFlow()

    private val _holdings = MutableStateFlow<List<StockHolding>>(emptyList())
    val holdings: StateFlow<List<StockHolding>> = _holdings.asStateFlow()

    private val _selectedStock = MutableStateFlow<Stock?>(null)
    val selectedStock: StateFlow<Stock?> = _selectedStock.asStateFlow()

    private val _priceHistory = MutableStateFlow<List<StockPricePoint>>(emptyList())
    val priceHistory: StateFlow<List<StockPricePoint>> = _priceHistory.asStateFlow()

    private val _activeEvents = MutableStateFlow<List<StockMarketEvent>>(emptyList())
    val activeEvents: StateFlow<List<StockMarketEvent>> = _activeEvents.asStateFlow()

    private val _showBuyDialog = MutableStateFlow(false)
    val showBuyDialog: StateFlow<Boolean> = _showBuyDialog.asStateFlow()

    private val _showSellDialog = MutableStateFlow(false)
    val showSellDialog: StateFlow<Boolean> = _showSellDialog.asStateFlow()

    private val _buyQuantityText = MutableStateFlow("100")
    val buyQuantityText: StateFlow<String> = _buyQuantityText.asStateFlow()

    private val _sellQuantityText = MutableStateFlow("1")
    val sellQuantityText: StateFlow<String> = _sellQuantityText.asStateFlow()

    val buyQuantity: Int get() = _buyQuantityText.value.toIntOrNull() ?: 0
    val sellQuantity: Int get() = _sellQuantityText.value.toIntOrNull() ?: 0

    /** 校长个人资金（用于股票投资） */
    val personalFunds: Double get() = gameEngine.principal.personalFunds

    init {
        initializeStocks()
        observeCash()
    }

    private fun observeCash() {
        viewModelScope.safeLaunch {
            schoolRepository.getSchoolFlow().collect { school ->
                _currentCash.value = school?.cash ?: 0.0
                _campusLevel.value = school?.campusLevel ?: 1
            }
        }
    }

    private fun initializeStocks() {
        viewModelScope.safeLaunch {
            stockRepository.initializeDefaultStocks()
            stockRepository.getStocksFlow().collect {
                _stocks.value = it
            }
        }
    }

    fun refreshHoldings() {
        viewModelScope.safeLaunch {
            stockRepository.getHoldingsFlow().collect {
                val stocks = _stocks.value
                _holdings.value = it.map { holding ->
                    val stock = stocks.find { s -> s.id == holding.stockId }
                    holding.copy(currentPrice = stock?.currentPrice ?: 0.0)
                }
            }
        }
    }

    fun onStockClick(stock: Stock) {
        _selectedStock.value = stock
        loadPriceHistory(stock.id)
    }

    fun clearSelectedStock() {
        _selectedStock.value = null
        _priceHistory.value = emptyList()
    }

    private fun loadPriceHistory(stockId: String) {
        viewModelScope.safeLaunch {
            _priceHistory.value = stockRepository.getPriceHistory(stockId, 60)
        }
    }

    fun refreshActiveEvents() {
        viewModelScope.safeLaunch {
            _activeEvents.value = stockRepository.getActiveEvents()
        }
    }

    fun showBuyDialog() {
        _showBuyDialog.value = true
        _buyQuantityText.value = "100"
    }

    fun dismissBuyDialog() {
        _showBuyDialog.value = false
    }

    fun showSellDialog() {
        _showSellDialog.value = true
        _sellQuantityText.value = "1"
    }

    fun dismissSellDialog() {
        _showSellDialog.value = false
    }

    fun setBuyQuantityText(text: String) {
        // 只允许数字字符
        _buyQuantityText.value = text.filter { it.isDigit() }
    }

    fun setSellQuantityText(text: String) {
        _sellQuantityText.value = text.filter { it.isDigit() }
    }

    private val _buyError = MutableStateFlow<String?>(null)
    val buyError: StateFlow<String?> = _buyError.asStateFlow()
    fun clearBuyError() { _buyError.value = null }

    fun buyStock() {
        viewModelScope.safeLaunch {
            val stock = _selectedStock.value ?: return@safeLaunch
            val qty = buyQuantity
            if (qty < 1) return@safeLaunch
            val totalCost = stock.currentPrice * qty
            val maxAllowed = maxInvestmentPerTrade
            if (totalCost > maxAllowed) {
                _buyError.value = "单次投资上限 ${String.format("%.0f", maxAllowed)}万（校园等级${_campusLevel.value}级），请减少数量"
                return@safeLaunch
            }
            val result = gameEngine.executeStockPurchase(
                stock.id,
                qty,
                maxAllowed
            )
            if (!result.success) {
                _buyError.value = result.message
                return@safeLaunch
            }
            _showBuyDialog.value = false
            _buyError.value = null
            audioManager.playCashLose()
        }
    }

    fun sellStock() {
        viewModelScope.safeLaunch {
            val stock = _selectedStock.value ?: return@safeLaunch
            val qty = sellQuantity
            if (qty < 1) return@safeLaunch
            val result = gameEngine.executeStockSale(stock.id, qty)
            if (!result.success) {
                _buyError.value = result.message
                return@safeLaunch
            }
            _showSellDialog.value = false
            _buyError.value = null
            audioManager.playCashEarn()
        }
    }

    fun getHoldingForStock(stockId: String): StockHolding? {
        return _holdings.value.find { it.stockId == stockId }
    }

}