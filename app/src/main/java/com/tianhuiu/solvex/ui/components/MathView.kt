package com.tianhuiu.solvex.ui.components

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlin.math.abs

/** LaTeX 标记检测 */
private val latexPattern = Regex(
    """\$|\\\[|\\\(|\\begin\{|\\frac|\\sqrt|\\sum|\\int|\\alpha|\\beta|\\gamma|\\theta|\\pi|\\infty"""
)

private fun hasLatex(text: String): Boolean = latexPattern.containsMatchIn(text)

/** HTML 模板，只构建一次 */
private val htmlTemplate = run {
    val lineHeight = 1.4f
    """<!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"><script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script><script>window.MathJax={tex:{inlineMath:[["$","$"],["\\(","\\)"]],displayMath:[["$$","$$"],["\\[","\\]"]],processEscapes:!1,processEnvironments:!1},options:{ignoreHtmlClass:"tex2jax_ignore",processHtmlClass:"tex2jax_process",enableMenu:!1},chtml:{displayAlign:"left"},startup:{typeset:!1}}</script><script id="MathJax-script" async src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js"></script><style>body,html{margin:0;padding:0;scrollbar-width:none;-ms-overflow-style:none}body::-webkit-scrollbar,html::-webkit-scrollbar{display:none}body{font-family:-apple-system,"Noto Sans SC",sans-serif;font-size:15px;line-height:${lineHeight};color:#1C1B1F;word-wrap:break-word;overflow-wrap:break-word;-webkit-user-select:none}#content{width:100%;overflow-x:hidden}.formula-scroll{width:100%;overflow-x:auto;overflow-y:hidden;-webkit-overflow-scrolling:touch;scrollbar-width:none;-ms-overflow-style:none}.formula-scroll::-webkit-scrollbar{display:none}mjx-container{max-width:100%!important}mjx-container[display=true]{display:block!important;text-align:left!important;margin:0.3em 0!important}mjx-container[display=true] mjx-math{min-width:max-content!important}h3{margin-top:12px;margin-bottom:6px}p{margin:0.1em 0}ol,ul{padding-left:20px;margin:0.2em 0}pre{overflow-x:auto;padding:8px;border-radius:8px;white-space:pre-wrap;word-wrap:break-word}code{word-break:break-word}</style></head><body><div id="content" class="tex2jax_process"></div><script>function wrapMathContainers(){document.querySelectorAll('mjx-container[display="true"]').forEach(function(e){var t;e.parentElement&&e.parentElement.classList.contains("formula-scroll")||((t=document.createElement("div")).className="formula-scroll",e.parentNode.insertBefore(t,e),t.appendChild(e))})}function updateContent(e){try{var t,n;e&&(t=decodeURIComponent(escape(window.atob(e))).replace(/\\\\/g,"\\\\\\\\"),(n=document.getElementById("content")).innerHTML=marked.parse(t),window.MathJax)&&window.MathJax.typesetPromise&&window.MathJax.typesetPromise([n]).then(function(){wrapMathContainers();if(window.Android)window.Android.onRendered()}).catch(function(e){console.log(e);if(window.Android)window.Android.onRendered()})}catch(e){console.error(e);if(window.Android)window.Android.onRendered()}}</script></body></html>"""
}

private const val MAX_LATEX_WEBVIEWS = 5

/**
 * 响应式槽位管理器
 */
private object LatexSlotManager {
    private var activeCount by mutableIntStateOf(0)
    
    val availableSlots: Int
        get() = MAX_LATEX_WEBVIEWS - activeCount

    fun tryAcquire(): Boolean {
        return synchronized(this) {
            if (activeCount < MAX_LATEX_WEBVIEWS) {
                activeCount++
                true
            } else {
                false
            }
        }
    }

    fun release() {
        synchronized(this) {
            if (activeCount > 0) activeCount--
        }
    }
}

private fun destroyWebView(wv: WebView) {
    try {
        (wv.parent as? ViewGroup)?.removeView(wv)
    } catch (_: Exception) {
    }
    wv.destroy()
}

