package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

class WebSearchTool(
    private val httpClient: HttpClient,
    private val apiKey: String = "",
    private val baseUrl: String = "https://api.tavily.com"
) : Tool {

    override val name = "web_search"

    override val description = "Search the web for current information. Use this when you need up-to-date facts, news, or information you don't have."

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("query") {
                put("type", "string")
                put("description", "The search query")
            }
            putJsonObject("max_results") {
                put("type", "integer")
                put("description", "Maximum number of results to return (default 5)")
            }
        }
        putJsonArray("required") {
            add("query")
        }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val query = input["query"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing required parameter: query", isError = true)
        val maxResults = input["max_results"]?.jsonPrimitive?.intOrNull ?: 5

        return try {
            val response = httpClient.post("$baseUrl/search") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("api_key", apiKey)
                    put("query", query)
                    put("max_results", maxResults)
                    put("include_answer", true)
                    put("include_raw_content", false)
                }.toString())
            }

            if (!response.status.isSuccess()) {
                return ToolResult("Search failed: HTTP ${response.status.value}", isError = true)
            }

            val body = Json.decodeFromString(JsonObject.serializer(), response.bodyAsText())
            val answer = body["answer"]?.jsonPrimitive?.contentOrNull
            val results = body["results"]?.jsonArray

            val formatted = buildString {
                if (answer != null) {
                    appendLine("Summary: $answer")
                    appendLine()
                }
                results?.take(maxResults)?.forEachIndexed { i, result ->
                    val obj = result.jsonObject
                    val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "Untitled"
                    val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: ""
                    val content = obj["content"]?.jsonPrimitive?.contentOrNull ?: ""
                    appendLine("${i + 1}. $title")
                    appendLine("   URL: $url")
                    appendLine("   $content")
                    appendLine()
                }
            }

            ToolResult(formatted.ifEmpty { "No results found for: $query" })
        } catch (e: Exception) {
            ToolResult("Search error: ${e.message}", isError = true)
        }
    }
}
