package com.androidclaw.shared.agent

import com.androidclaw.shared.llm.ClaudeToolDefinition
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToolExtensionsTest {

    @Test
    fun toClaudeToolDefinition_mapsCorrectly() {
        val tool = object : Tool {
            override val name = "test_tool"
            override val description = "A test tool"
            override val inputSchema = buildJsonObject {
                put("type", "object")
            }
            override suspend fun execute(input: kotlinx.serialization.json.JsonObject): ToolResult {
                return ToolResult("ok")
            }
        }

        val def = tool.toClaudeToolDefinition()
        assertEquals("test_tool", def.name)
        assertEquals("A test tool", def.description)
        assertEquals(tool.inputSchema, def.inputSchema)
    }

    @Test
    fun toolResult_defaultNotError() {
        val result = ToolResult("content")
        assertEquals("content", result.content)
        assertEquals(false, result.isError)
    }

    @Test
    fun toolResult_withError() {
        val result = ToolResult("error message", isError = true)
        assertTrue(result.isError)
        assertEquals("error message", result.content)
    }
}
