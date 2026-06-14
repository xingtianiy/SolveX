package com.tianhuiu.solvex.ui.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.tianhuiu.solvex.data.models.AssistantConfig
import com.tianhuiu.solvex.ui.MainViewModel
import com.tianhuiu.solvex.ui.components.SolveXConfirmDialog
import java.util.Collections

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AssistantSettingsScreen(
    viewModel: MainViewModel,
    onEditAssistant: (String?) -> Unit,
    onBack: () -> Unit
) {
    var assistantIdToDelete by remember { mutableStateOf<String?>(null) }
    val assistantsList = remember { mutableStateListOf<AssistantConfig>() }

    // 同步 ViewModel 的助手列表
    LaunchedEffect(viewModel.assistants) {
        assistantsList.clear()
        assistantsList.addAll(viewModel.assistants)
    }

    val listState = rememberLazyListState()
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var draggingOffset by remember { mutableStateOf(0f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("助手管理", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                windowInsets = WindowInsets(top = 0.dp)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEditAssistant(null) }) {
                Icon(Icons.Default.Add, contentDescription = "添加助手")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "长按右侧图标可拖动排序，排序决定首页抽屉展示顺序",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                itemsIndexed(assistantsList, key = { _, item -> item.id }) { index, assistant ->
                    val isDragging = draggedItemIndex == index
                    val shadowElevation by animateDpAsState(if (isDragging) 8.dp else 0.dp)
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(shadowElevation, RoundedCornerShape(12.dp))
                            .zIndex(if (isDragging) 1f else 0f),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDragging) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isDragging) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                                Text(
                                    assistant.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "个性化智能助手",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            IconButton(onClick = { onEditAssistant(assistant.id) }) {
                                Icon(Icons.Default.Edit, contentDescription = "编辑", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            
                            IconButton(onClick = { assistantIdToDelete = assistant.id }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                            }
                            
                            Icon(
                                Icons.Default.DragIndicator,
                                contentDescription = "拖动排序",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .pointerInput(Unit) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { _ ->
                                                draggedItemIndex = index
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                draggingOffset += dragAmount.y
                                                
                                                val threshold = 60f // 增加阈值提高稳定性
                                                if (draggingOffset > threshold && index < assistantsList.size - 1) {
                                                    Collections.swap(assistantsList, index, index + 1)
                                                    draggedItemIndex = index + 1
                                                    draggingOffset = 0f
                                                } else if (draggingOffset < -threshold && index > 0) {
                                                    Collections.swap(assistantsList, index, index - 1)
                                                    draggedItemIndex = index - 1
                                                    draggingOffset = 0f
                                                }
                                            },
                                            onDragEnd = {
                                                draggedItemIndex = null
                                                draggingOffset = 0f
                                                viewModel.updateAssistants(assistantsList.toList())
                                            },
                                            onDragCancel = {
                                                draggedItemIndex = null
                                                draggingOffset = 0f
                                                viewModel.updateAssistants(assistantsList.toList())
                                            }
                                        )
                                    }
                            )
                        }
                    }
                }
            }
        }
    }

    if (assistantIdToDelete != null) {
        val assistant = viewModel.assistants.find { it.id == assistantIdToDelete }
        SolveXConfirmDialog(
            onDismissRequest = { assistantIdToDelete = null },
            onConfirm = {
                assistantIdToDelete?.let { viewModel.deleteAssistant(it) }
                assistantIdToDelete = null
            },
            title = "确认删除",
            message = "确定要删除助手 \"${assistant?.name}\" 吗？",
            confirmText = "删除",
            isDestructive = true,
            icon = Icons.Default.Delete
        )
    }
}
