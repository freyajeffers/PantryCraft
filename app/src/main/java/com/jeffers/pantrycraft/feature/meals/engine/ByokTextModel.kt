package com.jeffers.pantrycraft.feature.meals.engine

import android.content.Context
import com.jeffers.pantrycraft.data.AiProvider
import com.jeffers.pantrycraft.data.ByokPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val MAX_TOKENS_ANTHROPIC = 2048
private const val TIMEOUT_CONNECT_MILLIS = 15_000
private const val TIMEOUT_READ_MILLIS = 30_000
private const val HTTP_SUCCESS_START = 200
private const val HTTP_SUCCESS_END = 299

interface TextGenerationModel {
    suspend fun generate(
        systemPrompt: String,
        userPrompt: String,
    ): String
}

class ByokTextModel(
    private val context: Context,
) : TextGenerationModel {
    override suspend fun generate(
        systemPrompt: String,
        userPrompt: String,
    ): String =
        withContext(Dispatchers.IO) {
            val provider = ByokPreferences.selectedProvider(context)
            val model = ByokPreferences.selectedModel(context)
            val endpoint = ByokPreferences.selectedEndpoint(context)
            val apiKey = ByokPreferences.loadApiKey(context).orEmpty()

            when (provider) {
                AiProvider.GEMINI -> callGemini(endpoint, model, apiKey, systemPrompt, userPrompt)
                AiProvider.OPENAI -> callOpenAi(endpoint, model, apiKey, systemPrompt, userPrompt)
                AiProvider.ANTHROPIC -> callAnthropic(endpoint, model, apiKey, systemPrompt, userPrompt)
                AiProvider.OLLAMA -> callOllama(endpoint, model, systemPrompt, userPrompt)
                AiProvider.OFFLINE_MLKIT -> error("Offline provider routed to local fallback")
            }
        }

    private fun callGemini(
        endpoint: String,
        model: String,
        apiKey: String,
        sys: String,
        usr: String,
    ): String {
        val url = "${endpoint.trimEnd('/')}/$model:generateContent?key=$apiKey"
        val payload =
            JSONObject().apply {
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", "$sys\n\n$usr")),
                        ),
                    ),
                )
            }
        val response = postJson(url, payload.toString(), emptyMap())
        return JSONObject(response)
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
        sys: String,
        usr: String,
    ): String {
        val payload =
            JSONObject().apply {
                put("model", model)
                put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", sys))
                        .put(JSONObject().put("role", "user").put("content", usr)),
                )
            }
        val response = postJson(endpoint, payload.toString(), mapOf("Authorization" to "Bearer $apiKey"))
        return JSONObject(response)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }

    private fun callAnthropic(
        endpoint: String,
        model: String,
        apiKey: String,
        sys: String,
        usr: String,
    ): String {
        val payload =
            JSONObject().apply {
                put("model", model)
                put("max_tokens", MAX_TOKENS_ANTHROPIC)
                put("system", sys)
                put(
                    "messages",
                    JSONArray().put(JSONObject().put("role", "user").put("content", usr)),
                )
            }
        val headers =
            mapOf(
                "x-api-key" to apiKey,
                "anthropic-version" to "2023-06-01",
            )
        val response = postJson(endpoint, payload.toString(), headers)
        return JSONObject(response)
            .getJSONArray("content")
            .getJSONObject(0)
            .getString("text")
    }

    private fun callOllama(
        endpoint: String,
        model: String,
        sys: String,
        usr: String,
    ): String {
        val payload =
            JSONObject().apply {
                put("model", model)
                put("system", sys)
                put("prompt", usr)
                put("stream", false)
            }
        val response = postJson(endpoint, payload.toString(), emptyMap())
        return JSONObject(response).getString("response")
    }

    private fun postJson(
        urlString: String,
        jsonBody: String,
        headers: Map<String, String>,
    ): String {
        val url = URL(urlString)
        val connection =
            (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_CONNECT_MILLIS
                readTimeout = TIMEOUT_READ_MILLIS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
        return connection.outputStream.use { out ->
            out.write(jsonBody.toByteArray(Charsets.UTF_8))
            out.flush()
            val code = connection.responseCode
            if (code in HTTP_SUCCESS_START..HTTP_SUCCESS_END) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                val err = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                error("HTTP $code: $err")
            }
        }
    }
}
