package com.androidclaw.shared.tools

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebContentToolTest {

    private fun createMockClient(
        responseBody: String = "<html><body>Hello World</body></html>",
        contentType: String = "text/html",
        status: HttpStatusCode = HttpStatusCode.OK
    ): HttpClient {
        return HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    respond(
                        content = responseBody,
                        status = status,
                        headers = headersOf(HttpHeaders.ContentType, contentType)
                    )
                }
            }
        }
    }

    @Test
    fun name_isReadWebpage() {
        val tool = WebContentTool(createMockClient())
        assertEquals("read_webpage", tool.name)
    }

    @Test
    fun execute_missingUrl_returnsError() = runTest {
        val tool = WebContentTool(createMockClient())
        val result = tool.execute(buildJsonObject { })
        assertTrue(result.isError)
        assertTrue(result.content.contains("Missing required parameter"))
    }

    @Test
    fun execute_htmlContent_stripsTagsAndReturnsText() = runTest {
        val html = "<html><body><p>Hello</p><p>World</p></body></html>"
        val tool = WebContentTool(createMockClient(html))
        val input = buildJsonObject { put("url", "https://example.com") }
        val result = tool.execute(input)
        assertFalse(result.isError)
        assertTrue(result.content.contains("Hello"))
        assertTrue(result.content.contains("World"))
        // Tags should be stripped
        assertFalse(result.content.contains("<p>"))
    }

    @Test
    fun execute_stripsScriptTags() = runTest {
        val html = "<html><body><script>alert('xss')</script><p>Safe content</p></body></html>"
        val tool = WebContentTool(createMockClient(html))
        val input = buildJsonObject { put("url", "https://example.com") }
        val result = tool.execute(input)
        assertFalse(result.isError)
        assertTrue(result.content.contains("Safe content"))
        assertFalse(result.content.contains("alert"))
    }

    @Test
    fun execute_stripsStyleTags() = runTest {
        val html = "<html><body><style>body { color: red; }</style><p>Styled</p></body></html>"
        val tool = WebContentTool(createMockClient(html))
        val input = buildJsonObject { put("url", "https://example.com") }
        val result = tool.execute(input)
        assertFalse(result.isError)
        assertTrue(result.content.contains("Styled"))
        assertFalse(result.content.contains("color: red"))
    }

    @Test
    fun execute_stripsNavHeaderFooter() = runTest {
        val html = """
            <html><body>
                <nav>Navigation menu</nav>
                <header>Header content</header>
                <main>Main content</main>
                <footer>Footer content</footer>
            </body></html>
        """.trimIndent()
        val tool = WebContentTool(createMockClient(html))
        val input = buildJsonObject { put("url", "https://example.com") }
        val result = tool.execute(input)
        assertFalse(result.isError)
        assertTrue(result.content.contains("Main content"))
        assertFalse(result.content.contains("Navigation menu"))
        assertFalse(result.content.contains("Header content"))
        assertFalse(result.content.contains("Footer content"))
    }

    @Test
    fun execute_decodesHtmlEntities() = runTest {
        val html = "<html><body>Tom &amp; Jerry &lt;3&gt; &quot;friends&quot;</body></html>"
        val tool = WebContentTool(createMockClient(html))
        val input = buildJsonObject { put("url", "https://example.com") }
        val result = tool.execute(input)
        assertFalse(result.isError)
        assertTrue(result.content.contains("Tom & Jerry"))
        assertTrue(result.content.contains("<3>"))
        assertTrue(result.content.contains("\"friends\""))
    }

    @Test
    fun execute_jsonContent_returnsRaw() = runTest {
        val json = """{"key": "value"}"""
        val tool = WebContentTool(createMockClient(json, "application/json"))
        val input = buildJsonObject { put("url", "https://example.com/api") }
        val result = tool.execute(input)
        assertFalse(result.isError)
        assertTrue(result.content.contains("JSON content"))
        assertTrue(result.content.contains(""""key": "value""""))
    }

    @Test
    fun execute_httpError_returnsError() = runTest {
        val tool = WebContentTool(createMockClient(status = HttpStatusCode.NotFound))
        val input = buildJsonObject { put("url", "https://example.com/404") }
        val result = tool.execute(input)
        assertTrue(result.isError)
        assertTrue(result.content.contains("HTTP error"))
    }

    @Test
    fun execute_summaryMode_truncatesContent() = runTest {
        val longContent = "<html><body>${"A".repeat(5000)}</body></html>"
        val tool = WebContentTool(createMockClient(longContent))
        val input = buildJsonObject {
            put("url", "https://example.com")
            put("extract", "summary")
        }
        val result = tool.execute(input)
        assertFalse(result.isError)
        // Summary mode should truncate to ~2000 chars
        assertTrue(result.content.length < 5000)
    }

    @Test
    fun execute_linksMode_extractsLinks() = runTest {
        val html = """
            <html><body>
                <a href="https://example.com/page1">Page 1</a>
                <a href="https://example.com/page2">Page 2</a>
            </body></html>
        """.trimIndent()
        val tool = WebContentTool(createMockClient(html))
        val input = buildJsonObject {
            put("url", "https://example.com")
            put("extract", "links")
        }
        val result = tool.execute(input)
        assertFalse(result.isError)
        assertTrue(result.content.contains("Links from"))
        assertTrue(result.content.contains("Page 1"))
        assertTrue(result.content.contains("Page 2"))
    }
}
