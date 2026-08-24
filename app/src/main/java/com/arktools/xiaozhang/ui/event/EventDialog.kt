package com.arktools.xiaozhang.ui.event

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.arktools.xiaozhang.R
import com.arktools.xiaozhang.domain.model.GameEvent
import com.arktools.xiaozhang.ui.components.PixelButton
import com.arktools.xiaozhang.ui.components.PixelButtonStyle
import com.arktools.xiaozhang.ui.effects.ConfettiEffect
import com.arktools.xiaozhang.ui.theme.AccentGreen
import com.arktools.xiaozhang.ui.theme.AccentOrange
import com.arktools.xiaozhang.ui.theme.AccentRed
import kotlinx.coroutines.delay

@Composable
fun EventDialogContainer(
    viewModel: EventViewModel = hiltViewModel()
) {
    val currentEvent by viewModel.currentEvent.collectAsState()

    currentEvent?.let { event ->
        when (event) {
            is GameEvent.PositiveEvent -> PositiveEventDialog(
                event = event,
                onDismiss = { viewModel.dismissEvent() },
                onAdRewarded = { viewModel.onPositiveEventAdRewarded() }
            )
            is GameEvent.NegativeEvent -> NegativeEventDialog(
                event = event,
                onDismiss = { viewModel.dismissEvent() },
                onAdRewarded = { viewModel.onNegativeEventAdRewarded() }
            )
            is GameEvent.ChoiceEvent -> ChoiceEventDialog(
                event = event,
                onChoiceSelected = { viewModel.handleChoice(it) }
            )
            is GameEvent.MilestoneEvent -> MilestoneEventDialog(
                event = event,
                onDismiss = { viewModel.dismissEvent() }
            )
        }
    }
}

