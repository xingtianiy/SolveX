# 数据层 代码地图

**最后更新：** 2026-06-12
**入口点：** `data/SolveXDatabase.kt`、`data/HistoryRepository.kt`、`data/SettingsRepository.kt`

## 架构

```
两种持久化系统：

1. Room 数据库（history_items 表）
   AppData/
   +-- SolveXDatabase（Room）
   |     +-- HistoryDao
   |     +-- Converters（Room 的 TypeConverter）
   +-- HistoryRepository
   |     +-- 基于 Flow 的查询
   |     +-- 受 Mutex 保护的更新

2. DataStore Preferences（设置）
   +-- SettingsRepository
         +-- AppConfig（JSON 序列化的配置块）
         +-- 更新元数据（ETag、上次检查时间、缓存版本）
         +-- 启动次数
```

## SolveXDatabase（Room）

```kotlin
@Database(entities = [HistoryItem::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class SolveXDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}
```

- **数据库名称：** `solvex_database`
- **版本：** 2（升级时采用破坏性迁移）
- **单例**，通过双重检查锁定模式实现
- **TypeConverter：** 处理 `imagePaths` 的 `List<String>` 序列化

### HistoryDao

| 查询 | 描述 |
|-------|-------------|
| `getAllHistoryItems()` | 所有条目的 Flow，按时间戳 DESC 排序 |
| `getHistoryCount()` | 条目计数的 Flow |
| `getHistoryItemById(id)` | 单条记录（suspend） |
| `getHistoryItemByIdFlow(id)` | 单条记录的 Flow |
| `insertHistoryItem(item)` | 插入，使用 REPLACE 冲突策略 |
| `deleteHistoryItemById(id)` | 按 ID 删除 |
| `clearHistory()` | 删除全部 |
| `markProcessingAsCancelled(...)` | 清理卡住的处理中条目 |

## HistoryRepository

包装 `HistoryDao`，附加逻辑：

- **受 Mutex 保护的更新：** `updateHistoryItem(id, block)` 使用 Kotlin Mutex 防止并发修改
- **清理：** `cleanupProcessingItems()` 在服务启动时将卡住的 PROCESSING 状态条目标记为 CANCELLED
- **Flow 暴露：** `historyItemsFlow` 用于响应式 UI 更新

## Converters

Room TypeConverter，用于不原生支持的数据类型：

- **`List<String>` <-> `String`** - 通过 JSON 数组序列化 `imagePaths`

## SettingsRepository（DataStore）

将所有应用设置存储为单个 JSON 块以及单独的偏好项键值。

### 键

| 键 | 类型 | 描述 |
|-----|------|-------------|
| `app_config` | String（JSON） | 完整的 AppConfig（提供商、助手、设置） |
| `last_update_check` | Long | 上次更新检查的时间戳 |
| `launch_count` | Int | 应用启动次数 |
| `update_etag` | String | 用于条件更新请求的 ETag |
| `cached_version` | String | 缓存的 version.json，用于离线回退 |
| `consecutive_no_update` | Int | 连续无更新次数 |

### Flow

所有偏好项通过 map 操作暴露为类型化 `Flow`：
- `appConfigFlow: Flow<AppConfig>`
- `lastUpdateCheckFlow: Flow<Long>`
- `launchCountFlow: Flow<Int>`
- `updateEtagFlow: Flow<String?>`
- `cachedVersionFlow: Flow<String?>`
- `consecutiveNoUpdateFlow: Flow<Int>`

### 保存方法

| 方法 | 描述 |
|--------|-------------|
| `saveAppConfig(config)` | 序列化并保存完整配置 |
| `saveLastUpdateCheck(timestamp)` | 持久化上次检查时间 |
| `saveUpdateEtag(etag)` | 保存 ETag 用于条件请求 |
| `saveCachedVersion(json)` | 缓存版本信息用于离线 |
| `saveConsecutiveNoUpdate(count)` | 追踪连续无更新次数 |
| `incrementLaunchCount()` | 增加启动计数 |

## AppConfig 结构

```kotlin
@Serializable
data class AppConfig(
    val providers: List<ModelProvider>,
    val assistants: List<AssistantConfig>,
    val permissions: PermissionSettings,
    val studyConfig: StudyModeConfig,
    val quickConfig: QuickModeConfig,
    val multiImageConfig: MultiImageModeConfig,
    val defaultProviderId: String?,
    val selectedAssistantId: String?,
    val selectedEngine: EngineType,
    val selectedMode: ProjectMode,
    val autoScrollContent: Boolean,
)
```

## 数据流：配置保存

```
ViewModel 设置某个属性（如 setMode(mode)）
  -> 在内存中更新属性
  -> 调用 save()（300ms 防抖）
     -> repository.saveAppConfig(config)
        -> Kotlin Serialization encodeToString
        -> DataStore edit（异步）
           -> UI 通过 appConfigFlow 响应式更新
```

## 相关领域

- [数据模型](DATA_MODELS.md) - 所有模型类
- [工具类](UTILITIES.md) - FileUtils 图片存储
