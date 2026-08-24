package com.arktools.xiaozhang.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiaozhang.audio.AudioManager
import com.arktools.xiaozhang.data.pref.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.arktools.xiaozhang.util.safeLaunch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    val audioManager: AudioManager
) : ViewModel() {

    private val _darkMode = MutableStateFlow(false)
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()

    private val _soundEnabled = MutableStateFlow(true)
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _musicEnabled = MutableStateFlow(true)
    val musicEnabled: StateFlow<Boolean> = _musicEnabled.asStateFlow()

    private val _gameSpeed = MutableStateFlow(1f)
    val gameSpeed: StateFlow<Float> = _gameSpeed.asStateFlow()

    private val _sfxVolume = MutableStateFlow(0.7f)
    val sfxVolume: StateFlow<Float> = _sfxVolume.asStateFlow()

    private val _bgmVolume = MutableStateFlow(0.5f)
    val bgmVolume: StateFlow<Float> = _bgmVolume.asStateFlow()

    // 文字颜色模式: "auto" | "dark" | "light"
    private val _textColorMode = MutableStateFlow("auto")
    val textColorMode: StateFlow<String> = _textColorMode.asStateFlow()

    init {
        viewModelScope.safeLaunch {
            settingsDataStore.darkMode.collect { _darkMode.value = it }
        }
        viewModelScope.safeLaunch {
            settingsDataStore.soundEnabled.collect { _soundEnabled.value = it }
        }
        viewModelScope.safeLaunch {
            settingsDataStore.musicEnabled.collect { _musicEnabled.value = it }
        }
        viewModelScope.safeLaunch {
            settingsDataStore.gameSpeed.collect { _gameSpeed.value = it }
        }
        viewModelScope.safeLaunch {
            settingsDataStore.textColorMode.collect { _textColorMode.value = it }
        }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.safeLaunch {
            settingsDataStore.setDarkMode(enabled)
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.safeLaunch {
            settingsDataStore.setSoundEnabled(enabled)
        }
    }

    fun setMusicEnabled(enabled: Boolean) {
        viewModelScope.safeLaunch {
            settingsDataStore.setMusicEnabled(enabled)
            if (!enabled) {
                audioManager.stopBgm()
            } else {
                audioManager.startBgm()
            }
        }
    }

    fun setSfxVolume(volume: Float) {
        _sfxVolume.value = volume
        audioManager.setSfxVolume(volume)
    }

    fun setBgmVolume(volume: Float) {
        _bgmVolume.value = volume
        audioManager.setBgmVolume(volume)
    }

    fun setGameSpeed(speed: Float) {
        viewModelScope.safeLaunch {
            settingsDataStore.setGameSpeed(speed)
        }
    }

    fun setTextColorMode(mode: String) {
        viewModelScope.safeLaunch {
            settingsDataStore.setTextColorMode(mode)
        }
    }
}
