package com.androidclaw.app.llm

import android.content.Context
import android.util.Log
import com.androidclaw.shared.llm.ClaudeRequest
import com.androidclaw.shared.llm.ClaudeStreamEvent
import com.androidclaw.shared.llm.ContentBlock
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device LLM inference using MediaPipe LLM Inference API.
 * Runs models like Gemma 2B directly on the phone — fully offline, privacy-first.
 *
 * Supported models (download from HuggingFace):
 * - gemma-2b-it-gpu-int4.bin (~1.4GB)
 * - phi-2-cpu-int4.bin (~1.3GB)
 * - stablelm-3b-4e1t-cpu-int4.bin (~1.6GB)
 */
class OnDeviceLlmEngine(private val context: Context) {

    private var llmInference: LlmInference? = null
    private var currentModelPath: String? = null

    companion object {
        private const val TAG = "OnDeviceLLM"

        val AVAILABLE_MODELS = listOf(
            OnDeviceModel("gemma-2b-it-gpu", "Gemma 2B (GPU)", "gemma-2b-it-gpu-int4.bin", 1_400_000_000L,
                "https://huggingface.co/mediapipe/gemma-2b-it-gpu-int4/resolve/main/gemma-2b-it-gpu-int4.bin"),
            OnDeviceModel("gemma-2b-it-cpu", "Gemma 2B (CPU)", "gemma-2b-it-cpu-int4.bin", 1_400_000_000L,
                "https://huggingface.co/mediapipe/gemma-2b-it-cpu-int4/resolve/main/gemma-2b-it-cpu-int4.bin"),
        )

        fun getModelDir(context: Context): File {
            val dir = File(context.filesDir, "models/llm")
            dir.mkdirs()
            return dir
        }
    }

    fun isModelDownloaded(modelId: String): Boolean {
        val model = AVAILABLE_MODELS.find { it.id == modelId } ?: return false
        val file = File(getModelDir(context), model.fileName)
        return file.exists() && file.length() > 0
    }

    fun getModelFile(modelId: String): File? {
        val model = AVAILABLE_MODELS.find { it.id == modelId } ?: return null
        val file = File(getModelDir(context), model.fileName)
        return if (file.exists()) file else null
    }

    suspend fun initialize(modelId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelFile = getModelFile(modelId)
            if (modelFile == null || !modelFile.exists()) {
                Log.e(TAG, "Model file not found for $modelId")
                return@withContext false
            }

            if (currentModelPath == modelFile.absolutePath && llmInference != null) {
                Log.d(TAG, "Model already loaded")
                return@withContext true
            }

            // Release previous model
            release()

            Log.i(TAG, "Loading model: ${modelFile.absolutePath} (${modelFile.length() / 1_000_000}MB)")

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(2048)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            currentModelPath = modelFile.absolutePath
            Log.i(TAG, "Model loaded successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model", e)
            false
        }
    }

    fun streamMessage(request: ClaudeRequest): Flow<ClaudeStreamEvent> = channelFlow {
        val inference = llmInference
        if (inference == null) {
            send(ClaudeStreamEvent.Error("On-device model not loaded. Please download a model in Settings."))
            return@channelFlow
        }

        try {
            // Build prompt from messages (convert Claude format to chat format)
            val prompt = buildPrompt(request)
            Log.i(TAG, "Generating response, prompt length=${prompt.length}")

            send(ClaudeStreamEvent.MessageStart("on-device", currentModelPath ?: "local"))
            send(ClaudeStreamEvent.ContentBlockStart(0, "text"))

            // Use synchronous inference (streaming not reliably available in all MediaPipe versions)
            val fullResponse = withContext(Dispatchers.IO) {
                inference.generateResponse(prompt)
            }

            if (fullResponse.isNotEmpty()) {
                // Send in chunks to simulate streaming
                val chunkSize = 20
                var i = 0
                while (i < fullResponse.length) {
                    val end = minOf(i + chunkSize, fullResponse.length)
                    send(ClaudeStreamEvent.TextDelta(fullResponse.substring(i, end)))
                    i = end
                }
            }

            send(ClaudeStreamEvent.ContentBlockStop(0))
            send(ClaudeStreamEvent.MessageDelta("end_turn"))
            send(ClaudeStreamEvent.MessageStop)

            Log.i(TAG, "Response complete, length=${fullResponse.length}")
        } catch (e: Exception) {
            Log.e(TAG, "Inference error", e)
            send(ClaudeStreamEvent.Error("On-device inference failed: ${e.message}"))
        }
    }

    private fun buildPrompt(request: ClaudeRequest): String {
        val sb = StringBuilder()

        // System prompt
        if (!request.system.isNullOrBlank()) {
            sb.appendLine("<start_of_turn>user")
            sb.appendLine("System instructions: ${request.system}")
            sb.appendLine("<end_of_turn>")
        }

        // Tool descriptions (inject into context for models without native function calling)
        if (!request.tools.isNullOrEmpty()) {
            sb.appendLine("<start_of_turn>user")
            sb.appendLine("You have tools available. To use one, respond ONLY with JSON: {\"tool\": \"name\", \"input\": {\"param\": \"value\"}}")
            sb.appendLine("Available tools:")
            for (tool in request.tools!!) {
                sb.appendLine("- ${tool.name}: ${tool.description}")
            }
            sb.appendLine("<end_of_turn>")
        }

        // Messages
        for (msg in request.messages) {
            val role = if (msg.role == "user") "user" else "model"
            sb.appendLine("<start_of_turn>$role")
            for (block in msg.content) {
                when (block) {
                    is ContentBlock.Text -> sb.appendLine(block.text)
                    is ContentBlock.ToolUse -> sb.appendLine("Using tool ${block.name}: ${block.input}")
                    is ContentBlock.ToolResult -> sb.appendLine("Tool result: ${block.content}")
                }
            }
            sb.appendLine("<end_of_turn>")
        }

        sb.appendLine("<start_of_turn>model")
        return sb.toString()
    }

    fun release() {
        try {
            llmInference?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing model", e)
        }
        llmInference = null
        currentModelPath = null
    }
}

data class OnDeviceModel(
    val id: String,
    val displayName: String,
    val fileName: String,
    val sizeBytes: Long,
    val downloadUrl: String
) {
    val sizeMb: Long get() = sizeBytes / 1_000_000
}
