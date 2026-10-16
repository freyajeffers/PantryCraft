package com.jeffers.pantrycraft.feature.camera.vision

import android.content.Context
import android.util.Base64
import com.jeffers.pantrycraft.data.ByokPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/** Routes snapshot inference to an opted-in provider and falls back to the local model on any failure. */
class ModelRouter(
    context: Context,
    private val offlineModel: LocalVisionModel,
    private val connectTimeoutMillis: Int = 8_000,
    private val readTimeoutMillis: Int = 20_000,
) : LocalVisionModel,
    AutoCloseable {
    private val appContext = context.applicationContext

    override suspend fun analyze(
        webpBytes: ByteArray,
        prompt: String,
    ): String {
        val configuration = ByokPreferences.read(appContext)
        if (configuration.provider == ByokPreferences.Provider.OFFLINE ||
            (configuration.provider != ByokPreferences.Provider.OLLAMA && configuration.apiKey.isNullOrBlank())
        ) {
            return offlineModel.analyze(webpBytes, prompt)
        }
        return runCatching {
            withContext(Dispatchers.IO) { request(configuration, webpBytes, prompt) }
        }.getOrElse {
            offlineModel.analyze(webpBytes, prompt)
        }
    }

    override fun close() {
        (offlineModel as? AutoCloseable)?.close()
    }

    private fun request(
        configuration: ByokPreferences.Configuration,
        webpBytes: ByteArray,
        prompt: String,
    ): String {
        val endpoint = configuration.endpoint ?: defaultEndpoint(configuration.provider)
        val connection =
            (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                configuration.apiKey?.let { key ->
                    when (configuration.provider) {
                        ByokPreferences.Provider.GEMINI -> Unit
                        ByokPreferences.Provider.ANTHROPIC -> setRequestProperty("x-api-key", key)
                        else -> setRequestProperty("Authorization", "Bearer $key")
                    }
                }
            }
        try {
            val body = requestBody(configuration.provider, webpBytes, prompt)
            if (configuration.provider == ByokPreferences.Provider.GEMINI) {
                val separator = if (endpoint.contains('?')) '&' else '?'
                connection.setRequestProperty("Content-Type", "application/json")
                val key = configuration.apiKey.orEmpty()
                val urlWithKey = URL("$endpoint${separator}key=$key")
                connection.disconnect()
                return requestWithUrl(urlWithKey, configuration.provider, body, null)
            }
            return requestWithConnection(connection, configuration.provider, body)
        } finally {
            connection.disconnect()
        }
    }

    private fun requestWithUrl(
        url: URL,
        provider: ByokPreferences.Provider,
        body: String,
        apiKey: String?,
    ): String {
        val connection =
            (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        return try {
            requestWithConnection(connection, provider, body)
        } finally {
            connection.disconnect()
        }
    }

    private fun requestWithConnection(
        connection: HttpURLConnection,
        provider: ByokPreferences.Provider,
        body: String,
    ): String {
        connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        check(status in 200..299) { "Model provider request failed with HTTP $status" }
        return extractText(provider, response)
    }

    private fun requestBody(
        provider: ByokPreferences.Provider,
        bytes: ByteArray,
        prompt: String,
    ): String {
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return when (provider) {
            ByokPreferences.Provider.GEMINI ->
                JSONObject()
                    .put(
                        "contents",
                        JSONArray().put(
                            JSONObject().put(
                                "parts",
                                JSONArray()
                                    .put(JSONObject().put("text", prompt))
                                    .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/webp").put("data", encoded))),
                            ),
                        ),
                    ).toString()
            ByokPreferences.Provider.ANTHROPIC ->
                JSONObject()
                    .put("model", "claude-3-5-sonnet-latest")
                    .put("max_tokens", 2048)
                    .put(
                        "messages",
                        JSONArray().put(
                            JSONObject()
                                .put("role", "user")
                                .put(
                                    "content",
                                    JSONArray()
                                        .put(JSONObject().put("type", "text").put("text", prompt))
                                        .put(
                                            JSONObject().put("type", "image").put(
                                                "source",
                                                JSONObject()
                                                    .put("type", "base64")
                                                    .put("media_type", "image/webp")
                                                    .put("data", encoded),
                                            ),
                                        ),
                                ),
                        ),
                    ).toString()
            ByokPreferences.Provider.OLLAMA ->
                JSONObject()
                    .put("model", "llava")
                    .put("stream", false)
                    .put(
                        "messages",
                        JSONArray().put(JSONObject().put("role", "user").put("content", prompt).put("images", JSONArray().put(encoded))),
                    ).toString()
            else ->
                JSONObject()
                    .put("model", "gpt-4o-mini")
                    .put(
                        "messages",
                        JSONArray().put(
                            JSONObject().put("role", "user").put(
                                "content",
                                JSONArray()
                                    .put(JSONObject().put("type", "text").put("text", prompt))
                                    .put(
                                        JSONObject()
                                            .put(
                                                "type",
                                                "image_url",
                                            ).put("image_url", JSONObject().put("url", "data:image/webp;base64,$encoded")),
                                    ),
                            ),
                        ),
                    ).toString()
        }
    }

    private fun extractText(
        provider: ByokPreferences.Provider,
        response: String,
    ): String {
        val json = JSONObject(response)
        if (json.has("detected_items")) return json.toString()
        return when (provider) {
            ByokPreferences.Provider.GEMINI ->
                json
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
            ByokPreferences.Provider.ANTHROPIC -> json.getJSONArray("content").getJSONObject(0).getString("text")
            ByokPreferences.Provider.OLLAMA -> json.getJSONObject("message").getString("content")
            else ->
                json
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
        }
    }

    private fun defaultEndpoint(provider: ByokPreferences.Provider): String =
        when (provider) {
            ByokPreferences.Provider.GEMINI -> "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"
            ByokPreferences.Provider.ANTHROPIC -> "https://api.anthropic.com/v1/messages"
            ByokPreferences.Provider.OLLAMA -> "http://127.0.0.1:11434/api/chat"
            else -> "https://api.openai.com/v1/chat/completions"
        }
}
