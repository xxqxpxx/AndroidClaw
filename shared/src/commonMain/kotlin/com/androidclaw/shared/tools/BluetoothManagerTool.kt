package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

class BluetoothManagerTool(
    private val bridge: DeviceActionBridge
) : Tool {

    override val name = "bluetooth_manager"

    override val description = """Manage Bluetooth devices: list paired devices, connect or disconnect a specific device,
        |open Bluetooth pairing settings, and toggle Bluetooth on/off.""".trimMargin()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("description", "Bluetooth management action")
                putJsonArray("enum") {
                    add("paired_devices")
                    add("connect")
                    add("disconnect")
                    add("pair")
                    add("on")
                    add("off")
                }
            }
            putJsonObject("address") {
                put("type", "string")
                put("description", "Bluetooth device MAC address for connect/disconnect (from paired_devices list)")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val action = input["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing required parameter: action", isError = true)

        val result = when (action) {
            "paired_devices" -> bridge.getBluetoothPairedDevices()
            "connect" -> {
                val address = input["address"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing Bluetooth device address", isError = true)
                bridge.connectBluetoothDevice(address)
            }
            "disconnect" -> {
                val address = input["address"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing Bluetooth device address", isError = true)
                bridge.disconnectBluetoothDevice(address)
            }
            "pair" -> bridge.pairBluetoothDevice()
            "on" -> bridge.setBluetoothEnabled(true)
            "off" -> bridge.setBluetoothEnabled(false)
            else -> return ToolResult("Unknown Bluetooth action: $action", isError = true)
        }

        return result.fold(
            onSuccess = { ToolResult(it) },
            onFailure = { ToolResult("Failed: ${it.message}", isError = true) }
        )
    }
}
