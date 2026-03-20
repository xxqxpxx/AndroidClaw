package com.androidclaw.app.voice

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class WhisperTranscriber(private val context: Context) {
    private val whisperJni = WhisperJni()
    private var contextPtr: Long = 0L
    private val mutex = Mutex()
    private var initialized = false

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (initialized) return@withContext true

            if (!WhisperJni.loadLibrary()) {
                return@withContext false
            }

            val modelFile = getModelFile()
            if (!modelFile.exists()) {
                // Model needs to be downloaded - return false for now
                return@withContext false
            }

            contextPtr = whisperJni.initModel(modelFile.absolutePath)
            initialized = contextPtr != 0L
            initialized
        }
    }

    suspend fun transcribe(audioSamples: FloatArray): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!initialized || contextPtr == 0L) {
                return@withContext ""
            }
            whisperJni.transcribe(contextPtr, audioSamples)
        }
    }

    fun release() {
        if (initialized && contextPtr != 0L) {
            whisperJni.freeModel(contextPtr)
            contextPtr = 0L
            initialized = false
        }
    }

    private fun getModelFile(): File {
        val modelsDir = File(context.filesDir, "models")
        modelsDir.mkdirs()
        return File(modelsDir, "ggml-base.en-q5_0.bin")
    }

    fun isModelDownloaded(): Boolean = getModelFile().exists()
}
