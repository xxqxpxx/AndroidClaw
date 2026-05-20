package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

/**
 * Currency converter backed by the free Frankfurter API (ECB rates, no key).
 * Supports ~30 fiat currencies. https://www.frankfurter.app/docs/
 */
class CurrencyConverterTool(
    private val httpClient: HttpClient
) : Tool {

    override val name = "convert_currency"

    override val description =
        "Convert an amount between fiat currencies using ECB reference rates. Codes are ISO-4217 (USD, EUR, GBP, JPY, EGP, …)."

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("amount") {
                put("type", "number")
                put("description", "Amount to convert")
            }
            putJsonObject("from") {
                put("type", "string")
                put("description", "Source currency ISO-4217 code (e.g. 'USD')")
            }
            putJsonObject("to") {
                put("type", "string")
                put("description", "Target currency ISO-4217 code (e.g. 'EUR')")
            }
        }
        putJsonArray("required") { add("amount"); add("from"); add("to") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val amount = input["amount"]?.jsonPrimitive?.doubleOrNull
            ?: return ToolResult("Missing or invalid 'amount'", isError = true)
        val from = input["from"]?.jsonPrimitive?.contentOrNull?.uppercase()
            ?: return ToolResult("Missing 'from'", isError = true)
        val to = input["to"]?.jsonPrimitive?.contentOrNull?.uppercase()
            ?: return ToolResult("Missing 'to'", isError = true)

        if (from == to) return ToolResult("$amount $from = $amount $to (same currency)")

        return try {
            val url = "https://api.frankfurter.app/latest?amount=$amount&from=$from&to=$to"
            val resp = httpClient.get(url) { header(HttpHeaders.Accept, "application/json") }
            if (!resp.status.isSuccess()) {
                return ToolResult("Currency API error: ${resp.status.value}", isError = true)
            }
            val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
            val rates = body["rates"]?.jsonObject
                ?: return ToolResult("Malformed response", isError = true)
            val converted = rates[to]?.jsonPrimitive?.doubleOrNull
                ?: return ToolResult("Currency $to not supported", isError = true)
            val date = body["date"]?.jsonPrimitive?.contentOrNull ?: "today"
            val rounded = ((converted * 100).toLong()) / 100.0
            ToolResult("$amount $from = $rounded $to (rate as of $date)")
        } catch (e: Exception) {
            ToolResult("Currency conversion failed: ${e.message}", isError = true)
        }
    }
}