private fun latexToReadable(text: String): String {
    var r = text

    // 1) 行间/行内公式标记
    r = r.replace("\\[", "").replace("\\]", "")
    r = r.replace("\\(", "").replace("\\)", "")

    // 2) 希腊字母 & 常用符号 → Unicode
    r = r.replace("\\alpha", "α").replace("\\beta", "β")
    r = r.replace("\\gamma", "γ").replace("\\delta", "δ")
    r = r.replace("\\epsilon", "ε").replace("\\theta", "θ")
    r = r.replace("\\lambda", "λ").replace("\\mu", "μ")
    r = r.replace("\\sigma", "σ").replace("\\phi", "φ")
    r = r.replace("\\omega", "ω").replace("\\pi", "π")
    r = r.replace("\\infty", "∞").replace("\\partial", "∂")
    r = r.replace("\\nabla", "∇")

    // 关系 / 运算
    r = r.replace("\\geq", "≥").replace("\\leq", "≤")
    r = r.replace("\\neq", "≠").replace("\\approx", "≈")
    r = r.replace("\\equiv", "≡").replace("\\times", "×")
    r = r.replace("\\div", "÷").replace("\\pm", "±")
    r = r.replace("\\cdot", "·").replace("\\ldots", "…")

    // 集合 / 逻辑
    r = r.replace("\\forall", "∀").replace("\\exists", "∃")
    r = r.replace("\\in", "∈").replace("\\notin", "∉")
    r = r.replace("\\subset", "⊂").replace("\\supset", "⊃")
    r = r.replace("\\cup", "∪").replace("\\cap", "∩")
    r = r.replace("\\emptyset", "∅")

    // 箭头
    r = r.replace("\\rightarrow", "→").replace("\\leftarrow", "←")
    r = r.replace("\\Rightarrow", "⇒").replace("\\Leftarrow", "⇐")

    // 大运算符
    r = r.replace("\\sum", "∑").replace("\\int", "∫")
    r = r.replace("\\prod", "∏").replace("\\iint", "∬")

    // 上标: x^{123} → x¹²³
    r = r.replace(Regex("\\^\\{(\\d+)\\}")) { m ->
        m.groupValues[1].map { c ->
            when (c) {
                '0' -> '⁰'; '1' -> '¹'; '2' -> '²'
                '3' -> '³'; '4' -> '⁴'; '5' -> '⁵'
                '6' -> '⁶'; '7' -> '⁷'; '8' -> '⁸'
                '9' -> '⁹'; else -> c
            }
        }.joinToString("")
    }
    // 上标单个数字 x^2 → x²
    r = r.replace(Regex("\\^(\\d)")) { m ->
        when (m.groupValues[1][0]) {
            '0' -> "⁰"; '1' -> "¹"; '2' -> "²"
            '3' -> "³"; '4' -> "⁴"; '5' -> "⁵"
            '6' -> "⁶"; '7' -> "⁷"; '8' -> "⁸"
            '9' -> "⁹"; else -> "^${m.groupValues[1]}"
        }
    }
    r = r.replace(Regex("\\^([a-zA-Z])")) { "^${it.groupValues[1]}" }

    // 分数: \frac{a}{b} → a/b
    r = r.replace(Regex("\\\\frac\\{([^}]*)\\}\\{([^}]*)\\}")) { m ->
        "${m.groupValues[1]}/${m.groupValues[2]}"
    }

    // 根号: \sqrt[n]{x} → ⁿ√x, \sqrt{x} → √x
    r = r.replace(Regex("\\\\sqrt\\[([^}]*)\\]\\{([^}]*)\\}")) { m ->
        "${m.groupValues[1]}√${m.groupValues[2]}"
    }
    r = r.replace(Regex("\\\\sqrt\\{([^}]*)\\}")) { m ->
        "√${m.groupValues[1]}"
    }

    // 极限 / 角标: x_{...} → x_(...)
    r = r.replace(Regex("_\\{([^}]*)\\}")) { "₍${it.groupValues[1]}₎" }

    // 4) 清理分隔符
    r = r.replace("$$", "").replace("$", "")
    r = r.replace("}{", "/")
    r = r.replace("{", "(").replace("}", ")")
    r = r.replace("\\left", "").replace("\\right", "")
    // 常见环境
    r = r.replace(Regex("\\\\begin\\{[^}]*\\}"), "")
    r = r.replace(Regex("\\\\end\\{[^}]*\\}"), "")

    return r.trim()
}

