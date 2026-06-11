# 工具类 代码地图

**最后更新：** 2026-06-12
**源文件：** `utils/` 目录

## 模块地图

| 文件 | 用途 |
|------|---------|
| `UpdateManager.kt` | 版本检查、APK 下载、安装 |
| `NotificationHelper.kt` | 系统通知 + Toast 辅助方法 |
| `FileUtils.kt` | Bitmap 读写、资源文件读取、存储空间大小 |
| `SystemUtils.kt` | 剪贴板、振动、无障碍服务检查 |
| `ResponseParser.kt` | LLM 响应解析（分段、JSON、摘要） |
| `AppNotificationManager.kt` | 应用内通知状态管理 |
| `shared/LatexPatterns.kt` | LaTeX/Markdown 共享正则表达式 |

## UpdateManager

### 版本源（竞速机制）

| 源 | URL |
|--------|-----|
| Gitee | `https://gitee.com/xingtianiy/SolveX/raw/main/version.json` |
| GitHub | `https://raw.githubusercontent.com/xingtianiy/SolveX/main/version.json` |
| JsDelivr | `https://cdn.jsdelivr.net/gh/xingtianiy/SolveX@main/version.json` |

### 检查更新流程

```
checkUpdate(etag) -> Result<Pair<VersionInfo, String?>>
  +-- 同时向 3 个源发起异步请求
  +-- 获取第一个成功响应
  +-- 如果全部失败，返回失败
  |
  +-- ETag 处理：
        +-- 发送 If-None-Match 附带已保存的 ETag
        +-- 304 Not Modified -> NotModifiedException
        +-- 新 ETag 保存供下次检查使用

downloadApk(githubUrl, giteeUrl) -> Flow<DownloadStatus>
  +-- 优先尝试 Gitee，回退到 GitHub
  +-- Content-Type 检查（拒绝 text/html -> 重定向到错误页面）
  +-- 64KB 缓冲区，进度上报
  +-- 文件完整性检查，对照 Content-Length

installApk(apkPath) -> 启动系统安装器
  +-- 使用 FileProvider 共享 URI
  +-- Intent.ACTION_VIEW 附带 application/vnd.android.package-archive
```

### 自适应检查频率

| 条件 | 间隔 |
|-----------|----------|
| 连续无更新 >= 3 次 | 14 天 |
| 已找到更新 | 1 天 |
| 默认 | 7 天 |

## NotificationHelper

| 方法 | 描述 |
|----------|-------------|
| `showToast(context, message)` | 短 Toast 提示 |
| `showFeedback(context, userMessage, ...)` | Toast + Log 组合反馈 |
| `sendResultNotification(context, title, content, historyId)` | 系统通知，附带"查看"操作 |

通知渠道：`solvex_result_channel`（"分析结果通知"，IMPORTANCE_DEFAULT）

"查看"操作通过发送到 `MainService` 的 `ACTION_VIEW_HISTORY` Intent 打开抽屉。

## AppNotificationManager

管理 HomeScreen 上显示的应用内通知。

### 通知类型（按优先级排序）

1. **权限类**（优先级 10） - 缺少权限（悬浮窗、通知、无障碍、Shizuku）
2. **就绪状态类**（优先级 5） - 服务就绪/运行状态
3. **引导类**（优先级 3） - 新用户教程引导（前 3 次启动）

### 同步逻辑

`syncAll()` 方法评估当前状态并生成最多 3 条按优先级排序的通知：
- 权限警告（与当前截图模式相关）
- 就绪状态（所有权限满足后）
- 教程引导（启动次数 1-3）

## FileUtils

| 方法 | 描述 |
|----------|-------------|
| `saveBitmapToInternal(context, bitmap)` | 保存 JPEG 到 `filesDir/history_images/` |
| `getHistoryStorageSize(context)` | 计算图片总存储大小（字节） |
| `formatFileSize(size)` | 人类可读的文件大小字符串 |
| `clearHistoryImages(context)` | 删除图片目录 |
| `readAssetFile(context, fileName)` | 从 assets 目录读取文本文件 |

Bitmap 扩展：
- `Bitmap.toBase64Jpeg(quality)` -> Base64 编码的 JPEG 字符串

## SystemUtils

| 方法 | 描述 |
|----------|-------------|
| `isAccessibilityServiceEnabled(context)` | 检查无障碍服务是否已启用 |
| `copyToClipboard(context, text)` | 复制文本到系统剪贴板 |
| `vibrate(context, ms)` / `vibrateSuccess` / `vibrateError` | 触觉反馈 |

## ResponseParser

（详细的函数列表请参阅 [处理流水线](PROCESSING_PIPELINE.md)）

核心职责：
- 按 `### Title` 标题分割 AI 输出
- 提取最终答案（最后一段，去除 LaTeX/Markdown）
- 解析自动化 JSON 响应
- 从多图输出中提取各题目内容
- 解析结构化题目 JSON

## shared/LatexPatterns

LaTeX 和 Markdown 处理的集中式正则表达式模式：

| 模式 | 用途 |
|---------|---------|
| `latexEnvRegex` | `\begin{...}...\end{...}` 环境 |
| `displayMathRegex` | `$$...$$` |
| `inlineMathRegex` | `$...$` |
| `latexCmdRegex` | 常见 LaTeX 命令 |
| `mdHeadingRegex` / `mdBoldRegex` 等 | Markdown 语法移除 |
| `mdLinkRegex` | `[text](url)` -> 提取文本 |
| `whitespaceRegex` | 折叠空白字符 |
| `displayMathProtectRegex` | 包裹行间数学公式以保护 MathJax 渲染 |

## 相关领域

- 所有其他代码地图（工具类的使用贯穿全局）
