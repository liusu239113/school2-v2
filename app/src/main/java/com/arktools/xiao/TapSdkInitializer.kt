package com.arktools.xiao

import android.content.Context
import android.util.Log
import com.taptap.sdk.core.TapTapSdk
import com.taptap.sdk.core.TapTapSdkOptions
import com.taptap.sdk.core.TapTapRegion
import com.taptap.sdk.compliance.option.TapTapComplianceOptions

/**
 * TapTap SDK 全局初始化器（单例）
 * 确保 SDK 只初始化一次，避免 Activity 重建时重复调用导致白屏。
 */
object TapSdkInitializer {

    private var initialized = false

    /**
     * 初始化 TapTap SDK（幂等，多次调用安全）
     * 必须在用户同意隐私政策后才能调用。
     */
    @Synchronized
    fun ensureInitialized(context: Context) {
        if (initialized) return
        try {
            val tapSdkOptions = TapTapSdkOptions(
                clientId = "hareuymxpznuvu7uqj",
                clientToken = "1sXfqQULeW0drOqd8oXjlhXa1f9RlDxx2LmpwHcw",
                region = TapTapRegion.CN,
                enableLog = BuildConfig.DEBUG
            )
            TapTapSdk.init(
                context.applicationContext,
                tapSdkOptions,
                options = arrayOf(
                    TapTapComplianceOptions(
                        showSwitchAccount = true,
                        useAgeRange = false
                    )
                )
            )
            initialized = true
            Log.i("TapSdkInitializer", "TapTap SDK initialized successfully")
        } catch (e: Exception) {
            Log.e("TapSdkInitializer", "TapTap SDK init failed", e)
        }
    }

    fun isInitialized(): Boolean = initialized
}
