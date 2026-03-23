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
import androidx.core.content.ContextCompat
import com.androidclaw.app.admin.ClawDeviceAdminReceiver
import com.androidclaw.app.service.AutoSendAccessibilityService
import com.androidclaw.shared.tools.DeviceActionBridge
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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
            "Notification access requires NotificationListenerService permission. " +
                "Please enable it in Settings > Apps > Special access > Notification access."
        }
    }

    override suspend fun dismissNotification(key: String): Result<String> {
        logAction("dismissNotification", "key=$key")
        return runCatching {
            "Notification dismissal requires NotificationListenerService permission."
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

    override suspend fun navigateTo(destination: String): Result<String> {
        logAction("navigateTo", "destination=$destination")
        return runCatching {
            val uri = Uri.parse("google.navigation:q=${Uri.encode(destination)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Starting navigation to $destination"
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
    // Helpers
    // ==========================================

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
