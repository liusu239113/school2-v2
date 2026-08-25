package com.arktools.xiaozhang.ui.district

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.arktools.xiaozhang.R
import com.arktools.xiaozhang.domain.engine.GameBalanceConfig
import com.arktools.xiaozhang.domain.expansion.*
import com.arktools.xiaozhang.domain.model.DistrictType
import com.arktools.xiaozhang.ui.animation.AnimationConstants
import com.arktools.xiaozhang.ui.animation.cardTapAnimation
import com.arktools.xiaozhang.ui.components.PixelAlertDialog
import com.arktools.xiaozhang.ui.components.PixelButton
import com.arktools.xiaozhang.ui.components.PixelButtonStyle
import com.arktools.xiaozhang.ui.components.PixelIcon
import com.arktools.xiaozhang.ui.theme.AccentGreen
import com.arktools.xiaozhang.ui.theme.AccentOrange
import com.arktools.xiaozhang.ui.theme.AccentRed
import com.arktools.xiaozhang.ui.theme.Primary
import kotlinx.coroutines.launch

@Composable
fun DistrictScreen(
    viewModel: DistrictViewModel = hiltViewModel()
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("社会合作", "校园扩建")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color.Transparent
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTabIndex) {
            0 -> DistrictManageContent(viewModel)
            1 -> ExpansionContent(viewModel)
        }
    }
}

// ===== 社会合作 Tab =====
@Composable
private fun DistrictManageContent(viewModel: DistrictViewModel) {
    val districtStats by viewModel.districtStats.collectAsState()
    val school by viewModel.school.collectAsState()
    val upgradeMessage by viewModel.upgradeMessage.collectAsState()
    var showUpgradeDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(upgradeMessage) {
        upgradeMessage?.let { msg ->
            scope.launch {
                snackbarHostState.showSnackbar(msg)
                viewModel.clearUpgradeMessage()
            }
        }
    }

    val currentReputation = school?.reputation ?: 0L
    val currentLevel = school?.campusLevel ?: 1
    val districts = remember { DistrictType.entries.toList() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "社会合作",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Primary.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Lv.$currentLevel ${GameBalanceConfig.getSchoolLevelName(currentLevel)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "社会合作与校园等级",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "社会合作决定生源触达和合作收益。解锁更高层级的合作网络需要达到对应的校园等级和声誉门槛。\n校园等级越高，社会曝光加成越大，合作抽成越优惠。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            item {
                CampusUpgradeCard(
                    school = school,
                    onUpgradeClick = { showUpgradeDialog = true }
                )
            }

            item {
                LevelBonusOverviewCard(schoolLevel = currentLevel)
            }

            item {
                DistrictUnlockProgress(
                    districts = districts,
                    schoolLevel = currentLevel,
                    currentReputation = currentReputation
                )
            }

            itemsIndexed(districts) { index, district ->
                val stats = districtStats[district] ?: DistrictViewModel.DistrictStats(0, 0, 0.0, 0f)
                val isUnlocked = GameBalanceConfig.isDistrictUnlocked(district, currentLevel, currentReputation)
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(
                        animationSpec = androidx.compose.animation.core.tween(
                            AnimationConstants.defaultDuration,
                            delayMillis = index * AnimationConstants.entranceDelay
                        )
                    ) + slideInVertically(
                        animationSpec = androidx.compose.animation.core.tween(
                            AnimationConstants.defaultDuration,
                            delayMillis = index * AnimationConstants.entranceDelay
                        ),
                        initialOffsetY = { it / 8 }
                    )
                ) {
                    DistrictCard(
                        district = district,
                        stats = stats,
                        isUnlocked = isUnlocked,
                        currentLevel = currentLevel,
                        currentReputation = currentReputation,
                        effectiveExposure = viewModel.getEffectiveExposure(district),
                        effectiveCommission = viewModel.getEffectiveCommission(district),
                        effectiveMaxCourses = viewModel.getEffectiveMaxCourses(district)
                    )
                }
            }
        }
    }

    val capturedSchool = school
    if (showUpgradeDialog && capturedSchool != null) {
        var conditions by remember { mutableStateOf<List<DistrictViewModel.UpgradeCondition>>(emptyList()) }
        LaunchedEffect(showUpgradeDialog) {
            conditions = viewModel.getUpgradeConditions()
        }
        val allMet = conditions.isNotEmpty() && conditions.all { it.met }
        val conditionsText = buildString {
            append("当前等级: Lv.${capturedSchool.campusLevel} ${GameBalanceConfig.getSchoolLevelName(capturedSchool.campusLevel)}\n")
            append("目标等级: Lv.${capturedSchool.campusLevel + 1} ${GameBalanceConfig.getSchoolLevelName(capturedSchool.campusLevel + 1)}\n\n")
            append("━━ 升级条件 ━━\n")
            conditions.forEach { cond ->
                val icon = if (cond.met) "✓" else "✗"
                append("$icon ${cond.label}: ${cond.current} / 需${cond.required}\n")
            }
            if (!allMet) {
                append("\n⚠ 请先满足所有条件再升级")
            }
        }
        PixelAlertDialog(
            onDismissRequest = { showUpgradeDialog = false },
            title = "升级校园",
            text = conditionsText,
            confirmText = if (allMet) "升级" else "条件不足",
            dismissText = "关闭",
            onConfirm = {
                if (allMet) {
                    viewModel.upgradeCampus()
                }
                showUpgradeDialog = false
            },
            onDismiss = { showUpgradeDialog = false },
            confirmStyle = if (allMet) PixelButtonStyle.PRIMARY else PixelButtonStyle.CANCEL,
            dismissStyle = PixelButtonStyle.CANCEL
        )
    }
}

