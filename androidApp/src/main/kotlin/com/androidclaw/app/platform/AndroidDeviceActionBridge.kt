package com.androidclaw.app.platform

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.core.content.ContextCompat
import com.androidclaw.app.admin.ClawDeviceAdminReceiver
import com.androidclaw.shared.tools.DeviceActionBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class AndroidDeviceActionBridge(
    private val context: Context
) : DeviceActionBridge {

    // -- Wi-Fi --
    override suspend fun setWifiEnabled(enabled: Boolean): Result<String> = runCatching {
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
    }

    // -- Bluetooth --
    override suspend fun setBluetoothEnabled(enabled: Boolean): Result<String> = runCatching {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        "Opened Bluetooth settings. Please toggle Bluetooth ${if (enabled) "on" else "off"}."
    }

    // -- Flashlight --
    override suspend fun setFlashlightEnabled(enabled: Boolean): Result<String> = runCatching {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull()
            ?: return@runCatching "No camera available for flashlight"
        cameraManager.setTorchMode(cameraId, enabled)
        "Flashlight ${if (enabled) "on" else "off"}"
    }

    // -- Brightness --
    override suspend fun setBrightness(level: Int): Result<String> = runCatching {
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
    }

    // -- Volume --
    override suspend fun setVolume(stream: String, level: Int): Result<String> = runCatching {
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
    }

    // -- Device Info --
    override suspend fun getDeviceInfo(): Result<String> = runCatching {
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
            } catch (_: Exception) {}
        }.trim()
    }

    // -- App Launcher --
    override suspend fun launchApp(packageName: String): Result<String> = runCatching {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return@runCatching "App not found: $packageName"
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        "Launched $packageName"
    }

    override suspend fun listInstalledApps(): Result<String> = withContext(Dispatchers.IO) {
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
        }
    }

    override suspend fun searchApps(query: String): Result<String> = withContext(Dispatchers.IO) {
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
        }
    }

    // -- Clipboard --
    override suspend fun setClipboard(text: String): Result<String> = withContext(Dispatchers.Main) {
        runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("AndroidClaw", text))
            "Copied to clipboard: ${text.take(100)}${if (text.length > 100) "..." else ""}"
        }
    }

    override suspend fun getClipboard(): Result<String> = withContext(Dispatchers.Main) {
        runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).coerceToText(context).toString()
                "Clipboard contents: $text"
            } else {
                "Clipboard is empty"
            }
        }
    }

    // -- Notifications --
    override suspend fun getRecentNotifications(count: Int): Result<String> = runCatching {
        "Notification access requires NotificationListenerService permission. " +
            "Please enable it in Settings > Apps > Special access > Notification access."
    }

    override suspend fun dismissNotification(key: String): Result<String> = runCatching {
        "Notification dismissal requires NotificationListenerService permission."
    }

    // -- Alarms & Timers --
    override suspend fun setAlarm(hour: Int, minute: Int, label: String): Result<String> = runCatching {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            if (label.isNotEmpty()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        val timeStr = String.format("%02d:%02d", hour, minute)
        "Alarm set for $timeStr${if (label.isNotEmpty()) " - $label" else ""}"
    }

    override suspend fun setTimer(seconds: Int, label: String): Result<String> = runCatching {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            if (label.isNotEmpty()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        val mins = seconds / 60
        val secs = seconds % 60
        val timeStr = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
        "Timer set for $timeStr${if (label.isNotEmpty()) " - $label" else ""}"
    }

    // ==========================================
    // NEW: Contacts
    // ==========================================

    override suspend fun getContacts(query: String, limit: Int): Result<String> = withContext(Dispatchers.IO) {
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
        }
    }

    override suspend fun addContact(name: String, phone: String, email: String): Result<String> = withContext(Dispatchers.IO) {
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
        }
    }

    override suspend fun findContactByName(name: String): Result<String> = getContacts(name, 10)

    // ==========================================
    // NEW: Calendar
    // ==========================================

    @SuppressLint("MissingPermission")
    override suspend fun getCalendarEvents(daysAhead: Int): Result<String> = withContext(Dispatchers.IO) {
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
        }
    }

    override suspend fun createCalendarEvent(
        title: String, startTimeMillis: Long, endTimeMillis: Long, description: String
    ): Result<String> = runCatching {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTimeMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTimeMillis)
            if (description.isNotEmpty()) {
                putExtra(CalendarContract.Events.DESCRIPTION, description)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        "Opening calendar to create event: $title"
    }

    // ==========================================
    // NEW: SMS
    // ==========================================

    override suspend fun sendSms(phoneNumber: String, message: String): Result<String> = runCatching {
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
    }

    @SuppressLint("MissingPermission")
    override suspend fun getRecentSms(count: Int): Result<String> = withContext(Dispatchers.IO) {
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
        }
    }

    override suspend fun getSmsFromContact(contactName: String, count: Int): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!hasPermission(Manifest.permission.READ_SMS) || !hasPermission(Manifest.permission.READ_CONTACTS)) {
                return@runCatching "SMS and contacts permissions required."
            }

            // First find the contact's phone number
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

            val messages = mutableListOf<String>()
            // Query SMS for each phone number
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
        }
    }

    // ==========================================
    // NEW: Phone / Calls
    // ==========================================

    override suspend fun makeCall(phoneNumber: String): Result<String> = runCatching {
        if (!hasPermission(Manifest.permission.CALL_PHONE)) {
            // Fall back to dial intent (shows dialer, user presses call)
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return@runCatching "Opened dialer for $phoneNumber (call permission not granted for direct calling)"
        }

        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        "Calling $phoneNumber"
    }

    @SuppressLint("MissingPermission")
    override suspend fun getCallLog(count: Int): Result<String> = withContext(Dispatchers.IO) {
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
        }
    }

    // ==========================================
    // NEW: Location
    // ==========================================

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) &&
                !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                return@runCatching "Location permission not granted. Please grant location access in app settings."
            }

            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            // Try to get last known location from available providers
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
                } catch (_: Exception) {}
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
        }
    }

    // ==========================================
    // NEW: Device Admin
    // ==========================================

    override suspend fun lockScreen(): Result<String> = runCatching {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ClawDeviceAdminReceiver.getComponentName(context)
        if (!dpm.isAdminActive(adminComponent)) {
            return@runCatching "Device admin not active. Please enable it in Settings > Security > Device Admin."
        }
        dpm.lockNow()
        "Screen locked successfully"
    }

    override suspend fun setCameraDisabled(disabled: Boolean): Result<String> = runCatching {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ClawDeviceAdminReceiver.getComponentName(context)
        if (!dpm.isAdminActive(adminComponent)) {
            return@runCatching "Device admin not active. Please enable it in Settings > Security > Device Admin."
        }
        dpm.setCameraDisabled(adminComponent, disabled)
        "Camera ${if (disabled) "disabled" else "enabled"}"
    }

    override suspend fun setMaxScreenLockTimeout(timeoutMs: Long): Result<String> = runCatching {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ClawDeviceAdminReceiver.getComponentName(context)
        if (!dpm.isAdminActive(adminComponent)) {
            return@runCatching "Device admin not active. Please enable it in Settings > Security > Device Admin."
        }
        dpm.setMaximumTimeToLock(adminComponent, timeoutMs)
        "Max screen lock timeout set to ${timeoutMs / 1000} seconds"
    }

    override suspend fun getDeviceAdminStatus(): Result<String> = runCatching {
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
    }

    // ==========================================
    // NEW: Intent Messaging (WhatsApp, Telegram, etc.)
    // ==========================================

    override suspend fun sendIntentMessage(packageName: String, phoneNumber: String, message: String): Result<String> = runCatching {
        // Check if the app is installed
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

        when (packageName) {
            "com.whatsapp" -> {
                // WhatsApp uses a special URL scheme
                val phone = phoneNumber.replace("+", "").replace(" ", "").replace("-", "")
                val uri = Uri.parse("https://wa.me/$phone?text=${Uri.encode(message)}")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opening WhatsApp chat with $phoneNumber: ${message.take(50)}..."
            }
            "org.telegram.messenger" -> {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    setPackage("org.telegram.messenger")
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opening Telegram to share: ${message.take(50)}..."
            }
            else -> {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    setPackage(packageName)
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opening $packageName to share message"
            }
        }
    }

    override suspend fun shareText(text: String, packageName: String?): Result<String> = runCatching {
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
    }

    // ==========================================
    // Helpers
    // ==========================================

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
