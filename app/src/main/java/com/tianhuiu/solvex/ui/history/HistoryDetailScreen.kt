package com.tianhuiu.solvex.ui.history

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tianhuiu.solvex.ui.components.DetailSection
import com.tianhuiu.solvex.ui.components.MergeModeScreenshotOverlay
import com.tianhuiu.solvex.ui.components.OutputRenderer
import com.tianhuiu.solvex.ui.components.StatusBadge
import com.tianhuiu.solvex.utils.ResponseParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 历史解析详情屏幕。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(
    itemId: String,
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
) {
    val item by viewModel.getHistoryItemById(itemId).collectAsState(initial = null)

    if (item == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("解析详情", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    windowInsets = WindowInsets(top = 0.dp)
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    } else {
        val currentItem = item!!
        key(itemId) {
            val imagePaths = currentItem.imagePaths.ifEmpty {
                listOfNotNull(currentItem.imagePath)
            }
            var currentImageIndex by remember { mutableStateOf(0) }
            var showFullscreenImage by remember { mutableStateOf(false) }

            val sectionLabel = remember(currentItem.query, currentItem.result) {
                ResponseParser.detectSectionLabel(
                    currentItem.result.ifBlank { currentItem.query }
                )
            }
            val isPerImageMode = currentItem.query.contains(sectionLabel) ||
                        currentItem.result.contains("## $sectionLabel")
            val isMergeMode = imagePaths.size > 1 && !isPerImageMode

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("解析详情", fontWeight = FontWeight.Bold)
                                if (!isMergeMode && imagePaths.size > 1) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "${currentImageIndex + 1}/${imagePaths.size}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                            }
                        },
                        actions = {
                            if (!isMergeMode && imagePaths.size > 1) {
                                IconButton(
                                    onClick = { if (currentImageIndex > 0) currentImageIndex-- },
                                    enabled = currentImageIndex > 0
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.NavigateBefore, "上一张")
                                }
                                IconButton(
                                    onClick = { if (currentImageIndex < imagePaths.size - 1) currentImageIndex++ },
                                    enabled = currentImageIndex < imagePaths.size - 1
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.NavigateNext, "下一张")
                                }
                            }
                        },
                        windowInsets = WindowInsets(top = 0.dp)
                    )
                }
            ) { padding ->
                val timeStr = remember(currentItem.timestamp) {
                    com.tianhuiu.solvex.utils.formatTimestamp(currentItem.timestamp)
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MetadataRow(
                                    icon = Icons.Default.Schedule,
                                    label = "时间",
                                    value = timeStr,
                                    modifier = Modifier.weight(1f)
                                )
                                StatusBadge(currentItem.status)
                            }

                            Row(modifier = Modifier.fillMaxWidth()) {
                                MetadataRow(
                                    icon = Icons.Default.Face,
                                    label = "智能助手",
                                    value = currentItem.assistantName ?: "默认助手",
                                    modifier = Modifier.weight(1f)
                                )
                                MetadataRow(
                                    icon = Icons.Default.AutoAwesome,
                                    label = "识别引擎",
                                    value = currentItem.engineName ?: "未知",
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            MetadataRow(
                                icon = Icons.Default.Memory,
                                label = "调用模型",
                                value = currentItem.modelName ?: "默认模型"
                            )
                        }
                    }

                    if (imagePaths.isNotEmpty()) {
                        val currentPath = imagePaths.getOrElse(currentImageIndex) { imagePaths.first() }
                        val file = File(currentPath)
                        if (file.exists()) {
                            val bitmap by produceState<android.graphics.Bitmap?>(null, currentPath) {
                                value = withContext(Dispatchers.IO) {
                                    com.tianhuiu.solvex.utils.decodeSampledBitmap(currentPath, 1200, 1200)
                                }
                            }
                            val loadedBitmap = bitmap
                            if (loadedBitmap != null) {
                                val imageBitmap = loadedBitmap.asImageBitmap()
                                Column(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "屏幕截图",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Image(
                                                bitmap = imageBitmap,
                                                contentDescription = "截图",
                                                contentScale = ContentScale.FillWidth,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { showFullscreenImage = true }
                                            )
                                        }
                                        // 合并模式下截图左右翻页按钮
                                        if (isMergeMode) {
                                            MergeModeScreenshotOverlay(
                                                currentImageIndex = currentImageIndex,
                                                totalImages = imagePaths.size,
                                                onPrevImage = { if (currentImageIndex > 0) currentImageIndex-- },
                                                onNextImage = { if (currentImageIndex < imagePaths.size - 1) currentImageIndex++ },
                                            )
                                        }
                                    }
                                }

                                if (showFullscreenImage) {
                                    FullscreenImageDialog(
                                        bitmap = imageBitmap,
                                        onDismiss = { showFullscreenImage = false },
                                        onPrevious = if (currentImageIndex > 0) {
                                            { currentImageIndex-- }
                                        } else null,
                                        onNext = if (currentImageIndex < imagePaths.size - 1) {
                                            { currentImageIndex++ }
                                        } else null,
                                        pageInfo = if (imagePaths.size > 1) "${currentImageIndex + 1}/${imagePaths.size}" else null
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {

                        val queryText = if (imagePaths.size > 1) {
                            val perQ = ResponseParser.extractPerQuestionQuery(currentItem.query, currentImageIndex + 1, sectionLabel)
                            if (perQ != null) {
                                perQ
                            } else if (isPerImageMode) {
                                "正在等待..."
                            } else {
                                currentItem.query
                            }
                        } else {
                            currentItem.query
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

                        val answerForDisplay = if (imagePaths.size > 1) {
                            val section = ResponseParser.extractPerQuestionSection(currentItem.result, currentImageIndex + 1, sectionLabel)
                            if (section != null) {
                                section
                            } else if (isPerImageMode) {
                                "正在等待..."
                            } else {
                                currentItem.result
                            }
                        } else {
                            currentItem.result
                        }
                        OutputRenderer(result = answerForDisplay)
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun MetadataRow(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * 全屏截图查看对话框。
 */
@Composable
fun FullscreenImageDialog(
    bitmap: androidx.compose.ui.graphics.ImageBitmap,
    onDismiss: () -> Unit,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    pageInfo: String? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White)
                }

                if (pageInfo != null) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = pageInfo,
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }

                if (onPrevious != null) {
                    IconButton(
                        onClick = onPrevious,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.NavigateBefore,
                            contentDescription = "上一张",
                            tint = Color.White
                        )
                    }
                }

                if (onNext != null) {
                    IconButton(
                        onClick = onNext,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.NavigateNext,
                            contentDescription = "下一张",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

