# SolveX — 基于 AI 的 Android 学习助手

SolveX 是一款开源的 Android 学习助手，通过截取屏幕截图并调用大语言模型（LLM）进行分析，以悬浮窗叠加层的方式流式返回结果。基于 Kotlin 和 Jetpack Compose 构建。

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-0.0.4--alpha-orange)](version.json)

## 功能特性

- **三种截图引擎：** 系统 MediaProjection、无障碍服务（API 34+）、Shizuku ADB
- **四种模型提供商类型：** OpenAI Chat 兼容、OpenAI Responses API、Anthropic（Claude）、Google Gemini
- **三种分析模式：** 常规学习模式（裁剪 + 逐步推理）、自动速查模式（全屏截图 + 即时答案）、多图模式（批量截图分析）
- **悬浮窗叠加层：** 可拖拽悬浮球（支持自动隐藏与大小调节 24dp~64dp）、侧边抽屉（流式结果展示、多图翻页导航）、截图裁剪选区
- **悬浮球答案显示：** 逐张模式下悬浮球答案与抽屉翻页同步切换；合并模式下每 3.5 秒循环滚动显示各题答案
- **流式响应：** 服务端推送事件（SSE），支持首字超时与错误处理
- **各模式独立模型配置：** 每种运行模式可独立设置提供商和模型（含多图模式专用多模态模型）
- **可配置智能助手：** 自定义 OCR 提取、文本分析和视觉分析的提示词模板
- **历史记录持久化：** Room 数据库存储，支持分页列表、关键词搜索和截图缩略图
- **配置导入导出：** JSON 格式备份，可选择性导出提供商、助手及是否包含 API 密钥
- **智能更新检测：** 多源竞速检查（Gitee / GitHub / JsDelivr），基于 ETag 条件请求、自适应检查频率（1~14
  天）和离线缓存回退
- **应用内通知：** 上下文相关的权限警告、状态指示和新用户引导提示

## 快速开始

### 前置条件

- JDK 17
- Android Studio（最新稳定版）
- 设备：最低 Android 7.0（API 24），推荐 Android 12+（API 31）

### 构建与运行

```bash
# 克隆仓库
git clone https://github.com/xingtianiy/SolveX.git

# 在 Android Studio 中打开项目，执行 Gradle Sync 后即可运行

# 或通过命令行构建：
./gradlew assembleDebug

# 运行测试
./gradlew test

# 构建 Release APK（ABI 拆分：arm64-v8a、armeabi-v7a）
./gradlew assembleRelease
# 输出目录：app/build/outputs/apk/release/
```

---

## 架构

```
com.tianhuiu.solvex/
  ├── capture/           屏幕截图引擎（接口 + 3 种实现）
  ├── data/              数据持久化层
  │   ├── dao/           Room DAO
  │   └── models/        可序列化的数据模型
  ├── floating/          悬浮窗 UI 叠加层管理器
  ├── network/           LLM 通信与适配器
  │   └── adapter/       各提供商专用适配器
  ├── service/           Android 后台服务
  ├── ui/                Jetpack Compose 界面
  │   ├── components/    可复用的 Composable 组件
  │   ├── history/       历史记录列表与详情页
  │   ├── home/          首页
  │   └── settings/      设置页面
  └── utils/             辅助工具类
```

详细代码地图请参阅 [docs/CODEMAPS/INDEX.md](docs/CODEMAPS/INDEX.md)。

---

## 使用入门

### 1. 初始配置

1. **添加 AI 提供商：** 设置 → 模型设置 → 提供商 → 添加。输入 API 基础地址（无需 `/v1` 后缀）和 API
   密钥。目前支持四种提供商类型：OpenAI Chat 兼容、OpenAI Responses API、Anthropic Claude、Google Gemini。
2. **获取模型列表：** 在提供商编辑页点击"获取模型列表"，自动发现可用模型。
3. **选择默认模型：** 为每个提供商分别选择默认的 OCR 模型、文本模型和视觉模型。
4. **选择智能助手：** 在首页点击助手横幅，选择适合当前使用场景的助手。

### 2. 权限设置

前往「设置 → 通用配置」页面选择截图方式：

| 截图方式 | 所需权限 | 备注 |
|---------|---------|------|
| 系统屏幕录制 | 悬浮窗 | 每次启动需手动授权确认 |
| 无障碍截图 | 悬浮窗 + 无障碍服务 | 一次授权，后续静默截图 |
| Shizuku ADB | 悬浮窗 + Shizuku | 需安装 Shizuku 应用 |

> 建议：同时开启通知权限并关闭电池优化，确保后台稳定运行。

### 3. 启动应用

- **单击**首页开始按钮 → 启动常规学习模式（框选题目 → AI 解析 → 展示解题思路）
- **长按**首页开始按钮 → 启动自动速查模式（全屏截图 → AI 分析 → 悬浮球气泡或自动复制答案）
- **首页切换到多图模式**后单击开始按钮 → 启动多图模式，逐张截图后长按提交分析

