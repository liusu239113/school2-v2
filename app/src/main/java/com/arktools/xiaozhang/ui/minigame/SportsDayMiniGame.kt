package com.arktools.xiaozhang.ui.minigame

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
fun SportsDayMiniGame(
    state: SportsDayGameState,
    onSelectEvent: (SportsEvent) -> Unit,
    onConfirmEvents: () -> Unit,
    onSelectTactic: (TacticCard?) -> Unit,
    onSkipTactic: () -> Unit,
    onCheer: () -> Unit,
    onHitCritical: () -> Unit,
    onProceedAfterResult: () -> Unit,
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
                            Color(0xFF1B5E20),
                            Color(0xFF2E7D32),
                            Color(0xFF388E3C)
                        )
                    )
                )
                .statusBarsPadding()
                .navigationBarsPadding()
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
                        text = "🏟️ 校运动会",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "✕ 退出",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onClose)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = when (state.phase) {
                        SportsDayPhase.SELECT_EVENTS -> "选择3个参赛项目"
                        SportsDayPhase.PRE_RACE_TACTIC -> "第${state.currentRaceIndex + 1}场赛前准备"
                        SportsDayPhase.RACE_IN_PROGRESS -> "比赛进行中！${state.selectedEvents.getOrNull(state.currentRaceIndex)?.let { it.emoji + it.displayName } ?: ""}"
                        SportsDayPhase.CRITICAL_MOMENT -> "⚡ 关键时刻！"
                        SportsDayPhase.RACE_RESULT -> "本场结束"
                        SportsDayPhase.SHOW_RESULTS -> "运动会结束！"
                    },
                    color = Color(0xFFFFEB3B),
                    style = MaterialTheme.typography.bodyLarge
                )

                // 体力条（选项选完后显示）
                if (state.phase != SportsDayPhase.SELECT_EVENTS && state.phase != SportsDayPhase.SHOW_RESULTS) {
                    Spacer(modifier = Modifier.height(6.dp))
                    StaminaBar(stamina = state.stamina, maxStamina = state.maxStamina)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 内容区域
                when (state.phase) {
                    SportsDayPhase.SELECT_EVENTS -> SelectEventsPhase(state, onSelectEvent, onConfirmEvents)
                    SportsDayPhase.PRE_RACE_TACTIC -> TacticPhase(state, onSelectTactic, onSkipTactic)
                    SportsDayPhase.RACE_IN_PROGRESS -> RacePhase(state, onCheer)
                    SportsDayPhase.CRITICAL_MOMENT -> CriticalMomentPhase(state, onHitCritical)
                    SportsDayPhase.RACE_RESULT -> RaceResultPhase(state, onProceedAfterResult)
                    SportsDayPhase.SHOW_RESULTS -> FinalResultsPhase(state, onClose)
                }
            }
        }
    }
}

// ==================== 体力条 ====================

@Composable
private fun StaminaBar(stamina: Int, maxStamina: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "⚡", fontSize = 14.sp)
        Spacer(modifier = Modifier.width(4.dp))
        val fraction = stamina.toFloat() / maxStamina
        val barColor = when {
            fraction > 0.6f -> Color(0xFF4CAF50)
            fraction > 0.3f -> Color(0xFFFF9800)
            else -> Color(0xFFF44336)
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp)),
            color = barColor,
            trackColor = Color.White.copy(alpha = 0.2f)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$stamina/$maxStamina",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp
        )
    }
}

// ==================== 阶段1：选择项目 ====================

@Composable
private fun ColumnScope.SelectEventsPhase(
    state: SportsDayGameState,
    onSelectEvent: (SportsEvent) -> Unit,
    onConfirmEvents: () -> Unit
) {
    Text(
        text = "选择3个项目参赛：",
        color = Color.White.copy(alpha = 0.8f),
        fontSize = 13.sp
    )
    Spacer(modifier = Modifier.height(6.dp))

    // 2列网格紧凑布局
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        state.availableEvents.chunked(2).forEach { rowEvents ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rowEvents.forEach { event ->
                    val isSelected = event in state.selectedEvents
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) Color(0xFF4CAF50).copy(alpha = 0.8f)
                                else Color.White.copy(alpha = 0.12f)
                            )
                            .then(
                                if (isSelected) Modifier.border(1.5.dp, Color(0xFFFFEB3B), RoundedCornerShape(8.dp))
                                else Modifier
                            )
                            .clickable { onSelectEvent(event) }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = event.emoji, fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = event.displayName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                                Text(
                                    text = event.idealTempo.displayName,
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 9.sp,
                                    maxLines = 1
                                )
                            }
                            if (isSelected) {
                                Text(text = "✓", color = Color(0xFFFFEB3B), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
                // 奇数个时填充空位
                if (rowEvents.size < 2) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        PixelButton(
            text = "确认选择 (${state.selectedEvents.size}/3)",
            onClick = onConfirmEvents,
            style = PixelButtonStyle.CONFIRM,
            modifier = Modifier.fillMaxWidth(),
            height = 42.dp,
            enabled = state.selectedEvents.size == 3
        )
    }
}

