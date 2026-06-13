package com.tianhuiu.solvex.service

import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.coroutineScope
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.tianhuiu.solvex.R
import com.tianhuiu.solvex.SolveXApplication
import com.tianhuiu.solvex.capture.AccessibilityCaptureEngine
import com.tianhuiu.solvex.capture.ScreenCaptureEngine
import com.tianhuiu.solvex.capture.ShizukuCaptureEngine
import com.tianhuiu.solvex.capture.SystemCaptureEngine
import com.tianhuiu.solvex.data.HistoryRepository
import com.tianhuiu.solvex.data.SettingsRepository
import com.tianhuiu.solvex.data.models.AnalysisStatus
import com.tianhuiu.solvex.data.models.AutomationAction
import com.tianhuiu.solvex.data.models.CaptureMode
import com.tianhuiu.solvex.data.models.EngineType
import com.tianhuiu.solvex.data.models.HistoryItem
import com.tianhuiu.solvex.data.models.ProcessingStatus
import com.tianhuiu.solvex.data.models.currentModeConfig
import com.tianhuiu.solvex.floating.BallStatus
import com.tianhuiu.solvex.floating.CropManager
import com.tianhuiu.solvex.floating.DrawerManager
import com.tianhuiu.solvex.floating.FloatingBallManager
import com.tianhuiu.solvex.mode.ModeRegistry
import com.tianhuiu.solvex.network.SseStreamClient
import com.tianhuiu.solvex.utils.NotificationUtils
import com.tianhuiu.solvex.utils.SystemUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 后台核心服务。
 */
