package com.tianhuiu.solvex.data.models

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 通知类型枚举
 */
@Serializable
enum class NotificationType(val priority: Int) {
    READY_STATUS(5),   // 系统就绪状态
    TUTORIAL(3)        // 教程引导
}

/**
 * 应用内通知数据模型。
 */
@Serializable
data class InAppNotification(
    val id: String = UUID.randomUUID().toString(),
    val type: NotificationType,
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
