package com.arktools.xiaozhang.ui.minigame

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.arktools.xiaozhang.domain.minigame.*
import com.arktools.xiaozhang.ui.components.PixelButton
import com.arktools.xiaozhang.ui.components.PixelButtonStyle

@Composable
fun CulturalFestMiniGame(
    state: CulturalFestGameState,
    onToggleAct: (PerformanceAct) -> Unit,
    onConfirmSelection: () -> Unit,
    onSwapActs: (Int, Int) -> Unit,
    onStartPerformance: () -> Unit,
    onClose: () -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF4A148C),
                            Color(0xFF6A1B9A),
                            Color(0xFF7B1FA2)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 标题栏（含关闭按钮）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🎭 文艺汇演",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "✕ 退出",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onClose)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when (state.phase) {
                        CulturalFestPhase.SELECT_ACTS -> "选择5个节目组成演出阵容"
                        CulturalFestPhase.ARRANGE_ORDER -> "调整节目顺序（考虑观众疲劳度）"
                        CulturalFestPhase.PERFORMING -> "演出进行中..."
                        CulturalFestPhase.SHOW_RESULTS -> "演出结束"
                    },
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(12.dp))

                Box(modifier = Modifier.weight(1f)) {
                    when (state.phase) {
                        CulturalFestPhase.SELECT_ACTS -> SelectActsPhase(
                            state = state,
                            onToggle = onToggleAct,
                            onConfirm = onConfirmSelection
                        )
                        CulturalFestPhase.ARRANGE_ORDER -> ArrangeOrderPhase(
                            state = state,
                            onSwap = onSwapActs,
                            onStart = onStartPerformance
                        )
                        CulturalFestPhase.PERFORMING -> PerformingPhase(state = state)
                        CulturalFestPhase.SHOW_RESULTS -> CulturalResultsPhase(
                            state = state,
                            onClose = onClose
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectActsPhase(
    state: CulturalFestGameState,
    onToggle: (PerformanceAct) -> Unit,
    onConfirm: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 已选计数
        Text(
            text = "选择5个节目（已选${state.selectedActs.size}/5）：",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (state.selectedActs.size == 5) Color(0xFFFFD54F) else Color.White
        )
        Spacer(modifier = Modifier.height(6.dp))

        // 2列网格紧凑布局
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            state.availableActs.chunked(2).forEach { rowActs ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    rowActs.forEach { act ->
                        val isSelected = act in state.selectedActs
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) Color(0xFFFFD54F).copy(alpha = 0.2f)
                                    else Color.White.copy(alpha = 0.1f)
                                )
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onToggle(act) }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = act.type.emoji, fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = act.name,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${"⚡".repeat(act.energy)} ${act.type.displayName}",
                                        fontSize = 9.sp,
                                        color = Color.White.copy(alpha = 0.5f),
                                        maxLines = 1
                                    )
                                }
                                if (isSelected) {
                                    Text(text = "✓", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    if (rowActs.size < 2) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // 提示 + 按钮
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "💡 连续高能节目会让观众疲劳",
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.6f)
        )
        if (state.selectedActs.size == 5) {
            Spacer(modifier = Modifier.height(6.dp))
            PixelButton(
                text = "确认阵容",
                onClick = onConfirm,
                style = PixelButtonStyle.PRIMARY,
                modifier = Modifier.fillMaxWidth(),
                height = 42.dp
            )
        }
    }
}

