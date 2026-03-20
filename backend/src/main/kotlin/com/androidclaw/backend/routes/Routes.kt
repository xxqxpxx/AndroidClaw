package com.androidclaw.backend.routes

import com.androidclaw.backend.config.AppConfig
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRoutes(config: AppConfig) {
    routing {
        statusRoutes(config)
        authRoutes(config)
        chatRoutes(config)
        searchRoutes(config)
        conversationRoutes()
    }
}