// ==================== 阶段2：赛前战术 ====================

@Composable
private fun ColumnScope.TacticPhase(
    state: SportsDayGameState,
    onSelectTactic: (TacticCard?) -> Unit,
    onSkipTactic: () -> Unit
) {
    val currentEvent = state.selectedEvents.getOrNull(state.currentRaceIndex)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 当前比赛信息
        if (currentEvent != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Column {
                    Text(
                        text = "${currentEvent.emoji} ${currentEvent.displayName}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "${currentEvent.mechanic} · ${currentEvent.idealTempo.displayName}",
                        color = Color(0xFFFFEB3B).copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 对手情报（如果使用了SPY卡）
        if (state.showOpponentStats) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF7B1FA2).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Column {
                    Text("🕵️ 情报", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    state.classes.filter { !it.isPlayerClass }.forEach { cls ->
                        val specialNote = if (cls.speciality == currentEvent) " ⚠️擅长!" else ""
                        Text(
                            text = "${cls.name}: 实力${cls.baseStrength}$specialNote",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Text(
            text = "选择战术卡（或直接开始）",
            color = Color.White.copy(alpha = 0.9f),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(4.dp))

        // 2列网格战术卡
        state.availableTactics.toList().chunked(2).forEach { rowCards ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rowCards.forEach { (card, remaining) ->
                    val usable = remaining > 0
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (usable) Color.White.copy(alpha = 0.15f)
                                else Color.White.copy(alpha = 0.05f)
                            )
                            .then(
                                if (usable) Modifier.clickable { onSelectTactic(card) }
                                else Modifier
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = card.emoji, fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = card.displayName,
                                    color = if (usable) Color.White else Color.White.copy(alpha = 0.4f),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                                Text(
                                    text = "×$remaining",
                                    color = if (usable) Color(0xFFFFEB3B) else Color.White.copy(alpha = 0.3f),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
                if (rowCards.size < 2) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        PixelButton(
            text = "不使用战术，直接开始",
            onClick = onSkipTactic,
            style = PixelButtonStyle.PRIMARY,
            modifier = Modifier.fillMaxWidth(),
            height = 42.dp
        )
    }
}

// ==================== 阶段3：比赛进行中 ====================

@Composable
private fun ColumnScope.RacePhase(
    state: SportsDayGameState,
    onCheer: () -> Unit
) {
    val race = state.currentRace ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 比赛信息
        Text(
            text = "${race.event.emoji} ${race.event.displayName}",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
        Text(
            text = "第 ${state.currentRaceIndex + 1}/${state.selectedEvents.size} 场 · ${race.event.idealTempo.displayName}",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        // 节奏/判定提示区
        if (race.lastJudgement.isNotEmpty()) {
            AnimatedVisibility(visible = true, enter = fadeIn() + scaleIn()) {
                Text(
                    text = race.lastJudgement,
                    color = if ("Miss" in race.lastJudgement || "太早" in race.lastJudgement)
                        Color(0xFFFF5252) else Color(0xFFFFEB3B),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 赛道可视化
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1B3A1B), RoundedCornerShape(8.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            race.participants.forEach { participant ->
                RaceTrack(participant = participant, isFinished = race.finished)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 节奏指示器（节奏/精准模式）
        when (race.event.idealTempo) {
            CheerTempo.RHYTHMIC -> {
                RhythmIndicator(beatPosition = race.beatPosition)
                Spacer(modifier = Modifier.height(8.dp))
            }
            CheerTempo.PRECISE -> {
                PreciseIndicator(promptActive = race.promptActive)
                Spacer(modifier = Modifier.height(8.dp))
            }
            else -> {}
        }

        // 加油按钮区
        if (!race.finished) {
            val canCheer = state.stamina >= (if (state.activeTacticThisRace == TacticCard.CHEER_SQUAD) 1 else 2)

            // 连击显示
            if (state.combo >= 3) {
                Text(
                    text = "🔥 连击 ×${state.combo}",
                    color = Color(0xFFFF9800),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            // 加油按钮
            val pulseScale by rememberInfiniteTransition(label = "pulse").animateFloat(
                initialValue = 1f,
                targetValue = if (canCheer) 1.1f else 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(300),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse"
            )

            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        if (canCheer) Color(0xFFFF5722) else Color(0xFF757575)
                    )
                    .clickable(
                        enabled = canCheer,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onCheer() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = when (race.event.idealTempo) {
                            CheerTempo.FAST -> "冲！"
                            CheerTempo.RHYTHMIC -> "🎵"
                            CheerTempo.PRECISE -> if (race.promptActive) "NOW!" else "等..."
                            CheerTempo.STEADY -> "💪"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            if (!canCheer) {
                Text(
                    text = "体力不足！",
                    color = Color(0xFFF44336),
                    fontSize = 12.sp
                )
            }
        }
    }
}

// ==================== 节奏指示器 ====================

@Composable
private fun RhythmIndicator(beatPosition: Float) {
    // 简单节拍条：在两端时点击最佳
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "🎵 跟着节拍点！", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF263238))
        ) {
            // 节拍区域标识（左右两端为best）
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.2f)
                        .background(Color(0xFF4CAF50).copy(alpha = 0.3f))
                )
                Box(modifier = Modifier.fillMaxHeight().weight(0.6f))
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.2f)
                        .background(Color(0xFF4CAF50).copy(alpha = 0.3f))
                )
            }
            // 移动指针
            Box(
                modifier = Modifier
                    .offset(x = (beatPosition * 200).dp)
                    .size(width = 4.dp, height = 16.dp)
                    .background(Color(0xFFFFEB3B), RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
private fun PreciseIndicator(promptActive: Boolean) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        val color = if (promptActive) Color(0xFF4CAF50) else Color(0xFF616161)
        val pulse by rememberInfiniteTransition(label = "precPulse").animateFloat(
            initialValue = 1f,
            targetValue = if (promptActive) 1.3f else 1f,
            animationSpec = infiniteRepeatable(tween(200), RepeatMode.Reverse),
            label = "precPulse"
        )
        Box(
            modifier = Modifier
                .size(20.dp)
                .scale(pulse)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (promptActive) "⚡ NOW! 点击！" else "等待时机...",
            color = if (promptActive) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.5f),
            fontWeight = if (promptActive) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp
        )
    }
}

// ==================== 阶段4：关键时刻 ====================

@Composable
private fun ColumnScope.CriticalMomentPhase(
    state: SportsDayGameState,
    onHitCritical: () -> Unit
) {
    val moment = state.criticalMoment ?: return

    val bigPulse by rememberInfiniteTransition(label = "crit").animateFloat(
        initialValue = 0.9f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(300), RepeatMode.Reverse),
        label = "crit"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = moment.emoji,
            fontSize = 60.sp,
            modifier = Modifier.scale(bigPulse)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = moment.description,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (state.criticalMomentActive) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(bigPulse)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFFFF9800), Color(0xFFF44336))
                        )
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onHitCritical() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "抓住!",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "快速点击！",
                color = Color(0xFFFFEB3B),
                fontSize = 14.sp
            )
        } else {
            // 已判定
            val success = state.criticalMomentSuccess == true
            Text(
                text = if (success) "✅ 成功！加速冲刺！" else "❌ 错过了...",
                color = if (success) Color(0xFF4CAF50) else Color(0xFFF44336),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }
    }
}

// ==================== 阶段5：单场结果 ====================

@Composable
private fun ColumnScope.RaceResultPhase(
    state: SportsDayGameState,
    onProceed: () -> Unit
) {
    val race = state.currentRace ?: return
    val result = state.raceResults.lastOrNull() ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val rankEmoji = when (result.playerRank) {
            1 -> "🥇"
            2 -> "🥈"
            3 -> "🥉"
            else -> "💪"
        }
        Text(text = rankEmoji, fontSize = 42.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "${race.event.emoji} ${race.event.displayName}",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "第${result.playerRank}名 · +${result.score}分",
            color = Color(0xFFFFEB3B),
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 详细统计
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (result.tacticUsed != null) {
                Text(
                    text = "战术：${result.tacticUsed.emoji} ${result.tacticUsed.displayName}",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp
                )
            }
            if (result.criticalSuccess != null) {
                Text(
                    text = "关键时刻：${if (result.criticalSuccess) "✅ 抓住了" else "❌ 错过"}",
                    color = if (result.criticalSuccess) Color(0xFF4CAF50) else Color(0xFFF44336),
                    fontSize = 13.sp
                )
            }
            if (result.comboAchieved > 0) {
                Text(
                    text = "最高连击：🔥 ×${result.comboAchieved}",
                    color = Color(0xFFFF9800),
                    fontSize = 13.sp
                )
            }
            Text(
                text = "当前总分：${state.totalScore}/${state.maxScore}",
                color = Color.White,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        val isLast = state.currentRaceIndex >= state.selectedEvents.size - 1
        PixelButton(
            text = if (isLast) "查看最终结果" else "下一场比赛",
            onClick = onProceed,
            style = PixelButtonStyle.PRIMARY,
            modifier = Modifier.fillMaxWidth(),
            height = 44.dp
        )
    }
}

// ==================== 阶段6：最终汇总 ====================

@Composable
private fun ColumnScope.FinalResultsPhase(
    state: SportsDayGameState,
    onClose: () -> Unit
) {
    val performance = if (state.maxScore > 0) state.totalScore.toFloat() / state.maxScore else 0f
    val gradeEmoji = when {
        performance >= 0.9f -> "🏆"
        performance >= 0.7f -> "🎉"
        performance >= 0.5f -> "👍"
        else -> "💪"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 顶部：结果 + 总分 + 统计 合并为紧凑一行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = gradeEmoji, fontSize = 28.sp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "总得分",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
                Text(
                    text = "${state.totalScore}/${state.maxScore}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "${state.maxCombo}", color = Color(0xFFFFEB3B), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(text = "连击", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "${state.goodHits}/${state.totalHits}", color = Color(0xFFFFEB3B), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(text = "命中", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
            }
        }

        // 各场比赛结果（紧凑列表）
        state.raceResults.forEach { result ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${result.event.emoji} ${result.event.displayName}",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                val rankEmoji = when (result.playerRank) {
                    1 -> "🥇"
                    2 -> "🥈"
                    3 -> "🥉"
                    else -> ""
                }
                Text(
                    text = "$rankEmoji 第${result.playerRank}名 +${result.score}分",
                    color = when (result.playerRank) {
                        1 -> Color(0xFFFFD700)
                        2 -> Color(0xFFC0C0C0)
                        else -> Color.White.copy(alpha = 0.8f)
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        PixelButton(
            text = "关闭",
            onClick = onClose,
            style = PixelButtonStyle.PRIMARY,
            modifier = Modifier.fillMaxWidth(),
            height = 44.dp
        )
    }
}

@Composable
private fun StatBadge(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = Color(0xFFFFEB3B), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(text = label, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
    }
}

// ==================== 赛道可视化 ====================

@Composable
private fun RaceTrack(participant: RaceParticipant, isFinished: Boolean) {
    val color = if (participant.isPlayer) Color(0xFFFF9800) else Color(0xFF90CAF9)
    val label = if (participant.isPlayer) "★ ${participant.className}" else participant.className

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = color,
                fontSize = 11.sp,
                fontWeight = if (participant.isPlayer) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.width(75.dp)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF263238))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(participant.position.coerceIn(0f, 1f))
                        .background(
                            Brush.horizontalGradient(
                                colors = if (participant.isPlayer)
                                    listOf(Color(0xFFFF9800), Color(0xFFFFEB3B))
                                else
                                    listOf(Color(0xFF42A5F5), Color(0xFF90CAF9))
                            ),
                            RoundedCornerShape(4.dp)
                        )
                )
                Box(
                    modifier = Modifier
                        .offset(x = ((participant.position * 180).coerceAtMost(180f)).dp)
                        .size(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🏃", fontSize = 12.sp)
                }
            }
            if (isFinished && participant.rank > 0) {
                Text(
                    text = "#${participant.rank}",
                    color = when (participant.rank) {
                        1 -> Color(0xFFFFD700)
                        2 -> Color(0xFFC0C0C0)
                        3 -> Color(0xFFCD7F32)
                        else -> Color.White.copy(alpha = 0.6f)
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(28.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}
