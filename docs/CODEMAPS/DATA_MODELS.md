# 数据模型 代码地图

**最后更新：** 2026-06-12
**源文件：** `data/models/SettingsModels.kt`、`data/models/HistoryModels.kt`、`data/models/ProcessingModels.kt`、`data/models/UpdateModels.kt`、`data/models/InAppNotification.kt`

## 概述

所有数据模型均为 `@Serializable`（Kotlin Serialization）数据类。Room 实体另外使用 `@Entity` 和 `@PrimaryKey` 注解。

## SettingsModels.kt

### ProviderKind

```kotlin
enum class ProviderKind(val displayName: String) {
    OPENAI_COMPATIBLE("OpenAI Compatible"),
    OPENAI_RESPONSES("OpenAI Responses"),
    ANTHROPIC("Anthropic"),
    GOOGLE("Google Gemini"),
}
```

### EngineType

```kotlin
enum class EngineType(val displayName: String) {
    TEXT_ENGINE("Text Engine"),      // OCR -> 文本 LLM
    VISION_ENGINE("Vision Engine"),  // 直接多模态
}
```

### ProjectMode

```kotlin
enum class ProjectMode(val displayName: String, val description: String) {
    STUDY_MODE("Study Mode", "显示逐步推理过程和答案"),
    QUICK_MODE("Quick Mode", "悬浮球显示答案 / 自动复制"),
    MULTI_IMAGE_MODE("Multi-Image Mode", "批量分析多张截图"),
}
```

### ModelProvider

| 字段 | 类型 | 描述 |
|-------|------|-------------|
| `id` | String | UUID |
| `type` | ProviderKind | API 适配器类型 |
| `name` | String | 显示名称 |
| `url` | String | API 基础 URL（无 `/v1` 后缀） |
| `apiKey` | String | API 密钥（存储在 DataStore 中，导出时若选择排除则清除） |
| `availableModels` | List<String> | 获取到的模型列表 |
| `defaultOcrModel` | String | OCR 角色的默认模型 |
| `defaultTextModel` | String | 文本分析角色的默认模型 |
| `defaultVisionModel` | String | 视觉分析角色的默认模型 |

### AssistantConfig

| 字段 | 类型 | 描述 |
|-------|------|-------------|
| `id` | String | UUID |
| `name` | String | 显示名称 |
| `ocrPrompt` | String | 提取阶段的用户提示词 |
| `textPrompt` | String | 文本分析阶段的用户提示词 |
| `visionPrompt` | String | 视觉分析阶段的用户提示词 |
| `sectionLabel` | String | 多图分段的标签（默认："题目"） |

### IModeConfig（接口）

所有模式的通用配置接口：

| 字段 | 类型 | 描述 |
|-------|------|-------------|
| `allowNotification` | Boolean | 完成时发送系统通知 |
| `showFloatingToast` | Boolean | 在悬浮球上显示结果 |
| `autoOpenDrawer` | Boolean | 处理时自动打开侧边抽屉 |
| `showScreenshotInRealtime` | Boolean | 在抽屉/通知中显示截图 |
| `ocrProviderId` / `ocrModel` | String? | 覆盖 OCR 的提供商/模型 |
| `textProviderId` / `textModel` | String? | 覆盖文本分析的提供商/模型 |
| `visionProviderId` / `visionModel` | String? | 覆盖视觉分析的提供商/模型 |
| `firstDeltaTimeoutSeconds` | Long | 首个 LLM 响应增量的超时时间 |

**实现类：** `StudyModeConfig`、`QuickModeConfig`、`MultiImageModeConfig`

### MultiImageModeConfig 额外字段

| 字段 | 类型 | 描述 |
|-------|------|-------------|
| `multiImageMergeEnabled` | Boolean | 将全部图片合并到一次 LLM 调用中 |
| `multiImageVisionProviderId` / `multiImageVisionModel` | String? | 多图模式的专用视觉配置 |
| `multiImageCropEnabled` | Boolean | 多图模式下对每张图片进行裁剪 |
| `multiImageNotificationMode` | String | `final_only`、`per_image` 或 `progress` |
| `multiImageAutoOpenDrawer` | Boolean | 多图模式下自动打开抽屉 |

