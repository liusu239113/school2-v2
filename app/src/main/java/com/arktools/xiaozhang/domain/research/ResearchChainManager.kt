package com.arktools.xiaozhang.domain.research

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 科研课题链：多阶段长线研究。
 * - 三条课题链（教学精进 / 应用科研 / 产学研合作），每条三个阶段
 * - 报名扣启动经费，之后每天由引擎的科研日推进累计进度
 * - 阶段完成发奖金/声誉，教学链的已完成阶段永久提高教学质量系数
 * - 状态内嵌 policyJson 持久化，不改数据库结构
 */
@Singleton
class ResearchChainManager @Inject constructor() {

    @Serializable
    data class ChainStage(
        val name: String,
        val description: String,
        val requiredDays: Int,
        val startFeeWan: Double,
        val rewardCashWan: Double,
        val rewardReputation: Long,
        val rewardQuality: Float
    )

    @Serializable
    data class ChainDef(
        val id: String,
        val name: String,
        val description: String,
        val stages: List<ChainStage>
    )

    @Serializable
    data class ChainProgress(
        val chainId: String,
        val stageIndex: Int,
        val daysDone: Int,
        val roundIndex: Int = 0
    )

    @Serializable
    data class ManagerState(
        val programs: Map<String, ChainProgress> = emptyMap(),
        val completedChains: List<String> = emptyList(),
        val completedRounds: Map<String, Int> = emptyMap()
    )

    data class StageCompletion(
        val chain: ChainDef,
        val stageIndex: Int,
        val stage: ChainStage,
        val chainFinished: Boolean
    )

    data class StartResult(
        val success: Boolean,
        val message: String,
        val fee: Double
    )

    private var state = ManagerState()

    /** 阶段完成产生的待入账奖励（月结时统一计入收入报表） */
    private var pendingCashWan = 0.0
    private var pendingReputation = 0L

    fun consumePendingRewards(): Pair<Double, Long> {
        val result = pendingCashWan to pendingReputation
        pendingCashWan = 0.0
        pendingReputation = 0L
        return result
    }

    fun definitions(): List<ChainDef> = DEFS

    fun snapshotState(): ManagerState = state

    fun toJson(): String = runCatching { Json.encodeToString(state) }.getOrDefault("")

    fun restoreFromJson(json: String) {
        if (json.isBlank()) return
        runCatching { state = Json.decodeFromString<ManagerState>(json) }
    }

    fun reset() {
        state = ManagerState()
    }

    fun startProgram(
        chainId: String,
        cash: Double,
        campusLevel: Int
    ): StartResult {
        val def = DEFS.firstOrNull { it.id == chainId }
            ?: return StartResult(false, "课题链不存在", 0.0)
        val round = completedRoundCount(chainId)
        val stage = scaledStage(def.stages.first(), round)
        if (state.programs.containsKey(chainId)) {
            return StartResult(false, "${def.name}正在进行中", 0.0)
        }
        if (campusLevel < (CHAIN_UNLOCK_LEVEL[chainId] ?: 1)) {
            return StartResult(
                false,
                "${def.name}需要校园${CHAIN_UNLOCK_LEVEL[chainId]}级",
                0.0
            )
        }
        if (cash < stage.startFeeWan) {
            return StartResult(
                false,
                "启动经费不足：需要 ${stage.startFeeWan.toInt()}万",
                0.0
            )
        }
        state = state.copy(
            programs = state.programs + (chainId to ChainProgress(chainId, 0, 0, round))
        )
        val roundLabel = if (round == 0) "第一轮" else "第${round + 1}轮"
        return StartResult(
            true,
            "已启动${def.name}${roundLabel}「${stage.name}」，每天随科研推进，约${stage.requiredDays}天完成",
            stage.startFeeWan
        )
    }

    /** 每个科研日调用：推进所有在研课题，返回本日完成的阶段 */
    fun advanceDay(): List<StageCompletion> {
        if (state.programs.isEmpty()) return emptyList()
        val completions = mutableListOf<StageCompletion>()
        val updated = mutableMapOf<String, ChainProgress>()
        state.programs.forEach { (chainId, progress) ->
            val def = DEFS.firstOrNull { it.id == chainId } ?: return@forEach
            val rawStage = def.stages.getOrNull(progress.stageIndex) ?: return@forEach
            val round = progress.roundIndex
            val stage = scaledStage(rawStage, round)
            val days = progress.daysDone + 1
            if (days >= stage.requiredDays) {
                pendingCashWan += stage.rewardCashWan
                pendingReputation += stage.rewardReputation
                val isLast = progress.stageIndex >= def.stages.lastIndex
                completions.add(
                    StageCompletion(def, progress.stageIndex, stage, isLast)
                )
                if (isLast) {
                    updated.remove(chainId)
                } else {
                    updated[chainId] = ChainProgress(
                        chainId,
                        progress.stageIndex + 1,
                        0,
                        round
                    )
                }
            } else {
                updated[chainId] = progress.copy(daysDone = days)
            }
        }
        val newCompleted = completions.filter { it.chainFinished }.map { it.chain.id }
        val rounds = state.completedRounds.toMutableMap()
        newCompleted.forEach { id ->
            rounds[id] = completedRoundCount(id) + 1
        }
        state = state.copy(
            programs = updated,
            completedChains = (state.completedChains + newCompleted).distinct(),
            completedRounds = rounds
        )
        return completions
    }

