package com.tianhuiu.solvex.capture

import android.graphics.Bitmap

/**
 * 截屏引擎统一接口
 */
interface ScreenCaptureEngine {
    // 初始化资源
    suspend fun prepare() {}
    // 执行截屏
    suspend fun capture(): Bitmap?
    // 释放资源
    fun release()
}
