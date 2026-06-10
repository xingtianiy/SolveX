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
             "type": "单选题/多选题/判断题/填空题/简答题",
             "question": "题目正文",
             "options": ["A. xxx", "B. xxx"], 
             "image_analysis": "（仅视觉引擎）对图片中手写文字或复杂图形的补充描述，如果是纯文本可为空"
           }
        2. **题型识别**：严禁使用“选择题”这一笼统称呼。必须严格根据截屏的信息和设问识别并区分“单选题”或“多选题”等体系。
        3. **干扰过滤**：彻底过滤系统状态栏、虚拟按键、应用功能按钮及广告弹窗。
        4. **数学渲染**：所有数学公式、符号、变量必须使用 LaTeX 语法（行内 $...$，独立行 $$...$$）。
        5. **选项规范**：如果是选择题，必须完整提取所有选项。
        6. **严禁改写**：必须保持原文内容。
    """.trimIndent()

    // 任务处理与分析 — 系统提示词框架

    val ANALYSIS_SYSTEM_BASE = """
        # 格式规范
        1. **数学渲染**：除“最终答案”外，所有公式、符号、变量必须使用 LaTeX 语法（行内使用 $...$，独立行使用 $$...$$）。
        2. **Markdown 结构**：使用标准 Markdown 标题（###）和列表。
        3. **最终答案硬约束**：
           - **内容要求**：“### 最终答案”部分 must output **plain text**.
           - **题型对应**：如果是多选题，最终答案必须包含所有正确选项的字母（如 ABCD），不要只输出一个。
           - **严禁符号**：严禁在此部分使用 LaTeX 语法（禁止使用 $、$$、\begin、\end、\mathbf 等）、Markdown 加粗、代码块或任何格式化标记。
           - **结果导向**：不要展示计算过程中的矩阵、复杂分式或公式，直接用文字描述结果。例如：不要输出公式，直接输出“答案为：-71”。
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
