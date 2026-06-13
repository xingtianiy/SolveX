package com.tianhuiu.solvex

import android.app.Application

/**
 * 全局 Application 类：初始化依赖注入容器（AppContainer）。
 */
class SolveXApplication : Application() {
    internal lateinit var container: AppContainer
        private set

    /**
     * 全局持有 MainViewModel 引用，方便 Service 触发状态刷新。
     */
    var viewModel: com.tianhuiu.solvex.ui.MainViewModel? = null

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
    }
}
