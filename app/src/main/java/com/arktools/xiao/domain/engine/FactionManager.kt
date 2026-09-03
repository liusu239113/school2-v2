package com.arktools.xiao.domain.engine

import com.arktools.xiao.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID
import kotlin.random.Random

data class FactionRuntimeSnapshot(
    val dissatisfactionCooldowns: Map<FactionType, Int>,
    val resolvedFactionEventIds: Set<String>
)

/**
 * 学校内部派系系统管理器
 * 管理教学派、行政派、改革派、保守派之间的博弈关系
 * 校长需要在各派系间保持平衡，任何一方过于强势或不满都会引发问题
 */
@Singleton
class FactionManager @Inject constructor() {

    companion object {
        /** 同一派系不满事件在后续四次月度更新内不可再次触发（仅当前运行期有效）。 */
        private const val DISSATISFACTION_COOLDOWN_UPDATES = 4
    }

    /**
     * 运行期状态：FactionManager 是 Singleton，因此可防止同一次游戏运行中同派系反复触发不满事件。
     * 不写入 Principal，避免在本次修复中扩大存档模型和兼容性范围。
     */
    private val dissatisfactionCooldowns = mutableMapOf<FactionType, Int>()

    /** 已结算事件的 ID；relationChanges 只能由 [applyFactionEventChoice] 应用一次。 */
    private val resolvedFactionEventIds = mutableSetOf<String>()

    fun snapshotRuntime(): FactionRuntimeSnapshot = FactionRuntimeSnapshot(
        dissatisfactionCooldowns = dissatisfactionCooldowns.toMap(),
        resolvedFactionEventIds = resolvedFactionEventIds.toSet()
    )

    fun restoreRuntime(snapshot: FactionRuntimeSnapshot) {
        dissatisfactionCooldowns.clear()
        dissatisfactionCooldowns.putAll(snapshot.dissatisfactionCooldowns)
        resolvedFactionEventIds.clear()
        resolvedFactionEventIds.addAll(snapshot.resolvedFactionEventIds)
    }

    /**
     * 派系动态 - 每月派系力量变化和互动
     */
    fun monthlyUpdate(principal: Principal, school: School): List<FactionEvent> {
        val events = mutableListOf<FactionEvent>()

        // 不满事件冷却按月推进；本月刚触发的事件从下次 monthlyUpdate 开始计数。
        advanceDissatisfactionCooldowns()

        // 自然关系衰减/恢复（趋向50）
        naturalDrift(principal)

        // 派系间冲突检测
        val conflict = checkFactionConflict(principal, school)
        if (conflict != null) events.add(conflict)

        // 派系满意度事件
        val satisfactionEvent = checkSatisfaction(principal, school)
        if (satisfactionEvent != null) events.add(satisfactionEvent)

        // 派系权力博弈
        val powerPlay = checkPowerPlay(principal, school)
        if (powerPlay != null) events.add(powerPlay)

        return events
    }

    /**
     * 应用派系事件中玩家选项的关系变化。
     *
     * 该方法是 FactionEventChoice.relationChanges 的唯一写入入口：同一 eventId
     * 无论被手动选择、自动处理或重复回调多少次，都只会成功应用一次。
     *
     * @return 仅在 choiceIndex 有效且该事件尚未结算时返回 true。
     */
    fun applyFactionEventChoice(
        principal: Principal,
        event: FactionEvent,
        choiceIndex: Int
    ): Boolean {
        val choice = event.choices.getOrNull(choiceIndex) ?: return false
        if (!resolvedFactionEventIds.add(event.id)) return false

        choice.relationChanges.forEach { (faction, delta) ->
            adjustRelation(principal, faction, delta)
        }
        return true
    }

