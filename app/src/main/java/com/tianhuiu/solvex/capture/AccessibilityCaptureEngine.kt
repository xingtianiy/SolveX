package com.tianhuiu.solvex.capture

import android.graphics.Bitmap
import com.tianhuiu.solvex.service.SolveXAccessibilityService

/**
 * 封装无障碍服务截屏接口的引擎实现。
 */
class AccessibilityCaptureEngine : ScreenCaptureEngine {

    override suspend fun capture(): Bitmap? {
        return SolveXAccessibilityService.instance?.takeScreenshotCompat()
    }

    override fun release() {
        // 无需资源释放
    }
}
