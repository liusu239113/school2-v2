package com.arktools.xiaozhang.ui.menu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.arktools.xiaozhang.domain.model.SchoolOwnership
import com.arktools.xiaozhang.domain.model.SchoolTier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arktools.xiaozhang.R
import com.arktools.xiaozhang.ui.components.PixelNineSlice
import com.arktools.xiaozhang.ui.theme.Primary
import kotlinx.coroutines.delay

private enum class MenuState {
    TITLE,
    NEW_GAME
}

/** 办学风格：开局差异化（综合=+50万启动经费；理工/人文=免费赠送对应学院） */
enum class FoundingStyle(val key: String, val displayName: String, val detail: String) {
    BALANCED("BALANCED", "综合型", "启动经费 +50 万"),
    TECH("TECH", "理工强校", "免费成立理学院"),
    HUMAN("HUMAN", "人文名校", "免费成立人文学院")
}

@Composable
fun MainMenuScreen(
    hasSaveData: Boolean,
    saveSummary: String? = null,
    onNewGame: (schoolName: String, principalName: String, tierKey: String, ownershipKey: String, style: FoundingStyle) -> Unit,
    onContinueGame: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuState by rememberSaveable { mutableIntStateOf(MenuState.TITLE.ordinal) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .paint(
                painter = painterResource(id = R.drawable.bg_main_menu_v2),
                contentScale = ContentScale.Crop
            )
    ) {
        when (MenuState.entries[menuState]) {
            MenuState.TITLE -> TitleScreen(
                hasSaveData = hasSaveData,
                saveSummary = saveSummary,
                onNewGame = { menuState = MenuState.NEW_GAME.ordinal },
                onContinue = onContinueGame,
                onSettings = onOpenSettings
            )
            MenuState.NEW_GAME -> NewGamePanel(
                onConfirm = onNewGame,
                onBack = { menuState = MenuState.TITLE.ordinal }
            )
        }
    }
}

@Composable
private fun TitleScreen(
    hasSaveData: Boolean,
    saveSummary: String? = null,
    onNewGame: () -> Unit,
    onContinue: () -> Unit,
    onSettings: () -> Unit
) {
    var logoVisible by remember { mutableStateOf(false) }
    var buttonsVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(200)
        logoVisible = true
        delay(400)
        buttonsVisible = true
    }

    val infiniteTransition = rememberInfiniteTransition(label = "title_pulse")
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(0.15f))
        AnimatedVisibility(
            visible = logoVisible,
            enter = scaleIn(initialScale = 0.3f, animationSpec = tween(600)) + fadeIn(tween(600))
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_game_v2),
                contentDescription = "校长我来当 2",
                modifier = Modifier.size(176.dp).scale(logoScale),
                contentScale = ContentScale.Fit
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "UNIVERSITY ERA  ·  大学经营模拟",
            color = Color(0xFFD4B06A),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(20.dp))
        AnimatedVisibility(
            visible = buttonsVisible,
            enter = fadeIn(tween(400)) + slideInVertically(
                initialOffsetY = { it / 3 },
                animationSpec = tween(500)
            )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(0.88f)
            ) {
                if (hasSaveData) {
                    PixelMenuButton(
                        text = "继续游戏",
                        subText = saveSummary,
                        icon = Icons.Default.PlayArrow,
                        isPrimary = true,
                        onClick = onContinue
                    )
                }
                PixelMenuButton(
                    text = "开始新游戏",
                    icon = Icons.Default.Add,
                    isPrimary = false,
                    onClick = onNewGame
                )
                PixelMenuButton(
                    text = "游戏设置",
                    icon = Icons.Default.Settings,
                    isPrimary = false,
                    onClick = onSettings
                )
            }
        }
        Spacer(modifier = Modifier.weight(0.15f))
    }
}