@Composable
private fun ArrangeOrderPhase(
    state: CulturalFestGameState,
    onSwap: (Int, Int) -> Unit,
    onStart: () -> Unit
) {
    // 记录当前选中的交换位置
    var selectedIndex by remember { mutableIntStateOf(-1) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "点击两个节目交换位置（🎯最佳位置表现更好）：",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(6.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            state.orderedActs.forEachIndexed { index, act ->
                val isSwapSelected = selectedIndex == index
                val isInBestPosition = when (act.bestPosition) {
                    1 -> index < 2
                    3 -> index >= 3
                    else -> index in 1..3
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                isSwapSelected -> Color(0xFFFFD54F).copy(alpha = 0.3f)
                                isInBestPosition -> Color(0xFF66BB6A).copy(alpha = 0.15f)
                                else -> Color.White.copy(alpha = 0.1f)
                            }
                        )
                        .border(
                            width = if (isSwapSelected) 2.dp else 1.dp,
                            color = when {
                                isSwapSelected -> Color(0xFFFFD54F)
                                isInBestPosition -> Color(0xFF66BB6A).copy(alpha = 0.5f)
                                else -> Color.White.copy(alpha = 0.2f)
                            },
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            if (selectedIndex < 0) {
                                selectedIndex = index
                            } else {
                                if (selectedIndex != index) {
                                    onSwap(selectedIndex, index)
                                }
                                selectedIndex = -1
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = act.type.emoji, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = act.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    if (isInBestPosition) {
                        Text(text = "✅", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        PixelButton(
            text = "🎬 开始演出",
            onClick = onStart,
            style = PixelButtonStyle.PRIMARY,
            modifier = Modifier.fillMaxWidth(),
            height = 42.dp
        )
    }
}

@Composable
private fun PerformingPhase(state: CulturalFestGameState) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 观众情绪仪表盘
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.25f))
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "👥 观众状态",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                MoodBar(
                    label = "🔥 兴奋度",
                    value = state.audienceMood.excitement,
                    color = Color(0xFFFF7043)
                )
                MoodBar(
                    label = "😫 疲劳度",
                    value = state.audienceMood.fatigue,
                    color = Color(0xFFFFCA28)
                )
                MoodBar(
                    label = "😊 满意度",
                    value = state.audienceMood.satisfaction,
                    color = Color(0xFF66BB6A)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 当前节目
        val currentAct = state.orderedActs.getOrNull(state.currentActIndex)
        if (currentAct != null) {
            // 进度
            Text(
                text = "第 ${state.currentActIndex + 1}/${state.orderedActs.size} 个节目",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 正在表演的节目
            val pulseAnim = rememberInfiniteTransition(label = "pulse")
            val scale by pulseAnim.animateFloat(
                initialValue = 1f,
                targetValue = 1.1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = EaseInOutCubic),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = currentAct.type.emoji,
                        fontSize = (40 * scale).sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = currentAct.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = currentAct.type.displayName + " · " + currentAct.duration,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 演出结果列表
        if (state.actResults.isNotEmpty()) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                state.actResults.forEach { result ->
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = result.act.type.emoji, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = result.act.name,
                            fontSize = 12.sp,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = result.audienceReaction,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "+${result.scoreContribution}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD54F)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodBar(label: String, value: Int, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.width(70.dp)
        )
        LinearProgressIndicator(
            progress = { value / 100f },
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = Color.White.copy(alpha = 0.15f)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$value",
            fontSize = 11.sp,
            color = Color.White,
            modifier = Modifier.width(24.dp)
        )
    }
}

@Composable
private fun CulturalResultsPhase(
    state: CulturalFestGameState,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 顶部结果
        Text(
            text = state.resultMessage,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "总分：${state.totalScore}/${state.maxScore}",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFD54F)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 观众最终情绪（紧凑）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.2f))
                .padding(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "👥 观众反馈",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                MoodBar("🔥 兴奋度", state.audienceMood.excitement, Color(0xFFFF7043))
                MoodBar("😫 疲劳度", state.audienceMood.fatigue, Color(0xFFFFCA28))
                MoodBar("😊 满意度", state.audienceMood.satisfaction, Color(0xFF66BB6A))
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 各节目表现
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.2f))
                .padding(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "📋 节目表现",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                state.actResults.forEach { result ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = result.act.type.emoji, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = result.act.name,
                            fontSize = 11.sp,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        if (result.positionBonus) {
                            Text(text = "🎯", fontSize = 11.sp)
                        }
                        Text(
                            text = "+${result.scoreContribution}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                result.scoreContribution >= 18 -> Color(0xFF66BB6A)
                                result.scoreContribution >= 12 -> Color.White
                                else -> Color(0xFFEF5350)
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        PixelButton(
            text = "完成",
            onClick = onClose,
            style = PixelButtonStyle.PRIMARY,
            modifier = Modifier.fillMaxWidth(),
            height = 44.dp
        )
    }
}
