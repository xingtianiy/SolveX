package com.tianhuiu.solvex.network.adapter

import android.util.Log
import com.tianhuiu.solvex.data.models.ModelProvider
import com.tianhuiu.solvex.data.models.ProviderKind
import com.tianhuiu.solvex.network.LlmEvent
import com.tianhuiu.solvex.network.ProviderAdapter
import com.tianhuiu.solvex.network.SseStreamClient
import com.tianhuiu.solvex.network.StreamRequest
import com.tianhuiu.solvex.network.ToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * OpenAI Chat Completions API 适配器。
 */
class OpenAiChatAdapter(
    private val client: OkHttpClient,
    internal val sseClient: SseStreamClient,
    private val json: Json
) : ProviderAdapter {

    override suspend fun stream(request: StreamRequest): Flow<LlmEvent> = callbackFlow {
        // 构建 OpenAI Chat Completions 请求体
        val body = buildJsonObject {
            put("model", request.model)
            put("stream", true)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", request.systemPrompt) })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text"); put(
                            "text",
                            request.userPrompt
                        )
                        })
                        request.imagesBase64.forEach { img ->
                            add(buildJsonObject {
                                put("type", "image_url")
                                put(
                                    "image_url",
                                    buildJsonObject { put("url", "data:image/jpeg;base64,$img") })
                            })
                        }
                    })
                })
            })
            request.tools?.let { tools ->
                put("tools", ToolRegistry.formatForProvider(tools, ProviderKind.OPENAI_COMPATIBLE))
            }
        }

        val httpRequest = Request.Builder()
            .url("${request.provider.url}/chat/completions")
            .header("Authorization", "Bearer ${request.provider.apiKey}")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val job = launch {
            try {
                sseClient.stream(
                    request = httpRequest,
                    firstDeltaTimeoutMillis = request.firstDeltaTimeoutMillis,
                    onEvent = { _, _, _, data ->
                        if (data == "[DONE]") return@stream SseStreamClient.StreamEventResult(done = true)
                        try {
                            val obj = json.parseToJsonElement(data).jsonObject
                            val delta =
                                obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("delta")?.jsonObject
                            // 提取文本增量
                            val content = delta?.get("content")?.jsonPrimitive?.content
                            if (content != null) return@stream SseStreamClient.StreamEventResult(
                                delta = content
                            )
                            // 提取工具调用
                            val toolCalls = delta?.get("tool_calls")?.jsonArray
                            if (toolCalls != null) {
                                val func =
                                    toolCalls.firstOrNull()?.jsonObject?.get("function")?.jsonObject
                                val name = func?.get("name")?.jsonPrimitive?.content
                                val args = func?.get("arguments")?.jsonPrimitive?.content ?: "{}"
                                if (name != null) {
                                    val argsMap =
                                        json.parseToJsonElement(args).jsonObject.mapValues { it.value.jsonPrimitive.content }
                                    return@stream SseStreamClient.StreamEventResult(
                                        toolCall = LlmEvent.ToolCall(
                                            name,
                                            argsMap
                                        )
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("OpenAiChatAdapter", "解析错误: $data", e)
                        }
                        null
                    },
                    onDelta = { trySend(LlmEvent.TextDelta(it)) },
                    onToolCall = { trySend(it) }
                )
                trySend(LlmEvent.Done); close()
            } catch (e: Exception) {
                trySend(LlmEvent.Error(e.message ?: "未知错误")); close(e)
            }
        }
        awaitClose { job.cancel() }
    }

    override suspend fun fetchModels(provider: ModelProvider): List<String> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${provider.url}/models")
                .header("Authorization", "Bearer ${provider.apiKey}")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: return@withContext emptyList()
                    val jsonObject = json.parseToJsonElement(body).jsonObject
                    jsonObject["data"]?.jsonArray?.asSequence()
                        ?.map { it.jsonObject["id"]?.jsonPrimitive?.content ?: "" }
                        ?.filter { it.isNotEmpty() }?.toList() ?: emptyList()
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
}
