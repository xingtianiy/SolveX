package com.tianhuiu.solvex.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.tianhuiu.solvex.R

/** 系统通知与 Toast 工具 */
object NotificationHelper {
    private const val CHANNEL_ID = "solvex_result_channel"
    private const val CHANNEL_NAME = "解析结果通知"

    const val ACTION_VIEW_HISTORY = "com.tianhuiu.solvex.VIEW_HISTORY"
    const val EXTRA_HISTORY_ID = "history_id"

    /** 显示短 Toast */
    fun showToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /** 统一反馈：Toast + Log */
    fun showFeedback(
        context: Context,
        userMessage: String,
        detailedLog: String? = null,
        tag: String = "SolveX",
        priority: Int = Log.ERROR,
        throwable: Throwable? = null
    ) {
        Toast.makeText(context, userMessage, Toast.LENGTH_SHORT).show()
        val logContent = detailedLog ?: userMessage
        val fullLog = logContent + (throwable?.let { "\n" + Log.getStackTraceString(it) } ?: "")
        Log.println(priority, tag, fullLog)
    }

    /** 发送解析结果系统通知 */
    fun sendResultNotification(
        context: Context,
        title: String,
        content: String,
        historyId: String? = null,
        notificationId: Int? = null
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "展示解题的最终答案" }
            notificationManager.createNotificationChannel(channel)
        }

        val viewIntent = Intent(context, com.tianhuiu.solvex.service.MainService::class.java).apply {
            if (historyId != null) {
                action = ACTION_VIEW_HISTORY
                putExtra(EXTRA_HISTORY_ID, historyId)
            }
        }
        val viewPendingIntent = PendingIntent.getService(
            context, (historyId ?: "error").hashCode(), viewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
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

        notificationManager.notify(
            notificationId ?: (historyId?.hashCode() ?: System.currentTimeMillis().toInt()),
            notification
        )
    }
}
