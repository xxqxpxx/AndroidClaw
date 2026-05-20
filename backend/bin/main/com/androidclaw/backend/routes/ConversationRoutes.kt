package com.androidclaw.backend.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class ConversationSyncRequest(
    val conversationId: String,
    val messages: List<SyncMessage>
)

@Serializable
data class SyncMessage(
    val id: String,
    val role: String,
    val content: String,
    val createdAt: Long
)

@Serializable
data class ConversationSyncResponse(
    val conversationId: String,
    val synced: Boolean,
    val messageCount: Int
)

// In-memory store for development; swap for database in production
private val conversationStore = ConcurrentHashMap<String, MutableList<SyncMessage>>()

fun Routing.conversationRoutes() {
    authenticate("auth-jwt") {
        route("/api/conversations") {
            // Sync conversation to server
            post("/sync") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.getClaim("userId", String::class) ?: "anonymous"
                val request = call.receive<ConversationSyncRequest>()

                val key = "$userId:${request.conversationId}"
                val existing = conversationStore.getOrPut(key) { mutableListOf() }

                // Add only new messages
                val existingIds = existing.map { it.id }.toSet()
                val newMessages = request.messages.filter { it.id !in existingIds }
                existing.addAll(newMessages)

                call.respond(ConversationSyncResponse(
                    conversationId = request.conversationId,
                    synced = true,
                    messageCount = existing.size
                ))
            }

            // Get synced conversation
            get("/{conversationId}") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.getClaim("userId", String::class) ?: "anonymous"
                val conversationId = call.parameters["conversationId"]
                    ?: return@get call.respondText("Missing ID", status = HttpStatusCode.BadRequest)

                val key = "$userId:$conversationId"
                val messages = conversationStore[key]

                if (messages != null) {
                    call.respond(mapOf(
                        "conversationId" to conversationId,
                        "messages" to messages
                    ))
                } else {
                    call.respondText(
                        """{"error": "Conversation not found"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.NotFound
                    )
                }
            }

            // List user's conversations
            get {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.getClaim("userId", String::class) ?: "anonymous"

                val userConversations = conversationStore
                    .filterKeys { it.startsWith("$userId:") }
                    .map { (key, messages) ->
                        val convId = key.removePrefix("$userId:")
                        mapOf(
                            "conversationId" to convId,
                            "messageCount" to messages.size.toString(),
                            "lastMessage" to (messages.lastOrNull()?.content?.take(100) ?: "")
                        )
                    }

                call.respond(userConversations)
            }
        }
    }
}
