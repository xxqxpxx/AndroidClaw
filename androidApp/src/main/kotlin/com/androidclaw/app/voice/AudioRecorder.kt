package com.androidclaw.app.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

class AudioRecorder(private val context: Context) {
    private var audioRecord: AudioRecord? = null

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun recordAudioFrames(): Flow<ShortArray> = flow {
        if (!hasPermission()) {
            Log.e(TAG, "RECORD_AUDIO permission not granted, aborting")
            return@flow
        }

        val bufferSize = maxOf(
            AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT),
            FRAME_SIZE * 2
        )
        Log.i(TAG, "Creating AudioRecord: sampleRate=$SAMPLE_RATE, bufferSize=$bufferSize")

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AudioRecord", e)
            return@flow
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize, state=${audioRecord?.state}")
            audioRecord?.release()
            audioRecord = null
            return@flow
        }

        Log.i(TAG, "Starting recording...")
        audioRecord?.startRecording()

        if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            Log.e(TAG, "AudioRecord not recording, recordingState=${audioRecord?.recordingState}")
            stop()
            return@flow
        }

        Log.i(TAG, "Recording active, reading frames")
        val buffer = ShortArray(FRAME_SIZE)
        var frameCount = 0
        try {
            while (coroutineContext.isActive) {
                val read = audioRecord?.read(buffer, 0, FRAME_SIZE) ?: break
                if (read > 0) {
                    frameCount++
                    if (frameCount <= 3 || frameCount % 100 == 0) {
                        Log.d(TAG, "Frame #$frameCount, read=$read samples")
                    }
                    emit(buffer.copyOf(read))
                } else if (read < 0) {
                    Log.e(TAG, "AudioRecord.read error: $read")
                    break
                }
            }
        } finally {
            Log.i(TAG, "Recording stopped after $frameCount frames")
            stop()
        }
    }.flowOn(Dispatchers.IO)

    fun stop() {
        try {
            val recorder = audioRecord
            if (recorder != null && recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                Log.d(TAG, "Stopping AudioRecord")
                recorder.stop()
            }
            recorder?.release()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "AudioRecord stop error", e)
        } finally {
            audioRecord = null
        }
    }

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val FRAME_SIZE = 512

        private const val TAG = "AudioRecorder"

        fun shortsToFloats(shorts: ShortArray): FloatArray {
            return FloatArray(shorts.size) { shorts[it] / 32768f }
        }
    }
}
