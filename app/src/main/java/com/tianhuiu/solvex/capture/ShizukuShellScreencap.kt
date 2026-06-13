package com.tianhuiu.solvex.capture

import android.os.DeadObjectException
import android.util.Log
import com.tianhuiu.solvex.service.ShizukuUserServiceClient

/**
 * Shizuku shell 截屏工具：通过 `screencap -p` 获取 PNG 字节。
 */
object ShizukuShellScreencap {

    suspend fun capturePng(context: android.content.Context): ByteArray? {
        // 尝试获取服务
        var svc = ShizukuUserServiceClient.acquire(context) ?: return null
        
        return try {
            executeCapture(svc)
        } catch (e: DeadObjectException) {
            Log.w("ShizukuScreencap", "Shizuku service died, retrying once...")
            // 如果是因为 Binder 死亡，尝试重新获取一次
            ShizukuUserServiceClient.invalidate()
            svc = ShizukuUserServiceClient.acquire(context) ?: return null
            try {
                executeCapture(svc)
            } catch (e2: Exception) {
                Log.e("ShizukuScreencap", "Retry capture failed", e2)
                null
            }
        } catch (e: Exception) {
            Log.e("ShizukuScreencap", "screencap failed", e)
            null
        }
    }
    
    private fun executeCapture(svc: com.tianhuiu.solvex.service.IShizukuShellService): ByteArray {
        val result = svc.exec(arrayOf("sh", "-c", "screencap -p"))
        Log.d("ShizukuScreencap", "Captured ${result.size} bytes")
        return result
    }
}
