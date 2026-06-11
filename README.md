# 🚀 SolveX - 智能 Android 学习助手

SolveX 是一款专为 Android 打造的开源学习辅助工具。它结合了屏幕捕捉、高精度 OCR
文字识别、以及大语言模型 (LLM) 的 SSE 流式响应技术，旨在为你提供丝滑、高效的题目解答和内容分析体验。


## 🔥 核心功能

* **⚪ 智能悬浮交互**：始终置顶的悬浮球，支持自由拖动和快捷菜单，无需切换应用即可触发核心功能。
* **📸 多样化截屏**：支持 **系统投影**、**无障碍服务**以及 **Shizuku** 多种截图引擎，确保不同环境下的兼容性。
* **👁️ 高精度 OCR**：集成 **Google ML Kit**，实现快速准确的文字提取，支持中英等多国语言。
* **⚡ SSE 流式响应**：基于 **OkHttp** 实现 Server-Sent Events (SSE) 技术，答案"逐字生成"，告别漫长等待。
* **🤖 多模型适配**：内置适配器，支持 **OpenAI** 、**Anthropic** 以及 **Google Gemini**。
* **📚 历史轨迹追踪**：使用 **Room** 数据库自动持久化记录所有识别图片和 AI 对话内容。
* **⚙️ 灵活配置管理**：基于 **Jetpack DataStore** 管理应用偏好设置及模型参数。
* **📢 多渠道反馈**：集成 **GitHub** 和 **Gitee** Issues，方便提交建议或反馈问题。
* **🔄 智能更新检测**：竞速多源检测 + ETag 条件请求 + 分级更新策略，省流量、响应快。

---

## 📦 版本管理

### version.json 格式

项目根目录的 `version.json` 是应用更新的唯一依据，客户端通过竞速请求多源获取：

```json
{
  "versionCode": 1,
  "versionName": "0.0.1-alpha",
  "releaseDate": "2026-06-11",
  "level": "critical",
  "apkSize": "16 MB",
  "updateLog": [
    "多项用户体验优化",
    "优化更新检测逻辑"
  ],
  "githubUrl": "https://github.com/xingtianiy/SolveX/releases/download/v0.0.1-alpha/app-release.apk",
  "giteeUrl": "https://gitee.com/xingtianiy/SolveX/releases/download/v0.0.1-alpha/app-release.apk"
}
```

| 字段            | 类型       | 说明                                                        |
|:--------------|:---------|:----------------------------------------------------------|
| `versionCode` | Int      | 递增的整数版本号，用于比较新旧版本                                         |
| `versionName` | String   | 用户可见的语义版本号，如 `0.0.3-alpha`                                |
| `releaseDate` | String   | 发布日期，格式 `YYYY-MM-DD`                                      |
| `level`       | String   | 更新级别：`critical`（重要，不可关闭）、`recommended`（推荐）、`optional`（可选） |
| `apkSize`     | String   | APK 文件大小，如 `"16 MB"`                                      |
| `updateLog`   | String[] | 更新日志列表，每条单独一行显示                                           |
| `githubUrl`   | String   | GitHub Release 直链下载地址                                     |
| `giteeUrl`    | String   | Gitee Release 直链下载地址                                      |

### 更新检测机制

1. **竞速多源请求**：同时请求 Gitee、GitHub、JsDelivr 三个源，取最快成功响应，避免单点故障。
2. **ETag 条件请求**：请求携带上次响应的 `ETag`，若版本未变化服务端返回 `304 Not Modified`，节省流量。
3. **本地版本缓存**：网络不可用时，使用上次缓存的版本信息兜底，确保用户仍能看到更新提示。
4. **分级更新策略**：`critical` 级别不可关闭弹窗，`recommended`/`optional` 级别可自由关闭。
5. **自适应检查频率**：连续多次无更新自动降低检查频率（最长 14 天），发现更新后自动恢复高频（1 天）。

### 发版流程

1. 构建新版本 APK，修改 `version.json` 中的 `versionCode`、`versionName`、`releaseDate` 等字段。
2. 将 APK 和 `version.json` 一同推送至 GitHub/Gitee Release。
3. 客户端在下次定时检查或用户手动检查时自动发现新版本。

