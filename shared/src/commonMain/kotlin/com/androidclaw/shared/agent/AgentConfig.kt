package com.androidclaw.shared.agent

import com.androidclaw.shared.llm.ClaudeModels

data class AgentConfig(
    val model: String = ClaudeModels.DEFAULT_MODEL,
    val maxTokens: Int = ClaudeModels.DEFAULT_MAX_TOKENS,
    val systemPrompt: String = ClaudeModels.DEFAULT_SYSTEM_PROMPT,
    val maxToolIterations: Int = 50
)
