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
import com.tianhuiu.solvex.ui.components.SolveXConfirmDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    var pendingMode by remember { mutableStateOf<String?>(null) }
    var showAccessibilityConfirm by remember { mutableStateOf(false) }

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
                        subLabel = "利用无障碍服务截取屏幕，一次授权后续静默截图",
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
                                        if (!viewModel.isAccessibilityEnabled) {
                                            showAccessibilityConfirm = true
                                        }
                                    }
                                }
                            )
                        },
                        onClick = {
                            if (viewModel.isServiceRunning) {
                                pendingMode = CaptureMode.ACCESSIBILITY
                            } else {
                                viewModel.updatePermissions(viewModel.permissions.copy(captureMode = CaptureMode.ACCESSIBILITY))
                                if (!viewModel.isAccessibilityEnabled) {
                                    showAccessibilityConfirm = true
                                }
                            }
                        }
                    )
                    SettingsItem(
                        label = "Shizuku ADB",
                        subLabel = "通过 Shizuku 授权后调用 ADB 截图。适合进阶用户使用。",
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
                                        if (!viewModel.isShizukuPermissionGranted) {
                                            viewModel.requestShizukuPermission()
                                        }
                                    }
                                }
                            )
                        },
                        onClick = {
                            if (viewModel.isServiceRunning) {
                                pendingMode = CaptureMode.SHIZUKU
                            } else {
                                viewModel.updatePermissions(viewModel.permissions.copy(captureMode = CaptureMode.SHIZUKU))
                                if (!viewModel.isShizukuPermissionGranted) {
                                    viewModel.requestShizukuPermission()
                                }
                            }
                        }
                    )
                }
            }

            item {
                SettingsGroup(title = "隐私保护") {
                    SettingsItem(
                        label = "防截屏录屏",
                        subLabel = "开启后应用的内容将无法被系统或其他应用截取。这是系统级底层保护。",
                        icon = Icons.Default.Visibility,
                        trailing = {
                            Switch(
                                checked = viewModel.permissions.enableScreenProtection,
                                onCheckedChange = {
                                    viewModel.updatePermissions(
                                        viewModel.permissions.copy(
                                            enableScreenProtection = it
                                        )
                                    )
                                }
                            )
                        },
                        onClick = {
                            viewModel.updatePermissions(
                                viewModel.permissions.copy(
                                    enableScreenProtection = !viewModel.permissions.enableScreenProtection
                                )
                            )
                        }
                    )
                    SettingsItem(
                        label = "隐匿模式",
                        subLabel = if (viewModel.isShizukuPermissionGranted) {
                            "监测到受保护应用时自动同步开启自身隐私保护，并从多任务列表中隐藏。"
                        } else {
                            "隐匿模式使用需要激活 Shizuku 后使用。"
                        },
                        icon = Icons.Default.Warning,
                        enabled = viewModel.isShizukuPermissionGranted,
                        trailing = {
                            Switch(
                                checked = viewModel.permissions.enableStealthMode && viewModel.isShizukuPermissionGranted,
                                onCheckedChange = {
                                    viewModel.updatePermissions(
                                        viewModel.permissions.copy(
                                            enableStealthMode = it
                                        )
                                    )
                                },
                                enabled = viewModel.isShizukuPermissionGranted
                            )
                        },
                        onClick = {
                            if (viewModel.isShizukuPermissionGranted) {
                                viewModel.updatePermissions(
                                    viewModel.permissions.copy(
                                        enableStealthMode = !viewModel.permissions.enableStealthMode
                                    )
                                )
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

    if (showAccessibilityConfirm) {
        SolveXConfirmDialog(
            onDismissRequest = { showAccessibilityConfirm = false },
            onConfirm = {
                showAccessibilityConfirm = false
                viewModel.requestAccessibilityPermission()
            },
            title = "授权无障碍服务",
            message = "无障碍截图模式需要启用“SolveX 截屏助手”服务。点击确认将前往系统设置页，请在“已安装的服务”中找到并开启。",
            confirmText = "前往设置",
            dismissText = "取消",
            icon = Icons.Default.AccessibilityNew
        )
    }

    if (pendingMode != null) {
        SolveXConfirmDialog(
            onDismissRequest = { pendingMode = null },
            onConfirm = {
                viewModel.stopService()
                viewModel.updatePermissions(viewModel.permissions.copy(captureMode = pendingMode!!))
                if (pendingMode == CaptureMode.SHIZUKU && !viewModel.isShizukuPermissionGranted) {
                    viewModel.requestShizukuPermission()
                }
                if (pendingMode == CaptureMode.ACCESSIBILITY && !viewModel.isAccessibilityEnabled) {
                    showAccessibilityConfirm = true
                }
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
