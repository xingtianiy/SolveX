package com.tianhuiu.solvex.data.models

import kotlinx.serialization.Serializable

/**
 * 版本信息数据模型。
 */
@Serializable
data class VersionInfo(

    /**
     * 版本号（用于比较更新）
     *
     * 当前安装版本：
     * BuildConfig.VERSION_CODE
     *
     * 服务端版本：
     * versionCode
     *
     * versionCode > BuildConfig.VERSION_CODE
     * 则提示更新
     */
    val versionCode: Int,

    /**
     * 版本名称
     *
     * 示例：
     * 0.0.1-alpha
     * 0.0.1-beta
     * 0.0.1
     * 1.0.0
     */
    val versionName: String,

    /**
     * 发布时间
     *
     * 格式：
     * yyyy-MM-dd
     */
    val releaseDate: String,

    /**
     * 是否强制更新
     *
     * true：
     * 用户必须更新
     *
     * false：
     * 可以跳过
     */
    val forceUpdate: Boolean = false,

    /**
     * APK大小
     *
     * 用于更新弹窗显示
     *
     * 示例：
     * 25.8 MB
     */
    val apkSize: String,

    /**
     * 更新日志
     */
    val updateLog: List<String>,

    /**
     * Github APK下载地址
     */
    val githubUrl: String,

    /**
     * Gitee APK下载地址
     */
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
