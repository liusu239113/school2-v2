package com.arktools.xiao.ui.facility

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.hilt.navigation.compose.hiltViewModel
import com.arktools.xiao.domain.model.Facility
import com.arktools.xiao.domain.model.FacilityBonusCalculator
import com.arktools.xiao.domain.model.FacilityCategory
import com.arktools.xiao.domain.model.FacilityType
import com.arktools.xiao.ui.components.LegacyPageHeader
import com.arktools.xiao.ui.components.PixelButton
import com.arktools.xiao.ui.components.PixelButtonStyle
import com.arktools.xiao.ui.components.PixelNineSlice
import com.arktools.xiao.ui.theme.AccentGreen
import com.arktools.xiao.ui.theme.AccentOrange
import com.arktools.xiao.ui.theme.AccentRed
import com.arktools.xiao.ui.theme.BackgroundDark
import com.arktools.xiao.ui.theme.TextPrimaryDark
import com.arktools.xiao.ui.theme.TextSecondaryDark
import com.arktools.xiao.ui.utils.FacilityImageHelper
import com.arktools.xiao.R

@Composable
fun FacilityScreen(
    viewModel: FacilityViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        LegacyPageHeader("设施容量")
        SnackbarHost(hostState = snackbarHostState)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with maintenance info
            item {
                FacilityHeader(
                    facilityCount = state.facilities.size,
                    maxFacilities = state.maxFacilities,
                    totalMaintenance = state.totalMaintenance,
                    cash = state.cash
                )
            }
            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    PixelNineSlice(res = R.drawable.card_bg, slice = 48, modifier = Modifier.matchParentSize())
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("全校容量总览", fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                        Text("教室学位 ${state.classroomSeats} · 在校 ${state.studentCount} 人", color = TextSecondaryDark)
                        Text("宿舍床位 ${state.dormBeds} · 食堂餐位 ${state.canteenSeats}", color = TextSecondaryDark)
                        Text(
                            "建造和搬楼只在校园地图里做。这里只看容量和维修。",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondaryDark
                        )
                    }
                }
            }

            // 一键维修按钮
            val needRepairCount = state.facilities.count { it.condition < 95f }
            if (needRepairCount > 0) {
                item {
                    val totalRepairCost = state.facilities
                        .filter { it.condition < 95f }
                        .sumOf { it.type.baseMaintenance * 2 }
                    PixelButton(
                        text = "一键维修 ${needRepairCount} 项（${String.format("%.1f", totalRepairCost)}万）",
                        onClick = { viewModel.repairAllFacilities() },
                        enabled = state.cash >= totalRepairCost,
                        modifier = Modifier.fillMaxWidth(),
                        style = PixelButtonStyle.PRIMARY,
                        icon = Icons.Default.Build
                    )
                }
            }

            // Bonus summary card (only when there are operational facilities)
            if (state.facilities.any { it.isOperational }) {
                item {
                    FacilityBonusSummaryCard(bonuses = state.bonuses)
                }
            }

            // Owned facilities by category
            val byCategory = state.facilities.groupBy { it.type.category }
            FacilityCategory.values().forEach { category ->
                val facilities = byCategory[category]
                if (!facilities.isNullOrEmpty()) {
                    item {
                        Text(
                            text = category.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(
                        facilities.size,
                        key = { index -> facilities[index].id }
                    ) { index ->
                        val facility = facilities[index]
                        OwnedFacilityCard(
                            facility = facility,
                            onUpgrade = { viewModel.upgradeFacility(facility.id) },
                            onRepair = { viewModel.repairFacility(facility.id) },
                            cash = state.cash
                        )
                    }
                }
            }

            // 设施数量已达上限提示
            if (state.isAtCapacity) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        PixelNineSlice(res = R.drawable.card_bg, slice = 48, modifier = Modifier.matchParentSize())
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Build,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "设施位已满（${state.maxFacilities}/${state.maxFacilities}）",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "请到「社会」页面升级大学校园以解锁更多设施位",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FacilityHeader(
    facilityCount: Int,
    maxFacilities: Int,
    totalMaintenance: Double,
    cash: Double
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        PixelNineSlice(res = R.drawable.card_bg, slice = 48, modifier = Modifier.matchParentSize())
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.School,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "校园设施",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "已建设 $facilityCount / $maxFacilities 项设施",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (facilityCount >= maxFacilities)
                            AccentOrange
                        else
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            // 容量进度条
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { facilityCount.toFloat() / maxFacilities.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = if (facilityCount >= maxFacilities) AccentOrange else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "月维护费: ${String.format("%.1f", totalMaintenance)} 万",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentOrange
                )
                Text(
                    text = "可用资金: ${String.format("%.1f", cash)} 万",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (cash > 0) AccentGreen else AccentRed
                )
            }
        }
    }
}

