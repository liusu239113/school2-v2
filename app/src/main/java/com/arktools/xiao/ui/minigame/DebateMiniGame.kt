package com.arktools.xiao.ui.minigame

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material3.MaterialTheme
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
import com.arktools.xiao.domain.minigame.*
import com.arktools.xiao.ui.components.PixelButton
import com.arktools.xiao.ui.components.PixelButtonStyle

@Composable
fun DebateMiniGame(
    state: DebateGameState,
    onChooseStance: (Boolean) -> Unit,
    onPlayArgument: (ArgumentCard) -> Unit,
    onUseRebuttal: () -> Unit,
    onSkipRebuttal: () -> Unit,
    onClose: () -> Unit
) {
    // 首次进入显示玩法说明
    var showInstructions by remember { mutableStateOf(true) }

    if (showInstructions && state.phase == DebatePhase.CHOOSE_STANCE) {
        Dialog(onDismissRequest = { showInstructions = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1A237E))
                    .padding(20.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "⚔️ 辩论赛玩法说明",
                        color = Color(0xFFFFD54F),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val rules = listOf(
                        "1. 选择正方或反方立场开始辩论",
                        "2. 共5个环节，每环节出一张论点卡",
                        "3. 论点分4类：逻辑、情感、数据、权威，存在克制关系",
                        "4. 克制对手论点类型可获得额外伤害",
                        "5. 匹配评委偏好的类型可获得额外加分",
                        "6. 你有2次反驳机会，可将对手当回合得分减半",
                        "7. 5回合后总分高者获胜！"
                    )
                    rules.forEach { rule ->
                        Text(
                            text = rule,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    PixelButton(
                        text = "我知道了",
                        onClick = { showInstructions = false },
                        style = PixelButtonStyle.PRIMARY,
                        modifier = Modifier.fillMaxWidth(),
                        height = 44.dp
                    )
                }
            }
        }
    }

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
                            Color(0xFF1A237E),
                            Color(0xFF283593),
                            Color(0xFF303F9F)
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
                        text = "⚔️ 校际辩论赛",
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
                    text = "辩题：${state.topic.title}",
                    color = Color(0xFFFFD54F),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                if (state.phase != DebatePhase.CHOOSE_STANCE) {
                    Text(
                        text = "VS ${state.opponentName}",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                when (state.phase) {
                    DebatePhase.CHOOSE_STANCE -> ChooseStancePhase(state, onChooseStance)
                    DebatePhase.ARGUMENT_ROUND -> ArgumentRoundPhase(state, onPlayArgument)
                    DebatePhase.REBUTTAL_CHANCE -> RebuttalPhase(state, onUseRebuttal, onSkipRebuttal)
                    DebatePhase.SHOW_VERDICT -> VerdictPhase(state, onClose)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ChooseStancePhase(
    state: DebateGameState,
    onChooseStance: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "选择你的立场",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )

        // 评委偏好提示
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF4527A0).copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "评委：${state.judgePreference.name} · ${state.judgePreference.preferredCategory.emoji} ${state.judgePreference.preferredCategory.displayName}",
                    color = Color(0xFFB39DDB),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        // 正方
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF2E7D32).copy(alpha = 0.8f))
                .clickable { onChooseStance(true) }
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "正方", color = Color(0xFF81C784), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.topic.proStance,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // 反方
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFC62828).copy(alpha = 0.8f))
                .clickable { onChooseStance(false) }
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "反方", color = Color(0xFFEF9A9A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.topic.conStance,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // 规则提示（紧凑）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Text(
                text = "📋 5回合出论点卡 · 克制加分 · 评委偏好加分 · 2次反驳",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun ColumnScope.ArgumentRoundPhase(
    state: DebateGameState,
    onPlayArgument: (ArgumentCard) -> Unit
) {
    // 环节名称
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFF6F00).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "【${state.currentRoundType.displayName}】${state.currentRoundType.description}",
            color = Color(0xFFFFCC02),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    // 比分和回合信息
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "我方", color = Color(0xFF81C784), fontSize = 12.sp)
            Text(
                text = "${state.playerTotalScore}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "第 ${state.currentRound}/${state.maxRounds} 环节",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp
            )
            Text(text = "VS", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            // 连胜/反驳提示
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.momentum >= 2) {
                    Text(text = "🔥×${state.momentum}", color = Color(0xFFFF6D00), fontSize = 11.sp)
                }
                Text(text = "💬×${state.rebuttalCharges}", color = Color(0xFF80DEEA), fontSize = 11.sp)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "对方", color = Color(0xFFEF9A9A), fontSize = 12.sp)
            Text(
                text = "${state.opponentTotalScore}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            )
        }
    }

    Spacer(modifier = Modifier.height(6.dp))

    // 评委偏好小提示
    Text(
        text = "评委${state.judgePreference.name}偏好: ${state.judgePreference.preferredCategory.emoji}${state.judgePreference.preferredCategory.displayName}",
        color = Color(0xFFB39DDB),
        fontSize = 11.sp
    )

    Spacer(modifier = Modifier.height(4.dp))

    // 上回合结果
    if (state.roundResults.isNotEmpty()) {
        val lastResult = state.roundResults.last()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Text(
                text = "💬 ${lastResult.commentary}",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // 提示
    Text(
        text = "选择论点出击（剩余${state.playerHand.size}张）：",
        color = Color.White.copy(alpha = 0.8f),
        fontSize = 13.sp
    )
    Spacer(modifier = Modifier.height(4.dp))

    // 手牌 - 2列紧凑网格
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        state.playerHand.chunked(2).forEach { rowCards ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rowCards.forEach { card ->
                    val isJudgeFavorite = card.category == state.judgePreference.preferredCategory
                    val bgColor = when {
                        card.isSophistry -> Color(0xFF6A1B9A).copy(alpha = 0.7f)
                        isJudgeFavorite -> Color(0xFF1565C0).copy(alpha = 0.5f)
                        else -> Color.White.copy(alpha = 0.12f)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(bgColor)
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .clickable { onPlayArgument(card) }
                            .padding(horizontal = 6.dp, vertical = 5.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${card.category.emoji}",
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = card.text,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(text = "⚔️${card.attackPower}", color = Color(0xFFFF8A65), fontSize = 9.sp)
                                Text(text = "🛡️${card.defensePower}", color = Color(0xFF80CBC4), fontSize = 9.sp)
                                if (card.isSophistry) Text(text = "⚡诡辩", color = Color(0xFFCE93D8), fontSize = 9.sp)
                                if (isJudgeFavorite) Text(text = "⭐评委", color = Color(0xFF64B5F6), fontSize = 9.sp)
                            }
                        }
                    }
                }
                if (rowCards.size < 2) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.RebuttalPhase(
    state: DebateGameState,
    onUseRebuttal: () -> Unit,
    onSkipRebuttal: () -> Unit
) {
    val opponentCard = state.currentOpponentCard ?: return
    val playerCard = state.currentPlayerCard ?: return

    // 内容区
    Column(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "对手出牌",
            color = Color(0xFFEF9A9A),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )

        // 显示对手的牌
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFC62828).copy(alpha = 0.4f))
                .border(1.dp, Color(0xFFEF9A9A).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${opponentCard.category.emoji} ${opponentCard.text}",
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = opponentCard.category.displayName,
                        color = Color(0xFFEF9A9A),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(text = "⚔️${opponentCard.attackPower}", color = Color(0xFFFF8A65), fontSize = 12.sp)
                    Text(text = "🛡️${opponentCard.defensePower}", color = Color(0xFF80CBC4), fontSize = 12.sp)
                }
            }
        }

        // 本回合得分预览
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "我方得分", color = Color(0xFF81C784), fontSize = 12.sp)
                Text(text = "+${state.pendingPlayerScore}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "对方得分", color = Color(0xFFEF9A9A), fontSize = 12.sp)
                Text(text = "+${state.pendingOpponentScore}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        }

        // 反驳选项
        Text(
            text = "是否使用反驳？（剩余${state.rebuttalCharges}次）",
            color = Color(0xFF80DEEA),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Text(
            text = "反驳可将对手本回合得分减半",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp
        )
    }

    // 按钮固定在底部
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PixelButton(
            text = "💬 反驳！",
            onClick = onUseRebuttal,
            style = PixelButtonStyle.PRIMARY,
            modifier = Modifier.weight(1f),
            height = 44.dp
        )
        PixelButton(
            text = "跳过",
            onClick = onSkipRebuttal,
            style = PixelButtonStyle.SECONDARY,
            modifier = Modifier.weight(1f),
            height = 44.dp
        )
    }
}

