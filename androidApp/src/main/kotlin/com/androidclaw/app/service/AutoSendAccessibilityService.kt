package com.androidclaw.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*

class AutoSendAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility service connected")

        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 300
            packageNames = SUPPORTED_PACKAGES.toTypedArray()
        }

        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !pendingSend) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName !in SUPPORTED_PACKAGES) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            scope.launch {
                // Small delay to let the UI fully load
                delay(1500)
                if (pendingSend) {
                    val sent = tryClickSend(packageName)
                    if (sent) {
                        Log.i(TAG, "Message auto-sent in $packageName")
                        pendingSend = false
                        pendingPackage = null
                    }
                }
            }
        }
    }

    private fun tryClickSend(packageName: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false

        return when (packageName) {
            "com.whatsapp" -> findAndClickSend(rootNode, listOf("Send", "send"))
            "org.telegram.messenger" -> findAndClickSend(rootNode, listOf("Send", "send"))
            "org.thoughtcrime.securesms" -> findAndClickSend(rootNode, listOf("Send", "send"))
            "com.viber.voip" -> findAndClickSend(rootNode, listOf("Send", "send"))
            else -> findAndClickSend(rootNode, listOf("Send", "send"))
        }
    }

    private fun findAndClickSend(root: AccessibilityNodeInfo, descriptions: List<String>): Boolean {
        // Try by content description
        for (desc in descriptions) {
            val nodes = root.findAccessibilityNodeInfosByText(desc)
            for (node in nodes) {
                if (node.isClickable && (node.contentDescription?.toString()?.contains(desc, ignoreCase = true) == true)) {
                    Log.d(TAG, "Found send button by description: ${node.contentDescription}")
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
            }
        }

        // Try finding send button by traversing all clickable nodes
        return findSendButtonRecursive(root)
    }

    private fun findSendButtonRecursive(node: AccessibilityNodeInfo): Boolean {
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""

        if (node.isClickable && (desc.contains("send") || text == "send")) {
            Log.d(TAG, "Found send button recursively: desc=$desc text=$text")
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findSendButtonRecursive(child)) return true
        }

        return false
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        instance = null
        Log.i(TAG, "Accessibility service destroyed")
    }

    companion object {
        private const val TAG = "AutoSendService"
        var instance: AutoSendAccessibilityService? = null
            private set

        var pendingSend = false
        var pendingPackage: String? = null

        fun isEnabled(): Boolean = instance != null

        fun requestAutoSend(packageName: String) {
            Log.i(TAG, "Requesting auto-send for $packageName")
            pendingSend = true
            pendingPackage = packageName
        }

        val SUPPORTED_PACKAGES = setOf(
            "com.whatsapp",
            "org.telegram.messenger",
            "org.thoughtcrime.securesms",
            "com.viber.voip",
            "com.facebook.orca",
        )
    }
}
