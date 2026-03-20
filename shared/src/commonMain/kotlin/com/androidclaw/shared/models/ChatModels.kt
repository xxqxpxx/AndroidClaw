package com.androidclaw.shared.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

enum class MessageRole {
    USER, ASSISTANT, SYSTEM, TOOL;

    fun toDbString(): String = name.lowercase()

    companion object {
        fun fromDbString(value: String): MessageRole =
            entries.first { it.name.equals(value, ignoreCase = true) }
    }
}

data class ConversationUiModel(
    val id: String,
    val title: String,
    val lastMessage: String?,
    val updatedAt: Instant
)

data class MessageUiModel(
    val id: String,
    val role: MessageRole,
    val content: String,
    val isStreaming: Boolean = false,
    val toolName: String? = null,
    val createdAt: Instant
)

@Serializable
data class ApiConfig(
    val baseUrl: String,
    val authToken: String? = null
)
