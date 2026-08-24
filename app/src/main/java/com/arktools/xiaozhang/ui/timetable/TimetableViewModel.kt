package com.arktools.xiaozhang.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiaozhang.domain.engine.GameEngine
import com.arktools.xiaozhang.domain.model.SchoolClass
import com.arktools.xiaozhang.domain.model.Subject
import com.arktools.xiaozhang.domain.repository.TeacherRepository
import com.arktools.xiaozhang.domain.timetable.WeeklyTimetable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import com.arktools.xiaozhang.util.safeLaunch

/**
 * 课表格子坐标：dayOfWeek=1~5, periodIndex=0~7
 */
data class SlotPosition(val dayOfWeek: Int, val periodIndex: Int)

data class TimetableUiState(
    val classes: List<SchoolClass> = emptyList(),
    val selectedClassId: String? = null,
    val currentTimetable: WeeklyTimetable? = null,
    val selectedSlot: SlotPosition? = null,  // 调课：第一次点击选中的格子
    val swapHint: String? = null,            // 底部提示文字
    val showSubjectSettings: Boolean = false,
    val currentSubjectHours: Map<Subject, Int> = emptyMap(),
    val subjectSettingsError: String? = null
)

@HiltViewModel
class TimetableViewModel @Inject constructor(
    private val gameEngine: GameEngine,
    private val teacherRepository: TeacherRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimetableUiState())
    val uiState: StateFlow<TimetableUiState> = _uiState.asStateFlow()

    init {
        refreshData()
    }

    fun refreshData() {
        val classes = gameEngine.classes
        if (classes.isEmpty()) {
            _uiState.value = TimetableUiState()
            return
        }
        // 确保所有班级都有课表（兜底懒生成，防止错过学期初时机）
        viewModelScope.safeLaunch { gameEngine.ensureTimetablesGenerated() }

        val selectedId = _uiState.value.selectedClassId ?: classes.firstOrNull()?.id
        _uiState.value = _uiState.value.copy(
            classes = classes,
            selectedClassId = selectedId,
            currentTimetable = selectedId?.let { id ->
                gameEngine.timetableManager.getAllTimetables()[id]
            },
            selectedSlot = null,
            swapHint = null
        )
    }

    fun selectClass(classId: String) {
        val timetable = gameEngine.timetableManager.getAllTimetables()[classId]
        _uiState.value = _uiState.value.copy(
            selectedClassId = classId,
            currentTimetable = timetable,
            selectedSlot = null,
            swapHint = null
        )
    }

    /**
     * 点击课表格子：
     * - 未选中 → 选中这一格，提示"请点击另一节课交换"
     * - 已选中 → 执行调课交换，清除选中状态
     */
    fun onSlotClick(dayOfWeek: Int, periodIndex: Int) {
        val current = _uiState.value
        val classId = current.selectedClassId ?: return
        val first = current.selectedSlot

        if (first == null) {
            // 第一次点击：选中
            _uiState.value = current.copy(
                selectedSlot = SlotPosition(dayOfWeek, periodIndex),
                swapHint = "已选中，请点击另一节课进行交换"
            )
        } else {
            if (first.dayOfWeek == dayOfWeek && first.periodIndex == periodIndex) {
                // 点击同一格：取消选中
                _uiState.value = current.copy(selectedSlot = null, swapHint = null)
            } else {
                // 第二次点击：执行交换
                val updated = gameEngine.timetableManager.swapSlots(
                    classId, first.dayOfWeek, first.periodIndex, dayOfWeek, periodIndex
                )
                _uiState.value = current.copy(
                    currentTimetable = updated ?: current.currentTimetable,
                    selectedSlot = null,
                    swapHint = if (updated != null) "调课成功" else "调课失败"
                )
            }
        }
    }

    fun clearHint() {
        _uiState.value = _uiState.value.copy(swapHint = null)
    }

    /**
     * 打开科目课时设置弹窗
     */
    fun openSubjectSettings() {
        val classId = _uiState.value.selectedClassId ?: return
        val schoolClass = _uiState.value.classes.find { it.id == classId } ?: return
        val hours = gameEngine.timetableManager.getSubjectHoursForClass(schoolClass)
        _uiState.value = _uiState.value.copy(
            showSubjectSettings = true,
            currentSubjectHours = hours,
            subjectSettingsError = null
        )
    }

    fun closeSubjectSettings() {
        _uiState.value = _uiState.value.copy(
            showSubjectSettings = false,
            currentSubjectHours = emptyMap(),
            subjectSettingsError = null
        )
    }

    /**
     * 调整某科目课时（+1 或 -1）
     */
    fun adjustSubjectHours(subject: Subject, delta: Int) {
        val current = _uiState.value.currentSubjectHours.toMutableMap()
        val newValue = (current.getOrDefault(subject, 0) + delta).coerceAtLeast(0)
        if (newValue == 0) {
            current.remove(subject)
        } else {
            current[subject] = newValue
        }
        _uiState.value = _uiState.value.copy(
            currentSubjectHours = current,
            subjectSettingsError = null
        )
    }

    /**
     * 保存自定义课时并重新生成课表
     */
    fun saveSubjectHours() {
        val classId = _uiState.value.selectedClassId ?: return
        val schoolClass = _uiState.value.classes.find { it.id == classId } ?: return
        val hours = _uiState.value.currentSubjectHours

        val totalSlots = 5 * 8 // 每周40节课
        if (hours.values.sum() > totalSlots) {
            _uiState.value = _uiState.value.copy(
                subjectSettingsError = "每周总课时不能超过 ${totalSlots} 节"
            )
            return
        }

        viewModelScope.safeLaunch {
            gameEngine.timetableManager.setCustomSubjectHours(classId, hours)
            val allTeachers = teacherRepository.getTeachers()
            gameEngine.timetableManager.regenerateTimetable(schoolClass, allTeachers)
            _uiState.value = _uiState.value.copy(
                currentTimetable = gameEngine.timetableManager.getAllTimetables()[classId],
                showSubjectSettings = false,
                currentSubjectHours = emptyMap(),
                subjectSettingsError = null,
                swapHint = "课表已按新课时重新生成"
            )
        }
    }

    /**
     * 重置为班型默认课时
     */
    fun resetSubjectHours() {
        val classId = _uiState.value.selectedClassId ?: return
        val schoolClass = _uiState.value.classes.find { it.id == classId } ?: return
        viewModelScope.safeLaunch {
            gameEngine.timetableManager.resetCustomSubjectHours(classId)
            val allTeachers = teacherRepository.getTeachers()
            gameEngine.timetableManager.regenerateTimetable(schoolClass, allTeachers)
            _uiState.value = _uiState.value.copy(
                currentTimetable = gameEngine.timetableManager.getAllTimetables()[classId],
                showSubjectSettings = false,
                currentSubjectHours = emptyMap(),
                subjectSettingsError = null,
                swapHint = "已恢复班型默认课表"
            )
        }
    }
}