    /**
     * 执行校长的派系操作
     */
    fun executeFactionAction(
        principal: Principal,
        school: School,
        action: FactionAction
    ): FactionActionResult {
        val currentRelation = principal.factionRelations[action.targetFaction] ?: 50

        when (action.type) {
            FactionActionType.APPEASE -> {
                // 安抚派系：目标+15~25，对立派系-5~10
                val gain = Random.nextInt(15, 26)
                principal.factionRelations[action.targetFaction] =
                    (currentRelation + gain).coerceAtMost(100)

                val opposingFaction = getOpposingFaction(action.targetFaction)
                val opposingRelation = principal.factionRelations[opposingFaction] ?: 50
                val loss = Random.nextInt(5, 11)
                principal.factionRelations[opposingFaction] =
                    (opposingRelation - loss).coerceAtLeast(0)

                return FactionActionResult(
                    success = true,
                    message = "${action.targetFaction.displayName}对你的重视表示满意，关系改善了。",
                    relationChanges = mapOf(
                        action.targetFaction to gain,
                        opposingFaction to -loss
                    )
                )
            }

            FactionActionType.SUPPRESS -> {
                // 打压派系：目标-20~30，但有反噬风险
                val suppression = Random.nextInt(20, 31)
                principal.factionRelations[action.targetFaction] =
                    (currentRelation - suppression).coerceAtLeast(0)

                // 30%概率引发反弹
                if (Random.nextFloat() < 0.3f) {
                    val backlash = Random.nextInt(10, 20)
                    school.reputation = (school.reputation - backlash * 50L).coerceAtLeast(0)
                    return FactionActionResult(
                        success = false,
                        message = "${action.targetFaction.displayName}强烈反弹！有人向董事会反映学校管理问题。",
                        relationChanges = mapOf(action.targetFaction to -suppression),
                        reputationChange = -(backlash * 50).toLong()
                    )
                }

                return FactionActionResult(
                    success = true,
                    message = "成功压制了${action.targetFaction.displayName}的影响力。",
                    relationChanges = mapOf(action.targetFaction to -suppression)
                )
            }

            FactionActionType.ALLY -> {
                // 结盟：目标+30~40，花费个人资金和声望
                if (principal.personalFunds < 3.0) {
                    return FactionActionResult(
                        success = false,
                        message = "个人资金不足，无法拉拢派系核心人物。"
                    )
                }
                principal.personalFunds -= 3.0
                val gain = Random.nextInt(30, 41)
                principal.factionRelations[action.targetFaction] =
                    (currentRelation + gain).coerceAtMost(100)

                // 其他所有派系略有不满
                FactionType.entries.filter { it != action.targetFaction }.forEach { faction ->
                    val rel = principal.factionRelations[faction] ?: 50
                    principal.factionRelations[faction] = (rel - Random.nextInt(3, 8)).coerceAtLeast(0)
                }

                return FactionActionResult(
                    success = true,
                    message = "与${action.targetFaction.displayName}核心人物建立了紧密关系。",
                    relationChanges = mapOf(action.targetFaction to gain),
                    personalFundsCost = 3.0
                )
            }

            FactionActionType.MEDIATE -> {
                // 调解两个对立派系：双方各+10~15
                val opposingFaction = getOpposingFaction(action.targetFaction)
                val gainA = Random.nextInt(10, 16)
                val gainB = Random.nextInt(10, 16)
                principal.factionRelations[action.targetFaction] =
                    (currentRelation + gainA).coerceAtMost(100)
                val opposingRel = principal.factionRelations[opposingFaction] ?: 50
                principal.factionRelations[opposingFaction] =
                    (opposingRel + gainB).coerceAtMost(100)

                // 校长个人声望+5
                principal.personalReputation = (principal.personalReputation + 5).coerceAtMost(100)

                return FactionActionResult(
                    success = true,
                    message = "成功调解了${action.targetFaction.displayName}与${opposingFaction.displayName}的矛盾。",
                    relationChanges = mapOf(
                        action.targetFaction to gainA,
                        opposingFaction to gainB
                    )
                )
            }

            FactionActionType.REFORM_PUSH -> {
                // 推动改革：改革派+20，保守派-15，有50%概率获得声誉
                val reformGain = 20
                val conservLoss = 15
                principal.factionRelations[FactionType.REFORM] =
                    ((principal.factionRelations[FactionType.REFORM] ?: 50) + reformGain).coerceAtMost(100)
                principal.factionRelations[FactionType.CONSERVATIVE] =
                    ((principal.factionRelations[FactionType.CONSERVATIVE] ?: 50) - conservLoss).coerceAtLeast(0)

                val reputationGain = if (Random.nextFloat() < 0.5f) 500L else 0L
                if (reputationGain > 0) school.reputation += reputationGain

                return FactionActionResult(
                    success = true,
                    message = if (reputationGain > 0)
                        "改革取得初步成效，学校活力提升！"
                    else
                        "改革推进中，效果尚需时间验证。",
                    relationChanges = mapOf(
                        FactionType.REFORM to reformGain,
                        FactionType.CONSERVATIVE to -conservLoss
                    ),
                    reputationChange = reputationGain
                )
            }

            FactionActionType.STABILIZE -> {
                // 维稳：保守派+20，改革派-10，降低出事概率
                val conservGain = 20
                val reformLoss = 10
                principal.factionRelations[FactionType.CONSERVATIVE] =
                    ((principal.factionRelations[FactionType.CONSERVATIVE] ?: 50) + conservGain).coerceAtMost(100)
                principal.factionRelations[FactionType.REFORM] =
                    ((principal.factionRelations[FactionType.REFORM] ?: 50) - reformLoss).coerceAtLeast(0)

                return FactionActionResult(
                    success = true,
                    message = "学校秩序稳定，元老们表示认可。",
                    relationChanges = mapOf(
                        FactionType.CONSERVATIVE to conservGain,
                        FactionType.REFORM to -reformLoss
                    )
                )
            }
        }
    }