    /** 已完成阶段累计的教学质量加成（永久） */
    fun qualityBonus(): Float {
        var bonus = 0f
        DEFS.forEach { def ->
            val rounds = completedRoundCount(def.id)
            if (rounds > 0) {
                def.stages.forEach { stage ->
                    bonus += stage.rewardQuality
                    if (rounds > 1) bonus += stage.rewardQuality * 0.4f * (rounds - 1)
                }
            }
        }
        state.programs.values.forEach { progress ->
            val def = DEFS.firstOrNull { it.id == progress.chainId } ?: return@forEach
            for (i in 0 until progress.stageIndex) {
                bonus += scaledStage(def.stages[i], progress.roundIndex).rewardQuality
            }
        }
        return bonus
    }

    fun progressSummary(): String {
        if (state.programs.isEmpty() && completedRoundCountAll() == 0) {
            return "尚未启动任何课题链"
        }
        val parts = mutableListOf<String>()
        DEFS.forEach { def ->
            val rounds = completedRoundCount(def.id)
            if (rounds > 0 && !state.programs.containsKey(def.id)) {
                parts.add("${def.name}已完成${rounds}轮，可开第${rounds + 1}轮")
            }
        }
        state.programs.values.forEach { p ->
            val def = DEFS.firstOrNull { it.id == p.chainId } ?: return@forEach
            val stage = scaledStage(def.stages[p.stageIndex], p.roundIndex)
            parts.add("${def.name}第${p.roundIndex + 1}轮·${stage.name} ${p.daysDone}/${stage.requiredDays}天")
        }
        return parts.joinToString("；")
    }

    fun completedRoundCount(chainId: String): Int {
        val stored = state.completedRounds[chainId]
        if (stored != null) return stored
        return if (state.completedChains.contains(chainId)) 1 else 0
    }

    private fun completedRoundCountAll(): Int = DEFS.sumOf { completedRoundCount(it.id) }

    /** 是否至少结题过一轮（用于校园 Lv6 升级门槛） */
    fun anyCompletedRound(): Boolean = completedRoundCountAll() > 0

    companion object {
        val CHAIN_UNLOCK_LEVEL = mapOf("TEACHING" to 1, "APPLIED" to 2, "INDUSTRY" to 3)

        fun scaledStage(stage: ChainStage, roundIndex: Int): ChainStage {
            val bump = 1.0 + roundIndex * 0.35
            val dayBump = 1.0 + roundIndex * 0.20
            return stage.copy(
                requiredDays = (stage.requiredDays * dayBump).toInt().coerceAtLeast(stage.requiredDays),
                startFeeWan = stage.startFeeWan * bump,
                rewardCashWan = stage.rewardCashWan * bump,
                rewardReputation = (stage.rewardReputation * bump).toLong(),
                rewardQuality = if (roundIndex == 0) stage.rewardQuality else stage.rewardQuality * 0.4f
            )
        }

        private val DEFS = listOf(
            ChainDef(
                "TEACHING", "教学精进链", "打磨课堂与课程体系，每阶段永久提高教学质量。",
                listOf(
                    ChainStage("集体备课制度化", "统一教案与听评课", 30, 20.0, 0.0, 20L, 0.02f),
                    ChainStage("课程思政建设", "课程与育人融合", 45, 35.0, 0.0, 40L, 0.03f),
                    ChainStage("一流课程培育", "打造校级金课", 60, 60.0, 20.0, 80L, 0.04f)
                )
            ),
            ChainDef(
                "APPLIED", "应用科研链", "面向产业需求的横向课题，阶段完成发放科研到账经费。",
                listOf(
                    ChainStage("市级横向课题", "与企业联合立项", 40, 30.0, 40.0, 30L, 0.0f),
                    ChainStage("省级重点课题", "进入省级科研序列", 60, 50.0, 80.0, 60L, 0.0f),
                    ChainStage("成果转化落地", "专利与成果转化", 80, 90.0, 150.0, 120L, 0.0f)
                )
            ),
            ChainDef(
                "INDUSTRY", "产学研合作链", "共建实验室与产业学院，长期带来声誉与经费。",
                listOf(
                    ChainStage("校企联合实验室", "共建首个联合平台", 50, 40.0, 60.0, 40L, 0.01f),
                    ChainStage("现代产业学院", "行业订单式培养", 75, 70.0, 120.0, 80L, 0.01f),
                    ChainStage("国家级平台申报", "冲击国家级平台", 100, 120.0, 220.0, 160L, 0.02f)
                )
            )
        )
    }
}
