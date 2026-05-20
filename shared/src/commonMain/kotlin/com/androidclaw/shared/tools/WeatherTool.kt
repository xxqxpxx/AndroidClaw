package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

/**
 * Current + short-term weather forecast for a location, using the free
 * Open-Meteo API (no API key required). Uses their geocoding endpoint to
 * resolve a place name into latitude / longitude.
 */
class WeatherTool(
    private val httpClient: HttpClient
) : Tool {

    override val name = "weather"

    override val description =
        "Get the current weather and a short forecast for a location (city name or 'lat,lon'). Uses Open-Meteo."

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("location") {
                put("type", "string")
                put("description", "City name (e.g. 'Cairo'), or 'lat,lon' (e.g. '30.04,31.23')")
            }
            putJsonObject("units") {
                put("type", "string")
                put("description", "Temperature units: 'celsius' (default) or 'fahrenheit'")
                putJsonArray("enum") { add("celsius"); add("fahrenheit") }
            }
        }
        putJsonArray("required") { add("location") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val location = input["location"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing 'location'", isError = true)
        val units = input["units"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "celsius"
        val tempUnit = if (units == "fahrenheit") "fahrenheit" else "celsius"

        return try {
            val (lat, lon, label) = resolveLocation(location)
                ?: return ToolResult("Could not resolve location: $location", isError = true)

            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,wind_speed_10m" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum" +
                "&temperature_unit=$tempUnit&timezone=auto&forecast_days=3"

            val resp = httpClient.get(url) { header(HttpHeaders.Accept, "application/json") }
            if (!resp.status.isSuccess()) {
                return ToolResult("Weather API error: ${resp.status.value}", isError = true)
            }
            val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
            val current = body["current"]?.jsonObject ?: return ToolResult("No current weather data", isError = true)
            val daily = body["daily"]?.jsonObject

            val tUnit = if (tempUnit == "fahrenheit") "°F" else "°C"
            val temp = current["temperature_2m"]?.jsonPrimitive?.contentOrNull
            val feels = current["apparent_temperature"]?.jsonPrimitive?.contentOrNull
            val humidity = current["relative_humidity_2m"]?.jsonPrimitive?.contentOrNull
            val wind = current["wind_speed_10m"]?.jsonPrimitive?.contentOrNull
            val code = current["weather_code"]?.jsonPrimitive?.intOrNull
            val desc = weatherDescription(code)

            val sb = StringBuilder()
            sb.appendLine("Weather for $label:")
            sb.appendLine("- Now: $desc, $temp$tUnit (feels like $feels$tUnit)")
            sb.appendLine("- Humidity: $humidity%, Wind: $wind km/h")

            daily?.let { d ->
                val dates = d["time"]?.jsonArray
                val highs = d["temperature_2m_max"]?.jsonArray
                val lows = d["temperature_2m_min"]?.jsonArray
                val codes = d["weather_code"]?.jsonArray
                val precip = d["precipitation_sum"]?.jsonArray
                if (dates != null && highs != null && lows != null) {
                    sb.appendLine("Forecast:")
                    for (i in 0 until dates.size) {
                        val date = dates[i].jsonPrimitive.content
                        val hi = highs[i].jsonPrimitive.contentOrNull
                        val lo = lows[i].jsonPrimitive.contentOrNull
                        val c = codes?.get(i)?.jsonPrimitive?.intOrNull
                        val p = precip?.get(i)?.jsonPrimitive?.contentOrNull
                        sb.appendLine("  $date: ${weatherDescription(c)}, $lo$tUnit–$hi$tUnit, precip ${p}mm")
                    }
                }
            }
            ToolResult(sb.toString().trimEnd())
        } catch (e: Exception) {
            ToolResult("Weather lookup failed: ${e.message}", isError = true)
        }
    }

    private suspend fun resolveLocation(input: String): Triple<String, String, String>? {
        val coordRegex = Regex("""^\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*$""")
        coordRegex.matchEntire(input)?.let { m ->
            return Triple(m.groupValues[1], m.groupValues[2], "${m.groupValues[1]}, ${m.groupValues[2]}")
        }
        val geoUrl = "https://geocoding-api.open-meteo.com/v1/search" +
            "?name=${input.encodeURLParameter()}&count=1&language=en&format=json"
        val resp = httpClient.get(geoUrl) { header(HttpHeaders.Accept, "application/json") }
        if (!resp.status.isSuccess()) return null
        val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        val results = body["results"]?.jsonArray ?: return null
        if (results.isEmpty()) return null
        val first = results[0].jsonObject
        val lat = first["latitude"]?.jsonPrimitive?.contentOrNull ?: return null
        val lon = first["longitude"]?.jsonPrimitive?.contentOrNull ?: return null
        val name = first["name"]?.jsonPrimitive?.contentOrNull ?: input
        val country = first["country"]?.jsonPrimitive?.contentOrNull
        val label = if (country != null) "$name, $country" else name
        return Triple(lat, lon, label)
    }

    private fun weatherDescription(code: Int?): String = when (code) {
        0 -> "Clear sky"
        1, 2, 3 -> "Partly cloudy"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61, 63, 65 -> "Rain"
        66, 67 -> "Freezing rain"
        71, 73, 75 -> "Snow"
        77 -> "Snow grains"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm with hail"
        null -> "Unknown"
        else -> "Code $code"
    }
}
