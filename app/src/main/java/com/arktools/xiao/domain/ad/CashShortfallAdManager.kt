package com.arktools.xiao.domain.ad

import com.arktools.xiao.data.pref.SettingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class CashShortfallOffer(
    val needed: Double,
    val currentCash: Double,
    val shortfall: Double,
    val actionLabel: String
)

@Singleton
class CashShortfallAdManager @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        const val DAILY_LIMIT = 8
    }

    private val _offer = MutableStateFlow<CashShortfallOffer?>(null)
    val offer: StateFlow<CashShortfallOffer?> = _offer.asStateFlow()

    private val _remaining = MutableStateFlow(DAILY_LIMIT)
    val remaining: StateFlow<Int> = _remaining.asStateFlow()

    suspend fun refreshRemaining() {
        _remaining.value = (DAILY_LIMIT - settingsDataStore.getCashShortfallAdCount()).coerceAtLeast(0)
    }

    fun offerIfShort(needed: Double, currentCash: Double, actionLabel: String): Boolean {
        val shortfall = needed - currentCash
        if (needed <= 0.0 || shortfall <= 0.0) return false
        _offer.value = CashShortfallOffer(
            needed = needed,
            currentCash = currentCash,
            shortfall = shortfall,
            actionLabel = actionLabel
        )
        return true
    }

    fun dismiss() {
        _offer.value = null
    }

    suspend fun canWatch(): Boolean {
        refreshRemaining()
        return _remaining.value > 0
    }

    suspend fun consumeWatch(): Boolean {
        if (!canWatch()) return false
        val used = settingsDataStore.incrementCashShortfallAdCount()
        _remaining.value = (DAILY_LIMIT - used).coerceAtLeast(0)
        return true
    }
}
