package com.androidclaw.app.voice

class WhisperJni {
    companion object {
        private var loaded = false

        fun loadLibrary(): Boolean {
            return try {
                System.loadLibrary("whisper_jni")
                loaded = true
                true
            } catch (e: UnsatisfiedLinkError) {
                loaded = false
                false
            }
        }

        fun isLoaded() = loaded
    }

    external fun initModel(modelPath: String): Long
    external fun transcribe(contextPtr: Long, audioSamples: FloatArray): String
    external fun freeModel(contextPtr: Long)
}
