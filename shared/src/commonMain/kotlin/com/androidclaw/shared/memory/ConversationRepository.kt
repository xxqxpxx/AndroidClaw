package com.androidclaw.shared.memory

import com.androidclaw.shared.models.ConversationUiModel
import com.androidclaw.shared.models.MessageRole
import com.androidclaw.shared.models.MessageUiModel
import kotlinx.coroutines.flow.Flow

interface ConversationRepository {
    fun getConversations(): Flow<List<ConversationUiModel>>
    fun getMessages(conversationId: String): Flow<List<MessageUiModel>>
    suspend fun createConversation(title: String = ""): String
    suspend fun addMessage(
        conversationId: String,
        role: MessageRole,
        content: String,
        toolName: String? = null,
        toolInput: String? = null,
        toolResult: String? = null,
        tokenCount: Long = 0
    ): String
    suspend fun updateTitle(conversationId: String, title: String)
    suspend fun archiveConversation(conversationId: String)
    suspend fun getMessagesSnapshot(conversationId: String): List<MessageUiModel>
    suspend fun getConversationsSnapshot(): List<ConversationUiModel>
    suspend fun deleteConversation(conversationId: String)
}
