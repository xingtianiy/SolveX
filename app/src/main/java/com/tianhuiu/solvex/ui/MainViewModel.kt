package com.tianhuiu.solvex.ui

import android.app.Application
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tianhuiu.solvex.BuildConfig
import com.tianhuiu.solvex.data.SettingsRepository
import com.tianhuiu.solvex.data.models.AppConfig
import com.tianhuiu.solvex.data.models.AssistantConfig
import com.tianhuiu.solvex.data.models.CaptureMode
import com.tianhuiu.solvex.data.models.DownloadStatus
import com.tianhuiu.solvex.data.models.EngineType
import com.tianhuiu.solvex.data.models.ModelProvider
import com.tianhuiu.solvex.data.models.MultiImageModeConfig
import com.tianhuiu.solvex.data.models.PermissionSettings
import com.tianhuiu.solvex.data.models.ProjectMode
import com.tianhuiu.solvex.data.models.ProviderKind
import com.tianhuiu.solvex.data.models.QuickModeConfig
import com.tianhuiu.solvex.data.models.StudyModeConfig
import com.tianhuiu.solvex.data.models.UpdateLevel
import com.tianhuiu.solvex.data.models.VersionInfo
import com.tianhuiu.solvex.network.UnifiedLLMClient
import com.tianhuiu.solvex.service.MainService
import com.tianhuiu.solvex.utils.NotificationHelper
import com.tianhuiu.solvex.utils.SystemUtils
import com.tianhuiu.solvex.utils.UpdateManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import rikka.shizuku.Shizuku
import java.util.UUID
import java.util.concurrent.TimeUnit

@Serializable
data class ExportData(
    val providers: List<ModelProvider> = emptyList(),
    val assistants: List<AssistantConfig> = emptyList(),
)

