package com.tianhuiu.solvex.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shizuku ADB 截屏引擎：通过 Shizuku 执行 screencap -p 命令截取屏幕。
 * 一次授权后无需每次弹窗确认。
 */
class ShizukuCaptureEngine(private val context: Context) : ScreenCaptureEngine {

    override suspend fun capture(): Bitmap? = withContext(Dispatchers.IO) {
        val bytes = ShizukuShellScreencap.capturePng(context) ?: return@withContext null
        try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun release() {
        // 不主动释放 Shizuku 用户服务绑定，daemon 进程可跨服务启停复用。
        // 仅在 Shizuku binder 死亡时由 DeathRecipient 自动清理。
    }
}
