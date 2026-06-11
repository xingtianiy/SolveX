package com.tianhuiu.solvex.floating

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * 浮动窗口公共参数工厂：统一管理 type/format/flags 与 ComposeView 生命周期绑定。
 */
object OverlayParams {

    /**
     * 创建所有浮动覆盖层共用的基础 LayoutParams。
     * @param touchFlag 触摸标志：FLAG_NOT_FOCUSABLE 或 FLAG_NOT_TOUCH_MODAL
     */
    fun createBaseParams(touchFlag: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            format = PixelFormat.TRANSLUCENT
            flags = touchFlag or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        }
    }

    /**
     * 为通过 WindowManager 挂载的 ComposeView 设置生命周期所有者。
     * 在 setContent 之前调用。
     */
    fun ComposeView.setupLifecycleOwners(context: Context) {
        (context as? LifecycleOwner)?.let { setViewTreeLifecycleOwner(it) }
        (context as? ViewModelStoreOwner)?.let { setViewTreeViewModelStoreOwner(it) }
        (context as? SavedStateRegistryOwner)?.let { setViewTreeSavedStateRegistryOwner(it) }
    }
}
