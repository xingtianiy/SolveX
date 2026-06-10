# 🚀 SolveX - 智能 Android 学习助手

SolveX 是一款专为 Android 打造的开源学习辅助工具。它结合了屏幕捕捉、高精度 OCR
文字识别、以及大语言模型 (LLM) 的 SSE 流式响应技术，旨在为你提供丝滑、高效的题目解答和内容分析体验。

## 🌐 翻译

[English](./README.md) | [简体中文](./README_CN.md)

---

## 🔥 核心功能

* **⚪ 智能悬浮交互**：始终置顶的悬浮球，支持自由拖动和快捷菜单，无需切换应用即可触发核心功能。
* **📸 多样化截屏**：支持 **系统投影**、**无障碍服务**以及 **Shizuku** 多种截图引擎，确保不同环境下的兼容性。
* **👁️ 高精度 OCR**：集成 **Google ML Kit**，实现快速准确的文字提取，支持中英等多国语言。
* **⚡ SSE 流式响应**：基于 **OkHttp** 实现 Server-Sent Events (SSE) 技术，答案“逐字生成”，告别漫长等待。
* **🤖 多模型适配**：内置适配器，支持 **OpenAI** 、**Anthropic** 以及 **Google Gemini**。
* **📚 历史轨迹追踪**：使用 **Room** 数据库自动持久化记录所有识别图片和 AI 对话内容。
* **⚙️ 灵活配置管理**：基于 **Jetpack DataStore** 管理应用偏好设置及模型参数。
* **📢 多渠道反馈**：集成 **GitHub** 和 **Gitee** Issues，方便提交建议或反馈问题。

---

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
│   │   ├── settings/        # 设置与配置相关页面
│   │   └── MainViewModel.kt # 全局状态管理
│   └── utils/               # 工具类
├── gradle/                  # 依赖管理 (Version Catalog)
└── build.gradle.kts         # 构建配置
```

---

## 🛠️ 技术栈

| 领域         | 选型                            |
|:-----------|:------------------------------|
| **开发语言**   | Kotlin                        |
| **UI 框架**  | Jetpack Compose               |
| **OCR 引擎** | Google ML Kit                 |
| **持久化存储**  | Room (数据库) + DataStore (配置)   |
| **网络通讯**   | OkHttp + Kotlin Serialization |
| **异步处理**   | Kotlin Coroutines & Flow      |

---

## 🚀 快速开始

### 环境准备

* **JDK**: 17
* **Android Studio**: 最新稳定版
* **设备**: 建议 Android 12 (API 31) 及以上系统以获得最佳体验

### 构建步骤

1. **克隆代码**: `git clone https://github.com/xingtianiy/SolveX.git`
2. **同步项目**: 使用 Android Studio 打开项目并执行 `Gradle Sync`。
3. **运行安装**: 连接设备后点击 `Run`。

---

## ⭐ 感谢支持

如果你觉得 SolveX 对你有帮助，请给本项目一个 **Star** ⭐️，这也是我们持续维护的动力！

---

**[回到顶部](#🚀-solvex---智能-android-学习助手)**
