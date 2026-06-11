package com.tianhuiu.solvex.data.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DownloadStatusTest {

    @Test
    fun `Idle is singleton`() {
        assertSame(DownloadStatus.Idle, DownloadStatus.Idle)
    }

    @Test
    fun `Downloading reports correct progress`() {
        val status = DownloadStatus.Downloading(50)
        assertEquals(50, status.progress)
    }

    @Test
    fun `Downloading progress min bound`() {
        val status = DownloadStatus.Downloading(0)
        assertEquals(0, status.progress)
    }

    @Test
    fun `Downloading progress max bound`() {
        val status = DownloadStatus.Downloading(100)
        assertEquals(100, status.progress)
    }

    @Test
    fun `Success contains apk path`() {
        val path = "/storage/cache/updates/app.apk"
        val status = DownloadStatus.Success(path)
        assertEquals(path, status.apkPath)
    }

    @Test
    fun `Error contains message`() {
        val msg = "所有下载源均失效"
        val status = DownloadStatus.Error(msg)
        assertEquals(msg, status.message)
    }

    @Test
    fun `sealed class subtypes are distinct`() {
        val idle = DownloadStatus.Idle
        val downloading = DownloadStatus.Downloading(0)
        val success = DownloadStatus.Success("/path")
        val error = DownloadStatus.Error("err")

        assertNotEquals(idle, downloading)
        assertNotEquals(downloading, success)
        assertNotEquals(success, error)
    }
}