@Composable
private fun PositiveEventDialog(
    event: GameEvent.PositiveEvent,
    onDismiss: () -> Unit,
    onAdRewarded: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val hasBonus = event.bonusCash > 0.0 || event.bonusReputation > 0L
    var isAdLoading by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .padding(16.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.dialog_bg),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.FillBounds
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ThumbUp,
                    contentDescription = null,
                    tint = AccentGreen,
                    modifier = Modifier.height(32.dp)
                )
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = event.message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                event.bonusCash?.let {
                    Text(
                        text = "资金 +${it}万",
                        color = AccentGreen,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                event.bonusReputation?.let {
                    Text(
                        text = "声誉 +$it",
                        color = AccentGreen,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (hasBonus) {
                    PixelButton(
                        text = "看视频双倍奖励",
                        onClick = {
                            val activity = context as? android.app.Activity
                            if (activity != null) {
                                com.arktools.adsdk.AdHelper.showRewardAd(
                                    activity = activity,
                                    onRewarded = { onAdRewarded() },
                                    onFailed = {
                                        isAdLoading = false
                                    },
                                    onLoadStart = { isAdLoading = true },
                                    onComplete = { isAdLoading = false }
                                )
                            }
                        },
                        style = PixelButtonStyle.CONFIRM,
                        modifier = Modifier.fillMaxWidth(),
                        height = 44.dp
                    )
                }
                PixelButton(
                    text = if (hasBonus) "跳过" else "太棒了！",
                    onClick = onDismiss,
                    style = if (hasBonus) PixelButtonStyle.CANCEL else PixelButtonStyle.CONFIRM,
                    modifier = Modifier.fillMaxWidth(),
                    height = 44.dp
                )
            }

            if (isAdLoading) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(40.dp),
                            color = Color.White,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "广告加载中...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NegativeEventDialog(
    event: GameEvent.NegativeEvent,
    onDismiss: () -> Unit,
    onAdRewarded: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val hasPenalty = event.penaltyCash > 0.0 || event.penaltyReputation > 0L
    var isAdLoading by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .padding(16.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.dialog_bg),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.FillBounds
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = AccentRed,
                    modifier = Modifier.height(32.dp)
                )
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = event.message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                event.penaltyCash?.let {
                    Text(
                        text = "资金 -${it}万",
                        color = AccentRed,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                event.penaltyReputation?.let {
                    Text(
                        text = "声誉 -$it",
                        color = AccentRed,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (hasPenalty) {
                    PixelButton(
                        text = "看视频免除处罚",
                        onClick = {
                            val activity = context as? android.app.Activity
                            if (activity != null) {
                                com.arktools.adsdk.AdHelper.showRewardAd(
                                    activity = activity,
                                    onRewarded = { onAdRewarded() },
                                    onFailed = {
                                        isAdLoading = false
                                    },
                                    onLoadStart = { isAdLoading = true },
                                    onComplete = { isAdLoading = false }
                                )
                            }
                        },
                        style = PixelButtonStyle.CONFIRM,
                        modifier = Modifier.fillMaxWidth(),
                        height = 44.dp
                    )
                }
                PixelButton(
                    text = "知道了",
                    onClick = onDismiss,
                    style = PixelButtonStyle.DANGER,
                    modifier = Modifier.fillMaxWidth(),
                    height = 44.dp
                )
            }

            if (isAdLoading) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(40.dp),
                            color = Color.White,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "广告加载中...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceEventDialog(
    event: GameEvent.ChoiceEvent,
    onChoiceSelected: (Int) -> Unit
) {
    // 签字动画状态：-1=未签字（显示选项），>=0=正在签字（对应choiceIndex）
    // key=event 确保事件切换时状态重置，避免第二个签字弹窗卡住
    var signingChoiceIndex by remember(event) { mutableIntStateOf(-1) }
    var signatureComplete by remember(event) { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .padding(16.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.dialog_bg),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.FillBounds
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Help,
                    contentDescription = null,
                    tint = AccentOrange,
                    modifier = Modifier.height(32.dp)
                )
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = event.message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (signingChoiceIndex < 0) {
                    // 正常选项阶段 - 拒绝/不参加选项置顶显示
                    val declineKeywords = listOf("拒绝", "不参加", "不批准", "不同意", "放弃", "取消", "婉拒", "谢绝")
                    val sortedChoices = event.choices.mapIndexed { i, c -> i to c }
                        .sortedByDescending { (_, c) ->
                            if (declineKeywords.any { kw -> c.text.contains(kw) }) 1 else 0
                        }
                    sortedChoices.forEach { (originalIndex, choice) ->
                        val isDecline = declineKeywords.any { kw -> choice.text.contains(kw) }
                        PixelButton(
                            text = choice.text,
                            onClick = {
                                if (choice.consequence.requiresSignature) {
                                    // 进入签字动画
                                    signingChoiceIndex = originalIndex
                                } else {
                                    onChoiceSelected(originalIndex)
                                }
                            },
                            style = when {
                                isDecline -> PixelButtonStyle.CANCEL
                                originalIndex == 0 -> PixelButtonStyle.PRIMARY
                                else -> PixelButtonStyle.SECONDARY
                            },
                            modifier = Modifier.fillMaxWidth(),
                            height = 44.dp
                        )
                    }
                } else {
                    // 签字动画阶段
                    val viewModel: EventViewModel = hiltViewModel()
                    val school by viewModel.currentSchool.collectAsState()
                    val principalName = school?.principalName ?: "校长"

                    SignatureAnimation(
                        principalName = principalName,
                        onComplete = {
                            signatureComplete = true
                        }
                    )

                    // 签字完成后自动延迟关闭，无需额外点击确认
                    if (signatureComplete) {
                        LaunchedEffect(Unit) {
                            delay(1200L)
                            onChoiceSelected(signingChoiceIndex)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 校长签字打字机动画效果
 * principalName 由外部传入，避免在嵌套 composable 中调用 hiltViewModel() 导致崩溃
 */
@Composable
private fun SignatureAnimation(
    principalName: String,
    onComplete: () -> Unit
) {
    var displayedChars by remember { mutableIntStateOf(0) }
    var showStamp by remember { mutableStateOf(false) }

    LaunchedEffect(principalName) {
        // 打字机效果：每个字 200ms
        for (i in 1..principalName.length) {
            delay(200L)
            displayedChars = i
        }
        // 签完名后稍等，再显示"已批准"印章
        delay(400L)
        showStamp = true
        onComplete()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "━━━ 校长签字 ━━━",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF757575),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 签名区域
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = principalName.take(displayedChars),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    fontFamily = FontFamily.Cursive,
                    color = Color(0xFF1A237E) // 深蓝色钢笔色
                ),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 已批准印章效果
        AnimatedVisibility(
            visible = showStamp,
            enter = fadeIn()
        ) {
            Text(
                text = "【已批准】",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD32F2F) // 红色印章
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MilestoneEventDialog(
    event: GameEvent.MilestoneEvent,
    onDismiss: () -> Unit
) {
    var showConfetti by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .padding(16.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.dialog_bg),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.FillBounds
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = AccentOrange,
                    modifier = Modifier.height(36.dp)
                )
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = event.message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                PixelButton(
                    text = "继续加油！",
                    onClick = {
                        showConfetti = false
                        onDismiss()
                    },
                    style = PixelButtonStyle.PRIMARY,
                    modifier = Modifier.fillMaxWidth(),
                    height = 44.dp
                )
            }
        }
    }

    if (showConfetti) {
        ConfettiEffect(
            originX = 200f,
            originY = 300f,
            onFinished = { showConfetti = false }
        )
    }
}
