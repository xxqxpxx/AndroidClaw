package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.*

/**
 * Convert a wall-clock datetime from one IANA timezone to another using kotlinx-datetime.
 * Accepts ISO-8601 datetime strings ("2025-01-15T14:30:00") or full instants
 * ("2025-01-15T14:30:00Z").
 */
class TimezoneConverterTool : Tool {

    override val name = "timezone_converter"

    override val description =
        "Convert a wall-clock datetime between IANA timezones (e.g. 'America/New_York' → 'Asia/Tokyo'). Input ISO-8601 datetime like '2025-01-15T14:30:00'."

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("datetime") {
                put("type", "string")
                put("description", "ISO-8601 datetime, e.g. '2025-01-15T14:30:00'. May include trailing 'Z' for UTC.")
            }
            putJsonObject("from_tz") {
                put("type", "string")
                put("description", "Source IANA timezone, e.g. 'America/New_York'. Ignored when datetime ends in 'Z'.")
            }
            putJsonObject("to_tz") {
                put("type", "string")
                put("description", "Target IANA timezone, e.g. 'Asia/Tokyo'")
            }
        }
        putJsonArray("required") { add("datetime"); add("from_tz"); add("to_tz") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val dt = input["datetime"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing 'datetime'", isError = true)
        val fromId = input["from_tz"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing 'from_tz'", isError = true)
        val toId = input["to_tz"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing 'to_tz'", isError = true)

        val fromTz = try {
            TimeZone.of(fromId)
        } catch (e: Exception) {
            return ToolResult("Unknown 'from_tz': $fromId", isError = true)
        }
        val toTz = try {
            TimeZone.of(toId)
        } catch (e: Exception) {
            return ToolResult("Unknown 'to_tz': $toId", isError = true)
        }

        return try {
            val instant: Instant = try {
                Instant.parse(dt)
            } catch (_: Exception) {
                LocalDateTime.parse(dt).toInstant(fromTz)
            }
            val source = instant.toLocalDateTime(fromTz)
            val target = instant.toLocalDateTime(toTz)
            ToolResult(
                "Timezone conversion:\n" +
                    "  $source ($fromId)\n" +
                    "  → $target ($toId)\n" +
                    "  UTC: $instant"
            )
        } catch (e: Exception) {
            ToolResult("Timezone conversion failed: ${e.message}", isError = true)
        }
    }
}
