package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

/**
 * Tool for scheduling automated tasks that run at specified times.
 * Supports one-time and repeating schedules with graceful degradation
 * (exact alarm → inexact → WorkManager → skip with notification).
 */
class SchedulerTool(
    private val bridge: DeviceActionBridge
) : Tool {

    override val name = "scheduler"

    override val description = """Schedule tasks to run automatically at specified times.
        |Actions:
        | - schedule: Create a new scheduled task (one-time or repeating)
        | - list: List all scheduled tasks and their status
        | - cancel: Cancel a scheduled task by ID
        | - history: View recently completed/failed tasks
        | - run_now: Execute a task's steps immediately
        |
        |Steps format: each step is {"action": "...", "params": {"key": "value"}}
        |Available step actions: launch_app, set_volume, set_brightness, set_dnd, set_wifi,
        |play_music, set_alarm, send_sms, open_url, navigate, apply_mode, read_aloud, vision_tap
        |
        |Example: Schedule a morning routine at 7:00 AM daily that sets brightness, plays music, reads calendar.
        |The scheduler automatically degrades: exact alarm (best) → inexact alarm → WorkManager (battery-friendly).""".trimMargin()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                putJsonArray("enum") { add("schedule"); add("list"); add("cancel"); add("history"); add("run_now") }
            }
            putJsonObject("name") {
                put("type", "string")
                put("description", "Task name (for schedule)")
            }
            putJsonObject("description") {
                put("type", "string")
                put("description", "Task description")
            }
            putJsonObject("trigger_time_ms") {
                put("type", "integer")
                put("description", "Unix timestamp in milliseconds when to trigger (for schedule)")
            }
            putJsonObject("repeat_interval_minutes") {
                put("type", "integer")
                put("description", "Repeat interval in minutes (0 or omit for one-time)")
            }
            putJsonObject("steps") {
                put("type", "array")
                put("description", "Array of step objects: [{\"action\":\"...\",\"params\":{...}}]")
            }
            putJsonObject("task_id") {
                put("type", "string")
                put("description", "Task ID (for cancel/run_now)")
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "Max results for history (default 10)")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val action = input["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing action", isError = true)

        val result = when (action) {
            "schedule" -> {
                val name = input["name"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing 'name' for the task", isError = true)
                val triggerTimeMs = input["trigger_time_ms"]?.jsonPrimitive?.longOrNull
                    ?: return ToolResult("Missing 'trigger_time_ms'", isError = true)
                val repeatMinutes = input["repeat_interval_minutes"]?.jsonPrimitive?.intOrNull ?: 0
                val steps = input["steps"]?.jsonArray?.toString() ?: "[]"
                val description = input["description"]?.jsonPrimitive?.contentOrNull ?: ""
                bridge.scheduleTask(name, description, triggerTimeMs, repeatMinutes.toLong() * 60000L, steps)
            }
            "list" -> bridge.listScheduledTasks()
            "cancel" -> {
                val taskId = input["task_id"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing 'task_id'", isError = true)
                bridge.cancelScheduledTask(taskId)
            }
            "history" -> {
                val limit = input["limit"]?.jsonPrimitive?.intOrNull ?: 10
                bridge.getTaskHistory(limit)
            }
            "run_now" -> {
                val taskId = input["task_id"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing 'task_id'", isError = true)
                bridge.runTaskNow(taskId)
            }
            else -> return ToolResult("Unknown action: $action", isError = true)
        }

        return result.fold(
            onSuccess = { ToolResult(it) },
            onFailure = { ToolResult("Scheduler error: ${it.message}", isError = true) }
        )
    }
}
