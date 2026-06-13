package com.tianhuiu.solvex.ui.history

import android.graphics.BitmapFactory
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import com.tianhuiu.solvex.data.models.AnalysisStatus
import com.tianhuiu.solvex.render.MarkdownParser
import com.tianhuiu.solvex.ui.components.LoadingOverlay
import com.tianhuiu.solvex.ui.components.MathView
import com.tianhuiu.solvex.ui.components.StatusBadge
import com.tianhuiu.solvex.utils.AutomationTools
import com.tianhuiu.solvex.utils.DateTimeUtils
import com.tianhuiu.solvex.utils.NotificationUtils
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
    var showFullscreenImage by remember { mutableStateOf(value = false) }

    Box(modifier = Modifier.fillMaxSize()) {
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
            if (item != null) {
                val currentItem = item!!
                key(itemId) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                    ) {
                        val timeStr = DateTimeUtils.formatFull(currentItem.timestamp)

                        // 元数据卡片
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

                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(
                                        alpha = 0.5f
                                    )
                                )

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    MetadataRow(
                                        icon = Icons.Default.Face,
                                        label = "智能助手",
                                        value = currentItem.assistantName?.ifBlank { null }
                                            ?: "默认助手",
                                        modifier = Modifier.weight(1f)
                                    )
                                    MetadataRow(
                                        icon = Icons.Default.AutoAwesome,
                                        label = "识别引擎",
                                        value = currentItem.engineName?.ifBlank { null } ?: "未知",
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                MetadataRow(
                                    icon = Icons.Default.Memory,
                                    label = "调用模型",
                                    value = currentItem.modelName?.ifBlank { null }
                                        ?: "未配置默认模型"
                                )
                            }
                        }

                        // 截图展示
                        currentItem.imagePath?.let { path ->
                            val file = File(path)
                            if (file.exists()) {
                                val bitmap by produceState<android.graphics.Bitmap?>(null, path) {
                                    value = withContext(Dispatchers.IO) {
                                        BitmapFactory.decodeFile(path)
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
                                    }

                                    if (showFullscreenImage) {
                                        FullscreenImageDialog(
                                            bitmap = imageBitmap,
                                            onDismiss = { showFullscreenImage = false }
                                        )
                                    }
                                }
                            }
                        }

                        // 内容区块
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val sections = remember(currentItem.result) {
                                MarkdownParser.parse(currentItem.result)
                            }

                            val isQueryPlaceholder =
                                currentItem.query == "思考中..." || currentItem.query.isBlank()
                            val isResultPlaceholder =
                                sections.all { it.title.isEmpty() && (it.content == "思考中..." || it.content.isBlank()) }

                            // 1. 提取阶段
                            if (currentItem.status == AnalysisStatus.PROCESSING && isQueryPlaceholder) {
                                ThinkingCard("正在提取内容...")
                            } else {
                                // 2. 显示提取内容
                                if (!isQueryPlaceholder && currentItem.status != AnalysisStatus.FAILURE) {
                                    val isStructured = remember(currentItem.query) {
                                        currentItem.query.contains("{") && currentItem.query.contains(
                                            "}"
                                        )
                                    }
                                    ContentCard(
                                        title = if (isStructured) "解析问题" else "提取内容",
                                        content = NotificationUtils.renderStructuredQuestion(
                                            currentItem.query
                                        ),
                                        badgeText = AutomationTools.extractQuestionType(currentItem.query)
                                    )
                                }

                                // 3. 分析阶段
                                if (currentItem.status == AnalysisStatus.PROCESSING && isResultPlaceholder) {
                                    ThinkingCard("正在生成分析")
                                } else if (currentItem.status != AnalysisStatus.CANCELLED) {
                                    // 4. 显示分析结果（已取消任务不显示部分结果）
                                    sections.forEachIndexed { index, section ->
                                        if (section.content.isNotBlank()) {
                                            DetailSection(
                                                title = section.title,
                                                content = section.content,
                                                isPrimary = index == sections.lastIndex
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }

        LoadingOverlay(
            isLoading = item == null,
            message = "加载详情中"
        )
    }
}

/**
 * 元数据行。
 */
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
 * 思考中进度卡片。
 */
@Composable
fun ThinkingCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 详情内容区块（无标题的纯内容卡片）。
 */
@Composable
fun ContentCard(
    title: String = "解析问题",
    content: String,
    badgeText: String? = null
) {
    Column(
        modifier = Modifier.padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            if (badgeText != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        ) {
            MathView(
                text = content,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }
    }
}

/**
 * 详情内容区块（带标题）。
 */
@Composable
fun DetailSection(
    title: String,
    content: String,
    isPrimary: Boolean = false,
    badgeText: String? = null
) {
    Column(
        modifier = Modifier.padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (title.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )

                if (badgeText != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = if (isPrimary) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isPrimary) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        ) {
            MathView(
                text = content,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
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
    onDismiss: () -> Unit
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
            }
        }
    }
}
