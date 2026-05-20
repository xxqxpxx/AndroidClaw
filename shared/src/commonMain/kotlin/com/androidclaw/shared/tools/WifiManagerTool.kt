package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

class WifiManagerTool(
    private val bridge: DeviceActionBridge
) : Tool {

    override val name = "wifi_manager"

    override val description = """Manage Wi-Fi networks: scan for available networks, view saved networks,
        |connect to a specific SSID, forget a saved network, and get current Wi-Fi connection info.""".trimMargin()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("description", "Wi-Fi management action")
                putJsonArray("enum") {
                    add("scan")
                    add("saved_networks")
                    add("connect")
                    add("forget")
                    add("status")
                }
            }
            putJsonObject("ssid") {
                put("type", "string")
                put("description", "Network name (SSID) for connect or forget")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val action = input["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing required parameter: action", isError = true)

        val result = when (action) {
            "scan" -> bridge.scanWifiNetworks()
            "saved_networks" -> bridge.getSavedWifiNetworks()
            "connect" -> {
                val ssid = input["ssid"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing SSID to connect to", isError = true)
                bridge.connectToWifi(ssid)
            }
            "forget" -> {
                val ssid = input["ssid"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing SSID to forget", isError = true)
                bridge.forgetWifiNetwork(ssid)
            }
            "status" -> bridge.getWifiInfo()
            else -> return ToolResult("Unknown Wi-Fi action: $action", isError = true)
        }

        return result.fold(
            onSuccess = { ToolResult(it) },
            onFailure = { ToolResult("Failed: ${it.message}", isError = true) }
        )
    }
}
