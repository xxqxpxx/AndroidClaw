package com.androidclaw.shared.agent

import com.androidclaw.shared.models.MessageUiModel

/**
 * Manages conversation context window to stay within token limits.
 * Uses a simple character-based token estimation (avg ~4 chars per token).
 * Implements sliding window: keeps system prompt + recent messages.
 */
class ContextManager(
    private val maxTokens: Int = 100_000,
    private val reservedForResponse: Int = 4096
) {
    private val availableTokens get() = maxTokens - reservedForResponse

    fun estimateTokens(text: String): Int {
        // Rough estimation: ~4 characters per token for English text
        return (text.length / 4).coerceAtLeast(1)
    }

    fun estimateMessageTokens(messages: List<MessageUiModel>): Int {
        return messages.sumOf { estimateTokens(it.content) + 4 } // +4 per message overhead
    }

    /**
     * Trims messages to fit within context window.
     * Always keeps the first message (for context) and trims from the middle.
     */
    fun trimToFit(messages: List<MessageUiModel>, systemPromptTokens: Int = 500): List<MessageUiModel> {
        if (messages.isEmpty()) return messages

        val budget = availableTokens - systemPromptTokens
        var totalTokens = estimateMessageTokens(messages)

        if (totalTokens <= budget) return messages

        // Keep first message and last N messages that fit
        val result = mutableListOf<MessageUiModel>()
        val first = messages.first()
        val firstTokens = estimateTokens(first.content) + 4

        var remaining = budget - firstTokens
        val kept = mutableListOf<MessageUiModel>()

        // Walk backwards, keeping messages that fit
        for (i in messages.lastIndex downTo 1) {
            val msgTokens = estimateTokens(messages[i].content) + 4
            if (remaining >= msgTokens) {
                kept.add(0, messages[i])
                remaining -= msgTokens
            } else {
                break // Stop when we can't fit more
            }
        }

        result.add(first)
        result.addAll(kept)
        return result
    }

    /**
     * Returns context usage as a fraction (0.0 to 1.0).
     */
    fun getUsage(messages: List<MessageUiModel>, systemPromptTokens: Int = 500): Float {
        val used = estimateMessageTokens(messages) + systemPromptTokens
        return (used.toFloat() / maxTokens).coerceIn(0f, 1f)
    }

    fun getUsageSummary(messages: List<MessageUiModel>, systemPromptTokens: Int = 500): String {
        val used = estimateMessageTokens(messages) + systemPromptTokens
        return "${formatTokenCount(used)} / ${formatTokenCount(maxTokens)} tokens"
    }

    private fun formatTokenCount(count: Int): String = when {
        count >= 1_000_000 -> "${count / 1_000_000}.${(count % 1_000_000) / 100_000}M"
        count >= 1_000 -> "${count / 1_000}.${(count % 1_000) / 100}K"
        else -> count.toString()
    }
}
