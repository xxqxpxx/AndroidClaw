package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import io.ktor.client.*

class ToolRegistry(
    private val httpClient: HttpClient,
    private val tavilyApiKey: String = "",
    private val deviceBridge: DeviceActionBridge? = null
) {
    fun getTools(): List<Tool> {
        val tools = mutableListOf<Tool>()

        if (tavilyApiKey.isNotEmpty()) {
            tools.add(WebSearchTool(httpClient, tavilyApiKey))
        }

        // Phase 2: Device control tools
        deviceBridge?.let { bridge ->
            tools.add(DeviceSettingsTool(bridge))
            tools.add(AppLauncherTool(bridge))
            tools.add(ClipboardTool(bridge))
            tools.add(AlarmTimerTool(bridge))
            tools.add(NotificationTool(bridge))
        }

        // Phase 3: Add browse tools here
        // Phase 4: Add code execution tools here

        return tools
    }
}
