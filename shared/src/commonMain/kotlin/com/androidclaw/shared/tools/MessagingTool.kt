package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

class MessagingTool(
    private val bridge: DeviceActionBridge
) : Tool {

    override val name = "messaging"

    override val description = """Send messages through apps like WhatsApp, Telegram, etc. or share text to any app.
        |Use this when the user wants to message someone on WhatsApp or share content through an app.
        |For regular SMS, use the sms tool instead.""".trimMargin()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                putJsonArray("enum") { add("send_whatsapp"); add("send_telegram"); add("share_text") }
                put("description", "Action: send WhatsApp message, send Telegram message, or share text to any app")
            }
            putJsonObject("phone_number") {
                put("type", "string")
                put("description", "Phone number with country code (e.g. +1234567890) for WhatsApp/Telegram")
            }
            putJsonObject("message") {
                put("type", "string")
                put("description", "Message text to send")
            }
            putJsonObject("package_name") {
                put("type", "string")
                put("description", "Target app package name for share_text (optional, opens chooser if not specified)")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val action = input["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing required parameter: action", isError = true)

        val result = when (action) {
            "send_whatsapp" -> {
                val phone = input["phone_number"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing phone_number", isError = true)
                val message = input["message"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing message", isError = true)
                bridge.sendIntentMessage("com.whatsapp", phone, message)
            }
            "send_telegram" -> {
                val phone = input["phone_number"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing phone_number", isError = true)
                val message = input["message"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing message", isError = true)
                bridge.sendIntentMessage("org.telegram.messenger", phone, message)
            }
            "share_text" -> {
                val message = input["message"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing message", isError = true)
                val pkg = input["package_name"]?.jsonPrimitive?.contentOrNull
                bridge.shareText(message, pkg)
            }
            else -> return ToolResult("Unknown action: $action", isError = true)
        }

        return result.fold(
            onSuccess = { ToolResult(it) },
            onFailure = { ToolResult("Failed: ${it.message}", isError = true) }
        )
    }
}
