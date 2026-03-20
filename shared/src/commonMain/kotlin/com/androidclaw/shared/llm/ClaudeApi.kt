package com.androidclaw.shared.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class ClaudeRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val messages: List<ClaudeMessage>,
    val system: String? = null,
    val tools: List<ClaudeToolDefinition>? = null,
    val stream: Boolean = true
)

@Serializable
data class ClaudeMessage(
    val role: String,
    val content: List<ContentBlock>
) {
    companion object {
        fun user(text: String) = ClaudeMessage("user", listOf(ContentBlock.Text(text)))
        fun assistant(text: String) = ClaudeMessage("assistant", listOf(ContentBlock.Text(text)))
    }
}

@Serializable
sealed class ContentBlock {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : ContentBlock()

    @Serializable
    @SerialName("tool_use")
    data class ToolUse(
        val id: String,
        val name: String,
        val input: JsonObject
    ) : ContentBlock()

    @Serializable
    @SerialName("tool_result")
    data class ToolResult(
        @SerialName("tool_use_id") val toolUseId: String,
        val content: String,
        @SerialName("is_error") val isError: Boolean = false
    ) : ContentBlock()
}

@Serializable
data class ClaudeToolDefinition(
    val name: String,
    val description: String,
    @SerialName("input_schema") val inputSchema: JsonObject
)

// SSE Events
sealed class ClaudeStreamEvent {
    data class MessageStart(val messageId: String, val model: String) : ClaudeStreamEvent()
    data class ContentBlockStart(val index: Int, val type: String) : ClaudeStreamEvent()
    data class TextDelta(val text: String) : ClaudeStreamEvent()
    data class InputJsonDelta(val partialJson: String) : ClaudeStreamEvent()
    data class ContentBlockStop(val index: Int) : ClaudeStreamEvent()
    data class MessageDelta(val stopReason: String?) : ClaudeStreamEvent()
    data object MessageStop : ClaudeStreamEvent()
    data class ToolUseStart(val index: Int, val id: String, val name: String) : ClaudeStreamEvent()
    data class Error(val message: String) : ClaudeStreamEvent()
}

// Internal SSE parsing models
@Serializable
internal data class SseMessageStart(
    val type: String,
    val message: SseMessageBody
)

@Serializable
internal data class SseMessageBody(
    val id: String,
    val model: String,
    val role: String
)

@Serializable
internal data class SseContentBlockStart(
    val type: String,
    val index: Int,
    @SerialName("content_block") val contentBlock: SseContentBlock
)

@Serializable
internal data class SseContentBlock(
    val type: String,
    val id: String? = null,
    val name: String? = null,
    val text: String? = null
)

@Serializable
internal data class SseContentBlockDelta(
    val type: String,
    val index: Int,
    val delta: SseDelta
)

@Serializable
internal data class SseDelta(
    val type: String,
    val text: String? = null,
    @SerialName("partial_json") val partialJson: String? = null
)

@Serializable
internal data class SseContentBlockStop(
    val type: String,
    val index: Int
)

@Serializable
internal data class SseMessageDelta(
    val type: String,
    val delta: SseMessageDeltaBody
)

@Serializable
internal data class SseMessageDeltaBody(
    @SerialName("stop_reason") val stopReason: String? = null
)

@Serializable
internal data class SseError(
    val type: String,
    val error: SseErrorBody
)

@Serializable
internal data class SseErrorBody(
    val type: String,
    val message: String
)
