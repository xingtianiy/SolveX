package com.tianhuiu.solvex.service

import android.os.ParcelFileDescriptor
import kotlin.system.exitProcess

/**
 * Shizuku 用户服务
 */
class ShizukuShellService : IShizukuShellService.Stub() {

    override fun exec(command: Array<out String>?): ByteArray {
        if (command.isNullOrEmpty()) return byteArrayOf()

        return try {
            val process = ProcessBuilder(*command)
                .redirectErrorStream(false)
                .start()

            val stdout = process.inputStream.readBytes()
            stdout
        } catch (_: Exception) {
            byteArrayOf()
        }
    }

    override fun execStream(command: Array<out String>?): ParcelFileDescriptor? {
        if (command.isNullOrEmpty()) return null
        return try {
            val process = ProcessBuilder(*command).start()
            val pipe = ParcelFileDescriptor.createPipe()
            val readSide = pipe[0]
            val writeSide = pipe[1]

            // 在后台线程将进程输出写入管道
            Thread {
                try {
                    process.inputStream.use { input ->
                        ParcelFileDescriptor.AutoCloseOutputStream(writeSide).use { output ->
                            input.copyTo(output)
                        }
                    }
                    process.waitFor()
                } catch (e: Exception) {
                }
            }.start()

            readSide
        } catch (_: Exception) {
            null
        }
    }

    override fun getSecureWindowCount(): Int {
        return try {
            val command = arrayOf("sh", "-c", "dumpsys window windows | grep -E 'Window #|com.tianhuiu.solvex|FLAG_SECURE|secure=true|flags=0x'")
            val process = Runtime.getRuntime().exec(command)

            var secureCount = 0
            var currentIsOurs = false
            var currentHasSecure = false
            var hasStarted = false

            process.inputStream.bufferedReader().forEachLine { line ->
                if (line.contains("Window #")) {
                    if (hasStarted && currentHasSecure && !currentIsOurs) {
                        secureCount++
                    }
                    hasStarted = true
                    currentIsOurs = false
                    currentHasSecure = false
                } else {
                    if (line.contains("com.tianhuiu.solvex")) {
                        currentIsOurs = true
                    }
                    if (line.contains("FLAG_SECURE") || line.contains("secure=true")) {
                        currentHasSecure = true
                    } else if (line.contains("flags=0x")) {
                        if (lineHasSecureFlag(line)) {
                            currentHasSecure = true
                        }
                    }
                }
            }

            // 检查最后一个窗口块
            if (hasStarted && currentHasSecure && !currentIsOurs) {
                secureCount++
            }

            process.destroy()
            secureCount
        } catch (e: Exception) {
            android.util.Log.e("ShizukuShell", "getSecureWindowCount error", e)
            0
        }
    }

    /**
     * 检查单行文本是否包含 FLAG_SECURE。
     */
    private fun lineHasSecureFlag(text: String): Boolean {
        if (text.contains("FLAG_SECURE")) return true
        if (text.contains("secure=true")) return true
        
        val match = Regex("flags=0x([0-9a-fA-F]+)").find(text)
        if (match != null) {
            try {
                val flags = match.groupValues[1].toLong(16)
                if ((flags and 0x00002000L) != 0L) return true
            } catch (_: Exception) { }
        }
        return false
    }

    override fun destroy() {
        exitProcess(0)
    }
}
