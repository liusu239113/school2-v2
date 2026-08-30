package com.arktools.xiaozhang.domain.milestone

import com.arktools.xiaozhang.domain.model.School

/**
 * 里程碑系统 —— 学校大亨2 核心进度追踪
 *
 * 里程碑有进度条（不同于一次性解锁的 Achievement），
 * 用于给予玩家中长期目标感和成就方向。
 *
 * 里程碑分阶段: 每个大里程碑包含多个小目标。
 */
data class Milestone(
    val id: String,
    val title: String,
    val description: String,
    val category: MilestoneCategory,
    val stages: List<MilestoneStage>,
    var currentStageIndex: Int = 0,
    var completed: Boolean = false,
    var completedTime: Long = 0
) {
    val currentStage: MilestoneStage? get() = stages.getOrNull(currentStageIndex)
    val progress: Float get() {
        if (completed) return 1f
        val stageProgress = stages.size.toFloat()
        return (currentStageIndex.toFloat() + (currentStage?.progress ?: 0f)) / stageProgress
    }
    val isLastStage: Boolean get() = currentStageIndex >= stages.size - 1
}

data class MilestoneStage(
    val targetValue: Long,
    val rewardDescription: String,
    val rewardCash: Double = 0.0,
    val rewardReputation: Long = 0,
    var currentValue: Long = 0,
    var achieved: Boolean = false
) {
    val progress: Float get() = if (targetValue > 0) {
        (currentValue.toFloat() / targetValue).coerceIn(0f, 1f)
    } else 0f
}

enum class MilestoneCategory(val displayName: String, val emoji: String) {
    ENROLLMENT("招生规模", "👨‍🎓"),
    REVENUE("收入目标", "💰"),
    REPUTATION("声誉等级", "⭐"),
    CAMPUS("校区发展", "🏫"),
    GRADUATION("毕业生", "🎓")
}

/**
 * 里程碑注册表 - 预定义所有里程碑和阶段
 */
object MilestoneRegistry {

    fun getAllMilestones(): List<Milestone> = listOf(
        // 招生规模里程碑（奖励是锦上添花，不是一步登天）
        Milestone(
            id = "enrollment_scale",
            title = "桃李满天下",
            description = "累计在读学生人数",
            category = MilestoneCategory.ENROLLMENT,
            stages = listOf(
                MilestoneStage(10, "招收10名学生", rewardCash = 5.0, rewardReputation = 10),
                MilestoneStage(50, "招收50名学生", rewardCash = 15.0, rewardReputation = 30),
                MilestoneStage(200, "招收200名学生", rewardCash = 40.0, rewardReputation = 80),
                MilestoneStage(1000, "招收1000名学生", rewardCash = 80.0, rewardReputation = 150),
                MilestoneStage(3000, "招收3000名学生", rewardCash = 150.0, rewardReputation = 300),
                MilestoneStage(8000, "招收8000名学生", rewardCash = 250.0, rewardReputation = 500)
            )
        ),

        // 收入目标里程碑（纯声望奖励，不再给钱打破经济）
        Milestone(
            id = "revenue_target",
            title = "财源滚滚",
            description = "累计总收入",
            category = MilestoneCategory.REVENUE,
            stages = listOf(
                MilestoneStage(100, "累计收入100万", rewardReputation = 20),
                MilestoneStage(500, "累计收入500万", rewardReputation = 50),
                MilestoneStage(2000, "累计收入2000万", rewardReputation = 120),
                MilestoneStage(10000, "累计收入1亿", rewardReputation = 300),
                MilestoneStage(50000, "累计收入5亿", rewardReputation = 600)
            )
        ),

        // 声誉等级里程碑
        Milestone(
            id = "reputation_level",
            title = "口碑之路",
            description = "学校声誉值",
            category = MilestoneCategory.REPUTATION,
            stages = listOf(
                MilestoneStage(200, "声誉达到200", rewardCash = 10.0),
                MilestoneStage(800, "声誉达到800", rewardCash = 30.0),
                MilestoneStage(3000, "声誉达到3000", rewardCash = 60.0),
                MilestoneStage(10000, "声誉达到10000", rewardCash = 100.0),
                MilestoneStage(50000, "声誉达到50000", rewardCash = 200.0)
            )
        ),

        // 校区发展里程碑
        Milestone(
            id = "campus_growth",
            title = "学府崛起",
            description = "校舍等级",
            category = MilestoneCategory.CAMPUS,
            stages = listOf(
                MilestoneStage(2, "校园升级到2级", rewardCash = 10.0, rewardReputation = 20),
                MilestoneStage(3, "校园升级到3级", rewardCash = 25.0, rewardReputation = 50),
                MilestoneStage(4, "校园升级到4级", rewardCash = 50.0, rewardReputation = 100),
                MilestoneStage(5, "校园升级到5级", rewardCash = 100.0, rewardReputation = 200),
                MilestoneStage(6, "校园升级到6级", rewardCash = 200.0, rewardReputation = 500)
            )
        ),

        // 毕业生里程碑
        Milestone(
            id = "graduation_count",
            title = "英才辈出",
            description = "累计毕业学生数量",
            category = MilestoneCategory.GRADUATION,
            stages = listOf(
                MilestoneStage(5, "培养5名毕业生", rewardCash = 3.0, rewardReputation = 10),
                MilestoneStage(30, "培养30名毕业生", rewardCash = 15.0, rewardReputation = 40),
                MilestoneStage(100, "培养100名毕业生", rewardCash = 40.0, rewardReputation = 100),
                MilestoneStage(500, "培养500名毕业生", rewardCash = 80.0, rewardReputation = 250),
                MilestoneStage(2000, "培养2000名毕业生", rewardCash = 150.0, rewardReputation = 500)
            )
        )
    )
}
