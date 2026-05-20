package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

/**
 * Pure unit converter for common physical units. No network, no permissions.
 * Categories: length, mass, temperature, volume, speed, time, data.
 */
class UnitConverterTool : Tool {

    override val name = "unit_converter"

    override val description =
        "Convert a numeric value between units. Supported: length (m, km, mi, ft, in, cm, mm), mass (kg, g, mg, lb, oz), temperature (c, f, k), volume (l, ml, gal, qt, pt, cup, floz), speed (mps, kph, mph, knot), time (s, min, h, day, week), data (b, kb, mb, gb, tb)."

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("value") {
                put("type", "number")
                put("description", "Numeric value to convert")
            }
            putJsonObject("from") {
                put("type", "string")
                put("description", "Source unit (case-insensitive)")
            }
            putJsonObject("to") {
                put("type", "string")
                put("description", "Target unit (case-insensitive)")
            }
        }
        putJsonArray("required") { add("value"); add("from"); add("to") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val value = input["value"]?.jsonPrimitive?.doubleOrNull
            ?: return ToolResult("Missing or invalid 'value'", isError = true)
        val from = input["from"]?.jsonPrimitive?.contentOrNull?.lowercase()
            ?: return ToolResult("Missing 'from'", isError = true)
        val to = input["to"]?.jsonPrimitive?.contentOrNull?.lowercase()
            ?: return ToolResult("Missing 'to'", isError = true)

        return try {
            val result = convert(value, from, to)
            val formatted = if (result == result.toLong().toDouble()) result.toLong().toString()
            else ((result * 1_000_000).toLong() / 1_000_000.0).toString()
            ToolResult("$value $from = $formatted $to")
        } catch (e: Exception) {
            ToolResult("Conversion failed: ${e.message}", isError = true)
        }
    }

    private fun convert(value: Double, from: String, to: String): Double {
        // Temperature is non-multiplicative, handle separately
        if (from in TEMP || to in TEMP) {
            require(from in TEMP && to in TEMP) { "Cannot mix temperature with other units" }
            return convertTemp(value, from, to)
        }
        val (cat1, base1) = baseOf(from)
        val (cat2, base2) = baseOf(to)
        require(cat1 == cat2) { "Incompatible units: $from ($cat1) vs $to ($cat2)" }
        return value * base1 / base2
    }

    private fun baseOf(unit: String): Pair<String, Double> {
        return LENGTH[unit]?.let { "length" to it }
            ?: MASS[unit]?.let { "mass" to it }
            ?: VOLUME[unit]?.let { "volume" to it }
            ?: SPEED[unit]?.let { "speed" to it }
            ?: TIME[unit]?.let { "time" to it }
            ?: DATA[unit]?.let { "data" to it }
            ?: throw IllegalArgumentException("Unknown unit: $unit")
    }

    private fun convertTemp(value: Double, from: String, to: String): Double {
        val celsius = when (from) {
            "c" -> value
            "f" -> (value - 32) * 5.0 / 9.0
            "k" -> value - 273.15
            else -> error(from)
        }
        return when (to) {
            "c" -> celsius
            "f" -> celsius * 9.0 / 5.0 + 32
            "k" -> celsius + 273.15
            else -> error(to)
        }
    }

    private companion object {
        val TEMP = setOf("c", "f", "k")

        // base = meters
        val LENGTH = mapOf(
            "m" to 1.0, "km" to 1000.0, "cm" to 0.01, "mm" to 0.001,
            "mi" to 1609.344, "ft" to 0.3048, "in" to 0.0254, "yd" to 0.9144
        )
        // base = grams
        val MASS = mapOf(
            "g" to 1.0, "kg" to 1000.0, "mg" to 0.001, "t" to 1_000_000.0,
            "lb" to 453.59237, "oz" to 28.349523125
        )
        // base = liters
        val VOLUME = mapOf(
            "l" to 1.0, "ml" to 0.001, "m3" to 1000.0,
            "gal" to 3.785411784, "qt" to 0.946352946, "pt" to 0.473176473,
            "cup" to 0.2365882365, "floz" to 0.0295735296
        )
        // base = meters per second
        val SPEED = mapOf(
            "mps" to 1.0, "kph" to 1000.0 / 3600.0, "mph" to 1609.344 / 3600.0,
            "knot" to 1852.0 / 3600.0
        )
        // base = seconds
        val TIME = mapOf(
            "s" to 1.0, "min" to 60.0, "h" to 3600.0,
            "day" to 86400.0, "week" to 604800.0
        )
        // base = bytes
        val DATA = mapOf(
            "b" to 1.0, "kb" to 1024.0, "mb" to 1024.0 * 1024,
            "gb" to 1024.0 * 1024 * 1024, "tb" to 1024.0 * 1024 * 1024 * 1024
        )
    }
}
