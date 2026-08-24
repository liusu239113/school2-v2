package com.arktools.xiaozhang.domain.autohandle

import kotlinx.serialization.Serializable

/**
 * 事件自动处理策略
 */
@Serializable
enum class AutoStrategy(val displayName: String, val description: String) {
    MANUAL("手动处理", "每次弹窗让你亲自决定"),
    AUTO_APPROVE("自动批准", "自动选择「同意/批准/第一个选项」"),
    AUTO_REJECT("自动拒绝", "自动选择「拒绝/驳回/最后一个选项」");
}

/**
 * 事件自动处理配置
 * 存储在校长办公室，控制各类事件的自动处理策略
 */
@Serializable
data class AutoHandleConfig(
    /** 总开关 */
    val enabled: Boolean = false,

    // ======== 选择类事件（ChoiceEvent）的分类策略 ========

    /** 教师加薪请求 */
    val teacherRaiseStrategy: AutoStrategy = AutoStrategy.MANUAL,

    /** 教师续约请求 */
    val teacherRenewalStrategy: AutoStrategy = AutoStrategy.MANUAL,

    /** 教师离职请求 */
    val teacherResignStrategy: AutoStrategy = AutoStrategy.MANUAL,

    /** 活动审批（季节活动申请） */
    val activityApprovalStrategy: AutoStrategy = AutoStrategy.MANUAL,

    /** 社团审批（学生社团申请） */
    val clubApprovalStrategy: AutoStrategy = AutoStrategy.MANUAL,

    /** 突发危机事件（强烈建议手动） */
    val crisisStrategy: AutoStrategy = AutoStrategy.MANUAL,

    /** 其他选择事件（未分类的） */
    val otherChoiceStrategy: AutoStrategy = AutoStrategy.MANUAL,

    // ======== 信息类事件（非选择）的自动关闭 ========

    /** 正面事件自动关闭（不弹窗） */
    val positiveAutoClose: Boolean = true,

    /** 负面事件自动关闭（不弹窗，仅进通知中心） */
    val negativeAutoClose: Boolean = false,

    /** 里程碑事件自动关闭 */
    val milestoneAutoClose: Boolean = false
)

/**
 * 自动处理记录（用于日志/回顾）
 */
@Serializable
data class AutoHandledRecord(
    val eventTitle: String,
    val eventType: String,
    val action: String,  // "auto_approve", "auto_reject", "auto_close"
    val timestamp: Long = System.currentTimeMillis()
)
