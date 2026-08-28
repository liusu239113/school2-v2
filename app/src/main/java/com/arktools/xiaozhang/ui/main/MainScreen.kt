package com.arktools.xiaozhang.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.arktools.xiaozhang.ui.campus.CampusView
import com.arktools.xiaozhang.ui.external.ExternalScreen
import com.arktools.xiaozhang.ui.governance.GovernanceScreen
import com.arktools.xiaozhang.ui.hiring.HiringScreen
import com.arktools.xiaozhang.ui.campus.CampusView
import com.arktools.xiaozhang.BuildConfig
import com.arktools.xiaozhang.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arktools.xiaozhang.ui.animation.AnimationConstants
import com.arktools.xiaozhang.ui.animation.pressAnimation
import com.arktools.xiaozhang.ui.teaching.TeachingScreen
import com.arktools.xiaozhang.ui.district.DistrictScreen
import com.arktools.xiaozhang.ui.event.EventDialogContainer
import com.arktools.xiaozhang.ui.minigame.MiniGameContainer
import com.arktools.xiaozhang.ui.event.EventScreen
import com.arktools.xiaozhang.ui.overview.OverviewScreen
import com.arktools.xiaozhang.ui.ranking.RankingScreen
import com.arktools.xiaozhang.ui.research.ResearchScreen
import com.arktools.xiaozhang.ui.stock.StockScreen
import com.arktools.xiaozhang.ui.teacher.TeacherListScreen
import com.arktools.xiaozhang.ui.facility.FacilityScreen
import com.arktools.xiaozhang.ui.student.StudentScreen
import com.arktools.xiaozhang.ui.settings.SettingsScreen
import com.arktools.xiaozhang.ui.effects.ConfettiEffect
import com.arktools.xiaozhang.ui.gameover.CrisisDialog
import com.arktools.xiaozhang.ui.gameover.GameOverScreen
import com.arktools.xiaozhang.ui.gameover.GameOverViewModel

import com.arktools.xiaozhang.ui.theme.AccentGreen
import com.arktools.xiaozhang.ui.theme.AccentOrange
import com.arktools.xiaozhang.ui.theme.AccentRed
import com.arktools.xiaozhang.ui.tutorial.TutorialOverlay
import com.arktools.xiaozhang.ui.tutorial.TutorialManager
import com.arktools.xiaozhang.ui.tutorial.CompletionCondition
import com.arktools.xiaozhang.ui.utils.FormatUtils
import com.arktools.xiaozhang.domain.engine.CrisisState
import com.arktools.xiaozhang.ui.menu.MainMenuScreen
import com.arktools.xiaozhang.ui.menu.MenuViewModel
import com.arktools.adsdk.AdHelper
import com.arktools.adsdk.TosinAdInitializer
import com.arktools.adsdk.ui.PrivacyPolicyDialog
import com.arktools.xiaozhang.ui.login.TapTapLoginScreen
import com.arktools.xiaozhang.ui.login.ComplianceManager
import com.taptap.sdk.login.TapTapLogin
import com.arktools.xiaozhang.ui.achievement.AchievementScreen
import com.arktools.xiaozhang.ui.achievement.AchievementToastOverlay
import com.arktools.xiaozhang.ui.report.ReportScreen
import com.arktools.xiaozhang.ui.marketing.MarketingScreen
import com.arktools.xiaozhang.ui.notification.NotificationScreen
import com.arktools.xiaozhang.ui.alumni.AlumniScreen
import com.arktools.xiaozhang.ui.policy.PolicyScreen
import com.arktools.xiaozhang.ui.club.ClubScreen
import com.arktools.xiaozhang.ui.seasonal.SeasonalScreen

import com.arktools.xiaozhang.ui.reputation.ReputationScreen
import com.arktools.xiaozhang.ui.studentlife.StudentLifeScreen

import com.arktools.xiaozhang.ui.conference.ConferenceScreen


import com.arktools.xiaozhang.ui.parent.ParentScreen
import com.arktools.xiaozhang.ui.government.GovernmentScreen
import com.arktools.xiaozhang.ui.scholarship.ScholarshipScreen

