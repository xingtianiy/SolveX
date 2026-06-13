package com.tianhuiu.solvex.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.tianhuiu.solvex.service.ShizukuUserServiceClient
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
        // 释放 Shizuku 服务绑定
        ShizukuUserServiceClient.release(context)
    }
}
