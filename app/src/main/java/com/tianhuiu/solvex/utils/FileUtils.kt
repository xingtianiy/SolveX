package com.tianhuiu.solvex.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.log10
import kotlin.math.pow

/**
 * 文件操作工具类：负责图片的内部存储、空间统计及格式化。
 */
object FileUtils {
    /**
     * 将 Bitmap 保存到应用内部私有目录。
     * 返回保存文件的绝对路径，如果失败则返回 null。
     */
    fun saveBitmapToInternal(context: Context, bitmap: Bitmap): String? {
        return try {
            val directory = File(context.filesDir, "history_images")
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val fileName = "img_${UUID.randomUUID()}.jpg"
            val file = File(directory, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 计算历史图片目录占用的总空间大小（字节）。
     */
    fun getHistoryStorageSize(context: Context): Long {
        val directory = File(context.filesDir, "history_images")
        if (!directory.exists()) return 0L
        return directory.walk().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * 将字节大小格式化为人类可读的字符串（例如：1.5 MB）。
     */
    fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
        return java.text.DecimalFormat("#,##0.#")
            .format(size / 1024.0.pow(digitGroups.toDouble())) + " " + units[digitGroups]
    }

    /**
     * 清空历史记录图片目录。
     */
    fun clearHistoryImages(context: Context) {
        val directory = File(context.filesDir, "history_images")
        if (directory.exists()) {
            directory.deleteRecursively()
        }
    }

    /**
     * 读取 Assets 目录下的文本文件内容。
     */
    fun readAssetFile(context: Context, fileName: String): String {
        return try {
            context.assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "读取教程内容失败: ${e.message}"
        }
    }
}

/** Bitmap 解码采样尺寸计算 */
fun BitmapFactory.Options.calculateInSampleSize(reqWidth: Int, reqHeight: Int): Int {
    val height = outHeight
    val width = outWidth
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

/** Bitmap 转 Base64 JPEG */
fun Bitmap.toBase64Jpeg(quality: Int = 85): String {
    val output = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, quality, output)
    return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
}

/** 两步解码：先读取尺寸计算采样比例，再解码为 Bitmap */
fun decodeSampledBitmap(path: String, maxWidth: Int, maxHeight: Int): Bitmap? {
    return try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        options.inSampleSize = options.calculateInSampleSize(maxWidth, maxHeight)
        options.inJustDecodeBounds = false
        BitmapFactory.decodeFile(path, options)
    } catch (_: Exception) {
        null
    }
}

/** 使用应用默认格式和时区格式化时间戳 */
fun formatTimestamp(timestamp: Long): String {
    val dateFormat = SimpleDateFormat("yyyy MM-dd HH:mm:ss", Locale.getDefault())
    return dateFormat.format(Date(timestamp))
}
