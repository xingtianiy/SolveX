package com.tianhuiu.solvex.data.models

import kotlinx.serialization.Serializable

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

    /** 是否强制更新 */
    val forceUpdate: Boolean = false,

    /** APK 大小 */
    val apkSize: String,

    /** 更新日志 */
    val updateLog: List<String>,

    /** GitHub APK 下载地址 */
    val githubUrl: String,

    /** Gitee APK 下载地址 */
    val giteeUrl: String
)

/**
 * 下载状态密封类。
 */
sealed class DownloadStatus {
    object Idle : DownloadStatus()
    data class Downloading(val progress: Int) : DownloadStatus()
    data class Success(val apkPath: String) : DownloadStatus()
    data class Error(val message: String) : DownloadStatus()
}
