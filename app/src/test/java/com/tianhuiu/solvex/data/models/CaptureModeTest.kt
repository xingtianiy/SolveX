package com.tianhuiu.solvex.data.models

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureModeTest {

    @Test
    fun `toDisplayName for known modes`() {
        assertEquals("系统录屏", CaptureMode.toDisplayName(CaptureMode.SYSTEM))
        assertEquals("无障碍截图", CaptureMode.toDisplayName(CaptureMode.ACCESSIBILITY))
        assertEquals("Shizuku ADB", CaptureMode.toDisplayName(CaptureMode.SHIZUKU))
    }

    @Test
    fun `toDisplayName for null returns unknown`() {
        assertEquals("未知", CaptureMode.toDisplayName(null))
    }

    @Test
    fun `toDisplayName for unknown string returns itself`() {
        assertEquals("custom_mode", CaptureMode.toDisplayName("custom_mode"))
    }

    @Test
    fun `toDisplayName for empty string returns empty`() {
        assertEquals("", CaptureMode.toDisplayName(""))
    }

    @Test
    fun `capture mode constants are correct`() {
        assertEquals("system", CaptureMode.SYSTEM)
        assertEquals("accessibility", CaptureMode.ACCESSIBILITY)
        assertEquals("shizuku", CaptureMode.SHIZUKU)
    }
}
