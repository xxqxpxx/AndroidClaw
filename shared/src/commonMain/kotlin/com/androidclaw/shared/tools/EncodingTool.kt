package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import io.ktor.http.*
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Encode / decode strings using common schemes: base64, base64url, url, hex.
 * Pure Kotlin, no platform or network calls.
 */
class EncodingTool : Tool {

    override val name = "encode_decode"

    override val description =
        "Encode or decode text. Schemes: base64, base64url, url, hex. Operation: 'encode' or 'decode'."

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("text") {
                put("type", "string")
                put("description", "Input text")
            }
            putJsonObject("scheme") {
                put("type", "string")
                put("description", "Encoding scheme")
                putJsonArray("enum") { add("base64"); add("base64url"); add("url"); add("hex") }
            }
            putJsonObject("operation") {
                put("type", "string")
                put("description", "encode or decode")
                putJsonArray("enum") { add("encode"); add("decode") }
            }
        }
        putJsonArray("required") { add("text"); add("scheme"); add("operation") }
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun execute(input: JsonObject): ToolResult {
        val text = input["text"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing 'text'", isError = true)
        val scheme = input["scheme"]?.jsonPrimitive?.contentOrNull?.lowercase()
            ?: return ToolResult("Missing 'scheme'", isError = true)
        val op = input["operation"]?.jsonPrimitive?.contentOrNull?.lowercase()
            ?: return ToolResult("Missing 'operation'", isError = true)

        return try {
            val out = when (scheme) {
                "base64" -> if (op == "encode") Base64.encode(text.encodeToByteArray())
                            else Base64.decode(text).decodeToString()
                "base64url" -> if (op == "encode") Base64.UrlSafe.encode(text.encodeToByteArray())
                               else Base64.UrlSafe.decode(text).decodeToString()
                "url" -> if (op == "encode") text.encodeURLParameter()
                         else text.decodeURLPart()
                "hex" -> if (op == "encode") text.encodeToByteArray().joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
                         else {
                             require(text.length % 2 == 0) { "Hex string must have even length" }
                             ByteArray(text.length / 2) { i ->
                                 text.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                             }.decodeToString()
                         }
                else -> return ToolResult("Unknown scheme: $scheme", isError = true)
            }
            ToolResult(out)
        } catch (e: Exception) {
            ToolResult("$op/$scheme failed: ${e.message}", isError = true)
        }
    }
}
