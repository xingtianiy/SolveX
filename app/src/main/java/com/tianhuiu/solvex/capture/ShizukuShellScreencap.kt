package com.tianhuiu.solvex.capture

import android.util.Log
import com.tianhuiu.solvex.service.ShizukuUserServiceClient

/**
 * Shizuku shell 截屏工具：通过 `screencap -p` 获取 PNG 字节。
 */
object ShizukuShellScreencap {

    suspend fun capturePng(): ByteArray? {
        val svc = ShizukuUserServiceClient.acquire() ?: return null
        return try {
            val result = svc.exec(arrayOf("sh", "-c", "screencap -p"))
            Log.d("ShizukuScreencap", "Captured ${result.size} bytes")
            result
        } catch (e: Exception) {
            Log.e("ShizukuScreencap", "screencap failed", e)
            ShizukuUserServiceClient.invalidate()
            null
        }
    }
}
