package com.androidclaw.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.androidclaw.shared.agent.AgentEvent
import com.androidclaw.shared.agent.AgentLoop
import com.androidclaw.shared.memory.ConversationRepository
import com.androidclaw.shared.models.MessageUiModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(
    private val agentLoop: AgentLoop,
    private val conversationRepo: ConversationRepository,
    val conversationId: String
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

    fun sendMessage(text: String) {
        if (text.isBlank() || _isLoading.value) return

        viewModelScope.launch {
            _isLoading.value = true
            _streamingText.value = ""
            _activeToolName.value = null

            try {
                agentLoop.run(conversationId, text).collect { event ->
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
                        }
                        is AgentEvent.Error -> {
                            _streamingText.value = "Error: ${event.throwable.message}"
                        }
                    }
                }
            } catch (e: Exception) {
                _streamingText.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
                _activeToolName.value = null
            }
        }
    }
}
