package com.androidclaw.app.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.androidclaw.shared.agent.AgentEvent
import com.androidclaw.shared.agent.AgentLoop
import com.androidclaw.shared.memory.ConversationRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class VoicePipelineState {
    IDLE,
    LISTENING,     // Wake word detection active
    RECORDING,     // User speaking
    TRANSCRIBING,  // Processing speech
    THINKING,      // Claude processing
    SPEAKING,      // TTS playing response
    ERROR
}

class VoicePipeline(
    private val context: Context,
    private val agentLoop: AgentLoop,
    private val conversationRepo: ConversationRepository,
    private val apiKeyProvider: () -> String = { "" }
) {
    private val _state = MutableStateFlow(VoicePipelineState.IDLE)
    val state: StateFlow<VoicePipelineState> = _state.asStateFlow()

    private val _lastTranscription = MutableStateFlow("")
    val lastTranscription: StateFlow<String> = _lastTranscription.asStateFlow()

    private val _lastResponse = MutableStateFlow("")
    val lastResponse: StateFlow<String> = _lastResponse.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val speechHelper = SpeechRecognizerHelper(context)
    private val ttsEngine = TextToSpeechEngine(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var pipelineJob: Job? = null
    private var activeConversationId: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun startListening(conversationId: String? = null) {
        activeConversationId = conversationId
        _state.value = VoicePipelineState.LISTENING
    }

    fun stopListening() {
        Log.i(TAG, "stopListening")
        pipelineJob?.cancel()
        speechHelper.stop()
        ttsEngine.stop()
        _state.value = VoicePipelineState.IDLE
        _partialText.value = ""
    }

    fun startRecordingManually() {
        Log.i(TAG, "startRecordingManually called")

        if (!speechHelper.isAvailable()) {
            Log.e(TAG, "Speech recognition not available")
            _state.value = VoicePipelineState.ERROR
            return
        }

        _state.value = VoicePipelineState.RECORDING
        _partialText.value = ""
        _lastTranscription.value = ""

        // SpeechRecognizer must be started on main thread
        mainHandler.post {
            speechHelper.startListening()
        }

        pipelineJob = scope.launch {
            speechHelper.results.collect { result ->
                when (result) {
                    is SpeechResult.ReadyForSpeech -> {
                        Log.i(TAG, "Ready for speech")
                    }
                    is SpeechResult.Partial -> {
                        _partialText.value = result.text
                    }
                    is SpeechResult.EndOfSpeech -> {
                        Log.i(TAG, "End of speech detected")
                        _state.value = VoicePipelineState.TRANSCRIBING
                    }
                    is SpeechResult.Final -> {
                        val text = result.text.trim()
                        Log.i(TAG, "Final transcription: \"${text.take(100)}\"")
                        _partialText.value = ""
                        _lastTranscription.value = text

                        if (text.isNotEmpty()) {
                            processTranscription(text)
                        } else {
                            Log.w(TAG, "Empty transcription")
                            _state.value = VoicePipelineState.IDLE
                        }
                        return@collect
                    }
                    is SpeechResult.Error -> {
                        Log.e(TAG, "Speech error: ${result.message}")
                        _partialText.value = ""
                        _state.value = if (result.code == android.speech.SpeechRecognizer.ERROR_NO_MATCH ||
                            result.code == android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            VoicePipelineState.IDLE
                        } else {
                            VoicePipelineState.ERROR
                        }
                        return@collect
                    }
                }
            }
        }
    }

    private suspend fun processTranscription(text: String) {
        val conversationId = activeConversationId
            ?: conversationRepo.createConversation().also { activeConversationId = it }

        _state.value = VoicePipelineState.THINKING
        Log.i(TAG, "Sending to agent: \"${text.take(80)}\"")

        val responseBuilder = StringBuilder()
        try {
            val currentApiKey = apiKeyProvider().takeIf { it.isNotBlank() }
            agentLoop.run(conversationId, text, apiKey = currentApiKey).collect { event ->
                when (event) {
                    is AgentEvent.TextDelta -> responseBuilder.append(event.text)
                    is AgentEvent.MessageComplete -> {
                        _lastResponse.value = event.fullText
                    }
                    is AgentEvent.Error -> {
                        Log.e(TAG, "Agent error: ${event.throwable.message}")
                        _lastResponse.value = "Sorry, something went wrong."
                    }
                    else -> {}
                }
            }

            val response = responseBuilder.toString()
            if (response.isNotEmpty()) {
                Log.i(TAG, "Speaking response (${response.length} chars)")
                _state.value = VoicePipelineState.SPEAKING
                ttsEngine.speak(response)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing transcription", e)
        }

        _state.value = VoicePipelineState.IDLE
    }

    fun release() {
        stopListening()
        speechHelper.release()
        ttsEngine.release()
        scope.cancel()
    }

    companion object {
        private const val TAG = "VoicePipeline"
    }
}
