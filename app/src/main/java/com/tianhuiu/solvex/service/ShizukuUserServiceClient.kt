package com.tianhuiu.solvex.service

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs
import kotlin.coroutines.resume

/**
 * Shizuku 用户服务客户端：管理 binder 连接的生命周期。
 *
 * 实现"持久化连接"模式：
 * - 单例缓存 binder 引用，重复调用无需重新绑定
 * - 带超时的按需连接
 * - 5 秒超时保护，防止永久挂起
 */
object ShizukuUserServiceClient {
    private const val TAG = "ShizukuClient"
    private const val BIND_TIMEOUT_MS = 5000L

    @Volatile
    private var service: IShizukuShellService? = null
    private val mutex = Mutex()

    private val userServiceArgs = UserServiceArgs(
        ComponentName("com.tianhuiu.solvex", ShizukuShellService::class.java.name)
    )
        .processNameSuffix("capture")
        .tag("solvex-shizuku-shell")
        .version(1)
        .daemon(false)
        .debuggable(false)

    /**
     * 获取 Shizuku 服务引用。首次调用时绑定，后续返回缓存。
     * 5 秒超时，超时返回 null。
     */
    suspend fun acquire(): IShizukuShellService? = mutex.withLock {
        val current = service
        if (current != null && current.asBinder().isBinderAlive) {
            return@withLock current
        }
        service = null

        if (!Shizuku.pingBinder()) {
            Log.w(TAG, "Shizuku binder not alive")
            return@withLock null
        }

        withTimeoutOrNull(BIND_TIMEOUT_MS) {
            suspendCancellableCoroutine<IShizukuShellService?> { continuation ->
                var resumed = false
                val callback = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        Log.d(TAG, "onServiceConnected")
                        if (resumed) return
                        resumed = true
                        Shizuku.unbindUserService(userServiceArgs, this, false)
                        if (binder != null && binder.isBinderAlive) {
                            val s = IShizukuShellService.Stub.asInterface(binder)
                            service = s
                            continuation.resume(s)
                        } else {
                            continuation.resume(null)
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        Log.d(TAG, "onServiceDisconnected")
                        service = null
                        if (!resumed) {
                            resumed = true
                            continuation.resume(null)
                        }
                    }
                }

                try {
                    Shizuku.bindUserService(userServiceArgs, callback)
                } catch (e: Exception) {
                    Log.e(TAG, "bindUserService failed", e)
                    if (!resumed) {
                        resumed = true
                        continuation.resume(null)
                    }
                }

                continuation.invokeOnCancellation {
                    resumed = true
                }
            }
        }
    }

    /**
     * 清理缓存引用。下次 acquire() 将重新绑定。
     */
    fun invalidate() {
        service = null
    }
}
