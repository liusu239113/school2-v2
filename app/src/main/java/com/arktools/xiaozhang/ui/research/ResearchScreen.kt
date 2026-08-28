package com.arktools.xiaozhang.ui.research

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.arktools.xiaozhang.domain.model.BonusType
import com.arktools.xiaozhang.domain.model.MethodCategory
import com.arktools.xiaozhang.domain.model.TeachingMethod
import com.arktools.xiaozhang.ui.theme.AccentGreen
import com.arktools.xiaozhang.ui.theme.AccentOrange
import com.arktools.xiaozhang.ui.theme.AccentRed
import com.arktools.xiaozhang.ui.animation.cardTapAnimation
import com.arktools.xiaozhang.ui.components.PixelButton
import com.arktools.xiaozhang.ui.components.PixelButtonStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResearchScreen(
    viewModel: ResearchViewModel = hiltViewModel()
) {
    val methods by viewModel.methods.collectAsState()
    val selectedMethod by viewModel.selectedMethod.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val bonusSummary by viewModel.bonusSummary.collectAsState()
    val recentlyUnlocked by viewModel.recentlyUnlocked.collectAsState()
    val unlockError by viewModel.unlockError.collectAsState()

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
                    text = "科研系统",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                val unlockedCount = methods.count { it.isUnlocked }
                Text(
                    text = "$unlockedCount/${methods.size} 已解锁",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (methods.isEmpty() && selectedCategory == null) {
                EmptyResearchState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Category filter chips
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedCategory == null,
                                onClick = { viewModel.selectCategory(null) },
                                label = { Text("全部") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                            MethodCategory.entries.forEach { category ->
                                FilterChip(
                                    selected = selectedCategory == category,
                                    onClick = {
                                        viewModel.selectCategory(
                                            if (selectedCategory == category) null else category
                                        )
                                    },
                                    label = { Text(category.displayName) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = getCategoryColor(category).copy(alpha = 0.2f)
                                    )
                                )
                            }
                        }
                    }

                    // Info card - 科研引导
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "如何使用科研？",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "① 选择下方教学方法卡片，点击查看详情\n" +
                                           "② 确认资金充足后点击「投入研究」按钮\n" +
                                           "③ 研究将在倒计时结束后生效，永久提升对应属性\n\n" +
                                           "效果说明：已解锁的研究项目会永久加成你的大学——" +
                                           "提升教学质量、加快招生速度、增加收入或降低运营成本。\n\n" +
                                           "提示：低费用方法无前置条件，高级方法需先解锁一定数量的基础方法。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.3
                                )
                            }
                        }
                    }

                    // Progress summary
                    item {
                        ResearchProgressBar(methods = methods)
                    }

                    // 科研课题链
                    item {
                        ResearchChainSection(viewModel)
                    }

                    // Bonus summary panel - show all active bonuses
                    item {
                        BonusSummaryPanel(bonusSummary = bonusSummary)
                    }

                    if (methods.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "该分类暂无科研项目",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(methods) { method ->
                            MethodCard(
                                method = method,
                                onClick = { viewModel.selectMethod(method) }
                            )
                        }
                    }
                }
            }
        }
    }

    selectedMethod?.let { method ->
        MethodDetailDialog(
            method = method,
            unlockError = unlockError,
            onDismiss = {
                viewModel.clearSelectedMethod()
                viewModel.clearUnlockError()
            },
            onUnlock = { viewModel.unlockMethod(method.id) }
        )
    }

    // Unlock success feedback dialog
    recentlyUnlocked?.let { method ->
        UnlockSuccessDialog(
            method = method,
            onDismiss = { viewModel.clearRecentlyUnlocked() }
        )
    }
}

@Composable
private fun ResearchProgressBar(methods: List<TeachingMethod>) {
    val total = methods.size
    val unlocked = methods.count { it.isUnlocked }
    val progress = if (total > 0) unlocked.toFloat() / total else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "研究进度",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "$unlocked / $total",
                    style = MaterialTheme.typography.labelMedium,
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
        }
    }
}

