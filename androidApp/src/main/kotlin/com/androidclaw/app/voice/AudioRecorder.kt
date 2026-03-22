package com.androidclaw.app.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

class AudioRecorder(private val context: Context) {
    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val FRAME_SIZE = 512 // samples per frame

        fun shortsToFloats(shorts: ShortArray): FloatArray {
            return FloatArray(shorts.size) { shorts[it] / 32768f }
        }
    }

    private var audioRecord: AudioRecord? = null

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun recordAudioFrames(): Flow<ShortArray> = flow {
        if (!hasPermission()) return@flow

        val bufferSize = maxOf(
            AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT),
            FRAME_SIZE * 2
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        audioRecord?.startRecording()

        val buffer = ShortArray(FRAME_SIZE)
        try {
            while (coroutineContext.isActive) {
                val read = audioRecord?.read(buffer, 0, FRAME_SIZE) ?: break
                if (read > 0) {
                    emit(buffer.copyOf(read))
                }
            }
        } finally {
            stop()
        }
    }.flowOn(Dispatchers.IO)

    fun stop() {
        try {
            val recorder = audioRecord
            if (recorder != null && recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop()
            }
            recorder?.release()
        } catch (_: IllegalStateException) {
            // Already stopped or not initialized
        } finally {
            audioRecord = null
        }
    }
}
