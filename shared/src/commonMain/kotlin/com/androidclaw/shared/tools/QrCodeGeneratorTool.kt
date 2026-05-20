package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import io.ktor.http.*
import kotlinx.serialization.json.*

/**
 * Returns a URL to a PNG QR code rendering of the given text, using the
 * api.qrserver.com free service. No API key required. The Claude model can
 * surface this URL to the user (the UI renders image URLs).
 */
class QrCodeGeneratorTool : Tool {

    override val name = "generate_qr_code"

    override val description =
        "Generate a QR code image URL for arbitrary text (URLs, Wi-Fi creds, contacts). Returns a PNG URL."

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("text") {
                put("type", "string")
                put("description", "Text or URL to encode in the QR code")
            }
            putJsonObject("size") {
                put("type", "integer")
                put("description", "Square image side length in pixels (default 300, max 1000)")
            }
        }
        putJsonArray("required") { add("text") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val text = input["text"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing 'text'", isError = true)
        val size = (input["size"]?.jsonPrimitive?.intOrNull ?: 300).coerceIn(64, 1000)

        val url = "https://api.qrserver.com/v1/create-qr-code/" +
            "?size=${size}x$size&data=${text.encodeURLParameter()}"
        return ToolResult("QR code image: $url")
    }
}
