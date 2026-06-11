package com.tianhuiu.solvex.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class AnalysisStatus(val displayName: String) {
    SUCCESS("已完成"),
    FAILURE("失败"),
    CANCELLED("已取消"),
    PROCESSING("处理中"),
}

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
    val imagePaths: List<String> = emptyList(),
    val mode: String? = null,
    val assistantName: String? = null,
    val providerName: String? = null,
    val modelName: String? = null,
    val engineName: String? = null,
    val status: AnalysisStatus = AnalysisStatus.SUCCESS,
)
