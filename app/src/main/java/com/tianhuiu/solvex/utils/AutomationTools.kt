package com.tianhuiu.solvex.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.tianhuiu.solvex.data.models.AutomationAction
import com.tianhuiu.solvex.data.models.ExtractedQuestion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 自动化处理工具类。
 */
object AutomationTools {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 将文字复制到系统剪贴板。
     */
    fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("SolveX Answer", text)
        clipboard.setPrimaryClip(clip)
    }

    /**
     * 解析模型返回的自动化 JSON 响应。
     */
    fun parseAutomationResponse(text: String): AutomationAction? {
        if (text.isBlank()) return null
        return try {
            val cleanJson = text.substringAfter("{").substringBeforeLast("}")
            val obj = json.parseToJsonElement("{$cleanJson}").jsonObject
            val type = obj["type"]?.jsonPrimitive?.content ?: "other"
            val rawAnswer = obj["answer"]?.jsonPrimitive?.content ?: ""

            // 处理答案中的缩进和多余空白，特别是某些模型返回的 A B C 格式
            val answer = if (type.lowercase() == "choice") {
                rawAnswer.replace(" ", "").replace("\n", "").replace("\r", "").uppercase()
            } else {
                rawAnswer.trim()
            }

            val actionType =
                if (type.lowercase() == "choice") "show_bubble_letters" else "set_clipboard"
            AutomationAction(actionType, answer)
        } catch (_: Exception) {
            null
        }
    }

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
