package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

class PhoneTool(
    private val bridge: DeviceActionBridge
) : Tool {

    override val name = "phone"

    override val description = """Make phone calls and view call history.
        |Use this to call someone by phone number or name, or check recent call history.""".trimMargin()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                putJsonArray("enum") { add("call"); add("call_log") }
                put("description", "Action: make a phone call, or view recent call log")
            }
            putJsonObject("phone_number") {
                put("type", "string")
                put("description", "Phone number to call")
            }
            putJsonObject("count") {
                put("type", "integer")
                put("description", "Number of call log entries to return (default 10)")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val action = input["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing required parameter: action", isError = true)

        val result = when (action) {
            "call" -> {
                val phone = input["phone_number"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing phone_number for call", isError = true)
                bridge.makeCall(phone)
            }
            "call_log" -> {
                val count = input["count"]?.jsonPrimitive?.intOrNull ?: 10
                bridge.getCallLog(count)
            }
            else -> return ToolResult("Unknown action: $action", isError = true)
        }

        return result.fold(
            onSuccess = { ToolResult(it) },
            onFailure = { ToolResult("Failed: ${it.message}", isError = true) }
        )
    }
}
