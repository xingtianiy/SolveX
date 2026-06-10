package com.tianhuiu.solvex.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tianhuiu.solvex.data.models.AssistantConfig
import com.tianhuiu.solvex.ui.MainViewModel
import java.util.UUID

/**
 * 助手编辑页面：用于创建或修改智能助手的角色设定（Prompt）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantEditScreen(
    assistantId: String?,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val existingAssistant = remember(assistantId) {
        viewModel.assistants.find { it.id == assistantId }
    }

    var name by remember { mutableStateOf(existingAssistant?.name ?: "") }
    var ocrPrompt by remember { mutableStateOf(existingAssistant?.ocrPrompt ?: "") }
    var textPrompt by remember { mutableStateOf(existingAssistant?.textPrompt ?: "") }
    var visionPrompt by remember { mutableStateOf(existingAssistant?.visionPrompt ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (assistantId == null) "添加助手" else "编辑助手") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val assistant = AssistantConfig(
                                id = assistantId ?: UUID.randomUUID().toString(),
                                name = name,
                                ocrPrompt = ocrPrompt,
                                textPrompt = textPrompt,
                                visionPrompt = visionPrompt
                            )
                            if (assistantId == null) {
                                viewModel.addAssistant(assistant)
                            } else {
                                viewModel.updateAssistant(assistant)
                            }
                            onBack()
                        },
                        enabled = name.isNotBlank()
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
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("助手名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = ocrPrompt,
                onValueChange = { ocrPrompt = it },
                label = { Text("OCR 提示词") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5
            )

            OutlinedTextField(
                value = textPrompt,
                onValueChange = { textPrompt = it },
                label = { Text("文本解析提示词") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5
            )

            OutlinedTextField(
                value = visionPrompt,
                onValueChange = { visionPrompt = it },
                label = { Text("多模态视觉提示词") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5
            )
        }
    }
}