## 📖 使用教程

### 1. 初次配置

1. **添加 AI 提供方**：进入「设置 → 模型设置 → 提供方」，点击添加按钮，填写 API 地址和密钥。
2. **获取模型列表**：在提供方编辑页面点击「获取模型列表」，自动拉取可用模型并智能推荐默认模型。
3. **选择助手**：在首页顶部助手选择器中，选择适合当前场景的助手身份。

### 2. 权限设置

进入「设置 → 权限设置」，根据截屏模式授予相应权限：

| 截屏模式        | 所需权限          | 说明              |
|:------------|:--------------|:----------------|
| 系统屏幕录制      | 悬浮窗           | 每次启动需手动授权录屏     |
| 无障碍截图       | 悬浮窗 + 无障碍服务   | 一次授权，后续静默截图     |
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

## 📂 项目目录结构

```text
SolveX/
├── app/src/main/java/com/tianhuiu/solvex/
│   ├── capture/             # 屏幕捕捉逻辑 (支持系统投影、无障碍、Shizuku 引擎)
│   ├── data/                # 数据持久化层
│   │   ├── dao/             # Room DAO 接口，用于历史记录存储
│   │   ├── models/          # 核心数据与 UI 模型
│   │   ├── HistoryRepository.kt
│   │   ├── SettingsRepository.kt (基于 DataStore 的配置管理)
│   │   └── SolveXDatabase.kt # Room 数据库定义
│   ├── floating/            # 悬浮球与侧边抽屉
│   │   ├── FloatingBallManager.kt
│   │   ├── DrawerManager.kt
│   │   └── DrawerView.kt
│   ├── network/             # 网络通讯与 AI 处理逻辑
│   │   ├── adapter/         # LLM 厂商适配器 (OpenAI, Google, Anthropic)
│   │   ├── ProcessingPipeline.kt # 核心 OCR + AI 调度流水线
│   │   └── SseStreamClient.kt    # SSE 流式通讯实现
│   ├── service/             # Android 系统服务
│   │   ├── MainService.kt        # 前台服务，负责悬浮球显示与核心逻辑控制
│   │   ├── SolveXAccessibilityService.kt
│   │   └── ShizukuShellService.kt
│   ├── ui/                  # 界面层 (Jetpack Compose)
│   │   ├── components/      # 可复用的通用 UI 组件
│   │   ├── history/         # 历史记录列表与详情页面
│   │   ├── home/            # 首页
│   │   ├── settings/        # 设置与配置相关页面
│   │   ├── MainViewModel.kt # 全局状态管理
│   │   └── SolveXApp.kt     # 导航路由
│   └── utils/               # 工具类 (更新管理、通知等)
├── gradle/                  # 依赖管理 (Version Catalog)
└── build.gradle.kts         # 构建配置
```

## 🛠️ 技术栈

| 领域         | 选型                            |
|:-----------|:------------------------------|
| **开发语言**   | Kotlin                        |
| **UI 框架**  | Jetpack Compose               |
| **OCR 引擎** | Google ML Kit                 |
| **持久化存储**  | Room (数据库) + DataStore (配置)   |
| **网络通讯**   | OkHttp + Kotlin Serialization |
| **异步处理**   | Kotlin Coroutines & Flow      |


## 🚀 快速开始

### 环境准备

* **JDK**: 17
* **Android Studio**: 最新稳定版
* **设备**: 建议 Android 12 (API 31) 及以上系统以获得最佳体验

### 构建步骤

1. **克隆代码**: `git clone https://github.com/xingtianiy/SolveX.git`
2. **同步项目**: 使用 Android Studio 打开项目并执行 `Gradle Sync`。
3. **运行安装**: 连接设备后点击 `Run`。

### 运行测试

```bash
# 运行所有单元测试
./gradlew test

# 运行指定测试类
./gradlew test --tests "com.tianhuiu.solvex.data.models.VersionInfoTest"
```

测试报告位于 `app/build/reports/tests/` 目录下。

### 构建发布包

```bash
# 构建 Release APK
./gradlew assembleRelease
```

APK 输出路径：`app/build/outputs/apk/release/`
