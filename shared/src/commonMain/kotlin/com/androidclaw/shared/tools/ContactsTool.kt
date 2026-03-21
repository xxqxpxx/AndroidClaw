package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

class ContactsTool(
    private val bridge: DeviceActionBridge
) : Tool {

    override val name = "contacts"

    override val description = """Read and manage phone contacts.
        |Use this to find someone's phone number, add a new contact, or search contacts by name.""".trimMargin()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                putJsonArray("enum") { add("search"); add("add"); add("list") }
                put("description", "Action: search contacts by name, add a new contact, or list all contacts")
            }
            putJsonObject("name") {
                put("type", "string")
                put("description", "Contact name to search for, or name for a new contact")
            }
            putJsonObject("phone") {
                put("type", "string")
                put("description", "Phone number for a new contact")
            }
            putJsonObject("email") {
                put("type", "string")
                put("description", "Email for a new contact (optional)")
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "Max contacts to return (default 20)")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val action = input["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing required parameter: action", isError = true)

        val result = when (action) {
            "search" -> {
                val name = input["name"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing name for search", isError = true)
                bridge.findContactByName(name)
            }
            "add" -> {
                val name = input["name"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing name for new contact", isError = true)
                val phone = input["phone"]?.jsonPrimitive?.contentOrNull
                    ?: return ToolResult("Missing phone for new contact", isError = true)
                val email = input["email"]?.jsonPrimitive?.contentOrNull ?: ""
                bridge.addContact(name, phone, email)
            }
            "list" -> {
                val limit = input["limit"]?.jsonPrimitive?.intOrNull ?: 20
                bridge.getContacts(limit = limit)
            }
            else -> return ToolResult("Unknown action: $action", isError = true)
        }

        return result.fold(
            onSuccess = { ToolResult(it) },
            onFailure = { ToolResult("Failed: ${it.message}", isError = true) }
        )
    }
}
