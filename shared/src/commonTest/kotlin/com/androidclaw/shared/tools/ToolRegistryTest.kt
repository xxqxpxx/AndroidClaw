package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.ToolResult
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToolRegistryTest {

    private fun createMockHttpClient(): HttpClient {
        return HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    respond(
                        content = "{}",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }
    }

    @Test
    fun getTools_withoutApiKey_excludesWebSearch() {
        val registry = ToolRegistry(
            httpClient = createMockHttpClient(),
            tavilyApiKey = ""
        )
        val tools = registry.getTools()
        val toolNames = tools.map { it.name }
        assertTrue("web_search" !in toolNames, "web_search should not be included without API key")
    }

    @Test
    fun getTools_withApiKey_includesWebSearch() {
        val registry = ToolRegistry(
            httpClient = createMockHttpClient(),
            tavilyApiKey = "test-api-key"
        )
        val tools = registry.getTools()
        val toolNames = tools.map { it.name }
        assertTrue("web_search" in toolNames, "web_search should be included with API key")
    }

    @Test
    fun getTools_alwaysIncludesBaseTools() {
        val registry = ToolRegistry(
            httpClient = createMockHttpClient(),
            tavilyApiKey = ""
        )
        val tools = registry.getTools()
        val toolNames = tools.map { it.name }
        assertTrue("read_webpage" in toolNames, "read_webpage should always be included")
        assertTrue("datetime" in toolNames, "datetime should always be included")
        assertTrue("calculator" in toolNames, "calculator should always be included")
        assertTrue("run_code" in toolNames, "run_code should always be included")
    }

    @Test
    fun getTools_withoutDeviceBridge_excludesDeviceTools() {
        val registry = ToolRegistry(
            httpClient = createMockHttpClient(),
            deviceBridge = null
        )
        val tools = registry.getTools()
        val toolNames = tools.map { it.name }
        assertTrue("device_settings" !in toolNames, "device_settings should not be included without bridge")
        assertTrue("app_launcher" !in toolNames, "app_launcher should not be included without bridge")
        assertTrue("clipboard" !in toolNames, "clipboard should not be included without bridge")
        assertTrue("alarm_timer" !in toolNames, "alarm_timer should not be included without bridge")
        assertTrue("notifications" !in toolNames, "notifications should not be included without bridge")
    }

    @Test
    fun getTools_withDeviceBridge_includesDeviceTools() {
        val bridge = TestDeviceActionBridge()
        val registry = ToolRegistry(
            httpClient = createMockHttpClient(),
            deviceBridge = bridge
        )
        val tools = registry.getTools()
        val toolNames = tools.map { it.name }
        assertTrue("device_settings" in toolNames, "device_settings should be included with bridge")
        assertTrue("app_launcher" in toolNames, "app_launcher should be included with bridge")
        assertTrue("clipboard" in toolNames, "clipboard should be included with bridge")
        assertTrue("alarm_timer" in toolNames, "alarm_timer should be included with bridge")
        assertTrue("notifications" in toolNames, "notifications should be included with bridge")
    }

    @Test
    fun getTools_allToolsHaveNameAndDescription() {
        val registry = ToolRegistry(
            httpClient = createMockHttpClient(),
            tavilyApiKey = "test",
            deviceBridge = TestDeviceActionBridge()
        )
        val tools = registry.getTools()
        for (tool in tools) {
            assertTrue(tool.name.isNotEmpty(), "Tool name should not be empty")
            assertTrue(tool.description.isNotEmpty(), "Tool description should not be empty for ${tool.name}")
        }
    }

    @Test
    fun getTools_allToolsHaveInputSchema() {
        val registry = ToolRegistry(
            httpClient = createMockHttpClient(),
            tavilyApiKey = "test",
            deviceBridge = TestDeviceActionBridge()
        )
        val tools = registry.getTools()
        for (tool in tools) {
            assertTrue(tool.inputSchema.containsKey("type"), "inputSchema should have 'type' for ${tool.name}")
        }
    }
}

/**
 * Test stub for DeviceActionBridge.
 */
private class TestDeviceActionBridge : DeviceActionBridge {
    override suspend fun setWifiEnabled(enabled: Boolean) = Result.success("ok")
    override suspend fun setBluetoothEnabled(enabled: Boolean) = Result.success("ok")
    override suspend fun setFlashlightEnabled(enabled: Boolean) = Result.success("ok")
    override suspend fun setBrightness(level: Int) = Result.success("ok")
    override suspend fun setVolume(stream: String, level: Int) = Result.success("ok")
    override suspend fun getDeviceInfo() = Result.success("test device")
    override suspend fun launchApp(packageName: String) = Result.success("ok")
    override suspend fun listInstalledApps() = Result.success("[]")
    override suspend fun searchApps(query: String) = Result.success("[]")
    override suspend fun setClipboard(text: String) = Result.success("ok")
    override suspend fun getClipboard() = Result.success("clipboard content")
    override suspend fun getRecentNotifications(count: Int) = Result.success("[]")
    override suspend fun dismissNotification(key: String) = Result.success("ok")
    override suspend fun setAlarm(hour: Int, minute: Int, label: String) = Result.success("ok")
    override suspend fun setTimer(seconds: Int, label: String) = Result.success("ok")
}
