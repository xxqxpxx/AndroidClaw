package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

class LocationTool(
    private val bridge: DeviceActionBridge
) : Tool {

    override val name = "location"

    override val description = """Get the device's current GPS location.
        |Use this when the user asks where they are, wants their coordinates, or needs location-based information.""".trimMargin()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                putJsonArray("enum") { add("get_current") }
                put("description", "Action: get current location")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val result = bridge.getCurrentLocation()
        return result.fold(
            onSuccess = { ToolResult(it) },
            onFailure = { ToolResult("Failed: ${it.message}", isError = true) }
        )
    }
}
