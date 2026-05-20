package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

class AudioProfileTool(
    private val bridge: DeviceActionBridge
) : Tool {

    override val name = "audio_profile"

    override val description = """Save, load, list, and delete audio profiles. An audio profile captures all volume levels
        |(media, ring, alarm, notification) and ringer mode. Save your current settings as 'work', 'sleep',
        |'driving', etc. and quickly switch between them.""".trimMargin()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("description", "Audio profile action")
                putJsonArray("enum") {
                    add("save")
                    add("load")
                    add("list")
                    add("delete")
                }
            }
            putJsonObject("name") {
                put("type", "string")
                put("description", "Profile name (e.g. 'work', 'sleep', 'driving', 'meeting')")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val action = input["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing required parameter: action", isError = true)

        val result = when (action) {
            "save" -> {
                val name = input["name"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing profile name", isError = true)
                bridge.saveAudioProfile(name)
            }
            "load" -> {
                val name = input["name"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing profile name", isError = true)
                bridge.loadAudioProfile(name)
            }
            "list" -> bridge.listAudioProfiles()
            "delete" -> {
                val name = input["name"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing profile name", isError = true)
                bridge.deleteAudioProfile(name)
            }
            else -> return ToolResult("Unknown audio profile action: $action", isError = true)
        }

        return result.fold(
            onSuccess = { ToolResult(it) },
            onFailure = { ToolResult("Failed: ${it.message}", isError = true) }
        )
    }
}
