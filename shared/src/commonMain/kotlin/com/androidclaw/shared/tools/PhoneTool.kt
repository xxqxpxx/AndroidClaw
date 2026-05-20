package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

class PhoneTool(
    private val bridge: DeviceActionBridge
) : Tool {

    override val name = "phone"

    override val description = """Make phone calls, view call history, block numbers, and check voicemail.
        |Use this to call someone, check recent calls, block a phone number, or access voicemail.""".trimMargin()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                putJsonArray("enum") { add("call"); add("call_log"); add("block_number"); add("voicemail") }
                put("description", "Action: make a call, view call log, block a number, or check voicemail")
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
            "block_number" -> {
                val phone = input["phone_number"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing phone_number for block", isError = true)
                bridge.blockNumber(phone)
            }
            "voicemail" -> bridge.checkVoicemail()
            else -> return ToolResult("Unknown action: $action", isError = true)
        }

        return result.fold(
            onSuccess = { ToolResult(it) },
            onFailure = { ToolResult("Failed: ${it.message}", isError = true) }
        )
    }
}
