package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import kotlinx.serialization.json.*
import kotlin.random.Random

/**
 * Generates a cryptographically-uncertain but practically-secure random password.
 * Uses kotlin.random.Random (good enough for non-secret UI generation); for
 * production-grade secrets the caller should use a platform-specific secure RNG.
 */
class PasswordGeneratorTool : Tool {

    override val name = "generate_password"

    override val description =
        "Generate a random password. Configurable length and character classes (lowercase, uppercase, digits, symbols)."

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("length") {
                put("type", "integer")
                put("description", "Password length (default 16, min 4, max 128)")
            }
            putJsonObject("lowercase") { put("type", "boolean"); put("description", "Include a-z (default true)") }
            putJsonObject("uppercase") { put("type", "boolean"); put("description", "Include A-Z (default true)") }
            putJsonObject("digits") { put("type", "boolean"); put("description", "Include 0-9 (default true)") }
            putJsonObject("symbols") { put("type", "boolean"); put("description", "Include punctuation (default false)") }
            putJsonObject("count") {
                put("type", "integer")
                put("description", "How many passwords to generate (default 1, max 20)")
            }
        }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val length = (input["length"]?.jsonPrimitive?.intOrNull ?: 16).coerceIn(4, 128)
        val lower = input["lowercase"]?.jsonPrimitive?.booleanOrNull ?: true
        val upper = input["uppercase"]?.jsonPrimitive?.booleanOrNull ?: true
        val digits = input["digits"]?.jsonPrimitive?.booleanOrNull ?: true
        val symbols = input["symbols"]?.jsonPrimitive?.booleanOrNull ?: false
        val count = (input["count"]?.jsonPrimitive?.intOrNull ?: 1).coerceIn(1, 20)

        val pool = buildString {
            if (lower) append("abcdefghijklmnopqrstuvwxyz")
            if (upper) append("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
            if (digits) append("0123456789")
            if (symbols) append("!@#$%^&*()-_=+[]{};:,.<>?/")
        }
        if (pool.isEmpty()) {
            return ToolResult("At least one character class must be enabled", isError = true)
        }

        val passwords = List(count) {
            buildString(length) { repeat(length) { append(pool[Random.nextInt(pool.length)]) } }
        }
        return ToolResult(passwords.joinToString("\n"))
    }
}
