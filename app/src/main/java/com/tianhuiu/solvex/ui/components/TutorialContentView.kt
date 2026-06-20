package com.tianhuiu.solvex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 教程内容渲染组件：纯 Compose 实现，无 WebView / 无网络依赖。
 * 支持 ## / ### 标题、**粗体**、`行内代码`、> 引用块、- 无序列表、1. 有序列表。
 */
@Composable
fun TutorialContent(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val lines = markdown.split("\n")
    val blocks = parseBlocks(lines)

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(horizontal = 4.dp),
    ) {
        var firstBlock = true
        for (block in blocks) {
            if (!firstBlock) {
                Spacer(Modifier.height(12.dp))
            }
            firstBlock = false
            when (block) {
                is Block.Heading2 -> Heading2(block.text)
                is Block.Heading3 -> Heading3(block.text)
                is Block.Paragraph -> Paragraph(block.text)
                is Block.Quote -> Quote(block.text)
                is Block.UnorderedList -> UnorderedList(block.items)
                is Block.OrderedList -> OrderedList(block.items)
                is Block.Divider -> HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                is Block.Empty -> Unit
            }
        }
    }
}

// ─── 内部数据模型 ──────────────────────────────────────────

private sealed class Block {
    data class Heading2(val text: String) : Block()
    data class Heading3(val text: String) : Block()
    data class Paragraph(val text: String) : Block()
    data class Quote(val text: String) : Block()
    data class UnorderedList(val items: List<String>) : Block()
    data class OrderedList(val items: List<String>) : Block()
    data object Divider : Block()
    data object Empty : Block()
}

// ─── 解析器 ────────────────────────────────────────────────

private fun parseBlocks(lines: List<String>): List<Block> {
    val blocks = mutableListOf<Block>()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        when {
            // 分割线 ---
            line.trim().matches(Regex("^-{3,}$")) -> {
                blocks.add(Block.Divider)
                i++
            }

            // ## 标题（排除 ###）
            line.startsWith("## ") && !line.startsWith("### ") -> {
                val text = line.removePrefix("## ").trim()
                if (text.isNotEmpty()) blocks.add(Block.Heading2(text))
                i++
            }

            // ### 标题
            line.startsWith("### ") -> {
                val text = line.removePrefix("### ").trim()
                if (text.isNotEmpty()) blocks.add(Block.Heading3(text))
                i++
            }

            // - / * 无序列表（连续行合并）
            line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                val items = mutableListOf<String>()
                while (i < lines.size && (lines[i].trimStart().startsWith("- ") || lines[i].trimStart().startsWith("* "))) {
                    items.add(lines[i].trimStart().removePrefix("- ").removePrefix("* "))
                    i++
                }
                blocks.add(Block.UnorderedList(items))
            }

            // 1. 2. 有序列表（连续行合并）
            line.trimStart().matches(Regex("^\\d+\\.\\s.*")) -> {
                val items = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().matches(Regex("^\\d+\\.\\s.*"))) {
                    items.add(lines[i].trimStart().replaceFirst(Regex("^\\d+\\.\\s"), ""))
                    i++
                }
                blocks.add(Block.OrderedList(items))
            }

            // 空行跳过
            line.isBlank() -> {
                i++
            }

            // > 引用块（连续行合并）
            line.trimStart().startsWith(">") -> {
                val quoteLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                    val content = lines[i].trimStart().removePrefix(">").trim()
                    if (content.isNotEmpty()) quoteLines.add(content)
                    i++
                }
                blocks.add(Block.Quote(quoteLines.joinToString(" ")))
            }

            // 普通段落（合并连续非空非特殊行）
            else -> {
                val paraLines = mutableListOf(line.trim())
                i++
                while (i < lines.size && lines[i].isNotBlank()
                    && !lines[i].startsWith("#")
                    && !lines[i].trimStart().startsWith("- ")
                    && !lines[i].trimStart().startsWith("* ")
                    && !lines[i].trimStart().matches(Regex("^\\d+\\.\\s.*"))
                    && !lines[i].trimStart().startsWith(">")
                    && !lines[i].trim().matches(Regex("^-{3,}$"))
                ) {
                    paraLines.add(lines[i].trim())
                    i++
                }
                blocks.add(Block.Paragraph(paraLines.joinToString(" ")))
            }
        }
    }
    return blocks
}

// ─── 内联格式渲染 ──────────────────────────────────────────

private data class InlineToken(
    val type: InlineToken.Type,
    val text: String,
) {
    enum class Type { TEXT, BOLD, CODE }
}

/**
 * 将行内文本按 `code` 和 **bold** 切分为 Token。
 */
private fun tokenizeInline(text: String): List<InlineToken> {
    val tokens = mutableListOf<InlineToken>()
    val regex = Regex("""(`[^`]+`)|(\*\*[^*]+\*\*)""")
    var lastEnd = 0

    for (match in regex.findAll(text)) {
        if (match.range.first > lastEnd) {
            tokens.add(InlineToken(InlineToken.Type.TEXT, text.substring(lastEnd, match.range.first)))
        }
        val matched = match.value
        when {
            matched.startsWith("`") && matched.endsWith("`") ->
                tokens.add(InlineToken(InlineToken.Type.CODE, matched.removePrefix("`").removeSuffix("`")))
            matched.startsWith("**") && matched.endsWith("**") ->
                tokens.add(InlineToken(InlineToken.Type.BOLD, matched.removePrefix("**").removeSuffix("**")))
        }
        lastEnd = match.range.last + 1
    }
    if (lastEnd < text.length) {
        tokens.add(InlineToken(InlineToken.Type.TEXT, text.substring(lastEnd)))
    }
    return tokens
}

/**
 * 将 Token 列表渲染为 AnnotatedString。
 */
private fun renderInline(text: String): androidx.compose.ui.text.AnnotatedString = buildAnnotatedString {
    for (token in tokenizeInline(text)) {
        when (token.type) {
            InlineToken.Type.CODE -> withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = Color(0x1A000000),
                    fontSize = 14.sp,
                )
            ) { append(token.text) }

            InlineToken.Type.BOLD -> withStyle(
                SpanStyle(fontWeight = FontWeight.Bold)
            ) { append(token.text) }

            InlineToken.Type.TEXT -> append(token.text)
        }
    }
}

// ─── Block 级 Composable ──────────────────────────────────

@Composable
private fun Heading2(text: String) {
    Text(
        text = renderInline(text),
        style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            lineHeight = 28.sp,
        ),
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Heading3(text: String) {
    Text(
        text = renderInline(text),
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            lineHeight = 24.sp,
        ),
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(2.dp))
}

@Composable
private fun Paragraph(text: String) {
    Text(
        text = renderInline(text),
        style = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 15.sp,
            lineHeight = 24.sp,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Quote(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = renderInline(text),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                lineHeight = 22.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun UnorderedList(items: List<String>) {
    Column {
        for (item in items) {
            Row(
                modifier = Modifier.padding(vertical = 2.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp, start = 4.dp, end = 8.dp)
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
                Text(
                    text = renderInline(item),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun OrderedList(items: List<String>) {
    Column {
        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier.padding(vertical = 2.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(24.dp),
                )
                Text(
                    text = renderInline(item),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
