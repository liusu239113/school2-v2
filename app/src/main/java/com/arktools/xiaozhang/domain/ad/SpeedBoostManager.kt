package com.arktools.xiaozhang.domain.ad

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 管理加速广告的解锁状态
 * 看一次广告解锁 2x/3x/5x 加速，持续20分钟
 * 退出游戏后buff持久化，重进继续计时
 */
@Singleton
class SpeedBoostManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val BOOST_DURATION_MS = 20 * 60 * 1000L // 20分钟
        private const val PREFS_NAME = "speed_boost_prefs"
        private const val KEY_EXPIRE_TIME = "boost_expire_time"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _boostExpireTime = MutableStateFlow(prefs.getLong(KEY_EXPIRE_TIME, 0L))
    val boostExpireTime: StateFlow<Long> = _boostExpireTime.asStateFlow()

    /**
     * 是否当前加速已解锁
     */
    fun isBoostActive(): Boolean {
        return System.currentTimeMillis() < _boostExpireTime.value
    }

    /**
     * 获取剩余秒数
     */
    fun getRemainingSeconds(): Int {
        val remaining = _boostExpireTime.value - System.currentTimeMillis()
        return if (remaining > 0) (remaining / 1000).toInt() else 0
    }

    /**
     * 观看广告成功后调用，激活加速
     */
    fun activateBoost() {
        val expireTime = System.currentTimeMillis() + BOOST_DURATION_MS
        _boostExpireTime.value = expireTime
        prefs.edit().putLong(KEY_EXPIRE_TIME, expireTime).apply()
    }

    /**
     * 加速到期后重置速度为1x
     */
    fun isSpeedAllowed(speed: Float): Boolean {
        if (speed <= 1f) return true
        return isBoostActive()
    }
}
