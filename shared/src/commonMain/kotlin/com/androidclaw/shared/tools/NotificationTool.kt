package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

class NotificationTool(
    private val bridge: DeviceActionBridge
) : Tool {

    override val name = "notifications"

    override val description = "Read recent notifications or dismiss them. Use when the user asks about their notifications or wants to clear them."

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                putJsonArray("enum") { add("list"); add("dismiss") }
                put("description", "List recent notifications or dismiss one")
            }
            putJsonObject("count") {
                put("type", "integer")
                put("description", "Number of notifications to retrieve (default 10)")
            }
            putJsonObject("key") {
                put("type", "string")
                put("description", "Notification key to dismiss")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val action = input["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing required parameter: action", isError = true)

        val result = when (action) {
            "list" -> {
                val count = input["count"]?.jsonPrimitive?.intOrNull ?: 10
                bridge.getRecentNotifications(count)
            }
            "dismiss" -> {
                val key = input["key"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing notification key to dismiss", isError = true)
                bridge.dismissNotification(key)
            }
            else -> return ToolResult("Unknown action: $action", isError = true)
        }

        return result.fold(
            onSuccess = { ToolResult(it) },
            onFailure = { ToolResult("Failed: ${it.message}", isError = true) }
        )
    }
}
