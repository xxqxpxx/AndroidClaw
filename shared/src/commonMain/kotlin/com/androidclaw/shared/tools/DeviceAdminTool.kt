package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

class DeviceAdminTool(
    private val bridge: DeviceActionBridge
) : Tool {

    override val name = "device_admin"

    override val description = """Control device admin features like locking the screen, disabling the camera, and setting screen lock timeout.
        |Requires device admin permission to be enabled first.""".trimMargin()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                putJsonArray("enum") {
                    add("lock_screen")
                    add("disable_camera"); add("enable_camera")
                    add("set_lock_timeout")
                    add("status")
                }
                put("description", "Action: lock screen, enable/disable camera, set lock timeout, or check admin status")
            }
            putJsonObject("timeout_seconds") {
                put("type", "integer")
                put("description", "Screen lock timeout in seconds (for set_lock_timeout action)")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val action = input["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing required parameter: action", isError = true)

        val result = when (action) {
            "lock_screen" -> bridge.lockScreen()
            "disable_camera" -> bridge.setCameraDisabled(true)
            "enable_camera" -> bridge.setCameraDisabled(false)
            "set_lock_timeout" -> {
                val seconds = input["timeout_seconds"]?.jsonPrimitive?.intOrNull
                    ?: return ToolResult("Missing timeout_seconds", isError = true)
                bridge.setMaxScreenLockTimeout(seconds * 1000L)
            }
            "status" -> bridge.getDeviceAdminStatus()
            else -> return ToolResult("Unknown action: $action", isError = true)
        }

        return result.fold(
            onSuccess = { ToolResult(it) },
            onFailure = { ToolResult("Failed: ${it.message}", isError = true) }
        )
    }
}
