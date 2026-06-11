package com.tianhuiu.solvex.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.tianhuiu.solvex.data.models.DownloadStatus
import com.tianhuiu.solvex.data.models.VersionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * 软件更新管理器：负责检测版本、下载 APK 及引导安装。
 */
class UpdateManager(
    private val context: Context,
    private val client: OkHttpClient,
    private val json: Json
) {
    private data class UpdateSource(val name: String, val url: String)

    private val versionSources = listOf(
        UpdateSource(
            "Gitee",
            "https://gitee.com/xingtianiy/SolveX/raw/main/version.json"
        ),
        UpdateSource(
            "Github",
            "https://raw.githubusercontent.com/xingtianiy/SolveX/main/version.json"
        ),
        UpdateSource(
            "JsDelivr",
            "https://cdn.jsdelivr.net/gh/xingtianiy/SolveX@main/version.json"
        )
    )

    /**
     * 竞速检测新版本：并行请求所有源，取最快成功响应。
     * @param etag 上次保存的 ETag，用于条件请求
     * @return Pair<VersionInfo, String?> 版本信息和新的 ETag
     */
    suspend fun checkUpdate(etag: String? = null): Result<Pair<VersionInfo, String?>> =
        withContext(Dispatchers.IO) {
            val deferred = versionSources.map { source ->
                async {
                    fetchVersionInfo(source.url, source.name, etag)
                }
            }

            // 收集首个成功结果
            var lastError: Throwable? = null
            for (d in deferred) {
                d.await().fold(
                    onSuccess = { return@withContext Result.success(it) },
                    onFailure = { lastError = it }
                )
            }

            Result.failure(lastError ?: Exception("更新源不可用"))
        }

    /**
     * 请求单个源，支持 ETag 条件请求。
     * @return Result<Pair<VersionInfo, String?>> 版本信息 + 新 ETag
     */
    private fun fetchVersionInfo(
        url: String,
        sourceName: String,
        etag: String?
    ): Result<Pair<VersionInfo, String?>> {
        return try {
            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", "SolveX-Update-Checker")
                .header("Cache-Control", "no-cache")
            if (etag != null) {
                requestBuilder.header("If-None-Match", etag)
            }
            val request = requestBuilder.build()

            client.newCall(request).execute().use { response ->
                // 304 Not Modified — 版本未变化
                if (response.code == 304) {
                    return Result.failure(NotModifiedException(sourceName))
                }

                if (!response.isSuccessful) {
                    return Result.failure(Exception("[$sourceName] HTTP ${response.code}"))
                }
                val body = response.body?.string()
                    ?: return Result.failure(Exception("[$sourceName] 响应体为空"))

                try {
                    val info = json.decodeFromString<VersionInfo>(body)
                    val newEtag = response.header("ETag")
                    Result.success(info to newEtag)
                } catch (e: Exception) {
                    Result.failure(Exception("[$sourceName] 数据解析失败"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("[$sourceName] ${e.message ?: "网络异常"}"))
        }
    }

    /**
     * 从缓存的 JSON 字符串解析版本信息。
     */
    fun parseCachedVersion(jsonStr: String): VersionInfo? {
        return try {
            json.decodeFromString<VersionInfo>(jsonStr)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 序列化版本信息为 JSON 字符串用于缓存。
     */
    fun encodeVersion(info: VersionInfo): String {
        return json.encodeToString(info)
    }

    /**
     * 下载 APK 文件。
     */
    fun downloadApk(githubUrl: String, giteeUrl: String): Flow<DownloadStatus> = flow {
        val downloadSources = listOf(
            UpdateSource("Gitee", giteeUrl),
            UpdateSource("Github", githubUrl)
        )

        var isSuccessful = false

        for (source in downloadSources) {
            if (source.url.isBlank()) continue

            try {
                performDownload(source.url).collect { status ->
                    if (status is DownloadStatus.Success) {
                        isSuccessful = true
                        emit(status)
                    } else if (status is DownloadStatus.Error) {
                        Log.w("UpdateManager", "源 ${source.name} 下载失败: ${status.message}")
                    } else {
                        emit(status)
                    }
                }
                if (isSuccessful) break
            } catch (e: Exception) {
                Log.e("UpdateManager", "源 ${source.name} 下载异常", e)
            }
        }

        if (!isSuccessful) {
            emit(DownloadStatus.Error("所有下载源均失效"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 核心下载逻辑。
     */
    private fun performDownload(url: String): Flow<DownloadStatus> = flow {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SolveX-Downloader")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(DownloadStatus.Error("HTTP ${response.code}"))
                    return@flow
                }

                val body = response.body ?: throw Exception("响应体为空")
                val totalBytes = body.contentLength()

                // 验证内容类型
                val contentType = response.header("Content-Type") ?: ""
                if (contentType.contains("text/html")) {
                    emit(DownloadStatus.Error("下载链接失效"))
                    return@flow
                }

                val destinationFile = File(context.externalCacheDir, "updates/app-release.apk")

                destinationFile.parentFile?.let {
                    if (!it.exists()) it.mkdirs()
                }

                body.byteStream().use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int
                        var totalRead: Long = 0
                        var lastProgress = -1

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (totalBytes > 0) {
                                val progress = ((totalRead * 100) / totalBytes).toInt()
                                if (progress != lastProgress) {
                                    emit(DownloadStatus.Downloading(progress))
                                    lastProgress = progress
                                }
                            }
                        }
                    }
                }

                // 校验文件完整性
                if (totalBytes > 0 && destinationFile.length() < totalBytes) {
                    emit(DownloadStatus.Error("文件下载不完整"))
                } else {
                    emit(DownloadStatus.Success(destinationFile.absolutePath))
                }
            }
        } catch (e: Exception) {
            emit(DownloadStatus.Error(e.message ?: "未知网络异常"))
        }
    }

    /**
     * 启动系统安装程序。
     */
    fun installApk(apkPath: String) {
        val file = File(apkPath)
        if (!file.exists()) return

        val intent = Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val uri: Uri =
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            setDataAndType(uri, "application/vnd.android.package-archive")
        }
        context.startActivity(intent)
    }

    /**
     * 版本未变化异常（304 响应）。
     */
    class NotModifiedException(source: String) : Exception("[$source] 版本未变化")
}
