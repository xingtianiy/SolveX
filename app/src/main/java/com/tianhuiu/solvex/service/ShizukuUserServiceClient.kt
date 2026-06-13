package com.tianhuiu.solvex.service

import android.content.ComponentName
import android.content.Context
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
 * Shizuku 用户服务客户端。
 */
object ShizukuUserServiceClient {
    private const val TAG = "ShizukuClient"
    private const val BIND_TIMEOUT_MS = 10000L

    @Volatile
    private var service: IShizukuShellService? = null

    @Volatile
    private var serviceConnection: ServiceConnection? = null

    private val mutex = Mutex()

    private fun getUserServiceArgs(context: Context): UserServiceArgs {
        return UserServiceArgs(
            ComponentName(context.packageName, ShizukuShellService::class.java.name)
        )
            .processNameSuffix("shizuku_service")
            .tag("solvex-shizuku-shell")
            .version(1)
            .daemon(true)
            .debuggable(true)
    }

    /**
     * 获取 Shizuku 服务引用。首次调用时绑定，后续返回缓存。
     * 超时返回 null。
     */
    suspend fun acquire(context: Context): IShizukuShellService? = mutex.withLock {
        val current = service
        if (current != null && current.asBinder().isBinderAlive) {
            return@withLock current
        }
        
        // 如果旧连接存在但已失效，先尝试彻底释放
        Log.d(TAG, "Binder died or not present, attempting re-bind")
        release(context)

        if (!Shizuku.pingBinder()) {
            Log.w(TAG, "Shizuku binder not alive")
            return@withLock null
        }

        withTimeoutOrNull(BIND_TIMEOUT_MS) {
            suspendCancellableCoroutine<IShizukuShellService?> { continuation ->
                var resumed = false
                val args = getUserServiceArgs(context)
                val callback = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        Log.d(TAG, "Shizuku service connected: ${name?.className}")
                        if (resumed) return
                        resumed = true
                        
                        if (binder != null && binder.isBinderAlive) {
                            val s = IShizukuShellService.Stub.asInterface(binder)
                            service = s
                            serviceConnection = this
                            continuation.resume(s)
                        } else {
                            continuation.resume(null)
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        Log.d(TAG, "Shizuku service disconnected: ${name?.className}")
                        // 这里不要立即设为 null，因为 Shizuku 可能会重连或者我们需要在 acquire 中处理
                        if (!resumed) {
                            resumed = true
                            continuation.resume(null)
                        }
                    }
                }

                try {
                    Shizuku.bindUserService(args, callback)
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
     * 释放 Shizuku 服务连接。
     */
    fun release(context: Context) {
        try {
            val conn = serviceConnection
            if (conn != null) {
                Log.d(TAG, "Unbinding Shizuku user service")
                Shizuku.unbindUserService(getUserServiceArgs(context), conn, true)
            } else {
                // 即使没有活跃连接，也尝试通过参数强制解除，清理潜在的残留
                Log.d(TAG, "Force unbinding potential stale service")
                Shizuku.unbindUserService(getUserServiceArgs(context), null, true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unbind service", e)
        } finally {
            service = null
            serviceConnection = null
        }
    }

    /**
     * 清理缓存引用。下次 acquire() 将重新绑定。
     */
    fun invalidate() {
        service = null
    }
}
