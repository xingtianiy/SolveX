package com.tianhuiu.solvex.mode

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode

// 自动速查模式
object QuickMode : Mode {
    override val id = "quick"
    override val displayName = "自动模式"
    override val description = "适合快速查询"
    override val icon = Icons.Default.AutoMode
    override val shouldCrop = false
    override val requiresAutomationAction = true
    override fun defaultConfig() = ModeConfig(autoOpenDrawer = false)
}
