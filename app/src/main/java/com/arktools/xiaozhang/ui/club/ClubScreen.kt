package com.arktools.xiaozhang.ui.club

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.arktools.xiaozhang.R
import com.arktools.xiaozhang.domain.club.*
import com.arktools.xiaozhang.domain.clubactivity.*
import com.arktools.xiaozhang.ui.components.PixelButton
import com.arktools.xiaozhang.ui.components.PixelButtonStyle
import com.arktools.xiaozhang.ui.components.PixelIcon
import com.arktools.xiaozhang.ui.theme.AccentGreen
import com.arktools.xiaozhang.ui.theme.AccentOrange
import com.arktools.xiaozhang.ui.theme.AccentRed

@Composable
fun ClubScreen(
    viewModel: ClubViewModel = hiltViewModel()
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTabIndex, containerColor = MaterialTheme.colorScheme.surface) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = { Text("社团管理") },
                icon = { Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = { Text("活动竞赛") },
                icon = { Icon(Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        when (selectedTabIndex) {
            0 -> ClubManageContent(viewModel)
            1 -> ClubActivityContent(viewModel)
        }
    }
}

// ========== 社团管理 Tab ==========

@Composable
private fun ClubManageContent(viewModel: ClubViewModel) {
    val clubs by viewModel.clubs.collectAsState()
    val recentEvents by viewModel.recentEvents.collectAsState()
    val pendingApplications by viewModel.pendingApplications.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        ClubStatsHeader(
            clubCount = clubs.size,
            totalMembers = clubs.sumOf { it.memberCount },
            satisfactionBonus = viewModel.getTotalSatisfactionBonus(),
            trophies = clubs.sumOf { it.trophyCount },
            pendingCount = pendingApplications.size
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (pendingApplications.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("待审批申请", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Badge(containerColor = AccentRed) { Text("${pendingApplications.size}", color = Color.White) }
                    }
                }
                items(pendingApplications, key = { it.id }) { application ->
                    PendingApplicationCard(application)
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            if (clubs.isEmpty() && pendingApplications.isEmpty()) {
                item { EmptyClubState() }
            } else if (clubs.isNotEmpty()) {
                item {
                    Text("活跃社团 (${clubs.size}/${ClubManager.getMaxClubs(viewModel.getCampusLevel())})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                items(clubs, key = { it.id }) { club ->
                    ClubCard(club = club, onDisband = { viewModel.disbandClub(club.id) })
                }
                if (recentEvents.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("近期活动", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    items(recentEvents.take(10)) { event -> EventCard(event) }
                }
            }
        }
    }
}

// ========== 活动竞赛 Tab ==========

@Composable
private fun ClubActivityContent(viewModel: ClubViewModel) {
    val actState by viewModel.activityState.collectAsState()
    val clubs by viewModel.clubs.collectAsState()
    var showPlanDialog by remember { mutableStateOf(false) }
    var showCompetitionDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { ActivityOverviewCard(actState) }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PixelButton(text = "策划活动", style = PixelButtonStyle.PRIMARY, onClick = { showPlanDialog = true }, modifier = Modifier.weight(1f), enabled = clubs.isNotEmpty())
                PixelButton(text = "报名竞赛", style = PixelButtonStyle.PRIMARY, onClick = { showCompetitionDialog = true }, modifier = Modifier.weight(1f), enabled = clubs.isNotEmpty())
            }
        }

        if (actState.plannedActivities.isNotEmpty()) {
            item { Text("活动计划", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            items(actState.plannedActivities) { activity ->
                PlannedActivityCard(activity, onCancel = { viewModel.cancelActivity(activity.id) })
            }
        }

        if (actState.activeCompetitions.isNotEmpty()) {
            item { Text("参赛中", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            items(actState.activeCompetitions) { entry -> CompetitionEntryCard(entry) }
        }

        if (actState.awardsHistory.isNotEmpty()) {
            item { Text("荣誉墙", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            items(actState.awardsHistory.take(10)) { award -> AwardCard(award) }
        }

        if (actState.recentEvents.isNotEmpty()) {
            item { Text("活动日志", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            items(actState.recentEvents.take(8)) { event -> ActivityEventRow(event) }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }

    if (showPlanDialog) {
        PlanActivityDialog(
            clubs = clubs,
            activityTypes = viewModel.getActivityTypes(),
            onConfirm = { clubId, type, name, budget ->
                viewModel.planActivity(clubId, type, name, budget)
                showPlanDialog = false
            },
            onDismiss = { showPlanDialog = false }
        )
    }

    if (showCompetitionDialog) {
        CompetitionRegistrationDialog(
            clubs = clubs,
            competitions = viewModel.getAvailableCompetitions(100L),
            onConfirm = { clubId, comp ->
                viewModel.registerForCompetition(clubId, comp)
                showCompetitionDialog = false
            },
            onDismiss = { showCompetitionDialog = false }
        )
    }
}

// ===== 统计头部 =====

@Composable
private fun ClubStatsHeader(clubCount: Int, totalMembers: Int, satisfactionBonus: Float, trophies: Int, pendingCount: Int) {
    Box(modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(16.dp)) {
        Image(painter = painterResource(id = R.drawable.card_bg), contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("社团管理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (pendingCount > 0) {
                    AssistChip(onClick = {}, label = { Text("${pendingCount}份申请待签字") },
                        colors = AssistChipDefaults.assistChipColors(containerColor = AccentOrange.copy(alpha = 0.15f), labelColor = AccentOrange))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("社团由学生申请创建，校长签字审批", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                HeaderStatItem("校", "$clubCount", "社团")
                HeaderStatItem("人", "$totalMembers", "成员")
                HeaderStatItem("心", "+${String.format("%.1f", satisfactionBonus)}", "满意度")
                HeaderStatItem("杯", "$trophies", "奖杯")
            }
        }
    }
}

@Composable
private fun HeaderStatItem(icon: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 22.sp)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ===== 待审批卡片 =====

@Composable
private fun PendingApplicationCard(application: ClubApplication) {
    val remainingDays = ClubManager.APPLICATION_TIMEOUT_DAYS - application.waitDays
    val urgencyColor = when { remainingDays <= 5 -> AccentRed; remainingDays <= 10 -> AccentOrange; else -> Color(0xFF2196F3) }

    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = urgencyColor.copy(alpha = 0.05f))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelIcon(emoji = application.clubType.icon, size = 20.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(application.clubType.defaultName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                AssistChip(onClick = {}, label = { Text("待签字", style = MaterialTheme.typography.labelSmall) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = urgencyColor.copy(alpha = 0.15f), labelColor = urgencyColor))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("申请人：${application.applicantName}等${application.applicantCount}人", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Text("\"${application.reason}\"", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${application.clubType.category.displayName} · ¥${String.format("%.1f", application.clubType.monthlyCost)}万/月", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("剩余 ${remainingDays} 天", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = urgencyColor)
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { application.waitDays.toFloat() / ClubManager.APPLICATION_TIMEOUT_DAYS },
                modifier = Modifier.fillMaxWidth().height(4.dp), color = urgencyColor, trackColor = urgencyColor.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(8.dp))
            Text("请在事件弹窗中签字批准或驳回", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ===== 社团卡片 =====

@Composable
private fun ClubCard(club: Club, onDisband: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth().wrapContentHeight().animateContentSize().clickable { expanded = !expanded }) {
        Image(painter = painterResource(id = R.drawable.card_bg), contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(getLevelColor(club.level).copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    PixelIcon(emoji = club.type.icon, size = 22.dp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(club.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(modifier = Modifier.width(6.dp))
                        LevelBadge(club.level)
                    }
                    Text("${club.memberCount}人 · ${club.type.category.displayName} · 活跃${club.monthsActive}月", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (club.trophyCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PixelIcon(emoji = "🏆", size = 14.dp)
                        Text("${club.trophyCount}", fontSize = 14.sp)
                    }
                }
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("热情度: ${club.enthusiasm.toInt()}%", style = MaterialTheme.typography.bodySmall)
                        Text("满意度加成: +${String.format("%.1f", club.type.satisfactionBonus * club.level.multiplier)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("月费: ¥${String.format("%.1f", club.type.monthlyCost * club.level.multiplier)}万/月", style = MaterialTheme.typography.bodySmall)
                        Text("声誉加成: +${(club.type.reputationBonus * club.level.multiplier).toInt()}/月", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(progress = { club.enthusiasm / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = getLevelColor(club.level), trackColor = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text("已签字批准创建", style = MaterialTheme.typography.labelSmall, color = AccentGreen)
                Spacer(modifier = Modifier.height(8.dp))
                PixelButton(text = "解散社团", style = PixelButtonStyle.DANGER, modifier = Modifier.fillMaxWidth(), onClick = onDisband)
            }
        }
    }
}

@Composable
private fun LevelBadge(level: ClubLevel) {
    Surface(shape = RoundedCornerShape(4.dp), color = getLevelColor(level).copy(alpha = 0.15f)) {
        Text(level.displayName, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = getLevelColor(level), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EventCard(event: ClubEvent) {
    val (icon, text) = when (event) {
        is ClubEvent.Competition -> { if (event.won) "🏆" to "${event.clubName}在竞赛中获胜！声誉+${event.reputationReward}" else "⚔️" to "${event.clubName}参加了竞赛，虽败犹荣" }
        is ClubEvent.Exhibition -> "🎪" to "${event.clubName}举办了展览，声誉+${event.reputationReward}"
        is ClubEvent.Recruitment -> "📢" to "${event.clubName}招新活动成功，新增${event.newMembers}名成员"
        is ClubEvent.Achievement -> "🏅" to "${event.clubName}: ${event.achievement}"
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 16.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun EmptyClubState() {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PixelIcon(emoji = "🎪", size = 48.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Text("还没有社团", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("学生会在适当时机提交社团创建申请，\n届时需要校长签字审批。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(8.dp))
            Text("学校声誉越高、学生越多，申请越频繁", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

// ===== 活动概览 =====

@Composable
private fun ActivityOverviewCard(state: ClubActivityState) {
    Box(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
        Image(painter = painterResource(id = R.drawable.card_bg), contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
        Box(modifier = Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF7B1FA2), Color(0xFF4A148C)))).padding(20.dp)) {
            Column {
                Text("社团活动中心", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ActStatItem("进行中活动", "${state.plannedActivities.size}", Color.White)
                    ActStatItem("参赛中", "${state.activeCompetitions.size}", Color(0xFFFFD54F))
                    ActStatItem("累计获奖", "${state.totalAwardsCount}", Color(0xFFFF8A65))
                    ActStatItem("贡献声誉", "${state.totalReputationFromActivities}", Color(0xFF81C784))
                }
            }
        }
    }
}

@Composable
private fun ActStatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
    }
}

// ===== 活动卡片 =====

@Composable
private fun PlannedActivityCard(activity: PlannedActivity, onCancel: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
        Image(painter = painterResource(id = R.drawable.card_bg), contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(activity.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("${activity.clubName} · ${activity.activityType.displayName}", fontSize = 12.sp, color = Color.Gray)
                }
                AssistChip(onClick = {}, label = { Text(activity.status.displayName, fontSize = 11.sp) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = when (activity.status) { ActivityStatus.PLANNING -> Color(0xFFFFF3E0); ActivityStatus.IN_PROGRESS -> Color(0xFFE8F5E9); else -> Color(0xFFF5F5F5) }))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("预算: ¥${activity.budgetAllocated}", fontSize = 12.sp, color = Color(0xFF4A148C))
                Text("预计参与: ${activity.expectedParticipants}人", fontSize = 12.sp, color = Color.Gray)
            }
            if (activity.status == ActivityStatus.PLANNING) {
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(progress = { 1f - activity.remainingPrepMonths.toFloat() / activity.preparationMonths.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = Color(0xFF7B1FA2), trackColor = Color(0xFFE1BEE7))
                Text("筹备剩余 ${activity.remainingPrepMonths} 个月", fontSize = 11.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(6.dp))
                PixelButton(text = "取消活动", style = PixelButtonStyle.DANGER, modifier = Modifier.fillMaxWidth(), onClick = onCancel)
            }
        }
    }
}

@Composable
private fun CompetitionEntryCard(entry: CompetitionEntry) {
    Box(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
        Image(painter = painterResource(id = R.drawable.card_bg), contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.competitionName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("${entry.clubName} · ${entry.level.displayName} · ${entry.field.displayName}", fontSize = 12.sp, color = Color.Gray)
                }
                AssistChip(onClick = {}, label = { Text(entry.status.displayName, fontSize = 11.sp) })
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (entry.status == CompetitionStatus.IN_PROGRESS) {
                LinearProgressIndicator(progress = { entry.currentRound.toFloat() / entry.remainingRounds.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = Color(0xFFE65100), trackColor = Color(0xFFFFE0B2))
                Text("第 ${entry.currentRound}/${entry.remainingRounds} 轮 · 积分 ${entry.score.toInt()}", fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun AwardCard(award: Award) {
    Box(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
        Image(painter = painterResource(id = R.drawable.card_bg), contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PixelIcon(emoji = award.type.icon, size = 24.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(award.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(award.description, fontSize = 11.sp, color = Color.Gray)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("+${award.reputationGained}", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("${award.year}年${award.month}月", fontSize = 10.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun ActivityEventRow(event: ActivityEvent) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val icon = when (event.type) { ActivityEventType.ACTIVITY_START -> "开始"; ActivityEventType.ACTIVITY_COMPLETE -> "完成"; ActivityEventType.COMPETITION_RESULT -> "竞赛"; ActivityEventType.COMPETITION_INVITE -> "邀请"; ActivityEventType.ANNUAL_REVIEW -> "总结" }
        Text(icon, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(event.title, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(event.description, fontSize = 11.sp, color = Color.Gray)
        }
        Text("${event.year}/${event.month}", fontSize = 10.sp, color = Color.Gray)
    }
}

// ===== 策划活动对话框 =====

@Composable
private fun PlanActivityDialog(clubs: List<Club>, activityTypes: List<ActivityType>, onConfirm: (Long, ActivityType, String, Long) -> Unit, onDismiss: () -> Unit) {
    var selectedClubIndex by remember { mutableIntStateOf(0) }
    var selectedTypeIndex by remember { mutableIntStateOf(0) }
    var customName by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf("5000") }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxWidth(0.92f).wrapContentHeight()) {
            Image(painter = painterResource(id = R.drawable.dialog_bg), contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("策划新活动", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(4.dp))
                Text("选择社团", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(clubs.size) { index ->
                        val club = clubs[index]
                        val selected = selectedClubIndex == index
                        Button(
                            onClick = { selectedClubIndex = index },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                club.name.take(4),
                                fontSize = 10.sp,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                Text("活动类型", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Column {
                    activityTypes.take(4).forEachIndexed { index, type ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedTypeIndex == index, onClick = { selectedTypeIndex = index })
                            Column { Text(type.displayName, fontSize = 13.sp); Text("费用¥${type.baseCost} · 筹备${type.prepMonths}月", fontSize = 11.sp, color = Color.Gray) }
                        }
                    }
                }
                OutlinedTextField(value = customName, onValueChange = { customName = it }, label = { Text("活动名称(可选)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = budget, onValueChange = { budget = it.filter { c -> c.isDigit() } }, label = { Text("预算(元)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PixelButton(text = "取消", style = PixelButtonStyle.CANCEL, onClick = onDismiss, modifier = Modifier.weight(1f))
                    PixelButton(text = "确认策划", style = PixelButtonStyle.CONFIRM, onClick = {
                        val club = clubs.getOrNull(selectedClubIndex) ?: return@PixelButton
                        val type = activityTypes.getOrNull(selectedTypeIndex) ?: return@PixelButton
                        val name = customName.ifBlank { "${club.name}${type.displayName}" }
                        val budgetValue = budget.toLongOrNull() ?: type.baseCost
                        onConfirm(club.id, type, name, budgetValue)
                    }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ===== 竞赛报名对话框 =====

@Composable
private fun CompetitionRegistrationDialog(clubs: List<Club>, competitions: List<CompetitionInfo>, onConfirm: (Long, CompetitionInfo) -> Unit, onDismiss: () -> Unit) {
    var selectedClubIndex by remember { mutableIntStateOf(0) }
    var selectedCompIndex by remember { mutableIntStateOf(0) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxWidth(0.92f).wrapContentHeight()) {
            Image(painter = painterResource(id = R.drawable.dialog_bg), contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("报名竞赛", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(4.dp))
                Text("选择参赛社团", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(clubs.size) { index ->
                        val club = clubs[index]
                        val selected = selectedClubIndex == index
                        Button(
                            onClick = { selectedClubIndex = index },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                club.name.take(4),
                                fontSize = 10.sp,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                Text("可报名竞赛", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                if (competitions.isEmpty()) { Text("暂无可报名的竞赛", fontSize = 12.sp, color = Color.Gray) } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(competitions.size) { index ->
                            val comp = competitions[index]
                            val selected = selectedCompIndex == index
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .clickable { selectedCompIndex = index }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selected, onClick = { selectedCompIndex = index })
                                Column {
                                    Text(comp.name, fontSize = 13.sp)
                                    Text("${comp.level.displayName} · ${comp.field.displayName} · 报名费${comp.registrationFee}万", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PixelButton(text = "取消", style = PixelButtonStyle.CANCEL, onClick = onDismiss, modifier = Modifier.weight(1f))
                    PixelButton(text = "确认报名", style = PixelButtonStyle.CONFIRM, onClick = {
                        val club = clubs.getOrNull(selectedClubIndex) ?: return@PixelButton
                        val comp = competitions.getOrNull(selectedCompIndex) ?: return@PixelButton
                        onConfirm(club.id, comp)
                    }, modifier = Modifier.weight(1f), enabled = competitions.isNotEmpty())
                }
            }
        }
    }
}

// ===== 工具 =====

private fun getLevelColor(level: ClubLevel): Color {
    return when (level) { ClubLevel.BEGINNER -> Color(0xFF9E9E9E); ClubLevel.DEVELOPING -> Color(0xFF4CAF50); ClubLevel.ESTABLISHED -> Color(0xFF2196F3); ClubLevel.ADVANCED -> Color(0xFF9C27B0); ClubLevel.ELITE -> Color(0xFFFF9800) }
}