// ===== 校区扩建 Tab =====
@Composable
private fun ExpansionContent(viewModel: DistrictViewModel) {
    val state by viewModel.expansionState.collectAsState()
    val school by viewModel.school.collectAsState()
    var showBuildDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            CampusOverviewCard(state)
        }

        item {
            CapacityCard(state)
        }

        item {
            CampusLevelCard(state, onUpgrade = { viewModel.upgradeCampusExpansionLevel() })
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "校区建筑",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                PixelButton(
                    text = "新建",
                    style = PixelButtonStyle.PRIMARY,
                    onClick = { showBuildDialog = true },
                    enabled = state.zones.size < state.currentLevel.maxZones
                )
            }
        }

        val constructing = state.zones.filter { !it.isCompleted }
        if (constructing.isNotEmpty()) {
            item {
                Text(
                    "在建工程 (${constructing.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFFF9800)
                )
            }
            items(constructing) { zone ->
                ConstructionZoneCard(zone, onInvest = { viewModel.investInZone(zone.id, zone.totalCostWan / 4.0) })
            }
        }

        val completed = state.zones.filter { it.isCompleted }
        if (completed.isNotEmpty()) {
            item {
                Text(
                    "已建成 (${completed.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF4CAF50)
                )
            }
            items(completed) { zone ->
                CompletedZoneCard(
                    zone = zone,
                    cash = school?.cash ?: 0.0,
                    upgradeCost = viewModel.getUpgradeQualityCost(zone.id),
                    onRepair = { viewModel.repairZone(zone.id) },
                    onUpgrade = { viewModel.upgradeZoneQuality(zone.id) }
                )
            }
        }

        if (state.events.isNotEmpty()) {
            item {
                Text(
                    "建设日志",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(state.events.take(10)) { event ->
                EventRow(event)
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }

    if (showBuildDialog) {
        BuildDialog(
            availableTypes = viewModel.getAvailableZoneTypes(),
            onConfirm = { type, quality ->
                viewModel.startConstruction(type, type.displayName, quality)
                showBuildDialog = false
            },
            onDismiss = { showBuildDialog = false }
        )
    }
}

// ===== 共享组件 =====

@Composable
private fun CampusUpgradeCard(
    school: com.arktools.xiaozhang.domain.model.School?,
    onUpgradeClick: () -> Unit
) {
    if (school == null) return

    val upgradeCost = GameBalanceConfig.getCampusUpgradeCost(school.campusLevel)
    val canAfford = school.cash >= upgradeCost
    val isMaxLevel = school.campusLevel >= GameBalanceConfig.MAX_SCHOOL_LEVEL

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .cardTapAnimation(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Apartment,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                    Column {
                        Text(
                            text = "Lv.${school.campusLevel} ${GameBalanceConfig.getSchoolLevelName(school.campusLevel)}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "教师上限: ${school.maxTeachers}人 | 月运营: ${GameBalanceConfig.getMonthlyRent(school.campusLevel)}万",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
                if (!isMaxLevel) {
                    Button(
                        onClick = onUpgradeClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (canAfford) Primary else Primary.copy(alpha = 0.6f)
                        )
                    ) {
                        Icon(
                            Icons.Default.Upgrade,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        Text("升级")
                    }
                }
            }

            if (!isMaxLevel) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "升级费用: ${upgradeCost}万",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (canAfford) AccentGreen else AccentRed
                    )
                    Text(
                        text = "当前资金: ${String.format("%.1f", school.cash)}万",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "已达最高等级！",
                    style = MaterialTheme.typography.labelMedium,
                    color = AccentOrange
                )
            }
        }
    }
}

