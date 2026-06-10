package com.tianhuiu.solvex.capture

import android.graphics.Bitmap

/**
 * 截屏引擎统一接口。每种截屏方式实现此接口。
 */
interface ScreenCaptureEngine {
    /** 初始化资源（可选）。 */
    suspend fun prepare() {}

    /** 执行截屏，返回 Bitmap 或 null。 */
    suspend fun capture(): Bitmap?

    /** 释放资源。 */
    fun release()
}
