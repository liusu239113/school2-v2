package com.arktools.xiaozhang.ui.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiaozhang.audio.AudioManager
import com.arktools.xiaozhang.data.pref.SettingsDataStore
import com.arktools.xiaozhang.data.save.SaveManager
import com.arktools.xiaozhang.domain.model.schoolOwnership
import com.arktools.xiaozhang.domain.model.schoolTier
import com.arktools.xiaozhang.domain.repository.SchoolRepository
import com.arktools.xiaozhang.util.safeLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MenuViewModel @Inject constructor(
    private val schoolRepository: SchoolRepository,
    private val settingsDataStore: SettingsDataStore,
    private val saveManager: SaveManager,
    private val audioManager: AudioManager
) : ViewModel() {

    private val _hasSaveData = MutableStateFlow(false)
    val hasSaveData: StateFlow<Boolean> = _hasSaveData.asStateFlow()

    private val _saveSummary = MutableStateFlow<String?>(null)
    val saveSummary: StateFlow<String?> = _saveSummary.asStateFlow()

    init {
        audioManager.init()
        refreshState()
    }

    fun refreshState() {
        viewModelScope.safeLaunch {
            var school = runCatching { schoolRepository.getSchool() }.getOrNull()
            if (school == null) {
                // 数据库瞬时锁等异常时先重试，避免把"有存档"误报成"没有存档"
                kotlinx.coroutines.delay(2000)
                school = runCatching { schoolRepository.getSchool() }.getOrNull()
            }
            if (school != null) {
                val typeSummary = school.schoolTier().displayName +
                    "·" + school.schoolOwnership().displayName
                _saveSummary.value = school.name + " · " + typeSummary + " · Lv." + school.campusLevel + " · " + school.currentYear + "年" + school.currentMonth + "月"
                // 兜底同步 schoolId：旧版本存档可能没记录 schoolId，
                // 不同步会导致学生/教师/课程按 schoolId 过滤后全部显示为 0。
                val savedSchoolId = settingsDataStore.getSchoolId()
                if (savedSchoolId != school.id) {
                    settingsDataStore.setSchoolId(school.id)
                }
            }
            // live 数据库有学校，或任一本地有效备份存在时，都提供“继续游戏”入口。
            val backupAvailable = withContext(Dispatchers.IO) {
                saveManager.hasAnyBackupData()
            }
            _hasSaveData.value = school != null || backupAvailable
        }
    }

    fun playClickSound() {
        audioManager.playButtonClick()
    }

    fun startMenuBgm() {
        audioManager.startBgm("v2_bgm_menu")
    }

    fun stopMenuBgm() {
        audioManager.stopBgm()
    }
}
