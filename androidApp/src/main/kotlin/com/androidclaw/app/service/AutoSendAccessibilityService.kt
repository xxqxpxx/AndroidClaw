package com.androidclaw.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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

    // ==========================================
    // Chrome tab sorting
    // ==========================================

    private data class TabCard(val title: String, val bounds: Rect)

    /**
     * Opens Chrome's tab switcher and reorders the open tabs by title using
     * long-press drag gestures. Chrome exposes no API for this, so it is done
     * entirely through the accessibility tree and is best-effort.
     */
    suspend fun sortChromeTabs(order: String): String {
        val ascending = !order.equals("reverse_alphabetical", ignoreCase = true)
        val byTitle = compareBy<String> { it.lowercase() }
        val comparator = if (ascending) byTitle else byTitle.reversed()

        // Make sure we're in the tab switcher grid before reading cards.
        if (readChromeTabs().isEmpty()) {
            if (!openTabSwitcher()) {
                return "Couldn't open Chrome's tab switcher. Make sure Chrome is open in the foreground."
            }
            delay(1200)
        }

        var tabs = readChromeTabs()
        if (tabs.isEmpty()) {
            return "No Chrome tabs were found. Open Chrome (with the tab switcher reachable) and try again."
        }
        if (tabs.size == 1) {
            return "Only one Chrome tab is open — nothing to sort."
        }

        val originalTitles = tabs.map { it.title }
        val desired = originalTitles.sortedWith(comparator)
        if (originalTitles == desired) {
            return "Chrome tabs are already sorted ${if (ascending) "A-Z" else "Z-A"} (${tabs.size} tabs)."
        }

        // Selection sort: drag the correct tab into each slot, re-reading after
        // every move since the visual positions shift.
        var moves = 0
        for (i in 0 until desired.size - 1) {
            tabs = readChromeTabs()
            if (i >= tabs.size) break
            if (tabs[i].title.equals(desired[i], ignoreCase = true)) continue

            val from = (i + 1 until tabs.size).firstOrNull {
                tabs[it].title.equals(desired[i], ignoreCase = true)
            } ?: continue

            val dragged = dragTab(tabs[from].bounds, tabs[i].bounds)
            if (dragged) {
                moves++
                delay(700)
            }
        }

        val finalTitles = readChromeTabs().map { it.title }
        val sortedNow = finalTitles == finalTitles.sortedWith(comparator)
        val orderLabel = if (ascending) "A-Z" else "Z-A"
        return if (sortedNow) {
            "Sorted ${finalTitles.size} Chrome tabs $orderLabel ($moves moves)."
        } else {
            "Attempted to sort ${finalTitles.size} Chrome tabs $orderLabel with $moves moves. " +
                "Chrome's tab grid may not have fully reordered; current order: " +
                finalTitles.joinToString(", ")
        }
    }

    /**
     * Closes Chrome tabs in the tab switcher. filter="duplicates" keeps one tab per
     * title and closes the rest; filter="all" closes tabs down to the last one.
     */
    suspend fun closeChromeTabs(filter: String): String {
        if (readChromeTabs().isEmpty()) {
            if (!openTabSwitcher()) {
                return "Couldn't open Chrome's tab switcher. Make sure Chrome is open in the foreground."
            }
            delay(1200)
        }
        val initial = readChromeTabs()
        if (initial.isEmpty()) return "No Chrome tabs were found."

        val dedupe = !filter.equals("all", ignoreCase = true)
        var closed = 0
        var guard = 0
        while (guard++ < 200) {
            val buttons = readChromeTabCloseButtons()
            if (buttons.isEmpty()) break
            val target: AccessibilityNodeInfo? = if (dedupe) {
                val seen = mutableSetOf<String>()
                buttons.firstOrNull { !seen.add(it.first.lowercase()) }?.second
            } else {
                if (buttons.size <= 1) null else buttons.first().second
            }
            if (target == null) break

            val clicked = target.performAction(AccessibilityNodeInfo.ACTION_CLICK) ||
                (target.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false)
            if (!clicked) break
            closed++
            delay(500)
        }
        return when {
            closed == 0 && dedupe -> "No duplicate Chrome tabs found (${initial.size} tabs open)."
            dedupe -> "Closed $closed duplicate Chrome tab(s); kept one of each."
            else -> "Closed $closed Chrome tab(s) (kept the last one open)."
        }
    }

    private fun readChromeTabCloseButtons(): List<Pair<String, AccessibilityNodeInfo>> {
        val root = rootInActiveWindow ?: return emptyList()
        val out = mutableListOf<Triple<String, AccessibilityNodeInfo, Rect>>()
        collectCloseButtons(root, out)
        return out.sortedWith(compareBy({ it.third.top / 100 }, { it.third.left }))
            .map { it.first to it.second }
    }

    private fun collectCloseButtons(node: AccessibilityNodeInfo, out: MutableList<Triple<String, AccessibilityNodeInfo, Rect>>) {
        val desc = node.contentDescription?.toString()
        if (desc != null) {
            val match = closeTabRegex.find(desc.trim())
            if (match != null) {
                val title = match.groupValues[1].trim()
                val bounds = Rect().also { node.getBoundsInScreen(it) }
                if (title.isNotEmpty()) out.add(Triple(title, node, bounds))
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectCloseButtons(child, out)
        }
    }

    private fun openTabSwitcher(): Boolean {
        val root = rootInActiveWindow ?: return false
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        collectTabSwitcherButtons(root, candidates)
        val button = candidates.firstOrNull() ?: return false
        return button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun collectTabSwitcherButtons(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val isSwitcher = node.isClickable && (
            desc.contains("switch or close tabs") ||
                (desc.contains("tab") && (desc.contains("open") || desc.contains("switch")))
            )
        if (isSwitcher) out.add(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTabSwitcherButtons(child, out)
        }
    }

    private fun readChromeTabs(): List<TabCard> {
        val root = rootInActiveWindow ?: return emptyList()
        val cards = mutableListOf<TabCard>()
        collectTabCards(root, cards)
        // Visual order: group into rows (by top), then left-to-right within a row.
        return cards.sortedWith(
            compareBy({ it.bounds.top / 100 }, { it.bounds.left })
        )
    }

    private val closeTabRegex = Regex("(?i)close\\s+(.+?)\\s+tab\\.?$")

    private fun collectTabCards(node: AccessibilityNodeInfo, out: MutableList<TabCard>) {
        val desc = node.contentDescription?.toString()
        if (desc != null) {
            val match = closeTabRegex.find(desc.trim())
            if (match != null) {
                val title = match.groupValues[1].trim()
                val cardNode = node.parent ?: node
                val bounds = Rect().also { cardNode.getBoundsInScreen(it) }
                if (title.isNotEmpty() && !bounds.isEmpty) {
                    out.add(TabCard(title, bounds))
                }
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTabCards(child, out)
        }
    }

    private suspend fun dragTab(from: Rect, to: Rect): Boolean {
        val startX = from.exactCenterX()
        val startY = from.exactCenterY()
        val endX = to.exactCenterX()
        val endY = to.exactCenterY()

        // Long-press to pick the tab up, then continue the same gesture to drop it.
        val pressPath = Path().apply { moveTo(startX, startY); lineTo(startX, startY) }
        val pressStroke = GestureDescription.StrokeDescription(pressPath, 0, LONG_PRESS_MS, true)
        if (!dispatch(GestureDescription.Builder().addStroke(pressStroke).build())) return false

        val movePath = Path().apply { moveTo(startX, startY); lineTo(endX, endY) }
        val moveStroke = pressStroke.continueStroke(movePath, 0, DRAG_MS, false)
        return dispatch(GestureDescription.Builder().addStroke(moveStroke).build())
    }

    private suspend fun dispatch(gesture: GestureDescription): Boolean = suspendCoroutine { cont ->
        val dispatched = dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(description: GestureDescription?) {
                cont.resume(true)
            }

            override fun onCancelled(description: GestureDescription?) {
                cont.resume(false)
            }
        }, null)
        if (!dispatched) cont.resume(false)
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
        private const val LONG_PRESS_MS = 700L
        private const val DRAG_MS = 600L
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
            "com.android.chrome",
        )
    }
}
