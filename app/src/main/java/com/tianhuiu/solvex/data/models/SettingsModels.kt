package com.tianhuiu.solvex.data.models

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
 * 项目运行模式。
 */
@Serializable
enum class ProjectMode(val displayName: String, val description: String) {
    STUDY_MODE("常规模式", "展示解题思路和答案片段"),
    QUICK_MODE("自动模式", "悬浮球展示选择题答案/填空题自动复制"),
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
    val drawerSettings: DrawerSettings = DrawerSettings()
)

/**
 * 常规学习模式下的具体工作流配置。
 */
@Serializable
data class StudyModeConfig(
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

/**
 * 自动模式下的具体工作流配置。
 */
@Serializable
data class QuickModeConfig(
    val allowNotification: Boolean = true,
    val showFloatingToast: Boolean = true,
    val autoOpenDrawer: Boolean = false,
    val ocrProviderId: String? = null,
    val ocrModel: String? = null,
    val textProviderId: String? = null,
    val textModel: String? = null,
    val visionProviderId: String? = null,
    val visionModel: String? = null,
    val firstDeltaTimeoutSeconds: Long = 10,
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
    val studyConfig: StudyModeConfig = StudyModeConfig(),
    val quickConfig: QuickModeConfig = QuickModeConfig(),
    val defaultProviderId: String? = null,
    val selectedAssistantId: String? = null,
    val selectedEngine: EngineType = EngineType.VISION_ENGINE,
    val selectedMode: ProjectMode = ProjectMode.STUDY_MODE,
    val autoScrollContent: Boolean = true,
)
