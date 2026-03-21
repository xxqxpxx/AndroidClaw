package com.androidclaw.shared.agent

import com.androidclaw.shared.llm.ClaudeModels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentConfigTest {

    @Test
    fun defaults_useSonnet4Model() {
        val config = AgentConfig()
        assertEquals(ClaudeModels.SONNET_4, config.model)
    }

    @Test
    fun defaults_maxTokens4096() {
        val config = AgentConfig()
        assertEquals(4096, config.maxTokens)
    }

    @Test
    fun defaults_maxToolIterations5() {
        val config = AgentConfig()
        assertEquals(5, config.maxToolIterations)
    }

    @Test
    fun defaults_systemPromptNotEmpty() {
        val config = AgentConfig()
        assertTrue(config.systemPrompt.isNotEmpty())
    }

    @Test
    fun customConfig_overridesDefaults() {
        val config = AgentConfig(
            model = ClaudeModels.HAIKU_45,
            maxTokens = 2048,
            systemPrompt = "Custom prompt",
            maxToolIterations = 10
        )
        assertEquals(ClaudeModels.HAIKU_45, config.model)
        assertEquals(2048, config.maxTokens)
        assertEquals("Custom prompt", config.systemPrompt)
        assertEquals(10, config.maxToolIterations)
    }

    @Test
    fun claudeModels_constants() {
        assertEquals("claude-sonnet-4-20250514", ClaudeModels.SONNET_4)
        assertEquals("claude-3-5-haiku-20241022", ClaudeModels.HAIKU_35)
        assertEquals("claude-haiku-4-5-20251001", ClaudeModels.HAIKU_45)
        assertEquals(ClaudeModels.SONNET_4, ClaudeModels.DEFAULT_MODEL)
        assertEquals(ClaudeModels.HAIKU_45, ClaudeModels.FAST_MODEL)
        assertEquals(4096, ClaudeModels.DEFAULT_MAX_TOKENS)
    }

    @Test
    fun defaultSystemPrompt_mentionsCapabilities() {
        val prompt = ClaudeModels.DEFAULT_SYSTEM_PROMPT
        assertTrue(prompt.contains("AndroidClaw"))
        assertTrue(prompt.contains("web_search"))
        assertTrue(prompt.contains("calculator"))
        assertTrue(prompt.contains("datetime"))
        assertTrue(prompt.contains("device_settings"))
        assertTrue(prompt.contains("run_code"))
    }
}
