package com.androidclaw.backend.config

data class AppConfig(
    val claudeApiKey: String,
    val claudeBaseUrl: String = "https://api.anthropic.com",
    val jwtSecret: String,
    val jwtIssuer: String = "androidclaw",
    val jwtAudience: String = "androidclaw-app",
    val tavilyApiKey: String = "",
    val port: Int = 8080
) {
    companion object {
        fun fromEnvironment(): AppConfig {
            return AppConfig(
                claudeApiKey = System.getenv("CLAUDE_API_KEY") ?: "",
                claudeBaseUrl = System.getenv("CLAUDE_BASE_URL") ?: "https://api.anthropic.com",
                jwtSecret = System.getenv("JWT_SECRET") ?: "dev-secret-change-in-production",
                jwtIssuer = System.getenv("JWT_ISSUER") ?: "androidclaw",
                jwtAudience = System.getenv("JWT_AUDIENCE") ?: "androidclaw-app",
                tavilyApiKey = System.getenv("TAVILY_API_KEY") ?: "",
                port = System.getenv("PORT")?.toIntOrNull() ?: 8080
            )
        }
    }
}
