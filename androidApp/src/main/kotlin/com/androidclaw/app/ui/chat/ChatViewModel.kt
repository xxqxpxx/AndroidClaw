package com.androidclaw.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.androidclaw.shared.agent.AgentEvent
import com.androidclaw.shared.agent.AgentLoop
import com.androidclaw.shared.memory.ConversationRepository
import com.androidclaw.shared.models.MessageUiModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(
    private val agentLoop: AgentLoop,
    private val conversationRepo: ConversationRepository,
    val conversationId: String,
    private val apiKeyProvider: () -> String = { "" }
) : ViewModel() {

    val messages: StateFlow<List<MessageUiModel>> = conversationRepo
        .getMessages(conversationId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _activeToolName = MutableStateFlow<String?>(null)
    val activeToolName: StateFlow<String?> = _activeToolName.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var currentJob: Job? = null
    private var lastFailedMessage: String? = null

    companion object {
        private const val MAX_RETRIES = 2
        private val RETRY_DELAYS = listOf(2000L, 4000L)
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _isLoading.value) return
        lastFailedMessage = null
        _errorMessage.value = null
        executeWithRetry(text)
    }

    fun retry() {
        lastFailedMessage?.let { msg ->
            _errorMessage.value = null
            executeWithRetry(msg)
        }
    }

    fun cancelCurrentRequest() {
        currentJob?.cancel()
        _isLoading.value = false
        _streamingText.value = ""
        _activeToolName.value = null
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    private fun executeWithRetry(text: String, attempt: Int = 0) {
        currentJob = viewModelScope.launch {
            _isLoading.value = true
            _streamingText.value = ""
            _activeToolName.value = null

            try {
                val currentApiKey = apiKeyProvider().takeIf { it.isNotBlank() }
                agentLoop.run(conversationId, text, apiKey = currentApiKey).collect { event ->
                    when (event) {
                        is AgentEvent.TextDelta -> {
                            _streamingText.value += event.text
                        }
                        is AgentEvent.ToolCallStart -> {
                            _activeToolName.value = event.toolName
                        }
                        is AgentEvent.ToolCallComplete -> {
                            _activeToolName.value = null
                        }
                        is AgentEvent.MessageComplete -> {
                            _streamingText.value = ""
                            lastFailedMessage = null
                        }
                        is AgentEvent.Error -> {
                            handleError(text, event.throwable, attempt)
                        }
                    }
                }
            } catch (e: CancellationException) {
                // User cancelled - don't retry
                throw e
            } catch (e: Exception) {
                handleError(text, e, attempt)
            } finally {
                _isLoading.value = false
                _activeToolName.value = null
            }
        }
    }

    private suspend fun handleError(text: String, error: Throwable, attempt: Int) {
        val isNetworkError = error.message?.let {
            it.contains("connect", ignoreCase = true) ||
                it.contains("timeout", ignoreCase = true) ||
                it.contains("network", ignoreCase = true) ||
                it.contains("refused", ignoreCase = true)
        } ?: false

        if (isNetworkError && attempt < MAX_RETRIES) {
            _streamingText.value = "Connection error. Retrying in ${RETRY_DELAYS[attempt] / 1000}s..."
            delay(RETRY_DELAYS[attempt])
            _streamingText.value = ""
            executeWithRetry(text, attempt + 1)
        } else {
            lastFailedMessage = text
            val friendlyMessage = when {
                isNetworkError && apiKeyProvider().isBlank() ->
                    "No API key set. Go to Settings and enter your Anthropic API key."
                isNetworkError -> "Unable to connect. Check your internet connection and API key."
                error.message?.contains("401") == true -> "Invalid API key. Check your key in Settings."
                error.message?.contains("429") == true -> "Too many requests. Please wait a moment."
                error.message?.contains("500") == true || error.message?.contains("529") == true ->
                    "Claude is overloaded. Please try again."
                else -> "Something went wrong: ${error.message}"
            }
            _errorMessage.value = friendlyMessage
            _streamingText.value = ""
        }
    }
}
