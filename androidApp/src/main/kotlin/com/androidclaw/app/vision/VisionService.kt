package com.androidclaw.app.vision

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * Vision-based fallback for UI interaction. When the accessibility tree
 * can't locate a target element, this service:
 * 1. Captures a screenshot via AccessibilityService.takeScreenshot()
 * 2. Sends it to Claude with a prompt asking for element coordinates
 * 3. Returns tap coordinates for the requested element
 *
 * Inspired by droidclaw's perception → reasoning → action loop.
 */
class VisionService(
    private val apiKey: String
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val executor: Executor = Executors.newSingleThreadExecutor()

    companion object {
        private const val TAG = "VisionService"
        private const val ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages"
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private const val MODEL = "claude-sonnet-4-20250514"
        private const val MAX_SCREENSHOT_WIDTH = 1080
    }

    /**
     * Captures a screenshot from the accessibility service.
     * Requires Android 11+ (API 30).
     */
    suspend fun captureScreen(service: AccessibilityService): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(TAG, "Screenshot capture requires Android 11+")
            return null
        }
        return suspendCancellableCoroutine { cont ->
            service.takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                executor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        val hardwareBuffer = result.hardwareBuffer
                        val colorSpace = result.colorSpace
                        val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                        hardwareBuffer.close()
                        cont.resume(bitmap)
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.e(TAG, "Screenshot failed with error code: $errorCode")
                        cont.resume(null)
                    }
                }
            )
        }
    }

    /**
     * Encodes a bitmap to base64 JPEG (smaller than PNG for network transfer), scaled down if needed.
     */
    fun encodeScreenshot(bitmap: Bitmap): String {
        val scaled = if (bitmap.width > MAX_SCREENSHOT_WIDTH) {
            val ratio = MAX_SCREENSHOT_WIDTH.toFloat() / bitmap.width
            Bitmap.createScaledBitmap(
                bitmap,
                MAX_SCREENSHOT_WIDTH,
                (bitmap.height * ratio).toInt(),
                true
            )
        } else {
            bitmap
        }

        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        if (scaled !== bitmap) scaled.recycle()
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Makes a POST request to the Anthropic API using HttpURLConnection.
     */
    private suspend fun postToAnthropic(requestBody: JsonObject): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL(ANTHROPIC_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("x-api-key", apiKey)
            connection.setRequestProperty("anthropic-version", ANTHROPIC_VERSION)
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 60000

            connection.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                Log.e(TAG, "Vision API failed with HTTP $responseCode")
                return@withContext null
            }

            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Vision API request failed: ${e.message}", e)
            null
        }
    }

    /**
     * Asks Claude to locate a UI element on the screenshot and return tap coordinates.
     */
    suspend fun findElementCoordinates(
        screenshotBase64: String,
        targetDescription: String,
        screenWidth: Int,
        screenHeight: Int
    ): TapTarget? {
        val prompt = buildString {
            append("You are analyzing a mobile phone screenshot (${screenWidth}x${screenHeight} pixels).\n")
            append("Find the UI element matching: \"$targetDescription\"\n\n")
            append("Respond with ONLY a JSON object, no markdown:\n")
            append("If found: {\"found\":true,\"x\":<center_x>,\"y\":<center_y>,\"confidence\":<0.0-1.0>,\"label\":\"<text on element>\"}\n")
            append("If not found: {\"found\":false,\"reason\":\"<why>\"}\n")
            append("Coordinates are absolute pixels in the ${screenWidth}x${screenHeight} space.")
        }

        val requestBody = buildJsonObject {
            put("model", MODEL)
            put("max_tokens", 200)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "image")
                            putJsonObject("source") {
                                put("type", "base64")
                                put("media_type", "image/jpeg")
                                put("data", screenshotBase64)
                            }
                        }
                        addJsonObject {
                            put("type", "text")
                            put("text", prompt)
                        }
                    }
                }
            }
        }

        return try {
            val responseBody = postToAnthropic(requestBody) ?: return null
            val responseJson = json.parseToJsonElement(responseBody).jsonObject
            val content = responseJson["content"]?.jsonArray?.firstOrNull()?.jsonObject
            val text = content?.get("text")?.jsonPrimitive?.contentOrNull ?: return null

            // Strip markdown code fences if present
            val cleaned = text.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()

            val result = json.parseToJsonElement(cleaned).jsonObject
            val found = result["found"]?.jsonPrimitive?.booleanOrNull ?: false

            if (found) {
                val x = result["x"]?.jsonPrimitive?.intOrNull ?: return null
                val y = result["y"]?.jsonPrimitive?.intOrNull ?: return null
                val confidence = result["confidence"]?.jsonPrimitive?.floatOrNull ?: 0.5f
                val label = result["label"]?.jsonPrimitive?.contentOrNull ?: ""
                Log.i(TAG, "Found element at ($x, $y) conf=$confidence: $label")
                TapTarget(x, y, confidence, label)
            } else {
                val reason = result["reason"]?.jsonPrimitive?.contentOrNull ?: "unknown"
                Log.i(TAG, "Element not found: $reason")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vision analysis failed: ${e.message}", e)
            null
        }
    }

    /**
     * Describes the current screen for the agent's situational awareness.
     */
    suspend fun describeScreen(screenshotBase64: String): String? {
        val prompt = "Briefly describe this Android screen: which app, main content visible, and interactive elements (buttons/fields). 2-3 sentences max."

        val requestBody = buildJsonObject {
            put("model", MODEL)
            put("max_tokens", 300)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "image")
                            putJsonObject("source") {
                                put("type", "base64")
                                put("media_type", "image/jpeg")
                                put("data", screenshotBase64)
                            }
                        }
                        addJsonObject {
                            put("type", "text")
                            put("text", prompt)
                        }
                    }
                }
            }
        }

        return try {
            val responseBody = postToAnthropic(requestBody) ?: return null
            val responseJson = json.parseToJsonElement(responseBody).jsonObject
            responseJson["content"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull
        } catch (e: Exception) {
            Log.e(TAG, "Screen describe failed: ${e.message}", e)
            null
        }
    }
}

data class TapTarget(
    val x: Int,
    val y: Int,
    val confidence: Float,
    val label: String
)
