package com.androidclaw.shared.llm

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
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

    fun streamMessage(request: ClaudeRequest, authToken: String? = null): Flow<ClaudeStreamEvent> = flow {
        val response = httpClient.preparePost("$baseUrl/api/chat") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ClaudeRequest.serializer(), request))
            authToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            header(HttpHeaders.Accept, "text/event-stream")
        }.execute { response ->
            if (!response.status.isSuccess()) {
                emit(ClaudeStreamEvent.Error("HTTP ${response.status.value}: ${response.status.description}"))
                return@execute
            }

            val channel = response.bodyAsChannel()
            val buffer = StringBuilder()

            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break

                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") {
                        emit(ClaudeStreamEvent.MessageStop)
                        break
                    }
                    val event = parseSseData(data)
                    if (event != null) {
                        emit(event)
                    }
                }
            }
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
                data.contains("\"type\":\"error\"") || data.contains("\"type\": \"error\"") -> {
                    val parsed = json.decodeFromString(SseError.serializer(), data)
                    ClaudeStreamEvent.Error(parsed.error.message)
                }
                else -> null
            }
        } catch (e: Exception) {
            ClaudeStreamEvent.Error("Parse error: ${e.message}")
        }
    }
}
