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
    override suspend fun getDeviceInfo() = Result.success("ok")
    override suspend fun launchApp(packageName: String) = Result.success("ok")
    override suspend fun listInstalledApps() = Result.success("ok")
    override suspend fun searchApps(query: String) = Result.success("ok")
    override suspend fun setClipboard(text: String) = Result.success("ok")
    override suspend fun getClipboard() = Result.success("ok")
    override suspend fun getRecentNotifications(count: Int) = Result.success("ok")
    override suspend fun dismissNotification(key: String) = Result.success("ok")
    override suspend fun setAlarm(hour: Int, minute: Int, label: String) = Result.success("ok")
    override suspend fun setTimer(seconds: Int, label: String) = Result.success("ok")
    override suspend fun getContacts(query: String, limit: Int) = Result.success("ok")
    override suspend fun addContact(name: String, phone: String, email: String) = Result.success("ok")
    override suspend fun findContactByName(name: String) = Result.success("ok")
    override suspend fun getCalendarEvents(daysAhead: Int) = Result.success("ok")
    override suspend fun createCalendarEvent(title: String, startTimeMillis: Long, endTimeMillis: Long, description: String) = Result.success("ok")
    override suspend fun sendSms(phoneNumber: String, message: String) = Result.success("ok")
    override suspend fun getRecentSms(count: Int) = Result.success("ok")
    override suspend fun getSmsFromContact(contactName: String, count: Int) = Result.success("ok")
    override suspend fun makeCall(phoneNumber: String) = Result.success("ok")
    override suspend fun getCallLog(count: Int) = Result.success("ok")
    override suspend fun getCurrentLocation() = Result.success("ok")
    override suspend fun lockScreen() = Result.success("ok")
    override suspend fun setCameraDisabled(disabled: Boolean) = Result.success("ok")
    override suspend fun setMaxScreenLockTimeout(timeoutMs: Long) = Result.success("ok")
    override suspend fun getDeviceAdminStatus() = Result.success("ok")
    override suspend fun sendIntentMessage(packageName: String, phoneNumber: String, message: String) = Result.success("ok")
    override suspend fun shareText(text: String, packageName: String?) = Result.success("ok")
    override suspend fun openUrl(url: String, packageName: String?) = Result.success("ok")
    override suspend fun mediaPlayPause() = Result.success("ok")
    override suspend fun mediaNext() = Result.success("ok")
    override suspend fun mediaPrevious() = Result.success("ok")
    override suspend fun mediaStop() = Result.success("ok")
    override suspend fun setDoNotDisturb(enabled: Boolean) = Result.success("ok")
    override suspend fun setAutoRotate(enabled: Boolean) = Result.success("ok")
    override suspend fun takeScreenshot() = Result.success("ok")
    override suspend fun openSettings(settingsPage: String) = Result.success("ok")
    override suspend fun expandNotifications() = Result.success("ok")
    override suspend fun goHome() = Result.success("ok")
    override suspend fun goBack() = Result.success("ok")
    override suspend fun showRecents() = Result.success("ok")
    override suspend fun navigateTo(destination: String, mode: String) = Result.success("ok")
    override suspend fun sendEmail(to: String, subject: String, body: String) = Result.success("ok")
    override suspend fun setRingerMode(mode: String) = Result.success("ok")
    override suspend fun setSpeakerphone(enabled: Boolean) = Result.success("ok")
    override suspend fun setScreenTimeout(seconds: Int) = Result.success("ok")
    override suspend fun setBatterySaver(enabled: Boolean) = Result.success("ok")
    override suspend fun setDarkMode(enabled: Boolean) = Result.success("ok")
    override suspend fun openCamera() = Result.success("ok")
    override suspend fun takePhoto() = Result.success("ok")
    override suspend fun uninstallApp(packageName: String) = Result.success("ok")
    override suspend fun forceStopApp(packageName: String) = Result.success("ok")
    override suspend fun getAppInfo(packageName: String) = Result.success("ok")
    override suspend fun openQuickSettings() = Result.success("ok")
    override suspend fun openPowerMenu() = Result.success("ok")
    override suspend fun splitScreen() = Result.success("ok")
    override suspend fun lockOrientation(portrait: Boolean) = Result.success("ok")
    override suspend fun createNote(title: String, content: String) = Result.success("ok")
    override suspend fun scanQrCode() = Result.success("ok")
    override suspend fun getBatteryInfo() = Result.success("ok")
    override suspend fun getStorageInfo() = Result.success("ok")
    override suspend fun getNetworkInfo() = Result.success("ok")
    override suspend fun getBluetoothDevices() = Result.success("ok")
    override suspend fun findMyPhone() = Result.success("ok")
    override suspend fun readAloud(text: String) = Result.success("ok")
    override suspend fun openHotspotSettings() = Result.success("ok")
    override suspend fun openAirplaneSettings() = Result.success("ok")
    override suspend fun clearAllNotifications() = Result.success("ok")
    override suspend fun startStopwatch() = Result.success("ok")
    override suspend fun translateText(text: String, targetLang: String) = Result.success("ok")
    override suspend fun identifySong() = Result.success("ok")
    override suspend fun quickShare(text: String) = Result.success("ok")
    override suspend fun openFileManager() = Result.success("ok")
    override suspend fun answerCall() = Result.success("ok")
    override suspend fun rejectCall() = Result.success("ok")
    override suspend fun setWallpaper(url: String) = Result.success("ok")
    override suspend fun setFontSize(scale: String) = Result.success("ok")
    override suspend fun screenRecord() = Result.success("ok")
    override suspend fun restartDevice() = Result.success("ok")
    override suspend fun listFiles(directory: String, sortBy: String) = Result.success("ok")
    override suspend fun getFileInfo(filePath: String) = Result.success("ok")
    override suspend fun deleteFile(filePath: String) = Result.success("ok")
    override suspend fun moveFile(sourcePath: String, destDirectory: String) = Result.success("ok")
    override suspend fun organizeFiles(directory: String) = Result.success("ok")
    override suspend fun orderRide(destination: String, service: String) = Result.success("ok")
    override suspend fun getEmailNotifications(count: Int) = Result.success("ok")
    override suspend fun openDeepLink(uri: String, packageName: String?, fallbackUrl: String?) = Result.success("ok")
    override suspend fun coinFlip() = Result.success("ok")
    override suspend fun rollDice(sides: Int) = Result.success("ok")
    override suspend fun randomNumber(min: Int, max: Int) = Result.success("ok")
    override suspend fun countdownTo(date: String) = Result.success("ok")
    override suspend fun startVoiceRecording() = Result.success("ok")
    override suspend fun openSpeedTest() = Result.success("ok")
    override suspend fun castScreen() = Result.success("ok")
    override suspend fun openIncognito() = Result.success("ok")
    override suspend fun sortChromeTabs(order: String) = Result.success("ok")
    override suspend fun emergencyCall() = Result.success("ok")
    override suspend fun getDataUsage() = Result.success("ok")
    override suspend fun getSimInfo() = Result.success("ok")
    override suspend fun getDeviceUptime() = Result.success("ok")
    override suspend fun getMemoryInfo() = Result.success("ok")
    override suspend fun checkForUpdate() = Result.success("ok")
    override suspend fun setNightLight(enabled: Boolean) = Result.success("ok")
    override suspend fun setBedtimeMode(enabled: Boolean) = Result.success("ok")
    override suspend fun pinApp() = Result.success("ok")
    override suspend fun flashlightSos() = Result.success("ok")
    override suspend fun setColorInversion(enabled: Boolean) = Result.success("ok")
    override suspend fun setMagnification(enabled: Boolean) = Result.success("ok")
    override suspend fun clearAppData(packageName: String) = Result.success("ok")
    override suspend fun openDefaultApps() = Result.success("ok")
    override suspend fun openDigitalWellbeing() = Result.success("ok")
    override suspend fun openRingtoneSettings() = Result.success("ok")
    override suspend fun createReminder(text: String, timeMillis: Long) = Result.success("ok")
    override suspend fun shareMedia(filePath: String, packageName: String?) = Result.success("ok")
    override suspend fun setAutoBrightness(enabled: Boolean) = Result.success("ok")
    override suspend fun setLocationEnabled(enabled: Boolean) = Result.success("ok")
    override suspend fun setAirplaneMode(enabled: Boolean) = Result.success("ok")
    override suspend fun setHotspot(enabled: Boolean) = Result.success("ok")
    override suspend fun setMobileData(enabled: Boolean) = Result.success("ok")
    override suspend fun openNfcSettings() = Result.success("ok")
    override suspend fun getWifiInfo() = Result.success("ok")
    override suspend fun getVolumeInfo() = Result.success("ok")
    override suspend fun listAlarms() = Result.success("ok")
    override suspend fun cancelTimer() = Result.success("ok")
    override suspend fun getReminders() = Result.success("ok")
    override suspend fun deleteReminder(id: String) = Result.success("ok")
    override suspend fun editContact(name: String, newPhone: String, newEmail: String) = Result.success("ok")
    override suspend fun deleteContact(name: String) = Result.success("ok")
    override suspend fun getFavoriteContacts() = Result.success("ok")
    override suspend fun searchSms(query: String, count: Int) = Result.success("ok")
    override suspend fun deleteSmsConversation(contactName: String) = Result.success("ok")
    override suspend fun deleteCalendarEvent(eventId: String) = Result.success("ok")
    override suspend fun searchCalendarEvents(query: String, daysAhead: Int) = Result.success("ok")
    override suspend fun blockNumber(phoneNumber: String) = Result.success("ok")
    override suspend fun checkVoicemail() = Result.success("ok")
    override suspend fun replyToNotification(key: String, text: String) = Result.success("ok")
    override suspend fun getScreenTime(days: Int) = Result.success("ok")
    override suspend fun getAppUsageStats(days: Int) = Result.success("ok")
    override suspend fun getBatteryUsageStats() = Result.success("ok")
    override suspend fun setTalkBack(enabled: Boolean) = Result.success("ok")
    override suspend fun setDisplaySize(scale: String) = Result.success("ok")
    override suspend fun setHighContrast(enabled: Boolean) = Result.success("ok")
    override suspend fun getAccessibilitySettings() = Result.success("ok")
    override suspend fun setRingtone(uri: String) = Result.success("ok")
    override suspend fun setNotificationSound(uri: String) = Result.success("ok")
    override suspend fun schedulePowerOff(hour: Int, minute: Int) = Result.success("ok")
    override suspend fun getSignalStrength() = Result.success("ok")
    override suspend fun getConnectionType() = Result.success("ok")
    override suspend fun getIpAddress() = Result.success("ok")
    override suspend fun pingHost(host: String) = Result.success("ok")
    override suspend fun setDefaultBrowser(packageName: String) = Result.success("ok")
    override suspend fun setDefaultLauncher(packageName: String) = Result.success("ok")
    override suspend fun setDrivingMode(enabled: Boolean) = Result.success("ok")
    override suspend fun getCpuInfo() = Result.success("ok")
    override suspend fun getSensorList() = Result.success("ok")
    override suspend fun getThermalInfo() = Result.success("ok")
    override suspend fun getProcessList() = Result.success("ok")
    override suspend fun getStorageBreakdown() = Result.success("ok")
    override suspend fun ttsSpeak(text: String, language: String, speed: Float, pitch: Float) = Result.success("ok")
    override suspend fun ttsStop() = Result.success("ok")
    override suspend fun ttsGetVoices() = Result.success("ok")
    override suspend fun setDndMode(mode: String) = Result.success("ok")
    override suspend fun getDndStatus() = Result.success("ok")
    override suspend fun scanWifiNetworks() = Result.success("ok")
    override suspend fun connectToWifi(ssid: String) = Result.success("ok")
    override suspend fun getSavedWifiNetworks() = Result.success("ok")
    override suspend fun forgetWifiNetwork(ssid: String) = Result.success("ok")
    override suspend fun connectBluetoothDevice(address: String) = Result.success("ok")
    override suspend fun disconnectBluetoothDevice(address: String) = Result.success("ok")
    override suspend fun pairBluetoothDevice() = Result.success("ok")
    override suspend fun getBluetoothPairedDevices() = Result.success("ok")
    override suspend fun searchPlaces(query: String) = Result.success("ok")
    override suspend fun getDirections(from: String, to: String, mode: String) = Result.success("ok")
    override suspend fun openStreetView(latitude: Double, longitude: Double) = Result.success("ok")
    override suspend fun getNearbyPlaces(type: String, radius: Int) = Result.success("ok")
    override suspend fun saveAudioProfile(name: String) = Result.success("ok")
    override suspend fun loadAudioProfile(name: String) = Result.success("ok")
    override suspend fun listAudioProfiles() = Result.success("ok")
    override suspend fun deleteAudioProfile(name: String) = Result.success("ok")
    override suspend fun createHomeShortcut(name: String, uri: String) = Result.success("ok")
    override suspend fun pinAppShortcut(packageName: String) = Result.success("ok")
    override suspend fun openDocumentScanner() = Result.success("ok")
    override suspend fun discoverCastDevices() = Result.success("ok")
    override suspend fun castMedia(url: String) = Result.success("ok")
    override suspend fun completeReminder(id: String) = Result.success("ok")
    override suspend fun getAppPermissions(packageName: String) = Result.success("ok")
    override suspend fun getAppStorageInfo(packageName: String) = Result.success("ok")
    override suspend fun clearAppCache(packageName: String) = Result.success("ok")
    override suspend fun getAppNotificationSettings(packageName: String) = Result.success("ok")
    override suspend fun getAppBatteryUsage() = Result.success("ok")
    override suspend fun getDefaultApps() = Result.success("ok")
    override suspend fun setDefaultApp(role: String, packageName: String) = Result.success("ok")
    override suspend fun getRecentlyInstalledApps(days: Int) = Result.success("ok")
    override suspend fun getRunningApps() = Result.success("ok")
    override suspend fun killBackgroundApp(packageName: String) = Result.success("ok")
    override suspend fun findDuplicateFiles(directory: String) = Result.success("ok")
    override suspend fun findLargeFiles(directory: String, minSizeMb: Int) = Result.success("ok")
    override suspend fun findOldFiles(directory: String, olderThanDays: Int) = Result.success("ok")
    override suspend fun findScreenshots() = Result.success("ok")
    override suspend fun cleanupScreenshots(olderThanDays: Int) = Result.success("ok")
    override suspend fun suggestUnusedApps(days: Int) = Result.success("ok")
    override suspend fun applyDeviceMode(mode: String) = Result.success("ok")
    override suspend fun closeChromeTabs(filter: String) = Result.success("ok")
}
