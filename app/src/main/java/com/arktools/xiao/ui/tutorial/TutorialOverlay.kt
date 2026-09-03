package com.arktools.xiao.ui.tutorial

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arktools.xiao.ui.theme.Primary
import com.arktools.xiao.ui.theme.AccentGreen
import com.arktools.xiao.ui.theme.AccentOrange
import kotlinx.coroutines.delay

// ==================== 主入口 ====================

@Composable
fun TutorialOverlay(
    tutorialManager: TutorialManager,
    onDismiss: () -> Unit
) {
    if (!tutorialManager.isActive) {
        onDismiss()
        return
    }

    val step = tutorialManager.currentStep

    when (step.mode) {
        TutorialMode.STORY -> StoryDialogOverlay(
            step = step,
            manager = tutorialManager,
            onSkip = {
                tutorialManager.dismiss()
                onDismiss()
            }
        )
        TutorialMode.HIGHLIGHT -> HighlightOverlay(
            step = step,
            manager = tutorialManager,
            onSkip = {
                tutorialManager.dismiss()
                onDismiss()
            }
        )
        TutorialMode.ACTION -> ActionWaitOverlay(
            step = step,
            manager = tutorialManager,
            onSkip = {
                tutorialManager.dismiss()
                onDismiss()
            }
        )
    }
}

// ==================== 模式1：剧情对话 ====================

@Composable
private fun StoryDialogOverlay(
    step: TutorialStepData,
    manager: TutorialManager,
    onSkip: () -> Unit
) {
    // 打字机效果
    var displayedText by remember(step.text) { mutableStateOf("") }
    var isTypingDone by remember(step.text) { mutableStateOf(false) }

    LaunchedEffect(step.text) {
        displayedText = ""
        isTypingDone = false
        for (i in step.text.indices) {
            displayedText = step.text.substring(0, i + 1)
            delay(30L) // 打字速度
        }
        isTypingDone = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (isTypingDone) {
                    // 点击继续
                    if (step.completionCondition == CompletionCondition.TAP_CONTINUE) {
                        manager.nextStep()
                    }
                } else {
                    // 跳过打字，直接显示全文
                    displayedText = step.text
                    isTypingDone = true
                }
            }
    ) {
        // 跳过按钮（右上角）
        TextButton(
            onClick = onSkip,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Text(
                "跳过教程 >>",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp
            )
        }

        // 对话框区域（底部）
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 说话人名字标签
            step.speaker?.let { speaker ->
                Surface(
                    color = getSpeakerColor(speaker).copy(alpha = 0.9f),
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                ) {
                    Text(
                        text = speaker,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 对话框主体
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(
                    topStart = if (step.speaker != null) 0.dp else 16.dp,
                    topEnd = 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp
                ),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xF0FFFFFF)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // 对话文本
                    Text(
                        text = displayedText,
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                        color = Color(0xFF212121)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 底部提示
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 进度
                        Text(
                            text = "${manager.currentStepIndex + 1}/${manager.totalSteps}",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )

                        // 继续提示（闪烁）
                        if (isTypingDone) {
                            val alpha by rememberInfiniteTransition(label = "blink")
                                .animateFloat(
                                    initialValue = 0.4f,
                                    targetValue = 1f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(800),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "blinkAlpha"
                                )
                            Text(
                                text = "点击继续 >",
                                fontSize = 13.sp,
                                color = Primary.copy(alpha = alpha),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== 模式2：高亮指引 ====================

@Composable
private fun HighlightOverlay(
    step: TutorialStepData,
    manager: TutorialManager,
    onSkip: () -> Unit
) {
    val needsAction = step.completionCondition != CompletionCondition.TAP_CONTINUE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (!needsAction) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { manager.nextStep() }
                } else Modifier
            )
    ) {
        // 半透明遮罩（根据高亮目标留不同区域）
        // 顶部遮罩
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(getHighlightTopOffset(step.highlightTarget))
                .background(Color.Black.copy(alpha = 0.7f))
                .align(Alignment.TopCenter)
        )
        // 底部遮罩
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(getHighlightBottomHeight(step.highlightTarget))
                .background(Color.Black.copy(alpha = 0.7f))
                .align(Alignment.BottomCenter)
        )

        // 跳过按钮
        TextButton(
            onClick = onSkip,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 4.dp, end = 8.dp)
        ) {
            Text("跳过 >>", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        }

        // 引导气泡（根据高亮位置决定放在上方还是下方）
        val bubbleAlignment = when (step.highlightTarget) {
            HighlightTarget.STATUS_BAR, HighlightTarget.TOP_BAR, HighlightTarget.PAUSE_BUTTON ->
                Alignment.Center
            HighlightTarget.TAB_OVERVIEW, HighlightTarget.TAB_TEACHING,
            HighlightTarget.TAB_TEACHER, HighlightTarget.TAB_RESEARCH,
            HighlightTarget.TAB_DISTRICT ->
                Alignment.Center
            else -> Alignment.Center
        }

        // 引导信息卡片
        Card(
            modifier = Modifier
                .align(bubbleAlignment)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xF8FFFFFF)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 箭头指示（如果有）
                if (step.arrowDirection == ArrowDirection.UP) {
                    PulsingArrow(direction = ArrowDirection.UP)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 主标题
                Text(
                    text = step.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Primary,
                    textAlign = TextAlign.Center
                )

                // 副文本
                step.subText?.let { sub ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = sub,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = Color(0xFF555555),
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 底部：进度 + 操作提示
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${manager.currentStepIndex + 1}/${manager.totalSteps}",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )

                    if (needsAction) {
                        // 需要操作才能继续
                        val pulseAlpha by rememberInfiniteTransition(label = "pulse")
                            .animateFloat(
                                initialValue = 0.6f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(600),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "pulseAlpha"
                            )
                        Text(
                            text = getActionHint(step.completionCondition),
                            fontSize = 12.sp,
                            color = AccentOrange.copy(alpha = pulseAlpha),
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "点击继续 >",
                            fontSize = 12.sp,
                            color = Primary.copy(alpha = 0.7f)
                        )
                    }
                }

                // 箭头指示（如果是向下的）
                if (step.arrowDirection == ArrowDirection.DOWN) {
                    Spacer(modifier = Modifier.height(8.dp))
                    PulsingArrow(direction = ArrowDirection.DOWN)
                }
            }
        }
    }
}

