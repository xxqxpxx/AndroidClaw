package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

/**
 * Automates tedious, repetitive maintenance tasks: storage cleanup (duplicates,
 * large/old files), screenshot tidying, surfacing unused apps, one-shot device
 * "modes", and Chrome tab cleanup.
 */
class AutomationTool(
    private val bridge: DeviceActionBridge
) : Tool {

    override val name = "automation"

    override val description = """Automate boring/repetitive maintenance tasks. Actions:
        | - find_duplicate_files: find duplicate files in a folder (content hash) and reclaimable space
        | - find_large_files: list the biggest files in a folder (min_size_mb threshold)
        | - find_old_files: list files not modified in the last older_than_days days
        | - find_screenshots: count screenshots and their total size
        | - cleanup_screenshots: move screenshots older than older_than_days into a reversible trash folder
        | - suggest_unused_apps: list user apps not opened in the last days (needs Usage Access)
        | - apply_mode: apply a bundle of settings at once (focus, sleep, battery_saver, outdoor, normal)
        | - close_chrome_tabs: close duplicate tabs (filter=duplicates) or all-but-one (filter=all); needs accessibility
        | - list_chrome_tabs: read open Chrome tab titles so you can cluster/group them by topic, then act (sort/close)
        | - find_blurry_photos: scan DCIM/Pictures and list likely-blurry photos
        | - find_similar_photos: group visually near-duplicate photos (perceptual hash)
        | - cleanup_photos: move blurry photos (criteria=blurry) into a reversible trash folder
        | - clear_notifications_from_app: dismiss active notifications from a package_name
        | - clear_notifications_by_keyword: dismiss active notifications matching a keyword
        | - delete_old_sms: delete SMS older than older_than_days (needs default-SMS-app role)
        | - run_routine: run a bundle of cleanup checks at once (routine=storage or full)
        |The 'directory' accepts named folders (Downloads, Pictures, DCIM, Documents, Music, Movies, Camera) or an absolute path.""".trimMargin()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("description", "The automation action to perform")
                putJsonArray("enum") {
                    add("find_duplicate_files"); add("find_large_files"); add("find_old_files")
                    add("find_screenshots"); add("cleanup_screenshots")
                    add("suggest_unused_apps"); add("apply_mode"); add("close_chrome_tabs"); add("list_chrome_tabs")
                    add("find_blurry_photos"); add("find_similar_photos"); add("cleanup_photos")
                    add("clear_notifications_from_app"); add("clear_notifications_by_keyword")
                    add("delete_old_sms"); add("run_routine")
                }
            }
            putJsonObject("directory") {
                put("type", "string")
                put("description", "Folder name or absolute path (default Downloads)")
            }
            putJsonObject("min_size_mb") {
                put("type", "integer")
                put("description", "Minimum size in MB for find_large_files (default 50)")
            }
            putJsonObject("older_than_days") {
                put("type", "integer")
                put("description", "Age threshold in days for find_old_files / cleanup_screenshots")
            }
            putJsonObject("days") {
                put("type", "integer")
                put("description", "Lookback window in days for suggest_unused_apps (default 30)")
            }
            putJsonObject("mode") {
                put("type", "string")
                putJsonArray("enum") { add("focus"); add("sleep"); add("battery_saver"); add("outdoor"); add("normal") }
                put("description", "Device mode for apply_mode")
            }
            putJsonObject("filter") {
                put("type", "string")
                putJsonArray("enum") { add("duplicates"); add("all") }
                put("description", "Which Chrome tabs to close for close_chrome_tabs (default duplicates)")
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "Max photos to scan for find_blurry_photos / find_similar_photos (default 200)")
            }
            putJsonObject("criteria") {
                put("type", "string")
                putJsonArray("enum") { add("blurry") }
                put("description", "What to clean for cleanup_photos (currently 'blurry')")
            }
            putJsonObject("package_name") {
                put("type", "string")
                put("description", "App package for clear_notifications_from_app")
            }
            putJsonObject("keyword") {
                put("type", "string")
                put("description", "Keyword to match for clear_notifications_by_keyword")
            }
            putJsonObject("routine") {
                put("type", "string")
                putJsonArray("enum") { add("storage"); add("full") }
                put("description", "Which cleanup bundle to run for run_routine")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val action = input["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing required parameter: action", isError = true)

        val directory = input["directory"]?.jsonPrimitive?.contentOrNull ?: "Downloads"

        val result = when (action) {
            "find_duplicate_files" -> bridge.findDuplicateFiles(directory)
            "find_large_files" -> {
                val min = input["min_size_mb"]?.jsonPrimitive?.intOrNull ?: 50
                bridge.findLargeFiles(directory, min)
            }
            "find_old_files" -> {
                val days = input["older_than_days"]?.jsonPrimitive?.intOrNull ?: 90
                bridge.findOldFiles(directory, days)
            }
            "find_screenshots" -> bridge.findScreenshots()
            "cleanup_screenshots" -> {
                val days = input["older_than_days"]?.jsonPrimitive?.intOrNull ?: 30
                bridge.cleanupScreenshots(days)
            }
            "suggest_unused_apps" -> {
                val days = input["days"]?.jsonPrimitive?.intOrNull ?: 30
                bridge.suggestUnusedApps(days)
            }
            "apply_mode" -> {
                val mode = input["mode"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing mode (focus/sleep/battery_saver/outdoor/normal)", isError = true)
                bridge.applyDeviceMode(mode)
            }
            "close_chrome_tabs" -> {
                val filter = input["filter"]?.jsonPrimitive?.contentOrNull ?: "duplicates"
                bridge.closeChromeTabs(filter)
            }
            "list_chrome_tabs" -> bridge.getChromeTabs()
            "find_blurry_photos" -> {
                val limit = input["limit"]?.jsonPrimitive?.intOrNull ?: 200
                bridge.findBlurryPhotos(limit)
            }
            "find_similar_photos" -> {
                val limit = input["limit"]?.jsonPrimitive?.intOrNull ?: 200
                bridge.findSimilarPhotos(limit)
            }
            "cleanup_photos" -> {
                val criteria = input["criteria"]?.jsonPrimitive?.contentOrNull ?: "blurry"
                bridge.cleanupPhotos(criteria)
            }
            "clear_notifications_from_app" -> {
                val pkg = input["package_name"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing package_name", isError = true)
                bridge.clearNotificationsFromApp(pkg)
            }
            "clear_notifications_by_keyword" -> {
                val keyword = input["keyword"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing keyword", isError = true)
                bridge.clearNotificationsByKeyword(keyword)
            }
            "delete_old_sms" -> {
                val days = input["older_than_days"]?.jsonPrimitive?.intOrNull ?: 365
                bridge.deleteOldSms(days)
            }
            "run_routine" -> {
                val routine = input["routine"]?.jsonPrimitive?.contentOrNull ?: "storage"
                bridge.runCleanupRoutine(routine)
            }
            else -> return ToolResult("Unknown action: $action", isError = true)
        }

        return result.fold(
            onSuccess = { ToolResult(it) },
            onFailure = { ToolResult("Failed: ${it.message}", isError = true) }
        )
    }
}
