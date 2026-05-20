package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

class ScreenTimeTool(
    private val bridge: DeviceActionBridge
) : Tool {

    override val name = "screen_time"

    override val description = """View screen time, app usage statistics, and battery usage.
        |Use when the user asks how much they've used their phone, which apps they use most, or what's draining battery.""".trimMargin()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                putJsonArray("enum") { add("get_screen_time"); add("get_app_usage"); add("get_battery_usage") }
                put("description", "Action: get total screen time, per-app usage stats, or battery usage breakdown")
            }
            putJsonObject("days") {
                put("type", "integer")
                put("description", "Number of days to look back (default 1, max 7)")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val action = input["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing required parameter: action", isError = true)

        val days = input["days"]?.jsonPrimitive?.intOrNull ?: 1

        val result = when (action) {
            "get_screen_time" -> bridge.getScreenTime(days.coerceIn(1, 7))
            "get_app_usage" -> bridge.getAppUsageStats(days.coerceIn(1, 7))
            "get_battery_usage" -> bridge.getBatteryUsageStats()
            else -> return ToolResult("Unknown action: $action", isError = true)
        }

        return result.fold(
            onSuccess = { ToolResult(it) },
            onFailure = { ToolResult("Failed: ${it.message}", isError = true) }
        )
    }
}
