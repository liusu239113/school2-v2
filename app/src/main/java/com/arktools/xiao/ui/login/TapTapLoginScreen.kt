package com.arktools.xiao.ui.login

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arktools.xiao.R
import com.arktools.xiao.ui.theme.Primary
import com.taptap.sdk.login.TapTapLogin
import com.taptap.sdk.login.TapTapAccount
import com.taptap.sdk.login.Scopes
import com.taptap.sdk.kit.internal.callback.TapTapCallback
import com.taptap.sdk.kit.internal.exception.TapTapException
import kotlinx.coroutines.delay

/**
 * TapTap 登录界面
 * 在进入游戏前要求用户登录 TapTap 账号
 */
@Composable
fun TapTapLoginScreen(
    onLoginSuccess: (TapTapAccount) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var isLoggingIn by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var logoVisible by remember { mutableStateOf(false) }
    var buttonVisible by remember { mutableStateOf(false) }

    // 进入动画
    LaunchedEffect(Unit) {
        delay(200)
        logoVisible = true
        delay(400)
        buttonVisible = true
    }

    // 自动检查登录状态
    LaunchedEffect(Unit) {
        val currentAccount = TapTapLogin.getCurrentTapAccount()
        if (currentAccount != null) {
            // 已经登录过，通知上层（防沉迷由 MainScreen 统一启动）
            onLoginSuccess(currentAccount)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .paint(
                painter = painterResource(id = R.drawable.bg_main_menu_v2),
                contentScale = ContentScale.Crop
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo 动画
            AnimatedVisibility(
                visible = logoVisible,
                enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { -100 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(id = R.drawable.logo_game_v2),
                        contentDescription = "校长我来当 2",
                        modifier = Modifier.size(180.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "校长我来当 2",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Primary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "登录 TapTap 账号开始游戏",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(60.dp))

            // 登录按钮
            AnimatedVisibility(
                visible = buttonVisible,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { 50 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isLoggingIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = Primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "正在登录...",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    } else {
                        Button(
                            onClick = {
                                if (activity == null) return@Button
                                isLoggingIn = true
                                loginError = null

                                val scopes = arrayOf(Scopes.SCOPE_PUBLIC_PROFILE)
                                try {
                                    TapTapLogin.loginWithScopes(
                                        activity,
                                        scopes,
                                        object : TapTapCallback<TapTapAccount> {
                                            override fun onSuccess(result: TapTapAccount) {
                                                isLoggingIn = false
                                                // 登录成功，通知上层（防沉迷由 MainScreen 统一启动）
                                                onLoginSuccess(result)
                                            }

                                            override fun onCancel() {
                                                isLoggingIn = false
                                                loginError = "登录已取消"
                                            }

                                            override fun onFail(exception: TapTapException) {
                                                isLoggingIn = false
                                                loginError = "登录失败: ${exception.message}"
                                            }
                                        }
                                    )
                                } catch (e: Exception) {
                                    // P2: SDK 内部可能抛出 ActivityNotFoundException
                                    // （设备未安装 TapTap 客户端时 Fragment 启动失败）
                                    isLoggingIn = false
                                    loginError = "登录服务不可用，请安装或更新 TapTap 客户端"
                                    android.util.Log.e("TapTapLogin", "loginWithScopes crashed: ${e.message}", e)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Primary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "TapTap 登录",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        // 错误提示
                        val capturedLoginError = loginError
                        if (capturedLoginError != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = capturedLoginError,
                                fontSize = 13.sp,
                                color = Color(0xFFE53935),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