    /**
     * 获取当前可用的派系操作
     */
    fun getAvailableActions(principal: Principal, school: School): List<FactionAction> {
        if (principal.isSuspended) return emptyList()

        val actions = mutableListOf<FactionAction>()

        FactionType.entries.forEach { faction ->
            val relation = principal.factionRelations[faction] ?: 50

            // 安抚 - 关系<70时可用
            if (relation < 70) {
                actions.add(FactionAction(
                    type = FactionActionType.APPEASE,
                    targetFaction = faction,
                    description = "安抚${faction.displayName}：召开座谈会听取意见",
                    personalCost = 0.0
                ))
            }

            // 打压 - 关系>30时可用（太低了打压没意义）
            if (relation > 30) {
                actions.add(FactionAction(
                    type = FactionActionType.SUPPRESS,
                    targetFaction = faction,
                    description = "打压${faction.displayName}：调整其核心人物岗位",
                    personalCost = 0.0,
                    riskDescription = "可能引发反弹"
                ))
            }

            // 结盟 - 个人资金>=3万且关系<80
            if (principal.personalFunds >= 3.0 && relation < 80) {
                actions.add(FactionAction(
                    type = FactionActionType.ALLY,
                    targetFaction = faction,
                    description = "拉拢${faction.displayName}核心人物（花费个人资金3万）",
                    personalCost = 3.0
                ))
            }

            // 调解 - 任一对立派系关系差距>30
            val opposing = getOpposingFaction(faction)
            val opposingRel = principal.factionRelations[opposing] ?: 50
            if (kotlin.math.abs(relation - opposingRel) > 30) {
                actions.add(FactionAction(
                    type = FactionActionType.MEDIATE,
                    targetFaction = faction,
                    description = "调解${faction.displayName}与${opposing.displayName}的矛盾",
                    personalCost = 0.0
                ))
            }
        }

        // 全局操作
        if ((principal.factionRelations[FactionType.REFORM] ?: 50) >= 40) {
            actions.add(FactionAction(
                type = FactionActionType.REFORM_PUSH,
                targetFaction = FactionType.REFORM,
                description = "推动教学改革（改革派支持，保守派反对）",
                personalCost = 0.0,
                riskDescription = "可能引起保守派不满"
            ))
        }

        if ((principal.factionRelations[FactionType.CONSERVATIVE] ?: 50) >= 40) {
            actions.add(FactionAction(
                type = FactionActionType.STABILIZE,
                targetFaction = FactionType.CONSERVATIVE,
                description = "强调稳定发展（保守派支持，改革派不满）",
                personalCost = 0.0
            ))
        }

        return actions
    }

