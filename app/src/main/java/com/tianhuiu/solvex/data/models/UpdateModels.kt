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

    val versionCode: Int,

    val versionName: String,

    val releaseDate: String,

    val level: String = "recommended",

    val apkSize: String,

    val updateLog: List<String>,

    val githubUrl: String,

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

sealed class DownloadStatus {
    object Idle : DownloadStatus()
    data class Downloading(val progress: Int) : DownloadStatus()
    data class Success(val apkPath: String) : DownloadStatus()
    data class Error(val message: String) : DownloadStatus()
}
