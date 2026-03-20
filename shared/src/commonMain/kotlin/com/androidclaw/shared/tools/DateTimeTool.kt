package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.*

class DateTimeTool : Tool {

    override val name = "datetime"

    override val description = "Get the current date, time, and timezone information."

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("timezone") {
                put("type", "string")
                put("description", "Timezone ID (e.g. 'America/New_York'). Defaults to device timezone.")
            }
        }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        return try {
            val tzId = input["timezone"]?.jsonPrimitive?.contentOrNull
            val tz = if (tzId != null) {
                try { TimeZone.of(tzId) } catch (_: Exception) { TimeZone.currentSystemDefault() }
            } else {
                TimeZone.currentSystemDefault()
            }

            val now = Clock.System.now()
            val local = now.toLocalDateTime(tz)

            val dayOfWeek = local.dayOfWeek.name.lowercase()
                .replaceFirstChar { it.uppercase() }

            val result = buildString {
                appendLine("Current date and time:")
                appendLine("Date: ${local.year}-${local.monthNumber.toString().padStart(2, '0')}-${local.dayOfMonth.toString().padStart(2, '0')}")
                appendLine("Day: $dayOfWeek")
                appendLine("Time: ${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}:${local.second.toString().padStart(2, '0')}")
                appendLine("Timezone: ${tz.id}")
                appendLine("Unix timestamp: ${now.epochSeconds}")
            }

            ToolResult(result.trim())
        } catch (e: Exception) {
            ToolResult("Failed to get datetime: ${e.message}", isError = true)
        }
    }
}
