package com.androidclaw.app.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * Handles image attachments for chat messages.
 * Converts images to base64 for inclusion in Claude API requests.
 */
object ImageAttachment {

    data class ProcessedImage(
        val base64Data: String,
        val mimeType: String,
        val width: Int,
        val height: Int,
        val sizeBytes: Int
    )

    private const val MAX_DIMENSION = 1024
    private const val JPEG_QUALITY = 85

    /**
     * Process an image URI into a base64-encoded string suitable for the Claude API.
     * Resizes large images to fit within MAX_DIMENSION.
     */
    fun processImage(context: Context, uri: Uri): ProcessedImage? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            val resized = resizeIfNeeded(bitmap)
            val outputStream = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            val bytes = outputStream.toByteArray()
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

            ProcessedImage(
                base64Data = base64,
                mimeType = "image/jpeg",
                width = resized.width,
                height = resized.height,
                sizeBytes = bytes.size
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Process a camera capture bitmap.
     */
    fun processBitmap(bitmap: Bitmap): ProcessedImage? {
        return try {
            val resized = resizeIfNeeded(bitmap)
            val outputStream = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            val bytes = outputStream.toByteArray()
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

            ProcessedImage(
                base64Data = base64,
                mimeType = "image/jpeg",
                width = resized.width,
                height = resized.height,
                sizeBytes = bytes.size
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun resizeIfNeeded(bitmap: Bitmap): Bitmap {
        if (bitmap.width <= MAX_DIMENSION && bitmap.height <= MAX_DIMENSION) return bitmap

        val scale = MAX_DIMENSION.toFloat() / maxOf(bitmap.width, bitmap.height)
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    fun formatSize(bytes: Int): String = when {
        bytes >= 1_000_000 -> "${bytes / 1_000_000}.${(bytes % 1_000_000) / 100_000}MB"
        bytes >= 1_000 -> "${bytes / 1_000}KB"
        else -> "${bytes}B"
    }
}
