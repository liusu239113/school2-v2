package com.arktools.xiaozhang.domain.milestone

import com.arktools.xiaozhang.domain.model.School
import com.arktools.xiaozhang.domain.repository.StudentRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 里程碑管理器 —— 管理里程碑进度检测和奖励发放
 */
@Singleton
class MilestoneManager @Inject constructor(
    private val studentRepository: StudentRepository
) {

    private val milestones = MilestoneRegistry.getAllMilestones().toMutableList()

    private val _milestoneState = MutableStateFlow<List<Milestone>>(milestones.toList())
    val milestoneState: StateFlow<List<Milestone>> = _milestoneState.asStateFlow()

    private val _milestoneCompleted = MutableSharedFlow<MilestoneStageCompletion>()
    val milestoneCompleted: SharedFlow<MilestoneStageCompletion> = _milestoneCompleted.asSharedFlow()

    /**
     * 每月检查里程碑进度 (在GameEngine月结算时调用)
     * @return 本次触发的所有阶段完成奖励
     */
    suspend fun checkMilestones(school: School): List<MilestoneStageCompletion> {
        val completions = mutableListOf<MilestoneStageCompletion>()
        val activeStudentCount = studentRepository.getActiveStudentCount()
        val graduateCount = studentRepository.getGraduateCount()

        milestones.forEach { milestone ->
            if (milestone.completed) return@forEach

            val currentStage = milestone.currentStage ?: return@forEach

            // 更新当前值
            val currentValue = getMilestoneCurrentValue(milestone, school, activeStudentCount, graduateCount)
            currentStage.currentValue = currentValue

            // 检查是否达标
            if (currentValue >= currentStage.targetValue && !currentStage.achieved) {
                currentStage.achieved = true

                val completion = MilestoneStageCompletion(
                    milestoneId = milestone.id,
                    milestoneTitle = milestone.title,
                    stageDescription = currentStage.rewardDescription,
                    stageIndex = milestone.currentStageIndex,
                    totalStages = milestone.stages.size,
                    rewardCash = currentStage.rewardCash,
                    rewardReputation = currentStage.rewardReputation
                )
                completions.add(completion)
                _milestoneCompleted.emit(completion)

                // 进入下一阶段或完成
                if (milestone.isLastStage) {
                    milestone.completed = true
                    milestone.completedTime = System.currentTimeMillis()
                } else {
                    milestone.currentStageIndex++
                    // 更新新阶段的当前值
                    milestone.currentStage?.currentValue = currentValue
                }
            }
        }

        _milestoneState.value = milestones.toList()
        return completions
    }

    /**
     * 根据里程碑类型获取当前进度值
     */
    private fun getMilestoneCurrentValue(
        milestone: Milestone,
        school: School,
        activeStudentCount: Int,
        graduateCount: Int
    ): Long {
        return when (milestone.id) {
            "enrollment_scale" -> activeStudentCount.toLong()
            "revenue_target" -> school.totalRevenue.toLong()
            "reputation_level" -> school.reputation
            "campus_growth" -> school.campusLevel.toLong()
            "graduation_count" -> graduateCount.toLong()
            else -> 0L
        }
    }

    /**
     * 获取整体里程碑完成进度
     */
    fun getOverallProgress(): Float {
        val totalStages = milestones.sumOf { it.stages.size }
        val completedStages = milestones.sumOf { milestone ->
            milestone.stages.count { it.achieved }
        }
        return if (totalStages > 0) completedStages.toFloat() / totalStages else 0f
    }

    /**
     * 获取指定分类的里程碑
     */
    fun getMilestonesByCategory(category: MilestoneCategory): List<Milestone> {
        return milestones.filter { it.category == category }
    }

    /**
     * 重置所有里程碑 (新游戏)
     */
    fun reset() {
        val fresh = MilestoneRegistry.getAllMilestones()
        milestones.clear()
        milestones.addAll(fresh)
        _milestoneState.value = milestones.toList()
    }

    fun toJson(): String {
        return try {
            val data = MilestonePersistData(
                progress = milestones.map { m ->
                    MilestoneProgress(
                        id = m.id,
                        currentStageIndex = m.currentStageIndex,
                        stageValues = m.stages.map { it.currentValue },
                        stageAchieved = m.stages.map { it.achieved },
                        completed = m.completed,
                        completedTime = m.completedTime
                    )
                }
            )
            Json.encodeToString(data)
        } catch (_: Exception) { "" }
    }

    fun restoreFromJson(json: String) {
        if (json.isBlank()) return
        try {
            val data = Json { ignoreUnknownKeys = true }.decodeFromString<MilestonePersistData>(json)
            data.progress.forEach { mp ->
                milestones.find { it.id == mp.id }?.let { milestone ->
                    milestone.currentStageIndex = mp.currentStageIndex
                    milestone.completed = mp.completed
                    milestone.completedTime = mp.completedTime
                    mp.stageValues.forEachIndexed { index, value ->
                        if (index < milestone.stages.size) {
                            milestone.stages[index].currentValue = value
                        }
                    }
                    mp.stageAchieved.forEachIndexed { index, achieved ->
                        if (index < milestone.stages.size) {
                            milestone.stages[index].achieved = achieved
                        }
                    }
                }
            }
            _milestoneState.value = milestones.toList()
        } catch (e: Exception) {
            throw IllegalArgumentException("MilestoneManager.restoreFromJson failed", e)
        }
    }
}

@Serializable
data class MilestonePersistData(
    val progress: List<MilestoneProgress> = emptyList()
)

@Serializable
data class MilestoneProgress(
    val id: String,
    val currentStageIndex: Int = 0,
    val stageValues: List<Long> = emptyList(),
    val stageAchieved: List<Boolean> = emptyList(),
    val completed: Boolean = false,
    val completedTime: Long = 0L
)

/**
 * 里程碑阶段完成事件数据
 */
data class MilestoneStageCompletion(
    val milestoneId: String,
    val milestoneTitle: String,
    val stageDescription: String,
    val stageIndex: Int,
    val totalStages: Int,
    val rewardCash: Double,
    val rewardReputation: Long
)
