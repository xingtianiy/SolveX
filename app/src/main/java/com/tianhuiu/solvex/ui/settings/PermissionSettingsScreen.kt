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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
                title = { Text("权限设置") },
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
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                                putExtra(
                                                    Settings.EXTRA_APP_PACKAGE,
                                                    context.packageName
                                                )
                                            }
                                        } else {
                                            Intent("android.settings.APP_NOTIFICATION_SETTINGS").apply {
                                                putExtra("app_package", context.packageName)
                                                putExtra("app_uid", context.applicationInfo.uid)
                                            }
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
                }
            }

            item {
                SettingsGroup(title = "稳定性权限") {
                    SettingsItem(
                        label = "电池优化",
                        subLabel = if (isBatteryOptimized) "电池优化可能限制后台运行，导致服务被关闭" else "已允许后台运行",
                        icon = Icons.Default.BatterySaver,
                        trailing = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        },
                        onClick = {
                            val intent =
                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(intent)
                        }
                    )
                    SettingsItem(
                        label = "自启动",
                        subLabel = "后台被关闭后自动启动应用",
                        icon = Icons.Default.Refresh,
                        trailing = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        },
                        onClick = {
                            val intent =
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = "package:${context.packageName}".toUri()
                                }
                            context.startActivity(intent)
                        }
                    )
                }
            }

            item {
                Spacer(Modifier.height(32.dp))
            }
        }
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
