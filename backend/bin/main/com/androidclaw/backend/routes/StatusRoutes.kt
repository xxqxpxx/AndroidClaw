package com.androidclaw.backend.routes

import com.androidclaw.backend.config.AppConfig
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

@Serializable
data class StatusResponse(
    val status: String,
    val version: String,
    val capabilities: List<String>
)

fun Routing.statusRoutes(config: AppConfig) {
    get("/api/status") {
        val capabilities = mutableListOf("chat", "streaming")
        if (config.tavilyApiKey.isNotEmpty()) capabilities.add("web_search")
        capabilities.add("device_control") // Phase 2

        call.respond(
            StatusResponse(
                status = "ok",
                version = "0.2.0",
                capabilities = capabilities
            )
        )
    }

    get("/api/models") {
        call.respondText(
            buildJsonObject {
                putJsonArray("whisper") {
                    addModelEntry("tiny.en", "ggml-tiny.en.bin", 77_691_713)
                    addModelEntry("base.en", "ggml-base.en.bin", 147_951_465)
                    addModelEntry("base.en-q5_0", "ggml-base.en-q5_0.bin", 57_336_064)
                    addModelEntry("small.en", "ggml-small.en.bin", 487_601_913)
                }
                put("default_model", "base.en-q5_0")
            }.toString(),
            ContentType.Application.Json
        )
    }
}

private fun kotlinx.serialization.json.JsonArrayBuilder.addModelEntry(
    name: String, fileName: String, size: Long
) {
    add(buildJsonObject {
        put("name", name)
        put("file_name", fileName)
        put("size_bytes", size)
    })
}
