# 屏幕截图 代码地图

**最后更新：** 2026-06-12
**入口点：** `capture/ScreenCaptureEngine.kt`、`service/MainService.kt`

## 架构

```
ScreenCaptureEngine（接口）
  +-- prepare()  -- 可选的资源初始化
  +-- capture()  -> Bitmap?
  +-- release()  -- 清理

实现类：
  +-- SystemCaptureEngine    -- MediaProjection API
  +-- AccessibilityCaptureEngine -- AccessibilityService（API 34+）
  +-- ShizukuCaptureEngine   -- Shizuku ADB screencap -p
```

## 关键模块

### ScreenCaptureEngine.kt（接口）

```kotlin
interface ScreenCaptureEngine {
    suspend fun prepare() {}
    suspend fun capture(): Bitmap?
    fun release()
}
```

所有截图策略的基础约定。每个引擎必须实现 `capture()`，返回一个 `Bitmap` 或失败时返回 `null`。

### SystemCaptureEngine.kt

**截图方法：** `MediaProjection` + `VirtualDisplay` + `ImageReader`

| 方面 | 详情 |
|--------|--------|
| 权限 | 系统屏幕录制对话框（每次会话） |
| API 级别 | 21+（MediaProjection） |
| 优点 | 最佳兼容性，标准 Android API |
| 缺点 | 每次启动服务需手动授权 |
| 分辨率 | 来自 WindowManager 的实际显示尺寸 |
| 超时 | 获取 ImageReader 缓冲区超时 200ms |

**生命周期：**
```
prepare() -> getMediaProjection -> createVirtualDisplay -> ImageReader
capture() -> reader.acquireNextImage() -> 处理 plane -> Bitmap（裁剪掉填充边距）
release() -> 关闭 ImageReader -> 释放 VirtualDisplay -> 停止 MediaProjection
```

使用 `withTimeoutOrNull(200L)` 获取图像以避免卡住。

### AccessibilityCaptureEngine.kt

**截图方法：** `AccessibilityService.takeScreenshot()`（API 34+）

| 方面 | 详情 |
|--------|--------|
| 权限 | 无障碍服务（一次性授权） |
| API 级别 | 34+（UPSIDE_DOWN_CAKE） |
| 优点 | 静默截图，无需重复弹窗 |
| 缺点 | 仅 API 34+；需启用服务 |

委托给单例 `SolveXAccessibilityService.instance?.takeScreenshotCompat()`。

### ShizukuCaptureEngine.kt

**截图方法：** 通过 Shizuku IPC 的 Shell 命令 `screencap -p`

| 方面 | 详情 |
|--------|--------|
| 权限 | Shizuku 运行时 + 应用授权 |
| API 级别 | 23+（Shizuku 最低要求） |
| 优点 | 一次性授权，可截取受保护内容 |
| 缺点 | 需安装并运行 Shizuku 应用 |

调用 `ShizukuShellScreencap.capturePng()`，其流程如下：
1. 检查 Shizuku 进程（binder 存活检测）
2. 调用 `Shizuku.newProcess(new String[]{"sh", "-c", "screencap -p"},...)`
3. 从进程标准输出读取原始 PNG 字节
4. 返回由 `BitmapFactory` 解码的 `ByteArray`

## 截图引擎选择（在 MainService 中）

```kotlin
// 由 PermissionSettings 中的 CaptureMode 决定
captureEngine = when (captureMode) {
    CaptureMode.SHIZUKU -> ShizukuCaptureEngine()
    CaptureMode.ACCESSIBILITY -> AccessibilityCaptureEngine()
    else -> SystemCaptureEngine(this, resultCode, data)
}
```

截图模式在 `设置 -> 权限设置 -> 屏幕录制方式` 中设置。

## 引擎切换

用户可通过长按悬浮球在 `TEXT_ENGINE` 和 `VISION_ENGINE` 之间切换。这**不会**改变截图引擎，只改变处理路线（OCR+文本 vs. 直接多模态）。

## 流程：单次截图周期（从悬浮球点击触发）

```
1. FloatingBallManager.onSingleClick()
2. tempHide() 悬浮球（将其隐藏在截图范围之外）
3. delay(100ms) 等待 WindowManager 生效
4. captureEngine.capture() -> Bitmap?
5. restore() 悬浮球
6. 如果为 null：根据截图模式显示对应的错误通知
7. 如果成功：
   a. 如果是 STUDY_MODE：CropManager.crop(bitmap) -> 裁剪后的 bitmap
   b. ProcessingPipeline.process(config, bitmap, callbacks...)
```

## 错误处理

当截图失败时，错误通知会包含模式相关的提示：
- **系统模式：** "请确认已授予屏幕录制权限"
- **无障碍模式：** "请确认无障碍服务已启用"
- **Shizuku 模式：** "请确认 Shizuku 已连接并授权"

## 外部依赖

- `Shizuku`（rikka.shizuku） - Shizuku API 库
- `Android MediaProjection API` - 系统框架 API

## 相关领域

- [服务层](SERVICE_LAYER.md) - MainService 编排截图生命周期
- [悬浮 UI](FLOATING_UI.md) - 通过悬浮球手势触发
- [数据模型](DATA_MODELS.md) - `CaptureMode` 常量、`EngineType`
