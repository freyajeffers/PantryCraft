package com.jeffers.pantrycraft.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ByokAiProviderTest {
    @Test
    fun fromIdResolvesKnownProvidersCaseInsensitively() {
        assertEquals(AiProvider.GEMINI, AiProvider.fromId("GEMINI"))
        assertEquals(AiProvider.GEMINI, AiProvider.fromId("gemini"))
        assertEquals(AiProvider.OPENAI, AiProvider.fromId("openai"))
        assertEquals(AiProvider.ANTHROPIC, AiProvider.fromId("Anthropic"))
        assertEquals(AiProvider.OLLAMA, AiProvider.fromId("ollama"))
        assertEquals(AiProvider.OFFLINE_MLKIT, AiProvider.fromId("OFFLINE_MLKIT"))
    }

    @Test
    fun fromIdDefaultsToOfflineMlKitForUnknownOrNull() {
        assertEquals(AiProvider.OFFLINE_MLKIT, AiProvider.fromId(null))
        assertEquals(AiProvider.OFFLINE_MLKIT, AiProvider.fromId(""))
        assertEquals(AiProvider.OFFLINE_MLKIT, AiProvider.fromId("UNKNOWN_PROVIDER"))
    }

    @Test
    fun apiKeyRequirementMatchesProviderTypes() {
        assertTrue(AiProvider.GEMINI.requiresApiKey)
        assertTrue(AiProvider.OPENAI.requiresApiKey)
        assertTrue(AiProvider.ANTHROPIC.requiresApiKey)
        assertFalse(AiProvider.OFFLINE_MLKIT.requiresApiKey)
        assertFalse(AiProvider.OLLAMA.requiresApiKey)
    }

    @Test
    fun cloudAndLocalProvidersDefineSensibleDefaults() {
        assertTrue(AiProvider.GEMINI.defaultEndpoint.startsWith("https://"))
        assertTrue(AiProvider.OPENAI.defaultEndpoint.startsWith("https://"))
        assertTrue(AiProvider.ANTHROPIC.defaultEndpoint.startsWith("https://"))
        assertTrue(AiProvider.OLLAMA.defaultEndpoint.startsWith("http://"))

        assertTrue(AiProvider.GEMINI.defaultModel.isNotBlank())
        assertTrue(AiProvider.OPENAI.defaultModel.isNotBlank())
        assertTrue(AiProvider.ANTHROPIC.defaultModel.isNotBlank())
        assertTrue(AiProvider.OLLAMA.defaultModel.isNotBlank())
    }

    @Test
    fun allProvidersDefineNonEmptyIdAndLabel() {
        for (provider in AiProvider.entries) {
            assertTrue(provider.id.isNotBlank())
            assertTrue(provider.label.isNotBlank())
        }
    }

    @Test
    fun providerDisplayLabelsAreAccurate() {
        assertEquals("On-Device Offline (ML Kit / Local)", AiProvider.OFFLINE_MLKIT.label)
        assertEquals("Google Gemini (BYOKey)", AiProvider.GEMINI.label)
        assertEquals("OpenAI / OpenRouter (BYOKey)", AiProvider.OPENAI.label)
        assertEquals("Anthropic Claude (BYOKey)", AiProvider.ANTHROPIC.label)
        assertEquals("Self-Hosted Ollama (Local LAN)", AiProvider.OLLAMA.label)
    }
}
