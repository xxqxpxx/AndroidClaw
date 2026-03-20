package com.androidclaw.app.voice

import android.content.Context

/**
 * Wake word detection using Porcupine.
 * Requires a Picovoice access key from https://console.picovoice.ai/
 *
 * Phase 1: Stub implementation. Actual Porcupine integration requires:
 * 1. Picovoice access key
 * 2. Custom .ppn wake word file or built-in keyword
 */
class WakeWordDetector(
    private val context: Context,
    private val accessKey: String = "",
    private val onWakeWordDetected: () -> Unit
) {
    private var isListening = false

    fun start() {
        if (accessKey.isEmpty()) return
        isListening = true
        // TODO: Initialize PorcupineManager with accessKey and keyword
        // porcupineManager = PorcupineManager.Builder()
        //     .setAccessKey(accessKey)
        //     .setKeywordPath("path/to/keyword.ppn")
        //     .setSensitivity(0.7f)
        //     .build(context) { keywordIndex ->
        //         onWakeWordDetected()
        //     }
        // porcupineManager.start()
    }

    fun stop() {
        isListening = false
        // porcupineManager?.stop()
        // porcupineManager?.delete()
    }

    fun isActive() = isListening
}
