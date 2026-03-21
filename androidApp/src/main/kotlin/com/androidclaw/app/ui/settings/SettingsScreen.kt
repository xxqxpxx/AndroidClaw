package com.androidclaw.app.ui.settings

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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.androidclaw.app.admin.DeviceAdminManager
import com.androidclaw.app.settings.SettingsManager
import com.androidclaw.app.settings.ThemeMode
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToModels: () -> Unit,
    onNavigateToPersona: () -> Unit = {},
    onNavigateToStats: () -> Unit = {}
) {
    val settings = koinInject<SettingsManager>()
    val deviceAdminManager = koinInject<DeviceAdminManager>()
    val context = LocalContext.current

    var serverUrl by remember { mutableStateOf(settings.serverUrl.value) }
    var apiKey by remember { mutableStateOf(settings.apiKey.value) }
    var showApiKey by remember { mutableStateOf(false) }
    var isDeviceAdmin by remember { mutableStateOf(deviceAdminManager.isAdminActive) }
    val selectedModel by settings.model.collectAsState()
    val themeMode by settings.themeMode.collectAsState()
    val dynamicColors by settings.dynamicColors.collectAsState()
    val voiceEnabled by settings.voiceEnabled.collectAsState()
    val alwaysListening by settings.alwaysListening.collectAsState()
    val hapticFeedback by settings.hapticFeedback.collectAsState()

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
            // Connection section
            item { SectionHeader("Connection") }

            item {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; settings.setApiKey(it) },
                    label = { Text("Anthropic API Key") },
                    placeholder = { Text("sk-ant-...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showApiKey = !showApiKey }) {
                            Text(if (showApiKey) "Hide" else "Show")
                        }
                    },
                    supportingText = {
                        if (apiKey.isBlank()) {
                            Text("Required. Get your key from console.anthropic.com")
                        } else {
                            Text("Direct API mode active")
                        }
                    }
                )
            }

            item {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it; settings.setServerUrl(it) },
                    label = { Text("Backend Server URL (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = {
                        Text("Only needed if using a proxy server instead of direct API")
                    }
                )
            }

            // Model section
            item { SectionHeader("AI Model") }

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
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        models.forEach { (id, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { settings.setModel(id); expanded = false }
                            )
                        }
                    }
                }
            }

            item {
                ListItem(
                    headlineContent = { Text("AI Persona") },
                    supportingContent = { Text("Customize the AI's personality and behavior") },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onNavigateToPersona)
                )
            }

            // Appearance section
            item { SectionHeader("Appearance") }

            item {
                var expanded by remember { mutableStateOf(false) }
                val themes = listOf(
                    ThemeMode.SYSTEM to "System Default",
                    ThemeMode.LIGHT to "Light",
                    ThemeMode.DARK to "Dark"
                )
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = themes.find { it.first == themeMode }?.second ?: "System Default",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Theme") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        themes.forEach { (mode, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { settings.setThemeMode(mode); expanded = false }
                            )
                        }
                    }
                }
            }

            item {
                SettingsToggle(
                    title = "Dynamic Colors",
                    subtitle = "Use Material You colors from your wallpaper (Android 12+)",
                    checked = dynamicColors,
                    onCheckedChange = { settings.setDynamicColors(it) }
                )
            }

            // Voice section
            item { SectionHeader("Voice & Speech") }

            item {
                SettingsToggle(
                    title = "Voice Input",
                    subtitle = "Enable microphone for voice chat",
                    checked = voiceEnabled,
                    onCheckedChange = { settings.setVoiceEnabled(it) }
                )
            }

            item {
                SettingsToggle(
                    title = "Always Listening",
                    subtitle = "Keep listening in background with wake word",
                    checked = alwaysListening,
                    onCheckedChange = { settings.setAlwaysListening(it) }
                )
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
            item { SectionHeader("General") }

            item {
                SettingsToggle(
                    title = "Haptic Feedback",
                    subtitle = "Vibrate on actions",
                    checked = hapticFeedback,
                    onCheckedChange = { settings.setHapticFeedback(it) }
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Usage Stats") },
                    supportingContent = { Text("View conversation and tool usage statistics") },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onNavigateToStats)
                )
            }

            // Device Admin section
            item { SectionHeader("Device Control") }

            item {
                ListItem(
                    headlineContent = { Text("Device Admin") },
                    supportingContent = {
                        Text(
                            if (isDeviceAdmin) "Active - AndroidClaw can lock screen, manage security"
                            else "Enable to allow screen lock, security management"
                        )
                    },
                    trailingContent = {
                        if (isDeviceAdmin) {
                            Text("Active", color = MaterialTheme.colorScheme.primary)
                        } else {
                            Button(onClick = {
                                context.startActivity(deviceAdminManager.requestAdminActivation())
                            }) {
                                Text("Enable")
                            }
                        }
                    }
                )
            }

            item {
                Spacer(Modifier.height(32.dp))
                Text(
                    "AndroidClaw v0.5.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
