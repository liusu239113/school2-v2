package com.arktools.xiao.domain.repository

import com.arktools.xiao.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * 教学配置 Repository - 管理学校的班型分配、教学强度、作息政策和特殊项目
 */
interface TeachingConfigRepository {
    /** 获取当前教学配置（响应式） */
    fun getConfigFlow(): Flow<TeachingConfig>

    /** 获取当前教学配置（一次性） */
    suspend fun getConfig(): TeachingConfig

    /** 保存/更新教学配置 */
    suspend fun saveConfig(config: TeachingConfig)

    /** 更新班型分配 */
    suspend fun updateClassDistribution(distribution: Map<ClassTier, Int>)

    /** 设置教学强度 */
    suspend fun setIntensity(intensity: TeachingIntensity)

    /** 设置文理方向 */
    suspend fun setSubjectTrack(track: SubjectTrack)

    /** 设置文理比例 */
    suspend fun setScienceRatio(ratio: Float)

    /** 启用/禁用作息政策 */
    suspend fun toggleSchedulePolicy(policy: SchedulePolicy, enabled: Boolean)

    /** 开设特殊项目 */
    suspend fun addSpecialProgram(program: SpecialProgram)

    /** 关闭特殊项目 */
    suspend fun removeSpecialProgram(program: SpecialProgram)

    /** 设置每周体育课时 */
    suspend fun setWeeklyPEHours(hours: Int)

    /** 设置阶段考核频率 */
    suspend fun setMonthlyExamFrequency(frequency: Int)

    /** 重置为默认配置 */
    suspend fun resetToDefault()
}
