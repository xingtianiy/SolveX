package com.tianhuiu.solvex.floating

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.tianhuiu.solvex.data.HistoryRepository
import com.tianhuiu.solvex.data.models.DrawerSide
import com.tianhuiu.solvex.data.models.HistoryItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 抽屉管理器：负责抽屉 WindowManager 挂载与状态更新。
 */
class DrawerManager(
    private val context: Context,
    private val historyRepository: HistoryRepository
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var composeView: ComposeView? = null

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var observeJob: Job? = null
    private val currentItem = mutableStateOf<HistoryItem?>(null)

    private val layoutParams = WindowManager.LayoutParams().apply {
        type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        format = PixelFormat.TRANSLUCENT
        flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        width = WindowManager.LayoutParams.MATCH_PARENT
        height = WindowManager.LayoutParams.MATCH_PARENT
    }

    /**
     * 显示抽屉。
     */
    fun show(
        historyId: String,
        side: DrawerSide,
        widthPercent: Float,
        showMetadata: Boolean = false
    ) {
        if (composeView != null) {
            updateContent(historyId)
            return
        }

        val finalWidthPercent = 0.9f // 强制 90% 宽度

        composeView = ComposeView(context).apply {
            (context as? LifecycleOwner)?.let { setViewTreeLifecycleOwner(it) }
            (context as? ViewModelStoreOwner)?.let { setViewTreeViewModelStoreOwner(it) }
            (context as? SavedStateRegistryOwner)?.let { setViewTreeSavedStateRegistryOwner(it) }

            setContent {
                MaterialTheme {
                    val item by currentItem
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 背景遮罩，点击关闭
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { hide() }
                        )

                        // 抽屉内容
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(finalWidthPercent)
                                .align(if (side == DrawerSide.LEFT) Alignment.CenterStart else Alignment.CenterEnd)
                                .clickable(enabled = false) {} // 拦截点击
                        ) {
                            DrawerView(
                                item = item,
                                showMetadata = showMetadata,
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
                currentItem.value = items.find { it.id == historyId }
            }
        }
    }

    /**
     * 隐藏抽屉。
     */
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
        composeView = null
    }

    fun isShowing(): Boolean = composeView != null
}
