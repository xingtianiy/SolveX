package com.tianhuiu.solvex.service

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
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
import com.tianhuiu.solvex.data.models.AppConfig
import com.tianhuiu.solvex.data.models.CaptureMode
import com.tianhuiu.solvex.data.models.EngineType
import com.tianhuiu.solvex.data.models.HistoryItem
import com.tianhuiu.solvex.data.models.ProcessingResult
import com.tianhuiu.solvex.data.models.ProcessingStatus
import com.tianhuiu.solvex.data.models.currentModeConfig
import com.tianhuiu.solvex.floating.BallStatus
import com.tianhuiu.solvex.floating.CropManager
import com.tianhuiu.solvex.floating.DrawerManager
import com.tianhuiu.solvex.floating.FloatingBallManager
import com.tianhuiu.solvex.floating.TextRegionManager
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
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * 核心业务流
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
    private var textRegionManager: TextRegionManager? = null
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
        textRegionManager = TextRegionManager(this)

        floatingBallManager = FloatingBallManager(this).apply {
            onSingleClick = {
                if (processingJob?.isActive == true) {
                    currentHistoryId?.let { id ->
                        lifecycle.coroutineScope.launch {
                            val config = repository.appConfigFlow.first()
                            drawerManager?.show(
                                historyId = id,
                                side = config.currentModeConfig().drawerSide,
                                widthPercent = config.permissions.drawerSettings.widthPercent,
                                showMetadata = false,
                                autoScrollEnabled = config.autoScrollContent
                            )
                        }
                    }
                } else {
                    processingJob = lifecycle.coroutineScope.launch {
                        try {
                            val config = repository.appConfigFlow.first()

                            if (config.permissions.captureMode == CaptureMode.TEXT_ONLY) {
                                // 屏幕取字：先检查无障碍服务是否可用
                                if (SolveXAccessibilityService.instance == null) {
                                    lifecycle.coroutineScope.launch {
                                        _serviceError.emit("屏幕取字需要无障碍服务，请先开启 SolveX 无障碍服务")
                                    }
                                    updateStatus(BallStatus.ERROR)
                                    SystemUtils.vibrateError(this@MainService)
                                    return@launch
                                }
                                // 显示选区覆盖层，悬浮球保持可见用于取消
                                updateStatus(BallStatus.RUNNING)
                                val selection = textRegionManager?.selectRegion()
                                if (selection == null) {
                                    updateStatus(defaultIdleStatus)
                                    return@launch
                                }
                                val text = selection.scannedText

                                android.util.Log.d("SolveX", "selectRegion scanned: ${text.length} chars, region=${selection.region}")

                                if (text.isBlank()) {
                                    val msg = if (SolveXAccessibilityService.instance == null) {
                                        "屏幕取字需要无障碍服务，请先开启 SolveX 无障碍服务"
                                    } else {
                                        "当前选区未发现可提取的文本内容"
                                    }
                                    lifecycle.coroutineScope.launch { _serviceError.emit(msg) }
                                    updateStatus(BallStatus.ERROR)
                                    SystemUtils.vibrateError(this@MainService)
                                    return@launch
                                }

                                SystemUtils.vibrateSuccess(this@MainService)
                                historyRepository.deleteProcessingItems()

                                val models = pipeline.resolveModels(config)
                                val initialResult = pipeline.createBaseResultTextOnly(models, "正在获取文本内容...")
                                val historyId = initialResult.id
                                currentHistoryId = historyId
                                val historyItem = HistoryItem(
                                    id = historyId,
                                    query = "正在思考中...",
                                    result = "正在思考中...",
                                    imagePath = null,
                                    mode = config.selectedModeId,
                                    assistantName = initialResult.assistantName,
                                    providerName = initialResult.modelSummary,
                                    modelName = initialResult.modelSummary,
                                    engineName = "屏幕取字",
                                    status = AnalysisStatus.PROCESSING
                                )
                                historyRepository.addHistoryItem(historyItem)

                                val autoOpen = config.currentModeConfig().autoOpenDrawer
                                if (autoOpen) {
                                    lifecycle.coroutineScope.launch {
                                        drawerManager?.show(
                                            historyId = historyId,
                                            side = config.currentModeConfig().drawerSide,
                                            widthPercent = config.permissions.drawerSettings.widthPercent,
                                            showMetadata = false
                                        )
                                    }
                                }

                                var currentQueryText = ""
                                var currentResultText = ""
                                var pendingUpdateJob: Job? = null

                                fun scheduleUpdate() {
                                    if (pendingUpdateJob?.isActive == true) return
                                    pendingUpdateJob = lifecycle.coroutineScope.launch(Dispatchers.IO) {
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
                                    val result = pipeline.processTextOnly(
                                        config = config,
                                        capturedText = text,
                                        onSummaryGenerated = { title, summary ->
                                            lifecycle.coroutineScope.launch {
                                                historyRepository.updateHistoryItem(historyId) { current ->
                                                    current.copy(title = title, summary = summary)
                                                }
                                            }
                                        },
                                        onQueryExtracted = { delta ->
                                            currentQueryText += delta
                                            drawerManager?.appendLiveQuery(delta)
                                            scheduleUpdate()
                                        },
                                        onDelta = { delta ->
                                            currentResultText += delta
                                            drawerManager?.appendLiveResult(delta)
                                            scheduleUpdate()
                                        }
                                    )
                                    handleProcessingResult(result, historyId, config, pendingUpdateJob)
                                } catch (e: CancellationException) {
                                    drawerManager?.hide()
                                    cleanupScope.launch {
                                        historyRepository.updateHistoryItem(historyId) { current ->
                                            current.copy(
                                                query = "用户已取消", result = "用户已取消", status = AnalysisStatus.CANCELLED
                                            )
                                        }
                                    }
                                    throw e
                                }

                                return@launch
                            }

                            updateStatus(BallStatus.RUNNING)
                            floatingBallManager?.tempHide()
                            delay(100)
                            val bitmap: android.graphics.Bitmap? = try {
                                captureEngine?.capture()
                            } finally {
                                floatingBallManager?.restore()
                            }

                            if (bitmap != null) {
                                SystemUtils.vibrateSuccess(this@MainService)
                                val config = repository.appConfigFlow.first()
                                var image: android.graphics.Bitmap = bitmap
                                val mode = ModeRegistry.get(config.selectedModeId)
                                val needCrop = config.currentModeConfig().enableCrop ?: mode.shouldCrop
                                if (needCrop) {
                                    cropManager?.let { manager ->
                                        val cropped = manager.crop(image)
                                        if (cropped == null) {
                                            updateStatus(defaultIdleStatus)
                                            return@launch
                                        }
                                        // 不主动 recycle 原图，避免 CropView 待绘制帧使用已回收的 Bitmap
                                        image = cropped
                                    }
                                }

                                // 清理前一次处理残留的占位记录
                                historyRepository.deleteProcessingItems()

                                // 创建初始记录
                                val models = pipeline.resolveModels(config)
                                val initialResult =
                                    pipeline.createBaseResult(models, image, "正在获取题目...")
                                val historyId = initialResult.id
                                currentHistoryId = historyId
                                val historyItem = HistoryItem(
                                    id = historyId,
                                    query = "正在思考中...",
                                    result = "正在思考中...",
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
                                                side = config.currentModeConfig().drawerSide,
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

                                        handleProcessingResult(result, historyId, config, pendingUpdateJob)
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
                updateStatus(defaultIdleStatus)
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

    private fun applyPrivacyPolicy(config: AppConfig) {
        lifecycle.coroutineScope.launch(Dispatchers.IO) {
            val permissions = config.permissions
            val isShizukuReady = try {
                permissions.enableStealthMode &&
                        Shizuku.pingBinder() &&
                        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (_: Exception) {
                false
            }

            if (isShizukuReady) {
                startStealthMonitor()
            } else {
                stopStealthMonitor()
                withContext(Dispatchers.Main) {
                    updateWindowsSecure(permissions.enableScreenProtection)
                    if (floatingBallManager?.status == BallStatus.LOW_PROFILE ||
                        floatingBallManager?.status == BallStatus.PROTECTED) {
                        floatingBallManager?.updateStatus(BallStatus.IDLE)
                    }
                }
            }
        }
    }

    private fun startStealthMonitor() {
        if (stealthJob?.isActive == true) return

        stealthJob = lifecycle.coroutineScope.launch(Dispatchers.IO) {
            var svc: IShizukuShellService? = null
            var lastCount = -1

            withContext(Dispatchers.Main) {
                isStealthActive = false
                floatingBallManager?.updateStatus(BallStatus.LOW_PROFILE)
                floatingBallManager?.defaultIdleStatus = BallStatus.LOW_PROFILE
            }

            while (true) {
                try {
                    if (svc == null || !svc.asBinder().isBinderAlive) {
                        svc = ShizukuUserServiceClient.acquire(this@MainService)
                        if (svc == null) {
                            delay(3000)
                            continue
                        }
                    }

                    val count = svc.getSecureWindowCount()
                    val shouldBeSecure = count > 0

                    if (isStealthActive != shouldBeSecure) {
                        isStealthActive = shouldBeSecure
                        val config = repository.appConfigFlow.first()

                        withContext(Dispatchers.Main) {
                            val finalSecure = shouldBeSecure || config.permissions.enableScreenProtection
                            updateWindowsSecure(finalSecure)
                            floatingBallManager?.updateStatus(
                                if (shouldBeSecure) BallStatus.PROTECTED else BallStatus.LOW_PROFILE
                            )
                        }
                    }

                    // FLAG_SECURE 窗口数量减少时，短暂隐藏悬浮球避免被屏幕抓拍捕获
                    if (lastCount > 0 && count < lastCount) {
                        withContext(Dispatchers.Main) {
                            floatingBallManager?.tempHide()
                        }
                        delay(800)
                        withContext(Dispatchers.Main) {
                            floatingBallManager?.restore()
                        }
                    }
                    lastCount = count

                    delay(1500)
                } catch (e: Exception) {
                    svc = null
                    delay(3000)
                }
            }
        }
    }

    private fun stopStealthMonitor() {
        stealthJob?.cancel()
        stealthJob = null
        isStealthActive = false
        // 退出隐匿模式时，自动恢复状态改回 IDLE
        floatingBallManager?.defaultIdleStatus = BallStatus.IDLE
    }

    private fun updateWindowsSecure(enabled: Boolean) {
        floatingBallManager?.updateSecureFlag(enabled)
        drawerManager?.updateSecureFlag(enabled)
        cropManager?.updateSecureFlag(enabled)
        textRegionManager?.updateScreenProtection(enabled)
    }

    private fun switchEngine() {
        lifecycle.coroutineScope.launch {
            val config = repository.appConfigFlow.first()
            // 屏幕取字模式下禁止切换引擎
            if (config.permissions.captureMode == CaptureMode.TEXT_ONLY) return@launch
            val newEngine = if (config.selectedEngine == EngineType.VISION_ENGINE) {
                EngineType.TEXT_ENGINE
            } else {
                EngineType.VISION_ENGINE
            }
            repository.saveAppConfig(config.copy(selectedEngine = newEngine))
            SystemUtils.vibrate(this@MainService, 100)
        }
    }

    /**
     * 统一处理解析结果：状态更新、通知、自动化动作、数据库持久化。
     */
    private suspend fun handleProcessingResult(
        result: ProcessingResult,
        historyId: String,
        config: AppConfig,
        pendingUpdateJob: Job?
    ) {
        pendingUpdateJob?.cancelAndJoin()
        drawerManager?.clearLiveBuffer()

        if (result.status == ProcessingStatus.SUCCESS) {
            SystemUtils.vibrateSuccess(this@MainService)

            // 统一结果投递：提取最终答案 → 复制到剪贴板 + 显示在悬浮球
            val finalAnswer = NotificationUtils.extractFinalAnswer(result.answer ?: "")
            if (finalAnswer.isNotBlank()) {
                SystemUtils.deliverResult(this@MainService, finalAnswer)
                floatingBallManager?.showText(finalAnswer)
            } else {
                floatingBallManager?.updateStatus(BallStatus.SUCCESS)
            }

            val currentHistory = historyRepository.historyItemsFlow.first().find { it.id == historyId }
            if (config.currentModeConfig().allowNotification) {
                val notifyTitle = currentHistory?.title ?: "解析完成"
                val rawAnswer = result.answer ?: "已获取最终答案"
                NotificationUtils.sendResultNotification(
                    this@MainService, notifyTitle,
                    NotificationUtils.extractFinalAnswer(rawAnswer), historyId
                )
            }
            historyRepository.updateHistoryItem(historyId) { current ->
                current.copy(
                    query = result.extractedText ?: current.query,
                    result = result.answer ?: "已获取最终答案",
                    status = AnalysisStatus.SUCCESS
                )
            }
        } else {
            floatingBallManager?.updateStatus(BallStatus.ERROR)
            SystemUtils.vibrateError(this@MainService)
            drawerManager?.hide()

            if (config.currentModeConfig().allowNotification) {
                NotificationUtils.sendResultNotification(
                    this@MainService, "解析失败", result.detail, historyId
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
    }

    override fun onDestroy() {
        _isRunning.value = false
        (application as SolveXApplication).viewModel?.checkPermissions()
        stopStealthMonitor()
        captureEngine?.release()
        captureEngine = null
        floatingBallManager?.hide()
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
                            side = config.currentModeConfig().drawerSide,
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
     * 以前台服务形式启动，根据截屏模式创建对应引擎。
     */
    private fun startAsForeground(intent: Intent?) {
        createNotificationChannel()

        val captureMode = intent?.getStringExtra(EXTRA_CAPTURE_MODE) ?: CaptureMode.SYSTEM

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SolveX 运行中")
            .setContentText("AI 也会犯错，不要过度相信！")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()

        var fgsType = 0
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            if (captureMode == CaptureMode.SYSTEM) {
                fgsType = fgsType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
        }

        startForeground(NOTIFICATION_ID, notification, fgsType)
        // 根据截屏模式创建引擎
        captureEngine?.release()
        captureEngine = when (captureMode) {
            CaptureMode.SHIZUKU -> ShizukuCaptureEngine(this)
            CaptureMode.ACCESSIBILITY -> AccessibilityCaptureEngine()
            CaptureMode.TEXT_ONLY -> null
            else -> {
                val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
                @Suppress("DEPRECATION")
                val data = intent?.getParcelableExtra<Intent>(EXTRA_PROJECTION_DATA)

                if (resultCode != 0 && data != null) {
                    SystemCaptureEngine(this, resultCode, data).also { engine ->
                        lifecycle.coroutineScope.launch {
                            delay(100)
                            engine.prepare()
                        }
                    }
                } else null
            }
        }

        floatingBallManager?.show()
    }

    private fun createNotificationChannel() {
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
