package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*

/**
 * Compute MD5, SHA-1, or SHA-256 hashes of arbitrary text.
 * Pure-Kotlin implementation so it works identically across Android and iOS.
 */
class HashTool : Tool {

    override val name = "hash"

    override val description =
        "Compute a cryptographic hash (MD5, SHA-1, SHA-256) of a UTF-8 text string. Returns a lowercase hex digest. Use SHA-256 for security-sensitive cases."

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("text") {
                put("type", "string")
                put("description", "Text to hash (encoded as UTF-8 bytes)")
            }
            putJsonObject("algorithm") {
                put("type", "string")
                put("description", "Hash algorithm")
                putJsonArray("enum") {
                    add("MD5"); add("SHA-1"); add("SHA-256")
                }
            }
        }
        putJsonArray("required") { add("text"); add("algorithm") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val text = input["text"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing 'text'", isError = true)
        val algo = input["algorithm"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing 'algorithm'", isError = true)

        val hex = PureHash.digestHex(algo, text.encodeToByteArray())
            ?: return ToolResult(
                "Unsupported algorithm: '$algo'. Supported: MD5, SHA-1, SHA-256.",
                isError = true,
            )

        val normalized = when (algo.uppercase()) {
            "SHA1" -> "SHA-1"
            "SHA256" -> "SHA-256"
            else -> algo.uppercase()
        }
        return ToolResult("$normalized: $hex")
    }
}
