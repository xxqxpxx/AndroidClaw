package com.androidclaw.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.androidclaw.app.R

/**
 * Manages sound effects for the app.
 * Plays short SFX for task completion, tool calls, sending messages, etc.
 */
class SoundManager(context: Context) {

    private val soundPool: SoundPool
    private val sounds = mutableMapOf<SoundEffect, Int>()
    private var loaded = false

    init {
        Log.i(TAG, "Initializing SoundManager")
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(attrs)
            .build()

        soundPool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) {
                loaded = true
                Log.d(TAG, "Sounds loaded")
            }
        }

        try {
            sounds[SoundEffect.TASK_DONE] = soundPool.load(context, R.raw.sfx_task_done, 1)
            sounds[SoundEffect.SEND] = soundPool.load(context, R.raw.sfx_send, 1)
            sounds[SoundEffect.TOOL_COMPLETE] = soundPool.load(context, R.raw.sfx_tool_complete, 1)
            sounds[SoundEffect.SUCCESS] = soundPool.load(context, R.raw.sfx_success, 1)
            Log.i(TAG, "Sound effects queued for loading")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load sounds", e)
        }
    }

    fun play(effect: SoundEffect, volume: Float = 0.5f) {
        val soundId = sounds[effect] ?: return
        if (!loaded) {
            Log.w(TAG, "Sounds not loaded yet, skipping $effect")
            return
        }
        Log.d(TAG, "Playing sound: $effect")
        soundPool.play(soundId, volume, volume, 1, 0, 1f)
    }

    fun release() {
        Log.d(TAG, "Releasing SoundManager")
        soundPool.release()
    }

    companion object {
        private const val TAG = "SoundManager"
    }
}

enum class SoundEffect {
    TASK_DONE,      // Message complete / task finished
    SEND,           // Message sent
    TOOL_COMPLETE,  // Tool execution finished
    SUCCESS         // Special success (e.g., first message, multi-tool chain complete)
}
