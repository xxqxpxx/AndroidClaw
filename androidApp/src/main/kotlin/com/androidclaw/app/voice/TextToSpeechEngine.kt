package com.androidclaw.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Text-to-speech using Piper TTS via sherpa-onnx.
 *
 * Phase 1: Stub using Android's built-in TTS as fallback.
 * Full Piper integration requires sherpa-onnx native library + model files.
 */
class TextToSpeechEngine(private val context: Context) {
    private var tts: android.speech.tts.TextToSpeech? = null
    private var initialized = false

    fun initialize(onReady: () -> Unit) {
        tts = android.speech.tts.TextToSpeech(context) { status ->
            initialized = status == android.speech.tts.TextToSpeech.SUCCESS
            if (initialized) onReady()
        }
    }

    suspend fun speak(text: String) = withContext(Dispatchers.Main) {
        if (!initialized) return@withContext
        tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "utterance_${System.currentTimeMillis()}")
    }

    fun stop() {
        tts?.stop()
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        initialized = false
    }
}
