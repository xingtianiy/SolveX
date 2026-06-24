package com.tianhuiu.solvex.ui

import android.app.Application
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tianhuiu.solvex.SolveXApplication
import com.tianhuiu.solvex.data.SettingsRepository
import com.tianhuiu.solvex.data.models.AppConfig
import com.tianhuiu.solvex.data.models.AssistantConfig
import com.tianhuiu.solvex.data.models.CaptureMode
import com.tianhuiu.solvex.data.models.EngineType
import com.tianhuiu.solvex.data.models.ModelProvider
import com.tianhuiu.solvex.data.models.PermissionSettings
import com.tianhuiu.solvex.data.models.PermissionSetupStep
import com.tianhuiu.solvex.data.models.ProviderKind
import com.tianhuiu.solvex.data.models.currentModeConfig
import com.tianhuiu.solvex.mode.ModeConfig
import com.tianhuiu.solvex.mode.ModeRegistry
import com.tianhuiu.solvex.network.UnifiedLLMClient
import com.tianhuiu.solvex.service.AdbCommandHelper
import com.tianhuiu.solvex.service.MainService
import com.tianhuiu.solvex.service.SolveXAccessibilityService
import com.tianhuiu.solvex.utils.SystemUtils
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

/**
 * 应用全局数据导出结构。
 */
@Serializable
data class ExportData(
    val providers: List<ModelProvider> = emptyList(),
    val assistants: List<AssistantConfig> = emptyList(),
)

/**
 * 全局弹窗数据模型。
 */
data class GlobalDialogData(
    val title: String,
    val message: String,
    val confirmText: String = "确定",
    val dismissText: String? = null,
    val onConfirm: () -> Unit = {},
    val onDismiss: (() -> Unit)? = null,
    val isDestructive: Boolean = false,
    val icon: ImageVector? = null
)

