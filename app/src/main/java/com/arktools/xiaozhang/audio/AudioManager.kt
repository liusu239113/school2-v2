package com.arktools.xiaozhang.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.arktools.xiaozhang.data.pref.SettingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private var soundPool: SoundPool? = null
    private val soundMap = mutableMapOf<SoundType, Int>()
    private var sfxVolume: Float = 0.7f
    private var bgmVolume: Float = 0.5f
    private var initialized = false

    // BGM player — 所有操作统一在 Main 线程，避免竞态
    private var mediaPlayer: MediaPlayer? = null
    private var isBgmPlaying = false
    private var currentBgmRes: String = ""

    enum class SoundType {
        BUTTON_CLICK,
        CARD_OPEN,
        COURSE_CREATE,
        COURSE_RELEASE,
        TEACHER_HIRE,
        RESEARCH_UNLOCK,
        CASH_EARN,
        CASH_LOSE,
        EVENT_POSITIVE,
        EVENT_NEGATIVE,
        MILESTONE,
        LEVEL_UP,
        // 新增音效
        BUILD_FACILITY,
        SAVE_SUCCESS,
        TEACHER_HIRED,
        STUDENT_ENROLLED,
        CRISIS_ALERT,
        MINIGAME_WIN,
        MINIGAME_FAIL,
        MONEY_EARNED,
        REPUTATION_UP
    }

    enum class BgmType(val resName: String) {
        MENU("v2_bgm_menu"),
        MAIN("v2_bgm_campus"),
        BUSY("v2_bgm_campus"),
        RELAXED("v2_bgm_campus"),
        CRISIS("v2_bgm_crisis")
    }

    private val soundResNames = mapOf(
        SoundType.BUTTON_CLICK to "v2_ui_click",
        SoundType.CARD_OPEN to "sfx_card_open",
        SoundType.COURSE_CREATE to "sfx_course_create",
        SoundType.COURSE_RELEASE to "sfx_course_release",
        SoundType.TEACHER_HIRE to "sfx_hire",
        SoundType.RESEARCH_UNLOCK to "v2_research_complete",
        SoundType.CASH_EARN to "sfx_cash_earn",
        SoundType.CASH_LOSE to "sfx_cash_lose",
        SoundType.EVENT_POSITIVE to "sfx_event_positive",
        SoundType.EVENT_NEGATIVE to "sfx_event_negative",
        SoundType.MILESTONE to "sfx_milestone",
        SoundType.LEVEL_UP to "sfx_level_up",
        // 新增音效映射
        SoundType.BUILD_FACILITY to "build_facility",
        SoundType.SAVE_SUCCESS to "save_success",
        SoundType.TEACHER_HIRED to "teacher_hired",
        SoundType.STUDENT_ENROLLED to "student_enrolled",
        SoundType.CRISIS_ALERT to "v2_crisis",
        SoundType.MINIGAME_WIN to "minigame_win",
        SoundType.MINIGAME_FAIL to "minigame_fail",
        SoundType.MONEY_EARNED to "money_earned",
        SoundType.REPUTATION_UP to "reputation_up"
    )

    private fun getResId(name: String): Int {
        return context.resources.getIdentifier(name, "raw", context.packageName)
    }

    fun init() {
        if (initialized) return
        initialized = true

        soundPool = SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(audioAttributes)
            .build()

        soundResNames.forEach { (type, name) ->
            val resId = getResId(name)
            if (resId != 0) {
                try {
                    soundMap[type] = soundPool!!.load(context, resId, 1)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * 确保已初始化（防止 release 后未重新 init 的情况）
     */
    private fun ensureInit() {
        if (!initialized) {
            init()
        }
    }

    fun playSound(type: SoundType) {
        scope.launch {
            ensureInit()
            if (!settingsDataStore.soundEnabled.first()) return@launch

            val soundId = soundMap[type] ?: return@launch
            if (soundId == 0) return@launch

            soundPool?.play(soundId, sfxVolume, sfxVolume, 1, 0, 1.0f)
        }
    }

    /**
     * Start playing background music from a raw resource.
     * The music will loop until stopped.
     * 所有 MediaPlayer 操作统一在 Main 线程执行，避免竞态。
     */
    fun startBgm(resName: String = "bgm_main") {
        scope.launch {
            if (!settingsDataStore.musicEnabled.first()) return@launch

            val resId = getResId(resName)
            if (resId == 0) return@launch

            try {
                // 先停掉旧的
                stopBgmInternal()

                mediaPlayer = MediaPlayer.create(context, resId)?.apply {
                    isLooping = true
                    setVolume(bgmVolume, bgmVolume)
                    start()
                }
                isBgmPlaying = true
                currentBgmRes = resName
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopBgm() {
        scope.launch {
            stopBgmInternal()
        }
    }

    private fun stopBgmInternal() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
            mediaPlayer = null
            isBgmPlaying = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun pauseBgm() {
        scope.launch {
            try {
                mediaPlayer?.pause()
                isBgmPlaying = false
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun resumeBgm() {
        scope.launch {
            if (!settingsDataStore.musicEnabled.first()) return@launch
            try {
                mediaPlayer?.start()
                isBgmPlaying = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setBgmVolume(volume: Float) {
        bgmVolume = volume.coerceIn(0f, 1f)
        scope.launch {
            mediaPlayer?.setVolume(bgmVolume, bgmVolume)
        }
    }

    fun setSfxVolume(volume: Float) {
        sfxVolume = volume.coerceIn(0f, 1f)
    }

    /**
     * 根据 BgmType 切换 BGM，如果已经在播放相同 BGM 则不做操作
     */
    fun switchBgm(type: BgmType) {
        if (currentBgmRes == type.resName && isBgmPlaying) return
        startBgm(type.resName)
    }

    // Convenience methods
    fun playButtonClick() = playSound(SoundType.BUTTON_CLICK)
    fun playCardOpen() = playSound(SoundType.CARD_OPEN)
    fun playCourseCreate() = playSound(SoundType.COURSE_CREATE)
    fun playCourseRelease() = playSound(SoundType.COURSE_RELEASE)
    fun playTeacherHire() = playSound(SoundType.TEACHER_HIRE)
    fun playResearchUnlock() = playSound(SoundType.RESEARCH_UNLOCK)
    fun playCashEarn() = playSound(SoundType.CASH_EARN)
    fun playCashLose() = playSound(SoundType.CASH_LOSE)
    fun playEventPositive() = playSound(SoundType.EVENT_POSITIVE)
    fun playEventNegative() = playSound(SoundType.EVENT_NEGATIVE)
    fun playMilestone() = playSound(SoundType.MILESTONE)
    fun playLevelUp() = playSound(SoundType.LEVEL_UP)

    /**
     * 释放资源。作为 @Singleton，正常情况下不应该被调用。
     * 如果被调用，会重置 initialized 标记，下次使用时自动重建。
     */
    fun release() {
        stopBgmInternal()
        soundPool?.release()
        soundPool = null
        soundMap.clear()
        initialized = false
    }
}