    /**
     * 根据学校决策自动调整派系关系
     * 在各个Manager执行操作后调用
     */
    fun onSchoolDecision(principal: Principal, decision: SchoolDecision) {
        when (decision) {
            SchoolDecision.HIRE_EXPENSIVE_TEACHER -> {
                adjustRelation(principal, FactionType.TEACHING, 8)
                adjustRelation(principal, FactionType.ADMINISTRATIVE, -3)
            }
            SchoolDecision.FIRE_TEACHER -> {
                adjustRelation(principal, FactionType.TEACHING, -15)
                adjustRelation(principal, FactionType.ADMINISTRATIVE, 5)
            }
            SchoolDecision.BUILD_FACILITY -> {
                adjustRelation(principal, FactionType.ADMINISTRATIVE, 5)
                adjustRelation(principal, FactionType.CONSERVATIVE, -3)
            }
            SchoolDecision.LAUNCH_NEW_COURSE -> {
                adjustRelation(principal, FactionType.REFORM, 8)
                adjustRelation(principal, FactionType.CONSERVATIVE, -5)
            }
            SchoolDecision.CANCEL_COURSE -> {
                adjustRelation(principal, FactionType.REFORM, -10)
                adjustRelation(principal, FactionType.CONSERVATIVE, 5)
            }
            SchoolDecision.RAISE_TUITION -> {
                adjustRelation(principal, FactionType.TEACHING, -5)
                adjustRelation(principal, FactionType.ADMINISTRATIVE, 8)
            }
            SchoolDecision.MARKETING_CAMPAIGN -> {
                adjustRelation(principal, FactionType.ADMINISTRATIVE, 5)
                adjustRelation(principal, FactionType.TEACHING, -3)
            }
            SchoolDecision.RESEARCH_INVESTMENT -> {
                adjustRelation(principal, FactionType.TEACHING, 10)
                adjustRelation(principal, FactionType.REFORM, 8)
                adjustRelation(principal, FactionType.ADMINISTRATIVE, -5)
            }
            SchoolDecision.EXPAND_CAMPUS -> {
                adjustRelation(principal, FactionType.ADMINISTRATIVE, 10)
                adjustRelation(principal, FactionType.REFORM, 5)
                adjustRelation(principal, FactionType.CONSERVATIVE, -8)
            }
            SchoolDecision.CUT_BUDGET -> {
                adjustRelation(principal, FactionType.TEACHING, -10)
                adjustRelation(principal, FactionType.REFORM, -8)
                adjustRelation(principal, FactionType.ADMINISTRATIVE, 5)
            }
            SchoolDecision.TEACHER_TRAINING -> {
                adjustRelation(principal, FactionType.TEACHING, 12)
                adjustRelation(principal, FactionType.REFORM, 5)
            }
            SchoolDecision.CLUB_ACTIVITY -> {
                adjustRelation(principal, FactionType.REFORM, 5)
                adjustRelation(principal, FactionType.CONSERVATIVE, -3)
            }
            SchoolDecision.STRICT_DISCIPLINE -> {
                adjustRelation(principal, FactionType.CONSERVATIVE, 10)
                adjustRelation(principal, FactionType.REFORM, -8)
            }
            SchoolDecision.OPEN_BRANCH -> {
                adjustRelation(principal, FactionType.ADMINISTRATIVE, 15)
                adjustRelation(principal, FactionType.CONSERVATIVE, -10)
                adjustRelation(principal, FactionType.TEACHING, -5)
            }
            SchoolDecision.CORRUPT_ACT -> {
                // 腐败行为被知道的话，全派系关系下降
                adjustRelation(principal, FactionType.TEACHING, -5)
                adjustRelation(principal, FactionType.REFORM, -5)
            }
        }
    }

