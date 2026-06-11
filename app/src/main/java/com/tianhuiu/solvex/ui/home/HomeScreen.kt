package com.tianhuiu.solvex.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tianhuiu.solvex.data.models.CaptureMode
import com.tianhuiu.solvex.data.models.EngineType
import com.tianhuiu.solvex.data.models.ProjectMode
import com.tianhuiu.solvex.ui.MainViewModel
import com.tianhuiu.solvex.ui.components.SolveXConfirmDialog

/**
 * 首页屏幕。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToTutorial: () -> Unit = {}
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val lifecycleOwner = LocalLifecycleOwner.current
    val inAppNotifications by viewModel.inAppNotifications.collectAsState(initial = emptyList())

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
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
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "SolveX",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = surfaceColor,
                    scrolledContainerColor = Color.Unspecified,
                    navigationIconContentColor = Color.Unspecified,
                    titleContentColor = Color.Unspecified,
                    actionIconContentColor = Color.Unspecified
                ),
                windowInsets = WindowInsets(top = 0.dp)
            )
        }
    ) { padding ->
        // 停止服务确认弹窗
        if (viewModel.showStopConfirmationDialog) {
            SolveXConfirmDialog(
                onDismissRequest = { viewModel.updateShowStopConfirmationDialog(false) },
                onConfirm = { viewModel.stopService() },
                title = "停止服务",
                message = "确认要停止 SolveX 服务吗？停止后将无法使用实时捕获功能。",
                confirmText = "确认停止",
                isDestructive = true,
                icon = Icons.Default.StopCircle
            )
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            surfaceColor,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        )
                    )
                )
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // 助手选择器
            item {
                CompactAssistantBanner(viewModel)
            }

            // 首页通知展示区
            if (inAppNotifications.isNotEmpty()) {
                item {
                    NotificationStack(
                        notifications = inAppNotifications,
                        onDismiss = { viewModel.dismissInAppNotification(it) },
                        onRequestOverlayPermission = { viewModel.requestOverlayPermission() },
                        onRequestNotificationPermission = { viewModel.requestNotificationPermission() },
                        onRequestAccessibilityPermission = { viewModel.requestAccessibilityPermission() },
                        onRequestShizukuPermission = { viewModel.requestShizukuPermission() },
                        onNavigateToTutorial = onNavigateToTutorial
                    )
                }
            }

            // 引擎选择
            item {
                SectionHeader("识别引擎", "决定如何理解屏幕内容")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EngineCardCompact(
                        modifier = Modifier.weight(1f),
                        type = EngineType.TEXT_ENGINE,
                        isSelected = viewModel.selectedEngine == EngineType.TEXT_ENGINE,
                        onClick = { viewModel.setEngine(EngineType.TEXT_ENGINE) }
                    )
                    EngineCardCompact(
                        modifier = Modifier.weight(1f),
                        type = EngineType.VISION_ENGINE,
                        isSelected = viewModel.selectedEngine == EngineType.VISION_ENGINE,
                        onClick = { viewModel.setEngine(EngineType.VISION_ENGINE) }
                    )
                }
            }

            // 操作区
            item {
                Spacer(Modifier.height(8.dp))
                ActionSection(viewModel)
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

/**
 * 首页通知栈：管理并展示多种类型的应用内通知（权限、就绪状态）。
 */
@Composable
fun NotificationStack(
    notifications: List<com.tianhuiu.solvex.data.models.InAppNotification>,
    onDismiss: (String) -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestAccessibilityPermission: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onNavigateToTutorial: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        notifications.forEach { notification ->
            NotificationCard(
                notification = notification,
                onDismiss = { onDismiss(notification.id) },
                onAction = {
                    if (notification.type == com.tianhuiu.solvex.data.models.NotificationType.PERMISSION) {
                        when (notification.permissionType) {
                            com.tianhuiu.solvex.data.models.PermissionType.OVERLAY -> onRequestOverlayPermission()
                            com.tianhuiu.solvex.data.models.PermissionType.NOTIFICATION -> onRequestNotificationPermission()
                            com.tianhuiu.solvex.data.models.PermissionType.ACCESSIBILITY -> onRequestAccessibilityPermission()
                            com.tianhuiu.solvex.data.models.PermissionType.SHIZUKU -> onRequestShizukuPermission()
                            else -> onRequestOverlayPermission()
                        }
                    } else if (notification.type == com.tianhuiu.solvex.data.models.NotificationType.TUTORIAL) {
                        onNavigateToTutorial()
                    }
                }
            )
        }
    }
}

/**
 * 通用通知卡片组件。
 */
