package com.tianhuiu.solvex.ui.settings

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tianhuiu.solvex.ui.MainViewModel
import com.tianhuiu.solvex.ui.components.ModelSelectorItem
import com.tianhuiu.solvex.ui.components.SettingsGroup
import com.tianhuiu.solvex.ui.components.SettingsItem

/**
 * 多图模式设置页面：配置多图模式下的模型选择及截图行为。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiImageSettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val config = viewModel.multiImageConfig

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("多图模式配置") },
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
                        label = "多模态模型",
                        icon = Icons.Default.AutoAwesome,
                        providers = viewModel.providers,
                        selectedProviderId = config.multiImageVisionProviderId ?: config.visionProviderId,
                        selectedModel = config.multiImageVisionModel ?: config.visionModel,
                        type = "vision",
                        onFetchModels = { viewModel.fetchModelsDirect(it) },
                        onModelSelected = { pid, model ->
                            viewModel.updateMultiImageConfig(
                                config.copy(
                                    multiImageVisionProviderId = pid,
                                    multiImageVisionModel = model
                                )
                            )
                        },
                        defaultProviderId = viewModel.defaultProviderId
                    )
                }
            }

            item {
                SettingsGroup(title = "截图设置") {
                    SettingsItem(
                        label = "启用裁剪",
                        subLabel = "多图模式每次截图后可裁剪区域",
                        icon = Icons.Default.CropSquare,
                        trailing = {
                            Switch(
                                checked = config.multiImageCropEnabled,
                                onCheckedChange = {
                                    viewModel.updateMultiImageConfig(config.copy(multiImageCropEnabled = it))
                                }
                            )
                        }
                    )
                    SettingsItem(
                        label = "合并提交分析",
                        subLabel = "开启将所有截图一次性提交给模型",
                        icon = Icons.AutoMirrored.Filled.MergeType,
                        trailing = {
                            Switch(
                                checked = config.multiImageMergeEnabled,
                                onCheckedChange = {
                                    viewModel.updateMultiImageConfig(config.copy(multiImageMergeEnabled = it))
                                }
                            )
                        }
                    )
                }
            }

            item {
                SettingsGroup(title = "通知与显示") {
                    SettingsItem(
                        label = "是否启用通知",
                        subLabel = "开启后多图分析完成发送系统通知",
                        icon = Icons.Default.Notifications,
                        trailing = {
                            Switch(
                                checked = config.allowNotification,
                                onCheckedChange = {
                                    viewModel.updateMultiImageConfig(config.copy(allowNotification = it))
                                }
                            )
                        },
                        onClick = {
                            viewModel.updateMultiImageConfig(config.copy(allowNotification = !config.allowNotification))
                        }
                    )
                    SettingsItem(
                        label = "自动打开抽屉",
                        subLabel = "开始执行多图分析后自动弹出实时解析抽屉",
                        icon = Icons.Default.OpenInBrowser,
                        trailing = {
                            Switch(
                                checked = config.multiImageAutoOpenDrawer,
                                onCheckedChange = {
                                    viewModel.updateMultiImageConfig(config.copy(multiImageAutoOpenDrawer = it))
                                }
                            )
                        },
                        onClick = {
                            viewModel.updateMultiImageConfig(config.copy(multiImageAutoOpenDrawer = !config.multiImageAutoOpenDrawer))
                        }
                    )
                }
            }
        }
    }
}
