package com.tianhuiu.solvex

import android.app.Application

/**
 * 全局 Application 类：初始化依赖注入容器（AppContainer）。
 */
class SolveXApplication : Application() {
    internal lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
    }
}
