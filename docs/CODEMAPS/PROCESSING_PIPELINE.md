# 处理流水线 代码地图

**最后更新：** 2026-06-12
**入口点：** `network/ProcessingPipeline.kt`

## 架构

```
ProcessingPipeline
  |
  +-- ResolvedModels（将 AppConfig 映射为已解析的提供商/模型组合）
  |     +-- AssistantConfig
  |     +-- textProvider、textModel
  |     +-- visionProvider、visionModel
  |     +-- ocrProvider、ocrModel
  |     +-- engine：TEXT_ENGINE 或 VISION_ENGINE
  |
  +-- process(config, bitmap, callbacks) -> ProcessingResult（单图）
  +-- processMultiImage(config, bitmaps, callbacks) -> ProcessingResult（批量）
  |
  +-- 使用：
        +-- UnifiedLLMClient（流式传输）
        +-- FileUtils（保存 bitmap 到内部存储）
        +-- ResponseParser（解析结构化输出）
```

## 处理结果

```kotlin
data class ProcessingResult(
    val id: String,             // UUID
    val status: ProcessingStatus, // RUNNING、SUCCESS、FAILURE
    val route: ProcessingRoute,   // OCR_THEN_LLM 或 MULTIMODAL_DIRECT
    val extractedText: String?,   // OCR/文本提取结果
    val answer: String?,          // AI 分析结果
    val automationAction: AutomationAction?, // 用于快速模式
    val screenshotPath: String?,          // 主截图
    val screenshotPaths: List<String>,    // 所有截图（多图模式）
    val modelSummary: String,             // 人类可读的模型信息
    val assistantName: String,
    val detail: String,
    val events: List<ProcessingEvent>
)
```

## 模式配置解析

对于每种模式，流水线会解析每个角色（文本、视觉、OCR）应使用哪个提供商/模型：

1. 检查模式专用配置（`StudyModeConfig`、`QuickModeConfig`、`MultiImageModeConfig`）
2. 如果模式未指定提供商，回退到 `defaultProviderId`
3. 如果模型为空，回退到提供商的 `defaultTextModel` / `defaultVisionModel` / `defaultOcrModel`
4. 超时值会被钳位到最低 1 秒后再转换为毫秒

## 处理流程：学习/快速模式（process）

```
process(config, bitmap, callbacks):
  |
  +-- resolveModels(config) -> ResolvedModels
  +-- createBaseResult() -> ProcessingResult（初始状态，status=RUNNING）
  +-- imageBase64 = bitmap.toBase64Jpeg()
  |
  +-- 并行执行：
  |     +-- summaryDeferred（协程 async）：
  |     |     +-- LLM 调用，使用 SUMMARY_SYSTEM_PROMPT
  |     |     +-- ResponseParser.parseSummary() -> (title, summary)
  |     |
  |     +-- 顺序执行：
  |           +-- extractedText = LLM 调用，使用 EXTRACTION_SYSTEM_PROMPT
  |           |     使用 OCR 提供商+模型（TEXT_ENGINE）或 vision 提供商+模型（VISION_ENGINE）
  |           |
  |           +-- answer = LLM 调用，使用 ANALYSIS_SYSTEM_PROMPT
  |           |     使用 text 提供商+模型（TEXT_ENGINE）或 vision 提供商+模型（VISION_ENGINE）
  |           |
  |           +-- automationAction = LLM 调用，使用 AUTOMATION_SYSTEM_PROMPT
  |                 检测题目类型并提取答案以进行自动化处理
  |
  +-- summaryDeferred.await()
  +-- 返回最终 ProcessingResult（SUCCESS 或 FAILURE）
```

**关键行为：**
- 摘要生成与提取/分析并行运行
- 如果提取的文本为空，返回 FAILURE 并提示"未找到内容"
- 如果在 QUICK_MODE 下且 automationAction 为 null，返回 FAILURE
- 每个 LLM 调用使用 `collectTextStream()` 缓冲流式增量数据

## 处理流程：多图模式（processMultiImage）

```
processMultiImage(config, bitmaps, callbacks):
  |
  +-- resolveModels(config) -> ResolvedModels
  +-- createBaseResult(models, bitmaps.first(), more bitmaps...) -> Result
  |
  +-- 两条路径，取决于 multiImageMergeEnabled：
  |
  |     合并模式（true）：
  |     +-- 将所有图片在一次 LLM 调用中发送
  |     +-- 单次提取 + 单次分析
  |
  |     逐页模式（false）：
  |     +-- 对每个 bitmap：
  |           +-- 提取 LLM 调用
  |           +-- 分析 LLM 调用
  |           +-- onPageStart(index) 回调
  |           +-- onImageComplete(index, answer) 回调
  |
  +-- automationAction（对合并后的提取文本进行单次处理）
  +-- 返回最终 ProcessingResult
```

**逐页分段标签：** 由 `AssistantConfig.sectionLabel` 控制（默认："题目"）。

**多图通知模式：**
- `per_image` - 每完成一张图片发送通知
- `progress` - 每张图片更新进度通知
- `final_only` - 结束时发送单条通知

## 引擎选择

| 引擎 | 路线 | 流程 |
|--------|-------|------|
| `TEXT_ENGINE` | OCR_THEN_LLM | OCR 模型提取文本 -> 文本模型分析 |
| `VISION_ENGINE` | MULTIMODAL_DIRECT | 视觉模型完成所有工作（图像 + 文本） |

## ResponseParser

`utils/ResponseParser.kt` 中的工具对象：

| 方法 | 用途 |
|----------|---------|
| `parseAnswerSections(fullAnswer)` | 按 `### Title` 标题分割为各段 |
| `extractFinalAnswer(fullAnswer)` | 获取最后一段，去除 LaTeX/Markdown |
| `parseAutomationResponse(text)` | 解析 JSON 响应以获取自动模式操作 |
| `parseSummary(text)` | 提取 Title:/Summary: 行 |
| `parseStructuredQuestion(text)` | 解析包含 `type`/`question`/`options` 的 JSON |
| `renderStructuredQuestion(text)` | 将结构化题目转换为 Markdown |
| `detectSectionLabel(text)` | 检测 `## SectionLabel N` 模式 |
| `extractPerQuestionSection(fullAnswer, n)` | 从多图输出中提取第 N 段 |
| `extractPerQuestionQuery(fullQuery, n)` | 从多图查询文本中提取第 N 个查询 |

## 自动化操作

```kotlin
data class AutomationAction(val type: String, val text: String)
// TYPE_CLIPBOARD = "set_clipboard"  -> 复制到剪贴板
// TYPE_BUBBLE = "show_bubble_letters" -> 在悬浮球上显示
```

逻辑：
- `{"type": "choice", "answer": "A"}` -> 气泡显示（用于选择题）  
- `{"type": "other", "answer": "..."}` -> 剪贴板复制（用于其他类型）

回退：如果没有返回结构化操作，则使用常规答案文本的 `extractFinalAnswer()` 作为替代。

## 相关领域

- [LLM 集成](LLM_INTEGRATION.md) - 底层流式客户端和适配器
- [服务层](SERVICE_LAYER.md) - MainService 编排流水线调用
- [数据模型](DATA_MODELS.md) - 处理模型、自动化模型
