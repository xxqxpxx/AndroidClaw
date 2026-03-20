package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

class AppLauncherTool(
    private val bridge: DeviceActionBridge
) : Tool {

    override val name = "app_launcher"

    override val description = """Launch apps, list installed apps, or search for apps on the device.
        |Use when the user wants to open an application.""".trimMargin()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                putJsonArray("enum") { add("launch"); add("list"); add("search") }
                put("description", "Action: launch an app, list all, or search by name")
            }
            putJsonObject("package_name") {
                put("type", "string")
                put("description", "Package name to launch (e.g. com.android.chrome)")
            }
            putJsonObject("query") {
                put("type", "string")
                put("description", "Search query for finding apps by name")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val action = input["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing required parameter: action", isError = true)

        val result = when (action) {
            "launch" -> {
                val pkg = input["package_name"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing package_name for launch", isError = true)
                bridge.launchApp(pkg)
            }
            "list" -> bridge.listInstalledApps()
            "search" -> {
                val query = input["query"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing query for search", isError = true)
                bridge.searchApps(query)
            }
            else -> return ToolResult("Unknown action: $action", isError = true)
        }

        return result.fold(
            onSuccess = { ToolResult(it) },
            onFailure = { ToolResult("Failed: ${it.message}", isError = true) }
        )
    }
}
