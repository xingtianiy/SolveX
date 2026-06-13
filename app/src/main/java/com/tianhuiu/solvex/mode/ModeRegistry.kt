package com.tianhuiu.solvex.mode

// 模式注册中心
object ModeRegistry {
    private val modes = mapOf(
        StudyMode.id to StudyMode,
        QuickMode.id to QuickMode,
    )

    val all: List<Mode> get() = modes.values.toList()

    fun get(id: String): Mode = modes[id] ?: error("未知模式: $id")

    fun defaultId(): String = StudyMode.id
}
