package com.tianhuiu.solvex.service

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.delay
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
    private const val BIND_TIMEOUT_MS = 8000L
    private const val RETRY_DELAY_MS = 500L

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
     * 获取 Shizuku 服务引用，失败时清理残留并重试一次。
     */
    suspend fun acquire(context: Context): IShizukuShellService? = mutex.withLock {
        val current = service
        if (current != null && current.asBinder().isBinderAlive) {
            return@withLock current
        }

        // 清理本地失效引用
        service = null
        serviceConnection = null

        if (!Shizuku.pingBinder()) {
            Log.w(TAG, "Shizuku binder not alive")
            return@withLock null
        }

        // 第一次尝试绑定
        var result = tryBind(context)
        if (result != null) {
            return@withLock result
        }

        // 绑定失败，可能是进程重启后 Shizuku 服务端残留了旧 daemon 的失效记录
        // 强制清理后重试
        Log.w(TAG, "首次绑定失败，清理 Shizuku 残留状态后重试...")
        try {
            val args = getUserServiceArgs(context)
            Shizuku.unbindUserService(args, null, true)
        } catch (_: Exception) {
        }
        delay(RETRY_DELAY_MS)

        result = tryBind(context)
        if (result != null) {
            Log.i(TAG, "重试绑定成功")
        } else {
            Log.e(TAG, "重试绑定仍然失败，请检查 Shizuku 是否正常运行")
        }
        return@withLock result
    }

    /**
     * 单次绑定尝试，超时或失败返回 null。
     */
    private suspend fun tryBind(context: Context): IShizukuShellService? {
        return withTimeoutOrNull(BIND_TIMEOUT_MS) {
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
                            try {
                                binder.linkToDeath(
                                    object : IBinder.DeathRecipient {
                                        override fun binderDied() {
                                            Log.d(TAG, "Binder died, invalidating cached service")
                                            binder.unlinkToDeath(this, 0)
                                            service = null
                                            serviceConnection = null
                                        }
                                    },
                                    0
                                )
                            } catch (_: Exception) {
                                service = null
                                serviceConnection = null
                                continuation.resume(null)
                                return
                            }
                            continuation.resume(s)
                        } else {
                            continuation.resume(null)
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        Log.d(TAG, "Shizuku service disconnected: ${name?.className}")
                        service = null
                        serviceConnection = null
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
                    if (!resumed) {
                        resumed = true
                        try {
                            Shizuku.unbindUserService(args, callback, true)
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }
    }

    /**
     * 释放活跃的 Shizuku 服务连接。
     */
    fun release(context: Context) {
        try {
            val conn = serviceConnection
            if (conn != null) {
                Log.d(TAG, "Unbinding Shizuku user service")
                Shizuku.unbindUserService(getUserServiceArgs(context), conn, true)
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
        serviceConnection = null
    }
}
