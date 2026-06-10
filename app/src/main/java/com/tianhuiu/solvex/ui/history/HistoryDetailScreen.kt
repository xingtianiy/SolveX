package com.tianhuiu.solvex.ui.history

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.tianhuiu.solvex.ui.components.MathView
import com.tianhuiu.solvex.utils.AutomationTools
import com.tianhuiu.solvex.utils.NotificationUtils
import com.tianhuiu.solvex.ui.components.StatusBadge
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

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
    val historyItems by viewModel.historyItems.collectAsState()
    val item = historyItems.find { it.id == itemId }
    var showFullscreenImage by remember { mutableStateOf(value = false) }

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
        if (item == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                if (historyItems.isEmpty()) {
                    CircularProgressIndicator()
                } else {
                    Text("未找到该记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            key(itemId) {
                val dateFormat =
                    remember { SimpleDateFormat("yyyy MM-dd HH:mm:ss", Locale.getDefault()) }
                val timeStr = dateFormat.format(Date(item.timestamp))

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                ) {
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
                                StatusBadge(item.status)
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
                                    value = item.assistantName ?: "默认助手",
                                    modifier = Modifier.weight(1f)
                                )
                                MetadataRow(
                                    icon = Icons.Default.AutoAwesome,
                                    label = "识别引擎",
                                    value = item.engineName ?: "未知",
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            MetadataRow(
                                icon = Icons.Default.Memory,
                                label = "调用模型",
                                value = item.modelName ?: "默认模型"
                            )
                        }
                    }

                    // 截图展示
                    item.imagePath?.let { path ->
                        val file = File(path)
                        if (file.exists()) {
                            val bitmap = remember(path) { BitmapFactory.decodeFile(path) }
                            if (bitmap != null) {
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
                                            bitmap = bitmap.asImageBitmap(),
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
                                        bitmap = bitmap.asImageBitmap(),
                                        onDismiss = { showFullscreenImage = false }
                                    )
                                }
                            }
                        }
                    }

                    // 题目与解答区块
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DetailSection(
                            title = "解析问题",
                            content = NotificationUtils.renderStructuredQuestion(item.query),
                            badgeText = AutomationTools.extractQuestionType(item.query)
                        )

                        val (process, finalAnswer) = NotificationUtils.splitAnalysisResult(item.result)

                        if (process.isNotBlank()) {
                            val title = if (finalAnswer.isNotBlank()) "解析过程" else "解析结果"
                            DetailSection(title = title, content = process)
                        }

                        if (finalAnswer.isNotBlank()) {
                            FinalAnswerSection(title = "最终答案", content = finalAnswer)
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

/**
 * 元数据行：展示带有图标和标签的信息。
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
 * 详情内容区块：使用 MathView 支持 LaTeX 渲染。
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

@Composable
fun FinalAnswerSection(title: String, content: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
        ) {
            Text(
                text = content,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
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
