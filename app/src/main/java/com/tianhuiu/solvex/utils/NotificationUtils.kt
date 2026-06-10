package com.tianhuiu.solvex.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.tianhuiu.solvex.MainActivity
import com.tianhuiu.solvex.R

/**
 * 通知工具类：负责发送系统通知栏消息以及应用内原生 Toast 提示。
 */
object NotificationUtils {
    private const val CHANNEL_ID = "solvex_result_channel"
    private const val CHANNEL_NAME = "解析结果通知"

    const val ACTION_VIEW_HISTORY = "com.tianhuiu.solvex.VIEW_HISTORY"
    const val EXTRA_HISTORY_ID = "history_id"

    /**
     * 将完整解析结果拆分为“解析过程”和“最终答案”。
     * 以 ### 最终答案 为界限。
     */
    fun splitAnalysisResult(fullAnswer: String): Pair<String, String> {
        val marker = "### 最终答案"
        val index = fullAnswer.indexOf(marker)
        return if (index != -1) {
            val process = fullAnswer.substring(0, index).trim()
            val finalAnswer = fullAnswer.substring(index + marker.length).trim()
            process to finalAnswer
        } else {
            fullAnswer.trim() to ""
        }
    }

    /**
     * 从完整解析结果中提取最终答案，用于通知内容展示。
     * 移除所有 Markdown 符号以获得纯文本展示。
     */
    fun extractFinalAnswer(fullAnswer: String): String {
        val patterns = listOf(
            Regex("""###\s*【?最终答案】?\s*\n+(.*)""", setOf(RegexOption.DOT_MATCHES_ALL)),
            Regex("""###\s*最终答案\s*\n+(.*)""", setOf(RegexOption.DOT_MATCHES_ALL)),
            Regex("""最终答案[：:]\s*(.*)""", setOf(RegexOption.DOT_MATCHES_ALL))
        )

        var rawExtracted = fullAnswer
        for (pattern in patterns) {
            val match = pattern.find(fullAnswer)
            if (match != null) {
                val extracted = match.groupValues[1].trim()
                if (extracted.isNotBlank()) {
                    rawExtracted = extracted
                    break
                }
            }
        }

        // 强力清除 LaTeX 环境和符号
        val cleanText = rawExtracted
            .replace(
                Regex("""\\begin\{.*?\}.*?\\end\{.*?\}""", setOf(RegexOption.DOT_MATCHES_ALL)),
                "[公式]"
            ) // 移除复杂 LaTeX 环境
            .replace(Regex("""\$\$.*?\$\$""", setOf(RegexOption.DOT_MATCHES_ALL)), "") // 移除独立行公式
            .replace(Regex("""\$.*?\$"""), "") // 移除行内公式
            .replace(
                Regex("""\\(begin|end|left|right|vmatrix|matrix|frac|sqrt|cdot|times|div|pm|mp|le|ge|ne|approx|equiv|sum|prod|int|oint|partial|nabla|infty|alpha|beta|gamma|delta|epsilon|zeta|eta|theta|iota|kappa|lambda|mu|nu|xi|omicron|pi|rho|sigma|tau|upsilon|phi|chi|psi|omega)"""),
                ""
            ) // 移除常见 LaTeX 指令

        // 移除 Markdown 标记
        return cleanText
            .replace(Regex("""#+\s+"""), "") // 移除标题 #
            .replace(Regex("""(\*\*|__)"""), "") // 移除加粗 **
            .replace(Regex("""(\*|_)"""), "") // 移除斜体 *
            .replace(Regex("""`"""), "") // 移除代码块 `
            .replace(Regex(""">\s+"""), "") // 移除引用 >
            .replace(Regex("""\[(.*?)\]\((.*?)\)"""), "$1") // 移除链接
            .replace(Regex("""[ \t]+"""), " ") // 将多个空格或制表符替换为单个空格（保留换行）
            .trim()
    }

    /**
     * 将 JSON 格式的题目渲染为 Markdown。
     */
    fun renderStructuredQuestion(rawText: String): String {
        val structured = AutomationTools.parseStructuredQuestion(rawText) ?: return rawText

        return buildString {
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
    }

    /**
     * 从提取的文本中解析题型和题目主体。
     * 期望格式：【题型】题目内容...
     */
    fun parseQuestionInfo(extractedText: String?): Pair<String, String> {
        if (extractedText.isNullOrBlank()) return "解析问题" to "已获取题目内容"

        // 优先尝试解析 JSON
        val structured = AutomationTools.parseStructuredQuestion(extractedText)
        if (structured != null) {
            val type = structured.type ?: "解析问题"
            val content = structured.question ?: "已获取题目内容"
            return "解析问题【$type】" to content
        }

        val typeRegex = Regex("""^【(.+?)】""")
        val match = typeRegex.find(extractedText)

        return if (match != null) {
            val type = match.groupValues[1]
            val content = extractedText.removePrefix(match.value).trim()
            "解析问题【$type】" to content
        } else {
            "解析问题" to extractedText
        }
    }

    /**
     * 发送一个标准 Android Toast 提示。
     */
    fun showToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * 统一反馈接口：
     * 1. 弹出简短的 Toast 给用户
     * 2. 打印详细的 Log 供调试
     */
    fun showFeedback(
        context: Context,
        userMessage: String,
        detailedLog: String? = null,
        tag: String = "SolveX",
        priority: Int = android.util.Log.ERROR,
        throwable: Throwable? = null
    ) {
        Toast.makeText(context, userMessage, Toast.LENGTH_SHORT).show()
        val logContent = detailedLog ?: userMessage
        val fullLog =
            logContent + (throwable?.let { "\n" + android.util.Log.getStackTraceString(it) } ?: "")
        android.util.Log.println(priority, tag, fullLog)
    }

    /**
     * 发送一条解析结果系统通知，包含标题、内容及"查看"按钮。
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

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(false)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .addAction(0, "查看", viewPendingIntent)
            .setContentIntent(viewPendingIntent)
            .build()

        notificationManager.notify(historyId.hashCode(), notification)
    }
}
