package com.androidclaw.shared.agent

import com.androidclaw.shared.llm.ClaudeToolDefinition
import kotlinx.serialization.json.JsonObject

interface Tool {
    val name: String
    val description: String
    val inputSchema: JsonObject
    suspend fun execute(input: JsonObject): ToolResult
}

data class ToolResult(
    val content: String,
    val isError: Boolean = false
)

fun Tool.toClaudeToolDefinition(): ClaudeToolDefinition {
    return ClaudeToolDefinition(
        name = name,
        description = description,
        inputSchema = inputSchema
    )
}
