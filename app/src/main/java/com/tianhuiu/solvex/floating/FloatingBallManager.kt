package com.tianhuiu.solvex.floating

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import com.tianhuiu.solvex.floating.OverlayParams.setupLifecycleOwners
import kotlin.math.abs

class FloatingBallManager(private val context: Context) {

    companion object {
        /** 全局自动隐藏延迟时长（毫秒） */
        private const val AUTO_HIDE_DELAY_MS = 5000L
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val layoutParams = OverlayParams.createBaseParams(
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    ).apply {
        width = WindowManager.LayoutParams.WRAP_CONTENT
        height = WindowManager.LayoutParams.WRAP_CONTENT
        gravity = Gravity.TOP or Gravity.START
        x = 0
        y = 500
    }

    private var composeView: ComposeView? = null
    private var isTempHidden = false

    private val _enableAutoHide = mutableStateOf(value = true)
    var enableAutoHide: Boolean
        get() = _enableAutoHide.value
        set(value) {
            _enableAutoHide.value = value
            if (!value) {
                handler.removeCallbacks(hideRunnable)
                if (displayMode == BallDisplayMode.HIDDEN_STRIP) {
                    displayMode = BallDisplayMode.FULL
                    snapToEdge()
                }
            } else {
                resetHideTimer()
            }
        }
    var status by mutableStateOf(BallStatus.IDLE)
    private var displayMode by mutableStateOf(BallDisplayMode.FULL)
    private var isAtLeftEdge by mutableStateOf(value = true)
    private var ballText by mutableStateOf<String?>(null)
    private var badgeCount by mutableStateOf(0)
    private var isMultiImageMode by mutableStateOf(false)
    private var ballSizeDp by mutableStateOf(40f)

    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable {
        displayMode = BallDisplayMode.HIDDEN_STRIP
        snapToEdge()
    }

    private val statusResetRunnable = Runnable {
        status = BallStatus.IDLE
        ballText = null
        if (enableAutoHide) {
            displayMode = BallDisplayMode.HIDDEN_STRIP
            snapToEdge()
        }
    }

    var onSingleClick: (() -> Unit)? = null
    var onDoubleClick: (() -> Unit)? = null
    var onLongClick: (() -> Unit)? = null

    fun show() {
        if (isTempHidden) return
        val existing = composeView
        if ((existing != null) && (existing.parent != null)) return

        if (existing != null) {
            windowManager.addView(existing, layoutParams)
            resetHideTimer()
            return
        }

        composeView = ComposeView(context).apply {
            setupLifecycleOwners(context)

            setContent {
                FloatingBallView(
                    status = status,
                    displayMode = displayMode,
                    isAtLeftEdge = isAtLeftEdge,
                    ballText = ballText,
                    badgeCount = badgeCount,
                    isMultiImageMode = isMultiImageMode,
                    ballSizeDp = ballSizeDp
                )
            }

            setOnTouchListener(FloatingTouchListener())
        }

        windowManager.addView(composeView, layoutParams)
        resetHideTimer()
    }

    fun hide() {
        handler.removeCallbacks(hideRunnable)
        isTempHidden = false
        handler.post {
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

    fun tempHide() {
        handler.removeCallbacks(hideRunnable)
        isTempHidden = true
        handler.post {
            composeView?.let {
                if (it.parent != null) {
                    try {
                        windowManager.removeView(it)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    fun restore() {
        handler.post {
            composeView?.let {
                if (it.parent == null) {
                    try {
                        windowManager.addView(it, layoutParams)
                    } catch (_: Exception) {
                    }
                }
            }
        }
        isTempHidden = false
        resetHideTimer()
    }

    fun updateStatus(newStatus: BallStatus) {
        status = newStatus
        if ((newStatus == BallStatus.SUCCESS) || (newStatus == BallStatus.ERROR)) {
            handler.removeCallbacks(hideRunnable)
            handler.removeCallbacks(statusResetRunnable)
            handler.postDelayed(statusResetRunnable, AUTO_HIDE_DELAY_MS)
        } else {
            handler.removeCallbacks(statusResetRunnable)
            resetHideTimer()
        }
    }

    fun showText(text: String, persistent: Boolean = false) {
        ballText = text
        displayMode = BallDisplayMode.FULL
        handler.post { snapToEdge() }
        if (persistent) {
            handler.removeCallbacks(statusResetRunnable)
        }
        updateStatus(BallStatus.SUCCESS)
    }

    fun enterMultiImageMode() {
        isMultiImageMode = true
        badgeCount = 0
        displayMode = BallDisplayMode.FULL
        status = BallStatus.MULTI_IMAGE
        handler.removeCallbacks(hideRunnable)
        snapToEdge()
    }

    fun updateBadgeCount(count: Int) {
        badgeCount = count
    }

    fun exitMultiImageMode() {
        isMultiImageMode = false
        badgeCount = 0
        status = BallStatus.IDLE
        if (enableAutoHide) {
            resetHideTimer()
        }
    }

    fun setBallSize(sizeDp: Float) {
        ballSizeDp = sizeDp
        snapToEdge()
    }

    private fun resetHideTimer() {
        handler.removeCallbacks(hideRunnable)
        if (enableAutoHide) {
            handler.postDelayed(hideRunnable, AUTO_HIDE_DELAY_MS)
        }
    }

    private fun getCurrentBallWidthPx(): Int {
        val density = context.resources.displayMetrics.density
        val dpValue = if (displayMode == BallDisplayMode.FULL) ballSizeDp * 0.9f else ballSizeDp * 0.25f
        return (dpValue * density).toInt()
    }

    private fun snapToEdge() {
        val view = composeView ?: return
        if (view.parent == null) return

        val screenWidth = context.resources.displayMetrics.widthPixels
        val ballWidth = getCurrentBallWidthPx()
        val centerX = layoutParams.x + (ballWidth / 2)
        isAtLeftEdge = centerX < (screenWidth / 2)

        layoutParams.x = if (isAtLeftEdge) 0 else screenWidth - ballWidth
        windowManager.updateViewLayout(view, layoutParams)
    }

    private inner class FloatingTouchListener : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var isMoving = false
        private var lastLayoutUpdateTime = 0L

        private val gestureDetector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    if (displayMode == BallDisplayMode.HIDDEN_STRIP) {
                        displayMode = BallDisplayMode.FULL
                        snapToEdge()
                        resetHideTimer()
                        return true
                    }
                    onSingleClick?.invoke()
                    resetHideTimer()
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (displayMode == BallDisplayMode.FULL) {
                        onDoubleClick?.invoke()
                        resetHideTimer()
                    }
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    if (displayMode == BallDisplayMode.HIDDEN_STRIP) {
                        displayMode = BallDisplayMode.FULL
                        snapToEdge()
                        resetHideTimer()
                        return
                    }
                    if (displayMode == BallDisplayMode.FULL && (status == BallStatus.IDLE || status == BallStatus.MULTI_IMAGE)) {
                        onLongClick?.invoke()
                        resetHideTimer()
                    }
                }
            })

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            if (gestureDetector.onTouchEvent(event)) return true

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoving = false
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        isMoving = true
                        layoutParams.x = initialX + dx.toInt()
                        layoutParams.y = initialY + dy.toInt()

                        val screenWidth = context.resources.displayMetrics.widthPixels
                        val ballWidth = getCurrentBallWidthPx()
                        isAtLeftEdge = (layoutParams.x + (ballWidth / 2)) < (screenWidth / 2)

                        val now = System.currentTimeMillis()
                        if (now - lastLayoutUpdateTime >= 16) {
                            lastLayoutUpdateTime = now
                            val view = composeView
                            if (view != null && view.parent != null) {
                                windowManager.updateViewLayout(view, layoutParams)
                            }
                        }
                        handler.removeCallbacks(hideRunnable)
                    }
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isMoving) {
                        snapToEdge()
                        resetHideTimer()
                    }
                    return true
                }
            }
            return false
        }
    }
}