@Composable
private fun ArgumentCardView(
    card: ArgumentCard,
    isJudgeFavorite: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = when {
        card.isSophistry -> Color(0xFF6A1B9A).copy(alpha = 0.7f)
        isJudgeFavorite -> Color(0xFF1565C0).copy(alpha = 0.5f)
        else -> Color.White.copy(alpha = 0.15f)
    }
    val borderColor = when {
        card.isSophistry -> Color(0xFFCE93D8)
        isJudgeFavorite -> Color(0xFF64B5F6)
        else -> Color.White.copy(alpha = 0.3f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(10.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 类别标签
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${card.category.emoji}${card.category.displayName}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = card.text,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }
                if (card.isSophistry) {
                    Text(
                        text = "⚡诡辩",
                        color = Color(0xFFCE93D8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (isJudgeFavorite && !card.isSophistry) {
                    Text(
                        text = "⭐评委",
                        color = Color(0xFF64B5F6),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "⚔️${card.attackPower}", color = Color(0xFFFF8A65), fontSize = 11.sp)
                Text(text = "🛡️${card.defensePower}", color = Color(0xFF80CBC4), fontSize = 11.sp)
                Text(
                    text = "克制${card.category.beats().emoji}",
                    color = Color(0xFFFFD54F).copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
                if (card.isSophistry) {
                    Text(
                        text = "⚠️30%反驳",
                        color = Color(0xFFCE93D8).copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.VerdictPhase(
    state: DebateGameState,
    onClose: () -> Unit
) {
    val won = state.playerTotalScore > state.opponentTotalScore
    val tied = state.playerTotalScore == state.opponentTotalScore

    // 结果 + 比分（紧凑一行）
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = when {
                won -> "🏆"
                tied -> "🤝"
                else -> "😔"
            },
            fontSize = 28.sp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = when {
                won -> "辩论获胜！"
                tied -> "握手言和"
                else -> "惜败..."
            },
            color = when {
                won -> Color(0xFFFFD54F)
                tied -> Color.White
                else -> Color(0xFFEF9A9A)
            },
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = "${state.playerTotalScore}",
            color = Color(0xFF81C784),
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
        )
        Text(
            text = " : ",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "${state.opponentTotalScore}",
            color = Color(0xFFEF9A9A),
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
        )
    }

    Spacer(modifier = Modifier.height(6.dp))

    // 评委点评（紧凑）
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
            .padding(8.dp)
    ) {
        Text(
            text = "📋 ${state.judgeCommentary}",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 12.sp,
            maxLines = 2
        )
    }

    Spacer(modifier = Modifier.height(6.dp))

    // 各回合回顾 - 紧凑表格式
    Text(
        text = "环节详情",
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 11.sp
    )
    Spacer(modifier = Modifier.height(4.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        state.roundResults.forEach { result ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：环节名 + 卡牌
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${result.roundType.displayName}",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                    Text(
                        text = " ${result.playerCard.category.emoji}${result.playerCard.text.take(4)}",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )
                    if (result.categoryAdvantage) {
                        Text(text = "🎯", fontSize = 10.sp)
                    }
                    if (result.playerUsedRebuttal) {
                        Text(text = "💬", fontSize = 10.sp)
                    }
                }
                // 右侧：比分
                Text(
                    text = "${result.playerScore}-${result.opponentScore}",
                    color = if (result.playerScore > result.opponentScore) Color(0xFF81C784)
                    else if (result.playerScore < result.opponentScore) Color(0xFFEF9A9A)
                    else Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
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
