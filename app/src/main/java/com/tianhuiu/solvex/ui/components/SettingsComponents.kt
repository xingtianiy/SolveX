package com.tianhuiu.solvex.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.tianhuiu.solvex.data.models.ModelProvider
import kotlinx.coroutines.launch

@Composable
fun SettingBadge(
    text: String? = null,
    color: Color = MaterialTheme.colorScheme.error,
    contentColor: Color = MaterialTheme.colorScheme.onError
) {
    if (text == null) {
        // 默认红点样式
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, androidx.compose.foundation.shape.CircleShape)
        )
    } else {
        // 文字标签样式
        Surface(
            color = color,
            contentColor = contentColor,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SettingsGroup(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
fun NumberStepper(
    value: Long,
    onValueChange: (Long) -> Unit,
    min: Long = 1,
    max: Long = 300,
    step: Long = 1,
    modifier: Modifier = Modifier
) {
    var showEditDialog by remember { mutableStateOf(false) }

    if (showEditDialog) {
        var textValue by remember { mutableStateOf(value.toString()) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("修改数值") },
            text = {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                            textValue = newValue
                        }
                    },
                    label = { Text("请输入 $min 到 $max 之间的数值") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Timer, null) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val longValue = textValue.toLongOrNull() ?: value
                    onValueChange(longValue.coerceIn(min, max))
                    showEditDialog = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                val newValue = (value - step).coerceAtLeast(min)
                if (newValue != value) onValueChange(newValue)
            },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.Remove,
                contentDescription = "减少",
                modifier = Modifier.size(20.dp),
                tint = if (value > min) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        }

        Text(
            text = value.toString(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .width(48.dp)
                .clickable { showEditDialog = true },
            textAlign = TextAlign.Center
        )

        IconButton(
            onClick = {
                val newValue = (value + step).coerceAtMost(max)
                if (newValue != value) onValueChange(newValue)
            },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "增加",
                modifier = Modifier.size(20.dp),
                tint = if (value < max) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun SettingsItem(
    label: String,
    subLabel: String? = null,
    icon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    badge: @Composable (() -> Unit)? = null
) {
    val alpha = if (enabled) 1f else 0.5f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if ((onClick != null) && enabled) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f * alpha),
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                )
                if (badge != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    badge()
                }
            }
            if (subLabel != null) {
                Text(
                    text = subLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f * alpha)
                )
            }
        }
        if (trailing != null) {
            Box(modifier = Modifier.padding(start = 8.dp)) {
                CompositionLocalProvider(
                    LocalContentColor provides LocalContentColor.current.copy(
                        alpha = alpha
                    )
                ) {
                    trailing()
                }
            }
        }
    }
}

/**
 * 统一的可排序列表项组件。
 * 优化了拖拽手感和视觉反馈，移除突出的边框高亮，仅保留阴影提升效果。
 */
