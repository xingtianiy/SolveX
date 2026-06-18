package com.tianhuiu.solvex.service

import android.content.Context

/**
 * ADB 命令辅助工具：优先通过 Shizuku newProcess API 执行提权操作
 */
object AdbCommandHelper {
    private const val TAG = "AdbCommandHelper"

    /**
     * 通过 Shizuku 授予本应用 WRITE_SECURE_SETTINGS 权限。
     */
    suspend fun grantWriteSecureSettings(context: Context): Boolean {
        val output = ShizukuProcessHelper.exec(
            "pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS 2>&1"
        ) ?: return false
        val outputStr = String(output)
        return !outputStr.contains("Unknown permission") && !outputStr.contains("SecurityException")
    }

    /**
     * 通过 Shizuku 启用本应用的无障碍服务。
     */
    suspend fun enableAccessibilityService(context: Context): Boolean {
        val serviceName = "${context.packageName}/${SolveXAccessibilityService::class.java.name}"
        return try {
            // 先启用无障碍功能总开关
            ShizukuProcessHelper.exec("settings put secure accessibility_enabled 1")
            // 获取当前已启用的服务列表
            val currentServicesResult =
                ShizukuProcessHelper.exec("settings get secure enabled_accessibility_services")
                    ?: return false
            val currentServices = String(currentServicesResult).trim()

            // 如果不在列表中，则追加
            if (!currentServices.contains(serviceName)) {
                val newList = if (currentServices.isEmpty() || currentServices == "null") {
                    serviceName
                } else {
                    "$currentServices:$serviceName"
                }
                ShizukuProcessHelper.exec("settings put secure enabled_accessibility_services '$newList'")
            }

            true
        } catch (e: Exception) {
            false
        }
    }
}
