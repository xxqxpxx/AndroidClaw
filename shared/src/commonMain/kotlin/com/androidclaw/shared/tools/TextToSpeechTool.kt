package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

class TextToSpeechTool(
    private val bridge: DeviceActionBridge
) : Tool {

    override val name = "text_to_speech"

    override val description = """Speak text aloud using text-to-speech. Configure voice language, speed, and pitch.
        |Can also stop ongoing speech and list available voices/languages.""".trimMargin()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("description", "TTS action to perform")
                putJsonArray("enum") {
                    add("speak")
                    add("stop")
                    add("list_voices")
                }
            }
            putJsonObject("text") {
                put("type", "string")
                put("description", "Text to speak aloud")
            }
            putJsonObject("language") {
                put("type", "string")
                put("description", "Language code (e.g. 'en', 'es', 'fr', 'ar', 'ja', 'de', 'zh')")
            }
            putJsonObject("speed") {
                put("type", "number")
                put("description", "Speech speed (0.5 = slow, 1.0 = normal, 2.0 = fast)")
            }
            putJsonObject("pitch") {
                put("type", "number")
                put("description", "Voice pitch (0.5 = low, 1.0 = normal, 2.0 = high)")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val action = input["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing required parameter: action", isError = true)

        val result = when (action) {
            "speak" -> {
                val text = input["text"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing text to speak", isError = true)
                val language = input["language"]?.jsonPrimitive?.contentOrNull ?: ""
                val speed = input["speed"]?.jsonPrimitive?.floatOrNull ?: 1.0f
                val pitch = input["pitch"]?.jsonPrimitive?.floatOrNull ?: 1.0f
                bridge.ttsSpeak(text, language, speed, pitch)
            }
            "stop" -> bridge.ttsStop()
            "list_voices" -> bridge.ttsGetVoices()
            else -> return ToolResult("Unknown TTS action: $action", isError = true)
        }

        return result.fold(
            onSuccess = { ToolResult(it) },
            onFailure = { ToolResult("Failed: ${it.message}", isError = true) }
        )
    }
}
