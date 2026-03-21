package com.androidclaw.app.ui.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.androidclaw.app.voice.VoicePipeline
import com.androidclaw.app.voice.VoicePipelineState
import com.androidclaw.shared.memory.ConversationExporter
import com.androidclaw.shared.models.MessageRole
import com.androidclaw.shared.models.MessageUiModel
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    onBack: () -> Unit
) {
    // In a real app, inject via Koin ViewModelFactory
    val agentLoop = koinInject<com.androidclaw.shared.agent.AgentLoop>()
    val conversationRepo = koinInject<com.androidclaw.shared.memory.ConversationRepository>()
    val settings = koinInject<com.androidclaw.app.settings.SettingsManager>()
    val context = LocalContext.current

    val viewModel = remember {
        ChatViewModel(agentLoop, conversationRepo, conversationId, apiKeyProvider = { settings.apiKey.value })
    }

    val voicePipeline = remember {
        VoicePipeline(context, agentLoop, conversationRepo, apiKeyProvider = { settings.apiKey.value })
    }

    val messages by viewModel.messages.collectAsState()
    val streamingText by viewModel.streamingText.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val activeToolName by viewModel.activeToolName.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val voiceState by voicePipeline.state.collectAsState()
    val lastTranscription by voicePipeline.lastTranscription.collectAsState()
    val isVoiceActive = voiceState != VoicePipelineState.IDLE && voiceState != VoicePipelineState.ERROR

    var inputText by remember { mutableStateOf("") }
    var pendingImage by remember { mutableStateOf<ImageAttachment.ProcessedImage?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            pendingImage = ImageAttachment.processImage(context, it)
        }
    }

    // Mic permission launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voicePipeline.startRecordingManually()
        }
    }

    // When transcription arrives, send it as a message
    LaunchedEffect(lastTranscription) {
        val text = lastTranscription
        if (text.isNotBlank()) {
            viewModel.sendMessage(text)
        }
    }

    // Cleanup voice pipeline on dispose
    DisposableEffect(Unit) {
        onDispose { voicePipeline.release() }
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size, streamingText) {
        if (messages.isNotEmpty() || streamingText.isNotEmpty()) {
            val targetIndex = messages.size + (if (streamingText.isNotEmpty()) 1 else 0) - 1
            if (targetIndex >= 0) {
                listState.animateScrollToItem(targetIndex)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AndroidClaw") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val exporter = ConversationExporter(conversationRepo)
                            val markdown = exporter.exportToMarkdown(conversationId)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, markdown)
                                putExtra(Intent.EXTRA_SUBJECT, "AndroidClaw Conversation")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share conversation"))
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Error banner
            if (errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.retry() }) { Text("Retry") }
                        TextButton(onClick = { viewModel.dismissError() }) { Text("Dismiss") }
                    }
                }
            }

            // Messages list with scroll-to-bottom FAB
            val showScrollButton by remember {
                derivedStateOf { listState.firstVisibleItemIndex < (messages.size - 3).coerceAtLeast(0) && messages.size > 5 }
            }

            Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                // Quick action suggestions when chat is empty
                if (messages.isEmpty() && streamingText.isEmpty()) {
                    item("suggestions") {
                        QuickActionSuggestions { suggestion ->
                            viewModel.sendMessage(suggestion)
                        }
                    }
                }

                items(messages, key = { it.id }) { message ->
                    MessageBubble(message = message)
                }

                // Show streaming message
                if (streamingText.isNotEmpty()) {
                    item("streaming") {
                        MessageBubble(
                            message = MessageUiModel(
                                id = "streaming",
                                role = MessageRole.ASSISTANT,
                                content = streamingText,
                                isStreaming = true,
                                createdAt = Clock.System.now()
                            )
                        )
                    }
                }

                // Show tool call indicator
                if (activeToolName != null) {
                    item("tool_call") {
                        ToolCallIndicator(toolName = activeToolName!!)
                    }
                }

                // Voice state indicator
                if (isVoiceActive) {
                    item("voice_state") {
                        VoiceStateIndicator(state = voiceState)
                    }
                }
            }

            // Scroll-to-bottom FAB
            if (showScrollButton) {
                SmallFloatingActionButton(
                    onClick = {
                        scope.launch { listState.animateScrollToItem(messages.size.coerceAtLeast(1) - 1) }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Scroll to bottom")
                }
            }
            } // End Box

            // Image attachment preview
            if (pendingImage != null) {
                Surface(
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Image attached (${pendingImage!!.width}x${pendingImage!!.height}, ${ImageAttachment.formatSize(pendingImage!!.sizeBytes)})",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { pendingImage = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Input bar
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        enabled = !isLoading,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attach image")
                    }
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Ask anything...") },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                        enabled = !isLoading
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                val imageContext = pendingImage?.let { img ->
                                    "[Image attached: ${img.width}x${img.height}] "
                                } ?: ""
                                viewModel.sendMessage(imageContext + inputText)
                                inputText = ""
                                pendingImage = null
                            }
                        },
                        enabled = inputText.isNotBlank() && !isLoading
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                    IconButton(
                        onClick = {
                            if (isVoiceActive) {
                                voicePipeline.stopListening()
                            } else {
                                val hasMicPermission = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasMicPermission) {
                                    voicePipeline.startRecordingManually()
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        enabled = !isLoading || isVoiceActive
                    ) {
                        val micColor by animateColorAsState(
                            if (isVoiceActive) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface,
                            label = "micColor"
                        )
                        Icon(
                            imageVector = if (isVoiceActive) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isVoiceActive) "Stop recording" else "Voice input",
                            tint = micColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionSuggestions(onSuggestionClick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "How can I help?",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(16.dp))

        val suggestions = listOf(
            "What's the weather like today?" to "web_search",
            "Turn on the flashlight" to "device_settings",
            "Set a timer for 5 minutes" to "alarm_timer",
            "What time is it?" to "datetime",
            "Search for a good recipe" to "web_search",
            "What apps do I have installed?" to "app_launcher"
        )

        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestions.forEach { (text, _) ->
                SuggestionChip(
                    onClick = { onSuggestionClick(text) },
                    label = { Text(text, maxLines = 1) }
                )
            }
        }
    }
}

@Composable
private fun ToolCallIndicator(toolName: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(
            text = when (toolName) {
                "web_search" -> "Searching the web..."
                "device_settings" -> "Adjusting device settings..."
                "app_launcher" -> "Working with apps..."
                "clipboard" -> "Accessing clipboard..."
                "alarm_timer" -> "Setting alarm/timer..."
                "notifications" -> "Checking notifications..."
                "read_webpage" -> "Reading webpage..."
                "calculator" -> "Calculating..."
                "datetime" -> "Getting date/time..."
                "run_code" -> "Running code..."
                else -> "Using $toolName..."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VoiceStateIndicator(state: VoicePipelineState) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(
            text = when (state) {
                VoicePipelineState.LISTENING -> "Listening for wake word..."
                VoicePipelineState.RECORDING -> "Recording... speak now"
                VoicePipelineState.TRANSCRIBING -> "Transcribing speech..."
                VoicePipelineState.THINKING -> "Thinking..."
                VoicePipelineState.SPEAKING -> "Speaking response..."
                else -> ""
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
