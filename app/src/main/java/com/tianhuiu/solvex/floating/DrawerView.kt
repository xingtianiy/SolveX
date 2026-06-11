package com.tianhuiu.solvex.floating

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tianhuiu.solvex.data.models.HistoryItem
import com.tianhuiu.solvex.ui.components.DetailSection
import com.tianhuiu.solvex.ui.components.MergeModeScreenshotOverlay
import com.tianhuiu.solvex.ui.components.OutputRenderer
import com.tianhuiu.solvex.ui.components.StatusBadge
import com.tianhuiu.solvex.ui.history.MetadataRow
import com.tianhuiu.solvex.utils.ResponseParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 侧边抽屉视图：显示解析详情，多图模式下支持图片翻页。
 */
@Composable
fun DrawerView(
    item: HistoryItem?,
    showMetadata: Boolean,
    autoScroll: Boolean = true,
    imagePaths: List<String> = emptyList(),
    currentImageIndex: Int = 0,
    showScreenshot: Boolean = true,
    mergeMode: Boolean = false,
    onPrevImage: () -> Unit = {},
    onNextImage: () -> Unit = {},
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (showMetadata) "解析详情" else "实时解析",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    if (!mergeMode && imagePaths.size > 1) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${currentImageIndex + 1}/${imagePaths.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row {
                    if (!mergeMode && imagePaths.size > 1) {
                        IconButton(
                            onClick = onPrevImage,
                            enabled = currentImageIndex > 0,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.NavigateBefore,
                                contentDescription = "上一张",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = onNextImage,
                            enabled = currentImageIndex < imagePaths.size - 1,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.NavigateNext,
                                contentDescription = "下一张",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }
            }

            if (item == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val scrollState = rememberScrollState()

                LaunchedEffect(item.query, item.result, autoScroll) {
                    if (autoScroll) {
                        scrollState.scrollTo(scrollState.maxValue)
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 截图预览
                        if (showScreenshot) {
                            val screenshotPath = if (imagePaths.isNotEmpty()) {
                                imagePaths.getOrElse(currentImageIndex) { imagePaths.first() }
                            } else {
                                item.imagePath
                            }
                            if (screenshotPath != null) {
                                val file = File(screenshotPath)
                                if (file.exists()) {
                                    val bitmap by produceState<android.graphics.Bitmap?>(
                                        null,
                                        screenshotPath
                                    ) {
                                        value = withContext(Dispatchers.IO) {
                                            com.tianhuiu.solvex.utils.decodeSampledBitmap(screenshotPath, 400, 400)
                                        }
                                    }
                                    bitmap?.let { bm ->
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                border = androidx.compose.foundation.BorderStroke(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                                ),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Image(
                                                    bitmap = bm.asImageBitmap(),
                                                    contentDescription = "截图",
                                                    contentScale = ContentScale.FillWidth,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                            // 合并模式下截图左右翻页按钮
                                            if (mergeMode && imagePaths.size > 1) {
                                                MergeModeScreenshotOverlay(
                                                    currentImageIndex = currentImageIndex,
                                                    totalImages = imagePaths.size,
                                                    onPrevImage = onPrevImage,
                                                    onNextImage = onNextImage,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (showMetadata) {
                            val timeStr = remember(item.timestamp) {
                                com.tianhuiu.solvex.utils.formatTimestamp(item.timestamp)
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                        alpha = 0.3f
                                    )
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        MetadataRow(
                                            icon = Icons.Default.Schedule,
                                            label = "时间",
                                            value = timeStr
                                        )
                                        StatusBadge(item.status)
                                    }
                                    MetadataRow(
                                        icon = Icons.Default.Face,
                                        label = "助手",
                                        value = item.assistantName ?: "默认"
                                    )
                                }
                            }
                        }

                        val sectionLabel = remember(item.query, item.result) {
                            ResponseParser.detectSectionLabel(
                                item.result.ifBlank { item.query }
                            )
                        }
                        val isPerImageMode = item.query.contains(sectionLabel) ||
                            item.result.contains("## $sectionLabel")

                        val queryText = if (imagePaths.size > 1 && !mergeMode) {
                            ResponseParser.extractPerQuestionQuery(item.query, currentImageIndex + 1, sectionLabel)
                                ?: if (isPerImageMode) "正在等待..." else item.query
                        } else {
                            item.query
                        }

                        val allStructuredQuestions = remember(queryText) {
                            ResponseParser.parseAllStructuredQuestions(queryText)
                        }
                        if (allStructuredQuestions.isNotEmpty()) {
                            allStructuredQuestions.forEachIndexed { index, q ->
                                DetailSection(
                                    title = if (allStructuredQuestions.size > 1) "${sectionLabel} ${index + 1}" else "解析问题",
                                    content = ResponseParser.renderStructuredQuestionFromObject(q),
                                    badgeText = q.type
                                )
                            }
                        } else if (queryText.isNotBlank() && queryText != "正在处理...") {
                            DetailSection(
                                title = "解析问题",
                                content = queryText
                            )
                        }

                        if (item.result == "正在等待..." || item.result == "正在处理...") {
                            // 模型输出尚未到达，显示进度条
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "等待中...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            OutputRenderer(result = item.result)
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}
