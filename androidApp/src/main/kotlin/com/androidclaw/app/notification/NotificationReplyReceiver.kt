package com.androidclaw.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.androidclaw.app.MainActivity
import com.androidclaw.shared.agent.AgentEvent
import com.androidclaw.shared.agent.AgentLoop
import com.androidclaw.shared.memory.ConversationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Handles inline reply from notifications.
 * Users can type a response directly in the notification shade.
 */
class NotificationReplyReceiver : BroadcastReceiver(), KoinComponent {

    private val agentLoop: AgentLoop by inject()
    private val conversationRepo: ConversationRepository by inject()
    private val settings: com.androidclaw.app.settings.SettingsManager by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val KEY_TEXT_REPLY = "key_text_reply"
        const val EXTRA_CONVERSATION_ID = "conversation_id"
        const val CHANNEL_ID = "chat_reply_channel"
        const val REPLY_NOTIFICATION_ID = 2000

        fun createReplyNotification(context: Context, conversationId: String, message: String) {
            ensureChannel(context)

            val replyLabel = "Reply to AndroidClaw"
            val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
                .setLabel(replyLabel)
                .build()

            val replyIntent = Intent(context, NotificationReplyReceiver::class.java).apply {
                putExtra(EXTRA_CONVERSATION_ID, conversationId)
            }
            val replyPendingIntent = PendingIntent.getBroadcast(
                context, conversationId.hashCode(), replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            val replyAction = NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_send, "Reply", replyPendingIntent
            ).addRemoteInput(remoteInput).build()

            val openIntent = Intent(context, MainActivity::class.java).apply {
                putExtra("conversationId", conversationId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPending = PendingIntent.getActivity(
                context, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("AndroidClaw")
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .addAction(replyAction)
                .setContentIntent(openPending)
                .setAutoCancel(true)
                .build()

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(REPLY_NOTIFICATION_ID, notification)
        }

        private fun ensureChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Chat Replies",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for AndroidClaw chat responses"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInput?.getCharSequence(KEY_TEXT_REPLY)?.toString() ?: return
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID) ?: return

        // Show "thinking" notification
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val thinkingNotification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("AndroidClaw")
            .setContentText("Thinking...")
            .setOngoing(true)
            .build()
        manager.notify(REPLY_NOTIFICATION_ID, thinkingNotification)

        // Process the reply
        scope.launch {
            val responseBuilder = StringBuilder()
            try {
                val apiKey = settings.apiKey.value.takeIf { it.isNotBlank() }
                agentLoop.run(conversationId, replyText, apiKey = apiKey).collect { event ->
                    when (event) {
                        is AgentEvent.TextDelta -> responseBuilder.append(event.text)
                        is AgentEvent.MessageComplete -> {}
                        else -> {}
                    }
                }
            } catch (_: Exception) {
                responseBuilder.append("Sorry, something went wrong.")
            }

            val response = responseBuilder.toString().ifEmpty { "Done." }
            // Show response with another reply action
            createReplyNotification(context, conversationId, response)
        }
    }
}
