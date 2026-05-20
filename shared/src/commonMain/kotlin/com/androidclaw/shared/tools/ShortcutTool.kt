package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

class ShortcutTool(
    private val bridge: DeviceActionBridge
) : Tool {

    override val name = "shortcuts"

    override val description = """Create home screen shortcuts and pin app shortcuts. Create a custom shortcut
        |with a name and deep link URI, or pin an app's shortcut to the home screen.""".trimMargin()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("description", "Shortcut action to perform")
                putJsonArray("enum") {
                    add("create_shortcut")
                    add("pin_app")
                }
            }
            putJsonObject("name") {
                put("type", "string")
                put("description", "Display name for the shortcut")
            }
            putJsonObject("uri") {
                put("type", "string")
                put("description", "URI/deep link for the shortcut (e.g. 'https://example.com', 'tel:555-1234')")
            }
            putJsonObject("package_name") {
                put("type", "string")
                put("description", "App package name for pin_app action")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val action = input["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing required parameter: action", isError = true)

        val result = when (action) {
            "create_shortcut" -> {
                val name = input["name"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing shortcut name", isError = true)
                val uri = input["uri"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing shortcut URI", isError = true)
                bridge.createHomeShortcut(name, uri)
            }
            "pin_app" -> {
                val packageName = input["package_name"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing package name", isError = true)
                bridge.pinAppShortcut(packageName)
            }
            else -> return ToolResult("Unknown shortcut action: $action", isError = true)
        }

        return result.fold(
            onSuccess = { ToolResult(it) },
            onFailure = { ToolResult("Failed: ${it.message}", isError = true) }
        )
    }
}
