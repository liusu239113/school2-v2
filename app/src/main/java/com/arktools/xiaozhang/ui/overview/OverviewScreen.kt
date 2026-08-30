package com.arktools.xiaozhang.ui.overview

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.arktools.xiaozhang.ui.components.PixelNineSlice
import com.arktools.xiaozhang.R
import com.arktools.xiaozhang.domain.engine.GameBalanceConfig
import com.arktools.xiaozhang.domain.engine.GameModule
import com.arktools.xiaozhang.domain.engine.HealthReport
import com.arktools.xiaozhang.domain.engine.HealthStatus
import com.arktools.xiaozhang.ui.theme.AccentGreen
import com.arktools.xiaozhang.ui.theme.AccentOrange
import com.arktools.xiaozhang.ui.theme.AccentRed
import com.arktools.xiaozhang.ui.utils.FormatUtils
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Inbox

@Composable
fun OverviewScreen(
    onNavigateToRanking: () -> Unit = {},
    onNavigateToStock: () -> Unit = {},
    onNavigateToFacility: () -> Unit = {},
    onNavigateToStudent: () -> Unit = {},
    onNavigateToAchievement: () -> Unit = {},
    onNavigateToReport: () -> Unit = {},
    onNavigateToMarketing: () -> Unit = {},
    onNavigateToEvent: () -> Unit = {},
    onNavigateToNotification: () -> Unit = {},
    onNavigateToAlumni: () -> Unit = {},
    onNavigateToPolicy: () -> Unit = {},
    onNavigateToTeacher: () -> Unit = {},
    onNavigateToResearch: () -> Unit = {},
    onNavigateToClub: () -> Unit = {},
    onNavigateToSeasonal: () -> Unit = {},
    onNavigateToReputation: () -> Unit = {},
    onNavigateToStudentLife: () -> Unit = {},
    onNavigateToConference: () -> Unit = {},
    onNavigateToParent: () -> Unit = {},
    onNavigateToGovernment: () -> Unit = {},
    onNavigateToScholarship: () -> Unit = {},
    onNavigateToTimetable: () -> Unit = {},
    onNavigateToExam: () -> Unit = {},
    onNavigateToPrincipalOffice: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    viewModel: OverviewViewModel = hiltViewModel()
) {
    val stats by viewModel.schoolStats.collectAsState()
    val tips by viewModel.currentTips.collectAsState()
    val unreadNotifications by viewModel.unreadNotificationCount.collectAsState()
    val pendingSuggestions by viewModel.pendingSuggestionCount.collectAsState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            UniversityHeroCard()
        }

        item {
            PrincipalAgendaCard(
                stats = stats,
                onFinanceClick = onNavigateToPolicy,
                onTalentClick = onNavigateToTeacher,
                onResearchClick = onNavigateToResearch,
                onSocietyClick = onNavigateToReputation
            )
        }

        // 校际学科竞赛：报名费进、奖金与声誉出，胜率看学院师资覆盖
        item {
            CompetitionCard(
                uiState = viewModel.competitionUi.collectAsState().value,
                onRegister = { track, tier -> viewModel.registerCompetition(track, tier) },
                onConsumeMessage = { viewModel.consumeCompetitionMessage() }
            )
        }

        // 季节指示 + 学校健康度
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 季节徽章
                if (stats.currentSeason.isNotEmpty()) {
                    SeasonChip(
                        seasonName = stats.currentSeason,
                        seasonEmoji = stats.seasonEmoji
                    )
                }
                // 健康状态摘要
                stats.healthReport?.let { report ->
                    HealthBadge(report = report)
                }
            }
        }

        // 健康度详情面板
        stats.healthReport?.let { report ->
            if (report.cashStatus != HealthStatus.GOOD ||
                report.reputationStatus != HealthStatus.GOOD ||
                report.teacherStatus != HealthStatus.GOOD ||
                report.studentStatus != HealthStatus.GOOD
            ) {
                item {
                    HealthDetailCard(report = report)
                }
            }
        }

        // 待处理提醒
        if (unreadNotifications > 0 || pendingSuggestions > 0) {
            item {
                PendingReminderCard(
                    unreadNotifications = unreadNotifications,
                    pendingSuggestions = pendingSuggestions,
                    onNotificationClick = onNavigateToNotification,
                    onSuggestionClick = onNavigateToPrincipalOffice
                )
            }
        }

        // 学生概况
        item {
            SectionHeader(title = "本校学生概况", subtitle = "本科生、研究生与校园生活状态")
            Spacer(modifier = Modifier.height(8.dp))
            StudentOverviewCard(
                totalStudents = stats.totalStudents,
                monthlyNewStudents = stats.monthlyNewStudents,
                averageSatisfaction = stats.averageSatisfaction,
                activeCourses = stats.activeCourses,
                onClick = onNavigateToStudent
            )
        }

        // 经营数据
        item {
            SectionHeader(title = "办学财务", subtitle = "学费进账后会被薪资、校园租金和生均办学成本重新抽走")
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.TrendingUp,
                    label = "月收入",
                    value = FormatUtils.formatCash(stats.monthlyRevenue),
                    color = AccentGreen
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.AccountBalance,
                    label = "月支出",
                    value = FormatUtils.formatCash(stats.monthlyExpenses),
                    color = AccentRed
                )
            }
        }

        // 盈亏状态提示
        if (stats.monthlyRevenue > 0 || stats.monthlyExpenses > 0) {
            item {
                val profit = stats.monthlyRevenue - stats.monthlyExpenses
                val isProfit = profit >= 0
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isProfit)
                            AccentGreen.copy(alpha = 0.1f)
                        else
                            AccentRed.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isProfit) "本月盈利" else "本月亏损",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isProfit) AccentGreen else AccentRed
                        )
                        Text(
                            text = (if (isProfit) "+" else "") + FormatUtils.formatCash(profit),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isProfit) AccentGreen else AccentRed
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.MenuBook,
                    label = "开课中",
                    value = "${stats.activeCourses} 门",
                    color = MaterialTheme.colorScheme.primary
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Groups,
                    label = "教师",
                    value = "${stats.teacherCount}/${stats.maxTeachers}",
                    color = AccentOrange
                )
            }
        }

        // 教师团队概览
        if (stats.teacherCount > 0) {
            item {
                SectionHeader(title = "教师与研究团队", subtitle = "师资能力、研究投入与工作状态")
                Spacer(modifier = Modifier.height(8.dp))
                TeacherTeamCard(
                    teacherCount = stats.teacherCount,
                    avgSkill = stats.teacherAvgSkill,
                    sCount = stats.teacherSCount,
                    aCount = stats.teacherACount,
                    bCount = stats.teacherBCount,
                    cCount = stats.teacherCCount,
                    avgFatigue = stats.avgFatigue,
                    avgLoyalty = stats.avgLoyalty,
                    facultyCoverageRatio = stats.facultyCoverageRatio,
                    facultyCoverageSummary = stats.facultyCoverageSummary
                )
            }
        }

        // 教学质量概览
        if (stats.activeCourses > 0 || stats.coursePreparingCount > 0) {
            item {
                SectionHeader(title = "人才培养质量", subtitle = "课程、专业和毕业成果的综合表现")
                Spacer(modifier = Modifier.height(8.dp))
                CourseQualityCard(
                    activeCourses = stats.activeCourses,
                    preparingCount = stats.coursePreparingCount,
                    avgQuality = stats.avgCourseQuality,
                    topQuality = stats.topCourseQuality,
                    totalRevenue = stats.totalCourseRevenue
                )
            }
        }

        // 学校成长
        item {
            SectionHeader(title = "大学成长", subtitle = "提升学术声誉、人才质量与社会影响力")
            Spacer(modifier = Modifier.height(8.dp))
            GrowthCard(
                reputation = stats.reputation,
                starRating = stats.starRating,
                campusLevel = stats.campusLevel,
                researchUnlocked = stats.researchUnlocked,
                totalResearch = stats.totalResearch
            )
        }

        item {
            val nextUnlocks = GameBalanceConfig.getNextStageUnlocks(stats.campusLevel)
            val nextLevel = (stats.campusLevel + 1).coerceAtMost(GameBalanceConfig.MAX_SCHOOL_LEVEL)
            SectionHeader(
                title = "办学阶段",
                subtitle = if (nextUnlocks.isEmpty()) {
                    "已进入最高办学阶段，继续追求世界一流大学"
                } else {
                    "下一阶段 ${GameBalanceConfig.getSchoolLevelName(nextLevel)} 将开放：${nextUnlocks.joinToString("、") { it.displayName }}"
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "前期先把师资、教室和招生做稳；中后期再打开社团、奖学金、校友、学术会议和产业合作。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 快捷入口 - 排行 & 股市 & 设施 & 里程碑
        item {
            SectionHeader(title = "更多功能", subtitle = "按校园等级逐步开放，锁定条目会标明解锁条件")
            Spacer(modifier = Modifier.height(8.dp))
            val entries: List<Pair<Triple<GameModule?, Int, () -> Unit>, String>> = listOf(
                Triple(GameModule.FACILITY, R.drawable.ic_construction, onNavigateToFacility) to "建设升级设施",
                Triple(GameModule.RANKING, R.drawable.ic_trophy, onNavigateToRanking) to "查看学校排名",
                Triple(null, R.drawable.ic_medal_gold, onNavigateToAchievement) to "成就收集与里程碑",
                Triple(GameModule.STOCK, R.drawable.ic_chart, onNavigateToStock) to "用闲钱赚更多",
                Triple(GameModule.REPORT, R.drawable.ic_clipboard, onNavigateToReport) to "经营趋势与财务分析",
                Triple(GameModule.MARKETING, R.drawable.ic_megaphone, onNavigateToMarketing) to "招生营销活动",
                Triple(GameModule.EVENT, R.drawable.ic_calendar, onNavigateToEvent) to "查看历史事件",
                Triple(GameModule.ALUMNI, R.drawable.ic_graduation, onNavigateToAlumni) to "毕业生去向与校友网络",
                Triple(GameModule.POLICY, R.drawable.ic_memo, onNavigateToPolicy) to "调整运营参数",
                Triple(GameModule.CLUB, R.drawable.ic_people, onNavigateToClub) to "社团管理与竞赛",
                Triple(GameModule.SEASONAL, R.drawable.ic_celebration, onNavigateToSeasonal) to "校园活动日历",
                Triple(GameModule.REPUTATION, R.drawable.ic_crown, onNavigateToReputation) to "多维声誉分析",
                Triple(GameModule.STUDENT_LIFE, R.drawable.ic_house, onNavigateToStudentLife) to "宿舍食堂健康管理",
                Triple(GameModule.CONFERENCE, R.drawable.ic_books, onNavigateToConference) to "举办参加学术交流",
                Triple(GameModule.PARENT, R.drawable.ic_heart, onNavigateToParent) to "维护校友、家庭与社会支持网络",
                Triple(GameModule.GOVERNMENT, R.drawable.ic_government, onNavigateToGovernment) to "对接企业、城市与公共项目",
                Triple(GameModule.SCHOLARSHIP, R.drawable.ic_gem, onNavigateToScholarship) to "支持人才培养与公平入学",
                Triple(GameModule.TIMETABLE, R.drawable.ic_calendar, onNavigateToTimetable) to "统筹学院、专业与教学资源",
                Triple(GameModule.EXAM, R.drawable.ic_clipboard, onNavigateToExam) to "跟踪培养质量与毕业成果",
                Triple(GameModule.PRINCIPAL, R.drawable.ic_office, onNavigateToPrincipalOffice) to "个人事务灰色地带"
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                entries.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { (entry, fallbackDesc) ->
                            val (module, iconRes, onClick) = entry
                            val unlocked = module == null || GameBalanceConfig.isModuleUnlocked(module, stats.campusLevel)
                            QuickEntryCard(
                                modifier = Modifier.weight(1f),
                                iconRes = iconRes,
                                title = if (unlocked) (module?.displayName ?: "荣誉成就") else "${module?.displayName} 🔒",
                                description = if (unlocked) {
                                    fallbackDesc
                                } else {
                                    module?.let { GameBalanceConfig.getModuleLockReason(it) } ?: fallbackDesc
                                },
                                onClick = onClick,
                                locked = !unlocked
                            )
                        }
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // 玩法指引
        item {
            SectionHeader(title = "经营指南")
            Spacer(modifier = Modifier.height(8.dp))
            TipsCard(tips = tips)
        }
    }
}

@Composable
private fun PrincipalAgendaCard(
    stats: OverviewViewModel.SchoolOverviewStats,
    onFinanceClick: () -> Unit,
    onTalentClick: () -> Unit,
    onResearchClick: () -> Unit,
    onSocietyClick: () -> Unit
) {
    val financeProgress = if (stats.monthlyExpenses <= 0.0) 1f
    else (stats.monthlyRevenue / stats.monthlyExpenses).toFloat().coerceIn(0f, 1f)
    val talentProgress = (stats.avgLoyalty / 100f).coerceIn(0f, 1f)
    val researchProgress = if (stats.totalResearch <= 0) 0f
    else (stats.researchUnlocked.toFloat() / stats.totalResearch).coerceIn(0f, 1f)
    val societyProgress = (stats.reputation / 5000f).toFloat().coerceIn(0f, 1f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF172B3A)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("校长年度议程", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("${stats.currentYear}年${stats.currentMonth}月 · 找到最薄弱的一环，决定下一步投入", color = Color(0xFFB7C9D6), style = MaterialTheme.typography.bodySmall)
                    val collegeText = if (stats.foundedCollegeNames.isEmpty()) {
                        "尚未成立学院"
                    } else {
                        stats.foundedCollegeNames.joinToString("、")
                    }
                    val strengthText = if (stats.strongestCollegeName.isNotEmpty() && stats.strongestCollegeName != "未分院") {
                        " · 学科强项：${stats.strongestCollegeName}（${stats.strongestCollegeCount}人）"
                    } else {
                        ""
                    }
                    Text(
                        "目标「${stats.annualGoalName}」· $collegeText$strengthText · 师资覆盖 ${(stats.facultyCoverageRatio * 100).toInt()}% · 专项每月约 ${"%.1f".format(stats.collegeMonthlyCost)}万",
                        color = Color(0xFF8FA3B3),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Text("${stats.currentSeason}", color = Color(0xFFD4B06A), fontWeight = FontWeight.Bold)
            }
            AgendaLine("财务安全", if (financeProgress >= 1f) "现金流稳定" else "需要控制支出", financeProgress, onFinanceClick, Color(0xFF81C784))
            AgendaLine(
                "师资状态",
                if (stats.facultyCoverageRatio < 1f) stats.facultyCoverageSummary
                else if (talentProgress >= 0.7f) "核心师资已配齐"
                else "关注疲劳与流失",
                stats.facultyCoverageRatio.coerceIn(0f, 1f),
                onTalentClick,
                Color(0xFF64B5F6)
            )
            AgendaLine("科研积累", "${stats.researchUnlocked}/${stats.totalResearch} 个项目", researchProgress, onResearchClick, Color(0xFFBA68C8))
            AgendaLine("社会影响", "声誉 ${FormatUtils.formatReputation(stats.reputation)}", societyProgress, onSocietyClick, Color(0xFFFFB74D))
        }
    }
}

@Composable
private fun AgendaLine(
    title: String,
    detail: String,
    progress: Float,
    onClick: () -> Unit,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.width(76.dp)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Color(0xFFB7C9D6), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.weight(1f).height(7.dp).clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = Color.White.copy(alpha = 0.12f),
            strokeCap = StrokeCap.Round
        )
    }
}

