package com.jeffers.pantrycraft.feature.camera.vision

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiLiveVisionClientTest {
    @Test
    fun normalizeModelNamePrefixesModelsWhenMissing() {
        assertEquals(
            "models/gemini-2.0-flash-exp",
            GeminiLiveVisionClient.normalizeModelName("gemini-2.0-flash-exp"),
        )
        assertEquals(
            "models/custom-model",
            GeminiLiveVisionClient.normalizeModelName("models/custom-model"),
        )
        assertEquals(
            "models/gemini-2.0-flash-exp",
            GeminiLiveVisionClient.normalizeModelName(""),
        )
        assertEquals(
            "models/gemini-2.0-flash-exp",
            GeminiLiveVisionClient.normalizeModelName("mlkit-local"),
        )
    }

    @Test
    fun buildWebSocketUrlAppendsApiKey() {
        val url = GeminiLiveVisionClient.buildWebSocketUrl("test-api-key")
        assertTrue(url.startsWith("wss://generativelanguage.googleapis.com"))
        assertTrue(url.endsWith("?key=test-api-key"))
    }

    @Test
    fun buildSetupJsonFormatsCorrectStructure() {
        val json =
            GeminiLiveVisionClient.buildSetupJson(
                model = "models/gemini-2.0-flash-exp",
                systemInstruction = "Identify food ingredients",
            )
        val root = JSONObject(json)
        assertTrue(root.has("setup"))

        val setup = root.getJSONObject("setup")
        assertEquals("models/gemini-2.0-flash-exp", setup.getString("model"))

        val modalities = setup.getJSONObject("generationConfig").getJSONArray("responseModalities")
        assertEquals("TEXT", modalities.getString(0))

        val instruction = setup.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0)
        assertEquals("Identify food ingredients", instruction.getString("text"))
    }

    @Test
    fun buildRealtimeMediaChunkJsonFormatsMimeAndData() {
        val json =
            GeminiLiveVisionClient.buildRealtimeMediaChunkJson(
                base64Data = "UklGRiQAAABXRUJQVlA=",
                mimeType = "image/webp",
            )
        val root = JSONObject(json)
        assertTrue(root.has("realtimeInput"))

        val chunk = root.getJSONObject("realtimeInput").getJSONArray("mediaChunks").getJSONObject(0)
        assertEquals("image/webp", chunk.getString("mimeType"))
        assertEquals("UklGRiQAAABXRUJQVlA=", chunk.getString("data"))
    }

    @Test
    fun buildClientContentJsonFormatsTurnsAndCompletion() {
        val json = GeminiLiveVisionClient.buildClientContentJson("Identify food")
        val root = JSONObject(json)
        assertTrue(root.has("clientContent"))

        val clientContent = root.getJSONObject("clientContent")
        assertTrue(clientContent.getBoolean("turnComplete"))

        val turn = clientContent.getJSONArray("turns").getJSONObject(0)
        assertEquals("user", turn.getString("role"))
        assertEquals("Identify food", turn.getJSONArray("parts").getJSONObject(0).getString("text"))
    }

    @Test
    fun extractServerContentTextParsesChunksAndTurnComplete() {
        val serverJson =
            """
            {
              "serverContent": {
                "modelTurn": {
                  "parts": [
                    { "text": "{\"detected_items\": " },
                    { "text": "[]}" }
                  ]
                },
                "turnComplete": true
              }
            }
            """.trimIndent()

        val (text, isComplete) = GeminiLiveVisionClient.extractServerContentText(serverJson)
        assertEquals("{\"detected_items\": []}", text)
        assertTrue(isComplete)
    }

    @Test
    fun extractServerContentTextHandlesIncompleteTurn() {
        val serverJson =
            """
            {
              "serverContent": {
                "modelTurn": {
                  "parts": [
                    { "text": "partial response" }
                  ]
                },
                "turnComplete": false
              }
            }
            """.trimIndent()

        val (text, isComplete) = GeminiLiveVisionClient.extractServerContentText(serverJson)
        assertEquals("partial response", text)
        assertFalse(isComplete)
    }

    @Test
    fun extractServerContentTextHandlesMalformedOrEmptyInput() {
        val (text1, complete1) = GeminiLiveVisionClient.extractServerContentText("{}")
        assertEquals("", text1)
        assertFalse(complete1)

        val (text2, complete2) = GeminiLiveVisionClient.extractServerContentText("not a json")
        assertEquals("", text2)
        assertFalse(complete2)
    }

    @Test
    fun liveVisionPreferencesToggleState() {
        val store = mutableMapOf<String, Any?>()
        val mockPrefs =
            object : SharedPreferences {
                override fun getAll(): MutableMap<String, *> = store

                override fun getString(
                    key: String,
                    defValue: String?,
                ): String? = store[key] as? String ?: defValue

                override fun getStringSet(
                    key: String,
                    defValues: Set<String>?,
                ): Set<String>? = defValues

                override fun getInt(
                    key: String,
                    defValue: Int,
                ): Int = (store[key] as? Int) ?: defValue

                override fun getLong(
                    key: String,
                    defValue: Long,
                ): Long = (store[key] as? Long) ?: defValue

                override fun getFloat(
                    key: String,
                    defValue: Float,
                ): Float = (store[key] as? Float) ?: defValue

                override fun getBoolean(
                    key: String,
                    defValue: Boolean,
                ): Boolean = (store[key] as? Boolean) ?: defValue

                override fun contains(key: String): Boolean = store.containsKey(key)

                override fun edit(): SharedPreferences.Editor = createEditor(store)

                override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

                override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) =
                    Unit
            }

        val context =
            object : ContextWrapper(null) {
                override fun getApplicationContext(): Context = this

                override fun getSharedPreferences(
                    name: String,
                    mode: Int,
                ): SharedPreferences = mockPrefs
            }

        assertFalse(LiveVisionPreferences.isGeminiLiveEnabled(context))
        LiveVisionPreferences.setGeminiLiveEnabled(context, true)
        assertTrue(LiveVisionPreferences.isGeminiLiveEnabled(context))
        LiveVisionPreferences.setGeminiLiveEnabled(context, false)
        assertFalse(LiveVisionPreferences.isGeminiLiveEnabled(context))
    }

    private fun createEditor(store: MutableMap<String, Any?>): SharedPreferences.Editor =
        object : SharedPreferences.Editor {
            override fun putString(
                key: String,
                value: String?,
            ): SharedPreferences.Editor {
                store[key] = value
                return this
            }

            override fun putStringSet(
                key: String,
                values: Set<String>?,
            ): SharedPreferences.Editor = this

            override fun putInt(
                key: String,
                value: Int,
            ): SharedPreferences.Editor = this

            override fun putLong(
                key: String,
                value: Long,
            ): SharedPreferences.Editor = this

            override fun putFloat(
                key: String,
                value: Float,
            ): SharedPreferences.Editor = this

            override fun putBoolean(
                key: String,
                value: Boolean,
            ): SharedPreferences.Editor {
                store[key] = value
                return this
            }

            override fun remove(key: String): SharedPreferences.Editor {
                store.remove(key)
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                store.clear()
                return this
            }

            override fun commit(): Boolean = true

            override fun apply() = Unit
        }
}
