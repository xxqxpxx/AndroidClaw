package com.androidclaw.app.vision

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.abs

/**
 * Executes screen actions (tap, swipe, scroll) with:
 * - Stuck loop detection (3 identical screen states → recovery)
 * - Repetition tracking (same coords tapped 3x → force different strategy)
 * - Step limit (max 30 steps per goal)
 * - Action feedback loop (every result fed back)
 *
 * This is the "hands" of the vision system — VisionService is the "eyes".
 */
class ActionExecutor(
    private val service: AccessibilityService
) {
    companion object {
        private const val TAG = "ActionExecutor"
        const val MAX_STEPS_PER_GOAL = 30
        private const val STUCK_THRESHOLD = 3
        private const val REPEAT_THRESHOLD = 3
        private const val SCREEN_HASH_HISTORY = 3
        private const val ACTION_HISTORY_SIZE = 5
        private const val TAP_PROXIMITY_PX = 50 // consider same coord if within 50px
    }

    // Tracking state — reset per goal via resetGoal()
    private val screenHashes = ArrayDeque<Int>(SCREEN_HASH_HISTORY + 1)
    private val actionHistory = ArrayDeque<ActionRecord>(ACTION_HISTORY_SIZE + 1)
    private var stepCount = 0
    private var isStuck = false

    data class ActionRecord(
        val type: String, // "tap", "swipe", "scroll", "back"
        val x: Int,
        val y: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

    sealed class ActionResult {
        data class Success(val description: String) : ActionResult()
        data class StuckDetected(val message: String) : ActionResult()
        data class RepeatDetected(val message: String) : ActionResult()
        data class StepLimitReached(val totalSteps: Int) : ActionResult()
        data class Failed(val reason: String) : ActionResult()
    }

    /** Reset tracking for a new goal/task. */
    fun resetGoal() {
        screenHashes.clear()
        actionHistory.clear()
        stepCount = 0
        isStuck = false
        Log.i(TAG, "Goal reset — tracking cleared")
    }

    /** Current step count. */
    fun currentStep(): Int = stepCount

    /** Whether we're in a stuck state. */
    fun isStuck(): Boolean = isStuck

    /**
     * Tap at absolute screen coordinates with full safety checks.
     */
    suspend fun tap(x: Int, y: Int, label: String = ""): ActionResult {
        // Step limit check
        if (++stepCount > MAX_STEPS_PER_GOAL) {
            return ActionResult.StepLimitReached(stepCount)
        }

        // Repetition check
        val record = ActionRecord("tap", x, y)
        addAction(record)
        if (isRepeating(x, y)) {
            isStuck = true
            return ActionResult.RepeatDetected(
                "Tapped near ($x, $y) $REPEAT_THRESHOLD+ times without progress. Forcing different strategy."
            )
        }

        // Execute the tap
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val success = dispatchGesture(gesture)

        if (!success) {
            return ActionResult.Failed("Gesture dispatch failed at ($x, $y)")
        }

        // Wait for screen to settle
        delay(800)

        // Check for stuck state (screen unchanged)
        val currentHash = getScreenHash()
        addScreenHash(currentHash)
        if (isScreenStuck()) {
            isStuck = true
            return ActionResult.StuckDetected(
                "Screen hasn't changed after $STUCK_THRESHOLD actions. Recovery needed (try scrolling or going back)."
            )
        }

        isStuck = false
        val desc = if (label.isNotEmpty()) "Tapped '$label' at ($x, $y)" else "Tapped at ($x, $y)"
        Log.i(TAG, "$desc — step $stepCount")
        return ActionResult.Success(desc)
    }

    /**
     * Swipe from one point to another.
     */
    suspend fun swipe(
        startX: Int, startY: Int,
        endX: Int, endY: Int,
        durationMs: Long = 300
    ): ActionResult {
        if (++stepCount > MAX_STEPS_PER_GOAL) {
            return ActionResult.StepLimitReached(stepCount)
        }

        addAction(ActionRecord("swipe", startX, startY))

        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val success = dispatchGesture(gesture)

        if (!success) return ActionResult.Failed("Swipe failed")

        delay(600)
        addScreenHash(getScreenHash())

        return ActionResult.Success("Swiped from ($startX,$startY) to ($endX,$endY)")
    }

    /**
     * Scroll down on the current screen (useful for stuck recovery).
     */
    suspend fun scrollDown(): ActionResult {
        val metrics = service.resources.displayMetrics
        val centerX = metrics.widthPixels / 2
        val startY = (metrics.heightPixels * 0.7).toInt()
        val endY = (metrics.heightPixels * 0.3).toInt()
        return swipe(centerX, startY, centerX, endY, 400)
    }

    /**
     * Scroll up on the current screen.
     */
    suspend fun scrollUp(): ActionResult {
        val metrics = service.resources.displayMetrics
        val centerX = metrics.widthPixels / 2
        val startY = (metrics.heightPixels * 0.3).toInt()
        val endY = (metrics.heightPixels * 0.7).toInt()
        return swipe(centerX, startY, centerX, endY, 400)
    }

    /**
     * Press the system Back button (stuck recovery).
     */
    suspend fun goBack(): ActionResult {
        if (++stepCount > MAX_STEPS_PER_GOAL) {
            return ActionResult.StepLimitReached(stepCount)
        }
        addAction(ActionRecord("back", 0, 0))
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        delay(600)
        addScreenHash(getScreenHash())
        return ActionResult.Success("Pressed Back")
    }

    /**
     * Attempt automatic recovery when stuck: scroll, then back.
     */
    suspend fun attemptRecovery(): ActionResult {
        Log.i(TAG, "Attempting recovery from stuck state")
        // Try scrolling first
        val scrollResult = scrollDown()
        delay(500)
        val hashAfterScroll = getScreenHash()
        if (screenHashes.isEmpty() || hashAfterScroll != screenHashes.last()) {
            isStuck = false
            screenHashes.clear()
            return ActionResult.Success("Recovery: scrolled and screen changed")
        }

        // Try going back
        val backResult = goBack()
        delay(500)
        val hashAfterBack = getScreenHash()
        if (screenHashes.isEmpty() || hashAfterBack != screenHashes.last()) {
            isStuck = false
            screenHashes.clear()
            return ActionResult.Success("Recovery: pressed Back and screen changed")
        }

        return ActionResult.Failed("Recovery failed — screen unchanged after scroll and back. Manual intervention needed.")
    }

    // --- Private helpers ---

    private suspend fun dispatchGesture(gesture: GestureDescription): Boolean =
        suspendCancellableCoroutine { cont ->
            val dispatched = service.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(d: GestureDescription?) = cont.resume(true)
                    override fun onCancelled(d: GestureDescription?) = cont.resume(false)
                },
                null
            )
            if (!dispatched) cont.resume(false)
        }

    /**
     * Hash the current accessibility tree to detect screen changes.
     * Simple but effective: hash the concatenated text of all visible nodes.
     */
    private fun getScreenHash(): Int {
        val root = service.rootInActiveWindow ?: return 0
        val sb = StringBuilder()
        collectNodeText(root, sb, depth = 0, maxDepth = 6)
        return sb.toString().hashCode()
    }

    private fun collectNodeText(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int, maxDepth: Int) {
        if (depth > maxDepth) return
        node.text?.let { sb.append(it).append('|') }
        node.contentDescription?.let { sb.append(it).append('|') }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectNodeText(child, sb, depth + 1, maxDepth)
        }
    }

    private fun addScreenHash(hash: Int) {
        screenHashes.addLast(hash)
        if (screenHashes.size > SCREEN_HASH_HISTORY) screenHashes.removeFirst()
    }

    private fun isScreenStuck(): Boolean {
        if (screenHashes.size < STUCK_THRESHOLD) return false
        return screenHashes.all { it == screenHashes.last() }
    }

    private fun addAction(record: ActionRecord) {
        actionHistory.addLast(record)
        if (actionHistory.size > ACTION_HISTORY_SIZE) actionHistory.removeFirst()
    }

    private fun isRepeating(x: Int, y: Int): Boolean {
        val recent = actionHistory.filter { it.type == "tap" }
        if (recent.size < REPEAT_THRESHOLD) return false
        val nearSame = recent.takeLast(REPEAT_THRESHOLD).count { r ->
            abs(r.x - x) < TAP_PROXIMITY_PX && abs(r.y - y) < TAP_PROXIMITY_PX
        }
        return nearSame >= REPEAT_THRESHOLD
    }
}
