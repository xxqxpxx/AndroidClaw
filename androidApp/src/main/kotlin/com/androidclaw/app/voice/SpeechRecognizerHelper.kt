package com.androidclaw.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

sealed class SpeechResult {
    data class Partial(val text: String) : SpeechResult()
    data class Final(val text: String) : SpeechResult()
    data class Error(val code: Int, val message: String) : SpeechResult()
    data object ReadyForSpeech : SpeechResult()
    data object EndOfSpeech : SpeechResult()
}

class SpeechRecognizerHelper(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null
    private val resultChannel = Channel<SpeechResult>(Channel.BUFFERED)

    val results: Flow<SpeechResult> = resultChannel.receiveAsFlow()

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening() {
        Log.i(TAG, "startListening")
        stop()

        if (!isAvailable()) {
            Log.e(TAG, "SpeechRecognizer not available on this device")
            resultChannel.trySend(SpeechResult.Error(-1, "Speech recognition not available"))
            return
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.i(TAG, "Ready for speech")
                    resultChannel.trySend(SpeechResult.ReadyForSpeech)
                }

                override fun onBeginningOfSpeech() {
                    Log.i(TAG, "Speech began")
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.i(TAG, "End of speech")
                    resultChannel.trySend(SpeechResult.EndOfSpeech)
                }

                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                        else -> "Unknown error ($error)"
                    }
                    Log.e(TAG, "Recognition error: $msg (code=$error)")
                    resultChannel.trySend(SpeechResult.Error(error, msg))
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    Log.i(TAG, "Final result: \"${text.take(100)}\"")
                    resultChannel.trySend(SpeechResult.Final(text))
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    if (text.isNotBlank()) {
                        Log.d(TAG, "Partial: \"${text.take(80)}\"")
                        resultChannel.trySend(SpeechResult.Partial(text))
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        recognizer?.startListening(intent)
    }

    fun stop() {
        try {
            recognizer?.stopListening()
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping recognizer", e)
        }
        recognizer = null
    }

    fun release() {
        stop()
        resultChannel.close()
    }

    companion object {
        private const val TAG = "SpeechRecognizer"
    }
}
