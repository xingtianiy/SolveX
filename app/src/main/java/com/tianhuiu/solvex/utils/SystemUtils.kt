package com.tianhuiu.solvex.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast

// 系统工具类：震动、剪贴板、Toast
object SystemUtils {
    @Volatile
    private var cachedVibrator: Vibrator? = null

    private fun getVibrator(context: Context): Vibrator {
        return cachedVibrator ?: synchronized(this) {
            cachedVibrator ?: run {
                val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm =
                        context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vm.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
                cachedVibrator = v
                v
            }
        }
    }

    fun vibrate(context: Context, durationMillis: Long = 100) {
        val vibrator = getVibrator(context.applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    durationMillis,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMillis)
        }
    }

    fun vibrateSuccess(context: Context) = vibrate(context, 100)
    fun vibrateError(context: Context) = vibrate(context, 300)

    fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("SolveX Answer", text))
    }

    fun showToast(context: Context, message: String) {
        // 对于普通提示，保留 Toast 是合理的，因为不会打断用户。
        // 但为了响应用户“统一风格”的要求，如果是在 Activity 上下文中且消息重要，
        // 建议在 ViewModel 中使用 State 控制显示 SolveXConfirmDialog。
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

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
}
