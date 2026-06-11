# SolveX 代码地图索引

**最后更新：** 2026-06-12
**项目：** SolveX - 基于 AI 的 Android 学习助手
**仓库：** github.com/xingtianiy/SolveX

## 概述

SolveX 是一个使用 Kotlin 和 Jetpack Compose 构建的 Android 应用，可截取屏幕截图、发送给 LLM 提供商进行分析，并通过悬浮窗 UI 叠加层展示结果。它支持三种截图引擎、四种 LLM 提供商类型和三种分析模式。

## 架构地图

```
SolveXApplication
  |
  +-- AppContainer（手动 DI）
  |     +-- OkHttpClient
  |     +-- UnifiedLLMClient
  |     +-- ProcessingPipeline
  |     +-- SolveXDatabase（Room）
  |     +-- HistoryRepository
  |     +-- AppNotificationManager
  |
  +-- MainActivity
        +-- MediaProjection 启动器
        +-- SolveXApp（Compose NavHost）
              |
              +-- HomeScreen（启动/停止服务、引擎选择、助手选择）
              +-- HistoryScreen（分页列表、搜索）
              +-- HistoryDetailScreen（完整分析内容与缩略图）
              +-- SettingsScreen -> 子页面
              |
              +-- MainViewModel（全局状态）
```

## 代码地图列表

| 代码地图 | 描述 | 关键文件 |
|---------|-------------|-----------|
| [应用核心](APP_CORE.md) | 应用入口、DI、Manifest、构建配置 | `SolveXApplication.kt`、`AppContainer.kt`、`MainActivity.kt` |
| [屏幕截图](SCREEN_CAPTURE.md) | 3 种截图引擎实现 | `ScreenCaptureEngine.kt`、`SystemCaptureEngine.kt`、`AccessibilityCaptureEngine.kt`、`ShizukuCaptureEngine.kt` |
| [LLM 集成](LLM_INTEGRATION.md) | 统一 LLM 客户端、适配器、SSE 流式传输 | `UnifiedLLMClient.kt`、`SseStreamClient.kt`、适配器文件 |
| [处理流水线](PROCESSING_PIPELINE.md) | OCR + AI 分析编排 | `ProcessingPipeline.kt`、`Prompts.kt`、`ResponseParser.kt` |
| [悬浮 UI](FLOATING_UI.md) | 悬浮球、抽屉、裁剪叠加层 | `FloatingBallManager.kt`、`DrawerManager.kt`、`CropManager.kt` |
| [服务层](SERVICE_LAYER.md) | 后台服务 | `MainService.kt`、`SolveXAccessibilityService.kt`、`ShizukuShellService.kt` |
| [数据层](DATA_LAYER.md) | Room 数据库、DataStore、仓库 | `SolveXDatabase.kt`、`HistoryRepository.kt`、`SettingsRepository.kt` |
| [数据模型](DATA_MODELS.md) | 所有可序列化数据模型 | `SettingsModels.kt`、`HistoryModels.kt`、`ProcessingModels.kt`、`UpdateModels.kt`、`InAppNotification.kt` |
| [UI 层](UI_LAYER.md) | Compose 页面、ViewModel、组件 | `SolveXApp.kt`、`MainViewModel.kt`、`HistoryViewModel.kt`、所有页面文件 |
| [工具类](UTILITIES.md) | 辅助工具 | `UpdateManager.kt`、`NotificationHelper.kt`、`FileUtils.kt`、`SystemUtils.kt`、`AppNotificationManager.kt` |

## 包结构地图

```
com.tianhuiu.solvex
  +-- capture/        # 屏幕截图引擎（接口 + 3 种实现）
  +-- data/           # 持久化层
  |     +-- dao/      # Room DAO
  |     +-- models/   # 可序列化数据类、枚举、密封类
  +-- floating/       # 悬浮窗 UI 叠加层管理
  +-- network/        # LLM 通信
  |     +-- adapter/  # 各提供商专用适配器（OpenAI、Anthropic、Google）
  +-- service/        # Android 服务（前台、无障碍、Shizuku）
  +-- ui/             # Jetpack Compose UI
  |     +-- components/   # 可复用 Composable 组件
  |     +-- history/      # 历史记录列表和详情页
  |     +-- home/         # 主页
  |     +-- settings/     # 所有设置页面
  +-- utils/          # 工具类
        +-- shared/   # 共享常量（LaTeX 模式）
```

## 核心技术栈

| 领域 | 技术选型 |
|------|--------|
| 编程语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 导航 | Navigation Compose |
| 持久化 | Room（历史记录） + DataStore（设置） |
| 网络 | OkHttp + SSE |
| 序列化 | Kotlin Serialization |
| DI | 手动（AppContainer） |
| 截图 | MediaProjection / AccessibilityService / Shizuku |
| 后台运行 | LifecycleService + 前台服务 |
| OCR | 基于 LLM（通过配置的提供商，非 ML Kit） |

## 导航路由

| 路由 | 页面 | 描述 |
|-------|--------|-------------|
| `home` | HomeScreen | 主面板 |
| `history` | HistoryScreen | 历史分析记录 |
| `history/detail/{id}` | HistoryDetailScreen | 完整详情视图 |
| `settings` | SettingsScreen | 设置菜单 |
| `settings/general` | GeneralSettingsScreen | 通用偏好设置 |
| `settings/study` | StudyModeSettingsScreen | 学习模式配置 |
| `settings/quick` | QuickModeSettingsScreen | 快速模式配置 |
| `settings/multi_image` | MultiImageSettingsScreen | 多图模式配置 |
| `settings/models` | ModelSettingsScreen | 提供商与助手管理 |
| `settings/providers/edit?id={id}` | ProviderEditScreen | 编辑提供商 |
| `settings/assistants/edit?id={id}` | AssistantEditScreen | 编辑助手 |
| `settings/permissions` | PermissionSettingsScreen | 权限管理 |
| `settings/io` | ImportExportSettingsScreen | 备份/恢复配置 |
| `settings/about` | AboutScreen | 版本、反馈、源码 |
| `settings/tutorial` | TutorialScreen | 使用教程 |
