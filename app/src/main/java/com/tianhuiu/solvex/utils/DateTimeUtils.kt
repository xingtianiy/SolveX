package com.tianhuiu.solvex.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 日期时间格式化工具
object DateTimeUtils {
    private val fullFormat = SimpleDateFormat("yyyy MM-dd HH:mm:ss", Locale.getDefault())
    private val shortFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    fun formatFull(timestamp: Long): String = fullFormat.format(Date(timestamp))
    fun formatShort(timestamp: Long): String = shortFormat.format(Date(timestamp))
}
