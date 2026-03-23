package com.androidclaw.shared.llm

import com.androidclaw.shared.logging.Logger
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.json.*

/**
 * OpenAI-compatible streaming client for local/self-hosted LLMs.
 * Works with: Ollama, LM Studio, llama.cpp server, vLLM, LocalAI, text-generation-webui, etc.
 *
 * Converts Claude tool format to OpenAI function calling format and back.
 */
class LocalLlmStreamingClient(
    private val httpClient: HttpClient
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    companion object {
        private const val TAG = "LocalLLM"
    }

    fun streamMessage(
        request: ClaudeRequest,
        baseUrl: String,
        apiKey: String = "",
        model: String = ""
    ): Flow<ClaudeStreamEvent> = channelFlow {
        val effectiveModel = model.ifBlank { request.model }
        val url = "${baseUrl.trimEnd('/')}/v1/chat/completions"

        Logger.i(TAG, "Sending to $url, model=$effectiveModel")

        // Convert Claude format to OpenAI format
        val openAiBody = buildOpenAiRequest(request, effectiveModel)
        val jsonBody = openAiBody.toString()
        Logger.d(TAG, "Request body length=${jsonBody.length}")

        try {
            httpClient.preparePost(url) {
                setBody(TextContent(jsonBody, ContentType.Application.Json))
                header(HttpHeaders.Accept, "text/event-stream")
                if (apiKey.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $apiKey")
                }
            }.execute { response ->
                Logger.i(TAG, "Response status=${response.status}")

                if (!response.status.isSuccess()) {
                    val errorBody = try { response.bodyAsText() } catch (_: Exception) { "unable to read" }
                    Logger.e(TAG, "HTTP error ${response.status.value}: $errorBody")
                    send(ClaudeStreamEvent.Error("Local LLM error: HTTP ${response.status.value}: $errorBody"))
                    return@execute
                }

                val body = response.bodyAsText()

                // Check if streaming SSE or plain JSON
                if (body.trimStart().startsWith("{")) {
                    // Non-streaming response
                    Logger.d(TAG, "Non-streaming response, length=${body.length}")
                    parseOpenAiResponse(body).forEach { send(it) }
                } else {
                    // SSE streaming
                    var textLen = 0
                    var toolCallId = ""
                    var toolCallName = ""
                    var toolCallArgs = StringBuilder()
                    var hasToolCall = false

                    send(ClaudeStreamEvent.MessageStart("local", effectiveModel))

                    for (line in body.lines()) {
                        if (line.isBlank() || !line.startsWith("data: ")) continue
                        val data = line.removePrefix("data: ").trim()
                        if (data == "[DONE]") break

                        try {
                            val chunk = json.parseToJsonElement(data).jsonObject
                            val choices = chunk["choices"]?.jsonArray ?: continue
                            val delta = choices.firstOrNull()?.jsonObject?.get("delta")?.jsonObject ?: continue
                            val finishReason = choices.firstOrNull()?.jsonObject?.get("finish_reason")?.jsonPrimitive?.contentOrNull

                            // Text content
                            val content = delta["content"]?.jsonPrimitive?.contentOrNull
                            if (content != null) {
                                if (textLen == 0) send(ClaudeStreamEvent.ContentBlockStart(0, "text"))
                                textLen += content.length
                                send(ClaudeStreamEvent.TextDelta(content))
                            }

                            // Tool calls (OpenAI format)
                            val toolCalls = delta["tool_calls"]?.jsonArray
                            if (toolCalls != null) {
                                for (tc in toolCalls) {
                                    val tcObj = tc.jsonObject
                                    val fn = tcObj["function"]?.jsonObject
                                    val name = fn?.get("name")?.jsonPrimitive?.contentOrNull
                                    val args = fn?.get("arguments")?.jsonPrimitive?.contentOrNull
                                    val id = tcObj["id"]?.jsonPrimitive?.contentOrNull

                                    if (id != null && name != null) {
                                        if (textLen > 0) {
                                            send(ClaudeStreamEvent.ContentBlockStop(0))
                                        }
                                        toolCallId = id
                                        toolCallName = name
                                        toolCallArgs = StringBuilder()
                                        hasToolCall = true
                                        send(ClaudeStreamEvent.ToolUseStart(1, id, name))
                                    }
                                    if (args != null) {
                                        toolCallArgs.append(args)
                                        send(ClaudeStreamEvent.InputJsonDelta(args))
                                    }
                                }
                            }

                            if (finishReason != null) {
                                if (hasToolCall) {
                                    send(ClaudeStreamEvent.ContentBlockStop(1))
                                    send(ClaudeStreamEvent.MessageDelta("tool_use"))
                                } else {
                                    if (textLen > 0) send(ClaudeStreamEvent.ContentBlockStop(0))
                                    send(ClaudeStreamEvent.MessageDelta("end_turn"))
                                }
                            }
                        } catch (e: Exception) {
                            Logger.w(TAG, "Failed to parse SSE chunk: ${e.message}")
                        }
                    }

                    send(ClaudeStreamEvent.MessageStop)
                    Logger.i(TAG, "Stream done. textLen=$textLen, hasToolCall=$hasToolCall")
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Request failed: ${e::class.simpleName}: ${e.message}", e)
            send(ClaudeStreamEvent.Error("Local LLM request failed: ${e.message}"))
        }
    }

    private fun buildOpenAiRequest(request: ClaudeRequest, model: String): JsonObject {
        return buildJsonObject {
            put("model", model)
            put("max_tokens", request.maxTokens)
            put("stream", true)

            // Convert messages
            putJsonArray("messages") {
                // System message
                if (!request.system.isNullOrBlank()) {
                    addJsonObject {
                        put("role", "system")
                        put("content", request.system)
                    }
                }

                // Conversation messages
                for (msg in request.messages) {
                    addJsonObject {
                        put("role", msg.role)
                        // Convert content blocks to string or OpenAI format
                        val textParts = msg.content.filterIsInstance<ContentBlock.Text>()
                        val toolUseParts = msg.content.filterIsInstance<ContentBlock.ToolUse>()
                        val toolResultParts = msg.content.filterIsInstance<ContentBlock.ToolResult>()

                        when {
                            toolResultParts.isNotEmpty() -> {
                                // Tool results in OpenAI format
                                put("role", "tool")
                                put("content", toolResultParts.first().content)
                                put("tool_call_id", toolResultParts.first().toolUseId)
                            }
                            toolUseParts.isNotEmpty() -> {
                                put("role", "assistant")
                                if (textParts.isNotEmpty()) {
                                    put("content", textParts.joinToString("") { it.text })
                                }
                                putJsonArray("tool_calls") {
                                    for (tu in toolUseParts) {
                                        addJsonObject {
                                            put("id", tu.id)
                                            put("type", "function")
                                            putJsonObject("function") {
                                                put("name", tu.name)
                                                put("arguments", tu.input.toString())
                                            }
                                        }
                                    }
                                }
                            }
                            else -> {
                                put("content", textParts.joinToString("") { it.text })
                            }
                        }
                    }
                }
            }

            // Convert Claude tools to OpenAI functions
            if (!request.tools.isNullOrEmpty()) {
                putJsonArray("tools") {
                    for (tool in request.tools!!) {
                        addJsonObject {
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", tool.inputSchema)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun parseOpenAiResponse(body: String): List<ClaudeStreamEvent> {
        return try {
            val response = json.parseToJsonElement(body).jsonObject
            val events = mutableListOf<ClaudeStreamEvent>()
            val model = response["model"]?.jsonPrimitive?.contentOrNull ?: "local"
            val id = response["id"]?.jsonPrimitive?.contentOrNull ?: "local"

            events.add(ClaudeStreamEvent.MessageStart(id, model))

            val choices = response["choices"]?.jsonArray ?: return listOf(ClaudeStreamEvent.Error("No choices in response"))
            val message = choices.firstOrNull()?.jsonObject?.get("message")?.jsonObject ?: return listOf(ClaudeStreamEvent.Error("No message"))

            val content = message["content"]?.jsonPrimitive?.contentOrNull
            if (content != null) {
                events.add(ClaudeStreamEvent.ContentBlockStart(0, "text"))
                events.add(ClaudeStreamEvent.TextDelta(content))
                events.add(ClaudeStreamEvent.ContentBlockStop(0))
            }

            val toolCalls = message["tool_calls"]?.jsonArray
            if (toolCalls != null) {
                for ((i, tc) in toolCalls.withIndex()) {
                    val tcObj = tc.jsonObject
                    val fn = tcObj["function"]?.jsonObject ?: continue
                    val tcId = tcObj["id"]?.jsonPrimitive?.contentOrNull ?: "tc_$i"
                    val name = fn["name"]?.jsonPrimitive?.contentOrNull ?: continue
                    val args = fn["arguments"]?.jsonPrimitive?.contentOrNull ?: "{}"

                    events.add(ClaudeStreamEvent.ToolUseStart(i + 1, tcId, name))
                    events.add(ClaudeStreamEvent.InputJsonDelta(args))
                    events.add(ClaudeStreamEvent.ContentBlockStop(i + 1))
                }
            }

            val finishReason = choices.firstOrNull()?.jsonObject?.get("finish_reason")?.jsonPrimitive?.contentOrNull
            val stopReason = if (toolCalls != null && toolCalls.isNotEmpty()) "tool_use" else "end_turn"
            events.add(ClaudeStreamEvent.MessageDelta(stopReason))
            events.add(ClaudeStreamEvent.MessageStop)

            events
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to parse response", e)
            listOf(ClaudeStreamEvent.Error("Parse error: ${e.message}"))
        }
    }
}
