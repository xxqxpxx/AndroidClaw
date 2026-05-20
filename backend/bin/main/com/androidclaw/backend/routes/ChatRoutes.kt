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
import io.ktor.utils.io.*

private val proxyClient = HttpClient(CIO) {
    engine {
        requestTimeout = 120_000
    }
}

fun Routing.chatRoutes(config: AppConfig) {
    authenticate("auth-jwt") {
        post("/api/chat") {
            val requestBody = call.receiveText()

            // Proxy to Claude API with SSE streaming
            proxyClient.preparePost("${config.claudeBaseUrl}/v1/messages") {
                contentType(ContentType.Application.Json)
                header("x-api-key", config.claudeApiKey)
                header("anthropic-version", "2023-06-01")
                setBody(requestBody)
            }.execute { upstreamResponse ->
                // Relay status
                if (!upstreamResponse.status.isSuccess()) {
                    call.respondText(
                        upstreamResponse.bodyAsText(),
                        ContentType.Application.Json,
                        upstreamResponse.status
                    )
                    return@execute
                }

                // Stream SSE response byte-for-byte
                call.respondBytesWriter(
                    contentType = ContentType.Text.EventStream,
                    status = HttpStatusCode.OK
                ) {
                    val channel = upstreamResponse.bodyAsChannel()
                    val buffer = ByteArray(8192)
                    while (!channel.isClosedForRead) {
                        val read = channel.readAvailable(buffer)
                        if (read > 0) {
                            writeFully(buffer, 0, read)
                            flush()
                        }
                    }
                }
            }
        }
    }
}
