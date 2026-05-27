package com.androidclaw.app.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

/**
 * Task scheduler with graceful degradation:
 * 1. setExactAndAllowWhileIdle() — exact alarm (API 23+, needs SCHEDULE_EXACT_ALARM on 31+)
 * 2. setAndAllowWhileIdle() — inexact but reliable
 * 3. WorkManager periodic — deferrable, battery-friendly
 * 4. Skip with notification — if all else restricted
 *
 * Based on the official AndroidClaw scheduler pattern.
 */
class TaskScheduler(private val context: Context) {

    companion object {
        private const val TAG = "TaskScheduler"
        private const val ACTION_TASK_TRIGGER = "com.androidclaw.TASK_TRIGGER"
        private const val EXTRA_TASK_ID = "task_id"
    }

    enum class ScheduleMethod {
        EXACT_ALARM,       // Best precision
        INEXACT_ALARM,     // Reliable but may drift
        WORK_MANAGER,      // Battery-friendly, may delay
        SKIPPED            // Couldn't schedule at all
    }

    data class ScheduleResult(
        val method: ScheduleMethod,
        val message: String
    )

    /**
     * Schedule a task at the given time with automatic degradation.
     */
    fun scheduleTask(taskId: String, triggerTimeMs: Long, taskName: String): ScheduleResult {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, TaskTriggerReceiver::class.java).apply {
            action = ACTION_TASK_TRIGGER
            putExtra(EXTRA_TASK_ID, taskId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, taskId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Try exact alarm first
        if (canScheduleExactAlarms(alarmManager)) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent
                )
                Log.i(TAG, "Scheduled '$taskName' with exact alarm at $triggerTimeMs")
                return ScheduleResult(ScheduleMethod.EXACT_ALARM, "Scheduled with exact timing")
            } catch (e: SecurityException) {
                Log.w(TAG, "Exact alarm denied: ${e.message}")
            }
        }

        // Fall back to inexact alarm
        try {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent
            )
            Log.i(TAG, "Scheduled '$taskName' with inexact alarm at $triggerTimeMs")
            return ScheduleResult(ScheduleMethod.INEXACT_ALARM, "Scheduled (may vary by a few minutes)")
        } catch (e: SecurityException) {
            Log.w(TAG, "Inexact alarm also denied: ${e.message}")
        }

        // Fall back to WorkManager
        val delay = triggerTimeMs - System.currentTimeMillis()
        if (delay > 0) {
            val workRequest = OneTimeWorkRequestBuilder<TaskWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("task_id" to taskId))
                .addTag("task_$taskId")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "task_$taskId", ExistingWorkPolicy.REPLACE, workRequest
            )
            Log.i(TAG, "Scheduled '$taskName' via WorkManager (${delay}ms delay)")
            return ScheduleResult(ScheduleMethod.WORK_MANAGER, "Scheduled via WorkManager (battery-optimized, may delay)")
        }

        Log.w(TAG, "Could not schedule '$taskName' — all methods failed")
        return ScheduleResult(ScheduleMethod.SKIPPED, "Could not schedule — alarm permissions restricted")
    }

    /**
     * Schedule a repeating task.
     */
    fun scheduleRepeating(taskId: String, intervalMs: Long, taskName: String): ScheduleResult {
        val workRequest = PeriodicWorkRequestBuilder<TaskWorker>(
            intervalMs, TimeUnit.MILLISECONDS
        )
            .setInputData(workDataOf("task_id" to taskId))
            .addTag("task_$taskId")
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "task_$taskId", ExistingPeriodicWorkPolicy.UPDATE, workRequest
        )
        Log.i(TAG, "Scheduled repeating '$taskName' every ${intervalMs}ms via WorkManager")
        return ScheduleResult(ScheduleMethod.WORK_MANAGER, "Repeating task scheduled (interval: ${intervalMs / 60000}min)")
    }

    /**
     * Cancel a scheduled task.
     */
    fun cancelTask(taskId: String) {
        // Cancel alarm
        val intent = Intent(context, TaskTriggerReceiver::class.java).apply {
            action = ACTION_TASK_TRIGGER
            putExtra(EXTRA_TASK_ID, taskId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, taskId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)

        // Cancel WorkManager
        WorkManager.getInstance(context).cancelUniqueWork("task_$taskId")
        Log.i(TAG, "Cancelled task $taskId")
    }

    private fun canScheduleExactAlarms(alarmManager: AlarmManager): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true // Pre-S always allowed
        }
    }
}

/**
 * BroadcastReceiver that fires when a scheduled alarm triggers.
 */
class TaskTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra("task_id") ?: return
        Log.i("TaskTrigger", "Task triggered: $taskId")
        // Delegate to TaskWorker for actual execution
        val workRequest = OneTimeWorkRequestBuilder<TaskWorker>()
            .setInputData(workDataOf("task_id" to taskId))
            .build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }
}

/**
 * WorkManager worker that executes a scheduled task's steps.
 */
class TaskWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString("task_id") ?: return Result.failure()
        Log.i("TaskWorker", "Executing task: $taskId")

        return try {
            // Get the task repository and execute
            val repo = org.koin.java.KoinJavaComponent.getKoin()
                .getOrNull<TaskRepository>()
            if (repo == null) {
                Log.e("TaskWorker", "TaskRepository not available")
                return Result.failure()
            }

            val task = repo.getTask(taskId)
            if (task == null) {
                Log.w("TaskWorker", "Task $taskId not found in DB")
                return Result.failure()
            }

            // Execute steps via the agent (DeviceActionBridge)
            val bridge = org.koin.java.KoinJavaComponent.getKoin()
                .getOrNull<com.androidclaw.shared.tools.DeviceActionBridge>()

            val now = System.currentTimeMillis()
            if (bridge == null) {
                repo.markFailed(taskId, "DeviceActionBridge not available")
                return Result.failure()
            }

            // Parse and execute steps
            val steps = kotlinx.serialization.json.Json.decodeFromString<List<TaskStep>>(task.steps)
            val results = mutableListOf<String>()
            var succeeded = 0

            for (step in steps) {
                try {
                    val result = executeStep(bridge, step)
                    results.add("✓ ${step.action}: $result")
                    succeeded++
                } catch (e: Exception) {
                    results.add("✗ ${step.action}: ${e.message}")
                }
            }

            val summary = "$succeeded/${steps.size} steps succeeded"
            repo.markDone(taskId, "$summary\n${results.joinToString("\n")}")

            // Schedule next run if repeating
            if (task.repeatIntervalMs != null && task.repeatIntervalMs > 0) {
                val nextRun = now + task.repeatIntervalMs
                repo.scheduleNextRun(taskId, nextRun)
                TaskScheduler(applicationContext).scheduleTask(taskId, nextRun, task.name)
            }

            Log.i("TaskWorker", "Task $taskId complete: $summary")
            Result.success()
        } catch (e: Exception) {
            Log.e("TaskWorker", "Task $taskId failed: ${e.message}", e)
            Result.retry()
        }
    }

    private suspend fun executeStep(
        bridge: com.androidclaw.shared.tools.DeviceActionBridge,
        step: TaskStep
    ): String {
        return when (step.action) {
            "launch_app" -> bridge.launchApp(step.params["package"] ?: "").getOrThrow()
            "set_volume" -> bridge.setVolume(step.params["stream"] ?: "music", step.params["level"]?.toIntOrNull() ?: 50).getOrThrow()
            "set_brightness" -> bridge.setBrightness(step.params["level"]?.toIntOrNull() ?: 50).getOrThrow()
            "set_dnd" -> bridge.setDoNotDisturb(step.params["enabled"]?.toBooleanStrictOrNull() ?: true).getOrThrow()
            "set_wifi" -> bridge.setWifiEnabled(step.params["enabled"]?.toBooleanStrictOrNull() ?: true).getOrThrow()
            "play_music" -> bridge.playMusic(step.params["query"] ?: "", step.params["app"] ?: "").getOrThrow()
            "set_alarm" -> bridge.setAlarm(
                step.params["hour"]?.toIntOrNull() ?: 7,
                step.params["minute"]?.toIntOrNull() ?: 0,
                step.params["label"] ?: ""
            ).getOrThrow()
            "send_sms" -> bridge.sendSms(step.params["phone"] ?: "", step.params["message"] ?: "").getOrThrow()
            "open_url" -> bridge.openUrl(step.params["url"] ?: "").getOrThrow()
            "navigate" -> bridge.navigateTo(step.params["destination"] ?: "").getOrThrow()
            "apply_mode" -> bridge.applyDeviceMode(step.params["mode"] ?: "normal").getOrThrow()
            "read_aloud" -> bridge.readAloud(step.params["text"] ?: "").getOrThrow()
            "vision_tap" -> bridge.visionFindAndTap(step.params["target"] ?: "").getOrThrow()
            else -> "Unknown action: ${step.action}"
        }
    }
}

@kotlinx.serialization.Serializable
data class TaskStep(
    val action: String,
    val params: Map<String, String> = emptyMap()
)
