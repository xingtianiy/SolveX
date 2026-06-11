package com.tianhuiu.solvex.floating

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun FloatingBallView(
    status: BallStatus,
    displayMode: BallDisplayMode,
    isAtLeftEdge: Boolean,
    ballText: String? = null,
    badgeCount: Int = 0,
    isMultiImageMode: Boolean = false,
    ballSizeDp: Float = 40f,
) {
    val baseSize = if (displayMode == BallDisplayMode.FULL) ballSizeDp.dp else (ballSizeDp * 0.6f).dp
    val baseWidth = if (displayMode == BallDisplayMode.FULL) ballSizeDp.dp else (ballSizeDp * 0.35f).dp

    val targetHeight = if (displayMode == BallDisplayMode.FULL) baseSize else baseSize * 3
    val size by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = tween(200),
        label = "size"
    )

    val width by animateDpAsState(
        targetValue = baseWidth,
        animationSpec = tween(200),
        label = "width"
    )

    val alpha by animateFloatAsState(
        targetValue = if (displayMode == BallDisplayMode.FULL) 1f else 0.4f,
        label = "alpha"
    )

    val rotation: Float = if (status == BallStatus.RUNNING) {
        val rotationTransition = rememberInfiniteTransition(label = "rotation")
        val animatedRotation by rotationTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )
        animatedRotation
    } else 0f

    val backgroundColor = when (status) {
        BallStatus.IDLE -> Color(0xFF2196F3)
        BallStatus.RUNNING -> Color(0xFF673AB7)
        BallStatus.SUCCESS -> Color(0xFF4CAF50)
        BallStatus.ERROR -> Color(0xFFF44336)
        BallStatus.MULTI_IMAGE -> Color(0xFFFF9800)
    }

    Box(
        modifier = Modifier
            .size(width = width, height = size)
            .clip(
                if (displayMode == BallDisplayMode.FULL)
                    CircleShape
                else
                    RoundedCornerShape(
                        topStart = if (isAtLeftEdge) 0.dp else 8.dp,
                        bottomStart = if (isAtLeftEdge) 0.dp else 8.dp,
                        topEnd = if (isAtLeftEdge) 8.dp else 0.dp,
                        bottomEnd = if (isAtLeftEdge) 8.dp else 0.dp
                    )
            )
            .background(backgroundColor.copy(alpha = alpha))
            .padding(if (displayMode == BallDisplayMode.FULL) 4.dp else 0.dp),
        contentAlignment = Alignment.Center
    ) {
        if (displayMode == BallDisplayMode.FULL) {
            when (status) {
                BallStatus.IDLE -> {
                    Icon(
                        imageVector = Icons.Filled.SmartToy,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                BallStatus.RUNNING -> {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(rotation)
                    )
                }

                BallStatus.SUCCESS -> {
                    if (!ballText.isNullOrBlank()) {
                        val scrollState = rememberScrollState()

                        // 自动滚动逻辑：针对长文本开启循环滚动
                        if (ballText.length > 2) {
                            LaunchedEffect(ballText, displayMode) {
                                if (displayMode != BallDisplayMode.FULL) return@LaunchedEffect
                                while (isActive) {
                                    delay(1500)
                                    scrollState.animateScrollTo(
                                        scrollState.maxValue,
                                        animationSpec = tween(
                                            durationMillis = (ballText.length * 400).coerceIn(
                                                2000,
                                                8000
                                            ),
                                            easing = LinearEasing
                                        )
                                    )
                                    delay(1000)
                                    scrollState.scrollTo(0)
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = ballText,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = when {
                                    ballText.length <= 1 -> 16.sp
                                    ballText.length == 2 -> 14.sp
                                    ballText.length == 3 -> 12.sp
                                    else -> 10.sp
                                },
                                maxLines = 1,
                                modifier = Modifier.horizontalScroll(scrollState)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                BallStatus.ERROR -> {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                BallStatus.MULTI_IMAGE -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (badgeCount > 0) {
                            Text(
                                text = badgeCount.toString(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = when {
                                    badgeCount >= 100 -> 10.sp
                                    badgeCount >= 10 -> 14.sp
                                    else -> 18.sp
                                },
                                maxLines = 1
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.fillMaxSize(0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}
