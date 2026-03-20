package com.androidclaw.shared.agent

import com.androidclaw.shared.models.MessageRole
import com.androidclaw.shared.models.MessageUiModel
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContextManagerTest {

    private val contextManager = ContextManager(maxTokens = 1000, reservedForResponse = 100)
    private val now = Clock.System.now()

    private fun msg(content: String, role: MessageRole = MessageRole.USER) = MessageUiModel(
        id = content.hashCode().toString(),
        role = role,
        content = content,
        createdAt = now
    )

    @Test
    fun estimateTokens_basicEstimation() {
        assertEquals(1, contextManager.estimateTokens("Hi"))
        assertEquals(25, contextManager.estimateTokens("a".repeat(100)))
    }

    @Test
    fun trimToFit_emptyListReturnsEmpty() {
        val result = contextManager.trimToFit(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun trimToFit_smallConversationUnchanged() {
        val messages = listOf(
            msg("Hello"),
            msg("Hi there!", MessageRole.ASSISTANT),
            msg("How are you?")
        )
        val result = contextManager.trimToFit(messages)
        assertEquals(3, result.size)
    }

    @Test
    fun trimToFit_largeConversationTrimmed() {
        val longContent = "x".repeat(2000) // ~500 tokens each
        val messages = listOf(
            msg("first message"),
            msg(longContent, MessageRole.ASSISTANT),
            msg(longContent),
            msg("last message", MessageRole.ASSISTANT)
        )
        val result = contextManager.trimToFit(messages)
        // Should keep first and last messages, trim middle
        assertTrue(result.size < messages.size)
        assertEquals("first message", result.first().content)
        assertEquals("last message", result.last().content)
    }

    @Test
    fun getUsage_returnsValidFraction() {
        val messages = listOf(msg("Hello"))
        val usage = contextManager.getUsage(messages)
        assertTrue(usage in 0f..1f)
    }

    @Test
    fun getUsageSummary_formatsCorrectly() {
        val messages = listOf(msg("Hello"))
        val summary = contextManager.getUsageSummary(messages)
        assertTrue(summary.contains("tokens"))
    }
}
