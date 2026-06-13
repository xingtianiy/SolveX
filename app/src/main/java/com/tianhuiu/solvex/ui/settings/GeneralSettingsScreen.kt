package com.tianhuiu.solvex.ui.settings

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import com.tianhuiu.solvex.data.models.CaptureMode
import com.tianhuiu.solvex.ui.MainViewModel
import com.tianhuiu.solvex.ui.components.SettingsGroup
import com.tianhuiu.solvex.ui.components.SettingsItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    var pendingMode by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("通用配置") },
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
                SettingsGroup(title = "屏幕录制方式") {
                    SettingsItem(
                        label = "系统屏幕录制",
                        subLabel = "通过系统屏幕录制权限建立截图会话，每次启动需授权",
                        icon = Icons.Default.PhoneAndroid,
                        trailing = {
                            RadioButton(
                                selected = viewModel.permissions.captureMode == CaptureMode.SYSTEM,
                                onClick = {
                                    if (viewModel.isServiceRunning) {
                                        pendingMode = CaptureMode.SYSTEM
                                    } else {
                                        viewModel.updatePermissions(
                                            viewModel.permissions.copy(
                                                captureMode = CaptureMode.SYSTEM
                                            )
                                        )
                                    }
                                }
                            )
                        },
                        onClick = {
                            if (viewModel.isServiceRunning) {
                                pendingMode = CaptureMode.SYSTEM
                            } else {
                                viewModel.updatePermissions(viewModel.permissions.copy(captureMode = CaptureMode.SYSTEM))
                            }
                        }
                    )
                    SettingsItem(
                        label = "无障碍截图",
                        subLabel = "通过无障碍服务截取屏幕，一次授权后续静默截图",
                        icon = Icons.Default.AccessibilityNew,
                        trailing = {
                            RadioButton(
                                selected = viewModel.permissions.captureMode == CaptureMode.ACCESSIBILITY,
                                onClick = {
                                    if (viewModel.isServiceRunning) {
                                        pendingMode = CaptureMode.ACCESSIBILITY
                                    } else {
                                        viewModel.updatePermissions(
                                            viewModel.permissions.copy(
                                                captureMode = CaptureMode.ACCESSIBILITY
                                            )
                                        )
                                    }
                                }
                            )
                        },
                        onClick = {
                            if (viewModel.isServiceRunning) {
                                pendingMode = CaptureMode.ACCESSIBILITY
                            } else {
                                viewModel.updatePermissions(viewModel.permissions.copy(captureMode = CaptureMode.ACCESSIBILITY))
                            }
                        }
                    )
                    SettingsItem(
                        label = "Shizuku ADB",
                        subLabel = "通过 Shizuku 授权后调用 adb 截屏，一次授权后续静默截图",
                        icon = Icons.Default.Terminal,
                        trailing = {
                            RadioButton(
                                selected = viewModel.permissions.captureMode == CaptureMode.SHIZUKU,
                                onClick = {
                                    if (viewModel.isServiceRunning) {
                                        pendingMode = CaptureMode.SHIZUKU
                                    } else {
                                        viewModel.updatePermissions(
                                            viewModel.permissions.copy(
                                                captureMode = CaptureMode.SHIZUKU
                                            )
                                        )
                                    }
                                }
                            )
                        },
                        onClick = {
                            if (viewModel.isServiceRunning) {
                                pendingMode = CaptureMode.SHIZUKU
                            } else {
                                viewModel.updatePermissions(viewModel.permissions.copy(captureMode = CaptureMode.SHIZUKU))
                            }
                        }
                    )
                }
            }

            item {
                SettingsGroup(title = "显示设置") {
                    SettingsItem(
                        label = "跟随内容输出滚动",
                        subLabel = "开启后将自动滚动跟随最新内容",
                        icon = Icons.Default.Swipe,
                        trailing = {
                            Switch(
                                checked = viewModel.autoScrollContent,
                                onCheckedChange = {
                                    viewModel.updateAutoScrollContent(it)
                                }
                            )
                        },
                        onClick = {
                            viewModel.updateAutoScrollContent(!viewModel.autoScrollContent)
                        }
                    )
                    SettingsItem(
                        label = "隐藏悬浮球",
                        subLabel = "开启后悬浮球无操作时将自动隐藏到屏幕边缘",
                        icon = Icons.Default.Visibility,
                        trailing = {
                            Switch(
                                checked = viewModel.permissions.enableAutoHideBall,
                                onCheckedChange = {
                                    viewModel.updatePermissions(
                                        viewModel.permissions.copy(
                                            enableAutoHideBall = it
                                        )
                                    )
                                }
                            )
                        },
                        onClick = {
                            viewModel.updatePermissions(
                                viewModel.permissions.copy(
                                    enableAutoHideBall = !viewModel.permissions.enableAutoHideBall
                                )
                            )
                        }
                    )
                    SettingsItem(
                        label = "悬浮球大小",
                        subLabel = "${viewModel.permissions.ballFullSizeDp.toInt()}dp（拖动滑块调整）",
                        icon = Icons.Default.PhoneAndroid,
                        trailing = {
                            Slider(
                                value = viewModel.permissions.ballFullSizeDp,
                                onValueChange = { newValue ->
                                    viewModel.updateBallSize(fullSizeDp = newValue)
                                },
                                valueRange = 24f..64f,
                                steps = 0,
                                modifier = Modifier.widthIn(max = 100.dp)
                            )
                        }
                    )
                }
            }
        }
    }

    if (pendingMode != null) {
        com.tianhuiu.solvex.ui.components.SolveXConfirmDialog(
            onDismissRequest = { pendingMode = null },
            onConfirm = {
                viewModel.stopService()
                viewModel.updatePermissions(viewModel.permissions.copy(captureMode = pendingMode!!))
                pendingMode = null
            },
            title = "需要停止服务",
            message = "更改录制方式需要先停止当前运行的服务。是否停止服务并应用更改？",
            confirmText = "停止并应用",
            dismissText = "取消",
            isDestructive = true,
            icon = Icons.Default.Warning
        )
    }
}
