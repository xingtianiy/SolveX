package com.tianhuiu.solvex.mode

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode

// 自动速查模式
object QuickMode : Mode {
    override val id = "quick"
    override val displayName = "自动模式"
    override val description = "悬浮球展示选择题答案/填空题自动复制"
    override val icon = Icons.Default.AutoMode
    override val shouldCrop = false
    override val requiresAutomationAction = true
    override fun defaultConfig() = ModeConfig(autoOpenDrawer = false)
}