class MainService : LifecycleService(), ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()

        private val _serviceError = MutableSharedFlow<String>(replay = 0)
        val serviceError = _serviceError.asSharedFlow()

        const val CHANNEL_ID = "main_service_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.tianhuiu.solvex.ACTION_START"
        const val ACTION_STOP = "com.tianhuiu.solvex.ACTION_STOP"

        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_PROJECTION_DATA = "EXTRA_PROJECTION_DATA"
        const val EXTRA_CAPTURE_MODE = "EXTRA_CAPTURE_MODE"
    }

    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var floatingBallManager: FloatingBallManager? = null
    private var drawerManager: DrawerManager? = null
    private var cropManager: CropManager? = null
    private var currentHistoryId: String? = null
    private var processingJob: Job? = null
    private var captureEngine: ScreenCaptureEngine? = null
    private var stealthJob: Job? = null
    private var isStealthActive = false
    private lateinit var repository: SettingsRepository
    private lateinit var historyRepository: HistoryRepository

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry =
        savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        // 立即检查一次状态，确保 UI 就绪通知实时刷新
        (application as SolveXApplication).viewModel?.checkPermissions()
        savedStateRegistryController.performRestore(null)
        repository = SettingsRepository(this)

        val container = (application as SolveXApplication).container
        historyRepository = container.historyRepository

        // 清理意外中断的任务
        lifecycle.coroutineScope.launch {
            historyRepository.cleanupProcessingItems(
                query = "用户已关闭软件",
                result = "程序意外终止或手动清理后台导致任务取消"
            )
        }

        val pipeline = container.processingPipeline
        drawerManager = DrawerManager(this, historyRepository)
        cropManager = CropManager(this)

        floatingBallManager = FloatingBallManager(this).apply {
            onSingleClick = {
                if (processingJob?.isActive == true) {
                    currentHistoryId?.let { id ->
                        lifecycle.coroutineScope.launch {
                            val config = repository.appConfigFlow.first()
                            drawerManager?.show(
                                historyId = id,
                                side = config.permissions.drawerSettings.side,
                                widthPercent = config.permissions.drawerSettings.widthPercent,
                                showMetadata = false,
                                autoScrollEnabled = config.autoScrollContent
                            )
                        }
                    }
                } else {
                    processingJob = lifecycle.coroutineScope.launch {
                        try {
                            updateStatus(BallStatus.RUNNING)

                            // 截图前隐藏悬浮球，并等待 UI 更新
                            floatingBallManager?.tempHide()
                            delay(100) // 给 WindowManager 一点时间应用更改

                            val bitmap: android.graphics.Bitmap? = try {
                                captureEngine?.capture()
                            } finally {
                                // 无论截图成功与否，都恢复悬浮球
                                floatingBallManager?.restore()
                            }

                            if (bitmap != null) {
                                SystemUtils.vibrateSuccess(this@MainService)
                                val config = repository.appConfigFlow.first()

                                // 常规学习模式：弹出裁剪界面让用户选取重要区域
                                var image: android.graphics.Bitmap = bitmap
                                if (ModeRegistry.get(config.selectedModeId).shouldCrop) {
                                    cropManager?.let { manager ->
                                        val cropped = manager.crop(image)
                                        if (cropped == null) {
                                            // 用户取消了裁剪，恢复状态
                                            updateStatus(BallStatus.IDLE)
                                            return@launch
                                        }
                                        if (cropped !== image) {
                                            image.recycle()
                                        }
                                        image = cropped
                                    }
                                }

                                // 创建初始记录
                                val models = pipeline.resolveModels(config)
                                val initialResult =
                                    pipeline.createBaseResult(models, image, "正在获取题目...")
                                val historyId = initialResult.id
                                currentHistoryId = historyId
                                val historyItem = HistoryItem(
                                    id = historyId,
                                    query = "思考中...",
                                    result = "思考中...",
                                    imagePath = initialResult.screenshotPath,
                                    mode = config.selectedModeId,
                                    assistantName = initialResult.assistantName,
                                    providerName = initialResult.modelSummary,
                                    modelName = initialResult.modelSummary,
                                    engineName = config.selectedEngine.displayName,
                                    status = AnalysisStatus.PROCESSING
                                )
                                historyRepository.addHistoryItem(historyItem)

                                try {
                                    // 检查是否需要自动打开抽屉
                                    val autoOpen = config.currentModeConfig().autoOpenDrawer

                                    if (autoOpen) {
                                        lifecycle.coroutineScope.launch {
                                            drawerManager?.show(
                                                historyId = historyId,
                                                side = config.permissions.drawerSettings.side,
                                                widthPercent = config.permissions.drawerSettings.widthPercent,
                                                showMetadata = false
                                            )
                                        }
                                    }

                                    // 数据库更新节流逻辑
                                    var currentQueryText = ""
                                    var currentResultText = ""
                                    var pendingUpdateJob: Job? = null

                                    fun scheduleUpdate() {
                                        if (pendingUpdateJob?.isActive == true) return
                                        pendingUpdateJob =
                                            lifecycle.coroutineScope.launch(Dispatchers.IO) {
                                            delay(500)
                                            historyRepository.updateHistoryItem(historyId) { current ->
                                                current.copy(
                                                    query = currentQueryText.ifEmpty { current.query },
                                                    result = currentResultText.ifEmpty { current.result }
                                                )
                                            }
                                        }
                                    }

                                    try {
                                        val result = pipeline.process(
                                            config = config,
                                            bitmap = image,
                                            onSummaryGenerated = { title, summary ->
                                                lifecycle.coroutineScope.launch {
                                                    historyRepository.updateHistoryItem(historyId) { current ->
                                                        current.copy(
                                                            title = title,
                                                            summary = summary
                                                        )
                                                    }
                                                }
                                            },
                                            onQueryExtracted = { delta ->
                                                if (currentQueryText.isEmpty()) currentQueryText =
                                                    ""
                                                currentQueryText += delta
                                                drawerManager?.appendLiveQuery(delta)
                                                scheduleUpdate()
                                            },
                                            onDelta = { delta ->
                                                if (currentResultText.isEmpty()) currentResultText =
                                                    ""
                                                currentResultText += delta
                                                drawerManager?.appendLiveResult(delta)
                                                scheduleUpdate()
                                            }
                                        )

                                        // 等待待处理更新
                                        pendingUpdateJob?.cancelAndJoin()
                                        drawerManager?.clearLiveBuffer()

                                        if (result.status == ProcessingStatus.SUCCESS) {
                                            updateStatus(BallStatus.SUCCESS)
                                            SystemUtils.vibrateSuccess(this@MainService)

                                            // 执行自动化动作
                                            val action = result.automationAction ?: run {
                                                // 备选方案：从常规答案提取最终答案并复制
                                                val finalAnswer =
                                                    NotificationUtils.extractFinalAnswer(
                                                        result.answer ?: ""
                                                    )
                                                if (finalAnswer.isNotBlank()) {
                                                    AutomationAction("set_clipboard", finalAnswer)
                                                } else null
                                            }

                                            action?.let { act ->
                                                when (act.type) {
                                                    "show_bubble_letters" -> {
                                                        floatingBallManager?.showText(act.text)
                                                    }

                                                    "set_clipboard" -> {
                                                        SystemUtils.copyToClipboard(
                                                            this@MainService,
                                                            act.text
                                                        )
                                                        floatingBallManager?.showText("已复制")
                                                    }
                                                }
                                            }

                                            // 发送成功通知
                                            val currentHistory =
                                                historyRepository.historyItemsFlow.first()
                                                    .find { it.id == historyId }
                                            val allowNotification =
                                                config.currentModeConfig().allowNotification

                                            if (allowNotification) {
                                                val notifyTitle =
                                                    currentHistory?.title ?: "解析完成"
                                                val rawAnswer =
                                                    result.answer ?: result.automationThought
                                                    ?: "已获取最终答案"
                                                val notifyContent =
                                                    NotificationUtils.extractFinalAnswer(rawAnswer)
                                                NotificationUtils.sendResultNotification(
                                                    this@MainService,
                                                    notifyTitle,
                                                    notifyContent,
                                                    historyId
                                                )
                                            }

                                            historyRepository.updateHistoryItem(historyId) { current ->
                                                current.copy(
                                                    query = result.extractedText ?: current.query,
                                                    result = result.answer
                                                        ?: result.automationThought
                                                        ?: "已获取最终答案",
                                                    status = AnalysisStatus.SUCCESS
                                                )
                                            }
                                        } else {
                                            updateStatus(BallStatus.ERROR)
                                            SystemUtils.vibrateError(this@MainService)
                                            drawerManager?.hide()

                                            // 发送失败通知
                                            val allowNotification =
                                                config.currentModeConfig().allowNotification

                                            if (allowNotification) {
                                                NotificationUtils.sendResultNotification(
                                                    this@MainService,
                                                    "解析失败",
                                                    result.detail,
                                                    historyId
                                                )
                                            }

                                            historyRepository.updateHistoryItem(historyId) { current ->
                                                current.copy(
                                                    title = current.title ?: "解析失败",
                                                    result = result.detail,
                                                    status = AnalysisStatus.FAILURE
                                                )
                                            }
                                        }
                                    } finally {
                                        image.recycle()
                                    }
                                } catch (e: CancellationException) {
                                    drawerManager?.hide()
                                    cleanupScope.launch {
                                        historyRepository.updateHistoryItem(historyId) { current ->
                                            current.copy(
                                                query = "用户已取消",
                                                result = "用户已取消",
                                                status = AnalysisStatus.CANCELLED
                                            )
                                        }
                                    }
                                    throw e
                                }
                            } else {
                                // 截图失败：根据当前 captureMode 给出具体提示
                                val config = repository.appConfigFlow.first()
                                val captureHint = when (config.permissions.captureMode) {
                                    CaptureMode.SYSTEM ->
                                        "请确认已授予屏幕录制权限"

                                    CaptureMode.ACCESSIBILITY ->
                                        "请确认无障碍服务已开启"

                                    CaptureMode.SHIZUKU ->
                                        "请确认 Shizuku 已连接并授权"

                                    else -> "截图失败，请检查截屏权限设置"
                                }
                                android.util.Log.e("SolveX", "截图失败: $captureHint")
                                lifecycle.coroutineScope.launch {
                                    _serviceError.emit(captureHint)
                                }
                                updateStatus(BallStatus.ERROR)
                                SystemUtils.vibrateError(this@MainService)
                                drawerManager?.hide()

                                val allowNotification = config.currentModeConfig().allowNotification
                                if (allowNotification) {
                                    NotificationUtils.sendResultNotification(
                                        this@MainService,
                                        "截图失败",
                                        captureHint
                                    )
                                }
                            }
                        } catch (_: CancellationException) {
                            drawerManager?.hide()
                        } catch (e: Exception) {
                            android.util.Log.e("SolveX", "流程异常", e)
                            updateStatus(BallStatus.ERROR)
                            SystemUtils.vibrateError(this@MainService)
                            drawerManager?.hide()

                            currentHistoryId?.let { historyId ->
                                lifecycle.coroutineScope.launch {
                                    historyRepository.updateHistoryItem(historyId) { current ->
                                        current.copy(
                                            title = current.title ?: "解析失败",
                                            result = SseStreamClient.translateNetworkException(e),
                                            status = AnalysisStatus.FAILURE
                                        )
                                    }
                                }
                            }

                            try {
                                val config = repository.appConfigFlow.first()
                                val allowNotification = config.currentModeConfig().allowNotification
                                if (allowNotification) {
                                    NotificationUtils.sendResultNotification(
                                        this@MainService,
                                        "解析异常",
                                        SseStreamClient.translateNetworkException(e)
                                    )
                                }
                            } catch (_: Exception) { /* 通知发送失败不影响主流程 */
                            }
                        } finally {
                            currentHistoryId = null
                            processingJob = null
                        }
                    }
                }
            }
            onDoubleClick = {
                processingJob?.cancel()
                processingJob = null
                drawerManager?.hide()
                updateStatus(BallStatus.IDLE)
            }
            onLongClick = {
                SystemUtils.vibrate(this@MainService, 50)
                switchEngine()
            }
        }

        lifecycle.coroutineScope.launch {
            repository.appConfigFlow.collect { config ->
                floatingBallManager?.enableAutoHide = config.permissions.enableAutoHideBall
                floatingBallManager?.ballFullSizeDp = config.permissions.ballFullSizeDp
                applyPrivacyPolicy(config)
            }
        }
    }

    private fun applyPrivacyPolicy(config: com.tianhuiu.solvex.data.models.AppConfig) {
        val permissions = config.permissions

        // 如果开启了隐匿模式，且 Shizuku 就绪，启动/恢复监听
        if (permissions.enableStealthMode && 
            rikka.shizuku.Shizuku.pingBinder() && 
            rikka.shizuku.Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startStealthMonitor()
        } else {
            stopStealthMonitor()
            // 恢复为普通的防截屏设置
            updateWindowsSecure(permissions.enableScreenProtection)
            floatingBallManager?.updateStatus(BallStatus.IDLE)
        }
    }

    private fun startStealthMonitor() {
        if (stealthJob?.isActive == true) return
        
        // 检查 Shizuku 状态
        if (!rikka.shizuku.Shizuku.pingBinder() || 
            rikka.shizuku.Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            lifecycle.coroutineScope.launch {
                _serviceError.emit("隐匿模式需要 Shizuku 授权，请在设置中开启")
            }
            return
        }

        stealthJob = lifecycle.coroutineScope.launch {
            while (true) {
                try {
                    val svc = ShizukuUserServiceClient.acquire(this@MainService)
                    if (svc != null) {
                        val count = svc.getSecureWindowCount()
                        val shouldBeSecure = count > 0
                        if (isStealthActive != shouldBeSecure) {
                            isStealthActive = shouldBeSecure
                            val config = repository.appConfigFlow.first()
                            // 隐匿模式下：如果有隐私窗口，强制开启保护；否则遵循用户基本设置
                            val finalSecure = shouldBeSecure || config.permissions.enableScreenProtection
                            updateWindowsSecure(finalSecure)

                            // 视觉反馈：隐匿激活时变色或降低透明度
                            if (shouldBeSecure) {
                                floatingBallManager?.updateStatus(BallStatus.PROTECTED)
                            } else {
                                floatingBallManager?.updateStatus(BallStatus.IDLE)
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainService", "Stealth monitor error", e)
                }
                delay(1000)
            }
        }
    }

    private fun stopStealthMonitor() {
        stealthJob?.cancel()
        stealthJob = null
        isStealthActive = false
    }

    private fun updateWindowsSecure(enabled: Boolean) {
        floatingBallManager?.updateSecureFlag(enabled)
        drawerManager?.updateSecureFlag(enabled)
        cropManager?.updateSecureFlag(enabled)
    }

    private fun switchEngine() {
        lifecycle.coroutineScope.launch {
            val config = repository.appConfigFlow.first()
            val newEngine = if (config.selectedEngine == EngineType.VISION_ENGINE) {
                EngineType.TEXT_ENGINE
            } else {
                EngineType.VISION_ENGINE
            }
            repository.saveAppConfig(config.copy(selectedEngine = newEngine))
            SystemUtils.vibrate(this@MainService, 100)
        }
    }

    override fun onDestroy() {
        _isRunning.value = false
        // 停止时再次刷新，恢复“就绪”或“未就绪”状态
        (application as SolveXApplication).viewModel?.checkPermissions()
        stopStealthMonitor()
        captureEngine?.release()
        captureEngine = null
        floatingBallManager?.hide()
        // 取消正在处理的历史记录
        currentHistoryId?.let { id ->
            cleanupScope.launch {
                historyRepository.updateHistoryItem(id) { current ->
                    if (current.status == AnalysisStatus.PROCESSING) {
                        current.copy(
                            query = "用户已取消",
                            result = "用户已取消",
                            status = AnalysisStatus.CANCELLED
                        )
                    } else current
                }
            }
        }
        processingJob?.cancel()
        viewModelStore.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> startAsForeground(intent)
            ACTION_STOP -> stopSelf()
            NotificationUtils.ACTION_VIEW_HISTORY -> {
                val historyId = intent.getStringExtra(NotificationUtils.EXTRA_HISTORY_ID)
                if (historyId != null) {
                    lifecycle.coroutineScope.launch {
                        val config = repository.appConfigFlow.first()
                        drawerManager?.show(
                            historyId = historyId,
                            side = config.permissions.drawerSettings.side,
                            widthPercent = config.permissions.drawerSettings.widthPercent,
                            showMetadata = false
                        )
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    /**
     * 以前台服务形式启动。根据 EXTRA_CAPTURE_MODE 创建对应的截屏引擎，
     * 并选择对应的前台服务类型（非 MediaProjection 模式不使用 mediaProjection 类型）。
     */
    private fun startAsForeground(intent: Intent?) {
        createNotificationChannel()

        val captureMode = intent?.getStringExtra(EXTRA_CAPTURE_MODE) ?: CaptureMode.SYSTEM

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SolveX 运行中")
            .setContentText("随时准备解析屏幕内容")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fgsType = when (captureMode) {
                CaptureMode.SYSTEM -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                else -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            }
            startForeground(NOTIFICATION_ID, notification, fgsType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // 根据截屏模式创建引擎
        captureEngine?.release()
        captureEngine = when (captureMode) {
            CaptureMode.SHIZUKU -> ShizukuCaptureEngine(this)
            CaptureMode.ACCESSIBILITY -> AccessibilityCaptureEngine()
            else -> {
                val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0

                @Suppress("DEPRECATION")
                val data = intent?.getParcelableExtra<Intent>(EXTRA_PROJECTION_DATA)
                if (resultCode != 0 && data != null) {
                    SystemCaptureEngine(this, resultCode, data).also {
                        lifecycle.coroutineScope.launch { it.prepare() }
                    }
                } else null
            }
        }

        floatingBallManager?.show()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "后台核心服务",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持 SolveX 在后台运行以进行屏幕解析"
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