@Composable
private fun OwnedFacilityCard(
    facility: Facility,
    onUpgrade: () -> Unit,
    onRepair: () -> Unit,
    cash: Double
) {
    val conditionColor = when {
        facility.condition >= 70f -> AccentGreen
        facility.condition >= 40f -> AccentOrange
        else -> AccentRed
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        PixelNineSlice(res = R.drawable.card_bg, slice = 48, modifier = Modifier.matchParentSize())
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Image(
                painter = painterResource(id = FacilityImageHelper.getImageResId(facility.type)),
                contentDescription = facility.type.displayName,
                modifier = Modifier.size(64.dp),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = facility.type.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Lv.${facility.level}/${facility.type.maxLevel}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = facility.type.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!facility.isOperational) {
                    Text(
                        text = "已停用",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Condition bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (facility.isConstructing) {
                        "施工中：还需 ${facility.constructionDaysLeft} 天"
                    } else {
                        "状态"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                LinearProgressIndicator(
                    progress = { facility.condition / 100f },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp),
                    color = conditionColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${facility.condition.toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = conditionColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val upgradeCost = FacilityBonusCalculator.getUpgradeCost(facility)
                val canUpgrade = facility.level < facility.type.maxLevel && cash >= upgradeCost

                if (facility.level < facility.type.maxLevel) {
                    PixelButton(
                        text = "升级 ${String.format("%.1f", upgradeCost)}万",
                        onClick = onUpgrade,
                        enabled = canUpgrade,
                        modifier = Modifier.weight(1f),
                        style = PixelButtonStyle.PRIMARY,
                        icon = Icons.Default.Upgrade,
                        height = 40.dp
                    )
                }

                if (facility.condition < 95f) {
                    val repairCost = facility.type.baseMaintenance * 2
                    PixelButton(
                        text = "维修 ${String.format("%.1f", repairCost)}万",
                        onClick = onRepair,
                        enabled = cash >= repairCost,
                        modifier = Modifier.weight(1f),
                        style = PixelButtonStyle.SECONDARY,
                        icon = Icons.Default.Build,
                        height = 40.dp
                    )
                }
            }

            Text(
                text = "拆除和搬移请在校园地图中操作，避免地图位置与设施状态脱节。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Per-facility bonus labels
            val bonusLabels = getFacilityBonusLabels(facility)
            if (bonusLabels.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    bonusLabels.forEach { (label, color) ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .background(color.copy(alpha = 0.16f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Maintenance info
            Text(
                text = "月维护: ${String.format("%.1f", facility.maintenanceCost)} 万",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        } // Column end
        } // Row end
    }
}

/**
 * Returns a list of bonus labels with their colors for a specific facility.
 */
private fun getFacilityBonusLabels(facility: Facility): List<Pair<String, Color>> {
    if (!facility.isOperational) return emptyList()
    val lv = facility.level
    val result = mutableListOf<Pair<String, Color>>()
    when (facility.type) {
        FacilityType.CLASSROOM -> {
            result.add("招生+${5 * lv}%" to Color(0xFF2196F3))
        }
        FacilityType.MULTIMEDIA_ROOM -> {
            result.add("教学+${10 * lv}%" to Color(0xFF4CAF50))
        }
        FacilityType.LABORATORY -> {
            result.add("教学+${5 * lv}%" to Color(0xFF4CAF50))
            result.add("理科+${15 * lv}%" to Color(0xFF3F51B5))
        }
        FacilityType.COMPUTER_LAB -> {
            result.add("教学+${5 * lv}%" to Color(0xFF4CAF50))
            result.add("编程+${20 * lv}%" to Color(0xFF607D8B))
        }
        FacilityType.ART_STUDIO -> {
            result.add("教学+${5 * lv}%" to Color(0xFF4CAF50))
            result.add("艺术+${15 * lv}%" to Color(0xFFFF5722))
        }
        FacilityType.LIBRARY -> {
            result.add("研发+${10 * lv}%" to Color(0xFF9C27B0))
        }
        FacilityType.SPORTS_FIELD -> {
            result.add("招生+${5 * lv}%" to Color(0xFF2196F3))
        }
        FacilityType.CANTEEN -> {
            result.add("疲劳-${15 * lv}%" to Color(0xFFFF9800))
        }
        FacilityType.DORMITORY -> {
            result.add("招生+${20 * lv}%" to Color(0xFF2196F3))
        }
        FacilityType.CONFERENCE_CENTER -> {
            result.add("声誉+${8 * lv}%" to Color(0xFF1E96C8))
        }
        FacilityType.EMPLOYMENT_CENTER -> {
            result.add("去向质量+${6 * lv}%" to Color(0xFF2196F3))
        }
        FacilityType.INCUBATOR -> {
            result.add("创业深造+" to Color(0xFF2196F3))
        }
        FacilityType.INTERNATIONAL_CENTER -> {
            result.add("国际生收入+${10 * lv}%" to Color(0xFF1E96C8))
        }
        FacilityType.LOGISTICS_CENTER -> {
            result.add("维护费-${6 * lv}%" to Color(0xFF4CAF50))
        }
        FacilityType.AUDITORIUM -> {
            result.add("事件+${20 * lv}%" to Color(0xFF00BCD4))
            result.add("声誉+${5 * lv}%" to Color(0xFFFFD700))
        }
        FacilityType.GARDEN -> {
            result.add("忠诚衰减-${20 * lv}%" to Color(0xFFE91E63))
        }
        FacilityType.GATE -> {
            result.add("声誉+${10 * lv}%" to Color(0xFFFFD700))
        }
    }
    return result
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FacilityBonusSummaryCard(bonuses: FacilityBonusCalculator.FacilityBonuses) {
    val activeBonus = mutableListOf<Triple<String, String, Color>>()
    if (bonuses.teachingQualityBonus > 0f) activeBonus.add(Triple("教学质量", "+${(bonuses.teachingQualityBonus * 100).toInt()}%", Color(0xFF4CAF50)))
    if (bonuses.enrollmentBonus > 0f) activeBonus.add(Triple("招生吸引", "+${(bonuses.enrollmentBonus * 100).toInt()}%", Color(0xFF2196F3)))
    if (bonuses.researchBonus > 0f) activeBonus.add(Triple("研发效率", "+${(bonuses.researchBonus * 100).toInt()}%", Color(0xFF9C27B0)))
    if (bonuses.fatigueReduction > 0f) activeBonus.add(Triple("疲劳降低", "-${(bonuses.fatigueReduction * 100).toInt()}%", Color(0xFFFF9800)))
    if (bonuses.loyaltyDecayReduction > 0f) activeBonus.add(Triple("忠诚维护", "-${(bonuses.loyaltyDecayReduction * 100).toInt()}%衰减", Color(0xFFE91E63)))
    if (bonuses.reputationGrowthBonus > 0f) activeBonus.add(Triple("声誉增长", "+${(bonuses.reputationGrowthBonus * 100).toInt()}%", Color(0xFFFFD700)))
    if (bonuses.eventRewardBonus > 0f) activeBonus.add(Triple("事件奖励", "+${(bonuses.eventRewardBonus * 100).toInt()}%", Color(0xFF00BCD4)))
    if (bonuses.scienceBonus > 0f) activeBonus.add(Triple("理科加成", "+${(bonuses.scienceBonus * 100).toInt()}%", Color(0xFF3F51B5)))
    if (bonuses.programmingBonus > 0f) activeBonus.add(Triple("编程加成", "+${(bonuses.programmingBonus * 100).toInt()}%", Color(0xFF607D8B)))
    if (bonuses.artBonus > 0f) activeBonus.add(Triple("艺术加成", "+${(bonuses.artBonus * 100).toInt()}%", Color(0xFFFF5722)))

    if (activeBonus.isEmpty()) return

    Box(modifier = Modifier.fillMaxWidth()) {
        PixelNineSlice(res = R.drawable.card_bg, slice = 48, modifier = Modifier.matchParentSize())
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.TrendingUp,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "设施加成总览",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${activeBonus.size}项生效",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                activeBonus.forEach { (label, value, color) ->
                    BonusChip(label = label, value = value, color = color)
                }
            }
        }
    }
}

@Composable
private fun BonusChip(label: String, value: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.8f)
            )
        }
    }
}
