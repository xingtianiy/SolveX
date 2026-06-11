# 服务层 代码地图

**最后更新：** 2026-06-12
**入口点：** `service/MainService.kt`、`service/SolveXAccessibilityService.kt`、`service/ShizukuShellService.kt`

## 架构

```
MainService（LifecycleService）
  +-- 前台服务（NOTIFICATION_ID=1001）
  +-- 管理：FloatingBallManager、DrawerManager、CropManager、ScreenCaptureEngine
  +-- 处理：ACTION_START、ACTION_STOP、ACTION_VIEW_HISTORY
  +-- 编排截图 -> 处理 -> 展示生命周期

SolveXAccessibilityService（AccessibilityService）
  +-- 单例实例，供 AccessibilityCaptureEngine 访问
  +-- API 34+ takeScreenshot() 包装器

ShizukuShellService
  +-- 基于 AIDL 的服务，用于 Shizuku 用户服务通信
```

## MainService

核心后台服务，将截图、处理和展示串联在一起。

### Intent 操作

| 操作 | 描述 |
|--------|-------------|
| `ACTION_START` | 以截图引擎启动前台服务 |
| `ACTION_STOP` | 停止服务并清理 |
| `ACTION_VIEW_HISTORY` | 为指定历史记录项打开抽屉（来自通知） |

### 生命周期

```
onCreate():
  +-- 设置 _isRunning = true
  +-- 初始化仓库、流水线、各管理器
  +-- 清理卡住的"处理中"历史记录项
  +-- 配置 FloatingBallManager 回调：
  |     +-- onSingleClick：截图 + 处理（或进入多图模式）
  |     +-- onDoubleClick：取消处理（或退出多图模式）
  |     +-- onLongClick：进入多图模式或切换引擎
  +-- 观察 appConfigFlow 获取自动隐藏设置

onStartCommand():
  +-- ACTION_START -> startAsForeground(intent)
  |     +-- 创建通知渠道
  |     +-- 构建前台通知（"SolveX 运行中"）
  |     +-- 根据截图模式选择 FOREGROUND_SERVICE_TYPE
  |     +-- 根据模式创建截图引擎
  |     +-- 显示悬浮球
  |
  +-- ACTION_STOP -> stopSelf()
  +-- ACTION_VIEW_HISTORY -> 打开抽屉并显示对应历史记录项

onDestroy():
  +-- 设置 _isRunning = false
  +-- 释放截图引擎
  +-- 隐藏悬浮球
  +-- 取消处理任务
  +-- 将卡住的"处理中"项标记为取消
```

### 截图 + 处理流程（onSingleClick）

```
1. tempHide() 悬浮球
2. delay(100ms)
3. captureEngine.capture()
4. restore() 悬浮球
5. 如果 bitmap 为空：显示对应模式的错误通知
6. 如果 bitmap 有效：
   a. 如果是 STUDY_MODE：cropManager.crop(bitmap) -> 裁剪后的图片（取消则为 null）
   b. 创建 HistoryItem（status=PROCESSING）
   c. 可选：自动打开抽屉
   d. pipeline.process(config, bitmap, callbacks...)
      - onSummaryGenerated：在数据库中更新标题/摘要
      - onQueryExtracted：追加到实时缓冲区，节流更新数据库
      - onDelta：追加到实时缓冲区，节流更新数据库
   e. 成功时：
      - 执行自动化操作（复制/气泡显示）
      - 发送通知
      - 更新 HistoryItem status=SUCCESS
   f. 失败时：
      - 更新 HistoryItem status=FAILURE
      - 发送错误通知
```

### 多图流程

```
enterMultiImageMode():
  +-- 设置 isMultiImageMode 标志
  +-- 清空缓冲区
  +-- floatingBallManager.enterMultiImageMode()

captureAndBuffer()（每次单击时）：
  +-- 截取屏幕
  +-- 可选裁剪（取决于 multiImageCropEnabled）
  +-- 添加到缓冲区
  +-- 更新徽章计数

sendMultiImageBuffer()（多图模式下长按时）：
  +-- 清除多图状态
  +-- pipeline.processMultiImage(config, bitmaps, callbacks...)
  +-- 与单图相同的成功/失败处理逻辑
```

### 通知渠道

- **渠道 ID：** `main_service_channel`
- **渠道名称：** "后台核心服务"
- **重要性：** LOW
- **描述：** "保持 SolveX 在后台运行以进行屏幕分析"
- **前台类型：** `MEDIA_PROJECTION`（系统模式）或 `DATA_SYNC`（其他模式）

## SolveXAccessibilityService

**用途：** 通过 Android 无障碍服务 API 提供静默截图能力。

| 方面 | 详情 |
|--------|--------|
| API 级别 | 34+（`takeScreenshot` 所需） |
| 连接 | 单例：`instance` 静态字段 |
| 截图 | `takeScreenshotCompat()` -> suspendCancellableCoroutine |
| 处理 | 通过 `screenshotExecutor` 在后台线程执行 |
| 清理 | `onDestroy()` 设置 instance = null |

实际的截图引擎（`AccessibilityCaptureEngine`）直接访问该单例实例。

## ShizukuShellService 与 ShizukuUserServiceClient

**用途：** 在 Shizuku 进程中运行用户服务以执行 Shell 命令。

- **ShizukuShellService.kt：** Binder 服务定义（AIDL）
- **ShizukuUserServiceClient.kt：** Shizuku 用户服务的客户端包装器
- **ShizukuShellScreencap.kt：** 通过 Shizuku 进程执行 `screencap -p`

截图命令通过 `Shizuku.newProcess(new String[]{"sh", "-c", "screencap -p"}, ...)` 创建 Shell 进程并读取标准输出获取原始 PNG 字节。

## 相关领域

- [屏幕截图](SCREEN_CAPTURE.md) - MainService 使用的截图引擎
- [悬浮 UI](FLOATING_UI.md) - 由 MainService 创建和控制的管理器
- [处理流水线](PROCESSING_PIPELINE.md) - 由 MainService 调用进行分析
