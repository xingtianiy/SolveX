package com.tianhuiu.solvex.ui.settings

import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tianhuiu.solvex.service.SolveXAccessibilityService
import com.tianhuiu.solvex.ui.MainViewModel
import com.tianhuiu.solvex.ui.components.SettingsGroup
import com.tianhuiu.solvex.ui.components.SettingsItem
import com.tianhuiu.solvex.ui.components.SolveXConfirmDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionSettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isNotificationEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var isSystemAlertEnabled by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }
    var isAccessibilityEnabled by remember {
        mutableStateOf(isAccessibilityServiceEnabled(context))
    }
    var isBatteryOptimized by remember {
        val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
        mutableStateOf(!pm.isIgnoringBatteryOptimizations(context.packageName))
    }

    var showShizukuDownloadDialog by remember { mutableStateOf(false) }
    var showShizukuLaunchDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isNotificationEnabled =
                    NotificationManagerCompat.from(context).areNotificationsEnabled()
                isSystemAlertEnabled = Settings.canDrawOverlays(context)
                isAccessibilityEnabled = isAccessibilityServiceEnabled(context)
                isBatteryOptimized =
                    !(context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager)
                        .isIgnoringBatteryOptimizations(context.packageName)
                viewModel.checkPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("权限设置", fontWeight = FontWeight.Bold) },
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
                SettingsGroup(title = "系统权限") {
                    SettingsItem(
                        label = "通知权限",
                        subLabel = if (isNotificationEnabled) "已授予通知权限" else "需要通知权限以显示识别结果",
                        icon = Icons.Default.Notifications,
                        trailing = {
                            Button(
                                onClick = {
                                    val intent =
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(
                                                Settings.EXTRA_APP_PACKAGE,
                                                context.packageName
                                            )
                                        }
                                    context.startActivity(intent)
                                },
                                enabled = !isNotificationEnabled
                            ) {
                                Text(if (isNotificationEnabled) "已授权" else "去授权")
                            }
                        }
                    )
                    SettingsItem(
                        label = "悬浮窗权限",
                        subLabel = if (isSystemAlertEnabled) "已授予悬浮窗权限" else "需要悬浮窗权限以提供快速操作",
                        icon = Icons.Default.Layers,
                        trailing = {
                            Button(
                                onClick = {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        "package:${context.packageName}".toUri()
                                    )
                                    context.startActivity(intent)
                                },
                                enabled = !isSystemAlertEnabled
                            ) {
                                Text(if (isSystemAlertEnabled) "已授权" else "去授权")
                            }
                        }
                    )
                    SettingsItem(
                        label = "无障碍服务",
                        subLabel = if (isAccessibilityEnabled) "无障碍服务已开启" else "开启无障碍服务使用无障碍截图",
                        icon = Icons.Default.AccessibilityNew,
                        trailing = {
                            Button(
                                onClick = { viewModel.requestAccessibilityPermission() },
                                enabled = !isAccessibilityEnabled
                            ) {
                                Text(if (isAccessibilityEnabled) "已开启" else "去开启")
                            }
                        }
                    )
                    SettingsItem(
                        label = "电池优化",
                        subLabel = if (isBatteryOptimized) "已开启电池优化" else "已允许后台运行",
                        icon = Icons.Default.BatterySaver,
                        trailing = {
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                },
                                enabled = isBatteryOptimized
                            ) {
                                Text(if (isBatteryOptimized) "去关闭" else "已允许")
                            }
                        }
                    )
                    SettingsItem(
                        label = "Shizuku 授权",
                        subLabel = when {
                            !viewModel.isShizukuInstalled -> "未检测到 Shizuku 应用"
                            !viewModel.isShizukuRunning -> "Shizuku 服务未运行，请先启动 Shizuku"
                            !viewModel.isShizukuPermissionGranted -> "Shizuku 已运行但未授权 SolveX"
                            else -> "Shizuku 授权正常"
                        },
                        icon = Icons.Default.Terminal,
                        trailing = {
                            Button(
                                onClick = {
                                    when {
                                        !viewModel.isShizukuInstalled -> showShizukuDownloadDialog = true
                                        !viewModel.isShizukuRunning -> showShizukuLaunchDialog = true
                                        else -> viewModel.requestShizukuPermission()
                                    }
                                },
                                enabled = !viewModel.isShizukuPermissionGranted
                            ) {
                                Text(if (viewModel.isShizukuPermissionGranted) "已授权" else "去授权")
                            }
                        }
                    )
                }
            }

            item {
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (showShizukuDownloadDialog) {
        SolveXConfirmDialog(
            onDismissRequest = { showShizukuDownloadDialog = false },
            onConfirm = {
                showShizukuDownloadDialog = false
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    "https://github.com/RikkaApps/Shizuku/releases".toUri()
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
            },
            title = "安装 Shizuku",
            message = "需要 Shizuku 才能使用高级功能（如隐匿模式、ADB 截图）。是否前往下载？",
            confirmText = "前往下载",
            dismissText = "取消",
            icon = Icons.Default.Terminal
        )
    }

    if (showShizukuLaunchDialog) {
        SolveXConfirmDialog(
            onDismissRequest = { showShizukuLaunchDialog = false },
            onConfirm = {
                showShizukuLaunchDialog = false
                val launchIntent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                    ?: context.packageManager.getLaunchIntentForPackage("dev.rikka.shizuku")
                launchIntent?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                }
            },
            title = "启动 Shizuku",
            message = "请先在 Shizuku 应用中启动服务，然后返回 SolveX 继续授权。",
            confirmText = "打开 Shizuku",
            dismissText = "取消",
            icon = Icons.Default.Terminal
        )
    }
}

private fun isAccessibilityServiceEnabled(context: android.content.Context): Boolean {
    val serviceName = "${context.packageName}/${SolveXAccessibilityService::class.java.name}"
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabledServices.split(':').any { it == serviceName }
}