// ==================== 模式3：操作等待 ====================

@Composable
private fun ActionWaitOverlay(
    step: TutorialStepData,
    manager: TutorialManager,
    onSkip: () -> Unit
) {
    // 操作等待模式：提示条放在顶部状态栏下方，不遮挡游戏关键信息
    Box(modifier = Modifier.fillMaxWidth()) {
        // 操作提示条 — 留出顶部空间给 TopAppBar + StatusBar（约120dp）
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 120.dp, bottom = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Primary.copy(alpha = 0.95f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 主提示
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 闪烁的圆点
                        val dotAlpha by rememberInfiniteTransition(label = "dot")
                            .animateFloat(
                                initialValue = 0.3f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(500),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "dotAlpha"
                            )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(AccentGreen.copy(alpha = dotAlpha))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = step.text,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 跳过
                    TextButton(
                        onClick = onSkip,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("跳过", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                }

                // 副文本
                step.subText?.let { sub ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = sub,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}

// ==================== 辅助组件 ====================

/**
 * 脉冲箭头
 */
@Composable
private fun PulsingArrow(direction: ArrowDirection) {
    val offset by rememberInfiniteTransition(label = "arrow")
        .animateFloat(
            initialValue = 0f,
            targetValue = 8f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "arrowOffset"
        )

    val icon = when (direction) {
        ArrowDirection.UP -> Icons.Default.KeyboardArrowUp
        ArrowDirection.DOWN -> Icons.Default.KeyboardArrowDown
        ArrowDirection.LEFT -> Icons.Default.KeyboardArrowLeft
        ArrowDirection.RIGHT -> Icons.Default.KeyboardArrowRight
        else -> return
    }

    val offsetModifier = when (direction) {
        ArrowDirection.UP -> Modifier.offset(y = (-offset).dp)
        ArrowDirection.DOWN -> Modifier.offset(y = offset.dp)
        ArrowDirection.LEFT -> Modifier.offset(x = (-offset).dp)
        ArrowDirection.RIGHT -> Modifier.offset(x = offset.dp)
        else -> Modifier
    }

    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier
            .size(32.dp)
            .then(offsetModifier),
        tint = AccentOrange
    )
}

// ==================== 工具函数 ====================

private fun getSpeakerColor(speaker: String): Color {
    return when {
        "局长" in speaker -> Color(0xFF1565C0)   // 蓝色-官方
        "主任" in speaker || "主管" in speaker -> Color(0xFF2E7D32)  // 绿色-内部员工
        "系统" in speaker -> Color(0xFF6A1B9A)   // 紫色-系统
        "旁白" in speaker -> Color(0xFF37474F)   // 深灰-旁白
        else -> Color(0xFF455A64)                 // 默认灰
    }
}

private fun getActionHint(condition: CompletionCondition): String {
    return when (condition) {
        CompletionCondition.TAP_TAB_TEACHER -> "请点击「人事」标签 ↓"
        CompletionCondition.TAP_TAB_TEACHING -> "请点击「治院」标签 ↓"
        CompletionCondition.TAP_TAB_RESEARCH -> "请点击「外联」标签 ↓"
        CompletionCondition.TAP_TAB_OVERVIEW -> "请点击「校园」标签 ↓"
        CompletionCondition.TAP_TAB_DISTRICT -> "请点击「外联」标签 ↓"
        CompletionCondition.HIRE_TEACHER -> "等待你招聘教师..."
        CompletionCondition.CONFIGURE_TEACHING -> "等待你完成教学配置..."
        CompletionCondition.NAVIGATE_STUDENT -> "请进入学生生活或学生事务"
        CompletionCondition.NAVIGATE_FACILITY -> "请回到校园点开一座建筑"
        CompletionCondition.NAVIGATE_REPORT -> "请在治院打开数据报表"
        CompletionCondition.WAIT_ENROLLMENT -> "等待学生入学中..."
        CompletionCondition.WAIT_GAME_RESUME -> "请恢复游戏运行"
        else -> "点击继续"
    }
}

/**
 * 根据高亮目标计算顶部遮罩高度
 */
private fun getHighlightTopOffset(target: HighlightTarget): androidx.compose.ui.unit.Dp {
    return when (target) {
        HighlightTarget.STATUS_BAR -> 64.dp       // TopAppBar高度之后开始
        HighlightTarget.TOP_BAR, HighlightTarget.PAUSE_BUTTON, HighlightTarget.SPEED_BUTTON -> 0.dp
        HighlightTarget.TAB_OVERVIEW, HighlightTarget.TAB_TEACHING,
        HighlightTarget.TAB_TEACHER, HighlightTarget.TAB_RESEARCH,
        HighlightTarget.TAB_DISTRICT -> 0.dp  // 底部tab的遮罩，顶部全遮
        else -> 0.dp
    }
}

/**
 * 根据高亮目标计算底部遮罩高度
 */
private fun getHighlightBottomHeight(target: HighlightTarget): androidx.compose.ui.unit.Dp {
    return when (target) {
        HighlightTarget.STATUS_BAR -> 0.dp
        HighlightTarget.TOP_BAR, HighlightTarget.PAUSE_BUTTON, HighlightTarget.SPEED_BUTTON -> 0.dp
        HighlightTarget.TAB_OVERVIEW, HighlightTarget.TAB_TEACHING,
        HighlightTarget.TAB_TEACHER, HighlightTarget.TAB_RESEARCH,
        HighlightTarget.TAB_DISTRICT -> 0.dp
        else -> 0.dp
    }
}