@Composable
fun SortableListItem(
    index: Int,
    itemCount: Int,
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDragEnd: (Int) -> Unit,
    onSwap: (Int, Int) -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    val shadowElevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elevation")
    var draggingOffset by remember { mutableFloatStateOf(0f) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(shadowElevation, RoundedCornerShape(16.dp))
            .zIndex(if (isDragging) 1f else 0f),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 内容区域
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                content()
            }

            // 统一的拖拽手柄
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(40.dp)
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { _ ->
                                draggingOffset = 0f
                                onDragStart()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                draggingOffset += dragAmount.y
                                val threshold = 50f 
                                if (draggingOffset > threshold && index < itemCount - 1) {
                                    onSwap(index, index + 1)
                                    draggingOffset = 0f
                                } else if (draggingOffset < -threshold && index > 0) {
                                    onSwap(index, index - 1)
                                    draggingOffset = 0f
                                }
                            },
                            onDragEnd = {
                                draggingOffset = 0f
                                onDragEnd(index)
                            },
                            onDragCancel = {
                                draggingOffset = 0f
                                onDragEnd(index)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.DragIndicator,
                    contentDescription = "拖动排序",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun ModelSelectorItem(
    label: String,
    icon: ImageVector,
    providers: List<ModelProvider>,
    selectedProviderId: String?,
    selectedModel: String?,
    onModelSelected: (String?, String?) -> Unit,
    type: String = "text", // "ocr", "text", "vision"
    onFetchModels: suspend (String) -> List<String> = { emptyList() },
    defaultProviderId: String? = null,
    badge: @Composable (() -> Unit)? = null
) {
    var showDialog by remember { mutableStateOf(value = false) }
    val scope = rememberCoroutineScope()

    val effectiveProviderId = selectedProviderId ?: defaultProviderId
    val selectedProvider = providers.find { it.id == effectiveProviderId }

    val isUsingDefault = selectedProviderId == null || selectedModel.isNullOrBlank()

    val subLabel = if (selectedProvider != null) {
        val modelName = if (isUsingDefault) {
            val baseModel = when (type) {
                "ocr" -> selectedProvider.defaultOcrModel.ifBlank { "未设置" }
                "vision" -> selectedProvider.defaultVisionModel.ifBlank { "未设置" }
                else -> selectedProvider.defaultTextModel.ifBlank { "未设置" }
            }
            "$baseModel (默认)"
        } else {
            selectedModel
        }
        "${selectedProvider.name}\n$modelName"
    } else {
        "未配置"
    }

    SettingsItem(
        label = label,
        subLabel = subLabel,
        icon = icon,
        onClick = { showDialog = true },
        badge = badge,
        trailing = {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    )

    if (showDialog) {
        var currentStep by remember { mutableIntStateOf(if (selectedProviderId == null) 0 else 1) }
        var tempProviderId by remember { mutableStateOf(selectedProviderId) }
        var searchQuery by remember { mutableStateOf("") }
        var showResetConfirm by remember { mutableStateOf(false) }

        if (showResetConfirm) {
            SolveXConfirmDialog(
                onDismissRequest = { showResetConfirm = false },
                onConfirm = {
                    showResetConfirm = false
                    showDialog = false
                    onModelSelected(null, null)
                },
                title = "重置为未配置",
                message = "确定要清除当前选择的提供方和模型吗？",
                confirmText = "重置",
                isDestructive = true
            )
        }

        SolveXDialog(
            onDismissRequest = { showDialog = false },
            title = if (currentStep == 0) "选择提供方" else "选择模型",
            dismissButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { showResetConfirm = true }) {
                        Text("重置为未配置", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { showDialog = false }) { Text("取消") }
                }
            }
        ) {
            Column(modifier = Modifier.heightIn(max = 400.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("搜索") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )

                if (currentStep == 0) {
                    val filteredProviders = providers.filter {
                        it.name.contains(searchQuery, ignoreCase = true)
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(filteredProviders) { provider ->
                            Surface(
                                onClick = {
                                    tempProviderId = provider.id
                                    currentStep = 1
                                    searchQuery = "" // 切换时清空搜索
                                },
                                shape = MaterialTheme.shapes.medium,
                                tonalElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(provider.name, modifier = Modifier.padding(16.dp))
                            }
                        }
                    }
                } else {
                    val provider = providers.find { it.id == tempProviderId }
                    if (provider != null) {
                        var isRefreshing by remember { mutableStateOf(false) }
                        val filteredModels = provider.availableModels.filter {
                            it.contains(searchQuery, ignoreCase = true)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {
                                currentStep = 0
                                searchQuery = ""
                            }) {
                                Text("返回选择提供方")
                            }

                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                IconButton(onClick = {
                                    scope.launch {
                                        isRefreshing = true
                                        onFetchModels(provider.id)
                                        isRefreshing = false
                                    }
                                }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "刷新模型")
                                }
                            }
                        }

                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            item {
                                val defaultModel = when (type) {
                                    "ocr" -> provider.defaultOcrModel
                                    "vision" -> provider.defaultVisionModel
                                    else -> provider.defaultTextModel
                                }.ifBlank { "未设置" }

                                Surface(
                                    onClick = {
                                        onModelSelected(tempProviderId, null)
                                        showDialog = false
                                    },
                                    shape = MaterialTheme.shapes.medium,
                                    tonalElevation = 2.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            "使用默认模型",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            defaultModel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            items(filteredModels) { model ->
                                Surface(
                                    onClick = {
                                        onModelSelected(tempProviderId, model)
                                        showDialog = false
                                    },
                                    shape = MaterialTheme.shapes.medium,
                                    tonalElevation = 1.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(model, modifier = Modifier.padding(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
