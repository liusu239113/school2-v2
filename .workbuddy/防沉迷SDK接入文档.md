# TapTap 防沉迷 SDK 接入完整文档 — 校长我来当 (Kotlin版)

> 基于项目当前代码(v1.9)整理，所有路径均为项目内实际路径。
> SDK版本: tap-compliance:4.10.3

---

## 一、添加依赖

### 文件: `app/build.gradle.kts`

在第 195 行 `tap-login` 下方新增一行：

```kotlin
// === 当前已有 (第194-195行) ===
implementation("com.taptap.sdk:tap-core:4.10.3")
implementation("com.taptap.sdk:tap-login:4.10.3")

// === 新增这一行 ===
implementation("com.taptap.sdk:tap-compliance:4.10.3")
```

完整上下文：
```kotlin
    // TapTap SDK
    implementation("com.taptap.sdk:tap-core:4.10.3")
    implementation("com.taptap.sdk:tap-login:4.10.3")
    implementation("com.taptap.sdk:tap-compliance:4.10.3")  // ← 新增
```

---

## 二、SDK 初始化（含防沉迷配置）

### 文件: `app/src/main/java/com/arktools/xiaozhang/SchoolTycoonApplication.kt`

需要把现有的 `TapTapSdk.init` 调用改为包含防沉迷配置的版本。

**当前代码** (第31-37行):
```kotlin
val tapSdkOptions = TapTapSdkOptions(
    clientId = "ha7lqeih3exzc71dns",
    clientToken = "O2dSp7pg1RewA5r6tldEZh8JoT4csg9z0dc6tWxW",
    region = TapTapRegion.CN,
    enableLog = BuildConfig.DEBUG
)
TapTapSdk.init(this, tapSdkOptions)
```

**替换为**:
```kotlin
import com.taptap.sdk.compliance.option.TapTapComplianceOptions  // ← 新增import

// ... Application onCreate() 中 ...

val tapSdkOptions = TapTapSdkOptions(
    clientId = "ha7lqeih3exzc71dns",
    clientToken = "O2dSp7pg1RewA5r6tldEZh8JoT4csg9z0dc6tWxW",
    region = TapTapRegion.CN,
    enableLog = BuildConfig.DEBUG
)
TapTapSdk.init(
    this,
    tapSdkOptions,
    options = arrayOf(
        TapTapComplianceOptions(
            showSwitchAccount = true,    // 显示切换账号按钮
            useAgeRange = false          // 不获取真实年龄段（静默授权）
        )
    )
)
```

---

## 三、创建防沉迷回调管理类

### 新建文件: `app/src/main/java/com/arktools/xiaozhang/ui/login/ComplianceManager.kt`

```kotlin
package com.arktools.xiaozhang.ui.login

import android.app.Activity
import android.util.Log
import com.taptap.sdk.compliance.TapTapCompliance
import com.taptap.sdk.compliance.TapTapComplianceCallback
import com.taptap.sdk.compliance.constants.ComplianceMessage
import com.taptap.sdk.kit.internal.callback.TapTapCallback
import com.taptap.sdk.kit.internal.exception.TapTapException
import com.taptap.sdk.compliance.CheckPaymentResult
import com.google.gson.JsonElement

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
        /** 认证成功，可进入游戏 */
        fun onLoginSuccess()
        /** 退出认证（返回登录页） */
        fun onExited()
        /** 切换账号 */
        fun onSwitchAccount()
        /** 宵禁限制 */
        fun onPeriodRestrict()
        /** 无可玩时长 */
        fun onDurationLimit()
        /** 网络错误或配置错误 */
        fun onError(message: String)
    }

    private var listener: ComplianceListener? = null

    /**
     * 注册回调（在 Application.onCreate 或登录前调用）
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
                            // code=9002: 实名过程关闭窗口
                            listener.onExited()
                        }
                        else -> {
                            Log.w(TAG, "Unknown compliance code: $code")
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
    fun getRemainingTime(): Long = TapTapCompliance.getRemainingTime()

    /**
     * 退出登录时调用，重置防沉迷状态
     */
    fun exit() {
        TapTapCompliance.exit()
        listener = null
    }
}
```

---

## 四、修改登录流程

### 文件: `app/src/main/java/com/arktools/xiaozhang/ui/login/TapTapLoginScreen.kt`

在 `onLoginSuccess` 回调后启动防沉迷认证。

**当前代码** (第159-161行):
```kotlin
override fun onSuccess(result: TapTapAccount) {
    isLoggingIn = false
    onLoginSuccess(result)
}
```

