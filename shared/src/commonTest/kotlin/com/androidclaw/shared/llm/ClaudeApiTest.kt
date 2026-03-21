package com.androidclaw.shared.llm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClaudeApiTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ---- ClaudeMessage ----

    @Test
    fun claudeMessage_user_createsCorrectly() {
        val msg = ClaudeMessage.user("Hello")
        assertEquals("user", msg.role)
        assertEquals(1, msg.content.size)
        val block = msg.content[0] as ContentBlock.Text
        assertEquals("Hello", block.text)
    }

    @Test
    fun claudeMessage_assistant_createsCorrectly() {
        val msg = ClaudeMessage.assistant("Hi there!")
        assertEquals("assistant", msg.role)
        assertEquals(1, msg.content.size)
        val block = msg.content[0] as ContentBlock.Text
        assertEquals("Hi there!", block.text)
    }

    // ---- ContentBlock ----

    @Test
    fun contentBlock_text() {
        val block = ContentBlock.Text("test content")
        assertEquals("test content", block.text)
    }

    @Test
    fun contentBlock_toolUse() {
        val input = buildJsonObject { put("query", "test") }
        val block = ContentBlock.ToolUse("tool-1", "web_search", input)
        assertEquals("tool-1", block.id)
        assertEquals("web_search", block.name)
        assertEquals(input, block.input)
    }

    @Test
    fun contentBlock_toolResult() {
        val block = ContentBlock.ToolResult("tool-1", "result text", false)
        assertEquals("tool-1", block.toolUseId)
        assertEquals("result text", block.content)
        assertEquals(false, block.isError)
    }

    @Test
    fun contentBlock_toolResult_withError() {
        val block = ContentBlock.ToolResult("tool-1", "error occurred", true)
        assertTrue(block.isError)
    }

    // ---- ClaudeRequest ----

    @Test
    fun claudeRequest_serialization() {
        val request = ClaudeRequest(
            model = "claude-sonnet-4-20250514",
            maxTokens = 4096,
            messages = listOf(ClaudeMessage.user("Hello")),
            stream = true
        )
        val serialized = json.encodeToString(ClaudeRequest.serializer(), request)
        assertTrue(serialized.contains("claude-sonnet-4-20250514"))
        assertTrue(serialized.contains("max_tokens"))
        assertTrue(serialized.contains("4096"))
    }

    @Test
    fun claudeRequest_withSystemPrompt() {
        val request = ClaudeRequest(
            model = "claude-sonnet-4-20250514",
            maxTokens = 4096,
            messages = listOf(ClaudeMessage.user("Hello")),
            system = "You are helpful.",
            stream = true
        )
        assertEquals("You are helpful.", request.system)
    }

    @Test
    fun claudeRequest_withTools() {
        val toolDef = ClaudeToolDefinition(
            name = "calculator",
            description = "Evaluate math",
            inputSchema = buildJsonObject {
                put("type", "object")
            }
        )
        val request = ClaudeRequest(
            model = "claude-sonnet-4-20250514",
            maxTokens = 4096,
            messages = listOf(ClaudeMessage.user("Hello")),
            tools = listOf(toolDef),
            stream = true
        )
        assertEquals(1, request.tools?.size)
        assertEquals("calculator", request.tools!![0].name)
    }

    @Test
    fun claudeRequest_defaultsStreamTrue() {
        val request = ClaudeRequest(
            model = "test",
            maxTokens = 100,
            messages = emptyList()
        )
        assertTrue(request.stream)
    }

    @Test
    fun claudeRequest_defaultsNullOptionals() {
        val request = ClaudeRequest(
            model = "test",
            maxTokens = 100,
            messages = emptyList()
        )
        assertNull(request.system)
        assertNull(request.tools)
    }

    // ---- ClaudeStreamEvent ----

    @Test
    fun claudeStreamEvent_textDelta() {
        val event = ClaudeStreamEvent.TextDelta("hello")
        assertEquals("hello", event.text)
    }

    @Test
    fun claudeStreamEvent_toolUseStart() {
        val event = ClaudeStreamEvent.ToolUseStart(0, "tool-1", "calculator")
        assertEquals(0, event.index)
        assertEquals("tool-1", event.id)
        assertEquals("calculator", event.name)
    }

    @Test
    fun claudeStreamEvent_messageDelta() {
        val event = ClaudeStreamEvent.MessageDelta("end_turn")
        assertEquals("end_turn", event.stopReason)
    }

    @Test
    fun claudeStreamEvent_messageDelta_nullStopReason() {
        val event = ClaudeStreamEvent.MessageDelta(null)
        assertNull(event.stopReason)
    }

    @Test
    fun claudeStreamEvent_error() {
        val event = ClaudeStreamEvent.Error("something went wrong")
        assertEquals("something went wrong", event.message)
    }

    // ---- ClaudeToolDefinition ----

    @Test
    fun claudeToolDefinition_serialization() {
        val def = ClaudeToolDefinition(
            name = "web_search",
            description = "Search the web",
            inputSchema = buildJsonObject {
                put("type", "object")
            }
        )
        val serialized = json.encodeToString(ClaudeToolDefinition.serializer(), def)
        assertTrue(serialized.contains("web_search"))
        assertTrue(serialized.contains("input_schema"))
    }
}
