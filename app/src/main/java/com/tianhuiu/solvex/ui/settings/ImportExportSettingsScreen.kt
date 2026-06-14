package com.tianhuiu.solvex.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
                title = { Text("导入导出", fontWeight = FontWeight.Bold) },
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
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 顶部操作区（备份/导入）
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ActionCard(
                            icon = Icons.Default.Backup,
                            label = "全量备份",
                            description = "备份所有配置到文件",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                exportContent = viewModel.exportConfig()
                                createDocumentLauncher.launch("SolveX_Backup_${System.currentTimeMillis()}.json")
                            }
                        )
                        ActionCard(
                            icon = Icons.Default.UploadFile,
                            label = "导入配置",
                            description = "从 JSON 文件恢复",
                            modifier = Modifier.weight(1f),
                            onClick = { openDocumentLauncher.launch(arrayOf("application/json")) }
                        )
                    }
                }
            }

            // 2. 导出选中项控制区
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        "分项导出",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
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
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = selectedProviders.values.any { it } || selectedAssistants.values.any { it }
                    ) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("导出选中项")
                    }
                }
            }

            // 3. 模型提供方选择
            item {
                SectionHeader(
                    title = "模型提供方",
                    count = viewModel.providers.size,
                    checked = allProvidersSelected,
                    onCheckedChange = { checked ->
                        viewModel.providers.forEach { selectedProviders[it.id] = checked }
                    },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            items(viewModel.providers) { provider ->
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SelectionItemCard(
                        title = provider.name,
                        subtitle = provider.type.displayName,
                        isSelected = selectedProviders[provider.id] ?: false,
                        onSelectedChange = { selectedProviders[provider.id] = it },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("包含密钥", style = MaterialTheme.typography.labelSmall)
                                Checkbox(
                                    checked = includeApiKeyMap[provider.id] ?: false,
                                    onCheckedChange = { includeApiKeyMap[provider.id] = it },
                                    enabled = selectedProviders[provider.id] ?: false
                                )
                            }
                        }
                    )
                }
            }

            // 4. 助手配置选择
            item {
                SectionHeader(
                    title = "助手配置",
                    count = viewModel.assistants.size,
                    checked = allAssistantsSelected,
                    onCheckedChange = { checked ->
                        viewModel.assistants.forEach { selectedAssistants[it.id] = checked }
                    },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            items(viewModel.assistants) { assistant ->
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SelectionItemCard(
                        title = assistant.name,
                        subtitle = "助手身份配置",
                        isSelected = selectedAssistants[assistant.id] ?: false,
                        onSelectedChange = { selectedAssistants[assistant.id] = it }
                    )
                }
            }

            // 5. 危险操作
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 16.dp)) {
                    Text(
                        "危险区域",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        onClick = { showResetDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("重置所有配置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                                Text("清空全部数据并恢复到出厂默认状态", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
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
                ImportDetailRow(Icons.Default.Cloud, "模型提供方", "${data.providers.size} 个")
                ImportDetailRow(Icons.Default.Psychology, "助手配置", "${data.assistants.size} 个")
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
                            null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "导入配置后，同名配置将被覆盖，且无法撤销。请核对后再进行导入。",
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
fun ImportDetailRow(icon: ImageVector, label: String, count: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text = label, modifier = Modifier.weight(1f))
        Text(text = count, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ActionCard(
    icon: ImageVector,
    label: String,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    count: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$title ($count)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onCheckedChange(!checked) }
        ) {
            Text("全选", style = MaterialTheme.typography.labelSmall)
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun SelectionItemCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Surface(
        onClick = { onSelectedChange(!isSelected) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f) 
                else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp, 
            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onSelectedChange
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing?.invoke()
        }
    }
}
