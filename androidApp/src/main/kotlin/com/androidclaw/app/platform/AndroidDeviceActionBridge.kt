package com.androidclaw.app.platform

import android.Manifest
import android.annotation.SuppressLint
import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import android.provider.Telephony
import android.telephony.SmsManager
import android.net.wifi.WifiManager
import android.util.Log
import android.view.KeyEvent
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.androidclaw.app.admin.ClawDeviceAdminReceiver
import com.androidclaw.app.service.AutoSendAccessibilityService
import com.androidclaw.shared.tools.DeviceActionBridge
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

class AndroidDeviceActionBridge(
    private val context: Context
) : DeviceActionBridge {

    companion object {
        private const val TAG = "DeviceBridge"

        private val APP_NAMES = mapOf(
            "com.whatsapp" to "WhatsApp",
            "org.telegram.messenger" to "Telegram",
            "org.thoughtcrime.securesms" to "Signal",
            "com.viber.voip" to "Viber",
            "com.facebook.orca" to "Messenger",
            "com.facebook.katana" to "Facebook",
            "com.instagram.android" to "Instagram",
            "com.snapchat.android" to "Snapchat",
            "com.twitter.android" to "X/Twitter",
            "com.discord" to "Discord",
            "com.Slack" to "Slack",
            "com.microsoft.teams" to "Teams",
            "com.spotify.music" to "Spotify",
            "com.google.android.youtube" to "YouTube",
            "com.google.android.apps.maps" to "Google Maps",
            "com.google.android.gm" to "Gmail",
        )
    }

    private fun logAction(action: String, details: String = "") {
        Log.i(TAG, "$action${if (details.isNotEmpty()) " | $details" else ""}")
    }

    private fun logResult(action: String, result: String) {
        Log.d(TAG, "$action -> ${result.take(200)}")
    }

    private fun logError(action: String, error: Throwable) {
        Log.e(TAG, "$action FAILED: ${error::class.simpleName}: ${error.message}", error)
        try {
            FirebaseCrashlytics.getInstance().apply {
                log("$TAG: $action failed")
                recordException(error)
            }
        } catch (_: Exception) { /* Crashlytics not initialized */ }
    }

    // -- Wi-Fi --
    override suspend fun setWifiEnabled(enabled: Boolean): Result<String> {
        logAction("setWifiEnabled", "enabled=$enabled")
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val intent = Intent(Settings.Panel.ACTION_WIFI).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opened Wi-Fi settings panel. Please toggle Wi-Fi ${if (enabled) "on" else "off"}."
            } else {
                @Suppress("DEPRECATION")
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = enabled
                "Wi-Fi ${if (enabled) "enabled" else "disabled"}"
            }
        }.also { r ->
            r.onSuccess { logResult("setWifiEnabled", it) }
            r.onFailure { logError("setWifiEnabled", it) }
        }
    }

    // -- Bluetooth --
    override suspend fun setBluetoothEnabled(enabled: Boolean): Result<String> {
        logAction("setBluetoothEnabled", "enabled=$enabled")
        return runCatching {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened Bluetooth settings. Please toggle Bluetooth ${if (enabled) "on" else "off"}."
        }.also { r ->
            r.onSuccess { logResult("setBluetoothEnabled", it) }
            r.onFailure { logError("setBluetoothEnabled", it) }
        }
    }

    // -- Flashlight --
    override suspend fun setFlashlightEnabled(enabled: Boolean): Result<String> {
        logAction("setFlashlightEnabled", "enabled=$enabled")
        return runCatching {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull()
                ?: return@runCatching "No camera available for flashlight"
            cameraManager.setTorchMode(cameraId, enabled)
            "Flashlight ${if (enabled) "on" else "off"}"
        }.also { r ->
            r.onSuccess { logResult("setFlashlightEnabled", it) }
            r.onFailure { logError("setFlashlightEnabled", it) }
        }
    }

    // -- Brightness --
    override suspend fun setBrightness(level: Int): Result<String> {
        logAction("setBrightness", "level=$level")
        return runCatching {
            val brightnessValue = (level * 255) / 100
            if (Settings.System.canWrite(context)) {
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightnessValue)
                "Brightness set to $level%"
            } else {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Please grant write settings permission, then try again."
            }
        }.also { r ->
            r.onSuccess { logResult("setBrightness", it) }
            r.onFailure { logError("setBrightness", it) }
        }
    }

    // -- Volume --
    override suspend fun setVolume(stream: String, level: Int): Result<String> {
        logAction("setVolume", "stream=$stream, level=$level")
        return runCatching {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val streamType = when (stream) {
                "media" -> AudioManager.STREAM_MUSIC
                "ring" -> AudioManager.STREAM_RING
                "alarm" -> AudioManager.STREAM_ALARM
                "notification" -> AudioManager.STREAM_NOTIFICATION
                else -> AudioManager.STREAM_MUSIC
            }
            val maxVolume = audioManager.getStreamMaxVolume(streamType)
            val targetVolume = (level * maxVolume) / 100
            audioManager.setStreamVolume(streamType, targetVolume, 0)
            "Set $stream volume to $level% ($targetVolume/$maxVolume)"
        }.also { r ->
            r.onSuccess { logResult("setVolume", it) }
            r.onFailure { logError("setVolume", it) }
        }
    }

    // -- Device Info --
    override suspend fun getDeviceInfo(): Result<String> {
        logAction("getDeviceInfo")
        return runCatching {
            buildString {
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                appendLine("Product: ${Build.PRODUCT}")

                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val mediaVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val mediaMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                appendLine("Media volume: $mediaVol/$mediaMax")

                try {
                    val batteryIntent = context.registerReceiver(null,
                        android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
                    if (level >= 0 && scale > 0) {
                        appendLine("Battery: ${(level * 100) / scale}%")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get battery info", e)
                }
            }.trim()
        }.also { r ->
            r.onSuccess { logResult("getDeviceInfo", it) }
            r.onFailure { logError("getDeviceInfo", it) }
        }
    }

    // -- App Launcher --
    override suspend fun launchApp(packageName: String): Result<String> {
        logAction("launchApp", "package=$packageName")
        return runCatching {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return@runCatching "App not found: $packageName"
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Launched $packageName"
        }.also { r ->
            r.onSuccess { logResult("launchApp", it) }
            r.onFailure { logError("launchApp", it) }
        }
    }

    override suspend fun listInstalledApps(): Result<String> = withContext(Dispatchers.IO) {
        logAction("listInstalledApps")
        runCatching {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
                .take(50)
            buildString {
                appendLine("Installed apps (${apps.size} launchable):")
                apps.forEach { app ->
                    val label = pm.getApplicationLabel(app)
                    appendLine("- $label (${app.packageName})")
                }
            }.trim()
        }.also { r ->
            r.onSuccess { logResult("listInstalledApps", "${it.lines().size} lines") }
            r.onFailure { logError("listInstalledApps", it) }
        }
    }

    override suspend fun searchApps(query: String): Result<String> = withContext(Dispatchers.IO) {
        logAction("searchApps", "query=$query")
        runCatching {
            val pm = context.packageManager
            val q = query.lowercase()
            val matches = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .filter {
                    val label = pm.getApplicationLabel(it).toString().lowercase()
                    label.contains(q) || it.packageName.lowercase().contains(q)
                }
                .take(10)
            if (matches.isEmpty()) {
                "No apps found matching \"$query\""
            } else {
                buildString {
                    appendLine("Apps matching \"$query\":")
                    matches.forEach { app ->
                        appendLine("- ${pm.getApplicationLabel(app)} (${app.packageName})")
                    }
                }.trim()
            }
        }.also { r ->
            r.onSuccess { logResult("searchApps", it) }
            r.onFailure { logError("searchApps", it) }
        }
    }

    // -- Clipboard --
    override suspend fun setClipboard(text: String): Result<String> = withContext(Dispatchers.Main) {
        logAction("setClipboard", "length=${text.length}")
        runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("AndroidClaw", text))
            "Copied to clipboard: ${text.take(100)}${if (text.length > 100) "..." else ""}"
        }.also { r ->
            r.onSuccess { logResult("setClipboard", it) }
            r.onFailure { logError("setClipboard", it) }
        }
    }

    override suspend fun getClipboard(): Result<String> = withContext(Dispatchers.Main) {
        logAction("getClipboard")
        runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).coerceToText(context).toString()
                "Clipboard contents: $text"
            } else {
                "Clipboard is empty"
            }
        }.also { r ->
            r.onSuccess { logResult("getClipboard", it) }
            r.onFailure { logError("getClipboard", it) }
        }
    }

    // -- Notifications --
    override suspend fun getRecentNotifications(count: Int): Result<String> {
        logAction("getRecentNotifications", "count=$count")
        return runCatching {
            if (!com.androidclaw.app.service.ClawNotificationListenerService.isConnected) {
                return@runCatching "Notification access is not enabled. Please go to Settings > Apps > Special access > Notification access and enable AndroidClaw."
            }
            val notifications = com.androidclaw.app.service.ClawNotificationListenerService.getRecent(count)
            if (notifications.isEmpty()) {
                "No recent notifications."
            } else {
                val lines = notifications.mapIndexed { i, n -> "${i + 1}. ${n.toReadableString()}" }
                "Recent notifications (${notifications.size}):\n${lines.joinToString("\n")}"
            }
        }
    }

    override suspend fun dismissNotification(key: String): Result<String> {
        logAction("dismissNotification", "key=$key")
        return runCatching {
            val service = com.androidclaw.app.service.ClawNotificationListenerService.instance
                ?: return@runCatching "Notification listener service is not active. Enable it in Settings > Apps > Special access > Notification access."
            if (key == "all") {
                service.dismissAll()
                "All notifications dismissed."
            } else {
                val success = service.dismissByKey(key)
                if (success) "Notification dismissed: $key" else "Failed to dismiss notification: $key"
            }
        }
    }

    // -- Alarms & Timers --
    override suspend fun setAlarm(hour: Int, minute: Int, label: String): Result<String> {
        logAction("setAlarm", "hour=$hour, minute=$minute, label=$label")
        return runCatching {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                if (label.isNotEmpty()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // Force Google Clock to avoid third-party apps intercepting
                val clockPackages = listOf(
                    "com.google.android.deskclock",
                    "com.android.deskclock",
                    "com.sec.android.app.clockpackage"
                )
                for (pkg in clockPackages) {
                    try {
                        context.packageManager.getPackageInfo(pkg, 0)
                        setPackage(pkg)
                        break
                    } catch (_: PackageManager.NameNotFoundException) {}
                }
            }
            context.startActivity(intent)
            val timeStr = String.format("%02d:%02d", hour, minute)
            "Alarm set for $timeStr${if (label.isNotEmpty()) " - $label" else ""}"
        }.also { r ->
            r.onSuccess { logResult("setAlarm", it) }
            r.onFailure { logError("setAlarm", it) }
        }
    }

    override suspend fun setTimer(seconds: Int, label: String): Result<String> {
        logAction("setTimer", "seconds=$seconds, label=$label")
        return runCatching {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                if (label.isNotEmpty()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val clockPackages = listOf(
                    "com.google.android.deskclock",
                    "com.android.deskclock",
                    "com.sec.android.app.clockpackage"
                )
                for (pkg in clockPackages) {
                    try {
                        context.packageManager.getPackageInfo(pkg, 0)
                        setPackage(pkg)
                        break
                    } catch (_: PackageManager.NameNotFoundException) {}
                }
            }
            context.startActivity(intent)
            val mins = seconds / 60
            val secs = seconds % 60
            val timeStr = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
            "Timer set for $timeStr${if (label.isNotEmpty()) " - $label" else ""}"
        }.also { r ->
            r.onSuccess { logResult("setTimer", it) }
            r.onFailure { logError("setTimer", it) }
        }
    }

    // ==========================================
    // Contacts
    // ==========================================

    override suspend fun getContacts(query: String, limit: Int): Result<String> = withContext(Dispatchers.IO) {
        logAction("getContacts", "query=$query, limit=$limit")
        runCatching {
            if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
                return@runCatching "Contacts permission not granted. Please grant contacts access in app settings."
            }

            val contacts = mutableListOf<String>()
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val selection = if (query.isNotEmpty()) {
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            } else null
            val selectionArgs = if (query.isNotEmpty()) arrayOf("%$query%") else null

            context.contentResolver.query(uri, projection, selection, selectionArgs,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC")?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val phoneIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    val name = cursor.getString(nameIdx) ?: "Unknown"
                    val phone = cursor.getString(phoneIdx) ?: ""
                    contacts.add("- $name: $phone")
                    count++
                }
            }

            if (contacts.isEmpty()) {
                "No contacts found${if (query.isNotEmpty()) " matching \"$query\"" else ""}"
            } else {
                buildString {
                    appendLine("Contacts (${contacts.size}):")
                    contacts.forEach { appendLine(it) }
                }.trim()
            }
        }.also { r ->
            r.onSuccess { logResult("getContacts", it) }
            r.onFailure { logError("getContacts", it) }
        }
    }

    override suspend fun addContact(name: String, phone: String, email: String): Result<String> = withContext(Dispatchers.IO) {
        logAction("addContact", "name=$name, phone=$phone")
        runCatching {
            if (!hasPermission(Manifest.permission.WRITE_CONTACTS)) {
                return@runCatching "Contacts write permission not granted. Please grant contacts access in app settings."
            }

            val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
                type = ContactsContract.RawContacts.CONTENT_TYPE
                putExtra(ContactsContract.Intents.Insert.NAME, name)
                putExtra(ContactsContract.Intents.Insert.PHONE, phone)
                if (email.isNotEmpty()) {
                    putExtra(ContactsContract.Intents.Insert.EMAIL, email)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening contact creation for $name ($phone)${if (email.isNotEmpty()) " - $email" else ""}"
        }.also { r ->
            r.onSuccess { logResult("addContact", it) }
            r.onFailure { logError("addContact", it) }
        }
    }

    override suspend fun findContactByName(name: String): Result<String> {
        logAction("findContactByName", "name=$name")
        return getContacts(name, 10)
    }

    // ==========================================
    // Calendar
    // ==========================================

    @SuppressLint("MissingPermission")
    override suspend fun getCalendarEvents(daysAhead: Int): Result<String> = withContext(Dispatchers.IO) {
        logAction("getCalendarEvents", "daysAhead=$daysAhead")
        runCatching {
            if (!hasPermission(Manifest.permission.READ_CALENDAR)) {
                return@runCatching "Calendar permission not granted. Please grant calendar access in app settings."
            }

            val events = mutableListOf<String>()
            val now = System.currentTimeMillis()
            val end = now + daysAhead * 24L * 60 * 60 * 1000

            val projection = arrayOf(
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DESCRIPTION
            )

            val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
            val selectionArgs = arrayOf(now.toString(), end.toString())

            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI, projection, selection, selectionArgs,
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { cursor ->
                val titleIdx = cursor.getColumnIndex(CalendarContract.Events.TITLE)
                val startIdx = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
                val endIdx = cursor.getColumnIndex(CalendarContract.Events.DTEND)
                val locIdx = cursor.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)

                var count = 0
                while (cursor.moveToNext() && count < 20) {
                    val title = cursor.getString(titleIdx) ?: "Untitled"
                    val start = cursor.getLong(startIdx)
                    val endTime = cursor.getLong(endIdx)
                    val location = cursor.getString(locIdx) ?: ""

                    val startDate = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(start))
                    val endDate = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(endTime))

                    val entry = buildString {
                        append("- $title ($startDate - $endDate)")
                        if (location.isNotEmpty()) append(" @ $location")
                    }
                    events.add(entry)
                    count++
                }
            }

            if (events.isEmpty()) {
                "No upcoming events in the next $daysAhead days"
            } else {
                buildString {
                    appendLine("Upcoming events (next $daysAhead days):")
                    events.forEach { appendLine(it) }
                }.trim()
            }
        }.also { r ->
            r.onSuccess { logResult("getCalendarEvents", it) }
            r.onFailure { logError("getCalendarEvents", it) }
        }
    }

    override suspend fun createCalendarEvent(
        title: String, startTimeMillis: Long, endTimeMillis: Long, description: String
    ): Result<String> {
        logAction("createCalendarEvent", "title=$title, start=$startTimeMillis, end=$endTimeMillis")
        return runCatching {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTimeMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTimeMillis)
                if (description.isNotEmpty()) {
                    putExtra(CalendarContract.Events.DESCRIPTION, description)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                trySetPackage("com.google.android.calendar", "com.android.calendar", "com.samsung.android.calendar")
            }
            context.startActivity(intent)
            "Opening calendar to create event: $title"
        }.also { r ->
            r.onSuccess { logResult("createCalendarEvent", it) }
            r.onFailure { logError("createCalendarEvent", it) }
        }
    }

    // ==========================================
    // SMS
    // ==========================================

    override suspend fun sendSms(phoneNumber: String, message: String): Result<String> {
        logAction("sendSms", "to=$phoneNumber, msgLen=${message.length}")
        return runCatching {
            if (!hasPermission(Manifest.permission.SEND_SMS)) {
                return@runCatching "SMS permission not granted. Please grant SMS access in app settings."
            }

            @Suppress("DEPRECATION")
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(message)
            if (parts.size == 1) {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            } else {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            }
            "SMS sent to $phoneNumber: ${message.take(50)}${if (message.length > 50) "..." else ""}"
        }.also { r ->
            r.onSuccess { logResult("sendSms", it) }
            r.onFailure { logError("sendSms", it) }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun getRecentSms(count: Int): Result<String> = withContext(Dispatchers.IO) {
        logAction("getRecentSms", "count=$count")
        runCatching {
            if (!hasPermission(Manifest.permission.READ_SMS)) {
                return@runCatching "SMS read permission not granted. Please grant SMS access in app settings."
            }

            val messages = mutableListOf<String>()
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
                null, null, "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val addrIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                val typeIdx = cursor.getColumnIndex(Telephony.Sms.TYPE)

                var c = 0
                while (cursor.moveToNext() && c < count) {
                    val addr = cursor.getString(addrIdx) ?: "Unknown"
                    val body = cursor.getString(bodyIdx) ?: ""
                    val date = cursor.getLong(dateIdx)
                    val type = cursor.getInt(typeIdx)
                    val direction = if (type == Telephony.Sms.MESSAGE_TYPE_INBOX) "From" else "To"

                    val dateStr = java.text.SimpleDateFormat("MMM dd HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(date))
                    messages.add("- [$dateStr] $direction $addr: ${body.take(100)}")
                    c++
                }
            }

            if (messages.isEmpty()) "No SMS messages found"
            else buildString {
                appendLine("Recent messages:")
                messages.forEach { appendLine(it) }
            }.trim()
        }.also { r ->
            r.onSuccess { logResult("getRecentSms", "${it.lines().size} lines") }
            r.onFailure { logError("getRecentSms", it) }
        }
    }

    override suspend fun getSmsFromContact(contactName: String, count: Int): Result<String> = withContext(Dispatchers.IO) {
        logAction("getSmsFromContact", "contact=$contactName, count=$count")
        runCatching {
            if (!hasPermission(Manifest.permission.READ_SMS) || !hasPermission(Manifest.permission.READ_CONTACTS)) {
                return@runCatching "SMS and contacts permissions required."
            }

            val phones = mutableListOf<String>()
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$contactName%"), null
            )?.use { cursor ->
                val phoneIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    cursor.getString(phoneIdx)?.let { phones.add(it.replace("\\s".toRegex(), "")) }
                }
            }

            if (phones.isEmpty()) {
                return@runCatching "No contact found matching \"$contactName\""
            }
            Log.d(TAG, "getSmsFromContact: found ${phones.size} phone numbers for $contactName")

            val messages = mutableListOf<String>()
            for (phone in phones.take(3)) {
                context.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
                    "${Telephony.Sms.ADDRESS} LIKE ?",
                    arrayOf("%${phone.takeLast(7)}%"),
                    "${Telephony.Sms.DATE} DESC"
                )?.use { cursor ->
                    val addrIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                    val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                    val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                    val typeIdx = cursor.getColumnIndex(Telephony.Sms.TYPE)

                    var c = 0
                    while (cursor.moveToNext() && c < count) {
                        val body = cursor.getString(bodyIdx) ?: ""
                        val date = cursor.getLong(dateIdx)
                        val type = cursor.getInt(typeIdx)
                        val direction = if (type == Telephony.Sms.MESSAGE_TYPE_INBOX) "Received" else "Sent"

                        val dateStr = java.text.SimpleDateFormat("MMM dd HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(date))
                        messages.add("- [$dateStr] $direction: ${body.take(150)}")
                        c++
                    }
                }
            }

            if (messages.isEmpty()) "No messages found from $contactName"
            else buildString {
                appendLine("Messages with $contactName:")
                messages.forEach { appendLine(it) }
            }.trim()
        }.also { r ->
            r.onSuccess { logResult("getSmsFromContact", it) }
            r.onFailure { logError("getSmsFromContact", it) }
        }
    }

    // ==========================================
    // Phone / Calls
    // ==========================================

    override suspend fun makeCall(phoneNumber: String): Result<String> {
        logAction("makeCall", "to=$phoneNumber")
        return runCatching {
            if (!hasPermission(Manifest.permission.CALL_PHONE)) {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    trySetPackage("com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer")
                }
                context.startActivity(intent)
                return@runCatching "Opened dialer for $phoneNumber (call permission not granted for direct calling)"
            }

            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                trySetPackage("com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer")
            }
            context.startActivity(intent)
            "Calling $phoneNumber"
        }.also { r ->
            r.onSuccess { logResult("makeCall", it) }
            r.onFailure { logError("makeCall", it) }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun getCallLog(count: Int): Result<String> = withContext(Dispatchers.IO) {
        logAction("getCallLog", "count=$count")
        runCatching {
            if (!hasPermission(Manifest.permission.READ_CALL_LOG)) {
                return@runCatching "Call log permission not granted. Please grant phone access in app settings."
            }

            val entries = mutableListOf<String>()
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE,
                    CallLog.Calls.DATE, CallLog.Calls.DURATION),
                null, null, "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)

                var c = 0
                while (cursor.moveToNext() && c < count) {
                    val number = cursor.getString(numIdx) ?: "Unknown"
                    val name = cursor.getString(nameIdx) ?: number
                    val type = cursor.getInt(typeIdx)
                    val date = cursor.getLong(dateIdx)
                    val duration = cursor.getLong(durIdx)

                    val callType = when (type) {
                        CallLog.Calls.INCOMING_TYPE -> "Incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                        CallLog.Calls.MISSED_TYPE -> "Missed"
                        CallLog.Calls.REJECTED_TYPE -> "Rejected"
                        else -> "Other"
                    }

                    val dateStr = java.text.SimpleDateFormat("MMM dd HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(date))
                    val durStr = if (duration > 0) " (${duration}s)" else ""

                    entries.add("- [$dateStr] $callType: $name ($number)$durStr")
                    c++
                }
            }

            if (entries.isEmpty()) "No call history found"
            else buildString {
                appendLine("Recent calls:")
                entries.forEach { appendLine(it) }
            }.trim()
        }.also { r ->
            r.onSuccess { logResult("getCallLog", "${it.lines().size} lines") }
            r.onFailure { logError("getCallLog", it) }
        }
    }

    // ==========================================
    // Location
    // ==========================================

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(): Result<String> = withContext(Dispatchers.IO) {
        logAction("getCurrentLocation")
        runCatching {
            if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) &&
                !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                return@runCatching "Location permission not granted. Please grant location access in app settings."
            }

            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )

            var bestLocation: android.location.Location? = null
            for (provider in providers) {
                try {
                    val loc = locationManager.getLastKnownLocation(provider)
                    if (loc != null && (bestLocation == null || loc.time > bestLocation!!.time)) {
                        bestLocation = loc
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get location from $provider", e)
                }
            }

            if (bestLocation != null) {
                buildString {
                    appendLine("Current location:")
                    appendLine("  Latitude: ${bestLocation.latitude}")
                    appendLine("  Longitude: ${bestLocation.longitude}")
                    appendLine("  Accuracy: ${bestLocation.accuracy}m")
                    if (bestLocation.altitude != 0.0) {
                        appendLine("  Altitude: ${bestLocation.altitude}m")
                    }
                    val age = (System.currentTimeMillis() - bestLocation.time) / 1000
                    appendLine("  Age: ${age}s ago")
                    appendLine("  Provider: ${bestLocation.provider}")
                }.trim()
            } else {
                "Unable to get current location. GPS may be disabled or no recent location available."
            }
        }.also { r ->
            r.onSuccess { logResult("getCurrentLocation", it) }
            r.onFailure { logError("getCurrentLocation", it) }
        }
    }

    // ==========================================
    // Device Admin
    // ==========================================

    override suspend fun lockScreen(): Result<String> {
        logAction("lockScreen")
        return runCatching {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ClawDeviceAdminReceiver.getComponentName(context)
            if (!dpm.isAdminActive(adminComponent)) {
                return@runCatching "Device admin not active. Please enable it in Settings > Security > Device Admin."
            }
            dpm.lockNow()
            "Screen locked successfully"
        }.also { r ->
            r.onSuccess { logResult("lockScreen", it) }
            r.onFailure { logError("lockScreen", it) }
        }
    }

    override suspend fun setCameraDisabled(disabled: Boolean): Result<String> {
        logAction("setCameraDisabled", "disabled=$disabled")
        return runCatching {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ClawDeviceAdminReceiver.getComponentName(context)
            if (!dpm.isAdminActive(adminComponent)) {
                return@runCatching "Device admin not active. Please enable it in Settings > Security > Device Admin."
            }
            dpm.setCameraDisabled(adminComponent, disabled)
            "Camera ${if (disabled) "disabled" else "enabled"}"
        }.also { r ->
            r.onSuccess { logResult("setCameraDisabled", it) }
            r.onFailure { logError("setCameraDisabled", it) }
        }
    }

    override suspend fun setMaxScreenLockTimeout(timeoutMs: Long): Result<String> {
        logAction("setMaxScreenLockTimeout", "timeoutMs=$timeoutMs")
        return runCatching {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ClawDeviceAdminReceiver.getComponentName(context)
            if (!dpm.isAdminActive(adminComponent)) {
                return@runCatching "Device admin not active. Please enable it in Settings > Security > Device Admin."
            }
            dpm.setMaximumTimeToLock(adminComponent, timeoutMs)
            "Max screen lock timeout set to ${timeoutMs / 1000} seconds"
        }.also { r ->
            r.onSuccess { logResult("setMaxScreenLockTimeout", it) }
            r.onFailure { logError("setMaxScreenLockTimeout", it) }
        }
    }

    override suspend fun getDeviceAdminStatus(): Result<String> {
        logAction("getDeviceAdminStatus")
        return runCatching {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ClawDeviceAdminReceiver.getComponentName(context)
            val isActive = dpm.isAdminActive(adminComponent)
            buildString {
                appendLine("Device Admin Status:")
                appendLine("  Active: $isActive")
                if (isActive) {
                    appendLine("  Component: $adminComponent")
                    try {
                        val maxTime = dpm.getMaximumTimeToLock(adminComponent)
                        if (maxTime > 0) appendLine("  Max lock timeout: ${maxTime / 1000}s")
                    } catch (_: Exception) {}
                }
            }.trim()
        }.also { r ->
            r.onSuccess { logResult("getDeviceAdminStatus", it) }
            r.onFailure { logError("getDeviceAdminStatus", it) }
        }
    }

    // ==========================================
    // Intent Messaging (WhatsApp, Telegram, etc.)
    // ==========================================

    override suspend fun sendIntentMessage(packageName: String, phoneNumber: String, message: String): Result<String> {
        logAction("sendIntentMessage", "pkg=$packageName, to=$phoneNumber, msgLen=${message.length}")
        return runCatching {
            try {
                context.packageManager.getPackageInfo(packageName, 0)
            } catch (_: PackageManager.NameNotFoundException) {
                val appName = when (packageName) {
                    "com.whatsapp" -> "WhatsApp"
                    "org.telegram.messenger" -> "Telegram"
                    else -> packageName
                }
                return@runCatching "$appName is not installed on this device"
            }

            val appName = APP_NAMES[packageName] ?: packageName
            val autoSendEnabled = AutoSendAccessibilityService.isEnabled()

            when (packageName) {
                "com.whatsapp" -> {
                    val phone = phoneNumber.replace("+", "").replace(" ", "").replace("-", "")
                    val uri = Uri.parse("https://wa.me/$phone?text=${Uri.encode(message)}")
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage("com.whatsapp")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (autoSendEnabled) {
                        AutoSendAccessibilityService.requestAutoSend("com.whatsapp")
                    }
                    context.startActivity(intent)
                    if (autoSendEnabled) "Sending WhatsApp message to $phoneNumber"
                    else "Opened WhatsApp chat with $phoneNumber. Please tap send (enable Accessibility Service for auto-send)."
                }
                "org.telegram.messenger" -> {
                    val phone = phoneNumber.replace("+", "").replace(" ", "").replace("-", "")
                    if (autoSendEnabled) {
                        AutoSendAccessibilityService.requestAutoSend("org.telegram.messenger")
                    }
                    if (phone.isNotEmpty()) {
                        val uri = Uri.parse("tg://resolve?phone=$phone&text=${Uri.encode(message)}")
                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } else {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            setPackage("org.telegram.messenger")
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, message)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                    if (autoSendEnabled) "Sending Telegram message"
                    else "Opened Telegram. Please tap send (enable Accessibility Service for auto-send)."
                }
                "org.thoughtcrime.securesms" -> {
                    if (autoSendEnabled) {
                        AutoSendAccessibilityService.requestAutoSend("org.thoughtcrime.securesms")
                    }
                    if (phoneNumber.isNotEmpty()) {
                        val uri = Uri.parse("sgnl://signal.me/?p=${Uri.encode(phoneNumber)}")
                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } else {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            setPackage(packageName)
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, message)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                    if (autoSendEnabled) "Sending Signal message"
                    else "Opened Signal. Please tap send (enable Accessibility Service for auto-send)."
                }
                "com.spotify.music" -> {
                    // Spotify search/play
                    val searchUri = Uri.parse("spotify:search:${Uri.encode(message)}")
                    val intent = Intent(Intent.ACTION_VIEW, searchUri).apply {
                        setPackage("com.spotify.music")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    "Searching Spotify for: $message"
                }
                "com.google.android.youtube" -> {
                    // YouTube search
                    val searchUri = Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(message)}")
                    val intent = Intent(Intent.ACTION_VIEW, searchUri).apply {
                        setPackage("com.google.android.youtube")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    "Searching YouTube for: $message"
                }
                "com.google.android.apps.maps" -> {
                    // Google Maps search
                    val uri = Uri.parse("geo:0,0?q=${Uri.encode(message)}")
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage("com.google.android.apps.maps")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    "Searching Maps for: $message"
                }
                else -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        setPackage(packageName)
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, message)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    "Opening $appName to share message"
                }
            }
        }.also { r ->
            r.onSuccess { logResult("sendIntentMessage", it) }
            r.onFailure { logError("sendIntentMessage", it) }
        }
    }

    override suspend fun shareText(text: String, packageName: String?): Result<String> {
        logAction("shareText", "textLen=${text.length}, pkg=$packageName")
        return runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                packageName?.let { setPackage(it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (packageName != null) {
                context.startActivity(intent)
                "Sharing text to $packageName"
            } else {
                val chooser = Intent.createChooser(intent, "Share via").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
                "Opening share dialog"
            }
        }.also { r ->
            r.onSuccess { logResult("shareText", it) }
            r.onFailure { logError("shareText", it) }
        }
    }

    // ==========================================
    // Open URL
    // ==========================================

    override suspend fun shareMedia(filePath: String, packageName: String?): Result<String> {
        logAction("shareMedia", "path=$filePath, pkg=$packageName")
        return runCatching {
            val file = File(filePath)
            if (!file.exists()) throw IllegalArgumentException("File not found: $filePath")

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val ext = MimeTypeMap.getFileExtensionFromUrl(filePath)
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                packageName?.let { setPackage(it) }
            }

            if (packageName != null) {
                context.startActivity(intent)
                "Sharing file to $packageName"
            } else {
                val chooser = Intent.createChooser(intent, "Share file").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
                "Opening share dialog for file"
            }
        }.also { r ->
            r.onSuccess { logResult("shareMedia", it) }
            r.onFailure { logError("shareMedia", it) }
        }
    }

    override suspend fun openUrl(url: String, packageName: String?): Result<String> {
        logAction("openUrl", "url=$url, pkg=$packageName")
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                packageName?.let { setPackage(it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened $url${if (packageName != null) " in $packageName" else ""}"
        }.also { r ->
            r.onSuccess { logResult("openUrl", it) }
            r.onFailure { logError("openUrl", it) }
        }
    }

    // ==========================================
    // Media Control
    // ==========================================

    override suspend fun mediaPlayPause(): Result<String> {
        logAction("mediaPlayPause")
        return runCatching {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            audioManager.dispatchMediaKeyEvent(event)
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            if (audioManager.isMusicActive) "Media paused" else "Media playing"
        }.also { r ->
            r.onSuccess { logResult("mediaPlayPause", it) }
            r.onFailure { logError("mediaPlayPause", it) }
        }
    }

    override suspend fun mediaNext(): Result<String> {
        logAction("mediaNext")
        return runCatching {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT))
            "Skipped to next track"
        }.also { r ->
            r.onSuccess { logResult("mediaNext", it) }
            r.onFailure { logError("mediaNext", it) }
        }
    }

    override suspend fun mediaPrevious(): Result<String> {
        logAction("mediaPrevious")
        return runCatching {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
            "Skipped to previous track"
        }.also { r ->
            r.onSuccess { logResult("mediaPrevious", it) }
            r.onFailure { logError("mediaPrevious", it) }
        }
    }

    override suspend fun mediaStop(): Result<String> {
        logAction("mediaStop")
        return runCatching {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_STOP))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_STOP))
            "Media stopped"
        }.also { r ->
            r.onSuccess { logResult("mediaStop", it) }
            r.onFailure { logError("mediaStop", it) }
        }
    }

    // ==========================================
    // System Actions
    // ==========================================

    override suspend fun setDoNotDisturb(enabled: Boolean): Result<String> {
        logAction("setDoNotDisturb", "enabled=$enabled")
        return runCatching {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!notificationManager.isNotificationPolicyAccessGranted) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return@runCatching "Please grant Do Not Disturb access to AndroidClaw"
            }
            notificationManager.setInterruptionFilter(
                if (enabled) NotificationManager.INTERRUPTION_FILTER_NONE
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
            "Do Not Disturb ${if (enabled) "enabled" else "disabled"}"
        }.also { r ->
            r.onSuccess { logResult("setDoNotDisturb", it) }
            r.onFailure { logError("setDoNotDisturb", it) }
        }
    }

    override suspend fun setAutoRotate(enabled: Boolean): Result<String> {
        logAction("setAutoRotate", "enabled=$enabled")
        return runCatching {
            if (Settings.System.canWrite(context)) {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.ACCELEROMETER_ROTATION,
                    if (enabled) 1 else 0
                )
                "Auto-rotate ${if (enabled) "enabled" else "disabled"}"
            } else {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Please grant write settings permission"
            }
        }.also { r ->
            r.onSuccess { logResult("setAutoRotate", it) }
            r.onFailure { logError("setAutoRotate", it) }
        }
    }

    override suspend fun takeScreenshot(): Result<String> {
        logAction("takeScreenshot")
        return runCatching {
            val service = AutoSendAccessibilityService.instance
            if (service != null) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
                "Screenshot taken"
            } else {
                "Accessibility service not enabled. Please enable AndroidClaw in Settings > Accessibility."
            }
        }.also { r ->
            r.onSuccess { logResult("takeScreenshot", it) }
            r.onFailure { logError("takeScreenshot", it) }
        }
    }

    override suspend fun openSettings(settingsPage: String): Result<String> {
        logAction("openSettings", "page=$settingsPage")
        return runCatching {
            val action = when (settingsPage.lowercase()) {
                "wifi", "wi-fi" -> Settings.ACTION_WIFI_SETTINGS
                "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                "display" -> Settings.ACTION_DISPLAY_SETTINGS
                "sound", "audio" -> Settings.ACTION_SOUND_SETTINGS
                "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
                "storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
                "apps", "applications" -> Settings.ACTION_APPLICATION_SETTINGS
                "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
                "security" -> Settings.ACTION_SECURITY_SETTINGS
                "accounts" -> Settings.ACTION_SYNC_SETTINGS
                "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
                "developer" -> Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
                "date", "time", "date_time" -> Settings.ACTION_DATE_SETTINGS
                "language", "input" -> Settings.ACTION_INPUT_METHOD_SETTINGS
                "notification", "notifications" -> Settings.ACTION_APP_NOTIFICATION_SETTINGS
                "nfc" -> Settings.ACTION_NFC_SETTINGS
                "airplane", "flight" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
                "hotspot", "tethering" -> Settings.ACTION_WIRELESS_SETTINGS
                "vpn" -> Settings.ACTION_VPN_SETTINGS
                "about" -> Settings.ACTION_DEVICE_INFO_SETTINGS
                else -> Settings.ACTION_SETTINGS
            }
            val intent = Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            "Opened ${if (settingsPage.isEmpty()) "Settings" else "$settingsPage settings"}"
        }.also { r ->
            r.onSuccess { logResult("openSettings", it) }
            r.onFailure { logError("openSettings", it) }
        }
    }

    override suspend fun expandNotifications(): Result<String> {
        logAction("expandNotifications")
        return runCatching {
            val service = AutoSendAccessibilityService.instance
            if (service != null) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                "Notification shade expanded"
            } else {
                "Accessibility service not enabled"
            }
        }.also { r ->
            r.onSuccess { logResult("expandNotifications", it) }
            r.onFailure { logError("expandNotifications", it) }
        }
    }

    override suspend fun goHome(): Result<String> {
        logAction("goHome")
        return runCatching {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Going to home screen"
        }.also { r ->
            r.onSuccess { logResult("goHome", it) }
            r.onFailure { logError("goHome", it) }
        }
    }

    override suspend fun goBack(): Result<String> {
        logAction("goBack")
        return runCatching {
            val service = AutoSendAccessibilityService.instance
            if (service != null) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                "Going back"
            } else {
                "Accessibility service not enabled"
            }
        }.also { r ->
            r.onSuccess { logResult("goBack", it) }
            r.onFailure { logError("goBack", it) }
        }
    }

    override suspend fun showRecents(): Result<String> {
        logAction("showRecents")
        return runCatching {
            val service = AutoSendAccessibilityService.instance
            if (service != null) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                "Showing recent apps"
            } else {
                "Accessibility service not enabled"
            }
        }.also { r ->
            r.onSuccess { logResult("showRecents", it) }
            r.onFailure { logError("showRecents", it) }
        }
    }

    override suspend fun navigateTo(destination: String, mode: String): Result<String> {
        logAction("navigateTo", "destination=$destination, mode=$mode")
        return runCatching {
            val modeChar = when (mode.lowercase()) {
                "driving" -> "d"
                "walking" -> "w"
                "cycling", "bicycling" -> "b"
                "transit" -> "t"
                else -> ""
            }
            val modeParam = if (modeChar.isNotEmpty()) "&mode=$modeChar" else ""
            val uri = Uri.parse("google.navigation:q=${Uri.encode(destination)}$modeParam")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val modeLabel = if (mode.isNotEmpty()) " ($mode)" else ""
            "Starting navigation to $destination$modeLabel"
        }.also { r ->
            r.onSuccess { logResult("navigateTo", it) }
            r.onFailure { logError("navigateTo", it) }
        }
    }

    override suspend fun sendEmail(to: String, subject: String, body: String): Result<String> {
        logAction("sendEmail", "to=$to, subject=$subject")
        return runCatching {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                trySetPackage("com.google.android.gm", "com.microsoft.office.outlook")
            }
            context.startActivity(intent)
            "Opening email to $to"
        }.also { r ->
            r.onSuccess { logResult("sendEmail", it) }
            r.onFailure { logError("sendEmail", it) }
        }
    }

    // ==========================================
    // Ringer & Audio Modes
    // ==========================================

    override suspend fun setRingerMode(mode: String): Result<String> {
        logAction("setRingerMode", "mode=$mode")
        return runCatching {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            when (mode.lowercase()) {
                "silent" -> {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (!nm.isNotificationPolicyAccessGranted) {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        return@runCatching "Please grant DND access to set silent mode"
                    }
                    audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                    "Phone set to silent"
                }
                "vibrate" -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    "Phone set to vibrate"
                }
                "normal", "ring" -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    "Phone set to normal ringer"
                }
                else -> "Unknown ringer mode: $mode. Use silent, vibrate, or normal."
            }
        }.also { r ->
            r.onSuccess { logResult("setRingerMode", it) }
            r.onFailure { logError("setRingerMode", it) }
        }
    }

    override suspend fun setSpeakerphone(enabled: Boolean): Result<String> {
        logAction("setSpeakerphone", "enabled=$enabled")
        return runCatching {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = enabled
            "Speakerphone ${if (enabled) "on" else "off"}"
        }.also { r ->
            r.onSuccess { logResult("setSpeakerphone", it) }
            r.onFailure { logError("setSpeakerphone", it) }
        }
    }

    // ==========================================
    // Display & Power
    // ==========================================

    override suspend fun setScreenTimeout(seconds: Int): Result<String> {
        logAction("setScreenTimeout", "seconds=$seconds")
        return runCatching {
            if (Settings.System.canWrite(context)) {
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, seconds * 1000)
                val display = when {
                    seconds >= 60 -> "${seconds / 60} minutes"
                    else -> "$seconds seconds"
                }
                "Screen timeout set to $display"
            } else {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Please grant write settings permission"
            }
        }.also { r ->
            r.onSuccess { logResult("setScreenTimeout", it) }
            r.onFailure { logError("setScreenTimeout", it) }
        }
    }

    override suspend fun setBatterySaver(enabled: Boolean): Result<String> {
        logAction("setBatterySaver", "enabled=$enabled")
        return runCatching {
            val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened Battery Saver settings. Please ${if (enabled) "enable" else "disable"} it."
        }.also { r ->
            r.onSuccess { logResult("setBatterySaver", it) }
            r.onFailure { logError("setBatterySaver", it) }
        }
    }

    override suspend fun setDarkMode(enabled: Boolean): Result<String> {
        logAction("setDarkMode", "enabled=$enabled")
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
                uiModeManager.setApplicationNightMode(
                    if (enabled) android.app.UiModeManager.MODE_NIGHT_YES
                    else android.app.UiModeManager.MODE_NIGHT_NO
                )
                "Dark mode ${if (enabled) "enabled" else "disabled"}"
            } else {
                val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opened display settings to toggle dark mode"
            }
        }.also { r ->
            r.onSuccess { logResult("setDarkMode", it) }
            r.onFailure { logError("setDarkMode", it) }
        }
    }

    // ==========================================
    // Camera
    // ==========================================

    override suspend fun openCamera(): Result<String> {
        logAction("openCamera")
        return runCatching {
            val intent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                trySetPackage("com.google.android.GoogleCamera", "com.android.camera2", "com.android.camera", "com.samsung.android.app.camera")
            }
            try {
                context.startActivity(intent)
                "Camera opened"
            } catch (_: Exception) {
                val fallback = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage("com.android.camera2")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(fallback)
                    "Camera opened"
                } catch (_: Exception) {
                    context.startActivity(Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    "Camera opened"
                }
            }
        }.also { r ->
            r.onSuccess { logResult("openCamera", it) }
            r.onFailure { logError("openCamera", it) }
        }
    }

    override suspend fun takePhoto(): Result<String> = openCamera()

    // ==========================================
    // App Management
    // ==========================================

    override suspend fun uninstallApp(packageName: String): Result<String> {
        logAction("uninstallApp", "package=$packageName")
        return runCatching {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening uninstall dialog for $packageName"
        }.also { r ->
            r.onSuccess { logResult("uninstallApp", it) }
            r.onFailure { logError("uninstallApp", it) }
        }
    }

    override suspend fun forceStopApp(packageName: String): Result<String> {
        logAction("forceStopApp", "package=$packageName")
        return runCatching {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened app info for $packageName — tap 'Force Stop'"
        }.also { r ->
            r.onSuccess { logResult("forceStopApp", it) }
            r.onFailure { logError("forceStopApp", it) }
        }
    }

    override suspend fun getAppInfo(packageName: String): Result<String> = withContext(Dispatchers.IO) {
        logAction("getAppInfo", "package=$packageName")
        runCatching {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val pkgInfo = pm.getPackageInfo(packageName, 0)
            buildString {
                appendLine("App: ${pm.getApplicationLabel(info)}")
                appendLine("Package: $packageName")
                appendLine("Version: ${pkgInfo.versionName} (${pkgInfo.longVersionCode})")
                appendLine("Target SDK: ${info.targetSdkVersion}")
                appendLine("Installed: ${java.text.SimpleDateFormat("MMM dd yyyy", java.util.Locale.getDefault()).format(java.util.Date(pkgInfo.firstInstallTime))}")
                appendLine("Updated: ${java.text.SimpleDateFormat("MMM dd yyyy", java.util.Locale.getDefault()).format(java.util.Date(pkgInfo.lastUpdateTime))}")
                appendLine("Enabled: ${info.enabled}")
            }.trim()
        }.also { r ->
            r.onSuccess { logResult("getAppInfo", it) }
            r.onFailure { logError("getAppInfo", it) }
        }
    }

    // ==========================================
    // System UI
    // ==========================================

    override suspend fun openQuickSettings(): Result<String> {
        logAction("openQuickSettings")
        return runCatching {
            val service = AutoSendAccessibilityService.instance
            if (service != null) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                "Quick settings opened"
            } else {
                "Accessibility service not enabled"
            }
        }.also { r ->
            r.onSuccess { logResult("openQuickSettings", it) }
            r.onFailure { logError("openQuickSettings", it) }
        }
    }

    override suspend fun openPowerMenu(): Result<String> {
        logAction("openPowerMenu")
        return runCatching {
            val service = AutoSendAccessibilityService.instance
            if (service != null) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
                "Power menu opened"
            } else {
                "Accessibility service not enabled"
            }
        }.also { r ->
            r.onSuccess { logResult("openPowerMenu", it) }
            r.onFailure { logError("openPowerMenu", it) }
        }
    }

    override suspend fun splitScreen(): Result<String> {
        logAction("splitScreen")
        return runCatching {
            val service = AutoSendAccessibilityService.instance
            if (service != null) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
                "Split screen toggled"
            } else {
                "Accessibility service not enabled"
            }
        }.also { r ->
            r.onSuccess { logResult("splitScreen", it) }
            r.onFailure { logError("splitScreen", it) }
        }
    }

    override suspend fun lockOrientation(portrait: Boolean): Result<String> {
        logAction("lockOrientation", "portrait=$portrait")
        return runCatching {
            if (Settings.System.canWrite(context)) {
                // Disable auto-rotate first
                Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
                // Set orientation
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.USER_ROTATION,
                    if (portrait) 0 else 1 // 0 = portrait, 1 = landscape
                )
                "Screen locked to ${if (portrait) "portrait" else "landscape"}"
            } else {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Please grant write settings permission"
            }
        }.also { r ->
            r.onSuccess { logResult("lockOrientation", it) }
            r.onFailure { logError("lockOrientation", it) }
        }
    }

    // ==========================================
    // Notes & QR
    // ==========================================

    override suspend fun createNote(title: String, content: String): Result<String> {
        logAction("createNote", "title=$title")
        return runCatching {
            // Try Google Keep first
            try {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(Intent.EXTRA_TEXT, content)
                    setPackage("com.google.android.keep")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return@runCatching "Creating note in Google Keep: $title"
            } catch (_: Exception) {}

            // Fallback to any notes app via share
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "$title\n\n$content")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Save note to...").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            "Opening share dialog to save note: $title"
        }.also { r ->
            r.onSuccess { logResult("createNote", it) }
            r.onFailure { logError("createNote", it) }
        }
    }

    override suspend fun scanQrCode(): Result<String> {
        logAction("scanQrCode")
        return runCatching {
            // Try Google Lens / barcode scanner
            try {
                val intent = Intent("com.google.zxing.client.android.SCAN").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return@runCatching "Opening QR scanner"
            } catch (_: Exception) {}

            // Fallback: open Google Lens
            try {
                val intent = context.packageManager.getLaunchIntentForPackage("com.google.ar.lens")
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return@runCatching "Opening Google Lens to scan"
                }
            } catch (_: Exception) {}

            // Fallback: open camera
            openCamera().getOrDefault("Camera opened for scanning")
        }.also { r ->
            r.onSuccess { logResult("scanQrCode", it) }
            r.onFailure { logError("scanQrCode", it) }
        }
    }

    // ==========================================
    // Device Info Extended
    // ==========================================

    override suspend fun getBatteryInfo(): Result<String> {
        logAction("getBatteryInfo")
        return runCatching {
            val batteryIntent = context.registerReceiver(null,
                android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
            val plugged = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            val temp = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            val health = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_HEALTH, -1) ?: -1

            val pct = if (level >= 0 && scale > 0) (level * 100) / scale else -1
            val statusStr = when (status) {
                android.os.BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                android.os.BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                android.os.BatteryManager.BATTERY_STATUS_FULL -> "Full"
                android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
                else -> "Unknown"
            }
            val plugStr = when (plugged) {
                android.os.BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                android.os.BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                android.os.BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                else -> "Unplugged"
            }
            val healthStr = when (health) {
                android.os.BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                android.os.BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
                android.os.BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                android.os.BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                else -> "Unknown"
            }

            buildString {
                appendLine("Battery: $pct%")
                appendLine("Status: $statusStr")
                appendLine("Plugged: $plugStr")
                appendLine("Health: $healthStr")
                if (temp > 0) appendLine("Temperature: ${temp / 10.0}°C")
            }.trim()
        }.also { r ->
            r.onSuccess { logResult("getBatteryInfo", it) }
            r.onFailure { logError("getBatteryInfo", it) }
        }
    }

    override suspend fun getStorageInfo(): Result<String> = withContext(Dispatchers.IO) {
        logAction("getStorageInfo")
        runCatching {
            val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
            val totalBytes = stat.totalBytes
            val freeBytes = stat.availableBytes
            val usedBytes = totalBytes - freeBytes
            fun gb(bytes: Long) = "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
            buildString {
                appendLine("Internal Storage:")
                appendLine("  Total: ${gb(totalBytes)}")
                appendLine("  Used: ${gb(usedBytes)}")
                appendLine("  Free: ${gb(freeBytes)}")
                appendLine("  Usage: ${(usedBytes * 100 / totalBytes)}%")
            }.trim()
        }.also { r ->
            r.onSuccess { logResult("getStorageInfo", it) }
            r.onFailure { logError("getStorageInfo", it) }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun getNetworkInfo(): Result<String> {
        logAction("getNetworkInfo")
        return runCatching {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

            buildString {
                val network = connectivityManager.activeNetwork
                val caps = connectivityManager.getNetworkCapabilities(network)
                appendLine("Network connected: ${network != null}")

                if (caps != null) {
                    val type = when {
                        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                        else -> "Other"
                    }
                    appendLine("Type: $type")

                    if (type == "Wi-Fi") {
                        @Suppress("DEPRECATION")
                        val wifiInfo = wifiManager.connectionInfo
                        if (wifiInfo != null) {
                            appendLine("SSID: ${wifiInfo.ssid}")
                            appendLine("Signal: ${wifiInfo.rssi} dBm")
                            appendLine("Speed: ${wifiInfo.linkSpeed} Mbps")
                            val ip = wifiInfo.ipAddress
                            if (ip != 0) {
                                val ipStr = "${ip and 0xFF}.${(ip shr 8) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 24) and 0xFF}"
                                appendLine("IP: $ipStr")
                            }
                        }
                    }

                    val downMbps = caps.linkDownstreamBandwidthKbps / 1000
                    val upMbps = caps.linkUpstreamBandwidthKbps / 1000
                    appendLine("Bandwidth: ${downMbps}/${upMbps} Mbps (down/up)")
                }
            }.trim()
        }.also { r ->
            r.onSuccess { logResult("getNetworkInfo", it) }
            r.onFailure { logError("getNetworkInfo", it) }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun getBluetoothDevices(): Result<String> {
        logAction("getBluetoothDevices")
        return runCatching {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                return@runCatching "Bluetooth permission not granted"
            }
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
            val adapter = bluetoothManager.adapter
                ?: return@runCatching "Bluetooth not available"

            val paired = adapter.bondedDevices
            if (paired.isNullOrEmpty()) {
                "No paired Bluetooth devices"
            } else {
                buildString {
                    appendLine("Bluetooth ${if (adapter.isEnabled) "ON" else "OFF"}")
                    appendLine("Paired devices (${paired.size}):")
                    paired.forEach { device ->
                        val typeStr = when (device.type) {
                            android.bluetooth.BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic"
                            android.bluetooth.BluetoothDevice.DEVICE_TYPE_LE -> "BLE"
                            android.bluetooth.BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual"
                            else -> "Unknown"
                        }
                        appendLine("  - ${device.name ?: "Unknown"} ($typeStr) ${device.address}")
                    }
                }.trim()
            }
        }.also { r ->
            r.onSuccess { logResult("getBluetoothDevices", it) }
            r.onFailure { logError("getBluetoothDevices", it) }
        }
    }

    // ==========================================
    // Sound / TTS
    // ==========================================

    override suspend fun findMyPhone(): Result<String> {
        logAction("findMyPhone")
        return runCatching {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
            val originalMode = audioManager.ringerMode
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)

            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            audioManager.setStreamVolume(AudioManager.STREAM_RING, maxVolume, 0)

            val ringtone = android.media.RingtoneManager.getActualDefaultRingtoneUri(context, android.media.RingtoneManager.TYPE_RINGTONE)
            val r = android.media.RingtoneManager.getRingtone(context, ringtone)
            r?.play()

            // Stop after 10 seconds
            kotlinx.coroutines.delay(10000)
            r?.stop()
            audioManager.setStreamVolume(AudioManager.STREAM_RING, originalVolume, 0)
            audioManager.ringerMode = originalMode

            "Phone rang at max volume for 10 seconds"
        }.also { r ->
            r.onSuccess { logResult("findMyPhone", it) }
            r.onFailure { logError("findMyPhone", it) }
        }
    }

    override suspend fun readAloud(text: String): Result<String> {
        logAction("readAloud", "textLen=${text.length}")
        return runCatching {
            val tts = android.speech.tts.TextToSpeech(context, null)
            kotlinx.coroutines.delay(500) // wait for TTS init
            tts.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "androidclaw_tts")
            "Reading aloud: ${text.take(80)}..."
        }.also { r ->
            r.onSuccess { logResult("readAloud", it) }
            r.onFailure { logError("readAloud", it) }
        }
    }

    // ==========================================
    // More System Actions
    // ==========================================

    override suspend fun openHotspotSettings(): Result<String> {
        logAction("openHotspotSettings")
        return runCatching {
            val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened hotspot/tethering settings"
        }.also { r ->
            r.onSuccess { logResult("openHotspotSettings", it) }
            r.onFailure { logError("openHotspotSettings", it) }
        }
    }

    override suspend fun openAirplaneSettings(): Result<String> {
        logAction("openAirplaneSettings")
        return runCatching {
            val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened airplane mode settings"
        }.also { r ->
            r.onSuccess { logResult("openAirplaneSettings", it) }
            r.onFailure { logError("openAirplaneSettings", it) }
        }
    }

    override suspend fun clearAllNotifications(): Result<String> {
        logAction("clearAllNotifications")
        return runCatching {
            val service = AutoSendAccessibilityService.instance
            if (service != null) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
                "All notifications cleared"
            } else {
                "Accessibility service not enabled"
            }
        }.also { r ->
            r.onSuccess { logResult("clearAllNotifications", it) }
            r.onFailure { logError("clearAllNotifications", it) }
        }
    }

    override suspend fun startStopwatch(): Result<String> {
        logAction("startStopwatch")
        return runCatching {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, 0) // 0 = stopwatch mode on some devices
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                // Fallback: open clock app
                val clockIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.deskclock")
                    ?: context.packageManager.getLaunchIntentForPackage("com.android.deskclock")
                clockIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                clockIntent?.let { context.startActivity(it) }
            }
            "Opened stopwatch/clock"
        }.also { r ->
            r.onSuccess { logResult("startStopwatch", it) }
            r.onFailure { logError("startStopwatch", it) }
        }
    }

    override suspend fun translateText(text: String, targetLang: String): Result<String> {
        logAction("translateText", "text=${text.take(50)}, lang=$targetLang")
        return runCatching {
            val langParam = if (targetLang.isNotEmpty()) "&tl=${Uri.encode(targetLang)}" else ""
            val uri = Uri.parse("https://translate.google.com/?sl=auto$langParam&text=${Uri.encode(text)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try { setPackage("com.google.android.apps.translate") } catch (_: Exception) {}
            }
            context.startActivity(intent)
            "Opening Google Translate for: ${text.take(80)}"
        }.also { r ->
            r.onSuccess { logResult("translateText", it) }
            r.onFailure { logError("translateText", it) }
        }
    }

    override suspend fun identifySong(): Result<String> {
        logAction("identifySong")
        return runCatching {
            // Try Shazam first
            try {
                val intent = context.packageManager.getLaunchIntentForPackage("com.shazam.android")
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return@runCatching "Opening Shazam to identify song"
                }
            } catch (_: Exception) {}

            // Try Google Sound Search
            try {
                val intent = Intent(android.provider.MediaStore.INTENT_ACTION_MEDIA_SEARCH).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    trySetPackage("com.google.android.googlequicksearchbox")
                }
                context.startActivity(intent)
                return@runCatching "Opening sound search"
            } catch (_: Exception) {}

            // Fallback: Google search
            val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(android.app.SearchManager.QUERY, "identify this song")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                trySetPackage("com.google.android.googlequicksearchbox")
            }
            context.startActivity(intent)
            "Opening Google to identify song — try humming or playing the song"
        }.also { r ->
            r.onSuccess { logResult("identifySong", it) }
            r.onFailure { logError("identifySong", it) }
        }
    }

    override suspend fun quickShare(text: String): Result<String> {
        logAction("quickShare", "textLen=${text.length}")
        return shareText(text, null)
    }

    override suspend fun openFileManager(): Result<String> {
        logAction("openFileManager")
        return runCatching {
            // Try Google Files first
            try {
                val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.nbu.files")
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return@runCatching "Opened Google Files"
                }
            } catch (_: Exception) {}

            // Fallback: system file picker
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened file browser"
        }.also { r ->
            r.onSuccess { logResult("openFileManager", it) }
            r.onFailure { logError("openFileManager", it) }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun answerCall(): Result<String> {
        logAction("answerCall")
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
                if (hasPermission(Manifest.permission.ANSWER_PHONE_CALLS)) {
                    telecomManager.acceptRingingCall()
                    "Call answered"
                } else {
                    "ANSWER_PHONE_CALLS permission not granted"
                }
            } else {
                "Answer call not supported on this Android version"
            }
        }.also { r ->
            r.onSuccess { logResult("answerCall", it) }
            r.onFailure { logError("answerCall", it) }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun rejectCall(): Result<String> {
        logAction("rejectCall")
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
                if (hasPermission(Manifest.permission.ANSWER_PHONE_CALLS)) {
                    telecomManager.endCall()
                    "Call rejected"
                } else {
                    "ANSWER_PHONE_CALLS permission not granted"
                }
            } else {
                "Reject call not supported on this Android version"
            }
        }.also { r ->
            r.onSuccess { logResult("rejectCall", it) }
            r.onFailure { logError("rejectCall", it) }
        }
    }

    override suspend fun setWallpaper(url: String): Result<String> {
        logAction("setWallpaper", "url=$url")
        return runCatching {
            val intent = Intent(Intent.ACTION_SET_WALLPAPER).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened wallpaper picker"
        }.also { r ->
            r.onSuccess { logResult("setWallpaper", it) }
            r.onFailure { logError("setWallpaper", it) }
        }
    }

    override suspend fun setFontSize(scale: String): Result<String> {
        logAction("setFontSize", "scale=$scale")
        return runCatching {
            if (Settings.System.canWrite(context)) {
                val fontScale = when (scale.lowercase()) {
                    "small" -> 0.85f
                    "default", "normal" -> 1.0f
                    "large" -> 1.15f
                    "largest", "huge" -> 1.3f
                    else -> 1.0f
                }
                Settings.System.putFloat(context.contentResolver, Settings.System.FONT_SCALE, fontScale)
                "Font size set to $scale ($fontScale)"
            } else {
                val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opened display settings to change font size"
            }
        }.also { r ->
            r.onSuccess { logResult("setFontSize", it) }
            r.onFailure { logError("setFontSize", it) }
        }
    }

    override suspend fun screenRecord(): Result<String> {
        logAction("screenRecord")
        return runCatching {
            // Use accessibility service to trigger screen record (Android 11+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val service = AutoSendAccessibilityService.instance
                if (service != null) {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
                    "Use the screen recorder from Quick Settings tile"
                } else {
                    "Accessibility service not enabled"
                }
            } else {
                // Open quick settings where screen recorder tile is
                val service = AutoSendAccessibilityService.instance
                service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                "Opened Quick Settings — tap Screen Record tile"
            }
        }.also { r ->
            r.onSuccess { logResult("screenRecord", it) }
            r.onFailure { logError("screenRecord", it) }
        }
    }

    override suspend fun restartDevice(): Result<String> {
        logAction("restartDevice")
        return runCatching {
            val service = AutoSendAccessibilityService.instance
            if (service != null) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
                "Opened power menu — tap Restart"
            } else {
                "Accessibility service not enabled. Open power menu manually."
            }
        }.also { r ->
            r.onSuccess { logResult("restartDevice", it) }
            r.onFailure { logError("restartDevice", it) }
        }
    }

    // ==========================================
    // Fun / Random
    // ==========================================

    override suspend fun coinFlip(): Result<String> {
        logAction("coinFlip")
        return Result.success(if ((0..1).random() == 0) "Heads!" else "Tails!")
    }

    override suspend fun rollDice(sides: Int): Result<String> {
        logAction("rollDice", "sides=$sides")
        return Result.success("Rolled a ${(1..sides).random()} (d$sides)")
    }

    override suspend fun randomNumber(min: Int, max: Int): Result<String> {
        logAction("randomNumber", "min=$min, max=$max")
        return Result.success("Random number: ${(min..max).random()}")
    }

    override suspend fun countdownTo(date: String): Result<String> {
        logAction("countdownTo", "date=$date")
        return runCatching {
            val formats = listOf(
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()),
                java.text.SimpleDateFormat("MM/dd/yyyy", java.util.Locale.getDefault()),
                java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()),
                java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.getDefault()),
                java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()),
            )
            var targetDate: java.util.Date? = null
            for (fmt in formats) {
                try { targetDate = fmt.parse(date); break } catch (_: Exception) {}
            }
            if (targetDate == null) return@runCatching "Could not parse date: $date. Try YYYY-MM-DD format."
            val now = System.currentTimeMillis()
            val diff = targetDate.time - now
            val days = diff / (1000 * 60 * 60 * 24)
            when {
                days > 0 -> "$days days until $date"
                days == 0L -> "$date is today!"
                else -> "$date was ${-days} days ago"
            }
        }.also { r ->
            r.onSuccess { logResult("countdownTo", it) }
            r.onFailure { logError("countdownTo", it) }
        }
    }

    // ==========================================
    // Recording & Media
    // ==========================================

    override suspend fun startVoiceRecording(): Result<String> {
        logAction("startVoiceRecording")
        return runCatching {
            val intent = Intent(android.provider.MediaStore.Audio.Media.RECORD_SOUND_ACTION).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                "Voice recorder opened"
            } catch (_: Exception) {
                val fallback = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.recorder")
                    ?: context.packageManager.getLaunchIntentForPackage("com.sec.android.app.voicenote")
                if (fallback != null) {
                    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(fallback)
                    "Voice recorder opened"
                } else {
                    "No voice recorder app found"
                }
            }
        }.also { r ->
            r.onSuccess { logResult("startVoiceRecording", it) }
            r.onFailure { logError("startVoiceRecording", it) }
        }
    }

    override suspend fun openSpeedTest(): Result<String> {
        logAction("openSpeedTest")
        return runCatching {
            // Try Speedtest app first
            val intent = context.packageManager.getLaunchIntentForPackage("org.zwanoo.android.speedtest")
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                "Opening Speedtest app"
            } else {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://fast.com")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                "Opening speed test in browser"
            }
        }.also { r ->
            r.onSuccess { logResult("openSpeedTest", it) }
            r.onFailure { logError("openSpeedTest", it) }
        }
    }

    override suspend fun castScreen(): Result<String> {
        logAction("castScreen")
        return runCatching {
            val intent = Intent(Settings.ACTION_CAST_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened cast/mirror settings"
        }.also { r ->
            r.onSuccess { logResult("castScreen", it) }
            r.onFailure { logError("castScreen", it) }
        }
    }

    override suspend fun openIncognito(): Result<String> {
        logAction("openIncognito")
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                setPackage("com.android.chrome")
                putExtra("com.android.browser.application_id", "com.android.chrome")
                putExtra("create_new_tab", true)
                putExtra("com.google.android.apps.chrome.EXTRA_OPEN_NEW_INCOGNITO_TAB", true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening Chrome incognito tab"
        }.also { r ->
            r.onSuccess { logResult("openIncognito", it) }
            r.onFailure { logError("openIncognito", it) }
        }
    }

    override suspend fun sortChromeTabs(order: String): Result<String> {
        logAction("sortChromeTabs", "order=$order")
        return runCatching {
            val service = AutoSendAccessibilityService.instance
                ?: return@runCatching "Accessibility service not enabled. Enable AndroidClaw in Settings > Accessibility to sort Chrome tabs."

            val launch = context.packageManager.getLaunchIntentForPackage("com.android.chrome")
                ?: return@runCatching "Chrome is not installed on this device."
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
            kotlinx.coroutines.delay(1500)

            service.sortChromeTabs(order)
        }.also { r ->
            r.onSuccess { logResult("sortChromeTabs", it) }
            r.onFailure { logError("sortChromeTabs", it) }
        }
    }

    // ==========================================
    // Emergency
    // ==========================================

    override suspend fun emergencyCall(): Result<String> {
        logAction("emergencyCall")
        return runCatching {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:911")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                trySetPackage("com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer")
            }
            context.startActivity(intent)
            "Opening dialer with emergency number. Press call to connect."
        }.also { r ->
            r.onSuccess { logResult("emergencyCall", it) }
            r.onFailure { logError("emergencyCall", it) }
        }
    }

    // ==========================================
    // Device Info Extended 2
    // ==========================================

    override suspend fun getDataUsage(): Result<String> {
        logAction("getDataUsage")
        return runCatching {
            val intent = Intent(Settings.ACTION_DATA_USAGE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened data usage settings"
        }.also { r ->
            r.onSuccess { logResult("getDataUsage", it) }
            r.onFailure { logError("getDataUsage", it) }
        }
    }

    @SuppressLint("MissingPermission", "HardwareIds")
    override suspend fun getSimInfo(): Result<String> {
        logAction("getSimInfo")
        return runCatching {
            if (!hasPermission(Manifest.permission.READ_PHONE_STATE)) {
                return@runCatching "Phone state permission not granted"
            }
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
            buildString {
                appendLine("Carrier: ${tm.networkOperatorName}")
                appendLine("Network type: ${tm.dataNetworkType}")
                appendLine("SIM operator: ${tm.simOperatorName}")
                appendLine("SIM country: ${tm.simCountryIso?.uppercase()}")
                appendLine("Phone type: ${when(tm.phoneType) {
                    android.telephony.TelephonyManager.PHONE_TYPE_GSM -> "GSM"
                    android.telephony.TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
                    android.telephony.TelephonyManager.PHONE_TYPE_SIP -> "SIP"
                    else -> "Unknown"
                }}")
                if (hasPermission(Manifest.permission.READ_PHONE_STATE)) {
                    try { appendLine("Phone number: ${tm.line1Number ?: "N/A"}") } catch (_: Exception) {}
                }
            }.trim()
        }.also { r ->
            r.onSuccess { logResult("getSimInfo", it) }
            r.onFailure { logError("getSimInfo", it) }
        }
    }

    override suspend fun getDeviceUptime(): Result<String> {
        logAction("getDeviceUptime")
        return runCatching {
            val uptimeMs = android.os.SystemClock.elapsedRealtime()
            val hours = uptimeMs / (1000 * 60 * 60)
            val minutes = (uptimeMs / (1000 * 60)) % 60
            val days = hours / 24
            val h = hours % 24
            buildString {
                if (days > 0) append("${days}d ")
                append("${h}h ${minutes}m")
            }.let { "Device uptime: $it" }
        }
    }

    override suspend fun getMemoryInfo(): Result<String> {
        logAction("getMemoryInfo")
        return runCatching {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            fun gb(bytes: Long) = "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
            buildString {
                appendLine("RAM:")
                appendLine("  Total: ${gb(memInfo.totalMem)}")
                appendLine("  Available: ${gb(memInfo.availMem)}")
                appendLine("  Used: ${gb(memInfo.totalMem - memInfo.availMem)}")
                appendLine("  Usage: ${((memInfo.totalMem - memInfo.availMem) * 100 / memInfo.totalMem)}%")
                appendLine("  Low memory: ${memInfo.lowMemory}")
            }.trim()
        }.also { r ->
            r.onSuccess { logResult("getMemoryInfo", it) }
            r.onFailure { logError("getMemoryInfo", it) }
        }
    }

    override suspend fun checkForUpdate(): Result<String> {
        logAction("checkForUpdate")
        return runCatching {
            val intent = Intent("android.settings.SYSTEM_UPDATE_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                context.startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
            "Opened system update settings"
        }.also { r ->
            r.onSuccess { logResult("checkForUpdate", it) }
            r.onFailure { logError("checkForUpdate", it) }
        }
    }

    // ==========================================
    // Display & Accessibility
    // ==========================================

    override suspend fun setNightLight(enabled: Boolean): Result<String> {
        logAction("setNightLight", "enabled=$enabled")
        return runCatching {
            val intent = Intent(Settings.ACTION_NIGHT_DISPLAY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
            "Opened night light settings — please ${if (enabled) "enable" else "disable"} it"
        }.also { r ->
            r.onSuccess { logResult("setNightLight", it) }
            r.onFailure { logError("setNightLight", it) }
        }
    }

    override suspend fun setBedtimeMode(enabled: Boolean): Result<String> {
        logAction("setBedtimeMode", "enabled=$enabled")
        return runCatching {
            try {
                val intent = Intent().apply {
                    setClassName("com.google.android.apps.wellbeing", "com.google.android.apps.wellbeing.settings.WindDownSettingsActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opened bedtime mode settings"
            } catch (_: Exception) {
                val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opened display settings — look for bedtime/wind down mode"
            }
        }.also { r ->
            r.onSuccess { logResult("setBedtimeMode", it) }
            r.onFailure { logError("setBedtimeMode", it) }
        }
    }

    override suspend fun pinApp(): Result<String> {
        logAction("pinApp")
        return runCatching {
            val service = AutoSendAccessibilityService.instance
            if (service != null) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                "Showing recents — tap the pin icon on the app you want to pin"
            } else {
                "Accessibility service not enabled"
            }
        }.also { r ->
            r.onSuccess { logResult("pinApp", it) }
            r.onFailure { logError("pinApp", it) }
        }
    }

    override suspend fun flashlightSos(): Result<String> {
        logAction("flashlightSos")
        return runCatching {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull()
                ?: return@runCatching "No camera available"

            // SOS: 3 short, 3 long, 3 short
            val pattern = listOf(200L, 200L, 200L, 200L, 200L, 200L,  // 3 short
                500L, 200L, 500L, 200L, 500L, 200L,                     // 3 long
                200L, 200L, 200L, 200L, 200L, 200L)                     // 3 short

            for (i in pattern.indices) {
                val on = i % 2 == 0
                cameraManager.setTorchMode(cameraId, on)
                kotlinx.coroutines.delay(pattern[i])
            }
            cameraManager.setTorchMode(cameraId, false)
            "SOS signal flashed"
        }.also { r ->
            r.onSuccess { logResult("flashlightSos", it) }
            r.onFailure { logError("flashlightSos", it) }
        }
    }

    override suspend fun setColorInversion(enabled: Boolean): Result<String> {
        logAction("setColorInversion", "enabled=$enabled")
        return runCatching {
            try {
                Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_DISPLAY_INVERSION_ENABLED, if (enabled) 1 else 0)
                "Color inversion ${if (enabled) "enabled" else "disabled"}"
            } catch (_: Exception) {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opened accessibility settings — toggle color inversion"
            }
        }.also { r ->
            r.onSuccess { logResult("setColorInversion", it) }
            r.onFailure { logError("setColorInversion", it) }
        }
    }

    override suspend fun setMagnification(enabled: Boolean): Result<String> {
        logAction("setMagnification", "enabled=$enabled")
        return runCatching {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened accessibility settings — ${if (enabled) "enable" else "disable"} magnification"
        }.also { r ->
            r.onSuccess { logResult("setMagnification", it) }
            r.onFailure { logError("setMagnification", it) }
        }
    }

    // ==========================================
    // App & Settings Management
    // ==========================================

    override suspend fun clearAppData(packageName: String): Result<String> {
        logAction("clearAppData", "package=$packageName")
        return runCatching {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened app info for $packageName — tap Storage > Clear Data"
        }.also { r ->
            r.onSuccess { logResult("clearAppData", it) }
            r.onFailure { logError("clearAppData", it) }
        }
    }

    override suspend fun openDefaultApps(): Result<String> {
        logAction("openDefaultApps")
        return runCatching {
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened default apps settings"
        }.also { r ->
            r.onSuccess { logResult("openDefaultApps", it) }
            r.onFailure { logError("openDefaultApps", it) }
        }
    }

    override suspend fun openDigitalWellbeing(): Result<String> {
        logAction("openDigitalWellbeing")
        return runCatching {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.wellbeing")
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    "Opened Digital Wellbeing"
                } else {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    "Digital Wellbeing not available — opened Settings"
                }
            } catch (_: Exception) {
                "Digital Wellbeing app not found"
            }
        }.also { r ->
            r.onSuccess { logResult("openDigitalWellbeing", it) }
            r.onFailure { logError("openDigitalWellbeing", it) }
        }
    }

    override suspend fun openRingtoneSettings(): Result<String> {
        logAction("openRingtoneSettings")
        return runCatching {
            val intent = Intent(Settings.ACTION_SOUND_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened sound & ringtone settings"
        }.also { r ->
            r.onSuccess { logResult("openRingtoneSettings", it) }
            r.onFailure { logError("openRingtoneSettings", it) }
        }
    }

    override suspend fun createReminder(text: String, timeMillis: Long): Result<String> {
        logAction("createReminder", "text=$text, time=$timeMillis")
        return runCatching {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, "Reminder: $text")
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, timeMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, timeMillis + 30 * 60 * 1000)
                putExtra(CalendarContract.Events.HAS_ALARM, 1)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                trySetPackage("com.google.android.calendar", "com.android.calendar", "com.samsung.android.calendar")
            }
            context.startActivity(intent)
            "Creating reminder: $text"
        }.also { r ->
            r.onSuccess { logResult("createReminder", it) }
            r.onFailure { logError("createReminder", it) }
        }
    }

    // ==========================================
    // File Management
    // ==========================================

    override suspend fun listFiles(directory: String, sortBy: String): Result<String> = withContext(Dispatchers.IO) {
        logAction("listFiles", "dir=$directory, sortBy=$sortBy")
        runCatching {
            val dir = resolveDirectory(directory)
            if (!dir.exists() || !dir.isDirectory) {
                return@runCatching "Directory not found: ${dir.absolutePath}"
            }
            val files = dir.listFiles() ?: return@runCatching "Cannot list files in ${dir.absolutePath}"
            if (files.isEmpty()) return@runCatching "No files in ${dir.absolutePath}"

            val sorted = when (sortBy.lowercase()) {
                "name" -> files.sortedBy { it.name.lowercase() }
                "size" -> files.sortedByDescending { it.length() }
                "type" -> files.sortedBy { it.extension.lowercase() }
                else -> files.sortedByDescending { it.lastModified() } // date (default)
            }

            val lines = sorted.mapIndexed { i, f ->
                val size = formatFileSize(f.length())
                val modified = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(f.lastModified()))
                val type = if (f.isDirectory) "DIR" else f.extension.uppercase().ifEmpty { "FILE" }
                "${i + 1}. [$type] ${f.name} ($size, $modified)"
            }
            "Files in ${dir.absolutePath} (${sorted.size} items, sorted by $sortBy):\n${lines.joinToString("\n")}"
        }.also { r ->
            r.onSuccess { logResult("listFiles", it.take(200)) }
            r.onFailure { logError("listFiles", it) }
        }
    }

    override suspend fun getFileInfo(filePath: String): Result<String> = withContext(Dispatchers.IO) {
        logAction("getFileInfo", "path=$filePath")
        runCatching {
            val file = java.io.File(filePath)
            if (!file.exists()) return@runCatching "File not found: $filePath"
            val size = formatFileSize(file.length())
            val modified = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(file.lastModified()))
            buildString {
                appendLine("Name: ${file.name}")
                appendLine("Path: ${file.absolutePath}")
                appendLine("Size: $size")
                appendLine("Type: ${if (file.isDirectory) "Directory" else file.extension.uppercase().ifEmpty { "Unknown" }}")
                appendLine("Modified: $modified")
                appendLine("Readable: ${file.canRead()}")
                appendLine("Writable: ${file.canWrite()}")
            }
        }
    }

    override suspend fun deleteFile(filePath: String): Result<String> = withContext(Dispatchers.IO) {
        logAction("deleteFile", "path=$filePath")
        runCatching {
            val file = java.io.File(filePath)
            if (!file.exists()) return@runCatching "File not found: $filePath"
            if (file.isDirectory) {
                if (file.deleteRecursively()) "Deleted directory: ${file.name}" else "Failed to delete directory: ${file.name}"
            } else {
                if (file.delete()) "Deleted file: ${file.name}" else "Failed to delete file: ${file.name}"
            }
        }
    }

    override suspend fun moveFile(sourcePath: String, destDirectory: String): Result<String> = withContext(Dispatchers.IO) {
        logAction("moveFile", "source=$sourcePath, dest=$destDirectory")
        runCatching {
            val source = java.io.File(sourcePath)
            if (!source.exists()) return@runCatching "Source file not found: $sourcePath"
            val destDir = resolveDirectory(destDirectory)
            if (!destDir.exists()) destDir.mkdirs()
            val dest = java.io.File(destDir, source.name)
            if (dest.exists()) return@runCatching "File already exists at destination: ${dest.absolutePath}"
            if (source.renameTo(dest)) {
                "Moved ${source.name} to ${dest.absolutePath}"
            } else {
                // renameTo fails across mount points; fall back to copy+delete
                source.copyTo(dest, overwrite = false)
                source.delete()
                "Moved ${source.name} to ${dest.absolutePath}"
            }
        }
    }

    override suspend fun organizeFiles(directory: String): Result<String> = withContext(Dispatchers.IO) {
        logAction("organizeFiles", "dir=$directory")
        runCatching {
            val dir = resolveDirectory(directory)
            if (!dir.exists() || !dir.isDirectory) return@runCatching "Directory not found: ${dir.absolutePath}"
            val files = dir.listFiles()?.filter { it.isFile } ?: return@runCatching "No files found"
            if (files.isEmpty()) return@runCatching "No files to organize"

            val categoryMap = mapOf(
                "Images" to setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic", "heif"),
                "Videos" to setOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "3gp", "webm"),
                "Audio" to setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "wma"),
                "Documents" to setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt", "csv"),
                "Archives" to setOf("zip", "rar", "7z", "tar", "gz", "bz2"),
                "APKs" to setOf("apk", "xapk", "apkm"),
                "Code" to setOf("kt", "java", "py", "js", "ts", "html", "css", "json", "xml")
            )

            var movedCount = 0
            val summary = mutableMapOf<String, Int>()
            for (file in files) {
                val ext = file.extension.lowercase()
                val category = categoryMap.entries.find { ext in it.value }?.key ?: "Other"
                val categoryDir = java.io.File(dir, category)
                if (!categoryDir.exists()) categoryDir.mkdirs()
                val dest = java.io.File(categoryDir, file.name)
                if (!dest.exists() && file.renameTo(dest)) {
                    movedCount++
                    summary[category] = (summary[category] ?: 0) + 1
                }
            }
            val summaryStr = summary.entries.joinToString(", ") { "${it.value} ${it.key}" }
            "Organized $movedCount files in ${dir.name}: $summaryStr"
        }
    }

    // ==========================================
    // Ride-Hailing
    // ==========================================

    override suspend fun orderRide(destination: String, service: String): Result<String> {
        logAction("orderRide", "dest=$destination, service=$service")
        return runCatching {
            val encodedDest = android.net.Uri.encode(destination)
            val (deepLink, webFallback, appName) = when (service.lowercase()) {
                "uber" -> Triple(
                    "uber://?action=setPickup&pickup=my_location&dropoff[formatted_address]=$encodedDest",
                    "https://m.uber.com/ul/?action=setPickup&pickup=my_location&dropoff[formatted_address]=$encodedDest",
                    "Uber"
                )
                "lyft" -> Triple(
                    "lyft://ridetype?id=lyft&destination[address]=$encodedDest",
                    "https://ride.lyft.com/",
                    "Lyft"
                )
                "careem" -> Triple(
                    "careem://booking?pickup=current&dropoff_name=$encodedDest",
                    "https://app.careem.com/",
                    "Careem"
                )
                "bolt" -> Triple(
                    "bolt://ride?destination=$encodedDest",
                    "https://bolt.eu/",
                    "Bolt"
                )
                else -> Triple(
                    "uber://?action=setPickup&pickup=my_location&dropoff[formatted_address]=$encodedDest",
                    "https://m.uber.com/ul/?action=setPickup&pickup=my_location&dropoff[formatted_address]=$encodedDest",
                    "Uber"
                )
            }

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                "Opening $appName with destination: $destination"
            } catch (_: Exception) {
                // Fall back to web
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webFallback)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                "Opening $appName in browser (app not installed) with destination: $destination"
            }
        }.also { r ->
            r.onSuccess { logResult("orderRide", it) }
            r.onFailure { logError("orderRide", it) }
        }
    }

    // ==========================================
    // Email Notifications
    // ==========================================

    override suspend fun getEmailNotifications(count: Int): Result<String> {
        logAction("getEmailNotifications", "count=$count")
        return runCatching {
            if (!com.androidclaw.app.service.ClawNotificationListenerService.isConnected) {
                return@runCatching "Notification access is not enabled. Please enable it in Settings > Apps > Special access > Notification access to read email notifications."
            }
            val emails = com.androidclaw.app.service.ClawNotificationListenerService.getEmailNotifications(count)
            if (emails.isEmpty()) {
                "No email notifications found. Make sure you have email app notifications enabled."
            } else {
                val lines = emails.mapIndexed { i, n -> "${i + 1}. ${n.toReadableString()}" }
                "Email notifications (${emails.size}):\n${lines.joinToString("\n")}"
            }
        }
    }

    // ==========================================
    // Generic Deep-Link
    // ==========================================

    override suspend fun openDeepLink(uri: String, packageName: String?, fallbackUrl: String?): Result<String> {
        logAction("openDeepLink", "uri=$uri, pkg=$packageName")
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                packageName?.let { setPackage(it) }
            }
            try {
                context.startActivity(intent)
                "Opened: $uri" + (packageName?.let { " in $it" } ?: "")
            } catch (_: Exception) {
                if (fallbackUrl != null) {
                    val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallback)
                    "App not available, opened fallback URL: $fallbackUrl"
                } else {
                    "Failed to open: $uri. App may not be installed."
                }
            }
        }.also { r ->
            r.onSuccess { logResult("openDeepLink", it) }
            r.onFailure { logError("openDeepLink", it) }
        }
    }

    // ==========================================
    // NEW: Settings Toggles
    // ==========================================

    override suspend fun setAutoBrightness(enabled: Boolean): Result<String> {
        logAction("setAutoBrightness", "enabled=$enabled")
        return runCatching {
            if (Settings.System.canWrite(context)) {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    if (enabled) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                "Auto-brightness ${if (enabled) "enabled" else "disabled"}"
            } else {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Please grant write settings permission, then try again."
            }
        }.also { r ->
            r.onSuccess { logResult("setAutoBrightness", it) }
            r.onFailure { logError("setAutoBrightness", it) }
        }
    }

    override suspend fun setLocationEnabled(enabled: Boolean): Result<String> {
        logAction("setLocationEnabled", "enabled=$enabled")
        return runCatching {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened Location settings. Please toggle location ${if (enabled) "on" else "off"}."
        }.also { r ->
            r.onSuccess { logResult("setLocationEnabled", it) }
            r.onFailure { logError("setLocationEnabled", it) }
        }
    }

    override suspend fun setAirplaneMode(enabled: Boolean): Result<String> {
        logAction("setAirplaneMode", "enabled=$enabled")
        return runCatching {
            val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened Airplane mode settings. Please toggle airplane mode ${if (enabled) "on" else "off"}."
        }.also { r ->
            r.onSuccess { logResult("setAirplaneMode", it) }
            r.onFailure { logError("setAirplaneMode", it) }
        }
    }

    override suspend fun setHotspot(enabled: Boolean): Result<String> {
        logAction("setHotspot", "enabled=$enabled")
        return runCatching {
            val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened wireless settings. Please toggle hotspot ${if (enabled) "on" else "off"}."
        }.also { r ->
            r.onSuccess { logResult("setHotspot", it) }
            r.onFailure { logError("setHotspot", it) }
        }
    }

    override suspend fun setMobileData(enabled: Boolean): Result<String> {
        logAction("setMobileData", "enabled=$enabled")
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val intent = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opened internet connectivity panel. Please toggle mobile data ${if (enabled) "on" else "off"}."
            } else {
                val intent = Intent(Settings.ACTION_DATA_ROAMING_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opened mobile data settings. Please toggle mobile data ${if (enabled) "on" else "off"}."
            }
        }.also { r ->
            r.onSuccess { logResult("setMobileData", it) }
            r.onFailure { logError("setMobileData", it) }
        }
    }

    override suspend fun openNfcSettings(): Result<String> {
        logAction("openNfcSettings")
        return runCatching {
            val intent = Intent(Settings.ACTION_NFC_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened NFC settings."
        }.also { r ->
            r.onSuccess { logResult("openNfcSettings", it) }
            r.onFailure { logError("openNfcSettings", it) }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun getWifiInfo(): Result<String> {
        logAction("getWifiInfo")
        return runCatching {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wifiManager.connectionInfo
            val dhcp = wifiManager.dhcpInfo
            buildString {
                appendLine("Wi-Fi Information:")
                @Suppress("DEPRECATION")
                appendLine("  SSID: ${info.ssid?.replace("\"", "") ?: "Unknown"}")
                appendLine("  BSSID: ${info.bssid ?: "Unknown"}")
                appendLine("  Link speed: ${info.linkSpeed} Mbps")
                appendLine("  RSSI: ${info.rssi} dBm")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    appendLine("  Frequency: ${info.frequency} MHz")
                }
                @Suppress("DEPRECATION")
                val ip = info.ipAddress
                if (ip != 0) {
                    val ipStr = "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
                    appendLine("  IP: $ipStr")
                }
                appendLine("  Wi-Fi enabled: ${wifiManager.isWifiEnabled}")
            }.trim()
        }.also { r ->
            r.onSuccess { logResult("getWifiInfo", it) }
            r.onFailure { logError("getWifiInfo", it) }
        }
    }

    override suspend fun getVolumeInfo(): Result<String> {
        logAction("getVolumeInfo")
        return runCatching {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            buildString {
                appendLine("Volume levels:")
                val streams = mapOf(
                    "Media" to AudioManager.STREAM_MUSIC,
                    "Ring" to AudioManager.STREAM_RING,
                    "Alarm" to AudioManager.STREAM_ALARM,
                    "Notification" to AudioManager.STREAM_NOTIFICATION,
                    "System" to AudioManager.STREAM_SYSTEM,
                    "Voice Call" to AudioManager.STREAM_VOICE_CALL
                )
                for ((name, stream) in streams) {
                    val current = audioManager.getStreamVolume(stream)
                    val max = audioManager.getStreamMaxVolume(stream)
                    val pct = if (max > 0) (current * 100) / max else 0
                    appendLine("  $name: $current/$max ($pct%)")
                }
                appendLine("  Ringer mode: ${when (audioManager.ringerMode) {
                    AudioManager.RINGER_MODE_NORMAL -> "Normal"
                    AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
                    AudioManager.RINGER_MODE_SILENT -> "Silent"
                    else -> "Unknown"
                }}")
            }.trim()
        }.also { r ->
            r.onSuccess { logResult("getVolumeInfo", it) }
            r.onFailure { logError("getVolumeInfo", it) }
        }
    }

    // ==========================================
    // NEW: Alarm/Timer Management
    // ==========================================

    override suspend fun listAlarms(): Result<String> {
        logAction("listAlarms")
        return runCatching {
            // Android doesn't provide a direct API to query alarms set in the Clock app.
            // We open the alarm app so the user can see them.
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened alarms list in Clock app."
        }.also { r ->
            r.onSuccess { logResult("listAlarms", it) }
            r.onFailure { logError("listAlarms", it) }
        }
    }

    override suspend fun cancelTimer(): Result<String> {
        logAction("cancelTimer")
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(AlarmClock.ACTION_DISMISS_TIMER).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Timer dismissed."
            } else {
                val intent = Intent(AlarmClock.ACTION_SHOW_TIMERS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opened timers. Please cancel the timer manually."
            }
        }.also { r ->
            r.onSuccess { logResult("cancelTimer", it) }
            r.onFailure { logError("cancelTimer", it) }
        }
    }

    override suspend fun getReminders(): Result<String> = withContext(Dispatchers.IO) {
        logAction("getReminders")
        runCatching {
            if (!hasPermission(Manifest.permission.READ_CALENDAR)) {
                return@runCatching "Calendar permission not granted. Reminders are stored as calendar events."
            }

            val now = System.currentTimeMillis()
            val end = now + 30L * 24 * 60 * 60 * 1000 // 30 days ahead

            val reminders = mutableListOf<String>()
            val projection = arrayOf(
                CalendarContract.Reminders._ID,
                CalendarContract.Reminders.TITLE,
                CalendarContract.Reminders.DTSTART,
                CalendarContract.Reminders.MINUTES
            )

            // Query calendar events that might be reminders (all-day or short events)
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DESCRIPTION
                ),
                "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?",
                arrayOf(now.toString(), end.toString()),
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CalendarContract.Events._ID)
                val titleIdx = cursor.getColumnIndex(CalendarContract.Events.TITLE)
                val startIdx = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
                var count = 0
                while (cursor.moveToNext() && count < 20) {
                    val id = cursor.getLong(idIdx)
                    val title = cursor.getString(titleIdx) ?: "Untitled"
                    val start = cursor.getLong(startIdx)
                    val date = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(start))
                    reminders.add("- [ID:$id] $title ($date)")
                    count++
                }
            }

            if (reminders.isEmpty()) {
                "No upcoming reminders/events found."
            } else {
                buildString {
                    appendLine("Upcoming reminders/events (${reminders.size}):")
                    reminders.forEach { appendLine(it) }
                }.trim()
            }
        }.also { r ->
            r.onSuccess { logResult("getReminders", it) }
            r.onFailure { logError("getReminders", it) }
        }
    }

    override suspend fun deleteReminder(id: String): Result<String> = withContext(Dispatchers.IO) {
        logAction("deleteReminder", "id=$id")
        runCatching {
            if (!hasPermission(Manifest.permission.WRITE_CALENDAR)) {
                return@runCatching "Calendar write permission not granted."
            }
            val eventId = id.toLongOrNull()
                ?: return@runCatching "Invalid reminder ID: $id"
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            val deleted = context.contentResolver.delete(uri, null, null)
            if (deleted > 0) "Reminder deleted (ID: $id)" else "Reminder not found (ID: $id)"
        }.also { r ->
            r.onSuccess { logResult("deleteReminder", it) }
            r.onFailure { logError("deleteReminder", it) }
        }
    }

    // ==========================================
    // NEW: Contact Management
    // ==========================================

    override suspend fun editContact(name: String, newPhone: String, newEmail: String): Result<String> = withContext(Dispatchers.IO) {
        logAction("editContact", "name=$name")
        runCatching {
            if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
                return@runCatching "Contacts permission not granted."
            }

            // Find contact by name
            var contactId: Long = -1
            val uri = ContactsContract.Contacts.CONTENT_URI
            val selection = "${ContactsContract.Contacts.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$name%")
            context.contentResolver.query(uri, arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME),
                selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    contactId = cursor.getLong(cursor.getColumnIndex(ContactsContract.Contacts._ID))
                }
            }

            if (contactId == -1L) {
                return@runCatching "Contact not found: $name"
            }

            // Open contact for editing
            val contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
            val intent = Intent(Intent.ACTION_EDIT).apply {
                data = contactUri
                if (newPhone.isNotEmpty()) putExtra(ContactsContract.Intents.Insert.PHONE, newPhone)
                if (newEmail.isNotEmpty()) putExtra(ContactsContract.Intents.Insert.EMAIL, newEmail)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening contact editor for $name"
        }.also { r ->
            r.onSuccess { logResult("editContact", it) }
            r.onFailure { logError("editContact", it) }
        }
    }

    override suspend fun deleteContact(name: String): Result<String> = withContext(Dispatchers.IO) {
        logAction("deleteContact", "name=$name")
        runCatching {
            if (!hasPermission(Manifest.permission.READ_CONTACTS) || !hasPermission(Manifest.permission.WRITE_CONTACTS)) {
                return@runCatching "Contacts read/write permission not granted."
            }

            // Find contact by name
            var contactId: Long = -1
            var displayName = ""
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME),
                "${ContactsContract.Contacts.DISPLAY_NAME} LIKE ?",
                arrayOf("%$name%"),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    contactId = cursor.getLong(cursor.getColumnIndex(ContactsContract.Contacts._ID))
                    displayName = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)) ?: name
                }
            }

            if (contactId == -1L) {
                return@runCatching "Contact not found: $name"
            }

            // Open contact for viewing — let user confirm deletion from there
            val contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = contactUri
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened contact '$displayName'. Please use the delete option in the contact details."
        }.also { r ->
            r.onSuccess { logResult("deleteContact", it) }
            r.onFailure { logError("deleteContact", it) }
        }
    }

    override suspend fun getFavoriteContacts(): Result<String> = withContext(Dispatchers.IO) {
        logAction("getFavoriteContacts")
        runCatching {
            if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
                return@runCatching "Contacts permission not granted."
            }

            val favorites = mutableListOf<String>()
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.STARRED
                ),
                "${ContactsContract.CommonDataKinds.Phone.STARRED} = 1",
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val phoneIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val cName = cursor.getString(nameIdx) ?: "Unknown"
                    val phone = cursor.getString(phoneIdx) ?: ""
                    favorites.add("- $cName: $phone")
                }
            }

            if (favorites.isEmpty()) {
                "No favorite contacts found."
            } else {
                buildString {
                    appendLine("Favorite contacts (${favorites.size}):")
                    favorites.forEach { appendLine(it) }
                }.trim()
            }
        }.also { r ->
            r.onSuccess { logResult("getFavoriteContacts", it) }
            r.onFailure { logError("getFavoriteContacts", it) }
        }
    }

    // ==========================================
    // NEW: SMS Management
    // ==========================================

    override suspend fun searchSms(query: String, count: Int): Result<String> = withContext(Dispatchers.IO) {
        logAction("searchSms", "query=$query, count=$count")
        runCatching {
            if (!hasPermission(Manifest.permission.READ_SMS)) {
                return@runCatching "SMS permission not granted."
            }

            val messages = mutableListOf<String>()
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE
                ),
                "${Telephony.Sms.BODY} LIKE ?",
                arrayOf("%$query%"),
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val addrIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                val typeIdx = cursor.getColumnIndex(Telephony.Sms.TYPE)
                var c = 0
                while (cursor.moveToNext() && c < count) {
                    val addr = cursor.getString(addrIdx) ?: "Unknown"
                    val body = cursor.getString(bodyIdx) ?: ""
                    val date = cursor.getLong(dateIdx)
                    val type = cursor.getInt(typeIdx)
                    val direction = if (type == Telephony.Sms.MESSAGE_TYPE_INBOX) "from" else "to"
                    val dateStr = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(date))
                    messages.add("- [$dateStr] $direction $addr: ${body.take(100)}")
                    c++
                }
            }

            if (messages.isEmpty()) {
                "No messages found matching \"$query\""
            } else {
                buildString {
                    appendLine("Messages matching \"$query\" (${messages.size}):")
                    messages.forEach { appendLine(it) }
                }.trim()
            }
        }.also { r ->
            r.onSuccess { logResult("searchSms", it) }
            r.onFailure { logError("searchSms", it) }
        }
    }

    override suspend fun deleteSmsConversation(contactName: String): Result<String> {
        logAction("deleteSmsConversation", "contact=$contactName")
        return runCatching {
            // Open the default SMS app — direct deletion requires DEFAULT_SMS_APP role
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_MESSAGING)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened messaging app. Please find and delete the conversation with $contactName."
        }.also { r ->
            r.onSuccess { logResult("deleteSmsConversation", it) }
            r.onFailure { logError("deleteSmsConversation", it) }
        }
    }

    // ==========================================
    // NEW: Calendar Management
    // ==========================================

    override suspend fun deleteCalendarEvent(eventId: String): Result<String> = withContext(Dispatchers.IO) {
        logAction("deleteCalendarEvent", "eventId=$eventId")
        runCatching {
            if (!hasPermission(Manifest.permission.WRITE_CALENDAR)) {
                return@runCatching "Calendar write permission not granted."
            }
            val id = eventId.toLongOrNull()
                ?: return@runCatching "Invalid event ID: $eventId"
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
            val deleted = context.contentResolver.delete(uri, null, null)
            if (deleted > 0) "Calendar event deleted (ID: $eventId)" else "Event not found (ID: $eventId)"
        }.also { r ->
            r.onSuccess { logResult("deleteCalendarEvent", it) }
            r.onFailure { logError("deleteCalendarEvent", it) }
        }
    }

    override suspend fun searchCalendarEvents(query: String, daysAhead: Int): Result<String> = withContext(Dispatchers.IO) {
        logAction("searchCalendarEvents", "query=$query, daysAhead=$daysAhead")
        runCatching {
            if (!hasPermission(Manifest.permission.READ_CALENDAR)) {
                return@runCatching "Calendar permission not granted."
            }

            val now = System.currentTimeMillis()
            val end = now + daysAhead * 24L * 60 * 60 * 1000
            val events = mutableListOf<String>()

            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND,
                    CalendarContract.Events.EVENT_LOCATION
                ),
                "${CalendarContract.Events.TITLE} LIKE ? AND ${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?",
                arrayOf("%$query%", now.toString(), end.toString()),
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CalendarContract.Events._ID)
                val titleIdx = cursor.getColumnIndex(CalendarContract.Events.TITLE)
                val startIdx = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
                val endIdx = cursor.getColumnIndex(CalendarContract.Events.DTEND)
                val locIdx = cursor.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                var count = 0
                while (cursor.moveToNext() && count < 20) {
                    val id = cursor.getLong(idIdx)
                    val title = cursor.getString(titleIdx) ?: "Untitled"
                    val start = cursor.getLong(startIdx)
                    val endTime = cursor.getLong(endIdx)
                    val location = cursor.getString(locIdx) ?: ""
                    val startDate = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(start))
                    val endDate = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(endTime))
                    val entry = buildString {
                        append("- [ID:$id] $title ($startDate - $endDate)")
                        if (location.isNotEmpty()) append(" @ $location")
                    }
                    events.add(entry)
                    count++
                }
            }

            if (events.isEmpty()) {
                "No events found matching \"$query\" in the next $daysAhead days"
            } else {
                buildString {
                    appendLine("Events matching \"$query\" (${events.size}):")
                    events.forEach { appendLine(it) }
                }.trim()
            }
        }.also { r ->
            r.onSuccess { logResult("searchCalendarEvents", it) }
            r.onFailure { logError("searchCalendarEvents", it) }
        }
    }

    // ==========================================
    // NEW: Phone Management
    // ==========================================

    override suspend fun blockNumber(phoneNumber: String): Result<String> {
        logAction("blockNumber", "phone=$phoneNumber")
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val intent = Intent(android.telecom.TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    // Try the direct block number intent on supported devices
                    val blockIntent = Intent("android.provider.action.MANAGE_BLOCKED_NUMBERS").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(blockIntent)
                    "Opened blocked numbers settings. Please add $phoneNumber to the blocked list."
                } catch (_: Exception) {
                    context.startActivity(intent)
                    "Opened phone settings. Please navigate to blocked numbers and add $phoneNumber."
                }
            } else {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opened call settings. Please find the block number option and add $phoneNumber."
            }
        }.also { r ->
            r.onSuccess { logResult("blockNumber", it) }
            r.onFailure { logError("blockNumber", it) }
        }
    }

    override suspend fun checkVoicemail(): Result<String> {
        logAction("checkVoicemail")
        return runCatching {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("voicemail:")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                "Opening voicemail..."
            } catch (_: Exception) {
                // Fallback: open the Phone app
                val fallback = Intent(Intent.ACTION_DIAL).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                "Opened phone dialer. Please check your voicemail from there."
            }
        }.also { r ->
            r.onSuccess { logResult("checkVoicemail", it) }
            r.onFailure { logError("checkVoicemail", it) }
        }
    }

    // ==========================================
    // NEW: Notification Interaction
    // ==========================================

    override suspend fun replyToNotification(key: String, text: String): Result<String> {
        logAction("replyToNotification", "key=$key, textLen=${text.length}")
        return runCatching {
            val service = com.androidclaw.app.service.ClawNotificationListenerService.instance
                ?: return@runCatching "Notification listener service is not active. Enable it in Settings > Apps > Special access > Notification access."

            val notifications = com.androidclaw.app.service.ClawNotificationListenerService.getRecent(50)
            val target = notifications.find { it.key == key }
                ?: return@runCatching "Notification not found: $key"

            // Find the remote input action (reply action)
            val sbn = service.activeNotifications.find { it.key == key }
                ?: return@runCatching "Notification no longer active: $key"

            val notification = sbn.notification
            val actions = notification.actions ?: return@runCatching "No actions available on this notification."

            // Find an action with RemoteInput (reply-capable)
            for (action in actions) {
                val remoteInputs = action.remoteInputs
                if (remoteInputs != null && remoteInputs.isNotEmpty()) {
                    val intent = Intent()
                    val bundle = android.os.Bundle()
                    for (remoteInput in remoteInputs) {
                        bundle.putCharSequence(remoteInput.resultKey, text)
                    }
                    android.app.RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)
                    action.actionIntent.send(context, 0, intent)
                    return@runCatching "Replied to notification from ${target.appName}: \"$text\""
                }
            }

            "This notification doesn't support direct reply."
        }.also { r ->
            r.onSuccess { logResult("replyToNotification", it) }
            r.onFailure { logError("replyToNotification", it) }
        }
    }

    // ==========================================
    // NEW: Screen Time / Usage Stats
    // ==========================================

    override suspend fun getScreenTime(days: Int): Result<String> = withContext(Dispatchers.IO) {
        logAction("getScreenTime", "days=$days")
        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
                return@runCatching "Screen time requires Android 5.1+"
            }

            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
                ?: return@runCatching "Usage stats service not available"

            val end = System.currentTimeMillis()
            val start = end - days * 24L * 60 * 60 * 1000

            val stats = usageStatsManager.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_DAILY, start, end
            )

            if (stats.isNullOrEmpty()) {
                return@runCatching "No usage data available. Please enable Usage Access in Settings > Apps > Special access > Usage access."
            }

            var totalTime = 0L
            for (stat in stats) {
                totalTime += stat.totalTimeInForeground
            }

            val hours = totalTime / (1000 * 60 * 60)
            val minutes = (totalTime / (1000 * 60)) % 60

            buildString {
                appendLine("Screen time (last ${if (days == 1) "24 hours" else "$days days"}):")
                appendLine("  Total: ${hours}h ${minutes}m")

                // Top apps by usage
                val topApps = stats
                    .filter { it.totalTimeInForeground > 60_000 } // > 1 minute
                    .sortedByDescending { it.totalTimeInForeground }
                    .take(10)

                if (topApps.isNotEmpty()) {
                    appendLine("\nTop apps:")
                    val pm = context.packageManager
                    for (app in topApps) {
                        val appName = try {
                            pm.getApplicationLabel(pm.getApplicationInfo(app.packageName, 0)).toString()
                        } catch (_: Exception) {
                            APP_NAMES[app.packageName] ?: app.packageName
                        }
                        val appHours = app.totalTimeInForeground / (1000 * 60 * 60)
                        val appMins = (app.totalTimeInForeground / (1000 * 60)) % 60
                        appendLine("  - $appName: ${appHours}h ${appMins}m")
                    }
                }
            }.trim()
        }.also { r ->
            r.onSuccess { logResult("getScreenTime", it) }
            r.onFailure { logError("getScreenTime", it) }
        }
    }

    override suspend fun getAppUsageStats(days: Int): Result<String> = withContext(Dispatchers.IO) {
        logAction("getAppUsageStats", "days=$days")
        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
                return@runCatching "App usage stats require Android 5.1+"
            }

            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
                ?: return@runCatching "Usage stats service not available"

            val end = System.currentTimeMillis()
            val start = end - days * 24L * 60 * 60 * 1000

            val stats = usageStatsManager.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_DAILY, start, end
            )

            if (stats.isNullOrEmpty()) {
                return@runCatching "No usage data available. Please enable Usage Access in Settings > Apps > Special access > Usage access."
            }

            // Aggregate by package
            val aggregated = mutableMapOf<String, Long>()
            for (stat in stats) {
                if (stat.totalTimeInForeground > 0) {
                    aggregated[stat.packageName] = (aggregated[stat.packageName] ?: 0L) + stat.totalTimeInForeground
                }
            }

            val sorted = aggregated.entries
                .filter { it.value > 60_000 } // > 1 minute
                .sortedByDescending { it.value }
                .take(20)

            if (sorted.isEmpty()) {
                return@runCatching "No significant app usage recorded in the last ${if (days == 1) "24 hours" else "$days days"}."
            }

            val pm = context.packageManager
            buildString {
                appendLine("App usage (last ${if (days == 1) "24 hours" else "$days days"}):")
                for (entry in sorted) {
                    val appName = try {
                        pm.getApplicationLabel(pm.getApplicationInfo(entry.key, 0)).toString()
                    } catch (_: Exception) {
                        APP_NAMES[entry.key] ?: entry.key
                    }
                    val hours = entry.value / (1000 * 60 * 60)
                    val mins = (entry.value / (1000 * 60)) % 60
                    appendLine("  - $appName: ${hours}h ${mins}m")
                }
            }.trim()
        }.also { r ->
            r.onSuccess { logResult("getAppUsageStats", it) }
            r.onFailure { logError("getAppUsageStats", it) }
        }
    }

    override suspend fun getBatteryUsageStats(): Result<String> {
        logAction("getBatteryUsageStats")
        return runCatching {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            val batteryIntent = context.registerReceiver(null,
                android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))

            buildString {
                appendLine("Battery Information:")

                val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
                if (level >= 0 && scale > 0) {
                    appendLine("  Level: ${(level * 100) / scale}%")
                }

                val plugged = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) ?: 0
                val chargingSource = when (plugged) {
                    android.os.BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                    android.os.BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                    android.os.BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                    else -> "Not charging"
                }
                appendLine("  Charging: $chargingSource")

                val status = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
                val statusStr = when (status) {
                    android.os.BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                    android.os.BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                    android.os.BatteryManager.BATTERY_STATUS_FULL -> "Full"
                    android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
                    else -> "Unknown"
                }
                appendLine("  Status: $statusStr")

                val health = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_HEALTH, -1) ?: -1
                val healthStr = when (health) {
                    android.os.BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                    android.os.BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                    android.os.BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                    android.os.BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
                    android.os.BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                    else -> "Unknown"
                }
                appendLine("  Health: $healthStr")

                val temp = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
                if (temp > 0) {
                    appendLine("  Temperature: ${temp / 10.0}°C")
                }

                val voltage = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
                if (voltage > 0) {
                    appendLine("  Voltage: ${voltage / 1000.0}V")
                }

                val technology = batteryIntent?.getStringExtra(android.os.BatteryManager.EXTRA_TECHNOLOGY)
                if (!technology.isNullOrEmpty()) {
                    appendLine("  Technology: $technology")
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val remaining = batteryManager.computeChargeTimeRemaining()
                    if (remaining > 0) {
                        val hrs = remaining / (1000 * 60 * 60)
                        val mins = (remaining / (1000 * 60)) % 60
                        appendLine("  Time to full: ${hrs}h ${mins}m")
                    }
                }

                // Open battery usage settings for detailed per-app usage
                appendLine("\nFor detailed per-app battery usage, opening battery settings...")
            }.trim().also {
                try {
                    val intent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    // Not all devices support this intent
                }
            }
        }.also { r ->
            r.onSuccess { logResult("getBatteryUsageStats", it) }
            r.onFailure { logError("getBatteryUsageStats", it) }
        }
    }

    // ==========================================
    // NEW: Accessibility Extended
    // ==========================================

    override suspend fun setTalkBack(enabled: Boolean): Result<String> {
        logAction("setTalkBack", "enabled=$enabled")
        return runCatching {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened Accessibility settings. Please ${if (enabled) "enable" else "disable"} TalkBack."
        }.also { r ->
            r.onSuccess { logResult("setTalkBack", it) }
            r.onFailure { logError("setTalkBack", it) }
        }
    }

    override suspend fun setDisplaySize(scale: String): Result<String> {
        logAction("setDisplaySize", "scale=$scale")
        return runCatching {
            val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened Display settings. Please adjust the display size to '$scale'."
        }.also { r ->
            r.onSuccess { logResult("setDisplaySize", it) }
            r.onFailure { logError("setDisplaySize", it) }
        }
    }

    override suspend fun setHighContrast(enabled: Boolean): Result<String> {
        logAction("setHighContrast", "enabled=$enabled")
        return runCatching {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened Accessibility settings. Please ${if (enabled) "enable" else "disable"} high contrast text."
        }.also { r ->
            r.onSuccess { logResult("setHighContrast", it) }
            r.onFailure { logError("setHighContrast", it) }
        }
    }

    override suspend fun getAccessibilitySettings(): Result<String> {
        logAction("getAccessibilitySettings")
        return runCatching {
            buildString {
                appendLine("Accessibility Settings:")
                // Font scale
                val fontScale = Settings.System.getFloat(
                    context.contentResolver,
                    Settings.System.FONT_SCALE,
                    1.0f
                )
                appendLine("  Font scale: $fontScale")

                // Color inversion
                val colorInversion = try {
                    Settings.Secure.getInt(
                        context.contentResolver,
                        Settings.Secure.ACCESSIBILITY_DISPLAY_INVERSION_ENABLED
                    ) == 1
                } catch (_: Exception) { false }
                appendLine("  Color inversion: ${if (colorInversion) "On" else "Off"}")

                // Magnification
                val magnification = try {
                    Settings.Secure.getInt(
                        context.contentResolver,
                        "accessibility_display_magnification_enabled"
                    ) == 1
                } catch (_: Exception) { false }
                appendLine("  Magnification: ${if (magnification) "On" else "Off"}")

                // Touch & hold delay
                val longPressTimeout = Settings.Secure.getInt(
                    context.contentResolver,
                    "long_press_timeout",
                    400
                )
                appendLine("  Long press timeout: ${longPressTimeout}ms")

                // High contrast
                val highContrast = try {
                    Settings.Secure.getInt(
                        context.contentResolver,
                        "high_text_contrast_enabled"
                    ) == 1
                } catch (_: Exception) { false }
                appendLine("  High contrast text: ${if (highContrast) "On" else "Off"}")

                // TalkBack (check enabled accessibility services)
                val enabledServices = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: ""
                val talkBackActive = enabledServices.contains("talkback", ignoreCase = true)
                appendLine("  TalkBack: ${if (talkBackActive) "On" else "Off"}")
            }.trim()
        }.also { r ->
            r.onSuccess { logResult("getAccessibilitySettings", it) }
            r.onFailure { logError("getAccessibilitySettings", it) }
        }
    }

    // ==========================================
    // NEW: Ringtone / Sound Management
    // ==========================================

    override suspend fun setRingtone(uri: String): Result<String> {
        logAction("setRingtone", "uri=$uri")
        return runCatching {
            if (uri.isBlank()) {
                // Open ringtone picker
                val intent = Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_RINGTONE)
                    putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Ringtone")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opened ringtone picker. Please select a ringtone."
            } else {
                android.media.RingtoneManager.setActualDefaultRingtoneUri(
                    context,
                    android.media.RingtoneManager.TYPE_RINGTONE,
                    Uri.parse(uri)
                )
                "Ringtone set successfully."
            }
        }.also { r ->
            r.onSuccess { logResult("setRingtone", it) }
            r.onFailure { logError("setRingtone", it) }
        }
    }

    override suspend fun setNotificationSound(uri: String): Result<String> {
        logAction("setNotificationSound", "uri=$uri")
        return runCatching {
            if (uri.isBlank()) {
                val intent = Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_NOTIFICATION)
                    putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Notification Sound")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opened notification sound picker. Please select a sound."
            } else {
                android.media.RingtoneManager.setActualDefaultRingtoneUri(
                    context,
                    android.media.RingtoneManager.TYPE_NOTIFICATION,
                    Uri.parse(uri)
                )
                "Notification sound set successfully."
            }
        }.also { r ->
            r.onSuccess { logResult("setNotificationSound", it) }
            r.onFailure { logError("setNotificationSound", it) }
        }
    }

    // ==========================================
    // NEW: Power Management
    // ==========================================

    override suspend fun schedulePowerOff(hour: Int, minute: Int): Result<String> {
        logAction("schedulePowerOff", "hour=$hour, minute=$minute")
        return runCatching {
            // Schedule power off via alarm intent (requires device admin or OEM support)
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, "Scheduled Power Off")
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Created alarm at %02d:%02d. Note: Android doesn't support automatic power off — this alarm will remind you to shut down.".format(hour, minute)
        }.also { r ->
            r.onSuccess { logResult("schedulePowerOff", it) }
            r.onFailure { logError("schedulePowerOff", it) }
        }
    }

    // ==========================================
    // NEW: Network Diagnostics
    // ==========================================

    @SuppressLint("MissingPermission")
    override suspend fun getSignalStrength(): Result<String> {
        logAction("getSignalStrength")
        return runCatching {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
                ?: return@runCatching "Telephony service not available"

            buildString {
                appendLine("Signal Strength:")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val signalStrength = telephonyManager.signalStrength
                    if (signalStrength != null) {
                        appendLine("  Level: ${signalStrength.level}/4")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            for (cellSignal in signalStrength.cellSignalStrengths) {
                                appendLine("  dBm: ${cellSignal.dbm}")
                                appendLine("  ASU: ${cellSignal.asuLevel}")
                            }
                        }
                    } else {
                        appendLine("  Unable to read signal strength")
                    }
                } else {
                    appendLine("  Signal strength details require Android 9+")
                }

                // Wi-Fi signal
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val wifiInfo = wifiManager?.connectionInfo
                if (wifiInfo != null && wifiInfo.networkId != -1) {
                    val rssi = wifiInfo.rssi
                    val wifiLevel = WifiManager.calculateSignalLevel(rssi, 5)
                    appendLine("  Wi-Fi signal: $wifiLevel/4 (RSSI: ${rssi}dBm)")
                }
            }.trim()
        }.also { r ->
            r.onSuccess { logResult("getSignalStrength", it) }
            r.onFailure { logError("getSignalStrength", it) }
        }
    }

    override suspend fun getConnectionType(): Result<String> {
        logAction("getConnectionType")
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            buildString {
                appendLine("Connection Type:")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val network = cm.activeNetwork
                    val capabilities = cm.getNetworkCapabilities(network)
                    if (capabilities != null) {
                        val types = mutableListOf<String>()
                        if (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) types.add("Wi-Fi")
                        if (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)) types.add("Cellular")
                        if (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)) types.add("Ethernet")
                        if (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_BLUETOOTH)) types.add("Bluetooth")
                        if (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) types.add("VPN")
                        appendLine("  Active: ${types.joinToString(", ").ifEmpty { "Unknown" }}")

                        val hasInternet = capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        val validated = capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                        appendLine("  Internet: ${if (hasInternet) "Yes" else "No"}")
                        appendLine("  Validated: ${if (validated) "Yes" else "No"}")

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val downMbps = capabilities.linkDownstreamBandwidthKbps / 1000
                            val upMbps = capabilities.linkUpstreamBandwidthKbps / 1000
                            appendLine("  Estimated speed: ↓${downMbps}Mbps ↑${upMbps}Mbps")
                        }
                    } else {
                        appendLine("  No active network connection")
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val info = cm.activeNetworkInfo
                    appendLine("  Type: ${info?.typeName ?: "None"}")
                    appendLine("  Connected: ${info?.isConnected ?: false}")
                }
            }.trim()
        }.also { r ->
            r.onSuccess { logResult("getConnectionType", it) }
            r.onFailure { logError("getConnectionType", it) }
        }
    }

    override suspend fun getIpAddress(): Result<String> {
        logAction("getIpAddress")
        return runCatching {
            buildString {
                appendLine("IP Addresses:")

                // Wi-Fi IP
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val wifiInfo = wifiManager?.connectionInfo
                if (wifiInfo != null && wifiInfo.ipAddress != 0) {
                    val ip = wifiInfo.ipAddress
                    val ipStr = "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.${ip shr 24 and 0xff}"
                    appendLine("  Wi-Fi: $ipStr")
                }

                // All network interfaces
                try {
                    val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
                    while (interfaces.hasMoreElements()) {
                        val iface = interfaces.nextElement()
                        if (!iface.isUp || iface.isLoopback) continue
                        val addrs = iface.inetAddresses
                        while (addrs.hasMoreElements()) {
                            val addr = addrs.nextElement()
                            if (addr.isLoopbackAddress) continue
                            val hostAddr = addr.hostAddress ?: continue
                            if (addr is java.net.Inet4Address) {
                                appendLine("  ${iface.displayName} (IPv4): $hostAddr")
                            } else if (addr is java.net.Inet6Address && !hostAddr.startsWith("fe80")) {
                                appendLine("  ${iface.displayName} (IPv6): $hostAddr")
                            }
                        }
                    }
                } catch (e: Exception) {
                    appendLine("  Could not enumerate network interfaces: ${e.message}")
                }
            }.trim()
        }.also { r ->
            r.onSuccess { logResult("getIpAddress", it) }
            r.onFailure { logError("getIpAddress", it) }
        }
    }

    override suspend fun pingHost(host: String): Result<String> {
        logAction("pingHost", "host=$host")
        return withContext(Dispatchers.IO) {
            runCatching {
                val sanitizedHost = host.replace(Regex("[^a-zA-Z0-9.\\-]"), "")
                val process = Runtime.getRuntime().exec(arrayOf("/system/bin/ping", "-c", "4", "-W", "5", sanitizedHost))
                val output = process.inputStream.bufferedReader().readText()
                val error = process.errorStream.bufferedReader().readText()
                process.waitFor()

                if (output.isNotBlank()) {
                    "Ping $sanitizedHost:\n$output"
                } else if (error.isNotBlank()) {
                    "Ping failed: $error"
                } else {
                    "Ping to $sanitizedHost: no response"
                }
            }.also { r ->
                r.onSuccess { logResult("pingHost", it) }
                r.onFailure { logError("pingHost", it) }
            }
        }
    }

    // ==========================================
    // NEW: Default Apps
    // ==========================================

    override suspend fun setDefaultBrowser(packageName: String): Result<String> {
        logAction("setDefaultBrowser", "pkg=$packageName")
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opened Default Apps settings. Please set '$packageName' as the default browser."
            } else {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opened Settings. Please navigate to Apps > Default Apps to change the browser."
            }
        }.also { r ->
            r.onSuccess { logResult("setDefaultBrowser", it) }
            r.onFailure { logError("setDefaultBrowser", it) }
        }
    }

    override suspend fun setDefaultLauncher(packageName: String): Result<String> {
        logAction("setDefaultLauncher", "pkg=$packageName")
        return runCatching {
            val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened Home app settings. Please select '$packageName' as your default launcher."
        }.also { r ->
            r.onSuccess { logResult("setDefaultLauncher", it) }
            r.onFailure { logError("setDefaultLauncher", it) }
        }
    }

    // ==========================================
    // NEW: Focus Modes
    // ==========================================

    override suspend fun setDrivingMode(enabled: Boolean): Result<String> {
        logAction("setDrivingMode", "enabled=$enabled")
        return runCatching {
            // Try Android Auto / Driving mode / DND with driving rule
            try {
                // Try opening Android Auto settings
                val autoIntent = Intent().apply {
                    setClassName("com.google.android.projection.gearhead", "com.google.android.apps.auto.Auto")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(autoIntent)
                "Opened Android Auto. Driving mode ${if (enabled) "activating" else "deactivating"}."
            } catch (_: Exception) {
                // Fallback: enable DND as driving mode proxy
                if (enabled) {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (nm.isNotificationPolicyAccessGranted) {
                        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                        "Driving mode enabled (Do Not Disturb - Priority only). Calls from favorites will still ring."
                    } else {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        "Please grant DND access to enable driving mode."
                    }
                } else {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (nm.isNotificationPolicyAccessGranted) {
                        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                        "Driving mode disabled. All notifications restored."
                    } else {
                        "DND access not granted. Cannot disable driving mode."
                    }
                }
            }
        }.also { r ->
            r.onSuccess { logResult("setDrivingMode", it) }
            r.onFailure { logError("setDrivingMode", it) }
        }
    }

    // ==========================================
    // System Diagnostics
    // ==========================================

    override suspend fun getCpuInfo(): Result<String> {
        logAction("getCpuInfo")
        return runCatching {
            val sb = StringBuilder()
            // Read /proc/cpuinfo for processor details
            try {
                val cpuInfo = java.io.File("/proc/cpuinfo").readText()
                val cores = Runtime.getRuntime().availableProcessors()
                sb.appendLine("CPU Cores: $cores")
                // Extract model name
                cpuInfo.lines().firstOrNull { it.startsWith("Hardware") || it.startsWith("model name") }?.let {
                    sb.appendLine(it.trim())
                }
                // Extract CPU architecture
                sb.appendLine("Architecture: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
            } catch (_: Exception) {
                sb.appendLine("CPU cores: ${Runtime.getRuntime().availableProcessors()}")
                sb.appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
            }
            // Read /proc/stat for usage
            try {
                val stat = java.io.File("/proc/stat").readLines().first()
                sb.appendLine("CPU stat: $stat")
            } catch (_: Exception) {}
            sb.toString().trim()
        }.also { r ->
            r.onSuccess { logResult("getCpuInfo", it) }
            r.onFailure { logError("getCpuInfo", it) }
        }
    }

    override suspend fun getSensorList(): Result<String> {
        logAction("getSensorList")
        return runCatching {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
            val sensors = sm.getSensorList(android.hardware.Sensor.TYPE_ALL)
            val grouped = sensors.groupBy { it.type }
            val sb = StringBuilder("Sensors (${sensors.size} total):\n")
            for ((type, list) in grouped) {
                for (s in list) {
                    sb.appendLine("• ${s.name} (type=$type, vendor=${s.vendor}, power=${s.power}mA)")
                }
            }
            sb.toString().trim()
        }.also { r ->
            r.onSuccess { logResult("getSensorList", it) }
            r.onFailure { logError("getSensorList", it) }
        }
    }

    override suspend fun getThermalInfo(): Result<String> {
        logAction("getThermalInfo")
        return runCatching {
            val sb = StringBuilder("Thermal Info:\n")
            // Read thermal zones
            val thermalDir = java.io.File("/sys/class/thermal/")
            if (thermalDir.exists()) {
                thermalDir.listFiles()?.filter { it.name.startsWith("thermal_zone") }?.forEach { zone ->
                    try {
                        val temp = java.io.File(zone, "temp").readText().trim().toLongOrNull()
                        val type = java.io.File(zone, "type").readText().trim()
                        val tempC = if (temp != null && temp > 1000) temp / 1000.0 else temp?.toDouble()
                        sb.appendLine("• $type: ${tempC}°C")
                    } catch (_: Exception) {}
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                val thermal = pm.currentThermalStatus
                val statusName = when (thermal) {
                    0 -> "None"
                    1 -> "Light"
                    2 -> "Moderate"
                    3 -> "Severe"
                    4 -> "Critical"
                    5 -> "Emergency"
                    6 -> "Shutdown"
                    else -> "Unknown"
                }
                sb.appendLine("Thermal status: $statusName")
            }
            sb.toString().trim()
        }.also { r ->
            r.onSuccess { logResult("getThermalInfo", it) }
            r.onFailure { logError("getThermalInfo", it) }
        }
    }

    override suspend fun getProcessList(): Result<String> {
        logAction("getProcessList")
        return runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val runningApps = am.runningAppProcesses ?: emptyList()
            val sb = StringBuilder("Running processes (${runningApps.size}):\n")
            for (proc in runningApps.take(30)) {
                val importance = when (proc.importance) {
                    android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "Foreground"
                    android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "Visible"
                    android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "Service"
                    android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "Cached"
                    else -> "Background"
                }
                sb.appendLine("• ${proc.processName} ($importance, pid=${proc.pid})")
            }
            if (runningApps.size > 30) sb.appendLine("... and ${runningApps.size - 30} more")
            sb.toString().trim()
        }.also { r ->
            r.onSuccess { logResult("getProcessList", it) }
            r.onFailure { logError("getProcessList", it) }
        }
    }

    override suspend fun getStorageBreakdown(): Result<String> {
        logAction("getStorageBreakdown")
        return runCatching {
            val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
            val total = stat.totalBytes
            val free = stat.availableBytes
            val used = total - free
            val sb = StringBuilder("Storage Breakdown:\n")
            sb.appendLine("Total: ${formatFileSize(total)}")
            sb.appendLine("Used: ${formatFileSize(used)} (${(used * 100 / total)}%)")
            sb.appendLine("Free: ${formatFileSize(free)}")

            // Check common directories
            val dirs = mapOf(
                "DCIM" to android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DCIM),
                "Downloads" to android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                "Music" to android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC),
                "Movies" to android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES),
                "Pictures" to android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
            )
            sb.appendLine("\nDirectory sizes:")
            for ((name, dir) in dirs) {
                if (dir.exists()) {
                    val size = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    sb.appendLine("• $name: ${formatFileSize(size)}")
                }
            }
            sb.toString().trim()
        }.also { r ->
            r.onSuccess { logResult("getStorageBreakdown", it) }
            r.onFailure { logError("getStorageBreakdown", it) }
        }
    }

    // ==========================================
    // Text-to-Speech Enhanced
    // ==========================================

    override suspend fun ttsSpeak(text: String, language: String, speed: Float, pitch: Float): Result<String> {
        logAction("ttsSpeak", "text=${text.take(50)}, lang=$language, speed=$speed, pitch=$pitch")
        return runCatching {
            suspendCancellableCoroutine<String> { cont ->
                val tts = android.speech.tts.TextToSpeech(context) { status ->
                    if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                        val engine = (context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)
                        // This is the TTS object created in callback
                    }
                }
                // Use a handler to allow TTS to init
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        if (language.isNotEmpty()) {
                            val locale = java.util.Locale.forLanguageTag(language)
                            tts.language = locale
                        }
                        tts.setSpeechRate(speed)
                        tts.setPitch(pitch)
                        tts.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "claw_tts")
                        if (cont.isActive) cont.resume("Speaking: \"${text.take(100)}\" (lang=$language, speed=$speed, pitch=$pitch)")
                    } catch (e: Exception) {
                        if (cont.isActive) cont.resume("TTS error: ${e.message}")
                    }
                }, 500)
            }
        }.also { r ->
            r.onSuccess { logResult("ttsSpeak", it) }
            r.onFailure { logError("ttsSpeak", it) }
        }
    }

    override suspend fun ttsStop(): Result<String> {
        logAction("ttsStop")
        return runCatching {
            // Stop any active TTS by creating a temporary instance and stopping
            val tts = android.speech.tts.TextToSpeech(context) { }
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                tts.stop()
                tts.shutdown()
            }, 200)
            "Text-to-speech stopped."
        }.also { r ->
            r.onSuccess { logResult("ttsStop", it) }
            r.onFailure { logError("ttsStop", it) }
        }
    }

    override suspend fun ttsGetVoices(): Result<String> {
        logAction("ttsGetVoices")
        return runCatching {
            suspendCancellableCoroutine<String> { cont ->
                val tts = android.speech.tts.TextToSpeech(context) { status ->
                    if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                        // handled in delayed block
                    }
                }
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        val voices = tts.voices ?: emptySet()
                        val sb = StringBuilder("Available TTS voices (${voices.size}):\n")
                        for (voice in voices.take(30)) {
                            sb.appendLine("• ${voice.name} (${voice.locale}, quality=${voice.quality})")
                        }
                        if (voices.size > 30) sb.appendLine("... and ${voices.size - 30} more")
                        tts.shutdown()
                        if (cont.isActive) cont.resume(sb.toString().trim())
                    } catch (e: Exception) {
                        tts.shutdown()
                        if (cont.isActive) cont.resume("Failed to get voices: ${e.message}")
                    }
                }, 1000)
            }
        }.also { r ->
            r.onSuccess { logResult("ttsGetVoices", it) }
            r.onFailure { logError("ttsGetVoices", it) }
        }
    }

    // ==========================================
    // DND Granular
    // ==========================================

    override suspend fun setDndMode(mode: String): Result<String> {
        logAction("setDndMode", "mode=$mode")
        return runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.isNotificationPolicyAccessGranted) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return@runCatching "Please grant DND access permission first."
            }
            val filter = when (mode.lowercase()) {
                "priority_only", "priority" -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
                "alarms_only", "alarms" -> NotificationManager.INTERRUPTION_FILTER_ALARMS
                "total_silence", "silence", "none" -> NotificationManager.INTERRUPTION_FILTER_NONE
                "off", "all" -> NotificationManager.INTERRUPTION_FILTER_ALL
                else -> return@runCatching "Unknown DND mode: $mode. Use: priority_only, alarms_only, total_silence, off"
            }
            nm.setInterruptionFilter(filter)
            if (filter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                "Do Not Disturb turned OFF. All notifications enabled."
            } else {
                "Do Not Disturb set to: $mode"
            }
        }.also { r ->
            r.onSuccess { logResult("setDndMode", it) }
            r.onFailure { logError("setDndMode", it) }
        }
    }

    override suspend fun getDndStatus(): Result<String> {
        logAction("getDndStatus")
        return runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val filter = nm.currentInterruptionFilter
            val status = when (filter) {
                NotificationManager.INTERRUPTION_FILTER_ALL -> "Off (all notifications allowed)"
                NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "Priority only"
                NotificationManager.INTERRUPTION_FILTER_ALARMS -> "Alarms only"
                NotificationManager.INTERRUPTION_FILTER_NONE -> "Total silence"
                else -> "Unknown ($filter)"
            }
            val policyAccess = nm.isNotificationPolicyAccessGranted
            "DND Status: $status\nPolicy access granted: $policyAccess"
        }.also { r ->
            r.onSuccess { logResult("getDndStatus", it) }
            r.onFailure { logError("getDndStatus", it) }
        }
    }

    // ==========================================
    // Wi-Fi Management
    // ==========================================

    @SuppressLint("MissingPermission")
    override suspend fun scanWifiNetworks(): Result<String> {
        logAction("scanWifiNetworks")
        return runCatching {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (!wm.isWifiEnabled) {
                return@runCatching "Wi-Fi is disabled. Enable it first."
            }
            @Suppress("DEPRECATION")
            val results = wm.scanResults ?: emptyList()
            val sb = StringBuilder("Wi-Fi Networks (${results.size}):\n")
            for (r in results.sortedByDescending { it.level }.take(20)) {
                val bars = WifiManager.calculateSignalLevel(r.level, 5)
                @Suppress("DEPRECATION")
                sb.appendLine("• ${r.SSID.ifEmpty { "(hidden)" }} (signal: $bars/4, ${r.frequency}MHz)")
            }
            sb.toString().trim()
        }.also { r ->
            r.onSuccess { logResult("scanWifiNetworks", it) }
            r.onFailure { logError("scanWifiNetworks", it) }
        }
    }

    override suspend fun connectToWifi(ssid: String): Result<String> {
        logAction("connectToWifi", "ssid=$ssid")
        return runCatching {
            // For security, we do NOT accept passwords through the bridge
            // Instead, open Wi-Fi settings panel for user to connect
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val intent = Intent(Settings.Panel.ACTION_WIFI).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opened Wi-Fi panel. Please select '$ssid' and enter the password."
            } else {
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opened Wi-Fi settings. Please connect to '$ssid' manually."
            }
        }.also { r ->
            r.onSuccess { logResult("connectToWifi", it) }
            r.onFailure { logError("connectToWifi", it) }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun getSavedWifiNetworks(): Result<String> {
        logAction("getSavedWifiNetworks")
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // On Android 10+, apps can't access saved networks list
                "Saved Wi-Fi networks are not accessible on Android 10+. Opening Wi-Fi settings."
                    .also {
                        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
            } else {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                val configs = wm.configuredNetworks ?: emptyList()
                val sb = StringBuilder("Saved Wi-Fi Networks (${configs.size}):\n")
                for (c in configs) {
                    @Suppress("DEPRECATION")
                    sb.appendLine("• ${c.SSID}")
                }
                sb.toString().trim()
            }
        }.also { r ->
            r.onSuccess { logResult("getSavedWifiNetworks", it) }
            r.onFailure { logError("getSavedWifiNetworks", it) }
        }
    }

    override suspend fun forgetWifiNetwork(ssid: String): Result<String> {
        logAction("forgetWifiNetwork", "ssid=$ssid")
        return runCatching {
            // Cannot programmatically forget networks on modern Android
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened Wi-Fi settings. Please long-press '$ssid' and select 'Forget' to remove it."
        }.also { r ->
            r.onSuccess { logResult("forgetWifiNetwork", it) }
            r.onFailure { logError("forgetWifiNetwork", it) }
        }
    }

    // ==========================================
    // Bluetooth Management
    // ==========================================

    @SuppressLint("MissingPermission")
    override suspend fun connectBluetoothDevice(address: String): Result<String> {
        logAction("connectBluetoothDevice", "address=$address")
        return runCatching {
            val ba = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                ?: return@runCatching "Bluetooth not available on this device."
            if (!ba.isEnabled) return@runCatching "Bluetooth is disabled. Enable it first."

            val device = ba.getRemoteDevice(address)
            val name = device.name ?: address
            // Open Bluetooth settings for user to connect
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened Bluetooth settings. Please connect to '$name' ($address)."
        }.also { r ->
            r.onSuccess { logResult("connectBluetoothDevice", it) }
            r.onFailure { logError("connectBluetoothDevice", it) }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun disconnectBluetoothDevice(address: String): Result<String> {
        logAction("disconnectBluetoothDevice", "address=$address")
        return runCatching {
            val ba = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                ?: return@runCatching "Bluetooth not available."
            val device = ba.getRemoteDevice(address)
            val name = device.name ?: address
            // Use reflection to disconnect (no public API)
            try {
                val method = device.javaClass.getMethod("removeBond")
                method.invoke(device)
                "Disconnecting from '$name'. The device will be unpaired."
            } catch (_: Exception) {
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opened Bluetooth settings. Please disconnect '$name' manually."
            }
        }.also { r ->
            r.onSuccess { logResult("disconnectBluetoothDevice", it) }
            r.onFailure { logError("disconnectBluetoothDevice", it) }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun pairBluetoothDevice(): Result<String> {
        logAction("pairBluetoothDevice")
        return runCatching {
            val ba = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                ?: return@runCatching "Bluetooth not available."
            if (!ba.isEnabled) {
                return@runCatching "Bluetooth is disabled. Enable it first."
            }
            // Start discovery / open pairing UI
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened Bluetooth settings. Device is ready for pairing."
        }.also { r ->
            r.onSuccess { logResult("pairBluetoothDevice", it) }
            r.onFailure { logError("pairBluetoothDevice", it) }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun getBluetoothPairedDevices(): Result<String> {
        logAction("getBluetoothPairedDevices")
        return runCatching {
            val ba = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                ?: return@runCatching "Bluetooth not available."
            if (!ba.isEnabled) return@runCatching "Bluetooth is disabled."
            val bonded = ba.bondedDevices ?: emptySet()
            val sb = StringBuilder("Paired Bluetooth devices (${bonded.size}):\n")
            for (d in bonded) {
                val type = when (d.type) {
                    android.bluetooth.BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic"
                    android.bluetooth.BluetoothDevice.DEVICE_TYPE_LE -> "BLE"
                    android.bluetooth.BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual"
                    else -> "Unknown"
                }
                sb.appendLine("• ${d.name ?: "(unnamed)"} [${d.address}] ($type)")
            }
            sb.toString().trim()
        }.also { r ->
            r.onSuccess { logResult("getBluetoothPairedDevices", it) }
            r.onFailure { logError("getBluetoothPairedDevices", it) }
        }
    }

    // ==========================================
    // Navigation Enhanced
    // ==========================================

    override suspend fun searchPlaces(query: String): Result<String> {
        logAction("searchPlaces", "query=$query")
        return runCatching {
            val encodedQuery = Uri.encode(query)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encodedQuery")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                trySetPackage("com.google.android.apps.maps")
            }
            context.startActivity(intent)
            "Searching for '$query' in Maps."
        }.also { r ->
            r.onSuccess { logResult("searchPlaces", it) }
            r.onFailure { logError("searchPlaces", it) }
        }
    }

    override suspend fun getDirections(from: String, to: String, mode: String): Result<String> {
        logAction("getDirections", "from=$from, to=$to, mode=$mode")
        return runCatching {
            val modeParam = when (mode.lowercase()) {
                "driving", "d" -> "d"
                "walking", "w" -> "w"
                "bicycling", "b" -> "b"
                "transit", "t", "r" -> "r"
                else -> "d"
            }
            val encodedFrom = Uri.encode(from)
            val encodedTo = Uri.encode(to)
            val uri = "https://www.google.com/maps/dir/?api=1&origin=$encodedFrom&destination=$encodedTo&travelmode=${
                when (modeParam) { "d" -> "driving"; "w" -> "walking"; "b" -> "bicycling"; "r" -> "transit"; else -> "driving" }
            }"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                trySetPackage("com.google.android.apps.maps")
            }
            context.startActivity(intent)
            "Getting directions from '$from' to '$to' ($mode)."
        }.also { r ->
            r.onSuccess { logResult("getDirections", it) }
            r.onFailure { logError("getDirections", it) }
        }
    }

    override suspend fun openStreetView(latitude: Double, longitude: Double): Result<String> {
        logAction("openStreetView", "lat=$latitude, lon=$longitude")
        return runCatching {
            val uri = Uri.parse("google.streetview:cbll=$latitude,$longitude")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                trySetPackage("com.google.android.apps.maps")
            }
            context.startActivity(intent)
            "Opening Street View at ($latitude, $longitude)."
        }.also { r ->
            r.onSuccess { logResult("openStreetView", it) }
            r.onFailure { logError("openStreetView", it) }
        }
    }

    override suspend fun getNearbyPlaces(type: String, radius: Int): Result<String> {
        logAction("getNearbyPlaces", "type=$type, radius=$radius")
        return runCatching {
            val encodedType = Uri.encode(type)
            // Use geo: URI with query for nearby search
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encodedType+nearby")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                trySetPackage("com.google.android.apps.maps")
            }
            context.startActivity(intent)
            "Searching for $type within ${radius}m nearby."
        }.also { r ->
            r.onSuccess { logResult("getNearbyPlaces", it) }
            r.onFailure { logError("getNearbyPlaces", it) }
        }
    }

    // ==========================================
    // Audio Profiles
    // ==========================================

    override suspend fun saveAudioProfile(name: String): Result<String> {
        logAction("saveAudioProfile", "name=$name")
        return runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val prefs = context.getSharedPreferences("audio_profiles", Context.MODE_PRIVATE)
            val profile = buildString {
                append("media=${am.getStreamVolume(AudioManager.STREAM_MUSIC)},")
                append("ring=${am.getStreamVolume(AudioManager.STREAM_RING)},")
                append("alarm=${am.getStreamVolume(AudioManager.STREAM_ALARM)},")
                append("notification=${am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)},")
                append("ringer_mode=${am.ringerMode}")
            }
            prefs.edit().putString("profile_$name", profile).apply()
            "Audio profile '$name' saved: $profile"
        }.also { r ->
            r.onSuccess { logResult("saveAudioProfile", it) }
            r.onFailure { logError("saveAudioProfile", it) }
        }
    }

    override suspend fun loadAudioProfile(name: String): Result<String> {
        logAction("loadAudioProfile", "name=$name")
        return runCatching {
            val prefs = context.getSharedPreferences("audio_profiles", Context.MODE_PRIVATE)
            val profile = prefs.getString("profile_$name", null)
                ?: return@runCatching "Audio profile '$name' not found."
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val values = profile.split(",").associate {
                val (k, v) = it.split("=")
                k to v.toInt()
            }
            values["media"]?.let { am.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0) }
            values["ring"]?.let { am.setStreamVolume(AudioManager.STREAM_RING, it, 0) }
            values["alarm"]?.let { am.setStreamVolume(AudioManager.STREAM_ALARM, it, 0) }
            values["notification"]?.let { am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, it, 0) }
            values["ringer_mode"]?.let { am.ringerMode = it }
            "Audio profile '$name' loaded: $profile"
        }.also { r ->
            r.onSuccess { logResult("loadAudioProfile", it) }
            r.onFailure { logError("loadAudioProfile", it) }
        }
    }

    override suspend fun listAudioProfiles(): Result<String> {
        logAction("listAudioProfiles")
        return runCatching {
            val prefs = context.getSharedPreferences("audio_profiles", Context.MODE_PRIVATE)
            val profiles = prefs.all.filter { it.key.startsWith("profile_") }
            if (profiles.isEmpty()) return@runCatching "No saved audio profiles."
            val sb = StringBuilder("Audio profiles (${profiles.size}):\n")
            for ((key, value) in profiles) {
                val name = key.removePrefix("profile_")
                sb.appendLine("• $name: $value")
            }
            sb.toString().trim()
        }.also { r ->
            r.onSuccess { logResult("listAudioProfiles", it) }
            r.onFailure { logError("listAudioProfiles", it) }
        }
    }

    override suspend fun deleteAudioProfile(name: String): Result<String> {
        logAction("deleteAudioProfile", "name=$name")
        return runCatching {
            val prefs = context.getSharedPreferences("audio_profiles", Context.MODE_PRIVATE)
            if (!prefs.contains("profile_$name")) {
                return@runCatching "Audio profile '$name' not found."
            }
            prefs.edit().remove("profile_$name").apply()
            "Audio profile '$name' deleted."
        }.also { r ->
            r.onSuccess { logResult("deleteAudioProfile", it) }
            r.onFailure { logError("deleteAudioProfile", it) }
        }
    }

    // ==========================================
    // Shortcuts
    // ==========================================

    override suspend fun createHomeShortcut(name: String, uri: String): Result<String> {
        logAction("createHomeShortcut", "name=$name, uri=$uri")
        return runCatching {
            val shortcutManager = context.getSystemService(Context.SHORTCUT_SERVICE) as android.content.pm.ShortcutManager
            if (!shortcutManager.isRequestPinShortcutSupported) {
                return@runCatching "Pin shortcuts are not supported on this device/launcher."
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                setPackage(context.packageName)
            }
            val shortcutInfo = android.content.pm.ShortcutInfo.Builder(context, "shortcut_${name.lowercase().replace(" ", "_")}")
                .setShortLabel(name)
                .setLongLabel(name)
                .setIntent(intent)
                .setIcon(android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.ic_menu_compass))
                .build()
            shortcutManager.requestPinShortcut(shortcutInfo, null)
            "Requesting to pin shortcut '$name' → $uri to home screen."
        }.also { r ->
            r.onSuccess { logResult("createHomeShortcut", it) }
            r.onFailure { logError("createHomeShortcut", it) }
        }
    }

    override suspend fun pinAppShortcut(packageName: String): Result<String> {
        logAction("pinAppShortcut", "package=$packageName")
        return runCatching {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return@runCatching "App '$packageName' not found or has no launcher activity."
            val shortcutManager = context.getSystemService(Context.SHORTCUT_SERVICE) as android.content.pm.ShortcutManager
            if (!shortcutManager.isRequestPinShortcutSupported) {
                return@runCatching "Pin shortcuts not supported."
            }
            val appName = try {
                val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
                context.packageManager.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) { packageName }

            val shortcutInfo = android.content.pm.ShortcutInfo.Builder(context, "app_$packageName")
                .setShortLabel(appName)
                .setLongLabel(appName)
                .setIntent(launchIntent.apply { action = Intent.ACTION_MAIN })
                .setIcon(android.graphics.drawable.Icon.createWithAdaptiveBitmap(
                    android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
                ))
                .build()
            shortcutManager.requestPinShortcut(shortcutInfo, null)
            "Requesting to pin '$appName' shortcut to home screen."
        }.also { r ->
            r.onSuccess { logResult("pinAppShortcut", it) }
            r.onFailure { logError("pinAppShortcut", it) }
        }
    }

    // ==========================================
    // Document Scanner
    // ==========================================

    override suspend fun openDocumentScanner(): Result<String> {
        logAction("openDocumentScanner")
        return runCatching {
            // Try Google Drive document scanner first
            try {
                val intent = Intent().apply {
                    setClassName("com.google.android.apps.docs", "com.google.android.apps.docs.app.NewDocIntentHandler")
                    action = "android.media.action.IMAGE_CAPTURE"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return@runCatching "Opened Google Drive document scanner."
            } catch (_: Exception) {}
            // Try the generic camera scanner
            try {
                val intent = Intent("com.google.android.apps.docs.SCAN").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return@runCatching "Opened document scanner."
            } catch (_: Exception) {}
            // Fallback: open camera
            val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened camera for document scanning. Consider installing Google Drive for better scanning."
        }.also { r ->
            r.onSuccess { logResult("openDocumentScanner", it) }
            r.onFailure { logError("openDocumentScanner", it) }
        }
    }

    // ==========================================
    // Cast / Screen Mirror Enhanced
    // ==========================================

    override suspend fun discoverCastDevices(): Result<String> {
        logAction("discoverCastDevices")
        return runCatching {
            // Open cast / media route settings
            val intent = Intent(Settings.ACTION_CAST_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened Cast settings. Available cast devices will be shown."
        }.also { r ->
            r.onSuccess { logResult("discoverCastDevices", it) }
            r.onFailure { logError("discoverCastDevices", it) }
        }
    }

    override suspend fun castMedia(url: String): Result<String> {
        logAction("castMedia", "url=${url.take(80)}")
        return runCatching {
            // Open the URL and let user cast from the app
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening media: $url. Use the cast button in the media player to cast to a device."
        }.also { r ->
            r.onSuccess { logResult("castMedia", it) }
            r.onFailure { logError("castMedia", it) }
        }
    }

    // ==========================================
    // Reminders Enhanced
    // ==========================================

    override suspend fun completeReminder(id: String): Result<String> {
        logAction("completeReminder", "id=$id")
        return runCatching {
            // Try to mark reminder complete via CalendarContract
            try {
                val uri = ContentUris.withAppendedId(
                    android.provider.CalendarContract.Reminders.CONTENT_URI,
                    id.toLong()
                )
                val values = ContentValues().apply {
                    put(android.provider.CalendarContract.Reminders.MINUTES, 0)
                }
                context.contentResolver.update(uri, values, null, null)
                "Reminder $id marked as complete."
            } catch (_: Exception) {
                // Fallback: open calendar app
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("content://com.android.calendar/events/$id")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                    "Opened reminder $id in calendar. Please mark it as complete."
                } catch (_: Exception) {
                    "Could not find reminder with ID $id."
                }
            }
        }.also { r ->
            r.onSuccess { logResult("completeReminder", it) }
            r.onFailure { logError("completeReminder", it) }
        }
    }

    // ==========================================
    // App Management
    // ==========================================

    override suspend fun getAppPermissions(packageName: String): Result<String> {
        logAction("getAppPermissions", "packageName=$packageName")
        return runCatching {
            val pi = context.packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
            val perms = pi.requestedPermissions
            if (perms.isNullOrEmpty()) {
                "App $packageName has no requested permissions."
            } else {
                val granted = pi.requestedPermissionsFlags
                val sb = StringBuilder("Permissions for $packageName (${perms.size} total):\n")
                for (i in perms.indices) {
                    val isGranted = if (granted != null && i < granted.size) {
                        (granted[i] and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                    } else false
                    val status = if (isGranted) "GRANTED" else "DENIED"
                    val shortName = perms[i].substringAfterLast('.')
                    sb.appendLine("  [$status] $shortName")
                }
                sb.toString().trim()
            }
        }.also { r ->
            r.onSuccess { logResult("getAppPermissions", it) }
            r.onFailure { logError("getAppPermissions", it) }
        }
    }

    override suspend fun getAppStorageInfo(packageName: String): Result<String> {
        logAction("getAppStorageInfo", "packageName=$packageName")
        return runCatching {
            val ai = context.packageManager.getApplicationInfo(packageName, 0)
            val appName = context.packageManager.getApplicationLabel(ai).toString()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val ssm = context.getSystemService(android.content.Context.STORAGE_STATS_SERVICE) as android.app.usage.StorageStatsManager
                val uuid = ai.storageUuid
                val stats = ssm.queryStatsForPackage(uuid, packageName, android.os.Process.myUserHandle())
                val appBytes = stats.appBytes
                val dataBytes = stats.dataBytes
                val cacheBytes = stats.cacheBytes
                fun fmt(b: Long): String {
                    return when {
                        b >= 1_073_741_824 -> "%.1f GB".format(b / 1_073_741_824.0)
                        b >= 1_048_576 -> "%.1f MB".format(b / 1_048_576.0)
                        b >= 1024 -> "%.1f KB".format(b / 1024.0)
                        else -> "$b B"
                    }
                }
                """Storage info for $appName ($packageName):
  App size: ${fmt(appBytes)}
  Data: ${fmt(dataBytes)}
  Cache: ${fmt(cacheBytes)}
  Total: ${fmt(appBytes + dataBytes + cacheBytes)}""".trimIndent()
            } else {
                val sourceDir = ai.sourceDir
                val dataDir = ai.dataDir
                val sourceSize = java.io.File(sourceDir).length()
                "Storage info for $appName ($packageName):\n  APK: ${sourceSize / 1_048_576}MB\n  Data dir: $dataDir"
            }
        }.also { r ->
            r.onSuccess { logResult("getAppStorageInfo", it) }
            r.onFailure { logError("getAppStorageInfo", it) }
        }
    }

    override suspend fun clearAppCache(packageName: String): Result<String> {
        logAction("clearAppCache", "packageName=$packageName")
        return runCatching {
            // Cannot clear cache programmatically on modern Android — open app info instead
            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:$packageName")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened app info for $packageName. Tap 'Storage' → 'Clear Cache' to clear the cache."
        }.also { r ->
            r.onSuccess { logResult("clearAppCache", it) }
            r.onFailure { logError("clearAppCache", it) }
        }
    }

    override suspend fun getAppNotificationSettings(packageName: String): Result<String> {
        logAction("getAppNotificationSettings", "packageName=$packageName")
        return runCatching {
            val nm = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val appName = try {
                context.packageManager.getApplicationLabel(
                    context.packageManager.getApplicationInfo(packageName, 0)
                ).toString()
            } catch (_: Exception) { packageName }
            val enabled = nm.areNotificationsEnabled()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channels = nm.notificationChannels
                val sb = StringBuilder("Notification settings for $appName:\n  Notifications enabled: $enabled\n")
                if (channels.isNotEmpty()) {
                    sb.appendLine("  Channels (${channels.size}):")
                    for (ch in channels) {
                        val importance = when (ch.importance) {
                            android.app.NotificationManager.IMPORTANCE_NONE -> "NONE"
                            android.app.NotificationManager.IMPORTANCE_MIN -> "MIN"
                            android.app.NotificationManager.IMPORTANCE_LOW -> "LOW"
                            android.app.NotificationManager.IMPORTANCE_DEFAULT -> "DEFAULT"
                            android.app.NotificationManager.IMPORTANCE_HIGH -> "HIGH"
                            android.app.NotificationManager.IMPORTANCE_MAX -> "MAX"
                            else -> "UNKNOWN"
                        }
                        sb.appendLine("    - ${ch.name} (${ch.id}): $importance, sound=${ch.sound != null}, vibrate=${ch.shouldVibrate()}")
                    }
                }
                sb.toString().trim()
            } else {
                "Notification settings for $appName: enabled=$enabled"
            }
        }.also { r ->
            r.onSuccess { logResult("getAppNotificationSettings", it) }
            r.onFailure { logError("getAppNotificationSettings", it) }
        }
    }

    override suspend fun getAppBatteryUsage(): Result<String> {
        logAction("getAppBatteryUsage", "")
        return runCatching {
            val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            val bm = context.getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager
            val level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val charging = bm.isCharging
            val sb = StringBuilder("Battery status: ${level}%${if (charging) " (charging)" else ""}\n\n")

            // Show apps with battery optimization status
            val packages = context.packageManager.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
                .filter { (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
                .take(25)
            sb.appendLine("Battery optimization status (top user apps):")
            for (ai in packages) {
                val name = context.packageManager.getApplicationLabel(ai).toString()
                val ignored = pm.isIgnoringBatteryOptimizations(ai.packageName)
                sb.appendLine("  $name: ${if (ignored) "Unrestricted" else "Optimized"}")
            }
            sb.toString().trim()
        }.also { r ->
            r.onSuccess { logResult("getAppBatteryUsage", it) }
            r.onFailure { logError("getAppBatteryUsage", it) }
        }
    }

    override suspend fun getDefaultApps(): Result<String> {
        logAction("getDefaultApps", "")
        return runCatching {
            val pm = context.packageManager
            val sb = StringBuilder("Default app handlers:\n")

            // Browser
            val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com"))
            val browser = pm.resolveActivity(browserIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            sb.appendLine("  Browser: ${browser?.activityInfo?.packageName ?: "None"}")

            // SMS
            val smsPackage = android.provider.Telephony.Sms.getDefaultSmsPackage(context)
            sb.appendLine("  SMS: ${smsPackage ?: "None"}")

            // Phone / Dialer
            val dialIntent = android.content.Intent(android.content.Intent.ACTION_DIAL)
            val dialer = pm.resolveActivity(dialIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            sb.appendLine("  Phone: ${dialer?.activityInfo?.packageName ?: "None"}")

            // Camera
            val cameraIntent = android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
            val camera = pm.resolveActivity(cameraIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            sb.appendLine("  Camera: ${camera?.activityInfo?.packageName ?: "None"}")

            // Launcher
            val launcherIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME)
            val launcher = pm.resolveActivity(launcherIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            sb.appendLine("  Launcher: ${launcher?.activityInfo?.packageName ?: "None"}")

            // Email
            val emailIntent = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:"))
            val email = pm.resolveActivity(emailIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            sb.appendLine("  Email: ${email?.activityInfo?.packageName ?: "None"}")

            // Music
            val musicIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply { type = "audio/*" }
            val music = pm.resolveActivity(musicIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            sb.appendLine("  Music: ${music?.activityInfo?.packageName ?: "None"}")

            // Maps
            val mapIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0"))
            val maps = pm.resolveActivity(mapIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            sb.appendLine("  Maps: ${maps?.activityInfo?.packageName ?: "None"}")

            sb.toString().trim()
        }.also { r ->
            r.onSuccess { logResult("getDefaultApps", it) }
            r.onFailure { logError("getDefaultApps", it) }
        }
    }

    override suspend fun setDefaultApp(role: String, packageName: String): Result<String> {
        logAction("setDefaultApp", "role=$role, packageName=$packageName")
        return runCatching {
            // Open the default apps settings — cannot set programmatically
            val intent = when (role.lowercase()) {
                "browser" -> android.content.Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                "sms" -> {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        android.content.Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                    } else {
                        android.content.Intent(android.provider.Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                            putExtra(android.provider.Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                        }
                    }
                }
                "home", "launcher" -> android.content.Intent(android.provider.Settings.ACTION_HOME_SETTINGS)
                else -> android.content.Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            }
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Opened default apps settings for '$role'. Please select $packageName as default."
        }.also { r ->
            r.onSuccess { logResult("setDefaultApp", it) }
            r.onFailure { logError("setDefaultApp", it) }
        }
    }

    override suspend fun getRecentlyInstalledApps(days: Int): Result<String> {
        logAction("getRecentlyInstalledApps", "days=$days")
        return runCatching {
            val cutoff = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000)
            val apps = context.packageManager.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
                .mapNotNull { ai ->
                    try {
                        val pi = context.packageManager.getPackageInfo(ai.packageName, 0)
                        if (pi.firstInstallTime >= cutoff && (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) {
                            val name = context.packageManager.getApplicationLabel(ai).toString()
                            val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(pi.firstInstallTime))
                            Triple(name, ai.packageName, date)
                        } else null
                    } catch (_: Exception) { null }
                }
                .sortedByDescending { it.third }

            if (apps.isEmpty()) {
                "No apps installed in the last $days days."
            } else {
                val sb = StringBuilder("Recently installed apps (last $days days):\n")
                for ((name, pkg, date) in apps) {
                    sb.appendLine("  $name ($pkg) — installed $date")
                }
                sb.toString().trim()
            }
        }.also { r ->
            r.onSuccess { logResult("getRecentlyInstalledApps", it) }
            r.onFailure { logError("getRecentlyInstalledApps", it) }
        }
    }

    override suspend fun getRunningApps(): Result<String> {
        logAction("getRunningApps", "")
        return runCatching {
            val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            @Suppress("DEPRECATION")
            val processes = am.runningAppProcesses
            if (processes.isNullOrEmpty()) {
                "No running app processes found (may require additional permissions)."
            } else {
                val sb = StringBuilder("Running apps (${processes.size}):\n")
                for (p in processes) {
                    val importance = when (p.importance) {
                        android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "Foreground"
                        android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "Foreground Service"
                        android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "Visible"
                        android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "Service"
                        android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "Cached"
                        else -> "Background"
                    }
                    val appName = try {
                        context.packageManager.getApplicationLabel(
                            context.packageManager.getApplicationInfo(p.processName, 0)
                        ).toString()
                    } catch (_: Exception) { p.processName }
                    sb.appendLine("  $appName (${p.processName}): $importance")
                }
                sb.toString().trim()
            }
        }.also { r ->
            r.onSuccess { logResult("getRunningApps", it) }
            r.onFailure { logError("getRunningApps", it) }
        }
    }

    override suspend fun killBackgroundApp(packageName: String): Result<String> {
        logAction("killBackgroundApp", "packageName=$packageName")
        return runCatching {
            val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            @Suppress("DEPRECATION")
            am.killBackgroundProcesses(packageName)
            "Killed background processes for $packageName."
        }.also { r ->
            r.onSuccess { logResult("killBackgroundApp", it) }
            r.onFailure { logError("killBackgroundApp", it) }
        }
    }

    // ==========================================
    // Task automation: cleanup & tidy
    // ==========================================

    override suspend fun findDuplicateFiles(directory: String): Result<String> = withContext(Dispatchers.IO) {
        logAction("findDuplicateFiles", "dir=$directory")
        runCatching {
            val dir = resolveDirectory(directory)
            if (!dir.exists() || !dir.isDirectory) return@runCatching "Directory not found: ${dir.absolutePath}"
            val files = dir.walkTopDown().filter { it.isFile && it.length() > 0 }.toList()
            if (files.isEmpty()) return@runCatching "No files in ${dir.absolutePath}"

            val duplicateGroups = mutableListOf<List<java.io.File>>()
            // Group by size first (cheap), then hash content within each size group.
            for ((_, sameSize) in files.groupBy { it.length() }.filterValues { it.size > 1 }) {
                val byHash = sameSize.groupBy { hashFile(it) }.filterValues { it.size > 1 }
                duplicateGroups.addAll(byHash.values)
            }
            if (duplicateGroups.isEmpty()) return@runCatching "No duplicate files found in ${dir.name}."

            var reclaimable = 0L
            buildString {
                appendLine("Found ${duplicateGroups.size} duplicate set(s) in ${dir.name}:")
                duplicateGroups.sortedByDescending { it[0].length() }.take(20).forEachIndexed { i, group ->
                    val size = group[0].length()
                    reclaimable += size * (group.size - 1)
                    appendLine("${i + 1}. ${group.size} copies, ${formatFileSize(size)} each:")
                    group.forEach { appendLine("   - ${it.absolutePath}") }
                }
                appendLine("Reclaimable by removing extras: ${formatFileSize(reclaimable)}")
            }.trim()
        }.also { r ->
            r.onSuccess { logResult("findDuplicateFiles", it.take(200)) }
            r.onFailure { logError("findDuplicateFiles", it) }
        }
    }

    override suspend fun findLargeFiles(directory: String, minSizeMb: Int): Result<String> = withContext(Dispatchers.IO) {
        logAction("findLargeFiles", "dir=$directory, minSizeMb=$minSizeMb")
        runCatching {
            val dir = resolveDirectory(directory)
            if (!dir.exists() || !dir.isDirectory) return@runCatching "Directory not found: ${dir.absolutePath}"
            val threshold = minSizeMb.coerceAtLeast(1) * 1024L * 1024L
            val large = dir.walkTopDown().filter { it.isFile && it.length() >= threshold }
                .sortedByDescending { it.length() }.take(30).toList()
            if (large.isEmpty()) return@runCatching "No files larger than ${minSizeMb}MB in ${dir.name}."
            val total = large.sumOf { it.length() }
            buildString {
                appendLine("Largest files (>= ${minSizeMb}MB) in ${dir.name}, total ${formatFileSize(total)}:")
                large.forEachIndexed { i, f -> appendLine("${i + 1}. ${formatFileSize(f.length())} - ${f.absolutePath}") }
            }.trim()
        }.also { r ->
            r.onSuccess { logResult("findLargeFiles", it.take(200)) }
            r.onFailure { logError("findLargeFiles", it) }
        }
    }

    override suspend fun findOldFiles(directory: String, olderThanDays: Int): Result<String> = withContext(Dispatchers.IO) {
        logAction("findOldFiles", "dir=$directory, olderThanDays=$olderThanDays")
        runCatching {
            val dir = resolveDirectory(directory)
            if (!dir.exists() || !dir.isDirectory) return@runCatching "Directory not found: ${dir.absolutePath}"
            val cutoff = System.currentTimeMillis() - olderThanDays.coerceAtLeast(1) * 24L * 60 * 60 * 1000
            val old = dir.walkTopDown().filter { it.isFile && it.lastModified() in 1 until cutoff }
                .sortedBy { it.lastModified() }.take(30).toList()
            if (old.isEmpty()) return@runCatching "No files older than $olderThanDays days in ${dir.name}."
            val total = old.sumOf { it.length() }
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            buildString {
                appendLine("Files older than $olderThanDays days in ${dir.name}, total ${formatFileSize(total)}:")
                old.forEachIndexed { i, f ->
                    appendLine("${i + 1}. ${fmt.format(java.util.Date(f.lastModified()))}, ${formatFileSize(f.length())} - ${f.absolutePath}")
                }
            }.trim()
        }.also { r ->
            r.onSuccess { logResult("findOldFiles", it.take(200)) }
            r.onFailure { logError("findOldFiles", it) }
        }
    }

    override suspend fun findScreenshots(): Result<String> = withContext(Dispatchers.IO) {
        logAction("findScreenshots")
        runCatching {
            val dirs = screenshotDirectories().filter { it.exists() && it.isDirectory }
            if (dirs.isEmpty()) return@runCatching "No screenshot folders found."
            val shots = dirs.flatMap { it.listFiles()?.filter { f -> f.isFile } ?: emptyList() }
            if (shots.isEmpty()) return@runCatching "No screenshots found."
            val total = shots.sumOf { it.length() }
            val oldest = shots.minByOrNull { it.lastModified() }
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            buildString {
                appendLine("Found ${shots.size} screenshots taking ${formatFileSize(total)}.")
                appendLine("Folders: ${dirs.joinToString { it.absolutePath }}")
                oldest?.let { appendLine("Oldest: ${fmt.format(java.util.Date(it.lastModified()))}") }
            }.trim()
        }.also { r ->
            r.onSuccess { logResult("findScreenshots", it.take(200)) }
            r.onFailure { logError("findScreenshots", it) }
        }
    }

    override suspend fun cleanupScreenshots(olderThanDays: Int): Result<String> = withContext(Dispatchers.IO) {
        logAction("cleanupScreenshots", "olderThanDays=$olderThanDays")
        runCatching {
            val cutoff = System.currentTimeMillis() - olderThanDays.coerceAtLeast(0) * 24L * 60 * 60 * 1000
            val shots = screenshotDirectories().filter { it.exists() }
                .flatMap { it.listFiles()?.filter { f -> f.isFile && f.lastModified() < cutoff } ?: emptyList() }
            if (shots.isEmpty()) return@runCatching "No screenshots older than $olderThanDays days to clean up."
            val trashDir = java.io.File(android.os.Environment.getExternalStorageDirectory(), "AndroidClaw_Trash/Screenshots")
            if (!trashDir.exists()) trashDir.mkdirs()
            var moved = 0
            var freed = 0L
            for (f in shots) {
                val dest = java.io.File(trashDir, f.name)
                val size = f.length()
                val ok = f.renameTo(dest) || runCatching { f.copyTo(dest, overwrite = false); f.delete() }.getOrDefault(false)
                if (ok) {
                    moved++
                    freed += size
                }
            }
            "Moved $moved old screenshots (${formatFileSize(freed)}) to ${trashDir.absolutePath}. Delete that folder to free the space permanently."
        }.also { r ->
            r.onSuccess { logResult("cleanupScreenshots", it) }
            r.onFailure { logError("cleanupScreenshots", it) }
        }
    }

    override suspend fun suggestUnusedApps(days: Int): Result<String> = withContext(Dispatchers.IO) {
        logAction("suggestUnusedApps", "days=$days")
        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
                return@runCatching "Usage stats require Android 5.1+"
            }
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
                ?: return@runCatching "Usage stats service not available"
            val end = System.currentTimeMillis()
            val start = end - days.coerceAtLeast(1) * 24L * 60 * 60 * 1000
            val stats = usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, start, end)
            if (stats.isNullOrEmpty()) {
                return@runCatching "No usage data available. Enable Usage Access in Settings > Apps > Special access > Usage access."
            }
            val lastUsed = mutableMapOf<String, Long>()
            for (s in stats) {
                if (s.lastTimeUsed > 0) lastUsed[s.packageName] = maxOf(lastUsed[s.packageName] ?: 0L, s.lastTimeUsed)
            }
            val pm = context.packageManager
            val launchable = pm.getInstalledApplications(0).filter {
                pm.getLaunchIntentForPackage(it.packageName) != null &&
                    (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0
            }
            val unused = launchable.filter { (lastUsed[it.packageName] ?: 0L) < start }
                .sortedBy { lastUsed[it.packageName] ?: 0L }
            if (unused.isEmpty()) return@runCatching "No user-installed apps have gone unused in the last $days days."
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            buildString {
                appendLine("Apps not used in the last $days days (${unused.size}):")
                unused.take(25).forEach {
                    val name = runCatching { pm.getApplicationLabel(it).toString() }.getOrDefault(it.packageName)
                    val last = lastUsed[it.packageName] ?: 0L
                    val lastStr = if (last > 0) fmt.format(java.util.Date(last)) else "never in window"
                    appendLine("  - $name (${it.packageName}) - last used: $lastStr")
                }
            }.trim()
        }.also { r ->
            r.onSuccess { logResult("suggestUnusedApps", it.take(200)) }
            r.onFailure { logError("suggestUnusedApps", it) }
        }
    }

    override suspend fun applyDeviceMode(mode: String): Result<String> {
        logAction("applyDeviceMode", "mode=$mode")
        val results = mutableListOf<String>()
        suspend fun step(label: String, r: Result<String>) {
            val status = if (r.isSuccess) "done" else "failed (${r.exceptionOrNull()?.message ?: "no permission"})"
            results.add("  - $label: $status")
        }
        when (mode.lowercase().trim()) {
            "focus", "dnd", "do_not_disturb" -> {
                step("Do Not Disturb on", setDoNotDisturb(true))
                step("Ringer silent", setRingerMode("silent"))
            }
            "sleep", "bedtime", "night" -> {
                step("Do Not Disturb on", setDoNotDisturb(true))
                step("Night light on", setNightLight(true))
                step("Brightness low", setBrightness(10))
            }
            "battery_saver", "battery", "saver" -> {
                step("Battery saver on", setBatterySaver(true))
                step("Brightness low", setBrightness(20))
            }
            "outdoor", "sunlight" -> {
                step("Brightness max", setBrightness(100))
            }
            "normal", "off", "reset" -> {
                step("Do Not Disturb off", setDoNotDisturb(false))
                step("Ringer normal", setRingerMode("normal"))
            }
            else -> return Result.failure(IllegalArgumentException("Unknown mode '$mode'. Try: focus, sleep, battery_saver, outdoor, normal."))
        }
        return Result.success("Applied '$mode' mode:\n" + results.joinToString("\n"))
    }

    override suspend fun closeChromeTabs(filter: String): Result<String> {
        logAction("closeChromeTabs", "filter=$filter")
        return runCatching {
            val service = AutoSendAccessibilityService.instance
                ?: return@runCatching "Accessibility service not enabled. Enable AndroidClaw in Settings > Accessibility to manage Chrome tabs."
            val launch = context.packageManager.getLaunchIntentForPackage("com.android.chrome")
                ?: return@runCatching "Chrome is not installed on this device."
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
            kotlinx.coroutines.delay(1500)
            service.closeChromeTabs(filter)
        }.also { r ->
            r.onSuccess { logResult("closeChromeTabs", it) }
            r.onFailure { logError("closeChromeTabs", it) }
        }
    }

    private fun screenshotDirectories(): List<java.io.File> {
        val pics = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
        val dcim = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DCIM)
        return listOf(
            java.io.File(pics, "Screenshots"),
            java.io.File(dcim, "Screenshots"),
        )
    }

    private fun hashFile(file: java.io.File): String = runCatching {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            var read = input.read(buf)
            while (read >= 0) {
                md.update(buf, 0, read)
                read = input.read(buf)
            }
        }
        md.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }.getOrDefault(file.absolutePath)

    // ==========================================
    // Helpers
    // ==========================================

    private fun resolveDirectory(directory: String): java.io.File {
        val knownDirs = mapOf(
            "downloads" to android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
            "download" to android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
            "documents" to android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS),
            "pictures" to android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES),
            "photos" to android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES),
            "movies" to android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES),
            "music" to android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC),
            "dcim" to android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DCIM),
            "camera" to java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DCIM), "Camera")
        )
        val normalized = directory.lowercase().trim()
        return knownDirs[normalized] ?: java.io.File(directory)
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    private fun hasPermission(permission: String): Boolean {
        val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            Log.w(TAG, "Permission not granted: ${permission.substringAfterLast('.')}")
        }
        return granted
    }

    /** Try to set a specific package on the intent, falling back if not installed. */
    private fun Intent.trySetPackage(vararg packages: String): Intent {
        for (pkg in packages) {
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                setPackage(pkg)
                Log.d(TAG, "Targeting package: $pkg")
                return this
            } catch (_: PackageManager.NameNotFoundException) {}
        }
        return this
    }
}
