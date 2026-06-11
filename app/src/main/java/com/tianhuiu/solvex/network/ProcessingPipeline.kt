package com.tianhuiu.solvex.network

import android.content.Context
import android.graphics.Bitmap
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
import com.tianhuiu.solvex.data.models.ProjectMode
import com.tianhuiu.solvex.utils.ResponseParser
import com.tianhuiu.solvex.utils.FileUtils
import com.tianhuiu.solvex.utils.toBase64Jpeg
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.UUID

class ProcessingPipeline(
    private val appContext: Context,
    private val unifiedClient: UnifiedLLMClient,
) {

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

    fun resolveModels(config: AppConfig): ResolvedModels {
        val assistant = config.assistants.find { it.id == config.selectedAssistantId }
            ?: config.assistants.firstOrNull()
            ?: error("请先配置助手")

        val (textPid, textModel, visionPid, visionModel, ocrPid, ocrModel, timeout, defaultPid) = when (config.selectedMode) {
            ProjectMode.STUDY_MODE -> {
                val c = config.studyConfig
                DataConfig(
                    c.textProviderId,
                    c.textModel,
                    c.visionProviderId,
                    c.visionModel,
                    c.ocrProviderId,
                    c.ocrModel,
                    c.firstDeltaTimeoutSeconds,
                    config.defaultProviderId
                )
            }

            ProjectMode.QUICK_MODE, ProjectMode.MULTI_IMAGE_MODE -> {
                val c = config.quickConfig
                DataConfig(
                    c.textProviderId,
                    c.textModel,
                    c.visionProviderId,
                    c.visionModel,
                    c.ocrProviderId,
                    c.ocrModel,
                    c.firstDeltaTimeoutSeconds,
                    config.defaultProviderId
                )
            }
        }

        val finalTextPid = textPid ?: defaultPid
        val finalVisionPid = visionPid ?: defaultPid
        val finalOcrPid = ocrPid ?: defaultPid

        val textProvider = config.providers.find { it.id == finalTextPid }
        val visionProvider = config.providers.find { it.id == finalVisionPid }
        val ocrProvider = config.providers.find { it.id == finalOcrPid }

        val resolvedTextModel = if (textModel.isNullOrBlank()) {
            textProvider?.defaultTextModel ?: ""
        } else textModel

        val resolvedVisionModel = if (visionModel.isNullOrBlank()) {
            visionProvider?.defaultVisionModel ?: ""
        } else visionModel

        val resolvedOcrModel = if (ocrModel.isNullOrBlank()) {
            ocrProvider?.defaultOcrModel ?: ""
        } else ocrModel

        return ResolvedModels(
            assistant = assistant,
            textProvider = textProvider,
            textModel = resolvedTextModel,
            visionProvider = visionProvider,
            visionModel = resolvedVisionModel,
            ocrProvider = ocrProvider,
            ocrModel = resolvedOcrModel,
            firstDeltaTimeoutMillis = (timeout ?: 10L).coerceAtLeast(1) * 1000L,
            engine = config.selectedEngine
        )
    }

    private data class DataConfig(
        val textPid: String?,
        val textModel: String?,
        val visionPid: String?,
        val visionModel: String?,
        val ocrPid: String?,
        val ocrModel: String?,
        val timeout: Long?,
        val defaultPid: String?
    )

    private fun buildModelSummary(model: String, providerName: String?): String {
        val trimmedModel = model.trim()
        val trimmedProvider = providerName?.trim()
        if (trimmedModel.isBlank()) return ""
        return if (trimmedProvider.isNullOrBlank()) trimmedModel else "$trimmedModel（$trimmedProvider）"
    }

    fun createBaseResult(
        models: ResolvedModels,
        bitmap: Bitmap,
        detail: String,
        additionalBitmaps: List<Bitmap> = emptyList()
    ): ProcessingResult {
        val historyId = UUID.randomUUID().toString()
        val imagePath = FileUtils.saveBitmapToInternal(appContext, bitmap)
        val additionalPaths = additionalBitmaps.mapNotNull { FileUtils.saveBitmapToInternal(appContext, it) }
        val allPaths = listOfNotNull(imagePath) + additionalPaths

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
            screenshotPaths = allPaths,
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
                        ResponseParser.parseSummary(summaryText)?.let { (title, summary) ->
                            onSummaryGenerated(title, summary)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                    }
                }

                val extractedText = if (models.engine == EngineType.TEXT_ENGINE) {
                    collectTextStream(
                        provider = models.ocrProvider ?: error("未配置 OCR 模型"),
                        model = models.ocrModel,
                        systemPrompt = Prompts.EXTRACTION_SYSTEM_PROMPT,
                        userPrompt = models.assistant.ocrPrompt,
                        imagesBase64 = listOf(imageBase64),
                        firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis,
                        onDelta = onQueryExtracted
                    )
                } else {
                    collectTextStream(
                        provider = models.visionProvider ?: error("未配置视觉模型"),
                        model = models.visionModel,
                        systemPrompt = Prompts.EXTRACTION_SYSTEM_PROMPT,
                        userPrompt = models.assistant.ocrPrompt,
                        imagesBase64 = listOf(imageBase64),
                        firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis,
                        onDelta = onQueryExtracted
                    )
                }

                Log.d("ProcessingPipeline", "Extracted OCR Text:\n$extractedText")

                if (extractedText.isBlank()) {
                    summaryDeferred.await()
                    return@coroutineScope base.copy(
                        status = ProcessingStatus.FAILURE,
                        detail = "未发现可处理的有效内容，请确保截图包含清晰的信息",
                        extractedText = "（未识别到内容）"
                    )
                }

                val answer = if (models.engine == EngineType.TEXT_ENGINE) {
                    collectTextStream(
                        provider = models.textProvider ?: error("未配置文本模型"),
                        model = models.textModel,
                        systemPrompt = Prompts.ANALYSIS_SYSTEM_PROMPT,
                        userPrompt = "${models.assistant.textPrompt}\n\n以下是提取出的内容：\n\n$extractedText",
                        firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis,
                        onDelta = onDelta
                    )
                } else {
                    collectTextStream(
                        provider = models.visionProvider ?: error("未配置视觉模型"),
                        model = models.visionModel,
                        systemPrompt = Prompts.ANALYSIS_SYSTEM_PROMPT,
                        userPrompt = "${models.assistant.visionPrompt}\n\n文字提取参考：\n$extractedText",
                        imagesBase64 = listOf(imageBase64),
                        firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis,
                        onDelta = onDelta
                    )
                }

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
                    automationAction = ResponseParser.parseAutomationResponse(autoText)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("ProcessingPipeline", "Automation request failed", e)
                }

                summaryDeferred.await()

                if (config.selectedMode == ProjectMode.QUICK_MODE && automationAction == null) {
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
                base.copy(status = ProcessingStatus.FAILURE, detail = e.message ?: "未知错误")
            }
        }
    }

    /**
     * 多图模式处理：根据配置选择合并提交或逐张分析。
     */
    suspend fun processMultiImage(
        config: AppConfig,
        bitmaps: List<Bitmap>,
        onSummaryGenerated: (String, String) -> Unit = { _, _ -> },
        onQueryExtracted: (String) -> Unit = {},
        onDelta: (String) -> Unit,
        onPageStart: (Int) -> Unit = {},
        onImageComplete: ((Int, String) -> Unit)? = null
    ): ProcessingResult = withContext(Dispatchers.Default) {
        val models = resolveModels(config)
        val base = createBaseResult(models, bitmaps.first(), "正在处理多张截图...", bitmaps.drop(1))

        coroutineScope {
            try {
                val summaryDeferred = async {
                    try {
                        val summaryText = collectTextStream(
                            provider = models.visionProvider ?: models.ocrProvider
                            ?: error("未配置视觉或文本模型"),
                            model = if (models.visionProvider != null) models.visionModel else models.ocrModel,
                            systemPrompt = Prompts.SUMMARY_SYSTEM_PROMPT,
                            userPrompt = "请为此截图生成标题和摘要。",
                            imagesBase64 = listOf(bitmaps.first().toBase64Jpeg()),
                            firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis
                        ) { _ -> }
                        ResponseParser.parseSummary(summaryText)?.let { (title, summary) ->
                            onSummaryGenerated(title, summary)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                    }
                }

                val multiVisionProviderId = config.multiImageConfig.multiImageVisionProviderId
                val multiVisionModel = config.multiImageConfig.multiImageVisionModel
                val effectiveVisionProvider = (if (multiVisionProviderId != null) {
                    config.providers.find { it.id == multiVisionProviderId } ?: models.visionProvider
                } else models.visionProvider)
                    ?: error("多图模式需要配置视觉模型")
                val effectiveVisionModel = multiVisionModel?.ifBlank { null } ?: models.visionModel

                val mergeEnabled = config.multiImageConfig.multiImageMergeEnabled
                val sectionLabel = models.assistant.sectionLabel

                val allExtracted: StringBuilder
                val allAnswers: StringBuilder
                var hasAnyContent = false

                if (mergeEnabled) {
                    val imagesBase64 = bitmaps.map { it.toBase64Jpeg() }
                    onQueryExtracted("多图合并分析\n")

                    val extractedText = collectTextStream(
                        provider = effectiveVisionProvider,
                        model = effectiveVisionModel,
                        systemPrompt = Prompts.EXTRACTION_SYSTEM_PROMPT,
                        userPrompt = models.assistant.ocrPrompt,
                        imagesBase64 = imagesBase64,
                        firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis,
                        onDelta = onQueryExtracted
                    )
                    hasAnyContent = extractedText.isNotBlank()
                    allExtracted = StringBuilder(extractedText)

                    val answer = collectTextStream(
                        provider = effectiveVisionProvider,
                        model = effectiveVisionModel,
                        systemPrompt = Prompts.ANALYSIS_SYSTEM_PROMPT,
                        userPrompt = "${models.assistant.visionPrompt}\n\n文字提取参考：\n$extractedText",
                        imagesBase64 = imagesBase64,
                        firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis,
                        onDelta = onDelta
                    )
                    allAnswers = StringBuilder(answer)
                } else {
                    allExtracted = StringBuilder()
                    allAnswers = StringBuilder()

                    bitmaps.forEachIndexed { index, bitmap ->
                        val n = index + 1
                        val imageB64 = bitmap.toBase64Jpeg()

                        onPageStart(index)

                        onQueryExtracted("${sectionLabel}$n\n")

                        val extracted = collectTextStream(
                            provider = effectiveVisionProvider,
                            model = effectiveVisionModel,
                            systemPrompt = Prompts.EXTRACTION_SYSTEM_PROMPT,
                            userPrompt = models.assistant.ocrPrompt,
                            imagesBase64 = listOf(imageB64),
                            firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis,
                            onDelta = onQueryExtracted
                        )
                        onQueryExtracted("\n")

                        if (extracted.isNotBlank()) hasAnyContent = true
                        allExtracted.appendLine("${sectionLabel}$n:")
                        allExtracted.appendLine(extracted)

                        onDelta("## ${sectionLabel} $n\n")
                        val answer = collectTextStream(
                            provider = effectiveVisionProvider,
                            model = effectiveVisionModel,
                            systemPrompt = Prompts.ANALYSIS_SYSTEM_PROMPT,
                            userPrompt = "${models.assistant.visionPrompt}\n\n文字提取参考：\n$extracted",
                            imagesBase64 = listOf(imageB64),
                            firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis,
                            onDelta = onDelta
                        )
                        onDelta("\n\n")
                        allAnswers.appendLine("## ${sectionLabel} $n")
                        allAnswers.appendLine(answer)
                        allAnswers.appendLine()

                        onImageComplete?.invoke(index, answer)
                    }
                }

                var automationAction: AutomationAction? = null
                if (allExtracted.isNotBlank()) {
                    try {
                        val autoText = collectTextStream(
                            provider = effectiveVisionProvider,
                            model = effectiveVisionModel,
                            systemPrompt = Prompts.AUTOMATION_SYSTEM_PROMPT,
                            userPrompt = "请识别题型并给出答案。参考以下内容：\n${allExtracted}",
                            imagesBase64 = emptyList(),
                            firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis,
                            onDelta = { _ -> }
                        )
                        automationAction = ResponseParser.parseAutomationResponse(autoText)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e("ProcessingPipeline", "Multi-image automation failed", e)
                    }
                }

                summaryDeferred.await()

                if (!hasAnyContent && automationAction == null) {
                    return@coroutineScope base.copy(
                        status = ProcessingStatus.FAILURE,
                        detail = "未从多张截图中发现可处理的有效内容"
                    )
                }

                base.copy(
                    status = ProcessingStatus.SUCCESS,
                    extractedText = allExtracted.toString(),
                    answer = allAnswers.toString(),
                    automationAction = automationAction,
                    detail = "多图分析完成"
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                base.copy(status = ProcessingStatus.FAILURE, detail = e.message ?: "未知错误")
            }
        }
    }

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
