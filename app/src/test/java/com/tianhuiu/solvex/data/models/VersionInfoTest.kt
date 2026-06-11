package com.tianhuiu.solvex.data.models

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionInfoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `critical level is not dismissible`() {
        val info = VersionInfo(
            versionCode = 5, versionName = "1.0.0", releaseDate = "2026-06-11",
            level = "critical", apkSize = "16 MB", updateLog = emptyList(),
            githubUrl = "", giteeUrl = ""
        )
        assertEquals(UpdateLevel.CRITICAL, info.updateLevel)
        assertFalse(info.isDismissible)
    }

    @Test
    fun `recommended level is dismissible`() {
        val info = VersionInfo(
            versionCode = 5, versionName = "1.0.0", releaseDate = "2026-06-11",
            level = "recommended", apkSize = "16 MB", updateLog = emptyList(),
            githubUrl = "", giteeUrl = ""
        )
        assertEquals(UpdateLevel.RECOMMENDED, info.updateLevel)
        assertTrue(info.isDismissible)
    }

    @Test
    fun `optional level is dismissible`() {
        val info = VersionInfo(
            versionCode = 5, versionName = "1.0.0", releaseDate = "2026-06-11",
            level = "optional", apkSize = "16 MB", updateLog = emptyList(),
            githubUrl = "", giteeUrl = ""
        )
        assertEquals(UpdateLevel.OPTIONAL, info.updateLevel)
        assertTrue(info.isDismissible)
    }

    @Test
    fun `unknown level defaults to optional`() {
        val info = VersionInfo(
            versionCode = 5, versionName = "1.0.0", releaseDate = "2026-06-11",
            level = "unknown_value", apkSize = "16 MB", updateLog = emptyList(),
            githubUrl = "", giteeUrl = ""
        )
        assertEquals(UpdateLevel.OPTIONAL, info.updateLevel)
        assertTrue(info.isDismissible)
    }

    @Test
    fun `level is case insensitive`() {
        val info = VersionInfo(
            versionCode = 5, versionName = "1.0.0", releaseDate = "2026-06-11",
            level = "CRITICAL", apkSize = "16 MB", updateLog = emptyList(),
            githubUrl = "", giteeUrl = ""
        )
        assertEquals(UpdateLevel.CRITICAL, info.updateLevel)
    }

    @Test
    fun `default level is recommended`() {
        val jsonStr = """
        {
            "versionCode": 1,
            "versionName": "0.0.1",
            "releaseDate": "2026-01-01",
            "apkSize": "10 MB",
            "updateLog": ["test"],
            "githubUrl": "",
            "giteeUrl": ""
        }
        """.trimIndent()
        val info = json.decodeFromString<VersionInfo>(jsonStr)
        assertEquals(UpdateLevel.RECOMMENDED, info.updateLevel)
    }

    @Test
    fun `deserialize from json`() {
        val jsonStr = """
        {
            "versionCode": 3,
            "versionName": "0.0.3-alpha",
            "releaseDate": "2026-06-11",
            "level": "critical",
            "apkSize": "16 MB",
            "updateLog": ["修复崩溃", "性能优化"],
            "githubUrl": "https://github.com/x/releases/download/v0.0.3/app.apk",
            "giteeUrl": "https://gitee.com/x/releases/download/v0.0.3/app.apk"
        }
        """.trimIndent()
        val info = json.decodeFromString<VersionInfo>(jsonStr)
        assertEquals(3, info.versionCode)
        assertEquals("0.0.3-alpha", info.versionName)
        assertEquals("2026-06-11", info.releaseDate)
        assertEquals("16 MB", info.apkSize)
        assertEquals(2, info.updateLog.size)
        assertEquals("修复崩溃", info.updateLog[0])
        assertTrue(info.githubUrl.endsWith(".apk"))
        assertTrue(info.giteeUrl.endsWith(".apk"))
    }

    @Test
    fun `serialize and deserialize roundtrip`() {
        val info = VersionInfo(
            versionCode = 2, versionName = "0.0.2", releaseDate = "2026-05-01",
            level = "recommended", apkSize = "12 MB",
            updateLog = listOf("新增功能"), githubUrl = "https://a.com", giteeUrl = "https://b.com"
        )
        val jsonStr = json.encodeToString(VersionInfo.serializer(), info)
        val restored = json.decodeFromString<VersionInfo>(jsonStr)
        assertEquals(info.versionCode, restored.versionCode)
        assertEquals(info.versionName, restored.versionName)
        assertEquals(info.releaseDate, restored.releaseDate)
        assertEquals(info.level, restored.level)
        assertEquals(info.apkSize, restored.apkSize)
        assertEquals(info.updateLog, restored.updateLog)
        assertEquals(info.githubUrl, restored.githubUrl)
        assertEquals(info.giteeUrl, restored.giteeUrl)
        assertEquals(info.updateLevel, restored.updateLevel)
        assertEquals(info.isDismissible, restored.isDismissible)
    }
}
