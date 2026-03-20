package com.androidclaw.backend.routes

import com.androidclaw.backend.config.AppConfig
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class SearchRequest(val query: String, val maxResults: Int = 5)

private val searchClient = HttpClient(CIO)

fun Routing.searchRoutes(config: AppConfig) {
    authenticate("auth-jwt") {
        post("/api/search") {
            if (config.tavilyApiKey.isEmpty()) {
                call.respondText(
                    """{"error": "Search API key not configured"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.ServiceUnavailable
                )
                return@post
            }

            val request = call.receive<SearchRequest>()

            val response = searchClient.post("https://api.tavily.com/search") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("api_key", config.tavilyApiKey)
                    put("query", request.query)
                    put("max_results", request.maxResults)
                    put("include_answer", true)
                }.toString())
            }

            call.respondText(
                response.bodyAsText(),
                ContentType.Application.Json,
                response.status
            )
        }
    }
}
