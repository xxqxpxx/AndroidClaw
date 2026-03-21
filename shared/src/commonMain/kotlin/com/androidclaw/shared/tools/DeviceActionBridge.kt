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

    // Contacts
    suspend fun getContacts(query: String = "", limit: Int = 20): Result<String>
    suspend fun addContact(name: String, phone: String, email: String = ""): Result<String>
    suspend fun findContactByName(name: String): Result<String>

    // Calendar
    suspend fun getCalendarEvents(daysAhead: Int = 7): Result<String>
    suspend fun createCalendarEvent(title: String, startTimeMillis: Long, endTimeMillis: Long, description: String = ""): Result<String>

    // SMS
    suspend fun sendSms(phoneNumber: String, message: String): Result<String>
    suspend fun getRecentSms(count: Int = 10): Result<String>
    suspend fun getSmsFromContact(contactName: String, count: Int = 10): Result<String>

    // Phone
    suspend fun makeCall(phoneNumber: String): Result<String>
    suspend fun getCallLog(count: Int = 10): Result<String>

    // Location
    suspend fun getCurrentLocation(): Result<String>

    // Device Admin
    suspend fun lockScreen(): Result<String>
    suspend fun setCameraDisabled(disabled: Boolean): Result<String>
    suspend fun setMaxScreenLockTimeout(timeoutMs: Long): Result<String>
    suspend fun getDeviceAdminStatus(): Result<String>

    // Intent actions (for WhatsApp, etc.)
    suspend fun sendIntentMessage(packageName: String, phoneNumber: String, message: String): Result<String>
    suspend fun shareText(text: String, packageName: String? = null): Result<String>
}
