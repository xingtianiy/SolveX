package com.tianhuiu.solvex.mode

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School

// 常规学习模式
object StudyMode : Mode {
    override val id = "study"
    override val displayName = "常规模式"
    override val description = "适合学习场景"
    override val icon = Icons.Default.School
    override val shouldCrop = true
    override fun defaultConfig() = ModeConfig(autoOpenDrawer = true)
}
