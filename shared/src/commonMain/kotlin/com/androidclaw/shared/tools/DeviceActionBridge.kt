package com.androidclaw.shared.tools

/**
 * Platform bridge for device actions.
 * Android and iOS provide concrete implementations via Koin DI.
 */
interface DeviceActionBridge {
    // Device settings
    suspend fun setWifiEnabled(enabled: Boolean): Result<String>
    suspend fun setBluetoothEnabled(enabled: Boolean): Result<String>
    suspend fun setFlashlightEnabled(enabled: Boolean): Result<String>
    suspend fun setBrightness(level: Int): Result<String> // 0-100
    suspend fun setVolume(stream: String, level: Int): Result<String> // 0-100
    suspend fun getDeviceInfo(): Result<String>

    // App launcher
    suspend fun launchApp(packageName: String): Result<String>
    suspend fun listInstalledApps(): Result<String>
    suspend fun searchApps(query: String): Result<String>

    // Clipboard
    suspend fun setClipboard(text: String): Result<String>
    suspend fun getClipboard(): Result<String>

    // Notifications
    suspend fun getRecentNotifications(count: Int = 10): Result<String>
    suspend fun dismissNotification(key: String): Result<String>

    // Alarms & Reminders
    suspend fun setAlarm(hour: Int, minute: Int, label: String): Result<String>
    suspend fun setTimer(seconds: Int, label: String): Result<String>
}
