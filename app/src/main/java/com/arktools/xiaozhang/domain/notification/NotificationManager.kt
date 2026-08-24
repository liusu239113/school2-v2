package com.arktools.xiaozhang.domain.notification

import com.arktools.xiaozhang.domain.model.GameNotification
import com.arktools.xiaozhang.domain.model.NotificationPriority
import com.arktools.xiaozhang.domain.model.NotificationType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 游戏内通知管理器
 * - 收集游戏引擎产生的重要事件
 * - 维护通知列表（最多100条）
 * - 提供未读计数
 * - 支持按类型筛选
 */
@Singleton
class NotificationManager @Inject constructor() {

    private val _notifications = MutableStateFlow<List<GameNotification>>(emptyList())
    val notifications: StateFlow<List<GameNotification>> = _notifications.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    companion object {
        private const val MAX_NOTIFICATIONS = 100
    }

    fun addNotification(notification: GameNotification) {
        _notifications.update { current ->
            val updated = listOf(notification) + current
            if (updated.size > MAX_NOTIFICATIONS) {
                updated.take(MAX_NOTIFICATIONS)
            } else {
                updated
            }
        }
        updateUnreadCount()
    }

    fun addNotification(
        title: String,
        message: String,
        type: NotificationType,
        priority: NotificationPriority = NotificationPriority.NORMAL,
        gameYear: Int = 0,
        gameMonth: Int = 0,
        gameDay: Int = 0,
        actionLabel: String? = null,
        actionTabIndex: Int? = null
    ) {
        addNotification(
            GameNotification(
                title = title,
                message = message,
                type = type,
                priority = priority,
                gameYear = gameYear,
                gameMonth = gameMonth,
                gameDay = gameDay,
                actionLabel = actionLabel,
                actionTabIndex = actionTabIndex
            )
        )
    }

    fun markAsRead(notificationId: String) {
        _notifications.update { current ->
            current.map { if (it.id == notificationId) it.copy(isRead = true) else it }
        }
        updateUnreadCount()
    }

    fun markAllAsRead() {
        _notifications.update { current ->
            current.map { it.copy(isRead = true) }
        }
        updateUnreadCount()
    }

    fun clearAll() {
        _notifications.value = emptyList()
        _unreadCount.value = 0
    }

    fun getByType(type: NotificationType): List<GameNotification> {
        return _notifications.value.filter { it.type == type }
    }

    fun getUnread(): List<GameNotification> {
        return _notifications.value.filter { !it.isRead }
    }

    fun getHighPriority(): List<GameNotification> {
        return _notifications.value.filter {
            it.priority == NotificationPriority.HIGH || it.priority == NotificationPriority.URGENT
        }
    }

    private fun updateUnreadCount() {
        _unreadCount.value = _notifications.value.count { !it.isRead }
    }
}
