package com.tianhuiu.solvex.ui.settings

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tianhuiu.solvex.mode.ModeRegistry
import com.tianhuiu.solvex.ui.components.SettingsGroup
import com.tianhuiu.solvex.ui.components.SettingsItem

/**
 * 设置主页面：提供各类配置入口的列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "设置",
                        fontWeight = FontWeight.Bold
                    )
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
                SettingsGroup(title = "模式配置") {
                    ModeRegistry.all.forEach { mode ->
                        SettingsItem(
                            label = mode.displayName,
                            subLabel = mode.description,
                            icon = mode.icon,
                            onClick = { navController.navigate("settings/mode/${mode.id}") }
                        )
                    }
                }
            }
            item {
                SettingsGroup(title = "模型配置") {
                    SettingsItem(
                        label = "模型设置",
                        subLabel = "配置模型提供方和助手提示词",
                        icon = Icons.Default.SmartToy,
                        onClick = { navController.navigate("settings/models") }
                    )
                }
            }
            item {
                SettingsGroup(title = "系统配置") {
                    SettingsItem(
                        label = "通用设置",
                        subLabel = "配置截图录屏方式及抽屉外观",
                        icon = Icons.Default.Settings,
                        onClick = { navController.navigate("settings/general") }
                    )
                    SettingsItem(
                        label = "权限设置",
                        subLabel = "管理系统通知及稳定性权限",
                        icon = Icons.Default.Layers,
                        onClick = { navController.navigate("settings/permissions") }
                    )
                    SettingsItem(
                        label = "导入导出",
                        subLabel = "备份、恢复及重置全部配置",
                        icon = Icons.Default.Backup,
                        onClick = { navController.navigate("settings/io") }
                    )
                }
            }
            item {
                SettingsGroup(title = "其他信息") {
                    SettingsItem(
                        label = "使用教程",
                        subLabel = "查看功能指南与常见问题",
                        icon = Icons.Default.Description,
                        onClick = { navController.navigate("settings/tutorial") }
                    )
                    SettingsItem(
                        label = "关于我们",
                        subLabel = "查看版本信息及开源链接",
                        icon = Icons.Default.Info,
                        onClick = { navController.navigate("settings/about") }
                    )
                }
            }
        }
    }
}
