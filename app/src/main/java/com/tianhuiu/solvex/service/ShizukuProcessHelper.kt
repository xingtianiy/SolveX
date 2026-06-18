package com.tianhuiu.solvex.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.lang.reflect.Method

/**
 * 通过 Shizuku newProcess API 直接执行命令（避免 bindUserService）。
 *
 * Shizuku v13 中将 newProcess 标记为 private（计划在 v14 移除），
 * 但底层 IShizukuService 仍然支持该功能。本工具通过反射调用，
 * 在 Shizuku v13 服务端上正常工作。
 *
 * 当 bindUserService 在部分设备（如 OPPO）上超时时，此方案可绕过该问题。
 */
object ShizukuProcessHelper {
    private const val TAG = "ShizukuProcess"

    private val newProcessMethod: Method by lazy {
        Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        ).also { it.isAccessible = true }
    }

    /**
     * 执行命令并返回 stdout 输出。
     */
    suspend fun exec(command: String): ByteArray? = withContext(Dispatchers.IO) {
        execRaw(arrayOf("sh", "-c", command))
    }

    suspend fun execRaw(cmd: Array<String>): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val process = newProcessMethod.invoke(null, cmd, null, null)
            if (process != null) {
                @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                (process as Process).inputStream.use { input ->
                    input.readBytes()
                }
            } else {
                Log.e(TAG, "newProcess returned null")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "newProcess failed", e)
            null
        }
    }
}
