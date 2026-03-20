package com.androidclaw.app.platform

import android.app.AlarmManager
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import com.androidclaw.shared.tools.DeviceActionBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidDeviceActionBridge(
    private val context: Context
) : DeviceActionBridge {

    // -- Wi-Fi --
    override suspend fun setWifiEnabled(enabled: Boolean): Result<String> = runCatching {
        // Android 10+ requires Settings panel instead of direct toggle
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
                .take(50) // Limit to avoid overwhelming context
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
        // Requires NotificationListenerService - return guidance for now
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
}
