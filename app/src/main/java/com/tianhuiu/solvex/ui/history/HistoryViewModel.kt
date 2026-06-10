package com.tianhuiu.solvex.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tianhuiu.solvex.data.models.HistoryItem
import com.tianhuiu.solvex.utils.FileUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 历史记录 ViewModel：支持全量实时监听 + 内存分页展示。
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository =
        (application as com.tianhuiu.solvex.SolveXApplication).container.historyRepository

    // 原始数据库流：实时响应“处理中”状态
    private val allItemsFlow = repository.historyItemsFlow

    // 当前显示的条数限制
    private val visibleCount = MutableStateFlow(20)

    // 最终暴露给 UI 的流：实时且分页
    val historyItems: StateFlow<List<HistoryItem>> =
        combine(allItemsFlow, visibleCount) { items, count ->
            items.take(count)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 全局总数统计：不受分页限制
    val totalCount: StateFlow<Int> = repository.getHistoryCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

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
        viewModelScope.launch {
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
