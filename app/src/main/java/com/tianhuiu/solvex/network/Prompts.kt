package com.tianhuiu.solvex.network

/**
 * 集中管理不同阶段的系统提示词（Prompt）。
 * System Prompt 仅包含通用技术规范，具体任务和输出格式由助手的 User Prompt 定义。
 */
object Prompts {

    // 提取阶段 — 系统技术规范（System Prompt）

    val EXTRACTION_SYSTEM_PROMPT = """
        |# 技术规范
        |- 数学公式必须使用 LaTeX 语法（行内 $...$，独立行 $$...$$）
        |- 严格基于视觉内容提取，不编造不添加
        |- 过滤系统状态栏、虚拟按键、广告等非内容元素
        |- 输出格式遵循用户提示词的要求
    """.trimMargin()

    // 分析阶段 — 系统技术规范（System Prompt）

    val ANALYSIS_SYSTEM_PROMPT = """
        |# 技术规范
        |- 数学公式必须使用 LaTeX 语法，复杂多个步骤可以使用一个复杂公式进行输出（行内 $...$，独立行 $$...$$）
        |- 使用标准 Markdown 组织输出（### 标题、**加粗**、- 列表、代码块等），但是不需要 Markdown 的分割线
        |- 每个 ### 标题将独立渲染为一个卡片，按需使用，不需要的区块直接省略
        |- 输出结构和内容遵循用户提示词的要求
    """.trimMargin()


    // 摘要生成

    val SUMMARY_SYSTEM_PROMPT = """
        # Task
        为截图内容生成极简索引。

        # Requirements
        1. Title: 核心内容概括，不超过 10 字。
        2. Summary: 主要信息点描述，不超过 30 字。

        # Format
        Title: [标题]
        Summary: [概述]
    """.trimIndent()

    // 自动速查模式 (专用)

    val AUTOMATION_SYSTEM_PROMPT = """
        # JSON Output Rules
        你必须严格根据题目返回以下 JSON 格式：
        1. 单选题/多选题：{"type": "choice", "answer": "选项"}
        2. 非选择题：{"type": "other", "answer": "最终答案内容"}

        # Constraint
        严禁输出 JSON 之外的任何解释文字。如果是多选题，答案应包含所有正确选项（如 ABCD）。
    """.trimIndent()
}
