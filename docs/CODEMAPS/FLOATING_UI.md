# 悬浮 UI 代码地图

**最后更新：** 2026-06-12
**入口点：** `floating/FloatingBallManager.kt`、`floating/DrawerManager.kt`、`floating/CropManager.kt`

## 架构

```
基于 WindowManager 的叠加层系统（TYPE_APPLICATION_OVERLAY）

FloatingBallManager           DrawerManager              CropManager
  +-- FloatingBallView          +-- DrawerView              +-- CropView
  +-- FloatingTouchListener     +-- 实时缓冲区（查询+结果）
  +-- 自动隐藏计时器             +-- 多图分页
  +-- 状态（IDLE/RUNNING/等）    +-- 数据库观察 Flow
  +-- 多图计数徽章
```

## FloatingBallManager

管理悬浮球叠加层，提供主要的用户交互入口。

### 状态

```kotlin
enum class BallStatus { IDLE, RUNNING, SUCCESS, ERROR, MULTI_IMAGE }
enum class BallDisplayMode { FULL, HIDDEN_STRIP }
```

### 手势处理

| 手势 | 条件 | 操作 |
|---------|-----------|--------|
| 单击 | 条状模式 | 展开为完整模式 |
| 单击 | 完整模式、空闲 | 触发截图 + 分析 |
| 单击 | 完整模式、运行中 | 打开抽屉显示当前进度 |
| 单击 | 多图模式 | 截图并添加到缓冲区 |
| 双击 | 完整模式 | 取消当前处理 / 退出多图模式 |
| 长按 | 完整模式、空闲 | 切换引擎（TEXT/VISION）或进入多图模式 |
| 拖拽 | 任意 | 移动悬浮球；释放时吸附到最近边缘 |

### 自动隐藏行为

- 最后一次交互 5 秒后，悬浮球缩小为 10dp 的条状
- 可在设置中关闭（`enableAutoHideBall`）
- SUCCESS/ERROR 状态后，5 秒自动重置为 IDLE
- 拖拽期间，隐藏计时器取消

### 显示

- 使用 Jetpack Compose `ComposeView` 构建，添加到 `WindowManager`
- 布局参数：`WRAP_CONTENT` x `WRAP_CONTENT`，位置为 `TOP|START`
- 使用 `FLAG_NOT_FOCUSABLE` 和硬件加速

### 关键方法

| 方法 | 描述 |
|--------|-------------|
| `show()` | 将 ComposeView 添加到 WindowManager |
| `hide()` | 从 WindowManager 移除，置空引用 |
| `tempHide()` / `restore()` | 截图前隐藏，截图后恢复 |
| `updateStatus(status)` | 改变悬浮球外观 |
| `showText(text)` | 显示临时文本气泡 |
| `enterMultiImageMode()` | 切换到多图徽章模式 |
| `updateBadgeCount(count)` | 更新多图计数徽章 |

## DrawerManager

管理侧边抽屉叠加层，显示流式分析结果。

### 功能特性

- 从左或右侧滑入（可配置）
- 宽度可调（屏幕的 30%-90%，默认 90%）
- 显示截图缩略图 + 分析结果
- 多图模式：页面导航（上/下页）、每页独立内容
- 合并模式：所有页面共享同一流式内容
- 实时内容流式更新，通过 `appendLiveQuery()` / `appendLiveResult()` 实现
- 观察 `HistoryRepository` 的持久化更新
- 全屏半透明背景，点击关闭

### 多图支持

- `setImagePaths(paths)` - 初始化多图页面
- `setMergeMode(enabled)` - 切换合并/逐页模式
- `prevImage()` / `nextImage()` - 页面导航
- `setProcessingPage(page)` - 追踪当前正在处理的页面
- 内容渲染：合并模式共享一个流，逐页模式按图片索引分别追踪

### 关键方法

| 方法 | 描述 |
|--------|-------------|
| `show(historyId, side, widthPercent, ...)` | 打开抽屉并观察对应条目 |
| `hide()` | 关闭抽屉（保留 ComposeView 以便快速重新打开） |
| `appendLiveQuery(delta)` | 实时更新查询内容 |
| `appendLiveResult(delta)` | 实时更新结果内容 |
| `clearLiveBuffer()` | 重置实时查询/结果缓冲区 |
| `isShowing()` | 检查抽屉是否可见 |

## CropManager

管理裁剪叠加层，用于从截图中选择感兴趣的区域。

### 流程

1. `suspend fun crop(bitmap): Bitmap?` - 挂起协程直到用户操作
2. 以全屏叠加层形式显示 CropView 与截图
3. 用户可裁剪、使用完整图片或取消
4. 返回裁剪后的 Bitmap、原始 Bitmap 或 null（取消）
5. 使用 `suspendCancellableCoroutine` 桥接

### 回调

| 回调 | 结果 |
|----------|--------|
| `onConfirm(cropped)` | 用户选择了一个区域 |
| `onUseFull(bitmap)` | 用户选择使用完整图片 |
| `onCancel` | 用户取消，返回 null |

## WindowManager 配置（所有管理器通用）

```kotlin
WindowManager.LayoutParams().apply {
    type = TYPE_APPLICATION_OVERLAY（API 26+）/ TYPE_PHONE（旧版）
    format = PixelFormat.TRANSLUCENT
    flags = NOT_FOCUSABLE | LAYOUT_IN_SCREEN | WATCH_OUTSIDE_TOUCH | HARDWARE_ACCELERATED
}
```

## 相关领域

- [服务层](SERVICE_LAYER.md) - 创建和管理各管理器
- [处理流水线](PROCESSING_PIPELINE.md) - 提供流式内容
- [UI 层](UI_LAYER.md) - FloatingBallView、DrawerView、CropView Composable 组件
