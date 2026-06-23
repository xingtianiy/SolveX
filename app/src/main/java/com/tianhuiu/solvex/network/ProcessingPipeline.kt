package com.tianhuiu.solvex.network

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import com.tianhuiu.solvex.data.models.AppConfig
import com.tianhuiu.solvex.data.models.AssistantConfig
import com.tianhuiu.solvex.data.models.EngineType
import com.tianhuiu.solvex.data.models.ModelProvider
import com.tianhuiu.solvex.data.models.ProcessingEvent
import com.tianhuiu.solvex.data.models.ProcessingResult
import com.tianhuiu.solvex.data.models.ProcessingRoute
import com.tianhuiu.solvex.data.models.ProcessingStatus
import com.tianhuiu.solvex.data.models.currentModeConfig
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
     * 初始化纯文本处理结果，不保存截图。
     */
    fun createBaseResultTextOnly(
        models: ResolvedModels,
        detail: String
    ): ProcessingResult {
        val historyId = UUID.randomUUID().toString()
        return ProcessingResult(
            id = historyId,
            assistantName = models.assistant.name,
            route = ProcessingRoute.OCR_THEN_LLM,
            status = ProcessingStatus.RUNNING,
            modelSummary = buildModelSummary(models.textModel, models.textProvider?.name),
            detail = detail,
            screenshotPath = null,
            screenshotPaths = emptyList(),
            events = listOf(ProcessingEvent(title = "文本捕获", detail = "已通过无障碍服务获取屏幕文本"))
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

                // 检查是否提取到了有效内容
                val isEffectivelyEmpty = if (models.assistant.useStructuredExtraction) {
                    val structured = AutomationTools.parseStructuredQuestion(extractedText)
                    structured == null || (structured.question.isNullOrBlank() && 
                                          structured.options.isNullOrEmpty() && 
                                          structured.image_analysis.isNullOrBlank())
                } else {
                    extractedText.isBlank()
                }

                if (isEffectivelyEmpty) {
                    summaryDeferred.await()
                    return@coroutineScope base.copy(
                        status = ProcessingStatus.FAILURE,
                        detail = "未发现可处理的有效内容，请确保截图包含清晰的信息",
                        extractedText = extractedText.ifBlank { "（未识别到内容）" }
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

                summaryDeferred.await()

                if (answer.isBlank() && extractedText.isBlank()) {
                    return@coroutineScope base.copy(
                        status = ProcessingStatus.FAILURE,
                        detail = "模型返回空内容，请检查模型配置是否正确"
                    )
                }

                base.copy(
                    status = ProcessingStatus.SUCCESS,
                    extractedText = extractedText,
                    answer = answer,
                    detail = "分析完成"
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                base.copy(status = ProcessingStatus.FAILURE, detail = SseStreamClient.translateNetworkException(e))
            }
        }
    }

    /**
     * 纯文本处理路径：接收无障碍服务提取的文本，跳过截图与多模态。
     */
    suspend fun processTextOnly(
        config: AppConfig,
        capturedText: String,
        onSummaryGenerated: (String, String) -> Unit = { _, _ -> },
        onQueryExtracted: (String) -> Unit = {},
        onDelta: (String) -> Unit
    ): ProcessingResult = withContext(Dispatchers.Default) {
        val models = resolveModels(config)
        val base = createBaseResultTextOnly(models, "正在分析文本内容...")

        coroutineScope {
            try {
                onQueryExtracted(capturedText)

                val summaryDeferred = async {
                    try {
                        val summaryText = collectTextStream(
                            provider = models.textProvider ?: models.ocrProvider
                                ?: error("未配置文本模型"),
                            model = models.textModel.ifBlank { models.ocrModel },
                            systemPrompt = Prompts.SUMMARY_SYSTEM_PROMPT,
                            userPrompt = "请为以下文本生成标题和摘要。\n\n$capturedText",
                            imagesBase64 = emptyList(),
                            firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis
                        ) { _ -> }
                        parseSummary(summaryText)?.let { (title, summary) ->
                            onSummaryGenerated(title, summary)
                        }
                    } catch (_: Exception) { }
                }

                if (capturedText.isBlank()) {
                    summaryDeferred.await()
                    return@coroutineScope base.copy(
                        status = ProcessingStatus.FAILURE,
                        detail = "未从屏幕中提取到文本内容",
                        extractedText = ""
                    )
                }

                val analysisPrompt = if (models.assistant.useStructuredExtraction) {
                    "${Prompts.ANALYSIS_SYSTEM_BASE}\n用户配置：${models.assistant.textPrompt}"
                } else {
                    models.assistant.textPrompt
                }

                val answer = collectTextStream(
                    provider = models.textProvider ?: error("未配置文本模型"),
                    model = models.textModel,
                    systemPrompt = analysisPrompt,
                    userPrompt = "以下是从屏幕视图中提取的内容，请按照要求进行深度处理：\n\n$capturedText",
                    imagesBase64 = emptyList(),
                    firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis,
                    onDelta = onDelta
                )

                summaryDeferred.await()

                if (answer.isBlank() && capturedText.isBlank()) {
                    return@coroutineScope base.copy(
                        status = ProcessingStatus.FAILURE,
                        detail = "模型返回空内容，请检查模型配置是否正确"
                    )
                }

                base.copy(
                    status = ProcessingStatus.SUCCESS,
                    extractedText = capturedText,
                    answer = answer,
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
