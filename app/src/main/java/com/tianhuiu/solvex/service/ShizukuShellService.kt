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

            // 超时保护：最多等待 3 秒，防止 dumpsys 卡死
            val completed = process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                android.util.Log.w("ShizukuShell", "dumpsys window windows timed out")
                return 0
            }

            val output = process.inputStream.bufferedReader().readText()

            // 按 "Window #N" 分割成一个个窗口块，每块只计一次 FLAG_SECURE
            val windowSections = output.split(Regex("(?=\\n\\s*Window\\s+#\\d+)"))
            // 第一段是表头，跳过；后续每段对应一个窗口
            windowSections.drop(1).count { section ->
                // 排除自身应用的窗口
                if (section.contains("com.tianhuiu.solvex")) return@count false
                // 检查该窗口块内是否有 FLAG_SECURE 标记
                sectionHasSecureFlag(section)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    /**
     * 检查一段窗口文本中是否包含 FLAG_SECURE。
     * 三种匹配方式：文本标记 / flags 十六进制 / secure 属性。
     */
    private fun sectionHasSecureFlag(text: String): Boolean {
        if (text.contains("FLAG_SECURE")) return true
        // 解析 flags=0x... 中的十六进制值
        val match = Regex("flags=0x([0-9a-fA-F]+)").find(text)
        if (match != null) {
            try {
                val flags = match.groupValues[1].toLong(16)
                if ((flags and 0x00002000L) != 0L) return true
            } catch (_: Exception) { }
        }
        // 部分输出格式使用 secure=true 标记
        if (text.contains("mIsWallpaper=false") && text.contains("secure=true")) return true
        return false
    }

    override fun destroy() {
        exitProcess(0)
    }
}
