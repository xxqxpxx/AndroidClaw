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
        |Streaming: play_on_spotify, play_on_youtube_music, play_on_youtube, play_on_app
        |
        |Examples:
        |  - Play/pause current media: action=play_pause
        |  - Play a song on Spotify: action=play_on_spotify, query="Shape of You Ed Sheeran"
        |  - Play on YouTube Music: action=play_on_youtube_music, query="lofi beats"
        |  - Play on YouTube: action=play_on_youtube, query="coding tutorial"
        |  - Play on any music app: action=play_on_app, query="jazz", package_name="com.apple.android.music"
    """.trimMargin()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                putJsonArray("enum") {
                    add("play_pause"); add("next_track"); add("previous_track"); add("stop")
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

            "play_on_spotify" -> {
                val query = input["query"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing query for Spotify search", isError = true)
                val encoded = query.replace(" ", "%20")
                bridge.openDeepLink(
                    uri = "spotify:search:$encoded",
                    packageName = "com.spotify.music",
                    fallbackUrl = "https://open.spotify.com/search/$encoded"
                )
            }

            "play_on_youtube_music" -> {
                val query = input["query"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing query for YouTube Music search", isError = true)
                val encoded = query.replace(" ", "+")
                bridge.openDeepLink(
                    uri = "https://music.youtube.com/search?q=$encoded",
                    packageName = "com.google.android.apps.youtube.music",
                    fallbackUrl = "https://music.youtube.com/search?q=$encoded"
                )
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
