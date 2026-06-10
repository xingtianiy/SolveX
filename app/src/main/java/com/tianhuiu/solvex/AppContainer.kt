package com.tianhuiu.solvex

import android.content.Context
import com.tianhuiu.solvex.data.HistoryRepository
import com.tianhuiu.solvex.data.SolveXDatabase
import com.tianhuiu.solvex.network.ProcessingPipeline
import com.tianhuiu.solvex.network.UnifiedLLMClient
import com.tianhuiu.solvex.utils.AppNotificationManager
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 依赖注入容器：统一管理全局单例对象（如网络客户端、通知管理器、处理流水线）。
 */
internal class AppContainer(appContext: Context) {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val unifiedLLMClient = UnifiedLLMClient(okHttpClient)
    val appNotificationManager = AppNotificationManager()
    val processingPipeline = ProcessingPipeline(appContext, unifiedLLMClient)

    val database = SolveXDatabase.getDatabase(appContext)
    val historyRepository = HistoryRepository(database.historyDao())
}
