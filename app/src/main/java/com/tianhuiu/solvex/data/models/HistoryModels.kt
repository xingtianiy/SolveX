package com.tianhuiu.solvex.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 历史记录的解析状态。
 */
@Serializable
enum class AnalysisStatus(val displayName: String) {
    SUCCESS("已完成"),
    FAILURE("失败"),
    CANCELLED("已取消"),
    PROCESSING("处理中"),
}

/**
 * 历史记录条目数据模型。
 */
@Serializable
@Entity(tableName = "history_items")
data class HistoryItem(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val title: String? = null,
    val summary: String? = null,
    val query: String,
    val result: String,
    val imagePath: String? = null,
    val mode: String? = null,
    val assistantName: String? = null,
    val providerName: String? = null,
    val modelName: String? = null,
    val engineName: String? = null,
    val status: AnalysisStatus = AnalysisStatus.SUCCESS,
)
