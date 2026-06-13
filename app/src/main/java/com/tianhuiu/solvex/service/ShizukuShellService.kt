package com.tianhuiu.solvex.service

import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.system.exitProcess

/**
 * Shizuku 用户服务：在 Shizuku 进程中执行 shell 命令。
 */
class ShizukuShellService : IShizukuShellService.Stub() {

    override fun exec(command: Array<out String>?): ByteArray {
        if (command.isNullOrEmpty()) return byteArrayOf()

        return try {
            val process = ProcessBuilder(*command)
                .redirectErrorStream(false)
                .start()
            val stdout = process.inputStream.use(::readAllBytes)
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                // 如果是截图命令失败，不抛出异常影响主进程，只记录日志
                if (command.contains("screencap")) {
                    android.util.Log.e("ShizukuService", "screencap failed: $stderr")
                } else {
                    throw RuntimeException(stderr.ifBlank { "命令执行失败，退出码=$exitCode" })
                }
            }
            stdout
        } catch (e: Exception) {
            e.printStackTrace()
            byteArrayOf()
        }
    }

    override fun getSecureWindowCount(): Int {
        return try {
            val process = Runtime.getRuntime().exec("dumpsys window windows")
            val reader = process.inputStream.bufferedReader()
            var count = 0
            reader.useLines { lines ->
                lines.forEach { line ->
                    // 匹配包含 FLAG_SECURE 的窗口行
                    // dumpsys 输出中通常包含 mHasSurface=true 且 flags 包含 0x00002000 (FLAG_SECURE)
                    // 或者直接有文本标识
                    if (line.contains("FLAG_SECURE") || line.contains("flags=0x") && hasSecureFlag(line)) {
                        // 排除自身窗口（如果需要，但通常检测外部窗口即可）
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

    private fun readAllBytes(inputStream: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = inputStream.read(buffer)
            if (count <= 0) break
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
