package com.arktools.xiao.domain.model

/**
 * 游戏内通知模型 - 收集重要事件供玩家回顾
 */
data class GameNotification(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val message: String,
    val type: NotificationType,
    val priority: NotificationPriority = NotificationPriority.NORMAL,
    val timestamp: Long = System.currentTimeMillis(),
    val gameYear: Int = 0,
    val gameMonth: Int = 0,
    val gameDay: Int = 0,
    val isRead: Boolean = false,
    val actionLabel: String? = null,
    val actionTabIndex: Int? = null  // 点击后跳转到的标签页
)

enum class NotificationType {
    FINANCIAL,      // 财务相关（收支、破产警告）
    TEACHER,        // 教师相关（入职、离职、灵感）
    STUDENT,        // 学生相关（招生、毕业、退学）
    COURSE,         // 课程相关（完成、发布、评分）
    MILESTONE,      // 里程碑达成
    CRISIS,         // 危机警告
    COMPETITOR,     // 竞争对手动态
    MARKET          // 市场/股票事件
}

enum class NotificationPriority {
    LOW,        // 一般信息
    NORMAL,     // 普通通知
    HIGH,       // 重要通知（显示角标）
    URGENT      // 紧急通知（弹出提示）
}