@Composable
private fun PixelMenuButton(
    text: String,
    icon: ImageVector,
    isPrimary: Boolean,
    onClick: () -> Unit,
    subText: String? = null
) {
    val buttonResource = if (isPrimary) R.drawable.btn_primary else R.drawable.btn_secondary
    // 带副文字（存档信息）时加高，保证两行都落在按钮图内，不外溢叠字
    Box(
        modifier = Modifier.fillMaxWidth().height(if (subText != null) 68.dp else 56.dp)
    ) {
        PixelNineSlice(
            res = buttonResource,
            slice = 32,
            modifier = Modifier.fillMaxSize()
        )
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                // 按钮图均为浅色底，统一深色文字保证对比度
                contentColor = Color(0xFF083444)
            ),
            shape = androidx.compose.ui.graphics.RectangleShape,
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                )
                if (subText != null) {
                    Text(
                        text = subText,
                        fontSize = 10.sp,
                        color = Color(0xFF083444).copy(alpha = 0.75f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun NewGamePanel(
    onConfirm: (schoolName: String, principalName: String, tierKey: String, ownershipKey: String, style: FoundingStyle) -> Unit,
    onBack: () -> Unit
) {
    var schoolName by rememberSaveable { mutableStateOf("") }
    var principalName by rememberSaveable { mutableStateOf("") }
    var tierIndex by rememberSaveable { mutableIntStateOf(1) }   // 默认应用型本科
    var ownershipIndex by rememberSaveable { mutableIntStateOf(1) } // 默认民办
    var styleIndex by rememberSaveable { mutableIntStateOf(0) }
    var visible by remember { mutableStateOf(false) }

    val tiers = SchoolTier.entries
    val ownerships = SchoolOwnership.entries
    val styles = FoundingStyle.entries

    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(initialScale = 0.9f, animationSpec = tween(300)) + fadeIn(tween(300))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xE611263D))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_game_v2),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "创建你的大学",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFE7F1F8)
                )
                Spacer(modifier = Modifier.height(20.dp))

                // 第 1 步：创校登记
                StepLabel("① 创校登记")
                OutlinedTextField(
                    value = schoolName,
                    onValueChange = { if (it.length <= 12) schoolName = it },
                    label = { Text("大学名称") },
                    placeholder = { Text("例：星海大学", color = Color(0xFF6E8399)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = androidx.compose.ui.graphics.RectangleShape,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFFFFD54F),
                        focusedBorderColor = Color(0xFF1E96C8),
                        unfocusedBorderColor = Color(0xFF5A7186),
                        focusedLabelColor = Color(0xFFFFD54F),
                        unfocusedLabelColor = Color(0xFF9EB3C6)
                    )
                )
                Text(
                    text = "${schoolName.length}/12",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9E9E9E),
                    modifier = Modifier.align(Alignment.End)
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = principalName,
                    onValueChange = { if (it.length <= 6) principalName = it },
                    label = { Text("校长姓名") },
                    placeholder = { Text("例：张明", color = Color(0xFF6E8399)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = androidx.compose.ui.graphics.RectangleShape,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFFFFD54F),
                        focusedBorderColor = Color(0xFF1E96C8),
                        unfocusedBorderColor = Color(0xFF5A7186),
                        focusedLabelColor = Color(0xFFFFD54F),
                        unfocusedLabelColor = Color(0xFF9EB3C6)
                    )
                )
                Text(
                    text = "${principalName.length}/6",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9E9E9E),
                    modifier = Modifier.align(Alignment.End)
                )
                Spacer(modifier = Modifier.height(18.dp))

                // 第 2 步：办学层次
                StepLabel("② 办学层次（决定学制与玩法）")
                Image(
                    painter = painterResource(
                        id = when (tiers[tierIndex]) {
                            SchoolTier.VOCATIONAL -> R.drawable.preview_tier_vocational
                            SchoolTier.VOCATIONAL_BACHELOR -> R.drawable.preview_tier_vocbachelor
                            SchoolTier.APPLIED -> R.drawable.preview_tier_applied
                            SchoolTier.RESEARCH -> R.drawable.preview_tier_research
                        }
                    ),
                    contentDescription = "校园预览",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(8.dp))
                tiers.forEachIndexed { index, tier ->
                    OptionRow(
                        title = tier.displayName,
                        detail = tier.detail,
                        selected = tierIndex == index,
                        onClick = { tierIndex = index }
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))

                // 第 3 步：办学性质
                StepLabel("③ 办学性质（财政与压力）")
                ownerships.forEachIndexed { index, ownership ->
                    OptionRow(
                        title = ownership.displayName,
                        detail = ownership.detail,
                        selected = ownershipIndex == index,
                        onClick = { ownershipIndex = index }
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))

                // 第 4 步：办学风格
                StepLabel("④ 办学风格（开局加成）")
                styles.forEachIndexed { index, style ->
                    OptionRow(
                        title = style.displayName,
                        detail = style.detail,
                        selected = styleIndex == index,
                        onClick = { styleIndex = index }
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        onConfirm(
                            schoolName.trim(),
                            principalName.trim(),
                            tiers[tierIndex].key,
                            ownerships[ownershipIndex].key,
                            styles[styleIndex]
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = schoolName.isNotBlank() && principalName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = androidx.compose.ui.graphics.RectangleShape
                ) {
                    Text("开始办学", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(onClick = onBack) {
                    Text("返回", color = Color(0xFFB8C7D6))
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun StepLabel(text: String) {
    Text(
        text = text,
        color = Color(0xFFFFD54F),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        textAlign = TextAlign.Start
    )
}

@Composable
private fun OptionRow(
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Color(0xFF14648C) else Color(0xFF0B2038))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else Color(0xFFB8C7D6)
            )
            Text(
                detail,
                fontSize = 11.sp,
                color = Color(0xFFB8C7D6)
            )
        }
        if (selected) {
            Text("✓", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
}
