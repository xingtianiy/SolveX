package com.tianhuiu.solvex.floating

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.tianhuiu.solvex.service.SolveXAccessibilityService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.abs

/**
 * 屏幕取字区域选择管理器，双层窗口：外层 FrameLayout 控制位置，内层 SelectionDrawLayout 负责绘制与触控。
 */
class TextRegionManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val displayMetrics = context.resources.displayMetrics

    private val statusBarHeight: Int by lazy {
        val resid = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resid > 0) context.resources.getDimensionPixelSize(resid) else 0
    }

    val windowParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_SECURE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 0
        y = 200
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                WindowManager.LayoutParams::class.java
                    .getMethod("setTrustedOverlay", Boolean::class.javaPrimitiveType)
                    .invoke(this, true)
            } catch (_: Exception) { }
        }
    }

    private var selectionWindow: FrameLayout? = null
    private var drawLayout: SelectionDrawLayout? = null

    val isActive: Boolean get() = selectionWindow != null

    suspend fun selectRegion(): RegionSelection? = suspendCancellableCoroutine { cont ->
        show(
            onConfirm = { selection ->
                cont.resume(selection)
                hide()
            },
            onCancel = {
                cont.resume(null)
                hide()
            }
        )
        cont.invokeOnCancellation { hide() }
    }

    fun hide() {
        selectionWindow?.let { w ->
            if (w.isAttachedToWindow) {
                try { windowManager.removeView(w) } catch (_: Exception) { }
            }
        }
        selectionWindow = null
        drawLayout = null
    }

    fun updateScreenProtection(enabled: Boolean) {
        if (enabled) {
            windowParams.flags = windowParams.flags or WindowManager.LayoutParams.FLAG_SECURE
        } else {
            windowParams.flags = windowParams.flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
        }
        selectionWindow?.let {
            try { windowManager.updateViewLayout(it, windowParams) } catch (_: Exception) { }
        }
    }

    fun setOverlayAlpha(alpha: Float) {
        selectionWindow?.alpha = alpha
    }

    private fun show(
        onConfirm: (RegionSelection) -> Unit,
        onCancel: () -> Unit
    ) {
        hide()

        val screenW = displayMetrics.widthPixels
        val screenH = displayMetrics.heightPixels

        val outer = object : FrameLayout(context) {
            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                super.onSizeChanged(w, h, oldw, oldh)
                if (oldw == 0 && oldh == 0 && w > 0 && h > 0) {
                    windowParams.x = (screenW - w) / 2
                    windowParams.y = (screenH - h) / 3
                    try { windowManager.updateViewLayout(this, windowParams) } catch (_: Exception) { }
                }
            }
        }

        val innerW = (screenW * 0.7f).toInt().coerceIn(400, 900)
        val innerH = (screenH * 0.4f).toInt().coerceIn(200, 800)

        val inner = SelectionDrawLayout(context).apply {
            this.screenWidth = screenW
            this.screenHeight = screenH
            this.statusBarHeight = this@TextRegionManager.statusBarHeight
            this.onConfirm = onConfirm
            this.onCancel = onCancel
        }

        outer.addView(inner, FrameLayout.LayoutParams(innerW, innerH))

        inner.setOnTouchListener { _, event ->
            if (event.pointerCount > 1) return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    inner.initialX = windowParams.x
                    inner.initialY = windowParams.y
                    inner.initialTime = System.currentTimeMillis()
                    inner.initialTouchX = event.rawX
                    inner.initialTouchY = event.rawY

                    inner.isInCorner(event)
                    inner.isResizingDown = false
                    inner.blueLine = inner.isResizing
                    inner.hideButtonsAndClear()

                    return@setOnTouchListener false
                }
                MotionEvent.ACTION_UP -> {
                    inner.isResizing = false
                    if (inner.blueLine) {
                        inner.blueLine = false
                        inner.invalidate()
                    }

                    val duration = System.currentTimeMillis() - inner.initialTime
                    val moved = abs(event.rawX - inner.initialTouchX) < 10f &&
                            abs(event.rawY - inner.initialTouchY) < 10f
                    if (duration < 200L && moved) {
                        inner.triggerScan()
                    }

                    inner.restoreButtons()
                    return@setOnTouchListener false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (inner.isResizing) {
                        inner.updateSizeFromCorner(event, outer, windowParams, windowManager)
                        return@setOnTouchListener true
                    } else if (!inner.isResizingDown) {
                        windowParams.x = (inner.initialX + (event.rawX - inner.initialTouchX).toInt())
                            .coerceIn(0, screenW - outer.width)
                        windowParams.y = (inner.initialY + (event.rawY - inner.initialTouchY).toInt())
                            .coerceIn(0, screenH - outer.height)
                        windowManager.updateViewLayout(outer, windowParams)
                    }
                    return@setOnTouchListener true
                }
            }
            false
        }

        selectionWindow = outer
        drawLayout = inner
        windowManager.addView(outer, windowParams)
    }
}

