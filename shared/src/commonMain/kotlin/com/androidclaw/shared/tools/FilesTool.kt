package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

class FilesTool(
    private val bridge: DeviceActionBridge
) : Tool {

    override val name = "files"

    override val description = """Manage files on the device. List, sort, get info, delete, move, or auto-organize files.
        |Works with Downloads, Documents, Pictures, Music, Movies, DCIM, or any path.
        |Use organize to automatically sort files into categories (Images, Videos, Documents, etc.).""".trimMargin()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                putJsonArray("enum") {
                    add("list"); add("info"); add("delete"); add("move"); add("organize")
                }
                put("description", "File action: list (browse), info (details), delete, move, organize (auto-sort by type)")
            }
            putJsonObject("directory") {
                put("type", "string")
                put("description", "Directory to operate on: Downloads, Documents, Pictures, Music, Movies, DCIM, Camera, or full path. Default: Downloads")
            }
            putJsonObject("sort_by") {
                put("type", "string")
                putJsonArray("enum") { add("date"); add("name"); add("size"); add("type") }
                put("description", "Sort order for list action (default: date)")
            }
            putJsonObject("file_path") {
                put("type", "string")
                put("description", "Full file path for info/delete/move actions")
            }
            putJsonObject("destination") {
                put("type", "string")
                put("description", "Destination directory for move action")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val action = input["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing required parameter: action", isError = true)

        val result = when (action) {
            "list" -> {
                val dir = input["directory"]?.jsonPrimitive?.contentOrNull ?: "Downloads"
                val sortBy = input["sort_by"]?.jsonPrimitive?.contentOrNull ?: "date"
                bridge.listFiles(dir, sortBy)
            }
            "info" -> {
                val path = input["file_path"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing file_path for info action", isError = true)
                bridge.getFileInfo(path)
            }
            "delete" -> {
                val path = input["file_path"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing file_path for delete action", isError = true)
                bridge.deleteFile(path)
            }
            "move" -> {
                val source = input["file_path"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing file_path for move action", isError = true)
                val dest = input["destination"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing destination for move action", isError = true)
                bridge.moveFile(source, dest)
            }
            "organize" -> {
                val dir = input["directory"]?.jsonPrimitive?.contentOrNull ?: "Downloads"
                bridge.organizeFiles(dir)
            }
            else -> return ToolResult("Unknown action: $action", isError = true)
        }

        return result.fold(
            onSuccess = { ToolResult(it) },
            onFailure = { ToolResult("Failed: ${it.message}", isError = true) }
        )
    }
}
