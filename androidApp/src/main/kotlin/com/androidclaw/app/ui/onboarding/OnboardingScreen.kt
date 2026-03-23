package com.androidclaw.app.ui.onboarding

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.androidclaw.app.settings.SettingsManager
import com.androidclaw.app.ui.components.CatMascot
import com.androidclaw.app.ui.components.CatState
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val settings = koinInject<SettingsManager>()
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 4 })

    var selectedProvider by remember { mutableStateOf("claude") }
    var apiKey by remember { mutableStateOf(settings.apiKey.value) }
    var localServerUrl by remember { mutableStateOf(settings.localLlmUrl.value) }
    var localServerModel by remember { mutableStateOf(settings.localLlmModel.value) }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LinearProgressIndicator(
                progress = { (pagerState.currentPage + 1) / 4f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> WelcomePage()
                    1 -> ProviderPage(
                        selectedProvider = selectedProvider,
                        onProviderSelected = { selectedProvider = it }
                    )
                    2 -> ConfigPage(
                        provider = selectedProvider,
                        apiKey = apiKey,
                        onApiKeyChange = { apiKey = it },
                        localServerUrl = localServerUrl,
                        onLocalServerUrlChange = { localServerUrl = it },
                        localServerModel = localServerModel,
                        onLocalServerModelChange = { localServerModel = it }
                    )
                    3 -> FeaturesPage()
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (pagerState.currentPage > 0) {
                    OutlinedButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }
                    ) { Text("Back") }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                if (pagerState.currentPage < 3) {
                    Button(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }
                    ) { Text("Next") }
                } else {
                    Button(
                        onClick = {
                            settings.setLlmProvider(selectedProvider)
                            when (selectedProvider) {
                                "claude" -> settings.setApiKey(apiKey)
                                "on_device" -> { /* model download handled in settings later */ }
                                "custom_server" -> {
                                    settings.setLocalLlmUrl(localServerUrl)
                                    settings.setLocalLlmModel(localServerModel)
                                }
                            }
                            settings.setOnboardingCompleted(true)
                            onComplete()
                        }
                    ) { Text("Get Started") }
                }
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CatMascot(state = CatState.JUMPING, size = 96)
        Spacer(Modifier.height(24.dp))
        Text(
            "Welcome to AndroidClaw",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Your AI assistant that fully controls your phone.\nA smarter replacement for Google Assistant.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderPage(
    selectedProvider: String,
    onProviderSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Choose Your AI",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "You can change this anytime in Settings",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        ProviderCard(
            title = "Claude (Cloud)",
            subtitle = "Most powerful. 115+ device actions, tool calling, web search. Requires API key.",
            icon = Icons.Default.Cloud,
            selected = selectedProvider == "claude",
            onClick = { onProviderSelected("claude") }
        )

        Spacer(Modifier.height(12.dp))

        ProviderCard(
            title = "On-Device (Private)",
            subtitle = "Runs locally on your phone. No internet needed. Privacy-first. Download ~1.5GB model.",
            icon = Icons.Default.PhoneAndroid,
            selected = selectedProvider == "on_device",
            onClick = { onProviderSelected("on_device") }
        )

        Spacer(Modifier.height(12.dp))

        ProviderCard(
            title = "Custom Server (Pro)",
            subtitle = "Connect to Ollama, LM Studio, or any OpenAI-compatible server on your network.",
            icon = Icons.Default.Dns,
            selected = selectedProvider == "custom_server",
            onClick = { onProviderSelected("custom_server") }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (selected) CardDefaults.outlinedCardBorder() else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ConfigPage(
    provider: String,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    localServerUrl: String,
    onLocalServerUrlChange: (String) -> Unit,
    localServerModel: String,
    onLocalServerModelChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (provider) {
            "claude" -> {
                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Enter Your API Key", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text("Get yours at console.anthropic.com",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))

                var showKey by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text("Anthropic API Key") },
                    placeholder = { Text("sk-ant-...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showKey = !showKey }) {
                            Text(if (showKey) "Hide" else "Show")
                        }
                    }
                )
            }
            "on_device" -> {
                Icon(Icons.Default.DownloadForOffline, contentDescription = null, modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("On-Device AI", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text("A small AI model (~1.5 GB) will be downloaded to your phone. After that, everything runs offline.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Gemma 2B", style = MaterialTheme.typography.titleSmall)
                        Text("Google's compact AI model, optimized for mobile",
                            style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        Text("Size: ~1.4 GB | RAM: 4GB+",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline)
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("You can download the model after setup in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline)
            }
            "custom_server" -> {
                Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Custom Server", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text("Connect to Ollama, LM Studio, or any OpenAI-compatible API running on your network.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))

                OutlinedTextField(
                    value = localServerUrl,
                    onValueChange = onLocalServerUrlChange,
                    label = { Text("Server URL") },
                    placeholder = { Text("http://192.168.1.100:11434") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text("Ollama default: port 11434 | LM Studio: port 1234") }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = localServerModel,
                    onValueChange = onLocalServerModelChange,
                    label = { Text("Model Name") },
                    placeholder = { Text("llama3.2") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text("e.g. llama3.2, mistral, gemma2, phi3") }
                )
            }
        }
    }
}

@Composable
private fun FeaturesPage() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CatMascot(state = CatState.IDLE, size = 80)
        Spacer(Modifier.height(16.dp))
        Text("Ready to Go!", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))

        val features = listOf(
            "Send messages on WhatsApp, Telegram, SMS",
            "Control Wi-Fi, Bluetooth, flashlight, volume",
            "Make calls, manage contacts & calendar",
            "Play music on Spotify, search YouTube",
            "Navigate with Google Maps, send emails",
            "Take screenshots, translate, scan QR codes",
            "Set alarms, timers, reminders",
            "115+ device actions - replaces Google Assistant"
        )
        features.forEach { feature ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("\u2713", color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(12.dp))
                Text(feature, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