    /**
     * 获取派系整体状态摘要
     */
    fun getFactionSummary(principal: Principal): FactionSummary {
        val relations = principal.factionRelations
        val avgRelation = relations.values.average().toInt()
        val lowestFaction = relations.minByOrNull { it.value }
        val highestFaction = relations.maxByOrNull { it.value }

        val stability = when {
            relations.values.all { it in 30..70 } -> FactionStability.BALANCED
            relations.values.any { it < 20 } -> FactionStability.CRISIS
            relations.values.any { it < 30 } -> FactionStability.TENSE
            relations.values.any { it > 85 } -> FactionStability.ONE_DOMINANT
            else -> FactionStability.NORMAL
        }

        return FactionSummary(
            averageRelation = avgRelation,
            stability = stability,
            lowestFaction = lowestFaction?.key,
            lowestRelation = lowestFaction?.value ?: 50,
            highestFaction = highestFaction?.key,
            highestRelation = highestFaction?.value ?: 50,
            warningMessage = when (stability) {
                FactionStability.CRISIS -> "${lowestFaction?.key?.displayName}极度不满，随时可能爆发公开对抗！"
                FactionStability.TENSE -> "${lowestFaction?.key?.displayName}关系紧张，需要尽快安抚。"
                FactionStability.ONE_DOMINANT -> "${highestFaction?.key?.displayName}势力过大，其他派系心生不满。"
                else -> null
            }
        )
    }

    // ======== 内部方法 ========

    private fun advanceDissatisfactionCooldowns() {
        dissatisfactionCooldowns.entries.removeAll { (_, remainingUpdates) -> remainingUpdates <= 1 }
        dissatisfactionCooldowns.replaceAll { _, remainingUpdates -> remainingUpdates - 1 }
    }

    private fun isDissatisfactionOnCooldown(faction: FactionType): Boolean =
        (dissatisfactionCooldowns[faction] ?: 0) > 0

    private fun naturalDrift(principal: Principal) {
        principal.factionRelations.forEach { (faction, relation) ->
            // 缓慢趋向50（中立）
            val drift = when {
                relation > 60 -> -Random.nextInt(1, 3)
                relation < 40 -> Random.nextInt(1, 3)
                else -> 0
            }
            principal.factionRelations[faction] = (relation + drift).coerceIn(0, 100)
        }
    }

    private fun checkFactionConflict(principal: Principal, school: School): FactionEvent? {
        // 检查对立派系是否关系差距过大导致内斗
        val teachRelation = principal.factionRelations[FactionType.TEACHING] ?: 50
        val adminRelation = principal.factionRelations[FactionType.ADMINISTRATIVE] ?: 50
        val reformRelation = principal.factionRelations[FactionType.REFORM] ?: 50
        val conservRelation = principal.factionRelations[FactionType.CONSERVATIVE] ?: 50

        // 教学vs行政冲突
        if (kotlin.math.abs(teachRelation - adminRelation) > 40 && Random.nextFloat() < 0.2f) {
            val stronger = if (teachRelation > adminRelation) FactionType.TEACHING else FactionType.ADMINISTRATIVE
            val weaker = if (stronger == FactionType.TEACHING) FactionType.ADMINISTRATIVE else FactionType.TEACHING
            return FactionEvent(
                type = FactionEventType.INTERNAL_CONFLICT,
                title = "教务行政之争",
                message = "${stronger.displayName}在会议上公开批评${weaker.displayName}的工作方式，气氛紧张。",
                affectedFactions = listOf(stronger, weaker),
                reputationImpact = -200L,
                choices = listOf(
                    FactionEventChoice("支持${stronger.displayName}", mapOf(stronger to 10, weaker to -15)),
                    FactionEventChoice("支持${weaker.displayName}", mapOf(weaker to 15, stronger to -10)),
                    FactionEventChoice("各打五十大板", mapOf(stronger to -5, weaker to -5))
                )
            )
        }

        // 改革vs保守冲突
        if (kotlin.math.abs(reformRelation - conservRelation) > 40 && Random.nextFloat() < 0.2f) {
            val stronger = if (reformRelation > conservRelation) FactionType.REFORM else FactionType.CONSERVATIVE
            val weaker = if (stronger == FactionType.REFORM) FactionType.CONSERVATIVE else FactionType.REFORM
            return FactionEvent(
                type = FactionEventType.INTERNAL_CONFLICT,
                title = "路线之争",
                message = "${stronger.displayName}联名提交报告，要求学校采取他们主张的发展方向。",
                affectedFactions = listOf(stronger, weaker),
                reputationImpact = -300L,
                choices = listOf(
                    FactionEventChoice("全力支持改革", mapOf(FactionType.REFORM to 20, FactionType.CONSERVATIVE to -20)),
                    FactionEventChoice("维护传统", mapOf(FactionType.CONSERVATIVE to 20, FactionType.REFORM to -15)),
                    FactionEventChoice("折中方案", mapOf(FactionType.REFORM to 5, FactionType.CONSERVATIVE to 5))
                )
            )
        }

        return null
    }

