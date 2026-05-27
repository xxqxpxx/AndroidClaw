package com.androidclaw.app.scheduler

import android.util.Log
import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for scheduled tasks — backed by SQLDelight.
 * Provides CRUD + status updates for the scheduler system.
 */
class TaskRepository(private val driver: SqlDriver) {

    companion object {
        private const val TAG = "TaskRepo"
    }

    data class ScheduledTaskData(
        val id: String,
        val name: String,
        val description: String,
        val triggerType: String,
        val triggerTime: Long,
        val repeatIntervalMs: Long?,
        val steps: String,
        val status: String,
        val lastRunAt: Long?,
        val nextRunAt: Long?,
        val result: String?,
        val failureCount: Int,
        val maxRetries: Int,
        val createdAt: Long,
        val updatedAt: Long,
        val enabled: Boolean
    )

    suspend fun insertTask(
        id: String,
        name: String,
        description: String,
        triggerType: String,
        triggerTime: Long,
        repeatIntervalMs: Long?,
        steps: String
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        driver.execute(null,
            """INSERT INTO ScheduledTask(id, name, description, trigger_type, trigger_time, repeat_interval_ms, steps, status, next_run_at, created_at, updated_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, 'pending', ?, ?, ?)""",
            10
        ) {
            bindString(0, id)
            bindString(1, name)
            bindString(2, description)
            bindString(3, triggerType)
            bindLong(4, triggerTime)
            if (repeatIntervalMs != null) bindLong(5, repeatIntervalMs) else bindLong(5, null)
            bindString(6, steps)
            bindLong(7, triggerTime)
            bindLong(8, now)
            bindLong(9, now)
        }
        Log.i(TAG, "Inserted task $id: $name")
    }

    suspend fun getTask(id: String): ScheduledTaskData? = withContext(Dispatchers.IO) {
        var result: ScheduledTaskData? = null
        driver.executeQuery(null, "SELECT * FROM ScheduledTask WHERE id = ?", { cursor ->
            if (cursor.next().value) {
                result = cursorToTask(cursor)
            }
            app.cash.sqldelight.db.QueryResult.Value(Unit)
        }, 1) {
            bindString(0, id)
        }
        result
    }

    suspend fun getEnabledTasks(): List<ScheduledTaskData> = withContext(Dispatchers.IO) {
        val tasks = mutableListOf<ScheduledTaskData>()
        driver.executeQuery(null,
            "SELECT * FROM ScheduledTask WHERE enabled = 1 ORDER BY next_run_at ASC",
            { cursor ->
                while (cursor.next().value) {
                    cursorToTask(cursor)?.let { tasks.add(it) }
                }
                app.cash.sqldelight.db.QueryResult.Value(Unit)
            }, 0
        )
        tasks
    }

    suspend fun getPendingTasks(beforeTimeMs: Long): List<ScheduledTaskData> = withContext(Dispatchers.IO) {
        val tasks = mutableListOf<ScheduledTaskData>()
        driver.executeQuery(null,
            "SELECT * FROM ScheduledTask WHERE status = 'pending' AND enabled = 1 AND next_run_at <= ? ORDER BY next_run_at ASC",
            { cursor ->
                while (cursor.next().value) {
                    cursorToTask(cursor)?.let { tasks.add(it) }
                }
                app.cash.sqldelight.db.QueryResult.Value(Unit)
            }, 1
        ) {
            bindLong(0, beforeTimeMs)
        }
        tasks
    }

    suspend fun markDone(id: String, result: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        driver.execute(null,
            "UPDATE ScheduledTask SET status = 'done', last_run_at = ?, result = ?, updated_at = ? WHERE id = ?",
            4
        ) {
            bindLong(0, now)
            bindString(1, result)
            bindLong(2, now)
            bindString(3, id)
        }
    }

    suspend fun markFailed(id: String, reason: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        driver.execute(null,
            "UPDATE ScheduledTask SET failure_count = failure_count + 1, status = 'failed', result = ?, updated_at = ? WHERE id = ?",
            3
        ) {
            bindString(0, reason)
            bindLong(1, now)
            bindString(2, id)
        }
    }

    suspend fun scheduleNextRun(id: String, nextRunAt: Long) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        driver.execute(null,
            "UPDATE ScheduledTask SET next_run_at = ?, status = 'pending', updated_at = ? WHERE id = ?",
            3
        ) {
            bindLong(0, nextRunAt)
            bindLong(1, now)
            bindString(2, id)
        }
    }

    suspend fun deleteTask(id: String) = withContext(Dispatchers.IO) {
        driver.execute(null, "DELETE FROM ScheduledTask WHERE id = ?", 1) {
            bindString(0, id)
        }
    }

    suspend fun getTaskHistory(limit: Int = 20): List<ScheduledTaskData> = withContext(Dispatchers.IO) {
        val tasks = mutableListOf<ScheduledTaskData>()
        driver.executeQuery(null,
            "SELECT * FROM ScheduledTask WHERE status IN ('done', 'failed') ORDER BY last_run_at DESC LIMIT ?",
            { cursor ->
                while (cursor.next().value) {
                    cursorToTask(cursor)?.let { tasks.add(it) }
                }
                app.cash.sqldelight.db.QueryResult.Value(Unit)
            }, 1
        ) {
            bindLong(0, limit.toLong())
        }
        tasks
    }

    private fun cursorToTask(cursor: app.cash.sqldelight.db.SqlCursor): ScheduledTaskData? {
        return try {
            ScheduledTaskData(
                id = cursor.getString(0) ?: return null,
                name = cursor.getString(1) ?: "",
                description = cursor.getString(2) ?: "",
                triggerType = cursor.getString(3) ?: "once",
                triggerTime = cursor.getLong(4) ?: 0,
                repeatIntervalMs = cursor.getLong(5),
                steps = cursor.getString(6) ?: "[]",
                status = cursor.getString(7) ?: "pending",
                lastRunAt = cursor.getLong(8),
                nextRunAt = cursor.getLong(9),
                result = cursor.getString(10),
                failureCount = cursor.getLong(11)?.toInt() ?: 0,
                maxRetries = cursor.getLong(12)?.toInt() ?: 3,
                createdAt = cursor.getLong(13) ?: 0,
                updatedAt = cursor.getLong(14) ?: 0,
                enabled = cursor.getLong(15) == 1L
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse task row: ${e.message}")
            null
        }
    }
}
