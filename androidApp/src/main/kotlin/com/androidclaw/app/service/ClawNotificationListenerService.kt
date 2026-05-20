package com.androidclaw.app.service

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Listens for all notifications on the device, storing them
 * so the LLM assistant can read/summarize/dismiss them.
 */
class ClawNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "ClawNotifListener"
        private const val MAX_STORED = 200

        /** Thread-safe deque of captured notifications (newest first). */
        private val capturedNotifications = ConcurrentLinkedDeque<NotificationSnapshot>()

        /** Whether the service is currently connected/active. */
        @Volatile
        var isConnected = false
            private set

        /** Live reference so the bridge can call cancelNotification(key). */
        @Volatile
        var instance: ClawNotificationListenerService? = null
            private set

        fun getRecent(count: Int): List<NotificationSnapshot> =
            capturedNotifications.take(count.coerceAtLeast(1))

        fun getEmailNotifications(count: Int): List<NotificationSnapshot> {
            val emailPackages = setOf(
                "com.google.android.gm",       // Gmail
                "com.microsoft.office.outlook", // Outlook
                "com.yahoo.mobile.client.android.mail",
                "com.samsung.android.email.provider",
                "me.bluemail.mail",
                "org.kman.AquaMail",
                "com.easilydo.mail"             // Edison Mail
            )
            return capturedNotifications
                .filter { it.packageName in emailPackages }
                .take(count.coerceAtLeast(1))
        }

        fun clear() {
            capturedNotifications.clear()
        }
    }

    data class NotificationSnapshot(
        val key: String,
        val packageName: String,
        val appName: String,
        val title: String,
        val text: String,
        val subText: String,
        val timestamp: Long,
        val isOngoing: Boolean,
        val category: String?
    ) {
        fun toReadableString(): String {
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
            val parts = mutableListOf<String>()
            parts += "[$time] $appName"
            if (title.isNotBlank()) parts += "Title: $title"
            if (text.isNotBlank()) parts += "Content: $text"
            if (subText.isNotBlank()) parts += "Sub: $subText"
            if (isOngoing) parts += "(ongoing)"
            parts += "Key: $key"
            return parts.joinToString(" | ")
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        instance = this
        Log.i(TAG, "NotificationListenerService connected")

        // Capture existing active notifications
        try {
            activeNotifications?.forEach { sbn ->
                capturedNotifications.addFirst(sbn.toSnapshot())
            }
            trimIfNeeded()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read existing notifications", e)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        instance = null
        Log.i(TAG, "NotificationListenerService disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        capturedNotifications.addFirst(sbn.toSnapshot())
        trimIfNeeded()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // We keep removed notifications in history so the LLM can still reference them
    }

    fun dismissByKey(key: String): Boolean {
        return try {
            cancelNotification(key)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dismiss notification: $key", e)
            false
        }
    }

    fun dismissAll(): Boolean {
        return try {
            cancelAllNotifications()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dismiss all notifications", e)
            false
        }
    }

    private fun StatusBarNotification.toSnapshot(): NotificationSnapshot {
        val extras = notification.extras
        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }

        return NotificationSnapshot(
            key = key,
            packageName = packageName,
            appName = appName,
            title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "",
            text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "",
            subText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: "",
            timestamp = postTime,
            isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0,
            category = notification.category
        )
    }

    private fun trimIfNeeded() {
        while (capturedNotifications.size > MAX_STORED) {
            capturedNotifications.removeLast()
        }
    }
}
