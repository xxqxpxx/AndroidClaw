package com.androidclaw.app.models

import android.content.Context
import com.androidclaw.shared.models.ModelInfo
import com.androidclaw.shared.models.WhisperModels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val progress: Float, val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
    data object Complete : DownloadState()
    data class Error(val message: String) : DownloadState()
}

class ModelDownloader(private val context: Context) {

    private val modelsDir: File
        get() = File(context.filesDir, "models").also { it.mkdirs() }

    fun getModelFile(model: ModelInfo): File = File(modelsDir, model.fileName)

    fun isModelDownloaded(model: ModelInfo): Boolean = getModelFile(model).let {
        it.exists() && it.length() > 0
    }

    fun getDownloadedModels(): List<ModelInfo> =
        WhisperModels.ALL.filter { isModelDownloaded(it) }

    fun downloadModel(model: ModelInfo): Flow<DownloadState> = flow {
        emit(DownloadState.Downloading(0f, 0L, model.sizeBytes))

        val targetFile = getModelFile(model)
        val tempFile = File(modelsDir, "${model.fileName}.tmp")

        try {
            val url = URL(model.url)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000

            // Support resume
            var downloadedSoFar = 0L
            if (tempFile.exists()) {
                downloadedSoFar = tempFile.length()
                connection.setRequestProperty("Range", "bytes=$downloadedSoFar-")
            }

            connection.connect()

            val responseCode = connection.responseCode
            val totalBytes = if (responseCode == 206) {
                // Partial content - resuming
                downloadedSoFar + connection.contentLength
            } else {
                // Fresh download
                downloadedSoFar = 0
                connection.contentLength.toLong()
            }

            val inputStream = connection.inputStream
            val outputStream = if (downloadedSoFar > 0 && responseCode == 206) {
                tempFile.outputStream().apply { channel.position(downloadedSoFar) }
            } else {
                tempFile.outputStream()
            }

            outputStream.use { out ->
                inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = downloadedSoFar

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        val progress = if (totalBytes > 0) totalRead.toFloat() / totalBytes else 0f
                        emit(DownloadState.Downloading(progress, totalRead, totalBytes))
                    }
                }
            }

            // Rename temp to final
            tempFile.renameTo(targetFile)
            emit(DownloadState.Complete)

        } catch (e: Exception) {
            // Don't delete temp file - allows resume
            emit(DownloadState.Error("Download failed: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    fun deleteModel(model: ModelInfo): Boolean {
        val file = getModelFile(model)
        val temp = File(modelsDir, "${model.fileName}.tmp")
        temp.delete()
        return file.delete()
    }

    fun getTotalStorageUsed(): Long =
        modelsDir.listFiles()?.sumOf { it.length() } ?: 0L
}
