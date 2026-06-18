package com.tianhuiu.solvex.ui.settings

import android.content.ClipData
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.tianhuiu.solvex.data.models.ModelProvider
import com.tianhuiu.solvex.ui.ConnectivityTestState
import com.tianhuiu.solvex.ui.MainViewModel
import com.tianhuiu.solvex.ui.components.SolveXConfirmDialog
import com.tianhuiu.solvex.ui.components.SolveXDialog
import kotlinx.coroutines.launch
import java.util.Collections

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ModelSettingsScreen(
    viewModel: MainViewModel,
    onEditProvider: (String?) -> Unit,
    onBack: () -> Unit,
) {
    var providerToDelete by remember { mutableStateOf<ModelProvider?>(null) }
    val providersList = remember { mutableStateListOf<ModelProvider>() }

    LaunchedEffect(viewModel.providers) {
        providersList.clear()
        providersList.addAll(viewModel.providers)
    }

    val listState = rememberLazyListState()
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var draggingOffset by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()
    LocalContext.current

    val sheetState = rememberModalBottomSheetState()
    var showProviderSheet by remember { mutableStateOf(false) }

    if (showProviderSheet) {
        ProviderSelectionSheet(
            viewModel = viewModel,
            onDismissRequest = { showProviderSheet = false },
            sheetState = sheetState
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模型供应商", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                windowInsets = WindowInsets(top = 0.dp)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEditProvider(null) }) {
                Icon(Icons.Default.Add, contentDescription = "添加提供方")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 提示信息区域
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
                        "长按右侧图标拖动排序，决定默认优先尝试顺序",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 全局首选提供方选择器（独立卡片）
            Column(modifier = Modifier.padding(16.dp)) {
                val selectedProvider = viewModel.providers.find { it.id == viewModel.defaultProviderId }

                Text(
                    "全局首选提供方",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    onClick = { showProviderSheet = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Business,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                selectedProvider?.name ?: "未设置 (将仅使用手动指定的模型)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
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
                itemsIndexed(providersList, key = { _, item -> item.id }) { index, provider ->
                    val isDragging = draggedItemIndex == index
                    val shadowElevation by animateDpAsState(if (isDragging) 8.dp else 0.dp)
                    val testState = viewModel.connectivityTestStates[provider.id]
                    
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
                            // 连通性测试图标/状态
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        when (val result = viewModel.testConnectivity(provider)) {
                                            is ConnectivityTestState.Success ->
                                                viewModel.showFeedbackDialog(
                                                    title = "连接成功",
                                                    message = "${provider.name}: 连通成功 (${result.modelCount} 个模型)",
                                                    icon = Icons.Default.CheckCircle
                                                )
                                            is ConnectivityTestState.Failure ->
                                                viewModel.showFeedbackDialog(
                                                    title = "连接失败",
                                                    message = "${provider.name}: ${result.message}",
                                                    icon = Icons.Default.Error
                                                )
                                            else -> {}
                                        }
                                    }
                                }
                            ) {
                                when (testState) {
                                    is ConnectivityTestState.Testing ->
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    is ConnectivityTestState.Success ->
                                        Icon(Icons.Default.CheckCircle, "连接成功", tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                                    is ConnectivityTestState.Failure ->
                                        Icon(Icons.Default.Error, "连接失败", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                    else ->
                                        Icon(Icons.Default.Sync, "测试连通", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                }
                            }
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    provider.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    provider.type.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            IconButton(onClick = { onEditProvider(provider.id) }) {
                                Icon(Icons.Default.Edit, contentDescription = "编辑", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            
                            IconButton(onClick = { providerToDelete = provider }) {
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
                                                if (draggingOffset > threshold && index < providersList.size - 1) {
                                                    Collections.swap(providersList, index, index + 1)
                                                    draggedItemIndex = index + 1
                                                    draggingOffset = 0f
                                                } else if (draggingOffset < -threshold && index > 0) {
                                                    Collections.swap(providersList, index, index - 1)
                                                    draggedItemIndex = index - 1
                                                    draggingOffset = 0f
                                                }
                                            },
                                            onDragEnd = {
                                                draggedItemIndex = null
                                                draggingOffset = 0f
                                                viewModel.updateProviders(providersList.toList())
                                            },
                                            onDragCancel = {
                                                draggedItemIndex = null
                                                draggingOffset = 0f
                                                viewModel.updateProviders(providersList.toList())
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

    // 删除确认弹窗
    if (providerToDelete != null) {
        SolveXConfirmDialog(
            onDismissRequest = { providerToDelete = null },
            onConfirm = {
                providerToDelete?.let { viewModel.deleteProvider(it.id) }
                providerToDelete = null
            },
            title = "确认删除",
            message = "确定要删除提供方 \"${providerToDelete?.name}\" 吗？",
            confirmText = "删除",
            isDestructive = true,
            icon = Icons.Default.Delete
        )
    }
}

/**
 * 全局提供方选择底栏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSelectionSheet(
    viewModel: MainViewModel,
    onDismissRequest: () -> Unit,
    sheetState: SheetState
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Column(
                modifier = Modifier.padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier
                        .width(32.dp)
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(2.dp)
                ) {}
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "选择默认提供方",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "未手动指定模型的场景将优先尝试该提供方",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(Modifier.height(16.dp))
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                // 选项：不设置
                item {
                    val isNoneSelected = viewModel.defaultProviderId == null
                    Surface(
                        onClick = {
                            viewModel.updateDefaultProviderId(null)
                            onDismissRequest()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isNoneSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                                else Color.Transparent,
                        border = if (isNoneSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isNoneSelected) MaterialTheme.colorScheme.primary 
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Block,
                                        null,
                                        modifier = Modifier.size(20.dp),
                                        tint = if (isNoneSelected) MaterialTheme.colorScheme.onPrimary 
                                               else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "不设置默认提供方",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "将仅使用手动指定的模型",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            RadioButton(
                                selected = isNoneSelected,
                                onClick = null
                            )
                        }
                    }
                }

                // 提供方列表
                items(viewModel.providers.size) { index ->
                    val provider = viewModel.providers[index]
                    val isSelected = provider.id == viewModel.defaultProviderId
                    
                    Surface(
                        onClick = {
                            viewModel.updateDefaultProviderId(provider.id)
                            onDismissRequest()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                                else Color.Transparent,
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary 
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Business,
                                        null,
                                        modifier = Modifier.size(20.dp),
                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary 
                                               else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    provider.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    provider.type.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = null
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)
@Composable
fun ModelPreviewDialog(
    provider: ModelProvider,
    isFetching: Boolean,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val filteredModels = remember(searchQuery, provider.availableModels) {
        if (searchQuery.isBlank()) {
            provider.availableModels
        } else {
            provider.availableModels.filter { it.contains(searchQuery, ignoreCase = true) }
        }
    }

    SolveXDialog(
        onDismissRequest = onDismiss,
        title = "可用模型 - ${provider.name}",
        centerTitle = false,
        trailingTitleAction = {
            if (isFetching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", fontWeight = FontWeight.Bold) }
        }
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("搜索模型") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )

                if (isFetching && provider.availableModels.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (filteredModels.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (isFetching) "正在加载..." else "无可用模型，请点击刷新图标",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredModels) { model ->
                            Surface(
                                tonalElevation = 1.dp,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            scope.launch {
                                                clipboard.setClipEntry(
                                                    ClipEntry(
                                                        ClipData.newPlainText(
                                                            "model",
                                                            model
                                                        )
                                                    )
                                                )
                                                com.tianhuiu.solvex.utils.SystemUtils.showToast(
                                                    context,
                                                    "已复制: $model"
                                                )
                                            }
                                        },
                                        onLongClick = {
                                            scope.launch {
                                                clipboard.setClipEntry(
                                                    ClipEntry(
                                                        ClipData.newPlainText(
                                                            "model",
                                                            model
                                                        )
                                                    )
                                                )
                                                com.tianhuiu.solvex.utils.SystemUtils.showToast(
                                                    context,
                                                    "已复制: $model"
                                                )
                                            }
                                        }
                                    )
                            ) {
                                Text(
                                    text = model,
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
