package com.androidclaw.shared.agent

import com.androidclaw.shared.llm.ClaudeModels

data class AgentConfig(
    val model: String = ClaudeModels.DEFAULT_MODEL,
    val maxTokens: Int = ClaudeModels.DEFAULT_MAX_TOKENS,
    val systemPrompt: String = ClaudeModels.DEFAULT_SYSTEM_PROMPT,
    val maxToolIterations: Int = 50,
    /**
     * When true and using the direct Anthropic API path, the system prompt and
     * tool definitions are sent with `cache_control: ephemeral` so Anthropic
     * caches them across calls in a conversation. Cuts ~70-90% of input-token
     * cost on multi-turn / multi-tool-iteration runs.
     */
    val enablePromptCaching: Boolean = true,
    /**
     * Per-tool execution timeout in milliseconds. A misbehaving or hung tool
     * (e.g. an HTTP call with no response) would otherwise stall the entire
     * agent loop. When exceeded, the tool call returns an error result and the
     * loop continues so the model can recover.
     */
    val toolTimeoutMs: Long = 60_000L
)
