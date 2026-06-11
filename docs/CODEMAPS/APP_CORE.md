# 应用核心 代码地图

**最后更新：** 2026-06-12
**入口点：** `SolveXApplication.kt`、`MainActivity.kt`、`AppContainer.kt`

## 架构

```
SolveXApplication（Application）
  |
  +-- onCreate() -> 创建 AppContainer
  |
  +-- AppContainer（手动依赖注入）
        +-- OkHttpClient（共享，30s 连接超时 / 60s 读取超时 / 10MB 缓存）
        +-- UnifiedLLMClient
        +-- ProcessingPipeline
        +-- SolveXDatabase（Room，单例）
        +-- HistoryRepository
        +-- AppNotificationManager

MainActivity（ComponentActivity）
  |
  +-- viewModel: MainViewModel（通过 viewModels）
  +-- projectionLauncher（用于 MediaProjection 权限的 ActivityResult）
  +-- onCreate()
  |     +-- handleDeepLink（处理来自通知"查看"操作的深链接）
  |     +-- collect requestMediaProjection flow -> 启动 projectionLauncher
  |     +-- LifecycleEventObserver -> 在 RESUME 时调用 checkPermissions()
  |     +-- setContent { SolveXApp(viewModel) }
  +-- onNewIntent() -> handleDeepLink
```

## 关键模块

| 模块 | 用途 | 导出内容 | 依赖项 |
|--------|---------|---------|--------------|
| `SolveXApplication.kt` | Application 类，初始化 DI 容器 | `container`（internal） | 无 |
| `AppContainer.kt` | 手动 DI 容器，包含所有共享服务 | `okHttpClient`、`unifiedLLMClient`、`processingPipeline`、`database`、`historyRepository`、`appNotificationManager` | Context |
| `MainActivity.kt` | 主入口点，处理深链接和权限请求 | ComponentActivity | MainViewModel、NotificationHelper |

## 构建配置

- **应用 ID：** `com.tianhuiu.solvex`
- **编译 SDK：** 36（Android 16）
- **最低 SDK：** 24（Android 7.0）
- **目标 SDK：** 36
- **当前版本：** 0.0.4-alpha（versionCode 4）
- **签名：** Release 密钥库位于 `E:\xingtian\桌面\JKS\SolveX`
- **ABI 拆分：** arm64-v8a、armeabi-v7a（无通用 APK）
- **ProGuard：** Release 构建启用，使用 `proguard-rules.pro`

## Gradle 插件

- Android Application
- Kotlin Android
- Kotlin Compose
- Kotlin Serialization
- KSP（用于 Room 注解处理）

## 版本目录（libs.versions.toml）

关键依赖及其用途：

| 依赖 | 用途 |
|------------|---------|
| Navigation Compose | 页面路由 |
| Room（runtime + ktx + compiler） | 历史记录数据库 |
| DataStore Preferences | 设置持久化 |
| OkHttp + SSE | 网络请求 + 流式传输 |
| Kotlin Serialization | JSON 解析 |
| ML Kit Chinese | OCR（未使用，遗留依赖） |
| Shizuku（api + provider） | ADB 级屏幕截图 |
| Gson | 遗留 JSON 使用（极少量） |
| Compose BOM + Material 3 | UI 框架 |

## 外部依赖

- Google ML Kit - OCR（已引用，但实际 OCR 使用 LLM）
- Shizuku - 无 Root 的 ADB 授权
- OkHttp - HTTP/SSE 客户端

## 数据流：应用启动

```
Application.onCreate()
  -> AppContainer（DI 初始化）
  -> MainActivity.onCreate()
     -> SolveXApp（Compose NavHost）
        -> MainViewModel.init()
           -> 收集 MainService.isRunning
           -> 收集 repository.appConfigFlow（加载设置）
           -> 安排自适应更新检查（7-14 天间隔）
           -> 加载启动次数
           -> 同步通知状态
        -> HomeScreen 渲染
```

## 相关领域

- [UI 层](UI_LAYER.md) - ViewModel、Compose 页面
- [数据层](DATA_LAYER.md) - 仓库、数据库