@Composable
private fun MethodCard(
    method: TeachingMethod,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .cardTapAnimation()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (method.isUnlocked)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (method.isUnlocked) Icons.Default.CheckCircle else Icons.Default.Lock,
                contentDescription = null,
                tint = if (method.isUnlocked) AccentGreen else AccentOrange,
                modifier = Modifier.padding(end = 16.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = method.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (method.isUnlocked) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "已解锁",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentGreen
                        )
                    } else if (method.isResearching) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "研究中·剩余${method.remainingResearchDays}天",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentOrange
                        )
                    }
                }
                Text(
                    text = method.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    MethodCategoryBadge(category = method.category)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${method.unlockYear}年解锁",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!method.isUnlocked) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${method.cost}万",
                        style = MaterialTheme.typography.titleSmall,
                        color = AccentOrange
                    )
                    Text(
                        text = "${method.researchDays}天",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MethodCategoryBadge(category: MethodCategory) {
    val color = getCategoryColor(category)

    Text(
        text = category.displayName,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
private fun getCategoryColor(category: MethodCategory): Color {
    return when (category) {
        MethodCategory.PEDAGOGY -> AccentGreen
        MethodCategory.CURRICULUM -> AccentOrange
        MethodCategory.TECHNOLOGY -> MaterialTheme.colorScheme.primary
        MethodCategory.PSYCHOLOGY -> AccentRed
        MethodCategory.MANAGEMENT -> MaterialTheme.colorScheme.secondary
    }
}

@Composable
private fun EmptyResearchState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Science,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "暂无科研项目",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MethodDetailDialog(
    method: TeachingMethod,
    unlockError: String? = null,
    onDismiss: () -> Unit,
    onUnlock: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.dialog_bg),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.matchParentSize()
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                // Title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (method.isUnlocked) Icons.Default.CheckCircle else Icons.Default.Science,
                        contentDescription = null,
                        tint = if (method.isUnlocked) AccentGreen else AccentOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = method.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = method.description,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Bonus effect highlight card
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.card_bg),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.matchParentSize()
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = method.bonusType.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = when {
                                    method.isUnlocked -> "生效中"
                                    method.isResearching -> "研究中·剩余${method.remainingResearchDays}天"
                                    else -> "待研究"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (method.isUnlocked) AccentGreen else AccentOrange
                            )
                        }
                        Text(
                            text = "+${(method.bonusValue * 100).toInt()}%",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (method.isUnlocked) AccentGreen else AccentOrange
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                StatRow("类别", method.category.displayName)
                StatRow("解锁年份", "${method.unlockYear}")
                if (!method.isUnlocked) {
                    StatRow("研究成本", "${method.cost}万")
                    StatRow(
                        if (method.isResearching) "剩余天数" else "研究天数",
                        "${if (method.isResearching) method.remainingResearchDays else method.researchDays}天"
                    )
                }

                // Error message
                unlockError?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentRed,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!method.isUnlocked) {
                        PixelButton(
                            text = "关闭",
                            style = PixelButtonStyle.CANCEL,
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        )
                        if (!method.isResearching) {
                            PixelButton(
                                text = "投入研究 ${method.cost}万",
                                style = PixelButtonStyle.CONFIRM,
                                onClick = onUnlock,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            PixelButton(
                                text = "研究中 ${method.remainingResearchDays}天",
                                style = PixelButtonStyle.CONFIRM,
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        PixelButton(
                            text = "关闭",
                            style = PixelButtonStyle.CANCEL,
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun BonusSummaryPanel(bonusSummary: BonusSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "当前加成效果",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "${bonusSummary.totalBonusCount}/6 类生效",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentGreen
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (bonusSummary.totalBonusCount == 0) {
                Text(
                    text = "尚未解锁任何科研项目，解锁后这里会显示加成效果",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // Grid of bonus items (2 columns)
            val bonusList = listOf(
                Triple("教学质量", bonusSummary.teachingQuality, AccentGreen),
                Triple("备课速度", bonusSummary.researchSpeed, MaterialTheme.colorScheme.primary),
                Triple("招生加成", bonusSummary.enrollment, AccentOrange),
                Triple("收入加成", bonusSummary.revenue, Color(0xFFFFD700)),
                Triple("教师忠诚", bonusSummary.teacherLoyalty, AccentRed),
                Triple("成本降低", bonusSummary.costReduction, MaterialTheme.colorScheme.secondary)
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                bonusList.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { (label, value, color) ->
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = if (value > 0f) "+${(value * 100).toInt()}%" else "—",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (value > 0f) FontWeight.Bold else FontWeight.Normal,
                                    color = if (value > 0f) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnlockSuccessDialog(
    method: TeachingMethod,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(0.85f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = AccentGreen,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "研究已启动",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = method.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Effect highlight
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = AccentGreen.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = method.bonusType.displayName,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "+${(method.bonusValue * 100).toInt()}%",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = AccentGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "预计${method.researchDays}天后完成，倒计时结束才会正式启用加成。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))
                PixelButton(
                    text = "太好了！",
                    style = PixelButtonStyle.CONFIRM,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDismiss
                )
            }
        }
    }
}

// ===== 科研课题链 =====

@Composable
private fun ResearchChainSection(viewModel: com.arktools.xiaozhang.ui.research.ResearchViewModel) {
    val chainUi by viewModel.chainUi.collectAsState()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = com.arktools.xiaozhang.R.drawable.ic_research_chain),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("科研课题链", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(8.dp))
                if (chainUi.qualityBonus > 0f) {
                    Text(
                        "教学质量永久+${(chainUi.qualityBonus * 100).toInt()}%",
                        fontSize = 11.sp,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            chainUi.message?.let { message ->
                Text(
                    text = message,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { viewModel.consumeChainMessage() }
                )
            }
            chainUi.definitions.forEach { def ->
                val program = chainUi.programs[def.id]
                val finished = chainUi.completedChains.contains(def.id)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(def.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(
                                def.description,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        when {
                            finished -> Text(
                                "已结题",
                                fontSize = 11.sp,
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold
                            )
                            program != null -> Text(
                                "进行中",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            else -> Text(
                                text = "启动",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { viewModel.startChain(def.id) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    if (program != null) {
                        val stage = def.stages[program.stageIndex]
                        Text(
                            "第${program.stageIndex + 1}/${def.stages.size}阶段「${stage.name}」 ${program.daysDone}/${stage.requiredDays}天 · 到账${stage.rewardCashWan.toInt()}万 + ${stage.rewardReputation}声誉",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LinearProgressIndicator(
                            progress = {
                                (program.daysDone.toFloat() / stage.requiredDays).coerceIn(0f, 1f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        )
                    } else {
                        val first = def.stages.first()
                        Text(
                            "首阶段「${first.name}」：启动${first.startFeeWan.toInt()}万 · 约${first.requiredDays}天",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
