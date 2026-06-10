package com.tianhuiu.solvex.network

import com.tianhuiu.solvex.data.models.ProviderKind
import kotlinx.serialization.json.*

/**
 * 工具注册表：定义可供模型调用的工具函数，并负责将其格式化为不同提供商要求的 JSON 结构。
 */
internal object ToolRegistry {

    fun formatForProvider(tools: List<ToolDef>, kind: ProviderKind): JsonElement = when (kind) {
        ProviderKind.OPENAI_COMPATIBLE -> formatChatCompletions(tools)
        ProviderKind.OPENAI_RESPONSES -> formatResponses(tools)
        ProviderKind.ANTHROPIC -> formatAnthropic(tools)
        ProviderKind.GOOGLE -> formatGoogle(tools)
    }

    private fun formatChatCompletions(tools: List<ToolDef>): JsonArray = buildJsonArray {
        for (tool in tools) {
            add(buildJsonObject {
                put("type", "function")
                put("function", buildJsonObject {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("parameters", buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject {
                            for (p in tool.params) {
                                put(p.name, buildJsonObject {
                                    put("type", p.type)
                                    put("description", p.description)
                                    p.pattern?.let { put("pattern", it) }
                                })
                            }
                        })
                        put("required", buildJsonArray {
                            for (p in tool.params) add(JsonPrimitive(p.name))
                        })
                        put("additionalProperties", value = false)
                    })
                })
            })
        }
    }

    private fun formatResponses(tools: List<ToolDef>): JsonArray = buildJsonArray {
        for (tool in tools) {
            add(buildJsonObject {
                put("type", "function")
                put("name", tool.name)
                put("description", tool.description)
                put("parameters", buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        for (p in tool.params) {
                            put(p.name, buildJsonObject {
                                put("type", p.type)
                                put("description", p.description)
                                p.pattern?.let { put("pattern", it) }
                            })
                        }
                    })
                    put("required", buildJsonArray {
                        for (p in tool.params) add(JsonPrimitive(p.name))
                    })
                    put("additionalProperties", false)
                })
            })
        }
    }

    private fun formatAnthropic(tools: List<ToolDef>): JsonArray = buildJsonArray {
        for (tool in tools) {
            add(buildJsonObject {
                put("name", tool.name)
                put("description", tool.description)
                put("input_schema", buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        for (p in tool.params) {
                            put(p.name, buildJsonObject {
                                put("type", p.type)
                                put("description", p.description)
                                p.pattern?.let { put("pattern", it) }
                            })
                        }
                    })
                    put("required", buildJsonArray {
                        for (p in tool.params) add(JsonPrimitive(p.name))
                    })
                })
            })
        }
    }

    private fun formatGoogle(tools: List<ToolDef>): JsonObject = buildJsonObject {
        put("functionDeclarations", buildJsonArray {
            for (tool in tools) {
                add(buildJsonObject {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("parameters", buildJsonObject {
                        put("type", "OBJECT")
                        put("properties", buildJsonObject {
                            for (p in tool.params) {
                                put(p.name, buildJsonObject {
                                    put("type", "STRING")
                                    put("description", p.description)
                                    p.pattern?.let { put("pattern", it) }
                                })
                            }
                        })
                        put("required", buildJsonArray {
                            for (p in tool.params) add(JsonPrimitive(p.name))
                        })
                    })
                })
            }
        })
    }
}
