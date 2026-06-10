# 🚀 SolveX - Intelligent Android Study Assistant

SolveX is an open-source Android learning tool designed to provide a seamless experience for
problem-solving and screen content analysis. It integrates screen capture, high-precision OCR, and
Large Language Model (LLM) streaming responses.

## 🌐 Translations

[English](./README.md) | [简体中文](./README_CN.md)

---

## 🔥 Key Features

* **⚪ Intelligent Floating Interaction**: An always-on-top floating ball that supports dragging and
  quick menus for non-intrusive operations.
* **📸 Diverse Screen Capture**: Supports multiple capture engines including **System**, *
  *Accessibility Service**, and **Shizuku** for better compatibility.
* **👁️ High-Precision OCR**: Powered by **Google ML Kit** for fast and accurate text extraction (
  supports Chinese, English, and more).
* **⚡ SSE Streaming Response**: Real-time answer generation using Server-Sent Events (SSE) via *
  *OkHttp**, reducing perceived wait time.
* **🤖 Multi-Model Support**: Integrated adapters for **OpenAI**, **Anthropic** and **Google Gemini
  **.
* **📚 History Persistence**: Automatically saves capture images and AI dialogues locally using *
  *Room** database.
* **⚙️ Flexible Configuration**: Manages app preferences and model settings via **Jetpack DataStore
  **.
* **📢 Feedback Channels**: Integrated bug reporting and feature requests via **GitHub** and **Gitee
  ** Issues.

---

## 📂 Project Structure

```text
SolveX/
├── app/src/main/java/com/tianhuiu/solvex/
│   ├── capture/             # Screen capture logic (System, A11y, Shizuku engines)
│   ├── data/                # Data layer
│   │   ├── dao/             # Room DAOs for history persistence
│   │   ├── models/          # UI and Data models
│   │   ├── HistoryRepository.kt
│   │   ├── SettingsRepository.kt (DataStore based)
│   │   └── SolveXDatabase.kt # Room database definition
│   ├── network/             # Network and AI logic
│   │   ├── adapter/         # LLM providers (OpenAI, Google, Anthropic)
│   │   ├── ProcessingPipeline.kt # Core OCR + AI orchestration
│   │   └── SseStreamClient.kt    # SSE streaming implementation
│   ├── service/             # Android Services
│   │   ├── MainService.kt        # Foreground service for overlay & logic control
│   │   ├── SolveXAccessibilityService.kt
│   │   └── ShizukuShellService.kt
│   ├── ui/                  # UI layer (Jetpack Compose)
│   │   ├── components/      # Reusable Compose widgets
│   │   ├── history/         # History list and detail screens
│   │   ├── settings/        # Settings and configuration screens
│   │   └── MainViewModel.kt # Global state management
│   └── utils/               # Common utility classes
├── gradle/                  # Dependency management (Version Catalog)
└── build.gradle.kts         # Build configuration
```

---

## 🛠️ Tech Stack

| Domain           | Selection                              |
|:-----------------|:---------------------------------------|
| **Language**     | Kotlin                                 |
| **UI Framework** | Jetpack Compose                        |
| **OCR Engine**   | Google ML Kit                          |
| **Persistence**  | Room (Database) + DataStore (Settings) |
| **Network**      | OkHttp + Kotlin Serialization          |
| **Concurrency**  | Kotlin Coroutines & Flow               |

---

## 🚀 Quick Start

### Requirements

* **JDK**: 17.
* **Android Studio**: latest stable version.
* **Device**: Android 12 (API 31) or higher recommended.

### Build Steps

1. **Clone the Repo**: `git clone https://github.com/xingtianiy/SolveX.git`
2. **Sync Project**: Open in Android Studio and perform `Gradle Sync`.
3. **Run**: Connect your device and click `Run`.

---

## ⭐ Support Us

If you find SolveX helpful, please give us a **Star** ⭐️. It motivates us to keep improving!

---

**[Back to Top](#🚀-solvex---intelligent-android-study-assistant)**
