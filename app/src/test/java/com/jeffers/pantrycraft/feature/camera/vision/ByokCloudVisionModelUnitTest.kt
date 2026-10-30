package com.jeffers.pantrycraft.feature.camera.vision

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.jeffers.pantrycraft.data.AiProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.ServerSocket

class ByokCloudVisionModelUnitTest {
    @Test
    fun byokCloudVisionModelOfflineProviderThrowsIllegalState() =
        runBlocking {
            val prefsMap = mutableMapOf<String, Any?>()
            val mockPrefs = createMockPrefs(prefsMap)
            val context =
                object : ContextWrapper(null) {
                    override fun getApplicationContext(): Context = this

                    override fun getSharedPreferences(
                        name: String,
                        mode: Int,
                    ): SharedPreferences = mockPrefs
                }

            val model = ByokCloudVisionModel(context)
            assertNotNull(model)

            try {
                model.analyze(ByteArray(0), "Identify pantry items")
                fail("Expected IllegalStateException for OFFLINE_MLKIT routing")
            } catch (expected: IllegalStateException) {
                assertNotNull(expected.message)
            }
        }

    @Test
    fun byokCloudVisionModelCompanionProperties() {
        val companionField =
            ByokCloudVisionModel::class.java
                .getDeclaredField("Companion")
                .apply { isAccessible = true }
                .get(null)
        val getter =
            companionField.javaClass
                .getDeclaredMethod("getHTTP_SUCCESS_RANGE")
                .apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        val range = getter.invoke(companionField) as IntRange
        assertEquals(200..299, range)
    }

    @Test
    fun ollamaProviderSuccessParsesResponse() =
        runBlocking {
            val json = "{\"response\": \"{\\\"detected_items\\\":[{\\\"name\\\":\\\"Eggs\\\"}]}\"}"
            SimpleMockHttpServer(200, json).use { server ->
                val prefsMap =
                    mutableMapOf<String, Any?>(
                        "selected_provider" to AiProvider.OLLAMA.id,
                        "selected_endpoint_url" to server.url,
                        "selected_model_name" to "llava",
                    )
                val context =
                    object : ContextWrapper(null) {
                        override fun getApplicationContext(): Context = this

                        override fun getSharedPreferences(
                            name: String,
                            mode: Int,
                        ): SharedPreferences = createMockPrefs(prefsMap)
                    }
                val model = ByokCloudVisionModel(context)
                val result = model.analyze(ByteArray(0), "prompt")
                assertTrue(result.contains("Eggs"))
            }
        }

    @Test
    fun serverErrorThrowsIllegalStateException() =
        runBlocking {
            SimpleMockHttpServer(500, "Internal Server Error").use { server ->
                val prefsMap =
                    mutableMapOf<String, Any?>(
                        "selected_provider" to AiProvider.OLLAMA.id,
                        "selected_endpoint_url" to server.url,
                        "selected_model_name" to "llava",
                    )
                val context =
                    object : ContextWrapper(null) {
                        override fun getApplicationContext(): Context = this

                        override fun getSharedPreferences(
                            name: String,
                            mode: Int,
                        ): SharedPreferences = createMockPrefs(prefsMap)
                    }
                val model = ByokCloudVisionModel(context)
                try {
                    model.analyze(ByteArray(0), "prompt")
                    fail("Expected IllegalStateException for 500 status")
                } catch (expected: IllegalStateException) {
                    assertTrue(expected.message?.contains("500") == true)
                }
            }
        }

    @Test
    fun geminiProviderSuccessParsesResponse() =
        runBlocking {
            val json = "{\"candidates\": [{\"content\": {\"parts\": [{\"text\": \"Gemini Milk\"}]}}]}"
            SimpleMockHttpServer(200, json).use { server ->
                val prefsMap =
                    mutableMapOf<String, Any?>(
                        "selected_provider" to AiProvider.GEMINI.id,
                        "selected_endpoint_url" to server.url,
                        "selected_model_name" to "gemini-flash",
                    )
                val context =
                    object : ContextWrapper(null) {
                        override fun getApplicationContext(): Context = this

                        override fun getSharedPreferences(
                            name: String,
                            mode: Int,
                        ): SharedPreferences = createMockPrefs(prefsMap)
                    }
                val model = ByokCloudVisionModel(context)
                val result = model.analyze(ByteArray(0), "prompt")
                assertTrue(result.contains("Gemini Milk"))
            }
        }

