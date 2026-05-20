package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

class NavigationDirectionsTool(
    private val bridge: DeviceActionBridge
) : Tool {

    override val name = "navigation_directions"

    override val description = """Get directions, search for places, explore Street View, and find nearby places.
        |Enhanced navigation beyond simple navigateTo — supports place search, multi-mode directions,
        |nearby place discovery (restaurants, gas stations, etc.), and Street View.""".trimMargin()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("description", "Navigation action to perform")
                putJsonArray("enum") {
                    add("search_places")
                    add("directions")
                    add("street_view")
                    add("nearby")
                }
            }
            putJsonObject("query") {
                put("type", "string")
                put("description", "Search query for place search (e.g. 'coffee shops', 'gas station near me')")
            }
            putJsonObject("from") {
                put("type", "string")
                put("description", "Starting location for directions (address or 'current')")
            }
            putJsonObject("to") {
                put("type", "string")
                put("description", "Destination for directions")
            }
            putJsonObject("mode") {
                put("type", "string")
                put("description", "Travel mode for directions")
                putJsonArray("enum") { add("driving"); add("walking"); add("bicycling"); add("transit") }
            }
            putJsonObject("latitude") {
                put("type", "number")
                put("description", "Latitude for Street View")
            }
            putJsonObject("longitude") {
                put("type", "number")
                put("description", "Longitude for Street View")
            }
            putJsonObject("type") {
                put("type", "string")
                put("description", "Place type for nearby search (e.g. 'restaurant', 'gas_station', 'hospital', 'pharmacy', 'atm')")
            }
            putJsonObject("radius") {
                put("type", "integer")
                put("description", "Search radius in meters for nearby places (default 1000)")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val action = input["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing required parameter: action", isError = true)

        val result = when (action) {
            "search_places" -> {
                val query = input["query"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing search query", isError = true)
                bridge.searchPlaces(query)
            }
            "directions" -> {
                val from = input["from"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing 'from' location", isError = true)
                val to = input["to"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing 'to' destination", isError = true)
                val mode = input["mode"]?.jsonPrimitive?.contentOrNull ?: "driving"
                bridge.getDirections(from, to, mode)
            }
            "street_view" -> {
                val lat = input["latitude"]?.jsonPrimitive?.doubleOrNull
                    ?: return ToolResult("Missing latitude", isError = true)
                val lon = input["longitude"]?.jsonPrimitive?.doubleOrNull
                    ?: return ToolResult("Missing longitude", isError = true)
                bridge.openStreetView(lat, lon)
            }
            "nearby" -> {
                val type = input["type"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing place type", isError = true)
                val radius = input["radius"]?.jsonPrimitive?.intOrNull ?: 1000
                bridge.getNearbyPlaces(type, radius)
            }
            else -> return ToolResult("Unknown navigation action: $action", isError = true)
        }

        return result.fold(
            onSuccess = { ToolResult(it) },
            onFailure = { ToolResult("Failed: ${it.message}", isError = true) }
        )
    }
}
