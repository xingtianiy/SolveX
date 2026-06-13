package com.tianhuiu.solvex.floating

/**
 * 悬浮球运行状态。
 */
enum class BallStatus {
    IDLE,       // 空闲
    RUNNING,    // 处理中
    SUCCESS,    // 成功
    ERROR,      // 失败
    PROTECTED   // 隐匿保护中
}

/**
 * 悬浮球显示形态。
 */
enum class BallDisplayMode {
    FULL,           // 全显圆球
    HIDDEN_STRIP    // 侧边贴边（隐藏状态）
}
