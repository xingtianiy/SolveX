package com.tianhuiu.solvex.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.tianhuiu.solvex.R

/**
 * 通知工具类：负责发送系统通知栏消息及结果解析。
 */
object NotificationUtils {
    private const val CHANNEL_ID = "solvex_result_channel"
    private const val CHANNEL_NAME = "解析结果通知"

    const val ACTION_VIEW_HISTORY = "com.tianhuiu.solvex.VIEW_HISTORY"
    const val EXTRA_HISTORY_ID = "history_id"

    // 预编译正则表达式
    private val finalAnswerPatterns = listOf(
        Regex("""###\s*【?最终答案】?\s*\n+(.*)""", setOf(RegexOption.DOT_MATCHES_ALL)),
        Regex("""###\s*最终答案\s*\n+(.*)""", setOf(RegexOption.DOT_MATCHES_ALL)),
        Regex("""最终答案[：:]\s*(.*)""", setOf(RegexOption.DOT_MATCHES_ALL))
    )
    
    // LaTeX 检测正则表达式
    private val latexDetectPattern = Regex(
        """\$|\\\[|\\\(|\\begin\{|\\frac|\\sqrt|\\sum|\\int|\\alpha|\\beta|\\gamma|\\theta|\\pi|\\infty"""
    )

    private val mdHeadingRegex = Regex("""#+\s+""")
    private val mdBoldRegex = Regex("""(\*\*|__)""")
    private val mdItalicRegex = Regex("""(\*|_)""")
    private val mdCodeRegex = Regex("""`""")
    private val mdQuoteRegex = Regex(""">\s+""")
    private val mdLinkRegex = Regex("""\[(.*?)\]\((.*?)\)""")
    private val whitespaceRegex = Regex("""[ \t]+""")

    /**
     * 检测内容是否包含 LaTeX 或 Markdown 公式
     */
    fun hasLatex(text: String): Boolean = latexDetectPattern.containsMatchIn(text)

    /**
     * 从完整解析结果中提取最终答案，用于通知内容展示。
     * 移除所有 Markdown 符号以获得纯文本展示。
     */
    fun extractFinalAnswer(fullAnswer: String): String {
        var rawExtracted = fullAnswer
        for (pattern in finalAnswerPatterns) {
            val match = pattern.find(fullAnswer)
            if (match != null) {
                val extracted = match.groupValues[1].trim()
                if (extracted.isNotBlank()) {
                    rawExtracted = extracted
                    break
                }
            }
        }

        // 移除 Markdown 标记
        return rawExtracted
            .replace(mdHeadingRegex, "")
            .replace(mdBoldRegex, "")
            .replace(mdItalicRegex, "")
            .replace(mdCodeRegex, "")
            .replace(mdQuoteRegex, "")
            .replace(mdLinkRegex, "$1")
            .replace(whitespaceRegex, " ")
            .trim()
    }

    /**
     * 将 JSON 格式的题目渲染为 Markdown。
     * 如果不是 JSON 格式，则原样返回。
     */
    fun renderStructuredQuestion(rawText: String): String {
        val structured = AutomationTools.parseStructuredQuestion(rawText) ?: return rawText

        val content = buildString {
            if (!structured.question.isNullOrBlank()) {
                append("${structured.question}\n\n")
            }
            if (!structured.options.isNullOrEmpty()) {
                structured.options.forEach { option ->
                    append("- $option\n")
                }
                append("\n")
            }
            if (!structured.image_analysis.isNullOrBlank()) {
                append("> **图片补充分析**：${structured.image_analysis}\n")
            }
        }.trim()

        return content.ifBlank { "（内容提取为空，请检查截图或重试）" }
    }

    /**
     * 发送一条解析结果系统通知
     */
    fun sendResultNotification(
        context: Context,
        title: String,
        content: String,
        historyId: String? = null
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "展示解题的最终答案"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val viewIntent =
            Intent(context, com.tianhuiu.solvex.service.MainService::class.java).apply {
                if (historyId != null) {
                    action = ACTION_VIEW_HISTORY
                    putExtra(EXTRA_HISTORY_ID, historyId)
                }
            }
        val viewPendingIntent = PendingIntent.getService(
            context, (historyId ?: "error").hashCode(), viewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val finalContent = if (hasLatex(content)) {
            "点击查看详情"
        } else {
            content
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(finalContent)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(false)
            .setStyle(NotificationCompat.BigTextStyle().bigText(finalContent))
            .addAction(0, "查看", viewPendingIntent)
            .setContentIntent(viewPendingIntent)
            .build()

        notificationManager.notify(historyId.hashCode(), notification)
    }
}
