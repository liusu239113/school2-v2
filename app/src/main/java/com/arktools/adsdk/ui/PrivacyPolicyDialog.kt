package com.arktools.adsdk.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.arktools.adsdk.AdSdkConfig
import com.arktools.adsdk.PrivacyPolicyManager
import kotlinx.coroutines.launch

/**
 * 隐私政策弹窗 Compose 组件
 * 游戏启动时显示，用户必须同意后才能进入游戏
 *
 * @param onAccepted 用户点击同意后的回调
 * @param onDismiss 用户点击不同意后的回调（应退出应用）
 * @param appName 应用名称
 */
@Composable
fun PrivacyPolicyDialog(
    onAccepted: () -> Unit,
    onDismiss: () -> Unit = {},
    appName: String = "本应用"
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val privacyManager = remember { PrivacyPolicyManager(context) }

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A2E), RoundedCornerShape(12.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "隐私政策与用户协议",
                    color = Color(0xFFFFD700),
                    fontSize = 20.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .background(Color(0xFF16213E), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "欢迎使用$appName！",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "为保障您的权益，在使用本应用前，请您仔细阅读并同意以下条款：",
                        color = Color(0xFFAAAAAA),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "本应用收集的信息：",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val collectedItems = listOf(
                        "设备型号、操作系统版本（用于适配和优化）",
                        "设备标识符（用于广告展示和数据统计）",
                        "广告标识符 GAID（本游戏为展示广告和统计广告效果而获取）",
                        "网络类型（WiFi/移动数据，用于广告加载）",
                        "游戏存档数据（仅存储在本地设备）",
                        "应用崩溃日志（用于修复问题）"
                    )
                    collectedItems.forEach { item ->
                        Text(
                            text = "• $item",
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "信息使用目的：",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• 提供游戏服务和保存游戏进度\n• 展示广告以支持免费运营\n• 优化应用性能和修复问题",
                        color = Color(0xFFAAAAAA),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "第三方 SDK 收集的信息及目的：",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val sdkItems = listOf(
                        "优量汇SDK：获取 AndroidID、设备IP（用于广告展示与投放、反作弊和安全风控）",
                        "AdGain SDK：获取 AndroidID、OAID、WiFi BSSID（用于广告展示、效果归因与数据统计）",
                        "优推广告SDK：获取 OAID（用于广告展示与投放）",
                        "Tap登录SDK：获取 AndroidID（用于账号登录与身份鉴权）"
                    )
                    sdkItems.forEach { item ->
                        Text(
                            text = "• $item",
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 完整隐私政策链接
                    if (AdSdkConfig.privacyPolicyUrl.isNotEmpty()) {
                        Text(
                            text = "点击",
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp
                        )
                        LinkText(
                            text = "《查看完整隐私政策》",
                            url = AdSdkConfig.privacyPolicyUrl,
                            onLinkClick = { openUrl(context, it) }
                        )
                        Text(
                            text = "了解第三方 SDK 详情及您的权利。",
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = { onDismiss() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF444444)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("不同意并退出", color = Color.White, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                privacyManager.acceptPrivacyPolicy(true)
                                onAccepted()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("同意并继续", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkText(text: String, url: String, onLinkClick: (String) -> Unit) {
    Text(
        text = buildAnnotatedString {
            withStyle(
                style = SpanStyle(
                    color = Color(0xFF4CAF50),
                    textDecoration = TextDecoration.Underline,
                    fontSize = 12.sp
                )
            ) {
                append(text)
            }
        },
        modifier = Modifier.clickable { onLinkClick(url) }
    )
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}
