package com.arktools.xiaozhang.ui.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiaozhang.audio.AudioManager
import com.arktools.xiaozhang.domain.autohandle.AutoHandleManager
import com.arktools.xiaozhang.domain.autohandle.AutoHandleResult
import com.arktools.xiaozhang.domain.engine.GameEngine
import com.arktools.xiaozhang.domain.model.FacilityBonusCalculator
import com.arktools.xiaozhang.domain.model.GameEvent

import com.arktools.xiaozhang.domain.repository.SchoolRepository
import com.arktools.xiaozhang.domain.repository.TeacherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import com.arktools.xiaozhang.domain.model.School
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.arktools.xiaozhang.util.safeLaunch

@HiltViewModel
class EventViewModel @Inject constructor(
    private val gameEngine: GameEngine,
    private val schoolRepository: SchoolRepository,
    private val teacherRepository: TeacherRepository,
    private val audioManager: AudioManager,
    private val autoHandleManager: AutoHandleManager
) : ViewModel() {

    // 事件队列：月度结算可能产生多个事件，必须逐个展示给玩家
    private val _eventQueue = MutableStateFlow<List<GameEvent>>(emptyList())
    private val _currentEvent = MutableStateFlow<GameEvent?>(null)
    val currentEvent: StateFlow<GameEvent?> = _currentEvent.asStateFlow()
    private var claimedEvent: GameEvent? = null

    private fun claimCurrentEvent(event: GameEvent): Boolean {
        if (_currentEvent.value !== event || claimedEvent === event) return false
        claimedEvent = event
        return true
    }

    private fun releaseClaim(event: GameEvent) {
        if (claimedEvent === event) claimedEvent = null
    }

    /** 待处理事件数量（UI可显示为角标） */
    val pendingEventCount: StateFlow<Int> get() = MutableStateFlow(_eventQueue.value.size).asStateFlow()

    private val _eventHistory = MutableStateFlow<List<GameEvent>>(emptyList())
    val eventHistory: StateFlow<List<GameEvent>> = _eventHistory.asStateFlow()

    /** 当前学校信息（签字动画用） */
    val currentSchool: StateFlow<School?> = schoolRepository.getSchoolFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        collectEvents()
    }

    private fun collectEvents() {
        viewModelScope.safeLaunch {
            gameEngine.events.collect { event ->
                try {
                    // 所有事件都进历史记录
                    _eventHistory.value = _eventHistory.value + event

                    // 事件到达音效：危机、毕业、达标各有专属反馈
                    playArrivalSound(event)

                    // 检查自动处理
                    val autoResult = autoHandleManager.shouldAutoHandle(event)

                    if (autoResult != null) {
                        // 自动处理此事件
                        handleAutoResult(event, autoResult)
                    } else {
                        // 判断事件是否值得弹窗（有实质影响或需要玩家选择）
                        if (shouldPopup(event)) {
                            enqueueEvent(event)
                        }

                        // 非选择事件的效果立即应用（正面/负面奖惩不需等玩家操作）
                        // 选择事件效果在 handleChoice 中由玩家决定后才应用
                        if (event !is GameEvent.ChoiceEvent) {
                            applyEventEffects(event)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("EventViewModel", "collectEvents processing failed: ${event::class.simpleName}", e)
                }
            }
        }
    }

    /**
     * 事件到达时的即时反馈音效（与效果结算音效解耦，避免叠加）
     */
    private fun playArrivalSound(event: GameEvent) {
        when {
            event.title.startsWith("[突发危机]") ||
                event.title.startsWith("[危机进展]") ||
                event.title.contains("紧急危机") -> audioManager.playCrisisAlert()
            event.title.contains("毕业生喜讯") ||
                event.title.contains("毕业就业放榜") ||
                event.title.contains("危机解除") -> audioManager.playGraduation()
            event.title.contains("新学年开学") -> audioManager.playAdmissionSeason()
            event.title.contains("大二分专业") -> audioManager.playMajorAssign()
            event.title.contains("师资缺口") -> audioManager.playFacultyGap()
            event.title.contains("学年目标未完成") -> audioManager.playGoalFail()
        }
    }

    /**
     * 处理自动处理结果
     */
    private suspend fun handleAutoResult(event: GameEvent, result: AutoHandleResult) {
        when (result) {
            is AutoHandleResult.AutoClose -> {
                // 自动关闭：直接应用效果，不弹窗
                if (event !is GameEvent.ChoiceEvent) {
                    applyEventEffects(event)
                }
                autoHandleManager.recordAutoHandle(event, "auto_close")
            }
            is AutoHandleResult.AutoChoice -> {
                // 自动选择：模拟玩家选择
                val choiceEvent = event as? GameEvent.ChoiceEvent ?: return
                val choice = choiceEvent.choices.getOrNull(result.choiceIndex) ?: return
                autoHandleManager.recordAutoHandle(event, 
                    if (result.choiceIndex == 0) "auto_approve" else "auto_reject")
                // 执行选择后果（与 handleChoice 相同逻辑）
                executeChoiceConsequence(choiceEvent, result.choiceIndex)
            }
        }
    }

    /**
     * 执行选择后果（供 handleChoice 和自动处理共用）
     */
    private suspend fun executeChoiceConsequence(event: GameEvent.ChoiceEvent, choiceIndex: Int) {
        val choice = event.choices.getOrNull(choiceIndex) ?: return

        // 判断是否为突发危机事件
        val isCrisisEvent = event.title.startsWith("[突发危机]") || event.title.startsWith("[危机进展]")
        val consequence: com.arktools.xiaozhang.domain.model.EventConsequence
        var crisisFollowUp: GameEvent? = null

        if (isCrisisEvent && gameEngine.crisisScenarioManager.hasActiveCrisis()) {
            val school = schoolRepository.getSchool()
            val daysSinceStart = if (school != null) {
                (school.currentYear - school.foundedYear) * 360 +
                    (school.currentMonth - 1) * 30 + school.currentDay
            } else 0
            val (nextEvent, crisisConsequence) = gameEngine.crisisScenarioManager.advancePhase(choiceIndex, daysSinceStart)
            consequence = crisisConsequence
            crisisFollowUp = nextEvent
        } else {
            consequence = choice.consequence
        }

        // 原子操作：现金和声望变更
        if (consequence.cashChange != 0.0 || consequence.reputationChange != 0L) {
            schoolRepository.mutateSchool { school ->
                school.cash += consequence.cashChange
                school.reputation += consequence.reputationChange
                true
            }
        }

        // 教师忠诚度变化
        if (consequence.teacherLoyaltyChange != 0) {
            teacherRepository.adjustAllLoyalty(
                consequence.teacherLoyaltyChange
            )
        }

        // 教师审批动作
        consequence.teacherAction?.let { action ->
            executeTeacherAction(action)
        }

        // 派系事件选项（幂等，只结算一次）
        consequence.factionChoiceAction?.let { action ->
            gameEngine.applyFactionEventChoice(action)
        }

        // 季节活动审批动作
        consequence.activityAction?.let { action ->
            executeActivityAction(action)
        }

        // 社团审批动作
        consequence.clubAction?.let { action ->
            executeClubAction(action)
        }

        // 危机后续事件
        if (crisisFollowUp != null) {
            _eventHistory.value = _eventHistory.value + crisisFollowUp
            // 自动处理：危机后续也可能需要自动处理
            val nextAutoResult = autoHandleManager.shouldAutoHandle(crisisFollowUp)
            if (nextAutoResult != null) {
                handleAutoResult(crisisFollowUp, nextAutoResult)
            } else {
                _currentEvent.value = crisisFollowUp
            }
        } else {
            // 普通 followUpEvent 连锁事件
            val followUp = consequence.followUpEvent
            if (followUp != null) {
                _eventHistory.value = _eventHistory.value + followUp
                applyEventEffects(followUp)
            }
        }
    }

    private suspend fun executeTeacherAction(action: com.arktools.xiaozhang.domain.model.TeacherAction) {
        when (action) {
            is com.arktools.xiaozhang.domain.model.TeacherAction.ApproveResignation -> {
                teacherRepository.fireTeacher(action.teacherId)
                gameEngine.pressureSystemManager.expireContract(action.teacherId)
                gameEngine.refreshTimetablesForTeacherChange()
            }
            is com.arktools.xiaozhang.domain.model.TeacherAction.RetainWithRaise -> {
                teacherRepository.retainWithRaise(
                    action.teacherId,
                    action.raisePercent
                )
            }
            is com.arktools.xiaozhang.domain.model.TeacherAction.ForceRetain -> { /* 已废弃 */ }
            is com.arktools.xiaozhang.domain.model.TeacherAction.ApproveRaise -> {
                if (
                    teacherRepository.approveRaise(
                        action.teacherId,
                        action.raisePercent.toDouble()
                    )
                ) {
                    val school = schoolRepository.getSchool()
                    if (school != null) {
                        val absMonth = (school.currentYear - school.foundedYear) * 12 + school.currentMonth
                        gameEngine.pressureSystemManager.approveRaise(action.teacherId, absMonth)
                    }
                }
            }
            is com.arktools.xiaozhang.domain.model.TeacherAction.RejectRaise -> {
                val loyaltyPenalty = gameEngine.pressureSystemManager.rejectRaise(action.teacherId)
                teacherRepository.adjustLoyalty(
                    action.teacherId,
                    -loyaltyPenalty
                )
            }
            is com.arktools.xiaozhang.domain.model.TeacherAction.RenewContract -> {
                if (
                    teacherRepository.renewContract(
                        action.teacherId,
                        action.newSalary
                    )
                ) {
                    val school = schoolRepository.getSchool()
                    if (school != null) {
                        val absMonth = (school.currentYear - school.foundedYear) * 12 + school.currentMonth
                        gameEngine.pressureSystemManager.renewContract(action.teacherId, absMonth)
                    }
                }
            }
            is com.arktools.xiaozhang.domain.model.TeacherAction.DeclineRenewal -> {
                teacherRepository.fireTeacher(action.teacherId)
                gameEngine.pressureSystemManager.expireContract(action.teacherId)
                gameEngine.refreshTimetablesForTeacherChange()
            }
        }
    }

    private fun executeActivityAction(action: com.arktools.xiaozhang.domain.model.ActivityAction) {
        when (action) {
            is com.arktools.xiaozhang.domain.model.ActivityAction.Approve -> {
                val scale = com.arktools.xiaozhang.domain.seasonal.ActivityScale.valueOf(action.scaleName)
                gameEngine.seasonalActivityManager.approveActivity(action.activityId, scale)
            }
            is com.arktools.xiaozhang.domain.model.ActivityAction.Reject -> {
                val penalty = gameEngine.seasonalActivityManager.rejectActivity(action.activityId)
                if (penalty > 0) {
                    viewModelScope.safeLaunch {
                        schoolRepository.deductReputation(penalty.toLong())
                    }
                }
            }
        }
    }

    private fun executeClubAction(action: com.arktools.xiaozhang.domain.model.ClubAction) {
        when (action) {
            is com.arktools.xiaozhang.domain.model.ClubAction.Approve -> {
                gameEngine.clubManager.approveApplication(action.applicationId)
            }
            is com.arktools.xiaozhang.domain.model.ClubAction.Reject -> {
                val penalty = gameEngine.clubManager.rejectApplication(action.applicationId)
                if (penalty > 0) {
                    viewModelScope.safeLaunch {
                        schoolRepository.deductReputation(penalty.toLong())
                    }
                }
            }
            is com.arktools.xiaozhang.domain.model.ClubAction.CreateDirectly -> {
                val clubType = com.arktools.xiaozhang.domain.club.ClubType.valueOf(action.clubTypeName)
                gameEngine.clubManager.createClubDirectly(clubType)
            }
        }
    }

    /**
     * 将事件加入队列。如果当前没有弹窗显示，则立即展示。
     */
    private fun enqueueEvent(event: GameEvent) {
        if (_currentEvent.value == null) {
            _currentEvent.value = event
        } else {
            _eventQueue.value = _eventQueue.value + event
        }
    }

    /**
     * 从队列中取出下一个事件展示
     */
    private fun showNextEvent() {
        claimedEvent = null
        val queue = _eventQueue.value
        if (queue.isNotEmpty()) {
            _currentEvent.value = queue.first()
            _eventQueue.value = queue.drop(1)
        } else {
            _currentEvent.value = null
        }
    }

    /**
     * 判断事件是否需要弹窗打扰玩家
     * - 选择事件：必须弹窗（需要玩家决策）
     * - 里程碑事件：必须弹窗（重要成就）
     * - 毕业生喜讯/教师灵感等低影响正面事件：不弹窗，进通知中心
     * - 教师离职通知（非选择类）：不弹窗，进通知中心
     * - 有较大现金/声望影响的：弹窗
     * - 纯信息通知（无影响）：不弹窗，仅进历史
     * 
     * 注意：更细粒度的弹窗控制由校长办公室的「事件自动处理」配置实现（AutoHandleManager）
     */
    private fun shouldPopup(event: GameEvent): Boolean {
        return when (event) {
            is GameEvent.ChoiceEvent -> true  // 需要玩家决策
            is GameEvent.MilestoneEvent -> true  // 重要成就
            is GameEvent.PositiveEvent -> {
                val isLowImpact = event.bonusCash <= 0.0 && event.bonusReputation <= 5L && event.bonusTeacherSkill <= 0
                val isGraduationNews = event.title.contains("毕业生喜讯")
                val isInspirationEvent = event.title.contains("灵感")
                val isClubEvent = event.title.contains("社团")
                val isEnrollmentNews = event.title.contains("新学年开学") || event.title.contains("大二分专业")
                if (isGraduationNews || isInspirationEvent || isClubEvent) {
                    false
                } else if (isEnrollmentNews) {
                    true
                } else if (isLowImpact) {
                    false
                } else {
                    event.bonusCash > 0.0 || event.bonusReputation > 0L || event.bonusTeacherSkill > 0
                }
            }
            is GameEvent.NegativeEvent -> {
                val isTeacherLeaveNotice = event.title.contains("离职") && event.penaltyCash <= 0.0
                val isLowPenalty = event.penaltyCash <= 0.0 && event.penaltyReputation <= 3L
                if (isTeacherLeaveNotice || isLowPenalty) {
                    false
                } else {
                    event.penaltyCash > 0.0 || event.penaltyReputation > 3L
                }
            }
            else -> true
        }
    }

    private suspend fun applyEventEffects(event: GameEvent) {
        when (event) {
            is GameEvent.PositiveEvent -> {
                when {
                    event.title.contains("学年目标达成") ->
                        audioManager.playGoalPass()
                    // 到达音已覆盖：开学/分专业/毕业/师资缺口/目标未完成
                    event.title.contains("新学年开学") ||
                        event.title.contains("大二分专业") ||
                        event.title.contains("毕业生喜讯") ||
                        event.title.contains("毕业就业放榜") ||
                        event.title.contains("师资缺口") ||
                        event.title.contains("学年目标未完成") -> Unit
                    else -> audioManager.playEventPositive()
                }
                if (event.bonusCash > 0.0 || event.bonusReputation > 0L) {
                    // 原子操作：现金和声望变更在同一事务中完成
                    // 设施加成：礼堂的 eventRewardBonus 提升事件奖励
                    schoolRepository.mutateSchool { school ->
                        val facilityBonuses = FacilityBonusCalculator.calculate(school.facilities)
                        val rewardMultiplier = 1.0 + facilityBonuses.eventRewardBonus
                        if (event.bonusCash > 0.0) school.cash += event.bonusCash * rewardMultiplier
                        if (event.bonusReputation > 0L) school.reputation += (event.bonusReputation * rewardMultiplier).toLong()
                        true
                    }
                }
                // 教师技能加成
                if (event.bonusTeacherSkill > 0) {
                    val teachers = teacherRepository.getTeachers()
                    if (teachers.isNotEmpty()) {
                        val luckyTeacher = teachers.random()
                        teacherRepository.trainTeacher(luckyTeacher.id)
                    }
                }
            }
            is GameEvent.NegativeEvent -> {
                audioManager.playEventNegative()
                if (event.penaltyCash > 0.0 || event.penaltyReputation > 0L) {
                    // 原子操作：现金和声望扣除在同一事务中完成
                    schoolRepository.mutateSchool { school ->
                        if (event.penaltyCash > 0.0) school.cash -= event.penaltyCash
                        if (event.penaltyReputation > 0L) school.reputation -= event.penaltyReputation
                        true
                    }
                }
            }
            is GameEvent.MilestoneEvent -> {
                audioManager.playMilestone()
            }
            else -> {}
        }
    }

    fun dismissEvent() {
        showNextEvent()
    }

    /**
     * 负面事件看广告成功：返还扣款（免罚）+ 额外补偿
     */
    fun onNegativeEventAdRewarded() {
        val event = _currentEvent.value as? GameEvent.NegativeEvent ?: return
        if (!claimCurrentEvent(event)) return
        viewModelScope.safeLaunch {
            try {
                checkNotNull(schoolRepository.mutateSchool { school ->
                    if (event.penaltyCash > 0.0) school.cash += event.penaltyCash
                    val repRefund = event.penaltyReputation + 3L
                    school.reputation += repRefund
                    true
                }) { "Negative event ad reward commit failed" }
                showNextEvent()
            } catch (e: Exception) {
                releaseClaim(event)
                throw e
            }
        }
    }

    /**
     * 正面事件看广告成功：额外发放一份奖励（双倍），并保底不低于一定数值
     */
    fun onPositiveEventAdRewarded() {
        val event = _currentEvent.value as? GameEvent.PositiveEvent ?: return
        if (!claimCurrentEvent(event)) return
        viewModelScope.safeLaunch {
            try {
                checkNotNull(schoolRepository.mutateSchool { school ->
                    val cashBonus = maxOf(event.bonusCash, 0.5)
                    school.cash += cashBonus
                    val repBonus = maxOf(event.bonusReputation, 5L)
                    school.reputation += repBonus
                    true
                }) { "Positive event ad reward commit failed" }
                showNextEvent()
            } catch (e: Exception) {
                releaseClaim(event)
                throw e
            }
        }
    }

    fun handleChoice(choiceIndex: Int) {
        val event = _currentEvent.value as? GameEvent.ChoiceEvent ?: return
        if (event.choices.getOrNull(choiceIndex) == null) return
        if (!claimCurrentEvent(event)) return

        viewModelScope.safeLaunch {
            try {
                executeChoiceConsequence(event, choiceIndex)
                if (_currentEvent.value === event) {
                    dismissEvent()
                } else {
                    releaseClaim(event)
                }
            } catch (e: Exception) {
                releaseClaim(event)
                throw e
            }
        }
    }
}