class RegionSelection(
    val region: Rect,
    val scannedText: String
)

/**
 * 内层选区绘制视图，负责 Canvas 绘制、双指缩放与无障碍扫描。
 */
class SelectionDrawLayout(context: Context) : FrameLayout(context) {

    // 每个捕获的文本块
    private data class TextItem(
        val rect: Rect,
        val text: String
    )

    // 画笔 — 淡色系
    private val redFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED; alpha = 38; style = Paint.Style.FILL
    }
    private val yellowFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW; alpha = 20; style = Paint.Style.FILL
    }
    private val blueStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLUE; alpha = 50; style = Paint.Style.STROKE; strokeWidth = 1f
    }
    private val cornerWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; strokeWidth = 12f
    }
    private val cornerBlue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLUE; alpha = 100; strokeWidth = 12f
    }

    private val capturedItems = mutableListOf<TextItem>()
    private val hintItems = mutableListOf<TextItem>()
    private var scannedText = ""

    companion object {
        const val CORNER_THRESHOLD = 100
        const val MIN_WIDTH = 200; const val MIN_HEIGHT = 150
    }

    var isResizing = false
    var isResizingDown = false
    var blueLine = false
    private var holdingCorner = 0
    var initialX = 0; var initialY = 0
    var initialTouchX = 0f; var initialTouchY = 0f
    var initialTime = 0L
    private val deltaOffset = IntArray(2)
    private var anchorRight = 0
    private var anchorBottom = 0
    private var pinchInitW = 0; private var pinchInitH = 0
    private var pinchInitDX = 0f; private var pinchInitDY = 0f

    var screenWidth = 0; var screenHeight = 0
    var statusBarHeight = 0
    var onConfirm: ((RegionSelection) -> Unit)? = null
    var onCancel: (() -> Unit)? = null

    // 按钮文字颜色
    private val accentColor: Int

    private val hintView: TextView
    private val btnCancel: Button
    private val btnCopy: Button
    private val btnConfirm: Button
    private val buttonRow: LinearLayout

    init {
        setWillNotDraw(false)
        setBackgroundColor(0x407F7F7F.toInt())

        val attrs = intArrayOf(android.R.attr.colorAccent)
        val ta = context.obtainStyledAttributes(attrs)
        accentColor = ta.getColor(0, 0xFF90CAF9.toInt())
        ta.recycle()

        hintView = TextView(context).apply {
            text = "拖动四角调整选区，点击选区扫描文字"
            setTextColor(Color.WHITE); textSize = 13f
            gravity = Gravity.CENTER; setPadding(16, 4, 16, 4)
        }
        addView(hintView)

        btnCancel = Button(context, null, android.R.attr.buttonBarButtonStyle).apply {
            text = "取消"; setTextColor(accentColor)
            setOnClickListener { onCancel?.invoke() }
            setPadding(12, 4, 12, 4)
        }
        btnCopy = Button(context, null, android.R.attr.buttonBarButtonStyle).apply {
            text = "复制"; visibility = GONE; setTextColor(accentColor)
            setOnClickListener {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("SolveX", scannedText))
                Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
            setPadding(12, 4, 12, 4)
        }
        btnConfirm = Button(context, null, android.R.attr.buttonBarButtonStyle).apply {
            text = "点击选区扫描"; isEnabled = false; setTextColor(accentColor)
            setOnClickListener { onConfirm?.invoke(RegionSelection(screenRect(), scannedText)) }
            setPadding(12, 4, 12, 4)
        }

        buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            addView(btnCancel, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(4, 0, 4, 0) })
            addView(btnCopy, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(4, 0, 4, 0) })
            addView(btnConfirm, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(4, 0, 4, 0) })
        }
        addView(buttonRow)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)

        hintView.measure(
            MeasureSpec.makeMeasureSpec(w - 32, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        buttonRow.measure(
            MeasureSpec.makeMeasureSpec(w - 24, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l; val h = b - t

        val hintW = hintView.measuredWidth; val hintH = hintView.measuredHeight
        hintView.layout((w - hintW) / 2, 12, (w + hintW) / 2, 12 + hintH)

        val btnW = buttonRow.measuredWidth; val btnH = buttonRow.measuredHeight
        buttonRow.layout((w - btnW) / 2, h - btnH - 8, (w + btnW) / 2, h - 8)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (item in capturedItems) {
            canvas.drawRect(item.rect, redFill)
            canvas.drawRect(item.rect, blueStroke)
        }
        for (item in hintItems) {
            canvas.drawRect(item.rect, yellowFill)
            canvas.drawRect(item.rect, blueStroke)
        }

        val ll = 100
        val lp = if (blueLine) cornerBlue else cornerWhite

        canvas.drawLine(0f, 0f, ll.toFloat(), 0f, lp)
        canvas.drawLine(0f, 0f, 0f, ll.toFloat(), lp)
        canvas.drawLine(0f, height.toFloat(), ll.toFloat(), height.toFloat(), lp)
        canvas.drawLine(0f, height.toFloat(), 0f, (height - ll).toFloat(), lp)
        canvas.drawLine((width - ll).toFloat(), 0f, width.toFloat(), 0f, lp)
        canvas.drawLine(width.toFloat(), 0f, width.toFloat(), ll.toFloat(), lp)
        canvas.drawLine((width - ll).toFloat(), height.toFloat(), width.toFloat(), height.toFloat(), lp)
        canvas.drawLine(width.toFloat(), (height - ll).toFloat(), width.toFloat(), height.toFloat(), lp)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount == 1 &&
            (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_UP)
        ) {
            return true
        }

        if (event.pointerCount == 2) {
            when (event.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN -> {
                    pinchInitW = width; pinchInitH = height
                    pinchInitDX = abs(event.getX(0) - event.getX(1))
                    pinchInitDY = abs(event.getY(0) - event.getY(1))
                    isResizing = true; blueLine = true; invalidate()
                }
                MotionEvent.ACTION_MOVE -> if (isResizing) {
                    val newDX = abs(event.getX(0) - event.getX(1))
                    val newDY = abs(event.getY(0) - event.getY(1))
                    val nw = (pinchInitW + (newDX - pinchInitDX)).toInt()
                    val nh = (pinchInitH + (newDY - pinchInitDY)).toInt()
                    val params = layoutParams
                    params.width = nw.coerceIn(MIN_WIDTH, screenWidth)
                    params.height = nh.coerceIn(MIN_HEIGHT, screenHeight)
                    layoutParams = params
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    isResizing = false; isResizingDown = true
                    blueLine = false; invalidate()
                }
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    fun isInCorner(event: MotionEvent) {
        val loc = IntArray(2)
        (parent as? View)?.getLocationOnScreen(loc) ?: getLocationOnScreen(loc)
        anchorRight = loc[0] + ((parent as? View)?.width ?: width)
        anchorBottom = loc[1] + ((parent as? View)?.height ?: height)

        when {
            event.rawX < loc[0] + CORNER_THRESHOLD && event.rawY < loc[1] + CORNER_THRESHOLD -> {
                isResizing = true; holdingCorner = 1
            }
            event.rawX < loc[0] + CORNER_THRESHOLD && event.rawY > anchorBottom - CORNER_THRESHOLD -> {
                isResizing = true; holdingCorner = 2
            }
            event.rawX > anchorRight - CORNER_THRESHOLD && event.rawY < loc[1] + CORNER_THRESHOLD -> {
                isResizing = true; holdingCorner = 3
            }
            event.rawX > anchorRight - CORNER_THRESHOLD && event.rawY > anchorBottom - CORNER_THRESHOLD -> {
                isResizing = true; holdingCorner = 4
            }
            else -> isResizing = false
        }
        deltaOffset[0] = (event.rawX - loc[0]).toInt()
        deltaOffset[1] = (event.rawY - loc[1]).toInt()
    }

    fun updateSizeFromCorner(
        event: MotionEvent,
        outer: View,
        outerParams: WindowManager.LayoutParams,
        wm: WindowManager
    ) {
        val loc = IntArray(2)
        outer.getLocationOnScreen(loc)

        val innerParams = layoutParams

        when (holdingCorner) {
            1 -> {
                outerParams.x = event.rawX.toInt() - deltaOffset[0]
                innerParams.width = anchorRight - outerParams.x
                outerParams.y = event.rawY.toInt() - deltaOffset[1] - statusBarHeight
                innerParams.height = anchorBottom - outerParams.y - statusBarHeight
            }
            2 -> {
                outerParams.x = event.rawX.toInt() - deltaOffset[0]
                innerParams.width = anchorRight - outerParams.x
                innerParams.height = event.rawY.toInt() - loc[1] + (anchorBottom - loc[1]) - deltaOffset[1]
            }
            3 -> {
                innerParams.width = event.rawX.toInt() - loc[0] + (anchorRight - loc[0]) - deltaOffset[0]
                outerParams.y = event.rawY.toInt() - deltaOffset[1] - statusBarHeight
                innerParams.height = anchorBottom - outerParams.y - statusBarHeight
            }
            4 -> {
                innerParams.width = event.rawX.toInt() - loc[0] + (anchorRight - loc[0]) - deltaOffset[0]
                innerParams.height = event.rawY.toInt() - loc[1] + (anchorBottom - loc[1]) - deltaOffset[1]
            }
        }

        wm.updateViewLayout(outer, outerParams)

        innerParams.width = innerParams.width.coerceAtMost(screenWidth)
        innerParams.height = innerParams.height.coerceAtMost(screenHeight)
        layoutParams = innerParams
    }

    fun triggerScan() {
        val outer = parent as? View ?: this
        val loc = IntArray(2)
        outer.getLocationOnScreen(loc)
        val region = Rect(loc[0], loc[1], loc[0] + outer.width, loc[1] + outer.height)

        val svc = SolveXAccessibilityService.instance
        val result = svc?.scanRegionWithRects(region)

        if (result != null) {
            capturedItems.clear(); hintItems.clear()

            val myLoc = IntArray(2)
            getLocationOnScreen(myLoc)

            for (item in result.capturedItems) {
                val localRect = Rect(
                    item.rect.left - myLoc[0], item.rect.top - myLoc[1],
                    item.rect.right - myLoc[0], item.rect.bottom - myLoc[1]
                )
                capturedItems.add(TextItem(localRect, item.text))
            }
            for (item in result.hintItems) {
                val localRect = Rect(
                    item.rect.left - myLoc[0], item.rect.top - myLoc[1],
                    item.rect.right - myLoc[0], item.rect.bottom - myLoc[1]
                )
                hintItems.add(TextItem(localRect, item.text))
            }

            rebuildScannedText()

            if (scannedText.isNotEmpty()) {
                hintView.text = "扫描到 ${scannedText.length} 个字符"
                btnConfirm.text = "确认提取"; btnConfirm.isEnabled = true
                btnCopy.visibility = VISIBLE
            } else {
                hintView.text = "拖动四角调整选区，点击选区扫描文字"
                Toast.makeText(context, "当前选区未发现文本，请调整选区后重试", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "扫描失败，请确认无障碍服务已开启", Toast.LENGTH_SHORT).show()
        }
        requestLayout(); invalidate()
    }

    private fun rebuildScannedText() {
        val capturedText = capturedItems.joinToString("\n") { it.text }
        val hintText = hintItems.joinToString("\n") { it.text }
        scannedText = listOf(capturedText, hintText).filter { it.isNotEmpty() }.joinToString("\n")
    }

    fun screenRect(): Rect {
        val outer = parent as? View ?: this
        val loc = IntArray(2)
        outer.getLocationOnScreen(loc)
        return Rect(loc[0], loc[1], loc[0] + outer.width, loc[1] + outer.height)
    }

    fun hideButtonsAndClear() {
        btnCancel.visibility = INVISIBLE; btnCopy.visibility = INVISIBLE
        btnConfirm.visibility = INVISIBLE
        hintView.visibility = INVISIBLE
        capturedItems.clear(); hintItems.clear(); invalidate()
    }

    fun restoreButtons() {
        btnCancel.visibility = VISIBLE
        btnConfirm.visibility = VISIBLE
        hintView.visibility = VISIBLE
    }
}
