package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import io.ktor.client.*

class ToolRegistry(
    private val httpClient: HttpClient,
    private val tavilyApiKey: String = ""
) {
    fun getTools(): List<Tool> {
        val tools = mutableListOf<Tool>()

        if (tavilyApiKey.isNotEmpty()) {
            tools.add(WebSearchTool(httpClient, tavilyApiKey))
        }

        // Phase 2: Add device control tools here
        // Phase 3: Add browse tools here
        // Phase 4: Add code execution tools here

        return tools
    }
}
