package com.tianhuiu.solvex.capture

import com.tianhuiu.solvex.service.ShizukuProcessHelper
import com.tianhuiu.solvex.service.ShizukuUserServiceClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ShizukuShellScreencap {
    suspend fun capturePng(context: android.content.Context): ByteArray? =
        withContext(Dispatchers.IO) {
            val result = ShizukuProcessHelper.execRaw(arrayOf("screencap", "-p"))
            if (result != null) {
                return@withContext result
            }
            val svc = ShizukuUserServiceClient.acquire(context) ?: run {
                return@withContext null
            }
            return@withContext try {
                val pfd = svc.execStream(arrayOf("screencap", "-p")) ?: return@withContext null

                android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                    input.readBytes()
                }
            } catch (e: Exception) {
                null
            }
        }
}
