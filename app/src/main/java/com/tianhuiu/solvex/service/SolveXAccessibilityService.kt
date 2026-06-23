package com.tianhuiu.solvex.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * 提供基于无障碍服务的静默截屏能力。
 */
class SolveXAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: SolveXAccessibilityService? = null
            private set

        /**
         * 用于处理截屏数据的单线程执行器。
         */
        private val screenshotExecutor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "SolveX-Screenshot").apply { priority = Thread.NORM_PRIORITY }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("SolveXA11y", "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不需要处理无障碍事件
    }

    override fun onInterrupt() {
        // 服务中断
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d("SolveXA11y", "Accessibility service destroyed")
    }

    /**
     * 单个文字块的矩形信息。
     */
    data class TextRectInfo(
        val rect: Rect,
        val text: String
    )

    /**
     * 带矩形反馈的区域扫描结果。每个文字块与矩形一一对应。
     */
    class RegionScanResult(
        val text: String,
        val capturedItems: List<TextRectInfo>,
        val hintItems: List<TextRectInfo>
    )

    // 常见 UI 元素描述，不应作为文本内容
    private val uiNoisePatterns = setOf(
         "image", "icon", "banner", "ad", "navigation",
        "toolbar",  "tab", "menu", "link", "logo", "avatar"
    )

    /**
     * 扫描指定区域，返回文本及节点矩形（用于 UI 视觉反馈）。
     * capturedItems: 完全包含在选区内节点的矩形与文字（绘制红色）
     * hintItems: 部分重叠节点的矩形与文字（绘制黄色）
     */
    fun scanRegionWithRects(region: Rect, expandedPx: Int = 30): RegionScanResult {
        val root = rootInActiveWindow
        if (root == null) {
            Log.w("SolveXA11y", "scanRegionWithRects: rootInActiveWindow is null")
            return RegionScanResult("", emptyList(), emptyList())
        }
        try {
            val expanded = Rect(region)
            expanded.inset(-expandedPx, -expandedPx)
            val text = StringBuilder(1024)
            val visited = HashSet<Int>()
            val addedTexts = HashSet<String>()
            val captured = mutableListOf<TextRectInfo>()
            val hints = mutableListOf<TextRectInfo>()
            scanRects(root, text, visited, addedTexts, expanded, captured, hints)
            Log.d("SolveXA11y", "scanRegionWithRects: ${text.length} chars, ${captured.size} captured, ${hints.size} hints")
            return RegionScanResult(text.toString().trim(), captured, hints)
        } catch (e: Exception) {
            Log.e("SolveXA11y", "scanRegionWithRects failed", e)
            return RegionScanResult("", emptyList(), emptyList())
        } finally {
            root.recycle()
        }
    }

    private fun scanRects(
        node: AccessibilityNodeInfo,
        out: StringBuilder,
        visited: HashSet<Int>,
        addedTexts: HashSet<String>,
        region: Rect,
        captured: MutableList<TextRectInfo>,
        hints: MutableList<TextRectInfo>
    ) {
        if (!visited.add(System.identityHashCode(node))) return
        try {
            if (!node.isVisibleToUser) return
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.isEmpty) return

            val text = node.text?.toString()?.trim()
            val desc = node.contentDescription?.toString()?.trim()
            val piece = when {
                !text.isNullOrBlank() -> text
                !desc.isNullOrBlank() && desc.length > 2 &&
                    uiNoisePatterns.none { desc.contains(it, ignoreCase = true) } -> desc
                else -> null
            }

            if (piece != null && piece.isNotEmpty() && addedTexts.add(piece)) {
                if (region.contains(bounds)) {
                    captured.add(TextRectInfo(Rect(bounds), piece))
                    if (out.isNotEmpty()) out.append('\n')
                    out.append(piece)
                } else if (Rect.intersects(region, bounds)) {
                    hints.add(TextRectInfo(Rect(bounds), piece))
                    if (out.isNotEmpty()) out.append('\n')
                    out.append(piece)
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                scanRects(child, out, visited, addedTexts, region, captured, hints)
                child.recycle()
            }
        } catch (e: Exception) {
            Log.w("SolveXA11y", "scanRects node error", e)
        }
    }

    suspend fun takeScreenshotCompat(): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w("SolveXA11y", "takeScreenshot requires API 30+")
            return null
        }
        return suspendCancellableCoroutine { cont ->
            try {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    screenshotExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshot: ScreenshotResult) {
                            try {
                                val buffer = screenshot.hardwareBuffer
                                val bitmap = Bitmap.wrapHardwareBuffer(
                                    buffer,
                                    screenshot.colorSpace
                                )
                                // copy 在后台线程执行
                                val mutableBitmap = bitmap?.copy(Bitmap.Config.ARGB_8888, true)
                                if (mutableBitmap !== bitmap && bitmap != null) {
                                    bitmap.recycle()
                                }
                                buffer.close()
                                if (cont.isActive) cont.resume(mutableBitmap)
                            } catch (e: Exception) {
                                Log.e("SolveXA11y", "Screenshot processing failed", e)
                                if (cont.isActive) cont.resume(null)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            Log.e("SolveXA11y", "takeScreenshot failed: $errorCode")
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("SolveXA11y", "takeScreenshot exception", e)
                if (cont.isActive) cont.resume(null)
            }
        }
    }
}
