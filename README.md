# SolveX — 智能屏幕解析助手

[![License](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-0.1.3--alpha-orange)](version.json)

SolveX 是一款基于 Android 平台的 AI 屏幕解析工具。通过悬浮球交互、多引擎截屏、大语言模型流式响应，为用户提供即时的题目解答、内容分析和知识辅助。

## 🔥 核心功能

### 悬浮交互系统

- **全局悬浮球**：支持自由拖拽，通过单击触发解析、双击取消任务、长按切换 OCR/视觉引擎。
- **智能助手抽屉**：沉浸式底栏选择器，支持无限数量助手展示与快捷切换，适配单手操作。
- **配置看板**：首页整合助手与引擎选择，核心配置一目了然。
- **自适应隐藏**：截图前自动隐藏悬浮球，避免干扰识别，任务完成后自动恢复。

### 隐私与隐匿保护

- **防截屏录屏 (FLAG_SECURE)**：开启后 SolveX 自身的悬浮窗在录制中显示为黑色，保护隐私。
- **隐匿模式**：支持从最近任务列表中隐藏，并能通过 Shizuku 实时感知环境（如进入考试 App 时自动增强保护）。

### 智能截屏引擎

- **系统录屏**：标准的 Android 录屏接口，兼容性好。
- **无障碍截图**：基于无障碍服务实现，授予权限后可实现静默、连续截图。
- **Shizuku ADB**：通过 Shizuku 获得 ADB 权限，提供最高效的底层截图能力。
- **屏幕取字**：通过无障碍服务直接提取文字，无需产生截图文件，隐私更安全。

### OCR 与 AI 调度

- **多语种识别**：集成 Google ML Kit，支持中英文及复杂排版的高精度文字提取。
- **长按拖拽排序**：助手及模型提供商支持长按排序，自由定制优先级。
- **统一适配器**：内置 OpenAI、Anthropic、Google Gemini 适配器，完美兼容符合 OpenAI 接口协议的所有提供商。

### 工作模式与历史

- **常规模式**：支持手动框选目标区域，适合需要精细解题的过程展示。
- **自动模式**：全屏快速识别，自动提取答案摘要并复制到剪贴板。
- **持久化历史**：基于 Room 数据库存储所有记录，支持关键词搜索、详情回看和图片预览。

## 📖 使用教程

### 1. 初次配置

1. **添加 AI 提供方**：进入「设置 → 模型供应商」，点击添加按钮，填写 API 地址和密钥。
2. **获取模型列表**：在提供方编辑页面点击「获取模型列表」，自动拉取可用模型并智能推荐默认模型。
3. **管理助手**：进入「设置 → 助手管理」，创建、编辑或通过长按拖动调整助手优先级。
4. **选择助手**：在首页配置看板点击助手名称，在底部抽屉中选择合适的助手。

### 2. 权限设置

进入「设置 → 权限设置」，根据截屏模式授予相应权限：

| 截屏模式        | 所需权限          | 说明              |
|:------------|:--------------|:----------------|
| 系统屏幕录制      | 悬浮窗           | 每次启动需手动授权录屏     |
| 无障碍截图       | 悬浮窗 + 无障碍服务   | 需要保持授权，后续静默截图   |
| Shizuku ADB | 悬浮窗 + Shizuku | 需安装 Shizuku 并授权 |

> 建议同时授予**通知权限**和关闭**电池优化**，以确保后台服务稳定运行。

### 3. 启动服务

- **单击首页启动按钮**：以「常规模式」启动，可手动裁剪截图区域，展示完整解题过程。
- **长按首页启动按钮**：以「自动模式」启动，自动识别并快速返回答案。

### 4. 两种工作模式

**常规模式**（适合学习场景）：

- 截图后可手动裁剪关注区域
- 侧边抽屉实时展示解题思路、知识点和最终答案
- 解析结果自动保存到历史记录

**自动模式**（适合快速查询）：

- 自动截屏并识别，无需手动裁剪
- 悬浮球直接显示答案摘要
- 选择题答案自动复制到剪贴板

### 5. 通用配置

进入「设置 → 通用设置」可调整：

- **屏幕录制方式**：切换截屏引擎
- **抽屉弹出位置**：左侧或右侧
- **跟随内容输出滚动**：开启后抽屉和历史记录自动滚动到最新内容
- **隐藏悬浮球**：无操作时悬浮球自动缩回屏幕边缘

### 6. 历史记录

- 底部导航切换到「历史」标签查看所有解析记录
- 支持关键词搜索、长按删除单条记录
- 点击记录查看完整解析详情，包括截图、解题过程和最终答案

### 7. 更新与反馈

- **检查更新**：进入「设置 → 关于我们 → 版本信息」手动检查，或等待自动检测（7-14 天周期）
- **问题反馈**：进入「设置 → 关于我们 → 问题反馈」选择 GitHub 或 Gitee Issues 提交
- **开源地址**：进入「设置 → 关于我们 → 开源地址」选择 GitHub 或 Gitee 查看源码