@Composable
private fun UniversityHeroCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(158.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.bld_classroom),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x9911263D))
                .padding(18.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "大学治理驾驶舱",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "学术 · 人才 · 社会影响力",
                    color = Color(0xFFD4B06A),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String? = null) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StudentOverviewCard(
    totalStudents: Int,
    monthlyNewStudents: Int,
    averageSatisfaction: Float,
    activeCourses: Int = 0,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        PixelNineSlice(
                    res = R.drawable.card_bg,
                    slice = 48,
                    modifier = Modifier.matchParentSize()
                )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.People,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "$totalStudents 名学生",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "正在本校就读",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StudentStat(
                    label = "本月新生",
                    value = if (monthlyNewStudents > 0) "+$monthlyNewStudents" else "$monthlyNewStudents",
                    color = if (monthlyNewStudents > 0) AccentGreen else AccentRed
                )
                StudentStat(
                    label = "平均满意度",
                    value = "${String.format("%.0f", averageSatisfaction)}%",
                    color = when {
                        averageSatisfaction >= 80f -> AccentGreen
                        averageSatisfaction >= 60f -> AccentOrange
                        else -> AccentRed
                    }
                )
                StudentStat(
                    label = "开课中",
                    value = "${activeCourses}门",
                    color = if (activeCourses > 0) AccentGreen else AccentRed
                )
            }
        }
    }
}

