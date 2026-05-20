package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

/**
 * IP geolocation lookup via the free ipapi.co service.
 * If 'ip' is omitted, the caller's public IP is used.
 */
class IpLookupTool(
    private val httpClient: HttpClient
) : Tool {

    override val name = "ip_lookup"

    override val description =
        "Look up geolocation, ISP, and ASN for an IPv4/IPv6 address using the free ipapi.co service. Omit 'ip' to look up the caller's public IP."

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("ip") {
                put("type", "string")
                put("description", "IPv4 or IPv6 address. Optional — empty means caller's public IP.")
            }
        }
        putJsonArray("required") {}
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val ip = input["ip"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val url = if (ip.isEmpty()) "https://ipapi.co/json/" else "https://ipapi.co/$ip/json/"

        return try {
            val resp = httpClient.get(url) { header(HttpHeaders.Accept, "application/json") }
            if (!resp.status.isSuccess()) {
                return ToolResult("IP lookup API error: ${resp.status.value}", isError = true)
            }
            val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
            if (body["error"]?.jsonPrimitive?.booleanOrNull == true) {
                val reason = body["reason"]?.jsonPrimitive?.contentOrNull ?: "unknown error"
                return ToolResult("IP lookup error: $reason", isError = true)
            }

            fun s(key: String) = body[key]?.jsonPrimitive?.contentOrNull ?: "?"

            val sb = StringBuilder()
            sb.append("IP info for ").append(s("ip")).append(":\n")
            sb.append("  Location: ").append(s("city")).append(", ")
                .append(s("region")).append(", ").append(s("country_name"))
                .append(" (").append(s("country_code")).append(")\n")
            sb.append("  Postal: ").append(s("postal")).append("\n")
            sb.append("  Coords: ").append(s("latitude")).append(", ").append(s("longitude")).append("\n")
            sb.append("  Timezone: ").append(s("timezone"))
                .append(" (UTC").append(s("utc_offset")).append(")\n")
            sb.append("  ISP/Org: ").append(s("org")).append("\n")
            sb.append("  ASN: ").append(s("asn"))
            ToolResult(sb.toString())
        } catch (e: Exception) {
            ToolResult("IP lookup failed: ${e.message}", isError = true)
        }
    }
}
