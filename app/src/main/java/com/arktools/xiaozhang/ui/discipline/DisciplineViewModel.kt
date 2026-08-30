package com.arktools.xiaozhang.ui.discipline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arktools.xiaozhang.audio.AudioManager
import com.arktools.xiaozhang.domain.model.CollegeType
import com.arktools.xiaozhang.domain.model.DisciplineCatalog
import com.arktools.xiaozhang.domain.repository.SchoolRepository
import com.arktools.xiaozhang.domain.policy.SchoolPolicyManager
import com.arktools.xiaozhang.util.safeLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * 学科与专业建设：投钱升级学科 → 两年一次评估定级 → 反哺招生/声誉/财政。
 */
@HiltViewModel
class DisciplineViewModel @Inject constructor(
    private val schoolRepository: SchoolRepository,
    private val policyManager: SchoolPolicyManager,
    private val audioManager: AudioManager
) : ViewModel() {

    data class Row(
        val def: DisciplineCatalog.Def,
        val state: DisciplineCatalog.State,
        val collegeFounded: Boolean,
        val levelLocked: Boolean,
        val nextCostWan: Double,
        val affordable: Boolean,
        val maxed: Boolean
    )

    data class UiState(
        val cash: Double = 0.0,
        val campusLevel: Int = 1,
        val currentYear: Int = 2026,
        val rows: List<Row> = emptyList(),
        val message: String? = null,
        val nextEvalYear: Int = 0
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.safeLaunch {
            schoolRepository.getSchoolFlow().collect { school ->
                if (school == null) return@collect
                rebuild(school.cash, school.campusLevel, school.currentYear)
            }
        }
        viewModelScope.safeLaunch {
            policyManager.policies.collect {
                val cur = _state.value
                rebuild(cur.cash, cur.campusLevel, cur.currentYear)
            }
        }
    }

    private fun rebuild(cash: Double, campusLevel: Int, currentYear: Int) {
        val dev = policyManager.policies.value.collegeDevelopment
        val states = DisciplineCatalog.decode(dev.disciplinesJson)
        val founded = dev.founded.toSet()
        val rows = DisciplineCatalog.ALL.map { def ->
            val st = states[def.id] ?: DisciplineCatalog.State()
            val collegeFounded = def.college in founded
            val levelLocked = campusLevel < def.college.unlockLevel
            val cost = DisciplineCatalog.upgradeCostWan(st.level)
            Row(
                def = def,
                state = st,
                collegeFounded = collegeFounded,
                levelLocked = levelLocked,
                nextCostWan = cost,
                affordable = cash >= cost && cost > 0,
                maxed = st.level >= DisciplineCatalog.MAX_LEVEL
            )
        }
        _state.value = _state.value.copy(
            cash = cash,
            campusLevel = campusLevel,
            currentYear = if (currentYear > 0) currentYear else _state.value.currentYear,
            rows = rows,
            nextEvalYear = nextEvenYear(if (currentYear > 0) currentYear else _state.value.currentYear)
        )
    }

    private fun nextEvenYear(year: Int): Int {
        val y = if (year <= 0) 2026 else year
        return if (y % 2 == 0) y else y + 1
    }

    fun invest(defId: String) {
        audioManager.playButtonClick()
        val def = DisciplineCatalog.byId(defId) ?: return
        viewModelScope.safeLaunch {
            var cost = 0.0
            var newLevel = 0
            val result = schoolRepository.mutateSchool { school ->
                val dev = policyManager.policies.value.collegeDevelopment
                val states = DisciplineCatalog.decode(dev.disciplinesJson).toMutableMap()
                val st = states[defId] ?: DisciplineCatalog.State()
                if (def.college !in dev.founded) {
                    _state.value = _state.value.copy(message = "先成立${def.college.displayName}，才能建设该学科")
                    return@mutateSchool false
                }
                if (school.campusLevel < def.college.unlockLevel) {
                    _state.value = _state.value.copy(message = "校园 Lv.${def.college.unlockLevel} 解锁该学院建设")
                    return@mutateSchool false
                }
                if (st.level >= DisciplineCatalog.MAX_LEVEL) {
                    _state.value = _state.value.copy(message = "${def.name}已达最高建设等级")
                    return@mutateSchool false
                }
                cost = DisciplineCatalog.upgradeCostWan(st.level)
                if (school.cash < cost) {
                    _state.value = _state.value.copy(message = "资金不足！需要 ${cost.toInt()} 万")
                    audioManager.playEventNegative()
                    return@mutateSchool false
                }
                school.cash -= cost
                newLevel = st.level + 1
                states[defId] = st.copy(level = newLevel, investWan = st.investWan + cost)
                val updated = dev.copy(disciplinesJson = DisciplineCatalog.encode(states))
                policyManager.replaceCollegeDevelopment(updated)
                school.policyJson = policyManager.toJson()
                true
            }
            if (result != null) {
                audioManager.playBuildFacility()
                _state.value = _state.value.copy(message = "${def.name}建设投入完成，当前等级 Lv.$newLevel")
            }
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    /** 治院页摘要：已建设学科数与最高评级 */
    fun summaryText(): String {
        val dev = policyManager.policies.value.collegeDevelopment
        val states = DisciplineCatalog.decode(dev.disciplinesJson)
        val opened = states.values.count { it.level > 0 }
        val best = states.values.maxByOrNull { it.level }?.lastRating ?: "NONE"
        return if (opened == 0) "尚未建设任何学科" else "已建设 $opened 个学科 · 最高评级 $best"
    }
}