    private fun checkSatisfaction(principal: Principal, school: School): FactionEvent? {
        // 同一派系在冷却期内不重复触发；其他低关系派系仍可正常触发各自事件。
        val lowestEntry = principal.factionRelations
            .asSequence()
            .filter { (faction, relation) -> relation < 20 && !isDissatisfactionOnCooldown(faction) }
            .minByOrNull { it.value }
            ?: return null

        if (Random.nextFloat() >= 0.3f) return null

        val event = when (lowestEntry.key) {
            FactionType.TEACHING -> FactionEvent(
                type = FactionEventType.DISSATISFACTION,
                title = "教师集体不满",
                message = "多位教学骨干联名递交意见书，表示教学派与校方关系持续紧张，先前提出的诉求尚未得到回应。如不处理，可能有人离职。",
                affectedFactions = listOf(FactionType.TEACHING),
                reputationImpact = -500L,
                choices = listOf(
                    FactionEventChoice("紧急开会安抚（教学派+25）", mapOf(FactionType.TEACHING to 25)),
                    FactionEventChoice("置之不理（教学派-10，可能有人辞职）", mapOf(FactionType.TEACHING to -10))
                )
            )
            FactionType.ADMINISTRATIVE -> FactionEvent(
                type = FactionEventType.DISSATISFACTION,
                title = "行政人员消极怠工",
                message = "行政团队感到被边缘化，工作效率明显下降，学校日常运转出现问题。",
                affectedFactions = listOf(FactionType.ADMINISTRATIVE),
                reputationImpact = -300L,
                choices = listOf(
                    FactionEventChoice("提升行政待遇（行政派+20，花费2万）", mapOf(FactionType.ADMINISTRATIVE to 20)),
                    FactionEventChoice("强硬要求恢复工作", mapOf(FactionType.ADMINISTRATIVE to -5))
                )
            )
            FactionType.REFORM -> FactionEvent(
                type = FactionEventType.DISSATISFACTION,
                title = "年轻教师出走潮",
                message = "几位有创新想法的年轻教师感到学校太保守，开始接触外面的机会。",
                affectedFactions = listOf(FactionType.REFORM),
                reputationImpact = -400L,
                choices = listOf(
                    FactionEventChoice("承诺给予创新空间（改革派+25）", mapOf(FactionType.REFORM to 25)),
                    FactionEventChoice("随他们去", mapOf(FactionType.REFORM to -15))
                )
            )
            FactionType.CONSERVATIVE -> FactionEvent(
                type = FactionEventType.DISSATISFACTION,
                title = "元老级教师施压",
                message = "几位资深元老对学校激进的改革方向公开表态反对，扬言要联名向董事会施压。",
                affectedFactions = listOf(FactionType.CONSERVATIVE),
                reputationImpact = -400L,
                choices = listOf(
                    FactionEventChoice("尊重元老意见（保守派+25）", mapOf(FactionType.CONSERVATIVE to 25)),
                    FactionEventChoice("坚持改革方向（保守派-10，改革派+10）",
                        mapOf(FactionType.CONSERVATIVE to -10, FactionType.REFORM to 10))
                )
            )
        }

        // 事件一经发出即进入冷却，避免等待玩家处理期间再次生成同类事件。
        // 额外的 1 表示当前触发月不计入冷却；后续四次 monthlyUpdate 均保持不可触发。
        dissatisfactionCooldowns[lowestEntry.key] = DISSATISFACTION_COOLDOWN_UPDATES + 1
        return event
    }

