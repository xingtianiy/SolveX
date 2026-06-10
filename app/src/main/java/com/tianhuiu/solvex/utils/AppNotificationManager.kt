package com.tianhuiu.solvex.utils

import com.tianhuiu.solvex.data.models.InAppNotification
import com.tianhuiu.solvex.data.models.NotificationType
import com.tianhuiu.solvex.data.models.PermissionType
import com.tianhuiu.solvex.data.models.CaptureMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 应用内通知管理器：负责整合权限、就绪状态，按优先级展示。
 */
class AppNotificationManager {
    private val _notifications = MutableStateFlow<List<InAppNotification>>(emptyList())

    /**
     * 暴露给 UI 的通知列表流。
     */
    val notifications: StateFlow<List<InAppNotification>> = _notifications.asStateFlow()

    /**
     * 同步所有通知状态。
     */
    fun syncAll(
        isOverlayGranted: Boolean,
        isNotificationGranted: Boolean,
        isAccessibilityEnabled: Boolean = true,
        isShizukuGranted: Boolean = false,
        captureMode: String = "system",
    ) {
        _notifications.update { _ ->
            val nextNotifications = mutableListOf<InAppNotification>()

            // 1. 处理权限通知
            if (!isOverlayGranted) {
                nextNotifications.add(
                    InAppNotification(
                        id = "PERM_OVERLAY",
                        type = NotificationType.PERMISSION,
                        title = "悬浮窗权限缺失",
                        content = "需要授权以显示实时解题悬浮球",
                        permissionType = PermissionType.OVERLAY,
                    )
                )
            }
            if (!isNotificationGranted) {
                nextNotifications.add(
                    InAppNotification(
                        id = "PERM_NOTIFICATION",
                        type = NotificationType.PERMISSION,
                        title = "通知权限已禁用",
                        content = "解析结果将无法进行通知",
                        permissionType = PermissionType.NOTIFICATION
                    )
                )
            }
            if (captureMode == CaptureMode.ACCESSIBILITY && !isAccessibilityEnabled) {
                nextNotifications.add(
                    InAppNotification(
                        id = "PERM_ACCESSIBILITY",
                        type = NotificationType.PERMISSION,
                        title = "无障碍服务未开启",
                        content = "当前截屏模式需要开启无障碍服务",
                        permissionType = PermissionType.ACCESSIBILITY
                    )
                )
            }
            if (captureMode == CaptureMode.SHIZUKU && !isShizukuGranted) {
                nextNotifications.add(
                    InAppNotification(
                        id = "PERM_SHIZUKU",
                        type = NotificationType.PERMISSION,
                        title = "Shizuku 未授权",
                        content = "当前截屏模式需要 Shizuku 连接并授权",
                        permissionType = PermissionType.SHIZUKU
                    )
                )
            }

            // 2. 处理就绪状态通知（所有截屏模式必要权限均已满足）
            val allRequiredGranted = isOverlayGranted && when (captureMode) {
                CaptureMode.ACCESSIBILITY -> isAccessibilityEnabled
                CaptureMode.SHIZUKU -> isShizukuGranted
                else -> true
            }
            if (allRequiredGranted) {
                nextNotifications.add(
                    InAppNotification(
                        id = "STATUS_READY",
                        type = NotificationType.READY_STATUS,
                        title = "SolveX 已就绪",
                        content = "等待开启捕获"
                    )
                )
            }

            // 3. 混合并排序，支持最多 3 条，确保就绪通知能看到
            sortAndLimit(nextNotifications)
        }
    }

    /**
     * 关闭（移除）通知。
     */
    fun dismiss(id: String) {
        _notifications.update { current ->
            current.filter { it.id != id }
        }
    }

    private fun sortAndLimit(list: List<InAppNotification>): List<InAppNotification> {
        return list.asSequence().sortedWith(
            compareByDescending<InAppNotification> { it.type.priority }
                .thenByDescending { it.timestamp }
        ).take(3).toList()
    }
}
