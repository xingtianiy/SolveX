package com.tianhuiu.solvex.ui.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tianhuiu.solvex.data.models.AssistantConfig
import com.tianhuiu.solvex.ui.MainViewModel
import java.util.UUID

/**
 * 助手编辑页面。
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
    var useStructuredExtraction by remember {
        mutableStateOf(existingAssistant?.useStructuredExtraction ?: true)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (assistantId == null) "新建助手" else "编辑助手", fontWeight = FontWeight.Bold) },
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
                                visionPrompt = visionPrompt,
                                useStructuredExtraction = useStructuredExtraction
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 基本信息分组
            EditSection(title = "基本信息", icon = Icons.Default.Assistant) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("助手名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("例如：题目解答助手") },
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // 核心提示词分组
            EditSection(title = "核心提示词 (Prompts)", icon = Icons.Default.Psychology) {
                PromptInputField(
                    value = ocrPrompt,
                    onValueChange = { ocrPrompt = it },
                    label = "OCR 提取提示词",
                    placeholder = "指导 AI 如何从提取的文本中整理题目内容"
                )
                
                Spacer(Modifier.height(8.dp))
                
                PromptInputField(
                    value = textPrompt,
                    onValueChange = { textPrompt = it },
                    label = "文本分析提示词",
                    placeholder = "定义 AI 如何解答纯文本题目"
                )

                Spacer(Modifier.height(8.dp))

                PromptInputField(
                    value = visionPrompt,
                    onValueChange = { visionPrompt = it },
                    label = "视觉分析提示词",
                    placeholder = "指导 AI 如何分析图片中的题目与图像细节"
                )
            }

            // 高级配置分组
            EditSection(title = "高级配置", icon = Icons.Default.ViewModule) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("强制结构化输出", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text("要求 AI 严格按特定的 Markdown 模块返回结果", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = useStructuredExtraction,
                            onCheckedChange = { useStructuredExtraction = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EditSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        content()
    }
}

@Composable
fun PromptInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        maxLines = 8,
        placeholder = { Text(placeholder) },
        shape = RoundedCornerShape(12.dp)
    )
}
