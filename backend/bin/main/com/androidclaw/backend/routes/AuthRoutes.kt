package com.androidclaw.backend.routes

import com.androidclaw.backend.auth.generateToken
import com.androidclaw.backend.config.AppConfig
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class TokenRequest(val deviceId: String)

@Serializable
data class TokenResponse(val token: String, val expiresIn: Long)

fun Routing.authRoutes(config: AppConfig) {
    route("/auth") {
        post("/token") {
            val request = call.receive<TokenRequest>()
            // Phase 1: Simple token generation for any device
            val token = generateToken(config, request.deviceId)
            call.respond(TokenResponse(
                token = token,
                expiresIn = 7 * 24 * 60 * 60 // 7 days in seconds
            ))
        }
    }
}
