package com.tianhuiu.solvex.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tianhuiu.solvex.data.models.UpdateLevel
import com.tianhuiu.solvex.mode.ModeRegistry
import com.tianhuiu.solvex.ui.components.SolveXConfirmDialog
import com.tianhuiu.solvex.ui.components.UpdateDialog
import com.tianhuiu.solvex.ui.history.HistoryDetailScreen
import com.tianhuiu.solvex.ui.history.HistoryScreen
import com.tianhuiu.solvex.ui.home.HomeScreen
import com.tianhuiu.solvex.ui.settings.AboutScreen
import com.tianhuiu.solvex.ui.settings.AssistantEditScreen
import com.tianhuiu.solvex.ui.settings.AssistantSettingsScreen
import com.tianhuiu.solvex.ui.settings.GeneralSettingsScreen
import com.tianhuiu.solvex.ui.settings.ImportExportSettingsScreen
import com.tianhuiu.solvex.ui.settings.ModeSettingsScreen
import com.tianhuiu.solvex.ui.settings.ModelSettingsScreen
import com.tianhuiu.solvex.ui.settings.PermissionSettingsScreen
import com.tianhuiu.solvex.ui.settings.ProviderEditScreen
import com.tianhuiu.solvex.ui.settings.SettingsScreen
import com.tianhuiu.solvex.ui.settings.TutorialScreen

/**
 * 屏幕导航定义。
 */
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Screen("home", "首页", Icons.Default.Home)
    data object History : Screen("history", "历史", Icons.Default.History)
    data object Settings : Screen("settings", "设置", Icons.Default.Settings)
}

/**
 * 应用主入口及路由配置。
 */
@Composable
fun SolveXApp(viewModel: MainViewModel, updateViewModel: UpdateViewModel) {
    val navController = rememberNavController()
    val items = listOf(
        Screen.Home,
        Screen.History,
        Screen.Settings,
    )

    MaterialTheme {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        val showBottomBar = items.any { it.route == currentDestination?.route }

        // 软件更新弹窗
        updateViewModel.updateInfo?.let { info ->
            val shouldShowDialog = when (info.updateLevel) {
                UpdateLevel.CRITICAL -> true
                UpdateLevel.RECOMMENDED -> (updateViewModel.isFreshUpdate || updateViewModel.showDialogManually) && !updateViewModel.isDismissedInSession
                UpdateLevel.OPTIONAL -> updateViewModel.showDialogManually && !updateViewModel.isDismissedInSession
            }

            if (shouldShowDialog) {
                UpdateDialog(
                    info = info,
                    downloadStatus = updateViewModel.downloadStatus,
                    onDismiss = { updateViewModel.dismissUpdateDialog() },
                    onUpdate = { updateViewModel.startUpdate() }
                )
            }
        }

        // 全局通用弹窗
        viewModel.globalDialogState?.let { data ->
            SolveXConfirmDialog(
                onDismissRequest = {
                    data.onDismiss?.invoke()
                    viewModel.dismissGlobalDialog()
                },
                onConfirm = {
                    data.onConfirm()
                    viewModel.dismissGlobalDialog()
                },
                title = data.title,
                message = data.message,
                confirmText = data.confirmText,
                dismissText = data.dismissText,
                isDestructive = data.isDestructive,
                icon = data.icon
            )
        }

        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar {
                        items.forEach { screen ->
                            val hasSettingsBadge = screen == Screen.Settings && run {
                                val updateBadge = updateViewModel.updateInfo != null
                                val permissionBadge = !viewModel.isAllPermissionsReady
                                val providerBadge = viewModel.providers.all { it.apiKey.isBlank() }
                                val modeBadge = ModeRegistry.all.any { mode ->
                                    val config = viewModel.allModeConfigs[mode.id] ?: mode.defaultConfig()
                                    config.ocrProviderId.isNullOrBlank() && 
                                    config.textProviderId.isNullOrBlank() && 
                                    config.visionProviderId.isNullOrBlank() &&
                                    viewModel.defaultProviderId.isNullOrBlank()
                                }
                                updateBadge || permissionBadge || providerBadge || modeBadge
                            }

                            NavigationBarItem(
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (hasSettingsBadge) {
                                                Badge()
                                            }
                                        }
                                    ) {
                                        Icon(screen.icon, contentDescription = null)
                                    }
                                },
                                label = { Text(screen.label) },
                                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            val deepLinkId = viewModel.deepLinkHistoryId
            LaunchedEffect(deepLinkId) {
                deepLinkId?.let { id ->
                    navController.navigate("history/detail/$id")
                    viewModel.consumeDeepLink()
                }
            }
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        viewModel = viewModel,
                        onNavigateToTutorial = { navController.navigate("settings/tutorial") },
                        onNavigateToSettings = { navController.navigate("settings/assistants") }
                    )
                }
                composable(Screen.History.route) {
                    HistoryScreen(
                        onItemClick = { id -> navController.navigate("history/detail/$id") },
                        autoScroll = viewModel.autoScrollContent
                    )
                }
                composable(
                    route = "history/detail/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType })
                ) { backStackEntry ->
                    val itemId = backStackEntry.arguments?.getString("id") ?: ""
                    HistoryDetailScreen(
                        itemId = itemId,
                        viewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(navController, viewModel, updateViewModel)
                }
                composable("settings/general") {
                    GeneralSettingsScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    route = "settings/mode/{modeId}",
                    arguments = listOf(navArgument("modeId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val modeId = backStackEntry.arguments?.getString("modeId") ?: ""
                    ModeSettingsScreen(
                        modeId = modeId,
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("settings/models") {
                    ModelSettingsScreen(
                        viewModel = viewModel,
                        onEditProvider = { id ->
                            val route =
                                if (id != null) "settings/providers/edit?id=$id" else "settings/providers/edit"
                            navController.navigate(route)
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("settings/assistants") {
                    AssistantSettingsScreen(
                        viewModel = viewModel,
                        onEditAssistant = { id: String? ->
                            val route =
                                if (id != null) "settings/assistants/edit?id=$id" else "settings/assistants/edit"
                            navController.navigate(route)
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    route = "settings/assistants/edit?id={id}",
                    arguments = listOf(navArgument("id") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    })
                ) { backStackEntry ->
                    val assistantId = backStackEntry.arguments?.getString("id")
                    AssistantEditScreen(
                        assistantId = assistantId,
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    route = "settings/providers/edit?id={id}",
                    arguments = listOf(navArgument("id") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    })
                ) { backStackEntry ->
                    val providerId = backStackEntry.arguments?.getString("id")
                    ProviderEditScreen(
                        providerId = providerId,
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("settings/permissions") {
                    PermissionSettingsScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("settings/io") {
                    ImportExportSettingsScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("settings/about") {
                    AboutScreen(
                        viewModel = viewModel,
                        updateViewModel = updateViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("settings/tutorial") {
                    TutorialScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
