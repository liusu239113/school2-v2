package com.arktools.xiaozhang.domain.model

sealed class GameEvent {
    abstract val title: String
    abstract val message: String
    abstract val type: EventType

    data class PositiveEvent(
        override val title: String,
        override val message: String,
        val bonusCash: Double = 0.0,
        val bonusReputation: Long = 0,
        val bonusTeacherSkill: Int = 0
    ) : GameEvent() {
        override val type: EventType = EventType.POSITIVE
    }

    data class NegativeEvent(
        override val title: String,
        override val message: String,
        val penaltyCash: Double = 0.0,
        val penaltyReputation: Long = 0,
        val teacherId: String? = null
    ) : GameEvent() {
        override val type: EventType = EventType.NEGATIVE
    }

    data class ChoiceEvent(
        override val title: String,
        override val message: String,
        val choices: List<EventChoice>
    ) : GameEvent() {
        override val type: EventType = EventType.CHOICE
    }

    data class MilestoneEvent(
        override val title: String,
        override val message: String,
        val milestoneType: MilestoneType
    ) : GameEvent() {
        override val type: EventType = EventType.MILESTONE
    }
}

data class EventChoice(
    val text: String,
    val consequence: EventConsequence
)

data class EventConsequence(
    val cashChange: Double = 0.0,
    val reputationChange: Long = 0,
    val teacherLoyaltyChange: Int = 0,
    val teacherAction: TeacherAction? = null,
    val activityAction: ActivityAction? = null,
    val clubAction: ClubAction? = null,
    val factionChoiceAction: FactionChoiceAction? = null,
    val promotionAction: PromotionAction? = null,
    val followUpEvent: GameEvent? = null,
    /** 学业干预：给成绩最差的一批在读学生加学业分（补差/谈话等处置动作）。 */
    val studentAcademicBoost: Float = 0f,
    /** 是否需要校长签字（显示打字机签名动画） */
    val requiresSignature: Boolean = false
)

/**
 * 办学层次升格申报动作：签字后由 GameEngine.executePromotionApproval 幂等执行；
 * decline=true 表示暂缓申报（当年内不再提醒）。
 */
data class PromotionAction(
    val targetTierKey: String = "",
    val decline: Boolean = false
)

/**
 * 派系事件选项动作：通过 eventId 在 GameEngine/FactionManager 中幂等结算一次。
 */
data class FactionChoiceAction(
    val eventId: String,
    val choiceIndex: Int
)

/**
 * 教师相关的审批动作（签字后执行）
 */
sealed class TeacherAction {
    /** 批准离职 */
    data class ApproveResignation(val teacherId: String) : TeacherAction()
    /** 加薪挽留 */
    data class RetainWithRaise(val teacherId: String, val raisePercent: Double) : TeacherAction()
    /** 拒绝离职（强制留任，声誉惩罚） */
    data class ForceRetain(val teacherId: String) : TeacherAction()
    /** 批准加薪请求（更新薪资 + 重置计时器） */
    data class ApproveRaise(val teacherId: String, val raisePercent: Int) : TeacherAction()
    /** 拒绝加薪请求（扣忠诚度） */
    data class RejectRaise(val teacherId: String) : TeacherAction()
    /** 合同续约（更新薪资 + 续约） */
    data class RenewContract(val teacherId: String, val newSalary: Double) : TeacherAction()
    /** 不续约（解雇） */
    data class DeclineRenewal(val teacherId: String) : TeacherAction()
}

/**
 * 季节活动审批动作（签字后执行）
 */
sealed class ActivityAction {
    /** 批准举办活动（指定规模） */
    data class Approve(val activityId: String, val scaleName: String) : ActivityAction()
    /** 驳回活动申请 */
    data class Reject(val activityId: String) : ActivityAction()
}

sealed class ClubAction {
    /** 批准学生社团申请 */
    data class Approve(val applicationId: String) : ClubAction()
    /** 驳回学生社团申请 */
    data class Reject(val applicationId: String) : ClubAction()
    /** 校长手动创建社团（签字确认后） */
    data class CreateDirectly(val clubTypeName: String) : ClubAction()
}

enum class EventType {
    POSITIVE, NEGATIVE, CHOICE, MILESTONE
}

enum class MilestoneType {
    FIRST_COURSE, FIRST_PROFIT, CAMPUS_UPGRADE, TEACHER_HIRED,
    RESEARCH_UNLOCKED, BRANCH_SCHOOL, MARKET_CAP_MILESTONE
}
