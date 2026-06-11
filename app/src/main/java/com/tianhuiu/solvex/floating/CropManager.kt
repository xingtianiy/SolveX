package com.tianhuiu.solvex.floating

import android.content.Context
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import com.tianhuiu.solvex.floating.OverlayParams.setupLifecycleOwners
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 裁剪管理器：负责裁剪 WindowManager 挂载，暂停处理流直到用户完成裁剪或取消。
 */
class CropManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var composeView: ComposeView? = null

    private val layoutParams = OverlayParams.createBaseParams(
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
    ).apply {
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
            setupLifecycleOwners(context)
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
