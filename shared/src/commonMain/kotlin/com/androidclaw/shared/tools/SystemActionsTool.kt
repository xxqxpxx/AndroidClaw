package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

class SystemActionsTool(
    private val bridge: DeviceActionBridge
) : Tool {

    override val name = "system_actions"

    override val description = """Perform system-level actions: navigation (home/back/recents), screenshots, notifications,
        |settings pages, DND, auto-rotate, directions, email, media control, camera, QR scan,
        |split screen, power menu, orientation lock, notes, app management (uninstall/force-stop/info).""".trimMargin()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("description", "The system action to perform")
                putJsonArray("enum") {
                    // Navigation
                    add("go_home"); add("go_back"); add("show_recents")
                    // Screen
                    add("take_screenshot"); add("split_screen")
                    add("lock_portrait"); add("lock_landscape")
                    // Notifications & Quick Settings
                    add("expand_notifications"); add("quick_settings")
                    // Settings
                    add("open_settings"); add("power_menu")
                    // Toggles
                    add("dnd_on"); add("dnd_off")
                    add("auto_rotate_on"); add("auto_rotate_off")
                    // Navigation & Communication
                    add("navigate_to"); add("send_email")
                    // Media
                    add("media_play_pause"); add("media_next"); add("media_previous"); add("media_stop")
                    // Camera
                    add("open_camera"); add("take_photo"); add("scan_qr")
                    // Notes
                    add("create_note")
                    // App management
                    add("uninstall_app"); add("force_stop_app"); add("app_info")
                    // Info
                    add("battery_info"); add("storage_info"); add("network_info"); add("bluetooth_devices")
                    // Sound / TTS
                    add("find_my_phone"); add("read_aloud")
                    // More
                    add("stopwatch"); add("translate"); add("identify_song")
                    add("share"); add("open_files")
                    add("answer_call"); add("reject_call")
                    add("set_wallpaper"); add("set_font_size")
                    add("screen_record"); add("restart")
                    add("hotspot_settings"); add("airplane_settings")
                    add("clear_notifications")
                    // Fun
                    add("coin_flip"); add("roll_dice"); add("random_number"); add("countdown")
                    // Recording
                    add("voice_record"); add("speed_test"); add("cast_screen"); add("incognito")
                    // Emergency
                    add("emergency_call")
                    // Info
                    add("data_usage"); add("sim_info"); add("uptime"); add("memory_info"); add("check_update")
                    // Display
                    add("night_light_on"); add("night_light_off"); add("bedtime_on"); add("bedtime_off")
                    add("pin_app"); add("flashlight_sos")
                    add("color_inversion_on"); add("color_inversion_off")
                    add("magnification_on"); add("magnification_off")
                    // Settings
                    add("clear_app_data"); add("default_apps"); add("digital_wellbeing")
                    add("ringtone_settings"); add("create_reminder")
                }
            }
            putJsonObject("settings_page") {
                put("type", "string")
                put("description", "Settings page: wifi, bluetooth, display, sound, battery, storage, apps, location, security, notifications, airplane, hotspot, vpn, nfc, about, developer, date, language, accessibility")
            }
            putJsonObject("destination") {
                put("type", "string")
                put("description", "Address or place for navigate_to")
            }
            putJsonObject("to") {
                put("type", "string")
                put("description", "Email address for send_email")
            }
            putJsonObject("subject") {
                put("type", "string")
                put("description", "Email subject")
            }
            putJsonObject("body") {
                put("type", "string")
                put("description", "Email body or note content")
            }
            putJsonObject("title") {
                put("type", "string")
                put("description", "Note title for create_note")
            }
            putJsonObject("package_name") {
                put("type", "string")
                put("description", "App package for uninstall_app/force_stop_app/app_info")
            }
            putJsonObject("text") {
                put("type", "string")
                put("description", "Text for read_aloud, translate, or share")
            }
            putJsonObject("target_language") {
                put("type", "string")
                put("description", "Target language code for translate (e.g. 'es', 'fr', 'ar', 'ja')")
            }
            putJsonObject("font_scale") {
                put("type", "string")
                put("description", "Font size: small, default, large, largest")
            }
            putJsonObject("url") {
                put("type", "string")
                put("description", "URL for set_wallpaper")
            }
            putJsonObject("sides") {
                put("type", "integer")
                put("description", "Number of sides for roll_dice (default 6)")
            }
            putJsonObject("min") {
                put("type", "integer")
                put("description", "Min for random_number")
            }
            putJsonObject("max") {
                put("type", "integer")
                put("description", "Max for random_number")
            }
            putJsonObject("date") {
                put("type", "string")
                put("description", "Target date for countdown (YYYY-MM-DD)")
            }
            putJsonObject("reminder_text") {
                put("type", "string")
                put("description", "Reminder text for create_reminder")
            }
            putJsonObject("reminder_time") {
                put("type", "integer")
                put("description", "Reminder time in epoch millis for create_reminder")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val action = input["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing required parameter: action", isError = true)

        val result = when (action) {
            // Navigation
            "go_home" -> bridge.goHome()
            "go_back" -> bridge.goBack()
            "show_recents" -> bridge.showRecents()

            // Screen
            "take_screenshot" -> bridge.takeScreenshot()
            "split_screen" -> bridge.splitScreen()
            "lock_portrait" -> bridge.lockOrientation(portrait = true)
            "lock_landscape" -> bridge.lockOrientation(portrait = false)

            // Notifications
            "expand_notifications" -> bridge.expandNotifications()
            "quick_settings" -> bridge.openQuickSettings()

            // Settings & Power
            "open_settings" -> {
                val page = input["settings_page"]?.jsonPrimitive?.contentOrNull ?: ""
                bridge.openSettings(page)
            }
            "power_menu" -> bridge.openPowerMenu()

            // Toggles
            "dnd_on" -> bridge.setDoNotDisturb(true)
            "dnd_off" -> bridge.setDoNotDisturb(false)
            "auto_rotate_on" -> bridge.setAutoRotate(true)
            "auto_rotate_off" -> bridge.setAutoRotate(false)

            // Navigation & Communication
            "navigate_to" -> {
                val dest = input["destination"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing destination", isError = true)
                bridge.navigateTo(dest)
            }
            "send_email" -> {
                val to = input["to"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing email address", isError = true)
                val subject = input["subject"]?.jsonPrimitive?.contentOrNull ?: ""
                val body = input["body"]?.jsonPrimitive?.contentOrNull ?: ""
                bridge.sendEmail(to, subject, body)
            }

            // Media
            "media_play_pause" -> bridge.mediaPlayPause()
            "media_next" -> bridge.mediaNext()
            "media_previous" -> bridge.mediaPrevious()
            "media_stop" -> bridge.mediaStop()

            // Camera
            "open_camera" -> bridge.openCamera()
            "take_photo" -> bridge.takePhoto()
            "scan_qr" -> bridge.scanQrCode()

            // Notes
            "create_note" -> {
                val title = input["title"]?.jsonPrimitive?.contentOrNull ?: "Note"
                val body = input["body"]?.jsonPrimitive?.contentOrNull ?: ""
                bridge.createNote(title, body)
            }

            // App management
            "uninstall_app" -> {
                val pkg = input["package_name"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing package_name", isError = true)
                bridge.uninstallApp(pkg)
            }
            "force_stop_app" -> {
                val pkg = input["package_name"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing package_name", isError = true)
                bridge.forceStopApp(pkg)
            }
            "app_info" -> {
                val pkg = input["package_name"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing package_name", isError = true)
                bridge.getAppInfo(pkg)
            }

            // Info
            "battery_info" -> bridge.getBatteryInfo()
            "storage_info" -> bridge.getStorageInfo()
            "network_info" -> bridge.getNetworkInfo()
            "bluetooth_devices" -> bridge.getBluetoothDevices()

            // Sound / TTS
            "find_my_phone" -> bridge.findMyPhone()
            "read_aloud" -> {
                val text = input["text"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing text to read", isError = true)
                bridge.readAloud(text)
            }

            // More actions
            "stopwatch" -> bridge.startStopwatch()
            "translate" -> {
                val text = input["text"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing text to translate", isError = true)
                val lang = input["target_language"]?.jsonPrimitive?.contentOrNull ?: ""
                bridge.translateText(text, lang)
            }
            "identify_song" -> bridge.identifySong()
            "share" -> {
                val text = input["text"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing text to share", isError = true)
                bridge.quickShare(text)
            }
            "open_files" -> bridge.openFileManager()
            "answer_call" -> bridge.answerCall()
            "reject_call" -> bridge.rejectCall()
            "set_wallpaper" -> {
                val url = input["url"]?.jsonPrimitive?.contentOrNull ?: ""
                bridge.setWallpaper(url)
            }
            "set_font_size" -> {
                val scale = input["font_scale"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing font_scale (small/default/large/largest)", isError = true)
                bridge.setFontSize(scale)
            }
            "screen_record" -> bridge.screenRecord()
            "restart" -> bridge.restartDevice()
            "hotspot_settings" -> bridge.openHotspotSettings()
            "airplane_settings" -> bridge.openAirplaneSettings()
            "clear_notifications" -> bridge.clearAllNotifications()

            // Fun
            "coin_flip" -> bridge.coinFlip()
            "roll_dice" -> {
                val sides = input["sides"]?.jsonPrimitive?.intOrNull ?: 6
                bridge.rollDice(sides)
            }
            "random_number" -> {
                val min = input["min"]?.jsonPrimitive?.intOrNull ?: 1
                val max = input["max"]?.jsonPrimitive?.intOrNull ?: 100
                bridge.randomNumber(min, max)
            }
            "countdown" -> {
                val date = input["date"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing date for countdown", isError = true)
                bridge.countdownTo(date)
            }

            // Recording & browsing
            "voice_record" -> bridge.startVoiceRecording()
            "speed_test" -> bridge.openSpeedTest()
            "cast_screen" -> bridge.castScreen()
            "incognito" -> bridge.openIncognito()

            // Emergency
            "emergency_call" -> bridge.emergencyCall()

            // Info
            "data_usage" -> bridge.getDataUsage()
            "sim_info" -> bridge.getSimInfo()
            "uptime" -> bridge.getDeviceUptime()
            "memory_info" -> bridge.getMemoryInfo()
            "check_update" -> bridge.checkForUpdate()

            // Display & accessibility
            "night_light_on" -> bridge.setNightLight(true)
            "night_light_off" -> bridge.setNightLight(false)
            "bedtime_on" -> bridge.setBedtimeMode(true)
            "bedtime_off" -> bridge.setBedtimeMode(false)
            "pin_app" -> bridge.pinApp()
            "flashlight_sos" -> bridge.flashlightSos()
            "color_inversion_on" -> bridge.setColorInversion(true)
            "color_inversion_off" -> bridge.setColorInversion(false)
            "magnification_on" -> bridge.setMagnification(true)
            "magnification_off" -> bridge.setMagnification(false)

            // App & settings
            "clear_app_data" -> {
                val pkg = input["package_name"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing package_name", isError = true)
                bridge.clearAppData(pkg)
            }
            "default_apps" -> bridge.openDefaultApps()
            "digital_wellbeing" -> bridge.openDigitalWellbeing()
            "ringtone_settings" -> bridge.openRingtoneSettings()
            "create_reminder" -> {
                val text = input["reminder_text"]?.jsonPrimitive?.contentOrNull
                    ?: input["text"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing reminder text", isError = true)
                val time = input["reminder_time"]?.jsonPrimitive?.longOrNull
                    ?: (System.currentTimeMillis() + 3600000) // default 1 hour from now
                bridge.createReminder(text, time)
            }

            else -> return ToolResult("Unknown action: $action", isError = true)
        }

        return result.fold(
            onSuccess = { ToolResult(it) },
            onFailure = { ToolResult("Failed: ${it.message}", isError = true) }
        )
    }
}
