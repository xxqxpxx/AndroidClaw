package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

/**
 * Manage and run reusable automation skills (multi-step routines).
 * Skills are like macros: "Morning Routine", "Focus Mode", "Commute Setup", etc.
 * Users invoke them via slash commands (/morning) or by name.
 */
class SkillsTool(
    private val bridge: DeviceActionBridge
) : Tool {

    override val name = "skills"

    override val description = """Manage reusable automation skills (multi-step routines).
        |Actions:
        | - list: Show all available skills
        | - run: Execute a skill by name or trigger (e.g., "Morning Routine" or "/morning")
        | - create: Create a new skill with steps
        | - delete: Delete a skill by ID
        | - export: Export a skill as shareable JSON
        | - import: Import a skill from JSON
        |
        |Skills are like saved macros: each has a name, optional slash trigger,
        |optional schedule, and a list of steps to execute in order.
        |
        |Step actions: launch_app, set_volume, set_brightness, set_dnd, set_wifi,
        |play_music, set_alarm, send_sms, open_url, navigate, apply_mode, read_aloud, vision_tap""".trimMargin()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                putJsonArray("enum") { add("list"); add("run"); add("create"); add("delete"); add("export"); add("import") }
            }
            putJsonObject("name") {
                put("type", "string")
                put("description", "Skill name or slash trigger to run/find")
            }
            putJsonObject("id") {
                put("type", "string")
                put("description", "Skill ID (for delete/export)")
            }
            putJsonObject("description") {
                put("type", "string")
                put("description", "Skill description (for create)")
            }
            putJsonObject("trigger") {
                put("type", "string")
                put("description", "Slash trigger (for create), e.g. '/morning'")
            }
            putJsonObject("schedule") {
                put("type", "string")
                put("description", "Schedule string (for create), e.g. 'daily 07:00'")
            }
            putJsonObject("steps") {
                put("type", "array")
                put("description", "Array of step objects: [{\"action\":\"...\",\"params\":{...},\"description\":\"...\"}]")
            }
            putJsonObject("json") {
                put("type", "string")
                put("description", "JSON string for import")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val action = input["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing action", isError = true)

        val result = when (action) {
            "list" -> bridge.skillsList()
            "run" -> {
                val name = input["name"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing 'name' — which skill to run?", isError = true)
                bridge.skillsRun(name)
            }
            "create" -> {
                val name = input["name"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing 'name' for the skill", isError = true)
                val description = input["description"]?.jsonPrimitive?.contentOrNull ?: ""
                val trigger = input["trigger"]?.jsonPrimitive?.contentOrNull ?: ""
                val schedule = input["schedule"]?.jsonPrimitive?.contentOrNull ?: ""
                val steps = input["steps"]?.jsonArray?.toString() ?: "[]"
                bridge.skillsCreate(name, description, trigger, schedule, steps)
            }
            "delete" -> {
                val id = input["id"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing 'id'", isError = true)
                bridge.skillsDelete(id)
            }
            "export" -> {
                val id = input["id"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing 'id'", isError = true)
                bridge.skillsExport(id)
            }
            "import" -> {
                val jsonStr = input["json"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing 'json' string", isError = true)
                bridge.skillsImport(jsonStr)
            }
            else -> return ToolResult("Unknown action: $action", isError = true)
        }

        return result.fold(
            onSuccess = { ToolResult(it) },
            onFailure = { ToolResult("Skills error: ${it.message}", isError = true) }
        )
    }
}
