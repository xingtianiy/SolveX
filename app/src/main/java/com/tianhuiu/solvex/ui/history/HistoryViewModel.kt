package com.tianhuiu.solvex.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tianhuiu.solvex.data.models.HistoryItem
import com.tianhuiu.solvex.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 历史记录 ViewModel。
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository =
        (application as com.tianhuiu.solvex.SolveXApplication).container.historyRepository

    // 原始数据流
    private val allItemsFlow = repository.historyItemsFlow

    // 当前显示条数
    private val visibleCount = MutableStateFlow(20)

    // 分页流
    val historyItems: StateFlow<List<HistoryItem>> =
        combine(allItemsFlow, visibleCount) { items, count ->
            items.take(count)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 全局总数统计：不受分页限制
    val totalCount: StateFlow<Int> = repository.getHistoryCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun getHistoryItemById(id: String): Flow<HistoryItem?> = repository.getHistoryItemByIdFlow(id)

    private val _storageSize = MutableStateFlow(0L)
    val storageSize = _storageSize.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    init {
        updateStorageSize()
    }

    fun loadMore() {
        if (visibleCount.value < totalCount.value && !_isLoadingMore.value) {
            viewModelScope.launch {
                _isLoadingMore.value = true
                visibleCount.value += 20
                _isLoadingMore.value = false
            }
        }
    }

    private fun updateStorageSize() {
        viewModelScope.launch(Dispatchers.IO) {
            _storageSize.value = FileUtils.getHistoryStorageSize(getApplication())
        }
    }

    fun deleteItem(id: String) {
        viewModelScope.launch {
            repository.deleteHistoryItem(id)
            updateStorageSize()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            FileUtils.clearHistoryImages(getApplication())
            updateStorageSize()
        }
    }
}
