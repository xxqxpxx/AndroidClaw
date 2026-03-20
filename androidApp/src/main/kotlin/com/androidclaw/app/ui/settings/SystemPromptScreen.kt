package com.androidclaw.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.androidclaw.app.settings.SettingsManager
import com.androidclaw.shared.llm.ClaudeModels
import org.koin.compose.koinInject

/**
 * Screen for customizing the system prompt / AI persona.
 * Users can write a custom prompt or select from presets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemPromptScreen(onBack: () -> Unit) {
    val settings = koinInject<SettingsManager>()
    var promptText by remember { mutableStateOf(settings.customSystemPrompt.value) }
    var hasChanges by remember { mutableStateOf(false) }

    val presets = remember {
        listOf(
            "Default" to ClaudeModels.DEFAULT_SYSTEM_PROMPT,
            "Concise" to """You are AndroidClaw, a minimalist AI assistant on Android. Be extremely brief. One sentence answers when possible. Use tools proactively. No filler words.""",
            "Friendly" to """You are AndroidClaw, a warm and friendly AI assistant on Android. Be conversational and personable, like chatting with a knowledgeable friend. Use emoji occasionally. Celebrate the user's wins. Be encouraging and supportive while still being helpful and accurate.""",
            "Technical" to """You are AndroidClaw, a technical AI assistant on Android. Provide detailed, precise answers. Include technical details, code examples, and specifications when relevant. Prefer accuracy over brevity. Use proper terminology.""",
            "Creative" to """You are AndroidClaw, a creative AI assistant on Android. Think outside the box. Offer unique perspectives and creative solutions. Use vivid language and analogies. Be imaginative while remaining helpful."""
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Persona") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        promptText = ClaudeModels.DEFAULT_SYSTEM_PROMPT
                        hasChanges = true
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset to default")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                "Presets",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(presets) { (name, prompt) ->
                    FilterChip(
                        selected = promptText == prompt,
                        onClick = {
                            promptText = prompt
                            hasChanges = true
                        },
                        label = { Text(name) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "Custom System Prompt",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = promptText,
                onValueChange = { promptText = it; hasChanges = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = { Text("System prompt") },
                placeholder = { Text("Describe how the AI should behave...") }
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "${promptText.length} characters (~${promptText.length / 4} tokens)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    settings.setCustomSystemPrompt(promptText)
                    hasChanges = false
                },
                enabled = hasChanges,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Changes")
            }
        }
    }
}
