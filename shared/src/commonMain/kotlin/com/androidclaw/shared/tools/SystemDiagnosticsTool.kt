package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

class SystemDiagnosticsTool(
    private val bridge: DeviceActionBridge
) : Tool {

    override val name = "system_diagnostics"

    override val description = """Get detailed system diagnostic information: CPU info (architecture, cores, frequency),
        |sensor list (accelerometer, gyroscope, etc.), thermal/temperature info, running processes,
        |storage breakdown by category, detailed battery health/temp/voltage, memory usage, and device uptime.""".trimMargin()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("description", "The diagnostic info to retrieve")
                putJsonArray("enum") {
                    add("cpu_info")
                    add("sensor_list")
                    add("thermal_info")
                    add("process_list")
                    add("storage_breakdown")
                    add("battery_health")
                    add("memory_usage")
                    add("uptime")
                    add("full_report")
                }
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val action = input["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing required parameter: action", isError = true)

        val result = when (action) {
            "cpu_info" -> bridge.getCpuInfo()
            "sensor_list" -> bridge.getSensorList()
            "thermal_info" -> bridge.getThermalInfo()
            "process_list" -> bridge.getProcessList()
            "storage_breakdown" -> bridge.getStorageBreakdown()
            "battery_health" -> bridge.getBatteryInfo()
            "memory_usage" -> bridge.getMemoryInfo()
            "uptime" -> bridge.getDeviceUptime()
            "full_report" -> {
                val parts = mutableListOf<String>()
                bridge.getCpuInfo().onSuccess { parts.add("=== CPU ===\n$it") }
                bridge.getMemoryInfo().onSuccess { parts.add("=== MEMORY ===\n$it") }
                bridge.getBatteryInfo().onSuccess { parts.add("=== BATTERY ===\n$it") }
                bridge.getStorageBreakdown().onSuccess { parts.add("=== STORAGE ===\n$it") }
                bridge.getDeviceUptime().onSuccess { parts.add("=== UPTIME ===\n$it") }
                if (parts.isEmpty()) return ToolResult("Failed to retrieve diagnostics", isError = true)
                Result.success(parts.joinToString("\n\n"))
            }
            else -> return ToolResult("Unknown diagnostic action: $action", isError = true)
        }

        return result.fold(
            onSuccess = { ToolResult(it) },
            onFailure = { ToolResult("Failed: ${it.message}", isError = true) }
        )
    }
}
