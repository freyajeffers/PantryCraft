package com.jeffers.pantrycraft.feature.camera.vision

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeffers.pantrycraft.data.AiProvider
import com.jeffers.pantrycraft.data.ByokPreferences
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
class ByokCloudVisionModelTest {
    private lateinit var context: Context
    private var originalProvider: AiProvider = AiProvider.OFFLINE_MLKIT
    private var originalModel: String = ""
    private var originalEndpoint: String = ""
    private var originalApiKey: String? = null
    private var mockServer: MockHttpServer? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        originalProvider = ByokPreferences.selectedProvider(context)
        originalModel = ByokPreferences.selectedModel(context)
        originalEndpoint = ByokPreferences.selectedEndpoint(context)
        originalApiKey = ByokPreferences.loadApiKey(context)
    }

    @After
    fun tearDown() {
        mockServer?.close()
        mockServer = null
        if (originalApiKey != null) {
            val keyChars = originalApiKey!!.toCharArray()
            ByokPreferences.saveApiKey(context, keyChars)
            keyChars.fill('\u0000')
        } else {
            ByokPreferences.clearApiKey(context)
        }
        ByokPreferences.saveConfiguration(context, originalProvider, originalModel, originalEndpoint)
    }

    @Test
    fun geminiProviderSendsStructuredPayloadAndParsesResponse() = runBlocking {
        val server = MockHttpServer { request ->
            assertTrue(request.path.contains("gemini-test-model:generateContent"))
            assertTrue(request.path.contains("key=test-gemini-key"))
            assertTrue(request.body.contains("inline_data"))
            assertTrue(request.body.contains("image") && request.body.contains("webp"))

            MockHttpResponse(
                statusCode = 200,
                body = """
                    {
                      "candidates": [
                        {
                          "content": {
                            "parts": [
                              {
                                "text": "{\"detected_items\":[{\"name\":\"Gemini Milk\"}]}"
                              }
                            ]
                          }
                        }
                      ]
                    }
                """.trimIndent(),
            )
        }
        mockServer = server

        ByokPreferences.saveConfiguration(
            context,
            AiProvider.GEMINI,
            "gemini-test-model",
            server.baseUrl,
        )
        ByokPreferences.saveApiKey(context, "test-gemini-key".toCharArray())

        val model = ByokCloudVisionModel(context)
        val result = model.analyze(byteArrayOf(1, 2, 3), "scan shelf")
        assertTrue(result.contains("Gemini Milk"))
    }

    @Test
    fun openAiProviderSendsBearerAuthAndParsesContent() = runBlocking {
        val server = MockHttpServer { request ->
            assertEquals("Bearer test-openai-key", request.headers["authorization"])
            assertTrue(request.body.contains("image_url"))
            assertTrue(request.body.contains("gpt-4o-mini-test"))

            MockHttpResponse(
                statusCode = 200,
                body = """
                    {
                      "choices": [
                        {
                          "message": {
                            "content": "{\"detected_items\":[{\"name\":\"OpenAI Eggs\"}]}"
                          }
                        }
                      ]
                    }
                """.trimIndent(),
            )
        }
        mockServer = server

        ByokPreferences.saveConfiguration(
            context,
            AiProvider.OPENAI,
            "gpt-4o-mini-test",
            "${server.baseUrl}/v1/chat/completions",
        )
        ByokPreferences.saveApiKey(context, "test-openai-key".toCharArray())

        val model = ByokCloudVisionModel(context)
        val result = model.analyze(byteArrayOf(4, 5, 6), "scan eggs")
        assertTrue(result.contains("OpenAI Eggs"))
    }

    @Test
    fun anthropicProviderSendsCustomHeadersAndParsesText() = runBlocking {
        val server = MockHttpServer { request ->
            assertEquals("test-anthropic-key", request.headers["x-api-key"])
            assertEquals("2023-06-01", request.headers["anthropic-version"])
            assertTrue(request.body.contains("claude-test-model"))
            assertTrue(request.body.contains("max_tokens"))

            MockHttpResponse(
                statusCode = 200,
                body = """
                    {
                      "content": [
                        {
                          "type": "text",
                          "text": "{\"detected_items\":[{\"name\":\"Anthropic Cheese\"}]}"
                        }
                      ]
                    }
                """.trimIndent(),
            )
        }
        mockServer = server

        ByokPreferences.saveConfiguration(
            context,
            AiProvider.ANTHROPIC,
            "claude-test-model",
            "${server.baseUrl}/v1/messages",
        )
        ByokPreferences.saveApiKey(context, "test-anthropic-key".toCharArray())

        val model = ByokCloudVisionModel(context)
        val result = model.analyze(byteArrayOf(7, 8, 9), "scan cheese")
        assertTrue(result.contains("Anthropic Cheese"))
    }

    @Test
    fun ollamaProviderSendsPromptWithoutAuth() = runBlocking {
        val server = MockHttpServer { request ->
            assertTrue(request.body.contains("llava-test"))
            assertTrue(request.body.contains("scan pantry"))

            MockHttpResponse(
                statusCode = 200,
                body = """
                    {
                      "response": "{\"detected_items\":[{\"name\":\"Ollama Bread\"}]}"
                    }
                """.trimIndent(),
            )
        }
        mockServer = server

        ByokPreferences.saveConfiguration(
            context,
            AiProvider.OLLAMA,
            "llava-test",
            "${server.baseUrl}/api/generate",
        )
        ByokPreferences.clearApiKey(context)

        val model = ByokCloudVisionModel(context)
        val result = model.analyze(byteArrayOf(10, 11, 12), "scan pantry")
        assertTrue(result.contains("Ollama Bread"))
    }

    @Test(expected = IllegalStateException::class)
    fun offlineMlKitProviderThrowsIllegalStateException() = runBlocking {
        ByokPreferences.saveConfiguration(context, AiProvider.OFFLINE_MLKIT, "", "")
        val model = ByokCloudVisionModel(context)
        model.analyze(ByteArray(0), "test")
        Unit
    }

    @Test(expected = IllegalStateException::class)
    fun serverErrorResponseThrowsException() = runBlocking {
        val server = MockHttpServer {
            MockHttpResponse(statusCode = 500, body = "Internal Server Error")
        }
        mockServer = server

        ByokPreferences.saveConfiguration(
            context,
            AiProvider.OLLAMA,
            "llava",
            server.baseUrl,
        )

        val model = ByokCloudVisionModel(context)
        model.analyze(ByteArray(0), "prompt")
        Unit
    }

    @Test
    fun modelRouterCloseClosesBothModelsWhenAutoCloseable() {
        var localClosed = false
        var cloudClosed = false

        class CloseableLocalModel : LocalVisionModel, AutoCloseable {
            override suspend fun analyze(webpBytes: ByteArray, prompt: String) = ""
            override fun close() { localClosed = true }
        }

        class CloseableCloudModel : LocalVisionModel, AutoCloseable {
            override suspend fun analyze(webpBytes: ByteArray, prompt: String) = ""
            override fun close() { cloudClosed = true }
        }

        val router = ModelRouter(context, CloseableLocalModel(), CloseableCloudModel())
        router.close()

        assertTrue(localClosed)
        assertTrue(cloudClosed)
    }

    private data class MockHttpRequest(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: String,
    )

    private data class MockHttpResponse(
        val statusCode: Int,
        val body: String,
    )

    private class MockHttpServer(
        private val handler: (MockHttpRequest) -> MockHttpResponse,
    ) : AutoCloseable {
        private val serverSocket = ServerSocket(0)
        val baseUrl = "http://127.0.0.1:${serverSocket.localPort}"
        private val executor = Executors.newSingleThreadExecutor()
        @Volatile private var isRunning = true

        init {
            executor.execute {
                while (isRunning) {
                    try {
                        val client = serverSocket.accept()
                        handleClient(client)
                    } catch (_: Exception) {
                        break
                    }
                }
            }
        }

        private fun handleClient(client: Socket) {
            client.use { socket ->
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                val firstLine = reader.readLine() ?: return
                val parts = firstLine.split(" ")
                val method = parts.getOrNull(0) ?: "GET"
                val path = parts.getOrNull(1) ?: "/"

                val headers = readHeaders(reader)
                val body = readBody(reader, headers)

                val response = handler(MockHttpRequest(method, path, headers, body))
                sendResponse(socket, response)
            }
        }

        private fun readHeaders(reader: BufferedReader): Map<String, String> {
            val headers = mutableMapOf<String, String>()
            var line = reader.readLine()
            while (!line.isNullOrEmpty()) {
                val splitIdx = line.indexOf(':')
                if (splitIdx > 0) {
                    val key = line.substring(0, splitIdx).trim().lowercase()
                    val value = line.substring(splitIdx + 1).trim()
                    headers[key] = value
                }
                line = reader.readLine()
            }
            return headers
        }

        private fun readBody(reader: BufferedReader, headers: Map<String, String>): String {
            val length = headers["content-length"]?.toIntOrNull() ?: 0
            return when {
                length > 0 -> readFixedLengthBody(reader, length)
                headers["transfer-encoding"]?.equals("chunked", ignoreCase = true) == true -> readChunkedBody(reader)
                else -> ""
            }
        }

        private fun readFixedLengthBody(reader: BufferedReader, length: Int): String {
            val chars = CharArray(length)
            var offset = 0
            while (offset < length) {
                val read = reader.read(chars, offset, length - offset)
                if (read < 0) break
                offset += read
            }
            return String(chars, 0, offset)
        }

        private fun readChunkedBody(reader: BufferedReader): String {
            val sb = StringBuilder()
            var line = reader.readLine()?.trim()
            while (!line.isNullOrEmpty()) {
                val chunkSize = line.toIntOrNull(16) ?: 0
                if (chunkSize <= 0) break
                appendChunk(reader, sb, chunkSize)
                reader.readLine()
                line = reader.readLine()?.trim()
            }
            return sb.toString()
        }

        private fun appendChunk(reader: BufferedReader, sb: StringBuilder, chunkSize: Int) {
            val chunk = CharArray(chunkSize)
            var read = 0
            while (read < chunkSize) {
                val r = reader.read(chunk, read, chunkSize - read)
                if (r < 0) break
                read += r
            }
            sb.append(chunk, 0, read)
        }

        private fun sendResponse(socket: Socket, response: MockHttpResponse) {
            val out = socket.getOutputStream()
            val responseBytes = response.body.toByteArray(StandardCharsets.UTF_8)
            val statusText = if (response.statusCode == 200) "OK" else "Error"
            val responseHeader =
                "HTTP/1.1 ${response.statusCode} $statusText\r\n" +
                    "Content-Type: application/json; charset=utf-8\r\n" +
                    "Content-Length: ${responseBytes.size}\r\n" +
                    "Connection: close\r\n\r\n"
            out.write(responseHeader.toByteArray(StandardCharsets.UTF_8))
            out.write(responseBytes)
            out.flush()
        }

        override fun close() {
            isRunning = false
            runCatching { serverSocket.close() }
            executor.shutdownNow()
        }
    }
}
