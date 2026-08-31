package com.arktools.xiaozhang.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiaozhang.audio.AudioManager
import com.arktools.xiaozhang.domain.engine.GameEngine

import com.arktools.xiaozhang.domain.model.ClassTier
import com.arktools.xiaozhang.domain.model.School
import com.arktools.xiaozhang.domain.model.TeacherLevel
import com.arktools.xiaozhang.domain.repository.CourseRepository
import com.arktools.xiaozhang.domain.repository.ResearchRepository
import com.arktools.xiaozhang.domain.repository.SchoolRepository
import com.arktools.xiaozhang.domain.repository.StudentRepository
import com.arktools.xiaozhang.domain.repository.TeacherRepository
import com.arktools.xiaozhang.data.pref.SettingsDataStore
import com.arktools.xiaozhang.data.save.PersistenceCoordinator
import com.arktools.xiaozhang.data.save.SaveManager
import com.arktools.xiaozhang.domain.ad.SpeedBoostManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import com.arktools.xiaozhang.util.safeLaunch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val schoolRepository: SchoolRepository,
    private val teacherRepository: TeacherRepository,
    private val studentRepository: StudentRepository,
    private val courseRepository: CourseRepository,
    private val researchRepository: ResearchRepository,
    private val settingsDataStore: SettingsDataStore,
    private val gameEngine: GameEngine,
    private val audioManager: AudioManager,
    private val saveManager: SaveManager,
    private val persistenceCoordinator: PersistenceCoordinator,
    val achievementManager: com.arktools.xiaozhang.domain.achievement.AchievementManager,
    val speedBoostManager: SpeedBoostManager,
    private val policyManager: com.arktools.xiaozhang.domain.policy.SchoolPolicyManager
) : ViewModel() {

    val disciplinaryPause: StateFlow<GameEngine.DisciplinaryPause?> = gameEngine.disciplinaryPause

    private val _school = MutableStateFlow<School?>(null)
    val school: StateFlow<School?> = _school.asStateFlow()

    private val _isGameRunning = MutableStateFlow(false)
    val isGameRunning: StateFlow<Boolean> = _isGameRunning.asStateFlow()

    private val _storyTutorialPending = MutableStateFlow(false)
    val storyTutorialPending: StateFlow<Boolean> = _storyTutorialPending.asStateFlow()

    @Volatile
    private var tutorialRewardsGranted: Boolean = false

    fun consumeStoryTutorialPending() {
        _storyTutorialPending.value = false
    }

    fun requestStoryTutorialReplay() {
        tutorialRewardsGranted = false
        _storyTutorialPending.value = true
    }

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    // 自动存档通知：UI 层收到后展示轻量 toast，几秒后自动清除
    private val _autoSaveNotification = MutableStateFlow(false)
    val autoSaveNotification: StateFlow<Boolean> = _autoSaveNotification.asStateFlow()

    val gameSpeed: StateFlow<Float> = settingsDataStore.gameSpeed
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1f)

    // 教程用：监听教师数量
    val teacherCount: StateFlow<Int> = teacherRepository.getTeachersFlow()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val teachingConfigured: StateFlow<Boolean> = gameEngine.teachingManager.state
        .map { it.config.totalClasses > 0 && it.initialized }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // 教程用：监听学生入学数量
    val studentCount: StateFlow<Int> = studentRepository.observeActiveStudentCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // 是否需要显示看广告解锁加速的弹窗
    private val _showSpeedAdDialog = MutableStateFlow(false)
    val showSpeedAdDialog: StateFlow<Boolean> = _showSpeedAdDialog.asStateFlow()

    // 用户想要切换到的目标速度（看广告后应用）
    private val _pendingSpeed = MutableStateFlow(1f)

    // 双倍收益广告弹窗
    private val _showDoubleIncomeAd = MutableStateFlow(false)
    val showDoubleIncomeAd: StateFlow<Boolean> = _showDoubleIncomeAd.asStateFlow()
    private val _pendingBonusAmount = MutableStateFlow(0.0)
    val pendingBonusAmount: StateFlow<Double> = _pendingBonusAmount.asStateFlow()

    // 广告奖励到账确认通知（显示后几秒清除）
    private val _rewardNotification = MutableStateFlow<String?>(null)
    val rewardNotification: StateFlow<String?> = _rewardNotification.asStateFlow()

    fun setGameSpeed(speed: Float) {
        viewModelScope.safeLaunch {
            settingsDataStore.setGameSpeed(speed)
        }
    }

    /**
     * 用户点击速度按钮时调用
     * 如果目标速度 > 1x 且加速未解锁，弹出看广告提示
     */
    fun requestSpeedChange(speed: Float) {
        if (speed <= 1f || speedBoostManager.isBoostActive()) {
            setGameSpeed(speed)
        } else {
            _pendingSpeed.value = speed
            _showSpeedAdDialog.value = true
        }
    }

    fun dismissSpeedAdDialog() {
        _showSpeedAdDialog.value = false
    }

    /**
     * 看广告成功后调用
     */
    fun onSpeedAdRewarded() {
        speedBoostManager.activateBoost()
        _showSpeedAdDialog.value = false
        setGameSpeed(_pendingSpeed.value)
    }

    /**
     * 加速到期，重置为1x
     */
    fun onBoostExpired() {
        setGameSpeed(1f)
    }

    /**
     * 双倍收益：关闭弹窗
     */
    fun dismissDoubleIncomeAd() {
        _showDoubleIncomeAd.value = false
    }

    /**
     * 双倍收益：看广告成功，额外发放一份月收入
     */
    fun onDoubleIncomeRewarded() {
        val bonus = _pendingBonusAmount.value
        if (bonus > 0) {
            viewModelScope.safeLaunch {
                schoolRepository.addCash(bonus)
                // 显示奖励到账通知（bonus 单位已经是万元，无需再除以 10000）
                val formatted = String.format("%.1f", bonus)
                _rewardNotification.value = "恭喜获得双倍收益奖励 +${formatted}万元！"
                kotlinx.coroutines.delay(3000)
                _rewardNotification.value = null
            }
        }
        _showDoubleIncomeAd.value = false
    }



    // 读档后自动进入游戏的标记（供 UI 层检查）
    private val _justLoadedSave = MutableStateFlow(false)
    val justLoadedSave: StateFlow<Boolean> = _justLoadedSave.asStateFlow()

    init {
        audioManager.init()
        loadSchool()
        // 检查是否刚完成读档（进程重启后）：自动直接进入游戏
        if (saveManager.consumeJustLoaded()) {
            _justLoadedSave.value = true
            startLoadedGame()
        }
        // Sync pause state with engine (but NOT isGameRunning — that's menu vs game)
        viewModelScope.safeLaunch {
            gameEngine.isPausedFlow.collect { paused ->
                _isPaused.value = paused
            }
        }
        // 监听月结算信号，弹出双倍收益广告
        viewModelScope.safeLaunch {
            gameEngine.monthlyRevenueBonus.collect { revenue ->
                _pendingBonusAmount.value = revenue
                _showDoubleIncomeAd.value = true
            }
        }
    }

    /**
     * 读档后自动进入游戏。短暂保留 justLoadedSave 标记，避免重启后首帧/月初自动保存覆盖刚加载的档。
     */
    private fun startLoadedGame() {
        gameEngine.start()
        audioManager.startBgm()
        _isGameRunning.value = true
        _isPaused.value = false
        viewModelScope.safeLaunch {
            kotlinx.coroutines.delay(3000L)
            _justLoadedSave.value = false
        }
    }

    /**
     * 兼容旧 UI 调用：读档已自动恢复，只需清掉提示标记。
     */
    fun resumeAfterLoad() {
        _justLoadedSave.value = false
        audioManager.startBgm()
        gameEngine.resume()
    }

    private var lastAutoSaveMonth = -1

    private fun loadSchool() {
        viewModelScope.safeLaunch {
            schoolRepository.getSchoolFlow().collect { school ->
                _school.value = school
                if (school != null) {
                    // 兜底同步 schoolId：旧版本存档可能没记录 schoolId，不同步会导致
                    // 学生/教师/课程按 schoolId 过滤后全部显示为 0（表现为"所有数据清零"）。
                    viewModelScope.safeLaunch {
                        val savedSchoolId = settingsDataStore.getSchoolId()
                        if (savedSchoolId != school.id) {
                            settingsDataStore.setSchoolId(school.id)
                        }
                    }
                }
                // 根据学校状态智能切换 BGM
                if (school != null && _isGameRunning.value) {
                    updateBgmByState(school)
                    // Auto-save every month (when day resets to 1 and month changes)
                    // 读档恢复阶段不触发自动存档（避免覆盖刚加载的存档）
                    if (!_justLoadedSave.value && school.currentDay == 1 && school.currentMonth != lastAutoSaveMonth) {
                        lastAutoSaveMonth = school.currentMonth
                        performAutoSave(school)
                    }
                    // 初始化 lastAutoSaveMonth 避免读档后首次月初误触发
                    if (lastAutoSaveMonth == -1) {
                        lastAutoSaveMonth = school.currentMonth
                    }
                }
            }
        }
    }

    private fun performAutoSave(school: School) {
        viewModelScope.safeLaunch {
            val saved = persistenceCoordinator.requestAutoSave(
                "monthly-${school.currentYear}-${school.currentMonth}"
            ).await()
            if (!saved) return@safeLaunch

            _autoSaveNotification.value = true
            kotlinx.coroutines.delay(2000L)
            _autoSaveNotification.value = false
        }
    }

    private var lastBgmType: AudioManager.BgmType? = null

    private fun updateBgmByState(school: School) {
        val bgmType = when {
            school.cash < 50.0 -> AudioManager.BgmType.CRISIS       // 资金低于5万 → 紧张
            school.reputation > 500 && school.cash > 100 -> AudioManager.BgmType.BUSY  // 高声誉高资金 → 繁忙
            school.currentMonth in listOf(7, 8, 1, 2) -> AudioManager.BgmType.RELAXED  // 假期月份 → 轻松
            else -> AudioManager.BgmType.MAIN                       // 默认主题
        }
        if (bgmType != lastBgmType) {
            lastBgmType = bgmType
            audioManager.switchBgm(bgmType)
        }
    }

    fun startGame() {
        gameEngine.start()
        audioManager.startBgm()
        _isGameRunning.value = true
    }

    private val _needsRestart = MutableStateFlow(false)
    val needsRestart: StateFlow<Boolean> = _needsRestart.asStateFlow()

    /** 新存档开场漫画：仅 newGame 置位，看完/跳过后写进度不再出现 */
    data class OpeningStoryState(
        val show: Boolean = false,
        val principalName: String = "",
        val schoolName: String = ""
    )

    private val _showOpeningStory = MutableStateFlow(OpeningStoryState())
    val showOpeningStory: StateFlow<OpeningStoryState> = _showOpeningStory.asStateFlow()

    fun markOpeningStorySeen() {
        _showOpeningStory.value = OpeningStoryState()
        viewModelScope.safeLaunch {
            val pm = gameEngine.policyManager
            pm.replaceCollegeDevelopment(
                pm.policies.value.collegeDevelopment.copy(openingStoryDone = true)
            )
            schoolRepository.mutateSchool { school ->
                school.policyJson = pm.toJson()
                true
            }
        }
    }

    /**
     * 主菜单“继续游戏”：live 数据库有学校则直接继续；
     * live 数据库为空时，从所有历史候选中恢复修订时间最新的有效备份并重启进程。
     */
    fun continueGame() {
        viewModelScope.safeLaunch {
            val school = runCatching {
                schoolRepository.getSchool()
            }.getOrNull()
            val backupAvailable = withContext(Dispatchers.IO) {
                saveManager.hasAnyBackupData()
            }
            if (school != null) {
                startGame()
            } else if (backupAvailable) {
                if (persistenceCoordinator.restoreLatestBackup()) {
                    _needsRestart.value = true
                }
            }
        }
    }

    fun pauseGame() {
        gameEngine.pause()
        audioManager.pauseBgm()
        // Do NOT change _isGameRunning — pausing stays in game view with pause overlay
    }

    fun resumeGame() {
        gameEngine.resume()
        audioManager.resumeBgm()
    }

    /** 广告展示前暂停引擎 tick（不暂停 BGM，由 Activity 生命周期处理） */
    private var wasRunningBeforeAd = false
    fun pauseForAd() {
        wasRunningBeforeAd = !gameEngine.isPausedFlow.value
        gameEngine.pause()
    }

    /** 广告结束后恢复引擎 tick */
    fun resumeAfterAd() {
        if (wasRunningBeforeAd && gameEngine.disciplinaryPause.value == null) {
            gameEngine.resume()
        }
    }

    fun recoverFromDisciplinaryPause() {
        viewModelScope.safeLaunch {
            if (gameEngine.recoverFromDisciplinaryPause()) {
                audioManager.resumeBgm()
            }
        }
    }

    /** 设置页"强制时间流动"兜底：游戏时间异常卡住时恢复流动。 */
    fun forceResumeTimeFlow(): Boolean = gameEngine.forceResumeTimeFlow()

    /** 教程期间抑制所有事件弹窗 */
    fun setEventsSuppressed(suppressed: Boolean) {
        gameEngine.eventsSuppressed = suppressed
    }

    /**
     * 跳过教程时赠送初始奖励——与教程正常完成流程完全一致：
     * 1. 赠送1名C级教师（教程中玩家只招1名）
     * 2. 配置默认教学方案：1个专业核心班 + 2个通识教学班
     * 3. 触发招生，获得第一批学生（教程中 WAIT_ENROLLMENT 步骤效果）
     */
    fun grantSkipTutorialRewards() {
        if (tutorialRewardsGranted) return
        tutorialRewardsGranted = true
        viewModelScope.safeLaunch {
            val existingTeachers = teacherRepository.getTeachers()
            if (existingTeachers.isEmpty()) {
                val teacher = teacherRepository.generateCandidates(TeacherLevel.C, 1).first()
                teacherRepository.hireTeacher(teacher)
                gameEngine.refreshTimetablesForTeacherChange()
            }

            val teachingManager = gameEngine.teachingManager
            if (teachingManager.config.classDistribution.isEmpty()) {
                teachingManager.setClassDistribution(
                    mapOf(ClassTier.KEY to 1, ClassTier.NORMAL to 2)
                )
            }

            if (studentRepository.getActiveStudentCount() <= 0) {
                gameEngine.resume()
                gameEngine.triggerEnrollmentForTutorial()
            }
        }
    }

    /** 教程专用：直接触发招生，不让玩家等2.5分钟 */
    fun triggerEnrollmentForTutorial() {
        viewModelScope.safeLaunch {
            gameEngine.triggerEnrollmentForTutorial()
        }
    }

    fun newGame(
        schoolName: String,
        principalName: String,
        tierKey: String = "APPLIED",
        ownershipKey: String = "PRIVATE",
        foundingStyle: String? = null
    ) {
        viewModelScope.safeLaunch {
            try {
                val school = persistenceCoordinator.runExclusiveDestructiveOperation(
                    "new-game"
                ) {
                    gameEngine.stopAndJoin()
                    val created = schoolRepository.createNewSchool(
                        schoolName,
                        principalName,
                        tierKey,
                        ownershipKey
                    )
                    gameEngine.resetForNewGame()
                    created
                }

                // 默认科研数据在新学校事务提交后初始化；失败不会影响已创建学校或旧档保护快照。
                researchRepository.initializeDefaultMethods(school.id)
                if (foundingStyle != null) {
                    val allowed = com.arktools.xiaozhang.domain.model.SchoolTier
                        .fromKey(tierKey).allowedColleges
                    val bonus = policyManager.applyFoundingStyle(foundingStyle, allowed)
                    if (bonus > 0) {
                        schoolRepository.mutateSchool { s ->
                            s.cash += bonus
                            s.policyJson = policyManager.toJson()
                            true
                        }
                    }
                }
                _isGameRunning.value = false
                tutorialRewardsGranted = false
                _storyTutorialPending.value = true
                _showOpeningStory.value = OpeningStoryState(
                    show = true,
                    principalName = created.principalName,
                    schoolName = created.name
                )
                startGame()
            } catch (e: Exception) {
                android.util.Log.e(
                    "MainVM",
                    "New game was blocked or failed; existing progress was preserved",
                    e
                )
            }
        }
    }

    /**
     * 清除当前游戏。清档前必须成功创建保护快照，并等待引擎写入完全排空。
     */
    fun resetGame() {
        viewModelScope.safeLaunch {
            try {
                persistenceCoordinator.runExclusiveDestructiveOperation(
                    "reset-game"
                ) {
                    gameEngine.stopAndJoin()
                    schoolRepository.deleteAll()
                    gameEngine.resetForNewGame()
                }
                _school.value = null
                _isGameRunning.value = false
            } catch (e: Exception) {
                android.util.Log.e(
                    "MainVM",
                    "Reset was blocked or failed; existing progress was preserved",
                    e
                )
            }
        }
    }

    /**
     * Called when game is being backgrounded or activity is stopping.
     * Triggers auto-save if a game is running.
     */
    fun autoSaveOnPause() {
        val school = _school.value ?: return
        if (_isGameRunning.value) {
            performAutoSave(school)
        }
    }

    override fun onCleared() {
        super.onCleared()
        persistenceCoordinator.requestAutoSave("viewmodel-onCleared")
        // 不要 release() 单例 AudioManager —— SoundPool.release() 是不可逆的。
        audioManager.stopBgm()
    }
}
