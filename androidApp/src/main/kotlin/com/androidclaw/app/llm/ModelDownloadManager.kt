package com.androidclaw.app.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val progress: Float, val downloadedMb: Long, val totalMb: Long) : DownloadState()
    data object Complete : DownloadState()
    data class Failed(val error: String) : DownloadState()
}

class ModelDownloadManager(private val context: Context) {

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val modelDir = OnDeviceLlmEngine.getModelDir(context)

    fun isModelDownloaded(modelId: String): Boolean {
        val model = OnDeviceLlmEngine.AVAILABLE_MODELS.find { it.id == modelId } ?: return false
        val file = File(modelDir, model.fileName)
        return file.exists() && file.length() > model.sizeBytes * 0.9 // Allow 10% tolerance
    }

    fun getDownloadedModels(): List<OnDeviceModel> {
        return OnDeviceLlmEngine.AVAILABLE_MODELS.filter { isModelDownloaded(it.id) }
    }

    fun getModelSizeOnDisk(modelId: String): Long {
        val model = OnDeviceLlmEngine.AVAILABLE_MODELS.find { it.id == modelId } ?: return 0
        val file = File(modelDir, model.fileName)
        return if (file.exists()) file.length() else 0
    }

    suspend fun downloadModel(modelId: String): Boolean = withContext(Dispatchers.IO) {
        val model = OnDeviceLlmEngine.AVAILABLE_MODELS.find { it.id == modelId }
        if (model == null) {
            _downloadState.value = DownloadState.Failed("Unknown model: $modelId")
            return@withContext false
        }

        val targetFile = File(modelDir, model.fileName)
        val tempFile = File(modelDir, "${model.fileName}.tmp")

        try {
            Log.i(TAG, "Starting download: ${model.displayName} from ${model.downloadUrl}")
            _downloadState.value = DownloadState.Downloading(0f, 0, model.sizeMb)

            val url = URL(model.downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            // Support resume
            if (tempFile.exists()) {
                connection.setRequestProperty("Range", "bytes=${tempFile.length()}-")
            }

            connection.connect()

            val totalSize = if (tempFile.exists() && connection.responseCode == 206) {
                connection.contentLength + tempFile.length()
            } else {
                connection.contentLength.toLong()
            }

            val append = connection.responseCode == 206
            val inputStream = connection.inputStream
            val outputStream = tempFile.outputStream().let {
                if (append) java.io.FileOutputStream(tempFile, true) else it
            }

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalRead = if (append) tempFile.length() else 0L

            inputStream.use { input ->
                outputStream.use { output ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        val progress = if (totalSize > 0) totalRead.toFloat() / totalSize else 0f
                        _downloadState.value = DownloadState.Downloading(
                            progress,
                            totalRead / 1_000_000,
                            totalSize / 1_000_000
                        )
                    }
                }
            }

            // Rename temp to final
            tempFile.renameTo(targetFile)
            Log.i(TAG, "Download complete: ${targetFile.absolutePath} (${targetFile.length() / 1_000_000}MB)")
            _downloadState.value = DownloadState.Complete
            true
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            _downloadState.value = DownloadState.Failed("Download failed: ${e.message}")
            false
        }
    }

    fun deleteModel(modelId: String): Boolean {
        val model = OnDeviceLlmEngine.AVAILABLE_MODELS.find { it.id == modelId } ?: return false
        val file = File(modelDir, model.fileName)
        val tempFile = File(modelDir, "${model.fileName}.tmp")
        tempFile.delete()
        return file.delete().also {
            if (it) Log.i(TAG, "Deleted model: ${model.displayName}")
        }
    }

    fun cancelDownload() {
        _downloadState.value = DownloadState.Idle
        // Temp files are kept for resume support
    }

    companion object {
        private const val TAG = "ModelDownload"
    }
}
