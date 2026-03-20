package com.androidclaw.app.service

import android.content.Context
import android.content.Intent
import android.os.Build

object ServiceController {
    fun startListening(context: Context) {
        val intent = Intent(context, VoiceAssistantService::class.java).apply {
            action = VoiceAssistantService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopListening(context: Context) {
        val intent = Intent(context, VoiceAssistantService::class.java).apply {
            action = VoiceAssistantService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun mute(context: Context) {
        val intent = Intent(context, VoiceAssistantService::class.java).apply {
            action = VoiceAssistantService.ACTION_MUTE
        }
        context.startService(intent)
    }
}
