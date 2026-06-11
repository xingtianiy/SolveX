package com.tianhuiu.solvex.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tianhuiu.solvex.data.models.HistoryItem
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_items ORDER BY timestamp DESC")
    fun getAllHistoryItems(): Flow<List<HistoryItem>>

    @Query("SELECT COUNT(*) FROM history_items")
    fun getHistoryCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryItem(item: HistoryItem)

    @Query("DELETE FROM history_items WHERE id = :id")
    suspend fun deleteHistoryItemById(id: String)

    @Query("SELECT * FROM history_items WHERE id = :id")
    suspend fun getHistoryItemById(id: String): HistoryItem?

    @Query("SELECT * FROM history_items WHERE id = :id")
    fun getHistoryItemByIdFlow(id: String): Flow<HistoryItem?>

    @Query("UPDATE history_items SET `status` = :status WHERE `status` = :processingStatus")
    suspend fun markProcessingAsCancelled(
        processingStatus: String,
        status: String
    )

    @Query("DELETE FROM history_items")
    suspend fun clearHistory()
}