@Composable
private fun LevelBonusOverviewCard(schoolLevel: Int) {
    val exposureBonus = GameBalanceConfig.getDistrictExposureBonus(schoolLevel)
    val commissionDiscount = GameBalanceConfig.getDistrictCommissionDiscount(schoolLevel)
    val courseBonus = GameBalanceConfig.getDistrictCourseBonus(schoolLevel)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.TrendingUp,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = AccentGreen
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "当前等级加成",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BonusChip(
                    label = "曝光",
                    value = "×${String.format("%.1f", exposureBonus)}",
                    highlight = exposureBonus > 1.0
                )
                BonusChip(
                    label = "抽成折扣",
                    value = "${((1.0 - commissionDiscount) * 100).toInt()}%减免",
                    highlight = commissionDiscount < 1.0
                )
                BonusChip(
                    label = "并发+",
                    value = "+$courseBonus",
                    highlight = courseBonus > 0
                )
            }
        }
    }
}

@Composable
private fun BonusChip(label: String, value: String, highlight: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = if (highlight) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun DistrictUnlockProgress(
    districts: List<DistrictType>,
    schoolLevel: Int,
    currentReputation: Long
) {
    val totalDistricts = districts.size
    val unlockedCount = districts.count {
        GameBalanceConfig.isDistrictUnlocked(it, schoolLevel, currentReputation)
    }
    val progress = if (totalDistricts > 0) unlockedCount.toFloat() / totalDistricts else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "学区解锁进度",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "$unlockedCount / $totalDistricts",
                    style = MaterialTheme.typography.titleSmall,
                    color = AccentGreen,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = AccentGreen,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "学校等级: Lv.$schoolLevel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = "声誉: $currentReputation",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun DistrictCard(
    district: DistrictType,
    stats: DistrictViewModel.DistrictStats,
    isUnlocked: Boolean,
    currentLevel: Int,
    currentReputation: Long,
    effectiveExposure: Double,
    effectiveCommission: Double,
    effectiveMaxCourses: Int
) {
    val cardAlpha = if (isUnlocked) 1f else 0.7f
    val containerColor = if (isUnlocked) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(cardAlpha)
            .cardTapAnimation(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isUnlocked) 2.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (isUnlocked) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = district.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isUnlocked) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = district.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                }
                if (isUnlocked && stats.courseCount > 0) {
                    Text(
                        text = "${stats.courseCount} 门",
                        style = MaterialTheme.typography.titleSmall,
                        color = AccentGreen
                    )
                } else if (!isUnlocked) {
                    Text(
                        text = "未解锁",
                        style = MaterialTheme.typography.labelMedium,
                        color = AccentRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isUnlocked) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DistrictStatItem(
                        "有效曝光",
                        "×${String.format("%.2f", effectiveExposure)}",
                        if (effectiveExposure > district.baseExposure) AccentGreen else null
                    )
                    DistrictStatItem(
                        "实际抽成",
                        "${String.format("%.1f", effectiveCommission * 100)}%",
                        if (effectiveCommission < district.commissionRate) AccentGreen else null
                    )
                    DistrictStatItem(
                        "并发上限",
                        "$effectiveMaxCourses 门",
                        if (effectiveMaxCourses > district.maxConcurrentCourses) AccentGreen else null
                    )
                }

                if (stats.courseCount > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DistrictStatItem("总招生", "${stats.totalEnrollment}", null)
                        DistrictStatItem("总收入", "${String.format("%.1f", stats.totalRevenue)}万", null)
                        DistrictStatItem("均分", "${String.format("%.1f", stats.averageScore)}", null)
                    }
                } else {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "已解锁 · 暂无学生",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            } else {
                val levelMet = currentLevel >= district.requiredSchoolLevel
                val reputationMet = currentReputation >= district.reputationThreshold

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.School, null, modifier = Modifier.size(14.dp), tint = if (levelMet) AccentGreen else AccentRed)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("学校等级: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Lv.$currentLevel", style = MaterialTheme.typography.bodySmall, color = if (levelMet) AccentGreen else AccentRed, fontWeight = FontWeight.Bold)
                    Text(" / ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Lv.${district.requiredSchoolLevel}(${GameBalanceConfig.getSchoolLevelName(district.requiredSchoolLevel)})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (levelMet) "✓" else "✗", color = if (levelMet) AccentGreen else AccentRed, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Star, null, modifier = Modifier.size(14.dp), tint = if (reputationMet) AccentGreen else AccentOrange)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("声誉: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$currentReputation", style = MaterialTheme.typography.bodySmall, color = if (reputationMet) AccentGreen else AccentOrange, fontWeight = FontWeight.Bold)
                    Text(" / ${district.reputationThreshold}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (reputationMet) "✓" else "✗", color = if (reputationMet) AccentGreen else AccentRed, fontWeight = FontWeight.Bold)
                }

                if (!reputationMet && district.reputationThreshold > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val repProgress = (currentReputation.toFloat() / district.reputationThreshold).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { repProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (repProgress >= 0.8f) AccentOrange else MaterialTheme.colorScheme.outline,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "还差 ${district.reputationThreshold - currentReputation} 声誉",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentOrange.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("基础曝光: ×${district.baseExposure}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                Text("基础抽成: ${(district.commissionRate * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                Text("基础并发: ${district.maxConcurrentCourses}门", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun DistrictStatItem(label: String, value: String, highlightColor: Color?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = highlightColor ?: MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ===== 校区扩建组件 =====

@Composable
private fun CampusOverviewCard(state: CampusExpansionState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF1565C0), Color(0xFF42A5F5))
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(20.dp)
        ) {
            Column {
                Text("校园扩建", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(state.currentLevel.displayName, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ExpansionStatColumn("校园容量", "${state.totalCapacity}", Color.White)
                    ExpansionStatColumn("在建", "${state.constructingZones}", Color(0xFFFFE082))
                    ExpansionStatColumn("已建成", "${state.completedZones}", Color(0xFFA5D6A7))
                    ExpansionStatColumn("月维护", "${"%.1f".format(state.monthlyMaintenanceCost)}万", Color(0xFFEF9A9A))
                }
            }
        }
    }
}

@Composable
private fun ExpansionStatColumn(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
    }
}

@Composable
private fun CapacityCard(state: CampusExpansionState) {
    val usagePercent = if (state.totalCapacity > 0) {
        (state.usedCapacity.toFloat() / state.totalCapacity).coerceAtMost(1.5f)
    } else 1f

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("校园容量使用率", fontWeight = FontWeight.Medium)
                Text(
                    "${state.usedCapacity}/${state.totalCapacity} (${(usagePercent * 100).toInt()}%)",
                    color = if (usagePercent > 1f) MaterialTheme.colorScheme.error else Color.Unspecified,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (usagePercent / 1.5f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = when {
                    usagePercent > 1f -> MaterialTheme.colorScheme.error
                    usagePercent > 0.8f -> Color(0xFFFF9800)
                    else -> Color(0xFF4CAF50)
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            if (usagePercent > 0.9f) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (usagePercent > 1f) "容量超载！请尽快扩建" else "容量即将满载",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "校园容量是实体空间上限，招生上限取决于「学院」页面的班型配置",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun CampusLevelCard(state: CampusExpansionState, onUpgrade: () -> Unit) {
    val currentIndex = CampusLevel.entries.indexOf(state.currentLevel)
    val canUpgrade = currentIndex < CampusLevel.entries.size - 1
    val nextLevel = if (canUpgrade) CampusLevel.entries[currentIndex + 1] else null
    val completedCount = state.zones.count { it.isCompleted }
    val requiredBuildings = if (canUpgrade) (state.currentLevel.maxZones * 0.6).toInt().coerceAtLeast(1) else 0

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("校区等级: ${state.currentLevel.displayName}", fontWeight = FontWeight.Bold)
                    Text(
                        "最大建筑数: ${state.currentLevel.maxZones} · 容量加成: x${state.currentLevel.capacityBonus}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canUpgrade) {
                    Text(
                        "${currentIndex + 1}/${CampusLevel.entries.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text("MAX", fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                }
            }
            if (nextLevel != null) {
                Spacer(modifier = Modifier.height(12.dp))
                // 升级条件展示
                Text(
                    "升级到「${nextLevel.displayName}」需要：",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "• 资金: ${nextLevel.unlockCostWan.toInt()}万",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "• 已建成建筑: ${completedCount}/${requiredBuildings} 栋" +
                        if (completedCount >= requiredBuildings) " ✓" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (completedCount >= requiredBuildings) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "升级后: 最大建筑数 ${nextLevel.maxZones} · 容量加成 x${nextLevel.capacityBonus}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onUpgrade,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("升级校区 (${nextLevel.unlockCostWan.toInt()}万)")
                }
            }
        }
    }
}

@Composable
private fun ConstructionZoneCard(zone: CampusZone, onInvest: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelIcon(emoji = zone.type.icon, size = 24.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(zone.name, fontWeight = FontWeight.Bold)
                        Text(zone.phase.displayName, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF9800))
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${zone.progress.toInt()}%", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("${zone.monthsElapsed}/${zone.type.buildMonths}月", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { zone.progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Color(0xFFFF9800),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "已投资: ¥${String.format("%.1f", zone.totalInvested)}万 / ¥${String.format("%.1f", zone.totalCostWan)}万",
                        style = MaterialTheme.typography.bodySmall
                    )
                    val startThreshold = zone.totalCostWan * 0.10
                    if (zone.totalInvested < startThreshold) {
                        Text(
                            "还需投入 ¥${String.format("%.1f", startThreshold - zone.totalInvested)}万才开工",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFF57C00)
                        )
                    } else {
                        Text(
                            "已开工：每月自动推进约${(100f / zone.type.buildMonths).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF388E3C)
                        )
                    }
                    Text(
                        "预计容量: +${zone.expectedCapacity}人 · 质量等级: ${zone.qualityLevel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onInvest,
                    enabled = zone.remainingCostWan > 0
                ) {
                    Text("追加投资", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun CompletedZoneCard(
    zone: CampusZone,
    cash: Double,
    upgradeCost: Double,
    onRepair: () -> Unit,
    onUpgrade: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    PixelIcon(emoji = zone.type.icon, size = 24.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(zone.name, fontWeight = FontWeight.Bold)
                        Text(
                            "容量: ${zone.capacity}人 · 质量Lv.${zone.qualityLevel} · 建成于${zone.completedYear}年${zone.completedMonth}月",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("维护度", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(48.dp))
                LinearProgressIndicator(
                    progress = { zone.maintenanceLevel / 100f },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = when {
                        zone.maintenanceLevel >= 70f -> Color(0xFF4CAF50)
                        zone.maintenanceLevel >= 40f -> Color(0xFFFF9800)
                        else -> Color(0xFFF44336)
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("${zone.maintenanceLevel.toInt()}%", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (zone.maintenanceLevel < 70f) {
                    OutlinedButton(
                        onClick = onRepair,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("维修", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (zone.qualityLevel < 5) {
                    val canAfford = cash >= upgradeCost
                    OutlinedButton(
                        onClick = onUpgrade,
                        enabled = canAfford,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "升级质量 ${String.format("%.1f", upgradeCost)}万",
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Text(
                        "满级",
                        fontSize = 12.sp,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: ExpansionEvent) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (event.isPositive) Icons.Default.CheckCircle else Icons.Default.Warning,
            null,
            tint = if (event.isPositive) Color(0xFF4CAF50) else Color(0xFFFF9800),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(event.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(event.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "${event.year}年${event.month}月",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BuildDialog(
    availableTypes: List<CampusZoneType>,
    onConfirm: (CampusZoneType, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedType by remember { mutableStateOf(availableTypes.firstOrNull()) }
    var qualityLevel by remember { mutableStateOf(1f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
        ) {
            Image(
                painter = painterResource(id = R.drawable.dialog_bg),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.FillBounds
            )
            Column(modifier = Modifier.padding(24.dp).fillMaxHeight()) {
                Text(
                    text = "新建校区建筑",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                // 建筑列表可滚动，确保按钮不被裁剪
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    availableTypes.forEach { type ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedType == type, onClick = { selectedType = type })
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(type.displayName, fontWeight = FontWeight.Medium)
                                Text(
                                    "¥${type.baseCostWan.toInt()}万 · ${type.buildMonths}月 · +${type.baseCapacity}容量",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("建设质量: ${qualityLevel.toInt()}", fontWeight = FontWeight.Medium)
                    Slider(
                        value = qualityLevel,
                        onValueChange = { qualityLevel = it },
                        valueRange = 1f..5f,
                        steps = 3
                    )
                    Text(
                        "质量越高，造价越高但容量加成更大",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PixelButton(
                        text = "取消",
                        style = PixelButtonStyle.CANCEL,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    PixelButton(
                        text = "开始建设",
                        style = PixelButtonStyle.CONFIRM,
                        onClick = { selectedType?.let { onConfirm(it, qualityLevel.toInt()) } },
                        enabled = selectedType != null,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
