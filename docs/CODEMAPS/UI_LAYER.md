# UI 层 代码地图

**最后更新：** 2026-06-12
**入口点：** `ui/SolveXApp.kt`、`ui/MainViewModel.kt`

## 架构

```
Navigation Compose 配合底部导航（首页 | 历史 | 设置）

SolveXApp（NavHost，startDestination = "home"）
  |
  +-- MainViewModel（AndroidViewModel，跨页面共享）
  |     +-- 应用配置状态（提供商、助手、模式、权限）
  |     +-- 服务控制（启动/停止）
  |     +-- 更新管理（检查、下载、安装）
  |     +-- 配置导入/导出
  |     +-- 提供商连接性测试
  |
  +-- HomeScreen（底部导航标签 1）
  |     +-- 助手选择器下拉菜单
  |     +-- 应用内通知栈（权限、教程、状态）
  |     +-- 引擎选择器（TEXT vs VISION）
  |     +-- 启动/停止服务按钮（点击 = 学习模式，长按 = 快速模式）
  |
  +-- HistoryScreen（底部导航标签 2）
  |     +-- HistoryViewModel（分页、搜索、删除）
  |     +-- 分页 LazyColumn + 自动滚动
  |     +-- 搜索栏
  |     +-- 带缩略图的 HistoryCard
  |     +-- 清除全部 / 删除单个，附带确认对话框
  |
  +-- HistoryDetailScreen（导航：history/detail/{id}）
  |     +-- 完整分析视图，使用 OutputRenderer
  |     +-- 截图缩略图
  |     +-- 返回导航
  |
  +-- SettingsScreen（底部导航标签 3）
  |     +-- 分组设置列表导航
  |
  +-- 设置子页面（导航：settings/{subpage}）
        +-- GeneralSettingsScreen
        |     +-- 屏幕录制方式、抽屉侧边/宽度
        |     +-- 自动隐藏悬浮球、自动滚动
        |
        +-- StudyModeSettingsScreen
        |     +-- 模型覆盖、超时、通知设置
        |
        +-- QuickModeSettingsScreen
        |     +-- 模型覆盖、多图开关
        |
        +-- MultiImageSettingsScreen
        |     +-- 合并模式、专用视觉模型、通知模式
        |
        +-- ModelSettingsScreen
        |     +-- 默认提供商选择器
        |     +-- 提供商列表（添加/编辑/删除/测试连接性）
        |     +-- 助手列表（添加/编辑/删除）
        |     +-- ModelPreviewDialog（搜索/筛选/复制）
        |
        +-- ProviderEditScreen
        |     +-- 编辑提供商名称、URL、API 密钥、类型
        |     +-- 获取模型列表
        |     +-- 为每种角色选择默认模型
        |
        +-- AssistantEditScreen
        |     +-- 编辑名称、OCR/文本/视觉提示词
        |
        +-- PermissionSettingsScreen
        |     +-- 授予悬浮窗、通知、无障碍、Shizuku 权限
        |
        +-- ImportExportSettingsScreen
        |     +-- 选择要导出的提供商/助手
        |     +-- 包含/排除 API 密钥
        |     +-- 从 JSON 文件导入
        |     +-- 恢复出厂设置
        |
        +-- AboutScreen
        |     +-- 版本信息 + 手动检查更新
        |     +-- 反馈（GitHub/Gitee Issues）
        |     +-- 源代码（GitHub/Gitee）
        |     +-- 许可证
        |
        +-- TutorialScreen
              +-- 从 assets/tutorial/*.md 加载的 Markdown 教程页面
              +-- 使用 MathView 渲染器分页显示
```

## MainViewModel

跨所有页面共享的全局 ViewModel。核心职责：

### 状态属性

| 属性 | 类型 | 描述 |
|----------|------|-------------|
| `providers` | List<ModelProvider> | 所有已配置的 LLM 提供商 |
| `assistants` | List<AssistantConfig> | 所有已配置的助手 |
| `permissions` | PermissionSettings | 当前权限设置 |
| `selectedAssistantId` | String? | 当前选中助手 |
| `selectedEngine` | EngineType | TEXT 或 VISION 引擎 |
| `selectedMode` | ProjectMode | STUDY、QUICK 或 MULTI_IMAGE |
| `isServiceRunning` | Boolean | MainService 状态（来自 StateFlow） |
| `activeMode` | ProjectMode? | 服务启动时的模式 |
| `updateInfo` | VersionInfo? | 待处理的更新信息 |
| `downloadStatus` | DownloadStatus | APK 下载进度 |
| `connectivityTestStates` | Map<String, ConnectivityTestState> | 每个提供商的测试状态 |

### 关键方法

| 方法 | 描述 |
|--------|-------------|
| `startService()` | 根据截图模式权限启动 MainService |
| `startMainService(code, data)` | 携带投影数据启动前台服务 |
| `stopService()` | 停止服务 |
| `checkForUpdates(manual)` | 检查新版本（竞速 3 个源） |
| `startUpdate()` | 下载并安装 APK |
| `resetToDefault()` | 将所有配置重置为默认值 |
| `exportConfig(...)` | 将提供商/助手序列化为 JSON |
| `importConfig(data)` | 将导入的配置与当前合并 |
| `testConnectivity(provider)` | 测试提供商 API 连接性 |
| `fetchModelsForProvider(provider)` | 获取并缓存模型列表 |
| `checkPermissions()` | 同步所有权限状态 |
| `dismissInAppNotification(id)` | 关闭主页通知 |

## 可复用组件（ui/components/）

| 组件 | 描述 |
|-----------|-------------|
| `MathView` | LaTeX/Markdown 渲染器（WebView + MathJax） |
| `OutputRenderer` | 渲染以 ### 分隔的 AI 分析输出 |
| `DetailSection` | 包含标题 + 内容的单段卡片 |
| `StatusBadge` | 分析状态标签（颜色编码） |
| `SettingsGroup` / `SettingsItem` | 设置列表分组和项目 Composable |
| `UpdateDialog` | 更新可用对话框，含下载进度 |
| `SolveXDialog` / `SolveXConfirmDialog` | 可复用对话框 Composable |
| `CommonComponents.kt` | StatusBadge、SolveXDialog、SolveXConfirmDialog |

## HistoryViewModel

| 属性/方法 | 描述 |
|----------------|-------------|
| `historyItems` | 分页条目（每页 20 条，响应式） |
| `totalCount` | 条目总数 |
| `storageSize` | 图片总存储大小 |
| `loadMore()` | 增加一页（+20） |
| `deleteItem(id)` | 删除单条记录 |
| `clearHistory()` | 清除所有条目和图片 |

## 导航图

```
home（起始页）
  |
  +-- history
  |     +-- history/detail/{id}
  |
  +-- settings
        +-- settings/general
        +-- settings/study
        +-- settings/quick
        +-- settings/multi_image
        +-- settings/models
        |     +-- settings/providers/edit?id={id}
        |     +-- settings/assistants/edit?id={id}
        +-- settings/permissions
        +-- settings/io
        +-- settings/about
        +-- settings/tutorial
```

## 相关领域

- [数据模型](DATA_MODELS.md) - UI 层消费的模型
- [服务层](SERVICE_LAYER.md) - 从 ViewModel 控制的服务
- [处理流水线](PROCESSING_PIPELINE.md) - 结果在 UI 中展示
