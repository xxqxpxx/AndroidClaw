package com.androidclaw.shared.llm

object ClaudeModels {
    const val SONNET_4 = "claude-sonnet-4-20250514"
    const val HAIKU_35 = "claude-3-5-haiku-20241022"

    const val DEFAULT_MODEL = SONNET_4
    const val DEFAULT_MAX_TOKENS = 4096

    val DEFAULT_SYSTEM_PROMPT = """
        You are AndroidClaw, a helpful AI assistant running on a mobile device.
        You can help users with questions, tasks, and information retrieval.
        When you need current information, use the web_search tool.
        Be concise and helpful. Respond naturally as a voice assistant would.
        Keep responses brief unless the user asks for detail.
    """.trimIndent()
}
