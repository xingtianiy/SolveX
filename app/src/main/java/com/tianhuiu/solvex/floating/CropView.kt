package com.tianhuiu.solvex.floating

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * 裁剪视图：默认显示初始裁剪框，每次拖拽重新绘制区域，确认后裁剪。
 */
@Composable
fun CropView(
    bitmap: Bitmap,
    onConfirm: (Bitmap) -> Unit,
    onUseFull: () -> Unit,
    onCancel: () -> Unit
) {
    val density = LocalDensity.current
    val cornerRadius = with(density) { 8.dp.toPx() }
    val borderWidth = with(density) { 2.dp.toPx() }

    val bitmapW = bitmap.width.toFloat()
    val bitmapH = bitmap.height.toFloat()

    var cropLeft by remember { mutableStateOf(bitmapW * 0.175f) }
    var cropTop by remember { mutableStateOf(bitmapH * 0.175f) }
    var cropRight by remember { mutableStateOf(bitmapW * 0.825f) }
    var cropBottom by remember { mutableStateOf(bitmapH * 0.825f) }

    var dragStartBx by remember { mutableStateOf(0f) }
    var dragStartBy by remember { mutableStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 裁剪预览区域
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
            ) {
                val containerW = constraints.maxWidth.toFloat()
                val containerH = constraints.maxHeight.toFloat()

                if (containerW > 0 && containerH > 0) {
                    val bitmapAspect = bitmapW / bitmapH
                    val containerAspect = containerW / containerH

                    val displayW: Float
                    val displayH: Float
                    if (bitmapAspect > containerAspect) {
                        displayW = containerW
                        displayH = containerW / bitmapAspect
                    } else {
                        displayH = containerH
                        displayW = containerH * bitmapAspect
                    }

                    val offsetX = (containerW - displayW) / 2f
                    val offsetY = (containerH - displayH) / 2f
                    val scale = displayW / bitmapW

                    fun toBx(dx: Float) = (dx - offsetX) / scale
                    fun toBy(dy: Float) = (dy - offsetY) / scale
                    fun toDx(bx: Float) = offsetX + bx * scale
                    fun toDy(by: Float) = offsetY + by * scale

                    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { touch ->
                                        dragStartBx = toBx(touch.x)
                                        dragStartBy = toBy(touch.y)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val bx = toBx(change.position.x)
                                        val by = toBy(change.position.y)
                                        cropLeft = minOf(dragStartBx, bx)
                                        cropTop = minOf(dragStartBy, by)
                                        cropRight = maxOf(dragStartBx, bx)
                                        cropBottom = maxOf(dragStartBy, by)
                                    }
                                )
                            }
                            .drawCropOverlay(
                                imageBitmap = imageBitmap,
                                displayW = displayW,
                                displayH = displayH,
                                offsetX = offsetX,
                                offsetY = offsetY,
                                cropLeft = { cropLeft },
                                cropTop = { cropTop },
                                cropRight = { cropRight },
                                cropBottom = { cropBottom },
                                toDx = ::toDx,
                                toDy = ::toDy,
                                cornerRadius = cornerRadius,
                                borderWidth = borderWidth
                            )
                    )
                }
            }

            // 底部按钮栏
            Surface(
                tonalElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onUseFull,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("使用全图")
                    }
                    Button(
                        onClick = {
                            val cx = cropLeft.toInt().coerceIn(0, bitmap.width - 1)
                            val cy = cropTop.toInt().coerceIn(0, bitmap.height - 1)
                            val cw = (cropRight - cropLeft).toInt().coerceIn(1, bitmap.width - cx)
                            val ch = (cropBottom - cropTop).toInt().coerceIn(1, bitmap.height - cy)
                            onConfirm(Bitmap.createBitmap(bitmap, cx, cy, cw, ch))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("确认裁剪")
                    }
                }
            }
        }

        // 顶部关闭图标
        IconButton(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
                .statusBarsPadding()
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "取消裁剪",
                tint = Color.White
            )
        }
    }
}

/**
 * 绘制裁剪遮罩、边框和四角高亮。
 */
private fun Modifier.drawCropOverlay(
    imageBitmap: androidx.compose.ui.graphics.ImageBitmap,
    displayW: Float,
    displayH: Float,
    offsetX: Float,
    offsetY: Float,
    cropLeft: () -> Float,
    cropTop: () -> Float,
    cropRight: () -> Float,
    cropBottom: () -> Float,
    toDx: (Float) -> Float,
    toDy: (Float) -> Float,
    cornerRadius: Float,
    borderWidth: Float
): Modifier = this.then(
    Modifier.drawBehind {
        // 绘制图片
        drawImage(
            image = imageBitmap,
            dstOffset = IntOffset(offsetX.toInt(), offsetY.toInt()),
            dstSize = IntSize(displayW.toInt(), displayH.toInt())
        )

        // 裁剪框四角坐标
        val cdl = toDx(cropLeft())
        val cdt = toDy(cropTop())
        val cdr = toDx(cropRight())
        val cdb = toDy(cropBottom())
        val overlay = Color.Black.copy(alpha = 0.5f)

        // 上方遮罩
        drawRect(overlay, Offset(0f, 0f), Size(size.width, cdt))
        // 下方遮罩
        drawRect(overlay, Offset(0f, cdb), Size(size.width, size.height - cdb))
        // 左侧遮罩
        drawRect(overlay, Offset(0f, cdt), Size(cdl, cdb - cdt))
        // 右侧遮罩
        drawRect(overlay, Offset(cdr, cdt), Size(size.width - cdr, cdb - cdt))

        // 裁剪框边框
        drawRect(
            Color.White,
            Offset(cdl, cdt),
            Size(cdr - cdl, cdb - cdt),
            style = Stroke(borderWidth)
        )

        // 四角高亮
        drawCircle(Color.White, cornerRadius, Offset(cdl, cdt))
        drawCircle(Color.White, cornerRadius, Offset(cdr, cdt))
        drawCircle(Color.White, cornerRadius, Offset(cdl, cdb))
        drawCircle(Color.White, cornerRadius, Offset(cdr, cdb))
    }
)
