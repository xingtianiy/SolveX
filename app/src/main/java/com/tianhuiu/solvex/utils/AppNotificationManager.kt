package com.tianhuiu.solvex.utils

import com.tianhuiu.solvex.data.models.InAppNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 首页通知栏状态管理。
 */
class AppNotificationManager {
    private val _notifications = MutableStateFlow<List<InAppNotification>>(emptyList())

    val notifications: StateFlow<List<InAppNotification>> = _notifications.asStateFlow()

    /**
     * 同步首页通知：就绪状态 + 新手指引。
     */
    fun syncAll(
        isServiceRunning: Boolean = false,
        isReady: Boolean = false,
        launchCount: Int = 0,
    ) {
        _notifications.update { _ ->
            val next = mutableListOf<InAppNotification>()

            // 服务状态
            when {
                isServiceRunning -> next.add(
                    InAppNotification(
                        id = "STATUS_RUNNING",
                        type = com.tianhuiu.solvex.data.models.NotificationType.READY_STATUS,
                        title = "SolveX 服务运行中",
                        content = "点击悬浮球截图解题"
                    )
                )
                isReady -> next.add(
                    InAppNotification(
                        id = "STATUS_READY",
                        type = com.tianhuiu.solvex.data.models.NotificationType.READY_STATUS,
                        title = "SolveX 服务已就绪",
                        content = "点击下方按钮启动服务"
                    )
                )
            }

            // 新手教程（前 3 次启动）
            if (launchCount in 1..3) {
                next.add(
                    InAppNotification(
                        id = "TUTORIAL_GUIDE",
                        type = com.tianhuiu.solvex.data.models.NotificationType.TUTORIAL,
                        title = "新手指引",
                        content = "查看使用教程，快速了解功能与操作"
                    )
                )
            }

            next
        }
    }

    fun dismiss(id: String) {
        _notifications.update { current ->
            current.filter { it.id != id }
        }
    }
}