### 4. 悬浮球手势

| 手势 | 常规/自动模式             | 多图模式         |
|----|---------------------|--------------|
| 单击 | 截取并分析屏幕             | 截取当前屏幕并加入缓冲区 |
| 双击 | 取消当前解析              | 终止当前多图任务     |
| 长按 | 切换文本引擎与视觉引擎         | 提交已有截图开始分析   |
| 拖拽 | 移动悬浮球位置（释放后吸附到最近边缘） | 同左           |

### 5. 工作模式

**常规学习模式：** 截图 → 手动裁剪 → 流式分析（逐步推理）→ 结果在抽屉和通知栏中展示。

**自动速查模式：** 自动全屏截图 → 分析 → 悬浮球气泡展示答案（选择题）或自动复制到剪贴板（其他题型）。

**多图模式：** 在首页切换到多图模式，单击悬浮球逐张截图，长按提交全部图片统一分析。

- **逐张分析（合并开关关闭）：** 每张图独立处理，抽屉顶部显示翻页箭头切换查看各题结果，悬浮球答案随抽屉翻页自动同步切换。
- **合并分析（合并开关开启）：** 多张图一次性提交给模型，输出整合后的完整解答，悬浮球按 3.5
  秒间隔循环滚动显示各题答案。
- **通知：** 在「设置 → 多图模式配置 → 通知与显示」中可开启或关闭分析完成后的系统通知。
- **自动弹出抽屉：** 可在多图模式设置中控制开始分析后是否自动打开抽屉。

### 6. 悬浮球大小调节

进入「设置 → 通用配置 → 显示设置」，拖动「悬浮球大小」滑块可自定义悬浮球尺寸（范围 24dp ~ 64dp），满足不同屏幕密度和操作习惯。

### 7. 自动隐藏悬浮球

进入「设置 → 通用配置 → 显示设置」，开启「隐藏悬浮球」开关后，闲置 5 秒悬浮球自动收缩为半透明条状贴边，单击即可恢复。

### 8. 配置管理

- **导入/导出：** 设置 → 导入/导出，可将提供商和助手配置备份为 JSON 文件或从文件恢复。API 密钥可选择是否包含在导出中。
- **重置：** 在导入/导出页面可将所有设置恢复为默认值。

---

## 技术栈

| 领域 | 技术选型 |
|------|---------|
| 编程语言 | Kotlin |
| UI 框架 | Jetpack Compose + Material 3 |
| 导航 | Navigation Compose |
| 数据持久化 | Room（历史记录） + DataStore（设置） |
| 网络通信 | OkHttp + okhttp-sse |
| 序列化 | Kotlinx Serialization |
| 依赖注入 | 手动（AppContainer） |
| 截图 | MediaProjection / AccessibilityService / Shizuku |
| 后台运行 | LifecycleService + 前台服务 |

## 关键依赖

| 依赖库 | 用途 |
|--------|------|
| androidx.room | 历史记录数据库 |
| okhttp3 + okhttp-sse | HTTP / SSE 客户端 |
| androidx.datastore-preferences | 设置持久化 |
| rikka.shizuku | ADB 级屏幕截图 |
| kotlinx-serialization-json | JSON 序列化 |
| androidx.navigation.compose | 页面路由 |
| androidx.lifecycle | ViewModel + 服务生命周期 |

---

## 版本管理

版本号遵循项目根目录下的 `version.json`：

```json
{
  "versionCode": 4,
  "versionName": "0.0.4-alpha",
  "releaseDate": "2026-06-13",
  "level": "critical",
  "apkSize": "16 MB",
  "updateLog": ["更新日志条目..."],
  "githubUrl": "https://github.com/xingtianiy/SolveX/releases/...",
  "giteeUrl": "https://gitee.com/xingtianiy/SolveX/releases/..."
}
```

更新级别说明：

- `critical`：强制更新，弹窗不可关闭
- `recommended`：建议更新，可推迟
- `optional`：静默提示，无弹窗

更新检测通过竞速请求 3 个源（Gitee、GitHub、JsDelivr），采用基于 ETag 的条件请求，检查间隔为 1~14 天自适应调整。

---

## 文档

- [架构代码地图](docs/CODEMAPS/INDEX.md) — 详细的模块级文档
- 应用内教程 — 设置 → 教程（3 页内容，涵盖上手设置、模型配置和常见问题）

---

## 反馈与贡献

- **问题反馈：** [GitHub Issues](https://github.com/xingtianiy/SolveX/issues) | [Gitee Issues](https://gitee.com/xingtianiy/SolveX/issues)
- **源代码：** [GitHub](https://github.com/xingtianiy/SolveX) | [Gitee](https://gitee.com/xingtianiy/SolveX)
- **许可证：** Apache License Version 2.0
