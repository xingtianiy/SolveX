package com.tianhuiu.solvex.data.models

import kotlinx.serialization.Serializable

/**
 * 更新等级：决定弹窗行为和检测频率。
 */
@Serializable
enum class UpdateLevel {
    CRITICAL,    // 安全/崩溃修复，不可关闭
    RECOMMENDED, // 重要功能/性能修复，可推迟
    OPTIONAL     // 新功能/体验优化，静默提示
}

/**
 * 版本信息数据模型。
 */
@Serializable
data class VersionInfo(

    /** 版本号 */
    val versionCode: Int,

    /** 版本名称 */
    val versionName: String,

    /** 发布日期（yyyy-MM-dd） */
    val releaseDate: String,

    /** 更新等级 */
    val level: String = "recommended",

    /** APK 大小 */
    val apkSize: String,

    /** 更新日志 */
    val updateLog: List<String>,

    /** GitHub APK 下载地址 */
    val githubUrl: String,

    /** Gitee APK 下载地址 */
    val giteeUrl: String
) {
    val updateLevel: UpdateLevel
        get() = when (level.lowercase()) {
            "critical" -> UpdateLevel.CRITICAL
            "recommended" -> UpdateLevel.RECOMMENDED
            else -> UpdateLevel.OPTIONAL
        }

    val isDismissible: Boolean
        get() = updateLevel != UpdateLevel.CRITICAL
}

/**
 * 下载状态密封类。
 */
sealed class DownloadStatus {
    object Idle : DownloadStatus()
    data class Downloading(val progress: Int) : DownloadStatus()
    data class Success(val apkPath: String) : DownloadStatus()
    data class Error(val message: String) : DownloadStatus()
}
