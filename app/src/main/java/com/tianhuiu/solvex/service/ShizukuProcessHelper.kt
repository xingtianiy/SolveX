package com.tianhuiu.solvex.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.lang.reflect.Method

/**
 * 通过 Shizuku newProcess API 直接执行命令
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
