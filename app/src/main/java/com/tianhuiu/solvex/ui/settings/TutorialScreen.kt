package com.tianhuiu.solvex.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tianhuiu.solvex.ui.components.LoadingOverlay
import com.tianhuiu.solvex.ui.components.MathView
import com.tianhuiu.solvex.ui.components.SolveXDialog
import com.tianhuiu.solvex.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 使用教程页面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val tutorialFileNames = listOf(
        "tutorial/01.md",
        "tutorial/02.md",
        "tutorial/03.md"
    )

    var currentPage by remember { mutableIntStateOf(0) }
    var isRendered by remember { mutableStateOf(false) }
    var showNetworkError by remember { mutableStateOf(false) }

    val tutorialContent by produceState(initialValue = "", currentPage) {
        isRendered = false
        showNetworkError = false
        value = withContext(Dispatchers.IO) {
            FileUtils.readAssetFile(context, tutorialFileNames[currentPage])
        }
    }

    // 检测渲染超时：无网络时 MathJax CDN 无法加载
    LaunchedEffect(currentPage) {
        isRendered = false
        showNetworkError = false
        delay(3000L)
        if (!isRendered) {
            showNetworkError = true
        }
    }

    val isLoading = tutorialContent.isEmpty() || (!isRendered && !showNetworkError)

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("使用教程", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    windowInsets = WindowInsets(top = 0.dp)
                )
            },
            bottomBar = {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.height(56.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 上一页
                        if (currentPage > 0) {
                            IconButton(onClick = { currentPage-- }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.NavigateBefore,
                                    contentDescription = "上一页"
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(48.dp))
                        }

                        // 页码指示
                        Text(
                            text = "${currentPage + 1} / ${tutorialFileNames.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // 下一页
                        if (currentPage < tutorialFileNames.size - 1) {
                            IconButton(onClick = { currentPage++ }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.NavigateNext,
                                    contentDescription = "下一页"
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(48.dp))
                        }
                    }
                }
            }
        ) { padding ->
            if (tutorialContent.isNotEmpty()) {
                MathView(
                    text = tutorialContent,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    lineHeight = 2.0f,
                    forceMarkdown = true,
                    onRendered = { isRendered = true }
                )
            }
        }
        LoadingOverlay(
            isLoading = isLoading,
            message = "加载教程中"
        )
    }

    // 无网络弹窗：退出教程页面
    if (showNetworkError && !isRendered) {
        SolveXDialog(
            onDismissRequest = { onBack() },
            title = "加载失败",
            icon = Icons.Default.WifiOff,
            confirmButton = {
                TextButton(onClick = onBack) {
                    Text("返回", fontWeight = FontWeight.Bold)
                }
            }
        ) {
            Text(
                text = "网络连接异常，无法加载教程内容，请检查网络后重试。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
