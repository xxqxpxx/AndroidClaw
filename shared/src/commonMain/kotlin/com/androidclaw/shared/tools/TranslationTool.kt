package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

/**
 * Translation tool backed by the free MyMemory translation API
 * (no key required for low-volume use).
 * https://mymemory.translated.net/doc/spec.php
 */
class TranslationTool(
    private val httpClient: HttpClient
) : Tool {

    override val name = "translate"

    override val description =
        "Translate text between languages using the free MyMemory API. Pass two-letter ISO codes (e.g. 'en', 'es', 'fr', 'ar', 'ja'). If 'source_lang' is omitted, auto-detection is attempted."

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("text") {
                put("type", "string")
                put("description", "Text to translate")
            }
            putJsonObject("source_lang") {
                put("type", "string")
                put("description", "Source language ISO-639-1 code (e.g. 'en'). Use 'auto' to detect.")
            }
            putJsonObject("target_lang") {
                put("type", "string")
                put("description", "Target language ISO-639-1 code (e.g. 'es')")
            }
        }
        putJsonArray("required") { add("text"); add("target_lang") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val text = input["text"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing 'text'", isError = true)
        val target = input["target_lang"]?.jsonPrimitive?.contentOrNull?.lowercase()
            ?: return ToolResult("Missing 'target_lang'", isError = true)
        val source = input["source_lang"]?.jsonPrimitive?.contentOrNull?.lowercase()
            ?.takeIf { it.isNotBlank() && it != "auto" }
            ?: "autodetect"

        if (text.isBlank()) return ToolResult("'text' is empty", isError = true)

        return try {
            val encodedText = percentEncode(text)
            val langPair = percentEncode("$source|$target")
            val url = "https://api.mymemory.translated.net/get?q=$encodedText&langpair=$langPair"
            val resp = httpClient.get(url) { header(HttpHeaders.Accept, "application/json") }
            if (!resp.status.isSuccess()) {
                return ToolResult("Translation API error: ${resp.status.value}", isError = true)
            }
            val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
            val data = body["responseData"]?.jsonObject
                ?: return ToolResult("Malformed translation response", isError = true)
            val translated = data["translatedText"]?.jsonPrimitive?.contentOrNull
                ?: return ToolResult("No translation returned", isError = true)
            val match = data["match"]?.jsonPrimitive?.doubleOrNull
            val detected = body["responseData"]?.jsonObject?.get("detectedLanguage")
                ?.jsonPrimitive?.contentOrNull

            val sb = StringBuilder()
            sb.append("Translation (")
            sb.append(if (source == "autodetect") "auto" else source)
            sb.append(" → ").append(target).append("):\n")
            sb.append(translated)
            if (match != null) sb.append("\n(confidence: ").append((match * 100).toInt()).append("%)")
            if (detected != null) sb.append("\n(detected source: ").append(detected).append(")")
            ToolResult(sb.toString())
        } catch (e: Exception) {
            ToolResult("Translation failed: ${e.message}", isError = true)
        }
    }

    private fun percentEncode(s: String): String {
        val bytes = s.encodeToByteArray()
        val sb = StringBuilder(bytes.size)
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            val isUnreserved = (c in 0x30..0x39) || (c in 0x41..0x5A) || (c in 0x61..0x7A) ||
                c == 0x2D || c == 0x2E || c == 0x5F || c == 0x7E
            if (isUnreserved) sb.append(c.toChar())
            else {
                sb.append('%')
                sb.append("0123456789ABCDEF"[(c shr 4) and 0xF])
                sb.append("0123456789ABCDEF"[c and 0xF])
            }
        }
        return sb.toString()
    }
}
