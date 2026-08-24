package com.arktools.xiaozhang.domain.teaching

import com.arktools.xiaozhang.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 教学管理器 - 管理学校教学配置的单例
 *
 * 职责：
 * 1. 持有当前教学配置的状态流（ClassTier分布、强度、作息、特殊项目等）
 * 2. 提供配置变更接口（UI层调用）
 * 3. 提供JSON序列化/反序列化（与School.teachingConfigJson对接）
 * 4. 计算教学质量评分、运营成本、满意度影响等
 */
@Singleton
class TeachingManager @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _state = MutableStateFlow(TeachingState())
    val state: StateFlow<TeachingState> = _state.asStateFlow()

    val config: TeachingConfig get() = _state.value.config

    // ==================== 初始化/持久化 ====================

    /**
     * 从School的JSON字段加载配置（GameEngine启动时调用）
     */
    fun loadFromJson(jsonStr: String) {
        if (jsonStr.isBlank()) {
            _state.value = TeachingState(config = TeachingConfig(), initialized = true)
            return
        }
        try {
            val saved = json.decodeFromString<TeachingConfigDto>(jsonStr)
            _state.value = TeachingState(config = saved.toConfig(), initialized = true)
        } catch (e: Exception) {
            throw IllegalArgumentException("TeachingManager.loadFromJson failed", e)
        }
    }

    /**
     * 序列化当前配置为JSON（保存时调用）
     */
    fun toJson(): String {
        return try {
            json.encodeToString(TeachingConfigDto.fromConfig(_state.value.config))
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * 重置为默认配置（新游戏时调用）
     */
    fun reset() {
        _state.value = TeachingState(config = TeachingConfig(), initialized = true)
    }

    // ==================== 配置变更接口 ====================

    /**
     * 设置班型分布
     */
    fun setClassDistribution(distribution: Map<ClassTier, Int>) {
        _state.update { it.copy(config = it.config.copy(classDistribution = distribution)) }
    }

    /**
     * 调整某个班型的班数
     */
    fun setClassCount(tier: ClassTier, count: Int) {
        val newDist = _state.value.config.classDistribution.toMutableMap()
        if (count <= 0) {
            newDist.remove(tier)
        } else {
            newDist[tier] = count
        }
        _state.update { it.copy(config = it.config.copy(classDistribution = newDist)) }
    }

    /**
     * 设置教学强度
     */
    fun setIntensity(intensity: TeachingIntensity) {
        _state.update { it.copy(config = it.config.copy(intensity = intensity)) }
    }

    /**
     * 设置文理方向
     */
    fun setSubjectTrack(track: SubjectTrack) {
        _state.update { it.copy(config = it.config.copy(subjectTrack = track)) }
    }

    /**
     * 切换作息政策（启用/禁用）
     */
    fun toggleSchedulePolicy(policy: SchedulePolicy) {
        val current = _state.value.config.schedulePolicies.toMutableSet()
        if (policy in current) {
            current.remove(policy)
        } else {
            current.add(policy)
        }
        _state.update { it.copy(config = it.config.copy(schedulePolicies = current)) }
    }

    /**
     * 添加特殊项目
     */
    fun addSpecialProgram(program: SpecialProgram): Boolean {
        val current = _state.value.config.specialPrograms
        if (program in current) return false
        _state.update { it.copy(config = it.config.copy(specialPrograms = current + program)) }
        return true
    }

    /**
     * 移除特殊项目
     */
    fun removeSpecialProgram(program: SpecialProgram) {
        val current = _state.value.config.specialPrograms.toMutableSet()
        current.remove(program)
        _state.update { it.copy(config = it.config.copy(specialPrograms = current)) }
    }

    /**
     * 设置理科占比
     */
    fun setScienceRatio(ratio: Float) {
        _state.update { it.copy(config = it.config.copy(scienceToArtsRatio = ratio.coerceIn(0.2f, 0.9f))) }
    }

    /**
     * 设置每周体育课时
     */
    fun setWeeklyPEHours(hours: Int) {
        _state.update { it.copy(config = it.config.copy(weeklyPEHours = hours.coerceIn(0, 5))) }
    }

    /**
     * 设置每月考试频率
     */
    fun setMonthlyExamFrequency(freq: Int) {
        _state.update { it.copy(config = it.config.copy(monthlyExamFrequency = freq.coerceIn(0, 4))) }
    }

    // ==================== 计算接口 ====================

    /**
     * 开设当前配置所需的初始总费用（万元）
     */
    fun totalSetupCost(): Double {
        val classCost = config.classDistribution.entries.sumOf { (tier, count) ->
            tier.setupCost * count
        }
        val programCost = config.specialPrograms.sumOf { it.setupCost }
        return classCost + programCost
    }

    /**
     * 每月运营成本（万元）
     */
    fun monthlyOperatingCost(): Double = config.monthlyOperatingCost()

    /**
     * 计算学生成绩增长基础倍率（每月tick时用）
     * 综合了：班型倍率、强度倍率、作息加成
     */
    fun scoreGrowthMultiplier(classTier: ClassTier): Float {
        val tierMul = classTier.scoreMultiplier
        val intensityMul = config.intensity.scoreMultiplier
        val scheduleMul = 1f + config.schedulePolicies.sumOf { it.scoreBonus.toDouble() }.toFloat()
        // 综合倍率封顶2.0，防止极端配置下成绩增长过快不真实
        return (tierMul * intensityMul * scheduleMul).coerceAtMost(2.0f)
    }

    /**
     * 教师额外负担总和（影响教师满意度和离职率）
     */
    fun totalTeacherExtraLoad(): Float {
        val scheduleLoad = config.schedulePolicies.sumOf { it.teacherExtraLoad.toDouble() }.toFloat()
        val intensityLoad = config.intensity.teacherBurnoutRate
        return scheduleLoad + intensityLoad
    }

    /**
     * 检查当前配置是否已做初始设置（教程用）
     */
    fun isConfigured(): Boolean {
        return config.classDistribution.isNotEmpty() && config.totalClasses > 0
    }
}

// ==================== 状态数据类 ====================

data class TeachingState(
    val config: TeachingConfig = TeachingConfig(),
    val initialized: Boolean = false
)

// ==================== 序列化DTO（因为Map<Enum,Int>等需要转换） ====================

@Serializable
data class TeachingConfigDto(
    val id: String = "",
    val classDistribution: Map<String, Int> = emptyMap(),
    val subjectTrack: String = "COMPREHENSIVE",
    val intensity: String = "NORMAL",
    val schedulePolicies: List<String> = listOf("EVENING_STUDY"),
    val specialPrograms: List<String> = emptyList(),
    val scienceToArtsRatio: Float = 0.6f,
    val weeklyPEHours: Int = 2,
    val monthlyExamFrequency: Int = 1
) {
    fun toConfig(): TeachingConfig {
        return TeachingConfig(
            id = id.ifBlank { java.util.UUID.randomUUID().toString() },
            classDistribution = classDistribution.mapNotNull { (key, value) ->
                try { ClassTier.valueOf(key) to value } catch (_: Exception) { null }
            }.toMap().ifEmpty { mapOf(ClassTier.KEY to 2, ClassTier.NORMAL to 4) },
            subjectTrack = try { SubjectTrack.valueOf(subjectTrack) } catch (_: Exception) { SubjectTrack.DEFAULT },
            intensity = try { TeachingIntensity.valueOf(intensity) } catch (_: Exception) { TeachingIntensity.DEFAULT },
            schedulePolicies = schedulePolicies.mapNotNull { name ->
                try { SchedulePolicy.valueOf(name) } catch (_: Exception) { null }
            }.toSet(),
            specialPrograms = specialPrograms.mapNotNull { name ->
                try { SpecialProgram.valueOf(name) } catch (_: Exception) { null }
            }.toSet(),
            scienceToArtsRatio = scienceToArtsRatio,
            weeklyPEHours = weeklyPEHours,
            monthlyExamFrequency = monthlyExamFrequency
        )
    }

    companion object {
        fun fromConfig(config: TeachingConfig): TeachingConfigDto {
            return TeachingConfigDto(
                id = config.id,
                classDistribution = config.classDistribution.map { (tier, count) -> tier.name to count }.toMap(),
                subjectTrack = config.subjectTrack.name,
                intensity = config.intensity.name,
                schedulePolicies = config.schedulePolicies.map { it.name },
                specialPrograms = config.specialPrograms.map { it.name },
                scienceToArtsRatio = config.scienceToArtsRatio,
                weeklyPEHours = config.weeklyPEHours,
                monthlyExamFrequency = config.monthlyExamFrequency
            )
        }
    }
}
