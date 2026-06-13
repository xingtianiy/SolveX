package com.tianhuiu.solvex.network

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.tianhuiu.solvex.data.models.AppConfig
import com.tianhuiu.solvex.data.models.AssistantConfig
import com.tianhuiu.solvex.data.models.AutomationAction
import com.tianhuiu.solvex.data.models.EngineType
import com.tianhuiu.solvex.data.models.ModelProvider
import com.tianhuiu.solvex.data.models.ProcessingEvent
import com.tianhuiu.solvex.data.models.ProcessingResult
import com.tianhuiu.solvex.data.models.ProcessingRoute
import com.tianhuiu.solvex.data.models.ProcessingStatus
import com.tianhuiu.solvex.data.models.currentModeConfig
import com.tianhuiu.solvex.mode.ModeRegistry
import com.tianhuiu.solvex.utils.AutomationTools
import com.tianhuiu.solvex.utils.FileUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * 处理管道。
 */
class ProcessingPipeline(
    private val appContext: Context,
    private val unifiedClient: UnifiedLLMClient,
) {

    /**
     * 已解析的模型及配置。
     */
    data class ResolvedModels(
        val assistant: AssistantConfig,
        val textProvider: ModelProvider?,
        val textModel: String,
        val visionProvider: ModelProvider?,
        val visionModel: String,
        val ocrProvider: ModelProvider?,
        val ocrModel: String,
        val firstDeltaTimeoutMillis: Long,
        val engine: EngineType,
    )

    /**
     * 根据当前应用配置解析各阶段使用的模型及提供商。
     */
    fun resolveModels(config: AppConfig): ResolvedModels {
        val assistant = config.assistants.find { it.id == config.selectedAssistantId }
            ?: config.assistants.firstOrNull()
            ?: error("请先配置助手")

        val c = config.currentModeConfig()
        val defaultPid = config.defaultProviderId

        val finalTextPid = c.textProviderId ?: defaultPid
        val finalVisionPid = c.visionProviderId ?: defaultPid
        val finalOcrPid = c.ocrProviderId ?: defaultPid

        val textProvider = config.providers.find { it.id == finalTextPid }
        val visionProvider = config.providers.find { it.id == finalVisionPid }
        val ocrProvider = config.providers.find { it.id == finalOcrPid }

        val resolvedTextModel = if (c.textModel.isNullOrBlank()) {
            textProvider?.defaultTextModel ?: ""
        } else c.textModel

        val resolvedVisionModel = if (c.visionModel.isNullOrBlank()) {
            visionProvider?.defaultVisionModel ?: ""
        } else c.visionModel

        val resolvedOcrModel = if (c.ocrModel.isNullOrBlank()) {
            ocrProvider?.defaultOcrModel ?: ""
        } else c.ocrModel

        return ResolvedModels(
            assistant = assistant,
            textProvider = textProvider,
            textModel = resolvedTextModel,
            visionProvider = visionProvider,
            visionModel = resolvedVisionModel,
            ocrProvider = ocrProvider,
            ocrModel = resolvedOcrModel,
            firstDeltaTimeoutMillis = c.firstDeltaTimeoutSeconds.coerceAtLeast(1) * 1000L,
            engine = config.selectedEngine
        )
    }

    /**
     * 构建模型摘要描述。
     */
    private fun buildModelSummary(model: String, providerName: String?): String {
        val trimmedModel = model.trim()
        val trimmedProvider = providerName?.trim()
        if (trimmedModel.isBlank()) return ""
        return if (trimmedProvider.isNullOrBlank()) trimmedModel else "$trimmedModel（$trimmedProvider）"
    }

    /**
     * 初始化处理结果并保存截图。
     */
    fun createBaseResult(
        models: ResolvedModels,
        bitmap: Bitmap,
        detail: String
    ): ProcessingResult {
        val historyId = UUID.randomUUID().toString()
        val imagePath = FileUtils.saveBitmapToInternal(appContext, bitmap)

        return ProcessingResult(
            id = historyId,
            assistantName = models.assistant.name,
            route = if (models.engine == EngineType.TEXT_ENGINE) ProcessingRoute.OCR_THEN_LLM else ProcessingRoute.MULTIMODAL_DIRECT,
            status = ProcessingStatus.RUNNING,
            modelSummary = if (models.engine == EngineType.TEXT_ENGINE)
                buildModelSummary(models.textModel, models.textProvider?.name)
            else
                buildModelSummary(models.visionModel, models.visionProvider?.name),
            detail = detail,
            screenshotPath = imagePath,
            screenshotPaths = listOfNotNull(imagePath),
            events = listOf(ProcessingEvent(title = "请求开始", detail = "已创建处理记录"))
        )
    }

    /**
     * 执行自动化解题流程，包括并行生成摘要与提取题目。
     */
    suspend fun process(
        config: AppConfig,
        bitmap: Bitmap,
        onSummaryGenerated: (String, String) -> Unit = { _, _ -> },
        onQueryExtracted: (String) -> Unit = {},
        onDelta: (String) -> Unit
    ): ProcessingResult = withContext(Dispatchers.Default) {
        val models = resolveModels(config)
        val base = createBaseResult(models, bitmap, "正在获取题目...")
        val imageBase64 = bitmap.toBase64Jpeg()

        coroutineScope {
            try {
                // 并行生成截图标题和摘要
                val summaryDeferred = async {
                    try {
                        val summaryText = collectTextStream(
                            provider = models.visionProvider ?: models.ocrProvider
                            ?: error("未配置视觉或文本模型"),
                            model = if (models.visionProvider != null) models.visionModel else models.ocrModel,
                            systemPrompt = Prompts.SUMMARY_SYSTEM_PROMPT,
                            userPrompt = "请为此截图生成标题和摘要。",
                            imagesBase64 = listOf(imageBase64),
                            firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis
                        ) { _ -> }
                        parseSummary(summaryText)?.let { (title, summary) ->
                            onSummaryGenerated(title, summary)
                        }
                    } catch (e: Exception) {
                    }
                }

                // 提取题目内容（OCR 或 视觉提取）
                val extractionSystemPrompt = if (models.assistant.useStructuredExtraction) {
                    "${Prompts.EXTRACTION_SYSTEM_BASE}\n用户配置：${models.assistant.ocrPrompt}"
                } else {
                    models.assistant.ocrPrompt
                }

                val extractionUserPrompt = if (models.assistant.useStructuredExtraction) {
                    if (models.engine == EngineType.TEXT_ENGINE) Prompts.OCR_EXTRACTION_USER_PROMPT else Prompts.VISION_EXTRACTION_USER_PROMPT
                } else {
                    "请提取屏幕中的所有文本内容。"
                }

                val extractedText = if (models.engine == EngineType.TEXT_ENGINE) {
                    collectTextStream(
                        provider = models.ocrProvider ?: error("未配置 OCR 模型"),
                        model = models.ocrModel,
                        systemPrompt = extractionSystemPrompt,
                        userPrompt = extractionUserPrompt,
                        imagesBase64 = listOf(imageBase64),
                        firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis,
                        onDelta = onQueryExtracted
                    )
                } else {
                    collectTextStream(
                        provider = models.visionProvider ?: error("未配置视觉模型"),
                        model = models.visionModel,
                        systemPrompt = extractionSystemPrompt,
                        userPrompt = extractionUserPrompt,
                        imagesBase64 = listOf(imageBase64),
                        firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis,
                        onDelta = onQueryExtracted
                    )
                }

                Log.d("ProcessingPipeline", "Extracted OCR Text:\n$extractedText")

                // 检查是否提取到了有效内容
                if (extractedText.isBlank()) {
                    summaryDeferred.await()
                    return@coroutineScope base.copy(
                        status = ProcessingStatus.FAILURE,
                        detail = "未发现可处理的有效内容，请确保截图包含清晰的信息",
                        extractedText = "（未识别到内容）"
                    )
                }

                // 进行详细解答分析
                val answer = if (models.engine == EngineType.TEXT_ENGINE) {
                    collectTextStream(
                        provider = models.textProvider ?: error("未配置文本模型"),
                        model = models.textModel,
                        systemPrompt = "${Prompts.ANALYSIS_SYSTEM_BASE}\n用户配置：${models.assistant.textPrompt}",
                        userPrompt = "这是从图片中提取出的内容，请按照要求进行深度处理：\n\n$extractedText",
                        firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis,
                        onDelta = onDelta
                    )
                } else {
                    collectTextStream(
                        provider = models.visionProvider ?: error("未配置视觉模型"),
                        model = models.visionModel,
                        systemPrompt = "${Prompts.ANALYSIS_SYSTEM_BASE}\n用户配置：${models.assistant.visionPrompt}",
                        userPrompt = "这是你要处理的图片。文字提取结果参考：\n$extractedText\n\n请基于图片内容和提取参考进行详细处理。",
                        imagesBase64 = listOf(imageBase64),
                        firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis,
                        onDelta = onDelta
                    )
                }

                // 请求极简答案/自动化动作
                var automationAction: AutomationAction? = null
                try {
                    val autoText = collectTextStream(
                        provider = models.visionProvider ?: models.ocrProvider
                        ?: error("未配置提供商"),
                        model = if (models.visionProvider != null) models.visionModel else models.ocrModel,
                        systemPrompt = Prompts.AUTOMATION_SYSTEM_PROMPT,
                        userPrompt = "请识别题型并给出答案。参考 OCR 结果：\n$extractedText",
                        imagesBase64 = if (models.visionProvider != null) listOf(imageBase64) else emptyList(),
                        firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis,
                        onDelta = { _ -> }
                    )
                    automationAction = AutomationTools.parseAutomationResponse(autoText)
                } catch (e: Exception) {
                    Log.e("ProcessingPipeline", "Automation request failed", e)
                }

                summaryDeferred.await()

                // 检测自动化动作是否必需
                val mode = ModeRegistry.get(config.selectedModeId)
                if (mode.requiresAutomationAction && automationAction == null) {
                    return@coroutineScope base.copy(
                        status = ProcessingStatus.FAILURE,
                        detail = "自动模式解析失败：模型未返回有效结果，请检查模型是否支持 Chat Completions API"
                    )
                }
                if (answer.isBlank() && extractedText.isBlank() && automationAction == null) {
                    return@coroutineScope base.copy(
                        status = ProcessingStatus.FAILURE,
                        detail = "模型返回空内容，请检查模型配置是否正确"
                    )
                }

                base.copy(
                    status = ProcessingStatus.SUCCESS,
                    extractedText = extractedText,
                    answer = answer,
                    automationAction = automationAction,
                    detail = "分析完成"
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                base.copy(status = ProcessingStatus.FAILURE, detail = SseStreamClient.translateNetworkException(e))
            }
        }
    }

    /**
     * 解析生成的摘要内容。
     */
    private fun parseSummary(text: String): Pair<String, String>? {
        return try {
            val title = text.lines()
                .find { it.startsWith("Title:", ignoreCase = true) }
                ?.removePrefix("Title:")?.trim()
            val summary = text.lines()
                .find { it.startsWith("Summary:", ignoreCase = true) }
                ?.removePrefix("Summary:")?.trim()

            if ((title != null) && (summary != null)) title to summary else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 通用的流式文本收集器。
     */
    private suspend fun collectTextStream(
        provider: ModelProvider,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        imagesBase64: List<String> = emptyList(),
        firstDeltaTimeoutMillis: Long,
        onDelta: (String) -> Unit
    ): String {
        val text = StringBuilder(4096)
        unifiedClient.stream(
            provider = provider,
            model = model,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            imagesBase64 = imagesBase64,
            firstDeltaTimeoutMillis = firstDeltaTimeoutMillis
        ).collect { event ->
            currentCoroutineContext().ensureActive()
            when (event) {
                is LlmEvent.TextDelta -> {
                    val delta = event.text
                    if (delta != "null") {
                        text.append(delta)
                        onDelta(delta)
                    }
                }

                is LlmEvent.Done -> {}
                is LlmEvent.Error -> throw Exception(event.message)
                else -> {}
            }
        }
        return text.toString()
    }
}

/**
 * Bitmap 转 Base64。
 */
private fun Bitmap.toBase64Jpeg(quality: Int = 85): String {
    val output = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, quality, output)
    return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
}