**替换为**:
```kotlin
override fun onSuccess(result: TapTapAccount) {
    isLoggingIn = false
    // 登录成功后启动防沉迷认证
    val userId = result.openId ?: result.unionId ?: result.id
    if (activity != null && userId != null) {
        ComplianceManager.startup(activity, userId)
    } else {
        onLoginSuccess(result)
    }
}
```

顶部新增 import:
```kotlin
import com.arktools.xiaozhang.ui.login.ComplianceManager  // ← 新增
```

---

## 五、修改主界面防沉迷回调处理

### 文件: `app/src/main/java/com/arktools/xiaozhang/ui/main/MainScreen.kt`

在第 187 行附近（`isTapLoggedIn` 变量声明处）新增防沉迷状态管理：

```kotlin
// === 现有代码附近新增 ===

// 防沉迷状态
var complianceBlocked by remember { mutableStateOf(false) }
var complianceMessage by remember { mutableStateOf("") }

// 初始化防沉迷回调（首次组合时注册）
LaunchedEffect(Unit) {
    ComplianceManager.register(object : ComplianceManager.ComplianceListener {
        override fun onLoginSuccess() {
            complianceBlocked = false
        }
        override fun onExited() {
            complianceBlocked = true
            complianceMessage = "防沉迷认证已退出，请重新登录"
            isTapLoggedIn = false
        }
        override fun onSwitchAccount() {
            complianceBlocked = true
            complianceMessage = "正在切换账号..."
            isTapLoggedIn = false
        }
        override fun onPeriodRestrict() {
            complianceBlocked = true
            complianceMessage = "当前为宵禁时段（22:00-8:00），无法进入游戏"
        }
        override fun onDurationLimit() {
            complianceBlocked = true
            complianceMessage = "今日游戏时长已用完，请明天再来"
        }
        override fun onError(message: String) {
            complianceBlocked = true
            complianceMessage = message
        }
    })
}

// 防沉迷阻止时显示提示
if (complianceBlocked && complianceMessage.isNotEmpty()) {
    // 显示一个不可关闭的提示界面
    AlertDialog(
        onDismissRequest = { },
        title = { Text("防沉迷提示") },
        text = { Text(complianceMessage) },
        confirmButton = {
            TextButton(onClick = {
                // 返回登录页
                isTapLoggedIn = false
                complianceBlocked = false
            }) {
                Text("确定")
            }
        }
    )
}
```

顶部新增 import:
```kotlin
import com.arktools.xiaozhang.ui.login.ComplianceManager  // ← 新增
import androidx.compose.material3.AlertDialog              // ← 新增
import androidx.compose.material3.TextButton               // ← 新增
```

---

## 六、可选：游戏内充值检查（如有内购）

如果有游戏内充值功能，在发起支付前：

```kotlin
// 充值前检查（amount 单位：分，如 6元 = 600）
ComplianceManager.checkPaymentLimit(
    activity = activity,
    amount = 600,
    onAllowed = {
        // 发起支付
    },
    onError = { error ->
        // 检查失败，提示重试
    }
)

// 充值成功后上报
ComplianceManager.submitPayment(amount = 600)
```

---

## 七、可选：退出登录时清理

在用户退出登录的地方调用：

```kotlin
ComplianceManager.exit()
```

---

## 八、检查清单

- [ ] `app/build.gradle.kts` 添加 `tap-compliance:4.10.3` 依赖
- [ ] `SchoolTycoonApplication.kt` 的 `TapTapSdk.init` 增加 `TapTapComplianceOptions`
- [ ] 新建 `ComplianceManager.kt` 文件
- [ ] `TapTapLoginScreen.kt` 登录成功后调用 `ComplianceManager.startup()`
- [ ] `MainScreen.kt` 注册防沉迷回调 + 处理限制状态
- [ ] 在 TapTap 开发者中心开通「防沉迷服务」
- [ ] 测试：未成年账号登录 → 验证宵禁/时长限制
- [ ] 测试：成年账号登录 → 直接进入游戏

---

## 九、回调码速查表

| code | 常量 | 含义 | 游戏处理 |
|------|------|------|---------|
| 500 | `LOGIN_SUCCESS` | 认证通过 | 进入游戏 |
| 1000 | `EXITED` | 退出认证 | 回登录页 |
| 1001 | `SWITCH_ACCOUNT` | 切换账号 | 回登录页 |
| 1030 | `PERIOD_RESTRICT` | 宵禁限制 | 禁止进入 |
| 1050 | `DURATION_LIMIT` | 时长用完 | 禁止进入 |
| 1100 | `AGE_LIMIT` | 年龄不符 | 按规则处理 |
| 1200 | `INVALID_CLIENT_OR_NETWORK_ERROR` | 网络错误 | 提示重试 |
| 9002 | `REAL_NAME_STOP` | 实名关闭 | 重新认证 |
