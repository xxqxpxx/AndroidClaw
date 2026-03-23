package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

class MessagingTool(
    private val bridge: DeviceActionBridge
) : Tool {

    override val name = "messaging"

    override val description = """Send messages through messaging apps or share content to any app.
        |Supported apps: WhatsApp, Telegram, Signal, Viber, Facebook Messenger, Instagram, Snapchat, Discord, Slack, Teams, Gmail, and any other installed app.
        |Use 'send_app_message' with the app name for any messaging app.
        |Use 'share_text' to share text to any app via Android share sheet.
        |Use 'open_url' to open a URL in the browser or a specific app.
        |For regular SMS, use the sms tool instead.""".trimMargin()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                putJsonArray("enum") {
                    add("send_whatsapp"); add("send_telegram"); add("send_signal")
                    add("send_viber"); add("send_messenger"); add("send_app_message")
                    add("share_text"); add("open_url")
                }
                put("description", "Action to perform. Use send_app_message with app_name for any app not listed.")
            }
            putJsonObject("phone_number") {
                put("type", "string")
                put("description", "Phone number with country code (e.g. +1234567890)")
            }
            putJsonObject("message") {
                put("type", "string")
                put("description", "Message text to send or share")
            }
            putJsonObject("app_name") {
                put("type", "string")
                put("description", "App name for send_app_message (e.g. 'instagram', 'snapchat', 'discord', 'slack', 'teams', 'gmail')")
            }
            putJsonObject("package_name") {
                put("type", "string")
                put("description", "Android package name (optional, auto-resolved from app_name if not specified)")
            }
            putJsonObject("url") {
                put("type", "string")
                put("description", "URL to open (for open_url action)")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val action = input["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing required parameter: action", isError = true)

        val result = when (action) {
            "send_whatsapp" -> {
                val phone = input["phone_number"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing phone_number", isError = true)
                val message = input["message"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing message", isError = true)
                bridge.sendIntentMessage("com.whatsapp", phone, message)
            }
            "send_telegram" -> {
                val phone = input["phone_number"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing phone_number", isError = true)
                val message = input["message"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing message", isError = true)
                bridge.sendIntentMessage("org.telegram.messenger", phone, message)
            }
            "send_signal" -> {
                val phone = input["phone_number"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing phone_number", isError = true)
                val message = input["message"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing message", isError = true)
                bridge.sendIntentMessage("org.thoughtcrime.securesms", phone, message)
            }
            "send_viber" -> {
                val phone = input["phone_number"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing phone_number", isError = true)
                val message = input["message"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing message", isError = true)
                bridge.sendIntentMessage("com.viber.voip", phone, message)
            }
            "send_messenger" -> {
                val message = input["message"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing message", isError = true)
                bridge.sendIntentMessage("com.facebook.orca", "", message)
            }
            "send_app_message" -> {
                val message = input["message"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing message", isError = true)
                val pkg = input["package_name"]?.jsonPrimitive?.contentOrNull
                    ?: resolvePackageName(input["app_name"]?.jsonPrimitive?.contentOrNull ?: "")
                    ?: return ToolResult("Missing package_name or app_name", isError = true)
                val phone = input["phone_number"]?.jsonPrimitive?.contentOrNull ?: ""
                bridge.sendIntentMessage(pkg, phone, message)
            }
            "share_text" -> {
                val message = input["message"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing message", isError = true)
                val pkg = input["package_name"]?.jsonPrimitive?.contentOrNull
                    ?: resolvePackageName(input["app_name"]?.jsonPrimitive?.contentOrNull ?: "")
                bridge.shareText(message, pkg)
            }
            "open_url" -> {
                val url = input["url"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing url", isError = true)
                val pkg = input["package_name"]?.jsonPrimitive?.contentOrNull
                bridge.openUrl(url, pkg)
            }
            else -> return ToolResult("Unknown action: $action", isError = true)
        }

        return result.fold(
            onSuccess = { ToolResult(it) },
            onFailure = { ToolResult("Failed: ${it.message}", isError = true) }
        )
    }

    private fun resolvePackageName(appName: String): String? {
        if (appName.isBlank()) return null
        val name = appName.lowercase().trim()
        return APP_PACKAGES[name]
            ?: APP_PACKAGES.entries.firstOrNull { name.contains(it.key) || it.key.contains(name) }?.value
    }

    companion object {
        val APP_PACKAGES = mapOf(
            // Messaging
            "whatsapp" to "com.whatsapp",
            "telegram" to "org.telegram.messenger",
            "signal" to "org.thoughtcrime.securesms",
            "viber" to "com.viber.voip",
            "messenger" to "com.facebook.orca",
            "facebook messenger" to "com.facebook.orca",
            "wechat" to "com.tencent.mm",
            "line" to "jp.naver.line.android",
            "kakaotalk" to "com.kakao.talk",
            "kik" to "kik.android",
            "threema" to "ch.threema.app",
            // Social
            "instagram" to "com.instagram.android",
            "snapchat" to "com.snapchat.android",
            "twitter" to "com.twitter.android",
            "x" to "com.twitter.android",
            "facebook" to "com.facebook.katana",
            "tiktok" to "com.zhiliaoapp.musically",
            "reddit" to "com.reddit.frontpage",
            "linkedin" to "com.linkedin.android",
            "pinterest" to "com.pinterest",
            "threads" to "com.instagram.barcelona",
            // Productivity
            "discord" to "com.discord",
            "slack" to "com.Slack",
            "teams" to "com.microsoft.teams",
            "microsoft teams" to "com.microsoft.teams",
            "zoom" to "us.zoom.videomeetings",
            "gmail" to "com.google.android.gm",
            "outlook" to "com.microsoft.office.outlook",
            "google chat" to "com.google.android.apps.dynamite",
            // Media & Entertainment
            "youtube" to "com.google.android.youtube",
            "spotify" to "com.spotify.music",
            "netflix" to "com.netflix.mediaclient",
            "twitch" to "tv.twitch.android.app",
            // Maps & Travel
            "google maps" to "com.google.android.apps.maps",
            "uber" to "com.ubercab",
            "lyft" to "me.lyft.android",
            "airbnb" to "com.airbnb.android",
            // Finance
            "paypal" to "com.paypal.android.p2pmobile",
            "venmo" to "com.venmo",
            "cashapp" to "com.squareup.cash",
            "cash app" to "com.squareup.cash",
            // Shopping
            "amazon" to "com.amazon.mShop.android.shopping",
            "ebay" to "com.ebay.mobile",
            // Google
            "chrome" to "com.android.chrome",
            "google" to "com.google.android.googlequicksearchbox",
            "google drive" to "com.google.android.apps.docs",
            "google photos" to "com.google.android.apps.photos",
            "google docs" to "com.google.android.apps.docs.editors.docs",
            "google sheets" to "com.google.android.apps.docs.editors.sheets",
            "google keep" to "com.google.android.keep",
            "google calendar" to "com.google.android.calendar",
            // Notes
            "notion" to "notion.id",
            "evernote" to "com.evernote",
            "todoist" to "com.todoist",
            "trello" to "com.trello",
        )
    }
}
