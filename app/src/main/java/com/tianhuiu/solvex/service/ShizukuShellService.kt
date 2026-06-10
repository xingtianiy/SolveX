package com.tianhuiu.solvex.service

import android.os.Process
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
                throw RuntimeException(stderr.ifBlank { "命令执行失败，退出码=$exitCode" })
            }
            stdout
        } catch (e: Exception) {
            e.printStackTrace()
            byteArrayOf()
        }
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
