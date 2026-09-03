package com.arktools.xiao.ui.marketing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arktools.xiao.domain.model.MarketingCampaign
import com.arktools.xiao.domain.model.MarketingChannel
import com.arktools.xiao.ui.utils.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketingScreen(
    viewModel: MarketingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 显示提示信息
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("营销推广", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A237E),
                    titleContentColor = Color.White
                ),
                actions = {
                    Text(
                        "余额: ${FormatUtils.formatCash(state.schoolCash)}",
                        color = Color(0xFFFFD54F),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.showCreateDialog() },
                containerColor = Color(0xFF1A237E),
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("发起推广")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Summary stats header
            item {
                MarketingStatsHeader(
                    totalMonthlyCost = state.totalMonthlyCost,
                    enrollmentBoost = state.enrollmentBoost,
                    reputationBoost = state.reputationBoost,
                    activeCampaignCount = state.campaigns.count { it.isActive }
                )
            }

            // Active campaigns
            val activeCampaigns = state.campaigns.filter { it.isActive }
            if (activeCampaigns.isNotEmpty()) {
                item {
                    Text(
                        "进行中 (${activeCampaigns.size})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1A237E),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(activeCampaigns, key = { it.id }) { campaign ->
                    CampaignCard(
                        campaign = campaign,
                        courseName = "全校推广",
                        onStop = { viewModel.stopCampaign(campaign) }
                    )
                }
            }

            // Stopped campaigns
            val stoppedCampaigns = state.campaigns.filter { !it.isActive }
            if (stoppedCampaigns.isNotEmpty()) {
                item {
                    Text(
                        "已结束 (${stoppedCampaigns.size})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(stoppedCampaigns, key = { it.id }) { campaign ->
                    StoppedCampaignCard(
                        campaign = campaign,
                        courseName = "全校推广",
                        onRemove = { viewModel.removeCampaign(campaign) }
                    )
                }
            }

            // Empty state
            if (state.campaigns.isEmpty()) {
                item {
                    EmptyMarketingState()
                }
            }
        }
    }

    // Create campaign dialog
    if (state.showCreateDialog) {
        CreateCampaignDialog(
            state = state,
            onDismiss = { viewModel.dismissCreateDialog() },
            onSelectChannel = { viewModel.selectChannel(it) },
            onUpdateBudget = { viewModel.updateBudget(it) },
            onConfirm = { viewModel.createCampaign() }
        )
    }
}

@Composable
private fun MarketingStatsHeader(
    totalMonthlyCost: Double,
    enrollmentBoost: Double,
    reputationBoost: Double,
    activeCampaignCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF1A237E), Color(0xFF283593), Color(0xFF3949AB))
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    icon = Icons.Default.Campaign,
                    label = "活动数",
                    value = "$activeCampaignCount",
                    color = Color(0xFF64FFDA)
                )
                StatItem(
                    icon = Icons.Default.MonetizationOn,
                    label = "月费用",
                    value = FormatUtils.formatCash(totalMonthlyCost),
                    color = Color(0xFFFFD54F)
                )
                StatItem(
                    icon = Icons.Default.TrendingUp,
                    label = "招生加成",
                    value = "+${String.format("%.1f", enrollmentBoost)}%",
                    color = Color(0xFF69F0AE)
                )
                StatItem(
                    icon = Icons.Default.Star,
                    label = "声望加成",
                    value = "+${String.format("%.1f", reputationBoost)}",
                    color = Color(0xFFFF8A65)
                )
            }
        }
    }
}

@Composable
private fun StatItem(icon: ImageVector, label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
    }
}

