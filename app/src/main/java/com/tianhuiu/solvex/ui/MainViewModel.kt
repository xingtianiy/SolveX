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
import com.tianhuiu.solvex.data.models.PermissionSettings
import com.tianhuiu.solvex.data.models.ProjectMode
import com.tianhuiu.solvex.data.models.ProviderKind
import com.tianhuiu.solvex.data.models.QuickModeConfig
import com.tianhuiu.solvex.data.models.StudyModeConfig
import com.tianhuiu.solvex.data.models.VersionInfo
import com.tianhuiu.solvex.network.UnifiedLLMClient
import com.tianhuiu.solvex.service.MainService
import com.tianhuiu.solvex.service.SolveXAccessibilityService
import com.tianhuiu.solvex.utils.NotificationUtils
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

/**
 * 应用全局 ViewModel：管理配置、权限状态及核心服务逻辑。
 */
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

    var studyConfig by mutableStateOf(StudyModeConfig())
        private set

    var quickConfig by mutableStateOf(QuickModeConfig())
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
                    defaultProviderId = config.defaultProviderId
                    checkPermissions()
                }
            }
        }

        // 自动检测更新（7天频率）
        viewModelScope.launch {
            val lastCheck = repository.lastUpdateCheckFlow.first()
            val now = System.currentTimeMillis()
            if (now - lastCheck > TimeUnit.DAYS.toMillis(7)) {
                checkForUpdates(manual = false)
            }
        }
    }

    /**
     * 检测更新。
     */
    fun checkForUpdates(manual: Boolean = false) {
        viewModelScope.launch {
            if (manual) isCheckingUpdate = true
            updateManager.checkUpdate()
                .onSuccess { info ->
                    if (manual) isCheckingUpdate = false
                    repository.saveLastUpdateCheck(System.currentTimeMillis())

                    // 比较 versionCode
                    if (info.versionCode > BuildConfig.VERSION_CODE) {
                        updateInfo = info
                    } else if (manual) {
                        NotificationUtils.showToast(
                            getApplication(),
                            "当前已是最新版本"
                        )
                    }
                }
                .onFailure { error ->
                    if (manual) {
                        isCheckingUpdate = false
                        val message = error.message ?: "未知错误"
                        NotificationUtils.showFeedback(
                            getApplication(),
                            userMessage = "检查更新失败",
                            detailedLog = "Update check failed: $message"
                        )
                    }
                }
        }
    }

    /**
     * 开始下载并安装 APK。
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

    fun dismissUpdateDialog() {
        if (updateInfo?.forceUpdate == true) return
        updateInfo = null
        downloadStatus = DownloadStatus.Idle
    }

    private fun save() {
        pendingSaveJob?.cancel()
        pendingSaveJob = viewModelScope.launch {
            delay(300)
            repository.saveAppConfig(
                AppConfig(
                    providers,
                    assistants,
                    permissions,
                    studyConfig,
                    quickConfig,
                    defaultProviderId,
                    selectedAssistantId,
                    selectedEngine,
                    selectedMode
                )
            )
        }
    }

    /**
     * 移除应用内通知。
     */
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

    fun updateDefaultProviderId(id: String?) {
        defaultProviderId = id
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
            NotificationUtils.showFeedback(
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

    /** 连通性测试状态 */
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
        isAccessibilityEnabled = isAccessibilityServiceEnabled(context)

        val notificationManager =
            (context as com.tianhuiu.solvex.SolveXApplication).container.appNotificationManager
        // 同步所有通知状态（含无障碍状态）
        notificationManager.syncAll(
            isOverlayGranted = isOverlayPermissionGranted,
            isNotificationGranted = isNotificationPermissionGranted,
            isAccessibilityEnabled = isAccessibilityEnabled,
            isShizukuGranted = isShizukuPermissionGranted,
            captureMode = permissions.captureMode,
        )
    }

    private fun isAccessibilityServiceEnabled(context: android.content.Context): Boolean {
        val serviceName = "${context.packageName}/${SolveXAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any { it == serviceName }
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
                ocrPrompt = "你是一个精准的题目转录员。请直接原文输出图片中的题目文本和选项，严禁改写。如果是数学题，请使用 LaTeX 语法确保公式和符号渲染准确。",
                textPrompt = "你是一个资深的解题专家。请对提取出的题目进行深度解析，并严格按照以下结构输出：\n\n### 解题思路\n[分步骤详细说明解题过程。对于选择题，请逐一分析选项。]\n\n### 关键知识点\n[总结本题涉及的核心公式、定理或概念。]\n\n### 最终答案\n[重要：此处必须提供最终结论。请直接输出纯文本答案，严禁使用 LaTeX、Markdown 加粗或任何格式化标记，只允许纯文本输出。]",
                visionPrompt = "你是一个拥有视觉感知能力的解题专家。请结合图片细节进行深度解析，并严格按照以下结构输出：\n\n### 解题思路\n[分步骤详细说明解题过程。]\n\n### 关键知识点\n[总结核心考点。]\n\n### 最终答案\n[重要：此处必须提供最终结论。请直接输出纯文本答案，严禁使用 LaTeX、Markdown 加粗或任何格式化标记。]"
            ),
            AssistantConfig(
                id = UUID.randomUUID().toString(),
                name = "聊天总结助手",
                ocrPrompt = "你是一个高效的对话提取员。请按时间顺序提取截图中的聊天记录，包括发言人、时间（如果有）和消息内容。",
                textPrompt = "你是一个专业的内容分析师。请对提供的聊天记录进行精简总结，重点提取核心话题、主要观点、达成的共识以及待办事项。请使用简洁的列表形式输出。\n\n### 最终答案\n[请在此处提供一句话核心总结，使用纯文本，严禁格式化。]",
                visionPrompt = "你是一个专业的内容分析师。请观察截图中的聊天界面，对对话内容进行精简总结，提取核心话题和关键结论。请使用简洁的列表形式输出。\n\n### 最终答案\n[请在此处提供一句话核心总结，使用纯文本，严禁格式化。]"
            )
        )
        permissions = PermissionSettings()
        save()
    }
}

/** 连通性测试状态 */
sealed class ConnectivityTestState {
    data object Testing : ConnectivityTestState()
    data class Success(val modelCount: Int) : ConnectivityTestState()
    data class Failure(val message: String) : ConnectivityTestState()
}
