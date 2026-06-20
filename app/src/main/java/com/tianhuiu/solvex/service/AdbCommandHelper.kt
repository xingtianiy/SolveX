package com.tianhuiu.solvex.service

import android.content.Context

/**
 * ADB 命令辅助工具
 */
object AdbCommandHelper {
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
            ShizukuProcessHelper.exec("settings put secure accessibility_enabled 1")
            val currentServicesResult =
                ShizukuProcessHelper.exec("settings get secure enabled_accessibility_services")
                    ?: return false
            val currentServices = String(currentServicesResult).trim()
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
