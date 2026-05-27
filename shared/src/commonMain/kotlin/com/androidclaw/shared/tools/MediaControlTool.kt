package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

class MediaControlTool(
    private val bridge: DeviceActionBridge
) : Tool {

    override val name = "media_control"

    override val description = """Control media playback and play music/videos on streaming apps.
        |
        |Playback controls: play_pause, next_track, previous_track, stop
        |Streaming: play_music (starts playback directly), play_on_spotify, play_on_youtube_music, play_on_youtube, play_on_app
        |
        |Examples:
        |  - Play/pause current media: action=play_pause
        |  - Play a song (any/best music app): action=play_music, query="Shape of You", app="spotify"
        |  - Play a song on Spotify: action=play_on_spotify, query="Shape of You Ed Sheeran"
        |  - Play on YouTube Music: action=play_on_youtube_music, query="lofi beats"
        |  - Play on YouTube: action=play_on_youtube, query="coding tutorial"
        |  - Play on any music app: action=play_on_app, query="jazz", package_name="com.apple.android.music"
        |
        |Note: play_music / play_on_spotify / play_on_youtube_music start playback of the best match
        |directly (via the system play-from-search intent), rather than only opening a search.
    """.trimMargin()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                putJsonArray("enum") {
                    add("play_pause"); add("next_track"); add("previous_track"); add("stop")
                    add("play_music")
                    add("play_on_spotify"); add("play_on_youtube_music")
                    add("play_on_youtube"); add("play_on_app")
                }
                put("description", "Media control action")
            }
            putJsonObject("query") {
                put("type", "string")
                put("description", "Search query for streaming actions (song name, artist, playlist, etc.)")
            }
            putJsonObject("package_name") {
                put("type", "string")
                put("description", "App package name for play_on_app action")
            }
            putJsonObject("app") {
                put("type", "string")
                put("description", "Preferred music app for play_music (e.g. spotify, youtube_music, deezer, tidal); omit for any")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val action = input["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing required parameter: action", isError = true)

        val result = when (action) {
            "play_pause" -> bridge.mediaPlayPause()
            "next_track" -> bridge.mediaNext()
            "previous_track" -> bridge.mediaPrevious()
            "stop" -> bridge.mediaStop()

            "play_music" -> {
                val query = input["query"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing query for play_music", isError = true)
                val app = input["app"]?.jsonPrimitive?.contentOrNull ?: ""
                bridge.playMusic(query, app)
            }

            "play_on_spotify" -> {
                val query = input["query"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing query for Spotify search", isError = true)
                bridge.playMusic(query, "spotify")
            }

            "play_on_youtube_music" -> {
                val query = input["query"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing query for YouTube Music search", isError = true)
                bridge.playMusic(query, "youtube_music")
            }

            "play_on_youtube" -> {
                val query = input["query"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing query for YouTube search", isError = true)
                val encoded = query.replace(" ", "+")
                bridge.openDeepLink(
                    uri = "vnd.youtube://results?search_query=$encoded",
                    packageName = "com.google.android.youtube",
                    fallbackUrl = "https://www.youtube.com/results?search_query=$encoded"
                )
            }

            "play_on_app" -> {
                val query = input["query"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing query for app search", isError = true)
                val pkg = input["package_name"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing package_name for play_on_app", isError = true)
                bridge.openDeepLink(
                    uri = "android.intent.action.SEARCH",
                    packageName = pkg,
                    fallbackUrl = null
                )
                // Fallback: just launch the app with a search intent
                bridge.launchApp(pkg)
            }

            else -> return ToolResult("Unknown action: $action", isError = true)
        }

        return result.fold(
            onSuccess = { ToolResult(it) },
            onFailure = { ToolResult("Failed: ${it.message}", isError = true) }
        )
    }
}
