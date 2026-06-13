package com.tianhuiu.solvex.ui.settings

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewSidebar
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tianhuiu.solvex.mode.ModeRegistry
import com.tianhuiu.solvex.ui.MainViewModel
import com.tianhuiu.solvex.ui.components.ModelSelectorItem
import com.tianhuiu.solvex.ui.components.SettingsGroup
import com.tianhuiu.solvex.ui.components.SettingsItem

// 统一模式设置页面
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSettingsScreen(
    modeId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val mode = ModeRegistry.get(modeId)
    val config = viewModel.currentModeConfig

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${mode.displayName}配置") },
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
                SettingsGroup(title = "模型配置") {
                    ModelSelectorItem(
                        label = "OCR 模型",
                        icon = Icons.Default.TextFields,
                        providers = viewModel.providers,
                        selectedProviderId = config.ocrProviderId,
                        selectedModel = config.ocrModel,
                        type = "ocr",
                        onFetchModels = { viewModel.fetchModelsDirect(it) },
                        onModelSelected = { pid, model ->
                            viewModel.updateModeConfig(
                                config.copy(ocrProviderId = pid, ocrModel = model)
                            )
                        },
                        defaultProviderId = viewModel.defaultProviderId
                    )
                    ModelSelectorItem(
                        label = "文本分析模型",
                        icon = Icons.Default.Description,
                        providers = viewModel.providers,
                        selectedProviderId = config.textProviderId,
                        selectedModel = config.textModel,
                        type = "text",
                        onFetchModels = { viewModel.fetchModelsDirect(it) },
                        onModelSelected = { pid, model ->
                            viewModel.updateModeConfig(
                                config.copy(textProviderId = pid, textModel = model)
                            )
                        },
                        defaultProviderId = viewModel.defaultProviderId
                    )
                    ModelSelectorItem(
                        label = "多模态模型",
                        icon = Icons.Default.AutoAwesome,
                        providers = viewModel.providers,
                        selectedProviderId = config.visionProviderId,
                        selectedModel = config.visionModel,
                        type = "vision",
                        onFetchModels = { viewModel.fetchModelsDirect(it) },
                        onModelSelected = { pid, model ->
                            viewModel.updateModeConfig(
                                config.copy(visionProviderId = pid, visionModel = model)
                            )
                        },
                        defaultProviderId = viewModel.defaultProviderId
                    )
                }
            }

            item {
                SettingsGroup(title = "性能设置") {
                    SettingsItem(
                        label = "响应超时 (秒)",
                        subLabel = "等待首个字符返回的最长时间",
                        icon = Icons.Default.Timer,
                        trailing = {
                            var text by remember(config.firstDeltaTimeoutSeconds) {
                                mutableStateOf(config.firstDeltaTimeoutSeconds.toString())
                            }
                            OutlinedTextField(
                                value = text,
                                onValueChange = {
                                    text = it
                                    it.toLongOrNull()?.let { seconds ->
                                        viewModel.updateModeConfig(
                                            config.copy(firstDeltaTimeoutSeconds = seconds)
                                        )
                                    }
                                },
                                modifier = Modifier.width(80.dp),
                                singleLine = true
                            )
                        }
                    )
                }
            }

            item {
                SettingsGroup(title = "通知与显示") {
                    val isNotificationEnabled = viewModel.isNotificationPermissionGranted

                    SettingsItem(
                        label = "启用通知",
                        subLabel = if (isNotificationEnabled) "识别完成后发送系统通知" else "系统通知权限已禁用，请在设置中开启",
                        icon = Icons.Default.Notifications,
                        enabled = isNotificationEnabled,
                        trailing = {
                            Switch(
                                checked = config.allowNotification && isNotificationEnabled,
                                onCheckedChange = {
                                    viewModel.updateModeConfig(config.copy(allowNotification = it))
                                },
                                enabled = isNotificationEnabled
                            )
                        }
                    )
                    SettingsItem(
                        label = "自动弹出抽屉",
                        subLabel = "开始解析后自动打开侧边抽屉实时展示结果",
                        icon = Icons.AutoMirrored.Filled.ViewSidebar,
                        trailing = {
                            Switch(
                                checked = config.autoOpenDrawer,
                                onCheckedChange = {
                                    viewModel.updateModeConfig(config.copy(autoOpenDrawer = it))
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}
