package com.jeffers.pantrycraft.feature.camera.vision

import android.content.Context
import android.util.Base64
import com.jeffers.pantrycraft.data.ByokPreferences
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

private const val NORMAL_CLOSURE_STATUS = 1000
private const val TIMEOUT_MILLIS = 30_000L
private const val TIMEOUT_SECONDS = 30L
private const val DEFAULT_LIVE_MODEL = "models/gemini-2.0-flash-exp"
private const val LIVE_WS_BASE_URL =
    "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha." +
        "GenerativeService.BidiGenerateContent"

class GeminiLiveVisionClient(
    private val context: Context,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build(),
) : LocalVisionModel {

    override suspend fun analyze(webpBytes: ByteArray, prompt: String): String =
        withContext(Dispatchers.IO) {
            val apiKey = ByokPreferences.loadApiKey(context)
                ?: error("Gemini API Key is required for Live Vision API")
            val base64 = Base64.encodeToString(webpBytes, Base64.NO_WRAP)
            try {
                executeLiveSession(apiKey, base64, prompt)
            } finally {
                // Invariant: Ephemeral media bytes must not persist in memory
                webpBytes.fill(0)
            }
        }

    private suspend fun executeLiveSession(
        apiKey: String,
        base64Image: String,
        prompt: String,
    ): String = withTimeout(TIMEOUT_MILLIS) {
        val configuredModel = ByokPreferences.selectedModel(context)
        val model = normalizeModelName(configuredModel)
        val url = buildWebSocketUrl(apiKey)
        val request = Request.Builder().url(url).build()

        suspendCancellableCoroutine { continuation ->
            val accumulator = StringBuilder()
            val socket = okHttpClient.newWebSocket(
                request,
                createWebSocketListener(base64Image, model, prompt, accumulator) { result ->
                    if (continuation.isActive) {
                        result.fold(
                            onSuccess = { text -> continuation.resume(text) },
                            onFailure = { err -> continuation.resumeWithException(err) },
                        )
                    }
                },
            )
            continuation.invokeOnCancellation {
                socket.close(NORMAL_CLOSURE_STATUS, "Cancelled by coroutine")
            }
        }
    }

    private fun createWebSocketListener(
        base64Image: String,
        model: String,
        prompt: String,
        accumulator: StringBuilder,
        onComplete: (Result<String>) -> Unit,
    ): WebSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send(buildSetupJson(model, prompt))
            webSocket.send(buildRealtimeMediaChunkJson(base64Image))
            webSocket.send(buildClientContentJson("Identify ingredients in image"))
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val (chunk, isTurnComplete) = extractServerContentText(text)
            if (chunk.isNotEmpty()) {
                accumulator.append(chunk)
            }
            if (isTurnComplete) {
                webSocket.close(NORMAL_CLOSURE_STATUS, "Turn complete")
                onComplete(Result.success(accumulator.toString()))
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            onComplete(Result.failure(t))
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (accumulator.isNotEmpty()) {
                onComplete(Result.success(accumulator.toString()))
            }
        }
    }

    companion object {
        fun normalizeModelName(model: String): String =
            when {
                model.startsWith("models/") -> model
                model.isNotBlank() && model != "mlkit-local" -> "models/$model"
                else -> DEFAULT_LIVE_MODEL
            }

        fun buildWebSocketUrl(apiKey: String, baseUrl: String = LIVE_WS_BASE_URL): String =
            "$baseUrl?key=$apiKey"

        fun buildSetupJson(model: String, systemInstruction: String): String {
            val setupObj = JSONObject().apply {
                put("model", model)
                put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("TEXT")))
                put(
                    "systemInstruction",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", systemInstruction)),
                    ),
                )
            }
            return JSONObject().put("setup", setupObj).toString()
        }

        fun buildRealtimeMediaChunkJson(base64Data: String, mimeType: String = "image/webp"): String {
            val chunk = JSONObject().apply {
                put("mimeType", mimeType)
                put("data", base64Data)
            }
            val realtimeInput = JSONObject().put("mediaChunks", JSONArray().put(chunk))
            return JSONObject().put("realtimeInput", realtimeInput).toString()
        }

        fun buildClientContentJson(text: String): String {
            val part = JSONObject().put("text", text)
            val turn = JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(part))
            }
            val clientContent = JSONObject().apply {
                put("turns", JSONArray().put(turn))
                put("turnComplete", true)
            }
            return JSONObject().put("clientContent", clientContent).toString()
        }

        fun extractServerContentText(serverJson: String): Pair<String, Boolean> {
            val root = runCatching { JSONObject(serverJson) }.getOrNull()
            val serverContent = root?.optJSONObject("serverContent") ?: return Pair("", false)
            val isTurnComplete = serverContent.optBoolean("turnComplete", false)
            val parts = serverContent.optJSONObject("modelTurn")?.optJSONArray("parts")

            val sb = StringBuilder()
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    val text = part.optString("text", "")
                    if (text.isNotEmpty()) {
                        sb.append(text)
                    }
                }
            }
            return Pair(sb.toString(), isTurnComplete)
        }
    }
}