    @Test
    fun openAiProviderSuccessParsesResponse() =
        runBlocking {
            val json = "{\"choices\": [{\"message\": {\"content\": \"OpenAI Bread\"}}]}"
            SimpleMockHttpServer(200, json).use { server ->
                val prefsMap =
                    mutableMapOf<String, Any?>(
                        "selected_provider" to AiProvider.OPENAI.id,
                        "selected_endpoint_url" to server.url,
                        "selected_model_name" to "gpt-4o",
                    )
                val context =
                    object : ContextWrapper(null) {
                        override fun getApplicationContext(): Context = this

                        override fun getSharedPreferences(
                            name: String,
                            mode: Int,
                        ): SharedPreferences = createMockPrefs(prefsMap)
                    }
                val model = ByokCloudVisionModel(context)
                val result = model.analyze(ByteArray(0), "prompt")
                assertTrue(result.contains("OpenAI Bread"))
            }
        }

    @Test
    fun anthropicProviderSuccessParsesResponse() =
        runBlocking {
            val json = "{\"content\": [{\"type\": \"text\", \"text\": \"Anthropic Cheese\"}]}"
            SimpleMockHttpServer(200, json).use { server ->
                val prefsMap =
                    mutableMapOf<String, Any?>(
                        "selected_provider" to AiProvider.ANTHROPIC.id,
                        "selected_endpoint_url" to server.url,
                        "selected_model_name" to "claude-sonnet",
                    )
                val context =
                    object : ContextWrapper(null) {
                        override fun getApplicationContext(): Context = this

                        override fun getSharedPreferences(
                            name: String,
                            mode: Int,
                        ): SharedPreferences = createMockPrefs(prefsMap)
                    }
                val model = ByokCloudVisionModel(context)
                val result = model.analyze(ByteArray(0), "prompt")
                assertTrue(result.contains("Anthropic Cheese"))
            }
        }

    private class SimpleMockHttpServer(
        val statusCode: Int,
        val body: String,
    ) : AutoCloseable {
        private val serverSocket = ServerSocket(0)
        val url: String = "http://127.0.0.1:${serverSocket.localPort}"

        init {
            Thread {
                try {
                    serverSocket.accept().use { socket ->
                        val reader = socket.getInputStream().bufferedReader()
                        while (reader.readLine()?.isNotEmpty() == true) { /* drain */ }
                        val response = "HTTP/1.1 $statusCode OK\r\nContent-Length: ${body.length}\r\n\r\n$body"
                        socket.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
                    }
                } catch (_: Exception) {
                    // closed
                }
            }.start()
        }

        override fun close() {
            serverSocket.close()
        }
    }

    private fun createMockPrefs(prefsMap: MutableMap<String, Any?>): SharedPreferences =
        object : SharedPreferences {
            override fun getAll(): Map<String, *> = prefsMap

            override fun getString(
                key: String,
                defValue: String?,
            ): String? = prefsMap[key] as? String ?: defValue

            @Suppress("UNCHECKED_CAST")
            override fun getStringSet(
                key: String,
                def: Set<String>?,
            ): Set<String>? = prefsMap[key] as? Set<String> ?: def

            override fun getInt(
                key: String,
                def: Int,
            ): Int = prefsMap[key] as? Int ?: def

            override fun getLong(
                key: String,
                def: Long,
            ): Long = prefsMap[key] as? Long ?: def

            override fun getFloat(
                key: String,
                def: Float,
            ): Float = prefsMap[key] as? Float ?: def

            override fun getBoolean(
                key: String,
                def: Boolean,
            ): Boolean = prefsMap[key] as? Boolean ?: def

            override fun contains(key: String): Boolean = key in prefsMap

            override fun edit(): SharedPreferences.Editor =
                object : SharedPreferences.Editor {
                    override fun putString(
                        k: String,
                        v: String?,
                    ): SharedPreferences.Editor {
                        prefsMap[k] = v
                        return this
                    }

                    override fun putStringSet(
                        k: String,
                        v: Set<String>?,
                    ): SharedPreferences.Editor {
                        prefsMap[k] = v
                        return this
                    }

                    override fun putInt(
                        k: String,
                        v: Int,
                    ): SharedPreferences.Editor {
                        prefsMap[k] = v
                        return this
                    }

                    override fun putLong(
                        k: String,
                        v: Long,
                    ): SharedPreferences.Editor {
                        prefsMap[k] = v
                        return this
                    }

                    override fun putFloat(
                        k: String,
                        v: Float,
                    ): SharedPreferences.Editor {
                        prefsMap[k] = v
                        return this
                    }

                    override fun putBoolean(
                        k: String,
                        v: Boolean,
                    ): SharedPreferences.Editor {
                        prefsMap[k] = v
                        return this
                    }

                    override fun remove(k: String): SharedPreferences.Editor {
                        prefsMap.remove(k)
                        return this
                    }

                    override fun clear(): SharedPreferences.Editor {
                        prefsMap.clear()
                        return this
                    }

                    override fun commit(): Boolean = true

                    override fun apply() { /* no-op */ }
                }

            override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) { /* no-op */ }

            override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) { /* no-op */ }
        }
}
