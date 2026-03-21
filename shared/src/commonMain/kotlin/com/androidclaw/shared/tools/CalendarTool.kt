package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

class CalendarTool(
    private val bridge: DeviceActionBridge
) : Tool {

    override val name = "calendar"

    override val description = """Read and create calendar events.
        |Use this to check upcoming events, schedule meetings, or add reminders to the calendar.""".trimMargin()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                putJsonArray("enum") { add("get_events"); add("create_event") }
                put("description", "Action: get upcoming events or create a new event")
            }
            putJsonObject("days_ahead") {
                put("type", "integer")
                put("description", "Number of days ahead to look for events (default 7)")
            }
            putJsonObject("title") {
                put("type", "string")
                put("description", "Title for a new event")
            }
            putJsonObject("start_time") {
                put("type", "string")
                put("description", "Start time in ISO format (e.g. 2025-01-15T14:00:00) or epoch millis")
            }
            putJsonObject("end_time") {
                put("type", "string")
                put("description", "End time in ISO format or epoch millis")
            }
            putJsonObject("description") {
                put("type", "string")
                put("description", "Description/notes for the event")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val action = input["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing required parameter: action", isError = true)

        val result = when (action) {
            "get_events" -> {
                val days = input["days_ahead"]?.jsonPrimitive?.intOrNull ?: 7
                bridge.getCalendarEvents(days)
            }
            "create_event" -> {
                val title = input["title"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing title for event", isError = true)
                val startStr = input["start_time"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing start_time for event", isError = true)
                val endStr = input["end_time"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing end_time for event", isError = true)
                val desc = input["description"]?.jsonPrimitive?.contentOrNull ?: ""

                val startMillis = parseTimeToMillis(startStr)
                    ?: return ToolResult("Invalid start_time format: $startStr", isError = true)
                val endMillis = parseTimeToMillis(endStr)
                    ?: return ToolResult("Invalid end_time format: $endStr", isError = true)

                bridge.createCalendarEvent(title, startMillis, endMillis, desc)
            }
            else -> return ToolResult("Unknown action: $action", isError = true)
        }

        return result.fold(
            onSuccess = { ToolResult(it) },
            onFailure = { ToolResult("Failed: ${it.message}", isError = true) }
        )
    }

    private fun parseTimeToMillis(timeStr: String): Long? {
        // Try epoch millis first
        timeStr.toLongOrNull()?.let { return it }

        // Try ISO 8601 format manually (yyyy-MM-ddTHH:mm:ss)
        return try {
            val parts = timeStr.split("T")
            if (parts.size != 2) return null
            val dateParts = parts[0].split("-").map { it.toInt() }
            val timeParts = parts[1].split(":").map { it.toInt() }
            if (dateParts.size != 3 || timeParts.size < 2) return null

            // Simple calculation - not timezone-aware, uses local device time
            val year = dateParts[0]
            val month = dateParts[1]
            val day = dateParts[2]
            val hour = timeParts[0]
            val minute = timeParts[1]
            val second = if (timeParts.size > 2) timeParts[2] else 0

            // Use a simple epoch calculation
            calculateEpochMillis(year, month, day, hour, minute, second)
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateEpochMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long {
        // Days from epoch (1970-01-01) - simplified calculation
        val daysInMonth = intArrayOf(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var totalDays = 0L

        for (y in 1970 until year) {
            totalDays += if (y % 4 == 0 && (y % 100 != 0 || y % 400 == 0)) 366 else 365
        }

        val isLeap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
        for (m in 1 until month) {
            totalDays += daysInMonth[m]
            if (m == 2 && isLeap) totalDays += 1
        }
        totalDays += (day - 1)

        return (totalDays * 86400 + hour * 3600 + minute * 60 + second) * 1000
    }
}
