package com.tianhuiu.solvex.floating

import android.content.Context
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import com.tianhuiu.solvex.data.HistoryRepository
import com.tianhuiu.solvex.data.models.DrawerSide
import com.tianhuiu.solvex.data.models.HistoryItem
import com.tianhuiu.solvex.floating.OverlayParams.setupLifecycleOwners
import com.tianhuiu.solvex.utils.ResponseParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class DrawerManager(
    private val context: Context,
    private val historyRepository: HistoryRepository,
    private val scope: CoroutineScope,
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var composeView: ComposeView? = null

    private var observeJob: Job? = null
    private val currentItem = mutableStateOf<HistoryItem?>(null)

    private var liveQuery = ""
    private var liveResult = ""

    private val imagePaths = mutableStateOf<List<String>>(emptyList())
    private val currentImageIndex = mutableStateOf(0)

    private val pageQueries = mutableMapOf<Int, String>()
    private val pageResults = mutableMapOf<Int, String>()
    private var currentProcessingPage = 0
    private val mergeMode = mutableStateOf(false)

    private val autoScroll = mutableStateOf(true)
    private val showScreenshot = mutableStateOf(true)

    private val layoutParams = OverlayParams.createBaseParams(
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
    ).apply {
        width = WindowManager.LayoutParams.MATCH_PARENT
        height = WindowManager.LayoutParams.MATCH_PARENT
    }

    var onPageChanged: ((pageIndex: Int) -> Unit)? = null

    fun show(
        historyId: String,
        side: DrawerSide,
        widthPercent: Float,
        showMetadata: Boolean = false,
        autoScrollEnabled: Boolean = true,
        showScreenshotEnabled: Boolean = true
    ) {
        autoScroll.value = autoScrollEnabled
        showScreenshot.value = showScreenshotEnabled

        val existing = composeView
        // ComposeView 已存在：重新挂载到窗口
        if (existing != null) {
            layoutParams.gravity = if (side == DrawerSide.LEFT) android.view.Gravity.START else android.view.Gravity.END
            if (existing.parent == null) {
                windowManager.addView(existing, layoutParams)
            } else {
                windowManager.updateViewLayout(existing, layoutParams)
            }
            updateContent(historyId)
            return
        }

        val finalWidthPercent = widthPercent

        composeView = ComposeView(context).apply {
            setupLifecycleOwners(context)

            setContent {
                MaterialTheme {
                    val item by currentItem
                    val paths = imagePaths.value
                    val idx = currentImageIndex.value
                    val displayItem = item?.let {
                        if (paths.isNotEmpty()) {
                            if (mergeMode.value) {
                                it.copy(
                                    query = liveQuery.ifEmpty { it.query },
                                    result = liveResult.ifEmpty { it.result }
                                )
                            } else {
                                it.copy(
                                    query = pageQueries[idx] ?: "",
                                    result = pageResults[idx] ?: "正在等待..."
                                )
                            }
                        } else {
                            it.copy(
                                query = liveQuery.ifEmpty { it.query },
                                result = liveResult.ifEmpty { it.result }
                            )
                        }
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { hide() }
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(finalWidthPercent)
                                .align(if (side == DrawerSide.LEFT) Alignment.CenterStart else Alignment.CenterEnd)
                                .clickable(enabled = false) {}
                        ) {
                            DrawerView(
                                item = displayItem,
                                showMetadata = showMetadata,
                                autoScroll = autoScroll.value,
                                imagePaths = paths,
                                currentImageIndex = idx,
                                showScreenshot = showScreenshot.value,
                                mergeMode = mergeMode.value,
                                onPrevImage = { prevImage() },
                                onNextImage = { nextImage() },
                                onClose = { hide() }
                            )
                        }
                    }
                }
            }
        }

        windowManager.addView(composeView, layoutParams)
        updateContent(historyId)
    }

    private fun updateContent(historyId: String) {
        observeJob?.cancel()
        observeJob = scope.launch {
            historyRepository.historyItemsFlow.collect { items ->
                val item = items.find { it.id == historyId }
                currentItem.value = item
                val paths = item?.imagePaths.orEmpty()
                if (paths.isNotEmpty()) {
                    setImagePaths(paths)
                    val result = item!!.result
                    val query = item.query
                    val sectionLabel = ResponseParser.detectSectionLabel(result)
                    if (result.contains("## $sectionLabel")) {
                        for (i in paths.indices) {
                            pageResults[i] = ResponseParser.extractPerQuestionSection(result, i + 1)
                                ?: "正在等待..."
                        }
                    } else {
                        for (i in paths.indices) {
                            pageResults[i] = result
                        }
                    }
                    for (i in paths.indices) {
                        val q = ResponseParser.extractPerQuestionQuery(query, i + 1)
                        pageQueries[i] = q ?: if (mergeMode.value) query else ""
                    }
                }
            }
        }
    }

    fun appendLiveQuery(delta: String) {
        if (imagePaths.value.isNotEmpty()) {
            if (mergeMode.value) {
                liveQuery += delta
                currentItem.value = currentItem.value?.copy(query = liveQuery)
            } else {
                val page = currentProcessingPage
                pageQueries[page] = (pageQueries[page] ?: "") + delta
                if (currentImageIndex.value == page) {
                    currentItem.value = currentItem.value?.copy(query = pageQueries[page] ?: "")
                }
            }
        } else {
            liveQuery += delta
            currentItem.value = currentItem.value?.copy(query = liveQuery)
        }
    }

    fun appendLiveResult(delta: String) {
        if (imagePaths.value.isNotEmpty()) {
            if (mergeMode.value) {
                liveResult = if (liveResult == "正在等待...") delta else liveResult + delta
                currentItem.value = currentItem.value?.copy(result = liveResult)
            } else {
                val page = currentProcessingPage
                val current = pageResults[page]
                val newContent = if (current == null || current == "正在等待...") delta else current + delta
                pageResults[page] = newContent
                if (currentImageIndex.value == page) {
                    currentItem.value = currentItem.value?.copy(result = newContent)
                }
            }
        } else {
            liveResult += delta
            currentItem.value = currentItem.value?.copy(result = liveResult)
        }
    }

    fun clearLiveBuffer() {
        liveQuery = ""
        liveResult = ""
    }

    fun setProcessingPage(page: Int) {
        currentProcessingPage = page
        if (!pageResults.containsKey(page)) {
            pageResults[page] = "正在等待..."
        }
    }

    fun setImagePaths(paths: List<String>) {
        imagePaths.value = paths
        currentImageIndex.value = 0
        pageQueries.clear()
        pageResults.clear()
        paths.indices.forEach { i ->
            pageResults[i] = "正在等待..."
        }
    }

    /** 设置为合并模式：所有页面共享同一份流式内容，而非逐页独立输出。 */
    fun setMergeMode(enabled: Boolean) {
        mergeMode.value = enabled
        if (enabled) {
            currentProcessingPage = 0
        }
    }

    fun prevImage() {
        if (currentImageIndex.value > 0) {
            currentImageIndex.value--
            refreshPageDisplay()
            onPageChanged?.invoke(currentImageIndex.value)
        }
    }

    fun nextImage() {
        if (currentImageIndex.value < imagePaths.value.size - 1) {
            currentImageIndex.value++
            refreshPageDisplay()
            onPageChanged?.invoke(currentImageIndex.value)
        }
    }

    private fun refreshPageDisplay() {
        val page = currentImageIndex.value
        val item = currentItem.value ?: return
        if (mergeMode.value) {
            currentItem.value = item.copy(
                query = liveQuery.ifEmpty { item.query },
                result = liveResult.ifEmpty { item.result }
            )
        } else {
            val q = pageQueries[page] ?: ""
            val r = pageResults[page] ?: "正在等待..."
            currentItem.value = item.copy(query = q, result = r)
        }
    }

    fun hide() {
        observeJob?.cancel()
        observeJob = null
        composeView?.let {
            if (it.parent != null) {
                try {
                    windowManager.removeView(it)
                } catch (_: Exception) {
                }
            }
        }
        // 保留 composeView 引用与所有内存状态，再次 show() 时无需从 DB 重新加载
    }

    fun isShowing(): Boolean = composeView != null
}