import com.arktools.xiaozhang.ui.timetable.TimetableScreen
import com.arktools.xiaozhang.ui.exam.ExamScreen
import com.arktools.xiaozhang.ui.principal.PrincipalOfficeScreen
import com.arktools.xiaozhang.ui.notification.NotificationViewModel
import com.arktools.xiaozhang.domain.achievement.AchievementManager
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.arktools.xiaozhang.ui.components.AdLoadingOverlay
import com.arktools.xiaozhang.ui.components.FeatureLockedScreen
import com.arktools.xiaozhang.ui.components.PixelGameBackground
import com.arktools.xiaozhang.domain.engine.GameBalanceConfig


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
    gameOverViewModel: GameOverViewModel = hiltViewModel(),
    menuViewModel: MenuViewModel = hiltViewModel(),
    notificationViewModel: NotificationViewModel = hiltViewModel()
) {
    val school by viewModel.school.collectAsState()
    val isGameRunning by viewModel.isGameRunning.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val crisisState by gameOverViewModel.crisisState.collectAsState()
    val gameOverReason by gameOverViewModel.gameOverReason.collectAsState()
    val healthReport by gameOverViewModel.healthReport.collectAsState()
    val activeConditions by gameOverViewModel.activeConditions.collectAsState()
    val disciplinaryPause by viewModel.disciplinaryPause.collectAsState()
    val hasSaveData by menuViewModel.hasSaveData.collectAsState()
    val needsRestart by viewModel.needsRestart.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    // 保持总览页滚动位置（在 MainScreen 层 remember，不随 tab 切换销毁）
    val overviewListState = androidx.compose.foundation.lazy.rememberLazyListState()
    var showSettings by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    var showConfetti by remember { mutableStateOf(false) }
    var showTutorial by rememberSaveable { mutableStateOf(false) }
    val tutorialManager = remember { TutorialManager() }
    val unreadCount by notificationViewModel.unreadCount.collectAsState()
    val gameSpeed by viewModel.gameSpeed.collectAsState()
    val showSpeedAdDialog by viewModel.showSpeedAdDialog.collectAsState()
    val boostExpireTime by viewModel.speedBoostManager.boostExpireTime.collectAsState()
    val showDoubleIncomeAd by viewModel.showDoubleIncomeAd.collectAsState()
    val pendingBonusAmount by viewModel.pendingBonusAmount.collectAsState()
    val rewardNotification by viewModel.rewardNotification.collectAsState()
    var isAdLoading by remember { mutableStateOf(false) }

    // 加速到期检测：每秒检查一次，到期后回落1x
    LaunchedEffect(boostExpireTime) {
        if (boostExpireTime > 0L) {
            val remaining = boostExpireTime - System.currentTimeMillis()
            if (remaining > 0) {
                kotlinx.coroutines.delay(remaining)
                // 到期后如果当前速度 > 1x，回落
                if (viewModel.gameSpeed.value > 1f) {
                    viewModel.onBoostExpired()
                }
            }
        }
    }

    // === 门控状态：隐私协议 → TapTap登录 → 防沉迷（严格顺序，不可跳过） ===

    // 隐私政策状态
    val privacyManager = remember { com.arktools.adsdk.PrivacyPolicyManager(context) }
    var privacyAccepted by remember { mutableStateOf<Boolean?>(null) } // null=检查中
    var showPrivacyDialog by remember { mutableStateOf(false) }

    // TapTap 登录状态
    var isTapLoggedIn by remember { mutableStateOf(false) }
    // ═══ 测试期开关 ═══
    // debug 包直接进入游戏（跳过 TapTap 登录与防沉迷）；上线 release 包自动恢复三层门控
    val skipLoginAndCompliance = BuildConfig.DEBUG

    // 防沉迷状态：只有收到 LOGIN_SUCCESS(500) 才为 true
    var compliancePassed by remember { mutableStateOf(false) }
    var complianceBlocked by remember { mutableStateOf(false) }
    var complianceMessage by remember { mutableStateOf("") }

    // Step 1: 检查隐私政策（最先执行）
    LaunchedEffect(Unit) {
        privacyManager.isPrivacyAccepted.collect { accepted ->
            if (privacyAccepted == null) {
                privacyAccepted = accepted
                if (!accepted) {
                    showPrivacyDialog = true
                }
            }
        }
    }

    // Step 2: 隐私政策通过后，初始化 TapTap SDK 并检查登录状态
    LaunchedEffect(privacyAccepted) {
        if (privacyAccepted == true) {
            // 确保 TapTap SDK 已初始化（幂等，Activity重建时不会重复初始化）
            com.arktools.xiaozhang.TapSdkInitializer.ensureInitialized(context)
            // 初始化 Bugly 崩溃上报（幂等，内部会判断是否已初始化）
            com.tencent.bugly.crashreport.CrashReport.initCrashReport(
                context.applicationContext, "2b3a48166c", BuildConfig.DEBUG
            )
            // 初始化广告 SDK（幂等）
            try {
                TosinAdInitializer.getInstance().init(context.applicationContext as android.app.Application)
            } catch (_: Exception) { }
            val currentAccount = TapTapLogin.getCurrentTapAccount()
            if (currentAccount != null) {
                isTapLoggedIn = true
            }
        }
    }

    // Step 3: 隐私政策通过后注册防沉迷回调（SDK 必须已初始化）
    LaunchedEffect(privacyAccepted) {
        if (privacyAccepted != true) return@LaunchedEffect
        ComplianceManager.register(object : ComplianceManager.ComplianceListener {
            override fun onLoginSuccess() {
                // code=500: 唯一允许进入游戏的回调
                compliancePassed = true
                complianceBlocked = false
                complianceMessage = ""
            }
            override fun onExited() {
                // code=1000: 退出认证，返回登录页
                compliancePassed = false
                complianceBlocked = false
                isTapLoggedIn = false
            }
            override fun onSwitchAccount() {
                // code=1001: 切换账号，返回登录页
                compliancePassed = false
                complianceBlocked = false
                isTapLoggedIn = false
            }
            override fun onPeriodRestrict() {
                // code=1030: 宵禁，不可进入
                compliancePassed = false
                complianceBlocked = true
                complianceMessage = "当前为宵禁时段（22:00-8:00），无法进入游戏"
            }
            override fun onDurationLimit() {
                // code=1050: 无可玩时长，不可进入
                compliancePassed = false
                complianceBlocked = true
                complianceMessage = "今日游戏时长已用完，请明天再来"
            }
            override fun onAgeLimit() {
                // code=1100: 年龄限制，不可进入
                compliancePassed = false
                complianceBlocked = true
                complianceMessage = "根据国家相关规定，你的年龄暂时无法进入游戏"
            }
            override fun onRealNameStop() {
                // code=9002: 关闭实名窗口，不可跳过 → 阻止进入
                compliancePassed = false
                complianceBlocked = true
                complianceMessage = "需要完成实名认证才能进入游戏"
            }
            override fun onError(message: String) {
                // code=1200: 网络错误
                compliancePassed = false
                complianceBlocked = true
                complianceMessage = message
            }
        })
    }

    // 登录成功后自动启动防沉迷认证
    LaunchedEffect(isTapLoggedIn, compliancePassed) {
        if (isTapLoggedIn && !compliancePassed) {
            val activity = context as? android.app.Activity
            val currentAccount = TapTapLogin.getCurrentTapAccount()
            if (activity != null && currentAccount != null) {
                val userId = currentAccount.openId ?: currentAccount.unionId ?: ""
                if (userId.isNotEmpty()) {
                    ComplianceManager.startup(activity, userId)
                }
            }
        }
    }

    // 教程：监听教师数量、教学配置、学生入学状态变化
    val teacherCount by viewModel.teacherCount.collectAsState()
    val teachingConfigured by viewModel.teachingConfigured.collectAsState()
    val studentCount by viewModel.studentCount.collectAsState()

    LaunchedEffect(teacherCount, tutorialManager.currentStepIndex) {
        if (teacherCount > 0 && showTutorial) {
            tutorialManager.notifyAction(CompletionCondition.HIRE_TEACHER)
        }
    }
    LaunchedEffect(teachingConfigured, tutorialManager.currentStepIndex) {
        if (teachingConfigured && showTutorial) {
            tutorialManager.notifyAction(CompletionCondition.CONFIGURE_TEACHING)
        }
    }
    LaunchedEffect(studentCount, tutorialManager.currentStepIndex) {
        if (studentCount > 0 && showTutorial) {
            tutorialManager.notifyAction(CompletionCondition.WAIT_ENROLLMENT)
        }
    }

    // 教程：监听tab切换，通知教程管理器子页面导航完成
    LaunchedEffect(selectedTab) {
        if (showTutorial && tutorialManager.isActive) {
            tutorialManager.notifyNavigateToSubPage(selectedTab)
        }
    }

    // 教程期间暂停游戏时间流逝，教程结束后恢复
    // 但如果当前步骤标记了 unpauseGame=true，则取消暂停让游戏跑起来
    LaunchedEffect(showTutorial, tutorialManager.isActive, tutorialManager.currentStepIndex) {
        if (showTutorial && tutorialManager.isActive) {
            // 教程进行中：抑制所有事件弹窗
            viewModel.setEventsSuppressed(true)
            val currentStep = tutorialManager.currentStep
            if (currentStep.completionCondition == CompletionCondition.WAIT_ENROLLMENT) {
                // 教程等待入学步骤：直接触发招生，不让玩家傻等2.5分钟
                viewModel.resumeGame()
                viewModel.triggerEnrollmentForTutorial()
            } else if (currentStep.unpauseGame) {
                // 其他需要游戏运行的步骤
                viewModel.resumeGame()
            } else {
                viewModel.pauseGame()
            }
        } else if (!tutorialManager.isActive && showTutorial) {
            // 教程刚结束，恢复游戏并取消事件抑制
            showTutorial = false
            viewModel.setEventsSuppressed(false)
            viewModel.resumeGame()
        }
    }

    // 从自动存档恢复后需要重启进程以重建 Room/Hilt 单例，随后通过
    // consumeJustLoaded 自动进入游戏并暂停。
    LaunchedEffect(needsRestart) {
        if (needsRestart) {
            val activity = context as? android.app.Activity
            if (activity != null) {
                val pm = context.packageManager
                val intent = pm.getLaunchIntentForPackage(context.packageName)
                intent?.addFlags(
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
                context.startActivity(intent)
                activity.finish()
                Runtime.getRuntime().exit(0)
            }
        }
    }

    // === 严格三层门控：隐私协议 → TapTap登录 → 防沉迷 ===

    // 第一层门控：隐私协议（未同意时显示弹窗，不可跳过）
    if (privacyAccepted != true) {
        // 隐私政策弹窗
        if (showPrivacyDialog) {
            PrivacyPolicyDialog(
                appName = "校长我来当 2：大学时代",
                onAccepted = {
                    showPrivacyDialog = false
                    privacyAccepted = true
                    // 用户同意隐私政策后，才初始化 TapTap SDK（合规要求）
                    com.arktools.xiaozhang.TapSdkInitializer.ensureInitialized(context)
                    // 初始化 Bugly 崩溃上报（隐私政策同意后）
                    com.tencent.bugly.crashreport.CrashReport.initCrashReport(
                        context.applicationContext, "2b3a48166c", BuildConfig.DEBUG
                    )
                    // 安全初始化广告 SDK
                    try {
                        TosinAdInitializer.getInstance().init(context.applicationContext as android.app.Application)
                    } catch (e: Exception) {
                        android.util.Log.e("AdInit", "Ad SDK init failed, game continues without ads", e)
                    }
                },
                onDismiss = {
                    // 用户不同意隐私政策，退出应用
                    (context as? android.app.Activity)?.finishAffinity()
                }
            )
        }
        // 隐私协议未通过，阻止后续所有内容
        return
    }

    // 第二层门控：TapTap登录（未登录时显示登录界面）
    // debug 测试包跳过登录；release 包恢复
    if (!skipLoginAndCompliance && !isTapLoggedIn) {
        TapTapLoginScreen(
            onLoginSuccess = { account ->
                isTapLoggedIn = true
                // 不在此处直接进入游戏，需要等防沉迷回调 LOGIN_SUCCESS
            }
        )
        return
    }

    // 第三层门控：防沉迷（必须通过，不可跳过）
    // debug 测试包跳过防沉迷；release 包恢复
    if (!skipLoginAndCompliance && !compliancePassed) {
        // 防沉迷限制提示弹窗（宵禁/时长用完/年龄限制/实名未完成）
        if (complianceBlocked && complianceMessage.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("防沉迷提示") },
                text = { Text(complianceMessage) },
                confirmButton = {
                    Button(onClick = {
                        // 点击确定后重置状态，返回登录页重新走流程
                        complianceBlocked = false
                        complianceMessage = ""
                        compliancePassed = false
                        isTapLoggedIn = false
                    }) {
                        Text("确定")
                    }
                }
            )
        }
        // 防沉迷未通过（等待SDK回调中或被限制），阻止进入游戏
        return
    }

    // === 三层门控全部通过，以下为游戏内容 ===

    if (showSettings) {
        SettingsScreen(
            onBack = { showSettings = false }
        )
        return
    }

    if (!isGameRunning) {
        // Start menu BGM when showing menu
        LaunchedEffect(Unit) {
            menuViewModel.startMenuBgm()
        }

        // Show main menu when game is not actively running
        MainMenuScreen(
            hasSaveData = hasSaveData,
            onNewGame = { name, principalName, style ->
                menuViewModel.playClickSound()
                menuViewModel.stopMenuBgm()
                viewModel.newGame(name, principalName, style.key)
                showTutorial = true
            },
            onContinueGame = {
                menuViewModel.playClickSound()
                menuViewModel.stopMenuBgm()
                viewModel.continueGame()
            },
            onOpenSettings = {
                menuViewModel.playClickSound()
                showSettings = true
            }
        )
        return
    }

    // 拦截系统返回手势：子页面返回总览，主页面弹出退出确认
    BackHandler(enabled = school != null) {
        if (selectedTab > 4) {
            selectedTab = 0
        } else {
            showExitDialog = true
        }
    }

    // 退出确认对话框
    if (showExitDialog) {
        val activity = LocalContext.current as? android.app.Activity
        com.arktools.xiaozhang.ui.components.PixelAlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = "退出游戏",
            text = "确定要退出游戏吗？游戏进度会自动保存。",
            confirmText = "退出",
            dismissText = "继续游戏",
            onConfirm = {
                showExitDialog = false
                activity?.finish()
            },
            onDismiss = { showExitDialog = false },
            confirmStyle = com.arktools.xiaozhang.ui.components.PixelButtonStyle.DANGER,
            dismissStyle = com.arktools.xiaozhang.ui.components.PixelButtonStyle.SECONDARY
        )
    }

    // 加速看广告弹窗
    if (showSpeedAdDialog) {
        val activity = context as? android.app.Activity
        com.arktools.xiaozhang.ui.components.PixelAlertDialog(
            onDismissRequest = { viewModel.dismissSpeedAdDialog() },
            title = "解锁加速",
            text = "观看一段短视频即可解锁2x~5x加速，持续20分钟。",
            confirmText = "观看视频",
            dismissText = "取消",
            onConfirm = {
                if (activity != null) {
                    viewModel.pauseForAd()
                    AdHelper.showRewardAd(
                        activity = activity,
                        onRewarded = { viewModel.onSpeedAdRewarded() },
                        onFailed = {
                            isAdLoading = false
                            viewModel.dismissSpeedAdDialog()
                        },
                        onLoadStart = { isAdLoading = true },
                        onComplete = {
                            isAdLoading = false
                            viewModel.resumeAfterAd()
                        }
                    )
                }
            },
            onDismiss = { viewModel.dismissSpeedAdDialog() }
        )
    }

    // 双倍收益广告弹窗（月结算后）
    if (showDoubleIncomeAd) {
        val activity = context as? android.app.Activity
        val bonusText = String.format("%.1f", pendingBonusAmount)
        com.arktools.xiaozhang.ui.components.PixelAlertDialog(
            onDismissRequest = { viewModel.dismissDoubleIncomeAd() },
            title = "双倍收益",
            text = "本月净收入 ${bonusText}万元！\n观看一段短视频可额外获得 ${bonusText}万元奖励。",
            confirmText = "观看视频领取",
            dismissText = "跳过",
            onConfirm = {
                if (activity != null) {
                    viewModel.pauseForAd()
                    AdHelper.showRewardAd(
                        activity = activity,
                        onRewarded = { viewModel.onDoubleIncomeRewarded() },
                        onFailed = {
                            isAdLoading = false
                            viewModel.dismissDoubleIncomeAd()
                        },
                        onLoadStart = { isAdLoading = true },
                        onComplete = {
                            isAdLoading = false
                            viewModel.resumeAfterAd()
                        }
                    )
                }
            },
            onDismiss = { viewModel.dismissDoubleIncomeAd() }
        )
    }

    // 读档成功后 MainViewModel 会自动进入游戏；这里不再弹窗阻塞玩家操作。

    PixelGameBackground {
    Scaffold(
        containerColor = Color(0xCC0B2038),
        topBar = {
            TopAppBar(
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xCC0B2038)
                ),
                navigationIcon = {
                    if (selectedTab > 4) {
                        IconButton(onClick = { selectedTab = 0 }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    }
                },
                title = {
                    Column {
                        Text(
                            text = if (selectedTab > 4) getSubPageTitle(selectedTab) else (school?.name ?: "大学时代"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (selectedTab <= 4) {
                            Text(
                                text = "${school?.currentYear ?: "--"}年 ${school?.currentMonth ?: "-"}月 ${school?.currentDay ?: "-"}日",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { selectedTab = 14 }) {
                        BadgedBox(
                            badge = {
                                if (unreadCount > 0) {
                                    Badge {
                                        Text(
                                            text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = "通知")
                        }
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                    // 速度选择器
                    SpeedSelector(
                        currentSpeed = gameSpeed,
                        onSpeedChange = { viewModel.requestSpeedChange(it) },
                        isBoostActive = viewModel.speedBoostManager.isBoostActive()
                    )
                    IconButton(onClick = {
                        if (!isPaused) viewModel.pauseGame() else viewModel.resumeGame()
                    }) {
                        Icon(
                            imageVector = if (!isPaused) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (!isPaused) "暂停" else "继续"
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xE60B2038)) {
                NavigationBarItem(
                    icon = { Image(painter = painterResource(id = R.drawable.nav_campus_v2), contentDescription = "校园", modifier = Modifier.size(28.dp)) },
                    label = { Text("校园") },
                    selected = selectedTab == 0,
                    onClick = { audioManager.playButtonClick(); selectedTab = 0; tutorialManager.notifyTabChanged(0) }
                )
                NavigationBarItem(
                    icon = { Image(painter = painterResource(id = R.drawable.nav_academic_v2), contentDescription = "治院", modifier = Modifier.size(28.dp)) },
                    label = { Text("治院") },
                    selected = selectedTab == 1,
                    onClick = { audioManager.playButtonClick(); selectedTab = 1; tutorialManager.notifyTabChanged(1) }
                )
                NavigationBarItem(
                    icon = { Image(painter = painterResource(id = R.drawable.nav_teacher_v2), contentDescription = "人事", modifier = Modifier.size(28.dp)) },
                    label = { Text("人事") },
                    selected = selectedTab == 2,
                    onClick = { audioManager.playButtonClick(); selectedTab = 2; tutorialManager.notifyTabChanged(2) }
                )
                NavigationBarItem(
                    icon = { Image(painter = painterResource(id = R.drawable.nav_research_v2), contentDescription = "外联", modifier = Modifier.size(28.dp)) },
                    label = { Text("外联") },
                    selected = selectedTab == 3,
                    onClick = { audioManager.playButtonClick(); selectedTab = 3; tutorialManager.notifyTabChanged(3) }
                )
                }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues)
        ) {
            school?.let { SchoolStatusBar(school = it, onCampusClick = { selectedTab = 4 }) }

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(
                        animationSpec = tween(AnimationConstants.defaultDuration),
                        initialOffsetX = { it * direction })
                    ) togetherWith slideOutHorizontally(
                        animationSpec = tween(AnimationConstants.defaultDuration),
                        targetOffsetX = { -it * direction })
                },
                label = "tabContent"
            ) { tab ->
                when (tab) {
                    0 -> CampusView(onNavigateTo = { selectedTab = it })
                    1 -> GovernanceScreen(onNavigateTo = { selectedTab = it })
                    2 -> HiringScreen()
                    3 -> ExternalScreen()
                    4 -> DistrictScreen()
                    else -> {
                        val lockedModule = GameBalanceConfig.moduleForTab(tab)
                        val campusLevel = school?.campusLevel ?: 1
                        if (lockedModule != null &&
                            !GameBalanceConfig.isModuleUnlocked(lockedModule, campusLevel)
                        ) {
                            FeatureLockedScreen(
                                module = lockedModule,
                                campusLevel = campusLevel
                            )
                        } else {
                            when (tab) {
                                5 -> RankingScreen()
                                6 -> StockScreen()
                                7 -> FacilityScreen()
                                8 -> StudentScreen()
                                10 -> AchievementScreen()
                                11 -> ReportScreen()
                                12 -> MarketingScreen()
                                13 -> EventScreen()
                                14 -> NotificationScreen(
                                    onNavigateToTab = { tabIndex -> selectedTab = tabIndex }
                                )
                                15 -> AlumniScreen()
                                16 -> PolicyScreen()
                                17 -> ClubScreen()
                                18 -> SeasonalScreen()
                                20 -> ReputationScreen()
                                21 -> StudentLifeScreen()
                                23 -> ConferenceScreen()
                                27 -> ParentScreen()
                                28 -> GovernmentScreen()
                                29 -> ScholarshipScreen()
                                31 -> TimetableScreen()
                                32 -> ExamScreen()
                                33 -> PrincipalOfficeScreen()
                    40 -> TeachingScreen()
                    41 -> ResearchScreen()
                    42 -> DistrictScreen()
                    43 -> PolicyScreen()
                    44 -> ReportScreen()
                                else -> OverviewScreen(listState = overviewListState)
                            }
                        }
                    }
                }
            }
        }
    }
    } // PixelGameBackground

    EventDialogContainer()

    // 迷你游戏覆盖层（运动会、辩论赛等可玩小游戏）
    MiniGameContainer()

    // Achievement unlock toast notification
    AchievementToastOverlay(achievementManager = viewModel.achievementManager)

    // 广告奖励到账通知
    rewardNotification?.let { message ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 80.dp)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            androidx.compose.material3.Card(
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = Color(0xFF2E7D32)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = message,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showConfetti) {
        ConfettiEffect(
            originX = 200f,
            originY = 400f,
            onFinished = { showConfetti = false }
        )
    }

    if (showTutorial && tutorialManager.isActive) {
        TutorialOverlay(
            tutorialManager = tutorialManager,
            onDismiss = {
                showTutorial = false
                // 教程被跳过/关闭时：赠送初始教师 + 触发招生 + 恢复游戏
                viewModel.grantSkipTutorialRewards()
                viewModel.setEventsSuppressed(false)
                viewModel.resumeGame()
            }
        )
    }

    if (disciplinaryPause != null) {
        val activity = context as? android.app.Activity
        AlertDialog(
            onDismissRequest = { },
            title = { Text(disciplinaryPause!!.title) },
            text = { Text(disciplinaryPause!!.message) },
            confirmButton = {
                Button(
                    onClick = {
                        if (activity != null) {
                            viewModel.pauseForAd()
                            AdHelper.showRewardAd(
                                activity = activity,
                                onRewarded = { viewModel.recoverFromDisciplinaryPause() },
                                onFailed = { isAdLoading = false },
                                onLoadStart = { isAdLoading = true },
                                onComplete = { isAdLoading = false }
                            )
                        }
                    },
                    enabled = activity != null && !isAdLoading
                ) {
                    Text(if (isAdLoading) "广告加载中" else "观看视频恢复经营")
                }
            }
        )
    }

    // 紧急救助弹窗（CRITICAL状态时显示）
    val capturedHealthReport = healthReport
    if (crisisState == CrisisState.CRITICAL && capturedHealthReport != null) {
        val activity = context as? android.app.Activity
        CrisisDialog(
            healthReport = capturedHealthReport,
            conditions = activeConditions,
            onAcceptBailout = {
                if (activity != null) {
                    viewModel.pauseForAd()
                    AdHelper.showRewardAd(
                        activity = activity,
                        onRewarded = { gameOverViewModel.acceptBailout() },
                        onFailed = {
                            isAdLoading = false
                            /* 广告失败不执行救助 */
                        },
                        onLoadStart = { isAdLoading = true },
                        onComplete = {
                            isAdLoading = false
                            viewModel.resumeAfterAd()
                        }
                    )
                }
            },
            onDeclineBailout = { gameOverViewModel.declineBailout() }
        )
    }

    // GameOver 终结屏幕
    val capturedGameOverReason = gameOverReason
    if (crisisState == CrisisState.GAME_OVER && capturedGameOverReason != null) {
        GameOverScreen(
            reason = capturedGameOverReason,
            onNewGame = { viewModel.resetGame() }
        )
    }

    AdLoadingOverlay(visible = isAdLoading)
}

@Composable
private fun SchoolStatusBar(school: com.arktools.xiaozhang.domain.model.School, onCampusClick: () -> Unit = {}) {
    val animatedCash by animateFloatAsState(
        targetValue = school.cash.toFloat(),
        animationSpec = tween(AnimationConstants.slowDuration),
        label = "cashAnim"
    )
    val animatedReputation by animateFloatAsState(
        targetValue = school.reputation.toFloat(),
        animationSpec = tween(AnimationConstants.slowDuration),
        label = "repAnim"
    )
    val animatedRevenue by animateFloatAsState(
        targetValue = school.totalRevenue.toFloat(),
        animationSpec = tween(AnimationConstants.slowDuration),
        label = "revAnim"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(64.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.bar_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatusItem(
                label = "资金",
                value = FormatUtils.formatCash(animatedCash.toDouble()),
                color = if (animatedCash > 0) AccentGreen else AccentRed
            )
            StatusItem(
                label = "声誉",
                value = FormatUtils.formatReputation(animatedReputation.toLong()),
                color = AccentOrange
            )
            StatusItem(
                label = "总收入",
                value = FormatUtils.formatCash(animatedRevenue.toDouble()),
                color = Color(0xFF4E342E)
            )
            StatusItem(
                label = "校园",
                value = "Lv.${school.campusLevel}",
                color = Color(0xFF4E342E),
                onClick = onCampusClick
            )
            StatusItem(
                label = "星级",
                value = String.format("%.1f", school.starRating),
                color = AccentOrange
            )
        }
    }
}

@Composable
private fun StatusItem(label: String, value: String, color: Color, onClick: (() -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (onClick != null) {
            Modifier.clickable(onClick = onClick)
        } else {
            Modifier
        }
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else Color(0xFF5D4037)
        )
    }
}

/**
 * 游戏速度选择器 - 点击循环切换速度
 */
@Composable
private fun SpeedSelector(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    isBoostActive: Boolean = false
) {
    val speedOptions = listOf(1f, 2f, 3f, 5f)
    val currentIndex = speedOptions.indexOfFirst { kotlin.math.abs(it - currentSpeed) < 0.01f }
        .coerceAtLeast(0)

    val displayText = when {
        currentSpeed < 1f -> "×1"
        currentSpeed == currentSpeed.toInt().toFloat() -> "×${currentSpeed.toInt()}"
        else -> "×${String.format("%.1f", currentSpeed)}"
    }

    val speedColor by androidx.compose.animation.animateColorAsState(
        targetValue = when {
            currentSpeed <= 1f -> MaterialTheme.colorScheme.onSurface
            currentSpeed <= 2f -> MaterialTheme.colorScheme.primary
            currentSpeed <= 3f -> AccentOrange
            else -> AccentRed
        },
        animationSpec = tween(300),
        label = "speedColor"
    )

    androidx.compose.material3.TextButton(
        onClick = {
            val nextIndex = (currentIndex + 1) % speedOptions.size
            onSpeedChange(speedOptions[nextIndex])
        },
        modifier = Modifier.width(56.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 4.dp)
    ) {
        Text(
            text = if (currentSpeed > 1f && !isBoostActive) "×1🔒" else displayText,
            style = MaterialTheme.typography.labelMedium,
            color = speedColor,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
    }
}

/**
 * 获取子页面标题
 */
private fun getSubPageTitle(tab: Int): String {
    return when (tab) {
        5 -> "排行榜"
        6 -> "股票投资"
        7 -> "校园建设"
        8 -> "学生事务"
        10 -> "大学荣誉"
        11 -> "办学报表"
        12 -> "招生传播"
        13 -> "校园事件"
        14 -> "校务通知"
        15 -> "校友与就业"
        16 -> "大学政策"
        17 -> "学生社团"
        18 -> "校园活动"
        20 -> "社会声誉"
        21 -> "校园生活"
        23 -> "学术会议"
        27 -> "校友与家委会"
        28 -> "政府与行业"
        29 -> "奖助学金"
        31 -> "专业课表"
        32 -> "教学评估"
        33 -> "校长办公室"
        40 -> "教学配置"
        41 -> "科研研究"
        42 -> "社会合作"
        43 -> "学校政策"
        44 -> "办学报表"
        else -> "校长我来当 2：大学时代"
    }
}


