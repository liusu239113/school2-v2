package com.arktools.xiao.ui.minigame

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiao.domain.engine.GameEngine
import com.arktools.xiao.domain.minigame.*
import com.arktools.xiao.domain.seasonal.ActivityType
import com.arktools.xiao.domain.seasonal.SeasonalActivity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.arktools.xiao.util.safeLaunch
import kotlin.random.Random

@HiltViewModel
class MiniGameViewModel @Inject constructor(
    private val gameEngine: GameEngine
) : ViewModel() {

    init {
        // 监听 GameEngine 发出的迷你游戏触发信号
        viewModelScope.safeLaunch {
            gameEngine.miniGameTrigger.collect { activity ->
                triggerMiniGame(activity)
            }
        }
    }

    /** 当前触发的迷你游戏活动（null = 无迷你游戏弹出） */
    private val _activeActivity = MutableStateFlow<SeasonalActivity?>(null)
    val activeActivity: StateFlow<SeasonalActivity?> = _activeActivity.asStateFlow()

    /** 运动会游戏状态 */
    private val _sportsDayState = MutableStateFlow(SportsDayGameState())
    val sportsDayState: StateFlow<SportsDayGameState> = _sportsDayState.asStateFlow()

    /** 辩论赛游戏状态 */
    private val _debateState = MutableStateFlow(DebateGameState())
    val debateState: StateFlow<DebateGameState> = _debateState.asStateFlow()

    /** 科学展览游戏状态 */
    private val _scienceFairState = MutableStateFlow(ScienceFairGameState())
    val scienceFairState: StateFlow<ScienceFairGameState> = _scienceFairState.asStateFlow()

    /** 文艺汇演游戏状态 */
    private val _culturalFestState = MutableStateFlow(CulturalFestGameState())
    val culturalFestState: StateFlow<CulturalFestGameState> = _culturalFestState.asStateFlow()

    /** 迷你游戏结果（完成后发出，由外部消费） */
    private val _miniGameResult = MutableStateFlow<MiniGameResult?>(null)
    val miniGameResult: StateFlow<MiniGameResult?> = _miniGameResult.asStateFlow()

    private var raceAnimationJob: Job? = null

    /** 同日多个活动按 FIFO 顺序展示，避免覆盖当前小游戏。 */
    private val pendingActivities = ArrayDeque<SeasonalActivity>()

    /** 只在队列首次启动时记录暂停前状态。 */
    private var wasRunningBeforeMiniGame = false
    private var pausedByMiniGame = false

    // ===================== 公共方法 =====================

    /** 触发迷你游戏（由 GameEngine / EventViewModel 调用） */
    fun triggerMiniGame(activity: SeasonalActivity) {
        if (_activeActivity.value != null) {
            if (pendingActivities.none { it.id == activity.id }) pendingActivities.addLast(activity)
            return
        }
        if (!pausedByMiniGame) {
            wasRunningBeforeMiniGame = !gameEngine.isPausedFlow.value
            gameEngine.pause()
            pausedByMiniGame = true
        }
        showActivity(activity)
    }

    private fun showActivity(activity: SeasonalActivity) {
        _activeActivity.value = activity
        _miniGameResult.value = null
        when (activity.type) {
            ActivityType.SPORTS_DAY -> initSportsDay()
            ActivityType.DEBATE_TOURNAMENT -> initDebate()
            ActivityType.SCIENCE_FAIR -> initScienceFair()
            ActivityType.CULTURAL_FESTIVAL -> initCulturalFest()
            else -> {
                // 其他活动暂无迷你游戏，直接完成（默认0.7分）
                val defaultScore = 0.7f
                _miniGameResult.value = MiniGameResult(
                    activityId = activity.id,
                    activityType = activity.type,
                    performanceScore = defaultScore,
                    resultMessage = "${activity.type.displayName}顺利进行！"
                )
                // 将表现分数回传给季节活动系统，影响活动结算奖励
                gameEngine.seasonalActivityManager.applyMiniGamePerformance(activity.id, defaultScore)
                finishCurrentMiniGame()
            }
        }
    }

    /** 消费结果（外部读取后清空） */
    fun consumeResult(): MiniGameResult? {
        val r = _miniGameResult.value
        _miniGameResult.value = null
        return r
    }

    private fun finishCurrentMiniGame() {
        raceAnimationJob?.cancel()
        _activeActivity.value = null
        val next = pendingActivities.removeFirstOrNull()
        if (next != null) {
            showActivity(next)
            return
        }
        if (pausedByMiniGame && wasRunningBeforeMiniGame) gameEngine.resume()
        pausedByMiniGame = false
        wasRunningBeforeMiniGame = false
    }

    fun dismissMiniGame() {
        finishCurrentMiniGame()
    }

    // ===================== 运动会逻辑 =====================

    private fun initSportsDay() {
        val allEvents = SportsEvent.entries.toList()
        // 每个对手班级有随机擅长项目
        val classes = listOf(
            CompetingClass("大一(1)班", Random.nextInt(65, 85), isPlayerClass = true),
            CompetingClass("大一(2)班", Random.nextInt(60, 90), speciality = allEvents.random()),
            CompetingClass("大二(1)班", Random.nextInt(70, 95), speciality = allEvents.random()),
            CompetingClass("大二(3)班", Random.nextInt(55, 80), speciality = allEvents.random()),
        )
        _sportsDayState.value = SportsDayGameState(
            phase = SportsDayPhase.SELECT_EVENTS,
            availableEvents = allEvents,
            classes = classes,
            stamina = 100,
            availableTactics = TacticCard.entries.associateWith { it.usesLeft }
        )
    }

    /** 玩家选择参赛项目（选3个） */
    fun selectSportsEvent(event: SportsEvent) {
        val state = _sportsDayState.value
        val selected = state.selectedEvents.toMutableList()
        if (event in selected) {
            selected.remove(event)
        } else if (selected.size < 3) {
            selected.add(event)
        }
        _sportsDayState.value = state.copy(selectedEvents = selected)
    }

    /** 确认选择项目，进入第一场的战术卡阶段 */
    fun confirmSportsEvents() {
        val state = _sportsDayState.value
        if (state.selectedEvents.size == 3) {
            _sportsDayState.value = state.copy(
                phase = SportsDayPhase.PRE_RACE_TACTIC,
                currentRaceIndex = 0,
                raceResults = emptyList(),
                totalScore = 0,
                maxScore = 0
            )
        }
    }

    /** 选择战术卡并开始比赛 */
    fun selectTacticAndStart(tactic: TacticCard?) {
        val state = _sportsDayState.value
        var newStamina = state.stamina
        var showStats = false
        val newTactics = state.availableTactics.toMutableMap()

        if (tactic != null) {
            val remaining = newTactics[tactic] ?: 0
            if (remaining <= 0) return // 没有使用次数了
            newTactics[tactic] = remaining - 1

            // 应用战术卡效果
            when (tactic) {
                TacticCard.REST -> newStamina = (newStamina + 30).coerceAtMost(state.maxStamina)
                TacticCard.SPY -> showStats = true
                else -> {} // 其他效果在比赛中生效
            }
        }

        _sportsDayState.value = state.copy(
            activeTacticThisRace = tactic,
            availableTactics = newTactics,
            stamina = newStamina,
            showOpponentStats = showStats,
            phase = SportsDayPhase.RACE_IN_PROGRESS,
            cheerCount = 0,
            goodHits = 0,
            totalHits = 0,
            combo = 0,
            maxCombo = 0
        )
        startCurrentRace()
    }

    /** 跳过战术卡直接开始 */
    fun skipTacticAndStart() = selectTacticAndStart(null)

    /** 玩家点击加油 - 现在根据节奏类型判定 */
    fun cheer() {
        val state = _sportsDayState.value
        if (state.phase != SportsDayPhase.RACE_IN_PROGRESS) return
        val race = state.currentRace ?: return
        if (race.finished) return

        // 体力消耗（啦啦队卡减半消耗）
        val staminaCost = if (state.activeTacticThisRace == TacticCard.CHEER_SQUAD) 1 else 2
        if (state.stamina < staminaCost) return // 体力不足无法加油

        val event = race.event
        var judgement = ""
        var newGoodHits = state.goodHits
        var newTotalHits = state.totalHits + 1
        var newCombo = state.combo

        when (event.idealTempo) {
            CheerTempo.FAST -> {
                // 快速模式：每次点击都有效
                newGoodHits++
                newCombo++
                judgement = if (newCombo >= 5) "🔥连击x$newCombo" else "👍"
            }
            CheerTempo.RHYTHMIC -> {
                // 节奏模式：根据节拍位置判定
                val beat = race.beatPosition
                val accuracy = if (beat < 0.2f || beat > 0.8f) {
                    // 在节拍附近点击 = Good
                    newGoodHits++
                    newCombo++
                    judgement = if (newCombo >= 3) "Perfect!🎵x$newCombo" else "Good!🎵"
                    1f
                } else {
                    // 太早或太晚
                    newCombo = 0
                    judgement = "Miss...💨"
                    0f
                }
                // accuracy already computed above
            }
            CheerTempo.PRECISE -> {
                // 精准模式：只有提示激活时点击才有效
                if (race.promptActive) {
                    newGoodHits++
                    newCombo++
                    judgement = "Perfect!⚡"
                } else {
                    newCombo = 0
                    judgement = "太早了...⏱️"
                }
            }
            CheerTempo.STEADY -> {
                // 均匀模式：都有效但讲究间隔稳定性
                newGoodHits++
                newCombo++
                judgement = "稳住！💪"
            }
        }

        _sportsDayState.value = state.copy(
            cheerCount = state.cheerCount + 1,
            stamina = state.stamina - staminaCost,
            goodHits = newGoodHits,
            totalHits = newTotalHits,
            combo = newCombo,
            maxCombo = maxOf(state.maxCombo, newCombo),
            currentRace = race.copy(lastJudgement = judgement)
        )
    }

    /** 关键时刻QTE点击 */
    fun hitCriticalMoment() {
        val state = _sportsDayState.value
        if (!state.criticalMomentActive) return
        _sportsDayState.value = state.copy(
            criticalMomentSuccess = true,
            criticalMomentActive = false
        )
    }

    /** 关键时刻超时 */
    fun missCriticalMoment() {
        val state = _sportsDayState.value
        if (state.criticalMomentSuccess != null) return // 已判定
        _sportsDayState.value = state.copy(
            criticalMomentSuccess = false,
            criticalMomentActive = false
        )
    }

    /** 查看单场结果后继续 */
    fun proceedAfterRaceResult() {
        val state = _sportsDayState.value
        val nextIndex = state.currentRaceIndex + 1
        if (nextIndex >= state.selectedEvents.size) {
            finishSportsDay()
        } else {
            _sportsDayState.value = state.copy(
                currentRaceIndex = nextIndex,
                phase = SportsDayPhase.PRE_RACE_TACTIC,
                activeTacticThisRace = null,
                showOpponentStats = false,
                criticalMoment = null,
                criticalMomentActive = false,
                criticalMomentSuccess = null
            )
        }
    }

    private fun startCurrentRace() {
        val state = _sportsDayState.value
        val event = state.selectedEvents[state.currentRaceIndex]
        val participants = state.classes.map { cls ->
            RaceParticipant(
                className = cls.name,
                isPlayer = cls.isPlayerClass,
                hasSpecialityBonus = cls.speciality == event
            )
        }
        val race = RaceState(event = event, participants = participants)
        _sportsDayState.value = state.copy(currentRace = race)
        // 启动比赛动画
        raceAnimationJob?.cancel()
        raceAnimationJob = viewModelScope.safeLaunch {
            simulateRace()
        }
    }

    private suspend fun simulateRace() {
        val totalSteps = 40
        val state0 = _sportsDayState.value
        val race0 = state0.currentRace ?: return
        val event = race0.event

        // 决定是否触发关键时刻（60%概率，在比赛50%~80%进度时）
        val criticalStep = if (Random.nextFloat() < 0.6f) Random.nextInt(20, 32) else -1
        val criticalMoments = listOf(
            CriticalMoment("选手即将超越对手！抓住机会！", "⚡", 2000, 0.15f, 0.05f),
            CriticalMoment("最后冲刺的关键瞬间！", "🔥", 1800, 0.12f, 0.03f),
            CriticalMoment("弯道超车的绝佳时机！", "💨", 2200, 0.10f, 0.04f),
            CriticalMoment("观众欢呼带来的超级加速！", "🎉", 2500, 0.18f, 0.02f)
        )

        for (step in 1..totalSteps) {
            delay(80L)
            val state = _sportsDayState.value
            if (state.phase != SportsDayPhase.RACE_IN_PROGRESS) return
            val race = state.currentRace ?: return

            // 关键时刻触发
            if (step == criticalStep) {
                val moment = criticalMoments.random()
                _sportsDayState.value = state.copy(
                    criticalMoment = moment,
                    criticalMomentActive = true,
                    criticalMomentSuccess = null,
                    phase = SportsDayPhase.CRITICAL_MOMENT
                )
                // 等待玩家反应
                delay(moment.windowMs)
                // 检查是否已点击
                val afterState = _sportsDayState.value
                if (afterState.criticalMomentSuccess == null) {
                    missCriticalMoment()
                }
                delay(800L)
                // 恢复比赛
                val resumeState = _sportsDayState.value
                _sportsDayState.value = resumeState.copy(phase = SportsDayPhase.RACE_IN_PROGRESS)
                continue
            }

            // 更新节拍位置（用于节奏类型判定）
            val beatCycle = (step % 8).toFloat() / 8f  // 8步一个周期
            // 精准模式：随机出现提示
            val showPrompt = event.idealTempo == CheerTempo.PRECISE &&
                    Random.nextFloat() < 0.12f && !race.promptActive

            // 计算加油效果
            val effectiveCheerBonus = calculateCheerBonus(state)

            val updatedParticipants = race.participants.map { p ->
                val cls = state.classes.find { it.name == p.className }
                val baseSpeed = (cls?.baseStrength ?: 70) / 100f
                // 擅长项目加成
                val specialityMod = if (p.hasSpecialityBonus && !p.isPlayer) 0.12f else 0f
                // 战术卡加成
                val tacticMod = if (p.isPlayer) {
                    when (state.activeTacticThisRace) {
                        TacticCard.PEP_TALK -> 0.12f
                        TacticCard.SUBSTITUTE -> 0.18f
                        else -> 0f
                    }
                } else 0f
                // 关键时刻加成
                val criticalMod = if (p.isPlayer && state.criticalMomentSuccess == true) {
                    state.criticalMoment?.successBonus ?: 0f
                } else if (p.isPlayer && state.criticalMomentSuccess == false) {
                    -(state.criticalMoment?.failPenalty ?: 0f)
                } else 0f
                // 加油效果
                val playerBonus = if (p.isPlayer) effectiveCheerBonus else 0f
                // 啦啦队被动效果
                val passiveBonus = if (p.isPlayer && state.activeTacticThisRace == TacticCard.CHEER_SQUAD) 0.03f else 0f
                val randomFactor = Random.nextFloat() * 0.08f - 0.02f
                val speed = (baseSpeed + specialityMod + tacticMod + criticalMod + playerBonus + passiveBonus + randomFactor) / totalSteps
                val newPos = (p.position + speed).coerceAtMost(1f)
                p.copy(position = newPos)
            }

            val progress = step.toFloat() / totalSteps
            // 精准模式提示持续3步
            val promptShouldEnd = race.promptActive && step % 3 == 0

            _sportsDayState.value = state.copy(
                currentRace = race.copy(
                    participants = updatedParticipants,
                    progress = progress,
                    finished = step == totalSteps,
                    beatPosition = beatCycle,
                    promptActive = if (showPrompt) true else if (promptShouldEnd) false else race.promptActive
                )
            )
        }

        // 比赛结束，计算排名
        delay(400L)
        resolveRaceResult()
    }

    private fun calculateCheerBonus(state: SportsDayGameState): Float {
        val baseBonus = if (state.totalHits > 0) {
            val accuracy = state.goodHits.toFloat() / state.totalHits
            accuracy * 0.15f // 最高15%加成
        } else {
            state.cheerCount * 0.004f
        }
        // 能量补给卡加50%效果
        val snackMult = if (state.activeTacticThisRace == TacticCard.SNACK) 1.5f else 1f
        // 连击奖励
        val comboBonus = (state.maxCombo * 0.003f).coerceAtMost(0.05f)
        return (baseBonus * snackMult + comboBonus).coerceAtMost(0.25f)
    }

    private fun resolveRaceResult() {
        val state = _sportsDayState.value
        val race = state.currentRace ?: return
        val sorted = race.participants.sortedByDescending { it.position }
        val ranked = sorted.mapIndexed { index, p -> p.copy(rank = index + 1) }
        val playerRank = ranked.find { it.isPlayer }?.rank ?: ranked.size

        // 计算得分（满分10）
        val score = when (playerRank) {
            1 -> 10
            2 -> 7
            3 -> 4
            else -> 2
        }

        val raceResult = RaceResult(
            event = race.event,
            playerRank = playerRank,
            totalParticipants = ranked.size,
            score = score,
            tacticUsed = state.activeTacticThisRace,
            criticalSuccess = state.criticalMomentSuccess,
            comboAchieved = state.maxCombo
        )

        _sportsDayState.value = state.copy(
            phase = SportsDayPhase.RACE_RESULT,
            currentRace = race.copy(participants = ranked, finished = true),
            raceResults = state.raceResults + raceResult,
            totalScore = state.totalScore + score,
            maxScore = state.maxScore + 10
        )
    }

    private fun finishSportsDay() {
        val state = _sportsDayState.value
        _sportsDayState.value = state.copy(phase = SportsDayPhase.SHOW_RESULTS)

        val performance = if (state.maxScore > 0) {
            state.totalScore.toFloat() / state.maxScore
        } else 0.7f

        val activity = _activeActivity.value ?: return
        _miniGameResult.value = MiniGameResult(
            activityId = activity.id,
            activityType = activity.type,
            performanceScore = performance,
            specialAchievement = performance >= 0.9f,
            resultMessage = when {
                performance >= 0.9f -> "运动会大获全胜！全校沸腾！🏆"
                performance >= 0.7f -> "运动会圆满成功，成绩优秀！"
                performance >= 0.5f -> "运动会顺利举办，中等成绩。"
                else -> "运动会略有遗憾，下次加油！"
            }
        )
        // 回传小游戏表现到季节活动系统
        gameEngine.seasonalActivityManager.applyMiniGamePerformance(activity.id, performance)
    }

    fun closeSportsDay() {
        raceAnimationJob?.cancel()
        finishCurrentMiniGame()
    }

    // ===================== 辩论赛逻辑 =====================

    private val debateTopics = listOf(
        DebateTopic("网络对青少年的影响", "网络利大于弊", "网络弊大于利"),
        DebateTopic("学生是否应该穿校服", "应该统一穿校服", "不应该统一穿校服"),
        DebateTopic("毕业放榜是否公平", "毕业放榜制度是公平的", "毕业放榜制度不够公平"),
        DebateTopic("课外辅导班的存废", "应该保留辅导班", "应该禁止辅导班"),
        DebateTopic("手机是否应该进校园", "允许带手机进校园", "禁止带手机进校园"),
        DebateTopic("AI能否取代教师", "AI将取代传统教师", "AI无法取代教师"),
        DebateTopic("成绩排名是否应该公开", "应该公开成绩排名", "不应该公开排名"),
        DebateTopic("寒暑假是否应该缩短", "应该缩短寒暑假", "应该保持现有假期")
    )

    private val judgePool = listOf(
        JudgePreference("张教授", ArgumentCategory.LOGIC, "偏好逻辑严密的论证"),
        JudgePreference("李记者", ArgumentCategory.DATA, "看重数据和事实依据"),
        JudgePreference("王校长", ArgumentCategory.EMOTION, "关注人文关怀和共情"),
        JudgePreference("赵律师", ArgumentCategory.AUTHORITY, "重视权威文献引用")
    )

    private val roundTypes = DebateRoundType.entries.toList()

    private fun initDebate() {
        val topic = debateTopics.random()
        val judge = judgePool.random()
        _debateState.value = DebateGameState(
            phase = DebatePhase.CHOOSE_STANCE,
            topic = topic,
            opponentName = listOf("市一中辩论队", "实验中学队", "外国语学校队", "省重点辩论社").random(),
            judgePreference = judge,
            currentRoundType = DebateRoundType.OPENING
        )
    }

    /** 选择正方/反方 */
    fun chooseDebateStance(isProSide: Boolean) {
        val state = _debateState.value
        val hand = generateArgumentHand(isProSide)
        _debateState.value = state.copy(
            playerIsProSide = isProSide,
            phase = DebatePhase.ARGUMENT_ROUND,
            currentRound = 1,
            playerHand = hand,
            playerTotalScore = 0,
            opponentTotalScore = 0,
            roundResults = emptyList(),
            rebuttalCharges = 2,
            momentum = 0,
            currentRoundType = roundTypes[0]
        )
    }

    /** 玩家出牌 */
    fun playArgument(card: ArgumentCard) {
        val state = _debateState.value
        if (state.phase != DebatePhase.ARGUMENT_ROUND) return

        // 对手出牌
        val opponentCard = generateOpponentCard(state.currentRound, state)

        // 计算基础得分
        val playerAttack = card.attackPower
        val playerDefense = card.defensePower
        val oppAttack = opponentCard.attackPower
        val oppDefense = opponentCard.defensePower

        var playerRoundScore = (playerAttack - oppDefense / 2).coerceAtLeast(0)
        var oppRoundScore = (oppAttack - playerDefense / 2).coerceAtLeast(0)

        // 类别克制加成：玩家类别克制对手类别时 +3 分
        val categoryAdvantage = card.category.beats() == opponentCard.category
        if (categoryAdvantage) {
            playerRoundScore += 3
        }
        // 对手克制玩家
        if (opponentCard.category.beats() == card.category) {
            oppRoundScore += 3
        }

        // 评委偏好加分：匹配评委口味 +2
        if (card.category == state.judgePreference.preferredCategory) {
            playerRoundScore += 2
        }
        if (opponentCard.category == state.judgePreference.preferredCategory) {
            oppRoundScore += 2
        }

        // 连胜势头加成
        if (state.momentum >= 2) {
            playerRoundScore += state.momentum  // 连胜越多加分越多
        }

        // 诡辩风险
        if (card.isSophistry && Random.nextFloat() < 0.3f) {
            playerRoundScore = 0
        }

        // 进入反驳判断阶段（如果玩家还有反驳次数且对手得分较高）
        val remainingHand = state.playerHand.filter { it.id != card.id }
        if (state.rebuttalCharges > 0 && oppRoundScore > 0) {
            _debateState.value = state.copy(
                phase = DebatePhase.REBUTTAL_CHANCE,
                currentPlayerCard = card,
                currentOpponentCard = opponentCard,
                pendingPlayerScore = playerRoundScore,
                pendingOpponentScore = oppRoundScore,
                playerHand = remainingHand
            )
        } else {
            // 无反驳机会，直接结算本回合
            resolveRound(state, card, opponentCard, playerRoundScore, oppRoundScore,
                categoryAdvantage, false, remainingHand)
        }
    }

    /** 玩家选择使用反驳 */
    fun useRebuttal() {
        val state = _debateState.value
        if (state.phase != DebatePhase.REBUTTAL_CHANCE) return
        val card = state.currentPlayerCard ?: return
        val opponentCard = state.currentOpponentCard ?: return

        // 反驳减少对手得分50%
        val reducedOppScore = (state.pendingOpponentScore * 0.5f).toInt()
        val categoryAdvantage = card.category.beats() == opponentCard.category

        resolveRound(
            state.copy(rebuttalCharges = state.rebuttalCharges - 1),
            card, opponentCard,
            state.pendingPlayerScore, reducedOppScore,
            categoryAdvantage, true, state.playerHand
        )
    }

    /** 玩家选择不反驳 */
    fun skipRebuttal() {
        val state = _debateState.value
        if (state.phase != DebatePhase.REBUTTAL_CHANCE) return
        val card = state.currentPlayerCard ?: return
        val opponentCard = state.currentOpponentCard ?: return
        val categoryAdvantage = card.category.beats() == opponentCard.category

        resolveRound(state, card, opponentCard,
            state.pendingPlayerScore, state.pendingOpponentScore,
            categoryAdvantage, false, state.playerHand)
    }

    private fun resolveRound(
        state: DebateGameState,
        playerCard: ArgumentCard,
        opponentCard: ArgumentCard,
        playerScore: Int,
        oppScore: Int,
        categoryAdvantage: Boolean,
        usedRebuttal: Boolean,
        remainingHand: List<ArgumentCard>
    ) {
        val commentary = generateCommentary(playerCard, opponentCard, playerScore, oppScore, categoryAdvantage)
        val result = RoundResult(
            roundNumber = state.currentRound,
            roundType = state.currentRoundType,
            playerCard = playerCard,
            opponentCard = opponentCard,
            playerScore = playerScore,
            opponentScore = oppScore,
            commentary = commentary,
            categoryAdvantage = categoryAdvantage,
            playerUsedRebuttal = usedRebuttal
        )

        val newPlayerTotal = state.playerTotalScore + playerScore
        val newOppTotal = state.opponentTotalScore + oppScore
        val newResults = state.roundResults + result

        // 更新连胜
        val newMomentum = if (playerScore > oppScore) state.momentum + 1 else 0

        if (state.currentRound >= state.maxRounds) {
            val judgeComment = generateJudgeVerdict(newPlayerTotal, newOppTotal, state.judgePreference, newResults)
            _debateState.value = state.copy(
                phase = DebatePhase.SHOW_VERDICT,
                roundResults = newResults,
                playerTotalScore = newPlayerTotal,
                opponentTotalScore = newOppTotal,
                currentOpponentCard = opponentCard,
                judgeCommentary = judgeComment,
                playerHand = remainingHand,
                momentum = newMomentum
            )
            finishDebate(newPlayerTotal, newOppTotal)
        } else {
            val nextRound = state.currentRound + 1
            val nextRoundType = roundTypes.getOrElse(nextRound - 1) { DebateRoundType.FREE_DEBATE_2 }
            _debateState.value = state.copy(
                phase = DebatePhase.ARGUMENT_ROUND,
                currentRound = nextRound,
                currentRoundType = nextRoundType,
                roundResults = newResults,
                playerTotalScore = newPlayerTotal,
                opponentTotalScore = newOppTotal,
                currentOpponentCard = opponentCard,
                playerHand = remainingHand,
                momentum = newMomentum,
                currentPlayerCard = null,
                pendingPlayerScore = 0,
                pendingOpponentScore = 0
            )
        }
    }

    private fun generateJudgeVerdict(
        playerTotal: Int, oppTotal: Int,
        judge: JudgePreference, results: List<RoundResult>
    ): String {
        val judgeTypeFavor = results.count { it.playerCard.category == judge.preferredCategory }
        val prefix = "${judge.name}（${judge.description}）点评："
        return prefix + when {
            playerTotal > oppTotal + 10 -> "我方辩手表现堪称完美，论证层层递进，无懈可击！"
            playerTotal > oppTotal + 5 -> "我方论据充分、逻辑清晰，明显胜出一筹。"
            playerTotal > oppTotal -> "双方激烈交锋，我方凭借细节处理赢得评委青睐。"
            playerTotal == oppTotal -> "势均力敌的精彩对决！双方都展现了极高水准。"
            oppTotal > playerTotal + 5 -> "对方辩友准备充分，我方在关键环节有所疏漏。"
            else -> "虽然惜败，但我方也有不少亮眼表现，值得肯定。"
        }
    }

    private fun finishDebate(playerScore: Int, oppScore: Int) {
        val maxPossible = 50 // 5回合，每回合理论最高约10分
        val performance = (playerScore.toFloat() / maxPossible.coerceAtLeast(1)).coerceIn(0f, 1f)
        val won = playerScore > oppScore

        val activity = _activeActivity.value ?: return
        val finalScore = if (won) performance.coerceAtLeast(0.6f) else performance.coerceAtMost(0.5f)
        _miniGameResult.value = MiniGameResult(
            activityId = activity.id,
            activityType = activity.type,
            performanceScore = finalScore,
            specialAchievement = playerScore > oppScore + 15,
            resultMessage = when {
                playerScore > oppScore + 15 -> "辩论赛完胜！我校辩论队名扬四方！"
                playerScore > oppScore -> "辩论赛获胜！逻辑训练卓有成效！"
                playerScore == oppScore -> "辩论赛平局，双方势均力敌。"
                else -> "辩论赛惜败，但锻炼了学生思维能力。"
            }
        )
        // 回传小游戏表现到季节活动系统
        gameEngine.seasonalActivityManager.applyMiniGamePerformance(activity.id, finalScore)
    }

    fun closeDebate() {
        finishCurrentMiniGame()
    }

    private fun generateArgumentHand(isProSide: Boolean): List<ArgumentCard> {
        val cards = mutableListOf<ArgumentCard>()
        // 生成8张卡，每种类别各2张，玩家选5张使用
        val templates = if (isProSide) proArguments else conArguments
        templates.shuffled().take(8).forEachIndexed { index, template ->
            cards.add(ArgumentCard(
                id = index,
                text = template.text,
                category = template.category,
                attackPower = template.attack,
                defensePower = template.defense,
                isSophistry = template.attack >= 9 && template.defense <= 3
            ))
        }
        return cards
    }

    private fun generateOpponentCard(round: Int, state: DebateGameState): ArgumentCard {
        // 对手越到后面越强，且会适应玩家策略
        val baseAttack = 4 + round
        val baseDefense = 3 + round
        val attack = (baseAttack + Random.nextInt(-1, 2)).coerceIn(3, 9)
        val defense = (baseDefense + Random.nextInt(-1, 2)).coerceIn(3, 9)

        // 对手选择类别策略：50%随机，30%选克制玩家上次的类别，20%选评委偏好
        val lastPlayerCategory = state.roundResults.lastOrNull()?.playerCard?.category
        val category = when {
            lastPlayerCategory != null && Random.nextFloat() < 0.3f -> {
                // 尝试克制玩家上回合的类别
                ArgumentCategory.entries.find { it.beats() == lastPlayerCategory }
                    ?: ArgumentCategory.entries.random()
            }
            Random.nextFloat() < 0.3f -> state.judgePreference.preferredCategory
            else -> ArgumentCategory.entries.random()
        }

        val texts = mapOf(
            ArgumentCategory.LOGIC to listOf("从逻辑上推导...", "如果前提成立则必然...", "反证法表明..."),
            ArgumentCategory.DATA to listOf("统计数据显示...", "调研报告表明...", "实验结果证明..."),
            ArgumentCategory.EMOTION to listOf("设身处地想一想...", "每个家庭都会...", "将心比心..."),
            ArgumentCategory.AUTHORITY to listOf("权威期刊指出...", "教育部文件明确...", "诺贝尔得主认为...")
        )
        return ArgumentCard(
            id = 100 + round,
            text = texts[category]?.random() ?: "论点陈述...",
            category = category,
            attackPower = attack,
            defensePower = defense
        )
    }

    private fun generateCommentary(
        playerCard: ArgumentCard,
        opponentCard: ArgumentCard,
        playerScore: Int,
        oppScore: Int,
        categoryAdvantage: Boolean
    ): String {
        val advantageText = if (categoryAdvantage) "类别克制生效！" else ""
        return when {
            playerScore > oppScore + 5 -> "${advantageText}精彩！我方论点犀利，对方难以招架！"
            playerScore > oppScore -> "${advantageText}我方略占上风，论证有力。"
            playerScore == oppScore -> "双方旗鼓相当，各有千秋。"
            oppScore > playerScore + 5 -> "对方反驳有力，我方论点受到严峻挑战。"
            else -> "对方稍占优势，需要调整策略。"
        }
    }

    // 论据模板数据类
    private data class ArgTemplate(
        val text: String,
        val category: ArgumentCategory,
        val attack: Int,
        val defense: Int
    )

    // 正方论据模板
    private val proArguments = listOf(
        ArgTemplate("统计数据显示正面效果显著", ArgumentCategory.DATA, 7, 6),
        ArgTemplate("权威机构明确支持此观点", ArgumentCategory.AUTHORITY, 6, 8),
        ArgTemplate("长期实践证明效果良好", ArgumentCategory.DATA, 5, 7),
        ArgTemplate("从个体发展角度共情论证", ArgumentCategory.EMOTION, 8, 4),
        ArgTemplate("国际先进经验实证", ArgumentCategory.DATA, 7, 5),
        ArgTemplate("逻辑推导必然得出", ArgumentCategory.LOGIC, 9, 3),
        ArgTemplate("社会民意调查支持", ArgumentCategory.DATA, 6, 6),
        ArgTemplate("从教育公平角度出发", ArgumentCategory.EMOTION, 5, 8),
        ArgTemplate("三段论严密推理", ArgumentCategory.LOGIC, 7, 7),
        ArgTemplate("诺贝尔得主研究支持", ArgumentCategory.AUTHORITY, 8, 5),
        ArgTemplate("设身处地换位思考", ArgumentCategory.EMOTION, 6, 7),
        ArgTemplate("反证法排除其他可能", ArgumentCategory.LOGIC, 7, 6)
    )

    // 反方论据模板
    private val conArguments = listOf(
        ArgTemplate("实际案例揭示弊端", ArgumentCategory.DATA, 7, 6),
        ArgTemplate("深层逻辑矛盾不容忽视", ArgumentCategory.LOGIC, 6, 7),
        ArgTemplate("替代方案效果更佳", ArgumentCategory.LOGIC, 8, 5),
        ArgTemplate("长远危害大于短期收益", ArgumentCategory.EMOTION, 7, 5),
        ArgTemplate("核心矛盾逻辑推翻", ArgumentCategory.LOGIC, 6, 8),
        ArgTemplate("反面典型触目惊心", ArgumentCategory.EMOTION, 9, 3),
        ArgTemplate("基层真实数据反馈", ArgumentCategory.DATA, 5, 7),
        ArgTemplate("从可持续发展分析", ArgumentCategory.AUTHORITY, 6, 6),
        ArgTemplate("联合国报告反对", ArgumentCategory.AUTHORITY, 7, 7),
        ArgTemplate("归谬法推翻前提", ArgumentCategory.LOGIC, 8, 4),
        ArgTemplate("受害者亲身经历", ArgumentCategory.EMOTION, 7, 5),
        ArgTemplate("多家权威媒体质疑", ArgumentCategory.AUTHORITY, 6, 7)
    )

    // ===================== 科学展览会逻辑 =====================

    private val scienceProjects = listOf(
        ScienceProject(
            id = 1,
            title = "火山喷发模拟",
            emoji = "🌋",
            difficulty = 1,
            description = "用化学反应模拟火山喷发",
            correctSteps = listOf(
                ExperimentStep(1, "搭建火山模型外壳", "🏔️"),
                ExperimentStep(2, "放入小苏打", "🧂"),
                ExperimentStep(3, "加入红色颜料", "🎨"),
                ExperimentStep(4, "倒入白醋触发反应", "🧪"),
                ExperimentStep(5, "记录喷发高度和持续时间", "📝")
            ),
            questions = listOf(
                PresentationQuestion(
                    "这个实验的核心化学反应是什么？",
                    listOf("酸碱中和反应", "氧化还原反应", "置换反应", "分解反应"),
                    0, "完全正确！小苏打和醋的反应产生CO₂", "不太对哦，这是典型的酸碱反应"
                ),
                PresentationQuestion(
                    "如何改进实验使喷发效果更明显？",
                    listOf("加更多颜料", "增加小苏打和醋的量", "加入洗洁精产生更多泡沫", "提高温度"),
                    2, "很好！洗洁精能让CO₂产生的泡沫更壮观", "也有一定效果，但洗洁精的效果最佳"
                )
            )
        ),
        ScienceProject(
            id = 2,
            title = "植物向光性实验",
            emoji = "🌱",
            difficulty = 2,
            description = "探究植物生长与光照方向的关系",
            correctSteps = listOf(
                ExperimentStep(1, "准备相同的豆苗若干株", "🫘"),
                ExperimentStep(2, "设置对照组和实验组", "📋"),
                ExperimentStep(3, "用遮光箱制造单侧光源", "📦"),
                ExperimentStep(4, "每天定时观察记录弯曲角度", "📐"),
                ExperimentStep(5, "统计数据绘制生长曲线", "📊")
            ),
            questions = listOf(
                PresentationQuestion(
                    "植物向光性的主要原因是什么？",
                    listOf("光合作用", "生长素分布不均", "水分运输", "温度差异"),
                    1, "正确！背光侧生长素浓度高，生长更快", "生长素的不均匀分布才是根本原因"
                ),
                PresentationQuestion(
                    "这个实验的控制变量有哪些？",
                    listOf("只有光照方向", "光照、水分、温度", "光照方向不变，其他条件相同", "无需控制变量"),
                    2, "对！只改变光照方向，其他条件保持一致", "对照实验需要严格控制无关变量"
                )
            )
        ),
        ScienceProject(
            id = 3,
            title = "简易电磁铁制作",
            emoji = "🧲",
            difficulty = 2,
            description = "探究电流产生磁场及增强方法",
            correctSteps = listOf(
                ExperimentStep(1, "准备铁钉、漆包线和电池", "🔋"),
                ExperimentStep(2, "将漆包线紧密缠绕在铁钉上", "🔩"),
                ExperimentStep(3, "连接电池形成回路", "⚡"),
                ExperimentStep(4, "用回形针测试吸附力", "📎"),
                ExperimentStep(5, "改变线圈匝数对比磁力强弱", "📈")
            ),
            questions = listOf(
                PresentationQuestion(
                    "怎样增强电磁铁的磁力？",
                    listOf("换更大的铁钉", "增加线圈匝数或增大电流", "使用铜钉代替铁钉", "减少线圈匝数"),
                    1, "正确！增加匝数和电流都能增强磁场", "铜不是铁磁材料，换铜钉反而没磁性了"
                ),
                PresentationQuestion(
                    "电磁铁与永磁铁的最大区别是什么？",
                    listOf("磁力大小不同", "可以通过电流控制开关", "形状不同", "材料不同"),
                    1, "完全正确！这正是电磁铁的核心优势", "可控性才是电磁铁最大的特点"
                )
            )
        ),
        ScienceProject(
            id = 4,
            title = "DNA提取实验",
            emoji = "🧬",
            difficulty = 3,
            description = "从水果中提取可见的DNA",
            correctSteps = listOf(
                ExperimentStep(1, "将草莓放入密封袋中捣碎", "🍓"),
                ExperimentStep(2, "加入盐水和洗洁精混合液", "🧴"),
                ExperimentStep(3, "过滤获取澄清液体", "🫙"),
                ExperimentStep(4, "沿杯壁缓慢加入冰冷酒精", "🧊"),
                ExperimentStep(5, "用竹签挑起白色絮状DNA", "🥢")
            ),
            questions = listOf(
                PresentationQuestion(
                    "加入洗洁精的作用是什么？",
                    listOf("清洁杂质", "溶解细胞膜释放DNA", "使DNA变色", "防止细菌污染"),
                    1, "正确！洗洁精破坏脂质双分子层", "洗洁精中的表面活性剂能溶解细胞膜"
                ),
                PresentationQuestion(
                    "为什么要用冰冷的酒精？",
                    listOf("杀菌消毒", "DNA不溶于冷酒精会析出", "让DNA变色", "加速反应"),
                    1, "正确！低温酒精使DNA从溶液中沉淀析出", "冷酒精降低DNA溶解度使其可见"
                )
            )
        )
    )

    private fun initScienceFair() {
        val projects = scienceProjects.shuffled().take(3)
        _scienceFairState.value = ScienceFairGameState(
            phase = ScienceFairPhase.CHOOSE_PROJECT,
            availableProjects = projects
        )
    }

    fun selectScienceProject(project: ScienceProject) {
        val state = _scienceFairState.value
        if (state.phase != ScienceFairPhase.CHOOSE_PROJECT) return
        _scienceFairState.value = state.copy(
            selectedProject = project,
            phase = ScienceFairPhase.EXPERIMENT,
            shuffledSteps = project.correctSteps.shuffled(),
            playerStepOrder = emptyList()
        )
    }

    /** 玩家点击选择一个步骤（添加到顺序末尾） */
    fun selectExperimentStep(step: ExperimentStep) {
        val state = _scienceFairState.value
        if (state.phase != ScienceFairPhase.EXPERIMENT) return
        if (step in state.playerStepOrder) return // 已选过
        val newOrder = state.playerStepOrder + step
        _scienceFairState.value = state.copy(playerStepOrder = newOrder)
    }

    /** 撤回最后一个步骤 */
    fun undoLastStep() {
        val state = _scienceFairState.value
        if (state.playerStepOrder.isEmpty()) return
        _scienceFairState.value = state.copy(
            playerStepOrder = state.playerStepOrder.dropLast(1)
        )
    }

    /** 重置全部已选步骤 */
    fun resetAllSteps() {
        val state = _scienceFairState.value
        if (state.playerStepOrder.isEmpty()) return
        _scienceFairState.value = state.copy(playerStepOrder = emptyList())
    }

    /** 确认实验步骤，计算得分并进入答辩阶段 */
    fun confirmExperimentSteps() {
        val state = _scienceFairState.value
        val project = state.selectedProject ?: return
        if (state.playerStepOrder.size != project.correctSteps.size) return

        // 计算步骤得分：每个正确位置得1分
        var correctCount = 0
        project.correctSteps.forEachIndexed { index, step ->
            if (state.playerStepOrder.getOrNull(index)?.id == step.id) {
                correctCount++
            }
        }
        val experimentScore = correctCount.toFloat() / project.correctSteps.size

        _scienceFairState.value = state.copy(
            phase = ScienceFairPhase.PRESENTATION,
            experimentScore = experimentScore,
            currentQuestionIndex = 0,
            correctAnswers = 0,
            lastAnswerResult = -1
        )
    }

    /** 回答答辩问题 */
    fun answerPresentationQuestion(optionIndex: Int) {
        val state = _scienceFairState.value
        val project = state.selectedProject ?: return
        val question = project.questions.getOrNull(state.currentQuestionIndex) ?: return

        val isCorrect = optionIndex == question.correctIndex
        val newCorrect = state.correctAnswers + if (isCorrect) 1 else 0
        val comment = if (isCorrect) question.correctComment else question.wrongComment

        // 只记录答题结果和反馈，不推进题目索引（等用户点"继续"再推进）
        _scienceFairState.value = state.copy(
            correctAnswers = newCorrect,
            lastAnswerResult = if (isCorrect) 1 else 0,
            lastComment = comment
        )
    }

    /** 从答辩结果继续到下一题（或进入结果页） */
    fun proceedToNextQuestion() {
        val state = _scienceFairState.value
        val project = state.selectedProject ?: return

        val isLast = state.currentQuestionIndex >= project.questions.size - 1

        if (isLast) {
            // 答辩结束，计算总分
            val presentationScore = state.correctAnswers.toFloat() / project.questions.size
            val totalScore = state.experimentScore * 0.6f + presentationScore * 0.4f

            _scienceFairState.value = state.copy(
                lastAnswerResult = -1,
                lastComment = "",
                phase = ScienceFairPhase.SHOW_RESULTS,
                totalScore = totalScore,
                resultMessage = when {
                    totalScore >= 0.9f -> "🏆 一等奖！实验精准、答辩完美！"
                    totalScore >= 0.7f -> "🥈 二等奖！整体表现优秀！"
                    totalScore >= 0.5f -> "🥉 三等奖！还有提升空间。"
                    else -> "参与奖。实验过程需要更加严谨。"
                }
            )

            // 发出结果
            val activity = _activeActivity.value ?: return
            _miniGameResult.value = MiniGameResult(
                activityId = activity.id,
                activityType = activity.type,
                performanceScore = totalScore,
                specialAchievement = totalScore >= 0.9f,
                resultMessage = when {
                    totalScore >= 0.9f -> "科学展览大放异彩！多个项目获奖！🏆"
                    totalScore >= 0.7f -> "科学展览圆满成功！"
                    totalScore >= 0.5f -> "科学展览顺利举办。"
                    else -> "科学展览反响一般，下次需要更好准备。"
                }
            )
            // 回传小游戏表现到季节活动系统
            gameEngine.seasonalActivityManager.applyMiniGamePerformance(activity.id, totalScore)
        } else {
            // 推进到下一题
            _scienceFairState.value = state.copy(
                lastAnswerResult = -1,
                lastComment = "",
                currentQuestionIndex = state.currentQuestionIndex + 1
            )
        }
    }

    fun closeScienceFair() {
        finishCurrentMiniGame()
    }

    // ===================== 文艺汇演逻辑 =====================

    private val performancePool = listOf(
        PerformanceAct(1, "青春之歌", ActType.SONG, 3, 80, 1, "4分钟"),
        PerformanceAct(2, "街舞串烧", ActType.DANCE, 5, 85, 2, "5分钟"),
        PerformanceAct(3, "爆笑校园", ActType.SKIT, 4, 90, 2, "8分钟"),
        PerformanceAct(4, "钢琴独奏", ActType.INSTRUMENT, 2, 88, 2, "6分钟"),
        PerformanceAct(5, "校园合唱团", ActType.CHOIR, 3, 82, 3, "5分钟"),
        PerformanceAct(6, "杂技表演", ActType.ACROBATICS, 5, 78, 1, "4分钟"),
        PerformanceAct(7, "魔术秀", ActType.MAGIC, 4, 86, 3, "6分钟"),
        PerformanceAct(8, "经典朗诵", ActType.RECITATION, 1, 75, 2, "4分钟"),
        PerformanceAct(9, "民族舞", ActType.DANCE, 4, 84, 2, "5分钟"),
        PerformanceAct(10, "乐队演奏", ActType.INSTRUMENT, 4, 87, 3, "7分钟"),
        PerformanceAct(11, "相声", ActType.SKIT, 3, 83, 1, "6分钟"),
        PerformanceAct(12, "流行歌曲联唱", ActType.SONG, 4, 81, 3, "5分钟")
    )

    private fun initCulturalFest() {
        val available = performancePool.shuffled().take(8)
        _culturalFestState.value = CulturalFestGameState(
            phase = CulturalFestPhase.SELECT_ACTS,
            availableActs = available,
            selectedActs = emptyList()
        )
    }

    /** 选择/取消节目 */
    fun toggleActSelection(act: PerformanceAct) {
        val state = _culturalFestState.value
        if (state.phase != CulturalFestPhase.SELECT_ACTS) return
        val selected = state.selectedActs.toMutableList()
        if (act in selected) {
            selected.remove(act)
        } else if (selected.size < 5) {
            selected.add(act)
        }
        _culturalFestState.value = state.copy(selectedActs = selected)
    }

    /** 确认选择，进入排序阶段 */
    fun confirmActSelection() {
        val state = _culturalFestState.value
        if (state.selectedActs.size != 5) return
        _culturalFestState.value = state.copy(
            phase = CulturalFestPhase.ARRANGE_ORDER,
            orderedActs = state.selectedActs // 初始顺序 = 选择顺序
        )
    }

    /** 排序：将节目移到指定位置 */
    fun moveAct(fromIndex: Int, toIndex: Int) {
        val state = _culturalFestState.value
        if (state.phase != CulturalFestPhase.ARRANGE_ORDER) return
        val list = state.orderedActs.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        _culturalFestState.value = state.copy(orderedActs = list)
    }

    /** 交换两个节目位置 */
    fun swapActs(indexA: Int, indexB: Int) {
        val state = _culturalFestState.value
        if (state.phase != CulturalFestPhase.ARRANGE_ORDER) return
        val list = state.orderedActs.toMutableList()
        if (indexA !in list.indices || indexB !in list.indices) return
        val temp = list[indexA]
        list[indexA] = list[indexB]
        list[indexB] = temp
        _culturalFestState.value = state.copy(orderedActs = list)
    }

    /** 确认节目单，开始演出 */
    fun startPerformance() {
        val state = _culturalFestState.value
        if (state.orderedActs.size != 5) return
        _culturalFestState.value = state.copy(
            phase = CulturalFestPhase.PERFORMING,
            currentActIndex = 0,
            audienceMood = AudienceMood(),
            actResults = emptyList()
        )
        // 自动播放演出
        raceAnimationJob?.cancel()
        raceAnimationJob = viewModelScope.safeLaunch {
            simulatePerformance()
        }
    }

    private suspend fun simulatePerformance() {
        val state0 = _culturalFestState.value
        var mood = state0.audienceMood
        val results = mutableListOf<ActResult>()

        for (i in state0.orderedActs.indices) {
            val act = state0.orderedActs[i]
            _culturalFestState.value = _culturalFestState.value.copy(currentActIndex = i)
            delay(1200L) // 每个节目展示1.2秒

            // 计算位置匹配（1=开场即前2, 2=中段即中间, 3=压轴即后2）
            val positionMatch = when (act.bestPosition) {
                1 -> i < 2
                3 -> i >= 3
                else -> i in 1..3
            }

            // 计算观众反应
            val energyDiff = act.energy - 3  // >0高能, <0低能
            var excitementDelta = energyDiff * 8
            var fatigueDelta = if (act.energy >= 4) 12 else -8  // 高能增加疲劳, 低能恢复

            // 如果观众已疲劳，高能节目效果打折
            if (mood.fatigue > 60 && act.energy >= 4) {
                excitementDelta = excitementDelta / 2
                fatigueDelta += 5
            }
            // 位置加成
            val positionMultiplier = if (positionMatch) 1.3f else 1.0f

            val qualityBonus = ((act.quality - 70) * 0.5f).toInt()
            val contribution = ((act.quality / 10 + excitementDelta / 4 + qualityBonus) * positionMultiplier).toInt().coerceIn(5, 25)

            val newExcitement = (mood.excitement + excitementDelta).coerceIn(0, 100)
            val newFatigue = (mood.fatigue + fatigueDelta).coerceIn(0, 100)
            val newSatisfaction = (mood.satisfaction + contribution - newFatigue / 10).coerceIn(0, 100)
            mood = AudienceMood(newExcitement, newFatigue, newSatisfaction)

            val reaction = when {
                contribution >= 20 -> "🎉 全场沸腾！"
                contribution >= 15 -> "👏 掌声雷动！"
                contribution >= 10 -> "😊 反响不错"
                contribution >= 7 -> "😐 反应平淡"
                else -> "😴 有人打哈欠..."
            }

            results.add(ActResult(act, positionMatch, reaction, contribution))
            _culturalFestState.value = _culturalFestState.value.copy(
                audienceMood = mood,
                actResults = results.toList()
            )
        }

        delay(600L)
        // 演出结束，计算总分
        val totalContribution = results.sumOf { it.scoreContribution }
        val totalScore = (totalContribution + mood.satisfaction / 2).coerceIn(0, 100)

        _culturalFestState.value = _culturalFestState.value.copy(
            phase = CulturalFestPhase.SHOW_RESULTS,
            totalScore = totalScore,
            resultMessage = when {
                totalScore >= 85 -> "🏆 精彩绝伦！观众纷纷喝彩！"
                totalScore >= 70 -> "🎉 演出圆满成功！"
                totalScore >= 55 -> "👍 还不错，有待提升。"
                else -> "😅 节目编排需要改进..."
            }
        )

        // 发出结果
        val performance = totalScore / 100f
        val activity = _activeActivity.value ?: return
        _miniGameResult.value = MiniGameResult(
            activityId = activity.id,
            activityType = activity.type,
            performanceScore = performance,
            specialAchievement = performance >= 0.85f,
            resultMessage = when {
                performance >= 0.85f -> "文艺汇演轰动全校！🎭"
                performance >= 0.7f -> "文艺汇演圆满成功！"
                performance >= 0.5f -> "文艺汇演顺利举办。"
                else -> "文艺汇演有些冷场，下次注意节目编排。"
            }
        )
        // 回传小游戏表现到季节活动系统
        gameEngine.seasonalActivityManager.applyMiniGamePerformance(activity.id, performance)
    }

    fun closeCulturalFest() {
        raceAnimationJob?.cancel()
        finishCurrentMiniGame()
    }
}
