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

    /**
     * 触发指定时长的震动。
     */
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
