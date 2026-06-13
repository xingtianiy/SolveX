package com.tianhuiu.solvex.network

/**
 * 集中管理不同阶段的系统提示词（Prompt）。
 */
object Prompts {

    // 核心提取 — 系统提示词框架

    val EXTRACTION_SYSTEM_BASE = """
        # 核心规则
        1. **结构化输出**：必须严格按照以下 JSON 格式输出，不要包含任何多余文字：
           {
             "type": "单选题/多选题/判断题/填空题/简答题/计算题/证明题/其他",
             "question": "题目正文",
             "options": ["A. xxx", "B. xxx"]
           }
        2. **题型识别**：严禁使用"选择题"笼统称呼。必须严格区分为：
           单选题（唯一答案）、多选题（多答案）、判断题、填空题、简答题、计算题、证明题
        3. **干扰过滤**：彻底过滤状态栏、导航栏、广告等非题目元素。
        4. **数学渲染**：公式必须使用 LaTeX 语法（行内 $...$，独立行 $$...$$）。
        5. **选项规范**：选择题必须完整提取所有选项，每个选项一行。
        6. **严禁改写**：保持原文内容不变。
    """.trimIndent()

    // 任务处理与分析 — 系统提示词框架

    val ANALYSIS_SYSTEM_BASE = """
        # 输出规范
        1. **数学渲染**：所有公式、符号、变量必须使用 LaTeX 语法（行内 $...$，独立行 $$...$$）。
        2. **段落结构**：使用 Markdown 三级标题（###）划分段落，标题由你根据内容动态决定。
           - 示例标题：### 解题思路、### 详细步骤、### 最终答案
           - 题型提示可在第一个 ### 标题中体现，如：### 单选题解析
        3. **禁止格式**：禁止输出 JSON、XML、YAML、Markdown 表格或代码块包裹正文。
        4. **内容要求**：每个标题下必须有实质内容，禁止空标题或连续标题。
    """.trimIndent()

    // 默认提示词配置（用于默认助手）

    const val OCR_EXTRACTION_USER_PROMPT =
        "从屏幕内容中提取题目，返回包含题型、内容及选项的 JSON 格式。"
    const val VISION_EXTRACTION_USER_PROMPT =
        "基于截图视觉信息提取题目，识别题型并补全所有选项，以 JSON 格式输出。"


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
