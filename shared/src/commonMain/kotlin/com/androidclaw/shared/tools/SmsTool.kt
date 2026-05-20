package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

class SmsTool(
    private val bridge: DeviceActionBridge
) : Tool {

    override val name = "sms"

    override val description = """Send and read SMS text messages.
        |Use this to send a text message to someone, read recent messages, or check messages from a specific contact.
        |For messaging apps like WhatsApp, use the messaging tool instead.""".trimMargin()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                putJsonArray("enum") { add("send"); add("read_recent"); add("read_from_contact"); add("search"); add("delete_conversation") }
                put("description", "Action: send SMS, read recent, read from contact, search messages, or delete conversation")
            }
            putJsonObject("phone_number") {
                put("type", "string")
                put("description", "Phone number to send SMS to")
            }
            putJsonObject("message") {
                put("type", "string")
                put("description", "Text message content to send")
            }
            putJsonObject("contact_name") {
                put("type", "string")
                put("description", "Contact name to read messages from")
            }
            putJsonObject("count") {
                put("type", "integer")
                put("description", "Number of messages to retrieve (default 10)")
            }
            putJsonObject("query") {
                put("type", "string")
                put("description", "Search query to find in messages")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val action = input["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing required parameter: action", isError = true)

        val result = when (action) {
            "send" -> {
                val phone = input["phone_number"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing phone_number for send", isError = true)
                val message = input["message"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing message for send", isError = true)
                bridge.sendSms(phone, message)
            }
            "read_recent" -> {
                val count = input["count"]?.jsonPrimitive?.intOrNull ?: 10
                bridge.getRecentSms(count)
            }
            "read_from_contact" -> {
                val name = input["contact_name"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing contact_name", isError = true)
                val count = input["count"]?.jsonPrimitive?.intOrNull ?: 10
                bridge.getSmsFromContact(name, count)
            }
            "search" -> {
                val query = input["query"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing query for search", isError = true)
                val count = input["count"]?.jsonPrimitive?.intOrNull ?: 20
                bridge.searchSms(query, count)
            }
            "delete_conversation" -> {
                val name = input["contact_name"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing contact_name for delete", isError = true)
                bridge.deleteSmsConversation(name)
            }
            else -> return ToolResult("Unknown action: $action", isError = true)
        }

        return result.fold(
            onSuccess = { ToolResult(it) },
            onFailure = { ToolResult("Failed: ${it.message}", isError = true) }
        )
    }
}
