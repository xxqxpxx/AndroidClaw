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
    suspend fun openUrl(url: String, packageName: String? = null): Result<String>

    // Media control
    suspend fun mediaPlayPause(): Result<String>
    suspend fun mediaNext(): Result<String>
    suspend fun mediaPrevious(): Result<String>
    suspend fun mediaStop(): Result<String>

    // System actions
    suspend fun setDoNotDisturb(enabled: Boolean): Result<String>
    suspend fun setAutoRotate(enabled: Boolean): Result<String>
    suspend fun takeScreenshot(): Result<String>
    suspend fun openSettings(settingsPage: String = ""): Result<String>
    suspend fun expandNotifications(): Result<String>
    suspend fun goHome(): Result<String>
    suspend fun goBack(): Result<String>
    suspend fun showRecents(): Result<String>
    suspend fun navigateTo(destination: String): Result<String>
    suspend fun sendEmail(to: String, subject: String, body: String): Result<String>

    // Ringer & audio modes
    suspend fun setRingerMode(mode: String): Result<String> // silent, vibrate, normal
    suspend fun setSpeakerphone(enabled: Boolean): Result<String>

    // Display & power
    suspend fun setScreenTimeout(seconds: Int): Result<String>
    suspend fun setBatterySaver(enabled: Boolean): Result<String>
    suspend fun setDarkMode(enabled: Boolean): Result<String>

    // Camera
    suspend fun openCamera(): Result<String>
    suspend fun takePhoto(): Result<String>

    // App management
    suspend fun uninstallApp(packageName: String): Result<String>
    suspend fun forceStopApp(packageName: String): Result<String>
    suspend fun getAppInfo(packageName: String): Result<String>

    // System UI
    suspend fun openQuickSettings(): Result<String>
    suspend fun openPowerMenu(): Result<String>
    suspend fun splitScreen(): Result<String>
    suspend fun lockOrientation(portrait: Boolean): Result<String>

    // Fun / utility
    suspend fun createNote(title: String, content: String): Result<String>
    suspend fun scanQrCode(): Result<String>

    // Device info extended
    suspend fun getBatteryInfo(): Result<String>
    suspend fun getStorageInfo(): Result<String>
    suspend fun getNetworkInfo(): Result<String>
    suspend fun getBluetoothDevices(): Result<String>

    // Ringer / sound
    suspend fun findMyPhone(): Result<String>  // ring at max volume
    suspend fun readAloud(text: String): Result<String>  // TTS

    // More system actions
    suspend fun openHotspotSettings(): Result<String>
    suspend fun openAirplaneSettings(): Result<String>
    suspend fun clearAllNotifications(): Result<String>
    suspend fun startStopwatch(): Result<String>
    suspend fun translateText(text: String, targetLang: String = ""): Result<String>
    suspend fun identifySong(): Result<String>
    suspend fun quickShare(text: String): Result<String>
    suspend fun openFileManager(): Result<String>
    suspend fun answerCall(): Result<String>
    suspend fun rejectCall(): Result<String>
    suspend fun setWallpaper(url: String): Result<String>
    suspend fun setFontSize(scale: String): Result<String> // small, default, large, largest
    suspend fun screenRecord(): Result<String>
    suspend fun restartDevice(): Result<String>

    // Fun / random
    suspend fun coinFlip(): Result<String>
    suspend fun rollDice(sides: Int = 6): Result<String>
    suspend fun randomNumber(min: Int, max: Int): Result<String>
    suspend fun countdownTo(date: String): Result<String>

    // Recording & media
    suspend fun startVoiceRecording(): Result<String>
    suspend fun openSpeedTest(): Result<String>
    suspend fun castScreen(): Result<String>
    suspend fun openIncognito(): Result<String>

    // Emergency & calls
    suspend fun emergencyCall(): Result<String>

    // Device info extended
    suspend fun getDataUsage(): Result<String>
    suspend fun getSimInfo(): Result<String>
    suspend fun getDeviceUptime(): Result<String>
    suspend fun getMemoryInfo(): Result<String>
    suspend fun checkForUpdate(): Result<String>

    // Display & accessibility
    suspend fun setNightLight(enabled: Boolean): Result<String>
    suspend fun setBedtimeMode(enabled: Boolean): Result<String>
    suspend fun pinApp(): Result<String>
    suspend fun flashlightSos(): Result<String>
    suspend fun setColorInversion(enabled: Boolean): Result<String>
    suspend fun setMagnification(enabled: Boolean): Result<String>

    // App & settings management
    suspend fun clearAppData(packageName: String): Result<String>
    suspend fun openDefaultApps(): Result<String>
    suspend fun openDigitalWellbeing(): Result<String>
    suspend fun openRingtoneSettings(): Result<String>
    suspend fun createReminder(text: String, timeMillis: Long): Result<String>
}
