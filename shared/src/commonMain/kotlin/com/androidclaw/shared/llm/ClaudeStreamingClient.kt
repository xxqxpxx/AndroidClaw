package com.androidclaw.shared.llm

import com.androidclaw.shared.logging.Logger
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class ClaudeStreamingClient(
    private val httpClient: HttpClient,
    private val baseUrl: String
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    companion object {
        private const val TAG = "ClaudeClient"
        private const val ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages"
        private const val ANTHROPIC_VERSION = "2023-06-01"
    }

    fun streamMessage(request: ClaudeRequest, authToken: String? = null, apiKey: String? = null): Flow<ClaudeStreamEvent> = channelFlow {
        val useDirectApi = !apiKey.isNullOrBlank()
        val url = if (useDirectApi) ANTHROPIC_API_URL else "$baseUrl/api/chat"

        val jsonBody = json.encodeToString(ClaudeRequest.serializer(), request)
        Logger.i(TAG, "Sending request to $url, body length=${jsonBody.length}, model=${request.model}")

        try {
            httpClient.preparePost(url) {
                setBody(TextContent(jsonBody, ContentType.Application.Json))
                header(HttpHeaders.Accept, "text/event-stream")
                if (useDirectApi) {
                    header("x-api-key", apiKey)
                    header("anthropic-version", ANTHROPIC_VERSION)
                } else {
                    authToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                }
            }.execute { response ->
                Logger.i(TAG, "Response status=${response.status}")

                if (!response.status.isSuccess()) {
                    val errorBody = try { response.bodyAsText() } catch (_: Exception) { "unable to read body" }
                    Logger.e(TAG, "HTTP error ${response.status.value}: $errorBody")
                    send(ClaudeStreamEvent.Error("HTTP ${response.status.value}: $errorBody"))
                    return@execute
                }

                val body = response.bodyAsText()
                Logger.d(TAG, "Response body length=${body.length}")

                // Check if this is SSE or a plain JSON response
                if (body.trimStart().startsWith("{")) {
                    Logger.w(TAG, "Got non-streaming JSON response, parsing directly")
                    parseNonStreamingResponse(body)?.forEach { event -> send(event) }
                } else {
                    val lines = body.lines()
                    var lineCount = 0
                    var dataLineCount = 0

                    for (line in lines) {
                        if (line.isBlank()) continue
                        lineCount++

                        if (line.startsWith("data: ")) {
                            val data = line.removePrefix("data: ").trim()
                            dataLineCount++

                            if (dataLineCount <= 3) {
                                Logger.d(TAG, "SSE data #$dataLineCount: ${data.take(200)}")
                            }

                            if (data == "[DONE]") {
                                send(ClaudeStreamEvent.MessageStop)
                                break
                            }
                            val event = parseSseData(data)
                            if (event != null) {
                                send(event)
                            }
                        }
                    }

                    Logger.i(TAG, "Stream finished. Lines=$lineCount, dataEvents=$dataLineCount")
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Request failed: ${e::class.simpleName}: ${e.message}", e)
            send(ClaudeStreamEvent.Error("Request failed: ${e.message}"))
        }
    }

    private fun parseSseData(data: String): ClaudeStreamEvent? {
        return try {
            when {
                data.contains("\"type\":\"message_start\"") || data.contains("\"type\": \"message_start\"") -> {
                    val parsed = json.decodeFromString(SseMessageStart.serializer(), data)
                    ClaudeStreamEvent.MessageStart(parsed.message.id, parsed.message.model)
                }
                data.contains("\"type\":\"content_block_start\"") || data.contains("\"type\": \"content_block_start\"") -> {
                    val parsed = json.decodeFromString(SseContentBlockStart.serializer(), data)
                    if (parsed.contentBlock.type == "tool_use") {
                        ClaudeStreamEvent.ToolUseStart(
                            parsed.index,
                            parsed.contentBlock.id ?: "",
                            parsed.contentBlock.name ?: ""
                        )
                    } else {
                        ClaudeStreamEvent.ContentBlockStart(parsed.index, parsed.contentBlock.type)
                    }
                }
                data.contains("\"type\":\"content_block_delta\"") || data.contains("\"type\": \"content_block_delta\"") -> {
                    val parsed = json.decodeFromString(SseContentBlockDelta.serializer(), data)
                    when (parsed.delta.type) {
                        "text_delta" -> ClaudeStreamEvent.TextDelta(parsed.delta.text ?: "")
                        "input_json_delta" -> ClaudeStreamEvent.InputJsonDelta(parsed.delta.partialJson ?: "")
                        else -> null
                    }
                }
                data.contains("\"type\":\"content_block_stop\"") || data.contains("\"type\": \"content_block_stop\"") -> {
                    val parsed = json.decodeFromString(SseContentBlockStop.serializer(), data)
                    ClaudeStreamEvent.ContentBlockStop(parsed.index)
                }
                data.contains("\"type\":\"message_delta\"") || data.contains("\"type\": \"message_delta\"") -> {
                    val parsed = json.decodeFromString(SseMessageDelta.serializer(), data)
                    ClaudeStreamEvent.MessageDelta(parsed.delta.stopReason)
                }
                data.contains("\"type\":\"message_stop\"") || data.contains("\"type\": \"message_stop\"") -> {
                    ClaudeStreamEvent.MessageStop
                }
                data.contains("\"type\":\"ping\"") || data.contains("\"type\": \"ping\"") -> {
                    null
                }
                data.contains("\"type\":\"error\"") || data.contains("\"type\": \"error\"") -> {
                    val parsed = json.decodeFromString(SseError.serializer(), data)
                    ClaudeStreamEvent.Error(parsed.error.message)
                }
                else -> {
                    Logger.w(TAG, "Unknown SSE event type: ${data.take(100)}")
                    null
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Parse error for data: ${data.take(200)}", e)
            ClaudeStreamEvent.Error("Parse error: ${e.message}")
        }
    }

    private fun parseNonStreamingResponse(body: String): List<ClaudeStreamEvent> {
        return try {
            val response = json.decodeFromString(NonStreamingResponse.serializer(), body)
            val events = mutableListOf<ClaudeStreamEvent>()
            events.add(ClaudeStreamEvent.MessageStart(response.id, response.model))

            response.content.forEachIndexed { index, block ->
                when (block.type) {
                    "text" -> {
                        events.add(ClaudeStreamEvent.ContentBlockStart(index, "text"))
                        events.add(ClaudeStreamEvent.TextDelta(block.text ?: ""))
                        events.add(ClaudeStreamEvent.ContentBlockStop(index))
                    }
                    "tool_use" -> {
                        events.add(ClaudeStreamEvent.ToolUseStart(index, block.id ?: "", block.name ?: ""))
                        events.add(ClaudeStreamEvent.InputJsonDelta(block.input?.toString() ?: "{}"))
                        events.add(ClaudeStreamEvent.ContentBlockStop(index))
                    }
                }
            }

            events.add(ClaudeStreamEvent.MessageDelta(response.stopReason))
            events.add(ClaudeStreamEvent.MessageStop)
            events
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to parse non-streaming response", e)
            listOf(ClaudeStreamEvent.Error("Failed to parse response: ${e.message}"))
        }
    }

    @Serializable
    private data class NonStreamingResponse(
        val id: String,
        val model: String,
        val type: String = "message",
        val role: String = "assistant",
        val content: List<NonStreamingContentBlock>,
        @SerialName("stop_reason") val stopReason: String? = null
    )

    @Serializable
    private data class NonStreamingContentBlock(
        val type: String,
        val text: String? = null,
        val id: String? = null,
        val name: String? = null,
        val input: JsonObject? = null
    )
}
