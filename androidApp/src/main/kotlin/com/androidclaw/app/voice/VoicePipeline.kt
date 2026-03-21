package com.androidclaw.app.voice

import android.content.Context
import com.androidclaw.shared.agent.AgentEvent
import com.androidclaw.shared.agent.AgentLoop
import com.androidclaw.shared.memory.ConversationRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class VoicePipelineState {
    IDLE,
    LISTENING,     // Wake word detection active
    RECORDING,     // User speaking, VAD active
    TRANSCRIBING,  // whisper.cpp running
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

    private val audioRecorder = AudioRecorder(context)
    private val vadDetector = VadDetector()
    private val whisperTranscriber = WhisperTranscriber(context)
    private val ttsEngine = TextToSpeechEngine(context)
    private val wakeWordDetector = WakeWordDetector(context) { onWakeWordDetected() }

    private var pipelineJob: Job? = null
    private var activeConversationId: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    suspend fun initialize(): Boolean {
        val whisperReady = whisperTranscriber.initialize()
        ttsEngine.initialize {}
        return whisperReady
    }

    fun startListening(conversationId: String? = null) {
        activeConversationId = conversationId
        _state.value = VoicePipelineState.LISTENING
        wakeWordDetector.start()
    }

    fun stopListening() {
        pipelineJob?.cancel()
        wakeWordDetector.stop()
        audioRecorder.stop()
        ttsEngine.stop()
        vadDetector.reset()
        _state.value = VoicePipelineState.IDLE
    }

    fun startRecordingManually() {
        onWakeWordDetected()
    }

    private fun onWakeWordDetected() {
        _state.value = VoicePipelineState.RECORDING
        vadDetector.reset()

        pipelineJob = scope.launch {
            val audioBuffer = mutableListOf<ShortArray>()

            audioRecorder.recordAudioFrames().collect { frame ->
                val event = vadDetector.processFrame(frame)
                when (event) {
                    is VadEvent.SpeechStart -> {
                        audioBuffer.clear()
                        audioBuffer.add(frame)
                    }
                    is VadEvent.AudioFrame -> {
                        audioBuffer.add(event.samples)
                    }
                    is VadEvent.SpeechEnd -> {
                        audioRecorder.stop()
                        processRecordedAudio(audioBuffer)
                        return@collect
                    }
                    null -> {}
                }
            }
        }
    }

    private suspend fun processRecordedAudio(audioBuffer: List<ShortArray>) {
        _state.value = VoicePipelineState.TRANSCRIBING

        // Combine and convert audio
        val totalSamples = audioBuffer.sumOf { it.size }
        val combined = ShortArray(totalSamples)
        var offset = 0
        for (chunk in audioBuffer) {
            chunk.copyInto(combined, offset)
            offset += chunk.size
        }
        val floatSamples = AudioRecorder.shortsToFloats(combined)

        // Transcribe
        val text = whisperTranscriber.transcribe(floatSamples).trim()
        _lastTranscription.value = text

        if (text.isEmpty()) {
            _state.value = VoicePipelineState.LISTENING
            wakeWordDetector.start()
            return
        }

        // Get or create conversation
        val conversationId = activeConversationId
            ?: conversationRepo.createConversation().also { activeConversationId = it }

        // Send to agent
        _state.value = VoicePipelineState.THINKING
        val responseBuilder = StringBuilder()

        val currentApiKey = apiKeyProvider().takeIf { it.isNotBlank() }
        agentLoop.run(conversationId, text, apiKey = currentApiKey).collect { event ->
            when (event) {
                is AgentEvent.TextDelta -> responseBuilder.append(event.text)
                is AgentEvent.MessageComplete -> {
                    _lastResponse.value = event.fullText
                }
                is AgentEvent.Error -> {
                    _lastResponse.value = "Sorry, something went wrong."
                }
                else -> {}
            }
        }

        // Speak response
        val response = responseBuilder.toString()
        if (response.isNotEmpty()) {
            _state.value = VoicePipelineState.SPEAKING
            ttsEngine.speak(response)
        }

        // Return to listening
        _state.value = VoicePipelineState.LISTENING
        wakeWordDetector.start()
    }

    fun release() {
        stopListening()
        whisperTranscriber.release()
        ttsEngine.release()
        scope.cancel()
    }
}
