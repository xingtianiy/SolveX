package com.tianhuiu.solvex.ui.settings

import android.content.ClipData
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tianhuiu.solvex.data.models.ModelProvider
import com.tianhuiu.solvex.ui.ConnectivityTestState
import com.tianhuiu.solvex.ui.MainViewModel
import com.tianhuiu.solvex.ui.components.SettingsGroup
import com.tianhuiu.solvex.ui.components.SettingsItem
import com.tianhuiu.solvex.ui.components.SolveXConfirmDialog
import com.tianhuiu.solvex.ui.components.SolveXDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSettingsScreen(
    viewModel: MainViewModel,
    onEditAssistant: (String?) -> Unit,
    onEditProvider: (String?) -> Unit,
    onBack: () -> Unit,
) {
    var providerToDelete by remember { mutableStateOf<ModelProvider?>(null) }
    var assistantIdToDelete by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模型设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                windowInsets = WindowInsets(top = 0.dp)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                SettingsGroup(title = "提供方配置") {
                    var expanded by remember { mutableStateOf(false) }
                    val selectedProvider =
                        viewModel.providers.find { it.id == viewModel.defaultProviderId }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = true }
                            .padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Business,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("全局首选提供方", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    selectedProvider?.name ?: "未设置 (将仅使用手动指定的模型)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }

                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(
                                text = { Text("不设置默认提供方") },
                                onClick = {
                                    viewModel.updateDefaultProviderId(null)
                                    expanded = false
                                }
                            )
                            viewModel.providers.forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider.name) },
                                    onClick = {
                                        viewModel.updateDefaultProviderId(provider.id)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    viewModel.providers.forEach { provider ->
                        val testState = viewModel.connectivityTestStates[provider.id]
                        SettingsItem(
                            label = provider.name,
                            subLabel = provider.type.displayName,
                            trailing = {
                                Row {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                val result = viewModel.testConnectivity(provider)
                                                when (result) {
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
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(
                                                        20.dp
                                                    ), strokeWidth = 2.dp
                                                )

                                            else ->
                                                Icon(
                                                    Icons.Default.Sync,
                                                    contentDescription = "测试连通"
                                                )
                                        }
                                    }
                                    IconButton(onClick = { onEditProvider(provider.id) }) {
                                        Icon(Icons.Default.Edit, contentDescription = "编辑")
                                    }
                                    IconButton(onClick = { providerToDelete = provider }) {
                                        Icon(Icons.Default.Delete, contentDescription = "删除")
                                    }
                                }
                            }
                        )
                    }
                    TextButton(
                        onClick = { onEditProvider(null) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("添加提供方")
                    }
                }
            }

            item {
                SettingsGroup(title = "助手配置") {
                    viewModel.assistants.forEach { assistant ->
                        SettingsItem(
                            label = assistant.name,
                            trailing = {
                                Row {
                                    IconButton(onClick = { onEditAssistant(assistant.id) }) {
                                        Icon(Icons.Default.Edit, contentDescription = "编辑")
                                    }
                                    IconButton(onClick = { assistantIdToDelete = assistant.id }) {
                                        Icon(Icons.Default.Delete, contentDescription = "删除")
                                    }
                                }
                            }
                        )
                    }
                    TextButton(
                        onClick = { onEditAssistant(null) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("添加助手")
                    }
                }
            }
        }
    }

    // 连通性测试结果通过 Snackbar 展示，无需额外弹窗

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

@OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
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