@Composable
private fun CampaignCard(
    campaign: MarketingCampaign,
    courseName: String,
    onStop: () -> Unit
) {
    val channelColor = getChannelColor(campaign.channel)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Channel icon indicator
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(channelColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    getChannelIcon(campaign.channel),
                    contentDescription = null,
                    tint = channelColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    campaign.channel.displayName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    courseName,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "预算 ${FormatUtils.formatCash(campaign.budget)}/月",
                        fontSize = 11.sp,
                        color = channelColor,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "已运营 ${campaign.daysActive} 天",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "已花费 ${FormatUtils.formatCash(campaign.totalSpent)}",
                        fontSize = 11.sp,
                        color = Color(0xFFE57373)
                    )
                }
            }

            // Stop button
            FilledTonalButton(
                onClick = onStop,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color(0xFFFFEBEE),
                    contentColor = Color(0xFFE53935)
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("停止", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun StoppedCampaignCard(
    campaign: MarketingCampaign,
    courseName: String,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                getChannelIcon(campaign.channel),
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${campaign.channel.displayName} · $courseName",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
                Text(
                    "运营 ${campaign.daysActive} 天 · 花费 ${FormatUtils.formatCash(campaign.totalSpent)}",
                    fontSize = 11.sp,
                    color = Color.Gray.copy(alpha = 0.7f)
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "删除", tint = Color.Gray, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun EmptyMarketingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Campaign,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color(0xFF1A237E).copy(alpha = 0.3f)
        )
        Spacer(Modifier.height(16.dp))
        Text("还没有营销活动", fontSize = 16.sp, color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        Text(
            "通过营销推广提升招生率和学校声望",
            fontSize = 13.sp,
            color = Color.Gray.copy(alpha = 0.7f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateCampaignDialog(
    state: MarketingUiState,
    onDismiss: () -> Unit,
    onSelectChannel: (MarketingChannel) -> Unit,
    onUpdateBudget: (Double) -> Unit,
    onConfirm: () -> Unit
) {
    val channel = state.selectedChannel
    val canConfirm = channel != null &&
            state.budgetInput >= (channel?.minBudget ?: 0.0) &&
            state.budgetInput <= (channel?.maxBudget ?: 0.0) &&
            state.budgetInput <= state.schoolCash

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "发起营销推广",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // Step 1: Select channel
            Text("选择推广渠道", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(MarketingChannel.entries.toList()) { ch ->
                    val isSelected = state.selectedChannel == ch
                    val isAlreadyActive = state.campaigns.any { it.channel == ch && it.isActive }
                    val bgColor by animateColorAsState(
                        when {
                            isAlreadyActive -> Color.Gray.copy(alpha = 0.1f)
                            isSelected -> getChannelColor(ch).copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        label = "channelBg"
                    )
                    val borderColor by animateColorAsState(
                        when {
                            isAlreadyActive -> Color.Gray.copy(alpha = 0.3f)
                            isSelected -> getChannelColor(ch)
                            else -> Color.Transparent
                        },
                        label = "channelBorder"
                    )

                    Card(
                        modifier = Modifier
                            .width(100.dp)
                            .then(
                                if (!isAlreadyActive) Modifier.clickable { onSelectChannel(ch) }
                                else Modifier
                            )
                            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = bgColor)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                getChannelIcon(ch),
                                contentDescription = null,
                                tint = if (isAlreadyActive) Color.Gray else getChannelColor(ch),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                ch.displayName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isAlreadyActive) Color.Gray else Color.Unspecified
                            )
                            Text(
                                if (isAlreadyActive) "进行中" else "${ch.minBudget}-${ch.maxBudget}万/月",
                                fontSize = 9.sp,
                                color = if (isAlreadyActive) Color(0xFFE57373) else Color.Gray
                            )
                        }
                    }
                }
            }

            // Channel details
            if (channel != null) {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = getChannelColor(channel).copy(alpha = 0.05f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("招生加成", fontSize = 10.sp, color = Color.Gray)
                            Text(
                                "×${channel.enrollmentMultiplier}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("声望提升", fontSize = 10.sp, color = Color.Gray)
                            Text(
                                "+${channel.reputationBoost}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF8F00)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("预热天数", fontSize = 10.sp, color = Color.Gray)
                            Text(
                                "${channel.rampUpDays}天",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1565C0)
                            )
                        }
                    }
                }

                // Step 2: Budget slider
                Spacer(Modifier.height(20.dp))
                Text("设置月预算", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
                Spacer(Modifier.height(8.dp))

                Text(
                    "${FormatUtils.formatCash(state.budgetInput)}/月",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A237E)
                )
                Spacer(Modifier.height(4.dp))
                Slider(
                    value = state.budgetInput.toFloat(),
                    onValueChange = { onUpdateBudget(it.toDouble()) },
                    valueRange = channel.minBudget.toFloat()..channel.maxBudget.toFloat(),
                    steps = ((channel.maxBudget - channel.minBudget) / 0.5).toInt().coerceAtMost(40),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF1A237E),
                        activeTrackColor = Color(0xFF1A237E)
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${channel.minBudget}万", fontSize = 11.sp, color = Color.Gray)
                    if (state.budgetInput > state.schoolCash) {
                        Text("超出余额!", fontSize = 11.sp, color = Color(0xFFE53935), fontWeight = FontWeight.Bold)
                    }
                    Text("${channel.maxBudget}万", fontSize = 11.sp, color = Color.Gray)
                }
            }

            // Confirm button
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = canConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1A237E)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("确认发起推广", fontSize = 15.sp)
            }
        }
    }
}

private fun getChannelIcon(channel: MarketingChannel): ImageVector {
    return when (channel) {
        MarketingChannel.FLYER -> Icons.Default.Description
        MarketingChannel.NEWSPAPER -> Icons.Default.Newspaper
        MarketingChannel.SOCIAL_MEDIA -> Icons.Default.Share
        MarketingChannel.WORD_OF_MOUTH -> Icons.Default.RecordVoiceOver
        MarketingChannel.TV_AD -> Icons.Default.Tv
        MarketingChannel.ONLINE_AD -> Icons.Default.Language
    }
}

private fun getChannelColor(channel: MarketingChannel): Color {
    return when (channel) {
        MarketingChannel.FLYER -> Color(0xFF66BB6A)
        MarketingChannel.NEWSPAPER -> Color(0xFF42A5F5)
        MarketingChannel.SOCIAL_MEDIA -> Color(0xFFAB47BC)
        MarketingChannel.WORD_OF_MOUTH -> Color(0xFFFF7043)
        MarketingChannel.TV_AD -> Color(0xFFEC407A)
        MarketingChannel.ONLINE_AD -> Color(0xFF26C6DA)
    }
}
