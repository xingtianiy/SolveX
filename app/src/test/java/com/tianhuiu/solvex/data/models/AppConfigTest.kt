package com.tianhuiu.solvex.data.models

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConfigTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `default AppConfig has expected defaults`() {
        val config = AppConfig()
        assertTrue(config.providers.isEmpty())
        assertTrue(config.assistants.isEmpty())
        assertTrue(config.autoScrollContent)
        assertEquals(EngineType.VISION_ENGINE, config.selectedEngine)
        assertEquals("study", config.selectedModeId)
    }

    @Test
    fun `deserialize AppConfig from json`() {
        val jsonStr = """
        {
            "providers": [
                {
                    "id": "p1",
                    "type": "OPENAI_COMPATIBLE",
                    "name": "TestProvider",
                    "url": "https://api.example.com",
                    "apiKey": "sk-test",
                    "availableModels": ["gpt-4", "gpt-3.5"],
                    "defaultOcrModel": "gpt-4",
                    "defaultTextModel": "gpt-4",
                    "defaultVisionModel": "gpt-4"
                }
            ],
            "assistants": [
                {
                    "id": "a1",
                    "name": "题目助手",
                    "ocrPrompt": "提取题目",
                    "textPrompt": "逐步解答",
                    "visionPrompt": "分析图像并解答"
                }
            ],
            "permissions": {
                "allowNotificationNormal": true,
                "allowNotificationAuto": false,
                "enableAutoHideBall": true,
                "captureMode": "system",
                "drawerSettings": {
                    "side": "LEFT",
                    "widthPercent": 0.8
                }
            },
            "selectedModeId": "study",
            "modeConfigs": {
                "study": {
                    "firstDeltaTimeoutSeconds": 30
                },
                "quick": {
                    "firstDeltaTimeoutSeconds": 10,
                    "showFloatingToast": false
                }
            },
            "autoScrollContent": false
        }
        """.trimIndent()
        val config = json.decodeFromString<AppConfig>(jsonStr)
        assertEquals(1, config.providers.size)
        assertEquals("TestProvider", config.providers[0].name)
        assertEquals("https://api.example.com", config.providers[0].url)
        assertEquals("sk-test", config.providers[0].apiKey)
        assertEquals(2, config.providers[0].availableModels.size)
        assertEquals("gpt-4", config.providers[0].defaultTextModel)

        assertEquals(1, config.assistants.size)
        assertEquals("题目助手", config.assistants[0].name)
        assertEquals("逐步解答", config.assistants[0].textPrompt)
        assertEquals("分析图像并解答", config.assistants[0].visionPrompt)

        assertTrue(config.permissions.allowNotificationNormal)
        assertFalse(config.permissions.allowNotificationAuto)
        assertEquals(CaptureMode.SYSTEM, config.permissions.captureMode)
        assertEquals(DrawerSide.LEFT, config.permissions.drawerSettings.side)

        assertEquals(30L, config.modeConfigs["study"]?.firstDeltaTimeoutSeconds)
        assertEquals(10L, config.modeConfigs["quick"]?.firstDeltaTimeoutSeconds)
        assertFalse(config.modeConfigs["quick"]?.showFloatingToast ?: true)
        assertFalse(config.autoScrollContent)
    }

    @Test
    fun `serialize AppConfig roundtrip`() {
        val config = AppConfig(
            providers = listOf(
                ModelProvider(
                    id = "p1", name = "Test", url = "https://api.example.com",
                    apiKey = "key", availableModels = listOf("m1"),
                    defaultTextModel = "m1"
                )
            ),
            autoScrollContent = true
        )
        val jsonStr = json.encodeToString(AppConfig.serializer(), config)
        val restored = json.decodeFromString<AppConfig>(jsonStr)
        assertEquals(1, restored.providers.size)
        assertEquals("Test", restored.providers[0].name)
        assertTrue(restored.autoScrollContent)
    }
}
