package com.tianhuiu.solvex.capture

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 封装 MediaProjection 录屏接口的引擎实现。
 */
class SystemCaptureEngine(
    private val context: Context,
    private val resultCode: Int,
    private val data: Intent
) : ScreenCaptureEngine {

    private val projectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            release()
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
            this@SystemCaptureEngine.width = width
            this@SystemCaptureEngine.height = height
            mainHandler.post { recreateVirtualDisplay() }
        }
    }

    private var width = 0
    private var height = 0
    private var density = 0

    init {
        updateDisplayMetrics()
    }

    private fun updateDisplayMetrics() {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        width = metrics.widthPixels
        height = metrics.heightPixels
        density = metrics.densityDpi
    }

    override suspend fun prepare() = withContext(Dispatchers.Main) {
        prepareProjection()
    }

    private fun prepareProjection() {
        if (mediaProjection != null) return
        mediaProjection = try {
            projectionManager.getMediaProjection(resultCode, data)?.apply {
                registerCallback(projectionCallback, mainHandler)
            }
        } catch (e: Exception) {
            Log.e("SystemCapture", "Failed to get MediaProjection", e)
            null
        }
        recreateVirtualDisplay()
    }

    @SuppressLint("WrongConstant")
    private fun recreateVirtualDisplay() {
        val projection = mediaProjection ?: return
        if (width == 0 || height == 0) updateDisplayMetrics()
        imageReader?.close()
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        val current = virtualDisplay
        if (current == null) {
            virtualDisplay = projection.createVirtualDisplay(
                "ScreenCapture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface, null, null
            )
        } else {
            current.surface = imageReader!!.surface
            current.resize(width, height, density)
        }
    }

    override suspend fun capture(): Bitmap? = withContext(Dispatchers.IO) {
        withContext(Dispatchers.Main) {
            prepareProjection()
            val oldW = width
            val oldH = height
            updateDisplayMetrics()
            if (oldW != width || oldH != height) recreateVirtualDisplay()
        }

        val reader = imageReader ?: return@withContext null

        // Android 14+ 某些设备 acquireNextImage 可能会较慢或需要 acquireLatestImage
        val image = withTimeoutOrNull(500L) {
            var img = reader.acquireLatestImage()
            // 如果最新帧不可用，尝试等待下一帧
            if (img == null) {
                delay(50)
                img = reader.acquireNextImage()
            }
            img
        }

        if (image == null) {
            Log.e("SystemCapture", "Failed to acquire image after timeout")
            return@withContext null
        }

        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = createBitmap(image.width + (rowPadding / pixelStride), image.height)
        bitmap.copyPixelsFromBuffer(buffer)
        val fw = image.width
        val fh = image.height
        image.close()

        if (rowPadding != 0) {
            val cropped = Bitmap.createBitmap(bitmap, 0, 0, fw, fh)
            bitmap.recycle()
            cropped
        } else bitmap
    }

    override fun release() {
        imageReader?.close(); imageReader = null
        virtualDisplay?.release(); virtualDisplay = null
        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop(); mediaProjection = null
    }
}
