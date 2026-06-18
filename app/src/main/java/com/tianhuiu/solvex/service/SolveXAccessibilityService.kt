package com.tianhuiu.solvex.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
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
     * 针对不同 Android 版本执行兼容性截屏。
     */
    suspend fun takeScreenshotCompat(): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w("SolveXA11y", "takeScreenshot requires API 30+")
            return null
        }
        return suspendCancellableCoroutine { cont ->
            try {
                takeScreenshot(
                    android.view.Display.DEFAULT_DISPLAY,
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