/**
 * 应用全局 ViewModel：负责管理应用配置、权限状态及核心服务逻辑。
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    private val container = (application as com.tianhuiu.solvex.SolveXApplication).container
    private val client = container.okHttpClient
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    private val llmClient = UnifiedLLMClient(client, json)
    private val updateManager = UpdateManager(application, client, json)

    private var pendingSaveJob: kotlinx.coroutines.Job? = null

    var providers by mutableStateOf(emptyList<ModelProvider>())
        private set

    var assistants by mutableStateOf(emptyList<AssistantConfig>())
        private set

    var permissions by mutableStateOf(PermissionSettings())
        private set

    var selectedAssistantId by mutableStateOf<String?>(null)
        private set
    var selectedEngine by mutableStateOf(EngineType.VISION_ENGINE)
        private set
    var selectedMode by mutableStateOf(ProjectMode.STUDY_MODE)
        private set

    var autoScrollContent by mutableStateOf(true)
        private set

    var studyConfig by mutableStateOf(StudyModeConfig())
        private set

    var quickConfig by mutableStateOf(QuickModeConfig())
        private set

    var multiImageConfig by mutableStateOf(MultiImageModeConfig())
        private set

    var defaultProviderId by mutableStateOf<String?>(null)
        private set

    var isFetchingModels by mutableStateOf(value = false)
        private set

    var isOverlayPermissionGranted by mutableStateOf(false)
        private set

    var isNotificationPermissionGranted by mutableStateOf(false)
        private set

    var isServiceRunning by mutableStateOf(false)
        private set

    var activeMode by mutableStateOf<ProjectMode?>(null)
        private set

    var showStopConfirmationDialog by mutableStateOf(false)
        private set

    var isShizukuRunning by mutableStateOf(false)
        private set

    var isShizukuPermissionGranted by mutableStateOf(false)
        private set

    var isAccessibilityEnabled by mutableStateOf(false)
        private set

    var deepLinkHistoryId by mutableStateOf<String?>(null)

    // 更新相关状态
    var updateInfo by mutableStateOf<VersionInfo?>(null)
        private set
    var downloadStatus by mutableStateOf<DownloadStatus>(DownloadStatus.Idle)
        private set
    var isCheckingUpdate by mutableStateOf(false)
        private set

    var launchCount by mutableStateOf(0)
        private set

    fun consumeDeepLink(): String? {
        val id = deepLinkHistoryId
        deepLinkHistoryId = null
        return id
    }

    val inAppNotifications =
        (application as com.tianhuiu.solvex.SolveXApplication).container.appNotificationManager.notifications

    private val _requestMediaProjection = MutableSharedFlow<Unit>()
    val requestMediaProjection = _requestMediaProjection.asSharedFlow()

    init {
        viewModelScope.launch {
            MainService.isRunning.collect { running ->
                isServiceRunning = running
                if (running) {
                    if (activeMode == null) {
                        repository.appConfigFlow.first().let { activeMode = it.selectedMode }
                    }
                } else {
                    activeMode = null
                }
                // 同步应用内通知状态，确保服务状态变化时首页通知实时更新
                syncNotificationState()
            }
        }

        viewModelScope.launch {
            repository.appConfigFlow.collect { config ->
                if (config.providers.isEmpty() && config.assistants.isEmpty()) {
                    resetToDefault()
                } else {
                    providers = config.providers
                    assistants = config.assistants
                    permissions = config.permissions
                    selectedAssistantId = config.selectedAssistantId
                    selectedEngine = config.selectedEngine
                    selectedMode = config.selectedMode
                    studyConfig = config.studyConfig
                    quickConfig = config.quickConfig
                    multiImageConfig = config.multiImageConfig
                    defaultProviderId = config.defaultProviderId
                    autoScrollContent = config.autoScrollContent
                    checkPermissions()
                }
            }
        }

        // 自适应更新检测
        viewModelScope.launch {
            val lastCheck = repository.lastUpdateCheckFlow.first()
            val consecutiveNoUpdate = repository.consecutiveNoUpdateFlow.first()
            val now = System.currentTimeMillis()

            // 自适应间隔：连续无更新则延长，发现过更新则缩短
            val intervalDays = when {
                consecutiveNoUpdate >= 3 -> 14L
                updateInfo != null -> 1L
                else -> 7L
            }

            if (now - lastCheck > TimeUnit.DAYS.toMillis(intervalDays)) {
                checkForUpdates(manual = false)
            }
        }

        // 启动计数
        viewModelScope.launch {
            launchCount = repository.launchCountFlow.first()
            repository.incrementLaunchCount()
            // launchCount 加载完成后同步通知，确保新手引导通知正确显示
            syncNotificationState()
        }
    }

    /**
     * 检测更新：竞速请求 + ETag 条件请求 + 本地缓存兜底。
     */
    fun checkForUpdates(manual: Boolean = false) {
        viewModelScope.launch {
            if (manual) isCheckingUpdate = true

            // critical 级别忽略频率限制
            if (!manual && updateInfo?.updateLevel == UpdateLevel.CRITICAL) {
                return@launch // 已展示 critical 弹窗，不重复检测
            }

            val savedEtag = repository.updateEtagFlow.first()

            updateManager.checkUpdate(etag = savedEtag)
                .onSuccess { (info, newEtag) ->
                    if (manual) isCheckingUpdate = false
                    repository.saveLastUpdateCheck(System.currentTimeMillis())

                    if (newEtag != null) {
                        repository.saveUpdateEtag(newEtag)
                    }

                    repository.saveCachedVersion(updateManager.encodeVersion(info))

                    if (info.versionCode > BuildConfig.VERSION_CODE) {
                        updateInfo = info
                        repository.saveConsecutiveNoUpdate(0)
                    } else {
                        repository.saveConsecutiveNoUpdate(
                            (repository.consecutiveNoUpdateFlow.first()) + 1
                        )
                        if (manual) {
                            NotificationHelper.showToast(
                                getApplication(),
                                "当前已是最新版本"
                            )
                        }
                    }
                }
                .onFailure { error ->
                    if (manual) isCheckingUpdate = false

                    // NotModifiedException 表示版本未变化（304）
                    if (error is com.tianhuiu.solvex.utils.UpdateManager.NotModifiedException) {
                        repository.saveLastUpdateCheck(System.currentTimeMillis())
                        repository.saveConsecutiveNoUpdate(
                            (repository.consecutiveNoUpdateFlow.first()) + 1
                        )
                        if (manual) {
                            NotificationHelper.showToast(
                                getApplication(),
                                "当前已是最新版本"
                            )
                        }
                        return@launch
                    }

                    // 网络失败时尝试使用缓存版本
                    if (manual) {
                        val cachedJson = repository.cachedVersionFlow.first()
                        if (cachedJson != null) {
                            val cachedInfo = updateManager.parseCachedVersion(cachedJson)
                            if (cachedInfo != null && cachedInfo.versionCode > BuildConfig.VERSION_CODE) {
                                updateInfo = cachedInfo
                                NotificationHelper.showToast(
                                    getApplication(),
                                    "网络不可用，显示上次缓存的更新信息"
                                )
                                return@launch
                            }
                        }

                        val message = error.message ?: "未知错误"
                        NotificationHelper.showFeedback(
                            getApplication(),
                            userMessage = "检查更新失败",
                            detailedLog = "Update check failed: $message"
                        )
                    }
                }
        }
    }

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

    fun dismissUpdateDialog() {
        if (!(updateInfo?.isDismissible == true)) return
        // recommended 级别：推迟 24 小时后再提醒
        if (updateInfo?.updateLevel == UpdateLevel.RECOMMENDED) {
            viewModelScope.launch {
                repository.saveLastUpdateCheck(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(6))
            }
        }
        updateInfo = null
        downloadStatus = DownloadStatus.Idle
    }

    private fun save() {
        pendingSaveJob?.cancel()
        pendingSaveJob = viewModelScope.launch {
            delay(300)
            repository.saveAppConfig(
                AppConfig(
                    providers = providers,
                    assistants = assistants,
                    permissions = permissions,
                    studyConfig = studyConfig,
                    quickConfig = quickConfig,
                    multiImageConfig = multiImageConfig,
                    defaultProviderId = defaultProviderId,
                    selectedAssistantId = selectedAssistantId,
                    selectedEngine = selectedEngine,
                    selectedMode = selectedMode,
                    autoScrollContent = autoScrollContent
                )
            )
        }
    }

    fun dismissInAppNotification(id: String) {
        (getApplication<Application>() as com.tianhuiu.solvex.SolveXApplication).container.appNotificationManager.dismiss(
            id
        )
    }

    fun addProvider(provider: ModelProvider) {
        providers += provider
        save()
    }

    fun updateProvider(provider: ModelProvider) {
        providers = providers.map { if (it.id == provider.id) provider else it }
        save()
    }

    fun deleteProvider(id: String) {
        providers = providers.filter { it.id != id }
        save()
    }

    fun addAssistant(assistant: AssistantConfig) {
        assistants = assistants + assistant
        save()
    }

    fun updateAssistant(assistant: AssistantConfig) {
        assistants = assistants.map { if (it.id == assistant.id) assistant else it }
        save()
    }

    fun setAssistant(id: String?) {
        selectedAssistantId = id
        save()
    }

    fun setEngine(engine: EngineType) {
        selectedEngine = engine
        save()
    }

    fun setMode(mode: ProjectMode) {
        selectedMode = mode
        save()
    }

    fun deleteAssistant(id: String) {
        assistants = assistants.filter { it.id != id }
        save()
    }

    fun updatePermissions(newPermissions: PermissionSettings) {
        permissions = newPermissions
        save()
        checkPermissions()
    }

    fun updateStudyConfig(config: StudyModeConfig) {
        studyConfig = config
        save()
    }

    fun updateQuickConfig(config: QuickModeConfig) {
        quickConfig = config
        save()
    }

    fun updateMultiImageConfig(config: MultiImageModeConfig) {
        multiImageConfig = config
        save()
    }

    fun updateDefaultProviderId(id: String?) {
        defaultProviderId = id
        save()
    }

    fun updateAutoScrollContent(enabled: Boolean) {
        autoScrollContent = enabled
        save()
    }

    fun exportConfig(
        selectedProviders: List<ModelProvider> = providers,
        selectedAssistants: List<AssistantConfig> = assistants,
        includeApiKeyMap: Map<String, Boolean> = emptyMap()
    ): String {
        val filteredProviders = selectedProviders.map { provider ->
            if (includeApiKeyMap[provider.id] == true) {
                provider
            } else {
                provider.copy(apiKey = "")
            }
        }
        return json.encodeToString(ExportData(filteredProviders, selectedAssistants))
    }

    fun decodeImportConfig(jsonStr: String): ExportData? {
        return try {
            json.decodeFromString<ExportData>(jsonStr)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun importConfig(data: ExportData) {
        try {
            // 合并提供商：按名称匹配，若存在则更新内容但保留原 ID
            providers = mergeLists(providers, data.providers, { it.name }) { current, imported ->
                imported.copy(id = current.id)
            }
            // 合并助手：按名称匹配，若存在则更新内容但保留原 ID
            assistants = mergeLists(assistants, data.assistants, { it.name }) { current, imported ->
                imported.copy(id = current.id)
            }
            save()
        } catch (e: Exception) {
            NotificationHelper.showFeedback(
                getApplication(),
                userMessage = "导入失败",
                detailedLog = "Config import failed",
                throwable = e
            )
        }
    }

    private fun <T> mergeLists(
        currentList: List<T>,
        importedList: List<T>,
        nameSelector: (T) -> String,
        merger: (T, T) -> T
    ): List<T> {
        val newList = currentList.toMutableList()
        importedList.forEach { imported ->
            val index = newList.indexOfFirst { nameSelector(it) == nameSelector(imported) }
            if (index != -1) {
                newList[index] = merger(newList[index], imported)
            } else {
                newList.add(imported)
            }
        }
        return newList
    }

    var connectivityTestStates by mutableStateOf<Map<String, ConnectivityTestState>>(emptyMap())
        private set

    /**
     * 测试提供商连通性。
     */
    suspend fun testConnectivity(provider: ModelProvider): ConnectivityTestState {
        connectivityTestStates =
            connectivityTestStates + (provider.id to ConnectivityTestState.Testing)
        return try {
            val models = llmClient.fetchModels(provider)
            val result = if (models.isNotEmpty())
                ConnectivityTestState.Success(models.size)
            else
                ConnectivityTestState.Failure("无可用模型")
            connectivityTestStates = connectivityTestStates + (provider.id to result)
            result
        } catch (e: Exception) {
            val result = ConnectivityTestState.Failure(e.message ?: "连接失败")
            connectivityTestStates = connectivityTestStates + (provider.id to result)
            result
        }
    }

    /**
     * 获取模型列表（绕过已保存状态）。
     */
    suspend fun fetchModelsForProvider(provider: ModelProvider): List<String> {
        return try {
            val models = llmClient.fetchModels(provider)
            if (models.isNotEmpty() && providers.any { it.id == provider.id }) {
                updateProvider(provider.copy(availableModels = models))
            }
            models
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchModelsDirect(providerId: String): List<String> {
        val provider = providers.find { it.id == providerId } ?: return emptyList()
        return try {
            val models = llmClient.fetchModels(provider)
            if (models.isNotEmpty()) {
                updateProvider(provider.copy(availableModels = models))
            }
            models
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun checkPermissions() {
        val context = getApplication<Application>()
        isOverlayPermissionGranted = Settings.canDrawOverlays(context)
        isNotificationPermissionGranted =
            NotificationManagerCompat.from(context).areNotificationsEnabled()

        // Shizuku 状态
        isShizukuRunning = Shizuku.pingBinder()
        isShizukuPermissionGranted = if (isShizukuRunning) {
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else false

        // 无障碍状态
        isAccessibilityEnabled = SystemUtils.isAccessibilityServiceEnabled(context)

        syncNotificationState()
    }

    /**
     * 同步应用内通知状态（不重复检查系统权限，仅同步通知管理器）。
     */
    private fun syncNotificationState() {
        val context = getApplication<Application>()
        val notificationManager =
            (context as com.tianhuiu.solvex.SolveXApplication).container.appNotificationManager
        notificationManager.syncAll(
            isOverlayGranted = isOverlayPermissionGranted,
            isNotificationGranted = isNotificationPermissionGranted,
            isAccessibilityEnabled = isAccessibilityEnabled,
            isShizukuGranted = isShizukuPermissionGranted,
            captureMode = permissions.captureMode,
            isServiceRunning = isServiceRunning,
            launchCount = launchCount,
        )
    }

    fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:${getApplication<Application>().packageName}".toUri()
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(intent)
    }

    fun requestNotificationPermission() {
        val context = getApplication<Application>()
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent("android.settings.APP_NOTIFICATION_SETTINGS").apply {
                putExtra("app_package", context.packageName)
                putExtra("app_uid", context.applicationInfo.uid)
            }
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun requestAccessibilityPermission() {
        val context = getApplication<Application>()
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * 请求 Shizuku 权限（弹出 Shizuku 授权对话框）。
     */
    fun requestShizukuPermission() {
        if (isShizukuRunning && !isShizukuPermissionGranted) {
            Shizuku.requestPermission(0)
        }
    }

    /**
     * 启动后台核心服务。根据截屏模式分发：
     * - SYSTEM: 触发 MediaProjection 权限弹窗
     * - SHIZUKU: 检查 Shizuku 权限后直接启动
     * - ACCESSIBILITY: 检查无障碍服务后直接启动
     */
    fun startService() {
        if (!isOverlayPermissionGranted) return

        activeMode = selectedMode

        when (permissions.captureMode) {
            CaptureMode.SYSTEM -> {
                // 触发 MediaProjection 权限弹窗，结果通过 startMainService 回调
                viewModelScope.launch { _requestMediaProjection.emit(Unit) }
            }

            CaptureMode.SHIZUKU -> {
                if (!isShizukuPermissionGranted) return
                startMainService(resultCode = 0, projectionData = null)
            }

            CaptureMode.ACCESSIBILITY -> {
                if (!isAccessibilityEnabled) return
                startMainService(resultCode = 0, projectionData = null)
            }
        }
    }

    fun startMainService(resultCode: Int, projectionData: Intent?) {
        val context = getApplication<Application>()
        val intent = Intent(context, MainService::class.java).apply {
            action = MainService.ACTION_START
            putExtra(MainService.EXTRA_RESULT_CODE, resultCode)
            projectionData?.let { putExtra(MainService.EXTRA_PROJECTION_DATA, it) }
            putExtra(MainService.EXTRA_CAPTURE_MODE, permissions.captureMode)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        checkPermissions()
    }

    fun stopService() {
        val context = getApplication<Application>()
        val intent = Intent(context, MainService::class.java).apply {
            action = MainService.ACTION_STOP
        }
        context.stopService(intent)
        activeMode = null
        showStopConfirmationDialog = false
        checkPermissions()
    }

    fun updateShowStopConfirmationDialog(show: Boolean) {
        showStopConfirmationDialog = show
    }

    fun resetToDefault() {
        providers = listOf(
            ModelProvider(
                UUID.randomUUID().toString(),
                ProviderKind.OPENAI_COMPATIBLE,
                "OpenAI",
                "https://api.openai.com/v1",
                "",
                emptyList()
            )
        )
        assistants = listOf(
            AssistantConfig(
                id = UUID.randomUUID().toString(),
                name = "题目解答助手",
                ocrPrompt = "你是一个精准的题目转录员。请严格按照以下 JSON 格式输出截图中题目的内容，不要包含任何额外文字：\n{\"type\": \"单选题/多选题/判断题/填空题/简答题\", \"question\": \"题目正文\", \"options\": [\"A. xxx\", \"B. xxx\"]}\n\n注意：\n- 如果是数学题，使用 LaTeX 语法（行内 $...$，独立行 $$...$$）\n- 选择题必须完整提取所有选项\n- 严禁改写原文内容\n- 过滤系统状态栏、虚拟按键、广告等非内容元素",
                textPrompt = "你是一个资深的解题专家。严格遵循以下 ### 标题输出格式，每个区块以 ### 开头独占一行：\n\n### 解题思路\n[分步骤说明解题过程，选择题需逐一分析选项，可使用 LaTeX 公式]\n\n### 关键知识点\n[本题涉及的核心公式、定理或概念]\n\n### 最终答案\n[最终结论，仅纯文本]",
                visionPrompt = "你是一个拥有视觉感知能力的解题专家。严格遵循以下 ### 标题输出格式，每个区块以 ### 开头独占一行：\n\n### 解题思路\n[分步骤说明解题过程，可使用 LaTeX 公式]\n\n### 关键知识点\n[核心考点总结]\n\n### 最终答案\n[最终结论，仅纯文本]"
            ),
            AssistantConfig(
                id = UUID.randomUUID().toString(),
                name = "聊天总结助手",
                ocrPrompt = "你是一个高效的对话提取员。请按时间顺序提取截图中的聊天记录，包括发言人、时间和消息内容。直接以原文格式输出，不需要 JSON 格式。",
                textPrompt = "你是一个专业的内容分析师。严格遵循以下 ### 标题输出格式，每个区块以 ### 开头独占一行：\n\n### 核心话题\n[讨论的主要内容]\n\n### 关键观点\n[各方主要观点和共识]\n\n### 总结\n[一句话核心总结，纯文本]",
                visionPrompt = "你是一个专业的内容分析师。严格遵循以下 ### 标题输出格式，每个区块以 ### 开头独占一行：\n\n### 核心话题\n[讨论的主要内容]\n\n### 关键观点\n[各方主要观点和共识]\n\n### 总结\n[一句话核心总结，纯文本]"
            )
        )
        permissions = PermissionSettings()
        save()
    }
}

sealed class ConnectivityTestState {
    data object Testing : ConnectivityTestState()
    data class Success(val modelCount: Int) : ConnectivityTestState()
    data class Failure(val message: String) : ConnectivityTestState()
}