    private fun checkPowerPlay(principal: Principal, school: School): FactionEvent? {
        // 某派系关系>85时，该派系试图获取更多权力
        val dominantEntry = principal.factionRelations.maxByOrNull { it.value } ?: return null

        if (dominantEntry.value > 85 && Random.nextFloat() < 0.15f) {
            return when (dominantEntry.key) {
                FactionType.TEACHING -> FactionEvent(
                    type = FactionEventType.POWER_GRAB,
                    title = "教学派要求话语权",
                    message = "教学派提出：所有教学相关决策必须经过他们的委员会审批，实质上要架空校长的部分权力。",
                    affectedFactions = listOf(FactionType.TEACHING),
                    reputationImpact = 0L,
                    choices = listOf(
                        FactionEventChoice("同意让渡部分权力（教学派+10，个人声望-10）",
                            mapOf(FactionType.TEACHING to 10)),
                        FactionEventChoice("坚持校长决策权（教学派-20）",
                            mapOf(FactionType.TEACHING to -20)),
                        FactionEventChoice("设立咨询委员会折中（教学派+5，保持权力）",
                            mapOf(FactionType.TEACHING to 5))
                    )
                )
                FactionType.ADMINISTRATIVE -> FactionEvent(
                    type = FactionEventType.POWER_GRAB,
                    title = "行政扩权",
                    message = "行政派提议设立更多管理层级和审批流程，实质是扩大行政权力。",
                    affectedFactions = listOf(FactionType.ADMINISTRATIVE),
                    reputationImpact = 0L,
                    choices = listOf(
                        FactionEventChoice("批准新流程（行政派+10，效率可能下降）",
                            mapOf(FactionType.ADMINISTRATIVE to 10, FactionType.REFORM to -10)),
                        FactionEventChoice("驳回提议（行政派-15）",
                            mapOf(FactionType.ADMINISTRATIVE to -15)),
                        FactionEventChoice("部分采纳（行政派+5）",
                            mapOf(FactionType.ADMINISTRATIVE to 5))
                    )
                )
                FactionType.REFORM -> FactionEvent(
                    type = FactionEventType.POWER_GRAB,
                    title = "改革派激进计划",
                    message = "改革派提交了一份激进的改造方案：彻底推翻现有教学体系，全面采用新方法论。",
                    affectedFactions = listOf(FactionType.REFORM, FactionType.CONSERVATIVE),
                    reputationImpact = 0L,
                    choices = listOf(
                        FactionEventChoice("全力支持（改革派+15，保守派-25，高风险）",
                            mapOf(FactionType.REFORM to 15, FactionType.CONSERVATIVE to -25)),
                        FactionEventChoice("暂缓执行（改革派-10）",
                            mapOf(FactionType.REFORM to -10)),
                        FactionEventChoice("分步试点（改革派+5，保守派-5）",
                            mapOf(FactionType.REFORM to 5, FactionType.CONSERVATIVE to -5))
                    )
                )
                FactionType.CONSERVATIVE -> FactionEvent(
                    type = FactionEventType.POWER_GRAB,
                    title = "保守派阻挠改变",
                    message = "保守派元老们联合起来，要求叫停所有近期启动的改革项目。",
                    affectedFactions = listOf(FactionType.CONSERVATIVE, FactionType.REFORM),
                    reputationImpact = 0L,
                    choices = listOf(
                        FactionEventChoice("妥协叫停改革（保守派+15，改革派-20）",
                            mapOf(FactionType.CONSERVATIVE to 15, FactionType.REFORM to -20)),
                        FactionEventChoice("坚持改革（保守派-20，改革派+10）",
                            mapOf(FactionType.CONSERVATIVE to -20, FactionType.REFORM to 10)),
                        FactionEventChoice("暂缓新项目但保留已有改革（保守派+5，改革派-5）",
                            mapOf(FactionType.CONSERVATIVE to 5, FactionType.REFORM to -5))
                    )
                )
            }
        }
        return null
    }

