package com.androidclaw.shared.agent

import com.androidclaw.shared.llm.*
import com.androidclaw.shared.logging.Logger
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
    private val config: AgentConfig = AgentConfig(),
    private val contextManager: ContextManager = ContextManager(),
    private val localLlmClient: LocalLlmStreamingClient? = null
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val toolMap = tools.associateBy { it.name }

    // Local LLM config — set externally before calling run()
    var useLocalLlm: Boolean = false
    var localLlmUrl: String = "http://localhost:11434"
    var localLlmModel: String = ""
    var localLlmApiKey: String = ""

    fun run(conversationId: String, userMessage: String, authToken: String? = null, apiKey: String? = null): Flow<AgentEvent> = flow {
        Logger.i(TAG, "Starting run for conversation=$conversationId, message=${userMessage.take(50)}")

        // Save user message
        conversationRepo.addMessage(conversationId, MessageRole.USER, userMessage)

        // Auto-title: set conversation title from first user message
        autoTitleIfNeeded(conversationId, userMessage)

        // Build message history
        val messages = buildMessageHistory(conversationId)
        var currentMessages = messages.toMutableList()
        var iterations = 0
        Logger.i(TAG, "Message history size=${currentMessages.size}, model=${config.model}")

        while (iterations < config.maxToolIterations) {
            iterations++
            Logger.d(TAG, "Iteration $iterations")

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
            var eventCount = 0

            val streamFlow = if (useLocalLlm && localLlmClient != null) {
                Logger.i(TAG, "Using local LLM: $localLlmUrl, model=$localLlmModel")
                localLlmClient.streamMessage(request, localLlmUrl, localLlmApiKey, localLlmModel)
            } else {
                client.streamMessage(request, authToken, apiKey)
            }

            streamFlow.collect { event ->
                eventCount++
                if (eventCount <= 5) {
                    Logger.d(TAG, "Event #$eventCount: ${event::class.simpleName}")
                }
                when (event) {
                    is ClaudeStreamEvent.TextDelta -> {
                        textBuilder.append(event.text)
                        emit(AgentEvent.TextDelta(event.text))
                    }
                    is ClaudeStreamEvent.ToolUseStart -> {
                        Logger.i(TAG, "Tool call started: ${event.name}")
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
                        Logger.d(TAG, "MessageDelta stopReason=$stopReason")
                    }
                    is ClaudeStreamEvent.Error -> {
                        Logger.e(TAG, "Stream error: ${event.message}")
                        emit(AgentEvent.Error(RuntimeException(event.message)))
                    }
                    else -> {}
                }
            }
            Logger.i(TAG, "Stream done. Events=$eventCount, textLen=${textBuilder.length}, toolCalls=${toolCalls.size}, stopReason=$stopReason")

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
                    val inputStr = tc.inputJson.toString()
                    Logger.i(TAG, "Executing tool=${tc.name}, id=${tc.id}, input=${inputStr.take(500)}")
                    val result = if (tool != null) {
                        val inputObj = try {
                            json.decodeFromString(JsonObject.serializer(), inputStr)
                        } catch (e: Exception) {
                            Logger.e(TAG, "Failed to parse tool input for ${tc.name}", e)
                            JsonObject(emptyMap())
                        }
                        try {
                            tool.execute(inputObj).also { r ->
                                Logger.i(TAG, "Tool ${tc.name} result: isError=${r.isError}, content=${r.content.take(300)}")
                            }
                        } catch (e: Exception) {
                            Logger.e(TAG, "Tool ${tc.name} CRASHED: ${e::class.simpleName}: ${e.message}", e)
                            ToolResult("Tool execution failed: ${e.message}", isError = true)
                        }
                    } else {
                        Logger.w(TAG, "Unknown tool: ${tc.name}")
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
                Logger.i(TAG, "Final response, length=${fullText.length}, iterations=$iterations")
                if (fullText.isNotEmpty()) {
                    conversationRepo.addMessage(conversationId, MessageRole.ASSISTANT, fullText)
                }
                emit(AgentEvent.MessageComplete(fullText))
                break
            }
        }
        if (iterations >= config.maxToolIterations) {
            Logger.w(TAG, "Hit max tool iterations (${config.maxToolIterations})")
        }
    }

    private suspend fun autoTitleIfNeeded(conversationId: String, userMessage: String) {
        try {
            val conversations = conversationRepo.getConversationsSnapshot()
            val conversation = conversations.find { it.id == conversationId }
            if (conversation != null && (conversation.title.isEmpty() || conversation.title == "New Conversation")) {
                val title = generateTitle(userMessage)
                conversationRepo.updateTitle(conversationId, title)
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to auto-title conversation: ${e.message}")
        }
    }

    private fun generateTitle(message: String): String {
        val cleaned = message.trim()
        val firstSentence = cleaned.split(Regex("""[.!?]""")).firstOrNull()?.trim() ?: cleaned
        if (firstSentence.length in 1..50) return firstSentence
        val words = cleaned.split(Regex("""\s+"""))
        val titleWords = words.take(6).joinToString(" ")
        return if (titleWords.length > 50) titleWords.take(47) + "..." else titleWords
    }

    private suspend fun buildMessageHistory(conversationId: String): List<ClaudeMessage> {
        val allMessages = conversationRepo.getMessagesSnapshot(conversationId)
        val messages = contextManager.trimToFit(allMessages)
        return messages.mapNotNull { msg ->
            when (msg.role) {
                MessageRole.USER -> ClaudeMessage.user(msg.content)
                MessageRole.ASSISTANT -> ClaudeMessage.assistant(msg.content)
                MessageRole.SYSTEM -> null
                MessageRole.TOOL -> null
            }
        }
    }

    private data class PendingToolCall(
        val id: String,
        val name: String,
        val inputJson: StringBuilder
    )

    companion object {
        private const val TAG = "AgentLoop"
    }
}
