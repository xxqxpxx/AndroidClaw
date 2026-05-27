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
            tools.add(FilesTool(bridge))
            tools.add(IntentLauncherTool(bridge))
            tools.add(MediaControlTool(bridge))
            tools.add(ScreenTimeTool(bridge))
            tools.add(SystemDiagnosticsTool(bridge))
            tools.add(TextToSpeechTool(bridge))
            tools.add(DndControlTool(bridge))
            tools.add(WifiManagerTool(bridge))
            tools.add(BluetoothManagerTool(bridge))
            tools.add(NavigationDirectionsTool(bridge))
            tools.add(AudioProfileTool(bridge))
            tools.add(ShortcutTool(bridge))
            tools.add(AppManagementTool(bridge))
            tools.add(AppIntegrationTool(bridge))
            tools.add(AutomationTool(bridge))
            tools.add(ScreenAnalysisTool(bridge))
            tools.add(SchedulerTool(bridge))
            tools.add(SkillsTool(bridge))
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
