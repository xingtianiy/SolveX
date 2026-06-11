package com.tianhuiu.solvex.utils.shared

/** 共享的 LaTeX 与 Markdown 正则模式 */
object LatexPatterns {
    /** LaTeX 环境匹配 */
    val latexEnvRegex = Regex("""\\begin\{.*?\}.*?\\end\{.*?\}""", setOf(RegexOption.DOT_MATCHES_ALL))

    /** 显示数学公式 $$ */
    val displayMathRegex = Regex("""\$\$.*?\$\$""", setOf(RegexOption.DOT_MATCHES_ALL))

    /** 行内数学公式 $ */
    val inlineMathRegex = Regex("""\$.*?\$""")

    /** LaTeX 命令匹配 */
    val latexCmdRegex = Regex(
        """\\(begin|end|left|right|vmatrix|matrix|frac|sqrt|cdot|times|div|pm|mp|le|ge|ne|approx|equiv|sum|prod|int|oint|partial|nabla|infty|alpha|beta|gamma|delta|epsilon|zeta|eta|theta|iota|kappa|lambda|mu|nu|xi|omicron|pi|rho|sigma|tau|upsilon|phi|chi|psi|omega)"""
    )

    /** Markdown 标题标记 */
    val mdHeadingRegex = Regex("""#+\s+""")

    /** Markdown 加粗标记 */
    val mdBoldRegex = Regex("""(\*\*|__)""")

    /** Markdown 斜体标记 */
    val mdItalicRegex = Regex("""(\*|_)""")

    /** Markdown 行内代码标记 */
    val mdCodeRegex = Regex("""`""")

    /** Markdown 引用标记 */
    val mdQuoteRegex = Regex(""">\s+""")

    /** Markdown 链接语法 */
    val mdLinkRegex = Regex("""\[(.*?)\]\((.*?)\)""")

    /** 连续空白字符折叠 */
    val whitespaceRegex = Regex("""[ \t]+""")
}
