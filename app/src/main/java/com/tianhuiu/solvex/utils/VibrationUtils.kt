package com.tianhuiu.solvex.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 震动工具类：提供设备震动反馈功能。
 */
object VibrationUtils {
    /**
     * 触发指定时长的震动。
     */
    fun vibrate(context: Context, durationMillis: Long = 100) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

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

    /**
     * 触发成功反馈震动（短震）。
     */
    fun vibrateSuccess(context: Context) {
        vibrate(context, 100)
    }

    /**
     * 触发错误反馈震动（长震）。
     */
    fun vibrateError(context: Context) {
        vibrate(context, 300)
    }
}
