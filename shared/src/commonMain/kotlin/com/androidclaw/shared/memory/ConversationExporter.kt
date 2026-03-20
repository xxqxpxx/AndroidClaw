package com.androidclaw.shared.memory

import com.androidclaw.shared.models.MessageRole
import com.androidclaw.shared.models.MessageUiModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ExportedConversation(
    val id: String,
    val title: String,
    val exportedAt: Long,
    val messages: List<ExportedMessage>
)

@Serializable
data class ExportedMessage(
    val role: String,
    val content: String,
    val timestamp: Long
)

class ConversationExporter(
    private val repository: ConversationRepository
) {
    private val json = Json { prettyPrint = true }

    suspend fun exportToJson(conversationId: String): String {
        val messages = repository.getMessagesSnapshot(conversationId)
        val conversations = repository.getConversationsSnapshot()
        val conversation = conversations.find { it.id == conversationId }

        val exported = ExportedConversation(
            id = conversationId,
            title = conversation?.title ?: "Untitled",
            exportedAt = kotlinx.datetime.Clock.System.now().epochSeconds,
            messages = messages.map { msg ->
                ExportedMessage(
                    role = msg.role.name.lowercase(),
                    content = msg.content,
                    timestamp = msg.createdAt.epochSeconds
                )
            }
        )

        return json.encodeToString(exported)
    }

    suspend fun exportToMarkdown(conversationId: String): String {
        val messages = repository.getMessagesSnapshot(conversationId)
        val conversations = repository.getConversationsSnapshot()
        val conversation = conversations.find { it.id == conversationId }

        return buildString {
            appendLine("# ${conversation?.title ?: "Conversation"}")
            appendLine()

            for (msg in messages) {
                val prefix = when (msg.role) {
                    MessageRole.USER -> "**You**"
                    MessageRole.ASSISTANT -> "**AndroidClaw**"
                    MessageRole.SYSTEM -> "**System**"
                    MessageRole.TOOL -> "**Tool**"
                }
                appendLine("$prefix:")
                appendLine(msg.content)
                appendLine()
            }
        }
    }

    suspend fun exportToPlainText(conversationId: String): String {
        val messages = repository.getMessagesSnapshot(conversationId)

        return buildString {
            for (msg in messages) {
                val prefix = when (msg.role) {
                    MessageRole.USER -> "You"
                    MessageRole.ASSISTANT -> "AndroidClaw"
                    MessageRole.SYSTEM -> "System"
                    MessageRole.TOOL -> "Tool"
                }
                appendLine("[$prefix]")
                appendLine(msg.content)
                appendLine()
            }
        }
    }
}
