package com.tianhuiu.solvex.utils

import com.tianhuiu.solvex.data.models.AutomationAction
import com.tianhuiu.solvex.data.models.ExtractedQuestion
import com.tianhuiu.solvex.utils.shared.LatexPatterns
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 模型输出响应解析工具，集中处理 ### 标题分割、结构化提取、最终答案抽取 */
object ResponseParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** ### 标题分割后的区块 */
    data class AnswerSection(
        val title: String,
        val content: String,
    )

    /** 按 ### 标题将模型输出拆分为区块 */
    fun parseAnswerSections(fullAnswer: String): List<AnswerSection> {
        val raw = fullAnswer.trim()
        if (raw.isBlank()) return emptyList()

        val headerRegex = Regex("""^###\s+(.+)$""", RegexOption.MULTILINE)
        val matches = headerRegex.findAll(raw).toList()

        if (matches.isEmpty()) {
            return listOf(AnswerSection(title = "解析结果", content = raw))
        }

        val sections = mutableListOf<AnswerSection>()
        for (i in matches.indices) {
            val match = matches[i]
            val title = match.groupValues[1].trim()
            val contentStart = match.range.last + 1
            val contentEnd = if (i + 1 < matches.size) matches[i + 1].range.first else raw.length
            val content = raw.substring(contentStart, contentEnd).trim()
            if (content.isNotBlank()) {
                sections.add(AnswerSection(title = title, content = content))
            }
        }

        return sections
    }

    /** 从多图合并结果中提取指定题号的章节 */
    fun extractPerQuestionSection(fullAnswer: String, questionNumber: Int, sectionLabel: String = "题目"): String? {
        val escaped = Regex.escape(sectionLabel)
        val headerPattern = Regex("""^##\s*$escaped\s*(\d+)\s*$""", RegexOption.MULTILINE)
        val matches = headerPattern.findAll(fullAnswer).toList()
        if (matches.isEmpty()) return null
        for (i in matches.indices) {
            val match = matches[i]
            val qNum = match.groupValues[1].toIntOrNull() ?: continue
            if (qNum != questionNumber) continue
            val contentStart = match.range.last + 1
            val contentEnd = if (i + 1 < matches.size) matches[i + 1].range.first else fullAnswer.length
            return fullAnswer.substring(contentStart, contentEnd).trim()
        }
        return null
    }

    /** 从多图合并查询文本中提取指定题号的 OCR 内容 */
    fun extractPerQuestionQuery(fullQuery: String, questionNumber: Int, sectionLabel: String = "题目"): String? {
        val escaped = Regex.escape(sectionLabel)
        val headerPattern = Regex("""^$escaped(\d+)[:\s]*$""", RegexOption.MULTILINE)
        val matches = headerPattern.findAll(fullQuery).toList()
        if (matches.isEmpty()) return null
        for (i in matches.indices) {
            val match = matches[i]
            val qNum = match.groupValues[1].toIntOrNull() ?: continue
            if (qNum != questionNumber) continue
            val contentStart = match.range.last + 1
            val contentEnd = if (i + 1 < matches.size) matches[i + 1].range.first else fullQuery.length
            return fullQuery.substring(contentStart, contentEnd).trim()
        }
        return null
    }

    /** 提取最终答案纯文本：取最后一个 ### 区块内容并去除 LaTeX 与 Markdown 符号 */
    fun extractFinalAnswer(fullAnswer: String): String {
        val sections = parseAnswerSections(fullAnswer)
        val lastContent = sections.lastOrNull()?.content ?: fullAnswer
        return lastContent
            .replace(LatexPatterns.latexEnvRegex, "[公式]")
            .replace(LatexPatterns.displayMathRegex, "")
            .replace(LatexPatterns.inlineMathRegex, "")
            .replace(LatexPatterns.latexCmdRegex, "")
            .replace(LatexPatterns.mdHeadingRegex, "")
            .replace(LatexPatterns.mdBoldRegex, "")
            .replace(LatexPatterns.mdItalicRegex, "")
            .replace(LatexPatterns.mdCodeRegex, "")
            .replace(LatexPatterns.mdQuoteRegex, "")
            .replace(LatexPatterns.mdLinkRegex, "$1")
            .replace(LatexPatterns.whitespaceRegex, " ")
            .trim()
    }

    /** 将结构化题目 JSON 渲染为 Markdown */
    fun renderStructuredQuestion(rawText: String): String {
        val structured = parseStructuredQuestion(rawText) ?: return rawText
        return renderStructuredQuestionFromObject(structured)
    }

    /** 从 ExtractedQuestion 对象渲染为 Markdown */
    fun renderStructuredQuestionFromObject(q: ExtractedQuestion): String {
        return buildString {
            if (!q.question.isNullOrBlank()) {
                append("${q.question}\n\n")
            }
            if (!q.options.isNullOrEmpty()) {
                q.options.forEach { option ->
                    append("- $option\n")
                }
                append("\n")
            }
        }.trim()
    }

    /** 解析文本中所有结构化的题目 JSON 对象（支持多题合并场景） */
    fun parseAllStructuredQuestions(text: String): List<ExtractedQuestion> {
        val results = mutableListOf<ExtractedQuestion>()
        var i = 0
        while (true) {
            val start = text.indexOf('{', i)
            if (start == -1) break
            var depth = 0
            var end = start
            for (j in start until text.length) {
                when (text[j]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) { end = j; break }
                    }
                }
            }
            if (depth != 0) break
            val jsonStr = text.substring(start, end + 1)
            try {
                val q = json.decodeFromString<ExtractedQuestion>(jsonStr)
                results.add(q)
            } catch (_: Exception) { }
            i = end + 1
        }
        return results
    }

    /** 解析模型返回的自动化 JSON 响应 */
    fun parseAutomationResponse(text: String): AutomationAction? {
        if (text.isBlank()) return null
        return try {
            val cleanJson = text.substringAfter("{").substringBeforeLast("}")
            val obj = json.parseToJsonElement("{$cleanJson}").jsonObject
            val type = obj["type"]?.jsonPrimitive?.content ?: "other"
            val rawAnswer = obj["answer"]?.jsonPrimitive?.content ?: ""
            val answer = if (type.lowercase() == "choice") {
                rawAnswer.replace(" ", "").replace("\n", "").replace("\r", "").uppercase()
            } else {
                rawAnswer.trim()
            }
            val actionType = if (type.lowercase() == "choice") AutomationAction.TYPE_BUBBLE else AutomationAction.TYPE_CLIPBOARD
            AutomationAction(actionType, answer)
        } catch (_: Exception) { null }
    }

    /** 解析结构化题目 JSON */
    fun parseStructuredQuestion(text: String): ExtractedQuestion? {
        if (!text.contains("{") || !text.contains("}")) return null
        return try {
            val start = text.indexOf("{")
            val end = text.lastIndexOf("}")
            val jsonStr = text.substring(start, end + 1)
            json.decodeFromString<ExtractedQuestion>(jsonStr)
        } catch (_: Exception) { null }
    }

    /** 提取题型 */
    fun extractQuestionType(text: String): String? = parseStructuredQuestion(text)?.type

    /** 从多图结果文本中检测 section 标签，默认返回 "题目" */
    fun detectSectionLabel(text: String): String {
        val match = Regex("""^##\s*(\S+)\s*\d+\s*$""", RegexOption.MULTILINE).find(text)
        return match?.groupValues?.get(1) ?: "题目"
    }

    /** 解析模型摘要输出（Title/Summary 或 标题/摘要 前缀行） */
    fun parseSummary(text: String): Pair<String, String>? {
        return try {
            val title = text.lines()
                .firstOrNull { it.startsWith("Title:", ignoreCase = true) || it.startsWith("标题:") }
                ?.substringAfter(":")?.trim()
            val summary = text.lines()
                .firstOrNull { it.startsWith("Summary:", ignoreCase = true) || it.startsWith("摘要:") }
                ?.substringAfter(":")?.trim()
            if (title != null || summary != null) (title ?: "") to (summary ?: "") else null
        } catch (_: Exception) { null }
    }
}
