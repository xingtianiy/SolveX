package com.tianhuiu.solvex.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tianhuiu.solvex.BuildConfig
import com.tianhuiu.solvex.data.SettingsRepository
import com.tianhuiu.solvex.data.models.DownloadStatus
import com.tianhuiu.solvex.data.models.UpdateLevel
import com.tianhuiu.solvex.data.models.VersionInfo
import com.tianhuiu.solvex.network.UpdateManager
import com.tianhuiu.solvex.utils.SystemUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 版本更新 ViewModel：检查更新、下载和安装。
 */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)
    private val updateManager = UpdateManager(
        application,
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build(),
        Json { ignoreUnknownKeys = true }
    )

    var updateInfo by mutableStateOf<VersionInfo?>(null)
        private set

    var downloadStatus by mutableStateOf<DownloadStatus>(DownloadStatus.Idle)
        private set

    var isCheckingUpdate by mutableStateOf(false)
        private set

    var showDialogManually by mutableStateOf(false)
        private set

    /**
     * 启动时恢复缓存更新信息，按策略检测更新。
     */
    fun initialize() {
        viewModelScope.launch {
            repository.cachedVersionFlow.first()?.let { cachedJson ->
                updateManager.parseCachedVersion(cachedJson)?.let { cachedInfo ->
                    if (cachedInfo.versionCode > BuildConfig.VERSION_CODE) {
                        updateInfo = cachedInfo
                    }
                }
            }

            val lastCheck = repository.lastUpdateCheckFlow.first()
            val now = System.currentTimeMillis()
            val isCriticalPending = updateInfo?.updateLevel == UpdateLevel.CRITICAL

            val intervalMillis = when {
                isCriticalPending -> 0L
                else -> 0L 
            }

            if (now - lastCheck >= intervalMillis) {
                checkForUpdates(manual = false)
            }
        }
    }

    /**
     * 检查更新：ETag 条件请求 + 缓存兜底。
     */
    fun checkForUpdates(manual: Boolean = false) {
        viewModelScope.launch {
            if (manual) {
                isCheckingUpdate = true
                showDialogManually = true
            }

            val savedEtag = repository.updateEtagFlow.first()

            updateManager.checkUpdate(etag = savedEtag)
                .onSuccess { (info, newEtag) ->
                    if (manual) isCheckingUpdate = false
                    repository.saveLastUpdateCheck(System.currentTimeMillis())
                    if (newEtag != null) repository.saveUpdateEtag(newEtag)
                    repository.saveCachedVersion(updateManager.encodeVersion(info))

                    if (info.versionCode > BuildConfig.VERSION_CODE) {
                        updateInfo = info
                        repository.saveConsecutiveNoUpdate(0)
                    } else {
                        repository.saveConsecutiveNoUpdate(
                            (repository.consecutiveNoUpdateFlow.first()) + 1
                        )
                        if (manual) SystemUtils.showToast(getApplication(), "当前已是最新版本")
                    }
                }
                .onFailure { error ->
                    if (manual) isCheckingUpdate = false

                    if (error is UpdateManager.NotModifiedException) {
                        repository.saveLastUpdateCheck(System.currentTimeMillis())
                        repository.saveConsecutiveNoUpdate(
                            (repository.consecutiveNoUpdateFlow.first()) + 1
                        )
                        if (manual) SystemUtils.showToast(getApplication(), "当前已是最新版本")
                        return@launch
                    }

                    if (manual) {
                        val cachedJson = repository.cachedVersionFlow.first()
                        if (cachedJson != null) {
                            val cachedInfo = updateManager.parseCachedVersion(cachedJson)
                            if (cachedInfo != null && cachedInfo.versionCode > BuildConfig.VERSION_CODE) {
                                updateInfo = cachedInfo
                                SystemUtils.showToast(getApplication(), "网络不可用，显示上次缓存的更新信息")
                                return@launch
                            }
                        }
                        val message = error.message ?: "未知错误"
                        SystemUtils.showFeedback(
                            getApplication(),
                            userMessage = "检查更新失败",
                            detailedLog = "Update check failed: $message"
                        )
                    }
                }
        }
    }

    /**
     * 下载并安装 APK。
     */
    fun startUpdate() {
        val info = updateInfo ?: return
        viewModelScope.launch {
            updateManager.downloadApk(info.githubUrl, info.giteeUrl).collect { status ->
                downloadStatus = status
                if (status is DownloadStatus.Success) {
                    updateManager.installApk(status.apkPath)
                }
            }
        }
    }

    /**
     * 关闭更新弹窗（仅非强制更新可关闭）。
     */
    fun dismissUpdateDialog() {
        if (updateInfo?.isDismissible != true) return
        showDialogManually = false
        if (updateInfo?.updateLevel == UpdateLevel.RECOMMENDED) {
            viewModelScope.launch {
                repository.saveLastUpdateCheck(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(6))
            }
        }
        updateInfo = null
        downloadStatus = DownloadStatus.Idle
    }
}
