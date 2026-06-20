package com.tianhuiu.solvex.data

import com.tianhuiu.solvex.data.dao.HistoryDao
import com.tianhuiu.solvex.data.models.HistoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 历史记录仓库
 */
class HistoryRepository(private val historyDao: HistoryDao) {

    private val updateMutex = Mutex()

    val historyItemsFlow: Flow<List<HistoryItem>> = historyDao.getAllHistoryItems()

    fun getHistoryItemByIdFlow(id: String): Flow<HistoryItem?> =
        historyDao.getHistoryItemByIdFlow(id)

    suspend fun getHistoryItemsPaged(page: Int, pageSize: Int): List<HistoryItem> {
        return historyDao.getHistoryItemsPaged(pageSize, page * pageSize)
    }

    fun getHistoryCount(): Flow<Int> = historyDao.getHistoryCount()

    suspend fun addHistoryItem(item: HistoryItem) {
        historyDao.insertHistoryItem(item)
    }

    suspend fun deleteProcessingItems() {
        historyDao.deleteProcessingItems()
    }

    suspend fun clearHistory() {
        historyDao.clearHistory()
    }

    suspend fun deleteHistoryItem(id: String) {
        historyDao.deleteHistoryItemById(id)
    }

    suspend fun cleanupProcessingItems(query: String, result: String) {
        historyDao.markProcessingAsCancelled(
            processingStatus = "PROCESSING",
            status = "CANCELLED",
            query = query,
            result = result
        )
    }

    suspend fun updateHistoryItem(id: String, block: (HistoryItem) -> HistoryItem) {
        updateMutex.withLock {
            val item = historyDao.getHistoryItemById(id)
            if (item != null) {
                historyDao.insertHistoryItem(block(item))
            }
        }
    }
}
