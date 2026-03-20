package com.androidclaw.app.ui.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToModels: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("androidclaw_settings", Context.MODE_PRIVATE) }

    var serverUrl by remember { mutableStateOf(prefs.getString("server_url", "http://10.0.2.2:8080") ?: "") }
    var selectedModel by remember { mutableStateOf(prefs.getString("model", "claude-sonnet-4-20250514") ?: "") }
    var voiceEnabled by remember { mutableStateOf(prefs.getBoolean("voice_enabled", true)) }
    var alwaysListening by remember { mutableStateOf(prefs.getBoolean("always_listening", false)) }
    var hapticFeedback by remember { mutableStateOf(prefs.getBoolean("haptic_feedback", true)) }

    fun savePrefs() {
        prefs.edit()
            .putString("server_url", serverUrl)
            .putString("model", selectedModel)
            .putBoolean("voice_enabled", voiceEnabled)
            .putBoolean("always_listening", alwaysListening)
            .putBoolean("haptic_feedback", hapticFeedback)
            .apply()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Server section
            item {
                Text(
                    "Connection",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it; savePrefs() },
                    label = { Text("Backend Server URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // Model section
            item {
                Text(
                    "AI Model",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
            }

            item {
                var expanded by remember { mutableStateOf(false) }
                val models = listOf(
                    "claude-sonnet-4-20250514" to "Claude Sonnet 4 (Recommended)",
                    "claude-haiku-4-5-20251001" to "Claude Haiku 4.5 (Faster)",
                    "claude-3-5-haiku-20241022" to "Claude Haiku 3.5 (Legacy)"
                )
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = models.find { it.first == selectedModel }?.second ?: selectedModel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Model") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        models.forEach { (id, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedModel = id
                                    expanded = false
                                    savePrefs()
                                }
                            )
                        }
                    }
                }
            }

            // Speech section
            item {
                Text(
                    "Voice & Speech",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Voice Input", style = MaterialTheme.typography.bodyLarge)
                        Text("Enable microphone for voice chat", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = voiceEnabled, onCheckedChange = { voiceEnabled = it; savePrefs() })
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Always Listening", style = MaterialTheme.typography.bodyLarge)
                        Text("Keep listening in background with wake word", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = alwaysListening, onCheckedChange = { alwaysListening = it; savePrefs() })
                }
            }

            item {
                ListItem(
                    headlineContent = { Text("Speech Recognition Models") },
                    supportingContent = { Text("Download and manage Whisper models") },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onNavigateToModels)
                )
            }

            // General section
            item {
                Text(
                    "General",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Haptic Feedback", style = MaterialTheme.typography.bodyLarge)
                        Text("Vibrate on actions", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = hapticFeedback, onCheckedChange = { hapticFeedback = it; savePrefs() })
                }
            }

            item {
                Spacer(Modifier.height(32.dp))
                Text(
                    "AndroidClaw v0.2.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }
    }
}
