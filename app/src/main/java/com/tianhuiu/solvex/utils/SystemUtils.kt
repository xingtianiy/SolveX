package com.tianhuiu.solvex.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import com.tianhuiu.solvex.service.SolveXAccessibilityService

/**
 * Android 系统服务工具：无障碍检测、剪贴板、震动反馈。
 */
object SystemUtils {

    /** 检测无障碍服务是否已启用 */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val serviceName = "${context.packageName}/${SolveXAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any { it == serviceName }
    }


    /** 将文本复制到系统剪贴板 */
    fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("SolveX Answer", text)
        clipboard.setPrimaryClip(clip)
    }


    @Volatile
    private var cachedVibrator: Vibrator? = null

    private fun getVibrator(context: Context): Vibrator {
        return cachedVibrator ?: synchronized(this) {
            cachedVibrator ?: run {
                val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager =
                        context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibratorManager.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
                cachedVibrator = v
                v
            }
        }
    }

    /** 触发指定时长震动（毫秒） */
    fun vibrate(context: Context, durationMillis: Long = 100) {
        val vibrator = getVibrator(context.applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(durationMillis, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMillis)
        }
    }

    /** 成功反馈短震 */
    fun vibrateSuccess(context: Context) = vibrate(context, 100)

    /** 错误反馈长震 */
    fun vibrateError(context: Context) = vibrate(context, 300)
}
