package com.tianhuiu.solvex

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.tianhuiu.solvex.ui.MainViewModel
import com.tianhuiu.solvex.ui.SolveXApp
import com.tianhuiu.solvex.utils.NotificationUtils
import kotlinx.coroutines.launch

/**
 * 应用主 Activity：负责初始化、处理深度链接及请求系统录屏权限。
 */
class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if ((result.resultCode == RESULT_OK) && (result.data != null)) {
            viewModel.startMainService(result.resultCode, result.data)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("SolveX", "Uncaught exception in thread ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        handleDeepLink(intent)

        lifecycleScope.launch {
            viewModel.requestMediaProjection.collect {
                val manager =
                    getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                projectionLauncher.launch(manager.createScreenCaptureIntent())
            }
        }

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkPermissions()
            }
        })

        setContent {
            SolveXApp(viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent) {
        if (intent.action == NotificationUtils.ACTION_VIEW_HISTORY) {
            val historyId = intent.getStringExtra(NotificationUtils.EXTRA_HISTORY_ID)
            if (historyId != null) {
                viewModel.deepLinkHistoryId = historyId
            }
        }
    }
}