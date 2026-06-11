package com.tianhuiu.solvex.data.models

import kotlinx.serialization.Serializable

@Serializable
enum class ProcessingStatus {
    RUNNING,
    SUCCESS,
    FAILURE
}

/**
 * 处理路径：定义识别流程。
 */
@Serializable
enum class ProcessingRoute {
    OCR_THEN_LLM,      // 文本模式：OCR 后进行文本分析
    MULTIMODAL_DIRECT  // 视觉模式：直接进行多模态分析
}

@Serializable
data class ProcessingEvent(
    val title: String,
    val detail: String,
    val timestamp: Long = System.currentTimeMillis(),
)

@Serializable
data class AutomationAction(
    val type: String,
    val text: String
) {
    companion object {
        const val TYPE_CLIPBOARD = "set_clipboard"
        const val TYPE_BUBBLE = "show_bubble_letters"
    }
}

@Serializable
data class ExtractedQuestion(
    val type: String? = null,
    val question: String? = null,
    val options: List<String>? = null,
)

@Serializable
data class ProcessingResult(
    val id: String,
    val assistantName: String,
    val route: ProcessingRoute,
    val status: ProcessingStatus,
    val modelSummary: String,
    val detail: String,
    val extractedText: String? = null,
    val answer: String? = null,
    val automationThought: String? = null,
    val automationAction: AutomationAction? = null,
    val screenshotPath: String? = null,
    val screenshotPaths: List<String> = emptyList(),
    val events: List<ProcessingEvent> = emptyList(),
    val createdAtMillis: Long = System.currentTimeMillis()
)
