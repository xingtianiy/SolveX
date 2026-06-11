package com.tianhuiu.solvex.data.models

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureModeTest {

    @Test
    fun `capture mode constants are correct`() {
        assertEquals("system", CaptureMode.SYSTEM)
        assertEquals("accessibility", CaptureMode.ACCESSIBILITY)
        assertEquals("shizuku", CaptureMode.SHIZUKU)
    }
}
