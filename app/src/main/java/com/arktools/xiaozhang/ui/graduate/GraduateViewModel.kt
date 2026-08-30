package com.arktools.xiaozhang.ui.graduate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiaozhang.audio.AudioManager
import com.arktools.xiaozhang.domain.model.DisciplineCatalog
import com.arktools.xiaozhang.domain.model.Teacher
import com.arktools.xiaozhang.domain.policy.SchoolPolicyManager
import com.arktools.xiaozhang.domain.repository.SchoolRepository
import com.arktools.xiaozhang.domain.repository.TeacherRepository
import com.arktools.xiaozhang.util.safeLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * 研究生院：查看在读硕博、分配学业导师，名额与进度一览。
 */
@HiltViewModel
class GraduateViewModel @Inject constructor(
    private val schoolRepository: SchoolRepository,
    private val policyManager: SchoolPolicyManager,
    teacherRepository: TeacherRepository,
    private val audioManager: AudioManager
) : ViewModel() {

    data class StudentRow(
        val student: com.arktools.xiaozhang.domain.graduate.GradStudent,
        val disciplineName: String,
        val advisorName: String?,
        val advisorRemaining: Int
    )

    data class TeacherOption(
        val teacher: Teacher,
        val load: Int,
        val capacity: Int
    ) {
        val remaining: Int get() = (capacity - load).coerceAtLeast(0)
    }

    data class UiState(
        val programOn: Boolean = false,
        val campusLevel: Int = 1,
        val cash: Double = 0.0,
        val quotaMaster: Int = 0,
        val quotaPhd: Int = 0,
        val rows: List<StudentRow> = emptyList(),
        val teachers: List<TeacherOption> = emptyList(),
        val pickingStudentId: String? = null,
        val message: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var teacherCache: List<Teacher> = emptyList()

    init {
        viewModelScope.safeLaunch {
            teacherRepository.getTeachersFlow().collect { list ->
                teacherCache = list.filter { it.isWorking }
                rebuild()
            }
        }
        viewModelScope.safeLaunch {
            schoolRepository.getSchoolFlow().collect { school ->
                if (school == null) return@collect
                val cur = _state.value
                _state.value = cur.copy(cash = school.cash, campusLevel = school.campusLevel)
                rebuild()
            }
        }
        viewModelScope.safeLaunch {
            policyManager.policies.collect { rebuild() }
        }
        viewModelScope.safeLaunch {
            policyManager.graduateManager.state.collect { rebuild() }
        }
    }

    private fun rebuild() {
        val gm = policyManager.graduateManager
        val dev = policyManager.policies.value.collegeDevelopment
        val states = DisciplineCatalog.decode(dev.disciplinesJson)
        val load = gm.advisorLoad()
        val capacityOf: (Teacher) -> Int = {
            com.arktools.xiaozhang.domain.graduate.GraduateSchoolManager.advisorCapacity(it.level.name)
        }
        val rows = gm.state.value.students.map { s ->
            val advisor = teacherCache.firstOrNull { it.id == s.advisorId }
            StudentRow(
                student = s,
                disciplineName = DisciplineCatalog.byId(s.disciplineId)?.name ?: "未定学科",
                advisorName = advisor?.name,
                advisorRemaining = advisor?.let { capacityOf(it) - (load[it.id] ?: 0) } ?: 0
            )
        }
        val ratedAB = states.values.count { it.level >= 3 || it.lastRating == "A" || it.lastRating == "A+" }
        val ratedAPlus = states.values.count { it.lastRating == "A+" }
        _state.value = _state.value.copy(
            programOn = dev.graduateProgram,
            rows = rows,
            teachers = teacherCache.map { TeacherOption(it, load[it.id] ?: 0, capacityOf(it)) },
            quotaMaster = com.arktools.xiaozhang.domain.graduate.GraduateSchoolManager
                .masterQuota(_state.value.campusLevel, ratedAB),
            quotaPhd = com.arktools.xiaozhang.domain.graduate.GraduateSchoolManager
                .phdQuota(_state.value.campusLevel, ratedAPlus)
        )
    }

    fun openPicker(studentId: String) {
        audioManager.playButtonClick()
        _state.value = _state.value.copy(pickingStudentId = studentId)
    }

    fun closePicker() {
        _state.value = _state.value.copy(pickingStudentId = null)
    }

    fun assign(studentId: String, teacherId: String) {
        audioManager.playButtonClick()
        val teacher = teacherCache.firstOrNull { it.id == teacherId } ?: return
        val option = _state.value.teachers.firstOrNull { it.teacher.id == teacherId } ?: return
        if (option.remaining <= 0) {
            _state.value = _state.value.copy(message = "${teacher.name}的带教名额已满")
            return
        }
        val ok = policyManager.graduateManager.assignAdvisor(studentId, teacherId)
        if (ok) {
            viewModelScope.safeLaunch {
                schoolRepository.mutateSchool { school ->
                    school.policyJson = policyManager.toJson()
                    true
                }
            }
            _state.value = _state.value.copy(
                pickingStudentId = null,
                message = "已指派 ${teacher.name} 为导师，该生进度恢复全速"
            )
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
