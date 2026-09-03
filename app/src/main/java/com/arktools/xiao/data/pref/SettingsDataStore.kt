package com.arktools.xiao.data.pref

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val MUSIC_ENABLED = booleanPreferencesKey("music_enabled")
        val SELECTED_CAMPUS_BGM = stringPreferencesKey("selected_campus_bgm")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val LAST_PLAY_TIME = longPreferencesKey("last_play_time")
        val SCHOOL_ID = stringPreferencesKey("school_id")
    }

    val soundEnabled: Flow<Boolean> = context.dataStore.data.map { it[SOUND_ENABLED] ?: true }
    val musicEnabled: Flow<Boolean> = context.dataStore.data.map { it[MUSIC_ENABLED] ?: true }
    val selectedCampusBgm: Flow<String> = context.dataStore.data.map {
        it[SELECTED_CAMPUS_BGM] ?: "v2_bgm_campus"
    }
    val darkMode: Flow<Boolean> = context.dataStore.data.map { it[DARK_MODE] ?: false }
    val lastPlayTime: Flow<Long> = context.dataStore.data.map { it[LAST_PLAY_TIME] ?: 0L }
    val schoolId: Flow<String?> = context.dataStore.data.map { it[SCHOOL_ID] }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[SOUND_ENABLED] = enabled }
    }

    suspend fun setMusicEnabled(enabled: Boolean) {
        context.dataStore.edit { it[MUSIC_ENABLED] = enabled }
    }

    suspend fun setSelectedCampusBgm(resName: String) {
        context.dataStore.edit { it[SELECTED_CAMPUS_BGM] = resName }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[DARK_MODE] = enabled }
    }

    suspend fun setLastPlayTime(time: Long) {
        context.dataStore.edit { it[LAST_PLAY_TIME] = time }
    }

    suspend fun setSchoolId(id: String) {
        context.dataStore.edit { it[SCHOOL_ID] = id }
    }

    suspend fun getSchoolId(): String {
        return context.dataStore.data.map { it[SCHOOL_ID] ?: "" }.first()
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun clearSchoolId() {
        context.dataStore.edit { it.remove(SCHOOL_ID) }
    }

    // 文字颜色模式: "auto" = 跟随系统, "dark" = 强制深色文字, "light" = 强制浅色文字
    val TEXT_COLOR_MODE = stringPreferencesKey("text_color_mode")
    val textColorMode: Flow<String> = context.dataStore.data.map { it[TEXT_COLOR_MODE] ?: "auto" }

    suspend fun setTextColorMode(mode: String) {
        context.dataStore.edit { it[TEXT_COLOR_MODE] = mode }
    }

    val gameSpeed: Flow<Float> = context.dataStore.data.map { it[floatPreferencesKey("game_speed")] ?: 1f }

    val lastActiveTime: Flow<Long> = context.dataStore.data.map { it[longPreferencesKey("last_active_time")] ?: 0L }

    suspend fun setGameSpeed(speed: Float) {
        context.dataStore.edit { it[floatPreferencesKey("game_speed")] = speed }
    }

    suspend fun setLastActiveTime(time: Long) {
        context.dataStore.edit { it[longPreferencesKey("last_active_time")] = time }
    }

    suspend fun getLastActiveTime(): Long {
        return context.dataStore.data.map { it[longPreferencesKey("last_active_time")] ?: 0L }.first()
    }

    // ======== 事件自动处理配置 ========
    private val AUTO_HANDLE_CONFIG = stringPreferencesKey("auto_handle_config")

    val autoHandleConfig: Flow<String?> = context.dataStore.data.map { it[AUTO_HANDLE_CONFIG] }

    suspend fun setAutoHandleConfig(configJson: String) {
        context.dataStore.edit { it[AUTO_HANDLE_CONFIG] = configJson }
    }

    suspend fun getAutoHandleConfig(): String? {
        return context.dataStore.data.map { it[AUTO_HANDLE_CONFIG] }.first()
    }
}
