package com.tianhuiu.solvex.service

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
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

    // 关键：持有强引用，防止 ServiceConnection 在回调前被 GC
    @Volatile
    private var pendingConnection: ServiceConnection? = null

    // 缓存上次绑定的 args，用于超时后清理
    private var lastArgs: UserServiceArgs? = null

    /**
     * 获取服务引用。
     */
    suspend fun acquire(context: Context): IShizukuShellService? {
        val appContext = context.applicationContext

        val current = service
        if (current != null && current.asBinder().isBinderAlive) {
            return current
        }

        val ping = Shizuku.pingBinder()
        val perm = if (ping) Shizuku.checkSelfPermission() else -2

        if (!ping || perm != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Shizuku not ready")
            return null
        }

        // 清理上次遗留的连接
        cleanupPendingBind()
        service = null

        return withTimeoutOrNull(BIND_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val componentName = ComponentName(appContext.packageName, ShizukuShellService::class.java.name)
                val args = UserServiceArgs(componentName)
                    .daemon(true)
                    .processNameSuffix("shizuku_service")
                    .debuggable(true)
                lastArgs = args

                val connection = object : ServiceConnection {
                    private var resumed = false

                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        if (resumed) return
                        resumed = true

                        if (binder != null && binder.isBinderAlive) {
                            val s = IShizukuShellService.Stub.asInterface(binder)
                            service = s
                            Log.i(TAG, "Binding successful")
                            continuation.resume(s)
                        } else {
                            Log.e(TAG, "Binder invalid")
                            continuation.resume(null)
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        Log.w(TAG, "onServiceDisconnected: ${name?.className}")
                        service = null
                        if (!resumed) {
                            resumed = true
                            continuation.resume(null)
                        }
                    }
                }

                // 在字段中保存强引用
                pendingConnection = connection

                try {
                    Shizuku.bindUserService(args, connection)
                } catch (e: Exception) {
                    Log.e(TAG, "bindUserService failed", e)
                    pendingConnection = null
                    lastArgs = null
                    if (!continuation.isCompleted) continuation.resume(null)
                }
            }
        } ?: run {
            Log.e(TAG, "Binding timeout")
            cleanupPendingBind()
            null
        }
    }

    /**
     * 清理待处理的绑定，防止残留连接干扰后续绑定。
     */
    private fun cleanupPendingBind() {
        val conn = pendingConnection
        val args = lastArgs
        pendingConnection = null
        lastArgs = null
        if (conn != null && args != null) {
            try {
                Shizuku.unbindUserService(args, conn, true)
            } catch (_: Exception) { }
        }
    }

    /**
     * 清理缓存引用。
     */
    fun invalidate() {
        cleanupPendingBind()
        service = null
    }
}