## 📦 版本管理

版本号遵循项目根目录下的 `version.json`：

```json
{
  "versionCode": 1,
  "versionName": "0.0.1-alpha",
  "releaseDate": "2026-06-10",
  "level": "recommended",
  "apkSize": "16 MB",
  "updateLog": [
    "更新日志条目...",
    "更新日志条目..."
  ],
  "githubUrl": "https://github.com/xingtianiy/SolveX/releases/download/v0.0.1-alpha/app-release.apk",
  "giteeUrl": "https://gitee.com/xingtianiy/SolveX/releases/download/v0.0.1-alpha/app-release.apk"
}
```

| 字段            | 类型       | 说明                                                        |
|:--------------|:---------|:----------------------------------------------------------|
| `versionCode` | Int      | 递增的整数版本号，用于比较新旧版本                                         |
| `versionName` | String   | 用户可见的语义版本号，如 `0.0.1-alpha`                                |
| `releaseDate` | String   | 发布日期，格式 `YYYY-MM-DD`                                      |
| `level`       | String   | 更新级别：`critical`（重要，不可关闭）、`recommended`（推荐）、`optional`（可选） |
| `apkSize`     | String   | APK 文件大小，如 `"16 MB"`                                      |
| `updateLog`   | String[] | 更新日志列表，每条单独一行显示                                           |
| `githubUrl`   | String   | GitHub Release 直链下载地址                                     |
| `giteeUrl`    | String   | Gitee Release 直链下载地址                                      |

更新检测通过竞速请求 3 个源（Gitee、GitHub、JsDelivr），采用基于 ETag 的条件请求，检查间隔为 1~14 天自适应调整。

## 📂 项目结构

```text
app/src/main/java/com/tianhuiu/solvex/
├── capture/             # 截屏引擎实现 (System / Shizuku / Accessibility)
├── floating/            # 悬浮交互层 (悬浮球、实时抽屉、屏幕裁剪系统)
├── mode/                # 业务模式层 (常规解析与自动全屏模式逻辑)
├── network/             # 网络与 AI 层 (LLM 适配器、SSE 客户端、流水线)
├── service/             # 后台服务 (MainService、Shizuku 辅助、无障碍服务)
├── ui/                  # 界面表现层 (基于 Jetpack Compose)
│   ├── home/            # 首页控制中心（独立通知栏组件）
│   ├── history/         # 历史记录列表与多维详情展示
│   └── settings/        # 系统配置、模型管理与关于页面
├── data/                # 数据持久化 (Room 数据库、DataStore 偏好设置)
└── utils/               # 工具类 (文件、通知、日期、自动化工具)
```

## 🛠️ 技术栈和依赖

| 领域    | 技术选型                                             |
|-------|--------------------------------------------------|
| 编程语言  | Kotlin                                           |
| 导航    | Navigation Compose                               |
| UI 框架 | Jetpack Compose + Material 3                     |
| 数据持久化 | Room（历史记录） + DataStore（设置）                       |
| 网络通信  | OkHttp + okhttp-sse                              |
| 序列化   | Kotlinx Serialization                            |
| 依赖注入  | 手动（AppContainer）                                 |
| 截图    | MediaProjection / AccessibilityService / Shizuku |
| 后台运行  | LifecycleService + 前台服务                          |

| 依赖库                            | 用途                 |
|--------------------------------|--------------------|
| androidx.room                  | 历史记录数据库            |
| okhttp3 + okhttp-sse           | HTTP / SSE 客户端     |
| androidx.datastore-preferences | 设置持久化              |
| rikka.shizuku                  | ADB 级屏幕截图          |
| kotlinx-serialization-json     | JSON 序列化           |
| androidx.navigation.compose    | 页面路由               |
| androidx.lifecycle             | ViewModel + 服务生命周期 |

## 🚀 快速开始

### 前置条件

- JDK 17
- Android Studio（最新稳定版）
- 设备：推荐 Android 12 及以上设备或模拟器（项目 minSdk = 31）

### 构建与运行

```bash
# 克隆仓库
git clone https://github.com/xingtianiy/SolveX.git

# 在 Android Studio 中打开项目，执行 Gradle Sync 后即可运行

# 或通过命令行构建：
./gradlew assembleDebug

# 运行测试
./gradlew test

# 构建 Release APK
./gradlew assembleRelease
# 输出目录：app/build/outputs/apk/release/
```

## 📜 开发者与许可

- **开源协议**: Apache License 2.0
- **源码地址**: [GitHub](https://github.com/xingtianiy/SolveX) / [Gitee](https://gitee.com/xingtianiy/SolveX)
- **反馈渠道**: 请通过 GitHub Issues 提交建议或 BUG 反馈。
