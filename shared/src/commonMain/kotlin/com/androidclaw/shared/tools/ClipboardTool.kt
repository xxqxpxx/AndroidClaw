package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

class ClipboardTool(
    private val bridge: DeviceActionBridge
) : Tool {

    override val name = "clipboard"

    override val description = "Read from or write to the device clipboard."

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                putJsonArray("enum") { add("get"); add("set") }
                put("description", "Whether to get or set clipboard content")
            }
            putJsonObject("text") {
                put("type", "string")
                put("description", "Text to copy to clipboard (required for set)")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val action = input["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing required parameter: action", isError = true)

        val result = when (action) {
            "get" -> bridge.getClipboard()
            "set" -> {
                val text = input["text"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing text for clipboard set", isError = true)
                bridge.setClipboard(text)
            }
            else -> return ToolResult("Unknown action: $action", isError = true)
        }

        return result.fold(
            onSuccess = { ToolResult(it) },
            onFailure = { ToolResult("Failed: ${it.message}", isError = true) }
        )
    }
}