/**
 * 数学/LaTeX 渲染组件。
 *
 * - 纯文本 → Compose Text
 * - 含 LaTeX → WebView（最多 [MAX_LATEX_WEBVIEWS] 个同时存在），
 *   超出限制时显示优美的 [RenderingPlaceholder]，并自动在有空位时升级。
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun MathView(
    text: String,
    modifier: Modifier = Modifier,
    lineHeight: Float = 1.4f,
    forceMarkdown: Boolean = false,
    onRendered: (() -> Unit)? = null
) {
    if (!forceMarkdown && !hasLatex(text)) {
        Text(
            text = text,
            modifier = modifier,
            style = MaterialTheme.typography.bodyMedium
        )
        LaunchedEffect(text) {
            onRendered?.invoke()
        }
        return
    }

    // 槽位状态
    var hasSlot by remember { mutableStateOf(false) }
    val availableSlots by remember { derivedStateOf { LatexSlotManager.availableSlots } }

    // 尝试获取槽位
    LaunchedEffect(availableSlots) {
        if (!hasSlot && availableSlots > 0) {
            hasSlot = LatexSlotManager.tryAcquire()
        }
    }

    DisposableEffect(hasSlot) {
        onDispose {
            if (hasSlot) {
                LatexSlotManager.release()
                hasSlot = false
            }
        }
    }

    if (hasSlot) {
        var isMathJaxReady by remember { mutableStateOf(false) }
        var isPageLoaded by remember { mutableStateOf(false) }
        var lastRendered by remember { mutableStateOf("") }
        var currentWv by remember { mutableStateOf<WebView?>(null) }

        LaunchedEffect(text, isPageLoaded) {
            if (isPageLoaded && text.isNotBlank() && text != lastRendered) {
                val wv = currentWv ?: return@LaunchedEffect

                val isStreaming = lastRendered.isNotEmpty() && text.startsWith(lastRendered)
                
                if (!isStreaming || !isMathJaxReady) {
                    isMathJaxReady = false
                }
                
                if (lastRendered.isNotEmpty()) delay(50)

                val encodedText = android.util.Base64.encodeToString(
                    text.toByteArray(),
                    android.util.Base64.NO_WRAP
                )
                wv.evaluateJavascript("updateContent('$encodedText')", null)
                lastRendered = text
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                currentWv?.let { destroyWebView(it) }
            }
        }

        Box(modifier = modifier) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = if (isMathJaxReady) 1f else 0f
                    },
                factory = { context ->
                    WebView(context.applicationContext).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.loadsImagesAutomatically = true
                        setBackgroundColor(0)
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                        overScrollMode = View.OVER_SCROLL_NEVER
                        isLongClickable = false
                        setOnLongClickListener { true }

                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun onRendered() {
                                post {
                                    isMathJaxReady = true
                                    onRendered?.invoke()
                                }
                            }
                        }, "Android")

                        setOnTouchListener { _, event ->
                            when (event.action) {
                                MotionEvent.ACTION_DOWN ->
                                    parent.requestDisallowInterceptTouchEvent(false)

                                MotionEvent.ACTION_MOVE -> {
                                    if (abs(event.x - (event.x)) > 10)
                                        parent.requestDisallowInterceptTouchEvent(true)
                                }
                            }
                            false
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isPageLoaded = true
                            }
                        }

                        loadDataWithBaseURL(null, htmlTemplate, "text/html", "UTF-8", null)
                        currentWv = this
                    }
                },
                onRelease = { wv ->
                    currentWv = null
                    destroyWebView(wv)
                }
            )

            if (!isMathJaxReady) {
                RenderingPlaceholder(
                    text = text,
                    modifier = Modifier.fillMaxWidth(),
                    showStatusText = false
                )
            }
        }
    } else {
        RenderingPlaceholder(
            text = text,
            modifier = modifier
        )
        LaunchedEffect(text) {
            onRendered?.invoke()
        }
    }
}

/**
 * 优美的渲染占位符。
 */
@Composable
private fun RenderingPlaceholder(
    text: String,
    modifier: Modifier = Modifier,
    showStatusText: Boolean = true
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            if (showStatusText) {
                Text(
                    text = "正在排队渲染 LaTeX...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        Text(
            text = latexToReadable(text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
    }
}
