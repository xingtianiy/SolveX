package com.tianhuiu.solvex.data.models

import kotlinx.serialization.Serializable

/**
 * 任务处理状态。
 */
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

/**
 * 处理过程中的子事件。
 */
@Serializable
data class ProcessingEvent(
    val title: String,
    val detail: String,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * 自动化动作模型。
 */
@Serializable
data class AutomationAction(
    val type: String, // "set_clipboard" 或 "show_bubble_letters"
    val text: String
)

/**
 * 结构化题目提取模型。
 */
@Serializable
data class ExtractedQuestion(
    val type: String? = null,
    val question: String? = null,
    val options: List<String>? = null,
    val image_analysis: String? = null
)

/**
 * 完整的一次处理结果对象。
 */
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
