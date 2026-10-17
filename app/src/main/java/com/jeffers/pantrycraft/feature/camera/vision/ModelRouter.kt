package com.jeffers.pantrycraft.feature.camera.vision

import android.content.Context
import android.util.Base64
import com.jeffers.pantrycraft.data.AiProvider
import com.jeffers.pantrycraft.data.ByokPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ModelRouter(
    private val context: Context,
    private val localFallbackModel: LocalVisionModel,
    private val cloudModel: LocalVisionModel = ByokCloudVisionModel(context.applicationContext),
) : LocalVisionModel,
    AutoCloseable {
    override suspend fun analyze(
        webpBytes: ByteArray,
        prompt: String,
    ): String {
        val provider = ByokPreferences.selectedProvider(context)
        val useFallback = provider == AiProvider.OFFLINE_MLKIT ||
            (provider.requiresApiKey && !ByokPreferences.hasApiKey(context))
        return if (useFallback) {
            localFallbackModel.analyze(webpBytes, prompt)
        } else {
            runCatching { cloudModel.analyze(webpBytes, prompt) }
                .getOrElse { localFallbackModel.analyze(webpBytes, prompt) }
        }
    }

    override fun close() {
        (localFallbackModel as? AutoCloseable)?.close()
        (cloudModel as? AutoCloseable)?.close()
    }
}

class ByokCloudVisionModel(
    private val context: Context,
) : LocalVisionModel {
    override suspend fun analyze(
        webpBytes: ByteArray,
        prompt: String,
    ): String =
        withContext(Dispatchers.IO) {
            val provider = ByokPreferences.selectedProvider(context)
            val model = ByokPreferences.selectedModel(context)
            val endpoint = ByokPreferences.selectedEndpoint(context)
            val apiKey = ByokPreferences.loadApiKey(context)
            val base64Image = Base64.encodeToString(webpBytes, Base64.NO_WRAP)

            when (provider) {
                AiProvider.GEMINI -> callGemini(endpoint, model, apiKey.orEmpty(), base64Image, prompt)
                AiProvider.OPENAI -> callOpenAi(endpoint, model, apiKey.orEmpty(), base64Image, prompt)
                AiProvider.ANTHROPIC -> callAnthropic(endpoint, model, apiKey.orEmpty(), base64Image, prompt)
                AiProvider.OLLAMA -> callOllama(endpoint, model, base64Image, prompt)
                AiProvider.OFFLINE_MLKIT -> error("Offline provider should be routed locally")
            }
        }

    private fun callGemini(
        baseEndpoint: String,
        model: String,
        apiKey: String,
        base64Image: String,
        prompt: String,
    ): String {
        val url = "${baseEndpoint.trimEnd('/')}/$model:generateContent?key=$apiKey"
        val payload =
            JSONObject().apply {
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put(
                            "parts",
                            JSONArray()
                                .put(JSONObject().put("text", prompt))
                                .put(
                                    JSONObject().put(
                                        "inline_data",
                                        JSONObject()
                                            .put("mime_type", "image/webp")
                                            .put("data", base64Image),
                                    ),
                                ),
                        ),
                    ),
                )
            }
        val response = postJson(url, payload.toString(), emptyMap())
        val root = JSONObject(response)
        return root
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
    }

    private fun callOpenAi(
        endpoint: String,
        model: String,
        apiKey: String,
        base64Image: String,
        prompt: String,
    ): String {
        val payload =
            JSONObject().apply {
                put("model", model)
                put(
                    "messages",
                    JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put(
                                "content",
                                JSONArray()
                                    .put(JSONObject().put("type", "text").put("text", prompt))
                                    .put(
                                        JSONObject()
                                            .put("type", "image_url")
                                            .put(
                                                "image_url",
                                                JSONObject().put("url", "data:image/webp;base64,$base64Image"),
                                            ),
                                    ),
                            ),
                    ),
                )
            }
        val response =
            postJson(
                endpoint,
                payload.toString(),
                mapOf("Authorization" to "Bearer $apiKey"),
            )
        val root = JSONObject(response)
        return root
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }

    private fun callAnthropic(
        endpoint: String,
        model: String,
        apiKey: String,
        base64Image: String,
        prompt: String,
    ): String {
        val payload =
            JSONObject().apply {
                put("model", model)
                put("max_tokens", ANTHROPIC_MAX_TOKENS)
                put(
                    "messages",
                    JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put(
                                "content",
                                JSONArray()
                                    .put(
                                        JSONObject()
                                            .put("type", "image")
                                            .put(
                                                "source",
                                                JSONObject()
                                                    .put("type", "base64")
                                                    .put("media_type", "image/webp")
                                                    .put("data", base64Image),
                                            ),
                                    ).put(JSONObject().put("type", "text").put("text", prompt)),
                            ),
                    ),
                )
            }
        val response =
            postJson(
                endpoint,
                payload.toString(),
                mapOf(
                    "x-api-key" to apiKey,
                    "anthropic-version" to "2023-06-01",
                ),
            )
        val root = JSONObject(response)
        return root
            .getJSONArray("content")
            .getJSONObject(0)
            .getString("text")
    }

    private fun callOllama(
        endpoint: String,
        model: String,
        base64Image: String,
        prompt: String,
    ): String {
        val payload =
            JSONObject().apply {
                put("model", model)
                put("prompt", prompt)
                put("images", JSONArray().put(base64Image))
                put("stream", false)
            }
        val response = postJson(endpoint, payload.toString(), emptyMap())
        return JSONObject(response).getString("response")
    }

    private fun postJson(
        url: String,
        body: String,
        headers: Map<String, String>,
    ): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
            }
            check(connection.responseCode in HTTP_SUCCESS_RANGE) {
                "Vision API error: HTTP ${connection.responseCode}"
            }
            connection.inputStream.use { input ->
                input.reader(Charsets.UTF_8).readText()
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val ANTHROPIC_MAX_TOKENS = 1024
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 45_000
        val HTTP_SUCCESS_RANGE = 200..299
    }
}
