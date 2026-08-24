package com.arktools.xiaozhang.ui.achievement

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arktools.xiaozhang.domain.achievement.Achievement
import com.arktools.xiaozhang.domain.achievement.AchievementCategory
import com.arktools.xiaozhang.domain.milestone.Milestone
import com.arktools.xiaozhang.domain.milestone.MilestoneCategory
import com.arktools.xiaozhang.domain.milestone.MilestoneStage
import com.arktools.xiaozhang.ui.theme.AccentGreen
import com.arktools.xiaozhang.ui.theme.AccentOrange
import com.arktools.xiaozhang.ui.utils.FormatUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AchievementScreen(
    viewModel: AchievementViewModel = hiltViewModel()
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("成就", "里程碑")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTabIndex) {
            0 -> AchievementContent(viewModel = viewModel)
            1 -> MilestoneContent(viewModel = viewModel)
        }
    }
}

// ========== 成就 Tab 内容 ==========

@Composable
private fun AchievementContent(viewModel: AchievementViewModel) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Header with progress
        AchievementHeader(
            unlockedCount = state.unlockedCount,
            totalCount = state.totalCount,
            progress = state.progress
        )

        // Category filter chips
        AchievementCategoryFilterRow(
            selectedCategory = state.selectedCategory,
            onCategorySelected = { viewModel.selectCategory(it) }
        )

        // Achievement list
        val filtered = viewModel.getFilteredAchievements()
        val unlocked = filtered.filter { it.unlocked }.sortedByDescending { it.unlockTime }
        val locked = filtered.filter { !it.unlocked }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (unlocked.isNotEmpty()) {
                item {
                    Text(
                        text = "已解锁 (${unlocked.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = AccentGreen,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(unlocked, key = { it.id }) { achievement ->
                    AchievementCard(achievement = achievement, isUnlocked = true)
                }
            }

            if (locked.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "未解锁 (${locked.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(locked, key = { it.id }) { achievement ->
                    AchievementCard(achievement = achievement, isUnlocked = false)
                }
            }
        }
    }
}

@Composable
private fun AchievementHeader(
    unlockedCount: Int,
    totalCount: Int,
    progress: Float
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(800),
        label = "progress"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF1A237E),
                            Color(0xFF3949AB)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "成就进度",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$unlockedCount / $totalCount 已完成",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color(0xFFFFD700),
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFFFFD700),
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
            }
        }
    }
}

@Composable
private fun AchievementCategoryFilterRow(
    selectedCategory: AchievementCategory?,
    onCategorySelected: (AchievementCategory?) -> Unit
) {
    val categories = listOf(
        null to "全部",
        AchievementCategory.MILESTONE to "里程碑",
        AchievementCategory.FINANCIAL to "财务",
        AchievementCategory.ACADEMIC to "学术",
        AchievementCategory.GROWTH to "成长",
        AchievementCategory.TEACHER to "师资",
        AchievementCategory.FACILITY to "设施",
        AchievementCategory.CHALLENGE to "挑战"
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { (category, label) ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onCategorySelected(category) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

@Composable
private fun AchievementCard(
    achievement: Achievement,
    isUnlocked: Boolean
) {
    val cardAlpha = if (isUnlocked) 1f else 0.6f
    val borderColor by animateColorAsState(
        targetValue = if (isUnlocked) getCategoryColor(achievement.category) else Color.Gray.copy(alpha = 0.3f),
        label = "border"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(cardAlpha),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isUnlocked) 4.dp else 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnlocked)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = if (isUnlocked) 1.5.dp else 0.5.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isUnlocked)
                            getCategoryColor(achievement.category).copy(alpha = 0.15f)
                        else
                            Color.Gray.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isUnlocked) getCategoryIcon(achievement.category) else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (isUnlocked) getCategoryColor(achievement.category) else Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = achievement.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (isUnlocked && achievement.unlockTime > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        .format(Date(achievement.unlockTime))
                    Text(
                        text = "解锁于 $dateStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentGreen.copy(alpha = 0.8f)
                    )
                }
            }

            // Category tag
            if (isUnlocked) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(getCategoryColor(achievement.category).copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = getCategoryLabel(achievement.category),
                        style = MaterialTheme.typography.labelSmall,
                        color = getCategoryColor(achievement.category),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

private fun getCategoryColor(category: AchievementCategory): Color {
    return when (category) {
        AchievementCategory.MILESTONE -> Color(0xFF9C27B0)
        AchievementCategory.FINANCIAL -> Color(0xFF4CAF50)
        AchievementCategory.ACADEMIC -> Color(0xFF2196F3)
        AchievementCategory.GROWTH -> Color(0xFFFF9800)
        AchievementCategory.CHALLENGE -> Color(0xFFF44336)
        AchievementCategory.TEACHER -> Color(0xFF00BCD4)
        AchievementCategory.FACILITY -> Color(0xFF795548)
        AchievementCategory.SECRET -> Color(0xFF607D8B)
    }
}

private fun getCategoryIcon(category: AchievementCategory): ImageVector {
    return when (category) {
        AchievementCategory.MILESTONE -> Icons.Default.Star
        AchievementCategory.FINANCIAL -> Icons.Default.AccountBalance
        AchievementCategory.ACADEMIC -> Icons.Default.School
        AchievementCategory.GROWTH -> Icons.Default.TrendingUp
        AchievementCategory.CHALLENGE -> Icons.Default.Bolt
        AchievementCategory.TEACHER -> Icons.Default.Face
        AchievementCategory.FACILITY -> Icons.Default.Home
        AchievementCategory.SECRET -> Icons.Default.Lock
    }
}

private fun getCategoryLabel(category: AchievementCategory): String {
    return when (category) {
        AchievementCategory.MILESTONE -> "里程碑"
        AchievementCategory.FINANCIAL -> "财务"
        AchievementCategory.ACADEMIC -> "学术"
        AchievementCategory.GROWTH -> "成长"
        AchievementCategory.CHALLENGE -> "挑战"
        AchievementCategory.TEACHER -> "师资"
        AchievementCategory.FACILITY -> "设施"
        AchievementCategory.SECRET -> "隐藏"
    }
}

// ========== 里程碑 Tab 内容 ==========

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MilestoneContent(viewModel: AchievementViewModel) {
    val milestones by viewModel.milestones.collectAsState()
    val selectedCategory by viewModel.milestoneSelectedCategory.collectAsState()
    val overallProgress by viewModel.overallProgress.collectAsState()

    val filteredMilestones = viewModel.getFilteredMilestones(milestones, selectedCategory)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 总进度卡片
        item {
            MilestoneOverallProgressCard(overallProgress = overallProgress, milestones = milestones)
        }

        // 分类过滤器
        item {
            MilestoneCategoryFilterRow(
                selectedCategory = selectedCategory,
                onCategorySelected = { viewModel.selectMilestoneCategory(it) }
            )
        }

        // 里程碑列表
        items(filteredMilestones, key = { it.id }) { milestone ->
            MilestoneCard(milestone = milestone)
        }
    }
}

