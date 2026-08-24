package com.arktools.xiaozhang.ui.minigame

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.arktools.xiaozhang.domain.seasonal.ActivityType

/**
 * 迷你游戏容器：监听 MiniGameViewModel 状态并显示对应的迷你游戏界面。
 * 放在 MainScreen 的顶层（与 EventDialogContainer 同级）。
 */
@Composable
fun MiniGameContainer(
    viewModel: MiniGameViewModel = hiltViewModel()
) {
    val activeActivity by viewModel.activeActivity.collectAsState()
    val sportsDayState by viewModel.sportsDayState.collectAsState()
    val debateState by viewModel.debateState.collectAsState()
    val scienceFairState by viewModel.scienceFairState.collectAsState()
    val culturalFestState by viewModel.culturalFestState.collectAsState()

    activeActivity?.let { activity ->
        when (activity.type) {
            ActivityType.SPORTS_DAY -> {
                SportsDayMiniGame(
                    state = sportsDayState,
                    onSelectEvent = { viewModel.selectSportsEvent(it) },
                    onConfirmEvents = { viewModel.confirmSportsEvents() },
                    onSelectTactic = { viewModel.selectTacticAndStart(it) },
                    onSkipTactic = { viewModel.skipTacticAndStart() },
                    onCheer = { viewModel.cheer() },
                    onHitCritical = { viewModel.hitCriticalMoment() },
                    onProceedAfterResult = { viewModel.proceedAfterRaceResult() },
                    onClose = { viewModel.closeSportsDay() }
                )
            }
            ActivityType.DEBATE_TOURNAMENT -> {
                DebateMiniGame(
                    state = debateState,
                    onChooseStance = { viewModel.chooseDebateStance(it) },
                    onPlayArgument = { viewModel.playArgument(it) },
                    onUseRebuttal = { viewModel.useRebuttal() },
                    onSkipRebuttal = { viewModel.skipRebuttal() },
                    onClose = { viewModel.closeDebate() }
                )
            }
            ActivityType.SCIENCE_FAIR -> {
                ScienceFairMiniGame(
                    state = scienceFairState,
                    onSelectProject = { viewModel.selectScienceProject(it) },
                    onSelectStep = { viewModel.selectExperimentStep(it) },
                    onUndoStep = { viewModel.undoLastStep() },
                    onResetSteps = { viewModel.resetAllSteps() },
                    onConfirmSteps = { viewModel.confirmExperimentSteps() },
                    onAnswer = { viewModel.answerPresentationQuestion(it) },
                    onProceedQuestion = { viewModel.proceedToNextQuestion() },
                    onClose = { viewModel.closeScienceFair() }
                )
            }
            ActivityType.CULTURAL_FESTIVAL -> {
                CulturalFestMiniGame(
                    state = culturalFestState,
                    onToggleAct = { viewModel.toggleActSelection(it) },
                    onConfirmSelection = { viewModel.confirmActSelection() },
                    onSwapActs = { a, b -> viewModel.swapActs(a, b) },
                    onStartPerformance = { viewModel.startPerformance() },
                    onClose = { viewModel.closeCulturalFest() }
                )
            }
            else -> {
                // 其他活动类型暂无迷你游戏
            }
        }
    }
}
