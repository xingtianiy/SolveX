package com.tianhuiu.solvex.utils

import com.tianhuiu.solvex.data.models.ExtractedQuestion
import kotlinx.serialization.json.Json

/**
 * 自动化处理工具类。
 */
object AutomationTools {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 解析结构化题目 JSON。
     */
    fun parseStructuredQuestion(text: String): ExtractedQuestion? {
        if (!text.contains("{") || !text.contains("}")) return null
        return try {
            val start = text.indexOf("{")
            val end = text.lastIndexOf("}")
            val jsonStr = text.substring(start, end + 1)
            json.decodeFromString<ExtractedQuestion>(jsonStr)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 专门提取题型。
     */
    fun extractQuestionType(text: String): String? {
        return parseStructuredQuestion(text)?.type
    }
}
