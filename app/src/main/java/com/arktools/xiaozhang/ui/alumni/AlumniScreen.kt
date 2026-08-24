package com.arktools.xiaozhang.ui.alumni

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arktools.xiaozhang.domain.alumni.*
import com.arktools.xiaozhang.domain.model.UniversityTier
import com.arktools.xiaozhang.domain.employment.*
import com.arktools.xiaozhang.ui.components.PixelIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlumniScreen(
    viewModel: AlumniViewModel = hiltViewModel()
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("校友网络", "升学就业", "毕业总结")

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
            0 -> AlumniNetworkContent(viewModel = viewModel)
            1 -> EmploymentContent(viewModel = viewModel)
            2 -> GraduationSummaryContent(viewModel = viewModel)
        }
    }
}

// ========== 校友网络 Tab 内容 ==========

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlumniNetworkContent(viewModel: AlumniViewModel) {
    val stats by viewModel.stats.collectAsState()
    val alumni by viewModel.alumni.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val networkLevel by viewModel.networkLevel.collectAsState()
    val industryConnections by viewModel.industryConnections.collectAsState()
    val lastActivityResult by viewModel.lastActivityResult.collectAsState()

    // 活动结果弹窗
    lastActivityResult?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissActivityResult() },
            title = { Text(result.type.displayName) },
            text = {
                Column {
                    Text(result.description)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (result.donationGained > 0) {
                        Text("💰 获得捐款: ¥${String.format("%.1f", result.donationGained / 10000)}万",
                            color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    }
                    if (result.reputationGained > 0) {
                        Text("⭐ 声誉+${result.reputationGained}",
                            color = Color(0xFF2196F3), fontWeight = FontWeight.Bold)
                    }
                    if (result.extraEffect.isNotEmpty()) {
                        Text("✨ ${result.extraEffect}",
                            color = Color(0xFFFF9800))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissActivityResult() }) {
                    Text("确定")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 网络等级 + 统计卡片
        item {
            NetworkLevelCard(stats, networkLevel, viewModel)
        }

        // 校友活动区（Lv3+ 解锁）
        if (networkLevel >= 3) {
            item {
                ActivitiesCard(viewModel, networkLevel)
            }
        }

        // 行业人脉（Lv2+ 解锁）
        if (networkLevel >= 2 && industryConnections.isNotEmpty()) {
            item {
                IndustryConnectionsCard(industryConnections, viewModel)
            }
        }

        // 职业筛选器
        item {
            CareerFilterChips(
                selectedFilter = selectedFilter,
                careerDistribution = stats.careerDistribution,
                onFilterSelected = { viewModel.setFilter(it) }
            )
        }

        // 校友列表
        val filteredAlumni = viewModel.getFilteredAlumni()
        if (filteredAlumni.isEmpty()) {
            item { EmptyAlumniState() }
        } else {
            // 杰出校友
            if (selectedFilter == null && stats.executiveCount > 0) {
                item {
                    Text("杰出校友", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                val topAlumni = viewModel.getTopAlumni()
                items(topAlumni, key = { "top_${it.id}" }) { alumnus ->
                    AlumniCard(alumnus, isHighlighted = true)
                }
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("全部校友 (${filteredAlumni.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            items(filteredAlumni, key = { it.id }) { alumnus ->
                AlumniCard(alumnus, isHighlighted = false)
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
private fun NetworkLevelCard(stats: AlumniStats, networkLevel: Int, viewModel: AlumniViewModel) {
    val (currentProgress, nextReq) = viewModel.getNetworkLevelProgress()

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("校友网络", color = Color.White,
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.2f)
                            ) {
                                Text("Lv.$networkLevel",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            AlumniNetwork.LEVEL_FEATURES.getOrElse(networkLevel - 1) { "" },
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${stats.totalAlumni}", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                        Text("校友总数", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 升级进度
                if (networkLevel < 6) {
                    Text("下一等级: ${currentProgress}/${nextReq} 名资深+校友",
                        color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (currentProgress.toFloat() / nextReq.coerceAtLeast(1)).coerceAtMost(1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                } else {
                    Text("🏆 校友网络已达到最高等级", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 统计行
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    AlumniMiniStat("高管", "${stats.executiveCount}", Color(0xFFFFB74D))
                    AlumniMiniStat("资深", "${stats.seniorCount}", Color(0xFFCE93D8))
                    AlumniMiniStat("招生加成", "+${String.format("%.0f", viewModel.getFilteredAlumni().size * 0.5f)}%", Color(0xFF81C784))
                    AlumniMiniStat("满意度", if (stats.averageSatisfaction > 0) "${stats.averageSatisfaction.toInt()}%" else "-", Color(0xFF90CAF9))
                }
            }
        }
    }
}

@Composable
private fun AlumniMiniStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
    }
}

@Composable
private fun ActivitiesCard(viewModel: AlumniViewModel, networkLevel: Int) {
    val cooldown = viewModel.getActivityCooldown()

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("校友活动", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                if (cooldown > 0) {
                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Text("冷却中 ${cooldown}个月",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            AlumniActivityType.entries.forEach { activity ->
                val canHost = viewModel.canHostActivity(activity)
                val cost = viewModel.getActivityCost(activity)
                val unlocked = when (activity) {
                    AlumniActivityType.REUNION -> networkLevel >= 3
                    AlumniActivityType.INDUSTRY_FORUM -> networkLevel >= 4
                    AlumniActivityType.FUNDRAISING_GALA -> networkLevel >= 5
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(activity.displayName, fontWeight = FontWeight.Medium,
                            color = if (unlocked) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Text(
                            if (unlocked) "${activity.description} · 费用¥${cost}万"
                            else "需要校友网络Lv${when(activity) { AlumniActivityType.REUNION->3; AlumniActivityType.INDUSTRY_FORUM->4; AlumniActivityType.FUNDRAISING_GALA->5 }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { viewModel.hostActivity(activity) },
                        enabled = canHost,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(if (!unlocked) "🔒" else "举办", fontSize = 12.sp)
                    }
                }
                if (activity != AlumniActivityType.entries.last()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                }
            }
        }
    }
}

@Composable
private fun IndustryConnectionsCard(
    connections: Map<CareerPath, Int>,
    viewModel: AlumniViewModel
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("行业人脉加成", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("校友从事的行业越多，对应方向的招生加成越高",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))

            val sortedConnections = connections.entries.sortedByDescending { it.value }.take(5)
            sortedConnections.forEach { (career, count) ->
                val bonus = viewModel.getIndustryBonus(career)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(career.icon, fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(career.displayName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("${count}人", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(12.dp))
                    if (bonus > 0) {
                        Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF4CAF50).copy(alpha = 0.1f)) {
                            Text("+${(bonus * 100).toInt()}%",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    } else {
                        Text("未激活", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CareerFilterChips(
    selectedFilter: CareerPath?,
    careerDistribution: Map<CareerPath, Int>,
    onFilterSelected: (CareerPath?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedFilter == null,
            onClick = { onFilterSelected(null) },
            label = { Text("全部") },
            leadingIcon = if (selectedFilter == null) {
                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
            } else null
        )
        CareerPath.entries.forEach { career ->
            val count = careerDistribution[career] ?: 0
            if (count > 0) {
                FilterChip(
                    selected = selectedFilter == career,
                    onClick = { onFilterSelected(career) },
                    label = { Text("${career.icon} $count") },
                    leadingIcon = if (selectedFilter == career) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun AlumniCard(alumnus: Alumnus, isHighlighted: Boolean) {
    val cardColor = if (isHighlighted) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(colors = getCareerGradient(alumnus.careerLevel))),
                contentAlignment = Alignment.Center
            ) {
                PixelIcon(emoji = alumnus.career.icon, size = 22.dp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(alumnus.name, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.width(8.dp))
                    CareerLevelBadge(alumnus.careerLevel)
                }
                Spacer(modifier = Modifier.height(2.dp))
                // 具体职位头衔（职业+等级）—— 增强代入感
                Text(
                    getAlumnusJobTitle(alumnus.career, alumnus.careerLevel),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                // 毕业院校档次 + 毕业时长
                Row(verticalAlignment = Alignment.CenterVertically) {
                    alumnus.universityTier?.let { tier ->
                        Surface(shape = RoundedCornerShape(4.dp), color = getUniversityTierColor(tier).copy(alpha = 0.15f)) {
                            Text("🎓${tier.displayName}", modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                fontSize = 11.sp, color = getUniversityTierColor(tier), fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("毕业${formatGraduationDuration(alumnus.monthsSinceGraduation)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⭐${alumnus.graduationRating.toInt()}/5", fontSize = 12.sp, color = Color(0xFFFFB800))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("满意度 ${alumnus.satisfaction.toInt()}%",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** 根据职业方向与职业等级生成具体职位头衔，增强校友的真实感与成就感 */
private fun getAlumnusJobTitle(career: CareerPath, level: CareerLevel): String {
    val titles: List<String> = when (career) {
        CareerPath.TECH -> listOf("实习工程师", "软件工程师", "高级工程师", "技术总监", "科技公司CTO")
        CareerPath.FINANCE -> listOf("金融实习生", "理财顾问", "投资经理", "金融机构总监", "投行董事总经理")
        CareerPath.EDUCATION -> listOf("助教", "中学教师", "学科带头人", "学校副校长", "教育集团总裁")
        CareerPath.MEDICAL -> listOf("规培医师", "住院医师", "主治医师", "科室主任", "三甲医院院长")
        CareerPath.LAW -> listOf("律师助理", "执业律师", "资深律师", "律所合伙人", "顶级律所首席合伙人")
        CareerPath.ARTS -> listOf("文艺新人", "签约艺人", "知名艺术家", "艺术总监", "殿堂级艺术大师")
        CareerPath.BUSINESS -> listOf("创业新手", "个体经营者", "公司创始人", "企业董事", "上市公司董事长")
        CareerPath.GOVERNMENT -> listOf("基层公务员", "科员", "科长", "处级领导", "厅局级领导")
        CareerPath.RESEARCH -> listOf("研究助理", "助理研究员", "副研究员", "研究员", "院士级首席科学家")
        CareerPath.SPORTS -> listOf("体育苗子", "职业运动员", "国家队队员", "金牌教练", "国家队总教练")
    }
    val idx = level.ordinal.coerceIn(0, titles.size - 1)
    return "${career.icon} ${titles[idx]}"
}

/** 毕业时长格式化：超过12个月显示"X年Y个月" */
private fun formatGraduationDuration(months: Int): String {
    return if (months >= 12) {
        val years = months / 12
        val rem = months % 12
        if (rem == 0) "${years}年" else "${years}年${rem}个月"
    } else "${months}个月"
}

private fun getUniversityTierColor(tier: UniversityTier): Color = when (tier) {
    UniversityTier.QINGBEI -> Color(0xFFD32F2F)
    UniversityTier.TOP_985, UniversityTier.NORMAL_985 -> Color(0xFFE65100)
    UniversityTier.TOP_211, UniversityTier.NORMAL_211 -> Color(0xFF6A1B9A)
    UniversityTier.FIRST_TIER -> Color(0xFF1565C0)
    UniversityTier.SECOND_TIER -> Color(0xFF2E7D32)
    UniversityTier.JUNIOR_COLLEGE -> Color(0xFF757575)
    UniversityTier.NONE -> Color(0xFF9E9E9E)
}

@Composable
private fun CareerLevelBadge(level: CareerLevel) {
    val (text, color) = when (level) {
        CareerLevel.ENTRY -> "入门" to MaterialTheme.colorScheme.outline
        CareerLevel.JUNIOR -> "初级" to Color(0xFF4CAF50)
        CareerLevel.MIDDLE -> "中级" to Color(0xFF2196F3)
        CareerLevel.SENIOR -> "资深" to Color(0xFF9C27B0)
        CareerLevel.EXECUTIVE -> "高管" to Color(0xFFFF9800)
    }
    Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.15f)) {
        Text(text, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyAlumniState() {
    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PixelIcon(emoji = "🎓", size = 48.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Text("暂无校友", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Text("学生毕业后将自动加入校友网络",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
}

private fun getCareerGradient(level: CareerLevel): List<Color> {
    return when (level) {
        CareerLevel.ENTRY -> listOf(Color(0xFFE0E0E0), Color(0xFFBDBDBD))
        CareerLevel.JUNIOR -> listOf(Color(0xFFA5D6A7), Color(0xFF66BB6A))
        CareerLevel.MIDDLE -> listOf(Color(0xFF90CAF9), Color(0xFF42A5F5))
        CareerLevel.SENIOR -> listOf(Color(0xFFCE93D8), Color(0xFFAB47BC))
        CareerLevel.EXECUTIVE -> listOf(Color(0xFFFFCC80), Color(0xFFFFA726))
    }
}

// ========== 毕业总结 Tab 内容 ==========

@Composable
private fun GraduationSummaryContent(viewModel: AlumniViewModel) {
    val summaries by viewModel.graduationSummaries.collectAsState()

    if (summaries.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PixelIcon(emoji = "\uD83D\uDCCA", size = 48.dp)
                Spacer(modifier = Modifier.height(16.dp))
                Text("暂无毕业数据", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Text("每年高考放榜后将自动生成毕业总结",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 历届总览对比
            item {
                GraduationTrendCard(summaries)
            }

            // 每一届的详细卡片
            items(summaries, key = { it.year }) { summary ->
                GraduationBatchCard(summary)
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun GraduationTrendCard(summaries: List<com.arktools.xiaozhang.domain.alumni.GraduationBatchSummary>) {
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
                        colors = listOf(Color(0xFF6A1B9A), Color(0xFFAB47BC))
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(20.dp)
        ) {
            Column {
                Text("历届高考数据", color = Color.White,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("共 ${summaries.size} 届毕业生", color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(16.dp))

                val totalGrads = summaries.sumOf { it.totalStudents }
                val avgBengke = if (summaries.isNotEmpty()) summaries.map { it.bengkeRate }.average().toFloat() else 0f
                val total985 = summaries.sumOf { it.key985Count }
                val totalQingbei = summaries.sumOf { it.qingbeiCount }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    AlumniMiniStat("总毕业生", "$totalGrads", Color(0xFFBBDEFB))
                    AlumniMiniStat("平均本科率", "${avgBengke.toInt()}%", Color(0xFFA5D6A7))
                    AlumniMiniStat("累计985", "$total985", Color(0xFFCE93D8))
                    AlumniMiniStat("累计清北", "$totalQingbei", Color(0xFFFFCC80))
                }

                // 趋势：最近几届本科率
                if (summaries.size >= 2) {
                    Spacer(modifier = Modifier.height(12.dp))
                    val recent = summaries.take(5).reversed() // 从旧到新
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        recent.forEach { s ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val rateColor = when {
                                    s.bengkeRate >= 80f -> Color(0xFFA5D6A7)
                                    s.bengkeRate >= 50f -> Color(0xFFFFF59D)
                                    else -> Color(0xFFEF9A9A)
                                }
                                Text("${s.bengkeRate.toInt()}%", color = rateColor,
                                    fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("${s.year}届", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GraduationBatchCard(summary: com.arktools.xiaozhang.domain.alumni.GraduationBatchSummary) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("\uD83C\uDF93", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${summary.year}届毕业生", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        summary.bengkeRate >= 80f -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                        summary.bengkeRate >= 50f -> Color(0xFFFFC107).copy(alpha = 0.15f)
                        else -> Color(0xFFF44336).copy(alpha = 0.15f)
                    }
                ) {
                    Text(
                        "本科率 ${summary.bengkeRate.toInt()}%",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = when {
                            summary.bengkeRate >= 80f -> Color(0xFF2E7D32)
                            summary.bengkeRate >= 50f -> Color(0xFFF57F17)
                            else -> Color(0xFFC62828)
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 关键数据
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                GradStatItem("毕业人数", "${summary.totalStudents}")
                GradStatItem("平均分", "${summary.averageScore.toInt()}")
                GradStatItem("最高分", "${summary.highestScore.toInt()}")
                GradStatItem("985", "${summary.key985Count}人")
                if (summary.qingbeiCount > 0) {
                    GradStatItem("清北", "${summary.qingbeiCount}人")
                }
            }

            // 大学层次分布
            if (summary.universityDistribution.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(10.dp))
                Text("录取分布", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                val sorted = summary.universityDistribution.entries
                    .sortedByDescending { it.value }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sorted.forEach { (tier, count) ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text("$tier ${count}人",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // 优秀毕业生
            if (summary.topStudents.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(10.dp))
                Text("优秀毕业生 TOP${summary.topStudents.size}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                summary.topStudents.forEachIndexed { index, student ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val medal = when (index) { 0 -> "\uD83E\uDD47"; 1 -> "\uD83E\uDD48"; 2 -> "\uD83E\uDD49"; else -> "\u2003" }
                        Text(medal, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(student.name, modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("${student.score.toInt()}分", style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFF6F00), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(student.university ?: student.tierName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun GradStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ========== 就业市场 Tab 内容 ==========

@Composable
private fun EmploymentContent(viewModel: AlumniViewModel) {
    val state by viewModel.employmentState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 顶部统计概览
        item {
            EmploymentOverviewCard(state.stats)
        }

        // 就业率进度
        item {
            EmploymentRateCard(state.stats)
        }

        // 合作企业
        item {
            PartnerEmployersCard(state.employers)
        }

        // 行业分布
        if (state.stats.topIndustries.isNotEmpty()) {
            item {
                IndustryDistributionCard(state.stats.topIndustries)
            }
        }

        // 最近事件
        if (state.recentEvents.isNotEmpty()) {
            item {
                Text(
                    "近期动态",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(state.recentEvents) { event ->
                RecentEventCard(event)
            }
        }

        // 毕业生列表
        val graduatesForDisplay = viewModel.getGraduatesForDisplay()
        if (graduatesForDisplay.isNotEmpty()) {
            item {
                Text(
                    "毕业生跟踪 (${graduatesForDisplay.size}人)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(graduatesForDisplay.sortedByDescending { it.graduateYear * 12 + it.graduateMonth }.take(20)) { grad ->
                GraduateCard(grad)
            }
        }

        // 空状态
        if (graduatesForDisplay.isEmpty()) {
            item {
                EmptyEmploymentState()
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun EmploymentOverviewCard(stats: EmploymentStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF1565C0), Color(0xFF0D47A1))
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(20.dp)
        ) {
            Column {
                Text(
                    "毕业生去向总览",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    EmpStatColumn("总毕业生", "${stats.totalGraduates}", Color(0xFFBBDEFB))
                    EmpStatColumn("大学在读", "${stats.inUniversityCount}", Color(0xFF80DEEA))
                    EmpStatColumn("已就业", "${stats.employedCount}", Color(0xFFA5D6A7))
                    EmpStatColumn("深造/读研", "${stats.furtherStudyCount}", Color(0xFFCE93D8))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    EmpStatColumn("创业", "${stats.selfEmployedCount}", Color(0xFFFFCC80))
                    EmpStatColumn("未升学", "${stats.notAdmittedCount}", Color(0xFFEF9A9A))
                    EmpStatColumn("均薪", "¥${stats.averageSalary}", Color(0xFFFFF59D))
                    EmpStatColumn("反馈", String.format("%.1f", stats.averageFeedback), Color(0xFFB0BEC5))
                }
            }
        }
    }
}

@Composable
private fun EmpStatColumn(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            color = color,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun EmploymentRateCard(stats: EmploymentStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "升学率",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${(stats.universityRate * 100).toInt()}%",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        stats.universityRate >= 0.9f -> Color(0xFF2E7D32)
                        stats.universityRate >= 0.7f -> Color(0xFFF57F17)
                        else -> Color(0xFFC62828)
                    }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { stats.universityRate.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp)),
                color = when {
                    stats.universityRate >= 0.9f -> Color(0xFF4CAF50)
                    stats.universityRate >= 0.7f -> Color(0xFFFFC107)
                    else -> Color(0xFFF44336)
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "升学率 = 被大学录取人数 / 总毕业生人数（含大学在读、已就业、深造）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (stats.inUniversityCount > 0) {
                Text(
                    "📖 当前${stats.inUniversityCount}人在大学就读中，毕业后将进入就业市场",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val bonus = when {
                stats.employmentRate >= 0.95f -> "+8 招生加成"
                stats.employmentRate >= 0.90f -> "+5 招生加成"
                stats.employmentRate >= 0.80f -> "+3 招生加成"
                stats.employmentRate >= 0.70f -> "+1 招生加成"
                else -> "暂无招生加成"
            }
            Text(
                "招生影响：$bonus",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF1565C0)
            )
        }
    }
}

@Composable
private fun PartnerEmployersCard(employers: List<Employer>) {
    val partners = employers.filter { it.partnershipLevel > 0 }
        .sortedByDescending { it.partnershipLevel }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "合作企业 (${partners.size}家)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (partners.isEmpty()) {
                Text(
                    "暂无合作企业，培养更多优秀毕业生以吸引企业合作",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                partners.take(8).forEach { employer ->
                    EmployerRow(employer)
                    if (employer != partners.last()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmployerRow(employer: Employer) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    Color(0xFFE3F2FD),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            PixelIcon(emoji = employer.industry.icon, size = 16.dp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                employer.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "${employer.tier.displayName} · ${employer.industry.displayName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${employer.partnershipLevel}星",
                fontSize = 12.sp
            )
            Text(
                "录用${employer.hiredCount}人",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun IndustryDistributionCard(topIndustries: List<Pair<Industry, Int>>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "行业分布 TOP5",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            val maxCount = topIndustries.maxOfOrNull { it.second } ?: 1

            topIndustries.forEach { (industry, count) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        industry.displayName,
                        modifier = Modifier.width(120.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                    LinearProgressIndicator(
                        progress = { count.toFloat() / maxCount },
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF42A5F5),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "${count}人",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(36.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentEventCard(event: EmploymentEvent) {
    val (icon, title, description) = when (event) {
        is EmploymentEvent.UniversityGraduation -> Triple(
            "🎓",
            "${event.studentName} 大学毕业",
            "${event.universityTier.displayName} · ${event.industry.displayName} · ${event.salaryTier.displayName}"
        )
        is EmploymentEvent.FeedbackReport -> Triple(
            "📝",
            "升学反馈报告",
            "平均评分 ${String.format("%.1f", event.averageScore)}，招生加成 +${event.enrollmentBonus}"
        )
        is EmploymentEvent.AlumniSuccess -> Triple(
            "🌟",
            "${event.studentName} 成就达成",
            "${event.achievement}，声誉 +${event.reputationBonus}"
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PixelIcon(emoji = icon, size = 24.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GraduateCard(graduate: GraduateEmployment) {
    val statusColor = when (graduate.status) {
        EmploymentStatus.EMPLOYED -> Color(0xFF4CAF50)
        EmploymentStatus.SELF_EMPLOYED -> Color(0xFFFF9800)
        EmploymentStatus.FURTHER_STUDY -> Color(0xFF9C27B0)
        EmploymentStatus.IN_UNIVERSITY -> Color(0xFF3F51B5)
        EmploymentStatus.SEEKING -> Color(0xFF2196F3)
        EmploymentStatus.UNEMPLOYED -> Color(0xFFF44336)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(statusColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        graduate.studentName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "GPA ${String.format("%.1f", graduate.gpa)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        graduate.status.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor
                    )
                    if (graduate.employer != null) {
                        Text(
                            " · ${graduate.employer}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    graduate.salaryTier?.let { tier ->
                        Text(
                            " · ${tier.displayName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Text(
                "${graduate.graduateYear}届",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyEmploymentState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PixelIcon(emoji = "💼", size = 48.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "暂无毕业生数据",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "当学生毕业后，将自动进入就业市场追踪系统",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
