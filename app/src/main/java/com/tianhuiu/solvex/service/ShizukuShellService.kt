package com.tianhuiu.solvex.service

import android.os.ParcelFileDescriptor
import kotlin.system.exitProcess

/**
 * Shizuku 用户服务：在 Shizuku 进程中执行 shell 命令并支持大流传输。
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
            val process = Runtime.getRuntime().exec("dumpsys window windows")
            var count = 0
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val isSecure = line.contains("FLAG_SECURE") ||
                                 (line.contains("flags=0x") && hasSecureFlag(line)) ||
                                 (line.contains("mIsWallpaper=false") && line.contains("secure=true"))
                    
                    if (isSecure) {
                        if (!line.contains("com.tianhuiu.solvex")) {
                            count++
                        }
                    }
                }
            }
            process.waitFor()
            count
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    private fun hasSecureFlag(line: String): Boolean {
        // FLAG_SECURE = 0x00002000
        val match = Regex("flags=0x([0-9a-fA-F]+)").find(line)
        return if (match != null) {
            val flagsHex = match.groupValues[1]
            try {
                val flags = flagsHex.toLong(16)
                (flags and 0x00002000L) != 0L
            } catch (_: Exception) {
                false
            }
        } else false
    }

    override fun destroy() {
        exitProcess(0)
    }
}
