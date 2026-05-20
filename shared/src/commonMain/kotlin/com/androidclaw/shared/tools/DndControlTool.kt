package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

class DndControlTool(
    private val bridge: DeviceActionBridge
) : Tool {

    override val name = "dnd_control"

    override val description = """Control Do Not Disturb mode with granular options: set DND mode (priority only, alarms only,
        |total silence), check current DND status, or turn DND off. More precise than the simple on/off toggle.""".trimMargin()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("description", "DND action to perform")
                putJsonArray("enum") {
                    add("priority_only")
                    add("alarms_only")
                    add("total_silence")
                    add("off")
                    add("status")
                }
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val action = input["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing required parameter: action", isError = true)

        val result = when (action) {
            "priority_only" -> bridge.setDndMode("priority")
            "alarms_only" -> bridge.setDndMode("alarms")
            "total_silence" -> bridge.setDndMode("total_silence")
            "off" -> bridge.setDndMode("off")
            "status" -> bridge.getDndStatus()
            else -> return ToolResult("Unknown DND action: $action", isError = true)
        }

        return result.fold(
            onSuccess = { ToolResult(it) },
            onFailure = { ToolResult("Failed: ${it.message}", isError = true) }
        )
    }
}
