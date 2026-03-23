package com.androidclaw.app.voice

import android.util.Log

sealed class VadEvent {
    data object SpeechStart : VadEvent()
    data object SpeechEnd : VadEvent()
    data class AudioFrame(val samples: ShortArray) : VadEvent()
}

class VadDetector(
    private val speechThresholdMs: Int = 300,
    private val silenceThresholdMs: Int = 1000,
    private val energyThreshold: Float = 0.005f
) {
    private var isSpeaking = false
    private var speechFrames = 0
    private var silenceFrames = 0
    private var totalFrames = 0
    private val frameDurationMs = 32 // ~512 samples at 16kHz

    fun processFrame(samples: ShortArray): VadEvent? {
        val energy = calculateEnergy(samples)
        val isSpeech = energy > energyThreshold
        totalFrames++

        if (totalFrames <= 10 || totalFrames % 50 == 0) {
            Log.d(TAG, "Frame #$totalFrames energy=%.6f threshold=%.6f isSpeech=$isSpeech speaking=$isSpeaking".format(energy, energyThreshold))
        }

        if (isSpeech) {
            speechFrames++
            silenceFrames = 0

            if (!isSpeaking && speechFrames * frameDurationMs >= speechThresholdMs) {
                isSpeaking = true
                Log.i(TAG, "Speech START detected at frame #$totalFrames")
                return VadEvent.SpeechStart
            }
        } else {
            silenceFrames++

            if (isSpeaking && silenceFrames * frameDurationMs >= silenceThresholdMs) {
                Log.i(TAG, "Speech END detected at frame #$totalFrames (silence=${silenceFrames * frameDurationMs}ms)")
                isSpeaking = false
                speechFrames = 0
                silenceFrames = 0
                return VadEvent.SpeechEnd
            }
        }

        return if (isSpeaking) VadEvent.AudioFrame(samples) else null
    }

    fun reset() {
        isSpeaking = false
        speechFrames = 0
        silenceFrames = 0
        totalFrames = 0
    }

    private fun calculateEnergy(samples: ShortArray): Float {
        var sum = 0.0
        for (sample in samples) {
            val normalized = sample / 32768.0
            sum += normalized * normalized
        }
        return (sum / samples.size).toFloat()
    }

    companion object {
        private const val TAG = "VadDetector"
    }
}
