package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

class AlarmTimerTool(
    private val bridge: DeviceActionBridge
) : Tool {

    override val name = "alarm_timer"

    override val description = "Set alarms and timers, list existing alarms, cancel timers, and manage reminders. Use when the user asks to set, list, or cancel alarms, timers, or reminders."

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                putJsonArray("enum") { add("set_alarm"); add("set_timer"); add("list_alarms"); add("cancel_timer"); add("list_reminders"); add("delete_reminder") }
                put("description", "Whether to set/list/cancel alarms, timers, or reminders")
            }
            putJsonObject("hour") {
                put("type", "integer")
                put("description", "Hour for alarm (0-23)")
            }
            putJsonObject("minute") {
                put("type", "integer")
                put("description", "Minute for alarm (0-59)")
            }
            putJsonObject("seconds") {
                put("type", "integer")
                put("description", "Duration in seconds for timer")
            }
            putJsonObject("label") {
                put("type", "string")
                put("description", "Label/description for the alarm or timer")
            }
            putJsonObject("reminder_id") {
                put("type", "string")
                put("description", "Reminder ID to delete")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val action = input["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing required parameter: action", isError = true)
        val label = input["label"]?.jsonPrimitive?.contentOrNull ?: ""

        val result = when (action) {
            "set_alarm" -> {
                val hour = input["hour"]?.jsonPrimitive?.intOrNull
                    ?: return ToolResult("Missing hour for alarm", isError = true)
                val minute = input["minute"]?.jsonPrimitive?.intOrNull ?: 0
                bridge.setAlarm(hour.coerceIn(0, 23), minute.coerceIn(0, 59), label)
            }
            "set_timer" -> {
                val seconds = input["seconds"]?.jsonPrimitive?.intOrNull
                    ?: return ToolResult("Missing seconds for timer", isError = true)
                bridge.setTimer(seconds.coerceAtLeast(1), label)
            }
            "list_alarms" -> bridge.listAlarms()
            "cancel_timer" -> bridge.cancelTimer()
            "list_reminders" -> bridge.getReminders()
            "delete_reminder" -> {
                val id = input["reminder_id"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing reminder_id", isError = true)
                bridge.deleteReminder(id)
            }
            else -> return ToolResult("Unknown action: $action", isError = true)
        }

        return result.fold(
            onSuccess = { ToolResult(it) },
            onFailure = { ToolResult("Failed: ${it.message}", isError = true) }
        )
    }
}
