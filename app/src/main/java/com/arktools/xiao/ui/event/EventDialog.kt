package com.arktools.xiao.ui.event

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
import com.arktools.xiao.R
import com.arktools.xiao.domain.model.GameEvent
import com.arktools.xiao.ui.components.PixelButton
import com.arktools.xiao.ui.components.PixelButtonStyle
import com.arktools.xiao.ui.effects.ConfettiEffect
import com.arktools.xiao.ui.theme.AccentGreen
import com.arktools.xiao.ui.theme.AccentOrange
import com.arktools.xiao.ui.theme.AccentRed
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

private fun stripChoiceNumbers(text: String): String {
    val stripped = text
        .replace(Regex("[（(][^）)]*[+\\-＋－]\\s*\\d[^）)]*[)）]"), "")
        .replace(Regex("（花费[^）]*）"), "")
        .replace(Regex("\\(花费[^)]*\\)"), "")
        .replace(Regex("声誉[＋+\\-]\\d+"), "")
        .replace(Regex("经费[＋+\\-]\\d+(\\.\\d+)?万?"), "")
        .trim()
    return stripped.ifBlank { text }
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
                if (event.bonusCash > 0.0) {
                    Text(
                        text = "资金 +${event.bonusCash}万",
                        color = AccentGreen,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (event.bonusReputation > 0L) {
                    Text(
                        text = "声誉 +${event.bonusReputation}",
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
                if (event.penaltyCash > 0.0) {
                    Text(
                        text = "资金 -${event.penaltyCash}万",
                        color = AccentRed,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (event.penaltyReputation > 0L) {
                    Text(
                        text = "声誉 -${event.penaltyReputation}",
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
    var signingChoiceIndex by remember(event) { mutableIntStateOf(-1) }
    var signatureComplete by remember(event) { mutableStateOf(false) }
    var revealedIndex by remember(event) { mutableIntStateOf(-1) }

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

                if (revealedIndex >= 0) {
                    val picked = event.choices.getOrNull(revealedIndex)
                    val c = picked?.consequence
                    Text(
                        "处理结果",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF14648C)
                    )
                    Text(
                        stripChoiceNumbers(picked?.text.orEmpty()),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    if (c != null) {
                        val cash = c.cashChange
                        val rep = c.reputationChange
                        if (cash != 0.0) {
                            Text(
                                if (cash > 0) "经费 +${"%.1f".format(cash)}万" else "经费 ${"%.1f".format(cash)}万",
                                color = if (cash > 0) AccentGreen else AccentRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (rep != 0L) {
                            Text(
                                if (rep > 0) "声誉 +$rep" else "声誉 $rep",
                                color = if (rep > 0) AccentGreen else AccentRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (c.teacherLoyaltyChange != 0) {
                            Text("教师忠诚 ${if (c.teacherLoyaltyChange > 0) "+" else ""}${c.teacherLoyaltyChange}")
                        }
                        if (cash == 0.0 && rep == 0L && c.teacherLoyaltyChange == 0) {
                            Text("这件事没有立刻改账本，但会记进口碑和后续事件。")
                        }
                    }
                    PixelButton(
                        text = "知道了",
                        onClick = { onChoiceSelected(revealedIndex) },
                        style = PixelButtonStyle.CONFIRM,
                        modifier = Modifier.fillMaxWidth(),
                        height = 44.dp
                    )
                } else if (signingChoiceIndex < 0) {
                    event.choices.forEachIndexed { originalIndex, choice ->
                        PixelButton(
                            text = stripChoiceNumbers(choice.text),
                            onClick = {
                                if (choice.consequence.requiresSignature) {
                                    signingChoiceIndex = originalIndex
                                } else {
                                    revealedIndex = originalIndex
                                }
                            },
                            style = PixelButtonStyle.SECONDARY,
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
                            delay(800L)
                            revealedIndex = signingChoiceIndex
                            signingChoiceIndex = -1
                            signatureComplete = false
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
