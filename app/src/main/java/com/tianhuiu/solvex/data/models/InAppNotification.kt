package com.tianhuiu.solvex.data.models

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 通知类型枚举：决定通知的优先级和显示样式。
 */
@Serializable
enum class NotificationType(val priority: Int) {
    PERMISSION(10),    // 权限缺失（最高优先级）
    READY_STATUS(5),   // 系统就绪状态
    TUTORIAL(3)        // 教程引导
}

/**
 * 权限子类型：用于区分具体的权限缺失类型。
 */
@Serializable
enum class PermissionType {
    OVERLAY,
    NOTIFICATION,
    ACCESSIBILITY,
    SHIZUKU
}

data class InAppNotification(
    val id: String = UUID.randomUUID().toString(),
    val type: NotificationType,
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val permissionType: PermissionType? = null
)
