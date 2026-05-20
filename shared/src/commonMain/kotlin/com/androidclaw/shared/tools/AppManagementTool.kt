package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

class AppManagementTool(
    private val bridge: DeviceActionBridge
) : Tool {

    override val name = "app_management"

    override val description = """Manage installed apps: view usage stats, check permissions, storage, battery usage,
        |clear cache, view/set default apps, see running apps, and kill background apps.
        |
        |Actions:
        |  - usage_stats: App usage statistics for the last N days
        |  - permissions: List permissions granted to an app
        |  - storage_info: Storage/cache/data size for an app
        |  - clear_cache: Clear an app's cache
        |  - notification_settings: Check app notification channel settings
        |  - battery_usage: View battery consumption by apps
        |  - default_apps: Show default app handlers (browser, launcher, SMS, etc.)
        |  - set_default: Open prompt to set a default app for a role
        |  - recently_installed: List recently installed apps
        |  - running_apps: List currently running apps
        |  - kill_app: Force-stop a background app""".trimMargin()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                putJsonArray("enum") {
                    add("usage_stats"); add("permissions"); add("storage_info")
                    add("clear_cache"); add("notification_settings"); add("battery_usage")
                    add("default_apps"); add("set_default"); add("recently_installed")
                    add("running_apps"); add("kill_app")
                }
                put("description", "Management action to perform")
            }
            putJsonObject("package_name") {
                put("type", "string")
                put("description", "Target app package name (e.g. com.whatsapp)")
            }
            putJsonObject("days") {
                put("type", "integer")
                put("description", "Number of days for usage_stats or recently_installed (default 7 / 30)")
            }
            putJsonObject("role") {
                put("type", "string")
                putJsonArray("enum") {
                    add("browser"); add("sms"); add("phone"); add("launcher")
                    add("assistant"); add("home")
                }
                put("description", "Default app role for set_default action")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val action = input["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing required parameter: action", isError = true)

        val pkg = input["package_name"]?.jsonPrimitive?.contentOrNull

        val result = when (action) {
            "usage_stats" -> {
                val days = input["days"]?.jsonPrimitive?.intOrNull ?: 7
                bridge.getAppUsageStats(days)
            }
            "permissions" -> {
                pkg ?: return ToolResult("Missing package_name for permissions", isError = true)
                bridge.getAppPermissions(pkg)
            }
            "storage_info" -> {
                pkg ?: return ToolResult("Missing package_name for storage_info", isError = true)
                bridge.getAppStorageInfo(pkg)
            }
            "clear_cache" -> {
                pkg ?: return ToolResult("Missing package_name for clear_cache", isError = true)
                bridge.clearAppCache(pkg)
            }
            "notification_settings" -> {
                pkg ?: return ToolResult("Missing package_name for notification_settings", isError = true)
                bridge.getAppNotificationSettings(pkg)
            }
            "battery_usage" -> bridge.getAppBatteryUsage()
            "default_apps" -> bridge.getDefaultApps()
            "set_default" -> {
                val role = input["role"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing role for set_default", isError = true)
                pkg ?: return ToolResult("Missing package_name for set_default", isError = true)
                bridge.setDefaultApp(role, pkg)
            }
            "recently_installed" -> {
                val days = input["days"]?.jsonPrimitive?.intOrNull ?: 30
                bridge.getRecentlyInstalledApps(days)
            }
            "running_apps" -> bridge.getRunningApps()
            "kill_app" -> {
                pkg ?: return ToolResult("Missing package_name for kill_app", isError = true)
                bridge.killBackgroundApp(pkg)
            }
            else -> return ToolResult("Unknown action: $action", isError = true)
        }

        return result.fold(
            onSuccess = { ToolResult(it) },
            onFailure = { ToolResult("Failed: ${it.message}", isError = true) }
        )
    }
}
