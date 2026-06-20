package com.tianhuiu.solvex.floating

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 裁剪管理器：负责裁剪 WindowManager 挂载，暂停处理流直到用户完成裁剪或取消。
 */
class CropManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var composeView: ComposeView? = null

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                WindowManager.LayoutParams::class.java
                    .getMethod("setTrustedOverlay", Boolean::class.javaPrimitiveType)
                    .invoke(this, true)
            } catch (_: Exception) { }
        }
        width = WindowManager.LayoutParams.MATCH_PARENT
        height = WindowManager.LayoutParams.MATCH_PARENT
    }

    /**
     * 显示裁剪界面并挂起，返回裁剪后的 Bitmap 或 null（取消时）。
     */
    suspend fun crop(bitmap: Bitmap): Bitmap? = suspendCancellableCoroutine { cont ->
        show(
            bitmap,
            onConfirm = { cropped -> cont.resume(cropped) },
            onUseFull = { cont.resume(bitmap) },
            onCancel = { cont.resume(null) }
        )
        cont.invokeOnCancellation { hide() }
    }

    private fun show(
        bitmap: Bitmap,
        onConfirm: (Bitmap) -> Unit,
        onUseFull: () -> Unit,
        onCancel: () -> Unit
    ) {
        hide()
        composeView = ComposeView(context).apply {
            (context as? LifecycleOwner)?.let { setViewTreeLifecycleOwner(it) }
            (context as? ViewModelStoreOwner)?.let { setViewTreeViewModelStoreOwner(it) }
            (context as? SavedStateRegistryOwner)?.let { setViewTreeSavedStateRegistryOwner(it) }
            setContent {
                MaterialTheme {
                    CropView(
                        bitmap = bitmap,
                        onConfirm = { cropped ->
                            onConfirm(cropped)
                            hide()
                        },
                        onUseFull = {
                            onUseFull()
                            hide()
                        },
                        onCancel = {
                            onCancel()
                            hide()
                        }
                    )
                }
            }
        }
        windowManager.addView(composeView, layoutParams)
    }

    /**
     * 更新防截屏标志。
     */
    fun updateSecureFlag(enabled: Boolean) {
        val currentFlags = layoutParams.flags
        val newFlags = if (enabled) {
            currentFlags or WindowManager.LayoutParams.FLAG_SECURE
        } else {
            currentFlags and WindowManager.LayoutParams.FLAG_SECURE.inv()
        }

        if (currentFlags != newFlags) {
            layoutParams.flags = newFlags
            composeView?.let {
                if (it.parent != null) {
                    windowManager.updateViewLayout(it, layoutParams)
                }
            }
        }
    }

    fun hide() {
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
}