### CaptureMode（对象，非枚举）

```kotlin
object CaptureMode {
    const val SYSTEM = "system"
    const val ACCESSIBILITY = "accessibility"
    const val SHIZUKU = "shizuku"
}
```

### PermissionSettings、DrawerSettings、DrawerSide

控制：
- 通知权限
- 自动隐藏悬浮球
- 截图模式选择
- 抽屉侧边（LEFT/RIGHT）和宽度（0.3-0.9）

## HistoryModels.kt

### AnalysisStatus

```kotlin
enum class AnalysisStatus(val displayName: String) {
    SUCCESS("Completed"), FAILURE("Failed"),
    CANCELLED("Cancelled"), PROCESSING("Processing")
}
```

### HistoryItem（Room @Entity）

| 字段 | 类型 | Room | 描述 |
|-------|------|------|-------------|
| `id` | String | @PrimaryKey | UUID |
| `timestamp` | Long | - | 毫秒时间戳 |
| `title` / `summary` | String? | - | 由摘要提示词生成 |
| `query` | String | - | 提取的文本 / OCR 输出 |
| `result` | String | - | AI 分析答案 |
| `imagePath` | String? | - | 主截图文件路径 |
| `imagePaths` | List<String> | TypeConverter | 所有截图路径（多图） |
| `mode` | String? | - | ProjectMode 的显示名称 |
| `assistantName` / `providerName` / `modelName` | String? | - | 元数据 |
| `engineName` | String? | - | 引擎类型显示名称 |
| `status` | AnalysisStatus | - | 当前分析状态 |

## ProcessingModels.kt

| 模型 | 描述 |
|-------|-------------|
| `ProcessingStatus` | RUNNING、SUCCESS、FAILURE |
| `ProcessingRoute` | OCR_THEN_LLM 或 MULTIMODAL_DIRECT |
| `ProcessingEvent` | 标题 + 详情 + 时间戳 |
| `AutomationAction` | TYPE_CLIPBOARD 或 TYPE_BUBBLE + 文本 |
| `ExtractedQuestion` | 解析后的 JSON：类型、题目、选项 |
| `ProcessingResult` | 流水线返回的完整结果及其所有字段 |

## UpdateModels.kt

### VersionInfo

| 字段 | 类型 | 描述 |
|-------|------|-------------|
| `versionCode` | Int | 递增版本号 |
| `versionName` | String | 语义化版本字符串 |
| `releaseDate` | String | YYYY-MM-DD |
| `level` | String | `critical` / `recommended` / `optional` |
| `apkSize` | String | 人类可读的大小 |
| `updateLog` | List<String> | 更新日志条目 |
| `githubUrl` / `giteeUrl` | String | APK 下载 URL |

计算属性：
- `updateLevel: UpdateLevel` - 从 level 字符串解析
- `isDismissible: Boolean` - CRITICAL 时为 false

### UpdateLevel

```kotlin
enum class UpdateLevel { CRITICAL, RECOMMENDED, OPTIONAL }
```

### DownloadStatus

```kotlin
sealed class DownloadStatus {
    object Idle : DownloadStatus()
    data class Downloading(val progress: Int) : DownloadStatus()
    data class Success(val apkPath: String) : DownloadStatus()
    data class Error(val message: String) : DownloadStatus()
}
```

## InAppNotification.kt

| 模型 | 描述 |
|-------|-------------|
| `NotificationType` | PERMISSION、READY_STATUS、TUTORIAL（含优先级） |
| `PermissionType` | OVERLAY、NOTIFICATION、ACCESSIBILITY、SHIZUKU |
| `InAppNotification` | 类型、标题、内容、可选权限类型 |

## 相关领域

- [数据层](DATA_LAYER.md) - 仓库、数据库、DataStore
- [UI 层](UI_LAYER.md) - 消费这些模型的页面
