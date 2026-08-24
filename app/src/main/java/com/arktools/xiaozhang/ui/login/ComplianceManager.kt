package com.arktools.xiaozhang.ui.login

import android.app.Activity
import android.util.Log
import com.taptap.sdk.compliance.TapTapCompliance
import com.taptap.sdk.compliance.TapTapComplianceCallback
import com.taptap.sdk.compliance.constants.ComplianceMessage
import com.taptap.sdk.kit.internal.callback.TapTapCallback
import com.taptap.sdk.kit.internal.exception.TapTapException
import com.taptap.sdk.compliance.bean.CheckPaymentResult
import kotlinx.serialization.json.JsonElement

/**
 * TapTap 防沉迷管理
 *
 * 使用方式:
 *   1. 登录成功后调用 ComplianceManager.startup(activity, userId)
 *   2. 设置回调监听认证结果
 *   3. 充值前调用 checkPaymentLimit
 *   4. 充值后调用 submitPayment
 *   5. 退出登录时调用 exit
 */
object ComplianceManager {

    private const val TAG = "ComplianceManager"

    /** 防沉迷认证结果回调 */
    interface ComplianceListener {
        /** 认证成功（code=500），可进入游戏 */
        fun onLoginSuccess()
        /** 退出认证（code=1000），游戏应返回登录页 */
        fun onExited()
        /** 切换账号（code=1001），游戏应返回登录页 */
        fun onSwitchAccount()
        /** 宵禁限制（code=1030），不可进入游戏 */
        fun onPeriodRestrict()
        /** 无可玩时长（code=1050），不可进入游戏 */
        fun onDurationLimit()
        /** 年龄限制（code=1100），不可进入游戏 */
        fun onAgeLimit()
        /** 实名过程中关闭窗口（code=9002），应重新开始认证或退出 */
        fun onRealNameStop()
        /** 网络错误或配置错误（code=1200） */
        fun onError(message: String)
    }

    private var listener: ComplianceListener? = null

    /**
     * 注册回调（在登录前调用）
     */
    fun register(listener: ComplianceListener) {
        this.listener = listener
        TapTapCompliance.registerComplianceCallback(
            callback = object : TapTapComplianceCallback {
                override fun onComplianceResult(code: Int, extra: Map<String, Any>?) {
                    Log.d(TAG, "onComplianceResult: code=$code, extra=$extra")
                    when (code) {
                        ComplianceMessage.LOGIN_SUCCESS -> {
                            // code=500: 玩家正常进入游戏
                            listener.onLoginSuccess()
                        }
                        ComplianceMessage.EXITED -> {
                            // code=1000: 退出防沉迷认证
                            listener.onExited()
                        }
                        ComplianceMessage.SWITCH_ACCOUNT -> {
                            // code=1001: 用户点击切换账号
                            listener.onSwitchAccount()
                        }
                        ComplianceMessage.PERIOD_RESTRICT -> {
                            // code=1030: 宵禁
                            listener.onPeriodRestrict()
                        }
                        ComplianceMessage.DURATION_LIMIT -> {
                            // code=1050: 无可玩时长
                            listener.onDurationLimit()
                        }
                        ComplianceMessage.INVALID_CLIENT_OR_NETWORK_ERROR -> {
                            // code=1200: 网络/配置错误
                            listener.onError("数据请求失败，请检查网络连接")
                        }
                        ComplianceMessage.REAL_NAME_STOP -> {
                            // code=9002: 实名过程关闭窗口，不可跳过
                            listener.onRealNameStop()
                        }
                        else -> {
                            // 包括 code=1100 (AGE_LIMIT) 等未定义常量的情况
                            if (code == 1100) {
                                listener.onAgeLimit()
                            } else {
                                Log.w(TAG, "Unknown compliance code: $code")
                            }
                        }
                    }
                }
            }
        )
    }

    /**
     * 开始防沉迷认证
     * @param activity 当前 Activity
     * @param userId 玩家唯一标识（建议用 TapTap openId/unionId）
     */
    fun startup(activity: Activity, userId: String) {
        Log.d(TAG, "startup: userId=$userId")
        TapTapCompliance.startup(activity, userId)
    }

    /**
     * 充值前检查是否有限制
     * @param amount 充值金额，单位：分
     */
    fun checkPaymentLimit(
        activity: Activity,
        amount: Int,
        onAllowed: () -> Unit,
        onError: (String) -> Unit
    ) {
        TapTapCompliance.checkPaymentLimit(
            activity,
            amount,
            object : TapTapCallback<CheckPaymentResult> {
                override fun onSuccess(result: CheckPaymentResult) {
                    if (result.status) {
                        onAllowed()
                    }
                    // 被限制时 SDK 会自动弹窗提示
                }
                override fun onFail(exception: TapTapException) {
                    onError(exception.message ?: "检查失败")
                }
            }
        )
    }

    /**
     * 上报充值金额（充值成功后调用）
     * @param amount 充值金额，单位：分
     */
    fun submitPayment(amount: Int) {
        TapTapCompliance.submitPayment(
            amount,
            object : TapTapCallback<JsonElement> {
                override fun onSuccess(result: JsonElement) {
                    Log.d(TAG, "submitPayment success: amount=$amount")
                }
                override fun onFail(exception: TapTapException) {
                    Log.e(TAG, "submitPayment failed: ${exception.message}")
                }
            }
        )
    }

    /**
     * 获取玩家年龄段
     * @return -1未知, 0(0-7岁), 8(8-15岁), 16(16-17岁), 18(成年)
     */
    fun getAgeRange(): Int = TapTapCompliance.getAgeRange()

    /**
     * 获取剩余可玩时长（秒）
     */
    fun getRemainingTime(): Int = TapTapCompliance.getRemainingTime()

    /**
     * 退出登录时调用，重置防沉迷状态
     * 注意：不清除 listener，因为 MainScreen 中注册的回调在整个生命周期有效
     */
    fun exit() {
        TapTapCompliance.exit()
    }
}
