package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

class NotificationTool(
    private val bridge: DeviceActionBridge
) : Tool {

    override val name = "notifications"

    override val description = "Read recent notifications, email notifications, dismiss them, or reply to them. Use when the user asks about their notifications, emails, wants to clear them, or reply to a message."

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                putJsonArray("enum") { add("list"); add("list_emails"); add("dismiss"); add("reply") }
                put("description", "List recent notifications, list email notifications only, dismiss one, or reply to one")
            }
            putJsonObject("count") {
                put("type", "integer")
                put("description", "Number of notifications to retrieve (default 10)")
            }
            putJsonObject("key") {
                put("type", "string")
                put("description", "Notification key to dismiss or reply to")
            }
            putJsonObject("reply_text") {
                put("type", "string")
                put("description", "Text to reply with")
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
            "list_emails" -> {
                val count = input["count"]?.jsonPrimitive?.intOrNull ?: 10
                bridge.getEmailNotifications(count)
            }
            "dismiss" -> {
                val key = input["key"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing notification key to dismiss", isError = true)
                bridge.dismissNotification(key)
            }
            "reply" -> {
                val key = input["key"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing notification key to reply to", isError = true)
                val text = input["reply_text"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing reply_text", isError = true)
                bridge.replyToNotification(key, text)
            }
            else -> return ToolResult("Unknown action: $action", isError = true)
        }

        return result.fold(
            onSuccess = { ToolResult(it) },
            onFailure = { ToolResult("Failed: ${it.message}", isError = true) }
        )
    }
}
