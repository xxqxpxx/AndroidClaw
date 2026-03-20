package com.androidclaw.shared.memory

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.androidclaw.db.AndroidClawDb
import com.androidclaw.shared.models.ConversationUiModel
import com.androidclaw.shared.models.MessageRole
import com.androidclaw.shared.models.MessageUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ConversationRepositoryImpl(
    private val db: AndroidClawDb
) : ConversationRepository {

    private val conversationQueries get() = db.conversationQueries
    private val messageQueries get() = db.messageQueries

    override fun getConversations(): Flow<List<ConversationUiModel>> {
        return conversationQueries.getAllConversations()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { conversations ->
                conversations.map { conv ->
                    val lastMsg = messageQueries
                        .getLastMessageForConversation(conv.id)
                        .executeAsOneOrNull()
                    conv.toUiModel(lastMsg?.content?.take(100))
                }
            }
    }

    override fun getMessages(conversationId: String): Flow<List<MessageUiModel>> {
        return messageQueries.getMessagesForConversation(conversationId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { messages ->
                messages.map { it.toUiModel() }
            }
    }

    override suspend fun createConversation(title: String): String = withContext(Dispatchers.Default) {
        val id = Uuid.random().toString()
        val now = Clock.System.now().toEpochMilliseconds()
        conversationQueries.insertConversation(id, title, now, now)
        id
    }

    override suspend fun addMessage(
        conversationId: String,
        role: MessageRole,
        content: String,
        toolName: String?,
        toolInput: String?,
        toolResult: String?,
        tokenCount: Long
    ): String = withContext(Dispatchers.Default) {
        val id = Uuid.random().toString()
        val now = Clock.System.now().toEpochMilliseconds()
        messageQueries.insertMessage(
            id = id,
            conversation_id = conversationId,
            role = role.toDbString(),
            content = content,
            tool_name = toolName,
            tool_input = toolInput,
            tool_result = toolResult,
            created_at = now,
            token_count = tokenCount
        )
        conversationQueries.updateTitle(
            title = conversationQueries.getConversation(conversationId)
                .executeAsOne().title,
            updated_at = now,
            id = conversationId
        )
        id
    }

    override suspend fun updateTitle(conversationId: String, title: String) = withContext(Dispatchers.Default) {
        val now = Clock.System.now().toEpochMilliseconds()
        conversationQueries.updateTitle(title, now, conversationId)
    }

    override suspend fun archiveConversation(conversationId: String) = withContext(Dispatchers.Default) {
        val now = Clock.System.now().toEpochMilliseconds()
        conversationQueries.archiveConversation(now, conversationId)
    }

    override suspend fun getMessagesSnapshot(conversationId: String): List<MessageUiModel> =
        withContext(Dispatchers.Default) {
            messageQueries.getMessagesForConversation(conversationId)
                .executeAsList()
                .map { it.toUiModel() }
        }

    override suspend fun getConversationsSnapshot(): List<ConversationUiModel> =
        withContext(Dispatchers.Default) {
            conversationQueries.getAllConversations()
                .executeAsList()
                .map { it.toUiModel() }
        }

    override suspend fun deleteConversation(conversationId: String) = withContext(Dispatchers.Default) {
        messageQueries.deleteMessagesForConversation(conversationId)
        conversationQueries.deleteConversation(conversationId)
    }

    override suspend fun pinConversation(conversationId: String, pinned: Boolean) = withContext(Dispatchers.Default) {
        val now = Clock.System.now().toEpochMilliseconds()
        conversationQueries.pinConversation(if (pinned) 1L else 0L, now, conversationId)
    }

    override suspend fun setCategory(conversationId: String, category: String?) = withContext(Dispatchers.Default) {
        val now = Clock.System.now().toEpochMilliseconds()
        conversationQueries.setCategory(category, now, conversationId)
    }

    private fun com.androidclaw.db.Conversation.toUiModel(lastMessage: String? = null) = ConversationUiModel(
        id = id,
        title = title.ifEmpty { "New Conversation" },
        lastMessage = lastMessage,
        updatedAt = Instant.fromEpochMilliseconds(updated_at),
        isPinned = is_pinned != 0L,
        category = category
    )

    private fun com.androidclaw.db.Message.toUiModel() = MessageUiModel(
        id = id,
        role = MessageRole.fromDbString(role),
        content = content,
        toolName = tool_name,
        createdAt = Instant.fromEpochMilliseconds(created_at)
    )
}
