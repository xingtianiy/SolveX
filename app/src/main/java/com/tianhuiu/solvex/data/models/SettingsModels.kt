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
    val enableAutoHideBall: Boolean = true,
    val captureMode: String = CaptureMode.SYSTEM,
    val drawerSettings: DrawerSettings = DrawerSettings(),
    val ballFullSizeDp: Float = 42f,
    val enableScreenProtection: Boolean = false,
    val enableStealthMode: Boolean = false,
    val hasShownStealthWarning: Boolean = false,
    val isFirstLaunchSetupComplete: Boolean = false,
)

/**
 * 截屏模式常量。
 */
object CaptureMode {
    const val SYSTEM = "system"
    const val ACCESSIBILITY = "accessibility"
    const val SHIZUKU = "shizuku"
    const val TEXT_ONLY = "text_only"

    fun toDisplayName(mode: String?): String = when (mode) {
        SHIZUKU -> "Shizuku ADB"
        ACCESSIBILITY -> "无障碍截图"
        SYSTEM -> "系统录屏"
        TEXT_ONLY -> "屏幕取字"
        else -> mode ?: "未知"
    }
}

/**
 * 权限引导步骤，按推荐顺序排列。
 */
enum class PermissionSetupStep(
    val displayName: String,
    val description: String,
    val isOptional: Boolean = false,
) {
    OVERLAY("悬浮窗权限", "允许在其他应用上层显示悬浮球，是 SolveX 的核心交互方式"),
    NOTIFICATION("通知权限", "解析完成后发送系统通知，避免错过结果", isOptional = true),
    ACCESSIBILITY("无障碍服务", "辅助获取屏幕内容，仅无障碍截屏模式需要"),
    BATTERY("电池优化白名单", "允许 SolveX 在后台持续运行，防止被系统省电策略关闭"),
    SHIZUKU("Shizuku 授权", "ADB 级截屏权限，一次授权永久有效，无需反复弹窗确认"),
    DONE("设置完成", "SolveX 已准备就绪，可以开始使用了"),
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