@Composable
private fun StudentStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Box(modifier = modifier.height(72.dp)) {
        PixelNineSlice(
                    res = R.drawable.card_bg,
                    slice = 48,
                    modifier = Modifier.matchParentSize()
                )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun GrowthCard(
    reputation: Long,
    starRating: Float,
    campusLevel: Int,
    researchUnlocked: Int,
    totalResearch: Int
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        PixelNineSlice(
                    res = R.drawable.card_bg,
                    slice = 48,
                    modifier = Modifier.matchParentSize()
                )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                GrowthStat(Icons.Default.Star, "声誉", FormatUtils.formatReputation(reputation), AccentOrange)
                GrowthStat(Icons.Default.Star, "星级", "${String.format("%.1f", starRating)}星", AccentOrange)
                GrowthStat(Icons.Default.School, "校园", "Lv.$campusLevel", MaterialTheme.colorScheme.primary)
                GrowthStat(Icons.Default.AutoGraph, "科研", "$researchUnlocked/$totalResearch", AccentGreen)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "学术声誉越高，越容易吸引优质生源、师资和产业合作，形成大学成长的正循环",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GrowthStat(icon: ImageVector, label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QuickEntryCard(
    modifier: Modifier = Modifier,
    iconRes: Int,
    title: String,
    description: String,
    onClick: () -> Unit,
    locked: Boolean = false
) {
    Box(
        modifier = modifier
            .height(110.dp)
            .clickable { onClick() }
    ) {
        PixelNineSlice(
                    res = R.drawable.card_bg,
                    slice = 48,
                    modifier = Modifier.matchParentSize()
                )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier
                    .size(36.dp)
                    .then(if (locked) Modifier.alpha(0.35f) else Modifier)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                color = if (locked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TipsCard(tips: List<String>) {
    Box(modifier = Modifier.fillMaxWidth()) {
        PixelNineSlice(
                    res = R.drawable.card_bg,
                    slice = 48,
                    modifier = Modifier.matchParentSize()
                )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            tips.forEach { tip ->
                Text(
                    text = tip,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

// ===== 教师团队概览卡片 =====

@Composable
private fun TeacherTeamCard(
    teacherCount: Int,
    avgSkill: Float,
    sCount: Int,
    aCount: Int,
    bCount: Int,
    cCount: Int,
    avgFatigue: Float,
    avgLoyalty: Float,
    facultyCoverageRatio: Float = 1f,
    facultyCoverageSummary: String = "各学院核心师资已配齐"
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        PixelNineSlice(
                    res = R.drawable.card_bg,
                    slice = 48,
                    modifier = Modifier.matchParentSize()
                )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Level distribution row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LevelBadge(label = "S", count = sCount, color = Color(0xFFFFD700))
                LevelBadge(label = "A", count = aCount, color = Color(0xFF4CAF50))
                LevelBadge(label = "B", count = bCount, color = Color(0xFF2196F3))
                LevelBadge(label = "C", count = cCount, color = Color(0xFF9E9E9E))
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "学院师资覆盖 ${(facultyCoverageRatio * 100).toInt()}% · $facultyCoverageSummary",
                style = MaterialTheme.typography.bodySmall,
                color = if (facultyCoverageRatio < 1f) AccentOrange else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Avg skill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "平均技能",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = String.format("%.0f", avgSkill),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            LinearProgressIndicator(
                progress = { avgSkill / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .padding(vertical = 2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Fatigue and loyalty
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("疲劳", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${String.format("%.0f", avgFatigue)}%", style = MaterialTheme.typography.labelSmall, color = if (avgFatigue > 60f) AccentRed else AccentOrange)
                    }
                    LinearProgressIndicator(
                        progress = { avgFatigue / 100f },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = if (avgFatigue > 60f) AccentRed else AccentOrange,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("忠诚", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${String.format("%.0f", avgLoyalty)}%", style = MaterialTheme.typography.labelSmall, color = if (avgLoyalty >= 70f) AccentGreen else AccentOrange)
                    }
                    LinearProgressIndicator(
                        progress = { avgLoyalty / 100f },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = if (avgLoyalty >= 70f) AccentGreen else AccentOrange,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
private fun LevelBadge(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(color.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${count}人",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ===== 教学质量概览卡片 =====

@Composable
private fun CourseQualityCard(
    activeCourses: Int,
    preparingCount: Int,
    avgQuality: Float,
    topQuality: Float,
    totalRevenue: Double
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        PixelNineSlice(
                    res = R.drawable.card_bg,
                    slice = 48,
                    modifier = Modifier.matchParentSize()
                )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CourseQualityStat(
                    icon = Icons.Default.MenuBook,
                    label = "开课",
                    value = "${activeCourses}门",
                    color = AccentGreen
                )
                CourseQualityStat(
                    icon = Icons.Default.Star,
                    label = "均分",
                    value = if (avgQuality > 0) String.format("%.1f", avgQuality) else "-",
                    color = when {
                        avgQuality >= 8f -> Color(0xFFFFD700)
                        avgQuality >= 6f -> AccentOrange
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                CourseQualityStat(
                    icon = Icons.Default.LocalFireDepartment,
                    label = "最高",
                    value = if (topQuality > 0) String.format("%.1f", topQuality) else "-",
                    color = Color(0xFFFFD700)
                )
                CourseQualityStat(
                    icon = Icons.Default.TrendingUp,
                    label = "总收入",
                    value = FormatUtils.formatCash(totalRevenue),
                    color = AccentGreen
                )
            }

            if (preparingCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "另有 ${preparingCount} 个班级筹备中",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentOrange
                )
            }
        }
    }
}

@Composable
private fun CourseQualityStat(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ===== 季节指示器 =====

@Composable
private fun SeasonChip(seasonName: String, seasonEmoji: String) {
    val chipColor = when (seasonEmoji) {
        "spring" -> Color(0xFFFCE4EC) // 春 - 粉色
        "summer" -> Color(0xFFFFF8E1) // 夏 - 暖黄
        "autumn" -> Color(0xFFFBE9E7) // 秋 - 橙色
        else -> Color(0xFFE3F2FD)     // 冬 - 浅蓝
    }
    val textColor = when (seasonEmoji) {
        "spring" -> Color(0xFFAD1457)
        "summer" -> Color(0xFFF57F17)
        "autumn" -> Color(0xFFBF360C)
        else -> Color(0xFF1565C0)
    }

    Box(
        modifier = Modifier
            .background(chipColor, RoundedCornerShape(16.dp))
            .border(1.dp, textColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = "$seasonEmoji $seasonName",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}

// ===== 健康度徽章（摘要） =====

@Composable
private fun HealthBadge(report: HealthReport) {
    val worstStatus = listOf(
        report.cashStatus,
        report.reputationStatus,
        report.teacherStatus,
        report.studentStatus
    ).maxByOrNull { it.ordinal } ?: HealthStatus.GOOD

    val badgeColor by animateColorAsState(
        targetValue = when (worstStatus) {
            HealthStatus.GOOD -> AccentGreen
            HealthStatus.FAIR -> AccentOrange
            HealthStatus.POOR -> AccentRed
            HealthStatus.CRITICAL -> Color(0xFF880E4F)
        },
        label = "healthColor"
    )

    val label = when (worstStatus) {
        HealthStatus.GOOD -> "健康"
        HealthStatus.FAIR -> "注意"
        HealthStatus.POOR -> "危险"
        HealthStatus.CRITICAL -> "危机"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(badgeColor.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Icon(
            imageVector = if (worstStatus == HealthStatus.GOOD) Icons.Default.Favorite else Icons.Default.Warning,
            contentDescription = null,
            tint = badgeColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = badgeColor
        )
    }
}

// ===== 健康度详情卡片 =====

@Composable
private fun HealthDetailCard(report: HealthReport) {
    Box(modifier = Modifier.fillMaxWidth()) {
        PixelNineSlice(
                    res = R.drawable.card_bg,
                    slice = 48,
                    modifier = Modifier.matchParentSize()
                )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "大学运营健康度",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                HealthIndicator(label = "资金", status = report.cashStatus)
                HealthIndicator(label = "声誉", status = report.reputationStatus)
                HealthIndicator(label = "师资", status = report.teacherStatus)
                HealthIndicator(label = "生源", status = report.studentStatus)
            }

            // 显示警告
            if (report.warnings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                report.warnings.take(2).forEach { warning ->
                    Text(
                        text = "$warning",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 2.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 显示建议
            if (report.suggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                report.suggestions.take(1).forEach { suggestion ->
                    Text(
                        text = "$suggestion",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun HealthIndicator(label: String, status: HealthStatus) {
    val color = when (status) {
        HealthStatus.GOOD -> AccentGreen
        HealthStatus.FAIR -> AccentOrange
        HealthStatus.POOR -> AccentRed
        HealthStatus.CRITICAL -> Color(0xFF880E4F)
    }
    val statusText = when (status) {
        HealthStatus.GOOD -> "良好"
        HealthStatus.FAIR -> "一般"
        HealthStatus.POOR -> "较差"
        HealthStatus.CRITICAL -> "危机"
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ===== 待处理提醒卡片 =====

@Composable
private fun PendingReminderCard(
    unreadNotifications: Int,
    pendingSuggestions: Int,
    onNotificationClick: () -> Unit,
    onSuggestionClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = AccentOrange.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = AccentOrange,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "待处理事项",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = AccentOrange
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (unreadNotifications > 0) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNotificationClick() },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = AccentOrange,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "${unreadNotifications}条通知",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "点击查看",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                if (pendingSuggestions > 0) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSuggestionClick() },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inbox,
                                contentDescription = null,
                                tint = AccentRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "${pendingSuggestions}条建议",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "待处理",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===== 校际学科竞赛卡片 =====

@Composable
private fun CompetitionCard(
    uiState: OverviewViewModel.CompetitionUiState,
    onRegister: (com.arktools.xiaozhang.domain.model.AdmissionTrack,
        com.arktools.xiaozhang.domain.competition.UniversityCompetitionManager.CompetitionTier) -> Unit,
    onConsumeMessage: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        PixelNineSlice(
                    res = R.drawable.card_bg,
                    slice = 48,
                    modifier = Modifier.matchParentSize()
                )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.banner_competition_v2),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
            Text(
                text = "校际学科竞赛",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            if (uiState.message != null) {
                Text(
                    text = uiState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentOrange,
                    modifier = Modifier.clickable { onConsumeMessage() }
                )
            }
            if (uiState.catalog.isEmpty()) {
                Text(
                    text = "成立学院后，对应大类的学生才能代表学校参赛",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                uiState.active.forEach { comp ->
                    Text(
                        text = "已报名：${comp.name}（${comp.resolveMonth}月结算）",
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentGreen
                    )
                }
                uiState.catalog.forEach { entry ->
                    val already = uiState.active.any {
                        it.trackName == entry.track.displayName && it.tier == entry.tier.name
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${entry.tier.displayName}·${entry.track.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "报名 ${entry.entryFee.toInt()}万 · 奖金 ${entry.prize.toInt()}万 + ${entry.reputationReward}声誉",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (already) {
                            Text(
                                text = "已报名",
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentGreen
                            )
                        } else {
                            Text(
                                text = "报名",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onRegister(entry.track, entry.tier) }
                            )
                        }
                    }
                }
            }
            if (uiState.lastSummary.isNotEmpty()) {
                Text(
                    text = uiState.lastSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
