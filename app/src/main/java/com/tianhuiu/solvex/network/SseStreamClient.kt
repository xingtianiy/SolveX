package com.tianhuiu.solvex.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * SSE 流式客户端。
 */
class SseStreamClient(
    private val client: OkHttpClient,
) {
    private val tag = "SolveXSseClient"

    /**
     * 流式事件处理结果。
     */
    data class StreamEventResult(
        val delta: String? = null,
        val toolCall: LlmEvent.ToolCall? = null,
        val done: Boolean = false
    )

    /**
     * 建立 SSE 连接并开始流式传输。
     */
    suspend fun stream(
        request: Request,
        firstDeltaTimeoutMillis: Long = 10_000L,
        onEvent: (eventSource: EventSource, type: String?, id: String?, data: String) -> StreamEventResult?,
        onDelta: (String) -> Unit,
        onToolCall: (LlmEvent.ToolCall) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            val builder = StringBuilder()
            val finished = AtomicBoolean(false)
            val firstDeltaReceived = AtomicBoolean(false)
            var eventCount = 0

            fun finish(block: () -> Unit) {
                if (finished.compareAndSet(false, true)) {
                    block()
                }
            }

            val listener = object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: Response) {
                    Log.i(tag, "stream opened code=${response.code} url=${request.url}")
                }

                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String
                ) {
                    try {
                        eventCount += 1
                        val result = onEvent(eventSource, type, id, data)

                        result?.delta?.let { delta ->
                            if (delta.isNotEmpty()) {
                                firstDeltaReceived.set(true)
                                builder.append(delta)
                                onDelta(delta)
                            }
                        }

                        result?.toolCall?.let { tc ->
                            firstDeltaReceived.set(true)
                            onToolCall(tc)
                        }

                        if (result?.done == true) {
                            Log.i(
                                tag,
                                "stream completed totalEvents=$eventCount url=${request.url}"
                            )
                            finish { cont.resume(builder.toString()) }
                            eventSource.cancel()
                        }
                    } catch (t: Throwable) {
                        finish { cont.resumeWithException(t) }
                        eventSource.cancel()
                    }
                }

                override fun onFailure(
                    eventSource: EventSource,
                    t: Throwable?,
                    response: Response?
                ) {
                    if (finished.get() || cont.isCancelled) return

                    val normalized = if (
                        (!firstDeltaReceived.get() &&
                                ((t is SocketTimeoutException) || (t?.message?.contains(
                                    "timeout",
                                    ignoreCase = true
                                ) == true)))
                    ) {
                        SocketTimeoutException("首字延迟超时（${firstDeltaTimeoutMillis / 1000} 秒）")
                    } else {
                        t
                    }
                    finish {
                        val message = if (response != null) {
                            val bodySnippet = try {
                                response.peekBody(4096).string()
                            } catch (_: Exception) {
                                ""
                            }
                            parseApiErrorMessage(response.code, bodySnippet)
                        } else if (t != null) {
                            "网络异常: ${translateNetworkException(t)}"
                        } else {
                            "未知网络错误"
                        }
                        cont.resumeWithException(
                            normalized ?: IllegalStateException(message)
                        )
                    }
                }

                override fun onClosed(eventSource: EventSource) {
                    finish { cont.resume(builder.toString()) }
                }
            }

            val eventSource = EventSources.createFactory(client).newEventSource(request, listener)
            cont.invokeOnCancellation { eventSource.cancel() }
        }
    }

    companion object {
        private val errorJson = Json { ignoreUnknownKeys = true }

        /**
         * 将网络层异常消息翻译为中文。
         */
        fun translateNetworkException(t: Throwable): String {
            val msg = t.message ?: ""
            return when {
                msg.contains("Connection closed", ignoreCase = true) ||
                msg.contains("Connection reset", ignoreCase = true) ||
                msg.contains("Software caused connection abort", ignoreCase = true) ||
                msg.contains("broken pipe", ignoreCase = true) ||
                msg.contains("Socket closed", ignoreCase = true) ||
                msg.contains("unexpected end of stream", ignoreCase = true) ->
                    "网络连接中断，请检查网络后重试"
                msg.contains("timeout", ignoreCase = true) ->
                    "网络连接超时，请检查网络后重试"
                msg.contains("Unable to resolve host", ignoreCase = true) ||
                msg.contains("UnknownHost", ignoreCase = true) ||
                msg.contains("No address associated", ignoreCase = true) ->
                    "无法连接服务器，请检查网络连接"
                msg.contains("Connection refused", ignoreCase = true) ||
                msg.contains("Failed to connect", ignoreCase = true) ->
                    "服务器拒绝连接，请稍后重试"
                msg.contains("Network is unreachable", ignoreCase = true) ||
                msg.contains("No route to host", ignoreCase = true) ->
                    "网络不可达，请检查网络连接"
                msg.contains("SSL", ignoreCase = true) ||
                msg.contains("Certificate", ignoreCase = true) ->
                    "安全连接失败，请检查网络环境"
                msg.contains("Canceled", ignoreCase = true) ||
                msg.contains("cancelled", ignoreCase = true) ->
                    "请求已取消"
                else -> msg.ifBlank { "未知错误" }
            }
        }

        /**
         * 解析 API 错误响应体，提取人类可读的错误描述。
         * 同时根据 HTTP 状态码和常见错误关键词映射为用户友好的中文提示。
         */
        fun parseApiErrorMessage(code: Int, body: String): String {
            // 尝试从 JSON 中提取 error.message
            val apiMessage = try {
                val obj = errorJson.parseToJsonElement(body).jsonObject
                obj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                    ?: obj["message"]?.jsonPrimitive?.content
            } catch (_: Exception) {
                null
            }

            val detail = apiMessage ?: body.take(200)

            return when {
                // 余额/配额不足
                detail.contains("insufficient", ignoreCase = true) ||
                        detail.contains("balance", ignoreCase = true) ||
                        detail.contains("quota", ignoreCase = true) ||
                        detail.contains("billing", ignoreCase = true) ->
                    "API 余额不足或配额已用尽，请充值后重试"

                // API Key 问题
                code == 401 ->
                    "API Key 无效或已过期，请在提供方设置中更新"

                detail.contains("invalid_api_key", ignoreCase = true) ||
                        detail.contains("authentication", ignoreCase = true) ||
                        detail.contains("unauthorized", ignoreCase = true) ->
                    "API Key 验证失败，请检查提供方设置"

                // 权限/访问限制
                code == 403 ->
                    "无权访问该资源，请检查 API Key 权限"

                detail.contains("forbidden", ignoreCase = true) ||
                        detail.contains("access_denied", ignoreCase = true) ->
                    "访问被拒绝，请检查 API Key 权限范围"

                // 模型不存在
                detail.contains("model_not_found", ignoreCase = true) ||
                        detail.contains("model not exist", ignoreCase = true) ||
                        detail.contains("does not exist", ignoreCase = true) ->
                    "模型不存在: 请在模型设置中更换可用模型"

                // 请求频率限制
                code == 429 ->
                    "请求过于频繁，请稍后重试"

                detail.contains("rate_limit", ignoreCase = true) ||
                        detail.contains("too many requests", ignoreCase = true) ->
                    "请求频率超限，请稍后重试"

                // 请求内容过长
                code == 413 || detail.contains("too large", ignoreCase = true) ||
                        detail.contains("context_length", ignoreCase = true) ||
                        (detail.contains("token", ignoreCase = true) && detail.contains(
                            "exceed",
                            ignoreCase = true
                        )) ->
                    "请求内容超出模型限制（图片过大或文字过多），请尝试缩小截屏范围"

                // 服务器错误
                code in 500..599 ->
                    "API 服务器异常 (HTTP $code)，请稍后重试"

                // 通用 HTTP 错误
                code in 400..499 ->
                    "请求失败 (HTTP $code): $detail"

                else ->
                    "网络错误: $detail"
            }
        }
    }
}
