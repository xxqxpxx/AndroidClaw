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
    suspend fun navigateTo(destination: String, mode: String = ""): Result<String>
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

    // File management
    suspend fun listFiles(directory: String = "Downloads", sortBy: String = "date"): Result<String>
    suspend fun getFileInfo(filePath: String): Result<String>
    suspend fun deleteFile(filePath: String): Result<String>
    suspend fun moveFile(sourcePath: String, destDirectory: String): Result<String>
    suspend fun organizeFiles(directory: String = "Downloads"): Result<String>

    // Ride-hailing
    suspend fun orderRide(destination: String, service: String = "uber"): Result<String>

    // Music: actually start playback for a search query (not just open search)
    suspend fun playMusic(query: String, app: String = ""): Result<String>

    // Taps Confirm/Request on the currently-open ride app screen. Places a real paid ride;
    // callers must confirm with the user first. Requires the accessibility service.
    suspend fun confirmRideRequest(): Result<String>

    // Taps an on-screen button matching a label to finish an action a deep link only pre-filled
    // (Send/Post/Confirm/Pay). Callers must confirm with the user for paid/public/destructive taps.
    suspend fun tapScreenButton(label: String): Result<String>

    // Email reading (via notification capture)
    suspend fun getEmailNotifications(count: Int = 10): Result<String>

    // Generic intent / deep-link
    suspend fun openDeepLink(uri: String, packageName: String? = null, fallbackUrl: String? = null): Result<String>

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
    suspend fun sortChromeTabs(order: String = "alphabetical"): Result<String> // alphabetical, reverse_alphabetical

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

    // Media sharing
    suspend fun shareMedia(filePath: String, packageName: String? = null): Result<String>

    // --- NEW: Settings toggles ---
    suspend fun setAutoBrightness(enabled: Boolean): Result<String>
    suspend fun setLocationEnabled(enabled: Boolean): Result<String>
    suspend fun setAirplaneMode(enabled: Boolean): Result<String>
    suspend fun setHotspot(enabled: Boolean): Result<String>
    suspend fun setMobileData(enabled: Boolean): Result<String>
    suspend fun openNfcSettings(): Result<String>
    suspend fun getWifiInfo(): Result<String>
    suspend fun getVolumeInfo(): Result<String>

    // --- NEW: Alarm/Timer management ---
    suspend fun listAlarms(): Result<String>
    suspend fun cancelTimer(): Result<String>
    suspend fun getReminders(): Result<String>
    suspend fun deleteReminder(id: String): Result<String>

    // --- NEW: Contact management ---
    suspend fun editContact(name: String, newPhone: String, newEmail: String): Result<String>
    suspend fun deleteContact(name: String): Result<String>
    suspend fun getFavoriteContacts(): Result<String>

    // --- NEW: SMS management ---
    suspend fun searchSms(query: String, count: Int = 20): Result<String>
    suspend fun deleteSmsConversation(contactName: String): Result<String>

    // --- NEW: Calendar management ---
    suspend fun deleteCalendarEvent(eventId: String): Result<String>
    suspend fun searchCalendarEvents(query: String, daysAhead: Int = 30): Result<String>

    // --- NEW: Phone management ---
    suspend fun blockNumber(phoneNumber: String): Result<String>
    suspend fun checkVoicemail(): Result<String>

    // --- NEW: Notification interaction ---
    suspend fun replyToNotification(key: String, text: String): Result<String>

    // --- NEW: Screen time / usage ---
    suspend fun getScreenTime(days: Int = 1): Result<String>
    suspend fun getAppUsageStats(days: Int = 1): Result<String>
    suspend fun getBatteryUsageStats(): Result<String>

    // --- NEW: Accessibility extended ---
    suspend fun setTalkBack(enabled: Boolean): Result<String>
    suspend fun setDisplaySize(scale: String): Result<String> // small, default, large, largest
    suspend fun setHighContrast(enabled: Boolean): Result<String>
    suspend fun getAccessibilitySettings(): Result<String>

    // --- NEW: Ringtone/sound management ---
    suspend fun setRingtone(uri: String): Result<String>
    suspend fun setNotificationSound(uri: String): Result<String>

    // --- NEW: Power management ---
    suspend fun schedulePowerOff(hour: Int, minute: Int): Result<String>

    // --- NEW: Network diagnostics ---
    suspend fun getSignalStrength(): Result<String>
    suspend fun getConnectionType(): Result<String>
    suspend fun getIpAddress(): Result<String>
    suspend fun pingHost(host: String): Result<String>

    // --- NEW: Default apps ---
    suspend fun setDefaultBrowser(packageName: String): Result<String>
    suspend fun setDefaultLauncher(packageName: String): Result<String>

    // --- NEW: Focus modes ---
    suspend fun setDrivingMode(enabled: Boolean): Result<String>

    // --- System Diagnostics ---
    suspend fun getCpuInfo(): Result<String>
    suspend fun getSensorList(): Result<String>
    suspend fun getThermalInfo(): Result<String>
    suspend fun getProcessList(): Result<String>
    suspend fun getStorageBreakdown(): Result<String>

    // --- Text-to-Speech enhanced ---
    suspend fun ttsSpeak(text: String, language: String = "", speed: Float = 1.0f, pitch: Float = 1.0f): Result<String>
    suspend fun ttsStop(): Result<String>
    suspend fun ttsGetVoices(): Result<String>

    // --- DND granular ---
    suspend fun setDndMode(mode: String): Result<String> // priority, alarms, total_silence, off
    suspend fun getDndStatus(): Result<String>

    // --- Wi-Fi Management ---
    suspend fun scanWifiNetworks(): Result<String>
    suspend fun connectToWifi(ssid: String): Result<String>
    suspend fun getSavedWifiNetworks(): Result<String>
    suspend fun forgetWifiNetwork(ssid: String): Result<String>

    // --- Bluetooth Management ---
    suspend fun connectBluetoothDevice(address: String): Result<String>
    suspend fun disconnectBluetoothDevice(address: String): Result<String>
    suspend fun pairBluetoothDevice(): Result<String>
    suspend fun getBluetoothPairedDevices(): Result<String>

    // --- Navigation Enhanced ---
    suspend fun searchPlaces(query: String): Result<String>
    suspend fun getDirections(from: String, to: String, mode: String = "driving"): Result<String>
    suspend fun openStreetView(latitude: Double, longitude: Double): Result<String>
    suspend fun getNearbyPlaces(type: String, radius: Int = 1000): Result<String>

    // --- Audio Profiles ---
    suspend fun saveAudioProfile(name: String): Result<String>
    suspend fun loadAudioProfile(name: String): Result<String>
    suspend fun listAudioProfiles(): Result<String>
    suspend fun deleteAudioProfile(name: String): Result<String>

    // --- Shortcuts ---
    suspend fun createHomeShortcut(name: String, uri: String): Result<String>
    suspend fun pinAppShortcut(packageName: String): Result<String>

    // --- Document Scanner ---
    suspend fun openDocumentScanner(): Result<String>

    // --- Cast / Screen Mirror enhanced ---
    suspend fun discoverCastDevices(): Result<String>
    suspend fun castMedia(url: String): Result<String>

    // --- Reminders enhanced ---
    suspend fun completeReminder(id: String): Result<String>

    // --- App Management (usage, permissions, storage, battery, defaults, running) ---
    suspend fun getAppPermissions(packageName: String): Result<String>
    suspend fun getAppStorageInfo(packageName: String): Result<String>
    suspend fun clearAppCache(packageName: String): Result<String>
    suspend fun getAppNotificationSettings(packageName: String): Result<String>
    suspend fun getAppBatteryUsage(): Result<String>
    suspend fun getDefaultApps(): Result<String>
    suspend fun setDefaultApp(role: String, packageName: String): Result<String>
    suspend fun getRecentlyInstalledApps(days: Int = 30): Result<String>
    suspend fun getRunningApps(): Result<String>
    suspend fun killBackgroundApp(packageName: String): Result<String>

    // --- Task automation: cleanup & tidy ---
    suspend fun findDuplicateFiles(directory: String = "Downloads"): Result<String>
    suspend fun findLargeFiles(directory: String = "Downloads", minSizeMb: Int = 50): Result<String>
    suspend fun findOldFiles(directory: String = "Downloads", olderThanDays: Int = 90): Result<String>
    suspend fun findScreenshots(): Result<String>
    suspend fun cleanupScreenshots(olderThanDays: Int = 30): Result<String>
    suspend fun suggestUnusedApps(days: Int = 30): Result<String>
    suspend fun applyDeviceMode(mode: String): Result<String> // focus, sleep, battery_saver, outdoor, normal
    suspend fun closeChromeTabs(filter: String = "duplicates"): Result<String> // duplicates, all
    suspend fun getChromeTabs(): Result<String> // read open tab titles (e.g. to group/cluster by topic)

    // --- Task automation: photo intelligence, notifications, messaging, routines ---
    suspend fun findBlurryPhotos(limit: Int = 200): Result<String>
    suspend fun findSimilarPhotos(limit: Int = 200): Result<String>
    suspend fun cleanupPhotos(criteria: String = "blurry"): Result<String> // blurry
    suspend fun clearNotificationsFromApp(packageName: String): Result<String>
    suspend fun clearNotificationsByKeyword(keyword: String): Result<String>
    suspend fun deleteOldSms(olderThanDays: Int = 365): Result<String>
    suspend fun runCleanupRoutine(routine: String): Result<String> // storage, full
}
