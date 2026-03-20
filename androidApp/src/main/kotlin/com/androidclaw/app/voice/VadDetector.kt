package com.androidclaw.app.voice

sealed class VadEvent {
    data object SpeechStart : VadEvent()
    data object SpeechEnd : VadEvent()
    data class AudioFrame(val samples: ShortArray) : VadEvent()
}

class VadDetector(
    private val speechThresholdMs: Int = 300,
    private val silenceThresholdMs: Int = 1000,
    private val energyThreshold: Float = 0.02f
) {
    private var isSpeaking = false
    private var speechFrames = 0
    private var silenceFrames = 0
    private val frameDurationMs = 32 // ~512 samples at 16kHz

    fun processFrame(samples: ShortArray): VadEvent? {
        val energy = calculateEnergy(samples)
        val isSpeech = energy > energyThreshold

        if (isSpeech) {
            speechFrames++
            silenceFrames = 0

            if (!isSpeaking && speechFrames * frameDurationMs >= speechThresholdMs) {
                isSpeaking = true
                return VadEvent.SpeechStart
            }
        } else {
            silenceFrames++

            if (isSpeaking && silenceFrames * frameDurationMs >= silenceThresholdMs) {
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
    }

    private fun calculateEnergy(samples: ShortArray): Float {
        var sum = 0.0
        for (sample in samples) {
            val normalized = sample / 32768.0
            sum += normalized * normalized
        }
        return (sum / samples.size).toFloat()
    }
}
