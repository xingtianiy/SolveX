package com.tianhuiu.solvex.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tianhuiu.solvex.data.models.CaptureMode
import com.tianhuiu.solvex.data.models.DrawerSide
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
                SettingsGroup(title = "抽屉设置") {
                    SettingsItem(
                        label = "弹出位置",
                        subLabel = "设置抽屉从屏幕哪一侧弹出",
                        icon = Icons.Default.Layers,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (viewModel.permissions.drawerSettings.side == DrawerSide.LEFT) "左侧" else "右侧",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Switch(
                                    checked = viewModel.permissions.drawerSettings.side == DrawerSide.RIGHT,
                                    onCheckedChange = { isRight ->
                                        viewModel.updatePermissions(
                                            viewModel.permissions.copy(
                                                drawerSettings = viewModel.permissions.drawerSettings.copy(
                                                    side = if (isRight) DrawerSide.RIGHT else DrawerSide.LEFT
                                                )
                                            )
                                        )
                                    }
                                )
                            }
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
