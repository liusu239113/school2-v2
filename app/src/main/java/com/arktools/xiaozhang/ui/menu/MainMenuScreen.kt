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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arktools.xiaozhang.R
import com.arktools.xiaozhang.ui.theme.Primary
import kotlinx.coroutines.delay

private enum class MenuState {
    TITLE,
    NEW_GAME
}

@Composable
fun MainMenuScreen(
    hasSaveData: Boolean,
    onNewGame: (schoolName: String, principalName: String) -> Unit,
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
            .padding(horizontal = 32.dp),
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
                contentDescription = "校长我来当 2：大学时代",
                modifier = Modifier.size(220.dp).scale(logoScale),
                contentScale = ContentScale.Fit
            )
        }
        Spacer(modifier = Modifier.weight(0.1f))
        AnimatedVisibility(
            visible = buttonsVisible,
            enter = fadeIn(tween(400)) + slideInVertically(
                initialOffsetY = { it / 3 },
                animationSpec = tween(500)
            )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (hasSaveData) {
                    PixelMenuButton(
                        text = "继续游戏",
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
    onClick: () -> Unit
) {
    val buttonResource = if (isPrimary) R.drawable.btn_primary else R.drawable.btn_secondary
    Box(
        modifier = Modifier.fillMaxWidth().height(60.dp)
    ) {
        Image(
            painter = painterResource(id = buttonResource),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(4.dp),
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            )
        }
    }
}

@Composable
private fun NewGamePanel(
    onConfirm: (schoolName: String, principalName: String) -> Unit,
    onBack: () -> Unit
) {
    var schoolName by rememberSaveable { mutableStateOf("") }
    var principalName by rememberSaveable { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(0.3f))
        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(initialScale = 0.9f, animationSpec = tween(300)) + fadeIn(tween(300))
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logo_game_v2),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "创建你的大学",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF212121)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedTextField(
                        value = schoolName,
                        onValueChange = { if (it.length <= 12) schoolName = it },
                        label = { Text("大学名称") },
                        placeholder = { Text("例：星海大学") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${schoolName.length}/12",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF9E9E9E),
                        modifier = Modifier.align(Alignment.End)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = principalName,
                        onValueChange = { if (it.length <= 6) principalName = it },
                        label = { Text("校长姓名") },
                        placeholder = { Text("例：张明") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${principalName.length}/6",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF9E9E9E),
                        modifier = Modifier.align(Alignment.End)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { onConfirm(schoolName.trim(), principalName.trim()) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        enabled = schoolName.isNotBlank() && principalName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("开始办学", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = onBack) {
                        Text("返回", color = Color(0xFF757575))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.weight(0.3f))
    }
}
