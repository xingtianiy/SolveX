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
    MULTI_IMAGE_MODE("多图模式", "多张截图综合分析"),
}

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

@Serializable
data class AssistantConfig(
    val id: String,
    val name: String,
    val ocrPrompt: String,
    val textPrompt: String,
    val visionPrompt: String,
    val sectionLabel: String = "题目",
)

@Serializable
enum class DrawerSide(val displayName: String) {
    LEFT("左侧"),
    RIGHT("右侧")
}

@Serializable
data class DrawerSettings(
    val side: DrawerSide = DrawerSide.LEFT,
    val widthPercent: Float = 0.9f // 0.3 to 0.9
)

@Serializable
data class PermissionSettings(
    val allowNotificationNormal: Boolean = true,
    val allowNotificationAuto: Boolean = true,
    val enableAutoHideBall: Boolean = true,
    val captureMode: String = CaptureMode.SYSTEM,
    val drawerSettings: DrawerSettings = DrawerSettings(),
    val ballSizeDp: Float = 42f,
)

/**
 * 模式配置公共接口。新增模式只需实现此接口并在 AppConfig.getModeConfig() 中添加分支。
 */
interface IModeConfig {
    val allowNotification: Boolean
    val showFloatingToast: Boolean
    val autoOpenDrawer: Boolean
    val showScreenshotInRealtime: Boolean
    val cropBeforeProcessing: Boolean
    val multiImageEnabled: Boolean
    val ocrProviderId: String?
    val ocrModel: String?
    val textProviderId: String?
    val textModel: String?
    val visionProviderId: String?
    val visionModel: String?
    val firstDeltaTimeoutSeconds: Long
}

@Serializable
data class StudyModeConfig(
    override val allowNotification: Boolean = true,
    override val showFloatingToast: Boolean = true,
    override val autoOpenDrawer: Boolean = true,
    override val showScreenshotInRealtime: Boolean = true,
    override val cropBeforeProcessing: Boolean = true,
    override val multiImageEnabled: Boolean = false,
    override val ocrProviderId: String? = null,
    override val ocrModel: String? = null,
    override val textProviderId: String? = null,
    override val textModel: String? = null,
    override val visionProviderId: String? = null,
    override val visionModel: String? = null,
    override val firstDeltaTimeoutSeconds: Long = 10,
) : IModeConfig

@Serializable
data class QuickModeConfig(
    override val allowNotification: Boolean = true,
    override val showFloatingToast: Boolean = true,
    override val autoOpenDrawer: Boolean = false,
    override val showScreenshotInRealtime: Boolean = true,
    override val cropBeforeProcessing: Boolean = false,
    override val multiImageEnabled: Boolean = true,
    val multiImageCropEnabled: Boolean = false,
    override val ocrProviderId: String? = null,
    override val ocrModel: String? = null,
    override val textProviderId: String? = null,
    override val textModel: String? = null,
    override val visionProviderId: String? = null,
    override val visionModel: String? = null,
    override val firstDeltaTimeoutSeconds: Long = 10,
) : IModeConfig

/** 多图模式专属配置 */
@Serializable
data class MultiImageModeConfig(
    override val allowNotification: Boolean = true,
    override val showFloatingToast: Boolean = true,
    override val autoOpenDrawer: Boolean = true,
    override val showScreenshotInRealtime: Boolean = true,
    override val cropBeforeProcessing: Boolean = false,
    override val multiImageEnabled: Boolean = false,
    val multiImageMergeEnabled: Boolean = false,
    val multiImageVisionProviderId: String? = null,
    val multiImageVisionModel: String? = null,
    val multiImageCropEnabled: Boolean = false,
    val multiImageAutoOpenDrawer: Boolean = true,
    override val ocrProviderId: String? = null,
    override val ocrModel: String? = null,
    override val textProviderId: String? = null,
    override val textModel: String? = null,
    override val visionProviderId: String? = null,
    override val visionModel: String? = null,
    override val firstDeltaTimeoutSeconds: Long = 10,
) : IModeConfig

/**
 * 截屏模式常量。
 */
object CaptureMode {
    const val SYSTEM = "system"
    const val ACCESSIBILITY = "accessibility"
    const val SHIZUKU = "shizuku"
}

@Serializable
data class AppConfig(
    val providers: List<ModelProvider> = emptyList(),
    val assistants: List<AssistantConfig> = emptyList(),
    val permissions: PermissionSettings = PermissionSettings(),
    val studyConfig: StudyModeConfig = StudyModeConfig(),
    val quickConfig: QuickModeConfig = QuickModeConfig(),
    val multiImageConfig: MultiImageModeConfig = MultiImageModeConfig(),
    val defaultProviderId: String? = null,
    val selectedAssistantId: String? = null,
    val selectedEngine: EngineType = EngineType.VISION_ENGINE,
    val selectedMode: ProjectMode = ProjectMode.STUDY_MODE,
    val autoScrollContent: Boolean = true,
)

/** 按模式获取对应配置 */
fun AppConfig.getModeConfig(mode: ProjectMode? = null): IModeConfig {
    return when (mode ?: selectedMode) {
        ProjectMode.STUDY_MODE -> studyConfig
        ProjectMode.QUICK_MODE -> quickConfig
        ProjectMode.MULTI_IMAGE_MODE -> multiImageConfig
    }
}
