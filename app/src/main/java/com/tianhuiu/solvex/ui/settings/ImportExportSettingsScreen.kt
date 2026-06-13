package com.tianhuiu.solvex.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tianhuiu.solvex.ui.MainViewModel
import com.tianhuiu.solvex.ui.components.SettingsGroup
import com.tianhuiu.solvex.ui.components.SolveXConfirmDialog
import com.tianhuiu.solvex.ui.components.SolveXDialog
import com.tianhuiu.solvex.utils.SystemUtils
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportExportSettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var showResetDialog by remember { mutableStateOf(false) }
    var exportContent by remember { mutableStateOf("") }

    // Import confirmation state
    var showImportDialog by remember { mutableStateOf(false) }
    var pendingImportData by remember { mutableStateOf<com.tianhuiu.solvex.ui.ExportData?>(null) }

    // Selection state
    val selectedProviders = remember { mutableStateMapOf<String, Boolean>() }
    val includeApiKeyMap = remember { mutableStateMapOf<String, Boolean>() }
    val selectedAssistants = remember { mutableStateMapOf<String, Boolean>() }

    // Initialize selection state
    LaunchedEffect(viewModel.providers, viewModel.assistants) {
        viewModel.providers.forEach {
            if (it.id !in selectedProviders) selectedProviders[it.id] = true
            if (it.id !in includeApiKeyMap) includeApiKeyMap[it.id] = true
        }
        viewModel.assistants.forEach {
            if (it.id !in selectedAssistants) selectedAssistants[it.id] = true
        }
    }

    // Master Checkbox Logic
    val allProvidersSelected =
        viewModel.providers.isNotEmpty() && viewModel.providers.all { selectedProviders[it.id] == true }
    val allAssistantsSelected =
        viewModel.assistants.isNotEmpty() && viewModel.assistants.all { selectedAssistants[it.id] == true }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(exportContent.toByteArray())
                }
                viewModel.showFeedbackDialog(
                    title = "导出成功",
                    message = "配置已成功保存到文件",
                    icon = Icons.Default.CheckCircle
                )
            } catch (e: Exception) {
                viewModel.showFeedbackDialog(
                    title = "导出失败",
                    message = e.message ?: "未知错误",
                    icon = Icons.Default.Warning
                )
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val content = reader.readText()
                    val data = viewModel.decodeImportConfig(content)
                    if (data != null) {
                        pendingImportData = data
                        showImportDialog = true
                    } else {
                        SystemUtils.showFeedback(
                            context,
                            userMessage = "文件格式错误",
                            detailedLog = "Selected file is not a valid configuration format"
                        )
                    }
                }
            } catch (e: Exception) {
                SystemUtils.showFeedback(
                    context,
                    userMessage = "读取失败",
                    detailedLog = "Read failed: ${e.message}",
                    throwable = e
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("导入导出") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                windowInsets = WindowInsets(top = 0.dp)
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            exportContent = viewModel.exportConfig()
                            createDocumentLauncher.launch("SolveX_Backup_${System.currentTimeMillis()}.json")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("一键备份")
                    }
                    Button(
                        onClick = {
                            val providers =
                                viewModel.providers.filter { selectedProviders[it.id] == true }
                            val assistants =
                                viewModel.assistants.filter { selectedAssistants[it.id] == true }
                            exportContent =
                                viewModel.exportConfig(providers, assistants, includeApiKeyMap)
                            createDocumentLauncher.launch("SolveX_Export_${System.currentTimeMillis()}.json")
                        },
                        modifier = Modifier.weight(1f),
                        enabled = selectedProviders.values.any { it } || selectedAssistants.values.any { it }
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("导出选中")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SectionHeader(
                        title = "模型提供方",
                        count = viewModel.providers.size,
                        checked = allProvidersSelected,
                        onCheckedChange = { checked ->
                            viewModel.providers.forEach { selectedProviders[it.id] = checked }
                        }
                    )
                }
            }

            items(viewModel.providers) { provider ->
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    ProviderSelectionCard(
                        name = provider.name,
                        type = provider.type.displayName,
                        isSelected = selectedProviders[provider.id] ?: false,
                        onSelectedChange = { selectedProviders[provider.id] = it },
                        includeApiKey = includeApiKeyMap[provider.id] ?: false,
                        onIncludeApiKeyChange = { includeApiKeyMap[provider.id] = it }
                    )
                }
            }

            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SectionHeader(
                        title = "助手配置",
                        count = viewModel.assistants.size,
                        checked = allAssistantsSelected,
                        onCheckedChange = { checked ->
                            viewModel.assistants.forEach { selectedAssistants[it.id] = checked }
                        }
                    )
                }
            }

            items(viewModel.assistants) { assistant ->
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    AssistantSelectionCard(
                        name = assistant.name,
                        isSelected = selectedAssistants[assistant.id] ?: false,
                        onSelectedChange = { selectedAssistants[assistant.id] = it }
                    )
                }
            }

            item {
                SettingsGroup(title = "配置管理") {
                    ActionItem(
                        icon = Icons.Default.UploadFile,
                        label = "从文件导入配置",
                        subLabel = "选择 JSON 文件恢复配置",
                        onClick = { openDocumentLauncher.launch(arrayOf("application/json")) }
                    )
                    ActionItem(
                        icon = Icons.Default.Restore,
                        label = "重置到初始状态",
                        subLabel = "清空全部数据并恢复默认",
                        labelColor = MaterialTheme.colorScheme.error,
                        onClick = { showResetDialog = true }
                    )
                }
            }
        }
    }

    if (showResetDialog) {
        SolveXConfirmDialog(
            onDismissRequest = { showResetDialog = false },
            onConfirm = {
                viewModel.resetToDefault()
                showResetDialog = false
            },
            title = "确认重置",
            message = "确定要重置所有配置吗？此操作无法撤销。",
            confirmText = "重置",
            isDestructive = true,
            icon = Icons.Default.Restore
        )
    }

    if (showImportDialog && pendingImportData != null) {
        val data = pendingImportData!!

        SolveXDialog(
            onDismissRequest = {
                showImportDialog = false
                pendingImportData = null
            },
            icon = Icons.Default.UploadFile,
            title = "导入配置",
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingImportData?.let {
                            viewModel.importConfig(it)
                            viewModel.showFeedbackDialog(
                                title = "导入成功",
                                message = "配置已成功从文件导入",
                                icon = Icons.Default.CheckCircle
                            )
                        }
                        showImportDialog = false
                        pendingImportData = null
                    }
                ) { Text("导入", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImportDialog = false
                        pendingImportData = null
                    }
                ) { Text("取消") }
            }
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                Text(
                    text = "即将导入以下配置：",
                    style = MaterialTheme.typography.bodyMedium
                )

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Cloud,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Spacer(Modifier.width(12.dp))

                    Text(
                        text = "模型提供方",
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "${data.providers.size} 个",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Psychology,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Spacer(Modifier.width(12.dp))

                    Text(
                        text = "助手配置",
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "${data.assistants.size} 个",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                HorizontalDivider()

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "导入配置后，同名模型提供方和助手配置都将被覆盖，无法撤销，请核对后再进行导入操作。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    count: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$title (共 $count 个)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Text("全选", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun ProviderSelectionCard(
    name: String,
    type: String,
    isSelected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    includeApiKey: Boolean,
    onIncludeApiKeyChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isSelected, onCheckedChange = onSelectedChange)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        type,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("包含 API Key", style = MaterialTheme.typography.bodySmall)
                    Checkbox(
                        checked = includeApiKey,
                        onCheckedChange = onIncludeApiKeyChange,
                        enabled = isSelected
                    )
                }
            }
        }
    }
}

@Composable
fun AssistantSelectionCard(
    name: String,
    isSelected: Boolean,
    onSelectedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = isSelected, onCheckedChange = onSelectedChange)
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun ActionItem(
    icon: ImageVector,
    label: String,
    subLabel: String? = null,
    labelColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = androidx.compose.ui.graphics.Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (labelColor == MaterialTheme.colorScheme.onSurface) MaterialTheme.colorScheme.primary else labelColor
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = labelColor, style = MaterialTheme.typography.titleMedium)
                if (subLabel != null) {
                    Text(
                        text = subLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
