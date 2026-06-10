package com.tianhuiu.solvex.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tianhuiu.solvex.data.models.AppConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 设置仓库：基于 Jetpack DataStore 持久化应用全局配置（模型、助手、权限等）。
 */
class SettingsRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val APP_CONFIG_KEY = stringPreferencesKey("app_config")
    private val LAST_UPDATE_CHECK_KEY =
        androidx.datastore.preferences.core.longPreferencesKey("last_update_check")

    val appConfigFlow: Flow<AppConfig> = context.dataStore.data.map { preferences ->
        val jsonStr = preferences[APP_CONFIG_KEY]
        if (jsonStr != null) {
            try {
                json.decodeFromString<AppConfig>(jsonStr)
            } catch (e: Exception) {
                AppConfig()
            }
        } else {
            AppConfig()
        }
    }

    val lastUpdateCheckFlow: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[LAST_UPDATE_CHECK_KEY] ?: 0L
    }

    suspend fun saveLastUpdateCheck(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_UPDATE_CHECK_KEY] = timestamp
        }
    }

    suspend fun saveAppConfig(config: AppConfig) {
        context.dataStore.edit { preferences ->
            preferences[APP_CONFIG_KEY] = json.encodeToString(config)
        }
    }
}
