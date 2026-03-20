package com.androidclaw.shared.tools

import com.androidclaw.shared.agent.Tool
import com.androidclaw.shared.agent.ToolResult
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

class WebContentTool(
    private val httpClient: HttpClient
) : Tool {

    override val name = "read_webpage"

    override val description = """Fetch and read the content of a webpage. Use this when the user asks you to read, summarize, or extract information from a specific URL."""

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("url") {
                put("type", "string")
                put("description", "The URL of the webpage to read")
            }
            putJsonObject("extract") {
                put("type", "string")
                put("description", "What to extract: 'text' for full text, 'summary' for a brief summary, 'links' for all links")
                putJsonArray("enum") { add("text"); add("summary"); add("links") }
            }
        }
        putJsonArray("required") { add("url") }
    }

    override suspend fun execute(input: JsonObject): ToolResult {
        val url = input["url"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult("Missing required parameter: url", isError = true)
        val extract = input["extract"]?.jsonPrimitive?.contentOrNull ?: "text"

        return try {
            val response = httpClient.get(url) {
                header(HttpHeaders.UserAgent, "AndroidClaw/1.0")
                header(HttpHeaders.Accept, "text/html,text/plain,application/json")
            }

            if (!response.status.isSuccess()) {
                return ToolResult("HTTP error: ${response.status.value} for $url", isError = true)
            }

            val contentType = response.contentType()?.contentType ?: ""
            val body = response.bodyAsText()

            when {
                contentType.contains("json") -> {
                    ToolResult("JSON content from $url:\n${body.take(MAX_CONTENT_LENGTH)}")
                }
                contentType.contains("text") || contentType.contains("html") -> {
                    val cleanText = stripHtml(body)
                    when (extract) {
                        "links" -> {
                            val links = extractLinks(body, url)
                            ToolResult("Links from $url:\n${links.joinToString("\n") { "- ${it.first}: ${it.second}" }}")
                        }
                        "summary" -> {
                            ToolResult("Content from $url (first ${SUMMARY_LENGTH} chars):\n${cleanText.take(SUMMARY_LENGTH)}")
                        }
                        else -> {
                            ToolResult("Content from $url:\n${cleanText.take(MAX_CONTENT_LENGTH)}")
                        }
                    }
                }
                else -> {
                    ToolResult("Non-text content at $url (type: $contentType, size: ${body.length} bytes)")
                }
            }
        } catch (e: Exception) {
            ToolResult("Failed to fetch $url: ${e.message}", isError = true)
        }
    }

    private fun stripHtml(html: String): String {
        return html
            .replace(Regex("<script[^>]*>.*?</script>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<style[^>]*>.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<nav[^>]*>.*?</nav>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<header[^>]*>.*?</header>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<footer[^>]*>.*?</footer>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("&amp;"), "&")
            .replace(Regex("&lt;"), "<")
            .replace(Regex("&gt;"), ">")
            .replace(Regex("&quot;"), "\"")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun extractLinks(html: String, baseUrl: String): List<Pair<String, String>> {
        val linkRegex = Regex("""<a[^>]+href="([^"]*)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        return linkRegex.findAll(html)
            .map { match ->
                val href = match.groupValues[1]
                val text = match.groupValues[2].replace(Regex("<[^>]+>"), "").trim()
                val fullUrl = when {
                    href.startsWith("http") -> href
                    href.startsWith("//") -> "https:$href"
                    href.startsWith("/") -> {
                        val base = Url(baseUrl)
                        "${base.protocol.name}://${base.host}$href"
                    }
                    else -> href
                }
                Pair(text.ifEmpty { fullUrl }, fullUrl)
            }
            .filter { it.second.startsWith("http") }
            .distinctBy { it.second }
            .take(50)
            .toList()
    }

    companion object {
        private const val MAX_CONTENT_LENGTH = 8000
        private const val SUMMARY_LENGTH = 2000
    }
}
