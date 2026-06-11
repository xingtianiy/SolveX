package com.tianhuiu.solvex.ui.components

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    """<!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"><script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script><script>window.MathJax={tex:{inlineMath:[["$","$"],["\\(","\\)"]],displayMath:[["$$","$$"],["\\[","\\]"]],processEscapes:!1,processEnvironments:!1},options:{ignoreHtmlClass:"tex2jax_ignore",processHtmlClass:"tex2jax_process",enableMenu:!1},chtml:{displayAlign:"left"},startup:{typeset:!1}}</script><script id="MathJax-script" async src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js"></script><style>body,html{margin:0;padding:0;scrollbar-width:none;-ms-overflow-style:none}body::-webkit-scrollbar,html::-webkit-scrollbar{display:none}body{font-family:-apple-system,"Noto Sans SC",sans-serif;font-size:15px;line-height:${lineHeight};color:#1C1B1F;word-wrap:break-word;overflow-wrap:break-word;-webkit-user-select:none}#content{width:100%;overflow-x:auto}.formula-scroll{width:100%;overflow-x:auto;overflow-y:hidden;-webkit-overflow-scrolling:touch;scrollbar-width:none;-ms-overflow-style:none}.formula-scroll::-webkit-scrollbar{display:none}mjx-container{max-width:100%!important}mjx-container[display=true]{display:block!important;text-align:left!important;margin:0.3em 0!important}mjx-container[display=true] mjx-math{min-width:max-content!important}h3{margin-top:12px;margin-bottom:6px}p{margin:0.1em 0}ol,ul{padding-left:20px;margin:0.2em 0}pre{overflow-x:auto;padding:8px;border-radius:8px}code{word-break:break-word}</style></head><body><div id="content" class="tex2jax_process"></div><script>function wrapMathContainers(){document.querySelectorAll('mjx-container[display="true"]').forEach(function(e){var t;e.parentElement&&e.parentElement.classList.contains("formula-scroll")||((t=document.createElement("div")).className="formula-scroll",e.parentNode.insertBefore(t,e),t.appendChild(e))})}function updateContent(e){try{var t,n;e&&(t=decodeURIComponent(escape(window.atob(e))).replace(/\\\\/g,"\\\\\\\\"),(n=document.getElementById("content")).innerHTML=marked.parse(t),window.MathJax)&&window.MathJax.typesetPromise&&window.MathJax.typesetPromise([n]).then(function(){wrapMathContainers()}).catch(function(e){console.log(e)})}catch(e){console.error(e)}}</script></body></html>"""
}

/**
 * 销毁 WebView：从父容器移除并调用 destroy()。
 */
private fun destroyWebView(wv: WebView) {
    try {
        (wv.parent as? ViewGroup)?.removeView(wv)
    } catch (_: Exception) {
    }
    wv.destroy()
}

/**
 * 数学/LaTeX 渲染组件。纯文本使用 Compose Text，含 LaTeX 使用 WebView。
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun MathView(
    text: String,
    modifier: Modifier = Modifier,
    lineHeight: Float = 1.4f,
    forceMarkdown: Boolean = false
) {
    if (!forceMarkdown && !hasLatex(text)) {
        Text(
            text = text,
            modifier = modifier,
            style = MaterialTheme.typography.bodyMedium
        )
        return
    }

    var isPageLoaded by remember { mutableStateOf(false) }
    var lastRendered by remember { mutableStateOf("") }
    var currentWv by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(text, isPageLoaded) {
        if (isPageLoaded && text.isNotBlank() && text != lastRendered) {
            val wv = currentWv ?: return@LaunchedEffect
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

    AndroidView(
        modifier = modifier,
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
        onRelease = { wv -> destroyWebView(wv) }
    )
}
