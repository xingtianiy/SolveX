package com.tianhuiu.solvex.mode

import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

// 模式统一契约
interface Mode {
    val id: String
    val displayName: String
    val description: String
    val icon: ImageVector

    // [MainService.kt:142] 仅常规模式触发裁剪
    val shouldCrop: Boolean

    // [ProcessingPipeline.kt:284] 仅自动模式有此要求
    val requiresAutomationAction: Boolean

    fun defaultConfig(): ModeConfig
}

// 模式配置
@Serializable
data class ModeConfig(
    val allowNotification: Boolean = true,
    val showFloatingToast: Boolean = true,
    val autoOpenDrawer: Boolean = true,
    val ocrProviderId: String? = null,
    val ocrModel: String? = null,
    val textProviderId: String? = null,
    val textModel: String? = null,
    val visionProviderId: String? = null,
    val visionModel: String? = null,
    val firstDeltaTimeoutSeconds: Long = 10,
)
