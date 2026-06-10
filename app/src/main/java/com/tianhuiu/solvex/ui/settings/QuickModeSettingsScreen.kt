package com.tianhuiu.solvex.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewSidebar
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tianhuiu.solvex.ui.MainViewModel
import com.tianhuiu.solvex.ui.components.ModelSelectorItem
import com.tianhuiu.solvex.ui.components.SettingsGroup
import com.tianhuiu.solvex.ui.components.SettingsItem

/**
 * 自动模式设置页面：配置自动模式下的模型选择及自动化行为。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickModeSettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val config = viewModel.quickConfig

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("自动模式配置") },
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
                            viewModel.updateQuickConfig(
                                config.copy(
                                    ocrProviderId = pid,
                                    ocrModel = model
                                )
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
                            viewModel.updateQuickConfig(
                                config.copy(
                                    textProviderId = pid,
                                    textModel = model
                                )
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
                            viewModel.updateQuickConfig(
                                config.copy(
                                    visionProviderId = pid,
                                    visionModel = model
                                )
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
                                        viewModel.updateQuickConfig(
                                            config.copy(
                                                firstDeltaTimeoutSeconds = seconds
                                            )
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
                        subLabel = if (isNotificationEnabled) "识别完成后发送系统通知" else "系统通知权限已禁用，设置中开启",
                        icon = Icons.Default.Notifications,
                        enabled = isNotificationEnabled,
                        trailing = {
                            Switch(
                                checked = config.allowNotification && isNotificationEnabled,
                                onCheckedChange = {
                                    viewModel.updateQuickConfig(config.copy(allowNotification = it))
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
                                    viewModel.updateQuickConfig(config.copy(autoOpenDrawer = it))
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}