@Composable
fun NotificationCard(
    notification: com.tianhuiu.solvex.data.models.InAppNotification,
    onDismiss: () -> Unit,
    onAction: () -> Unit
) {
    val containerColor: Color
    val contentColor: Color
    val icon: ImageVector
    val actionText: String?

    when (notification.type) {
        com.tianhuiu.solvex.data.models.NotificationType.PERMISSION -> {
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
            contentColor = MaterialTheme.colorScheme.onErrorContainer
            icon = Icons.Default.Warning
            actionText = "去授权"
        }

        com.tianhuiu.solvex.data.models.NotificationType.READY_STATUS -> {
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            contentColor = MaterialTheme.colorScheme.onSurface
            icon = Icons.Default.Info
            actionText = null
        }

        com.tianhuiu.solvex.data.models.NotificationType.TUTORIAL -> {
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            icon = Icons.Default.AutoFixHigh
            actionText = "查看教程"
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, contentColor.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    notification.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    notification.content,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.8f),
                    maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            if (actionText != null) {
                TextButton(
                    onClick = onAction,
                    colors = ButtonDefaults.textButtonColors(contentColor = contentColor)
                ) {
                    Text(actionText, fontWeight = FontWeight.Bold)
                }
            } else {
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = contentColor.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "· $subtitle",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactAssistantBanner(viewModel: MainViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val selectedAssistant = viewModel.assistants.find { it.id == viewModel.selectedAssistantId }
        ?: viewModel.assistants.firstOrNull()

    var componentWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged {
                    componentWidth = with(density) { it.width.toDp() }
                },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AutoFixHigh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "智能助手",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        selectedAssistant?.name ?: "未设置助手",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(componentWidth)
        ) {
            viewModel.assistants.forEach { assistant ->
                DropdownMenuItem(
                    text = { Text(assistant.name) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Face,
                            null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        viewModel.setAssistant(assistant.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun EngineCardCompact(
    modifier: Modifier = Modifier,
    type: EngineType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor =
        if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val contentColor =
        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val borderColor =
        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

    Surface(
        onClick = onClick,
        modifier = modifier.height(90.dp),
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            borderColor
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    if (type == EngineType.TEXT_ENGINE) Icons.Default.TextFields else Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = contentColor
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    type.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    if (type == EngineType.TEXT_ENGINE) "OCR文字" else "多模态视觉",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }

            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActionSection(viewModel: MainViewModel) {
    val isOverlayEnabled = viewModel.isOverlayPermissionGranted
    val isAccessibilityEnabled = viewModel.isAccessibilityEnabled
    val isShizukuGranted = viewModel.isShizukuPermissionGranted
    val captureMode = viewModel.permissions.captureMode

    val isRunning = viewModel.isServiceRunning

    // 根据截屏模式判断启动按钮是否可用
    val isStartEnabled = when (captureMode) {
        CaptureMode.SYSTEM -> isOverlayEnabled
        CaptureMode.ACCESSIBILITY -> isOverlayEnabled && isAccessibilityEnabled
        CaptureMode.SHIZUKU -> isOverlayEnabled && isShizukuGranted
        else -> isOverlayEnabled
    }

    // 生成按钮禁用原因提示
    val disabledHint: String? = when {
        isStartEnabled || isRunning -> null
        !isOverlayEnabled -> "请先授予「显示在其他应用上层」权限"
        captureMode == CaptureMode.ACCESSIBILITY && !isAccessibilityEnabled -> "请先在系统设置中开启「SolveX 无障碍服务」"
        captureMode == CaptureMode.SHIZUKU && !isShizukuGranted -> "请先启动 Shizuku 并授权 SolveX"
        else -> null
    }

    var lastClickTime by remember { mutableLongStateOf(0L) }
    val debounceInterval = 500L

    fun safeAction(action: () -> Unit) {
        val currentTime = System.currentTimeMillis()
        if ((currentTime - lastClickTime) > debounceInterval) {
            lastClickTime = currentTime
            action()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(20.dp),
            color = when {
                !isStartEnabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                isRunning -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                else -> MaterialTheme.colorScheme.primaryContainer
            },
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                when {
                    !isStartEnabled -> MaterialTheme.colorScheme.outlineVariant
                    isRunning -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                }
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isStartEnabled) {
                            Modifier.combinedClickable(
                                onClick = {
                                    safeAction {
                                        if (isRunning) {
                                            viewModel.updateShowStopConfirmationDialog(true)
                                        } else {
                                            viewModel.setMode(ProjectMode.STUDY_MODE)
                                            viewModel.startService()
                                        }
                                    }
                                },
                                onLongClick = {
                                    safeAction {
                                        if (!isRunning) {
                                            viewModel.setMode(ProjectMode.QUICK_MODE)
                                            viewModel.startService()
                                        }
                                    }
                                }
                            )
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isRunning) Icons.Default.StopCircle else Icons.Default.RocketLaunch,
                        contentDescription = null,
                        tint = when {
                            !isStartEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            isRunning -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (isRunning) "停止服务" else "启动服务",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            !isStartEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            isRunning -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = RoundedCornerShape(8.dp)
        ) {
            val statusText = when {
                isRunning -> {
                    val modeName = viewModel.activeMode?.displayName ?: ""
                    "${modeName}正在运行中..."
                }

                disabledHint != null -> disabledHint
                else -> "单击：开启常规学习模式 | 长按：开启自动速查模式"
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = if (disabledHint != null)
                    MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}
