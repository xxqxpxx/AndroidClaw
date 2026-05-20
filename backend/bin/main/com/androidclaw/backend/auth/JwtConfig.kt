package com.androidclaw.backend.auth

import com.androidclaw.backend.config.AppConfig
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import java.util.*

fun Application.configureAuth(config: AppConfig) {
    install(Authentication) {
        jwt("auth-jwt") {
            realm = "androidclaw"
            verifier(
                JWT.require(Algorithm.HMAC256(config.jwtSecret))
                    .withIssuer(config.jwtIssuer)
                    .withAudience(config.jwtAudience)
                    .build()
            )
            validate { credential ->
                if (credential.payload.audience.contains(config.jwtAudience)) {
                    JWTPrincipal(credential.payload)
                } else null
            }
        }
    }
}

fun generateToken(config: AppConfig, userId: String): String {
    return JWT.create()
        .withIssuer(config.jwtIssuer)
        .withAudience(config.jwtAudience)
        .withClaim("userId", userId)
        .withExpiresAt(Date(System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000)) // 7 days
        .sign(Algorithm.HMAC256(config.jwtSecret))
}