    private fun adjustRelation(principal: Principal, faction: FactionType, delta: Int) {
        val current = principal.factionRelations[faction] ?: 50
        principal.factionRelations[faction] = (current + delta).coerceIn(0, 100)
    }

    private fun getOpposingFaction(faction: FactionType): FactionType {
        return when (faction) {
            FactionType.TEACHING -> FactionType.ADMINISTRATIVE
            FactionType.ADMINISTRATIVE -> FactionType.TEACHING
            FactionType.REFORM -> FactionType.CONSERVATIVE
            FactionType.CONSERVATIVE -> FactionType.REFORM
        }
    }
}

// ======== 数据类 ========

enum class FactionActionType {
    APPEASE,       // 安抚
    SUPPRESS,      // 打压
    ALLY,          // 结盟
    MEDIATE,       // 调解
    REFORM_PUSH,   // 推动改革
    STABILIZE      // 维稳
}

data class FactionAction(
    val type: FactionActionType,
    val targetFaction: FactionType,
    val description: String,
    val personalCost: Double = 0.0,
    val riskDescription: String? = null
)

data class FactionActionResult(
    val success: Boolean,
    val message: String,
    val relationChanges: Map<FactionType, Int> = emptyMap(),
    val reputationChange: Long = 0L,
    val personalFundsCost: Double = 0.0
)

enum class FactionEventType {
    INTERNAL_CONFLICT,  // 内部冲突
    DISSATISFACTION,    // 不满
    POWER_GRAB,         // 夺权
    COOPERATION         // 合作
}

data class FactionEvent(
    /** Stable identity used to make a selected choice idempotent within this runtime. */
    val id: String = UUID.randomUUID().toString(),
    val type: FactionEventType,
    val title: String,
    val message: String,
    val affectedFactions: List<FactionType>,
    val reputationImpact: Long,
    val choices: List<FactionEventChoice>
)

data class FactionEventChoice(
    val text: String,
    val relationChanges: Map<FactionType, Int>
)

/**
 * 学校决策类型 - 其他Manager操作时触发派系反应
 */
enum class SchoolDecision {
    HIRE_EXPENSIVE_TEACHER,
    FIRE_TEACHER,
    BUILD_FACILITY,
    LAUNCH_NEW_COURSE,
    CANCEL_COURSE,
    RAISE_TUITION,
    MARKETING_CAMPAIGN,
    RESEARCH_INVESTMENT,
    EXPAND_CAMPUS,
    CUT_BUDGET,
    TEACHER_TRAINING,
    CLUB_ACTIVITY,
    STRICT_DISCIPLINE,
    OPEN_BRANCH,
    CORRUPT_ACT
}

enum class FactionStability(val displayName: String) {
    BALANCED("四派平衡"),
    NORMAL("基本稳定"),
    TENSE("关系紧张"),
    CRISIS("濒临危机"),
    ONE_DOMINANT("一派独大")
}

data class FactionSummary(
    val averageRelation: Int,
    val stability: FactionStability,
    val lowestFaction: FactionType?,
    val lowestRelation: Int,
    val highestFaction: FactionType?,
    val highestRelation: Int,
    val warningMessage: String?
)
