package com.androidclaw.shared.agent

import com.androidclaw.shared.llm.*
import com.androidclaw.shared.memory.ConversationRepository
import com.androidclaw.shared.models.MessageRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

sealed class AgentEvent {
    data class TextDelta(val text: String) : AgentEvent()
    data class ToolCallStart(val toolName: String, val toolId: String) : AgentEvent()
    data class ToolCallComplete(val toolId: String, val result: String) : AgentEvent()
    data class MessageComplete(val fullText: String) : AgentEvent()
    data class Error(val throwable: Throwable) : AgentEvent()
}

class AgentLoop(
    private val client: ClaudeStreamingClient,
    private val tools: List<Tool>,
    private val conversationRepo: ConversationRepository,
    private val config: AgentConfig = AgentConfig()
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val toolMap = tools.associateBy { it.name }

    fun run(conversationId: String, userMessage: String, authToken: String? = null): Flow<AgentEvent> = flow {
        // Save user message
        conversationRepo.addMessage(conversationId, MessageRole.USER, userMessage)

        // Auto-title: set conversation title from first user message
        autoTitleIfNeeded(conversationId, userMessage)

        // Build message history
        val messages = buildMessageHistory(conversationId)
        var currentMessages = messages.toMutableList()
        var iterations = 0

        while (iterations < config.maxToolIterations) {
            iterations++

            val request = ClaudeRequest(
                model = config.model,
                maxTokens = config.maxTokens,
                messages = currentMessages,
                system = config.systemPrompt,
                tools = if (tools.isNotEmpty()) tools.map { it.toClaudeToolDefinition() } else null,
                stream = true
            )

            val textBuilder = StringBuilder()
            val toolCalls = mutableListOf<PendingToolCall>()
            var currentToolCall: PendingToolCall? = null
            var stopReason: String? = null

            client.streamMessage(request, authToken).collect { event ->
                when (event) {
                    is ClaudeStreamEvent.TextDelta -> {
                        textBuilder.append(event.text)
                        emit(AgentEvent.TextDelta(event.text))
                    }
                    is ClaudeStreamEvent.ToolUseStart -> {
                        currentToolCall = PendingToolCall(event.id, event.name, StringBuilder())
                        emit(AgentEvent.ToolCallStart(event.name, event.id))
                    }
                    is ClaudeStreamEvent.InputJsonDelta -> {
                        currentToolCall?.inputJson?.append(event.partialJson)
                    }
                    is ClaudeStreamEvent.ContentBlockStop -> {
                        currentToolCall?.let { tc ->
                            toolCalls.add(tc)
                            currentToolCall = null
                        }
                    }
                    is ClaudeStreamEvent.MessageDelta -> {
                        stopReason = event.stopReason
                    }
                    is ClaudeStreamEvent.Error -> {
                        emit(AgentEvent.Error(RuntimeException(event.message)))
                    }
                    else -> {}
                }
            }

            if (stopReason == "tool_use" && toolCalls.isNotEmpty()) {
                // Build assistant message with text + tool_use blocks
                val assistantContent = mutableListOf<ContentBlock>()
                if (textBuilder.isNotEmpty()) {
                    assistantContent.add(ContentBlock.Text(textBuilder.toString()))
                }
                for (tc in toolCalls) {
                    val inputObj = try {
                        json.decodeFromString(JsonObject.serializer(), tc.inputJson.toString())
                    } catch (_: Exception) {
                        JsonObject(emptyMap())
                    }
                    assistantContent.add(ContentBlock.ToolUse(tc.id, tc.name, inputObj))
                }
                currentMessages.add(ClaudeMessage("assistant", assistantContent))

                // Execute tools and build tool_result message
                val toolResults = mutableListOf<ContentBlock>()
                for (tc in toolCalls) {
                    val tool = toolMap[tc.name]
                    val result = if (tool != null) {
                        val inputObj = try {
                            json.decodeFromString(JsonObject.serializer(), tc.inputJson.toString())
                        } catch (_: Exception) {
                            JsonObject(emptyMap())
                        }
                        tool.execute(inputObj)
                    } else {
                        ToolResult("Unknown tool: ${tc.name}", isError = true)
                    }
                    toolResults.add(ContentBlock.ToolResult(tc.id, result.content, result.isError))
                    emit(AgentEvent.ToolCallComplete(tc.id, result.content))
                }
                currentMessages.add(ClaudeMessage("user", toolResults))

                // Continue loop for next Claude response
            } else {
                // Final text response
                val fullText = textBuilder.toString()
                if (fullText.isNotEmpty()) {
                    conversationRepo.addMessage(conversationId, MessageRole.ASSISTANT, fullText)
                }
                emit(AgentEvent.MessageComplete(fullText))
                break
            }
        }
    }

    private suspend fun autoTitleIfNeeded(conversationId: String, userMessage: String) {
        try {
            val conversations = conversationRepo.getConversationsSnapshot()
            val conversation = conversations.find { it.id == conversationId }
            if (conversation != null && (conversation.title.isEmpty() || conversation.title == "New Conversation")) {
                // Generate title from first message: take first sentence or first N words
                val title = generateTitle(userMessage)
                conversationRepo.updateTitle(conversationId, title)
            }
        } catch (_: Exception) {
            // Non-critical - don't fail the conversation
        }
    }

    private fun generateTitle(message: String): String {
        val cleaned = message.trim()

        // Use first sentence if short enough
        val firstSentence = cleaned.split(Regex("""[.!?]""")).firstOrNull()?.trim() ?: cleaned
        if (firstSentence.length in 1..50) return firstSentence

        // Otherwise take first few words
        val words = cleaned.split(Regex("""\s+"""))
        val titleWords = words.take(6).joinToString(" ")
        return if (titleWords.length > 50) titleWords.take(47) + "..." else titleWords
    }

    private suspend fun buildMessageHistory(conversationId: String): List<ClaudeMessage> {
        val messages = conversationRepo.getMessagesSnapshot(conversationId)
        return messages.mapNotNull { msg ->
            when (msg.role) {
                MessageRole.USER -> ClaudeMessage.user(msg.content)
                MessageRole.ASSISTANT -> ClaudeMessage.assistant(msg.content)
                MessageRole.SYSTEM -> null // System messages go in the system param
                MessageRole.TOOL -> null   // Tool results are handled inline
            }
        }
    }

    private data class PendingToolCall(
        val id: String,
        val name: String,
        val inputJson: StringBuilder
    )
}
