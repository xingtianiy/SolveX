package com.tianhuiu.solvex.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.tianhuiu.solvex.BuildConfig
import com.tianhuiu.solvex.R
import com.tianhuiu.solvex.ui.MainViewModel
import com.tianhuiu.solvex.ui.UpdateViewModel
import com.tianhuiu.solvex.ui.components.SettingBadge
import com.tianhuiu.solvex.ui.components.SettingsGroup
import com.tianhuiu.solvex.ui.components.SettingsItem
import com.tianhuiu.solvex.ui.components.SolveXDialog

/**
 * 关于我们页面：精简布局，支持详情弹窗。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    viewModel: MainViewModel,
    updateViewModel: UpdateViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var detailDialogContent by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showSourceDialog by remember { mutableStateOf(false) }
    val isChecking by remember { androidx.compose.runtime.derivedStateOf { updateViewModel.isCheckingUpdate } }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("关于我们", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            // 仅保留软件图标
            Surface(
                modifier = Modifier.size(96.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "SolveX Logo",
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // 使用无标题的卡片组展示列表
            SettingsGroup(title = "") {
                AboutInfoItem(
                    label = "版本信息",
                    subLabel = if (isChecking) "正在检测更新" else "版本 ${BuildConfig.VERSION_NAME}",
                    icon = Icons.Default.Info,
                    showArrow = true,
                    badge = if (updateViewModel.updateInfo != null) {
                        { SettingBadge() }
                    } else null,
                    onClick = {
                        if (!isChecking) {
                            updateViewModel.checkForUpdates(manual = true)
                        }
                    }
                )

                AboutInfoItem(
                    label = "软件介绍",
                    subLabel = "简洁、高效的 AI 屏幕解析工具",
                    icon = Icons.Default.Description,
                    showArrow = true,
                    onClick = {
                        detailDialogContent =
                            "软件介绍" to "SolveX 是一款专为 Android 打造的开源学习辅助工具。它结合了屏幕捕捉、高精度 OCR 文字识别、以及大语言模型 (LLM) 的 SSE 流式响应技术，旨在为你提供丝滑、高效的题目解答和内容分析体验。"
                    }
                )

                AboutInfoItem(
                    label = "问题反馈",
                    subLabel = "提交功能建议或报告软件问题",
                    icon = Icons.Default.BugReport,
                    showArrow = true,
                    onClick = { showFeedbackDialog = true }
                )

                AboutInfoItem(
                    label = "开源地址",
                    subLabel = "查看项目源码仓库",
                    icon = Icons.Default.Code,
                    showArrow = true,
                    onClick = { showSourceDialog = true }
                )

                AboutInfoItem(
                    label = "开源协议",
                    subLabel = "Apache License Version 2.0",
                    icon = Icons.Default.Description,
                    showArrow = true,
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            "https://github.com/xingtianiy/SolveX/blob/main/LICENSE".toUri()
                        )
                        context.startActivity(intent)
                    }
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // 详情展示弹窗
    detailDialogContent?.let { (title, content) ->
        SolveXDialog(
            onDismissRequest = { detailDialogContent = null },
            title = title,
            confirmButton = {
                TextButton(onClick = { detailDialogContent = null }) {
                    Text("确定", fontWeight = FontWeight.Bold)
                }
            }
        ) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }

    // 开源地址选择弹窗
    if (showSourceDialog) {
        SolveXDialog(
            onDismissRequest = { showSourceDialog = false },
            title = "选择开源平台",
            confirmButton = {
                TextButton(onClick = { showSourceDialog = false }) {
                    Text("取消")
                }
            }
        ) {
            Column {
                val onSourceClick: (String) -> Unit = { url ->
                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                    showSourceDialog = false
                }

                SettingsItem(
                    label = "GitHub",
                    subLabel = "github.com/xingtianiy/SolveX",
                    icon = Icons.Default.Code,
                    onClick = {
                        onSourceClick("https://github.com/xingtianiy/SolveX")
                    }
                )

                SettingsItem(
                    label = "Gitee",
                    subLabel = "gitee.com/xingtianiy/SolveX",
                    icon = Icons.Default.Code,
                    onClick = {
                        onSourceClick("https://gitee.com/xingtianiy/SolveX")
                    }
                )
            }
        }
    }

    // 问题反馈渠道选择弹窗
    if (showFeedbackDialog) {
        SolveXDialog(
            onDismissRequest = { showFeedbackDialog = false },
            title = "选择反馈渠道",
            confirmButton = {
                TextButton(onClick = { showFeedbackDialog = false }) {
                    Text("取消")
                }
            }
        ) {
            Column {
                val onFeedbackClick: (String) -> Unit = { url ->
                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                    showFeedbackDialog = false
                }

                SettingsItem(
                    label = "GitHub Issues",
                    subLabel = "推荐，适合国际用户",
                    icon = Icons.Default.BugReport,
                    onClick = {
                        onFeedbackClick("https://github.com/xingtianiy/SolveX/issues")
                    }
                )

                SettingsItem(
                    label = "Gitee Issues",
                    subLabel = "备选，适合国内用户",
                    icon = Icons.Default.BugReport,
                    onClick = {
                        onFeedbackClick("https://gitee.com/xingtianiy/SolveX/issues")
                    }
                )
            }
        }
    }
}

/**
 * 专门用于关于页面的条目组件，支持显示右箭头。
 */
@Composable
fun AboutInfoItem(
    label: String,
    subLabel: String? = null,
    icon: ImageVector? = null,
    showArrow: Boolean = false,
    badge: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    SettingsItem(
        label = label,
        subLabel = subLabel,
        icon = icon,
        badge = badge,
        trailing = if (showArrow) {
            {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        } else null,
        onClick = onClick
    )
}
