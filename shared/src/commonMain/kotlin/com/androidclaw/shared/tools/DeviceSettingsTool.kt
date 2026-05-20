package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

class DeviceSettingsTool(
    private val bridge: DeviceActionBridge
) : Tool {

    override val name = "device_settings"

    override val description = """Control device settings: Wi-Fi, Bluetooth, flashlight, brightness, volume, ringer mode, screen timeout, dark mode, auto-rotate, location, airplane mode, hotspot, mobile data, NFC, and more.
        |Use this when the user asks to toggle settings, adjust their device, silence their phone, check connectivity info, or get device info.""".trimMargin()

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
                    add("ringer_silent"); add("ringer_vibrate"); add("ringer_normal")
                    add("speakerphone_on"); add("speakerphone_off")
                    add("set_screen_timeout")
                    add("dark_mode_on"); add("dark_mode_off")
                    add("battery_saver")
                    add("device_info")
                    add("auto_brightness_on"); add("auto_brightness_off")
                    add("location_on"); add("location_off")
                    add("airplane_on"); add("airplane_off")
                    add("hotspot_on"); add("hotspot_off")
                    add("mobile_data_on"); add("mobile_data_off")
                    add("nfc_settings")
                    add("wifi_info")
                    add("get_volume")
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
            putJsonObject("seconds") {
                put("type", "integer")
                put("description", "Screen timeout in seconds (15, 30, 60, 120, 300, 600)")
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
            "ringer_silent" -> bridge.setRingerMode("silent")
            "ringer_vibrate" -> bridge.setRingerMode("vibrate")
            "ringer_normal" -> bridge.setRingerMode("normal")
            "speakerphone_on" -> bridge.setSpeakerphone(true)
            "speakerphone_off" -> bridge.setSpeakerphone(false)
            "set_screen_timeout" -> {
                val seconds = input["seconds"]?.jsonPrimitive?.intOrNull
                    ?: return ToolResult("Missing seconds for screen timeout", isError = true)
                bridge.setScreenTimeout(seconds)
            }
            "dark_mode_on" -> bridge.setDarkMode(true)
            "dark_mode_off" -> bridge.setDarkMode(false)
            "battery_saver" -> bridge.setBatterySaver(true)
            "device_info" -> bridge.getDeviceInfo()
            "auto_brightness_on" -> bridge.setAutoBrightness(true)
            "auto_brightness_off" -> bridge.setAutoBrightness(false)
            "location_on" -> bridge.setLocationEnabled(true)
            "location_off" -> bridge.setLocationEnabled(false)
            "airplane_on" -> bridge.setAirplaneMode(true)
            "airplane_off" -> bridge.setAirplaneMode(false)
            "hotspot_on" -> bridge.setHotspot(true)
            "hotspot_off" -> bridge.setHotspot(false)
            "mobile_data_on" -> bridge.setMobileData(true)
            "mobile_data_off" -> bridge.setMobileData(false)
            "nfc_settings" -> bridge.openNfcSettings()
            "wifi_info" -> bridge.getWifiInfo()
            "get_volume" -> bridge.getVolumeInfo()
            else -> return ToolResult("Unknown action: $action", isError = true)
        }

        return result.fold(
            onSuccess = { ToolResult(it) },
            onFailure = { ToolResult("Failed: ${it.message}", isError = true) }
        )
    }
}
