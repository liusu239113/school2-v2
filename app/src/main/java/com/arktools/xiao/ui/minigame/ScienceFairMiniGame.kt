package com.arktools.xiao.ui.minigame

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
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
fun ScienceFairMiniGame(
    state: ScienceFairGameState,
    onSelectProject: (ScienceProject) -> Unit,
    onSelectStep: (ExperimentStep) -> Unit,
    onUndoStep: () -> Unit,
    onResetSteps: () -> Unit,
    onConfirmSteps: () -> Unit,
    onAnswer: (Int) -> Unit,
    onProceedQuestion: () -> Unit,
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
                .systemBarsPadding()
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
                        text = "🔬 科学展览会",
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
                        ScienceFairPhase.CHOOSE_PROJECT -> "选择你的实验课题"
                        ScienceFairPhase.EXPERIMENT -> "按正确顺序排列实验步骤"
                        ScienceFairPhase.PRESENTATION -> "答辩环节"
                        ScienceFairPhase.SHOW_RESULTS -> "评审结果"
                    },
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(12.dp))

                // 主内容
                Box(modifier = Modifier.weight(1f)) {
                    when (state.phase) {
                        ScienceFairPhase.CHOOSE_PROJECT -> ChooseProjectPhase(
                            projects = state.availableProjects,
                            onSelect = onSelectProject
                        )
                        ScienceFairPhase.EXPERIMENT -> ExperimentPhase(
                            state = state,
                            onSelectStep = onSelectStep,
                            onUndo = onUndoStep,
                            onReset = onResetSteps,
                            onConfirm = onConfirmSteps
                        )
                        ScienceFairPhase.PRESENTATION -> PresentationPhase(
                            state = state,
                            onAnswer = onAnswer,
                            onProceed = onProceedQuestion
                        )
                        ScienceFairPhase.SHOW_RESULTS -> ScienceResultsPhase(
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
private fun ChooseProjectPhase(
    projects: List<ScienceProject>,
    onSelect: (ScienceProject) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "选择一个课题进行实验：",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.9f)
        )
        // 2列网格紧凑布局
        projects.chunked(2).forEach { rowProjects ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rowProjects.forEach { project ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                            .clickable { onSelect(project) }
                            .padding(8.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = project.emoji, fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = project.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${project.correctSteps.size}步骤 · ${"⭐".repeat(project.difficulty)}",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
                if (rowProjects.size < 2) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ExperimentPhase(
    state: ScienceFairGameState,
    onSelectStep: (ExperimentStep) -> Unit,
    onUndo: () -> Unit,
    onReset: () -> Unit,
    onConfirm: () -> Unit
) {
    val project = state.selectedProject ?: return
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 已选步骤区域
        Text(
            text = "📋 你的实验顺序（${state.playerStepOrder.size}/${project.correctSteps.size}）：",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.35f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.2f))
                .padding(8.dp)
        ) {
            if (state.playerStepOrder.isEmpty()) {
                Text(
                    text = "点击下方步骤添加到这里",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    state.playerStepOrder.forEachIndexed { index, step ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${index + 1}.",
                                fontSize = 11.sp,
                                color = Color(0xFF81C784),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${step.emoji} ${step.text}",
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 操作按钮
        if (state.playerStepOrder.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PixelButton(
                    text = "↩️ 撤回",
                    onClick = onUndo,
                    style = PixelButtonStyle.SECONDARY,
                    modifier = Modifier.weight(1f),
                    height = 44.dp
                )
                PixelButton(
                    text = "🔄 重置",
                    onClick = onReset,
                    style = PixelButtonStyle.SECONDARY,
                    modifier = Modifier.weight(1f),
                    height = 44.dp
                )
            }
            if (state.playerStepOrder.size == project.correctSteps.size) {
                Spacer(modifier = Modifier.height(8.dp))
                PixelButton(
                    text = "✅ 确认顺序",
                    onClick = onConfirm,
                    style = PixelButtonStyle.PRIMARY,
                    modifier = Modifier.fillMaxWidth(),
                    height = 48.dp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 可选步骤区域 - 2列网格
        Text(
            text = "🧪 可用步骤（点击添加）：",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(4.dp))

        Column(
            modifier = Modifier
                .weight(0.65f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            state.shuffledSteps.chunked(2).forEach { rowSteps ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    rowSteps.forEach { step ->
                        val isSelected = step in state.playerStepOrder
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isSelected) Color.White.copy(alpha = 0.05f)
                                    else Color.White.copy(alpha = 0.15f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color.White.copy(alpha = 0.1f)
                                    else Color.White.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable(enabled = !isSelected) { onSelectStep(step) }
                                .padding(horizontal = 6.dp, vertical = 5.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = "${step.emoji} ${step.text}",
                                fontSize = 11.sp,
                                color = if (isSelected) Color.White.copy(alpha = 0.3f)
                                else Color.White,
                                maxLines = 1
                            )
                        }
                    }
                    if (rowSteps.size < 2) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PresentationPhase(
    state: ScienceFairGameState,
    onAnswer: (Int) -> Unit,
    onProceed: () -> Unit
) {
    val project = state.selectedProject ?: return
    val question = project.questions.getOrNull(state.currentQuestionIndex) ?: return

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 实验分数展示
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.2f))
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🧪", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "实验得分：${(state.experimentScore * 100).toInt()}%",
                    fontSize = 13.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "答辩 ${state.currentQuestionIndex + 1}/${project.questions.size}",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }

        // 问题
        Text(
            text = "🎤 评委提问：",
            fontSize = 12.sp,
            color = Color(0xFF81C784),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = question.question,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.fillMaxWidth()
        )

        // 如果有答题反馈
        if (state.lastAnswerResult >= 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (state.lastAnswerResult == 1) Color(0xFF2E7D32).copy(alpha = 0.6f)
                        else Color(0xFFC62828).copy(alpha = 0.6f)
                    )
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = if (state.lastAnswerResult == 1) "✅ 回答正确！" else "❌ 回答有误",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (state.lastComment.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = state.lastComment,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
            PixelButton(
                text = "继续",
                onClick = onProceed,
                style = PixelButtonStyle.PRIMARY,
                modifier = Modifier.fillMaxWidth(),
                height = 42.dp
            )
        } else {
            // 选项
            question.options.forEachIndexed { index, option ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .clickable { onAnswer(index) }
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${'A' + index}.",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF81C784)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = option,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScienceResultsPhase(
    state: ScienceFairGameState,
    onClose: () -> Unit
) {
    val project = state.selectedProject
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = state.resultMessage,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 分数明细
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.25f))
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (project != null) {
                    Text(
                        text = "${project.emoji} ${project.title}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                ScoreRow("🧪 实验操作", state.experimentScore)
                val presentationScore = if (project != null && project.questions.isNotEmpty())
                    state.correctAnswers.toFloat() / project.questions.size else 0f
                ScoreRow("🎤 答辩表现", presentationScore)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "📊 综合评分",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${(state.totalScore * 100).toInt()}分",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD54F)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        PixelButton(
            text = "完成",
            onClick = onClose,
            style = PixelButtonStyle.PRIMARY,
            modifier = Modifier.fillMaxWidth(),
            height = 44.dp
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ScoreRow(label: String, score: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.9f)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { score },
                modifier = Modifier
                    .width(80.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = when {
                    score >= 0.8f -> Color(0xFF66BB6A)
                    score >= 0.5f -> Color(0xFFFFA726)
                    else -> Color(0xFFEF5350)
                },
                trackColor = Color.White.copy(alpha = 0.2f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${(score * 100).toInt()}%",
                fontSize = 12.sp,
                color = Color.White
            )
        }
    }
}
