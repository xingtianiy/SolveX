package com.tianhuiu.solvex.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tianhuiu.solvex.data.models.ModelProvider
import com.tianhuiu.solvex.data.models.ProviderKind
import com.tianhuiu.solvex.ui.MainViewModel
import com.tianhuiu.solvex.ui.components.SolveXConfirmDialog
import com.tianhuiu.solvex.ui.components.SolveXDialog
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 提供方编辑页面：用于创建或修改模型提供方的详细配置。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderEditScreen(
    providerId: String?,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val existingProvider = remember(providerId) {
        viewModel.providers.find { it.id == providerId }
    }

    val types = ProviderKind.entries.toTypedArray()
    var type by remember { mutableStateOf(existingProvider?.type ?: types[0]) }
    var name by remember { mutableStateOf(existingProvider?.name ?: "") }
    var url by remember { mutableStateOf(existingProvider?.url ?: "") }
    var apiKey by remember { mutableStateOf(existingProvider?.apiKey ?: "") }
    var defaultOcrModel by remember { mutableStateOf(existingProvider?.defaultOcrModel ?: "") }
    var defaultTextModel by remember { mutableStateOf(existingProvider?.defaultTextModel ?: "") }
    var defaultVisionModel by remember {
        mutableStateOf(
            existingProvider?.defaultVisionModel ?: ""
        )
    }

    var expanded by remember { mutableStateOf(false) }
    var apiKeyVisible by remember { mutableStateOf(false) }

    // 用于预览模型列表的弹窗状态
    var showPreviewDialog by remember { mutableStateOf(false) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    // 预览模式下的模型列表和加载状态（支持未保存前测试获取）
    var previewModels by remember {
        mutableStateOf(
            existingProvider?.availableModels ?: emptyList()
        )
    }
    var isPreviewFetching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val currentProviderState = remember(
        existingProvider,
        type,
        name,
        url,
        apiKey,
        defaultOcrModel,
        defaultTextModel,
        defaultVisionModel,
        previewModels
    ) {
        ModelProvider(
            id = existingProvider?.id ?: UUID.randomUUID().toString(),
            type = type,
            name = name,
            url = url,
            apiKey = apiKey,
            availableModels = previewModels.ifEmpty {
                existingProvider?.availableModels ?: emptyList()
            },
            defaultOcrModel = defaultOcrModel,
            defaultTextModel = defaultTextModel,
            defaultVisionModel = defaultVisionModel
        )
    }

    val hasUnsavedChanges = remember(existingProvider, currentProviderState) {
        if (existingProvider == null) {
            // 对于新添加的情况，如果任何字段不为空则视为有修改
            name.isNotBlank() || url.isNotBlank() || apiKey.isNotBlank() ||
                    defaultOcrModel.isNotBlank() || defaultTextModel.isNotBlank() || defaultVisionModel.isNotBlank()
        } else {
            // 对于编辑情况，对比当前状态与原始状态
            currentProviderState.copy(availableModels = existingProvider.availableModels) != existingProvider
        }
    }

    val attemptBack = {
        if (hasUnsavedChanges) {
            showExitConfirmation = true
        } else {
            onBack()
        }
    }

    BackHandler(enabled = true) {
        attemptBack()
    }

    val urlPreview = remember(type, url) {
        val baseUrl = url.ifBlank { "需输入链接" }
        when (type) {
            ProviderKind.OPENAI_COMPATIBLE -> "$baseUrl/chat/completions"
            ProviderKind.OPENAI_RESPONSES -> "$baseUrl/responses"
            ProviderKind.ANTHROPIC -> "$baseUrl/messages"
            ProviderKind.GOOGLE -> "$baseUrl/models"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (providerId == null) "添加提供方" else "编辑提供方") },
                navigationIcon = {
                    IconButton(onClick = attemptBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (providerId == null) {
                                viewModel.addProvider(currentProviderState)
                            } else {
                                viewModel.updateProvider(currentProviderState)
                            }
                            onBack()
                        },
                        enabled = name.isNotBlank() && url.isNotBlank()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "保存")
                    }
                },
                windowInsets = WindowInsets(top = 0.dp)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- 基础配置 ---
            Text(
                "基础配置",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = type.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("服务商类型") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    modifier = Modifier
                        .menuAnchor(
                            type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                            enabled = true
                        )
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    types.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption.displayName) },
                            onClick = {
                                type = selectionOption
                                expanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("提供方名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("例如: 我的 OpenAI") }
            )

            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("API 基础链接 (Base URL)") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://api.openai.com/v1") }
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "接口预览: $urlPreview",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                        Icon(
                            if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (apiKeyVisible) "隐藏" else "显示"
                        )
                    }
                },
                singleLine = true
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // --- 模型配置 ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "模型配置",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                TextButton(
                    onClick = {
                        // 先确保本地状态是最新的，虽然 preview 会带入 provider 对象
                        showPreviewDialog = true
                    },
                    enabled = url.isNotBlank()
                ) {
                    Icon(
                        Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("获取模型列表")
                }
            }

            // 可用模型（来自拉取结果或已保存数据）
            val availableModels =
                previewModels.ifEmpty { existingProvider?.availableModels ?: emptyList() }

            // 模型搜索弹窗状态：null 表示关闭，"ocr"/"text"/"vision" 表示目标字段
            var modelPickerTarget by remember { mutableStateOf<String?>(null) }

            // 默认 OCR 模型
            OutlinedTextField(
                value = defaultOcrModel,
                onValueChange = { defaultOcrModel = it },
                label = { Text("默认 OCR 模型") },
                placeholder = { Text("例如: gpt-4o") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = {
                        if (availableModels.isEmpty()) {
                            com.tianhuiu.solvex.utils.NotificationUtils.showToast(
                                context,
                                "请先获取模型列表"
                            )
                        } else {
                            modelPickerTarget = "ocr"
                        }
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索模型")
                    }
                }
            )

            // 默认文本模型
            OutlinedTextField(
                value = defaultTextModel,
                onValueChange = { defaultTextModel = it },
                label = { Text("默认文本模型 (常规问答)") },
                placeholder = { Text("例如: gpt-4o") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = {
                        if (availableModels.isEmpty()) {
                            com.tianhuiu.solvex.utils.NotificationUtils.showToast(
                                context,
                                "请先获取模型列表"
                            )
                        } else {
                            modelPickerTarget = "text"
                        }
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索模型")
                    }
                }
            )

            // 默认视觉模型
            OutlinedTextField(
                value = defaultVisionModel,
                onValueChange = { defaultVisionModel = it },
                label = { Text("默认多模态模型 (视觉分析)") },
                placeholder = { Text("例如: gpt-4o") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = {
                        if (availableModels.isEmpty()) {
                            com.tianhuiu.solvex.utils.NotificationUtils.showToast(
                                context,
                                "请先获取模型列表"
                            )
                        } else {
                            modelPickerTarget = "vision"
                        }
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索模型")
                    }
                }
            )

            // 模型搜索弹窗
            if (modelPickerTarget != null) {
                val target = modelPickerTarget!!
                ModelSearchDialog(
                    title = when (target) {
                        "ocr" -> "选择 OCR 模型"
                        "text" -> "选择文本模型"
                        else -> "选择视觉模型"
                    },
                    models = availableModels,
                    currentValue = when (target) {
                        "ocr" -> defaultOcrModel
                        "text" -> defaultTextModel
                        else -> defaultVisionModel
                    },
                    isFetching = isPreviewFetching,
                    onRefresh = {
                        scope.launch {
                            isPreviewFetching = true
                            val models = viewModel.fetchModelsForProvider(currentProviderState)
                            previewModels = models
                            isPreviewFetching = false
                        }
                    },
                    onModelSelected = { model ->
                        when (target) {
                            "ocr" -> defaultOcrModel = model
                            "text" -> defaultTextModel = model
                            else -> defaultVisionModel = model
                        }
                        modelPickerTarget = null
                    },
                    onDismiss = { modelPickerTarget = null }
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showPreviewDialog) {
        ModelPreviewDialog(
            provider = currentProviderState.copy(availableModels = previewModels),
            isFetching = isPreviewFetching,
            onRefresh = {
                scope.launch {
                    isPreviewFetching = true
                    val models = viewModel.fetchModelsForProvider(currentProviderState)
                    previewModels = models
                    // 模型列表获取成功后自动推荐并填充默认模型
                    if (models.isNotEmpty()) {
                        val filled = mutableListOf<String>()

                        if (defaultOcrModel.isBlank()) {
                            val rec = recommendOcrModel(models, currentProviderState.type)
                            if (rec != null) {
                                defaultOcrModel = rec; filled.add("OCR=$rec")
                            }
                        }
                        if (defaultTextModel.isBlank()) {
                            val rec = recommendTextModel(models, currentProviderState.type)
                            if (rec != null) {
                                defaultTextModel = rec; filled.add("文本=$rec")
                            }
                        }
                        if (defaultVisionModel.isBlank()) {
                            val rec = recommendVisionModel(models, currentProviderState.type)
                            if (rec != null) {
                                defaultVisionModel = rec; filled.add("视觉=$rec")
                            }
                        }

                        if (filled.isNotEmpty()) {
                            com.tianhuiu.solvex.utils.NotificationUtils.showFeedback(
                                context,
                                userMessage = "已填充默认模型",
                                detailedLog = "填充内容: ${filled.joinToString(", ")}",
                                priority = android.util.Log.INFO
                            )
                        }
                    }
                    isPreviewFetching = false
                }
            },
            onDismiss = { showPreviewDialog = false }
        )
    }

    if (showExitConfirmation) {
        SolveXConfirmDialog(
            onDismissRequest = { showExitConfirmation = false },
            onConfirm = {
                showExitConfirmation = false
                onBack()
            },
            title = "放弃修改？",
            message = "您有未保存的更改，确定要退出吗？",
            confirmText = "退出",
            dismissText = "取消",
            isDestructive = true,
            icon = Icons.Default.Warning
        )
    }
}

