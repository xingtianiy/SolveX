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
import com.tianhuiu.solvex.data.models.ProjectMode
import com.tianhuiu.solvex.data.models.getModeConfig
import com.tianhuiu.solvex.floating.BallStatus
import com.tianhuiu.solvex.floating.CropManager
import com.tianhuiu.solvex.floating.DrawerManager
import com.tianhuiu.solvex.floating.FloatingBallManager
import com.tianhuiu.solvex.utils.NotificationHelper
import com.tianhuiu.solvex.utils.ResponseParser
import com.tianhuiu.solvex.utils.SystemUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 后台核心服务。
 */
class MainService : LifecycleService(), ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()

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
    private var cycleJob: Job? = null
    private var captureEngine: ScreenCaptureEngine? = null
    private var isMultiImageMode = false
    private val multiImageBuffer = mutableListOf<android.graphics.Bitmap>()
    private lateinit var repository: SettingsRepository
    private lateinit var historyRepository: HistoryRepository

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry =
        savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        savedStateRegistryController.performRestore(null)
        repository = SettingsRepository(this)

        val container = (application as SolveXApplication).container
        historyRepository = container.historyRepository

        // 清理意外中断的任务
        lifecycle.coroutineScope.launch {
            historyRepository.cleanupProcessingItems()
        }

        val pipeline = container.processingPipeline
        drawerManager = DrawerManager(this, historyRepository, lifecycle.coroutineScope)
        cropManager = CropManager(this)

        floatingBallManager = FloatingBallManager(this).apply {
            onSingleClick = {
                if (this@MainService.isMultiImageMode) {
                    this@MainService.captureAndBuffer()
                } else if (processingJob?.isActive == true) {
                    currentHistoryId?.let { id ->
                        lifecycle.coroutineScope.launch {
                            val config = repository.appConfigFlow.first()
                            val showScreenshot = config.getModeConfig().showScreenshotInRealtime
                            drawerManager?.show(
                                historyId = id,
                                side = config.permissions.drawerSettings.side,
                                widthPercent = config.permissions.drawerSettings.widthPercent,
                                showMetadata = false,
                                autoScrollEnabled = config.autoScrollContent,
                                showScreenshotEnabled = showScreenshot
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
                                if (config.getModeConfig().cropBeforeProcessing) {
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

                                val models = pipeline.resolveModels(config)
                                val initialResult =
                                    pipeline.createBaseResult(models, image, "正在获取题目...")
                                val historyId = initialResult.id
                                currentHistoryId = historyId
                                val historyItem = HistoryItem(
                                    id = historyId,
                                    query = "正在处理...",
                                    result = "正在处理...",
                                    imagePath = initialResult.screenshotPath,
                                    mode = config.selectedMode.displayName,
                                    assistantName = initialResult.assistantName,
                                    providerName = initialResult.modelSummary,
                                    modelName = initialResult.modelSummary,
                                    engineName = config.selectedEngine.displayName,
                                    status = AnalysisStatus.PROCESSING
                                )
                                historyRepository.addHistoryItem(historyItem)

                                try {
                                    // 检查是否需要自动打开抽屉
                                    val autoOpen = config.getModeConfig().autoOpenDrawer
                                    val showScreenshot = config.getModeConfig().showScreenshotInRealtime

                                    if (autoOpen) {
                                        lifecycle.coroutineScope.launch {
                                            drawerManager?.show(
                                                historyId = historyId,
                                                side = config.permissions.drawerSettings.side,
                                                widthPercent = config.permissions.drawerSettings.widthPercent,
                                                showMetadata = false,
                                                showScreenshotEnabled = showScreenshot
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
                                                    ResponseParser.extractFinalAnswer(
                                                        result.answer ?: ""
                                                    )
                                                if (finalAnswer.isNotBlank()) {
                                                    AutomationAction(
                                                        AutomationAction.TYPE_CLIPBOARD,
                                                        finalAnswer
                                                    )
                                                } else null
                                            }

                                            action?.let { act ->
                                                when (act.type) {
                                                    AutomationAction.TYPE_BUBBLE -> {
                                                        floatingBallManager?.showText(act.text)
                                                    }

                                                    AutomationAction.TYPE_CLIPBOARD -> {
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
                                            val allowNotification = config.getModeConfig().allowNotification

                                            if (allowNotification) {
                                                val notifyTitle =
                                                    currentHistory?.title ?: "解析完成"
                                                val rawAnswer =
                                                    result.answer ?: result.automationThought
                                                    ?: "已获取最终答案"
                                                val notifyContent =
                                                    ResponseParser.extractFinalAnswer(rawAnswer)
                                                NotificationHelper.sendResultNotification(
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

                                            // 发送失败通知
                                            val allowNotification = config.getModeConfig().allowNotification

                                            if (allowNotification) {
                                                NotificationHelper.sendResultNotification(
                                                    this@MainService,
                                                    "解析失败",
                                                    result.detail,
                                                    historyId
                                                )
                                            }

                                            historyRepository.updateHistoryItem(historyId) { current ->
                                                current.copy(
                                                    query = result.detail,
                                                    result = result.detail,
                                                    status = AnalysisStatus.FAILURE
                                                )
                                            }
                                        }
                                    } finally {
                                        image.recycle()
                                    }
                                } catch (e: CancellationException) {
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
                                updateStatus(BallStatus.ERROR)
                                SystemUtils.vibrateError(this@MainService)

                                val allowNotification = config.getModeConfig().allowNotification
                                if (allowNotification) {
                                    NotificationHelper.sendResultNotification(
                                        this@MainService,
                                        "截图失败",
                                        captureHint
                                    )
                                }
                            }
                        } catch (_: CancellationException) {
                        } catch (e: Exception) {
                            android.util.Log.e("SolveX", "流程异常", e)
                            updateStatus(BallStatus.ERROR)
                            SystemUtils.vibrateError(this@MainService)

                            currentHistoryId?.let { historyId ->
                                lifecycle.coroutineScope.launch {
                                    historyRepository.updateHistoryItem(historyId) { current ->
                                        current.copy(
                                            query = e.message ?: "未知错误",
                                            result = e.message ?: "未知错误",
                                            status = AnalysisStatus.FAILURE
                                        )
                                    }
                                }
                            }

                            try {
                                val config = repository.appConfigFlow.first()
                                val allowNotification = config.getModeConfig().allowNotification
                                if (allowNotification) {
                                    NotificationHelper.sendResultNotification(
                                        this@MainService,
                                        "解析异常",
                                        e.message ?: "未知错误"
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
                if (this@MainService.isMultiImageMode) {
                    this@MainService.cancelMultiImageMode()
                } else {
                    processingJob?.cancel()
                    processingJob = null
                    updateStatus(BallStatus.IDLE)
                }
            }
            onLongClick = {
                if (this@MainService.isMultiImageMode) {
                    if (this@MainService.multiImageBuffer.isNotEmpty()) {
                        this@MainService.sendMultiImageBuffer()
                    }
                } else {
                    SystemUtils.vibrate(this@MainService, 50)
                    lifecycle.coroutineScope.launch {
                        val config = repository.appConfigFlow.first()
                        if (config.getModeConfig().multiImageEnabled
                            && processingJob?.isActive != true
                        ) {
                            this@MainService.enterMultiImageMode()
                        } else {
                            switchEngine()
                        }
                    }
                }
            }
        }

        lifecycle.coroutineScope.launch {
            repository.appConfigFlow.collect { config ->
                floatingBallManager?.enableAutoHide = config.permissions.enableAutoHideBall
                floatingBallManager?.setBallSize(config.permissions.ballSizeDp)
            }
        }
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

    private fun enterMultiImageMode() {
        isMultiImageMode = true
        multiImageBuffer.forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        multiImageBuffer.clear()
        floatingBallManager?.enterMultiImageMode()
        SystemUtils.vibrate(this@MainService, 50)
    }

    private fun captureAndBuffer() {
        lifecycle.coroutineScope.launch {
            floatingBallManager?.tempHide()
            delay(100)
            val bitmap = try {
                captureEngine?.capture()
            } finally {
                floatingBallManager?.restore()
            }
            if (bitmap != null) {
                var image: android.graphics.Bitmap = bitmap
                val config = repository.appConfigFlow.first()
                if (config.quickConfig.multiImageCropEnabled) {
                    cropManager?.let { manager ->
                        val cropped = manager.crop(image)
                        if (cropped == null) return@launch
                        if (cropped !== image) image.recycle()
                        image = cropped
                    }
                }
                if (!isMultiImageMode) {
                    if (!image.isRecycled) image.recycle()
                    return@launch
                }
                multiImageBuffer.add(image)
                floatingBallManager?.updateBadgeCount(multiImageBuffer.size)
                SystemUtils.vibrateSuccess(this@MainService)
            }
        }
    }

    private fun sendMultiImageBuffer() {
        val bitmaps = multiImageBuffer.toList()
        multiImageBuffer.clear()
        isMultiImageMode = false
        floatingBallManager?.exitMultiImageMode()

        if (bitmaps.isEmpty()) return

        val pipeline = (application as SolveXApplication).container.processingPipeline
        drawerManager?.onPageChanged = null
        processingJob = lifecycle.coroutineScope.launch {
            try {
                floatingBallManager?.updateStatus(BallStatus.RUNNING)
                SystemUtils.vibrateSuccess(this@MainService)

                val config = repository.appConfigFlow.first()

                // 创建初始记录（使用第一张图作为主截图）
                val models = pipeline.resolveModels(config)
                val initialResult = pipeline.createBaseResult(models, bitmaps.first(), "正在处理多张截图...", bitmaps.drop(1))
                val historyId = initialResult.id
                currentHistoryId = historyId
                val historyItem = HistoryItem(
                    id = historyId,
                    query = "正在处理...",
                    result = "正在处理...",
                    imagePath = initialResult.screenshotPath,
                    imagePaths = initialResult.screenshotPaths,
                    mode = ProjectMode.MULTI_IMAGE_MODE.displayName,
                    assistantName = initialResult.assistantName,
                    providerName = initialResult.modelSummary,
                    modelName = initialResult.modelSummary,
                    engineName = config.selectedEngine.displayName,
                    status = AnalysisStatus.PROCESSING
                )
                historyRepository.addHistoryItem(historyItem)

                // 设置抽屉多图路径并标记合并/逐张模式
                drawerManager?.setImagePaths(initialResult.screenshotPaths)
                drawerManager?.setMergeMode(config.multiImageConfig.multiImageMergeEnabled)

                // 自动打开抽屉（使用多图模式专用设置）
                if (config.multiImageConfig.multiImageAutoOpenDrawer) {
                    lifecycle.coroutineScope.launch {
                        drawerManager?.show(
                            historyId = historyId,
                            side = config.permissions.drawerSettings.side,
                            widthPercent = config.permissions.drawerSettings.widthPercent,
                            showMetadata = false,
                            autoScrollEnabled = config.autoScrollContent,
                            showScreenshotEnabled = config.quickConfig.showScreenshotInRealtime
                        )
                    }
                }

                try {
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

                    val result = pipeline.processMultiImage(
                        config = config,
                        bitmaps = bitmaps,
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
                        },
                        onPageStart = { page ->
                            drawerManager?.setProcessingPage(page)
                        },
                        onImageComplete = { index, answer ->
                            val fa = ResponseParser.extractFinalAnswer(answer)
                            if (fa.isNotBlank()) {
                                floatingBallManager?.showText(fa)
                            }
                        }
                    )

                    pendingUpdateJob?.cancelAndJoin()
                    drawerManager?.clearLiveBuffer()

                    if (result.status == ProcessingStatus.SUCCESS) {
                        floatingBallManager?.updateStatus(BallStatus.SUCCESS)
                        SystemUtils.vibrateSuccess(this@MainService)

                        val isMergeMode =
                            config.multiImageConfig.multiImageMergeEnabled && bitmaps.size > 1
                        val isPerImageMode =
                            !config.multiImageConfig.multiImageMergeEnabled && bitmaps.size > 1

                        if (isPerImageMode) {
                            // 逐张模式：onImageComplete 已在每张图完成时显示答案，此处覆盖为首页答案并绑定翻页回调
                            val sectionLabel =
                                ResponseParser.detectSectionLabel(result.answer ?: "")
                            val firstSection = ResponseParser.extractPerQuestionSection(
                                result.answer ?: "", 1, sectionLabel
                            )
                            val firstAnswer = ResponseParser.extractFinalAnswer(
                                firstSection ?: (result.answer ?: "")
                            )
                            if (firstAnswer.isNotBlank()) {
                                floatingBallManager?.showText(firstAnswer)
                            } else {
                                // 回退：无结构化输出时使用自动化工具
                                val action = result.automationAction ?: run {
                                    val fa = ResponseParser.extractFinalAnswer(result.answer ?: "")
                                    if (fa.isNotBlank()) AutomationAction(
                                        AutomationAction.TYPE_CLIPBOARD,
                                        fa
                                    )
                                    else null
                                }
                                action?.let { act ->
                                    when (act.type) {
                                        AutomationAction.TYPE_BUBBLE -> floatingBallManager?.showText(
                                            act.text
                                        )

                                        AutomationAction.TYPE_CLIPBOARD -> {
                                            SystemUtils.copyToClipboard(this@MainService, act.text)
                                            floatingBallManager?.showText("已复制")
                                        }
                                    }
                                }
                            }

                            // 翻页时更新悬浮球显示对应题目的答案
                            drawerManager?.onPageChanged = { pageIndex ->
                                val pageResult = result.answer?.let { fullAnswer ->
                                    val label = ResponseParser.detectSectionLabel(fullAnswer)
                                    ResponseParser.extractPerQuestionSection(
                                        fullAnswer,
                                        pageIndex + 1,
                                        label
                                    )
                                }
                                val pageAnswer =
                                    pageResult?.let { ResponseParser.extractFinalAnswer(it) }
                                if (!pageAnswer.isNullOrBlank()) {
                                    floatingBallManager?.showText(pageAnswer)
                                }
                            }
                        } else if (isMergeMode) {
                            // 合并模式：提取所有 ## 题目 N 的最终答案并循环滚动显示
                            val sectionLabel =
                                ResponseParser.detectSectionLabel(result.answer ?: "")
                            val allAnswers = mutableListOf<String>()
                            var n = 1
                            while (true) {
                                val section = ResponseParser.extractPerQuestionSection(
                                    result.answer ?: "", n, sectionLabel
                                )
                                if (section == null) break
                                val fa = ResponseParser.extractFinalAnswer(section)
                                if (fa.isNotBlank()) allAnswers.add(fa)
                                n++
                            }
                            cycleJob?.cancel()
                            if (allAnswers.size > 1) {
                                cycleJob = lifecycle.coroutineScope.launch {
                                    var idx = 0
                                    while (coroutineContext.isActive) {
                                        floatingBallManager?.showText(
                                            "${sectionLabel}${idx + 1}: ${allAnswers[idx]}",
                                            persistent = true
                                        )
                                        delay(3500)
                                        idx = (idx + 1) % allAnswers.size
                                    }
                                }
                            } else if (allAnswers.size == 1) {
                                floatingBallManager?.showText(allAnswers[0])
                            }
                            drawerManager?.onPageChanged = null
                        } else {
                            // 单图模式：使用自动化工具处理悬浮球显示与剪贴板
                            val action = result.automationAction ?: run {
                                val finalAnswer =
                                    ResponseParser.extractFinalAnswer(result.answer ?: "")
                                if (finalAnswer.isNotBlank()) {
                                    AutomationAction(AutomationAction.TYPE_CLIPBOARD, finalAnswer)
                                } else null
                            }

                            action?.let { act ->
                                when (act.type) {
                                    AutomationAction.TYPE_BUBBLE -> {
                                        floatingBallManager?.showText(act.text)
                                    }

                                    AutomationAction.TYPE_CLIPBOARD -> {
                                        SystemUtils.copyToClipboard(this@MainService, act.text)
                                        floatingBallManager?.showText("已复制")
                                    }
                                }
                            }
                            drawerManager?.onPageChanged = null
                        }

                        if (config.multiImageConfig.allowNotification) {
                            val currentHistory = historyRepository.historyItemsFlow.first()
                                .find { it.id == historyId }
                            val notifyTitle = currentHistory?.title ?: "多图解析完成"
                            val notifyContent = buildString {
                                val sectionLabel =
                                    ResponseParser.detectSectionLabel(result.answer ?: "")
                                var n = 1
                                while (true) {
                                    val section = ResponseParser.extractPerQuestionSection(
                                        result.answer ?: "", n, sectionLabel
                                    ) ?: break
                                    val fa = ResponseParser.extractFinalAnswer(section)
                                    if (fa.isNotBlank()) {
                                        if (isNotEmpty()) append("\n")
                                        append("${sectionLabel}$n: $fa")
                                    }
                                    n++
                                }
                                if (isEmpty()) {
                                    append(
                                        ResponseParser.extractFinalAnswer(
                                            result.answer ?: "已获取最终答案"
                                        )
                                    )
                                }
                            }
                            NotificationHelper.sendResultNotification(
                                this@MainService, notifyTitle, notifyContent, historyId
                            )
                        }

                        historyRepository.updateHistoryItem(historyId) { current ->
                            current.copy(
                                query = result.extractedText ?: current.query,
                                result = result.answer ?: "已获取最终答案",
                                imagePaths = result.screenshotPaths,
                                status = AnalysisStatus.SUCCESS
                            )
                        }
                    } else {
                        floatingBallManager?.updateStatus(BallStatus.ERROR)
                        SystemUtils.vibrateError(this@MainService)
                        drawerManager?.hide()

                        if (config.multiImageConfig.allowNotification) {
                            NotificationHelper.sendResultNotification(
                                this@MainService, "多图解析失败", result.detail, historyId
                            )
                        }

                        historyRepository.updateHistoryItem(historyId) { current ->
                            current.copy(
                                query = result.detail,
                                result = result.detail,
                                status = AnalysisStatus.FAILURE
                            )
                        }
                    }
                } catch (e: CancellationException) {
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
                } finally {
                    bitmaps.forEach { bitmap ->
                        if (!bitmap.isRecycled) bitmap.recycle()
                    }
                }
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                android.util.Log.e("SolveX", "多图流程异常", e)
                floatingBallManager?.updateStatus(BallStatus.ERROR)
                SystemUtils.vibrateError(this@MainService)
                cycleJob?.cancel()
                drawerManager?.hide()
                try {
                    val config = repository.appConfigFlow.first()
                    if (config.multiImageConfig.allowNotification) {
                        NotificationHelper.sendResultNotification(
                            this@MainService, "多图解析异常", e.message ?: "未知错误"
                        )
                    }
                } catch (_: Exception) {
                }
            } finally {
                currentHistoryId = null
                processingJob = null
                bitmaps.forEach { bitmap ->
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            }
        }
    }

    private fun cancelMultiImageMode() {
        multiImageBuffer.forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        multiImageBuffer.clear()
        isMultiImageMode = false
        drawerManager?.clearLiveBuffer()
        drawerManager?.onPageChanged = null
        cycleJob?.cancel()
        floatingBallManager?.exitMultiImageMode()
        SystemUtils.vibrate(this@MainService, 50)
    }

    override fun onDestroy() {
        _isRunning.value = false
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
        cycleJob?.cancel()
        cleanupScope.cancel()
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
            NotificationHelper.ACTION_VIEW_HISTORY -> {
                val historyId = intent.getStringExtra(NotificationHelper.EXTRA_HISTORY_ID)
                if (historyId != null) {
                    lifecycle.coroutineScope.launch {
                        val config = repository.appConfigFlow.first()
                        val showScreenshot = config.getModeConfig().showScreenshotInRealtime
                        // 检测是否为合并模式：逐张分析结果含 ## 题目 N 分节标题
                        val item = historyRepository.historyItemsFlow.first().find { it.id == historyId }
                        val isMerge = item?.let { hist ->
                            val sectionLabel = ResponseParser.detectSectionLabel(
                                hist.result.ifBlank { hist.query })
                            hist.imagePaths.size > 1 && !hist.result.contains("## $sectionLabel")
                        } ?: false
                        drawerManager?.setMergeMode(isMerge)
                        drawerManager?.show(
                            historyId = historyId,
                            side = config.permissions.drawerSettings.side,
                            widthPercent = config.permissions.drawerSettings.widthPercent,
                            showMetadata = false,
                            showScreenshotEnabled = showScreenshot
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
            CaptureMode.SHIZUKU -> ShizukuCaptureEngine()
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
