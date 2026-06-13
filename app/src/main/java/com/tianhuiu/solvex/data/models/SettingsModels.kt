package com.tianhuiu.solvex.data.models

import com.tianhuiu.solvex.mode.ModeConfig
import com.tianhuiu.solvex.mode.ModeRegistry
import kotlinx.serialization.Serializable

/**
 * 模型提供方类型：定义不同的 API 适配器。
 */
@Serializable
enum class ProviderKind(val displayName: String) {
    OPENAI_COMPATIBLE("OpenAI Compatible"),
    OPENAI_RESPONSES("OpenAI Responses"),
    ANTHROPIC("Anthropic"),
    GOOGLE("Google Gemini"),
}

/**
 * 识别引擎类型：决定处理流程。
 */
@Serializable
enum class EngineType(val displayName: String) {
    TEXT_ENGINE("文本引擎"),
    VISION_ENGINE("视觉引擎"),
}

/**
 * 模型提供方配置。
 */
@Serializable
data class ModelProvider(
    val id: String,
    val type: ProviderKind = ProviderKind.OPENAI_COMPATIBLE,
    val name: String,
    val url: String,
    val apiKey: String,
    val availableModels: List<String> = emptyList(),
    val defaultOcrModel: String = "",
    val defaultTextModel: String = "",
    val defaultVisionModel: String = "",
)

/**
 * 智能助手身份配置。
 */
@Serializable
data class AssistantConfig(
    val id: String,
    val name: String,
    val ocrPrompt: String,
    val textPrompt: String,
    val visionPrompt: String,
    val useStructuredExtraction: Boolean = true,
)

/**
 * 抽屉弹出侧边。
 */
@Serializable
enum class DrawerSide(val displayName: String) {
    LEFT("左侧"),
    RIGHT("右侧")
}

/**
 * 抽屉设置。
 */
@Serializable
data class DrawerSettings(
    val side: DrawerSide = DrawerSide.LEFT,
    val widthPercent: Float = 0.8f // 0.3 to 0.9
)

/**
 * 权限及基础设置。
 */
@Serializable
data class PermissionSettings(
    val allowNotificationNormal: Boolean = true,
    val allowNotificationAuto: Boolean = true,
    val enableAutoHideBall: Boolean = true,
    val captureMode: String = CaptureMode.SYSTEM,
    val drawerSettings: DrawerSettings = DrawerSettings(),
    val ballFullSizeDp: Float = 42f,
)

/**
 * 截屏模式常量。
 */
object CaptureMode {
    const val SYSTEM = "system"
    const val ACCESSIBILITY = "accessibility"
    const val SHIZUKU = "shizuku"

    fun toDisplayName(mode: String?): String = when (mode) {
        SHIZUKU -> "Shizuku ADB"
        ACCESSIBILITY -> "无障碍截图"
        SYSTEM -> "系统录屏"
        else -> mode ?: "未知"
    }
}

/**
 * 全局应用配置根对象。
 */
@Serializable
data class AppConfig(
    val providers: List<ModelProvider> = emptyList(),
    val assistants: List<AssistantConfig> = emptyList(),
    val permissions: PermissionSettings = PermissionSettings(),
    val defaultProviderId: String? = null,
    val selectedAssistantId: String? = null,
    val selectedEngine: EngineType = EngineType.VISION_ENGINE,
    val selectedModeId: String = ModeRegistry.defaultId(),
    val modeConfigs: Map<String, ModeConfig> = emptyMap(),
    val autoScrollContent: Boolean = true,
)

// 获取当前模式配置
fun AppConfig.currentModeConfig(): ModeConfig {
    return modeConfigs[selectedModeId] ?: ModeRegistry.get(selectedModeId).defaultConfig()
}
