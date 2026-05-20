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

        // Device control tools
        deviceBridge?.let { bridge ->
            tools.add(DeviceSettingsTool(bridge))
            tools.add(AppLauncherTool(bridge))
            tools.add(ClipboardTool(bridge))
            tools.add(AlarmTimerTool(bridge))
            tools.add(NotificationTool(bridge))
            tools.add(ContactsTool(bridge))
            tools.add(CalendarTool(bridge))
            tools.add(SmsTool(bridge))
            tools.add(PhoneTool(bridge))
            tools.add(LocationTool(bridge))
            tools.add(DeviceAdminTool(bridge))
            tools.add(MessagingTool(bridge))
            tools.add(SystemActionsTool(bridge))
        }

        // Utility tools
        tools.add(WebContentTool(httpClient))
        tools.add(DateTimeTool())
        tools.add(CalculatorTool())
        tools.add(UnitConverterTool())
        tools.add(PasswordGeneratorTool())
        tools.add(EncodingTool())
        tools.add(HashTool())
        tools.add(WeatherTool(httpClient))
        tools.add(QrCodeGeneratorTool())
        tools.add(CurrencyConverterTool(httpClient))
        tools.add(TranslationTool(httpClient))
        tools.add(TimezoneConverterTool())
        tools.add(IpLookupTool(httpClient))

        // Code execution
        tools.add(CodeExecutionTool())

        return tools
    }
}
