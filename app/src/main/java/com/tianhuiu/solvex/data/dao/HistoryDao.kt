package com.tianhuiu.solvex.data.dao

import androidx.room.*
import com.tianhuiu.solvex.data.models.HistoryItem
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_items ORDER BY timestamp DESC")
    fun getAllHistoryItems(): Flow<List<HistoryItem>>

    @Query("SELECT * FROM history_items ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getHistoryItemsPaged(limit: Int, offset: Int): List<HistoryItem>

    @Query("SELECT COUNT(*) FROM history_items")
    fun getHistoryCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryItem(item: HistoryItem)

    @Delete
    suspend fun deleteHistoryItem(item: HistoryItem)

    @Query("DELETE FROM history_items WHERE id = :id")
    suspend fun deleteHistoryItemById(id: String)

    @Query("SELECT * FROM history_items WHERE id = :id")
    suspend fun getHistoryItemById(id: String): HistoryItem?

    @Query("UPDATE history_items SET `status` = :status, `query` = :query, `result` = :result WHERE `status` = :processingStatus")
    suspend fun markProcessingAsCancelled(
        processingStatus: String,
        status: String,
        query: String,
        result: String
    )

    @Query("DELETE FROM history_items")
    suspend fun clearHistory()
}
