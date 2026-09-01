package com.arktools.xiaozhang.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arktools.xiaozhang.audio.AudioManager
import com.arktools.xiaozhang.ui.components.PixelNineSlice
import com.arktools.xiaozhang.R
import com.arktools.xiaozhang.ui.main.MainViewModel
import com.arktools.xiaozhang.ui.theme.Primary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    mainViewModel: MainViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val school by mainViewModel.school.collectAsState()
    val soundEnabled by settingsViewModel.soundEnabled.collectAsState()
    val musicEnabled by settingsViewModel.musicEnabled.collectAsState()
    val sfxVolume by settingsViewModel.sfxVolume.collectAsState()
    val bgmVolume by settingsViewModel.bgmVolume.collectAsState()
    val selectedCampusBgm by settingsViewModel.selectedCampusBgm.collectAsState()
    val gameSpeed by settingsViewModel.gameSpeed.collectAsState()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("游戏设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsCard(title = "音频设置") {
                SettingsRow(
                    icon = {
                        Icon(
                            if (soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = null,
                            tint = Primary
                        )
                    },
                    label = "音效",
                    description = "游戏交互音效"
                ) {
                    Switch(
                        checked = soundEnabled,
                        onCheckedChange = settingsViewModel::setSoundEnabled,
                        colors = SwitchDefaults.colors(checkedTrackColor = Primary)
                    )
                }
                if (soundEnabled) {
                    VolumeSlider("音效音量", sfxVolume, settingsViewModel::setSfxVolume)
                }
                Spacer(modifier = Modifier.height(8.dp))
                SettingsRow(
                    icon = {
                        Icon(
                            if (musicEnabled) Icons.Default.MusicNote else Icons.Default.MusicOff,
                            contentDescription = null,
                            tint = Primary
                        )
                    },
                    label = "背景音乐",
                    description = "游戏背景音乐"
                ) {
                    Switch(
                        checked = musicEnabled,
                        onCheckedChange = settingsViewModel::setMusicEnabled,
                        colors = SwitchDefaults.colors(checkedTrackColor = Primary)
                    )
                }
                if (musicEnabled) {
                    VolumeSlider("音乐音量", bgmVolume, settingsViewModel::setBgmVolume)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("校园曲目", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "危机和剧情音乐会临时覆盖；恢复经营后继续播放所选曲目。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val campusLevel = school?.campusLevel ?: 1
                    val graduateProgram = settingsViewModel.isGraduateProgramActive()
                    val trackAvailability = settingsViewModel.availableCampusTracks(
                        campusLevel,
                        graduateProgram
                    )
                    AudioManager.CampusTrack.entries.forEach { track ->
                        val unlocked = trackAvailability[track] == true
                        TextColorOption(
                            label = if (unlocked) track.displayName else "${track.displayName} · ${track.unlockDescription}",
                            selected = selectedCampusBgm == track.resName,
                            onClick = {
                                if (unlocked) {
                                    settingsViewModel.selectCampusBgm(track, campusLevel, graduateProgram)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }

            SettingsCard(title = "经营设置") {
                Text("默认游戏速度", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "顶栏速度按钮仍可临时切换；看广告加速不受此项影响。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1f, 2f, 3f).forEach { speed ->
                        TextColorOption(
                            label = "×${speed.toInt()}",
                            selected = kotlin.math.abs(gameSpeed - speed) < 0.01f,
                            onClick = {
                                settingsViewModel.setGameSpeed(speed)
                                mainViewModel.setGameSpeed(speed)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        mainViewModel.requestStoryTutorialReplay()
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("重玩剧情教程")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "剧情教程会暂停时间并高亮底部四主区。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SettingsCard(title = "进度保存") {
                Text("游戏会在每月结算、切换后台和退出时自动保存当前进度。")
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "4.1 起不再提供手动存档、读档或删除进度入口。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SettingsCard(title = "异常修复") {
                Text(
                    "如果游戏时间卡住不动，可点击下方按钮强制恢复时间流动（不会清空任何进度）。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                var forceMsg by remember { mutableStateOf<String?>(null) }
                forceMsg?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Button(
                    onClick = {
                        forceMsg = if (mainViewModel.forceResumeTimeFlow()) {
                            "已强制恢复时间流动"
                        } else {
                            "游戏已结束，无法恢复"
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("强制时间流动")
                }
            }

            SettingsCard(title = "游戏信息") {
                school?.let {
                    InfoRow("学校", it.name)
                    InfoRow("资金", "${String.format("%.1f", it.cash)}万元")
                    InfoRow("声誉", "${it.reputation}")
                    InfoRow("日期", "${it.currentYear}年${it.currentMonth}月${it.currentDay}日")
                    InfoRow("校园等级", "Lv.${it.campusLevel}")
                }
            }
            Text(
                text = "校长我来当 2：大学时代 · v${com.arktools.xiaozhang.BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth()) {
        PixelNineSlice(
                    res = R.drawable.card_bg,
                    slice = 48,
                    modifier = Modifier.matchParentSize()
                )
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    icon: @Composable () -> Unit,
    label: String,
    description: String,
    action: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action()
    }
}

@Composable
private fun VolumeSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(start = 36.dp, end = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = Primary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(thumbColor = Primary, activeTrackColor = Primary)
        )
    }
}

@Composable
private fun TextColorOption(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) Primary.copy(alpha = 0.15f) else Color.Transparent,
            contentColor = if (selected) Primary else MaterialTheme.colorScheme.onSurface
        )
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