/**
 * 模型搜索弹窗：在已拉取的模型列表中搜索并选择。
 */
@Composable
private fun ModelSearchDialog(
    title: String,
    models: List<String>,
    currentValue: String,
    isFetching: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    onModelSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredModels = remember(searchQuery, models) {
        if (searchQuery.isBlank()) models
        else models.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    SolveXDialog(
        onDismissRequest = onDismiss,
        title = title,
        centerTitle = false,
        trailingTitleAction = if (onRefresh != null) {
            {
                if (isFetching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新模型列表")
                    }
                }
            }
        } else null,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        content = {
            Column(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("输入关键词筛选") },
                    placeholder = { Text("搜索模型名称...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (filteredModels.isEmpty()) {
                    Text(
                        "无匹配模型",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredModels) { model ->
                            val isSelected = model == currentValue
                            Surface(
                                onClick = { onModelSelected(model) },
                                shape = MaterialTheme.shapes.medium,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surface,
                                tonalElevation = if (isSelected) 2.dp else 0.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = 16.dp,
                                        vertical = 12.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = model,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (isSelected) {
                                        Text(
                                            "当前",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

/**
 * 从模型列表中推荐 OCR 模型。
 * 优先匹配含 ocr 关键词的模型，其次为通用多模态模型。
 */
private fun recommendOcrModel(models: List<String>, kind: ProviderKind): String? {
    // 精确匹配 OCR 专用模型
    models.firstOrNull { it.contains("ocr", ignoreCase = true) }?.let { return it }
    // Anthropic/Google 系无专用 OCR，使用视觉模型替代
    return recommendVisionModel(models, kind)
}

/**
 * 从模型列表中推荐文本模型。
 * 优先主流对话模型，排除视觉/OCR/embedding 等专用模型。
 */
private fun recommendTextModel(models: List<String>, kind: ProviderKind): String? {
    val excluded = setOf(
        "vision", "ocr", "embedding", "whisper", "tts", "dall-e", "moderation",
        "audio", "speech", "image", "video", "screenshot", "image_generation", "realtime"
    )
    val candidates = models.filter { m ->
        excluded.none { m.contains(it, ignoreCase = true) }
    }.ifEmpty { return models.firstOrNull() }

    return when (kind) {
        ProviderKind.ANTHROPIC ->
            candidates.firstOrNull { it.contains("opus", ignoreCase = true) }
                ?: candidates.firstOrNull { it.contains("sonnet", ignoreCase = true) }
                ?: candidates.firstOrNull { it.contains("haiku", ignoreCase = true) }
                ?: candidates.firstOrNull()

        ProviderKind.GOOGLE ->
            candidates.firstOrNull {
                it.contains("gemini") && it.contains(
                    "pro",
                    ignoreCase = true
                )
            }
                ?: candidates.firstOrNull {
                    it.contains("gemini") && it.contains(
                        "flash",
                        ignoreCase = true
                    )
                }
                ?: candidates.firstOrNull { it.contains("gemini", ignoreCase = true) }
                ?: candidates.firstOrNull()

        else ->
            candidates.firstOrNull { m ->
                (m.contains("gpt", ignoreCase = true) || m.contains("o1", ignoreCase = true) ||
                        m.contains("o3", ignoreCase = true) || m.contains(
                    "o4",
                    ignoreCase = true
                )) &&
                        !m.contains("mini", ignoreCase = true)
            }
                ?: candidates.firstOrNull {
                    it.contains(
                        "gpt",
                        ignoreCase = true
                    ) || it.contains("deepseek", ignoreCase = true) || it.contains(
                        "qwen",
                        ignoreCase = true
                    )
                }
                ?: candidates.firstOrNull()
    }
}

/**
 * 从模型列表中推荐视觉/多模态模型。
 * 优先含 vision 关键词，其次为已知的多模态模型。
 */
private fun recommendVisionModel(models: List<String>, kind: ProviderKind): String? {
    models.firstOrNull { it.contains("vision", ignoreCase = true) }?.let { return it }
    return when (kind) {
        ProviderKind.ANTHROPIC ->
            models.firstOrNull {
                it == "claude-opus-4-7" || it.contains(
                    "sonnet",
                    ignoreCase = true
                )
            }
                ?: models.firstOrNull { it.contains("claude", ignoreCase = true) }

        ProviderKind.GOOGLE ->
            models.firstOrNull {
                it.contains("gemini") && it.contains(
                    "pro",
                    ignoreCase = true
                ) && it.contains("vision", ignoreCase = true)
            }
                ?: models.firstOrNull {
                    it.contains("gemini") && (it.contains(
                        "pro",
                        ignoreCase = true
                    ) || it.contains("flash", ignoreCase = true))
                }
                ?: models.firstOrNull { it.contains("gemini", ignoreCase = true) }

        else ->
            models.firstOrNull { it == "gpt-4o" || it == "gpt-4-turbo" }
                ?: models.firstOrNull { it.contains("gpt-4", ignoreCase = true) }
                ?: models.firstOrNull { it.contains("gemini", ignoreCase = true) }
    }
}
