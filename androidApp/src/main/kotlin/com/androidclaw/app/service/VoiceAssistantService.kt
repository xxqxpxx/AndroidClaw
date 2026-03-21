package com.androidclaw.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.androidclaw.app.MainActivity
import com.androidclaw.app.voice.VoicePipeline
import com.androidclaw.shared.agent.AgentLoop
import com.androidclaw.shared.memory.ConversationRepository
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class VoiceAssistantService : Service() {

    private val agentLoop: AgentLoop by inject()
    private val conversationRepo: ConversationRepository by inject()
    private val settings: com.androidclaw.app.settings.SettingsManager by inject()
    private var voicePipeline: VoicePipeline? = null
    private var stateObserverJob: kotlinx.coroutines.Job? = null
    private val serviceScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main
    )

    companion object {
        const val CHANNEL_ID = "voice_assistant_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.androidclaw.START_LISTENING"
        const val ACTION_STOP = "com.androidclaw.STOP_LISTENING"
        const val ACTION_MUTE = "com.androidclaw.MUTE"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        voicePipeline = VoicePipeline(this, agentLoop, conversationRepo, apiKeyProvider = { settings.apiKey.value })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForegroundWithNotification()
                voicePipeline?.startListening()
                observePipelineState()
            }
            ACTION_STOP -> {
                stateObserverJob?.cancel()
                voicePipeline?.stopListening()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_MUTE -> {
                voicePipeline?.stopListening()
                updateNotification("Muted", "Tap to resume listening")
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stateObserverJob?.cancel()
        serviceScope.cancel()
        voicePipeline?.release()
        super.onDestroy()
    }

    private fun observePipelineState() {
        stateObserverJob?.cancel()
        stateObserverJob = serviceScope.launch {
            voicePipeline?.state?.collect { state ->
                val (title, text) = when (state) {
                    com.androidclaw.app.voice.VoicePipelineState.IDLE -> "AndroidClaw" to "Ready"
                    com.androidclaw.app.voice.VoicePipelineState.LISTENING -> "AndroidClaw" to "Listening for wake word..."
                    com.androidclaw.app.voice.VoicePipelineState.RECORDING -> "Recording" to "Speak now..."
                    com.androidclaw.app.voice.VoicePipelineState.TRANSCRIBING -> "Processing" to "Transcribing speech..."
                    com.androidclaw.app.voice.VoicePipelineState.THINKING -> "Thinking" to "Processing your request..."
                    com.androidclaw.app.voice.VoicePipelineState.SPEAKING -> "Speaking" to "Playing response..."
                    com.androidclaw.app.voice.VoicePipelineState.ERROR -> "Error" to "Something went wrong"
                }
                updateNotification(title, text)
            }
        }
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification("AndroidClaw", "Listening for wake word...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(title: String, text: String) {
        val notification = buildNotification(title, text)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String, text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val stopIntent = Intent(this, VoiceAssistantService::class.java).apply {
            action = ACTION_STOP
        }.let {
            PendingIntent.getService(this, 1, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val muteIntent = Intent(this, VoiceAssistantService::class.java).apply {
            action = ACTION_MUTE
        }.let {
            PendingIntent.getService(this, 2, it, PendingIntent.FLAG_IMMUTABLE)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .addAction(android.R.drawable.ic_lock_silent_mode, "Mute", muteIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Voice Assistant",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "AndroidClaw voice assistant is listening"
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}
