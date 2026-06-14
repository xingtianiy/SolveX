package com.tianhuiu.solvex.service

import android.content.Context
import android.util.Log

/**
 * ADB 命令辅助工具：通过已绑定的 Shizuku 用户服务执行提权操作。
 */
object AdbCommandHelper {
    private const val TAG = "AdbCommandHelper"

    /**
     * 通过 Shizuku 授予本应用 WRITE_SECURE_SETTINGS 权限。
     */
    suspend fun grantWriteSecureSettings(context: Context): Boolean {
        val svc = ShizukuUserServiceClient.acquire(context)
        if (svc == null) return false
        return try {
            val result = svc.exec(
                arrayOf(
                    "sh", "-c",
                    "pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS 2>&1"
                )
            )
            val output = String(result)
            Log.d(TAG, "pm grant WRITE_SECURE_SETTINGS: $output")
            !output.contains("Unknown permission") && !output.contains("SecurityException")
        } catch (e: Exception) {
            Log.e(TAG, "grantWriteSecureSettings 失败", e)
            false
        }
    }

    /**
     * 通过 Shizuku 启用本应用的无障碍服务（需要 WRITE_SECURE_SETTINGS 权限）。
     */
    suspend fun enableAccessibilityService(context: Context): Boolean {
        val svc = ShizukuUserServiceClient.acquire(context)
        if (svc == null) return false
        val serviceName =
            "${context.packageName}/${context.packageName}.service.SolveXAccessibilityService"
        return try {
            svc.exec(
                arrayOf(
                    "sh", "-c",
                    "settings put secure accessibility_enabled 1"
                )
            )
            svc.exec(
                arrayOf(
                    "sh", "-c",
                    "settings put secure enabled_accessibility_services '$serviceName'"
                )
            )
            Log.d(TAG, "无障碍服务已通过 settings 命令启用")
            true
        } catch (e: Exception) {
            Log.e(TAG, "enableAccessibilityService 失败", e)
            false
        }
    }
}
