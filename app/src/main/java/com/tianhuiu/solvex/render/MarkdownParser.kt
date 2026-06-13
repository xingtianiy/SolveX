package com.tianhuiu.solvex.render

// Markdown 解析结果
data class Section(
    val title: String,
    val content: String,
)

// Markdown 解析器：识别 ### 标题作为模块分隔符
object MarkdownParser {
    private val headingRegex = Regex("(?m)^### (.+)$")

    fun parse(markdown: String): List<Section> {
        val sections = mutableListOf<Section>()
        val matches = headingRegex.findAll(markdown).toList()

        if (matches.isEmpty()) {
            val trimmed = markdown.trim()
            if (trimmed.isNotBlank()) {
                sections.add(Section(title = "", content = trimmed))
            }
            return sections
        }

        // 处理第一个标题前的内容
        val firstMatch = matches.first()
        val beforeFirst = markdown.substring(0, firstMatch.range.first).trim()
        if (beforeFirst.isNotBlank()) {
            sections.add(Section(title = "", content = beforeFirst))
        }

        for (i in matches.indices) {
            val title = matches[i].groupValues[1].trim()
            val start = matches[i].range.last + 1
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else markdown.length
            val content = markdown.substring(start, end).trim()

            if (content.isNotBlank()) {
                sections.add(Section(title = title, content = content))
            }
        }

        return sections
    }
}
