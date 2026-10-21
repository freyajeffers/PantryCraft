package com.jeffers.pantrycraft.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ByokPreferencesTest {
    private lateinit var context: Context
    private var originalApiKey: String? = null
    private lateinit var originalProvider: AiProvider
    private lateinit var originalModel: String
    private lateinit var originalEndpoint: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        originalApiKey = ByokPreferences.loadApiKey(context)
        originalProvider = ByokPreferences.selectedProvider(context)
        originalModel = ByokPreferences.selectedModel(context)
        originalEndpoint = ByokPreferences.selectedEndpoint(context)
        ByokPreferences.clearApiKey(context)
    }

    @After
    fun tearDown() {
        ByokPreferences.saveConfiguration(context, originalProvider, originalModel, originalEndpoint)
        if (originalApiKey != null) {
            val keyChars = originalApiKey!!.toCharArray()
            ByokPreferences.saveApiKey(context, keyChars)
            keyChars.fill('\u0000')
        } else {
            ByokPreferences.clearApiKey(context)
        }
    }


    @Test
    fun saveAndLoadApiKeyWithHardwareKeyStoreEncryption() {
        assertFalse(ByokPreferences.hasApiKey(context))
        assertNull(ByokPreferences.loadApiKey(context))

        val originalKey = "sk-antigravity-test-key-12345"
        val keyChars = originalKey.toCharArray()
        ByokPreferences.saveApiKey(context, keyChars)

        // Char array was scrubbed to null bytes after saving
        assertTrue(keyChars.all { it == '\u0000' })
        assertTrue(ByokPreferences.hasApiKey(context))

        val loaded = ByokPreferences.loadApiKey(context)
        assertNotNull(loaded)
        assertEquals(originalKey, loaded)

        ByokPreferences.clearApiKey(context)
        assertFalse(ByokPreferences.hasApiKey(context))
        assertNull(ByokPreferences.loadApiKey(context))
    }

    @Test
    fun savingEmptyApiKeyClearsExistingKey() {
        ByokPreferences.saveApiKey(context, "temporary-key".toCharArray())
        assertTrue(ByokPreferences.hasApiKey(context))

        val emptyChars = "   ".toCharArray()
        ByokPreferences.saveApiKey(context, emptyChars)
        assertFalse(ByokPreferences.hasApiKey(context))
        assertNull(ByokPreferences.loadApiKey(context))
        assertTrue(emptyChars.all { it == '\u0000' })
    }

    @Test
    fun configurationRoundTripsSuccessfully() {
        val testProvider = AiProvider.OPENAI
        val testModel = "gpt-4o"
        val testEndpoint = "https://api.openai.com/v1/chat/completions"

        ByokPreferences.saveConfiguration(context, testProvider, testModel, testEndpoint)

        assertEquals(testProvider, ByokPreferences.selectedProvider(context))
        assertEquals(testModel, ByokPreferences.selectedModel(context))
        assertEquals(testEndpoint, ByokPreferences.selectedEndpoint(context))
    }

    @Test
    fun saveConfigurationBlankModelAndEndpointDefaultsToProviderDefaults() {
        val testProvider = AiProvider.ANTHROPIC
        ByokPreferences.saveConfiguration(context, testProvider, "   ", "   ")

        assertEquals(testProvider, ByokPreferences.selectedProvider(context))
        assertEquals(testProvider.defaultModel, ByokPreferences.selectedModel(context))
        assertEquals(testProvider.defaultEndpoint, ByokPreferences.selectedEndpoint(context))
    }

    @Test
    fun loadApiKeyReturnsNullWhenCorrupted() {
        ByokPreferences.saveApiKey(context, "test-api-key-for-corruption".toCharArray())
        val prefs = context.getSharedPreferences("byok_ai_preferences", Context.MODE_PRIVATE)
        prefs.edit().putString("api_key_ciphertext", "VGhpcyBJcyBDb3JydXB0ZWREYXRh").apply()

        assertNull(ByokPreferences.loadApiKey(context))
    }

    @Test
    fun loadApiKeyReturnsNullWhenIvOrCiphertextMissing() {
        ByokPreferences.saveApiKey(context, "valid-key".toCharArray())
        val prefs = context.getSharedPreferences("byok_ai_preferences", Context.MODE_PRIVATE)

        // Missing IV
        prefs.edit().remove("api_key_iv").apply()
        assertNull(ByokPreferences.loadApiKey(context))

        // Restore IV, remove ciphertext
        ByokPreferences.saveApiKey(context, "valid-key".toCharArray())
        prefs.edit().remove("api_key_ciphertext").apply()
        assertNull(ByokPreferences.loadApiKey(context))
    }
}
