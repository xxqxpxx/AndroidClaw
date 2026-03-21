package com.androidclaw.shared.llm

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import io.ktor.utils.io.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

class ClaudeStreamingClient(
    private val httpClient: HttpClient,
    private val baseUrl: String
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    companion object {
        private const val TAG = "AndroidClaw"
        private const val ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages"
        private const val ANTHROPIC_VERSION = "2023-06-01"
    }

    fun streamMessage(request: ClaudeRequest, authToken: String? = null, apiKey: String? = null): Flow<ClaudeStreamEvent> = flow {
        val useDirectApi = !apiKey.isNullOrBlank()
        val url = if (useDirectApi) ANTHROPIC_API_URL else "$baseUrl/api/chat"

        val jsonBody = json.encodeToString(ClaudeRequest.serializer(), request)
        println("$TAG: Sending request to $url, body length=${jsonBody.length}, model=${request.model}")

        // Collect events into a channel so we don't lose them inside execute{}
        val events = mutableListOf<ClaudeStreamEvent>()

        try {
            httpClient.preparePost(url) {
                setBody(TextContent(jsonBody, ContentType.Application.Json))
                if (useDirectApi) {
                    header("x-api-key", apiKey)
                    header("anthropic-version", ANTHROPIC_VERSION)
                } else {
                    authToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                }
            }.execute { response ->
                println("$TAG: Response status=${response.status}")

                if (!response.status.isSuccess()) {
                    val errorBody = try { response.bodyAsText() } catch (_: Exception) { "unable to read body" }
                    println("$TAG: Error response: $errorBody")
                    events.add(ClaudeStreamEvent.Error("HTTP ${response.status.value}: $errorBody"))
                    return@execute
                }

                val channel = response.bodyAsChannel()
                var lineCount = 0
                var dataLineCount = 0

                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break
                    lineCount++

                    if (line.startsWith("data: ")) {
                        val data = line.removePrefix("data: ").trim()
                        dataLineCount++

                        if (dataLineCount <= 3) {
                            println("$TAG: SSE data #$dataLineCount: ${data.take(200)}")
                        }

                        if (data == "[DONE]") {
                            events.add(ClaudeStreamEvent.MessageStop)
                            break
                        }
                        val event = parseSseData(data)
                        if (event != null) {
                            events.add(event)
                        }
                    }
                }
                println("$TAG: Stream finished. Lines read=$lineCount, data events=$dataLineCount, parsed events=${events.size}")
            }
        } catch (e: Exception) {
            println("$TAG: Request failed: ${e::class.simpleName}: ${e.message}")
            events.add(ClaudeStreamEvent.Error("Request failed: ${e.message}"))
        }

        // Now emit all collected events from the proper flow context
        println("$TAG: Emitting ${events.size} events to flow")
        for (event in events) {
            emit(event)
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
                    null // Anthropic sends ping events - ignore them
                }
                data.contains("\"type\":\"error\"") || data.contains("\"type\": \"error\"") -> {
                    val parsed = json.decodeFromString(SseError.serializer(), data)
                    ClaudeStreamEvent.Error(parsed.error.message)
                }
                else -> {
                    println("$TAG: Unknown SSE event type: ${data.take(100)}")
                    null
                }
            }
        } catch (e: Exception) {
            println("$TAG: Parse error for data: ${data.take(200)}, error: ${e.message}")
            ClaudeStreamEvent.Error("Parse error: ${e.message}")
        }
    }
}
