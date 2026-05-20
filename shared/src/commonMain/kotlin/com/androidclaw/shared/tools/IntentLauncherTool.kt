package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

class IntentLauncherTool(
    private val bridge: DeviceActionBridge
) : Tool {

    override val name = "intent_launcher"

    override val description = """Open any app via deep-link or package name, order rides (Uber/Lyft/Careem/Bolt),
        |share content to specific apps, or open URLs in specific apps.
        |Use this for controlling 3rd-party apps that don't have dedicated tools.
        |
        |Common deep-links:
        |  - Uber ride: uber://?action=setPickup&dropoff[formatted_address]=DESTINATION
        |  - YouTube search: vnd.youtube://results?search_query=QUERY
        |  - Spotify search: spotify:search:QUERY
        |  - Instagram profile: instagram://user?username=NAME
        |  - Twitter/X profile: twitter://user?screen_name=NAME
        |  - Google Maps: geo:0,0?q=PLACE or google.navigation:q=PLACE
        |  - WhatsApp chat: whatsapp://send?phone=NUMBER
        |  - Telegram: tg://resolve?domain=USERNAME""".trimMargin()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                putJsonArray("enum") {
                    add("open_app"); add("deep_link"); add("open_url"); add("share_to_app")
                    add("share_media"); add("order_ride"); add("read_emails")
                }
                put("description", "Action type")
            }
            putJsonObject("package_name") {
                put("type", "string")
                put("description", "App package name for open_app/share_to_app (e.g. com.whatsapp, com.uber)")
            }
            putJsonObject("uri") {
                put("type", "string")
                put("description", "URI/deep-link for deep_link action")
            }
            putJsonObject("url") {
                put("type", "string")
                put("description", "URL for open_url action")
            }
            putJsonObject("text") {
                put("type", "string")
                put("description", "Text content for share_to_app")
            }
            putJsonObject("fallback_url") {
                put("type", "string")
                put("description", "Web fallback URL if app deep-link fails")
            }
            putJsonObject("destination") {
                put("type", "string")
                put("description", "Destination address for order_ride")
            }
            putJsonObject("service") {
                put("type", "string")
                putJsonArray("enum") { add("uber"); add("lyft"); add("careem"); add("bolt") }
                put("description", "Ride service for order_ride (default: uber)")
            }
            putJsonObject("count") {
                put("type", "integer")
                put("description", "Number of email notifications to retrieve (default 10)")
            }
            putJsonObject("file_path") {
                put("type", "string")
                put("description", "Absolute file path for share_media action")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val action = input["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing required parameter: action", isError = true)

        val result = when (action) {
            "open_app" -> {
                val pkg = input["package_name"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing package_name", isError = true)
                bridge.launchApp(pkg)
            }
            "deep_link" -> {
                val uri = input["uri"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing uri for deep_link", isError = true)
                val pkg = input["package_name"]?.jsonPrimitive?.contentOrNull
                val fallback = input["fallback_url"]?.jsonPrimitive?.contentOrNull
                bridge.openDeepLink(uri, pkg, fallback)
            }
            "open_url" -> {
                val url = input["url"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing url", isError = true)
                val pkg = input["package_name"]?.jsonPrimitive?.contentOrNull
                bridge.openUrl(url, pkg)
            }
            "share_to_app" -> {
                val text = input["text"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing text to share", isError = true)
                val pkg = input["package_name"]?.jsonPrimitive?.contentOrNull
                bridge.shareText(text, pkg)
            }
            "share_media" -> {
                val filePath = input["file_path"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing file_path for share_media", isError = true)
                val pkg = input["package_name"]?.jsonPrimitive?.contentOrNull
                bridge.shareMedia(filePath, pkg)
            }
            "order_ride" -> {
                val dest = input["destination"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing destination for ride order", isError = true)
                val service = input["service"]?.jsonPrimitive?.contentOrNull ?: "uber"
                bridge.orderRide(dest, service)
            }
            "read_emails" -> {
                val count = input["count"]?.jsonPrimitive?.intOrNull ?: 10
                bridge.getEmailNotifications(count)
            }
            else -> return ToolResult("Unknown action: $action", isError = true)
        }

        return result.fold(
            onSuccess = { ToolResult(it) },
            onFailure = { ToolResult("Failed: ${it.message}", isError = true) }
        )
    }
}