@Composable
private fun MilestoneOverallProgressCard(overallProgress: Float, milestones: List<Milestone>) {
    val animatedProgress by animateFloatAsState(
        targetValue = overallProgress,
        animationSpec = tween(800),
        label = "overallProgress"
    )
    val completedCount = milestones.count { it.completed }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = AccentOrange
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "里程碑进度",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "已完成 $completedCount/${milestones.size} 项主线目标",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = AccentGreen,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${(animatedProgress * 100).toInt()}% 总完成度",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MilestoneCategoryFilterRow(
    selectedCategory: MilestoneCategory?,
    onCategorySelected: (MilestoneCategory?) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FilterChip(
            selected = selectedCategory == null,
            onClick = { onCategorySelected(null) },
            label = { Text("全部") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )
        )

        MilestoneCategory.entries.forEach { category ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onCategorySelected(category) },
                label = { Text("${category.emoji} ${category.displayName}") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
            )
        }
    }
}

@Composable
private fun MilestoneCard(milestone: Milestone) {
    val cardColor = if (milestone.completed) {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = milestone.category.emoji,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = milestone.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = milestone.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (milestone.completed) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "已完成",
                        tint = AccentGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 阶段进度节点
            StageProgressRow(milestone = milestone)

            // 当前阶段详情
            if (!milestone.completed) {
                milestone.currentStage?.let { stage ->
                    Spacer(modifier = Modifier.height(12.dp))
                    CurrentStageDetail(
                        stage = stage,
                        stageIndex = milestone.currentStageIndex,
                        totalStages = milestone.stages.size
                    )
                }
            }
        }
    }
}

@Composable
private fun StageProgressRow(milestone: Milestone) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        milestone.stages.forEachIndexed { index, stage ->
            if (index > 0) {
                // 连接线
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(
                            if (stage.achieved) AccentGreen
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }

            // 阶段节点
            val isCurrentStage = index == milestone.currentStageIndex && !milestone.completed
            Box(
                modifier = Modifier
                    .size(if (isCurrentStage) 28.dp else 22.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            stage.achieved -> AccentGreen
                            isCurrentStage -> AccentOrange
                            else -> MaterialTheme.colorScheme.outlineVariant
                        }
                    )
                    .then(
                        if (isCurrentStage) Modifier.border(2.dp, AccentOrange, CircleShape)
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    stage.achieved -> Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color.White
                    )
                    isCurrentStage -> Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    else -> Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentStageDetail(stage: MilestoneStage, stageIndex: Int, totalStages: Int) {
    val progress by animateFloatAsState(
        targetValue = stage.progress.coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "stageProgress"
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "第${stageIndex + 1}/${totalStages}阶段",
                    style = MaterialTheme.typography.labelMedium,
                    color = AccentOrange,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${stage.currentValue}/${stage.targetValue}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = AccentOrange,
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 奖励预览
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stage.rewardDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (stage.rewardCash > 0 || stage.rewardReputation > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (stage.rewardCash > 0) {
                        Text(
                            text = "+${FormatUtils.formatCash(stage.rewardCash)}万",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentGreen
                        )
                    }
                    if (stage.rewardReputation > 0) {
                        Text(
                            text = "+${stage.rewardReputation}声誉",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentOrange
                        )
                    }
                }
            }
        }
    }
}