/**
 * 应用全局 ViewModel：负责管理应用配置、权限状态及核心服务逻辑。
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)

    init {
        (application as SolveXApplication).viewModel = this
    }
    override fun onCleared() {
        super.onCleared()
        (getApplication<Application>() as SolveXApplication).viewModel = null
    }

    private val container = (application as SolveXApplication).container
    private val client = container.okHttpClient
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    private val llmClient = UnifiedLLMClient(client, json)

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
    var selectedModeId by mutableStateOf(ModeRegistry.defaultId())
        private set

    var autoScrollContent by mutableStateOf(true)
        private set

    var currentModeConfig by mutableStateOf(ModeConfig())
        private set

    var allModeConfigs by mutableStateOf(emptyMap<String, ModeConfig>())
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

    var activeModeId by mutableStateOf<String?>(null)
        private set

    var showStopConfirmationDialog by mutableStateOf(false)
        private set

    var isShizukuRunning by mutableStateOf(false)
        private set

    var isShizukuPermissionGranted by mutableStateOf(false)
        private set

    var isShizukuInstalled by mutableStateOf(false)
        private set

    var isAccessibilityEnabled by mutableStateOf(false)
        private set

    var showPermissionSetupGuide by mutableStateOf(false)
        private set

    var currentSetupStep by mutableStateOf(PermissionSetupStep.OVERLAY)
        private set

    var deepLinkHistoryId by mutableStateOf<String?>(null)

    var launchCount by mutableStateOf(0)
        private set

    /** 是否满足当前模式下的所有必需权限 */
    val isAllPermissionsReady: Boolean
        get() {
            val mode = permissions.captureMode
            return isOverlayPermissionGranted && when {
                permissions.captureMode == CaptureMode.TEXT_ONLY || mode == CaptureMode.ACCESSIBILITY -> isAccessibilityEnabled
                mode == CaptureMode.SHIZUKU -> isShizukuPermissionGranted && isShizukuRunning
                else -> true
            }
        }

    /** 全局通用弹窗状态 */
    var globalDialogState by mutableStateOf<GlobalDialogData?>(null)
        private set

    fun showGlobalDialog(data: GlobalDialogData) {
        globalDialogState = data
    }

    fun showFeedbackDialog(title: String, message: String, icon: ImageVector? = null) {
        showGlobalDialog(
            GlobalDialogData(
                title = title,
                message = message,
                confirmText = "确定",
                icon = icon
            )
        )
    }

    fun dismissGlobalDialog() {
        globalDialogState = null
    }

    fun consumeDeepLink(): String? {
        val id = deepLinkHistoryId
        deepLinkHistoryId = null
        return id
    }

    val inAppNotifications =
        (application as SolveXApplication).container.appNotificationManager.notifications

    private val _requestMediaProjection = MutableSharedFlow<Unit>()
    val requestMediaProjection = _requestMediaProjection.asSharedFlow()

    init {
        viewModelScope.launch {
            MainService.isRunning.collect { running ->
                isServiceRunning = running
                if (running) {
                    if (activeModeId == null) {
                        repository.appConfigFlow.first().let { activeModeId = it.selectedModeId }
                    }
                } else {
                    activeModeId = null
                }
            }
        }

        viewModelScope.launch {
            MainService.serviceError.collect { error ->
                showGlobalDialog(
                    GlobalDialogData(
                        title = "服务异常",
                        message = error,
                        confirmText = "知道了",
                        icon = Icons.Default.Warning
                    )
                )
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
                    selectedEngine = if (config.permissions.captureMode == CaptureMode.TEXT_ONLY) {
                        EngineType.TEXT_ENGINE
                    } else {
                        config.selectedEngine
                    }
                    selectedModeId = config.selectedModeId
                    currentModeConfig = config.currentModeConfig()
                    allModeConfigs = config.modeConfigs
                    defaultProviderId = config.defaultProviderId
                    autoScrollContent = config.autoScrollContent
                    checkPermissions()
                }
            }
        }

        // 启动计数
        viewModelScope.launch {
            launchCount = repository.launchCountFlow.first()
            repository.incrementLaunchCount()
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        isShizukuRunning = true
        checkPermissions()
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        isShizukuRunning = false
        isShizukuPermissionGranted = false
    }
    private val requestPermissionResultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        val granted = grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED
        isShizukuPermissionGranted = granted
        if (granted) {
            // Shizuku 授权成功后自动提权：授予 WRITE_SECURE_SETTINGS 并启用无障碍服务
            viewModelScope.launch {
                val ctx = getApplication<Application>()
                AdbCommandHelper.grantWriteSecureSettings(ctx)
                AdbCommandHelper.enableAccessibilityService(ctx)
                // 延迟后刷新权限状态，确保 settings 命令生效
                delay(1000)
                checkPermissions()
            }
        }
    }

    fun registerShizukuListeners() {
        // 先移除再添加，防止 Activity 重建导致的重复注册
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
        isShizukuRunning = Shizuku.pingBinder()
        if (isShizukuRunning) {
            isShizukuPermissionGranted = Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    fun unregisterShizukuListeners() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
    }


    private fun save() {
        pendingSaveJob?.cancel()
        pendingSaveJob = viewModelScope.launch {
            delay(300)
            val finalModeConfigs = allModeConfigs.toMutableMap().apply {
                put(selectedModeId, currentModeConfig)
            }
            repository.saveAppConfig(
                AppConfig(
                    providers = providers,
                    assistants = assistants,
                    permissions = permissions,
                    defaultProviderId = defaultProviderId,
                    selectedAssistantId = selectedAssistantId,
                    selectedEngine = selectedEngine,
                    selectedModeId = selectedModeId,
                    modeConfigs = finalModeConfigs,
                    autoScrollContent = autoScrollContent
                )
            )
        }
    }

    /**
     * 移除应用内通知。
     */
    fun dismissInAppNotification(id: String) {
        (getApplication<Application>() as SolveXApplication).container.appNotificationManager.dismiss(
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

    fun updateProviders(newList: List<ModelProvider>) {
        providers = newList
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

    fun updateAssistants(newList: List<AssistantConfig>) {
        assistants = newList
        save()
    }

    fun setAssistant(id: String?) {
        selectedAssistantId = id
        save()
    }

    fun setEngine(engine: EngineType) {
        // 屏幕取字模式下锁定为文本引擎
        if (permissions.captureMode == CaptureMode.TEXT_ONLY && engine != EngineType.TEXT_ENGINE) return
        selectedEngine = engine
        save()
    }

    fun setMode(modeId: String) {
        selectedModeId = modeId
        currentModeConfig = allModeConfigs[modeId] ?: ModeRegistry.get(modeId).defaultConfig()
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

    fun updateModeConfig(modeId: String, config: ModeConfig) {
        allModeConfigs = allModeConfigs.toMutableMap().apply {
            put(modeId, config)
        }
        if (modeId == selectedModeId) {
            currentModeConfig = config
        }
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

    fun updateBallSize(fullSizeDp: Float) {
        permissions = permissions.copy(ballFullSizeDp = fullSizeDp)
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
            SystemUtils.showFeedback(
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
        isShizukuInstalled = isShizukuPackageInstalled()
        isShizukuRunning = if (isShizukuInstalled) Shizuku.pingBinder() else false
        isShizukuPermissionGranted = if (isShizukuRunning) {
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else false

        // 无障碍状态
        isAccessibilityEnabled = SystemUtils.isAccessibilityServiceEnabled(context, SolveXAccessibilityService::class.java)

        val notificationManager =
            (context as SolveXApplication).container.appNotificationManager

        // 判断当前截屏模式下是否已就绪（必需权限全部满足）
        val mode = permissions.captureMode
        val isReady = isOverlayPermissionGranted && when {
            permissions.captureMode == CaptureMode.TEXT_ONLY || mode == CaptureMode.ACCESSIBILITY -> isAccessibilityEnabled
            mode == CaptureMode.SHIZUKU -> isShizukuPermissionGranted && isShizukuRunning
            else -> true
        }

        notificationManager.syncAll(
            isServiceRunning = isServiceRunning,
            isReady = isReady && !isServiceRunning,
            launchCount = launchCount,
        )

        // 始终检查权限引导需求（根据截屏模式决定哪些权限是必需的）
        checkAndStartPermissionSetup()
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
        val intent =
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
                .apply {
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
     * 检查 Shizuku 应用是否已安装。
     */
    private fun isShizukuPackageInstalled(): Boolean {
        return try {
            val context = getApplication<Application>()
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (_: Exception) {
            try {
                val context = getApplication<Application>()
                context.packageManager.getPackageInfo("dev.rikka.shizuku", 0)
                true
            } catch (_: Exception) {
                false
            }
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

        when (permissions.captureMode) {
            CaptureMode.SYSTEM -> {
                // 触发 MediaProjection 权限弹窗，结果通过 startMainService 回调
                viewModelScope.launch { _requestMediaProjection.emit(Unit) }
            }

            CaptureMode.SHIZUKU -> {
                if (!isShizukuPermissionGranted) return
                startMainService(resultCode = 0, projectionData = null)
            }

            CaptureMode.ACCESSIBILITY, CaptureMode.TEXT_ONLY -> {
                if (!isAccessibilityEnabled) return
                startMainService(resultCode = 0, projectionData = null)
            }
        }
    }

    fun startMainService(resultCode: Int, projectionData: Intent?) {
        activeModeId = selectedModeId
        val context = getApplication<Application>()
        val intent = Intent(context, MainService::class.java).apply {
            action = MainService.ACTION_START
            putExtra(MainService.EXTRA_RESULT_CODE, resultCode)
            projectionData?.let { putExtra(MainService.EXTRA_PROJECTION_DATA, it) }
            putExtra(MainService.EXTRA_CAPTURE_MODE, permissions.captureMode)
        }
        context.startForegroundService(intent)
        checkPermissions()
    }

    fun stopService() {
        val context = getApplication<Application>()
        val intent = Intent(context, MainService::class.java).apply {
            action = MainService.ACTION_STOP
        }
        context.stopService(intent)
        activeModeId = null
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
                textPrompt = "你是一个资深的解题专家。请对提取出的题目进行深度解析。\n\n输出要求：\n- 必须包含且仅包含以下三个模块：### 题目分析、### 解题步骤、### 最终答案\n- **公式优先**：强烈建议并优先使用专业 LaTeX 公式。解题过程用文字描述逻辑，将数学表达融入高质量公式中。",
                visionPrompt = "你是一个拥有视觉感知能力的解题专家。请结合图片细节进行深度解析。\n\n输出要求：\n- 必须包含且仅包含以下三个模块：### 题目分析、### 解题步骤、### 最终答案\n- **公式优先**：强烈建议并优先使用专业 LaTeX 公式。解题过程用文字描述逻辑，将数学表达融入高质量公式中。"
            ),
            AssistantConfig(
                id = UUID.randomUUID().toString(),
                name = "聊天总结助手",
                ocrPrompt = "你是一个高效的对话提取员。请按时间顺序提取截图中的聊天记录，包括发言人、时间（如果有）和消息内容，但是需要排除屏幕无关信息。请直接输出文本，不要使用任何 JSON 格式。",
                textPrompt = "你是一个专业的内容分析师。请对提供的聊天记录进行精简总结，重点提取核心话题、主要观点、达成的共识以及待办事项。\n\n输出规范：\n- 使用 Markdown 三级标题（###）划分模块，例如：### 会话背景、### 核心讨论、### 结论摘要\n- 严禁按照题目解析的格式输出，请根据聊天内容的实际情况灵活调整模块标题，确保总结的高效性\n- 禁止输出 JSON、XML、YAML、表格或代码块包裹正文",
                visionPrompt = "你是一个专业的内容分析师。请观察截图中的聊天界面，对对话内容进行精简总结，提取核心话题和关键结论。\n\n输出规范：\n- 使用 Markdown 三级标题（###）划分模块，例如：### 界面概览、### 对话要点、### 行动指南\n- 严禁按照题目解析的格式输出，请根据聊天内容的实际情况灵活调整模块标题\n- 禁止输出 JSON、XML、YAML、表格或代码块包裹正文",
                useStructuredExtraction = false,
            )
        )
        permissions = PermissionSettings()
        save()
    }

    fun getRelevantSteps(): List<PermissionSetupStep> {
        val mode = permissions.captureMode
        val relevant = mutableListOf<PermissionSetupStep>()

        relevant.add(PermissionSetupStep.OVERLAY)
        relevant.add(PermissionSetupStep.NOTIFICATION)
        if (permissions.captureMode == CaptureMode.TEXT_ONLY || mode == CaptureMode.ACCESSIBILITY)
            relevant.add(PermissionSetupStep.ACCESSIBILITY)
        relevant.add(PermissionSetupStep.BATTERY)
        if (mode == CaptureMode.SHIZUKU) relevant.add(PermissionSetupStep.SHIZUKU)

        return relevant
    }

    /**
     * 检查权限并决定是否显示引导卡片。
     */
    fun checkAndStartPermissionSetup() {
        val context = getApplication<Application>()
        val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
        val mode = permissions.captureMode

        // 检查当前截屏模式下是否所有必需权限均已满足
        val requiredGranted = isOverlayPermissionGranted && when {
            permissions.captureMode == CaptureMode.TEXT_ONLY || mode == CaptureMode.ACCESSIBILITY -> isAccessibilityEnabled
            mode == CaptureMode.SHIZUKU -> isShizukuPermissionGranted && isShizukuRunning
            else -> true
        }
        val batteryOk = pm.isIgnoringBatteryOptimizations(context.packageName)

        if (requiredGranted && batteryOk && isNotificationPermissionGranted) {
            // 所有权限就绪，隐藏引导
            showPermissionSetupGuide = false
            if (!permissions.isFirstLaunchSetupComplete) {
                updatePermissions(permissions.copy(isFirstLaunchSetupComplete = true))
            }
        } else {
            // 存在缺失权限，显示引导并定位到第一个缺失项
            showPermissionSetupGuide = true
            advanceSetupToMissingStep()
        }
    }

    /**
     * 按优先级定位第一个缺失权限步骤。
     */
    fun advanceSetupToMissingStep() {
        val context = getApplication<Application>()
        val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
        val mode = permissions.captureMode

        currentSetupStep = when {
            // 必须：悬浮窗
            !isOverlayPermissionGranted -> PermissionSetupStep.OVERLAY
            // 可选：通知
            !isNotificationPermissionGranted -> PermissionSetupStep.NOTIFICATION
            // 屏幕取字模式或 ACCESSIBILITY 模式需要无障碍服务
            (permissions.captureMode == CaptureMode.TEXT_ONLY || mode == CaptureMode.ACCESSIBILITY) && !isAccessibilityEnabled -> PermissionSetupStep.ACCESSIBILITY
            // 必须：电池优化
            !pm.isIgnoringBatteryOptimizations(context.packageName) -> PermissionSetupStep.BATTERY
            // 仅 SHIZUKU 模式
            mode == CaptureMode.SHIZUKU && (!isShizukuPermissionGranted || !isShizukuRunning) -> PermissionSetupStep.SHIZUKU
            else -> PermissionSetupStep.DONE
        }

        if (currentSetupStep == PermissionSetupStep.DONE) {
            finishPermissionSetup()
        }
    }

    /**
     * 处理当前引导步骤的操作（跳转对应设置页）。
     */
    fun handleSetupStepAction(step: PermissionSetupStep) {
        when (step) {
            PermissionSetupStep.OVERLAY -> requestOverlayPermission()
            PermissionSetupStep.NOTIFICATION -> requestNotificationPermission()
            PermissionSetupStep.ACCESSIBILITY -> requestAccessibilityPermission()
            PermissionSetupStep.BATTERY -> requestBatteryOptimizationPermission()
            PermissionSetupStep.SHIZUKU -> {
                when {
                    !isShizukuInstalled -> {
                        // 跳转 Shizuku 下载页
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            "https://github.com/RikkaApps/Shizuku/releases".toUri()
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        getApplication<Application>().startActivity(intent)
                    }
                    !isShizukuRunning -> {
                        // 引导用户启动 Shizuku
                        showGlobalDialog(
                            GlobalDialogData(
                                title = "启动 Shizuku",
                                message = "请在 Shizuku 应用中启动服务，然后返回 SolveX 继续授权。",
                                confirmText = "打开 Shizuku",
                                onConfirm = {
                                    val ctx = getApplication<Application>()
                                    val launchIntent = ctx.packageManager
                                        .getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                        ?: ctx.packageManager
                                            .getLaunchIntentForPackage("dev.rikka.shizuku")
                                    launchIntent?.apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        ctx.startActivity(this)
                                    }
                                }
                            )
                        )
                    }
                    else -> requestShizukuPermission()
                }
            }
            PermissionSetupStep.DONE -> finishPermissionSetup()
        }
    }

    /**
     * 跳转系统电池优化设置。
     */
    fun requestBatteryOptimizationPermission() {
        val context = getApplication<Application>()
        val intent =
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:${context.packageName}".toUri()
            }
                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    }

    /**
     * 权限全部就绪时自动完成引导。
     */
    fun finishPermissionSetup() {
        showPermissionSetupGuide = false
        if (!permissions.isFirstLaunchSetupComplete) {
            updatePermissions(permissions.copy(isFirstLaunchSetupComplete = true))
        }
    }

    /**
     * 手动关闭引导（下次 ON_RESUME 若权限仍缺失会重新显示）。
     */
    fun skipPermissionSetup() {
        showPermissionSetupGuide = false
    }
}

/** 连通性测试状态 */
sealed class ConnectivityTestState {
    data object Testing : ConnectivityTestState()
    data class Success(val modelCount: Int) : ConnectivityTestState()
    data class Failure(val message: String) : ConnectivityTestState()
}
