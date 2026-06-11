# LLM 集成 代码地图

**最后更新：** 2026-06-12
**入口点：** `network/UnifiedLLMClient.kt`、`network/SseStreamClient.kt`

## 架构

```
UnifiedLLMClient
  +-- ProviderAdapter（接口）
  |     +-- stream(request: StreamRequest) -> Flow<LlmEvent>
  |     +-- fetchModels(provider: ModelProvider) -> List<String>
  |
  +-- 适配器（每种 ProviderKind 对应一个）：
  |     +-- OpenAiChatAdapter     -- /chat/completions（OpenAI 兼容）
  |     +-- OpenAiResponsesAdapter -- /responses（OpenAI Responses API）
  |     +-- AnthropicAdapter      -- /messages（Anthropic Messages API）
  |     +-- GoogleAdapter         -- Gemini streamGenerateContent
  |
  +-- SseStreamClient
  |     +-- 处理原始 SSE 连接生命周期
  |     +-- 解析 API 错误并提供人类可读消息
  |
  +-- LlmEvent（密封类）
  |     +-- TextDelta(text: String)
  |     +-- ToolCall(name: String, arguments: Map<String, String>)
  |     +-- Done
  |     +-- Error(message: String)
  |
  +-- Prompts（用于提取、分析、摘要、自动化的系统提示词）
  +-- ToolRegistry（按提供商格式化工具定义）
```

## 关键模块

### ProviderAdapter 接口

```kotlin
interface ProviderAdapter {
    suspend fun stream(request: StreamRequest): Flow<LlmEvent>
    suspend fun fetchModels(provider: ModelProvider): List<String>
}
```

每种 `ProviderKind` 映射到一个适配器实例，缓存在 `UnifiedLLMClient.adapters`（ConcurrentHashMap）中。

### 提供商类型

| 类型 | 适配器 | API 端点 | 认证头 |
|------|---------|-------------|-------------|
| `OPENAI_COMPATIBLE` | OpenAiChatAdapter | `{url}/chat/completions` | `Authorization: Bearer {key}` |
| `OPENAI_RESPONSES` | OpenAiResponsesAdapter | `{url}/responses` | `Authorization: Bearer {key}` |
| `ANTHROPIC` | AnthropicAdapter | `{url}/messages` | `x-api-key: {key}` |
| `GOOGLE` | GoogleAdapter | `{url}/models/{model}:streamGenerateContent?alt=sse` | `x-goog-api-key: {key}` |

### StreamRequest

```kotlin
data class StreamRequest(
    val provider: ModelProvider,
    val model: String,
    val systemPrompt: String,
    val userPrompt: String,
    val imagesBase64: List<String> = emptyList(),
    val tools: List<ToolDef>? = null,
    val firstDeltaTimeoutMillis: Long
)
```

### 适配器实现细节

所有适配器遵循相同模式：
1. 使用 `kotlinx.serialization.json.buildJsonObject` 构建 JSON 请求体
2. 创建附带认证头的 OkHttp `Request`
3. 调用 `sseClient.stream()`，通过 SSE 发出事件
4. 通过 `callbackFlow` 返回 `Flow<LlmEvent>`
5. 解析流式响应并发出 `TextDelta`、`ToolCall`、`Done` 或 `Error` 事件

**OpenAiChatAdapter：**
- 使用标准 Chat Completions 格式
- 支持以 `data:image/jpeg;base64,...` 格式传递图片 URL
- SSE 结束标记：`data: [DONE]`
- 模型列表从 `GET {url}/models` 获取

**OpenAiResponsesAdapter：**
- 使用较新的 Responses API 格式
- SSE 事件类型：`response.output_text.delta`、`response.completed`
- 支持 `input_image` 类型传递图片

**AnthropicAdapter：**
- 使用 Messages API，附带 `anthropic-version: 2023-06-01`
- SSE 事件：`content_block_delta`（文本）、`message_stop`（完成）
- 最大 token 数硬编码为 4096
- 无模型列表 API —— 返回硬编码列表：`claude-opus-4-7, claude-sonnet-4-6, claude-haiku-4-5`

**GoogleAdapter：**
- 使用 Gemini API，附带 `system_instruction` + `contents`
- SSE 端点：`streamGenerateContent?alt=sse`
- 模型列表从 `GET {url}/models?key={apiKey}` 获取
- 过滤掉嵌入模型

### SseStreamClient

处理原始 SSE 连接，具备以下能力：
- **首个 delta 超时：** 每个请求可配置（设置中默认 10s，最长 60s）
- **错误解析：** 将 API 错误响应映射为人类可读的中文消息：
  - 401："API Key 无效或已过期"
  - 429："请求频率过高"
  - 413/context_length："请求内容过长"
  - 500-599："服务器错误"
  - 余额/配额不足消息
  - 模型未找到消息
- 使用 `suspendCancellableCoroutine` 将 OkHttp SSE 回调桥接到协程

### Prompts

所有系统提示词定义在 `Prompts.kt` 中：

| 提示词 | 用途 |
|--------|---------|
| `EXTRACTION_SYSTEM_PROMPT` | 从截图中提取 OCR/文本的技术规范 |
| `ANALYSIS_SYSTEM_PROMPT` | 答案分析的技术规范，使用 Markdown 格式 |
| `SUMMARY_SYSTEM_PROMPT` | 生成标题（10 字）+ 摘要（30 字）用于历史记录 |
| `AUTOMATION_SYSTEM_PROMPT` | 仅输出 JSON 的规范，用于自动模式（选择题/其他） |

### ToolRegistry

按提供商模式格式化工具定义（OpenAI 原生格式、Anthropic 格式、Google 格式）。目前提供 `formatForProvider(tools, kind)` -> JSON 元素。

## 数据流：流式请求

```
ProcessingPipeline
  -> UnifiedLLMClient.stream(provider, model, systemPrompt, userPrompt, images)
     -> ProviderAdapter.stream(request)
        -> 构建 JSON 请求体
        -> 创建 OkHttp 请求
        -> sseClient.stream(request)
           -> EventSources.newEventSource(request, listener)
              -> onEvent：解析 SSE 数据 -> LlmEvent
              -> onDelta：向 Flow 发出 TextDelta
              -> onDone：发出 Done 并关闭 Flow
           -> Flow<LlmEvent> 被 ProcessingPipeline 收集
              -> TextDelta 事件追加到 StringBuilder
              -> 收到 Done 时返回完整文本
```

## 外部依赖

- OkHttp + okhttp-sse - HTTP 客户端和 SSE 支持
- Kotlin Serialization - JSON 解析

## 相关领域

- [处理流水线](PROCESSING_PIPELINE.md) - 在处理流程中编排 LLM 调用
- [数据模型](DATA_MODELS.md) - `ModelProvider`、`ProviderKind`、`EngineType`
