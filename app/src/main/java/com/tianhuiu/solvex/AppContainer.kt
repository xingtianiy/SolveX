package com.tianhuiu.solvex

import android.content.Context
import com.tianhuiu.solvex.data.HistoryRepository
import com.tianhuiu.solvex.data.SolveXDatabase
import com.tianhuiu.solvex.network.ProcessingPipeline
import com.tianhuiu.solvex.network.UnifiedLLMClient
import com.tianhuiu.solvex.utils.AppNotificationManager
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 依赖注入容器。
 */
internal class AppContainer(appContext: Context) {
    val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .cache(Cache(File(appContext.cacheDir, "http_cache"), 10 * 1024 * 1024))
        .build()

    val unifiedLLMClient = UnifiedLLMClient(okHttpClient)
    val appNotificationManager = AppNotificationManager()
    val processingPipeline = ProcessingPipeline(appContext, unifiedLLMClient)

    val database = SolveXDatabase.getDatabase(appContext)
    val historyRepository = HistoryRepository(database.historyDao())
}
