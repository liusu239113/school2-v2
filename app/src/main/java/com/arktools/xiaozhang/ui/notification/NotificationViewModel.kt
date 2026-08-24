package com.arktools.xiaozhang.ui.notification

import androidx.lifecycle.ViewModel
import com.arktools.xiaozhang.domain.notification.NotificationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val notificationManager: NotificationManager
) : ViewModel() {

    val notifications = notificationManager.notifications
    val unreadCount: StateFlow<Int> = notificationManager.unreadCount

    fun markAsRead(id: String) {
        notificationManager.markAsRead(id)
    }

    fun markAllAsRead() {
        notificationManager.markAllAsRead()
    }
}
