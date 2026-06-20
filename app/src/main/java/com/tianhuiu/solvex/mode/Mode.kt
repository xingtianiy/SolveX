package com.tianhuiu.solvex.mode

import androidx.compose.ui.graphics.vector.ImageVector
import com.tianhuiu.solvex.data.models.DrawerSide
import kotlinx.serialization.Serializable

// 模式统一契约
interface Mode {
    val id: String
    val displayName: String
    val description: String
    val icon: ImageVector
    val shouldCrop: Boolean
    val requiresAutomationAction: Boolean
    fun defaultConfig(): ModeConfig
}

// 模式配置
@Serializable
data class ModeConfig(
    val allowNotification: Boolean = true,
    val showFloatingToast: Boolean = true,
    val autoOpenDrawer: Boolean = true,
    val drawerSide: DrawerSide = DrawerSide.LEFT,
    val enableCrop: Boolean? = null,
    val ocrProviderId: String? = null,
    val ocrModel: String? = null,
    val textProviderId: String? = null,
    val textModel: String? = null,
    val visionProviderId: String? = null,
    val visionModel: String? = null,
    val firstDeltaTimeoutSeconds: Long = 10,
)
