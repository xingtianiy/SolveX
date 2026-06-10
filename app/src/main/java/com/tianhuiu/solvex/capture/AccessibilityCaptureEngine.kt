package com.tianhuiu.solvex.capture

import android.graphics.Bitmap
import com.tianhuiu.solvex.service.SolveXAccessibilityService

/**
 * 无障碍截屏引擎：通过 AccessibilityService.takeScreenshot() 截取屏幕。
 * 需要用户在系统设置中手动开启 SolveX 无障碍服务。
 */
class AccessibilityCaptureEngine : ScreenCaptureEngine {

    override suspend fun capture(): Bitmap? {
        return SolveXAccessibilityService.instance?.takeScreenshotCompat()
    }

    override fun release() {
        // 无障碍模式无需释放资源
    }
}
