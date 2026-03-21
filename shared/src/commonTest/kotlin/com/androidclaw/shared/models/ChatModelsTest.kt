package com.androidclaw.shared.models

import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatModelsTest {

    // ---- MessageRole ----

    @Test
    fun messageRole_toDbString_lowercase() {
        assertEquals("user", MessageRole.USER.toDbString())
        assertEquals("assistant", MessageRole.ASSISTANT.toDbString())
        assertEquals("system", MessageRole.SYSTEM.toDbString())
        assertEquals("tool", MessageRole.TOOL.toDbString())
    }

    @Test
    fun messageRole_fromDbString_caseInsensitive() {
        assertEquals(MessageRole.USER, MessageRole.fromDbString("user"))
        assertEquals(MessageRole.USER, MessageRole.fromDbString("USER"))
        assertEquals(MessageRole.USER, MessageRole.fromDbString("User"))
        assertEquals(MessageRole.ASSISTANT, MessageRole.fromDbString("assistant"))
        assertEquals(MessageRole.SYSTEM, MessageRole.fromDbString("system"))
        assertEquals(MessageRole.TOOL, MessageRole.fromDbString("tool"))
    }

    @Test
    fun messageRole_roundTrip() {
        for (role in MessageRole.entries) {
            assertEquals(role, MessageRole.fromDbString(role.toDbString()))
        }
    }

    @Test
    fun messageRole_fromDbString_invalidThrows() {
        assertFailsWith<NoSuchElementException> {
            MessageRole.fromDbString("invalid")
        }
    }

    // ---- ConversationUiModel ----

    @Test
    fun conversationUiModel_defaults() {
        val model = ConversationUiModel(
            id = "1",
            title = "Test",
            lastMessage = null,
            updatedAt = Clock.System.now()
        )
        assertFalse(model.isPinned)
        assertNull(model.category)
        assertNull(model.lastMessage)
    }

    @Test
    fun conversationUiModel_withAllFields() {
        val now = Clock.System.now()
        val model = ConversationUiModel(
            id = "abc",
            title = "Test Convo",
            lastMessage = "Hello",
            updatedAt = now,
            isPinned = true,
            category = "work"
        )
        assertEquals("abc", model.id)
        assertEquals("Test Convo", model.title)
        assertEquals("Hello", model.lastMessage)
        assertEquals(now, model.updatedAt)
        assertTrue(model.isPinned)
        assertEquals("work", model.category)
    }

    // ---- MessageUiModel ----

    @Test
    fun messageUiModel_defaults() {
        val now = Clock.System.now()
        val model = MessageUiModel(
            id = "1",
            role = MessageRole.USER,
            content = "Hello",
            createdAt = now
        )
        assertFalse(model.isStreaming)
        assertNull(model.toolName)
    }

    @Test
    fun messageUiModel_withToolName() {
        val now = Clock.System.now()
        val model = MessageUiModel(
            id = "1",
            role = MessageRole.ASSISTANT,
            content = "result",
            isStreaming = true,
            toolName = "calculator",
            createdAt = now
        )
        assertTrue(model.isStreaming)
        assertEquals("calculator", model.toolName)
    }

    // ---- ApiConfig ----

    @Test
    fun apiConfig_withDefaults() {
        val config = ApiConfig(baseUrl = "http://localhost:8080")
        assertEquals("http://localhost:8080", config.baseUrl)
        assertNull(config.authToken)
    }

    @Test
    fun apiConfig_withAuthToken() {
        val config = ApiConfig(baseUrl = "http://localhost:8080", authToken = "token123")
        assertEquals("token123", config.authToken)
    }
}
