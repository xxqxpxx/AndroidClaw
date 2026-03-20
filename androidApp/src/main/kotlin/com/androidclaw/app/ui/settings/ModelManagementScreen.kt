package com.androidclaw.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.androidclaw.app.models.DownloadState
import com.androidclaw.app.models.ModelDownloader
import com.androidclaw.shared.models.ModelInfo
import com.androidclaw.shared.models.WhisperModels
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagementScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val downloader = remember { ModelDownloader(context) }
    val scope = rememberCoroutineScope()

    var downloadStates by remember { mutableStateOf<Map<String, DownloadState>>(emptyMap()) }
    var downloadedModels by remember { mutableStateOf(downloader.getDownloadedModels().map { it.name }.toSet()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Model Management") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Text(
                    "Whisper Speech Recognition Models",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Download a model to enable on-device speech recognition. Smaller models are faster but less accurate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            items(WhisperModels.ALL) { model ->
                val isDownloaded = model.name in downloadedModels
                val state = downloadStates[model.name] ?: DownloadState.Idle
                val isDefault = model == WhisperModels.DEFAULT

                ModelCard(
                    model = model,
                    isDownloaded = isDownloaded,
                    isDefault = isDefault,
                    downloadState = state,
                    onDownload = {
                        scope.launch {
                            downloader.downloadModel(model).collect { newState ->
                                downloadStates = downloadStates + (model.name to newState)
                                if (newState is DownloadState.Complete) {
                                    downloadedModels = downloadedModels + model.name
                                }
                            }
                        }
                    },
                    onDelete = {
                        downloader.deleteModel(model)
                        downloadedModels = downloadedModels - model.name
                        downloadStates = downloadStates - model.name
                    }
                )
            }

            item {
                val totalUsed = downloader.getTotalStorageUsed()
                Text(
                    "Storage used: ${formatBytes(totalUsed)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: ModelInfo,
    isDownloaded: Boolean,
    isDefault: Boolean,
    downloadState: DownloadState,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(model.name, style = MaterialTheme.typography.titleSmall)
                        if (isDefault) {
                            Spacer(Modifier.width(8.dp))
                            AssistChip(
                                onClick = {},
                                label = { Text("Recommended") },
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                    Text(
                        model.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        formatBytes(model.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                when {
                    isDownloaded -> {
                        Row {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Downloaded",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    downloadState is DownloadState.Downloading -> {
                        // Progress shown below
                    }
                    downloadState is DownloadState.Error -> {
                        TextButton(onClick = onDownload) { Text("Retry") }
                    }
                    else -> {
                        IconButton(onClick = onDownload) {
                            Icon(Icons.Default.Download, contentDescription = "Download")
                        }
                    }
                }
            }

            // Download progress
            if (downloadState is DownloadState.Downloading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { downloadState.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "${formatBytes(downloadState.bytesDownloaded)} / ${formatBytes(downloadState.totalBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (downloadState is DownloadState.Error) {
                Text(
                    downloadState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes.toDouble() / (1024 * 1024))
}
