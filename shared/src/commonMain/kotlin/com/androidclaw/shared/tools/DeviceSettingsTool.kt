package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

class DeviceSettingsTool(
    private val bridge: DeviceActionBridge
) : Tool {

    override val name = "device_settings"

    override val description = """Control device settings like Wi-Fi, Bluetooth, flashlight, brightness, and volume.
        |Use this when the user asks to toggle settings or adjust their device.""".trimMargin()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("description", "The setting action to perform")
                putJsonArray("enum") {
                    add("wifi_on"); add("wifi_off")
                    add("bluetooth_on"); add("bluetooth_off")
                    add("flashlight_on"); add("flashlight_off")
                    add("set_brightness"); add("set_volume")
                    add("device_info")
                }
            }
            putJsonObject("level") {
                put("type", "integer")
                put("description", "Level value 0-100 for brightness or volume")
            }
            putJsonObject("stream") {
                put("type", "string")
                put("description", "Audio stream for volume: media, ring, alarm, notification")
                putJsonArray("enum") { add("media"); add("ring"); add("alarm"); add("notification") }
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val action = input["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing required parameter: action", isError = true)

        val result = when (action) {
            "wifi_on" -> bridge.setWifiEnabled(true)
            "wifi_off" -> bridge.setWifiEnabled(false)
            "bluetooth_on" -> bridge.setBluetoothEnabled(true)
            "bluetooth_off" -> bridge.setBluetoothEnabled(false)
            "flashlight_on" -> bridge.setFlashlightEnabled(true)
            "flashlight_off" -> bridge.setFlashlightEnabled(false)
            "set_brightness" -> {
                val level = input["level"]?.jsonPrimitive?.intOrNull
                    ?: return ToolResult("Missing level for brightness", isError = true)
                bridge.setBrightness(level.coerceIn(0, 100))
            }
            "set_volume" -> {
                val level = input["level"]?.jsonPrimitive?.intOrNull
                    ?: return ToolResult("Missing level for volume", isError = true)
                val stream = input["stream"]?.jsonPrimitive?.contentOrNull ?: "media"
                bridge.setVolume(stream, level.coerceIn(0, 100))
            }
            "device_info" -> bridge.getDeviceInfo()
            else -> return ToolResult("Unknown action: $action", isError = true)
        }

        return result.fold(
            onSuccess = { ToolResult(it) },
            onFailure = { ToolResult("Failed: ${it.message}", isError = true) }
        )
    }
}
